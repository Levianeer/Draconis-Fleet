Version 0.4.0
- MAJOR: Complete internal restructuring - renamed all IDs from fsdf_ to XLII_ prefix.
  - WARNING: This is a breaking change for save compatibility.
  - All ships, weapons, systems, and variants have been renamed.
- Added new sub-faction: XLII Battlegroup (Draconis Defence special forces).
  - New faction with unique fleet compositions and variants.
  - Added 5 new ship skins (Alruba, Alwaid, Errakis, Juza, Shaobi).
  - FSDF-specific variants for elite ships.
- NEW: AI Core Acquisition System (Nexerelin integration).
  - Draconis will now actively seek and raid markets with AI cores.
  - New intel events for AI core thefts and targeted raids.
  - Diplomatic strain system when stealing AI cores.
  - Steel Curtain market condition for high-security targets.
  - High Value Target scanner identifies core-rich markets.
  - Comprehensive raid monitoring and success tracking.
- IMPROVED: AI Core Raid System - Major reliability overhaul.
  - Added 90-day initial delay before first raid can trigger (configurable).
  - Implemented strict engagement requirements: fleet must be in combat AND within 200 units of target.
  - Added 2-day success delay after engagement conditions are met (configurable, default 12 days).
  - Minimum 2 days at target before engagement check begins (configurable).
  - Fleet destruction during success countdown now properly cancels the raid.
  - Override isFailed() to prevent premature raid failure while success conditions are being met.
  - Added comprehensive debug logging to track engagement conditions in real-time.
  - Fixed issue where raids could succeed/fail simultaneously.
  - Fixed issue where raids could trigger success before fleets actually engaged the target.
- FIXED: AI Core raids now properly trigger diplomatic strain (Nexerelin integration).
  - Stolen AI cores now apply negative diplomatic events based on core type.
  - Alpha Core: -4.8 disposition, Beta Core: -3.2, Gamma Core: -2.0.
  - Strain applies to both Draconis and the victim faction (bidirectional).
  - Only applies strain when cores are successfully installed.
- CLEANUP: Removed unused daysElapsed variable from DraconisAICoreRaidManager.
- Added 3 faction music tracks (friendly, neutral, hostile encounters).
- Added new character portraits and NPC initialization system.
- Added campaign rules for ship purchasing and faction interactions.
- Added commissioned crews bonus (CHM_XLII_draconis hullmod).
- Rebalanced Sunsetter-class Battleship.
  - Hull increased: 20,000 → 30,000.
  - Armor reduced: 2,000 → 1,500.
  - Armor modules buffed: 15,000 HP / 1,200 armor (was 12,500 HP / 2,000 armor).
- Rebalanced Time Slip ship system.
  - Flux cost: 4 → 3.
  - Cooldown: 10s → 6s.
  - Charges: 2 → 1.
  - Regen time: 10s → 12s.
- Improved Retreat Drive (Draconis warp mechanic).
  - Now only triggers on Direct Retreat order (prevents accidental warps).
  - Reduced charge times: Destroyer 8→7s, Cruiser 10→8s, Capital 12→9s.
  - Fixed critical bugs preventing dead ships from warping.
  - Added comprehensive safety checks to prevent crashes.
- Updated Incomprehensible Horrors hull mod description.
  - Complete rewrite with clear mechanics explanation.
  - Now shows exact percentages (20% hull restore, 25% per module, 35% cooldown).
  - No mechanical changes, improved user experience.
- Added new Halberd Pod variant weapon.
- Updated faction configuration and fleet doctrines.
- Improved hostile activity system with crisis management.
- Enhanced patrol fleet spawning system.
- Updated Exerelin strategic AI integration.
- Expanded descriptions for ships, weapons, and systems.
- Complete README rewrite with new lore (Fafnir Civil War background).
- Improved sound balancing and audio references.
- Updated engine styles and visual effects.
- New icons for intel, markets, and cargo.
- Added variant diversity for AI fleets.
- Fixed ship system CSV references after rename.
- Corrected sound and visual effect paths.
- Fixed variant loadouts and fleet compositions.
- Improved stability for warp drive mechanics.

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