
const WebSocket = require('ws');

// S4 Pickup Simulator
// Creates 2 bots:
// Bot-Observer: Sits near pickup location
// Bot-Picker: Moves to pickup location
// Verifies: Observer sees pickup disappear (via delta.objRemoves)

const HOST = process.env.WS_HOST || 'localhost';
const PORT = process.env.WS_PORT || 8080;
const WS_URL = `ws://${HOST}:${PORT}`;

console.log(`Connecting to ${WS_URL}...`);

// Pickup location (from server index.js)
const PICKUP_X = 400;
const PICKUP_Y = 400;
const PICKUP_ID = 'pickup_1';

const observer = new WebSocket(`${WS_URL}?roomId=s4_test_v2&name=Bot-Observer`);
const picker = new WebSocket(`${WS_URL}?roomId=s4_test_v2&name=Bot-Picker`);

let observerReady = false;
let pickerReady = false;
let objectDisappeared = false;

// Timeout
setTimeout(() => {
    console.error("❌ TEST FAILED: Timeout waiting for pickup event.");
    process.exit(1);
}, 10000);

// Observer Logic
observer.on('open', () => {
    console.log("Observer connected");
    // Move observer near pickup so they get updates
    observer.send(JSON.stringify({
        type: 'move',
        x: PICKUP_X + 100,
        y: PICKUP_Y
    }));
});

observer.on('message', (data) => {
    const msg = JSON.parse(data);
    if (msg.type === 'snapshot') {
        observerReady = true;
        checkStart();
    } else if (msg.type === 'delta') {
        if (msg.objRemoves && msg.objRemoves.includes(PICKUP_ID)) {
            console.log("✅ Observer saw pickup disappear!");
            objectDisappeared = true;
            finish();
        }
    }
});

// Picker Logic
picker.on('open', () => {
    console.log("Picker connected");
    // Move away initially
    picker.send(JSON.stringify({
        type: 'move',
        x: 0,
        y: 0
    }));
});

picker.on('message', (data) => {
    const msg = JSON.parse(data);
    if (msg.type === 'snapshot') {
        pickerReady = true;
        checkStart();
    }
});

function checkStart() {
    if (observerReady && pickerReady) {
        // Debounce to prevent multiple triggers
        if (this.started) return;
        this.started = true;
        
        console.log("Both ready. Waiting 200ms then moving picker...");
        setTimeout(() => {
            picker.send(JSON.stringify({
                type: 'move',
                x: PICKUP_X,
                y: PICKUP_Y
            }));
            
            // Retry a bit later just in case throttle hit
            setTimeout(() => {
                picker.send(JSON.stringify({
                    type: 'move',
                    x: PICKUP_X,
                    y: PICKUP_Y
                }));
            }, 500);
            
        }, 200);
    }
}

function finish() {
    if (objectDisappeared) {
        console.log("✅ TEST PASSED: Pickup + Sync Verified");
        observer.close();
        picker.close();
        process.exit(0);
    }
}
