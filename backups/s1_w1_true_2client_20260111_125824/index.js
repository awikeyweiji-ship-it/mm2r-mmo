const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = a => (a.res.setHeader('Access-Control-Allow-Origin', '*'),a.next());

const app = express();
app.use(express.json());
app.use(cors);

const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const rooms = {}; // In-memory store for rooms
const TICK_RATE = 15; // Hz

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({ 
    status: 'ok', 
    ws_server: 'running',
    active_rooms: Object.keys(rooms).length,
    players_in_rooms: Object.values(rooms).map(r => Object.keys(r.players).length)
  });
});

wss.on('connection', (ws, req) => {
  // Extract player info from URL, e.g., /ws?roomId=world&playerName=guest&playerColor=ff0000
  const url = new URL(req.url, `http://${req.headers.host}`);
  const roomId = url.searchParams.get('roomId') || 'poc_world';
  const playerId = `player_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
  const playerName = url.searchParams.get('playerName') || `Guest_${playerId.substr(-3)}`;
  const playerColor = `#${url.searchParams.get('playerColor') || '333333'}`;

  // Create room if it doesn't exist
  if (!rooms[roomId]) {
    rooms[roomId] = { players: {} };
    console.log(`Room [${roomId}] created.`);
  }

  const room = rooms[roomId];
  room.players[playerId] = { id: playerId, name: playerName, color: playerColor, x: 50, y: 50, ts: Date.now() };
  ws.roomId = roomId;
  ws.playerId = playerId;

  console.log(`Player [${playerName}] (${playerId}) connected to room [${roomId}].`);

  // Send a welcome message with the player's ID and current room state
  ws.send(JSON.stringify({ type: 'welcome', playerId, roomState: room.players }));

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      const player = room.players[playerId];

      if (data.type === 'move' && player) {
        // Just update the state, don't broadcast immediately
        player.x = data.x;
        player.y = data.y;
        player.ts = Date.now();
      }
    } catch (e) {
      console.error('Failed to process message:', message, e);
    }
  });

  ws.on('close', () => {
    if (ws.roomId && ws.playerId && rooms[ws.roomId]) {
      delete rooms[ws.roomId].players[ws.playerId];
      console.log(`Player [${playerName}] (${ws.playerId}) disconnected from room [${ws.roomId}].`);
      // Clean up empty room
      if (Object.keys(rooms[ws.roomId].players).length === 0) {
        delete rooms[ws.roomId];
        console.log(`Room [${ws.roomId}] is empty and has been removed.`);
      }
    }
  });
});

// Game loop for broadcasting state
setInterval(() => {
  for (const roomId in rooms) {
    const room = rooms[roomId];
    const statePayload = JSON.stringify({ type: 'state', players: room.players });
    
    wss.clients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN && client.roomId === roomId) {
        client.send(statePayload);
      }
    });
  }
}, 1000 / TICK_RATE);

const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log(`Server is listening on port ${PORT}`);
});
