const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const path = require('path');
const fs = require('fs');

// AUTO-FIX: Check and kill process on port 9000 before starting
const CHECK_PORT = 9000;

function killZombieProcess() {
  try {
    const logPath = path.join(__dirname, '../logs');
    if (!fs.existsSync(logPath)) fs.mkdirSync(logPath, { recursive: true });
    
    // We can't rely on 'lsof' or 'ss' if terminal commands fail.
    // We scan /proc for node processes running this script.
    // This is Linux-specific but works in the IDX environment.
    const pids = fs.readdirSync('/proc').filter(f => /^\d+$/.test(f));
    const selfPid = process.pid;
    let killed = false;
    let logContent = '';

    for (const pid of pids) {
      if (pid === String(selfPid)) continue; // Don't kill self

      try {
        const cmdlinePath = path.join('/proc', pid, 'cmdline');
        if (!fs.existsSync(cmdlinePath)) continue;

        const cmdline = fs.readFileSync(cmdlinePath, 'utf8');
        // Look for the signature of this very script running
        // Cmdline args are null-separated
        if (cmdline.includes('node') && cmdline.includes('web_dev_proxy.js')) {
           logContent += `Found zombie process PID: ${pid}\n`;
           try {
             process.kill(parseInt(pid), 'SIGKILL');
             logContent += `Successfully killed PID: ${pid}\n`;
             killed = true;
           } catch (e) {
             logContent += `Failed to kill PID: ${pid} - ${e.message}\n`;
           }
        }
      } catch (e) {
        // Process might have vanished
      }
    }

    if (killed) {
      const logFile = path.join(logPath, `port_9000_fix_${Date.now()}.log`);
      fs.writeFileSync(logFile, logContent);
      console.log('[Auto-Fix] Zombie process killed. Log written to ' + logFile);
    }

  } catch (e) {
    console.log('[Auto-Fix] Cleanup failed or not supported:', e.message);
  }
}

// Run the cleanup before doing anything else
killZombieProcess();

const app = express();
// FIX: Fallback if PORT is not set, instead of crashing.
// Dev mode often starts without PORT. 
const port = Number(process.env.PORT || process.env.SPORT || process.env.PREVIEW_PORT || 9000);

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
  if (!process.env.PORT) {
      console.warn("WARNING: PORT was not set, defaulted to 9000. Preview might not map correctly if not expected.");
  }
  console.log(`Proxying /api to ${BACKEND_URL}`);
  console.log(`Proxying /ws to ${WS_BACKEND_URL}/ws`);
});

// Also wire up the websocket proxy to the server
server.on('upgrade', wsProxy.upgrade);
