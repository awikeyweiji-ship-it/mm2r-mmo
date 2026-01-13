
const fs = require('fs');
const path = require('path');
const { extractPickups } = require('./extract_pickups.js');

const GENERATED_DIR = path.join('contentpacks', 'poc', 'world', 'generated');
const WORLD_OBJECTS_PATH = path.join(GENERATED_DIR, 'world_objects.json');
const TRACE_PATH = path.join(GENERATED_DIR, 'provider_trace.json');
const CANDIDATES_PATH = path.join(GENERATED_DIR, 'candidates_pickup.json');
const LOGS_DIR = 'logs';
const LOG_FILE_PATH = path.join(LOGS_DIR, `r1_2b_reset_pickups_${Date.now()}.log`);

function main() {
    // Ensure directories exist
    if (!fs.existsSync(GENERATED_DIR)) {
        fs.mkdirSync(GENERATED_DIR, { recursive: true });
    }
    if (!fs.existsSync(LOGS_DIR)) {
        fs.mkdirSync(LOGS_DIR, { recursive: true });
    }

    // 1. Read existing world objects, preserving portals and npcs
    let existingWorld = { objects: [] };
    if (fs.existsSync(WORLD_OBJECTS_PATH)) {
        try {
            const content = fs.readFileSync(WORLD_OBJECTS_PATH, 'utf8');
            if(content){
               const parsedContent = JSON.parse(content);
               if (parsedContent.objects && Array.isArray(parsedContent.objects)) {
                  existingWorld.objects = parsedContent.objects.filter(obj => obj.type === 'portal' || obj.type === 'npc');
               }
            }
        } catch (e) {
            console.error(`Error reading or parsing ${WORLD_OBJECTS_PATH}. Starting fresh.`, e);
            existingWorld = { objects: [] };
        }
    }

    // 2. Generate new pickups
    const { pickups, trace, candidates } = extractPickups();
    if (!pickups || pickups.length === 0) {
        throw new Error("extractPickups returned no pickups.");
    }

    // 3. Combine and write world objects
    const finalWorldObjects = { objects: [...existingWorld.objects, ...pickups] };
    if (finalWorldObjects.objects.length === 0) {
        throw new Error('Assertion failed: finalWorldObjects.objects is empty before writing.');
    }
    fs.writeFileSync(WORLD_OBJECTS_PATH, JSON.stringify(finalWorldObjects, null, 2));


    // 4. Write trace and candidates
    if (trace.length === 0) {
        throw new Error('Assertion failed: trace is empty before writing.');
    }
    fs.writeFileSync(TRACE_PATH, JSON.stringify({ trace }, null, 2));

    if (candidates.length === 0) {
        throw new Error('Assertion failed: candidates is empty before writing.');
    }
    fs.writeFileSync(CANDIDATES_PATH, JSON.stringify({ candidates }, null, 2));


    // 5. Write log summary
    const logSummary = `
R1.2B Reset Pickups Summary:
-----------------------------
Timestamp: ${new Date().toISOString()}
- Wrote ${pickups.length} pickups to ${WORLD_OBJECTS_PATH}.
- Preserved ${existingWorld.objects.length} existing objects (portals/npcs).
- Example pickup ID: ${pickups[0].id}
- Wrote ${trace.length} trace entries to ${TRACE_PATH}.
- Wrote ${candidates.length} candidate entries to ${CANDIDATES_PATH}.
    `;
    fs.writeFileSync(LOG_FILE_PATH, logSummary);

    // 6. Print summary to console
    console.log('--- Runner Summary ---');
    console.log(`- Generated ${pickups.length} pickups.`);
    console.log(`- Example pickup ID: ${pickups[0].id}`);
    console.log(`- Wrote outputs to:`);
    console.log(`  - ${WORLD_OBJECTS_PATH}`);
    console.log(`  - ${TRACE_PATH}`);
    console.log(`  - ${CANDIDATES_PATH}`);
    console.log(`- Log written to ${LOG_FILE_PATH}`);

}

try {
    main();
} catch (error) {
    console.error("Runner script failed:", error);
    process.exit(1);
}
