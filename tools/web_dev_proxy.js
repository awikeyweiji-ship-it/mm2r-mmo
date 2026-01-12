const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const path = require('path');

const app = express();
const port = Number(process.env.PORT || process.env.SPORT || process.env.PREVIEW_PORT || 0);
if (!port) {
  console.error("FATAL: PORT not set");
  process.exit(2);
}
const BACKEND_URL = 'http://127.0.0.1:8080';

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

const buildPath = path.join(__dirname, '../build/web');
app.use(express.static(buildPath));

app.get('*', (req, res) => {
  res.sendFile(path.join(buildPath, 'index.html'));
});

app.listen(port, '0.0.0.0', () => {
  console.log(`Proxy server listening on 0.0.0.0:${port}`);
  console.log(`Proxying /api to ${BACKEND_URL}`);
  console.log(`Serving static files from ${buildPath}`);
});
