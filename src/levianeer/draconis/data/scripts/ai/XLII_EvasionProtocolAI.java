// NO LONGER USED
package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * AI for the Evasion Protocol ship system.
 * <p>
 * Primary purpose: Mobility boost for rapid repositioning, pursuit, and flanking
 * Secondary purpose: Missile countermeasure when in critical danger
 */
public class XLII_EvasionProtocolAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.2f, 0.5f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;

        // Add jitter to desynchronize fleet-wide checks
        tracker.setInterval(
            0.2f + (float) Math.random() * 0.2f,
            0.5f + (float) Math.random() * 0.3f
        );
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        // Early returns for invalid states
        if (engine.isPaused()) return;
        if (system.isActive()) return;
        if (system.getCooldownRemaining() > 0f) return;
        if (system.isOutOfAmmo()) return; // Check if charges available
        if (ship.getFluxTracker().isOverloadedOrVenting()) return;

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        // Flux check (lenient given 0.075 cost, but prevent hard flux problems)
        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        float fluxCost = system.getFluxPerUse() / ship.getFluxTracker().getMaxFlux();
        float fluxAfterUse = fluxLevel + fluxCost;
        if (fluxAfterUse > 0.95f) return; // Only block if it would cap flux

        // Calculate threat priorities
        float criticalThreat = calculateCriticalThreat(missileDangerDir, collisionDangerDir);
        float mobilityNeed = calculateMobilityNeed(target);

        // Choose highest priority
        float activationValue = Math.max(criticalThreat, mobilityNeed);

        // Activation thresholds
        if (activationValue >= 0.7f) {
            // CRITICAL: Immediate use (missile swarm, imminent collision, pursuit opportunity)
            ship.useSystem();
        } else if (activationValue >= 0.5f) {
            // HIGH: Use if we have charges available
            if (system.getAmmo() >= 1) {
                ship.useSystem();
            }
        } else if (activationValue >= 0.3f) {
            // MEDIUM: Use if we have multiple charges or are in danger
            if (system.getAmmo() > 1 || ship.getHullLevel() < 0.4f) {
                ship.useSystem();
            }
        }
    }

    /**
     * Calculate critical defensive threats (missiles, collisions)
     * Returns 0.0-1.0+ threat level
     */
    private float calculateCriticalThreat(Vector2f missileDangerDir, Vector2f collisionDangerDir) {
        float threat = 0f;

        // Collision danger - immediate threat
        if (collisionDangerDir != null) {
            threat += 0.4f;
            // Higher if we're already damaged
            if (ship.getHullLevel() < 0.5f) threat += 0.3f;
        }

        // Missile threats - only use defensively when vulnerable
        if (shouldUseForMissiles()) {
            float missileThreat = calculateMissileThreat();
            threat += missileThreat;

            // Vanilla's basic missile danger check as fallback
            if (missileDangerDir != null) {
                threat += 0.2f;
            }
        }

        return Math.min(threat, 1.2f); // Allow exceeding 1.0 for true emergencies
    }

    /**
     * Calculate mobility/repositioning needs
     * Returns 0.0-1.0 need level
     */
    private float calculateMobilityNeed(ShipAPI target) {
        float need = 0f;

        // Check if we're moving slowly (system won't help much if already at speed)
        float currentSpeed = ship.getVelocity().length();
        float maxSpeed = ship.getMaxSpeed();
        boolean needsSpeedBoost = currentSpeed < maxSpeed * 0.6f;

        // HIGH PRIORITY: Offensive maneuvering
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING) && needsSpeedBoost) {
            need += 0.6f;
            // Even higher if target is vulnerable
            if (target != null && target.getFluxTracker().isOverloadedOrVenting()) {
                need += 0.2f;
            }
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.HARASS_MOVE_IN)) {
            need += 0.5f; // Close for attack
        }

        // MEDIUM PRIORITY: Tactical repositioning
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
            // Only use during retreat if ship is already moving (has committed to direction)
            // OR in critical danger - prevents wasting charges on indecisive retreats
            boolean isMovingWithPurpose = currentSpeed > maxSpeed * 0.3f;
            boolean isCriticalDanger = ship.getHullLevel() < 0.3f;

            if (isMovingWithPurpose || isCriticalDanger) {
                int nearbyThreats = countNearbyEnemies(800f);
                if (nearbyThreats >= 2) {
                    need += 0.35f; // Help escape when outnumbered
                } else if (nearbyThreats >= 1) {
                    need += 0.25f;
                }
            }
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MANEUVER_RANGE_FROM_TARGET)) {
            need += 0.3f; // Flanking/positioning
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MOVEMENT_DEST) && needsSpeedBoost) {
            need += 0.2f; // General movement
        }

        // LOW PRIORITY: Maintaining position/speed
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MAINTAINING_STRIKE_RANGE)) {
            if (needsSpeedBoost) {
                need += 0.2f;
            }
        }

        return Math.min(need, 1f);
    }

    /**
     * Scan for actual missile threats in range
     * Returns 0.0-1.0+ threat level
     */
    private float calculateMissileThreat() {
        Vector2f shipLoc = ship.getLocation();
        float threatRadius = 600f; // Effective countermeasure range

        int threateningMissiles = 0;
        float totalThreat = 0f;

        for (MissileAPI missile : engine.getMissiles()) {
            // Filter out invalid missiles
            if (missile.getSource() == null) continue;
            if (missile.getSource().getOwner() == ship.getOwner()) continue;
            if (missile.isFading() || missile.didDamage()) continue;

            float distance = Misc.getDistance(shipLoc, missile.getLocation());
            if (distance > threatRadius) continue;

            // Check if missile is heading toward us
            Vector2f toShip = Vector2f.sub(shipLoc, missile.getLocation(), new Vector2f());
            if (toShip.length() > 0f) {
                toShip.normalise();
                Vector2f missileVel = new Vector2f(missile.getVelocity());
                if (missileVel.length() > 0f) {
                    missileVel.normalise();
                    float dot = Vector2f.dot(toShip, missileVel);

                    if (dot > 0.3f) { // Heading toward us
                        threateningMissiles++;

                        // Base threat by damage
                        float damage = missile.getDamageAmount();
                        float baseThreat = damage < 100f ? 0.15f : (damage < 500f ? 0.25f : 0.4f);

                        // Distance factor (closer = more urgent)
                        float distanceFactor = 1f - (distance / threatRadius);

                        // Velocity factor (faster = more urgent)
                        float velocityFactor = Math.min(missile.getVelocity().length() / 300f, 1.5f);

                        float threat = baseThreat * (0.5f + distanceFactor * 0.5f) * velocityFactor;

                        // Check if targeting us directly
                        if (isMissileTargetingShip(missile)) {
                            threat *= 2f;
                        }

                        totalThreat += threat;
                    }
                }
            }
        }

        if (threateningMissiles == 0) return 0f;

        // Bonus for multiple missiles (swarm threat)
        float swarmBonus = 0f;
        if (threateningMissiles >= 3) swarmBonus += 0.2f;
        if (threateningMissiles >= 5) swarmBonus += 0.3f;

        return Math.min(totalThreat + swarmBonus, 1f);
    }

    /**
     * Check if we should use the system defensively for missiles
     * Only when vulnerable (low hull or high flux)
     */
    private boolean shouldUseForMissiles() {
        return ship.getHullLevel() < 0.5f || ship.getFluxTracker().getFluxLevel() > 0.7f;
    }

    /**
     * Check if a missile is targeting our ship
     */
    private boolean isMissileTargetingShip(MissileAPI missile) {
        if (missile.getMissileAI() instanceof GuidedMissileAI guidedAI) {
            CombatEntityAPI target = guidedAI.getTarget();
            return target == ship;
        }
        return false;
    }

    /**
     * Count enemy ships within specified radius
     */
    private int countNearbyEnemies(float radius) {
        Vector2f shipLoc = ship.getLocation();
        int count = 0;

        for (ShipAPI enemy : engine.getShips()) {
            if (enemy.getOwner() == ship.getOwner()) continue;
            if (enemy.isHulk() || enemy.isShuttlePod()) continue;

            float distance = Misc.getDistance(shipLoc, enemy.getLocation());
            if (distance <= radius) {
                count++;
            }
        }

        return count;
    }
}
