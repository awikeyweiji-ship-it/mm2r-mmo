const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

app.use(cors({ origin: true }));
app.options('*', cors());

app.get('/health', (req, res) => {
  res.status(200).json({ ok: true, timestamp: new Date().toISOString() });
});

const rooms = {};
const TICK_RATE = 15; // 15Hz

wss.on('connection', (ws, req) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const roomId = url.searchParams.get('roomId') || 'default';
  const playerId = `player_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
  const playerName = url.searchParams.get('playerName') || 'Guest';
  const playerColor = `#${url.searchParams.get('playerColor') || 'FF0000'}`;

  if (!rooms[roomId]) {
    rooms[roomId] = { players: {}, tickInterval: null };
  }

  const room = rooms[roomId];
  room.players[playerId] = { 
    id: playerId, 
    name: playerName,
    color: playerColor,
    x: 300, y: 300, ts: Date.now() 
  };

  ws.send(JSON.stringify({ type: 'welcome', playerId, roomState: room.players }));

  if (!room.tickInterval) {
    room.tickInterval = setInterval(() => {
      if (Object.keys(room.players).length > 0) {
        broadcastState(roomId);
      } else {
        clearInterval(room.tickInterval);
        room.tickInterval = null;
      }
    }, 1000 / TICK_RATE);
  }

  ws.on('message', (message) => {
    try {
        const data = JSON.parse(message);
        const player = room.players[playerId];
        if (player && data.type === 'move') {
          player.x = data.x;
          player.y = data.y;
          player.ts = Date.now();
        }
    } catch (e) {
        console.error('Failed to parse message:', e);
    }
  });

  ws.on('close', () => {
    delete room.players[playerId];
    if (Object.keys(room.players).length === 0) {
      console.log(`Room ${roomId} is now empty.`);
      if (room.tickInterval) {
          clearInterval(room.tickInterval);
          room.tickInterval = null;
      } 
    }
  });
});

function broadcastState(roomId) {
    const room = rooms[roomId];
    if (!room) return;

    const state = { type: 'state', players: room.players };
    const message = JSON.stringify(state);

    wss.clients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) {
          client.send(message, (error) => {
              if (error) {
                  console.error('Send error:', error);
              }
          });
      }
    });
}

const HOST = process.env.HOST || '0.0.0.0';
const PORT = process.env.PORT || 8080;

server.listen(PORT, HOST, () => {
  console.log(`Server is listening on ${HOST}:${PORT}`);
});
