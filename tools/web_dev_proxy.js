const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const path = require('path');
const fs = require('fs');

const app = express();

// STRICT PORT ENFORCEMENT
// We must use the PORT provided by the environment (IDX).
// Fallback to 9000 is explicitly forbidden to prevent port conflicts (EADDRINUSE).
const port = Number(process.env.PORT);

if (!port) {
  console.error("FATAL: PORT environment variable is not set. This script must be run within the IDX preview environment which provides a $PORT.");
  process.exit(2);
}

const BACKEND_URL = 'http://127.0.0.1:8080';
const WS_BACKEND_URL = 'ws://127.0.0.1:8080';
const RENDERER_URL = process.env.RENDERER_URL; // For Dev mode (flutter run)

// Proxy API requests
app.use('/api', createProxyMiddleware({
  target: BACKEND_URL,
  changeOrigin: true,
  pathRewrite: {
    '^/api': '', // remove /api prefix
  },
  onProxyRes: (proxyRes, req, res) => {
      proxyRes.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, proxy-revalidate';
      proxyRes.headers['Pragma'] = 'no-cache';
      proxyRes.headers['Expires'] = '0';
  }
}));

// Proxy WebSocket connections
const wsProxy = createProxyMiddleware({
  target: WS_BACKEND_URL,
  ws: true, // IMPORTANT: enable WebSocket proxying
  changeOrigin: true,
  pathRewrite: {
    '^/ws': '/ws', // proxy /ws to ws://.../ws
  },
});
app.use('/ws', wsProxy);

if (RENDERER_URL) {
  console.log(`DEV MODE: Proxying frontend to ${RENDERER_URL}`);
  // In dev mode, proxy everything else to the flutter dev server
  app.use('/', createProxyMiddleware({
    target: RENDERER_URL,
    changeOrigin: true,
    ws: true, // Support flutter's own WS if needed
    filter: (pathname, req) => {
      return !pathname.startsWith('/api') && !pathname.startsWith('/ws');
    }
  }));
} else {
  console.log(`RELEASE MODE: Serving static files`);
  const buildPath = path.join(__dirname, '../build/web');
  app.use(express.static(buildPath));
  app.get('*', (req, res) => {
    res.sendFile(path.join(buildPath, 'index.html'));
  });
}

const server = app.listen(port, '0.0.0.0', () => {
  console.log(`Proxy server listening on 0.0.0.0:${port}`);
  console.log(`Proxying /api to ${BACKEND_URL}`);
  console.log(`Proxying /ws to ${WS_BACKEND_URL}/ws`);
});

// Also wire up the websocket proxy to the server
server.on('upgrade', wsProxy.upgrade);
