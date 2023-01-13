@Library(['cloudbees-devops-global-pipeline@master', 'vcp-common']) _
pipeline {
  options {
    buildDiscarder(logRotator(numToKeepStr: "10"))
  }

  environment {
    cmkArn = credentials("devops-svc-kms-key")
    APP_NAME = env.APP_NAME

    DOCKER_REGISTRY = "docker-snapshot-local.artifactory.intouchhealth.io"

    IMAGE_NAME = "${APP_NAME}-mfe"
    CACHE_IMAGE_NAME = "${APP_NAME}-mfe-cache"
    USE_CACHE = true

    DOCKER_REGISTRY_TYPE = "docker-snapshot-local"
    DOCKER_REGISTRY_DOMAIN = "artifactory.intouchhealth.io"
    ARTIFACTORY_PREFIX = "ucp"

    DOCKER_REGISTRY_URL = "${env.DOCKER_REGISTRY_TYPE}.${env.DOCKER_REGISTRY_DOMAIN}/${env.ARTIFACTORY_PREFIX}"

    S3_STATIC_FOLDER = APP_NAME
    BASE_MANIFEST_URL = "/${S3_STATIC_FOLDER}"

    //TDB
    EKS_CLUSTER_NAME = "staging-eks-cluster"
    AWS_REGION = "us-east-1"
    DATACENTER = "aws"
    PLATFORM = "overlai"
    CLUSTER = "staging"
  }

  agent {
    kubernetes {
      label "devops-pipeline-npm-node"
      yaml """
---
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: eod-tools
      image: docker.artifactory.intouchhealth.io/devops/docker-jenkins-eod:1.20.4-6
      command:
        - cat
      tty: true
    - name: docker
      image: docker.artifactory.intouchhealth.io/devops/docker:19.03.6-dind
      command:
        - cat
      tty: true
      volumeMounts:
        - mountPath: /var/run/docker.sock
          name: docker-sock
      ports:
        - containerPort: 80
    - name: docker-jenkins-agent-aws-cli
      image: docker.artifactory.intouchhealth.io/devops/docker-jenkins-aws-encryption-sdk-cli:master-7
      command:
        - cat
      tty: true
      volumeMounts:
        - mountPath: /var/run/docker.sock
          name: docker-sock
      ports:
        - containerPort: 80
    - name: docker-jenkins-tdh-platform
      image: docker.artifactory.intouchhealth.io/devops/docker-jenkins-tdh-platform:master-18
      command:
        - cat
      tty: true
      volumeMounts:
        - mountPath: /var/run/docker.sock
          name: docker-sock
      ports:
        - containerPort: 80
  imagePullSecrets:
    - name: regcreds
  volumes:
    - name: docker-sock
      hostPath:
        path: /var/run/docker.sock
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
          - matchExpressions:
              - key: partition
                operator: In
                values:
                  - spot-agents
                  - regular-agents
  tolerations:
    - key: partition
      operator: Equal
      value: spot-agents
      effect: NoSchedule
    - key: partition
      operator: Equal
      value: regular-agents
      effect: NoSchedule
"""
    }
  }

  parameters {
    choice(description: 'Environment to deploy into', name: 'DEPLOY_ENVIRONMENT', choices: "ucp-sit")
    booleanParam(description: 'If true, should publish shared types to jfrog packages', name: 'SHOULD_PUBLISH_SHARED_TYPES', defaultValue: false)
  }

  stages {
    stage ("Initialization"){
      parallel {
        stage("Pre configuration") {
          steps {
            container("docker-jenkins-agent-aws-cli") {
              script {
                devopsKms.npmencrypt()
                devopsKms.npmdecrypt()
              }
            }
          }
        }
        stage("Pull cache image") {
          steps {
            container("docker") {
              script {
                env.YARN_LOCK_SHA = sh(returnStdout: true, script: "sha256sum yarn.lock | awk '{print \$1;}'").trim()
                build.dockerLogin()
                catchError {
                  ithArtifactorySnapshotUpload.dockerpull(imagenamebase: "${DOCKER_REGISTRY_URL}/${CACHE_IMAGE_NAME}:${YARN_LOCK_SHA}")
                }
              }
            }
          }
          post {
            failure {
              script {
                USE_CACHE = false
              }
            }
          }
        }
      }
    }

    stage('Build cache image') {
      when {
        expression { !USE_CACHE }
      }
      steps {
        container("docker") {
          script {
            sh """
              docker build --network=host \
              --build-arg BUILD_CACHE_MODE=1 \
              -t ${DOCKER_REGISTRY_URL}/${CACHE_IMAGE_NAME}:${YARN_LOCK_SHA} .
            """
            ithArtifactorySnapshotUpload.docker(imagenamebase: "${DOCKER_REGISTRY_URL}/${CACHE_IMAGE_NAME}:${YARN_LOCK_SHA}")
          }
        }
      }
    }

    stage('Build source image') {
      steps {
        container("docker") {
          script {
            sh """
              docker build --network=host \
              --build-arg BUILD_CACHE_MODE=0 \
              --build-arg CACHE_IMAGE=${DOCKER_REGISTRY_URL}/${CACHE_IMAGE_NAME}:${YARN_LOCK_SHA} \
              -t ${DOCKER_REGISTRY_URL}/${IMAGE_NAME} .
            """
          }
        }
      }
    }
    stage("Build and Checks") {
      parallel {
        stage("Build mfe") {
          steps {
            container("docker") {
              script {
                sh "docker rm -f mfe-runtime-build || true"
                sh """
                  docker run \
                  --name mfe-runtime-build \
                  ${DOCKER_REGISTRY_URL}/${IMAGE_NAME} \
                  sh -c 'yarn build'
                """
                sh "mkdir dist"
                sh "docker cp mfe-runtime-build:/app/dist/. dist/"
                sh "docker rm -f mfe-runtime-build || true"
              }
            }
          }
        }
        stage("Build shared types") {
          steps {
            container("docker") {
              script {
                sh "docker rm -f mfe-runtime-shared-build || true"
                sh """
                  docker run \
                  --name mfe-runtime-shared-build \
                  ${DOCKER_REGISTRY_URL}/${IMAGE_NAME} \
                  sh -c 'yarn share:types'
                """
                sh "docker cp mfe-runtime-shared-build:/app/package/. package/"
                sh "docker rm -f mfe-runtime-shared-build || true"
              }
            }
          }
        }
        stage('Types check:types') {
          steps {
            container("docker") {
              script {
                sh """
                  docker run --rm \
                  ${DOCKER_REGISTRY_URL}/${IMAGE_NAME} \
                  yarn check:types
                """
              }
            }
          }
        }
        stage('Code Style prettier-check:ts') {
          steps {
            container("docker") {
              script {
                sh """
                  docker run --rm \
                  ${DOCKER_REGISTRY_URL}/${IMAGE_NAME} \
                  yarn prettier-check:ts
                """
              }
            }
          }
        }
        stage('Code Style prettier-check:styles') {
          steps {
            container("docker") {
              script {
                sh """
                  docker run --rm \
                  ${DOCKER_REGISTRY_URL}/${IMAGE_NAME} \
                  yarn prettier-check:styles
                """
              }
            }
          }
        }
        stage('Code Style lint-check') {
          steps {
            container("docker") {
              script {
                sh """
                  docker run --rm \
                  ${DOCKER_REGISTRY_URL}/${IMAGE_NAME} \
                  yarn lint-check
                """
              }
            }
          }
        }
      }
    }
    stage('Publish') {
      parallel {
        stage("Publish shared types") {
          when {
            expression {
              return shouldPublishSharedTypes()
            }
          }
          steps {
            dir('package') {
              container("docker-jenkins-agent-aws-cli") {
                script {
                  devopsKms.npmencrypt()
                  devopsKms.npmdecrypt()
                }
              }
              container("docker-jenkins-tdh-platform") {
                script {
                  env.PREV_SHARED_TYPES_VERSION = sh(script: "npm show @teladoc/${APP_NAME}-mfe-types version || echo 0.0.0", returnStdout: true).trim()
                  echo "Current shared types package version: ${PREV_SHARED_TYPES_VERSION}"
                  sh '''
                    npm version "${PREV_SHARED_TYPES_VERSION}" || true 
                    npm version patch
                    npm publish --verbose
                  '''
                  env.NEW_SHARED_TYPES_VERSION = sh(script: "npm show @teladoc/${APP_NAME}-mfe-types version", returnStdout: true)
                  echo "New shared types package version: ${NEW_SHARED_TYPES_VERSION}"
                }
              }
              container('docker-jenkins-agent-aws-cli') {
                script {
                  ithArtifactoryReleaseUpload.npm(artifactpath: "@teladoc/${APP_NAME}-mfe-types/-", version: "${env.NEW_SHARED_TYPES_VERSION}")
                  ithArtifactorySnapshotRetention.basedonversion(repositoryname: 'npm-snapshot-local', artifactpath: "@teladoc/${APP_NAME}-mfe-types/-/@teladoc", numberOfversiontokeep: '10', dryrun: 'false')
                }
              }
            }
          }
        }
        stage("Publish MFE without sourcemaps") {
          when {
            expression {
              return shouldPublish()
            }
          }
          steps {
            script {
              withS3Credentials {
                sh "aws s3 cp dist s3://truclinic-static/${S3_STATIC_FOLDER}/ --recursive --acl public-read --cache-control max-age=31557600 --exclude '*.map' --exclude 'index.html'"
              }
            }
          }
        }
      }
    }
    stage("Deploy to SIT") {
      when {
        expression {
          return shouldPublish()
        }
      }
      environment {
        NAMESPACE = "ucp-sit"
      }
      steps {
        script {
          def manifestFile = findManifestFile()
          echo "${manifestFile}"
          env.MANIFEST_URL = "${BASE_MANIFEST_URL}/${manifestFile}"
          withEODTools {
            dir("config/deploy/manifest") {
              sh "helmfile template > ../../../manifest.yml"
              sh "helmfile apply"
            }
          }
        }
      }
    }
    stage("Deploy to QA") {
      when {
        expression {
          return shouldPublish()
        }
      }
      environment {
        NAMESPACE = "ucp-qa"
      }
      steps {
        script {
          def manifestFile = findManifestFile()
          echo "${manifestFile}"
          env.MANIFEST_URL = "${BASE_MANIFEST_URL}/${manifestFile}"
          withEODTools {
            dir("config/deploy/manifest") {
              sh "helmfile template > ../../../manifest.yml"
              sh "helmfile apply"
            }
          }
        }
      }
    }
    stage('Done') {
      steps {
        echo "Done"
      }
    }
  }
  post {
    always {
      script {
        if (shouldPublish()) {
          archiveArtifacts artifacts: "manifest.yml"
        }
      }
    }
    success {
      script {
        echo "success"
      }
    }
    failure {
      script {
        def errors = failedStages.getFailures(currentBuild)
        if (isMasterBranch()) {
          notifyDevelopers(errors)
        }
      }
    }
    cleanup {
      container("docker-jenkins-tdh-platform") {
        script {
          echo "Cleanup"
          vcpArtifacts.clear()
        }
      }
    }
  }
}

def inApp(Closure body) {
  nodejs(nodeJSInstallationName: '16.18', configId: 'npmrc-config') {
    body()
  }
}

def notifyDevelopers(String errors) {
  def blocks = [
      [
          type: "section",
          text: [
              type: "mrkdwn",
              text: "@here :x: Unified UI Components app build failed in master\n\n> nobody has a higher priority task than fixing the build (c) Kent Beck\n\n<${env.RUN_DISPLAY_URL}|Build results>\n\n```${errors}```"
          ]
      ]
  ]

  slackSend botUser: true, channel: '#unified-queue-fe', color: 'danger', message: "Unified UI Components build failed in master", blocks: blocks, tokenCredentialId: 'slack-api-token-new', username: 'Jenkins'
}

def findManifestFile() {
  dir ("dist"){
    String manifestPrefix = "unifiedQueueManifest"
    def manifestFile = findFiles(glob: "$manifestPrefix*.js")[0]

    if (manifestFile) {
      echo "Manifest file found: $manifestFile"
    } else {
      echo "Manifest file not found! Aborting..."
      exit 1
    }

    return manifestFile
  }
}

def shouldPublish() {
  return isMasterBranch()
}

def shouldPublishSharedTypes() {
  return  !!params.SHOULD_PUBLISH_SHARED_TYPES
}

def isMasterBranch() {
  return env.BRANCH_NAME == 'master'
}

def withS3Credentials(Closure body) {
  container("docker-jenkins-agent-aws-cli") {
    withCredentials([[
                         $class           : 'AmazonWebServicesCredentialsBinding',
                         credentialsId    : "aws-credentials-overlai-s3",
                         accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                         secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                     ]]) {
      body()
    }
  }
}

def withEODTools(Closure body) {
  container("eod-tools") {
    echo "executing setup.sh to configure aws profiles and kubernetes"
    script {
      withCredentials(
          [
              string(credentialsId: 'aws-iwa-staging-eks-deploy-user-accesskey', variable: 'AWS_ACCESS_KEY_ID'),
              string(credentialsId: 'aws-iwa-staging-eks-deploy-user-secretkey', variable: 'AWS_SECRET_ACCESS_KEY')
          ]) {
        sh('aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID')
        sh('aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY')
        sh('aws configure set region $AWS_REGION')
        sh('aws eks update-kubeconfig --name $EKS_CLUSTER_NAME')
      }
    }
    withCredentials([
        usernamePassword(credentialsId: 'artifactory-svc-repository-uploader', usernameVariable: 'ARTIFACTORY_HELM_REPO_USER', passwordVariable: 'ARTIFACTORY_HELM_REPO_PASS')
    ]) {
      body()
    }
  }
}
