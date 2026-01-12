const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const persistence = require('./persistence');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// Initialize Persistence
persistence.loadState();

app.use(cors({ origin: true }));
app.options('*', cors());

app.get('/health', (req, res) => {
  res.status(200).json({ ok: true, timestamp: new Date().toISOString() });
});

const rooms = {};
const TICK_RATE = 15; // 15Hz
const MOVE_THROTTLE_MS = 50;
const SNAPSHOT_INTERVAL_MS = 3000;
const CELL_SIZE = 200; // AOI cell size
const MAX_SPEED = 20; 
const WORLD_WIDTH = 5000;
const WORLD_HEIGHT = 5000;

let metrics = {
    broadcastCount: 0,
    violationCount: 0,
    botCount: 0
};

setInterval(() => {
    if (metrics.broadcastCount > 0 || metrics.violationCount > 0) {
        console.log(`[Metrics 10s] Rate: ${(metrics.broadcastCount/10).toFixed(1)} Hz | Violations: ${metrics.violationCount} | Bots: ${metrics.botCount}`);
        metrics.broadcastCount = 0;
    }
}, 10000);

wss.on('connection', (ws, req) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  
  // 1. Get client params
  let playerKey = url.searchParams.get('playerKey');
  let roomId = url.searchParams.get('roomId');
  let nameParam = url.searchParams.get('name');
  
  // 2. Load from persistence if available
  let persistedState = null;
  if (playerKey) {
      persistedState = persistence.getPlayerState(playerKey);
      if (persistedState) {
          // Restore if not overridden by strong params? 
          // Logic: If client sends roomId, maybe they want to switch rooms? 
          // For now: "Resume" logic implies stick to last room if not specified, 
          // OR if 'auto-resume' is desired.
          // Let's say: if client provides roomId, use it. Else use persisted.
          if (!roomId && persistedState.roomId) roomId = persistedState.roomId;
          if (!nameParam && persistedState.name) nameParam = persistedState.name;
          // Coordinates restored below
      }
  }

  // Defaults
  roomId = roomId || 'poc_world';
  if (!playerKey) {
      // Generate one if missing (though client should ideally store it)
      playerKey = `pk-${Math.random().toString(36).substr(2, 8)}`;
  }
  
  const playerId = playerKey; // Use playerKey as runtime ID for simplicity in this model
  const playerName = nameParam || `Player ${playerId.substr(0,4)}`;
  const playerColor = (persistedState && persistedState.color) 
                      ? persistedState.color 
                      : `#${Math.floor(Math.random()*16777215).toString(16).padStart(6, '0')}`;

  ws.roomId = roomId;
  ws.playerId = playerId;
  ws.playerKey = playerKey;

  // 3. Create/Join Room
  if (!rooms[roomId]) {
    rooms[roomId] = {
      players: {},
      cells: {}, 
      tickInterval: null,
      lastSnapshotTime: 0
    };
    
    console.log(`Created room: ${roomId}`);
    rooms[roomId].tickInterval = setInterval(() => {
        tickRoom(roomId);
    }, 1000 / TICK_RATE);
  }

  const room = rooms[roomId];
  
  // 4. Determine Spawn Position
  let startX = Math.random() * 300 + 50;
  let startY = Math.random() * 300 + 50;
  
  if (persistedState && persistedState.x !== undefined && persistedState.y !== undefined) {
      startX = persistedState.x;
      startY = persistedState.y;
      // console.log(`Restored ${playerName} to ${startX},${startY}`);
  }
  
  const lastMove = { time: 0, x: startX, y: startY };
  
  // 5. Init Player Runtime State
  room.players[playerId] = {
    id: playerId,
    name: playerName,
    color: playerColor,
    x: startX,
    y: startY,
    cell: getCellKey(startX, startY),
    ts: Date.now(),
    dirty: true 
  };
  
  // Save initial state to persistence (ensure record exists)
  persistence.updatePlayerState(playerKey, {
      roomId,
      name: playerName,
      color: playerColor,
      x: startX,
      y: startY,
      ts: Date.now()
  });

  addToCell(room, playerId, room.players[playerId].cell);

  // 6. Send Snapshot
  ws.send(JSON.stringify({ 
      type: 'snapshot', 
      proto: 2,
      roomId,
      you: playerId,
      playerKey, // Send back so client can store it if they generated it? 
                 // Actually client usually sends it. But helpful for debug.
      players: room.players,
      ts: Date.now()
  }));

  if (playerName.startsWith('Bot-')) metrics.botCount++;

  ws.on('message', (message) => {
    try {
      const now = Date.now();
      if (now - lastMove.time < MOVE_THROTTLE_MS) return;

      const data = JSON.parse(message);
      
      if (data.type === 'move') {
        const player = room.players[playerId];
        if (player) {
            const newX = parseFloat(data.x);
            const newY = parseFloat(data.y);

            if (newX < 0 || newX > WORLD_WIDTH || newY < 0 || newY > WORLD_HEIGHT) {
                metrics.violationCount++;
                return; 
            }
            const dist = Math.sqrt(Math.pow(newX - player.x, 2) + Math.pow(newY - player.y, 2));
            if (dist > MAX_SPEED * 5) {
                metrics.violationCount++;
                return; 
            }

            const newCell = getCellKey(newX, newY);
            if (newCell !== player.cell) {
                removeFromCell(room, playerId, player.cell);
                addToCell(room, playerId, newCell);
                player.cell = newCell;
            }

            player.x = newX;
            player.y = newY;
            player.ts = now;
            player.dirty = true;
            
            lastMove.time = now;
            
            // Persist (throttled)
            persistence.updatePlayerState(playerKey, {
                x: newX,
                y: newY,
                ts: now
            });
        }
      }
    } catch (e) {
      console.error('Failed to parse message:', e);
    }
  });

  ws.on('close', () => {
    if (rooms[roomId] && rooms[roomId].players[playerId]) {
        const p = rooms[roomId].players[playerId];
        removeFromCell(room, playerId, p.cell);
        
        if (!rooms[roomId].removedPlayers) rooms[roomId].removedPlayers = [];
        rooms[roomId].removedPlayers.push(playerId);
        
        delete rooms[roomId].players[playerId];
        
        if (playerName.startsWith('Bot-')) metrics.botCount--;

        if (Object.keys(rooms[roomId].players).length === 0) {
            clearInterval(rooms[roomId].tickInterval);
            delete rooms[roomId];
            console.log(`Room ${roomId} destroyed.`);
        }
    }
  });
});

function getCellKey(x, y) {
    const cx = Math.floor(x / CELL_SIZE);
    const cy = Math.floor(y / CELL_SIZE);
    return `${cx},${cy}`;
}

function addToCell(room, pid, key) {
    if (!room.cells[key]) room.cells[key] = [];
    room.cells[key].push(pid);
}

function removeFromCell(room, pid, key) {
    if (!room.cells[key]) return;
    room.cells[key] = room.cells[key].filter(id => id !== pid);
    if (room.cells[key].length === 0) delete room.cells[key];
}

function getNearbyPlayers(room, cellKey) {
    if (!cellKey) return [];
    const [cx, cy] = cellKey.split(',').map(Number);
    let pids = [];
    for (let x = cx - 1; x <= cx + 1; x++) {
        for (let y = cy - 1; y <= cy + 1; y++) {
            const k = `${x},${y}`;
            if (room.cells[k]) {
                pids = pids.concat(room.cells[k]);
            }
        }
    }
    return pids;
}

function tickRoom(roomId) {
  const room = rooms[roomId];
  if (!room) return;

  const now = Date.now();
  const isSnapshotTick = (now - room.lastSnapshotTime > SNAPSHOT_INTERVAL_MS);
  
  const upserts = [];
  Object.values(room.players).forEach(p => {
      if (p.dirty || isSnapshotTick) {
          upserts.push(p);
      }
  });

  const removes = room.removedPlayers || [];

  if (upserts.length === 0 && removes.length === 0 && !isSnapshotTick) {
      return; 
  }

  metrics.broadcastCount++;

  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN && client.roomId === roomId) {
        const myPlayer = room.players[client.playerId];
        if (!myPlayer) return; 

        const nearbyPids = getNearbyPlayers(room, myPlayer.cell);
        const visibleUpserts = upserts.filter(p => nearbyPids.includes(p.id));
        
        let msg = null;
        if (isSnapshotTick) {
            const allNearby = nearbyPids.map(pid => room.players[pid]).filter(p => p);
            msg = JSON.stringify({
                type: 'snapshot',
                proto: 2,
                ts: now,
                players: allNearby.reduce((acc, p) => { acc[p.id] = p; return acc; }, {}) 
            });
        } else {
             if (visibleUpserts.length > 0 || removes.length > 0) {
                 msg = JSON.stringify({
                    type: 'delta',
                    proto: 2,
                    ts: now,
                    upserts: visibleUpserts,
                    removes: removes
                 });
             }
        }

        if (msg) {
            client.send(msg, (err) => {});
        }
    }
  });

  Object.values(room.players).forEach(p => p.dirty = false);
  room.removedPlayers = [];
  if (isSnapshotTick) room.lastSnapshotTime = now;
}

const HOST = process.env.HOST || '0.0.0.0';
const PORT = process.env.PORT || 8080;

server.listen(PORT, HOST, () => {
  console.log(`Server is listening on ${HOST}:${PORT}`);
});
