
const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const path = require('path');
const http = require('http');

const app = express();

const port = Number(process.env.PORT);
if (!port) {
  console.error("FATAL: PORT environment variable is not set.");
  process.exit(2);
}

const BACKEND_URL = 'http://127.0.0.1:8080';
const WS_BACKEND_URL = 'ws://127.0.0.1:8080';
const RENDERER_URL = process.env.RENDERER_URL;

let isBackendReady = false;

const checkBackendReady = () => {
  return new Promise((resolve) => {
    const poll = setInterval(() => {
      http.get(`${BACKEND_URL}/health`, (res) => {
        if (res.statusCode === 200) {
          console.log('Backend is ready!');
          isBackendReady = true;
          clearInterval(poll);
          resolve(true);
        } else {
          console.log('Waiting for backend...');
        }
      }).on('error', (err) => {
        console.log('Waiting for backend...');
      });
    }, 1000);

    setTimeout(() => {
      if (!isBackendReady) {
        clearInterval(poll);
        console.error('Backend did not become ready in 15 seconds.');
        resolve(false); // Resolve, but don't start the proxy
      }
    }, 15000);
  });
};

// Middleware to handle requests before backend is ready
app.use((req, res, next) => {
  if (isBackendReady) {
    next();
    return;
  }

  if (req.path.startsWith('/api/health')) {
    res.status(503).send('backend not ready');
  } else if (req.path.startsWith('/ws')) {
    res.status(503).send('backend not ready');
  }
  else {
    next();
  }
});


// Proxy API requests
app.use('/api', createProxyMiddleware({
  target: BACKEND_URL,
  changeOrigin: true,
  timeout: 30000,
  proxyTimeout: 30000,
  pathRewrite: {
    '^/api': '',
  },
  onError: (err, req, res) => {
    res.writeHead(500, {
      'Content-Type': 'application/json'
    });
    res.end(JSON.stringify({ok: false, error: "proxy_error", detail: err.message}));
  },
  onProxyRes: (proxyRes, req, res) => {
      proxyRes.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, proxy-revalidate';
      proxyRes.headers['Pragma'] = 'no-cache';
      proxyRes.headers['Expires'] = '0';
  }
}));

// Proxy WebSocket connections
const wsProxy = createProxyMiddleware('/ws', {
  target: WS_BACKEND_URL,
  ws: true,
  changeOrigin: true,
  timeout: 30000,
  proxyTimeout: 30000,
});
app.use('/ws', wsProxy);

if (RENDERER_URL) {
  console.log(`DEV MODE: Proxying frontend to ${RENDERER_URL}`);
  app.use('/', createProxyMiddleware({
    target: RENDERER_URL,
    changeOrigin: true,
    ws: true,
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

const server = app.listen(port, '0.0.0.0', async () => {
  console.log(`Proxy server ready and listening on 0.0.0.0:${port}`);
  await checkBackendReady();
  console.log(`Proxying /api to ${BACKEND_URL}`);
  console.log(`Proxying /ws to ${WS_BACKEND_URL}/ws`);
});

server.on('upgrade', (req, socket, head) => {
  if (isBackendReady) {
    wsProxy.upgrade(req, socket, head);
  } else {
    console.log('WS upgrade rejected: Backend not ready.');
    socket.destroy();
  }
});
