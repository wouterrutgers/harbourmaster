# Harbourmaster

Harbourmaster is a RuneLite plugin that helps you earn Sailing XP from courier tasks. It recommends offers, guides you through pickups and deliveries, and plots your route between ports.

## How to use

Start at any port with a Sailing noticeboard. You can begin with that board's offers and discover more as you complete deliveries.

1. Enable Harbourmaster in RuneLite. The default settings provide offer recommendations, route guidance and cargo highlights. To include teleports or charter ships, configure the methods you can use under [Settings](#settings).
2. Open the noticeboard and let the route calculation finish. Accept the offers outlined in green, or your configured recommendation colour. Hover a highlighted offer to see the selected jobs in the current plan.
3. Close the board and follow the dock guidance. Use the ledger to collect task crates, then deposit them in your boat's cargo hold. Finish the current port's cargo actions before leaving.
4. Follow the route status to the next port. Sail along the drawn route, or use the teleport or charter instruction when one is recommended.
5. At the destination, take the highlighted delivery crates from the cargo hold, go ashore and use the ledger to deliver them. Cargo labels show where each crate belongs.
6. Check noticeboards when prompted as you reach ports. New offers become part of the plan, so your next destination may change. Accept newly highlighted offers and keep following the dock and travel instructions.

Rewards are normally paid when you finish unloading. If a completed task still occupies a slot afterward, follow the prompt to claim its rewards from the port master.

## How recommendations work

Harbourmaster compares combinations of offers from the boards you have opened, together with your accepted tasks, to find an efficient courier plan. Several tasks sharing a route can be better than one offer with a higher individual XP reward. Recommendations use estimated travel and cargo handling time to compare courier XP per hour.

RuneLite cannot read an unopened noticeboard's random offers. The plugin starts with the information available and updates its plan as you open more boards, accept tasks and complete deliveries. It cannot know the best possible route across every port without seeing those boards first.

For example, if you open the Lunar Isle board and later open Deepfin Point's board during the same offer cycle, Harbourmaster remembers both sets of offers. It considers them together when updating the plan and can send you back to Lunar Isle for a useful pickup. You do not need to reopen Lunar Isle's board just to keep those offers in memory.

The route status shows estimated travel time. Actual XP per hour also depends on wind, boosts, walking and cargo handling.

### Offer resets and memory

Remembered offers are cleared after every eight completed tasks and at the daily reset at midnight UK time. After a reset, check boards as you reach ports to discover the new offers and build up a fresh plan.

Logging out, hopping worlds or disabling the plugin also clears remembered offers. Reopen boards to make their offers available to the planner again. Moving between ports while staying logged in preserves that memory until the offer cycle resets.

### If no offers are highlighted

Read the message at the bottom of the noticeboard. **Continue to ...** means the plan's next stop is elsewhere. **Close the board and complete the dock actions** means there is cargo or another action to finish here. **No suitable courier offers** means the current information does not provide a suitable courier plan; try another board.

If recommendations are missing, check that:

- **Highlight recommended offers** in **Noticeboard** and **Enable route optimiser** in **Route** are enabled. Both are on by default.
- You have a free task slot. Complete deliveries and claim any outstanding rewards to free occupied slots.
- The route calculation has finished. Highlights are hidden while the planner is updating.

## Settings

Choose where to show your route: in the game world, on the minimap or on the world map. Enable **Show complete route** to see the rest of your planned journey. Highlight colours, dock guidance and cargo labels can also be adjusted in the plugin settings.

In **Teleports and charter ships**, enable **Use teleports and charter ships** and select the methods you can use. Each method is disabled by default. Choices include Sailors' amulet destinations, Aldarin and Prifddinas house portals, teleport crystals, Lunar Isle teleports, boat teleports and summons, and charter ships. Spells, tablets and capes that reach the same place share one checkbox.

Enabled methods are assumed available whenever the route needs them. The plugin does not check your gear, spellbook, levels, unlocks, charges, runes or coins. Make sure you have the required setup and supplies. Enabling charter ships assumes access to every charter destination.

In **Boat**, set the focus fitted to boats 1 through 5: **None**, **Teleport focus** or **Greater teleport focus**. A teleport focus allows summoning; a greater focus also allows teleporting to that boat. Each boat defaults to **None**. Enable the corresponding spell or tablet option in **Teleports and charter ships** as well.

You can leave a loaded boat docked while collecting known offers at other ports, then return to it. Open your courier boat's cargo hold once so its contents can be checked before recommending a summon. The planner will not teleport while you or your crew carry crates, or summon a boat with unrecognised contents.

## Screenshots

### Sailing route

![Route guidance drawn over the sea](screenshots/sailing-route.png)

### Dock guidance

![Highlighted ledger showing a crate pickup](screenshots/dock-guidance.png)

### Cargo labels

![Cargo icons](screenshots/cargo-labels.png)

### Noticeboard recommendations

![Recommended courier offer outlined in green](screenshots/noticeboard-offers.png)
