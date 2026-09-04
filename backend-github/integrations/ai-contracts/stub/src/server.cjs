'use strict';

const http = require('node:http');
const { loadConfig } = require('./config.cjs');
const { createRouter } = require('./routes.cjs');
const { StubState } = require('./state.cjs');

function createStubServer(options = {}) {
  const config = loadConfig({ ...process.env, ...options });
  const state = new StubState();
  const server = http.createServer(createRouter(config, state));
  server.on('clientError', (_error, socket) => socket.destroy());
  return { server, config, state };
}

function listen(options = {}) {
  const instance = createStubServer(options);
  instance.server.listen(instance.config.port, instance.config.host, () => {
    const address = instance.server.address();
    process.stdout.write(JSON.stringify({ event: 'wgai-provider-stub-listening', simulated: true,
      host: instance.config.host, port: address.port, providerVersion: 'stub-simulated-v1' }) + '\n');
  });
  return instance;
}

module.exports = { createStubServer, listen };
