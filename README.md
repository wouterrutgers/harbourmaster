# Harbourmaster

Harbourmaster helps you earn Sailing XP efficiently from courier tasks.

- Highlights recommended offers and combinations based on your current tasks.
- Guides you along an optimized pickup and delivery route.
- Highlights dock objects and labels task cargo to help you complete each stop.

Harbourmaster finds an efficient sailing route through the actions in its current courier plan. It favors faster calculations over finding the absolute shortest sailing path. It plans from tasks you hold and offers it has seen at noticeboards during the current cycle of eight completed tasks or until the daily board reset. It chooses observed offers by courier reward XP per hour using fixed best case timing: four tiles per tick while sailing, one tick each to board and unboard per route leg, one tick to accept each offer, and one tick per task pickup or delivery regardless of crate quantity. This assumes an ideal active setup, so the rate is optimistic for slower boats. It counts courier rewards only, not other Sailing XP.

The planner uses physical sailing routes and on foot noticeboard stops. Generate exact routes for all ports and boat sizes with `./gradlew generateRoutes`; the command downloads the latest complete English live cache from OpenRS2. Pass `-PcacheId=<id>` to regenerate from a specific cache. The `Generate sailing routes` workflow on GitHub runs one boat size on each of three runners and commits the files together. Regenerate after changing port locations, boat dimensions, or route rules. The planner does not assume unobserved offers or use teleport spells, charters, or automatic task acceptance and sailing.

## Screenshots

### Sailing route

![Route guidance drawn over the sea](screenshots/sailing-route.png)

### Dock guidance

![Highlighted ledger showing a crate pickup](screenshots/dock-guidance.png)

### Cargo labels

![Cargo icons](screenshots/cargo-labels.png)

### Noticeboard recommendations

![Recommended courier offer outlined in green](screenshots/noticeboard-offers.png)
