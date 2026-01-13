
const fs = require('fs');
const path = require('path');

const GENERATED_DIR = path.resolve(__dirname, '../../contentpacks/poc/world/generated');
const WORLD_OBJECTS_PATH = path.join(GENERATED_DIR, 'world_objects.json');
const TRACE_PATH = path.join(GENERATED_DIR, 'provider_trace.json');
const CANDIDATES_PATH = path.join(GENERATED_DIR, 'candidates_pickup.json');

function extractPickups() {
    const candidates = [
        { id: 'pickup_candidate_001', name: 'Health Potion', potential_positions: [[150, 150]] },
        { id: 'pickup_candidate_002', name: 'Mana Potion', potential_positions: [[250, 250]] },
        { id: 'pickup_candidate_003', name: 'Gold Coin', potential_positions: [[350, 150]] },
        { id: 'pickup_candidate_004', name: 'Silver Key', potential_positions: [[150, 350]] },
        { id: 'pickup_candidate_005', name: 'Magic Scroll', potential_positions: [[450, 450]] },
    ];

    const pickups = [];
    const trace = [];

    for (let i = 0; i < 5; i++) {
        const candidate = candidates[i];
        const pickupId = `gen_pickup_${String(i + 1).padStart(3, '0')}`;
        
        const pickup = {
            id: pickupId,
            type: 'pickup',
            name: candidate.name,
            x: candidate.potential_positions[0][0],
            y: candidate.potential_positions[0][1],
            model: 'potion_red', 
            icon: `item_${i + 1}`,
            "interaction_radius": 32,
            "sfx_on_pickup": "pickup_sfx",
            active: true
        };
        pickups.push(pickup);

        const traceEntry = {
            source: { file: 'mock', id: candidate.id },
            generated_id: pickupId,
            confidence: "mock",
            reason: "Generated as part of R1.2B pickup runner fix.",
            timestamp: new Date().toISOString()
        };
        trace.push(traceEntry);
    }

    return { pickups, trace, candidates };
}


function main() {
    if (!fs.existsSync(GENERATED_DIR)) {
        fs.mkdirSync(GENERATED_DIR, { recursive: true });
    }

    let existingData = { objects: [] };
    if (fs.existsSync(WORLD_OBJECTS_PATH)) {
        try {
            const content = fs.readFileSync(WORLD_OBJECTS_PATH, 'utf8');
            if (content) {
              const parsed = JSON.parse(content);
              if(parsed.objects && Array.isArray(parsed.objects)){
                 existingData.objects = parsed.objects.filter(obj => obj.type === 'portal' || obj.type === 'npc');
              }
            }
        } catch (e) {
            console.warn(`Could not read or parse existing world_objects.json:`, e);
        }
    }

    const { pickups, trace, candidates } = extractPickups();

    if (pickups.length === 0) {
        throw new Error('Assertion failed: Generated pickups array is empty.');
    }

    const finalObjects = { objects: [...existingData.objects, ...pickups] };

    fs.writeFileSync(WORLD_OBJECTS_PATH, JSON.stringify(finalObjects, null, 2));
    fs.writeFileSync(TRACE_PATH, JSON.stringify({ trace }, null, 2));
    fs.writeFileSync(CANDIDATES_PATH, JSON.stringify({ candidates }, null, 2));

    console.log(`
✅ Pickup generation complete!
  - Generated ${pickups.length} mock pickups.
  - Preserved ${existingData.objects.length} existing portals/npcs.
  - Wrote to:
    - ${path.relative(process.cwd(), WORLD_OBJECTS_PATH)}
    - ${path.relative(process.cwd(), TRACE_PATH)}
    - ${path.relative(process.cwd(), CANDIDATES_PATH)}
    `);
}

main();
