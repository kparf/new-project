ARG BASE_IMAGE=node:16.18-alpine
ARG CACHE_IMAGE=${BASE_IMAGE}

FROM ${CACHE_IMAGE} AS cache
WORKDIR /app

COPY package.json yarn.lock .npmrc /cache/

ARG BUILD_CACHE_MODE
RUN if [ "$BUILD_CACHE_MODE" = "1" ] ; then cd /cache && yarn install ; else echo "skip yarn install - using cache" ; fi
COPY . /app/

RUN if [ "$BUILD_CACHE_MODE" = "1" ] ; then rm -rf /app /cache/.npmrc && echo "removing sources for cache image" ; else  ln -s /cache/node_modules node_modules && echo "keeping sources for build image" ; fi