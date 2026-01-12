const WebSocket = require('ws');

// This script simulates two clients connecting to the same-origin WebSocket proxy.
// It requires the web preview and backend server to be running.

// The URL should be the one provided by the IDX preview environment.
// The script will automatically derive the wss:// endpoint from it.
const PREVIEW_URL = process.env.GITPOD_WORKSPACE_URL || process.env.PREVIEW_URL;

if (!PREVIEW_URL) {
    console.error("Error: PREVIEW_URL environment variable not set.");
    console.error("Please run this script in an environment where the preview URL is available.");
    process.exit(1);
}

function getWsUrl(previewUrl) {
    const url = new URL(previewUrl);
    const wsScheme = url.protocol === 'https:' ? 'wss' : 'ws';
    return `${wsScheme}://${url.host}/ws`;
}

const WS_URL = getWsUrl(PREVIEW_URL);

console.log(`Using Same-Origin WebSocket URL: ${WS_URL}`);

function connectClient(name) {
    const url = `${WS_URL}?roomId=poc_world&name=${name}`;
    const ws = new WebSocket(url);

    ws.on('open', function open() {
        console.log(`[${name}] ✅ Connected`);
    });

    ws.on('message', function incoming(data) {
        const message = JSON.parse(data);
        if (message.type === 'snapshot') {
            const playerCount = Object.keys(message.players).length;
            console.log(`[${name}]  snapshot received, players: ${playerCount}`);
            if (playerCount >= 2) {
                console.log(`[${name}] ✅ SUCCESS: Detected 2 or more players.`);
                ws.close();
                process.exit(0);
            }
        }
    });

    ws.on('close', function close() {
        console.log(`[${name}] Disconnected`);
    });

    ws.on('error', function error(err) {
        console.error(`[${name}] ❌ Error:`, err.message);
    });

    return ws;
}

console.log('Simulating two clients connecting...');
const client1 = connectClient('sim_client_1');
const client2 = connectClient('sim_client_2');

// Close connections after a timeout if the test doesn't pass
setTimeout(() => {
    console.error('❌ FAILURE: Test timed out. Did not detect 2 players.');
    client1.close();
    client2.close();
    process.exit(1);
}, 15000); // 15 seconds
