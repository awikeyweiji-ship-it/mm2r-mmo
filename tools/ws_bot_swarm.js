const WebSocket = require('ws');

// Config
const BOTS_COUNT = process.argv[2] ? parseInt(process.argv[2]) : 20;
const ROOM_ID = process.argv[3] || 'poc_world';
const DURATION_SEC = process.argv[4] ? parseInt(process.argv[4]) : 10;

const HOST = 'ws://localhost:8080';

console.log(`Starting Bot Swarm: ${BOTS_COUNT} bots in room '${ROOM_ID}' for ${DURATION_SEC}s`);

const bots = [];
let stats = {
    connected: 0,
    errors: 0,
    msgsReceived: 0,
    moveSent: 0
};

for (let i = 0; i < BOTS_COUNT; i++) {
    const ws = new WebSocket(`${HOST}?roomId=${ROOM_ID}&name=Bot-${i}`);
    
    ws.on('open', () => {
        stats.connected++;
        // Start moving
        ws.moveInterval = setInterval(() => {
            if (ws.readyState === WebSocket.OPEN) {
                // Random walk
                const x = Math.random() * 500;
                const y = Math.random() * 500;
                ws.send(JSON.stringify({ type: 'move', x, y }));
                stats.moveSent++;
            }
        }, 100); // 10Hz moves
    });

    ws.on('message', (data) => {
        stats.msgsReceived++;
    });

    ws.on('error', (e) => {
        stats.errors++;
    });

    bots.push(ws);
}

// Reporting
const reportInterval = setInterval(() => {
    console.log(`[Stats] Connected: ${stats.connected}/${BOTS_COUNT} | Moves: ${stats.moveSent} | Recv: ${stats.msgsReceived} | Errors: ${stats.errors}`);
}, 1000);

// Cleanup
setTimeout(() => {
    console.log('--- Test Finished ---');
    clearInterval(reportInterval);
    bots.forEach(b => {
        clearInterval(b.moveInterval);
        b.terminate();
    });
    process.exit(0);
}, DURATION_SEC * 1000);
