'use strict';

// Keep the downloaded upstream sources intact. Only the local listener is configured here.
const protocol = require('minecraft-protocol');
const createServer = protocol.createServer;
protocol.createServer = function (options) {
  const server = createServer.call(this, { ...options, host: '127.0.0.1', port: 0 });
  server.once('listening', () => {
    console.log('[VibeMiniblox:' + process.env.VIBE_BRIDGE_NONCE + ']:' + server.socketServer.address().port);
  });
  return server;
};

// The Java parent owns this process. EOF also covers a killed/crashed game process.
process.stdin.resume();
process.stdin.on('end', () => process.exit(0));
require('./index.js');
