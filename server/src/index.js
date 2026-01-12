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
const MOVE_THROTTLE_MS = 50;

// Log broadcast stats
let broadcastCounts = {};
setInterval(() => {
    for (const roomId in broadcastCounts) {
        const rate = broadcastCounts[roomId] / 10;
        console.log(`[Metrics] Room '${roomId}' broadcast rate: ${rate.toFixed(1)} Hz`);
    }
    broadcastCounts = {};
}, 10000);

wss.on('connection', (ws, req) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const roomId = url.searchParams.get('roomId') || 'poc_world';
  const playerName = url.searchParams.get('name') || `player-${Math.random().toString(36).substr(2, 4)}`;
  const playerColor = `#${Math.floor(Math.random()*16777215).toString(16).padStart(6, '0')}`;
  const playerId = `${playerName}-${Date.now()}`;

  ws.roomId = roomId;

  if (!rooms[roomId]) {
    rooms[roomId] = {
      players: {},
      tickInterval: null,
      broadcastCount: 0,
    };
    broadcastCounts[roomId] = 0;
  }

  const room = rooms[roomId];
  const lastMove = { time: 0 };
  
  room.players[playerId] = {
    id: playerId,
    name: playerName,
    color: playerColor,
    x: 300,
    y: 300,
    ts: Date.now(),
  };

  ws.send(JSON.stringify({ type: 'welcome', playerId, roomState: room.players }));

  if (!room.tickInterval) {
    room.tickInterval = setInterval(() => {
      if (Object.keys(room.players).length > 0) {
        broadcastState(roomId);
      } else {
        clearInterval(room.tickInterval);
        room.tickInterval = null;
        delete rooms[roomId];
        console.log(`Room ${roomId} is empty and closed.`);
      }
    }, 1000 / TICK_RATE);
  }

  ws.on('message', (message) => {
    try {
      const now = Date.now();
      if (now - lastMove.time < MOVE_THROTTLE_MS) return;

      const data = JSON.parse(message);
      const player = room.players[playerId];
      if (player && data.type === 'move') {
        player.x = data.x;
        player.y = data.y;
        player.ts = now;
        lastMove.time = now;
      }
    } catch (e) {
      console.error('Failed to parse message:', e);
    }
  });

  ws.on('close', () => {
    delete room.players[playerId];
    console.log(`Player ${playerId} left room ${roomId}.`);
  });
});

function broadcastState(roomId) {
  const room = rooms[roomId];
  if (!room) return;

  const state = { type: 'state', players: room.players };
  const message = JSON.stringify(state);
  
  broadcastCounts[roomId]++;

  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN && client.roomId === roomId) {
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
