package levianeer.draconis.data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.*;

/**
 * Mist Clouds combat system for XLII Battlegroup.
 * Spawns tactical smoke clouds that heal XLII ships and debuff enemies.
 * Inspired by Into the Breach's Mist Eaters faction.
 */
public class XLII_MistCloudsPlugin implements EveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(XLII_MistCloudsPlugin.class);

    // Faction configuration
    private static final String DEPLOYER_WEAPON_ID = "XLII_SLAP-ER_deployer";
    private static final String ANCHOR_WEAPON_ID = "XLII_mist_anchor";

    // Passive debuffs
    private static final float MISSILE_GUIDANCE_DEBUFF = -25f;
    private static final float MISSILE_SPEED_DEBUFF = -25f;
    private static final float AUTOFIRE_DEBUFF = -50f;

    // Cloud configuration
    private static final float CLOUD_RADIUS = 1200f;
    private static final float CLOUD_MIN_LIFETIME = 30f;
    private static final float CLOUD_MAX_LIFETIME = 60f;
    private static final int MAX_CLOUD_COUNT = 6;
    private static final float RESPAWN_INTERVAL_MIN = 60f;
    private static final float RESPAWN_INTERVAL_MAX = 90f;

    // Activation thresholds (both must be met for missile spawning)
    private static final float MIN_XLII_PERCENTAGE = 0.25f; // 25% XLII ships required
    private static final int MIN_TOTAL_DP = 50; // 50 DP minimum fleet strength

    // Buffs constants
    private static final float HEAL_PERCENT_PER_SEC = 0.005f; // % of max hull per second

    // Debuffs constants
    private static final float DOT_PERCENT_PER_SEC = 0.005f; // % of max hull per second

    // Visual configuration
    private static final Color NEBULA_COLOR_BASE = new Color(160, 160, 160, 60);
    private static final Color NEBULA_COLOR_WISP = new Color(190, 190, 190, 40);

    // Boundary ring colors UI
    private static final Color RING_COLOR_FRIENDLY = new Color(10, 255, 10, 10); // Green for player side
    private static final Color RING_COLOR_HOSTILE = new Color(255, 10, 10, 10); // Red for enemy side

    // EMP storm visual effects
    private static final Color EMP_ARC_FRINGE = new Color(100, 150, 255, 200); // Blue-tinted electrical arc
    private static final Color EMP_ARC_CORE = new Color(150, 200, 255, 255); // Brighter core
    private static final float EMP_ARC_CHANCE = 0.01f; // % chance per frame per cloud

    private static final String STATS_MOD_ID = "XLII_mistcloud";

    // Spawn staggering configuration
    private static final float SPAWN_STAGGER_DELAY = 0.1f; // Delay between missile spawns in seconds

    // State
    private CombatEngineAPI engine;
    private boolean initDone = false;
    private boolean hasXLIIShips = false;
    private final List<Cloud> clouds = new ArrayList<>();
    private float respawnTimer = 0f;
    private float nextRespawnInterval = RESPAWN_INTERVAL_MIN;
    private final Set<String> processedSpawnKeys = new HashSet<>();

    // Dynamic max cloud count based on fleet composition
    private int dynamicMaxCloudCount = MAX_CLOUD_COUNT;
    private float cloudCountRecalcTimer = 0f;
    private static final float CLOUD_COUNT_RECALC_INTERVAL = 10f; // Recalculate every 10 seconds

    // XLII side detection for missile spawning
    private int xliiOwnerSide = -1; // 0 = left/player side, 1 = right/enemy side
    private float spawnAngleBase = 0f; // Base angle for missile spawning (180° for side 0, 0° for side 1)

    // Ring sprite for cloud boundaries
    private SpriteAPI ringSprite;

    // Staggered spawn system
    private final List<PendingSpawn> spawnQueue = new ArrayList<>();
    private float elapsedTime = 0f; // Total elapsed time for spawn queue processing

    // OPTIMIZATION: Reusable vectors and lists to reduce per-frame allocations
    private final Vector2f tempVec1 = new Vector2f();
    private final Vector2f tempVec2 = new Vector2f();
    private final Vector2f tempZeroVel = new Vector2f(0f, 0f);
    private final List<ShipAPI> cachedShipList = new ArrayList<>();
    private final List<Cloud> shipsInClouds = new ArrayList<>(); // Reusable list for cloud effect tracking

    // Global cap tracking (no stacking - single cap per ship regardless of cloud count)
    private final Map<String, Float> globalShipHealingAccumulated = new HashMap<>();
    private final Map<String, Float> globalShipDamageAccumulated = new HashMap<>();

    /**
     * Cloud data structure
     * Note: Cap tracking is now global per ship, not per cloud
     */
    private static class Cloud {
        String id; // Unique ID for tracking in custom data
        Vector2f center;
        float life;
        float maxLife;
        CombatEntityAPI aiAnchor; // Invisible entity for AI defend orders
        Color ringColor; // Boundary ring color (green for friendly, red for hostile)
        boolean ringRendered; // Track if ring sprite has been spawned

        Cloud(Vector2f center, Color ringColor) {
            this.id = "XLII_MIST_CLOUD_" + System.nanoTime(); // Unique ID
            this.center = new Vector2f(center);
            this.life = 0f;
            this.maxLife = CLOUD_MIN_LIFETIME + (float)(Math.random() * (CLOUD_MAX_LIFETIME - CLOUD_MIN_LIFETIME));
            this.ringColor = ringColor;
            this.ringRendered = false; // Initialize as not rendered
        }
    }

    /**
     * Pending missile spawn for staggered spawning system
     */
    private static class PendingSpawn {
        float spawnTime; // Game time when this missile should spawn
        Vector2f targetLocation; // Location to aim toward

        PendingSpawn(float spawnTime, Vector2f targetLocation) {
            this.spawnTime = spawnTime;
            this.targetLocation = new Vector2f(targetLocation);
        }
    }

    @Override
    @Deprecated
    public void init(CombatEngineAPI engine) {
        this.engine = engine;

        if (initDone) return;
        initDone = true;

        // Check if XLII_fortysecond faction is present in the battle and detect side
        hasXLIIShips = checkForXLIIShips();

        if (!hasXLIIShips) {
            if (log.isInfoEnabled()) {
                log.info("Draconis: Mist Clouds system inactive - no XLII Battlegroup ships present");
            }
            return;
        }

        // Load ring sprite from base game FX
        try {
            ringSprite = Global.getSettings().getSprite("fx", "XLII_warning_ring");
            if (ringSprite != null) {
                ringSprite.setAdditiveBlend(); // Use additive for glowing effect
            }
            if (log.isInfoEnabled()) {
                log.info("Draconis: Ring sprite loaded successfully");
            }
        } catch (Exception e) {
            log.error("Draconis: Failed to load ring sprite: " + e.getMessage());
        }

        // Set spawn angle base based on XLII owner side
        // Side 0 (bottom/player): spawn from south (270° ± 45°)
        // Side 1 (top/enemy): spawn from north (90° ± 45°)
        spawnAngleBase = (xliiOwnerSide == 0) ? 270f : 90f;

        // Calculate XLII percentage for missile scaling
        float xliiPercent = calculateXLIIPercentage();

        // Calculate dynamic max cloud count
        dynamicMaxCloudCount = calculateDynamicMaxCloudCount();

        // Check activation thresholds before spawning missiles
        if (!meetsActivationThresholds()) {
            if (log.isInfoEnabled()) {
                log.info("Draconis: Mist Clouds system inactive - activation thresholds not met");
                log.info("Draconis: Requires minimum " + Math.round(MIN_XLII_PERCENTAGE * 100) + "% XLII ships AND " + MIN_TOTAL_DP + " DP");
            }
            // Skip missile spawning - respawn interval still set below for consistency
            nextRespawnInterval = RESPAWN_INTERVAL_MIN +
                (float)(Math.random() * (RESPAWN_INTERVAL_MAX - RESPAWN_INTERVAL_MIN));
            return;
        }

        // Scale initial missile count: 2 at 0%, 8 at 100%
        int initialMissileCount = Math.round(2 + (xliiPercent * 6));

        if (log.isInfoEnabled()) {
            log.info("Draconis: Mist Clouds system activated - XLII on side " + xliiOwnerSide + ", missiles spawning from " + spawnAngleBase + "° sector");
            log.info("Draconis: XLII fleet composition: " + Math.round(xliiPercent * 100) + "% (" + initialMissileCount + " initial missiles)");
        }

        // Queue initial cloud spawns with staggered timing
        Vector2f mapCenter = new Vector2f(engine.getMapWidth() * 0.5f, engine.getMapHeight() * 0.5f);
        for (int i = 0; i < initialMissileCount; i++) {
            float spawnTime = i * SPAWN_STAGGER_DELAY; // 0.0, 0.15, 0.30, 0.45, etc.
            spawnQueue.add(new PendingSpawn(spawnTime, mapCenter));
        }

        if (log.isInfoEnabled()) {
            log.info("Draconis: Queued " + initialMissileCount + " initial missiles with staggered spawning");
        }

        // Set first respawn interval
        nextRespawnInterval = RESPAWN_INTERVAL_MIN +
            (float)(Math.random() * (RESPAWN_INTERVAL_MAX - RESPAWN_INTERVAL_MIN));
    }

    /**
     * Check if any ships with XLII_fortysecond hullmod are present in the battle
     * Also detects which side (0 or 1) the XLII ships are on
     */
    private boolean checkForXLIIShips() {
        // OPTIMIZATION: Cache ship list for reuse
        cachedShipList.clear();
        cachedShipList.addAll(engine.getShips());

        // Check if any ship has the XLII_fortysecond hullmod
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk()) continue;

            if (ship.getVariant() != null &&
                ship.getVariant().hasHullMod("XLII_fortysecond")) {
                // Detect which side this ship is on (0 = left/player, 1 = right/enemy)
                xliiOwnerSide = ship.getOwner();
                if (log.isInfoEnabled()) {
                    log.info("Draconis: Found XLII ship '" + ship.getHullSpec().getHullName() + "' on side " + xliiOwnerSide);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate the percentage of allied ships (on XLII team) that have the XLII_fortysecond hullmod
     * Returns value from 0.0 (0%) to 1.0 (100%)
     * Optimized to use cached ship list
     */
    private float calculateXLIIPercentage() {
        int totalAlliedShips = 0;
        int xliiShips = 0;

        // OPTIMIZATION: Use cached ship list
        cachedShipList.clear();
        cachedShipList.addAll(engine.getShips());

        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk() || ship.isFighter() || ship.isDrone()) continue;

            // Count ships on the same team as XLII (not just same owner)
            if (isOnSameTeamAsXLII(ship.getOwner())) {
                totalAlliedShips++;
                if (ship.getVariant() != null && ship.getVariant().hasHullMod("XLII_fortysecond")) {
                    xliiShips++;
                }
            }
        }

        if (totalAlliedShips == 0) return 0f;
        return (float)xliiShips / (float)totalAlliedShips;
    }

    /**
     * Check if a ship owner is on the same team as the XLII ships
     * Handles multi-faction battles where player (owner 0) and allies (owner 2+) fight together
     *
     * @param shipOwner The owner ID to check
     * @return true if the ship is on the same team as XLII ships
     */
    private boolean isOnSameTeamAsXLII(int shipOwner) {
        // Same owner = definitely same team
        if (shipOwner == xliiOwnerSide) return true;

        // If XLII is player (owner 0), allies are anyone except enemy (owner 1)
        if (xliiOwnerSide == 0) {
            return shipOwner != 1;
        }

        // If XLII is enemy (owner 1), allies are only owner 1
        if (xliiOwnerSide == 1) {
            return false;
        }

        // If XLII is neutral/ally (owner 2+), assume fighting alongside player against owner 1
        // This handles the case where XLII ships are allied with the player
        return shipOwner != 1;
    }

    /**
     * Calculate dynamic max cloud count based on total fleet DP and XLII percentage
     * Formula: 9 + (Total DP / 50) * XLII%
     * Optimized to reuse cached ship list from calculateXLIIPercentage
     */
    private int calculateDynamicMaxCloudCount() {
        int totalDP = 0;

        // OPTIMIZATION: Reuse ship list from previous call (calculateXLIIPercentage was just called)
        // If cachedShipList is empty, populate it
        if (cachedShipList.isEmpty()) {
            cachedShipList.addAll(engine.getShips());
        }

        // Calculate total DP of ships on XLII team
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk() || ship.isFighter() || ship.isDrone()) continue;

            // Count ships on the same team as XLII (not just same owner)
            if (isOnSameTeamAsXLII(ship.getOwner())) {
                totalDP += (int) ship.getHullSpec().getSuppliesToRecover();
            }
        }

        float xliiPercent = calculateXLIIPercentage();

        // Formula: base (9) + (totalDP / 50) * xliiPercent
        int additionalClouds = Math.round((totalDP / 50f) * xliiPercent);
        int maxClouds = MAX_CLOUD_COUNT + additionalClouds;

        if (log.isInfoEnabled()) {
            log.info("Draconis: Dynamic max cloud count: " + maxClouds + " (Base: " + MAX_CLOUD_COUNT +
                     ", DP: " + totalDP + ", XLII%: " + Math.round(xliiPercent * 100) + "%)");
        }

        return maxClouds;
    }

    /**
     * Check if activation thresholds are met for missile spawning
     * Requires BOTH minimum XLII percentage AND minimum total DP
     * @return true if both thresholds are met, false otherwise
     */
    private boolean meetsActivationThresholds() {
        int totalDP = 0;
        int xliiShips = 0;
        int totalAlliedShips = 0;

        // OPTIMIZATION: Reuse cached ship list
        if (cachedShipList.isEmpty()) {
            cachedShipList.addAll(engine.getShips());
        }

        // Calculate total DP and XLII percentage for ships on XLII team
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk() || ship.isFighter() || ship.isDrone()) continue;

            if (isOnSameTeamAsXLII(ship.getOwner())) {
                totalAlliedShips++;
                totalDP += (int) ship.getHullSpec().getSuppliesToRecover();
                if (ship.getVariant() != null && ship.getVariant().hasHullMod("XLII_fortysecond")) {
                    xliiShips++;
                }
            }
        }

        float xliiPercent = (totalAlliedShips == 0) ? 0f : (float)xliiShips / (float)totalAlliedShips;

        // Both thresholds must be met
        boolean meetsXLIIThreshold = xliiPercent >= MIN_XLII_PERCENTAGE;
        boolean meetsDPThreshold = totalDP >= MIN_TOTAL_DP;
        boolean meetsThresholds = meetsXLIIThreshold && meetsDPThreshold;

        if (log.isDebugEnabled()) {
            log.debug("Draconis: Activation thresholds check - XLII%: " + Math.round(xliiPercent * 100) + "% (" +
                     (meetsXLIIThreshold ? "PASS" : "FAIL - need " + Math.round(MIN_XLII_PERCENTAGE * 100) + "%") +
                     "), DP: " + totalDP + " (" +
                     (meetsDPThreshold ? "PASS" : "FAIL - need " + MIN_TOTAL_DP) +
                     "), Overall: " + (meetsThresholds ? "PASS" : "FAIL"));
        }

        return meetsThresholds;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused() || !hasXLIIShips) return;

        // Track elapsed time for spawn queue processing
        elapsedTime += amount;

        // Process staggered missile spawns
        processSpawnQueue();

        // Recalculate dynamic max cloud count periodically
        cloudCountRecalcTimer += amount;
        if (cloudCountRecalcTimer >= CLOUD_COUNT_RECALC_INTERVAL) {
            dynamicMaxCloudCount = calculateDynamicMaxCloudCount();
            cloudCountRecalcTimer = 0f;
        }

        // Check for new cloud spawn signals from on-hit effects
        checkForNewClouds();

        // Update existing clouds
        updateClouds(amount);

        // Spawn new missiles periodically
        respawnTimer += amount;
        if (respawnTimer >= nextRespawnInterval && clouds.size() < dynamicMaxCloudCount) {
            spawnPeriodicClouds();
            respawnTimer = 0f;
            nextRespawnInterval = RESPAWN_INTERVAL_MIN +
                (float)(Math.random() * (RESPAWN_INTERVAL_MAX - RESPAWN_INTERVAL_MIN));
        }

        // Apply effects to ships (including fighters)
        applyCloudEffects();

        // Render visual effects
        renderCloudVisuals();
    }

    /**
     * Check for cloud spawn signals from missile impacts
     */
    private void checkForNewClouds() {
        Map<String, Object> customData = engine.getCustomData();
        List<String> keysToRemove = new ArrayList<>();
        List<Vector2f> cloudsToSpawn = new ArrayList<>();

        // Collect spawn locations during iteration (don't modify map yet)
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("XLII_MIST_SPAWN_") && !processedSpawnKeys.contains(key)) {
                Object value = entry.getValue();
                if (value instanceof Vector2f spawnLocation) {
                    cloudsToSpawn.add(new Vector2f(spawnLocation));
                    processedSpawnKeys.add(key);
                    keysToRemove.add(key);
                }
            }
        }

        // Now that iteration is complete, register clouds (safe to modify customData)
        for (Vector2f location : cloudsToSpawn) {
            registerCloud(location);
            if (log.isDebugEnabled()) {
                log.debug("Draconis: Mist cloud spawned at " + location);
            }
        }

        // Clean up processed keys
        for (String key : keysToRemove) {
            customData.remove(key);
        }
    }

    /**
     * Register a new cloud at the given location
     */
    private void registerCloud(Vector2f location) {
        if (clouds.size() >= dynamicMaxCloudCount) {
            // Remove oldest cloud
            Cloud oldest = clouds.get(0);
            if (oldest.aiAnchor != null) {
                engine.removeEntity(oldest.aiAnchor);
            }
            // Remove from custom data tracking
            engine.getCustomData().remove(oldest.id);
            clouds.remove(0);
        }

        // Determine ring color based on XLII ownership (green if player side, red if enemy side)
        Color ringColor = (xliiOwnerSide == 0) ? RING_COLOR_FRIENDLY : RING_COLOR_HOSTILE;
        Cloud newCloud = new Cloud(location, ringColor);

        // Create invisible AI anchor
        newCloud.aiAnchor = createInvisibleAnchor(location);

        // Store cloud location in custom data for missile AI to read
        engine.getCustomData().put(newCloud.id, new Vector2f(location));

        clouds.add(newCloud);
    }

    /**
     * Create an invisible entity for AI to path toward/defend
     * The anchor is owned by the XLII team, so XLII AI will defend it
     * <p>
     * Uses XLII_mist_anchor - a custom invisible projectile with no visual effects.
     * This allows XLII AI to detect and defend cloud locations without visible missiles.
     * Player-controlled ships ignore defend orders (manual control), but AI allies will defend.
     */
    private CombatEntityAPI createInvisibleAnchor(Vector2f location) {
        try {
            // Find a ship from the XLII team to use as source (for correct owner assignment)
            ShipAPI xliiShip = null;
            for (ShipAPI ship : engine.getShips()) {
                if (isOnSameTeamAsXLII(ship.getOwner())) {
                    xliiShip = ship;
                    break;
                }
            }

            // If no XLII team ship found, don't create anchor (no defend order needed)
            if (xliiShip == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Draconis: No XLII team ships found, skipping AI anchor creation");
                }
                return null;
            }

            // Spawn invisible anchor missile
            WeaponAPI fakeWeapon = engine.createFakeWeapon(xliiShip, ANCHOR_WEAPON_ID);
            DamagingProjectileAPI projectile = (DamagingProjectileAPI) engine.spawnProjectile(
                xliiShip,
                fakeWeapon,
                ANCHOR_WEAPON_ID,
                location,
                0f,
                new Vector2f(0f, 0f)
            );

            if (projectile != null) {
                projectile.setCollisionClass(CollisionClass.NONE);

                // Make it last for the cloud's lifetime (only works if it's a missile)
                if (projectile instanceof MissileAPI) {
                    ((MissileAPI) projectile).setMaxFlightTime(CLOUD_MAX_LIFETIME * 2);
                }
            }

            return projectile;
        } catch (Exception e) {
            log.warn("Draconis: Failed to create AI anchor for cloud: " + e.getMessage());
            return null;
        }
    }

    /**
     * Update cloud positions and lifetimes
     */
    private void updateClouds(float dt) {
        Iterator<Cloud> iter = clouds.iterator();
        while (iter.hasNext()) {
            Cloud cloud = iter.next();

            // Update cloud location in custom data for missile AI
            engine.getCustomData().put(cloud.id, new Vector2f(cloud.center));

            // Update AI anchor position
            if (cloud.aiAnchor != null && !cloud.aiAnchor.isExpired()) {
                cloud.aiAnchor.getLocation().set(cloud.center);
            }

            // Update lifetime
            cloud.life += dt;

            // Remove expired clouds
            if (cloud.life >= cloud.maxLife) {
                if (cloud.aiAnchor != null) {
                    engine.removeEntity(cloud.aiAnchor);
                }
                // Remove from custom data tracking
                engine.getCustomData().remove(cloud.id);
                iter.remove();

                // Spawn fade-out visual
                spawnFadeOutVisual(cloud.center);
            }
        }
    }

    /**
     * Apply effects to ships in clouds
     * Optimized with cached ship list and distance squared checks
     */
    private void applyCloudEffects() {
        // OPTIMIZATION: Cache ship list once per frame
        cachedShipList.clear();
        cachedShipList.addAll(engine.getShips());

        // OPTIMIZATION: Track which ships are in clouds to efficiently remove effects from others
        Set<ShipAPI> shipsInAnyClouds = new HashSet<>();

        // For each cloud, find ships in range
        for (Cloud cloud : clouds) {
            float radiusSquared = CLOUD_RADIUS * CLOUD_RADIUS;

            for (ShipAPI ship : cachedShipList) {
                if (ship == null || !ship.isAlive() || ship.isHulk() || ship.isShuttlePod()) continue;

                // OPTIMIZATION: Use squared distance for faster comparison
                float distSquared = MathUtils.getDistanceSquared(ship.getLocation(), cloud.center);
                if (distSquared < radiusSquared) {
                    shipsInAnyClouds.add(ship);

                    // Collect all clouds this ship is in (need to check all clouds still)
                    shipsInClouds.clear();
                    for (Cloud c : clouds) {
                        float dSquared = MathUtils.getDistanceSquared(ship.getLocation(), c.center);
                        if (dSquared < radiusSquared) {
                            shipsInClouds.add(c);
                        }
                    }

                    // Team-based ally detection: check if ship is on the same team as XLII
                    boolean isAlly = isOnSameTeamAsXLII(ship.getOwner());
                    applyInCloudEffects(ship, isAlly, shipsInClouds);
                }
            }
        }

        // Remove effects from ships not in any clouds
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || !ship.isAlive()) continue;
            if (!shipsInAnyClouds.contains(ship)) {
                boolean isAlly = isOnSameTeamAsXLII(ship.getOwner());
                removeCloudEffects(ship, isAlly);
            }
        }
    }

    /**
     * Apply effects to a ship inside clouds (can be multiple)
     */
    private void applyInCloudEffects(ShipAPI ship, boolean isAlly, List<Cloud> cloudsContainingShip) {
        float dt = engine.getElapsedInLastFrame();
        String shipId = ship.getId();

        if (isAlly) {
            // Allied ships: healing with global cap (non-stacking - single cap regardless of cloud count)
            float maxHull = ship.getMaxHitpoints();
            float currentHull = ship.getHitpoints();

            // Calculate healing cap: higher of 2000 or 50% max hull
            float healingCap = Math.max(2000f, maxHull * 0.5f);

            // Check global accumulated healing for this ship
            float accumulated = globalShipHealingAccumulated.getOrDefault(shipId, 0f);

            boolean canHeal = currentHull < maxHull && accumulated < healingCap;
            int cloudCount = cloudsContainingShip.size();

            if (canHeal) {
                // Fixed healing rate (does NOT stack with multiple clouds)
                float healAmount = maxHull * HEAL_PERCENT_PER_SEC * dt;

                // Don't exceed global cap
                float remainingAllowance = healingCap - accumulated;
                healAmount = Math.min(healAmount, remainingAllowance);

                // Apply healing
                ship.setHitpoints(Math.min(maxHull, currentHull + healAmount));

                // Track global accumulated healing
                globalShipHealingAccumulated.put(shipId, accumulated + healAmount);
            }

            // Status display for player ship
            if (ship == engine.getPlayerShip()) {
                String statusText;
                if (currentHull >= maxHull) {
                    statusText = "Hull Full";
                } else if (accumulated >= healingCap) {
                    statusText = "Healing Cap Reached";
                } else {
                    statusText = "+0.5% Hull/Sec (" + cloudCount + " cloud" + (cloudCount > 1 ? "s)" : ")");
                }

                engine.maintainStatusForPlayerShip(
                    "XLII_mistcloud_buff",
                    "graphics/icons/tactical/nebula_slowdown.png",
                    "Nanofilter Mending",
                    statusText,
                    false
                );
            }

        } else {
            // Enemy ships: DoT with global cap + Electronic Warfare debuffs
            float maxHull = ship.getMaxHitpoints();

            // Calculate damage cap: higher of 2000 or 50% max hull
            float damageCap = Math.max(2000f, maxHull * 0.5f);

            // Check global accumulated damage for this ship
            float accumulated = globalShipDamageAccumulated.getOrDefault(shipId, 0f);

            boolean canDamage = !ship.isPhased() && accumulated < damageCap;
            int cloudCount = cloudsContainingShip.size();

            if (canDamage) {
                // Fixed damage rate (does NOT stack with multiple clouds)
                float dotAmount = maxHull * DOT_PERCENT_PER_SEC * dt;

                // Don't exceed global cap
                float remainingAllowance = damageCap - accumulated;
                dotAmount = Math.min(dotAmount, remainingAllowance);

                // Apply damage (bypasses shields and armor)
                ship.setHitpoints(Math.max(1f, ship.getHitpoints() - dotAmount));

                // Track global accumulated damage
                globalShipDamageAccumulated.put(shipId, accumulated + dotAmount);
            }

            // Apply Electronic Warfare debuffs
            MutableShipStatsAPI stats = ship.getMutableStats();
            stats.getMissileGuidance().modifyFlat(STATS_MOD_ID, MISSILE_GUIDANCE_DEBUFF);
            stats.getMissileMaxSpeedBonus().modifyPercent(STATS_MOD_ID, MISSILE_SPEED_DEBUFF);
            stats.getAutofireAimAccuracy().modifyFlat(STATS_MOD_ID, AUTOFIRE_DEBUFF);

            // Status display for player ship
            if (ship == engine.getPlayerShip()) {
                String statusText;
                if (ship.isPhased()) {
                    statusText = "Phase Protected";
                } else if (accumulated >= damageCap) {
                    statusText = "Damage Cap Reached";
                } else {
                    statusText = "-0.5% Hull/Sec (" + cloudCount + " cloud" + (cloudCount > 1 ? "s)" : ")");
                }

                engine.maintainStatusForPlayerShip(
                    "XLII_mistcloud_debuff",
                    "graphics/icons/tactical/nebula_slowdown.png",
                    "Nanomist Deconstruction",
                    statusText,
                    true // Red/bad status
                );
            }
        }
    }

    /**
     * Remove cloud effects from a ship that left the cloud
     */
    private void removeCloudEffects(ShipAPI ship, boolean isAlly) {
        MutableShipStatsAPI stats = ship.getMutableStats();

        stats.getMaxSpeed().unmodify(STATS_MOD_ID);
        stats.getAcceleration().unmodify(STATS_MOD_ID);
        stats.getDeceleration().unmodify(STATS_MOD_ID);

        if (!isAlly) {
            // Remove movement debuffs
            stats.getTurnAcceleration().unmodify(STATS_MOD_ID);
            stats.getMaxTurnRate().unmodify(STATS_MOD_ID);

            // Remove Electronic Warfare debuffs
            stats.getMissileGuidance().unmodify(STATS_MOD_ID);
            stats.getMissileMaxSpeedBonus().unmodify(STATS_MOD_ID);
            stats.getAutofireAimAccuracy().unmodify(STATS_MOD_ID);
        }
    }

    /**
     * Render colored boundary rings for all clouds using MagicRender
     * Only renders once per cloud with full lifetime duration
     */
    private void renderCloudRings() {
        if (ringSprite == null || clouds.isEmpty()) return;

        for (Cloud cloud : clouds) {
            // Only render once per cloud
            if (cloud.ringRendered) continue;

            float diameter = CLOUD_RADIUS * 2f;
            Vector2f size = new Vector2f(diameter, diameter);
            Vector2f growthNone = new Vector2f(0f, 0f);

            org.magiclib.util.MagicRender.battlespace(
                ringSprite,
                cloud.center,
                growthNone,
                size,
                growthNone,
                0f,
                0f,
                cloud.ringColor,  // Use full color, MagicRender handles fade
                true,
                0f, 0f, 0f, 0f, 0f,
                0.5f,                      // Fade in duration
                cloud.maxLife * 0.1f,      // Fade out over last 10% of lifetime
                cloud.maxLife,             // Duration = full cloud lifetime
                CombatEngineLayers.ABOVE_SHIPS_LAYER
            );

            cloud.ringRendered = true;
        }
    }

    /**
     * Render EMP storm effects within clouds
     * Creates random lightning arcs between points to simulate electrical activity
     * Optimized with FastTrig and reusable vectors
     */
    private void renderEMPStormEffects() {
        for (Cloud cloud : clouds) {
            // % chance per frame per cloud to spawn an arc
            if (Math.random() > EMP_ARC_CHANCE) continue;

            // Pick two random points within the cloud radius
            float angle1 = (float)(Math.random() * 360f);
            float distance1 = (float)(Math.random() * CLOUD_RADIUS * 0.8f);

            // OPTIMIZATION: Reuse tempVec1 for point1
            tempVec1.set(
                cloud.center.x + (float)FastTrig.cos(Math.toRadians(angle1)) * distance1,
                cloud.center.y + (float)FastTrig.sin(Math.toRadians(angle1)) * distance1
            );

            float angle2 = (float)(Math.random() * 360f);
            float distance2 = (float)(Math.random() * CLOUD_RADIUS * 0.8f);

            // OPTIMIZATION: Reuse tempVec2 for point2
            tempVec2.set(
                cloud.center.x + (float)FastTrig.cos(Math.toRadians(angle2)) * distance2,
                cloud.center.y + (float)FastTrig.sin(Math.toRadians(angle2)) * distance2
            );

            // Spawn visual-only EMP arc between the two points
            engine.spawnEmpArcVisual(
                tempVec1,
                null,   // No source entity
                tempVec2,
                null,   // No target entity
                5f,    // Arc thickness
                EMP_ARC_FRINGE,
                EMP_ARC_CORE
            );
        }
    }

    /**
     * Render continuous visual effects for all clouds
     * Deep hyperspace-inspired layered atmospheric rendering
     * Optimized with FastTrig and reusable vectors
     */
    private void renderCloudVisuals() {
        for (Cloud cloud : clouds) {
            // Calculate fade based on remaining lifetime
            float lifeFraction = cloud.life / cloud.maxLife;
            float fadeMult = 1.0f;
            if (lifeFraction > 0.9f) {
                // Fade out in last 10% of lifetime
                fadeMult = 1.0f - ((lifeFraction - 0.9f) / 0.1f);
            }

            // Layer 1: Center fill (dense core particles to eliminate gap)
            // Spawn in center area (0-40% of radius)
            if (Math.random() < 0.5f) {
                float angle = (float)(Math.random() * 360f);
                float distance = CLOUD_RADIUS * (float)(Math.random() * 0.4f); // 0% to 40%

                // OPTIMIZATION: Reuse tempVec1 for position
                tempVec1.set(
                    cloud.center.x + (float)FastTrig.cos(Math.toRadians(angle)) * distance,
                    cloud.center.y + (float)FastTrig.sin(Math.toRadians(angle)) * distance
                );

                int alpha = (int)(NEBULA_COLOR_BASE.getAlpha() * fadeMult);

                engine.addNebulaParticle(
                    tempVec1,
                    tempZeroVel, // Stationary
                    100f + (float)(Math.random() * 80f), // Medium-large particles for center
                    1.3f, // Endsize multiplier
                    0.1f, // Growth rate
                    0.3f * fadeMult, // Slightly brighter for center
                    3.0f + (float)(Math.random() * 2f), // Duration
                    new Color(
                        NEBULA_COLOR_BASE.getRed(),
                        NEBULA_COLOR_BASE.getGreen(),
                        NEBULA_COLOR_BASE.getBlue(),
                        alpha
                    )
                );
            }

            // Layer 2: Large nebula base (deep hyperspace-style atmospheric base)
            // Spawn in outer ring (30-90% of radius)
            if (Math.random() < 0.35f) {
                float angle = (float)(Math.random() * 360f);
                float distance = CLOUD_RADIUS * (0.3f + (float)(Math.random() * 0.6f)); // 30% to 90%

                // OPTIMIZATION: Reuse tempVec2 for position
                tempVec2.set(
                    cloud.center.x + (float)FastTrig.cos(Math.toRadians(angle)) * distance,
                    cloud.center.y + (float)FastTrig.sin(Math.toRadians(angle)) * distance
                );

                int alpha = (int)(NEBULA_COLOR_BASE.getAlpha() * fadeMult);

                engine.addNebulaParticle(
                    tempVec2,
                    tempZeroVel, // Stationary
                    150f + (float)(Math.random() * 100f), // Large nebula particles
                    1.5f, // Endsize multiplier
                    0.1f, // Growth rate
                    0.25f * fadeMult, // Brightness
                    3.5f + (float)(Math.random() * 2f), // Duration
                    new Color(
                        NEBULA_COLOR_BASE.getRed(),
                        NEBULA_COLOR_BASE.getGreen(),
                        NEBULA_COLOR_BASE.getBlue(),
                        alpha
                    )
                );
            }

            // Layer 3: Wispy atmospheric depth layer
            // Spawn in mid ring (15-70% of radius) for better coverage
            if (Math.random() < 0.6f) {
                float angle = (float)(Math.random() * 360f);
                float distance = CLOUD_RADIUS * (0.15f + (float)(Math.random() * 0.55f)); // 15% to 70%

                // OPTIMIZATION: Reuse tempVec1 for position (alternating with tempVec2)
                tempVec1.set(
                    cloud.center.x + (float)FastTrig.cos(Math.toRadians(angle)) * distance,
                    cloud.center.y + (float)FastTrig.sin(Math.toRadians(angle)) * distance
                );

                int alpha = (int)(NEBULA_COLOR_WISP.getAlpha() * fadeMult);

                engine.addSmoothParticle(
                    tempVec1,
                    tempZeroVel, // Stationary
                    80f + (float)(Math.random() * 60f),
                    0.6f * fadeMult,
                    2.5f + (float)(Math.random() * 1.5f),
                    new Color(
                        NEBULA_COLOR_WISP.getRed(),
                        NEBULA_COLOR_WISP.getGreen(),
                        NEBULA_COLOR_WISP.getBlue(),
                        alpha
                    )
                );
            }

        }

        // Render EMP storm effects (lightning arcs)
        renderEMPStormEffects();

        // Render boundary rings using MagicRender
        renderCloudRings();
    }

    /**
     * Process the spawn queue and spawn missiles when their scheduled time arrives
     */
    private void processSpawnQueue() {
        if (spawnQueue.isEmpty()) return;

        // Process all spawns whose time has arrived
        Iterator<PendingSpawn> iter = spawnQueue.iterator();
        while (iter.hasNext()) {
            PendingSpawn pending = iter.next();

            if (elapsedTime >= pending.spawnTime) {
                // Time to spawn this missile
                spawnCloudMissile(pending.targetLocation);
                iter.remove();
            }
        }
    }

    /**
     * Spawn a smoke missile toward the map center
     */
    private void spawnCloudMissile(Vector2f toward) {
        // Pick a ship from the XLII team to spawn from (needed for correct team assignment)
        // Accept any ship on the same team, not just exact owner match
        ShipAPI sourceShip = null;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isAlive() && !ship.isHulk() && isOnSameTeamAsXLII(ship.getOwner())) {
                sourceShip = ship;
                break;
            }
        }

        // Fallback: use any ship from the XLII team (including hulks if necessary)
        if (sourceShip == null) {
            for (ShipAPI ship : engine.getShips()) {
                if (isOnSameTeamAsXLII(ship.getOwner())) {
                    sourceShip = ship;
                    break;
                }
            }
        }

        if (sourceShip == null) {
            log.warn("Draconis: Failed to spawn mist cloud missile - no source ship found on XLII team (owner " + xliiOwnerSide + ")");
            return;
        }

        // Calculate spawn position from XLII side (off-screen but within combat boundary)
        float spawnDistance = 1500f;
        // Use spawnAngleBase ± 15° variance (270° for side 0, 90° for side 1)
        // Tighter spread ensures missiles spawn from center-top/center-bottom
        float angleVariance = (float)(Math.random() * 30f - 15f); // -15 to +15
        float angle = spawnAngleBase + angleVariance;
        Vector2f spawnPos = new Vector2f(
            toward.x + (float)Math.cos(Math.toRadians(angle)) * spawnDistance,
            toward.y + (float)Math.sin(Math.toRadians(angle)) * spawnDistance
        );

        // Calculate target position (random position in central 40% of map)
        float targetRadius = Math.min(engine.getMapWidth(), engine.getMapHeight()) * 0.2f;
        float targetAngle = (float)(Math.random() * 360f);
        Vector2f targetPos = new Vector2f(
            toward.x + (float)Math.cos(Math.toRadians(targetAngle)) * targetRadius,
            toward.y + (float)Math.sin(Math.toRadians(targetAngle)) * targetRadius
        );

        // Calculate launch angle
        float launchAngle = Misc.getAngleInDegrees(spawnPos, targetPos);

        if (log.isDebugEnabled()) {
            log.debug("Draconis: Attempting to spawn mist cloud missile at " + spawnPos + " targeting " + targetPos +
                     " (angle: " + launchAngle + "°, distance: " + MathUtils.getDistance(spawnPos, targetPos) + ")");
        }

        try {
            WeaponAPI weapon = engine.createFakeWeapon(sourceShip, DEPLOYER_WEAPON_ID);

            MissileAPI missile = (MissileAPI) engine.spawnProjectile(
                sourceShip,
                weapon,
                DEPLOYER_WEAPON_ID,
                spawnPos,
                launchAngle,
                new Vector2f(0f, 0f)
            );

            if (missile != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Draconis: Mist cloud missile spawned successfully at " + missile.getLocation());
                }
            } else {
                log.warn("Draconis: Missile spawn returned null! Weapon: " + DEPLOYER_WEAPON_ID +
                         ", Source: " + sourceShip.getHullSpec().getHullName() + ", Position: " + spawnPos);
            }
        } catch (Exception e) {
            log.error("Draconis: Failed to spawn mist cloud missile - Weapon: " + DEPLOYER_WEAPON_ID +
                     ", Position: " + spawnPos + ", Angle: " + launchAngle, e);
        }
    }

    /**
     * Queue periodic cloud spawns with staggered timing
     */
    private void spawnPeriodicClouds() {
        // Check activation thresholds before spawning missiles
        if (!meetsActivationThresholds()) {
            if (log.isDebugEnabled()) {
                log.debug("Draconis: Periodic missile spawn skipped - activation thresholds not met");
            }
            return;
        }

        Vector2f mapCenter = new Vector2f(engine.getMapWidth() * 0.5f, engine.getMapHeight() * 0.5f);

        // Calculate XLII percentage for missile scaling
        float xliiPercent = calculateXLIIPercentage();

        // Scale periodic missile count: 1 at 0%, 4 at 100%
        int count = Math.round(1 + (xliiPercent * 3));

        // Queue spawns with staggered delays from current time
        for (int i = 0; i < count && clouds.size() < dynamicMaxCloudCount; i++) {
            float spawnTime = elapsedTime + (i * SPAWN_STAGGER_DELAY);
            spawnQueue.add(new PendingSpawn(spawnTime, mapCenter));
        }

        if (log.isDebugEnabled()) {
            log.debug("Draconis: Queued " + count + " additional mist cloud missiles with staggered spawning (XLII: " + Math.round(xliiPercent * 100) + "%)");
        }
    }

    /**
     * Spawn visual effect when a cloud fades out
     * Optimized with reusable vector
     */
    private void spawnFadeOutVisual(Vector2f location) {
        // Fading smoke burst
        for (int i = 0; i < 3; i++) {
            engine.addSmoothParticle(
                location,
                tempZeroVel, // Reuse zero velocity vector
                80f + (i * 20f),
                0.4f - (i * 0.1f),
                1.0f + (i * 0.3f),
                new Color(180, 180, 180, 100 - (i * 20))
            );
        }
    }

    // Required EveryFrameCombatPlugin methods
    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
    }
}
