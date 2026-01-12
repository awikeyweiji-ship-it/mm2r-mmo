const WebSocket = require('ws');

const BASE_URL = 'ws://127.0.0.1:8080/ws';
const ROOM_ID = `test_room_${Date.now()}`;

console.log(`Using room: ${ROOM_ID}`);

const clientA = new WebSocket(`${BASE_URL}?roomId=${ROOM_ID}&playerName=ClientA`);
const clientB = new WebSocket(`${BASE_URL}?roomId=${ROOM_ID}&playerName=ClientB`);

let receivedStateCount = 0;
let playerAId = null;

clientA.on('open', () => {
  console.log('Client A connected.');
  // Start moving once connected
  setInterval(() => {
    const move = {
      type: 'move',
      x: Math.random() * 600,
      y: Math.random() * 600,
    };
    clientA.send(JSON.stringify(move));
  }, 500);
});

clientA.on('message', (message) => {
    const data = JSON.parse(message);
    if (data.type === 'welcome') {
        playerAId = data.playerId;
        console.log(`Client A received welcome. Player ID: ${playerAId}`);
    }
});

clientB.on('open', () => {
  console.log('Client B connected.');
});

clientB.on('message', (message) => {
  const data = JSON.parse(message);

  if (data.type === 'state' && playerAId) {
    const playerAState = data.players[playerAId];
    if (playerAState) {
      console.log(`Client B received state update for Client A: (x=${playerAState.x.toFixed(2)}, y=${playerAState.y.toFixed(2)})`);
      receivedStateCount++;
      if (receivedStateCount >= 3) {
        console.log('\n✅ SUCCESS: Client B received 3 updates from Client A.');
        process.exit(0);
      }
    }
  }
});

clientA.on('error', (err) => console.error('Client A error:', err));
clientB.on('error', (err) => console.error('Client B error:', err));

setTimeout(() => {
  console.error('\n⚠️ FAILURE: Test timed out after 15 seconds.');
  process.exit(1);
}, 15000);
