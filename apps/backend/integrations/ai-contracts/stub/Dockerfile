ARG NODE_IMAGE=node:20.19.4-alpine3.22
FROM ${NODE_IMAGE}
WORKDIR /opt/wgai-provider-stub
COPY integrations/ai-contracts/stub/ ./
COPY integrations/ai-contracts/examples/ ../examples/
USER node
EXPOSE 18080
CMD ["node", "index.cjs"]
