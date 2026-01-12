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
const SNAPSHOT_INTERVAL_MS = 3000;
const CELL_SIZE = 200; // AOI cell size
const MAX_SPEED = 20; // Pixels per tick (approx) - generous for lag
const WORLD_WIDTH = 5000;
const WORLD_HEIGHT = 5000;

// Metrics
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
  const roomId = url.searchParams.get('roomId') || 'poc_world';
  const nameParam = url.searchParams.get('name');
  
  const randId = Math.random().toString(36).substr(2, 6);
  const playerId = `${nameParam || 'player'}-${randId}`;
  const playerName = nameParam || `Player ${randId}`;
  const playerColor = `#${Math.floor(Math.random()*16777215).toString(16).padStart(6, '0')}`;

  ws.roomId = roomId;
  ws.playerId = playerId;

  if (!rooms[roomId]) {
    rooms[roomId] = {
      players: {},
      cells: {}, // cellKey -> [playerId]
      tickInterval: null,
      lastSnapshotTime: 0
    };
    
    console.log(`Created room: ${roomId}`);
    rooms[roomId].tickInterval = setInterval(() => {
        tickRoom(roomId);
    }, 1000 / TICK_RATE);
  }

  const room = rooms[roomId];
  const lastMove = { time: 0, x: Math.random() * 300 + 50, y: Math.random() * 300 + 50 };
  
  // Init player
  room.players[playerId] = {
    id: playerId,
    name: playerName,
    color: playerColor,
    x: lastMove.x,
    y: lastMove.y,
    cell: getCellKey(lastMove.x, lastMove.y),
    ts: Date.now(),
    dirty: true // Mark for delta sync
  };
  
  addToCell(room, playerId, room.players[playerId].cell);

  // Send initial snapshot
  ws.send(JSON.stringify({ 
      type: 'snapshot', 
      proto: 2,
      roomId,
      you: playerId,
      players: room.players,
      ts: Date.now()
  }));

  if (playerName.startsWith('Bot-')) metrics.botCount++;

  ws.on('message', (message) => {
    try {
      const now = Date.now();
      
      // 1. Frequency Check
      if (now - lastMove.time < MOVE_THROTTLE_MS) {
          // metrics.violationCount++; // Too strict for some clients
          return;
      }

      const data = JSON.parse(message);
      
      if (data.type === 'move') {
        const player = room.players[playerId];
        if (player) {
            const newX = parseFloat(data.x);
            const newY = parseFloat(data.y);

            // 2. Bound Check
            if (newX < 0 || newX > WORLD_WIDTH || newY < 0 || newY > WORLD_HEIGHT) {
                metrics.violationCount++;
                return; 
            }

            // 3. Speed Check (Simple distance check)
            const dist = Math.sqrt(Math.pow(newX - player.x, 2) + Math.pow(newY - player.y, 2));
            if (dist > MAX_SPEED * 5) { // *5 buffer for lag spikes/jitters
                metrics.violationCount++;
                return; // Teleport hacking?
            }

            // AOI Update
            const newCell = getCellKey(newX, newY);
            if (newCell !== player.cell) {
                removeFromCell(room, playerId, player.cell);
                addToCell(room, playerId, newCell);
                player.cell = newCell;
            }

            // Update state
            player.x = newX;
            player.y = newY;
            player.ts = now;
            player.dirty = true;
            
            lastMove.time = now;
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
        
        // Mark as removed for delta sync (we keep it briefly or handle via explicit 'removes' list in tick)
        // For simplicity in this loop, we just delete and rely on periodic snapshot or client timeout
        // Better: add to a 'removed' list for one tick.
        if (!rooms[roomId].removedPlayers) rooms[roomId].removedPlayers = [];
        rooms[roomId].removedPlayers.push(playerId);
        
        delete rooms[roomId].players[playerId];
        
        if (playerName.startsWith('Bot-')) metrics.botCount--;

        // If room empty
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
  
  // Gather changed players (upserts)
  const upserts = [];
  Object.values(room.players).forEach(p => {
      if (p.dirty || isSnapshotTick) {
          upserts.push(p);
          // Don't clear dirty yet, need to send to everyone relevant
      }
  });

  const removes = room.removedPlayers || [];

  // If nothing happened and not snapshot time, skip broadcast
  if (upserts.length === 0 && removes.length === 0 && !isSnapshotTick) {
      return; 
  }

  metrics.broadcastCount++;

  // Broadcast
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN && client.roomId === roomId) {
        
        // Find relevant players for this client (AOI)
        const myPlayer = room.players[client.playerId];
        
        if (!myPlayer) return; // Should not happen if sync

        // AOI Filtering
        // Get all players in 3x3 grid around me
        const nearbyPids = getNearbyPlayers(room, myPlayer.cell);
        
        // Filter upserts to only those nearby
        const visibleUpserts = upserts.filter(p => nearbyPids.includes(p.id));
        
        // We also need to send updates for players that might have JUST left my AOI? 
        // For simplicity: We trust client to interpolation/timeout, OR we send 'removes' for those who left AOI.
        // Current Step 2 req: "九宫格" simple.
        
        // Construct Message
        let msg = null;
        if (isSnapshotTick) {
            // Snapshot sends ALL nearby players, ignoring dirty flag
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

  // Cleanup Dirty Flags & Removes
  Object.values(room.players).forEach(p => p.dirty = false);
  room.removedPlayers = [];
  
  if (isSnapshotTick) room.lastSnapshotTime = now;
}

const HOST = process.env.HOST || '0.0.0.0';
const PORT = process.env.PORT || 8080;

server.listen(PORT, HOST, () => {
  console.log(`Server is listening on ${HOST}:${PORT}`);
});
