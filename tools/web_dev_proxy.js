const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const path = require('path');

const app = express();
const PORT = process.env.PORT;
const BACKEND_URL = 'http://127.0.0.1:8080';

// Proxy /api requests to the backend
// We strip '/api' prefix when forwarding to backend if the backend doesn't expect /api
// But usually it's cleaner to keep it or strip it depending on backend.
// The user requirement says: "同源 /api 代理到后端 8080".
// Assuming backend handles /health at root?
// User said: "Health fetch to https://8080-.../health failed".
// So backend expects /health.
// If we request /api/health, we should rewrite it to /health.

app.use('/api', createProxyMiddleware({
  target: BACKEND_URL,
  changeOrigin: true,
  pathRewrite: {
    '^/api': '', // remove /api prefix
  },
  onProxyRes: (proxyRes, req, res) => {
      // Disable caching for API responses
      proxyRes.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, proxy-revalidate';
      proxyRes.headers['Pragma'] = 'no-cache';
      proxyRes.headers['Expires'] = '0';
  }
}));

// Serve static files from Flutter web build
const buildPath = path.join(__dirname, '../build/web');
app.use(express.static(buildPath));

// Fallback to index.html for SPA (though Flutter is single page usually)
app.get('*', (req, res) => {
  res.sendFile(path.join(buildPath, 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Proxy server running on port ${PORT}`);
  console.log(`Proxying /api to ${BACKEND_URL}`);
  console.log(`Serving static files from ${buildPath}`);
});
