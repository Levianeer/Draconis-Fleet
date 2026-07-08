package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class XLII_FlambergeOnHitEffect implements OnHitEffectPlugin {

    // Colors
    private static final Color EXPLOSION_FRINGE = new Color(255, 80, 80, 255);
    private static final Color EXPLOSION_CORE = new Color(255, 145, 145, 255);
    private static final Color FLASH_FRINGE = new Color(255, 105, 105, 160);
    private static final Color FLASH_CORE = new Color(255, 205, 205, 255);
    private static final Color FLARE_COLOR = new Color(255, 105, 105, 255);
    private static final Color FLARE_CORE = new Color(255, 255, 255, 255);
    private static final Color ARC_FRINGE = new Color(255, 120, 80, 255);
    private static final Color ARC_CORE = new Color(255, 220, 200, 255);
    private static final Color SHOCKWAVE_FRINGE = new Color(255, 105, 105, 255);
    private static final Color SHOCKWAVE_MID = new Color(255, 120, 120, 175);
    private static final Color SHOCKWAVE_CORE = new Color(255, 180, 180, 200);

    // Damaging explosion
    private static final float EXPLOSION_RADIUS = 100f;
    private static final float EXPLOSION_CORE_RADIUS = 60f;
    private static final float EXPLOSION_DAMAGE_FRACTION = 0.25f;

    // EMP arcs
    private static final int EMP_ARC_MIN = 3;
    private static final int EMP_ARC_MAX = 5;
    private static final float EMP_ARC_EMP_PER_ARC = 100f;
    private static final float EMP_ARC_THICKNESS = 15f;

    // Lens flares
    private static final int FLARE_COUNT = 4;
    private static final float FLARE_RANGE = 60f;
    private static final float FLARE_LENGTH = 200f;
    private static final float FLARE_WIDTH = 70f;

    // Ripple distortion
    private static final float RIPPLE_START_SIZE = 50f;
    private static final float RIPPLE_FINAL_SIZE = 300f;
    private static final float RIPPLE_INTENSITY = 100f;
    private static final float RIPPLE_EXPANSION_TIME = 0.4f;
    private static final float RIPPLE_FADE_TIME = 0.7f;
    private static final float RIPPLE_DURATION = 0.5f;

    private static final DamagingExplosionSpec VISUAL_EXPLOSION_SPEC = createVisualExplosionSpec();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (point == null) return;
        if (!(target instanceof ShipAPI)) return;

        float damage = projectile.getDamageAmount();
        ShipAPI source = projectile.getSource();

        // Damaging explosion
        engine.spawnDamagingExplosion(createDamagingExplosionSpec(damage), source, point);

        // Visual explosion
        engine.spawnDamagingExplosion(VISUAL_EXPLOSION_SPEC, source, point);

        // EMP arcs on hull hit
        if (!shieldHit) {
            int arcCount = MathUtils.getRandomNumberInRange(EMP_ARC_MIN, EMP_ARC_MAX);
            for (int i = 0; i < arcCount; i++) {
                engine.spawnEmpArc(
                        source, point, target, target,
                        DamageType.ENERGY,
                        0f,
                        EMP_ARC_EMP_PER_ARC,
                        100000f,
                        "tachyon_lance_emp_impact",
                        EMP_ARC_THICKNESS + (float) Math.random() * 8f,
                        ARC_FRINGE,
                        ARC_CORE
                );
            }
        }

        // Shockwave sprites
        spawnShockwave(engine, point);

        // Lens flares
        spawnLensFlares(engine, source, point);

        // Ripple distortion
        spawnRippleDistortion(point);
    }

    private static DamagingExplosionSpec createDamagingExplosionSpec(float projectileDamage) {
        float explosionDamage = projectileDamage * EXPLOSION_DAMAGE_FRACTION;
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.1f,
                EXPLOSION_RADIUS,
                EXPLOSION_CORE_RADIUS,
                explosionDamage,
                explosionDamage * 0.5f,
                CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
                CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
                2f, 4f,
                0.5f,
                15,
                EXPLOSION_FRINGE,
                EXPLOSION_CORE
        );
        spec.setDamageType(DamageType.HIGH_EXPLOSIVE);
        spec.setShowGraphic(false);
        spec.setSoundSetId("XLII_explosion_flak");
        return spec;
    }

    private static DamagingExplosionSpec createVisualExplosionSpec() {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.25f,
                150f,
                100f,
                0f, 0f,
                CollisionClass.NONE,
                CollisionClass.NONE,
                3f, 6f,
                1.0f,
                50,
                new Color(80, 100, 255, 180),
                new Color(160, 180, 255, 80)
        );
        spec.setUseDetailedExplosion(true);
        spec.setDetailedExplosionFlashDuration(0.5f);
        spec.setDetailedExplosionRadius(120f);
        spec.setDetailedExplosionFlashRadius(160f);
        spec.setDetailedExplosionFlashColorCore(FLASH_CORE);
        spec.setDetailedExplosionFlashColorFringe(FLASH_FRINGE);
        spec.setDamageType(DamageType.FRAGMENTATION);
        spec.setSoundSetId(null);
        return spec;
    }

    private static void spawnShockwave(CombatEngineAPI engine, Vector2f point) {
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point, new Vector2f(),
                new Vector2f(48, 48),
                new Vector2f(400, 400),
                360 * (float) Math.random(), 0,
                SHOCKWAVE_FRINGE,
                true,
                0, 0.1f, 0.15f
        );
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point, new Vector2f(),
                new Vector2f(64, 64),
                new Vector2f(200, 200),
                360 * (float) Math.random(), 0,
                SHOCKWAVE_MID,
                true,
                0.2f, 0.0f, 0.3f
        );
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point, new Vector2f(),
                new Vector2f(98, 98),
                new Vector2f(100, 100),
                360 * (float) Math.random(), 0,
                SHOCKWAVE_CORE,
                true,
                0.4f, 0.0f, 0.6f
        );

        engine.addHitParticle(point, new Vector2f(), 125, 0.1f, 1f, SHOCKWAVE_FRINGE);
        engine.addSmoothParticle(point, new Vector2f(), 175, 2f, 0.25f, Color.white);
        engine.addSmoothParticle(point, new Vector2f(), 250, 2f, 0.1f, Color.white);
    }

    private static void spawnLensFlares(CombatEngineAPI engine, ShipAPI source, Vector2f center) {
        for (int i = 0; i < FLARE_COUNT; i++) {
            Vector2f flarePoint = MathUtils.getRandomPointInCircle(center, FLARE_RANGE);
            float angle = (float) Math.random() * 360f;
            MagicLensFlare.createSharpFlare(
                    engine, source, flarePoint,
                    FLARE_LENGTH, FLARE_WIDTH, angle,
                    FLARE_COLOR, FLARE_CORE
            );
        }
    }

    private static void spawnRippleDistortion(Vector2f point) {
        RippleDistortion ripple = new RippleDistortion(point, new Vector2f());
        ripple.setSize(RIPPLE_FINAL_SIZE);
        ripple.setIntensity(RIPPLE_INTENSITY);
        ripple.setFrameRate(60f / RIPPLE_DURATION);
        ripple.fadeInSize(RIPPLE_EXPANSION_TIME);
        ripple.fadeOutIntensity(RIPPLE_FADE_TIME);
        ripple.setSize(RIPPLE_START_SIZE);
        DistortionShader.addDistortion(ripple);
    }
}
