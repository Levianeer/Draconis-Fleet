package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class XLII_PiernaczChargeEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    // Barrel layout - Piernacz fires both barrels simultaneously (LINKED)
    private static final int NUM_BARRELS = 2;

    // Heat gradient: white-hot core always nested in a blue-hot layer, nested in an orange/red
    // halo. Consistent across charge-up (ember->white-hot), ignition, flight (see .proj/.wpn/
    // trail files), and impact (reversed: blue-white flash before the red thermal bloom).
    private static final Color HEAT_EMBER = new Color(120, 30, 15, 255);
    private static final Color HEAT_WHITE_HOT = new Color(255, 250, 235, 255);

    // Outer halo around the charging core stays a constant warm ember glow (like a welding
    // arc's bloom) while the core beneath heats toward white.
    private static final Color GLOW_COLOR = new Color(255, 90, 40, 180);
    private static final Color FLARE_COLOR_LOW = new Color(255, 120, 60, 70);
    private static final Color FLARE_COLOR_HIGH = new Color(255, 250, 235, 70);
    private static final Color FLARE_CORE_COLOR_LOW = new Color(255, 150, 90, 160);
    private static final Color FLARE_CORE_COLOR_HIGH = new Color(255, 250, 235, 160);
    private static final Color LASER_FRINGE_LOW = new Color(230, 90, 60, 255);
    private static final Color RING_CORE_COLOR = new Color(255, 255, 255, 255);

    // On-fire muzzle blast: blue-hot core in an orange/red heat corona (see heat-gradient note
    // above), plus the beam pulse selling the "jet" reaching outward.
    private static final Color FIRE_NEBULA_COLOR = new Color(110, 175, 255, 130);
    private static final Color FIRE_NEBULA_HEAT_COLOR = new Color(255, 120, 60, 140);
    private static final Color FIRE_FLASH_COLOR = new Color(235, 245, 255, 255);
    private static final Color FIRE_RING_FRINGE_COLOR = new Color(120, 185, 255, 205);
    private static final Color FIRE_BEAM_CORE = new Color(245, 250, 255, 255);
    private static final Color FIRE_BEAM_FRINGE = new Color(120, 185, 255, 255);
    private static final Color FIRE_HEAT_BEAM_CORE = new Color(255, 150, 80, 190);
    private static final Color FIRE_HEAT_BEAM_FRINGE = new Color(255, 80, 40, 110);

    // Laser sight
    private static final float LASER_WIDTH = 3.9f;
    private static final float LASER_FULL = 0.03f;
    private static final float LASER_FADING = 0.09f;
    private static final float LASER_RANGE_BONUS = 450f;

    // Converging particles
    private static final int PARTICLE_BASE_COUNT = 1;
    private static final int PARTICLE_MAX_COUNT = 5;
    private static final float CONVERGE_RADIUS_MIN = 80f;
    private static final float CONVERGE_RADIUS_MAX = 200f;
    private static final float CONVERGE_SPEED = 400f;
    private static final float CONVERGE_PARTICLE_SIZE_MIN = 2f;
    private static final float CONVERGE_PARTICLE_SIZE_MAX = 5.2f;
    private static final float CONVERGE_PARTICLE_DURATION = 0.3f;

    // Muzzle glow
    private static final float GLOW_SIZE_MIN = 6.5f;
    private static final float GLOW_SIZE_MAX = 52f;

    // Lens flare at peak charge
    private static final float FLARE_CHARGE_THRESHOLD = 0.5f;
    private static final Vector2f FLARE_STREAK_SIZE = new Vector2f(520f, 7.8f);
    private static final Vector2f FLARE_CORE_SIZE = new Vector2f(227.5f, 5.2f);

    // On-fire muzzle blast
    private static final int FIRE_NEBULA_COUNT = 8;
    private static final float FIRE_NEBULA_SPREAD = 25f;
    private static final float FIRE_NEBULA_SPEED_MIN = 100f;
    private static final float FIRE_NEBULA_SPEED_MAX = 300f;
    private static final float FIRE_NEBULA_SIZE_MIN = 9.75f;
    private static final float FIRE_NEBULA_SIZE_MAX = 22.75f;
    private static final float FIRE_NEBULA_DURATION = 0.4f;
    private static final int FIRE_HIT_PARTICLE_COUNT = 5;
    private static final float FIRE_HIT_PARTICLE_SIZE = 26f;
    private static final float FIRE_RING_SIZE_MULT = 0.975f;
    private static final float FIRE_RING_DURATION_MULT = 2.5f;
    // Brief zero-damage fake-beam flash so the short/thick projectile reads as a jet burst
    // leaving the muzzle, rather than just a bolt appearing - damage is entirely on the projectile.
    private static final float FIRE_BEAM_WIDTH = 34f;
    private static final float FIRE_BEAM_RANGE = 700f;
    private static final float FIRE_BEAM_FULL = 0.035f;
    private static final float FIRE_BEAM_FADE = 0.09f;
    // Heat corona around the core beam pulse (see heat-gradient note above). Shorter reach: the
    // heat wash dissipates faster than the ionized core.
    private static final float FIRE_HEAT_BEAM_WIDTH = 72f;
    private static final float FIRE_HEAT_BEAM_RANGE = 500f;
    private static final float FIRE_HEAT_BEAM_FULL = 0.05f;
    private static final float FIRE_HEAT_BEAM_FADE = 0.14f;

    // Charge state tracking
    private boolean hasFired = false;

    // Intervals
    private final IntervalUtil laserInterval = new IntervalUtil(0.05f, 0.05f);
    private final IntervalUtil particleInterval = new IntervalUtil(0.03f, 0.06f);
    private final IntervalUtil glowInterval = new IntervalUtil(0.02f, 0.04f);

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || engine.isInFastTimeAdvance()) return;
        if (weapon == null) return;

        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        float chargeLevel = weapon.getChargeLevel();

        // Track charge state: only show effects during charge-up, not charge-down
        if (chargeLevel >= 1f) {
            hasFired = true;
        }
        if (chargeLevel <= 0f) {
            hasFired = false;
        }
        if (chargeLevel <= 0f || hasFired) return;

        Vector2f shipVel = ship.getVelocity();
        float weaponAngle = weapon.getCurrAngle();

        // Both barrels charge and fire together, so every effect below is drawn at each barrel
        for (int barrel = 0; barrel < NUM_BARRELS; barrel++) {
            Vector2f muzzle = weapon.getFirePoint(barrel);

            // Laser alpha scales with charge level, interval-capped for consistent DPS. Fringe
            // heats from ember to white-hot with charge, same as the muzzle glow below.
            if (barrel == 0) laserInterval.advance(amount);
            if (laserInterval.intervalElapsed()) {
                int alpha = (int) (255f * chargeLevel);
                Color core = new Color(205, 205, 205, alpha);
                Color fringeHeat = lerpColor(LASER_FRINGE_LOW, HEAT_WHITE_HOT, chargeLevel);
                Color fringe = new Color(fringeHeat.getRed(), fringeHeat.getGreen(), fringeHeat.getBlue(), alpha);
                XLII_FakeBeam.spawnFakeBeam(
                        engine, muzzle, weapon.getRange() + LASER_RANGE_BONUS, weaponAngle,
                        LASER_WIDTH, LASER_FULL, LASER_FADING, 16.25f,
                        core, fringe,
                        20.8335f, DamageType.ENERGY, 0f, ship
                );
            }

            if (barrel == 0) particleInterval.advance(amount);
            if (particleInterval.intervalElapsed()) {
                int count = (int) (PARTICLE_BASE_COUNT + (PARTICLE_MAX_COUNT - PARTICLE_BASE_COUNT) * chargeLevel);
                for (int i = 0; i < count; i++) {
                    float distance = MathUtils.getRandomNumberInRange(CONVERGE_RADIUS_MIN, CONVERGE_RADIUS_MAX);
                    float angle = MathUtils.getRandomNumberInRange(0f, 360f);
                    Vector2f spawnPoint = MathUtils.getPointOnCircumference(muzzle, distance, angle);

                    // Velocity points from spawn toward muzzle
                    float angleToMuzzle = VectorUtils.getAngle(spawnPoint, muzzle);
                    Vector2f dir = MathUtils.getPointOnCircumference(new Vector2f(), CONVERGE_SPEED, angleToMuzzle);
                    Vector2f vel = Vector2f.add(dir, shipVel, new Vector2f());

                    float size = MathUtils.getRandomNumberInRange(CONVERGE_PARTICLE_SIZE_MIN, CONVERGE_PARTICLE_SIZE_MAX);
                    engine.addSmoothParticle(spawnPoint, vel, size, chargeLevel, CONVERGE_PARTICLE_DURATION,
                            lerpColor(HEAT_EMBER, HEAT_WHITE_HOT, chargeLevel));
                }
            }

            if (barrel == 0) glowInterval.advance(amount);
            if (glowInterval.intervalElapsed()) {
                float glowSize = GLOW_SIZE_MIN + (GLOW_SIZE_MAX - GLOW_SIZE_MIN) * chargeLevel;
                engine.addHitParticle(muzzle, shipVel, glowSize, chargeLevel, 0.05f,
                        lerpColor(HEAT_EMBER, HEAT_WHITE_HOT, chargeLevel));
                engine.addSmoothParticle(muzzle, shipVel, glowSize * 1.5f, chargeLevel * 0.5f, 0.05f, GLOW_COLOR);
            }

            if (chargeLevel > FLARE_CHARGE_THRESHOLD) {
                float intensity = (chargeLevel - FLARE_CHARGE_THRESHOLD) / (1f - FLARE_CHARGE_THRESHOLD);

                SpriteAPI streak = Global.getSettings().getSprite("fx", "XLII_torpedo_flare");
                SpriteAPI flareCore = Global.getSettings().getSprite("fx", "XLII_torpedo_flare");

                MagicRender.singleframe(streak,
                        MathUtils.getRandomPointInCircle(muzzle, 1.5f),
                        new Vector2f(FLARE_STREAK_SIZE.x * intensity, FLARE_STREAK_SIZE.y * intensity),
                        0f, withAlpha(lerpColor(FLARE_COLOR_LOW, FLARE_COLOR_HIGH, intensity), intensity), true);

                MagicRender.singleframe(flareCore,
                        MathUtils.getRandomPointInCircle(muzzle, 1.5f),
                        new Vector2f(FLARE_CORE_SIZE.x * intensity, FLARE_CORE_SIZE.y * intensity),
                        0f, withAlpha(lerpColor(FLARE_CORE_COLOR_LOW, FLARE_CORE_COLOR_HIGH, intensity), intensity), true);
            }
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        ShipAPI ship = weapon.getShip();
        if (ship == null) return;

        // Each barrel fires its own projectile, so use its actual spawn point rather than
        // a fixed barrel index - this keeps the muzzle blast on the barrel that actually fired.
        Vector2f muzzle = projectile.getLocation();
        Vector2f shipVel = ship.getVelocity();
        float weaponAngle = weapon.getCurrAngle();

        // Two nebula bursts: blue-hot core sparks plus a wider orange/red heat corona (see
        // heat-gradient note above), erupting together.
        for (int i = 0; i < FIRE_NEBULA_COUNT; i++) {
            float angle = weaponAngle + MathUtils.getRandomNumberInRange(-FIRE_NEBULA_SPREAD, FIRE_NEBULA_SPREAD);
            float speed = MathUtils.getRandomNumberInRange(FIRE_NEBULA_SPEED_MIN, FIRE_NEBULA_SPEED_MAX);
            Vector2f vel = MathUtils.getPointOnCircumference(shipVel, speed, angle);
            float size = MathUtils.getRandomNumberInRange(FIRE_NEBULA_SIZE_MIN, FIRE_NEBULA_SIZE_MAX);
            engine.addNebulaParticle(muzzle, vel, size, 1.5f, 0f, 0f, FIRE_NEBULA_DURATION, FIRE_NEBULA_COLOR);
        }
        for (int i = 0; i < FIRE_NEBULA_COUNT; i++) {
            float angle = weaponAngle + MathUtils.getRandomNumberInRange(-FIRE_NEBULA_SPREAD * 1.4f, FIRE_NEBULA_SPREAD * 1.4f);
            float speed = MathUtils.getRandomNumberInRange(FIRE_NEBULA_SPEED_MIN * 0.6f, FIRE_NEBULA_SPEED_MAX * 0.7f);
            Vector2f vel = MathUtils.getPointOnCircumference(shipVel, speed, angle);
            float size = MathUtils.getRandomNumberInRange(FIRE_NEBULA_SIZE_MIN, FIRE_NEBULA_SIZE_MAX * 1.3f);
            engine.addNebulaParticle(muzzle, vel, size, 1.5f, 0f, 0f, FIRE_NEBULA_DURATION * 1.3f, FIRE_NEBULA_HEAT_COLOR);
        }

        for (int i = 0; i < FIRE_HIT_PARTICLE_COUNT; i++) {
            Vector2f point = MathUtils.getRandomPointInCircle(muzzle, 20f);
            engine.addHitParticle(point, shipVel, FIRE_HIT_PARTICLE_SIZE, 1f, 0.15f, FIRE_FLASH_COLOR);
        }

        // Zero-damage flash-beam pulse selling the "jet burst" look (see FIRE_BEAM_* comment
        // above); layered as a wider heat corona around the core pulse (see heat-gradient note above).
        XLII_FakeBeam.spawnFakeBeam(
                engine, muzzle, FIRE_HEAT_BEAM_RANGE, weaponAngle,
                FIRE_HEAT_BEAM_WIDTH, FIRE_HEAT_BEAM_FULL, FIRE_HEAT_BEAM_FADE, 16.25f,
                FIRE_HEAT_BEAM_CORE, FIRE_HEAT_BEAM_FRINGE,
                0f, DamageType.ENERGY, 0f, ship
        );
        XLII_FakeBeam.spawnFakeBeam(
                engine, muzzle, FIRE_BEAM_RANGE, weaponAngle,
                FIRE_BEAM_WIDTH, FIRE_BEAM_FULL, FIRE_BEAM_FADE, 16.25f,
                FIRE_BEAM_CORE, FIRE_BEAM_FRINGE,
                0f, DamageType.ENERGY, 0f, ship
        );

        XLII_MuzzleFlashEffect.ProjectileRingEffectPlugin ringPlugin =
                new XLII_MuzzleFlashEffect.ProjectileRingEffectPlugin(
                        muzzle, weaponAngle,
                        FIRE_RING_SIZE_MULT, FIRE_RING_DURATION_MULT,
                        RING_CORE_COLOR, FIRE_RING_FRINGE_COLOR,
                        shipVel
                );
        CombatEntityAPI entity = engine.addLayeredRenderingPlugin(ringPlugin);
        entity.getLocation().set(muzzle);
    }

    private static Color withAlpha(Color base, float alphaMult) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(),
                Math.round(base.getAlpha() * alphaMult));
    }

    private static Color lerpColor(Color from, Color to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t)
        );
    }
}
