package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class XLII_ShashkaChargeEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    // Colors
    private static final Color GLOW_COLOR = new Color(105, 105, 255, 205);
    private static final Color GLOW_CORE_COLOR = new Color(180, 180, 255, 255);
    private static final Color PARTICLE_COLOR = new Color(80, 105, 255, 180);
    private static final Color FLARE_COLOR = new Color(105, 105, 255, 65);
    private static final Color FLARE_CORE_COLOR = new Color(205, 205, 255, 155);
    private static final Color NEBULA_COLOR = new Color(60, 60, 205, 120);
    private static final Color RING_CORE_COLOR = new Color(255, 255, 255, 255);
    private static final Color RING_FRINGE_COLOR = new Color(105, 105, 255, 205);

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

        Vector2f muzzle = weapon.getFirePoint(0);
        Vector2f shipVel = ship.getVelocity();
        float weaponAngle = weapon.getCurrAngle();

        // Laser alpha scales with charge level, interval-capped for consistent DPS
        laserInterval.advance(amount);
        if (laserInterval.intervalElapsed()) {
            int alpha = (int) (255f * chargeLevel);
            Color core = new Color(205, 205, 205, alpha);
            Color fringe = new Color(75, 100, 255, alpha);
            MagicFakeBeam.spawnFakeBeam(
                    engine, muzzle, weapon.getRange() + LASER_RANGE_BONUS, weaponAngle,
                    LASER_WIDTH, LASER_FULL, LASER_FADING, 16.25f,
                    core, fringe,
                    20.8335f, DamageType.ENERGY, 0f, ship
            );
        }

        // Converging particles
        particleInterval.advance(amount);
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
                engine.addSmoothParticle(spawnPoint, vel, size, chargeLevel, CONVERGE_PARTICLE_DURATION, PARTICLE_COLOR);
            }
        }

        // Growing muzzle glow
        glowInterval.advance(amount);
        if (glowInterval.intervalElapsed()) {
            float glowSize = GLOW_SIZE_MIN + (GLOW_SIZE_MAX - GLOW_SIZE_MIN) * chargeLevel;
            engine.addHitParticle(muzzle, shipVel, glowSize, chargeLevel, 0.05f, GLOW_CORE_COLOR);
            engine.addSmoothParticle(muzzle, shipVel, glowSize * 1.5f, chargeLevel * 0.5f, 0.05f, GLOW_COLOR);
        }

        // Flare at peak charge
        if (chargeLevel > FLARE_CHARGE_THRESHOLD) {
            float intensity = (chargeLevel - FLARE_CHARGE_THRESHOLD) / (1f - FLARE_CHARGE_THRESHOLD);

            SpriteAPI streak = Global.getSettings().getSprite("fx", "XLII_torpedo_flare");
            SpriteAPI flareCore = Global.getSettings().getSprite("fx", "XLII_torpedo_flare");

            MagicRender.singleframe(streak,
                    MathUtils.getRandomPointInCircle(muzzle, 1.5f),
                    new Vector2f(FLARE_STREAK_SIZE.x * intensity, FLARE_STREAK_SIZE.y * intensity),
                    0f, withAlpha(FLARE_COLOR, intensity), true);

            MagicRender.singleframe(flareCore,
                    MathUtils.getRandomPointInCircle(muzzle, 1.5f),
                    new Vector2f(FLARE_CORE_SIZE.x * intensity, FLARE_CORE_SIZE.y * intensity),
                    0f, withAlpha(FLARE_CORE_COLOR, intensity), true);
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        ShipAPI ship = weapon.getShip();
        if (ship == null) return;

        Vector2f muzzle = weapon.getFirePoint(0);
        Vector2f shipVel = ship.getVelocity();
        float weaponAngle = weapon.getCurrAngle();

        // Nebula particles in forward cone
        for (int i = 0; i < FIRE_NEBULA_COUNT; i++) {
            float angle = weaponAngle + MathUtils.getRandomNumberInRange(-FIRE_NEBULA_SPREAD, FIRE_NEBULA_SPREAD);
            float speed = MathUtils.getRandomNumberInRange(FIRE_NEBULA_SPEED_MIN, FIRE_NEBULA_SPEED_MAX);
            Vector2f vel = MathUtils.getPointOnCircumference(shipVel, speed, angle);
            float size = MathUtils.getRandomNumberInRange(FIRE_NEBULA_SIZE_MIN, FIRE_NEBULA_SIZE_MAX);
            engine.addNebulaParticle(muzzle, vel, size, 1.5f, 0f, 0f, FIRE_NEBULA_DURATION, NEBULA_COLOR);
        }

        // Bright hit particles at muzzle
        for (int i = 0; i < FIRE_HIT_PARTICLE_COUNT; i++) {
            Vector2f point = MathUtils.getRandomPointInCircle(muzzle, 20f);
            engine.addHitParticle(point, shipVel, FIRE_HIT_PARTICLE_SIZE, 1f, 0.15f, GLOW_CORE_COLOR);
        }

        // Expanding ring shockwave at muzzle
        XLII_MuzzleFlashEffect.ProjectileRingEffectPlugin ringPlugin =
                new XLII_MuzzleFlashEffect.ProjectileRingEffectPlugin(
                        muzzle, weaponAngle,
                        FIRE_RING_SIZE_MULT, FIRE_RING_DURATION_MULT,
                        RING_CORE_COLOR, RING_FRINGE_COLOR,
                        shipVel
                );
        CombatEntityAPI entity = engine.addLayeredRenderingPlugin(ringPlugin);
        entity.getLocation().set(muzzle);
    }

    private static Color withAlpha(Color base, float alphaMult) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(),
                Math.round(base.getAlpha() * alphaMult));
    }
}
