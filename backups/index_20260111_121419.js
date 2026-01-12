const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const crypto = require('crypto');

const app = express();
const port = process.env.PORT || 8080;

app.get('/health', (req, res) => {
  res.json({
    ok: true,
    service: 'gateway',
    ts: new Date().toISOString(),
    ws_enabled: true
  });
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

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
    players.delete(clientId);
  });
});

server.listen(port, () => {
  console.log(`Gateway service (HTTP+WS) listening on port ${port}`);
});
