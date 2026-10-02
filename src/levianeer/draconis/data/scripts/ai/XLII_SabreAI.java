//By Tartiflette, modified for XLII Sabre missiles
package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.util.MagicTargeting;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Custom missile AI for XLII_sabre missiles launched by XLII_MissileBarrageStats ship system.
 */

@SuppressWarnings("deprecation")
public class XLII_SabreAI implements MissileAIPlugin, GuidedMissileAI {

    //////////////////////
    //     SETTINGS     //
    //////////////////////

    private final boolean TARGET_SWITCH = false;

    // Random target selection (ship system will override this with volley target)
    private final MagicTargeting.targetSeeking seeking = MagicTargeting.targetSeeking.FULL_RANDOM;

    // Target class priorities - focus on larger ships (ignore fighters)
    private final int fighters = 0;
    private final int frigates = 2;
    private final int destroyers = 3;
    private final int cruisers = 4;
    private final int capitals = 5;

    private final int SEARCH_CONE = 360;
    private final int MAX_SEARCH_RANGE = 2500;
    private final boolean FAILSAFE = true;

    private final boolean LEADING = true;
    private float ECCM = 2; // Precision without ECCM hullmod

    // No wave motion - direct flight path
    private final float WAVE_AMPLITUDE = -1;

    private float PRECISION_RANGE = 500;

    // Simple steering without oversteer corrections
    private final float DAMPING = 0.1f;

    // Obstacle avoidance - very strong avoidance for precision focus-fire
    private final float AVOIDANCE_DETECTION_RANGE = 450f;
    private final float MIN_OBSTACLE_DISTANCE = 200f;

    //////////////////////
    //    VARIABLES     //
    //////////////////////

    private final float MAX_SPEED;
    private CombatEngineAPI engine;
    private final MissileAPI MISSILE;
    private CombatEntityAPI target;
    private Vector2f lead = new Vector2f();
    private boolean launch = true;
    private float timer = 0, check = 0f;

    //////////////////////
    //  INITIALIZATION  //
    //////////////////////

    public XLII_SabreAI(MissileAPI missile, ShipAPI launchingShip) {
        this.MISSILE = missile;
        MAX_SPEED = missile.getMaxSpeed();

        if (launchingShip != null && launchingShip.getVariant().getHullMods().contains("eccm")) {
            ECCM = 1;
        }

        // Calculate precision range factor
        PRECISION_RANGE = (float) Math.pow((2 * PRECISION_RANGE), 2);
    }

    //////////////////////
    //   MAIN AI LOOP   //
    //////////////////////

    @Override
    public void advance(float amount) {
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }

        if (engine.isPaused() || MISSILE.isFading() || MISSILE.isFizzling()) {
            return;
        }

        // Check if target is dead/invalid - fade out if no retargeting
        if (target != null) {
            boolean targetInvalid = false;
            if (target instanceof ShipAPI) {
                ShipAPI shipTarget = (ShipAPI) target;
                targetInvalid = !shipTarget.isAlive() || shipTarget.isHulk();
            } else {
                targetInvalid = !engine.isEntityInPlay(target);
            }

            // If target is dead and retargeting disabled, fade out cleanly (0.5s)
            if (targetInvalid && !TARGET_SWITCH) {
                MISSILE.flameOut();
                return;
            }
        }

        if (target == null
                || (TARGET_SWITCH
                && ((target instanceof ShipAPI && !((ShipAPI) target).isAlive())
                || !engine.isEntityInPlay(target))
        )
        ) {
            setTarget(
                    MagicTargeting.pickTarget(
                            MISSILE,
                            seeking,
                            MAX_SEARCH_RANGE,
                            SEARCH_CONE,
                            fighters,
                            frigates,
                            destroyers,
                            cruisers,
                            capitals,
                            FAILSAFE
                    )
            );

            MISSILE.giveCommand(ShipCommand.ACCELERATE);
            return;
        }

        timer += amount;

        if (launch || timer >= check) {
            launch = false;
            timer -= check;

            check = Math.min(
                    0.25f,
                    Math.max(
                            0.05f,
                            MathUtils.getDistanceSquared(MISSILE.getLocation(), target.getLocation()) / PRECISION_RANGE)
            );

            if (LEADING) {
                lead = AIUtils.getBestInterceptPoint(
                        MISSILE.getLocation(),
                        MAX_SPEED * ECCM, // ECCM improves leading accuracy
                        target.getLocation(),
                        target.getVelocity()
                );

                if (lead == null) {
                    lead = target.getLocation();
                }
            } else {
                lead = target.getLocation();
            }
        }

        float correctAngle = VectorUtils.getAngle(
                MISSILE.getLocation(),
                lead
        );

        // Obstacle avoidance - strong steering away from obstacles
        CombatEntityAPI obstacle = findNearestObstacle();
        if (obstacle != null) {
            float distanceToObstacle = MathUtils.getDistance(MISSILE.getLocation(), obstacle.getLocation());

            if (distanceToObstacle < MIN_OBSTACLE_DISTANCE) {
                float angleToObstacle = VectorUtils.getAngle(MISSILE.getLocation(), obstacle.getLocation());

                // Calculate avoidance angle (perpendicular to obstacle)
                float relativeAngle = MathUtils.getShortestRotation(MISSILE.getFacing(), angleToObstacle);
                float avoidanceAngle;
                if (relativeAngle > 0) {
                    // Obstacle on left, steer right
                    avoidanceAngle = angleToObstacle - 90f;
                } else {
                    // Obstacle on right, steer left
                    avoidanceAngle = angleToObstacle + 90f;
                }

                // Blend avoidance with target angle (prioritize avoidance when close)
                float avoidanceWeight = 1.0f - (distanceToObstacle / MIN_OBSTACLE_DISTANCE);
                avoidanceWeight = Math.min(1.0f, avoidanceWeight * 3.0f); // Amplify for very strong avoidance

                // Blend angles correctly using shortest-rotation interpolation to avoid
                // wraparound artifacts (e.g. blending 350° and 10° linearly gives 180°, the wrong direction)
                float delta = MathUtils.getShortestRotation(correctAngle, avoidanceAngle);
                correctAngle = MathUtils.clampAngle(correctAngle + delta * avoidanceWeight);
            }
        }

        float aimAngle = MathUtils.getShortestRotation(MISSILE.getFacing(), correctAngle);

        MISSILE.giveCommand(ShipCommand.ACCELERATE);

        if (aimAngle < 0) {
            MISSILE.giveCommand(ShipCommand.TURN_RIGHT);
        } else {
            MISSILE.giveCommand(ShipCommand.TURN_LEFT);
        }

        // Damping - smooth out angular velocity when close to target angle
        if (Math.abs(aimAngle) < Math.abs(MISSILE.getAngularVelocity()) * DAMPING) {
            MISSILE.setAngularVelocity(aimAngle / DAMPING);
        }
    }

    //////////////////////
    //    TARGETING     //
    //////////////////////

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }

    public void init(CombatEngineAPI engine) {
    }

    //////////////////////
    //  OBSTACLE AVOID  //
    //////////////////////

    /**
     * Finds the nearest obstacle in the missile's path.
     * Detects: asteroids, non-target enemy ships, and dead ships (all).
     * Returns null if no obstacles detected within range.
     */
    private CombatEntityAPI findNearestObstacle() {
        CombatEntityAPI nearestObstacle = null;
        float minDistance = Float.MAX_VALUE;
        Vector2f missilePos = MISSILE.getLocation();
        float missileFacing = MISSILE.getFacing();

        for (CombatEntityAPI asteroid : engine.getAsteroids()) {
            if (asteroid == null) continue;

            float distance = MathUtils.getDistance(missilePos, asteroid.getLocation());
            if (distance > AVOIDANCE_DETECTION_RANGE) continue;

            float angleToAsteroid = VectorUtils.getAngle(missilePos, asteroid.getLocation());
            float angleDiff = Math.abs(MathUtils.getShortestRotation(missileFacing, angleToAsteroid));

            // 90-degree cone ahead
            if (angleDiff < 90f && distance < minDistance) {
                minDistance = distance;
                nearestObstacle = asteroid;
            }
        }

        for (ShipAPI ship : engine.getShips()) {
            if (ship == null) continue;

            // Skip if this is our target (we WANT to hit it)
            if (ship == target) continue;

            boolean shouldAvoid = false;

            // Avoid all dead ships regardless of owner
            if (ship.isHulk() || !ship.isAlive() || ship.isExpired()) {
                shouldAvoid = true;
            }

            if (!shouldAvoid && ship.getOwner() != MISSILE.getOwner()) {
                shouldAvoid = true;
            }

            // Skip friendly living ships (not obstacles)
            if (!shouldAvoid) continue;

            float distToCenter = MathUtils.getDistance(missilePos, ship.getLocation());
            float combinedRadius = ship.getCollisionRadius();
            float effectiveDistance = distToCenter - combinedRadius;

            if (effectiveDistance > AVOIDANCE_DETECTION_RANGE) continue;

            float angleToShip = VectorUtils.getAngle(missilePos, ship.getLocation());
            float angleDiff = Math.abs(MathUtils.getShortestRotation(missileFacing, angleToShip));

            // 90-degree cone ahead
            if (angleDiff < 90f && distToCenter < minDistance) {
                minDistance = distToCenter;
                nearestObstacle = ship;
            }
        }

        return nearestObstacle;
    }
}
