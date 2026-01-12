const WebSocket = require('ws');

const ROOM_ID = 'sim_room_123';
const BASE_URL = 'ws://127.0.0.1:8080';

const clientA = new WebSocket(`${BASE_URL}/ws?roomId=${ROOM_ID}&playerName=TesterA`);
const clientB = new WebSocket(`${BASE_URL}/ws?roomId=${ROOM_ID}&playerName=TesterB`);

let clientA_id = null;
let testSuccess = false;

const timeout = setTimeout(() => {
  console.error('TEST FAILED: Timeout reached. Did not receive expected state update.');
  process.exit(1);
}, 5000);

clientA.on('open', () => {
  console.log('[ClientA] Connected.');
});

clientA.on('message', (message) => {
  const data = JSON.parse(message);
  if (data.type === 'welcome') {
    clientA_id = data.playerId;
    console.log(`[ClientA] Welcomed with ID: ${clientA_id}`);
    // Now that we are in the room, send a move event
    clientA.send(JSON.stringify({ type: 'move', x: 123, y: 456 }));
    console.log('[ClientA] Sent move event.');
  }
});

clientB.on('open', () => {
  console.log('[ClientB] Connected.');
});

clientB.on('message', (message) => {
  const data = JSON.parse(message);
  
  if (data.type === 'state' && clientA_id) {
    const playerAState = data.players[clientA_id];
    if (playerAState) {
        console.log(`[ClientB] Received state update for ClientA: x=${playerAState.x}, y=${playerAState.y}`);
        if (playerAState.x === 123 && playerAState.y === 456) {
            console.log('TEST PASSED: ClientB received correct state from ClientA.');
            testSuccess = true;
            clearTimeout(timeout);
            process.exit(0);
        }
    }
  }
});

clientA.on('error', (err) => console.error('[ClientA] Error:', err.message));
clientB.on('error', (err) => console.error('[ClientB] Error:', err.message));

process.on('exit', (code) => {
    clientA.close();
    clientB.close();
    if (code === 0 && testSuccess) {
        console.log('Dual client simulation finished successfully.');
    } else {
        console.error(`Dual client simulation failed with code ${code}.`);
    }
});
