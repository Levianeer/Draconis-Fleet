package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

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
        if (engine.isPaused()) return;
        if (!AIUtils.canUseSystemThisFrame(ship)) return;

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        // Flux check (lenient given small cost, but prevent hard capping)
        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        float fluxCost = system.getFluxPerUse() / ship.getFluxTracker().getMaxFlux();
        if (fluxLevel + fluxCost > 0.95f) return;

        float criticalThreat = calculateCriticalThreat(missileDangerDir, collisionDangerDir);
        float mobilityNeed = calculateMobilityNeed(target);

        float activationValue = Math.max(criticalThreat, mobilityNeed);

        // If primarily mobility-driven, avoid ramming allies in the forward arc
        boolean offensiveUse = mobilityNeed >= criticalThreat;
        if (offensiveUse) {
            List<ShipAPI> nearbyAllies = AIUtils.getNearbyAllies(ship, 450f);
            for (ShipAPI ally : nearbyAllies) {
                if (ally.getCollisionClass() != CollisionClass.NONE
                        && ally.getCollisionClass() != CollisionClass.FIGHTER) {
                    float angle = VectorUtils.getAngle(ship.getLocation(), ally.getLocation());
                    if (Math.abs(MathUtils.getShortestRotation(angle, ship.getFacing())) <= 45f) {
                        return;
                    }
                }
            }
        }

        if (activationValue >= 0.7f) {
            ship.useSystem();
        } else if (activationValue >= 0.5f) {
            if (system.getAmmo() >= 1) ship.useSystem();
            else return;
        } else if (activationValue >= 0.3f) {
            if (system.getAmmo() > 1 || ship.getHullLevel() < 0.4f) ship.useSystem();
            else return;
        } else {
            return;
        }

        if (offensiveUse) flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_BACK_OFF, 3.5f);
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
            if (ship.getHullLevel() < 0.5f) threat += 0.3f;
        }

        // Missile threats - only use defensively when vulnerable
        if (shouldUseForMissiles()) {
            threat += calculateMissileThreat();
            if (missileDangerDir != null) {
                threat += 0.2f;
            }
        }

        return Math.min(threat, 1.2f); // Allow exceeding 1.0 for true emergencies
    }

    /**
     * Calculate mobility/repositioning needs
     * Returns 0.0-1.0 need level, or 0.0 if target is invalid for offensive use
     */
    private float calculateMobilityNeed(ShipAPI target) {
        // Don't use offensively against invalid targets or during retreat
        if (target == null) return 0f;
        if (!target.isAlive() || target.isAlly()) return 0f;
        if (target.isFighter() || target.isDrone() || target.isStation()
                || target.isStationModule() || target.getEngineController().isFlamedOut()) return 0f;
        if (ship.isRetreating()) return 0f;

        float need = 0f;

        float currentSpeed = ship.getVelocity().length();
        float maxSpeed = ship.getMaxSpeed();
        boolean needsSpeedBoost = currentSpeed < maxSpeed * 0.6f;

        // HIGH PRIORITY: Offensive maneuvering
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING) && needsSpeedBoost) {
            need += 0.6f;
            if (target.getFluxTracker().isOverloadedOrVenting()) {
                need += 0.2f;
            }
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.HARASS_MOVE_IN)) {
            need += 0.5f;
        }

        // MEDIUM PRIORITY: Escape when taking damage with committed hard flux
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)
                && flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)
                && ship.getHardFluxLevel() >= 0.6f) {
            need += 0.35f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MANEUVER_RANGE_FROM_TARGET)) {
            need += 0.3f; // Flanking/positioning
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MOVEMENT_DEST) && needsSpeedBoost) {
            need += 0.2f;
        }

        // LOW PRIORITY: Maintaining position/speed
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MAINTAINING_STRIKE_RANGE) && needsSpeedBoost) {
            need += 0.2f;
        }

        return Math.min(need, 1f);
    }

    /**
     * Scan for actual missile threats in range
     * Returns 0.0-1.0+ threat level
     */
    private float calculateMissileThreat() {
        Vector2f shipLoc = ship.getLocation();
        float threatRadius = 600f;

        int threateningMissiles = 0;
        float totalThreat = 0f;

        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.getSource() == null) continue;
            if (missile.getSource().getOwner() == ship.getOwner()) continue;
            if (missile.isFading() || missile.didDamage()) continue;

            float distance = Misc.getDistance(shipLoc, missile.getLocation());
            if (distance > threatRadius) continue;

            Vector2f toShip = Vector2f.sub(shipLoc, missile.getLocation(), new Vector2f());
            if (toShip.length() > 0f) {
                toShip.normalise();
                Vector2f missileVel = new Vector2f(missile.getVelocity());
                if (missileVel.length() > 0f) {
                    missileVel.normalise();
                    float dot = Vector2f.dot(toShip, missileVel);

                    if (dot > 0.3f) {
                        threateningMissiles++;

                        float damage = missile.getDamageAmount();
                        float baseThreat = damage < 100f ? 0.15f : (damage < 500f ? 0.25f : 0.4f);

                        float distanceFactor = 1f - (distance / threatRadius);
                        float velocityFactor = Math.min(missile.getVelocity().length() / 300f, 1.5f);

                        float threat = baseThreat * (0.5f + distanceFactor * 0.5f) * velocityFactor;

                        if (isMissileTargetingShip(missile)) {
                            threat *= 2f;
                        }

                        totalThreat += threat;
                    }
                }
            }
        }

        if (threateningMissiles == 0) return 0f;

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
}