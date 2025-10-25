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
- Added 3 faction music tracks (friendly, neutral, hostile encounters).
- Added new character portraits and NPC initialization system.
- Added campaign rules for ship purchasing and faction interactions.
- Added commissioned crews bonus (CHM_XLII_draconis hullmod).
- Rebalanced Sunsetter-class Battleship.
  - Hull increased: 20,000 → 30,000
  - Armor reduced: 2,000 → 1,500
  - Armor modules buffed: 15,000 HP / 1,200 armor (was 12,500 HP / 2,000 armor)
- Rebalanced Time Slip ship system.
  - Flux cost: 4 → 3
  - Cooldown: 10s → 6s
  - Charges: 2 → 1
  - Regen time: 10s → 12s
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
- And more...

Version 0.3.5
- Added some new mechanics to the Sunsetter bounty fight.

Version 0.3.4
- Introduced the Kuma-class capital ship.
- Added the Breach Jammer ship system to the Juni-class.
- Several weapons have been given a "DoT" effect, instead of their old armour stripping effect. (See Manongel Gun Launcher)
- Added more descriptions.
- Added/Changed ship variants.
- Improved ship system AI; they should work a lot better now.
- Removed deprecated weapon effect scripts.
- Balancing changes to built-in hullmods.
- Adjusted Sunsetter bounty fight.
- Adjusted faction configs. Again. He can't keep getting away with it!
- And more...

Version 0.3.3
- Updated Draconis faction config.
- Adjusted weapon and hull frequencies, ship roles, and market industry setup.
- Minor fixes to sound references and CSV trail data for new weapon effects.

Version 0.3.2
- Changed Particle Burst Lance damage type, ENERGY -> HIGH_EXPLOSIVE.

Version 0.3.1
- Changed weapon stats for Rapier Autocannon and Particle Burst Lance.
- Updated ship variants to adjust.
- Updated names and descriptions that were unfinished.

Version 0.3.0
- Added new Destroyer, Giausar-class.
- Added a mechanic to Draconis ships, they will now warp out of combat instead of direct retreating.
- Improved a few sprites.
- Updated a few .proj FXs.
- Reworked the Twin Spear Cannon.
- Nerfed the Mangonel Gun Launcher.
- Nerfed the Eltanin-class Cruiser.
- Cahnge Eltanin-class Cruiser large mount from Ballistic to Hybrid.
- Changed the Juza-class Cruiser balance.
- Added Bardiche missile AI.
- Limited weapon balance pass.
- Changed fleet doctrine, again...

Version 0.2.3
- Added new Heavy Cruiser, Juza-class.
- Added new Cruiser, Juni-class.
- Revised the role of the Bardiche missiles.
- Add a 1 second cooldown to Evasion Protocol ship system.
- Raised the Estoc's firerate and lowered the damage.
    - Updated the Estoc texture to make it look a little sharper
- Lowered Nodus engagement range to something reasonable.
- Adjusted audio balancing.
- Buffed the Particle Burst Lance.
    - Changed the Particle Lance's SFX.
- Removed the Emergency Power's passive reduction to CR. In it's place I've made it so the system just removes a very small amount of CR on use.
    - Increased Emergency Power's cooldown.
- Added back the Asuia's built-in weapon.
    - Moved the Asuia to a bounty.
- Reduced the number of chaff/flare fired. Got too laggy in some situations.
- Reworked the Errakis.
- Changed fleet doctrine.
- And a lot more...

Version 0.2.2
- Fixed variants setup.

Version 0.2.1
- Added new Estoc sfx.
  - Lowered Estoc firerate.
  - Increased reload time.
  - Increased accuracy.
- Fixed up the MagicLib bounties. Still need descriptions though.
- Added new sounds to the Estoc flak.
- Removed AI core spam on Kori and Vorium.
- Fixed Kori industry.
- Fixed broke shield pierce mechanic on the Halberd.
- Removed some extra engines on the Asuia.

Version 0.2.0
- Added forums link to versioning.
- Added missing Bardiche descriptions.
  - Added the Nodus drone, a fighter drone.
  - Added new missile launcher, the Arquebus SRM Launcher, a built-in for the Nodus.
- Added Estoc flak cannon, a small version of the Razor Flak.
- Improved Phase Mine FX for the Shaowei.
- Improved the faction icon/banner.
- Improved the Mk.II Durendal Cannon sprite.
  - Improved Pulsar Jammer ship system.
  - Now issues a defend order when used by the AI, to make them exploit it better.
- Improved Emergency Power ship system.
  - Less effective the larger the hullsize.
  - Increased duration and cooldown.
  - Reduced chargeup and chargedown times.
- Buffed the Alwaid Battlecarrier.
- Reduced the Rapier Autocannon's ammo count from 24 to 6.
  - Increased the Rapier Autocannon's range from 800 to 900.
- Reduced max engagement range of most fighters
- Fixed the FX of the Razor Flak's explosions.
- Fixed some bad wing_data tagging.
- Reworked Star System generation to avoid using a .json for markets.
  - Should fix the issue with the Fafnir system markets being owned by two different factions.
  - Unfortunately, I'm pretty sure this stops the update from being backwards compatible.
- Improved Engine FX.

Version 0.1.0
- Initial Release.