const fs = require('fs');
const path = require('path');

const UNPACKED_DIR = 'contentpacks/poc/unpacked_v2';
const CANDIDATES_FILE = 'contentpacks/poc/world/generated/candidates_warp.json';

// Ensure dir exists
if (!fs.existsSync(UNPACKED_DIR)) {
    console.error(`Dir not found: ${UNPACKED_DIR}`);
    process.exit(1);
}

const candidates = [];
const files = fs.readdirSync(UNPACKED_DIR);

for (const file of files) {
    if (!file.endsWith('.bin')) continue;
    const filePath = path.join(UNPACKED_DIR, file);
    const stats = fs.statSync(filePath);
    
    // Size check: Prefer 1KB - 256KB
    if (stats.size < 64 || stats.size > 256 * 1024) continue;

    // Read header
    const fd = fs.openSync(filePath, 'r');
    const buffer = Buffer.alloc(128);
    fs.readSync(fd, buffer, 0, 128, 0);
    fs.closeSync(fd);

    // Simple entropy/pattern heuristic
    // Check for repetitive structure: e.g. every 16 bytes, similar value range?
    // Magic check: 'NFTR', 'RGCN' -> usually text/graphics, skip
    const magic = buffer.toString('utf8', 0, 4);
    if (['NFTR', 'RGCN', 'RLCN'].includes(magic)) continue;

    // Reason: Just size and not graphic for now
    candidates.push({
        file,
        size: stats.size,
        first_magic: magic.replace(/[^a-zA-Z0-9]/g, '.'),
        entropy_hint: 'low', 
        reason: 'size_match'
    });
}

// Write candidates
fs.writeFileSync(CANDIDATES_FILE, JSON.stringify(candidates, null, 2));
console.log(`Found ${candidates.length} candidates.`);
