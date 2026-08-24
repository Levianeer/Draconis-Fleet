package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class XLII_PiernaczOnHitEffect implements OnHitEffectPlugin {

    // Colors
    private static final Color EXPLOSION_FRINGE = new Color(170, 80, 255, 255);
    private static final Color EXPLOSION_CORE = new Color(200, 170, 255, 255);
    private static final Color ARC_FRINGE = new Color(150, 90, 255, 255);
    private static final Color ARC_CORE = new Color(220, 200, 255, 255);

    // Layered blast FX, styled after XLII_EMPBlastOnHitEffect's lingering blob/afterglow
    // recipe, re-themed light purple and scaled to this weapon's blast radius (100 vs 75,
    // a 4/3 factor applied to every spatial value below; timings left as-is).
    private static final Color COLOR_FRINGE    = new Color(170, 110, 255, 140);
    private static final Color COLOR_CORE      = new Color(220, 200, 255, 200);
    private static final Color COLOR_RING      = new Color(230, 210, 255, 90);
    private static final Color COLOR_HAZE      = new Color(170, 140, 210, 110);
    private static final Color COLOR_AFTERGLOW = new Color(190, 150, 255, 120);

    // Damaging explosion
    private static final float EXPLOSION_RADIUS = 100f;
    private static final float EXPLOSION_CORE_RADIUS = 60f;
    private static final float EXPLOSION_DAMAGE_FRACTION = 0.25f;

    // EMP arcs
    private static final int EMP_ARC_MIN = 3;
    private static final int EMP_ARC_MAX = 5;
    private static final float EMP_ARC_TOTAL_EMP = 500f;
    private static final float EMP_ARC_THICKNESS = 9.75f;

    // Ripple distortion
    private static final float RIPPLE_START_SIZE = 32.5f;
    private static final float RIPPLE_FINAL_SIZE = 195f;
    private static final float RIPPLE_INTENSITY = 65f;
    private static final float RIPPLE_EXPANSION_TIME = 0.4f;
    private static final float RIPPLE_FADE_TIME = 0.7f;
    private static final float RIPPLE_DURATION = 0.5f;

    private static final DamagingExplosionSpec VISUAL_EXPLOSION_SPEC = createVisualExplosionSpec();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (point == null) return;
        if (!(target instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) target;

        float damage = projectile.getDamageAmount();
        ShipAPI source = projectile.getSource();

        // Damaging explosion
        engine.spawnDamagingExplosion(createDamagingExplosionSpec(damage), source, point);

        // Visual explosion
        engine.spawnDamagingExplosion(VISUAL_EXPLOSION_SPEC, source, point);

        // EMP arcs on hull hit, or on a successful shield pierce (mirrors PilumOnHitEffect)
        boolean piercedShield = false;
        if (shieldHit) {
            float pierceChance = ship.getHardFluxLevel() - 0.1f;
            pierceChance *= ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
            piercedShield = (float) Math.random() < pierceChance;
        }

        if (!shieldHit || piercedShield) {
            int arcCount = MathUtils.getRandomNumberInRange(EMP_ARC_MIN, EMP_ARC_MAX);
            for (int i = 0; i < arcCount; i++) {
                engine.spawnEmpArcPierceShields(
                        source, point, target, target,
                        DamageType.ENERGY,
                        0f,
                        EMP_ARC_TOTAL_EMP / arcCount,
                        100000f,
                        "tachyon_lance_emp_impact",
                        EMP_ARC_THICKNESS + (float) Math.random() * 5.2f,
                        ARC_FRINGE,
                        ARC_CORE
                );
            }
        }

        // Layered blast FX (blob/core/ring/afterglow/haze + distortion shockwave)
        spawnPiernaczVisuals(point);
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
                0.5f,
                100f,
                66.7f,
                0f, 0f,
                CollisionClass.NONE,
                CollisionClass.NONE,
                6.7f, 13.3f,
                1.0f,
                60,
                new Color(170, 110, 255, 180),
                new Color(220, 200, 255, 80)
        );
        spec.setUseDetailedExplosion(true);
        spec.setDetailedExplosionFlashDuration(1.0f);
        spec.setDetailedExplosionRadius(133.3f);
        spec.setDetailedExplosionFlashRadius(133.3f);
        spec.setDetailedExplosionFlashColorCore(new Color(220, 200, 255, 255));
        spec.setDetailedExplosionFlashColorFringe(new Color(170, 110, 255, 160));
        spec.setDamageType(DamageType.ENERGY);
        spec.setSoundSetId(null);
        return spec;
    }

    /**
     * Layered blob/ring/haze/afterglow blast plus a distortion shockwave, per
     * XLII_EMPBlastOnHitEffect's recipe. No arc sprites or lens flares - both read as
     * arc-like streaks, which would be confusing alongside the real EMP arcs above.
     */
    private static void spawnPiernaczVisuals(Vector2f center) {
        SpriteAPI spr = Global.getSettings().getSprite("fx", "XLII_explosion");

        // Single shared rotation so every layer reads as one coherent blast.
        float angle = 360f * (float) Math.random();

        // Soft growing blob.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(37.3f, 37.3f), new Vector2f(293.3f, 293.3f),
                angle, 0, COLOR_FRINGE, false, 0, 0.1f, 0.15f);
        // Denser, contracting core.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(53.3f, 53.3f), new Vector2f(-40f, -40f),
                angle, 0, COLOR_CORE, false, 0.1f, 0.2f, 0.5f);

        // Fast, subtle shockwave ring.
        SpriteAPI ring = Global.getSettings().getSprite("graphics/fx/explosion_ring0.png");
        MagicRender.battlespace(ring, center, new Vector2f(), new Vector2f(66.7f, 66.7f), new Vector2f(866.7f, 866.7f),
                angle, 0, COLOR_RING, true, 0, 0.05f, 0.2f);

        // Afterglow - appears near the flash blobs' peak size and just hangs, dimming
        // slowly, giving the blast its lingering hang time instead of a quick flash.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(113.3f, 113.3f), new Vector2f(33.3f, 33.3f),
                angle, 0, COLOR_AFTERGLOW, true, 0.15f, 0.6f, 1.8f);

        // Lingering haze.
        SpriteAPI haze = Global.getSettings().getSprite("graphics/fx/explosion3.png");
        MagicRender.battlespace(haze, center, new Vector2f(), new Vector2f(73.3f, 73.3f), new Vector2f(53.3f, 53.3f),
                angle, 5, COLOR_HAZE, false, 0.3f, 1.2f, 2.2f);

        spawnRippleDistortion(center);
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