Version 0.5.7 (Save-compatible with 0.5.6)
NEW CONTENT:
- NEW: Aldhibah-class Frigate - Fast attack frigate based off of the Wolf-class.
- NEW: Longsword Autocannon - Large ballistic weapon for sustained anti-shield firepower.

BALANCE CHANGES:
- Evasion Protocol ship system no longer lowers shields when active.
- Rebalanced Juni:
  - Increased Shield Efficiency: 1.4 → 1.2.
- Rebalanced Thuban:
  - Reduced Cost: 20000 → 18000.
- Rebalanced Alruba MK.I:
  - Reduced DP: 8 → 6.
- Rebalanced Particle Burst Lance:
  - Reduced flux/s: 1250 → 1000.
- Rebalanced Estoc Flak Cannon:
  - Reduced OP cost: 5 → 4.
- Rebalanced all Bardiche MRM variants:
  - Increased EMP damage: 250 → 500 (all variants).
  - Micro Bardiche: Reduced OP cost: 5 → 4, increased max ammo: 50 → 100.
  - Bardiche: Reduced OP cost: 10 → 8, increased max ammo: 200 → 400.

NEXERELIN CHANGES:
- Reworked diplomacy stuff:
  - Most major factions now start at a negative relationship.
- Increased colony expansion rate.
- Buffed ground combat effectiveness:
  - Added +25% attack multiplier.
  - Added +25% defense multiplier.
- Special forces adjustments:
  - Reduced special forces size multiplier: 4 → 2. Was a little silly.
  - Implemented custom Roman numeral naming system for special forces fleets.

PROGRESSIVE DIFFICULTY ADJUSTMENTS:
- Accelerated AI core scaling timeline for faster mid/late-game progression:
  - Mid-game transition: cycle 226 → 216 (5-10 cycles instead of 5-20).
  - Late-game start: cycle 226 → 216.
  - Late-game peak: cycle 256 → 236 (30 cycles instead of 50).
  - End-game start: cycle 256 → 236.
  - Maximum saturation: cycle 306 → 256 (50 cycles total instead of 100).
- Recheck interval for AI core assignment shortened: 120 days → 60 days.
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
  - Reduced OP cost: 14 → 9.
- Rebalanced Tianyi:
    - Reduced OP cost: 16 → 14.
- Rebalanced Arquebus SRM Launcher.
  - Reduced range: 1500 → 1000.
  - Reduced burst size: 2 → 1.

MINOR IMPROVEMENTS:
- Add FXs to the Fragarch Railgun.
- Changed .variant files.

Version 0.5.3 (Save-compatible with 0.5.2)
BALANCE CHANGES:
- Rebalanced Dziban:
  - Changed vanilla Lidar ship system to custom one.
    - Reduces max speed and acceleration/deceleration when used.
  - Max speed increased: 50 → 60.
  - Acceleration increased: 20 → 25.
  - Deceleration increased: 15 → 20.
  - Max turn rate reduced: 12 → 11.
  - Turn acceleration reduced: 12 → 11.

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
  - Max flux reduced: 25000 → 22500.
  - Flux dissipation reduced: 2250 → 1600.
  - Shield arc increased: 180 → 240.
  - Shield efficiency increased: 1.4 → 1.2.
  - Max speed reduced: 50 → 45.
  - Acceleration increased: 5 → 12.
  - Deceleration increased: 10 → 12.
  - Turn acceleration increased: 4 → 6.
- Giausar-class OP reduced: 18 → 10.
- Shaobi-class now classified as combat carrier (added COMBAT tag).
- Eltanin-class OP increased: 14 → 16.
- Altais-class ordnance points increased: 280 → 305.
- Rebalanced Twin Spear Cannon for reduced alpha strike capability (now fits more as a disabler):
  - Damage per shot reduced: 650 → 400.
  - EMP damage per shot increased: 250 → 500.
  - Turn rate reduced: 10 → 9.
  - OP increased: 26 → 28.
  - Flux cost increased: 550 → 700.
  - Min Spread increased: 1 → 2.
  - Max Spread increased: 5 → 8.
- Fragarach Railgun OP increased: 28 → 30.
- Mangonel Gun Launcher turn rate increased: 9 → 12.
- Swordbreaker SRM Launcher missile health increased: 1 → 25.
- Bardiche MRM Launcher (all variants) missile health increased: 50 → 100.
- Halberd-class Torpedo ammo reduced: 3 → 2, OP reduced: 12 → 8.
- Halberd-class Torpedo Pod ammo increased: 4 → 6, OP reduced: 14 → 12.
- Arquebus SRM Launcher flight time increased: 3.75 → 5.
- Falchion-class Torpedo missile health reduced: 1000 → 500.
- ECM Suite (Tianyi-class Fighter) range reduced: 500 → 250 to prevent excessive area denial.

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
    - Coverage percentage increases as game progresses (30% → 70% → 100%).
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
  - REDESIGNED: Core installation now processes by strict tier order (Alpha → Beta → Gamma).
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
  - Updated all ship skins: alruba_FSDF → alruba_fortysecond (and alwaid, errakis, juza, shaobi).
  - Renamed variants from FSDF to fortysecond naming convention.
  - Updated blueprint package: XLII_FSDF_package → XLII_fortysecond_package.
  - All sprites and configuration files updated to match new naming.

BALANCE CHANGES:
- Rebalanced Altais-class Battlecruiser for improved flux management and doctrine alignment:
  - Hull mod: Expanded Missiles Racks → Targeting Unit.
  - Flux vents increased: 39 → 50.
  - Flux capacitors increased: 0 → 4.
  - Large missile weapons: Halberd Pods (alternating) → Bardiche (linked).
  - Medium missile weapons: Bardiche Singles (linked) → Sabots (alternating).
  - Improved sustained combat capability with better flux stats.

- Missile Guidance Uplink → Missile Control Matrix (comprehensive rework):
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
  - Flux cost: 4 → 3.
  - Cooldown: 10s → 6s.
  - Charges: 2 → 1 (single powerful use).
  - Regen time: 10s → 12s.
- Retreat Drive (Draconis warp mechanic) major improvements.
  - Now only triggers on Direct Retreat order (you now have the option for a regular retreat).
  - Faster charge times: Destroyer 8→7s, Cruiser 10→8s, Capital 12→9s.
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
  - Ammo: 24 → 6.
  - Range: 800 → 900.
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