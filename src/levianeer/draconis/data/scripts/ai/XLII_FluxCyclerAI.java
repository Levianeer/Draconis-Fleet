package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.util.IntervalUtil;
import levianeer.draconis.data.scripts.shipsystems.XLII_FluxCyclerStats;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * AI for the Flux Cycler ship system (toggle SHIELD_MOD).
 * Adapted from Diable Avionics' virtuous citadel AI: scans for incoming
 * projectile/missile threats and ambient enemy pressure to raise the shield
 * pre-emptively, rather than reacting only after damage has already landed.
 *
 * Also aims for a target weapon-buff level per activation rather than dropping
 * the shield the instant a threat clears, then holds off raising it again
 * (barring real danger) until most of that buff has decayed - otherwise the AI
 * flickers the shield on and off constantly and never lets the buff pay off.
 */
public class XLII_FluxCyclerAI implements ShipSystemAIScript, DamageTakenModifier {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private ShipwideAIFlags flags;
    private final IntervalUtil tracker = new IntervalUtil(0.25f, 0.75f);

    // ==================== TUNING PARAMETERS ====================

    private static final float THREAT_SCAN_RANGE = 400f;
    private static final float PROJECTILE_DANGER_THRESHOLD = 750f;
    private static final float SHIP_DANGER_SCALE = 15f;
    private static final float NEARBY_ENEMY_RANGE = 1000f;
    private static final float DEACTIVATE_FLUX_LEVEL = 0.85f;

    // Stop actively holding the shield once this fraction of the max weapon buff is banked.
    private static final float TARGET_BUFF_FRACTION = 0.8f;
    // After voluntarily dropping the shield, don't raise it again for non-emergency reasons
    // until this fraction of the buff's decay window has passed.
    private static final float REACTIVATE_GATE_FRACTION = 0.8f;
    // While gated, only an immediate threat AND flux already this high counts as an emergency
    // worth breaking the gate for - otherwise routine ongoing fire (which is normal during any
    // firefight) would re-trigger the gate check every tick and defeat it entirely.
    private static final float EMERGENCY_FLUX_LEVEL = 0.6f;
    // Minimum time to hold the shield up once raised, so a fast buff-target hit under heavy
    // fire doesn't cause it to drop again within the same or next AI tick.
    private static final float MIN_ACTIVE_DURATION = 2f;

    // ==================== INSTANCE STATE ====================

    private boolean listenerRegistered = false;
    private boolean wasActive = false;
    private float damageSoakedThisActivation = 0f;
    private float reactivateGateRemaining = 0f;
    private float activeDuration = 0f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.flags = flags;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (!listenerRegistered) {
            ship.addListener(this);
            listenerRegistered = true;
        }

        boolean isActiveNow = system.isActive();
        if (wasActive && !isActiveNow) {
            // System went inactive either because we lowered it or because the ship's actual
            // shield was force-dropped (taking this SHIELD_MOD off too). Either way the buff is
            // gone, so gate reactivation the same as a voluntary drop - otherwise an external
            // shield-down would skip the gate and the AI would immediately treat this as ready to reactivate.
            reactivateGateRemaining = XLII_FluxCyclerStats.DECAY_DURATION * REACTIVATE_GATE_FRACTION;
        }
        wasActive = isActiveNow;

        reactivateGateRemaining = Math.max(0f, reactivateGateRemaining - amount);
        if (isActiveNow) activeDuration += amount;

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        if (!isActiveNow) {
            if (hasHardBlock() || !AIUtils.canUseSystemThisFrame(ship)) return;

            float projectileDanger = 0f;
            for (DamagingProjectileAPI p : getIncomingThreats()) {
                projectileDanger += p.getDamageAmount();
            }
            boolean underImmediateThreat = projectileDanger > PROJECTILE_DANGER_THRESHOLD;

            if (reactivateGateRemaining > 0f) {
                // Still letting the last buff play out - only an actual emergency
                // (real incoming fire while already under flux pressure) breaks that early.
                if (underImmediateThreat && ship.getFluxLevel() > EMERGENCY_FLUX_LEVEL) {
                    activate();
                }
                return;
            }

            if (underImmediateThreat) {
                activate();
                return;
            }

            float shipDanger = 0f;
            for (ShipAPI enemy : AIUtils.getNearbyEnemies(ship, NEARBY_ENEMY_RANGE)) {
                if (enemy.isDrone() || enemy.isFighter()) continue;
                shipDanger += enemy.getHullSpec().getFleetPoints()
                        * (1.5f - (MathUtils.getDistanceSquared(ship, enemy) / 1000000f));
            }

            float fluxThreshold = (float) Math.pow(0.25 + ship.getFluxLevel(), 2) * SHIP_DANGER_SCALE;
            if (shipDanger > fluxThreshold) {
                activate();
            }
        } else {
            // Safety exits apply immediately regardless of how long the shield's been up.
            if (ship.isRetreating() || ship.getFluxLevel() > DEACTIVATE_FLUX_LEVEL) {
                deactivate();
                return;
            }

            if (activeDuration < MIN_ACTIVE_DURATION) return;

            boolean buffTargetReached = damageSoakedThisActivation >= TARGET_BUFF_FRACTION * XLII_FluxCyclerStats.MAX_RAW_DAMAGE;
            boolean noThreat = getIncomingThreats().isEmpty() && AIUtils.getNearbyEnemies(ship, NEARBY_ENEMY_RANGE).isEmpty();

            if (buffTargetReached || noThreat) {
                deactivate();
            }
        }
    }

    private void activate() {
        ship.useSystem();
        damageSoakedThisActivation = 0f;
        activeDuration = 0f;
        tracker.setElapsed(-0.5f);
        // KEEP_SHIELDS_ON is refreshed every frame by XLII_FluxCyclerStats, not here - this AI
        // only ticks every 0.25-0.75s, too infrequent to stop the flag decaying between polls.
    }

    private void deactivate() {
        ship.useSystem();
        // Reactivation gate is set from the active->inactive edge detection above, once the
        // system actually finishes lowering - covers this call and any external shield-down.
    }

    private boolean hasHardBlock() {
        if (ship.isRetreating()) return true;
        if (ship.getFluxTracker().isOverloadedOrVenting()) return true;
        return flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY);
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (system.isActive() && shieldHit) {
            damageSoakedThisActivation += damage.getDamage();
        }
        return null;
    }

    private List<DamagingProjectileAPI> getIncomingThreats() {
        List<DamagingProjectileAPI> threats = new ArrayList<>();

        for (DamagingProjectileAPI p : CombatUtils.getProjectilesWithinRange(ship.getLocation(), THREAT_SCAN_RANGE)) {
            if (p.getDamageAmount() < 100) continue;
            float angleToShip = VectorUtils.getAngle(p.getLocation(), ship.getLocation());
            if (Math.abs(MathUtils.getShortestRotation(p.getFacing(), angleToShip)) < 45) {
                threats.add(p);
            }
        }

        for (MissileAPI m : AIUtils.getNearbyEnemyMissiles(ship, THREAT_SCAN_RANGE)) {
            if (m.getDamageAmount() < 150) continue;
            if (m.isGuided()) {
                threats.add(m);
                continue;
            }
            float angleToShip = VectorUtils.getAngle(m.getLocation(), ship.getLocation());
            if (Math.abs(MathUtils.getShortestRotation(m.getFacing(), angleToShip)) < 45) {
                threats.add(m);
            }
        }
        return threats;
    }
}
