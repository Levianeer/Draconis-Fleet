package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * Heatsink Vent ship system. One-shot (max uses:1, no regen): deliberately
 * does NOT call FluxTrackerAPI.ventFlux() - that puts the ship into the real
 * isVenting() state, which blocks weapons fire and shields exactly like
 * holding the vent key does, making it no better than vanilla Active
 * Venting. Instead, on activation it snapshots the ship's current flux and
 * the system's remaining active+down duration, then drains that exact
 * amount via decreaseFlux() at a flat rate timed to hit zero right as the
 * system finishes winding down - guaranteed full clear regardless of the
 * ship's own dissipation stat, and the ship can keep firing and shielding
 * the whole time. Also ejects a single cosmetic "heatsink" projectile
 * backward out of the ship on activation, and puffs vent-exhaust particles
 * (ramped by effectLevel like every other system here) from any SYSTEM
 * weapon slots the hull has while the system is in effect (same
 * cone-particle technique as XLII_ShuntDriveStats, sourced from system slots
 * the way XLII_DelayedFlareShot finds them instead of engines).
 */
public class XLII_HeatsinkVentStats extends BaseShipSystemScript {

    private static final String HEATSINK_WEAPON_ID = "XLII_heatsink_launcher";

    private static final Color VENT_PARTICLE_COLOR = new Color(190, 70, 235, 90);
    private final IntervalUtil particleInterval = new IntervalUtil(0.35f, 0.45f);

    private WeaponAPI heatsinkWeapon;
    private float prevEffectLevel = 0f;

    private boolean venting = false;
    private float ventDrainRate = 0f;
    private float ventTimeRemaining = 0f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI ship)) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        boolean activated = (effectLevel >= 1f && prevEffectLevel < 1f);
        if (activated) {
            launchHeatsink(engine, ship);
            beginVent(ship);
        }
        prevEffectLevel = effectLevel;

        if (engine.isPaused()) return;

        if (venting) {
            float dt = engine.getElapsedInLastFrame();
            ship.getFluxTracker().decreaseFlux(ventDrainRate * dt);
            ventTimeRemaining -= dt;
            if (ventTimeRemaining <= 0f) venting = false;
        }

        spawnExhaustParticles(engine, ship, effectLevel);
    }

    private void beginVent(ShipAPI ship) {
        float fluxSnapshot = ship.getFluxTracker().getCurrFlux();
        float duration = ship.getSystem().getChargeActiveDur() + ship.getSystem().getChargeDownDur();
        duration = Math.max(duration, 0.05f);

        ventDrainRate = fluxSnapshot / duration;
        ventTimeRemaining = duration;
        venting = fluxSnapshot > 0f;
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        return (index == 0 && effectLevel > 0f) ? new StatusData("Venting flux!", false) : null;
    }

    private void launchHeatsink(CombatEngineAPI engine, ShipAPI ship) {
        if (heatsinkWeapon == null) {
            heatsinkWeapon = engine.createFakeWeapon(ship, HEATSINK_WEAPON_ID);
        }

        float angle = ship.getFacing() + 180f;
        engine.spawnProjectile(ship, heatsinkWeapon, HEATSINK_WEAPON_ID, ship.getLocation(), angle, new Vector2f());
    }

    private void spawnExhaustParticles(CombatEngineAPI engine, ShipAPI ship, float effectLevel) {
        if (effectLevel < 0.1f) return;

        float timeMult = engine.getTimeMult().getMult();
        if (timeMult == 0) timeMult = 0.01f;
        particleInterval.advance(0.05f * timeMult);

        if (!particleInterval.intervalElapsed()) return;

        Vector2f shipVel = ship.getVelocity();

        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (!slot.isSystemSlot()) continue;

            Vector2f loc = slot.computePosition(ship);
            float angle = slot.computeMidArcAngle(ship);

            float min = 25;
            float range = 60;
            float spread = 20;
            float length = 70;
            int count = (int) (10 * effectLevel);
            for (int i = 0; i < count; i++) {
                float size = range * (float) Math.random() + min;
                float theta = (float) (Math.random() * Math.toRadians(spread) + Math.toRadians(angle - spread / 2f));
                float r = (float) (Math.random() * length);
                Vector2f dir = new Vector2f((float) Math.cos(theta), (float) Math.sin(theta));
                float x = dir.x * r;
                float y = dir.y * r;
                Vector2f particleLoc = new Vector2f(loc.x + x, loc.y + y);
                Vector2f vel = new Vector2f(x * 1.5f + shipVel.x + dir.x, y * 1.5f + shipVel.y + dir.y);
                engine.addNebulaParticle(particleLoc, vel, size, 1f, 0.1f, 0f, 2f, VENT_PARTICLE_COLOR);
            }
        }
    }
}
