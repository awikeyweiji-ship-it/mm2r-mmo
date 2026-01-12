const WebSocket = require('ws');
const fs = require('fs');
const path = require('path');

const STATE_FILE = path.join(__dirname, '../server/data/world_state.json');
const TEST_KEY = 'bot_persist_test_' + Math.random().toString(36).substr(2, 4);
const TEST_X = 123.45;
const TEST_Y = 67.89;

async function runTest() {
    console.log(`Starting Persistence Test with key: ${TEST_KEY}`);

    // 1. Join and Move (Gradually to avoid speed violation)
    const client1 = new WebSocket(`ws://localhost:8080?playerKey=${TEST_KEY}&name=PersistBot`);
    
    await new Promise((resolve, reject) => {
        client1.on('open', () => {
            console.log('Client 1 connected.');
            
            // First move to a closer point to set current, then to target
            // Or just do it once if startX is close enough. 
            // My server spawns at random 50~350. Target 123 is close enough.
            client1.send(JSON.stringify({ type: 'move', x: TEST_X, y: TEST_Y }));
            console.log(`Sent move to ${TEST_X}, ${TEST_Y}`);
            setTimeout(resolve, 3000);
        });
        client1.on('error', reject);
    });

    client1.close();

    // 2. Check File
    const state = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
    const entry = state[TEST_KEY];
    if (!entry) throw new Error('Test player not found in file!');
    
    // 3. Re-join and Verify
    const client2 = new WebSocket(`ws://localhost:8080?playerKey=${TEST_KEY}`);
    await new Promise((resolve, reject) => {
        client2.on('message', (data) => {
            const msg = JSON.parse(data);
            if (msg.type === 'snapshot') {
                const p = msg.players[TEST_KEY];
                if (p) {
                    console.log(`Restored pos: ${p.x}, ${p.y}`);
                    if (Math.abs(p.x - entry.x) < 0.1) {
                        console.log('✅ PERSISTENCE VERIFIED');
                        resolve();
                    }
                }
            }
        });
        setTimeout(() => reject('Wait timeout'), 5000);
    });

    client2.close();
    process.exit(0);
}

runTest().catch(err => {
    console.error('❌ TEST FAILED:', err);
    process.exit(1);
});
