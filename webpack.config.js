const path = require("path")
const HtmlWebPackPlugin = require("html-webpack-plugin")
const svgrConfig = require("./svgr.config")
const ModuleFederationPlugin = require("webpack/lib/container/ModuleFederationPlugin")
const contract = require("./webpack.contract")
const deps = require("./package.json").dependencies

const getFormattedBranchName = () => {
  const branch = process.env.BRANCH_NAME
  if (!branch) {
    return "local"
  }

  return branch.replace(/.*\//, "").toLowerCase()
}

module.exports = (env, argv) => ({
  entry: "./src/index.tsx",
  devServer: {
    ...contract.devServer,
    static: path.join(__dirname, "dist"),
    headers: { "Access-Control-Allow-Origin": "*" },
    historyApiFallback: true,
  },
  plugins: [
    new HtmlWebPackPlugin({
      template: "src/index.html",
    }),
    new ModuleFederationPlugin({
      name: contract.moduleFederationPlugin.name,
      filename:
        argv?.mode === "development"
          ? `${contract.moduleFederationPlugin.file.name}.${contract.moduleFederationPlugin.file.extension}`
          : `${
              contract.moduleFederationPlugin.file.name
            }.${getFormattedBranchName()}.[contenthash].${
              contract.moduleFederationPlugin.file.extension
            }`,
      remotes: {},
      exposes: {
        ".": "./src/App.tsx",
      },
      shared: {
        ...deps,
        react: {
          singleton: true,
          requiredVersion: deps.react,
        },
        "react-dom": {
          singleton: true,
          requiredVersion: deps["react-dom"],
        },
      },
    }),
  ],
  output: {
    filename: `${getFormattedBranchName()}-[contenthash].bundle.js`,
    path: path.resolve(__dirname, "dist"),
    clean: true,
  },
  resolve: {
    modules: ["node_modules"],
    extensions: [".js", ".ts", ".jsx", ".tsx"],
    fallback: {
      fs: false,
      net: false,
      stream: false,
    },
    alias: {
      // needed for "build" script
      "tj/react-click-outside": path.resolve(
        __dirname,
        "./node_modules/react-click-outside/build/index.js"
      ),
    },
  },
  module: {
    rules: [
      {
        test: /\.css$/i,
        use: ["style-loader", "css-loader"],
      },
      {
        test: /\.tsx?$/,
        use: "ts-loader",
        exclude: /node_modules/,
      },
      {
        // basic rule for JS, probably already part of your project
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: "babel-loader",
          options: {
            presets: ["@babel/preset-react"],
          },
        },
      },
      {
        // necessary Sass rule for CSS modules usage
        test: /\.scss$/,
        use: [
          "style-loader",
          {
            loader: "css-loader",
            options: {
              esModule: true,
              modules: true,
            },
          },
          {
            loader: "sass-loader",
            options: {
              sassOptions: {
                includePaths: ["node_modules"],
              },
            },
          },
        ],
      },
      {
        // necessary SVG rule for using Pulse icons
        test: /\.svg$/,
        use: {
          loader: "@svgr/webpack",
          options: svgrConfig,
        },
      },
    ],
  },
})
