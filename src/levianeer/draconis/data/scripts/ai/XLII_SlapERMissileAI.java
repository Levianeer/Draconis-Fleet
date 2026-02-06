//By Tartiflette, modified for XLII Mist Cloud system
//Intelligent targeting with cloud avoidance and ally healing support
package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import levianeer.draconis.data.scripts.weapons.XLII_MistCloudOnHitEffect;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class XLII_SlapERMissileAI implements MissileAIPlugin, GuidedMissileAI {

    //////////////////////
    //     SETTINGS     //
    //////////////////////

    //Angle with the target beyond which the missile turn around without accelerating. Avoid endless circling.
    //  Set to a negative value to disable
    private final float OVERSHOT_ANGLE = -1;

    //Time to complete a wave in seconds.
    private final float WAVE_TIME = 1;

    //Max angle of the waving in degree (divided by 3 with ECCM). Set to a negative value to avoid all waving.
    private final float WAVE_AMPLITUDE = -1;

    //Damping of the turn speed when closing on the desired aim. The smaller the snappier.
    private final float DAMPING = 0.2f;

    //Does the missile try to correct it's velocity vector as fast as possible or just point to the desired direction and drift a bit?
    //  Can create strange results with large waving
    //  Require a projectile with a decent turn rate and around twice that in turn acceleration
    //  Usefull for slow torpedoes with low forward acceleration, or ultra precise anti-fighter missiles.
    private final boolean OVERSTEER = true;  //REQUIRE NO OVERSHOOT ANGLE!

    //Does the missile switch its target if it has been destroyed?
    private final boolean TARGET_SWITCH = true;

    //range in which the missile seek a target in game units.
    private final int MAX_SEARCH_RANGE = 100000;

    //range under which the missile start to get progressively more precise in game units.
    private float PRECISION_RANGE = 750;

    //Is the missile lead the target or tailchase it?
    private final boolean LEADING = true;

    //Leading loss without ECCM hullmod. The higher, the less accurate the leading calculation will be.
    //   1: perfect leading with and without ECCM
    //   2: half precision without ECCM
    //   3: a third as precise without ECCM. Default
    //   4, 5, 6 etc : 1/4th, 1/5th, 1/6th etc precision.
    private float ECCM = 1;   //A VALUE BELOW 1 WILL PREVENT THE MISSILE FROM EVER HITTING ITS TARGET!

    // Mist Cloud System Settings
    private static final float CLOUD_RADIUS = 1200f;
    private static final float MIN_CLOUD_SPACING = 2400f; // 2x CLOUD_RADIUS - no overlap
    private static final float IMPACT_UPDATE_INTERVAL = 2.5f; // Update impact point every 2.5 seconds

    // Tactical Scoring Weights
    private static final float ALLY_DAMAGED_BASE_SCORE = 100f;
    private static final float ALLY_HEALTHY_BASE_SCORE = 50f;
    private static final float ENEMY_CAPITAL_BASE_SCORE = 60f;
    private static final float ENEMY_CRUISER_BASE_SCORE = 40f;
    private static final float ENEMY_DESTROYER_BASE_SCORE = 20f;
    private static final float ENEMY_FRIGATE_BASE_SCORE = 20f;
    private static final float DISTANCE_PENALTY_FACTOR = 0.0002f; // Score reduction per unit distance

    //////////////////////
    //    VARIABLES     //
    //////////////////////

    //max speed of the missile after modifiers.
    private final float MAX_SPEED;
    //Random starting offset for the waving.
    private final float OFFSET;
    private CombatEngineAPI engine;
    private final MissileAPI MISSILE;
    private final String MISSILE_ID;
    private CombatEntityAPI target;
    private Vector2f lead = new Vector2f();
    private boolean launch = true;
    private float timer = 0, check = 0f;
    private int missileOwner = -1;

    // Proximity failsafe for collision pass-through bug
    private float closestDistanceToTarget = Float.MAX_VALUE;
    private boolean wasInDetonationRange = false;

    // Periodic impact point update tracking
    private float impactUpdateTimer = 0f;

    // Reusable vectors to reduce allocations
    private final Vector2f tempVec1 = new Vector2f();
    private final Vector2f tempVec2 = new Vector2f();

    // Cached ship list for target selection (reused to avoid repeated engine.getShips() calls)
    private final List<ShipAPI> cachedShipList = new ArrayList<>();

    //////////////////////
    //  DATA COLLECTING //
    //////////////////////

    public XLII_SlapERMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        this.MISSILE = missile;
        this.MISSILE_ID = "XLII_SLAP_TARGET_" + System.nanoTime() + "_" + (int)(Math.random() * 10000);
        MAX_SPEED = missile.getMaxSpeed();
        if (missile.getSource() != null && missile.getSource().getVariant().getHullMods().contains("eccm")) {
            ECCM = 1;
        }
        //calculate the precision range factor
        PRECISION_RANGE = (float) Math.pow((2 * PRECISION_RANGE), 2);
        OFFSET = (float) (Math.random() * MathUtils.FPI * 2);

        // Determine missile owner side
        if (missile.getSource() != null) {
            missileOwner = missile.getSource().getOwner();
        }
    }

    //////////////////////
    //   MAIN AI LOOP   //
    //////////////////////

    @Override
    public void advance(float amount) {

        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }

        //skip the AI if the game is paused, the missile is engineless or fading
        if (Global.getCombatEngine().isPaused() || MISSILE.isFading() || MISSILE.isFizzling()) {
            // Clean up target registration if missile is expiring
            if (MISSILE.isFading() || MISSILE.isFizzling()) {
                cleanupTargetRegistration();
            }
            return;
        }

        //assigning a target if there is none or it got destroyed
        if (target == null
                || (TARGET_SWITCH
                && ((target instanceof ShipAPI && !((ShipAPI) target).isAlive())
                || !engine.isEntityInPlay(target))
        )
        ) {
            // Clean up old target registration
            cleanupTargetRegistration();

            // Use intelligent tactical targeting system
            setTarget(selectBestTarget());

            // Reset proximity tracking for new target
            closestDistanceToTarget = Float.MAX_VALUE;
            wasInDetonationRange = false;

            // Reset impact update timer for new target
            impactUpdateTimer = 0f;

            //forced acceleration by default
            MISSILE.giveCommand(ShipCommand.ACCELERATE);
            return;
        }

        timer += amount;
        //finding lead point to aim to
        if (launch || timer >= check) {
            launch = false;
            timer -= check;
            //set the next check time
            check = Math.min(
                    0.25f,
                    Math.max(
                            0.05f,
                            MathUtils.getDistanceSquared(MISSILE.getLocation(), target.getLocation()) / PRECISION_RANGE)
            );
            if (LEADING) {
                //best intercepting point
                lead = AIUtils.getBestInterceptPoint(
                        MISSILE.getLocation(),
                        MAX_SPEED * ECCM, //if eccm is intalled the point is accurate, otherwise it's placed closer to the target (almost tailchasing)
                        target.getLocation(),
                        target.getVelocity()
                );
                //null pointer protection
                if (lead == null) {
                    lead = target.getLocation();
                }
            } else {
                lead = target.getLocation();
            }
        }

        // Periodically update registered impact point to account for target movement
        impactUpdateTimer += amount;
        if (impactUpdateTimer >= IMPACT_UPDATE_INTERVAL && target instanceof ShipAPI) {
            updateRegisteredImpactPoint((ShipAPI) target);
            impactUpdateTimer = 0f;
        }

        // Proximity failsafe: detect if missile passed through target without collision
        if (target instanceof ShipAPI) {
            ShipAPI targetShip = (ShipAPI) target;
            float distToTarget = MathUtils.getDistance(MISSILE.getLocation(), target.getLocation());
            float detonationRange = targetShip.getCollisionRadius() + MISSILE.getCollisionRadius() + 50f;

            // Track if we entered detonation range
            if (distToTarget <= detonationRange) {
                wasInDetonationRange = true;
                closestDistanceToTarget = Math.min(closestDistanceToTarget, distToTarget);
            }

            // If we were close but now moving away → manual detonation (collision passed through)
            if (wasInDetonationRange && distToTarget > closestDistanceToTarget + 100f) {
                triggerManualDetonation(targetShip);
                return;
            }
        }

        //best velocity vector angle for interception
        float correctAngle = VectorUtils.getAngle(
                MISSILE.getLocation(),
                lead
        );

        if (OVERSTEER) {
            //velocity angle correction
            float offCourseAngle = MathUtils.getShortestRotation(
                    VectorUtils.getFacing(MISSILE.getVelocity()),
                    correctAngle
            );

            float correction = MathUtils.getShortestRotation(
                    correctAngle,
                    VectorUtils.getFacing(MISSILE.getVelocity()) + 180
            )
                    * 0.5f * //oversteer
                    (float) ((FastTrig.sin(MathUtils.FPI / 90 * (Math.min(Math.abs(offCourseAngle), 45))))); //damping when the correction isn't important

            //modified optimal facing to correct the velocity vector angle as soon as possible
            correctAngle = correctAngle + correction;
        }

        if (WAVE_AMPLITUDE > 0) {
            //waving
            float multiplier = 1;
            if (ECCM <= 1) {
                multiplier = 0.3f;
            }
            correctAngle += multiplier * WAVE_AMPLITUDE * check * Math.cos(OFFSET + MISSILE.getElapsed() * (2 * MathUtils.FPI / WAVE_TIME));
        }

        //target angle for interception
        float aimAngle = MathUtils.getShortestRotation(MISSILE.getFacing(), correctAngle);

        if (OVERSHOT_ANGLE <= 0 || Math.abs(aimAngle) < OVERSHOT_ANGLE) {
            MISSILE.giveCommand(ShipCommand.ACCELERATE);
        }

        if (aimAngle < 0) {
            MISSILE.giveCommand(ShipCommand.TURN_RIGHT);
        } else {
            MISSILE.giveCommand(ShipCommand.TURN_LEFT);
        }

        // Damp angular velocity if the missile aim is getting close to the targeted angle
        if (Math.abs(aimAngle) < Math.abs(MISSILE.getAngularVelocity()) * DAMPING) {
            MISSILE.setAngularVelocity(aimAngle / DAMPING);
        }
    }

    //////////////////////
    //   TEAM LOGIC     //
    //////////////////////

    /**
     * Check if a ship owner is on the same team as the missile owner
     * Handles multi-faction battles where player (owner 0) and allies (owner 2+) fight together
     *
     * @param shipOwner The owner ID to check
     * @return true if the ship is on the same team as the missile
     */
    private boolean isOnSameTeam(int shipOwner) {
        // Same owner = definitely same team
        if (shipOwner == missileOwner) return true;

        // If missile owner is player (owner 0), allies are anyone except enemy (owner 1)
        if (missileOwner == 0) {
            return shipOwner != 1;
        }

        // If missile owner is enemy (owner 1), allies are only owner 1
        if (missileOwner == 1) {
            return shipOwner == 1;
        }

        // If missile owner is neutral/ally (owner 2+), assume fighting alongside player against owner 1
        // This handles the case where XLII ships are allied with the player
        return shipOwner != 1;
    }

    //////////////////////
    //  PROXIMITY FAIL  //
    //////////////////////

    /**
     * Manually triggers detonation when missile passes through target without collision.
     * Freezes the missile in place, instantiates the OnHitEffect, and fades out the missile.
     */
    private void triggerManualDetonation(ShipAPI targetShip) {
        if (engine == null || MISSILE == null) return;

        // Capture exact detonation location before any modifications
        Vector2f detonationPoint = new Vector2f(MISSILE.getLocation());

        // FREEZE the missile in place to prevent drift
        MISSILE.getVelocity().set(0f, 0f);
        MISSILE.setAngularVelocity(0f);

        // Manually invoke the OnHitEffect at the frozen location
        XLII_MistCloudOnHitEffect effect = new XLII_MistCloudOnHitEffect();
        effect.onHit(MISSILE, targetShip, detonationPoint, false, null, engine);

        // Fade out the missile (now stationary)
        MISSILE.flameOut();
    }

    //////////////////////
    //    TARGETING     //
    //////////////////////

    /**
     * Intelligent target selection with cloud avoidance and tactical scoring
     * Uses two-pass system: first tries untargeted ships, then falls back to multi-targeting if needed
     * Optimized with combined customData iteration and cached ship list
     */
    private ShipAPI selectBestTarget() {
        if (engine == null) return null;

        // OPTIMIZATION: Combined single-pass customData iteration
        // Extracts clouds, impact points, and target counts in one loop
        List<Vector2f> cloudLocations = new ArrayList<>();
        List<Vector2f> otherMissileImpacts = new ArrayList<>();
        Map<ShipAPI, Integer> targetCounts = new HashMap<>();

        Map<String, Object> customData = engine.getCustomData();
        String thisImpactKey = MISSILE_ID.replace("TARGET", "IMPACT");

        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Collect cloud locations
            if (key.startsWith("XLII_MIST_CLOUD_") && value instanceof Vector2f) {
                cloudLocations.add((Vector2f) value);
            }
            // Collect other missiles' impact points
            else if (key.startsWith("XLII_SLAP_IMPACT_") && !key.equals(thisImpactKey) && value instanceof Vector2f) {
                otherMissileImpacts.add((Vector2f) value);
            }
            // Count targeted ships (skip our own registration)
            else if (key.startsWith("XLII_SLAP_TARGET_") && !key.equals(MISSILE_ID) && value instanceof ShipAPI) {
                ShipAPI ship = (ShipAPI) value;
                targetCounts.put(ship, targetCounts.getOrDefault(ship, 0) + 1);
            }
        }

        // Combine both to create comprehensive overlap avoidance
        List<Vector2f> allCloudLocations = new ArrayList<>(cloudLocations);
        allCloudLocations.addAll(otherMissileImpacts);

        // FIRST PASS: Try to find untargeted ships only
        ShipAPI bestTarget = findBestTargetWithFilter(allCloudLocations, targetCounts, false);

        // SECOND PASS: If no untargeted ships available, allow multi-targeting with penalty
        if (bestTarget == null) {
            bestTarget = findBestTargetWithFilter(allCloudLocations, targetCounts, true);
        }

        // Register target for multi-missile coordination
        if (bestTarget != null) {
            registerTarget(bestTarget);
        }

        return bestTarget;
    }

    /**
     * Find best target with optional multi-targeting fallback
     * @param cloudLocations Existing mist cloud positions
     * @param targetCounts Map of how many missiles are targeting each ship
     * @param allowMultiTarget If false, exclude ships with missiles. If true, allow but penalize heavily.
     */
    private ShipAPI findBestTargetWithFilter(List<Vector2f> cloudLocations,
                                             Map<ShipAPI, Integer> targetCounts,
                                             boolean allowMultiTarget) {
        float bestScore = -999999f;
        ShipAPI bestTarget = null;

        // OPTIMIZATION: Cache ship list (reuse the list to avoid repeated allocation)
        cachedShipList.clear();
        cachedShipList.addAll(engine.getShips());

        for (ShipAPI ship : cachedShipList) {
            if (!isValidTarget(ship)) continue;

            // Skip ships already in clouds
            if (isShipInCloud(ship, cloudLocations)) continue;

            // Get missile count for this ship
            int missilesTargeting = targetCounts.getOrDefault(ship, 0);

            // In first pass, skip already-targeted ships
            if (!allowMultiTarget && missilesTargeting > 0) {
                continue;
            }

            // OPTIMIZATION: Calculate distance once and pass to scoring function
            float distanceToShip = MathUtils.getDistance(MISSILE.getLocation(), ship.getLocation());

            // Calculate tactical score (passing distance to avoid recalculation)
            float score = calculateTacticalScore(ship, missilesTargeting, allowMultiTarget, distanceToShip);

            // Check if impact point would overlap with existing clouds
            Vector2f predictedImpact = predictImpactPoint(ship);
            if (predictedImpact != null && wouldOverlapCloud(predictedImpact, cloudLocations)) {
                continue; // Skip this target - would create overlapping cloud
            }

            if (score > bestScore) {
                bestScore = score;
                bestTarget = ship;
            }
        }

        return bestTarget;
    }

    /**
     * Check if ship is valid target candidate
     * Optimized with squared distance check
     */
    private boolean isValidTarget(ShipAPI ship) {
        if (ship == null || !ship.isAlive() || ship.isHulk()) return false;
        if (ship.isShuttlePod() || ship.isDrone()) return false;
        if (ship.isFighter()) return false; // Don't target fighters

        // OPTIMIZATION: Use squared distance for faster comparison
        float distSquared = MathUtils.getDistanceSquared(MISSILE.getLocation(), ship.getLocation());
        if (distSquared > MAX_SEARCH_RANGE * MAX_SEARCH_RANGE) return false;

        return true;
    }

    /**
     * Check if ship is currently inside any mist cloud
     */
    private boolean isShipInCloud(ShipAPI ship, List<Vector2f> cloudLocations) {
        for (Vector2f cloudCenter : cloudLocations) {
            float distance = MathUtils.getDistance(ship.getLocation(), cloudCenter);
            if (distance < CLOUD_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate tactical score for targeting priority
     * @param ship The ship to score
     * @param missilesTargeting Number of missiles already targeting this ship
     * @param fallbackMode If true, apply heavy penalty for multi-targeting instead of exclusion
     * @param distance Pre-calculated distance to ship (optimization to avoid recalculation)
     */
    private float calculateTacticalScore(ShipAPI ship, int missilesTargeting, boolean fallbackMode, float distance) {
        boolean isAlly = isOnSameTeam(ship.getOwner());
        float baseScore = 0f;

        if (isAlly) {
            // Allied ships - prioritize damaged ones
            float hullPercent = ship.getHitpoints() / ship.getMaxHitpoints();
            if (hullPercent < 0.5f) {
                // Damaged ally - high priority
                baseScore = ALLY_DAMAGED_BASE_SCORE * (1f - hullPercent); // Lower hull = higher score
            } else {
                // Healthy ally - medium priority
                baseScore = ALLY_HEALTHY_BASE_SCORE;
            }
        } else {
            // Enemy ships - prioritize by size
            switch (ship.getHullSize()) {
                case CAPITAL_SHIP:
                    baseScore = ENEMY_CAPITAL_BASE_SCORE;
                    break;
                case CRUISER:
                    baseScore = ENEMY_CRUISER_BASE_SCORE;
                    break;
                case DESTROYER:
                    baseScore = ENEMY_DESTROYER_BASE_SCORE;
                    break;
                case FRIGATE:
                    baseScore = ENEMY_FRIGATE_BASE_SCORE;
                    break;
                default:
                    baseScore = 10f;
            }
        }

        // Distance penalty (using pre-calculated distance)
        float distancePenalty = distance * DISTANCE_PENALTY_FACTOR;
        float score = baseScore - distancePenalty;

        // Multi-targeting penalty (only applies in fallback mode)
        if (fallbackMode && missilesTargeting > 0) {
            score *= 0.2f; // Reduce priority by 80% if already targeted in fallback mode
        }

        return score;
    }

    /**
     * Predict where the missile will impact the target (intercept point)
     */
    private Vector2f predictImpactPoint(ShipAPI ship) {
        Vector2f intercept = AIUtils.getBestInterceptPoint(
            MISSILE.getLocation(),
            MAX_SPEED * ECCM,
            ship.getLocation(),
            ship.getVelocity()
        );

        if (intercept == null) {
            return ship.getLocation();
        }

        return intercept;
    }

    /**
     * Check if predicted impact point would create overlapping cloud
     */
    private boolean wouldOverlapCloud(Vector2f impactPoint, List<Vector2f> cloudLocations) {
        for (Vector2f cloudCenter : cloudLocations) {
            float distance = MathUtils.getDistance(impactPoint, cloudCenter);
            if (distance < MIN_CLOUD_SPACING) {
                return true; // Too close to existing cloud
            }
        }
        return false;
    }

    // OPTIMIZATION NOTE: getCloudLocations(), getOtherMissilesImpactPoints(), and countTargetedShips()
    // have been combined into a single customData iteration in selectBestTarget() for better performance

    /**
     * Register this missile's target in customData for coordination
     * Also stores predicted impact point for cloud overlap avoidance
     */
    private void registerTarget(ShipAPI ship) {
        if (engine != null && ship != null) {
            // Register target ship for multi-targeting tracking
            engine.getCustomData().put(MISSILE_ID, ship);

            // Register predicted impact point for overlap avoidance
            Vector2f predictedImpact = predictImpactPoint(ship);
            String impactKey = MISSILE_ID.replace("TARGET", "IMPACT");
            engine.getCustomData().put(impactKey, predictedImpact);
        }
    }

    /**
     * Update registered impact point for moving targets
     * Called periodically (every 2.5s) to keep prediction accurate
     */
    private void updateRegisteredImpactPoint(ShipAPI ship) {
        if (engine != null && ship != null) {
            // Recalculate predicted impact point
            Vector2f predictedImpact = predictImpactPoint(ship);

            // Update only the impact point entry (target ship entry unchanged)
            String impactKey = MISSILE_ID.replace("TARGET", "IMPACT");
            engine.getCustomData().put(impactKey, predictedImpact);
        }
    }

    /**
     * Clean up target registration when missile expires
     * Removes both target ship and predicted impact point entries
     */
    private void cleanupTargetRegistration() {
        if (engine != null) {
            // Remove target ship registration
            engine.getCustomData().remove(MISSILE_ID);

            // Remove predicted impact point registration
            String impactKey = MISSILE_ID.replace("TARGET", "IMPACT");
            engine.getCustomData().remove(impactKey);
        }
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;

        // Register ship target for multi-missile coordination
        if (target instanceof ShipAPI) {
            registerTarget((ShipAPI) target);
        }
    }

    public void init(CombatEngineAPI engine) {
    }
}