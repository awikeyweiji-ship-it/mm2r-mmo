const WebSocket = require('ws');
const http = require('http');

// Simple probe to verify WSS connectivity logic from a script
// Usage: node tools/wss_probe_preview.js <baseUrl>

const baseUrl = process.argv[2] || 'http://localhost:8080';

console.log(`Probing Base URL: ${baseUrl}`);

// 1. Derive WS URL
let wsUrl = baseUrl.replace('http://', 'ws://').replace('https://', 'wss://');
// remove trailing slash if present
if (wsUrl.endsWith('/')) wsUrl = wsUrl.slice(0, -1);
wsUrl += '/ws';

console.log(`Derived WS URL: ${wsUrl}`);

// 2. Connect
const ws = new WebSocket(`${wsUrl}?roomId=probe_test`, {
    rejectUnauthorized: false // In case of self-signed certs in some envs
});

const timeout = setTimeout(() => {
    console.error('TIMEOUT: WebSocket connection timed out after 5000ms');
    process.exit(1);
}, 5000);

ws.on('open', () => {
    clearTimeout(timeout);
    console.log('WSS_OPEN_SUCCESS');
    ws.close();
    process.exit(0);
});

ws.on('error', (err) => {
    clearTimeout(timeout);
    console.error(`WS_ERROR: ${err.message}`);
    process.exit(1);
});
