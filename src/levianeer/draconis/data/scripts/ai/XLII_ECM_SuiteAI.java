package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.shipsystems.XLII_MissileHijackStats;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

public class XLII_ECM_SuiteAI implements ShipSystemAIScript {

    // Signal Hijack redirects missiles at enemies instead of disabling them - useless against
    // unguided bombs, unlike the ECM Suite, so it shouldn't bother jamming them.
    private static final String HIJACK_SYSTEM_ID = "XLII_signal_hijack";

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private boolean isHijack;

    private final IntervalUtil tracker = new IntervalUtil(0.1f, 0.2f);
    private float bestValueEver = 0f;

    // OMG INHUMAN REACTIONS.
    private static final float REACTION_DELAY_MIN = 0.2f;
    private static final float REACTION_DELAY_MAX = 0.4f;
    private boolean activationPending = false;
    private float reactionTimer = 0f;

    // Hold fire briefly so one activation catches as many missiles as possible.
    private static final float MAX_WAVE_WAIT = 1f;
    private static final float IMPACT_SAFETY_MARGIN = 0.6f;
    private boolean waitingForWave = false;
    private float waveHoldTimer = 0f;

    // Missiles about to flame out on their own aren't worth jamming.
    private static final float FADE_OUT_MARGIN = 1f; // non-hijack (near-instant ECM) path only

    // Hijack has to survive the AI reaction delay, the chargeup, and then dwell continuously in range for
    // DWELL_THRESHOLD before ownership actually changes hands - a missile that flames out before then would
    // waste a limited-ammo activation converting nothing.
    private static final float HIJACK_CONVERSION_SLACK = 0.5f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;
        this.isHijack = HIJACK_SYSTEM_ID.equals(system.getId());
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        if (waitingForWave) {
            waveHoldTimer += amount;
        }

        if (activationPending) {
            reactionTimer -= amount;
            if (reactionTimer <= 0f) {
                activationPending = false;
                if (AIUtils.canUseSystemThisFrame(ship)) {
                    ship.useSystem();
                }
            }
            return;
        }

        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (!AIUtils.canUseSystemThisFrame(ship)) {
                waitingForWave = false;
                waveHoldTimer = 0f;
                return;
            }

            ThreatAssessment threats = calculateMissileThreats();

            if (threats.value > bestValueEver) {
                bestValueEver = threats.value;
            }

            // Activation thresholds - be more conservative with limited charges
            boolean isEmergency = threats.value >= 0.4f && ship.getHullLevel() < 0.3f;
            boolean wantsToFire = threats.value >= 0.7f
                    || (threats.value >= 0.5f && system.getAmmo() > 1)
                    || isEmergency;

            if (!wantsToFire) {
                waitingForWave = false;
                waveHoldTimer = 0f;
                return;
            }

            boolean impactImminent = threats.minTimeToImpact <= IMPACT_SAFETY_MARGIN;

            if (!isEmergency && !impactImminent && threats.incomingSoon > 0 && waveHoldTimer < MAX_WAVE_WAIT) {
                waitingForWave = true;
                return;
            }

            waitingForWave = false;
            waveHoldTimer = 0f;
            activationPending = true;
            reactionTimer = REACTION_DELAY_MIN + (REACTION_DELAY_MAX - REACTION_DELAY_MIN) * (float) Math.random();
        }
    }

    private ThreatAssessment calculateMissileThreats() {
        Vector2f shipLoc = ship.getLocation();
        // Fighters have reduced ECM range when they use this system
        float disableRadius = ship.isFighter() ? 250f : 1000f;
        // How far beyond the disable radius to watch for missiles that are about to close in.
        float watchRadius = disableRadius + (ship.isFighter() ? 150f : 500f);

        int threateningMissiles = 0;
        float totalThreatValue = 0f;
        int incomingSoon = 0;
        float minTimeToImpact = Float.MAX_VALUE;

        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.getSource() == null) continue;
            if (missile.getSource().getOwner() == ship.getOwner()) continue;
            if (missile.isFading() || missile.didDamage()) continue;
            if (missile.isFlare() || (isHijack && isBomb(missile))) continue;
            if (isAboutToFadeOut(missile)) continue;

            float distance = Misc.getDistance(shipLoc, missile.getLocation());
            if (distance > watchRadius) continue;

            if (distance <= disableRadius) {
                float threatValue = assessMissileThreat(missile, distance);
                if (threatValue > 0f) {
                    threateningMissiles++;
                    totalThreatValue += threatValue;

                    float closingSpeed = getClosingSpeed(missile);
                    if (closingSpeed > 0f) {
                        float timeToImpact = distance / closingSpeed;
                        if (timeToImpact < minTimeToImpact) minTimeToImpact = timeToImpact;
                    }
                }
            } else {
                // Just outside jamming range - count it if it'll be in range before we'd give up waiting.
                float closingSpeed = getClosingSpeed(missile);
                if (closingSpeed > 0f) {
                    float timeToRange = (distance - disableRadius) / closingSpeed;
                    if (timeToRange <= MAX_WAVE_WAIT) incomingSoon++;
                }
            }
        }

        if (threateningMissiles == 0) return new ThreatAssessment(0f, incomingSoon, minTimeToImpact);

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

        return new ThreatAssessment(Math.min(value, 1f), incomingSoon, minTimeToImpact);
    }

    private float getClosingSpeed(MissileAPI missile) {
        Vector2f toShip = Vector2f.sub(ship.getLocation(), missile.getLocation(), new Vector2f());
        if (toShip.length() <= 0f) return 0f;
        toShip.normalise();
        return Vector2f.dot(toShip, missile.getVelocity());
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

    private boolean isBomb(MissileAPI missile) {
        return missile.getWeaponSpec() != null
                && missile.getWeaponSpec().getAIHints().contains(WeaponAPI.AIHints.BOMB);
    }

    private boolean isAboutToFadeOut(MissileAPI missile) {
        float remainingFlightTime = missile.getMaxFlightTime() - missile.getFlightTime();
        float margin = isHijack
                ? REACTION_DELAY_MAX + system.getChargeUpDur() + XLII_MissileHijackStats.DWELL_THRESHOLD + HIJACK_CONVERSION_SLACK
                : FADE_OUT_MARGIN;
        return remainingFlightTime <= margin;
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

    private static final class ThreatAssessment {
        final float value;
        final int incomingSoon;
        final float minTimeToImpact;

        ThreatAssessment(float value, int incomingSoon, float minTimeToImpact) {
            this.value = value;
            this.incomingSoon = incomingSoon;
            this.minTimeToImpact = minTimeToImpact;
        }
    }
}