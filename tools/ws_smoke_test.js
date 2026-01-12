const WebSocket = require('ws');

const ws = new WebSocket('ws://127.0.0.1:8080/ws?roomId=smoke_test&playerName=tester');

ws.on('open', function open() {
  console.log('Smoke Test: WebSocket connection opened.');
  // Send a move to prevent being idle
  ws.send(JSON.stringify({ type: 'move', x: 1, y: 1 }));
});

ws.on('message', function incoming(data) {
  const message = JSON.parse(data);
  console.log('Smoke Test: Received message type:', message.type);
  // If we get a welcome message, the test is successful
  if (message.type === 'welcome') {
    console.log('Smoke Test: SUCCESS - Received welcome message.');
    ws.close();
    process.exit(0);
  }
});

ws.on('error', function error(err) {
  console.error('Smoke Test: ERROR -', err.message);
  process.exit(1);
});

// Timeout after 5 seconds if no welcome message
setTimeout(() => {
  console.error('Smoke Test: FAILED - Timed out waiting for welcome message.');
  process.exit(1);
}, 5000);
