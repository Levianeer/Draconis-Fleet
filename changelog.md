Version 0.6.7 (Save-compatible with 0.6.6)
BUG FIXES:
- Fixed XLII Battlegroup at Kori spawning with really low FP.
- Fixed small ordering issue in rules.csv.

Version 0.6.6 (Save-compatible with 0.6.5)
NEW CONTENT:
- NEW: Sigma Octantis - A unique AI Core officer with a dedicated questline.
  - Quest: earn sufficient standing with the Draconis Alliance to unlock a special dialogue
    option with Fleet Admiral Emil. He'll ask you to procure a Pristine Nanoforge for the
    Alliance. Deliver one and receive the Sigma Octantis Uplink as your reward.
  - Unique skill: Marginal Allocation.
  - Failure state: if your reputation with the Draconis Alliance drops to hostile or below
    while Sigma Octantis is in your possession, it will confront you and permanently sever the
    connection - the core is removed from your fleet, cargo, and all market storage and cannot
    be recovered. (Console command it back if you want to keep it, this check is only made once).
- NEW: Tianlong-class Battlecruiser - A fast battlecruiser with three large mounts in superfiring configuration.
- NEW: Trebuchet Cannon - Large triple-barrel energy weapon.
- NEW: Alsafi-class Cargo Freighter - A dedicated freighter variant of the Alsafi.
  - Higher cargo capacity (400) than the base Alsafi.
  - Much lower fuel capacity (80) - trades range for hauling power.

BALANCE CHANGES:
- General fleet-wide balance pass; most ships received adjustments to flux, armor, and shields.
- Reclassified Alsafi as a dedicated Tanker:
  - Reduced cargo: 200 -> 20.
  - Increased fuel: 400 -> 800.
  - Buffed max flux: 1750 -> 3600.
  - Buffed flux dissipation: 105 -> 215.
  - Increased OP: 65 -> 75.
  - Increased CR to deploy: 15 -> 20.
  - Increased DP: 4 -> 5.
  - Reduced base value: 25,000 -> 20,000.
- Rebalanced Aldhibah-class Frigate:
  - Reduced flux dissipation: 280 -> 275.
  - Reduced OP: 55 -> 50.
  - Reduced max burn: 10 -> 9.
- Rebalanced Alruba-class Gunship:
  - Reduced armor: 500 -> 300.
  - Reduced max flux: 4500 -> 3750.
  - Reduced flux dissipation: 360 -> 300.
  - Improved shield efficiency: 1.2 -> 1.0.
- Rebalanced Alruba Mk.II-class Gunship:
  - Increased FP: 8 -> 9.
  - Reduced armor: 500 -> 300.
  - Reduced max flux: 4700 -> 3750.
  - Reduced flux dissipation: 380 -> 300.
  - Improved shield efficiency: 1.1 -> 1.0.
- Rebalanced Thuban-class Corvette:
  - Reduced max flux: 5300 -> 4000.
  - Reduced flux dissipation: 425 -> 320.
  - Improved shield efficiency: 1.2 -> 1.1.
  - Reduced max burn: 11 -> 10.
- Rebalanced Errakis-class Missile Destroyer:
  - Changed ship system: Maneuvering Jets -> Fast Missile Racks.
  - Increased OP: 90 -> 95.
  - Increased max speed: 70 -> 75.
- Rebalanced Giausar-class Destroyer:
  - Reduced max flux: 9000 -> 4500.
  - Reduced flux dissipation: 540 -> 270.
  - Reduced OP: 85 -> 60.
- Rebalanced Rastaban-class Light Destroyer:
  - Reduced max flux: 8700 -> 7000.
  - Reduced flux dissipation: 525 -> 425.
  - Reduced OP: 90 -> 80.
  - Improved shield efficiency: 1.3 -> 1.2.
- Rebalanced Shaobi-class Light Carrier:
  - Increased hitpoints: 3000 -> 3500.
  - Increased armor: 300 -> 330.
  - Reduced max flux: 8000 -> 5000.
  - Reduced flux dissipation: 480 -> 300.
  - Reduced OP: 105 -> 90.
  - Improved shield efficiency: 1.3 -> 1.2.
- Rebalanced Shaowei-class Phase Destroyer:
  - Increased hitpoints: 3500 -> 4500.
  - Increased max flux: 5500 -> 6000.
  - Reduced flux dissipation: 600 -> 500.
  - Reduced OP: 95 -> 80.
- Rebalanced Eltanin-class Light Cruiser:
  - Increased flux dissipation: 400 -> 500.
  - Reduced OP: 140 -> 125.
  - Reduced max burn: 9 -> 8.
- Rebalanced Juni-class Cruiser:
  - Reduced armor: 1000 -> 900.
  - Increased max flux: 12000 -> 14000.
  - Reduced flux dissipation: 700 -> 650.
  - Reduced OP: 155 -> 130.
  - Increased acceleration: 35 -> 40.
  - Increased deceleration: 25 -> 30.
  - Reduced max burn: 8 -> 7.
- Rebalanced Juza-class Heavy Cruiser:
  - Reduced FP: 24 -> 22.
  - Reduced hitpoints: 14000 -> 10000.
  - Reduced max flux: 15000 -> 14500.
  - Increased flux dissipation: 550 -> 725.
  - Reduced OP: 175 -> 160.
  - Reduced fuel/ly: 5 -> 4.
  - Reduced max burn: 8 -> 7.
  - Improved shield efficiency: 1.4 -> 1.2.
- Rebalanced Nushi-class Cruiser:
  - Downgraded one large ballistic turret to medium.
  - Reduced FP: 22 -> 18.
  - Reduced DP: 34 -> 30.
  - Increased hitpoints: 5000 -> 6500.
  - Increased armor: 750 -> 800.
  - Increased max flux: 10000 -> 12000.
  - Reduced OP: 135 -> 120.
  - Reduced max speed: 90 -> 75.
  - Reduced acceleration: 100 -> 80.
  - Reduced deceleration: 80 -> 60.
  - Reduced max turn rate: 30 -> 25.
  - Reduced turn acceleration: 60 -> 35.
  - Reduced max burn: 9 -> 8.
  - Reduced shield efficiency: 0.8 -> 1.1.
- Rebalanced Nushi Mk.II-class Cruiser:
  - Reduced FP: 20 -> 16.
  - Reduced DP: 28 -> 25.
  - Increased hitpoints: 4800 -> 6000.
  - Increased armor: 650 -> 750.
  - Increased max flux: 11000 -> 12000.
  - Reduced flux dissipation: 700 -> 550.
  - Reduced OP: 145 -> 120.
  - Reduced max speed: 90 -> 85.
  - Reduced acceleration: 110 -> 80.
  - Reduced deceleration: 90 -> 60.
  - Reduced max turn rate: 40 -> 25.
  - Reduced turn acceleration: 80 -> 35.
  - Increased mass: 1600 -> 1750.
  - Reduced max burn: 10 -> 8.
  - Reduced shield efficiency: 0.7 -> 1.0.
- Rebalanced Yunu-class Combat Freighter:
  - Increased armor: 750 -> 850.
  - Reduced max flux: 20000 -> 16000.
  - Reduced flux dissipation: 1000 -> 800.
  - Reduced OP: 190 -> 160.
  - Reduced max burn: 9 -> 7.
  - Reduced shield efficiency: 1.0 -> 1.4.
- Rebalanced Altais-class Battlecruiser:
  - Reduced FP: 26 -> 22.
  - Reduced max burn: 8 -> 7.
  - Reduced shield efficiency: 1.0 -> 1.1.
- Rebalanced Alwaid-class Battlecarrier:
  - Reduced FP: 36 -> 34.
  - Reduced armor: 1500 -> 1200.
  - Reduced max flux: 46500 -> 30000.
  - Reduced flux dissipation: 1850 -> 1200.
  - Reduced OP: 350 -> 320.
  - Reduced max burn: 7 -> 6.
- Rebalanced Dziban-class Battlecruiser:
  - Reduced FP: 24 -> 22.
  - Reduced armor: 1200 -> 1100.
  - Reduced flux dissipation: 1100 -> 900.
  - Reduced max burn: 8 -> 7.
- Rebalanced Kuma-class Battleship:
  - Reduced FP: 32 -> 30.
  - Reduced base value: 500,000 -> 450,000.
  - Reduced armor: 1200 -> 1000.
  - Increased max flux: 22500 -> 28000.
  - Reduced flux dissipation: 1600 -> 1100.
  - Increased OP: 265 -> 270.
  - Reduced max burn: 7 -> 6.
  - Reduced shield efficiency: 1.2 -> 1.4.
- Rebalanced Asuia-class Cruiser:
  - Reduced max flux: 16000 -> 14000.
  - Reduced flux dissipation: 800 -> 700.
  - Reduced OP: 140 -> 105.
  - Reduced max burn: 9 -> 8.
- Rebalanced Grumium Heavy Bomber:
  - Reduced op cost: 18 -> 16.
- Rebalanced Grumium Heavy Fighter:
  - Removed Ammofeed ship system.
  - Changed wing formation: BOX -> V.
  - Reduced op cost: 20 -> 18.
- Rebalanced Mangonel Gun Launcher:
  - Reduced flux/shot: 1500 -> 1200.
- Rebalanced Twin Spear Cannon:
  - Reduced chargedown: 4 -> 3.
- Rebalanced Longsword Autocannon:
  - Decreased Rarity: 0.5 -> 0.7.
  - Reduced impact: 75 -> 50.
  - Reduced ammo: 9 -> 6.
  - Improved reload rate: 0.35 -> 0.5 ammo/sec.
- Rebalanced Fragarach Railgun:
  - Increased OP cost: 28 -> 30.
  - Increased base value: 3,200 -> 4,200.
  - Changed primary role tag: Anti Armor -> General.
- Rebalanced Micro Bardiche Launcher:
  - Increased range: 1000 -> 1500.
- Rebalanced Bardiche MLRS Launcher:
  - Reduced range: 2000 -> 1500.
- Rebalanced Phase Torpedo Array (system weapon):
  - Reduced damage: 2000 -> 1000.
- Rebalanced Kestros Autocannon (fighter):
  - Reduced range: 700 -> 600.
  - Reduced flux/shot: 15 -> 10.
  - Improved fire rate (chargedown: 0.125 -> 0.1).
- Adjusted Jammer Suite ship system timing:
  - Breach Jammer: Increased charge up/down: 0.1 -> 0.5.
  - Pulsar Jammer: Added charge up/down: 0.5.
  - Blackout Jammer: Added charge up/down: 0.1.

FACTION CHANGES:
- Increased combat freighter spawn probability in fleet compositions: 1.25 -> 1.5.
- Updated fleet role assignments:
  - Alsafi now properly assigned to tanker role.
  - Alsafi (Cargo) now assigned to freighter role.
  - Yunu removed from freighter role; kept as primary combat freighter.

MINOR IMPROVEMENTS:
- Changed Jormungandr so it stays opposite Vorium.
- Improved Missile Barrage ship system status string formatting.
- Updated Alsafi sprite to distinguish tanker variant.
- Renamed "Auto Flare Launcher" hullmod to "Autoflare Dispenser".
  - Increased detection range: 800 -> 900.
  - Increased cooldown: 8 -> 12 sec.
  - Updated hullmod icon.
- Reworked Plasma Jets ship system timing:
  - Faster activation (charge up: 1 -> 0.5).
  - Doubled active duration: 3 -> 6.
  - Faster deactivation (charge down: 3 -> 0.5).
  - Reduced cooldown: 6 -> 5.
- Improved Evasion Protocol flare countermeasures:
  - Increased effect range: 300 -> 400.
  - Increased effect chance: 0.3 -> 0.4.
  - Reduced flare visual size.
- Adjusted weapon sound effects (Fragarach pitch, Longsword volume).
- Kestros Autocannon now uses a dedicated projectile and custom fire sound.
- Updated Durendal Cannon muzzle flash visuals.
- Refactored jammer suite ring visual effects for improved performance.
- Reworked SLAP-ER mist cloud parameters (reduced radius and lifetime).

Version 0.6.5 (Save-compatible with 0.6.4)
NEW CONTENT:
- NEW: Nushi Mk.II Cruiser - A supply-constrained variant of the Nushi.
  - Swaps the two large mounts for medium mounts and the Flux Cycler for Plasma Jets.
  - Faster and more agile than the original; more of a glass cannon.
  - Plasma Jets ship system - Extreme top speed and maneuverability boost.
- NEW: Culverin Coilgun - Medium ballistic weapon with HE and EMP damage.

BALANCE CHANGES:
- Rebalanced Giausar Destroyer:
  - Added built-in hangar + assault drone wing.
  - Changed ship system: Assault Drones -> Reserve Wing.
  - Added built-in Defensive Targeting Array hullmod.
- Rebalanced Assault Drone:
  - Changed built-in hullmod: Point Defense AI -> Terminator Core.
- Rebalanced Nushi Cruiser:
  - Reduced max speed: 95 -> 90.
  - Increased DP: 32 -> 34.
- Rebalanced Juza Heavy Cruiser:
  - Increased FP: 18 -> 24.
  - Increased DP: 32 -> 35.
- Rebalanced Kuma Battleship:
  - Decreased min crew: 700 -> 300.
  - Increased max crew: 1500 -> 2500.
  - Mainly to increase its usefulness as a colony ship.

FACTION CHANGES:
- Increased Draconis AI core purchase value multiplier: 3x -> 4x.
- Renamed fleet types for clarity:
  - "Shadow Picket" -> "Shadow Convoy".
  - "Resupply Convoy" -> "Supply Convoy".
- Added proper liner role ship assignments to default ship roles.
- Reduced Juni Support weight in fleet compositions: 1.0 -> 0.5.
- Added Nushi Mk.II variants to default ship roles.

MINOR IMPROVEMENTS:
- Reworked the Evasion Protocol ship system.
  - Now fires a burst of flares instead of one per system weapon slot.
  - AI-controlled ships will now attempt to ram enemies in their forward arc while active.
  - Ship mass is temporarily increased during activation to aid ramming physics.
- Refactored the AI core management system to use a centralized stockpile.
  - Stolen, recovered, and donated cores that can't be immediately installed now
    persist to the stockpile and are retried daily.
  - Diplomatic strain from theft is now applied even when no installation slots are free.
- Reordered AI core placement priority: Commerce now ranks above Megaport.
- Added a proper tracker for total cores seized.
  - This will probably get a proper usage later on.
- Adjusted DRACON settings to be a bit less crazy. Hopefully.

BUG FIXES:
- Fixed colony expeditions failing because of a poorly set default_ship_roles.
- Fixed significant performance issue causing freezes and slowdowns after founding a colony.
  - The colony crisis system was logging at INFO level inside methods called every frame for
    every star system, causing thousands of log writes per second in heavily modded games.
  - Fixed double market scan in the crisis magnitude calculation - was scanning all markets
    twice per system per call instead of once.
  - Removed a periodic diagnostic loop that iterated all star systems every 10 seconds.

Version 0.6.4 (Save-compatible with 0.6.3)
BUG FIXES:
- Actually fixed The Rift this time, honest!

Version 0.6.3 (Save-compatible with 0.6.2)
MINOR IMPROVEMENTS:
- Add some SFX to the Rift.
- Cleaned up some scripts.

BUG FIXES:
- General bugfixing.
- Fixed The Rift not properly applying to non-player fleets.

Version 0.6.2 (Save-compatible with 0.6.1)
MINOR IMPROVEMENTS:
- Cleaned up some scripts.

BUG FIXES:
- General bugfixing.
- Fixed a ConcurrentModificationException crash with XLII_SystemBurnOnHitEffect.
- Fixed some mistakes in descriptions.csv.

Version 0.6.1 (Save-compatible with 0.6.0)
NEW CONTENT:
- NEW: DRACON threat system - Should give Draconis some campaign level staying power now.
  - Effectively replaces the old Steel Curtain system for Nexerelin users.
- NEW: Nushi-class Cruiser - Three large mounts!? A silly little guy.

BALANCE CHANGES:
- Rebalanced Eltanin-class Light Cruiser:
  - Added Headache ECM Suite hullmod.
- Rebalanced Juza-class Heavy Cruiser:
  - Increased OP: 30 -> 32.
- Rebalanced Twin Spear Cannon:
  - Reduced OP: 28 -> 26.
- Rebalanced Fragarach Railgun:
  - Increased Damage: 800 -> 900.
  - Added EMP Damage: 900.
  - Increased Flux/shot: 1200 -> 1250.
  - Increased chargedown: 3 -> 4.
- Rebalanced Longsword Autocannon:
  - Reduced Range: 1200 -> 1000.
  - Reduced Chargedown: 2 -> 1.5.
  - Redueced Burst Delay: 0.5 -> 0.25.
- Rebalanced Razor Flak Cannon:
  - Reduced Flux/shot: 4 -> 2.

SOUND CHANGES:
- Swapped Longsword and Fragarach fire sounds.

MINOR IMPROVEMENTS:
- Improved some of the variants.
- Improved the FX for the Fragarach Railgun.
- Added check to see if Draconis controls Kori
  - If not, the Admiral is removed from the Market, he returns if Draconis recaptures it.
- Updated the AIO/XLII Battlegroup Flag. Nihil nisi ultio.

BUG FIXES:
- Fixed bug where XLII_durendal_mkii_fire referenced _03.ogg twice and skipped _02
- Fixed some mistakes in rules.csv.

Version 0.6.0
NEW CONTENT:
- NEW: Expanded the Fafnir system, new planets, some background lore too! Free story point at Fafnir's (now) broken Gate.
- NEW: Grumium-class Fighter Bomber - Sturdy bomber wing with guided bombs.
- NEW: Yunu-class Cruiser - Multirole Transport/Support ship.
  - NEW: Missile Barrage ship system - Rapid-fire missile launch system with stat bonuses.
- NEW: Phase Torpedo Array ship system - Deploys guided phase torpedoes for the Shaowei.
- NEW: The Rift terrain - Custom hyperspace anomaly surrounding Fafnir system with enhanced particle storms.
- OLD: Aldhibah-class Frigate:
  - NEW: Added Improved Systems hullmod - Increases ship system charges and reduces cooldown.
- NEW: Shaowei animated wing decoratives - Dynamic wing elements for visual effects.
- NEW: Cestus Gun Launcher - Medium energy gun launcher with heavy EMP.
- NEW: Sovnya Burst Lance - Large energy beam weapon for kinetic damage.
- NEW: Loading tips lore - Just some lore fluff (Check \data\strings\tips.json to remove).
- NEW: Proper weapon covers - Visual improvements for weapon mounts.
- NEW: The XLII Battlegroup has been given a unique mechanic, something, something 'Mist Eaters'.

SYSTEM CHANGES:
- Enhanced some effects with GraphicsLib integration.
- New hull related sounds with new effects.

CAMPAIGN CHANGES:
- Added 'The Rift' custom terrain to Fafnir system with unique visuals and some campaign effects.
- Reworked the Fafnir star system, now the player has room to take some uninhabited planets through force or commission.
- Removed unneeded campaign channels configuration (channels.json deleted).
- Streamlined world generation (removed static economy.json and starmap.json in favor of class-based generation).

EXERELIN INTEGRATION:
- Updated Draconis faction configuration for better strategic behavior. AGAIN. I cannot be stopped.
- Enhanced AI core donation and theft systems.

BALANCE CHANGES:
- Rebalanced Aldibain Fighter:
  - Reduced Hitpoints: 750 -> 500.
  - Reduced Armor: 250 -> 100.
  - Increased Max Flux: 750 -> 1000.
  - Reduced Max Speed: 275 -> 235.
  - Reduced Acceleration: 500 -> 350.
  - Reduced Deceleration: 450 -> 350.
  - Reduced Max Turn Rate: 150 -> 120.
  - Reduced Turn Acceleration: 360 -> 300.
  - Increased Shield Arc: 120 -> 160.
- Rebalanced Eldsich Fighter:
  - Reduced Hitpoints: 450 -> 350.
  - Reduced Armor: 75 -> 25.
  - Increased Max Flux: 350 -> 500.
  - Changed Shield Type: OMNI -> FRONT.
  - Increased Shield Arc: 120 -> 200.
- Rebalanced Tianyi Fighter:
  - Reduced Hitpoints: 600 -> 350.
  - Reduced Armor: 100 -> 75.
  - Changed Shield Type: OMNI -> FRONT.
  - Increased Shield Arc: 120 -> 200.
- Rebalanced Nodus Fighter:
  - Reduced Shield Arc: 240 -> 200.
- Rebalanced Alruba-class Frigate:
  - Increased Max Flux: 3000 -> 4500.
  - Increased Flux Dissipation: 250 -> 360.
  - Increased Shield Efficiency: 0.8 -> 1.2.
  - Reduced Max Crew: 35 -> 30.
  - Increased Fuel: 25 -> 35.
  - Increased CR to Deploy: 10 -> 15.
- Rebalanced Alruba Mk.II-class Frigate:
  - Increased Max Flux: 3000 -> 4700.
  - Increased Flux Dissipation: 250 -> 380.
  - Increased Shield Efficiency: 0.7 -> 1.1.
  - Increased Fuel: 25 -> 40.
  - Increased CR to Deploy: 10 -> 15.
  - Increased Base Value: 14000 -> 15000.
- Rebalanced Thuban-class Corvette:
  - Increased Max Flux: 3500 -> 5300.
  - Increased Flux Dissipation: 200 -> 425.
  - Increased Shield Efficiency: 0.8 -> 1.2.
  - Increased Fuel: 20 -> 35.
  - Increased CR to Deploy: 10 -> 15.
  - Increased Base Value: 18000 -> 20000.
- Rebalanced Aldhibah-class Frigate:
  - Increased Max Flux: 2250 -> 3500.
  - Increased Flux Dissipation: 150 -> 280.
  - Increased Shield Efficiency: 0.7 -> 1.1.
  - Increased Max Crew: 30 -> 35.
  - Increased Fuel: 20 -> 40.
  - Reduced CR to Deploy: 20 -> 15.
  - Increased Base Value: 12000 -> 14000.
- Rebalanced Giausar-class Destroyer:
  - Increased Max Flux: 6000 -> 9000.
  - Increased Flux Dissipation: 250 -> 540.
  - Increased Shield Efficiency: 0.8 -> 1.2.
  - Reduced CR to Deploy: 20 -> 15.
- Rebalanced Errakis-class Destroyer:
  - Increased Max Flux: 4200 -> 6300.
  - Increased Flux Dissipation: 200 -> 380.
  - Increased Shield Efficiency: 0.8 -> 1.2.
  - Increased Peak CR: 240 -> 300.
- Rebalanced Rastaban-class Destroyer:
  - Increased Max Flux: 6000 -> 8700.
  - Increased Flux Dissipation: 400 -> 525.
  - Increased Shield Efficiency: 0.9 -> 1.3.
  - Reduced CR to Deploy: 20 -> 15.
- Rebalanced Shaobi-class Light Carrier:
  - Changed Ship System: Reserve Wing -> Flare Launcher. Reserve Wing is just too silly.
  - Reduced FP: 13 -> 12.
  - Reduced DP: 15 -> 14.
  - Increased Max Flux: 6000 -> 8000.
  - Increased Flux Dissipation: 400 -> 480.
  - Increased Shield Efficiency: 0.9 -> 1.3.
  - Increased Peak CR: 240 -> 300.
- Rebalanced Alsafi-class Freighter:
  - Reduced Flux Dissipation: 110 -> 105.
  - Increased Min Crew: 10 -> 20.
  - Increased Max Crew: 20 -> 40.
  - Reduced CR to Deploy: 20 -> 15.
- Rebalanced Shaowei-class Phase Destroyer:
  - Changed Ship System: Temporal Dash -> Phase Torpedo Array.
  - Reduced FP: 26 -> 22.
  - Reduced Hitpoints: 4000 -> 3500.
  - Reduced Armor: 700 -> 400.
  - Increased Flux Dissipation: 450 -> 600.
  - Increased Ordnance Points: 90 -> 95.
  - Increased Max Speed: 120 -> 140.
  - Increased Deceleration: 80 -> 90.
  - Reduced Max Turn Rate: 60 -> 50.
  - Reduced Turn Acceleration: 70 -> 60.
  - Reduced Min Crew: 50 -> 35.
  - Reduced Max Crew: 100 -> 75.
  - Reduced CR %/Day: 10 -> 5.
  - Increased Peak CR: 240 -> 300.
- Rebalanced Eltanin-class Light Cruiser:
  - Increased DP: 16 -> 18.
  - Increased CR to Deploy: 12 -> 15.
- Rebalanced Juni-class Cruiser:
  - Reduced FP: 15 -> 14.
  - Reduced Max Flux: 14000 -> 12000.
  - Increased Max Speed: 80 -> 85.
  - Increased Mass: 1100 -> 1200.
  - Reduced Shield Efficiency: 1.2 -> 1.0.
  - Increased Cargo: 125 -> 200.
  - Increased Fuel: 100 -> 250.
  - Increased CR to Deploy: 12 -> 15.
- Rebalanced Juza-class Heavy Cruiser:
  - Reduced OP: 35 -> 30.
  - Reduced FP: 25 -> 18.
  - Reduced Max Crew: 300 -> 250.
  - Reduced Cargo: 180 -> 150.
  - Increased Fuel: 150 -> 200.
  - Increased CR to Deploy: 12 -> 15.
  - Increased Peak CR: 320 -> 420.
- Rebalanced Kuma-class Battleship:
  - Changed Ship System: Safety Overrides -> Fortress Shield.
- Rebalanced Alwaid-class Battlecarrier:
  - Increased Max Flux: 30000 -> 46500.
  - Increased Flux Dissipation: 1200 -> 1850.
  - Increased Shield Efficiency: 0.9 -> 1.4.
  - Reduced CR %/Day: 4 -> 3.
- Rebalanced Asuia-class Cruiser:
  - Changed Ship System: Flare Launcher -> Temporal Dash.
  - Increased Max Flux: 10000 -> 16000.
  - Increased Flux Dissipation: 600 -> 800.
  - Increased Shield Efficiency: 0.6 -> 1.0.
  - Reduced CR %/Day: 5 -> 4.
- Rebalanced Sunsetter-class Battleship:
  - Reduced CR %/Day: 5 -> 3.
- Rebalanced Estoc Flak Cannon:
  - Increased Magizine size: 30 -> 60.
  - Increased Range: 350 -> 450.
  - Reduced Damage: 25 -> 20.
  - Increased Impact: 0 -> 1.
  - Reduced Turn Rate: 500 -> 150.
  - Improved Chargedown: 0.06 -> 0.04.
- Rebalanced Razor Flak Cannon:
  - Increased Impact: 0 -> 1.
- Rebalanced Longsword Autocannon:
  - Changed Tier: 2 -> 3.
  - Added Rarity: 0.5.
  - Increased Base Value: 1800 -> 2600.
  - Reduced Damage: 530 -> 440.
  - Increased Impact: 15 -> 75.
  - Increased Ammo: 6 -> 9.
  - Added Reload Mechanics: 0.35 ammo/sec, reload size 2.
  - Increased Chargedown: 1.5 -> 2.
  - Added Burst Fire: 2 shots per burst, 0.5s delay.
  - Increased Flux Cost: 400 -> 700.
- Rebalanced Mangonel Gun Launcher:
  - Increased Rarity: 0.8 -> 0.5.
- Rebalanced Twin Spear Cannon:
  - Increased Rarity: 0.9 -> 0.8.
- Rebalanced Fragarach Railgun:
  - Increased Turn Rate: 4 -> 6.
  - Increased Rarity: 0.6 -> 0.5.
- Rebalanced Particle Burst Lance:
  - Increased Rarity: 0.9 -> 0.8.
- Rebalanced Swordbreaker SRM Launcher:
  - Reduced Range: 1200 -> 1000.
  - Reduced Damage: 300 -> 250.
  - Removed EMP Damage: 300 -> 0.
  - Reduced Ammo: 90 -> 40.
  - Reduced Flight Time: 7 -> 6.
- Rebalanced Micro Bardiche MRM Launcher:
  - Reduced EMP Damage: 500 -> 250.
  - Increased Burst Delay: 0.25 -> 0.3.
  - Increased Flight Time: 6 -> 16.
- Rebalanced Bardiche MRM Launcher:
  - Reduced EMP Damage: 500 -> 250.
  - Increased Burst Size: 5 -> 8.
  - Increased Chargedown: 3 -> 5.
  - Increased Flight Time: 6 -> 16.
- Rebalanced all Halberd Torpedo variants:
  - Increased Damage: 4000 -> 5000.
- Rebalanced Bardiche MRM Launcher (built-in):
  - Reduced EMP Damage: 500 -> 250.
  - Added Burst Delay: 0.2s.
  - Increased Chargedown: 3 -> 4.
  - Increased Flight Time: 6 -> 16.
- Rebalanced Arquebus SRM Launcher:
  - Reduced Range: 1000 -> 800.
  - Reduced Damage: 100 -> 50.
  - Removed Impact: 5 -> 0.
  - Increased Chargedown: 3 -> 5.
  - Increased Launch Speed: 50 -> 150.
  - Increased Flight Time: 5 -> 6.
- Rebalanced Falchion-class Torpedo:
  - Reduced Range: 1500 -> 1200.
  - Reduced Missile HP: 500 -> 250.

TECHNICAL IMPROVEMENTS:
- Added GraphicsLib dependency.
- Consolidated XLII_RetreatDrive into unified XLII_DraconisHull hullmod.
- Reorganized hullmod package structure - moved status scripts to hullmods package.
- Reorganized mod plugin structure for better maintainability.
- Enhanced world generation system with custom terrain plugin architecture.
- Added custom terrain plugin system for The Rift.
- Improved AI core acquisition system logging.
- Updated MagicLib trail data integration.
- Refined settings.json configuration options.

MINOR IMPROVEMENTS:
- Added hull system icon for Phase Torpedo Array.
- Added hull system icon for Missile Barrage (venting flux visual).
- Updated graphics for the Fragarach, Bardiche and Halberd weapons.
- Enhanced character faction assignments for combat chatter.
- Improved character dialogue and personality variations.
- Updated missile graphics with glow layers (phase torpedoes, pike missiles).
- Improved weapon sound effects (Cestus autocannon, Longsword autocannon).
- Enhanced faction default ship roles.
- Code cleanup and optimization across multiple Java classes.

BUG FIXES:
- Fixed an AI Expedition related crash.
- Fixed AI Core theft intel notification not expiring.
- Fixed poorly implemented AdvancedGunneryControl mod support.

LICENSE:
- Added and specified licensing.

REMOVALS:
- REMOVED: Javelin MLRS weapon - replaced by Pike MRM system.
- REMOVED: Unused Sabre torpedo (standalone weapon) - integrated into ship system weapons.

Version 0.5.8 (Save-compatible with 0.5.7)
BUG FIXES:
- Forgot to add the updated default_ship_roles.json.
- Changed some of the military market tags.

Version 0.5.7 (Save-compatible with 0.5.6)
NEW CONTENT:
- NEW: Aldhibah-class Frigate - Fast attack frigate based off of the Wolf-class.
- NEW: Longsword Autocannon - Large ballistic weapon for sustained anti-shield firepower.

BALANCE CHANGES:
- Evasion Protocol ship system no longer lowers shields when active.
- Rebalanced Juni:
  - Increased Shield Efficiency: 1.4 -> 1.2.
- Rebalanced Thuban:
  - Reduced Cost: 20000 -> 18000.
- Rebalanced Alruba MK.I:
  - Reduced DP: 8 -> 6.
- Rebalanced Particle Burst Lance:
  - Reduced flux/s: 1250 -> 1000.
- Rebalanced Estoc Flak Cannon:
  - Reduced OP cost: 5 -> 4.
- Rebalanced all Bardiche MRM variants:
  - Increased EMP damage: 250 -> 500 (all variants).
  - Micro Bardiche: Reduced OP cost: 5 -> 4, increased max ammo: 50 -> 100.
  - Bardiche: Reduced OP cost: 10 -> 8, increased max ammo: 200 -> 400.

NEXERELIN CHANGES:
- Reworked diplomacy stuff:
  - Most major factions now start at a negative relationship.
- Increased colony expansion rate.
- Buffed ground combat effectiveness:
  - Added +25% attack multiplier.
  - Added +25% defense multiplier.
- Special forces adjustments:
  - Reduced special forces size multiplier: 4 -> 2. Was a little silly.
  - Implemented custom Roman numeral naming system for special forces fleets.

PROGRESSIVE DIFFICULTY ADJUSTMENTS:
- Accelerated AI core scaling timeline for faster mid/late-game progression:
  - Mid-game transition: cycle 226 -> 216 (5-10 cycles instead of 5-20).
  - Late-game start: cycle 226 -> 216.
  - Late-game peak: cycle 256 -> 236 (30 cycles instead of 50).
  - End-game start: cycle 256 -> 236.
  - Maximum saturation: cycle 306 -> 256 (50 cycles total instead of 100).
- Recheck interval for AI core assignment shortened: 120 days -> 60 days.
  - Note: Settings adjustable in data/config/settings.json.

MINOR IMPROVEMENTS:
- Updated the old MagicLib Bounty descriptions, finally.
- Updated ship variants across the fleet to fit OP changes.
- Reworked of Breach Jammer AI, should be MUCH better now.

Version 0.5.6 (Save-compatible with 0.5.5)
BUG FIXES:
- Forgot to add the DO_NOT_AIM tag for the Javelin.

Version 0.5.5 (Save-compatible with 0.5.4)
BUG FIXES:
- Removed some random values that were in ship_data.csv.

BALANCE CHANGES:
- Rebalanced Javelin MLRS:
  - Changed mauravbility to make it usable on rear facing mounts.
  - Added proper fading when reaching weapon range.
- Changed how XLII ships are costed:
  - REP cost is now 1/2/3/4 based on hull size.
  - Credit cost is still 2* the original hull.

Version 0.5.4 (Save-compatible with 0.5.3)
BUG FIXES:
- Fixed issue with Admiral August not selling the rights ships, stingy bastard.

BALANCE CHANGES:
- Rebalanced Aldibain:
  - Reduced OP cost: 14 -> 9.
- Rebalanced Tianyi:
    - Reduced OP cost: 16 -> 14.
- Rebalanced Arquebus SRM Launcher.
  - Reduced range: 1500 -> 1000.
  - Reduced burst size: 2 -> 1.

MINOR IMPROVEMENTS:
- Add FXs to the Fragarch Railgun.
- Changed .variant files.

Version 0.5.3 (Save-compatible with 0.5.2)
BALANCE CHANGES:
- Rebalanced Dziban:
  - Changed vanilla Lidar ship system to custom one.
    - Reduces max speed and acceleration/deceleration when used.
  - Max speed increased: 50 -> 60.
  - Acceleration increased: 20 -> 25.
  - Deceleration increased: 15 -> 20.
  - Max turn rate reduced: 12 -> 11.
  - Turn acceleration reduced: 12 -> 11.

MINOR IMPROVEMENTS:
 - Updated some hardpoint weapon sprites to better fit their mounts.
 - Added unique engine SFXs.

Version 0.5.2 (Save-compatible with 0.5.1)
NEW CONTENT:
- NEW: Dziban-class Battlecruiser - Long-range fire support battlecruiser.

BALANCE CHANGES:
- Rebalanced Kuma-class Battleship for greater weapon loadout flexibility:
  - Large turrets no longer have built-in Fragarach railguns (changed from BUILT_IN to HYBRID mounts).
  - Small wing turrets changed from HYBRID to BALLISTIC type.
  - Rear hardpoint changed from UNIVERSAL to MISSILE type.
  - Max flux reduced: 25000 -> 22500.
  - Flux dissipation reduced: 2250 -> 1600.
  - Shield arc increased: 180 -> 240.
  - Shield efficiency increased: 1.4 -> 1.2.
  - Max speed reduced: 50 -> 45.
  - Acceleration increased: 5 -> 12.
  - Deceleration increased: 10 -> 12.
  - Turn acceleration increased: 4 -> 6.
- Giausar-class OP reduced: 18 -> 10.
- Shaobi-class now classified as combat carrier (added COMBAT tag).
- Eltanin-class OP increased: 14 -> 16.
- Altais-class ordnance points increased: 280 -> 305.
- Rebalanced Twin Spear Cannon for reduced alpha strike capability (now fits more as a disabler):
  - Damage per shot reduced: 650 -> 400.
  - EMP damage per shot increased: 250 -> 500.
  - Turn rate reduced: 10 -> 9.
  - OP increased: 26 -> 28.
  - Flux cost increased: 550 -> 700.
  - Min Spread increased: 1 -> 2.
  - Max Spread increased: 5 -> 8.
- Fragarach Railgun OP increased: 28 -> 30.
- Mangonel Gun Launcher turn rate increased: 9 -> 12.
- Swordbreaker SRM Launcher missile health increased: 1 -> 25.
- Bardiche MRM Launcher (all variants) missile health increased: 50 -> 100.
- Halberd-class Torpedo ammo reduced: 3 -> 2, OP reduced: 12 -> 8.
- Halberd-class Torpedo Pod ammo increased: 4 -> 6, OP reduced: 14 -> 12.
- Arquebus SRM Launcher flight time increased: 3.75 -> 5.
- Falchion-class Torpedo missile health reduced: 1000 -> 500.
- ECM Suite (Tianyi-class Fighter) range reduced: 500 -> 250 to prevent excessive area denial.

ADVANCED GUNNERY CONTROL IMPROVEMENTS:
- Significantly expanded weapon AI tag support for better integration with Advanced Gunnery Control mod.

MINOR IMPROVEMENTS:
- Added more ships to the mission High Orbit.
- Added Dziban-class to title screen rotation.
- Updated fleet composition tables to include new battlecruiser.

Version 0.5.1 (Save-compatible with 0.5.0)
BUG FIXES:
- Fixed AI Core Fleet Scaling system not properly assigning cores to spawned fleets.
  - PREVIOUS BUG: System only processed fleets when standing down at bases, but fleets despawned in that state.
  - FIX: Removed standing down requirement - fleets now receive cores immediately when spawned (any assignment).
  - Added periodic recheck system (configurable interval, default: 120 days) to handle:
    - Coverage percentage increases as game progresses (30% -> 70% -> 100%).
    - Replacing AI cores lost in combat.
    - Filling new empty officer slots.
- Fixed AI Core distribution using incorrect cycle thresholds.
  - PREVIOUS BUG: Thresholds assumed relative cycles (0-100), but Starsector campaigns start at cycle ~206.
  - This caused fleets to immediately receive endgame AI cores (Alpha/Beta) instead of early-game Gamma cores.
  - FIX: Updated cycle thresholds to absolute values (206-306) to properly track campaign progression.
  - Settings.json updated with clear documentation of absolute cycle numbers.
- Fixed AI Core Theft intel incorrectly reporting self-theft after successful colony capture.
  - PREVIOUS BUG: When Draconis captured a colony, theft system would still trigger and create intel saying "Draconis stole from Draconis".
    - Trust Nobody Not Even Yourself...
  - FIX: Added faction ownership check - theft is now skipped if target market is already Draconis-owned.

Version 0.5.0
BUG FIXES:
- Reorganised the AI Raids/Colony Crisis, should be less of a mess.
- Fixed critical infinite loop bug in AI Core Donation system.
  - PREVIOUS BUG: Cores of the same tier would displace each other infinitely, causing 20+ second freezes and only admin positions receiving cores.
  - REDESIGNED: Core installation now processes by strict tier order (Alpha -> Beta -> Gamma).
  - Each tier has three phases: admin positions (Alpha only), empty industry slots, then upgrades of lower-tier cores.
  - Added proper upgrade validation using canUpgradeCore() - same-tier "upgrades" are now prevented.
  - Removed displaced core re-queuing logic - displaced cores are consumed by the upgrade (realistic behavior).
  - Improved upgrade weighting from 80% to 95% to make higher-tier cores more competitive when displacing lower-tier cores.
- Fixed Colony Crisis event not ending properly when defeated.
- Added condition to AI Core raids, the Fleet must actually survive to steal AI Cores.

NEW CONTENT:
- NEW: Alsafi-class Destroyer - Civilian-grade transport destroyer.
- NEW: Alruba Mk.II-class Frigate - Advanced upgrade of the original Alruba.
- NEW: "High Orbit" Mission - Engage Hegemony forces at Kori Station.
- NEW: Combat Chatter Support - Voice lines for Draconis crews.
  - Three personalities: Loyalist, Officer, and Veteran.
- NEW: Lore document "Draconis Lore.pdf" - Comprehensive background on the Draconis Alliance.
- NEW: Progressive difficulty scaling system - Draconis gains more and better AI Core officers as the game progresses to supplement their forces.

FACTION CHANGES:
- Renamed XLII Battlegroup sub-faction for clarity and lore consistency.
  - Updated all ship skins: alruba_FSDF -> alruba_fortysecond (and alwaid, errakis, juza, shaobi).
  - Renamed variants from FSDF to fortysecond naming convention.
  - Updated blueprint package: XLII_FSDF_package -> XLII_fortysecond_package.
  - All sprites and configuration files updated to match new naming.

BALANCE CHANGES:
- Rebalanced Altais-class Battlecruiser for improved flux management and doctrine alignment:
  - Hull mod: Expanded Missiles Racks -> Targeting Unit.
  - Flux vents increased: 39 -> 50.
  - Flux capacitors increased: 0 -> 4.
  - Large missile weapons: Halberd Pods (alternating) -> Bardiche (linked).
  - Medium missile weapons: Bardiche Singles (linked) -> Sabots (alternating).
  - Improved sustained combat capability with better flux stats.

- Missile Guidance Uplink -> Missile Control Matrix (comprehensive rework):
  - RENAMED: Missile Guidance Uplink is now Missile Control Matrix.
  - NEW: Slowly regenerates ammunition for large missile weapons.
    - Reload interval scales with weapon rate of fire and max ammo.
    - Formula ensures reload is always slower than fire rate.
    - Higher max ammo weapons reload individual missiles faster.
  - NEW: Increases missile health by +50%.
  - Updated icon to missile autofactory graphic.
  - Description emphasizes phased array targeting and autoforge fabrication.

- XLII Fleet hull mod significantly enhanced with passive missile defense:
  - NEW: Integrated electronic warfare suite provides area missile defense.
  - Defense range = (collision radius × 3) + hull size bonus.
    - Frigate: +200 range bonus
    - Destroyer: +150 range bonus
    - Cruiser: +50 range bonus
    - Capital: +0 range bonus (base range only)
  - Each hostile missile within range has 50% chance to be affected.
  - Effects: Either jam and disable missile OR convert to friendly control and retarget.
    - Jammed missiles: Disabled, damage set to 0, flamed out (blue particle effect).
    - Converted missiles: Ownership changed, retargeted at original firing ship, ECCM applied, extended flight time (green particle effect).
  - Only affects guided missiles for conversion; unguided missiles are always jammed.
  - Disabled when ship is overloaded, venting, phased, or retreating.
  - Missiles are processed once to avoid repeated dice rolls.
  - Description updated to reflect new defensive capabilities.

- Removed starting AI core from Kori's High Command facility.

MINOR IMPROVEMENTS:
- AI Core system code reorganization for better maintainability:
  - Moved AI core raid classes from intel/events/aicore/ to intel/aicore/raids/.
  - Improved package structure and separation of concerns.
  - Files moved: DraconisAICoreActivityCause, DraconisAICoreRaidFactor, DraconisAICoreRaidIntel, DraconisAICoreRaidManager.
- Mod plugin logging improvements:
  - Implemented proper Log4j Logger instead of direct Global.getLogger() calls.
  - Improved startup logging with clear mod initialization status.
  - Better error handling with try-catch blocks and fallback values.
- NEW: Configuration toggles for raid systems:
  - Added draconisEnableAICoreRaids setting (default: true).
  - Added draconisEnableRemnantRaids setting (default: true).
  - Settings loaded from settings.json with fallback on errors.
  - Status logged during mod initialization.

Version 0.4.3 (Save-compatible with 0.4.0)
- I may of missed some things... >.>
- Fixed multiple ConcurrentModificationException crashes in AI Core systems.
  - Added defensive collection copying when iterating over market industries.
  - Affects AI Core Theft, Remnant Raids, Donations, and Activity tracking systems.
  - Prevents crashes in multi-threaded campaign environment.

Version 0.4.2 (Save-compatible with 0.4.0)
- Fixed ConcurrentModificationException crash in AI Core Acquisition system during core redistribution.
  - Replaced for-each loop with index-based iteration to safely handle displaced cores.
  - Resolves crash when raiding markets with multiple AI cores.

Version 0.4.1 (Save-compatible with 0.4.0)
- Fixed ConcurrentModificationException crash in AI Core Donation system when displacing lower-tier cores.
  - Core installation now processes in rounds to safely handle displaced cores.
  - Added infinite loop protection with maximum round limit.

Version 0.4.0
- WARNING: Breaking change - NOT compatible with saves from 0.3.5 or earlier.
  - Complete internal restructuring: all IDs renamed from fsdf_ to XLII_ prefix.
  - All ships, weapons, systems, and variants have new identifiers.

NEW CONTENT:
- Added a new Character, the enigmatic Fleet Admiral Emil August!
- Added new sub-faction: XLII Battlegroup (Draconis Defence Special Forces).
  - Elite fleet compositions with carrier doctrine.
  - 5 new special forces ship skins: Alruba, Alwaid, Errakis, Juza, Shaobi (purchased from Emil August!)
  - XLII-exclusive variants for high-end fleet encounters.
- Added 3 faction music tracks for dynamic encounter atmosphere.
  - Unique themes for friendly, neutral, and hostile encounters.
  - Thanks to the brilliant work of Ed Harrison (with permissions)!
- Added support for Commissioned Crews.
- Added Halberd Pod weapon variant.

NEXERELIN INTEGRATION:
- NEW: AI Core Acquisition System - Draconis actively pursues AI cores.
  - Faction will identify and raid markets with valuable AI cores.
  - High Value Target scanner prioritizes core-rich markets.
  - Steel Curtain market condition marks high-security targets.
  - New intel events track AI core thefts and targeted raids.
  - Comprehensive raid monitoring with success/failure tracking.
  - Strict engagement requirements: combat proximity + minimum dwell time.
  - Diplomatic strain system based on stolen core value:
    - Alpha Core: -4.8 disposition
    - Beta Core: -3.2 disposition
    - Gamma Core: -2.0 disposition
  - Bidirectional diplomatic penalties (affects both factions).
- Enhanced strategic AI behavior and faction interactions.
- Improved patrol fleet spawning and hostile activity systems.

BALANCE CHANGES:
- Time Slip system rebalanced for faster cycling.
  - Flux cost: 4 -> 3.
  - Cooldown: 10s -> 6s.
  - Charges: 2 -> 1 (single powerful use).
  - Regen time: 10s -> 12s.
- Retreat Drive (Draconis warp mechanic) major improvements.
  - Now only triggers on Direct Retreat order (you now have the option for a regular retreat).
  - Faster charge times: Destroyer 8->7s, Cruiser 10->8s, Capital 12->9s.
  - Fixed bug with disabled ships warping despite being dead.
  - Added safety checks to prevent crashes during warp.

QUALITY OF LIFE:
- [REDACTED] hull mod description completely rewritten.
  - Clear mechanics explanation with exact percentages.
  - 20% hull restore, 25% per module, 35% cooldown reduction.
- Campaign rules added for ship purchasing and faction interactions.
- Expanded descriptions for ships, weapons, and systems.
- Complete README rewrite with new Fafnir Civil War lore.

TECHNICAL IMPROVEMENTS:
- Updated faction configuration and fleet doctrines.
- Improved sound balancing and audio system integration.
- Updated engine styles and visual effects.
- New icons for intel events, markets, and cargo items.
- Increased AI fleet variant diversity.
- Fixed ship system CSV references.
- Corrected sound and visual effect file paths.
- Fixed variant loadouts and fleet compositions.

Version 0.3.5
- Added new mechanics to the Sunsetter bounty fight.

Version 0.3.4
- NEW: Kuma-class capital ship.
- Added Breach Jammer ship system to the Juni-class.
- CHANGED: Several weapons now have DoT effect instead of armor stripping.
  - See Manongel Gun Launcher for example.
- Added more ship and weapon descriptions.
- Added/changed ship variants.
- Improved ship system AI behavior.
- Removed deprecated weapon effect scripts.
- Rebalanced built-in hullmods.
- Adjusted Sunsetter bounty fight.
- Updated faction configurations.

Version 0.3.3
- Updated Draconis faction configuration.
- Adjusted weapon and hull frequencies.
- Updated ship roles and market industry setup.
- Fixed sound references and CSV trail data for weapon effects.

Version 0.3.2
- CHANGED: Particle Burst Lance damage type from ENERGY to HIGH_EXPLOSIVE.

Version 0.3.1
- Rebalanced Rapier Autocannon and Particle Burst Lance.
- Updated ship variants accordingly.
- Finished incomplete names and descriptions.

Version 0.3.0
- NEW: Giausar-class Destroyer.
- NEW: Draconis warp retreat mechanic.
  - Ships now warp out instead of direct retreating.
- Improved several ship sprites.
- Updated projectile FX (.proj files).
- Reworked Twin Spear Cannon.
- Rebalanced Mangonel Gun Launcher (nerf).
- Rebalanced Eltanin-class Cruiser (nerf).
  - Changed large mount from Ballistic to Hybrid.
- Rebalanced Juza-class Cruiser.
- Added Bardiche missile AI.
- General weapon balance pass.
- Updated fleet doctrine.

Version 0.2.3
- NEW: Juza-class Heavy Cruiser.
- NEW: Juni-class Cruiser.
- Revised Bardiche missile role.
- Added 1 second cooldown to Evasion Protocol ship system.
- Rebalanced Estoc weapon.
  - Increased fire rate, reduced damage.
  - Updated weapon texture.
- Reduced Nodus engagement range.
- Improved audio balancing.
- Buffed Particle Burst Lance.
  - Changed weapon SFX.
- Reworked Emergency Power ship system.
  - Removed passive CR reduction.
  - Now consumes small amount of CR on activation.
  - Increased cooldown.
- Restored Asuia's built-in weapon.
  - Moved Asuia to bounty encounter.
- Reduced chaff/flare count (performance improvement).
- Reworked Errakis-class ship.
- Updated fleet doctrine.

Version 0.2.2
- Fixed ship variant configurations.

Version 0.2.1
- Added new Estoc SFX.
  - Reduced fire rate.
  - Increased reload time.
  - Improved accuracy.
- Updated MagicLib bounties (descriptions still needed).
- Added new Estoc flak sounds.
- Removed AI core spam from Kori and Vorium markets.
- Fixed Kori industry configuration.
- Fixed Halberd shield pierce mechanic.
- Removed extra engines from Asuia.

Version 0.2.0
- Added forums link to version file.
- NEW: Nodus fighter drone.
  - Includes built-in Arquebus SRM Launcher.
- NEW: Estoc flak cannon (small Razor Flak variant).
- Added missing Bardiche descriptions.
- Improved Phase Mine FX for Shaowei.
- Improved faction icon/banner.
- Updated Mk.II Durendal Cannon sprite.
- Improved Pulsar Jammer ship system.
  - AI now issues defend order when using system.
- Improved Emergency Power ship system.
  - Scales with hull size (less effective on larger ships).
  - Increased duration and cooldown.
  - Reduced charge-up and charge-down times.
- Buffed Alwaid Battlecarrier.
- Rebalanced Rapier Autocannon.
  - Ammo: 24 -> 6.
  - Range: 800 -> 900.
- Reduced fighter engagement ranges across the board.
- Fixed Razor Flak explosion FX.
- Fixed wing_data tagging issues.
- MAJOR: Reworked star system generation.
  - No longer uses .json for market definitions.
  - Fixes Fafnir system dual-faction ownership bug.
  - WARNING: Not backwards compatible with old saves.
- Improved engine FX.

Version 0.1.0
- Initial release.
