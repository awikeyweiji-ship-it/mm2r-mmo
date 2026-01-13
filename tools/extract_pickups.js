
/**
 * Generates a deterministic list of mock pickups, trace data, and candidates.
 * This script is self-contained and does not read external files, ensuring it runs reliably.
 *
 * @returns {{pickups: object[], trace: object[], candidates: object[]}}
 */
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
            position: candidate.potential_positions[0], // Use the first potential position
            model: 'potion_red', // Mock model
            icon: `item_${i + 1}`, // Mock icon
            "interaction_radius": 32,
            "sfx_on_pickup": "pickup_sfx"
        };
        pickups.push(pickup);

        const traceEntry = {
            source_id: candidate.id,
            generated_id: pickupId,
            confidence: "mock",
            reason: "Generated as part of R1.2B reset.",
            timestamp: new Date().toISOString()
        };
        trace.push(traceEntry);
    }

    if (pickups.length === 0) {
        throw new Error('Assertion failed: Generated pickups array is empty.');
    }
     if (trace.length === 0) {
        throw new Error('Assertion failed: Generated trace array is empty.');
    }
     if (candidates.length === 0) {
        throw new Error('Assertion failed: Generated candidates array is empty.');
    }


    return { pickups, trace, candidates };
}

module.exports = { extractPickups };
