const fs = require('fs');
const path = require('path');

const DATA_DIR = path.join(__dirname, '../data');
const STATE_FILE = path.join(DATA_DIR, 'world_state.json');

// Ensure data dir exists
if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
}

let persistenceMap = {}; // playerKey -> { roomId, name, color, x, y, ts }
let saveTimeout = null;

function loadState() {
    try {
        if (fs.existsSync(STATE_FILE)) {
            const raw = fs.readFileSync(STATE_FILE, 'utf8');
            persistenceMap = JSON.parse(raw);
            console.log(`[Persistence] Loaded ${Object.keys(persistenceMap).length} players.`);
        }
    } catch (e) {
        console.error('[Persistence] Failed to load state:', e);
        // Rename bad file
        if (fs.existsSync(STATE_FILE)) {
            fs.renameSync(STATE_FILE, STATE_FILE + '.bad.' + Date.now());
        }
        persistenceMap = {};
    }
    return persistenceMap;
}

function getPlayerState(playerKey) {
    return persistenceMap[playerKey];
}

function updatePlayerState(playerKey, data) {
    if (!persistenceMap[playerKey]) {
        persistenceMap[playerKey] = {};
    }
    Object.assign(persistenceMap[playerKey], data);
    scheduleSave();
}

function scheduleSave() {
    if (saveTimeout) return;
    saveTimeout = setTimeout(() => {
        saveState();
        saveTimeout = null;
    }, 1500); // 1.5s debounce
}

function saveState() {
    try {
        const tempFile = STATE_FILE + '.tmp';
        fs.writeFileSync(tempFile, JSON.stringify(persistenceMap, null, 2));
        fs.renameSync(tempFile, STATE_FILE);
        // console.log('[Persistence] Saved world state.');
    } catch (e) {
        console.error('[Persistence] Save failed:', e);
    }
}

module.exports = {
    loadState,
    getPlayerState,
    updatePlayerState
};
