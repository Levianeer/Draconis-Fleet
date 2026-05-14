// By Tartiflette
// HEAVILY modified for XLII Mist Cloud system
package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import levianeer.draconis.data.scripts.XLII_MistCloudConstants;
import levianeer.draconis.data.scripts.weapons.XLII_MistCloudOnHitEffect;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XLII_SlapERMissileAI implements MissileAIPlugin, GuidedMissileAI {

    // Sorry about the absolutely horrible comment mess :sob:

    //range under which the missile start to get progressively more precise in game units.
    // Set in constructor to (2 * PRECISION_RANGE)^2 = (2*750)^2 = 2,250,000
    // Used as a squared-distance threshold; must NOT be declared final due to constructor transform
    private float PRECISION_RANGE = 750;

    //////////////////////
    //    VARIABLES     //
    //////////////////////

    //max speed of the missile after modifiers.
    private final float MAX_SPEED;
    private CombatEngineAPI engine;
    private final MissileAPI MISSILE;
    private CombatEntityAPI target;
    private Vector2f lead = new Vector2f();
    private boolean launch = true;
    private float timer = 0, check = 0f;
    private int missileOwner = -1;

    // Proximity failsafe for collision pass-through bug
    private float closestDistanceToTarget = Float.MAX_VALUE;
    private boolean wasInDetonationRange = false;

    //////////////////////
    //  DATA COLLECTING //
    //////////////////////

    public XLII_SlapERMissileAI(MissileAPI missile) {
        this.MISSILE = missile;
        MAX_SPEED = missile.getMaxSpeed();
        //calculate the precision range factor
        PRECISION_RANGE = (float) Math.pow((2 * PRECISION_RANGE), 2);

        // Determine missile owner side
        if (missile.getSource() != null) {
            missileOwner = missile.getSource().getOwner();
        } else {
            Global.getLogger(XLII_SlapERMissileAI.class).warn(
                "Draconis: SlapER missile spawned with null source - team detection will default to player-side"
            );
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
            return;
        }

        //assigning a target if there is none or it's no longer valid
        //Does the missile switch its target if it has been destroyed?
        if (target == null || !isValidCurrentTarget()) {
            setTarget(selectBestTarget());

            // Reset proximity tracking for new target
            closestDistanceToTarget = Float.MAX_VALUE;
            wasInDetonationRange = false;

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
            //Is the missile lead the target or tailchase it?
            //best intercepting point
            //Leading loss without ECCM hullmod. The higher, the less accurate the leading calculation will be.
            //   1: perfect leading with and without ECCM
            //   2: half precision without ECCM
            //   3: a third as precise without ECCM. Default
            //   4, 5, 6 etc. : 1/4th, 1/5th, 1/6th etc. precision.
            //A VALUE BELOW 1 WILL PREVENT THE MISSILE FROM EVER HITTING ITS TARGET!
            float ECCM = 1;
            lead = AIUtils.getBestInterceptPoint(
                    MISSILE.getLocation(),
                    MAX_SPEED * ECCM, //if eccm is installed the point is accurate, otherwise it's placed closer to the target (almost tailchasing)
                    target.getLocation(),
                    target.getVelocity()
            );
            //null pointer protection
            if (lead == null) {
                lead = target.getLocation();
            }
        }

        // Proximity failsafe: detect if missile passed through target without collision
        if (target instanceof ShipAPI targetShip) {
            float distToTarget = MathUtils.getDistance(MISSILE.getLocation(), target.getLocation());
            float detonationRange = targetShip.getCollisionRadius() + MISSILE.getCollisionRadius();

            // Track if we entered detonation range
            if (distToTarget <= detonationRange) {
                wasInDetonationRange = true;
                closestDistanceToTarget = Math.min(closestDistanceToTarget, distToTarget);
            }

            // If we were close but now moving away -> manual detonation (collision passed through)
            if (wasInDetonationRange && distToTarget > closestDistanceToTarget + 100f) {
                wasInDetonationRange = false;
                triggerManualDetonation(targetShip);
                return;
            }
        }

        //best velocity vector angle for interception
        float correctAngle = VectorUtils.getAngle(
                MISSILE.getLocation(),
                lead
        );

        //Does the missile try to correct it's velocity vector as fast as possible or just point to the desired direction and drift a bit?
        //  Can create strange results with large waving
        //  Require a projectile with a decent turn rate and around twice that in turn acceleration
        //  Usefull for slow torpedoes with low forward acceleration, or ultra precise anti-fighter missiles.
        //REQUIRE NO OVERSHOOT ANGLE!
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

        //target angle for interception
        float aimAngle = MathUtils.getShortestRotation(MISSILE.getFacing(), correctAngle);

        //////////////////////
        //Angle with the target beyond which the missile turn around without accelerating. Avoid endless circling.
        //  Set to a negative value to disable
        MISSILE.giveCommand(ShipCommand.ACCELERATE);

        if (aimAngle < 0) {
            MISSILE.giveCommand(ShipCommand.TURN_RIGHT);
        } else {
            MISSILE.giveCommand(ShipCommand.TURN_LEFT);
        }

        // Damp angular velocity if the missile aim is getting close to the targeted angle
        //Damping of the turn speed when closing on the desired aim. The smaller the snappier.
        float DAMPING = 0.2f;
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
        if (missileOwner == -1) return shipOwner != 1; // Explicit fallback with no silent failure

        // Same owner = definitely same team
        if (shipOwner == missileOwner) return true;

        // If missile owner is player (owner 0), allies are anyone except enemy (owner 1)
        if (missileOwner == 0) {
            return shipOwner != 1;
        }

        // If missile owner is enemy (owner 1), allies are only owner 1
        if (missileOwner == 1) {
            return false;
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
        if (MISSILE.isFading() || MISSILE.isFizzling()) return;

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
     * Check if the current target is still valid this frame.
     * Returns false if the target is dead, out of play, or has entered a mist cloud.
     */
    private boolean isValidCurrentTarget() {
        if (!(target instanceof ShipAPI ship)) return false;
        if (!ship.isAlive() || !engine.isEntityInPlay(ship)) return false;
        return isShipInCloud(ship, getMistCloudLocations());
    }

    /**
     * Select best target using a priority list:
     * 1. Enemy in weapon range of any allied ship
     * 2. Allied ship with lowest hull below 75%
     * 3. Largest enemy ship on the map
     */
    private ShipAPI selectBestTarget() {
        if (engine == null) return null;

        List<Vector2f> clouds = getMistCloudLocations();
        List<ShipAPI> ships = engine.getShips();

        ShipAPI result = findEnemyInAllyWeaponRange(ships, clouds);
        if (result != null) return result;

        result = findMostDamagedAllyBelow75(ships, clouds);
        if (result != null) return result;

        return findLargestEnemy(ships, clouds);
    }

    /**
     * Priority 1: Find the closest enemy that is within weapon range of any allied ship.
     */
    private ShipAPI findEnemyInAllyWeaponRange(List<ShipAPI> ships, List<Vector2f> clouds) {
        ShipAPI best = null;
        float bestDist = Float.MAX_VALUE;

        for (ShipAPI enemy : ships) {
            if (isValidCandidate(enemy, clouds)) continue;
            if (isOnSameTeam(enemy.getOwner())) continue;

            for (ShipAPI ally : ships) {
                if (!ally.isAlive() || ally.isHulk()) continue;
                if (!isOnSameTeam(ally.getOwner())) continue;

                float maxRange = 0f;
                for (WeaponGroupAPI group : ally.getWeaponGroupsCopy()) {
                    for (WeaponAPI weapon : group.getWeaponsCopy()) {
                        if (weapon.getRange() > maxRange) maxRange = weapon.getRange();
                    }
                }

                if (MathUtils.getDistanceSquared(ally.getLocation(), enemy.getLocation()) <= maxRange * maxRange) {
                    float dist = MathUtils.getDistance(MISSILE.getLocation(), enemy.getLocation());
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = enemy;
                    }
                    break; // This enemy qualifies; no need to check more allies
                }
            }
        }
        return best;
    }

    /**
     * Priority 2: Find the allied ship with the lowest hull percentage, if below 75%.
     */
    private ShipAPI findMostDamagedAllyBelow75(List<ShipAPI> ships, List<Vector2f> clouds) {
        ShipAPI best = null;
        float lowestHull = 0.75f;

        for (ShipAPI ship : ships) {
            if (isValidCandidate(ship, clouds)) continue;
            if (!isOnSameTeam(ship.getOwner())) continue;

            float hullPct = ship.getHitpoints() / ship.getMaxHitpoints();
            if (hullPct < lowestHull) {
                lowestHull = hullPct;
                best = ship;
            }
        }
        return best;
    }

    /**
     * Priority 3: Find the largest enemy ship; tiebreak by distance to missile.
     */
    private ShipAPI findLargestEnemy(List<ShipAPI> ships, List<Vector2f> clouds) {
        ShipAPI best = null;
        int bestSize = -1;
        float bestDist = Float.MAX_VALUE;

        for (ShipAPI ship : ships) {
            if (isValidCandidate(ship, clouds)) continue;
            if (isOnSameTeam(ship.getOwner())) continue;

            int size = ship.getHullSize().ordinal();
            float dist = MathUtils.getDistance(MISSILE.getLocation(), ship.getLocation());
            if (size > bestSize || (size == bestSize && dist < bestDist)) {
                bestSize = size;
                bestDist = dist;
                best = ship;
            }
        }
        return best;
    }

    /**
     * Check if a ship is a valid targeting candidate.
     */
    private boolean isValidCandidate(ShipAPI ship, List<Vector2f> clouds) {
        if (ship == null || !ship.isAlive() || ship.isHulk()) return true;
        if (ship.isShuttlePod() || ship.isDrone() || ship.isFighter()) return true;
        //range in which the missile seek a target in game units.
        int MAX_SEARCH_RANGE = 100000;
        if (MathUtils.getDistanceSquared(MISSILE.getLocation(), ship.getLocation()) > (float) MAX_SEARCH_RANGE * MAX_SEARCH_RANGE) return true;
        return !isShipInCloud(ship, clouds);
    }

    /**
     * Check if ship is currently inside any mist cloud.
     */
    private boolean isShipInCloud(ShipAPI ship, List<Vector2f> cloudLocations) {
        for (Vector2f cloudCenter : cloudLocations) {
            if (MathUtils.getDistanceSquared(ship.getLocation(), cloudCenter) < XLII_MistCloudConstants.CLOUD_RADIUS_SQ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Read all active mist cloud positions from combat engine custom data.
     */
    private List<Vector2f> getMistCloudLocations() {
        List<Vector2f> clouds = new ArrayList<>();
        Map<String, Object> customData = engine.getCustomData();
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            if (entry.getKey().startsWith("XLII_MIST_CLOUD_") && entry.getValue() instanceof Vector2f) {
                clouds.add((Vector2f) entry.getValue());
            }
        }
        return clouds;
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }
}