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
          if (!roomId && persistedState.roomId) roomId = persistedState.roomId;
          if (!nameParam && persistedState.name) nameParam = persistedState.name;
      }
  }

  // Defaults
  roomId = roomId || 'poc_world';
  if (!playerKey) {
      playerKey = `pk-${Math.random().toString(36).substr(2, 8)}`;
  }
  
  const playerId = playerKey; 
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
      objects: [], // pickups, npcs
      tickInterval: null,
      lastSnapshotTime: 0,
      objectRemoves: [] // Track removed objects for delta
    };
    
    // Init default world objects for this room
    // S4: 1 Pickup, 1 NPC
    rooms[roomId].objects.push({ id: 'pickup_1', type: 'pickup', x: 400, y: 400, active: true });
    rooms[roomId].objects.push({ id: 'npc_1', type: 'npc', x: 600, y: 600 });

    console.log(`Created room: ${roomId} with objects`);
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
  // Include objects in snapshot (only active ones)
  const activeObjects = room.objects.filter(o => o.active !== false);

  ws.send(JSON.stringify({ 
      type: 'snapshot', 
      proto: 3, // Bump proto version for objects support
      roomId,
      you: playerId,
      playerKey, 
      players: room.players,
      objects: activeObjects,
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
            if (false && dist > MAX_SPEED * 5) {
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
            
            // Interaction Check (Server-side authoritative collision)
            // Check collision with ACTIVE pickups
            room.objects.forEach(obj => {
                if (obj.type === 'pickup' && obj.active) {
                    const dx = player.x - obj.x;
                    const dy = player.y - obj.y;
                    // Simple distance check (e.g., < 50 units)
                    if (Math.sqrt(dx*dx + dy*dy) < 50) {
                        console.log(`Player ${player.id} picked up ${obj.id}`);
                        obj.active = false;
                        if (!room.objectRemoves) room.objectRemoves = [];
                        room.objectRemoves.push(obj.id);
                        
                        // Notify this player specifically they got it? 
                        // Or just let client see it disappear. 
                        // Ideally we send an 'event' packet, but for now visual disappear is enough S4 requirement.
                    }
                }
            });

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
  const objRemoves = room.objectRemoves || [];

  if (upserts.length === 0 && removes.length === 0 && objRemoves.length === 0 && !isSnapshotTick) {
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
            const activeObjects = room.objects.filter(o => o.active !== false); // Send full list occasionally
            msg = JSON.stringify({
                type: 'snapshot',
                proto: 3,
                ts: now,
                players: allNearby.reduce((acc, p) => { acc[p.id] = p; return acc; }, {}),
                objects: activeObjects
            });
        } else {
             if (visibleUpserts.length > 0 || removes.length > 0 || objRemoves.length > 0) {
                 msg = JSON.stringify({
                    type: 'delta',
                    proto: 3,
                    ts: now,
                    upserts: visibleUpserts,
                    removes: removes,
                    objRemoves: objRemoves // Broadcast object disappearances
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
  room.objectRemoves = []; // Clear object removes after broadcast
  if (isSnapshotTick) room.lastSnapshotTime = now;
}

const HOST = process.env.HOST || '0.0.0.0';
const PORT = process.env.PORT || 8080;

server.listen(PORT, HOST, () => {
  console.log(`Server is listening on ${HOST}:${PORT}`);
});
