# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Draconis Fleet is a full faction mod for Starsector 0.98a-RC8. It adds the Draconis Alliance - a fractured military coalition with fast, missile-focused ships built from wartime necessity. The mod includes 20+ custom ships, 25+ weapons, 12+ ship systems, strategic AI integration with Exerelin, and advanced combat mechanics.

**Required Dependencies**: MagicLib, LazyLib
**Optional Dependencies**: Nexerelin (enables AI core acquisition system)

## Build & Development

### Compilation
The project uses IntelliJ IDEA with direct compilation to the Starsector mods folder. There are no Gradle, Maven, or Ant build files.

**Build Configuration**:
- JDK: Azul Java 17
- Output: Compiles to `jars/Draconis.jar`
- Deployment: Build artifact copies entire project to `D:/Games/Starsector/mods/Draconis-Fleet`
- Build-on-make is enabled in `.idea/artifacts/Draconis_Fleet_Mod.xml`

**To compile**: Use IntelliJ's Build menu or Ctrl+F9. The artifact is configured to copy the entire mod folder to Starsector's mods directory.

**Testing**: No formal unit tests exist. Test changes by:
1. Building the mod in IntelliJ
2. Launching Starsector
3. Checking logs in Starsector's log folder for errors

### Important Libraries

**MagicLib** (`data.scripts.util.*`, `org.magiclib.*`):
- `MagicTargeting`: Used for missile AI target selection
- `MagicRender`: Screen-space rendering utilities

**LazyLib** (`org.lazywizard.lazylib.*`):
- `FastTrig`: Optimized trigonometric functions (use instead of Math.sin/cos in combat loops)
- `MathUtils`: Distance calculations, angle conversions
- `VectorUtils`: Vector operations (facing calculations, angle to target)
- `AIUtils`: Best intercept point calculations for guided missiles
- `CombatUtils`: Missile range and combat utilities

**Nexerelin** (optional, `exerelin.campaign.ai.*`):
- Detected via `Global.getSettings().getModManager().isModEnabled("nexerelin")`
- Enables strategic AI concerns and actions
- Required for AI core acquisition system

## Architecture

### Code Organization

Base package: `levianeer.draconis`

```
src/levianeer/draconis/
├── XLII_ModPlugin.java              # Entry point - mod initialization, missile AI routing
└── data/
    ├── campaign/
    │   ├── ai/                       # Strategic AI (Exerelin integration)
    │   │   ├── actions/             # DraconisAICoreRaidAction
    │   │   └── concerns/            # DraconisHighValueTargetConcern
    │   ├── econ/                    # XLII_HighCommand (patrol fleet spawning)
    │   ├── intel/
    │   │   └── aicore/              # AI core theft system (6 components)
    │   ├── econ/conditions/         # Market conditions (Steel Curtain, High Value Target)
    │   ├── characters/              # NPC initialization
    │   ├── ids/                     # Constants (Factions, FleetTypes)
    │   └── rulecmd/                 # Campaign rules/dialog commands
    └── scripts/
        ├── world/                   # XLII_WorldGen, sector generation
        ├── ai/                      # Combat AI (missiles, ship systems)
        ├── hullmods/                # Hull modification implementations
        ├── shipsystems/             # Ship system stats scripts
        └── weapons/                 # Weapon on-hit effects
```

### Key Systems

#### 1. Mod Initialization (XLII_ModPlugin.java)

Entry point extending `BaseModPlugin`. Key methods:
- `onApplicationLoad()`: Detects Nexerelin, sets up config
- `onGameLoad()`: Registers campaign scripts (hostile activity, steel curtain, AI core system)
- `pickMissileAI()`: Routes missiles to custom AI based on weapon ID

Pattern for adding new campaign scripts:
```java
@Override
public void onGameLoad(boolean newGame) {
    Global.getSector().addScript(new YourCampaignScript());
}
```

#### 2. Combat AI Scripts

**Missile AI** (`XLII_magicMissileAI.java`, `XLII_antiMissileAI.java`):
- Implements `MissileAIPlugin` and `GuidedMissileAI`
- Uses `AIUtils.getBestInterceptPoint()` for target leading
- Wave motion pattern with configurable amplitude/timing
- ECCM support and targeting priority system

**Ship System AI** (ECM/EAM/ESW Suite AI):
- Implements `ShipSystemAIScript`
- Evaluates threats in `advance()` and returns 0-1 activation level
- Uses proximity calculations and threat weighting
- Adjusts thresholds based on ship flux level

Pattern for new ship system AI:
```java
public class XLII_YourSystemAI implements ShipSystemAIScript {
    public void advance(float amount, Vector2f missileDangerDir,
                       Vector2f collisionDangerDir, ShipAPI ship) {
        // Calculate threat level (0-1)
        float threat = calculateThreat(ship);
        if (threat > ACTIVATION_THRESHOLD) {
            ship.useSystem();
        }
    }
}
```

#### 3. Ship System Stats (shipsystems/)

Extends `BaseShipSystemScript` and implements `ShipSystemStatsScript`.

Pattern:
```java
public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    if (state == State.OUT) {
        stats.getMaxSpeed().unmodify(id);  // Remove modifiers when inactive
    } else {
        stats.getMaxSpeed().modifyFlat(id, BONUS * effectLevel);
    }
}
```

Key: Use `id` parameter consistently for all modifiers to enable proper cleanup.

#### 4. Hull Modifications (hullmods/)

Extends `BaseHullMod`. Common patterns:
- Override `applyEffectsBeforeShipCreation()` for permanent stat changes
- Override `advanceInCombat()` for per-frame effects
- Use `stats.getVariant().hasHullMod("id")` for conditional effects
- Remember hull size scaling (frigate/destroyer/cruiser/capital)

Example from `XLII_FortySecond.java`:
```java
public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    float bonus = 0f;
    switch (hullSize) {
        case FRIGATE: bonus = 250f; break;
        case DESTROYER: bonus = 500f; break;
        case CRUISER: bonus = 750f; break;
        case CAPITAL_SHIP: bonus = 1000f; break;
    }
    stats.getSightRadiusMod().modifyFlat(id, bonus);
}
```

#### 5. Weapon Effects (weapons/)

Implements `OnHitEffectPlugin`. Pattern:
```java
public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                  Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult,
                  CombatEngineAPI engine) {
    // Spawn visual effects
    engine.addHitParticle(point, velocity, size, brightness, duration, color);

    // Apply damage/EMP
    engine.spawnEmpArc(source, point, target, target, DamageType.ENERGY,
                      damage, empDamage, maxRange, impactSound, arcWidth,
                      arcColor, glowColor);

    // Check for shield hit to adjust effects
    if (shieldHit) {
        // Reduced effects on shields
    }
}
```

#### 6. Strategic AI (Exerelin Integration)

Configured in `data/config/exerelin/strategicAIConfig.json`:
- **Concerns**: Evaluate strategic priorities (extends `MarketRelatedConcern`)
- **Actions**: Execute strategic decisions (extends `StrategicAction`)

Java implementations in `data/campaign/ai/`:
- `DraconisHighValueTargetConcern.java`: Identifies markets with AI cores
- `DraconisAICoreRaidAction.java`: Executes raids on those markets

Pattern:
```java
public class YourConcern extends MarketRelatedConcern {
    public float evaluate(SAIContext context, MarketAPI market) {
        // Return priority multiplier (1.0 = normal, >1.0 = higher priority)
        return hasDesiredResource(market) ? 2.5f : 1.0f;
    }
}
```

#### 7. AI Core Acquisition System

Multi-component system in `data/campaign/intel/aicore/`:
1. **Scanner** (`DraconisSingleTargetScanner`): Identifies high-value targets with AI cores
2. **Concern** (`DraconisHighValueTargetConcern`): Raises priority for markets with cores
3. **Action** (`DraconisAICoreRaidAction`): Executes raid missions
4. **Monitor** (`DraconisTargetedRaidMonitor`): Tracks raid outcomes
5. **Listener** (`DraconisAICoreTheftListener`): Processes successful thefts
6. **Condition** (`DraconisHighValueTargetCondition`): Marks markets in campaign layer

Daily theft limit prevents duplicate raids on same market.

### Data-Code Integration

#### CSV to Java Linking

**Hull Mods** (`data/hullmods/hull_mods.csv`):
```csv
name,id,script,...
XLII Fleet,XLII_fortysecond,levianeer.draconis.data.scripts.hullmods.XLII_FortySecond,...
```

**Ship Systems**: ID in `ship_systems.csv` must match class name pattern
- CSV: `XLII_evasionprotocol`
- Java: `XLII_EvasionProtocolStats.java`

**Weapons**: On-hit effects linked in weapon CSV or `.wpn` files via `onHitEffect` field

#### JSON Configuration

**Faction Config** (`data/world/factions/XLII_draconis.faction`):
- Defines ship/weapon knowledge via tags (`XLII_package_bp`)
- Sets diplomacy traits, fleet composition, market spawning

**Strategic AI Config** (`data/config/exerelin/strategicAIConfig.json`):
- Registers concerns and actions with full class paths
- Assigns module types (MILITARY, ECONOMIC)
- Sets tags for filtering

### Naming Conventions

**Consistent Prefix**: `XLII_` used throughout for namespacing
- Ships: `XLII_thuban`, `XLII_taurusonix`
- Weapons: `XLII_rapier`, `XLII_swordbreaker`
- Systems: `XLII_ecm_suite`, `XLII_evasionprotocol`
- Hull Mods: `XLII_fortysecond`

**Class Naming**:
- Stats: `XLII_<SystemName>Stats.java`
- AI: `XLII_<SystemName>AI.java`
- Effects: `XLII_<WeaponName><EffectType>>Effect.java`

## Common Development Tasks

### Adding a New Ship System

1. Add definition to `data/hulls/ship_data.csv`
2. Create `.ship` file in `data/hulls/`
3. Create `.variant` file in `data/variants/`
4. Add sprite to `graphics/ships/`
5. (Optional) Create ship system script in `src/levianeer/draconis/data/scripts/shipsystems/XLII_YourSystemStats.java`
6. (Optional) Create ship system AI script in `src/levianeer/draconis/data/scripts/ai/XLII_YourSystemAI.java`
7. (Optional) Add `.system` file in `data/shipsystems/` with ship system stats script reference

### Adding a New Weapon with Effects

1. Add weapon definition to `data/weapons/weapon_data.csv`
2. Create `.wpn` file in `data/weapons/`
3. Add sprite to `graphics/weapons/`
4. Create effect class in `src/levianeer/draconis/data/scripts/weapons/XLII_YourWeaponOnHitEffect.java`
5. Reference effect in `.wpn` file: `"onHitEffect":"levianeer.draconis.data.scripts.weapons.XLII_YourWeaponOnHitEffect"`

### Adding a New Guided Missile

1. Add weapon entry to `weapon_data.csv` with type MISSILE
2. Create `.wpn` file
3. Implement `MissileAIPlugin` in `src/levianeer/draconis/data/scripts/ai/XLII_YourMissileAI.java`
4. Add routing logic to `XLII_ModPlugin.pickMissileAI()`:
```java
if (missile.getProjectileSpecId().equals("XLII_your_missile")) {
    return new PluginPick<>(new XLII_YourMissileAI(missile, launchingShip), PickPriority.MOD_SPECIFIC);
}
```

### Adding Campaign Mechanics

1. Implement `EveryFrameScript` for persistent campaign behavior
2. Register in `XLII_ModPlugin.onGameLoad()`:
```java
Global.getSector().addScript(new YourCampaignScript());
```
3. Return `false` from `isDone()` to keep script running
4. Use `advance(float amount)` for per-frame logic

### Exerelin Strategic AI

1. Add concern/action definition to `data/config/exerelin/strategicAIConfig.json`
2. Create Java class in `src/levianeer/draconis/data/campaign/ai/concerns/` or `actions/`
3. Extend `MarketRelatedConcern` or `StrategicAction`
4. Implement `evaluate()` for concerns or `execute()` for actions
5. Test by checking logs for strategic AI execution

## Code Style & Patterns

### Performance Considerations

- Use `FastTrig` from LazyLib instead of `Math.sin/cos` in combat loops
- Cache frequently accessed objects (don't call `Global.getSector()` every frame)
- Use `CombatUtils.getMissilesWithinRange()` instead of iterating all projectiles
- Early return in `advance()` methods when system inactive

### Common Utilities

**Vector Operations**:
```java
VectorUtils.getAngle(from, to)  // Angle between two points
VectorUtils.getFacing(vector)   // Get facing from velocity vector
```

**Distance Calculations**:
```java
MathUtils.getDistance(point1, point2)
MathUtils.getDistanceSquared(point1, point2)  // Faster when exact distance not needed
```

**Intercept Calculations**:
```java
Vector2f intercept = AIUtils.getBestInterceptPoint(
    missileLocation,
    missileSpeed,
    targetLocation,
    targetVelocity
);
```

### Logging

Use Starsector's logging system for debugging:
```java
import org.apache.log4j.Logger;
private static final Logger log = Global.getLogger(YourClass.class);

log.info("Information message");
log.warn("Warning message");
log.error("Error message", exception);
```

## File Locations

**Source Code**: `src/levianeer/draconis/`
**Ship Definitions**: `data/hulls/` (CSV + individual `.ship` files)
**Weapon Definitions**: `data/weapons/` (CSV + individual `.wpn` files)
**Ship Systems**: `data/shipsystems/` (CSV + individual `.system` files)
**Faction Data**: `data/world/factions/`
**Campaign Rules**: `data/campaign/rules.csv`
**Sprites**: `graphics/ships/`, `graphics/weapons/`, `graphics/missiles/`
**Sounds**: `sounds/sfx_wpn_*/`, `sounds/sfx_systems/`
**Compiled Output**: `jars/Draconis.jar`

## Version Management

Version format: `major.minor.patch` (currently 0.3.5)

Update versions in:
1. `mod_info.json` - `"version":"0.3.5"`
2. `draconis.version` - For GitHub version checker
Changelog maintained in `changelog.md`.