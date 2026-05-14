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
    private static final float MISSILE_GUIDANCE_DEBUFF = -50f;
    private static final float MISSILE_SPEED_DEBUFF = -50f;
    private static final float AUTOFIRE_DEBUFF = -75f;

    // Cloud configuration
    private static final float CLOUD_RADIUS = XLII_MistCloudConstants.CLOUD_RADIUS;
    private static final float CLOUD_MIN_LIFETIME = XLII_MistCloudConstants.CLOUD_MIN_LIFETIME;
    private static final float CLOUD_MAX_LIFETIME = XLII_MistCloudConstants.CLOUD_MAX_LIFETIME;
    private static final int MAX_CLOUD_COUNT = 16;
    private static final float RESPAWN_INTERVAL_MIN = 60f;
    private static final float RESPAWN_INTERVAL_MAX = 90f;

    // Activation thresholds (both must be met for missile spawning)
    private static final float MIN_XLII_PERCENTAGE = XLII_MistCloudConstants.MIN_XLII_PERCENTAGE;
    private static final int MIN_TOTAL_SUPPLY_COST = XLII_MistCloudConstants.MIN_TOTAL_SUPPLY_COST;

    // Buffs constants
    private static final float HEAL_PERCENT_PER_SEC = XLII_MistCloudConstants.HEAL_PERCENT_PER_SEC;

    // Debuffs constants
    private static final float DOT_PERCENT_PER_SEC = XLII_MistCloudConstants.DOT_PERCENT_PER_SEC;

    // Visual configuration - alpha tuned for advance-based spawning (~7-12 stacked particles at steady state)
    private static final Color NEBULA_COLOR_BASE = new Color(125, 130, 142, 130); // dark blue-gray mid layer
    private static final Color NEBULA_COLOR_WISP = new Color(165, 170, 180, 90);  // lighter blue-gray edges
    private static final Color NEBULA_COLOR_DARK = new Color(82, 85, 98, 150);    // dark storm core

    // Boundary ring colors UI
    private static final Color RING_COLOR_FRIENDLY = new Color(10, 255, 10, 10); // Green for player side
    private static final Color RING_COLOR_HOSTILE = new Color(255, 10, 10, 10); // Red for enemy side

    // EMP storm visual effects
    private static final Color EMP_ARC_FRINGE = new Color(220, 220, 195, 200); // Yellow-white natural lightning
    private static final Color EMP_ARC_CORE = new Color(255, 255, 230, 255);   // Bright white lightning core
    private static final float EMP_ARC_CHANCE = 0.015f; // % chance per frame per cloud

    private static final String STATS_MOD_ID = "XLII_mistcloud";

    // Spawn staggering configuration
    private static final float SPAWN_STAGGER_DELAY = 5f; // Delay between missile spawns in seconds

    // State
    private CombatEngineAPI engine;
    private boolean initDone = false;
    private boolean hasXLIIShips = false;
    private final List<Cloud> clouds = new ArrayList<>();
    private float respawnTimer = 0f;
    private float nextRespawnInterval = RESPAWN_INTERVAL_MIN;

    // Dynamic max cloud count based on fleet composition
    private int dynamicMaxCloudCount = MAX_CLOUD_COUNT;
    private float cloudCountRecalcTimer = 0f;
    private static final float CLOUD_COUNT_RECALC_INTERVAL = 10f; // Recalculate every 10 seconds

    // XLII side detection for missile spawning
    private int xliiOwnerSide = -1; // 0 = left/player side, 1 = right/enemy side
    private final int forcedOwnerSide; // Owner side passed at construction; pins this plugin to one team

    // Ring sprite for cloud boundaries
    private SpriteAPI ringSprite;

    // Staggered spawn system
    private final List<PendingSpawn> spawnQueue = new ArrayList<>();
    private float elapsedTime = 0f; // Total elapsed time for spawn queue processing

    // Periodic fleet scan - replaces init()-time fleet check so ships are actually on the field
    private boolean initialMissilesQueued = false;
    private float periodicScanTimer = 0f;
    private static final float PERIODIC_SCAN_INTERVAL = 10f;

    // OPTIMIZATION: Reusable vectors and lists to reduce per-frame allocations
    private final Vector2f tempVec1 = new Vector2f();
    private final Vector2f tempVec2 = new Vector2f();
    private final Vector2f tempZeroVel = new Vector2f(0f, 0f);
    private final List<ShipAPI> cachedShipList = new ArrayList<>();
    private final List<Cloud> reusableCloudsForShip = new ArrayList<>(); // Reusable list for cloud effect tracking
    private final Set<ShipAPI> shipsInAnyClouds = new HashSet<>();

    // Promoted reusable collections for checkForNewClouds()
    private final List<String> keysToRemove = new ArrayList<>();
    private final List<Vector2f> cloudsToSpawn = new ArrayList<>();

    /**
     * Cloud data structure
     */
    private static class Cloud {
        String id; // Unique ID for tracking in custom data
        Vector2f center;
        float life;
        float maxLife;
        float poolHp; // Finite HP pool; drained by healing allies and damaging enemies
        CombatEntityAPI aiAnchor; // Invisible entity for AI defend orders
        Color ringColor; // Boundary ring color (green for friendly, red for hostile)
        boolean ringRendered; // Track if ring sprite has been spawned

        Cloud(Vector2f center, Color ringColor) {
            this.id = "XLII_MIST_CLOUD_" + System.nanoTime(); // Unique ID
            this.center = new Vector2f(center);
            this.life = 0f;
            this.maxLife = CLOUD_MIN_LIFETIME + (float)(Math.random() * (CLOUD_MAX_LIFETIME - CLOUD_MIN_LIFETIME));
            this.poolHp = XLII_MistCloudConstants.CLOUD_POOL_HP;
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

    /**
     * Results of a single-pass fleet scan used during init().
     */
    private static class InitStats {
        boolean hasXLII = false;
        int xliiOwnerSide = -1;
        int xliiShips = 0;
        int totalAlliedShips = 0;
        int totalSupplyCost = 0;
    }

    public XLII_MistCloudsPlugin(int ownerSide) {
        this.forcedOwnerSide = ownerSide;
    }

    @Override
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    public void init(CombatEngineAPI engine) {
        this.engine = engine;

        if (initDone) return;
        initDone = true;

        // Fleet scan and missile queuing are deferred to the periodic scan in advance().
        // advanceInCombat() fires on ships that are still in the deployment queue, so
        // engine.getShips() is empty of XLII ships when init() runs here. The periodic
        // scan waits until ships are actually on the field before evaluating thresholds.
        hasXLIIShips = true; // Optimistic - periodic scan will correct this if needed

        // Load ring sprite (graphics only, safe to do immediately)
        try {
            ringSprite = Global.getSettings().getSprite("fx", "XLII_warning_ring");
            if (ringSprite != null) {
                ringSprite.setAdditiveBlend();
            }
        } catch (Exception e) {
            log.error("Draconis: Failed to load ring sprite: " + e.getMessage());
        }
    }

    /**
     * Single-pass fleet scan that computes all data needed by init().
     * Avoids the 5 separate iterations that separate checkForXLII / calculateXLIIPercentage /
     * calculateDynamicMaxCloudCount / meetsActivationThresholds calls would perform.
     */
    private InitStats computeInitStats() {
        InitStats result = new InitStats();

        cachedShipList.clear();
        cachedShipList.addAll(engine.getShips());

        // Pass 1: locate the first XLII ship on the forced owner side to determine team side
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk()) continue;
            if (ship.getOwner() != forcedOwnerSide) continue;
            if (ship.getVariant() != null && ship.getVariant().hasHullMod("XLII_fortysecond")) {
                result.xliiOwnerSide = ship.getOwner();
                result.hasXLII = true;
                if (log.isDebugEnabled()) {
                    log.debug("Draconis: Found XLII ship '" + ship.getHullSpec().getHullName() + "' on side " + result.xliiOwnerSide);
                }
                break;
            }
        }

        if (!result.hasXLII) return result;

        // Temporarily set instance field so isOnSameTeamAsXLII() works in pass 2
        xliiOwnerSide = result.xliiOwnerSide;

        // Pass 2: count allied ships, XLII ships, and supply cost
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk() || ship.isFighter() || ship.isDrone()) continue;
            if (!engine.isEntityInPlay(ship)) continue; // Exclude deployment-queue ships
            if (isOnSameTeamAsXLII(ship.getOwner())) {
                result.totalAlliedShips++;
                // Uses supply recovery cost as a fleet-strength proxy (not true DP)
                result.totalSupplyCost += (int) ship.getHullSpec().getSuppliesToRecover();
                if (ship.getVariant() != null && ship.getVariant().hasHullMod("XLII_fortysecond")) {
                    result.xliiShips++;
                }
            }
        }

        return result;
    }

    /**
     * Check if a ship owner is on the same team as the XLII ships
     * Handles multi-faction battles where player (owner 0) and allies (owner 2+) fight together
     *
     * @param shipOwner The owner ID to check
     * @return true if the ship is on the same team as XLII ships
     */
    private boolean isOnSameTeamAsXLII(int shipOwner) {
        if (xliiOwnerSide == -1) {
            return false; // xliiOwnerSide not yet set by periodic scan - safe to ignore
        }

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
            if (!engine.isEntityInPlay(ship)) continue; // Exclude deployment-queue ships

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
     * Calculate dynamic max cloud count based on total fleet supply cost and XLII percentage
     * Formula: 9 + (Total supply cost / 50) * XLII%
     * Optimized to reuse cached ship list from calculateXLIIPercentage
     */
    private int calculateDynamicMaxCloudCount() {
        int totalSupplyCost = 0;

        // OPTIMIZATION: Reuse ship list from previous call (calculateXLIIPercentage was just called)
        // If cachedShipList is empty, populate it
        if (cachedShipList.isEmpty()) {
            cachedShipList.addAll(engine.getShips());
        }

        // Calculate total supply cost of ships on XLII team
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk() || ship.isFighter() || ship.isDrone()) continue;
            if (!engine.isEntityInPlay(ship)) continue; // Exclude deployment-queue ships

            if (isOnSameTeamAsXLII(ship.getOwner())) {
                // Uses supply recovery cost as a fleet-strength proxy (not true DP)
                totalSupplyCost += (int) ship.getHullSpec().getSuppliesToRecover();
            }
        }

        float xliiPercent = calculateXLIIPercentage();

        // Formula: base (9) + (totalSupplyCost / 50) * xliiPercent
        int additionalClouds = Math.round((totalSupplyCost / 50f) * xliiPercent);
        int maxClouds = MAX_CLOUD_COUNT + additionalClouds;

        if (log.isInfoEnabled()) {
            log.debug("Draconis: Dynamic max cloud count: " + maxClouds + " (Base: " + MAX_CLOUD_COUNT +
                     ", Supply cost: " + totalSupplyCost + ", XLII%: " + Math.round(xliiPercent * 100) + "%)");
        }

        return maxClouds;
    }

    /**
     * Runs every PERIODIC_SCAN_INTERVAL seconds.
     * Detects XLII ships on the field, updates side/angle, checks thresholds,
     * and queues the initial missile wave once thresholds first pass.
     * Re-evaluates each tick so the system deactivates if the fleet shrinks below thresholds.
     */
    private void performPeriodicScan() {
        InitStats stats = computeInitStats();

        hasXLIIShips = stats.hasXLII;
        if (!hasXLIIShips) {
            log.debug("Draconis: Periodic scan - no XLII Battlegroup ships in play");
            return;
        }

        // Update side detection
        xliiOwnerSide = stats.xliiOwnerSide;

        // Update dynamic max cloud count
        float xliiPercent = (stats.totalAlliedShips == 0) ? 0f
                : (float) stats.xliiShips / stats.totalAlliedShips;
        int additionalClouds = Math.round((stats.totalSupplyCost / 50f) * xliiPercent);
        dynamicMaxCloudCount = MAX_CLOUD_COUNT + additionalClouds;

        // Check thresholds
        boolean meetsXLIIThreshold = xliiPercent >= MIN_XLII_PERCENTAGE;
        boolean meetsCostThreshold = stats.totalSupplyCost >= MIN_TOTAL_SUPPLY_COST;

        log.debug("Draconis: Periodic scan - " + stats.xliiShips + "/" + stats.totalAlliedShips +
                  " XLII (" + Math.round(xliiPercent * 100) + "%, need " + Math.round(MIN_XLII_PERCENTAGE * 100) + "%), " +
                  "supply cost " + stats.totalSupplyCost + " (need " + MIN_TOTAL_SUPPLY_COST + ") - " +
                  (meetsXLIIThreshold && meetsCostThreshold ? "ACTIVE" : "thresholds not met"));

        if (!meetsXLIIThreshold || !meetsCostThreshold) return;

        // Queue the initial missile wave the first time thresholds pass
        if (!initialMissilesQueued) {
            initialMissilesQueued = true;
            int initialMissileCount = Math.round(2 + (xliiPercent * 6));
            Vector2f mapCenter = new Vector2f(0f, 0f);
            for (int i = 0; i < initialMissileCount; i++) {
                float spawnTime = elapsedTime + (i * SPAWN_STAGGER_DELAY);
                spawnQueue.add(new PendingSpawn(spawnTime, mapCenter));
            }
            nextRespawnInterval = RESPAWN_INTERVAL_MIN +
                    (float)(Math.random() * (RESPAWN_INTERVAL_MAX - RESPAWN_INTERVAL_MIN));
            log.info("Draconis: Mist Clouds activated on side " + xliiOwnerSide
                     + " - queued " + initialMissileCount + " initial missiles");
        }
    }

    /**
     * Check if activation thresholds are met for missile spawning
     * Requires BOTH minimum XLII percentage AND minimum total supply cost
     * @return true if both thresholds are met, false otherwise
     */
    private boolean meetsActivationThresholds() {
        int totalSupplyCost = 0;
        int xliiShips = 0;
        int totalAlliedShips = 0;

        // OPTIMIZATION: Reuse cached ship list
        if (cachedShipList.isEmpty()) {
            cachedShipList.addAll(engine.getShips());
        }

        // Calculate total supply cost and XLII percentage for ships on XLII team
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || ship.isHulk() || ship.isFighter() || ship.isDrone()) continue;
            if (!engine.isEntityInPlay(ship)) continue; // Exclude deployment-queue ships

            if (isOnSameTeamAsXLII(ship.getOwner())) {
                totalAlliedShips++;
                // Uses supply recovery cost as a fleet-strength proxy (not true DP)
                totalSupplyCost += (int) ship.getHullSpec().getSuppliesToRecover();
                if (ship.getVariant() != null && ship.getVariant().hasHullMod("XLII_fortysecond")) {
                    xliiShips++;
                }
            }
        }

        float xliiPercent = (totalAlliedShips == 0) ? 0f : (float)xliiShips / (float)totalAlliedShips;

        // Both thresholds must be met
        boolean meetsXLIIThreshold = xliiPercent >= MIN_XLII_PERCENTAGE;
        boolean meetsCostThreshold = totalSupplyCost >= MIN_TOTAL_SUPPLY_COST;
        boolean meetsThresholds = meetsXLIIThreshold && meetsCostThreshold;

        if (log.isDebugEnabled()) {
            log.debug("Draconis: Activation thresholds check - XLII%: " + Math.round(xliiPercent * 100) + "% (" +
                     (meetsXLIIThreshold ? "PASS" : "FAIL - need " + Math.round(MIN_XLII_PERCENTAGE * 100) + "%") +
                     "), Supply cost: " + totalSupplyCost + " (" +
                     (meetsCostThreshold ? "PASS" : "FAIL - need " + MIN_TOTAL_SUPPLY_COST) +
                     "), Overall: " + (meetsThresholds ? "PASS" : "FAIL"));
        }

        return meetsThresholds;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) return;

        // Track elapsed time for spawn queue processing
        elapsedTime += amount;

        // Periodic fleet scan: detects XLII ships once they're actually deployed,
        // checks thresholds, queues initial missiles, and re-evaluates as the battle evolves.
        periodicScanTimer += amount;
        if (periodicScanTimer >= PERIODIC_SCAN_INTERVAL) {
            periodicScanTimer = 0f;
            performPeriodicScan();
        }

        if (!hasXLIIShips) return;

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
        if (respawnTimer >= nextRespawnInterval && (clouds.size() + spawnQueue.size()) < dynamicMaxCloudCount) {
            spawnPeriodicClouds();
            respawnTimer = 0f;
            nextRespawnInterval = RESPAWN_INTERVAL_MIN +
                (float)(Math.random() * (RESPAWN_INTERVAL_MAX - RESPAWN_INTERVAL_MIN));
        }

        // Apply effects to ships (including fighters)
        applyCloudEffects();

        // Render visual effects
        renderCloudVisuals(amount);
    }

    /**
     * Check for cloud spawn signals from missile impacts
     */
    private void checkForNewClouds() {
        Map<String, Object> customData = engine.getCustomData();
        keysToRemove.clear();
        cloudsToSpawn.clear();

        // Collect spawn locations during iteration (don't modify map yet)
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("XLII_MIST_SPAWN_" + forcedOwnerSide + "_")) {
                Object value = entry.getValue();
                if (value instanceof Vector2f spawnLocation) {
                    cloudsToSpawn.add(new Vector2f(spawnLocation));
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
     * Update cloud lifetimes and expire clouds that have exceeded their maximum lifetime
     */
    private void updateClouds(float dt) {
        Iterator<Cloud> iter = clouds.iterator();
        while (iter.hasNext()) {
            Cloud cloud = iter.next();

            // Update lifetime
            cloud.life += dt;

            // Remove expired or pool-exhausted clouds
            if (cloud.life >= cloud.maxLife || cloud.poolHp <= 0) {
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
     * Ship-first loop: each ship is processed exactly once, eliminating O(N²·M) complexity
     */
    private void applyCloudEffects() {
        // OPTIMIZATION: Cache ship list once per frame
        cachedShipList.clear();
        cachedShipList.addAll(engine.getShips());

        shipsInAnyClouds.clear();
        float radiusSquared = CLOUD_RADIUS * CLOUD_RADIUS;

        // Ship-first loop: each ship is processed exactly once
        for (ShipAPI ship : cachedShipList) {
            if (ship == null || !ship.isAlive() || ship.isHulk() || ship.isShuttlePod()) continue;

            // Build list of active (non-depleted) clouds containing this ship
            reusableCloudsForShip.clear();
            for (Cloud cloud : clouds) {
                if (cloud.poolHp <= 0) continue;
                float distSquared = MathUtils.getDistanceSquared(ship.getLocation(), cloud.center);
                if (distSquared < radiusSquared) {
                    reusableCloudsForShip.add(cloud);
                }
            }

            if (!reusableCloudsForShip.isEmpty()) {
                shipsInAnyClouds.add(ship);
                boolean isAlly = isOnSameTeamAsXLII(ship.getOwner());
                for (Cloud cloud : reusableCloudsForShip) {
                    applyInCloudEffect(ship, isAlly, cloud);
                }
                // Show player HUD status once after processing all clouds
                if (ship == engine.getPlayerShip()) {
                    int cloudCount = reusableCloudsForShip.size();
                    if (isAlly) {
                        float maxHull = ship.getMaxHitpoints();
                        String statusText = (ship.getHitpoints() >= maxHull)
                                ? "Hull Full"
                                : "+" + (HEAL_PERCENT_PER_SEC * 100f) + "% Hull/Sec (" + cloudCount + " cloud" + (cloudCount > 1 ? "s)" : ")");
                        engine.maintainStatusForPlayerShip(
                                "XLII_mistcloud_buff",
                                "graphics/icons/tactical/nebula_slowdown.png",
                                "Nanofilter Mending",
                                statusText,
                                false
                        );
                    } else {
                        String statusText = ship.isPhased()
                                ? "Phase Protected"
                                : "-" + (DOT_PERCENT_PER_SEC * 100f) + "% Hull/Sec (" + cloudCount + " cloud" + (cloudCount > 1 ? "s)" : ")");
                        engine.maintainStatusForPlayerShip(
                                "XLII_mistcloud_debuff",
                                "graphics/icons/tactical/nebula_slowdown.png",
                                "Nanomist Deconstruction",
                                statusText,
                                true
                        );
                    }
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
     * Apply effects to a ship from a single cloud, draining the cloud's pool
     */
    private void applyInCloudEffect(ShipAPI ship, boolean isAlly, Cloud cloud) {
        float dt = engine.getElapsedInLastFrame();

        if (isAlly) {
            float maxHull = ship.getMaxHitpoints();
            float currentHull = ship.getHitpoints();

            if (currentHull < maxHull && cloud.poolHp > 0) {
                float healAmount = maxHull * HEAL_PERCENT_PER_SEC * dt;
                healAmount = Math.min(healAmount, cloud.poolHp);
                healAmount = Math.min(healAmount, maxHull - currentHull);

                ship.setHitpoints(currentHull + healAmount);
                cloud.poolHp -= healAmount;
            }
        } else {
            if (!ship.isPhased() && cloud.poolHp > 0) {
                float maxHull = ship.getMaxHitpoints();
                float dotAmount = maxHull * DOT_PERCENT_PER_SEC * dt;
                dotAmount = Math.min(dotAmount, cloud.poolHp);

                // Apply damage (bypasses shields and armor)
                ship.setHitpoints(Math.max(1f, ship.getHitpoints() - dotAmount));
                cloud.poolHp -= dotAmount;
            }

            // EW debuffs apply as long as enemy is in the cloud (no pool cost)
            MutableShipStatsAPI stats = ship.getMutableStats();
            stats.getMissileGuidance().modifyFlat(STATS_MOD_ID, MISSILE_GUIDANCE_DEBUFF);
            stats.getMissileMaxSpeedBonus().modifyPercent(STATS_MOD_ID, MISSILE_SPEED_DEBUFF);
            stats.getAutofireAimAccuracy().modifyFlat(STATS_MOD_ID, AUTOFIRE_DEBUFF);
        }
    }

    /**
     * Remove cloud effects from a ship that left the cloud
     */
    private void removeCloudEffects(ShipAPI ship, boolean isAlly) {
        MutableShipStatsAPI stats = ship.getMutableStats();

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
     * Render continuous visual effects for all clouds.
     * Spawn chances are expressed as particles-per-second multiplied by the frame delta (amount),
     * keeping particle counts frame-rate-independent and preventing alpha accumulation.
     */
    private void renderCloudVisuals(float amount) {
        for (Cloud cloud : clouds) {
            // Calculate fade based on remaining lifetime
            float lifeFraction = cloud.life / cloud.maxLife;
            float fadeMult = 1.0f;
            if (lifeFraction > 0.9f) {
                // Fade out in last 10% of lifetime
                fadeMult = 1.0f - ((lifeFraction - 0.9f) / 0.1f);
            }

            // Layer 0: Dark storm core (exclusive owner of 0-20% - prevents center accumulation)
            // ~6 particles/sec -> ~21 stacked at steady state (3.5s avg lifetime)
            if (Math.random() < 6.0f * amount) {
                float angle = (float)(Math.random() * 360f);
                float distance = CLOUD_RADIUS * (float)(Math.random() * 0.20f);

                tempVec1.set(
                    cloud.center.x + (float)FastTrig.cos(Math.toRadians(angle)) * distance,
                    cloud.center.y + (float)FastTrig.sin(Math.toRadians(angle)) * distance
                );

                int alpha = (int)(NEBULA_COLOR_DARK.getAlpha() * fadeMult);

                engine.addNebulaParticle(
                    tempVec1,
                    tempZeroVel,
                    75f + (float)(Math.random() * 65f), // 75-140px dense dark particles
                    1.2f, // Endsize multiplier (less expansion - stays dense)
                    0.05f, // Growth rate
                    0.28f * fadeMult, // Brightness (reduced to avoid accumulation)
                    2.5f + (float)(Math.random() * 2f), // Duration
                    new Color(
                        NEBULA_COLOR_DARK.getRed(),
                        NEBULA_COLOR_DARK.getGreen(),
                        NEBULA_COLOR_DARK.getBlue(),
                        alpha
                    )
                );
            }

            // Layer 1: Inner-mid fill (donut 20-50% - avoids overlapping the dark core)
            // ~9 particles/sec -> ~36 stacked at steady state (4s avg lifetime)
            if (Math.random() < 9.0f * amount) {
                float angle = (float)(Math.random() * 360f);
                float distance = CLOUD_RADIUS * (0.20f + (float)(Math.random() * 0.30f)); // 20% to 50%

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
                    1.1f, // Endsize multiplier (tighter - denser core)
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

            // Layer 2: Large nebula base (outer ring 30-90%)
            // ~7.5 particles/sec -> ~34 stacked at steady state (4.5s avg lifetime)
            if (Math.random() < 7.5f * amount) {
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
                    1.7f, // Endsize multiplier (more billowing at edges)
                    0.1f, // Growth rate
                    0.30f * fadeMult, // Brightness (brighter edges = light scattering)
                    3.5f + (float)(Math.random() * 2f), // Duration
                    new Color(
                        NEBULA_COLOR_BASE.getRed(),
                        NEBULA_COLOR_BASE.getGreen(),
                        NEBULA_COLOR_BASE.getBlue(),
                        alpha
                    )
                );
            }

            // Layer 3: Wispy atmospheric depth layer (outer-mid 40-75%)
            // ~10 particles/sec -> ~32 stacked at steady state (3.25s avg lifetime)
            if (Math.random() < 10.0f * amount) {
                float angle = (float)(Math.random() * 360f);
                float distance = CLOUD_RADIUS * (0.40f + (float)(Math.random() * 0.35f)); // 40% to 75%

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

        // Spawn from the middle of the appropriate map edge, just outside the boundary.
        // Player side (0) deploys from the south edge; enemy side (1) from the north edge.
        float halfH = engine.getMapHeight() * 0.5f;
        float xVariance = (float)(Math.random() * 200f - 100f); // ±100 units lateral spread
        Vector2f spawnPos = new Vector2f(
            xVariance,
            (xliiOwnerSide == 0) ? -(halfH + 200f) : (halfH + 200f)
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

        Vector2f mapCenter = new Vector2f(0f, 0f);

        // Calculate XLII percentage for missile scaling
        float xliiPercent = calculateXLIIPercentage();

        // Scale periodic missile count: 1 at 0%, 4 at 100%
        int count = Math.round(1 + (xliiPercent * 3));

        // Queue spawns with staggered delays from current time
        for (int i = 0; i < count && (clouds.size() + spawnQueue.size()) < dynamicMaxCloudCount; i++) {
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
