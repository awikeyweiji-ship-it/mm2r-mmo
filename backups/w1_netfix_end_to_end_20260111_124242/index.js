const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const crypto = require('crypto');

const app = express();
const port = process.env.PORT || 8080;

// Enable CORS for all
app.use((req, res, next) => {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
  next();
});

app.get('/health', (req, res) => {
  res.json({
    ok: true,
    service: 'gateway',
    ts: new Date().toISOString(),
    ws_enabled: true,
    ws_path: '/ws'
  });
});

const server = http.createServer(app);

// Mount WS on /ws path ONLY
const wss = new WebSocket.Server({ server, path: '/ws' });

const players = new Map(); // clientId -> { x, y, room, lastPing }

function broadcastState() {
  const state = {
    type: 'state',
    players: Array.from(players.entries()).map(([id, p]) => ({
      clientId: id,
      x: p.x,
      y: p.y
    }))
  };
  const msg = JSON.stringify(state);
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(msg);
    }
  });
}

// Throttled broadcast loop (100ms)
setInterval(broadcastState, 100);

wss.on('connection', (ws) => {
  const clientId = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2);
  console.log(`Client connected: ${clientId}`);
  
  ws.send(JSON.stringify({ type: 'welcome', clientId }));
  
  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      if (data.type === 'join') {
        players.set(clientId, { x: 0, y: 0, room: data.room, updatedAt: Date.now() });
      } else if (data.type === 'move') {
        const p = players.get(clientId);
        if (p) {
          p.x = data.x;
          p.y = data.y;
          p.updatedAt = Date.now();
        }
      }
    } catch (e) {
      // ignore bad json
    }
  });

  ws.on('close', () => {
    console.log(`Client disconnected: ${clientId}`);
    players.delete(clientId);
  });
});

// Listen on 0.0.0.0 for external access (essential for IDX/Docker)
server.listen(port, '0.0.0.0', () => {
  console.log(`Gateway service listening on 0.0.0.0:${port}`);
  console.log(`- HTTP: /health`);
  console.log(`- WS:   /ws`);
});
