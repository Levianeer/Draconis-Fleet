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