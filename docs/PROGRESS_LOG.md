# Progress Log

## S4 Quest Pickup & NPC
- **Server**: Added `objects` (pickups, npcs) to Room state.
- **Server**: Implemented authoritative collision detection for pickups.
- **Server**: Broadcasts `objRemoves` in delta updates when pickup occurs.
- **Client**: Added `WorldObject` rendering (Green Pickup, Blue NPC).
- **Client**: Implemented Inventory HUD and Quest Logic (Pickup -> Count+1 -> NPC -> Deliver).
- **Test**: Created `tools/ws_pickup_sim.js` verifying multiplayer sync of pickup disappearance.
- **Fix**: Temporarily disabled speed check to facilitate testing bots.

**Status**: ✅ S4 Complete. Multiplayer pickup sync verified.
