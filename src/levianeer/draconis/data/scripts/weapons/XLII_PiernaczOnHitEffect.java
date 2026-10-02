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
    private static final Color EXPLOSION_FRINGE = new Color(255, 110, 60, 255);
    private static final Color EXPLOSION_CORE = new Color(255, 225, 190, 255);
    // Blue: reads as residual ionization/EMP from the jet, distinct from the red/white thermal
    // detonation below - the jet is blue/white in flight, the burn it leaves behind is red/white.
    private static final Color ARC_FRINGE = new Color(90, 165, 255, 255);
    private static final Color ARC_CORE = new Color(220, 240, 255, 255);

    // Layered blast FX, styled after XLII_EMPBlastOnHitEffect's lingering blob/afterglow
    // recipe, re-themed red/white thermal (Meltagun/Volcano Cannon detonation) and scaled to
    // this weapon's blast radius (100 vs 75, a 4/3 factor applied to every spatial value below;
    // timings left as-is), then bumped ~30% larger on top for a denser, more violent detonation.
    private static final Color COLOR_FRINGE    = new Color(255, 110, 60, 150);
    private static final Color COLOR_CORE      = new Color(255, 235, 200, 215);
    private static final Color COLOR_RING      = new Color(255, 240, 220, 100);
    private static final Color COLOR_HAZE      = new Color(110, 60, 50, 115);
    private static final Color COLOR_AFTERGLOW = new Color(255, 140, 90, 135);
    private static final Color COLOR_EMBER     = new Color(255, 180, 110, 255);
    // Blue-white arrival flash - continuity with the jet's hot core arriving, an instant
    // before it's overtaken by the red thermal bloom below.
    private static final Color COLOR_IMPACT_FLASH = new Color(220, 235, 255, 235);

    // Damaging explosion
    private static final float EXPLOSION_RADIUS = 100f;
    private static final float EXPLOSION_CORE_RADIUS = 60f;
    private static final float EXPLOSION_DAMAGE_FRACTION = 0.25f;

    // EMP arcs
    private static final int EMP_ARC_MIN = 3;
    private static final int EMP_ARC_MAX = 5;
    private static final float EMP_ARC_TOTAL_EMP = 500f;
    private static final float EMP_ARC_THICKNESS = 9.75f;

    // Ember/spark burst
    private static final int EMBER_COUNT_MIN = 8;
    private static final int EMBER_COUNT_MAX = 14;
    private static final float EMBER_SPEED_MIN = 150f;
    private static final float EMBER_SPEED_MAX = 380f;
    private static final float EMBER_SIZE_MIN = 4f;
    private static final float EMBER_SIZE_MAX = 9f;
    private static final float EMBER_DURATION = 0.35f;

    // Ripple distortion - bumped ~30% alongside the layered blast for a heavier concussion.
    private static final float RIPPLE_START_SIZE = 32.5f;
    private static final float RIPPLE_FINAL_SIZE = 250f;
    private static final float RIPPLE_INTENSITY = 85f;
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

        engine.spawnDamagingExplosion(createDamagingExplosionSpec(damage), source, point);

        engine.spawnDamagingExplosion(VISUAL_EXPLOSION_SPEC, source, point);

        // Mirrors PilumOnHitEffect's shield-pierce arc logic.
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

        spawnPiernaczVisuals(point);
        spawnEmberBurst(engine, point, ship.getVelocity());
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
                new Color(255, 120, 70, 180),
                new Color(255, 235, 210, 90)
        );
        spec.setUseDetailedExplosion(true);
        spec.setDetailedExplosionFlashDuration(1.0f);
        spec.setDetailedExplosionRadius(133.3f);
        spec.setDetailedExplosionFlashRadius(133.3f);
        spec.setDetailedExplosionFlashColorCore(new Color(255, 245, 230, 255));
        spec.setDetailedExplosionFlashColorFringe(new Color(255, 110, 60, 175));
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

        // Blue-white arrival flash - the jet's own hot core hitting, gone almost instantly.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(40f, 40f), new Vector2f(90f, 90f),
                angle, 0, COLOR_IMPACT_FLASH, true, 0, 0.05f, 0.1f);

        // Soft growing blob.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(48.5f, 48.5f), new Vector2f(381.3f, 381.3f),
                angle, 0, COLOR_FRINGE, false, 0, 0.1f, 0.15f);
        // Denser, contracting core.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(69.3f, 69.3f), new Vector2f(-52f, -52f),
                angle, 0, COLOR_CORE, false, 0.1f, 0.2f, 0.5f);

        // Fast, subtle shockwave ring.
        SpriteAPI ring = Global.getSettings().getSprite("graphics/fx/explosion_ring0.png");
        MagicRender.battlespace(ring, center, new Vector2f(), new Vector2f(86.7f, 86.7f), new Vector2f(1126.7f, 1126.7f),
                angle, 0, COLOR_RING, true, 0, 0.05f, 0.2f);

        // Afterglow - appears near the flash blobs' peak size and just hangs, dimming
        // slowly, giving the blast its lingering hang time instead of a quick flash.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(147.3f, 147.3f), new Vector2f(43.3f, 43.3f),
                angle, 0, COLOR_AFTERGLOW, true, 0.15f, 0.6f, 1.8f);

        // Lingering haze.
        SpriteAPI haze = Global.getSettings().getSprite("graphics/fx/explosion3.png");
        MagicRender.battlespace(haze, center, new Vector2f(), new Vector2f(95.3f, 95.3f), new Vector2f(69.3f, 69.3f),
                angle, 5, COLOR_HAZE, false, 0.3f, 1.2f, 2.2f);

        spawnRippleDistortion(center);
    }

    /**
     * Sparks flying off the hit point - the "busier" half of the denser thermal detonation,
     * layered alongside the blob/ring/haze blast above.
     */
    private static void spawnEmberBurst(CombatEngineAPI engine, Vector2f center, Vector2f sourceVel) {
        int count = MathUtils.getRandomNumberInRange(EMBER_COUNT_MIN, EMBER_COUNT_MAX);
        for (int i = 0; i < count; i++) {
            float angle = MathUtils.getRandomNumberInRange(0f, 360f);
            float speed = MathUtils.getRandomNumberInRange(EMBER_SPEED_MIN, EMBER_SPEED_MAX);
            Vector2f vel = MathUtils.getPointOnCircumference(sourceVel, speed, angle);
            float size = MathUtils.getRandomNumberInRange(EMBER_SIZE_MIN, EMBER_SIZE_MAX);
            engine.addHitParticle(center, vel, size, 1f, EMBER_DURATION, COLOR_EMBER);
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