ARG NODE_IMAGE=node:16.20.2-bullseye
ARG NGINX_IMAGE=nginx:1.26.3-alpine

FROM ${NODE_IMAGE} AS build
ENV NODE_OPTIONS=--max_old_space_size=4096
WORKDIR /workspace/apps/frontend
COPY apps/frontend/package.json apps/frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --legacy-peer-deps --no-audit --no-fund
COPY apps/frontend/ ./
RUN npm run build

FROM ${NGINX_IMAGE} AS runtime
COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/frontend/dist/ /usr/share/nginx/html/
COPY deploy/frontend/config.js.template /opt/wgai/config.js.template
COPY deploy/frontend/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN chmod 0755 /docker-entrypoint.d/40-runtime-config.sh
EXPOSE 80
