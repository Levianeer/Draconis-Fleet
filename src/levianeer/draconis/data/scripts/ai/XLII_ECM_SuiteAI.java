package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class XLII_ECM_SuiteAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.1f, 0.2f);
    private float bestValueEver = 0f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0) return;
            if (system.isOutOfAmmo()) return;
            if (system.isActive()) return;

            float currentValue = calculateMissileThreats();

            if (currentValue > bestValueEver) {
                bestValueEver = currentValue;
            }

            // Activation thresholds - be more conservative with limited charges
            if (currentValue >= 0.7f) {
                ship.useSystem();
            } else if (currentValue >= 0.5f && system.getAmmo() > 1) {
                // Use more liberally if we have multiple charges
                ship.useSystem();
            } else if (currentValue >= 0.4f && ship.getHullLevel() < 0.3f) {
                // Use defensively when critically damaged
                ship.useSystem();
            }
        }
    }

    private float calculateMissileThreats() {
        Vector2f shipLoc = ship.getLocation();
        // Fighters have reduced ECM range when they use this system
        float disableRadius = ship.isFighter() ? 500f : 1000f;

        int threateningMissiles = 0;
        float totalThreatValue = 0f;

        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.getSource() == null) continue;
            if (missile.getSource().getOwner() == ship.getOwner()) continue;
            if (missile.isFading() || missile.didDamage()) continue;

            float distance = Misc.getDistance(shipLoc, missile.getLocation());
            if (distance > disableRadius) continue;

            float threatValue = assessMissileThreat(missile, distance);
            if (threatValue > 0f) {
                threateningMissiles++;
                totalThreatValue += threatValue;
            }
        }

        if (threateningMissiles == 0) return 0f;

        // Base value from missile threat
        float value = Math.min(totalThreatValue, 1f);

        // Bonus for multiple missiles
        if (threateningMissiles >= 3) value += 0.3f;
        if (threateningMissiles >= 5) value += 0.2f;

        // Urgency modifiers
        if (ship.getHullLevel() < 0.5f) value *= 1.2f;
        if (ship.getFluxTracker().getFluxLevel() > 0.7f) value *= 1.1f;

        // AI state modifiers
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
            value *= 1.3f; // More valuable when retreating
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)) {
            value *= 1.2f; // Help when overwhelmed
        }

        return Math.min(value, 1f);
    }

    private float assessMissileThreat(MissileAPI missile, float distance) {
        float threat;

        // Base threat - since we don't have getWeaponSize(), use missile damage/speed as heuristics
        float damage = missile.getDamageAmount();
        if (damage < 100f) threat = 0.15f;        // Light missiles
        else if (damage < 500f) threat = 0.3f;    // Medium missiles
        else threat = 0.5f;                       // Heavy missiles/torpedoes

        // Higher threat for missiles targeting us or nearby allies
        if (isMissileTargetingUsOrAllies(missile)) {
            threat *= 2f;
        }

        // Distance factor - closer missiles are more urgent
        float distanceFactor = 1f - (distance / (ship.isFighter() ? 500f : 1000f));
        threat *= (0.5f + distanceFactor * 0.5f);

        // Velocity factor - fast approaching missiles are urgent
        Vector2f toShip = Vector2f.sub(ship.getLocation(), missile.getLocation(), new Vector2f());
        if (toShip.length() > 0f) {
            toShip.normalise();
            Vector2f missileVel = new Vector2f(missile.getVelocity());
            if (missileVel.length() > 0f) {
                missileVel.normalise();
                float dot = Vector2f.dot(toShip, missileVel);
                if (dot > 0.3f) { // Missile heading towards us
                    threat *= 1.3f;
                }
            }
        }

        // Special missile types
        if (isTorpedo(missile)) {
            threat *= 1.5f; // Torpedoes are high priority
        }

        return threat;
    }

    private boolean isMissileTargetingUsOrAllies(MissileAPI missile) {
        // Check if missile has guided AI and get its target
        if (missile.getMissileAI() instanceof GuidedMissileAI guidedAI) {
            CombatEntityAPI target = guidedAI.getTarget();
            if (target instanceof ShipAPI missileTarget) {

                // Check if targeting us
                if (missileTarget == ship) return true;

                // Check if targeting nearby allies
                if (missileTarget.getOwner() == ship.getOwner()) {
                    float distance = Misc.getDistance(ship.getLocation(), missileTarget.getLocation());
                    return distance <= 800f; // Protect nearby allies
                }
            }
        }

        return false;
    }

    private boolean isTorpedo(MissileAPI missile) {
        // Add null check for weapon spec
        if (missile.getWeaponSpec() == null) {
            return false;
        }
        // Large, slow missiles are likely torpedoes
        return missile.getWeaponSpec().getSize() == WeaponAPI.WeaponSize.LARGE &&
                missile.getVelocity().length() < 200f;
    }
}