const WebSocket = require('ws');
const fs = require('fs');
const path = require('path');

const LOG_DIR = path.join(__dirname, '../../logs');
if (!fs.existsSync(LOG_DIR)) {
  fs.mkdirSync(LOG_DIR, { recursive: true });
}
const LOG_FILE = path.join(LOG_DIR, `r1_2b1_verify_${Date.now()}.log`);

const log = (message) => {
  console.log(message);
  fs.appendFileSync(LOG_FILE, `[${new Date().toISOString()}] ${message}\n`);
};

const WORLD_OBJECTS_PATH = path.join(__dirname, '../../contentpacks/poc/world/generated/world_objects.json');

log('Starting R1.2B.1 Pickup Verification Script...');

let worldObjects;
try {
  const fileContent = fs.readFileSync(WORLD_OBJECTS_PATH, 'utf8');
  worldObjects = JSON.parse(fileContent);
  log(`Successfully read and parsed ${WORLD_OBJECTS_PATH}`);
} catch (error) {
  log(`FATAL: Could not read or parse ${WORLD_OBJECTS_PATH}. Error: ${error.message}`);
  process.exit(1);
}

const firstPickup = worldObjects.pickups && worldObjects.pickups[0];
if (!firstPickup) {
  log('FATAL: No pickups found in the generated world_objects.json file.');
  process.exit(1);
}

log(`Found first pickup: ID=${firstPickup.id} at (x=${firstPickup.x}, y=${firstPickup.y})`);

const wsUrl = 'ws://127.0.0.1:8080/ws?roomId=poc_world&name=pickup_verifier';
log(`Connecting to WebSocket at ${wsUrl}...`);

const ws = new WebSocket(wsUrl);

ws.on('open', () => {
  log('WebSocket connection established.');
  
  const moveMessage = JSON.stringify({
    type: 'move',
    x: firstPickup.x,
    y: firstPickup.y,
  });

  log(`Sending move command: ${moveMessage}`);
  ws.send(moveMessage);
});

ws.on('message', (message) => {
  const data = JSON.parse(message);
  log(`Received message from server: ${JSON.stringify(data)}`);

  if (data.type === 'delta' && data.objRemoves && data.objRemoves.includes(firstPickup.id)) {
    log(`✅ SUCCESS: Server correctly broadcasted the removal of pickup ID: ${firstPickup.id}`);
    ws.close();
    process.exit(0);
  }
});

ws.on('close', () => {
  log('WebSocket connection closed.');
});

ws.on('error', (error) => {
  log(`WebSocket error: ${error.message}`);
  process.exit(1);
});

setTimeout(() => {
  log('⚠️ TIMEOUT: Script timed out after 15 seconds. Did not receive expected objRemoves message.');
  ws.close();
  process.exit(1);
}, 15000);
