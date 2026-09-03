#!/bin/sh
set -eu

: "${VUE_APP_API_BASE_URL:=/jeecg-boot}"
: "${VUE_APP_CAS_BASE_URL:=}"
: "${VUE_APP_ONLINE_BASE_URL:=}"
: "${VUE_APP_VIDEO_STREAM_URL:=}"
export VUE_APP_API_BASE_URL VUE_APP_CAS_BASE_URL VUE_APP_ONLINE_BASE_URL VUE_APP_VIDEO_STREAM_URL

envsubst '${VUE_APP_API_BASE_URL} ${VUE_APP_CAS_BASE_URL} ${VUE_APP_ONLINE_BASE_URL} ${VUE_APP_VIDEO_STREAM_URL}' \
  < /opt/wgai/config.js.template \
  > /usr/share/nginx/html/static/config.js
