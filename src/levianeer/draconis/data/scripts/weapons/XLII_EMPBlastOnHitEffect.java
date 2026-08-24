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

public class XLII_EMPBlastOnHitEffect implements OnHitEffectPlugin {

    private static final DamagingExplosionSpec VISUAL_EXPLOSION_SPEC = createCachedVisualExplosionSpec();

    // EMP arcs - same pierce-chance/arc-spawning pattern as XLII_FlambergeOnHitEffect
    // and XLII_ShashkaOnHitEffect.
    private static final int   EMP_ARC_MIN = 3;
    private static final int   EMP_ARC_MAX = 5;
    private static final float EMP_DAMAGE_FRACTION = 1.5f;
    private static final float EMP_ARC_THICKNESS = 9.75f;
    private static final Color ARC_FRINGE = new Color(80, 120, 255, 255);
    private static final Color ARC_CORE   = new Color(200, 220, 255, 255);

    // Layered blast FX, styled after XLII_LargeTorpOnHitEffect's lingering blob/afterglow
    // recipe and XLII_NukeOnHitEffect's ripple treatment, re-themed electric blue.
    private static final Color COLOR_FRINGE    = new Color(100, 150, 255, 140);
    private static final Color COLOR_CORE      = new Color(200, 220, 255, 200);
    private static final Color COLOR_RING      = new Color(220, 235, 255, 90);
    private static final Color COLOR_HAZE      = new Color(140, 160, 210, 110);
    private static final Color COLOR_AFTERGLOW = new Color(140, 190, 255, 120);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (!(target instanceof ShipAPI ship)) return;

        float empDamage = projectile.getEmpAmount();
        ShipAPI source = projectile.getSource();

        // EMP arcs on hull hit, or on a successful shield pierce (mirrors Flamberge/Shashka)
        boolean piercedShield = false;
        if (shieldHit) {
            float pierceChance = ship.getHardFluxLevel() - 0.1f;
            pierceChance *= ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
            pierceChance *= 3f;
            piercedShield = (float) Math.random() < pierceChance;
        }

        if (!shieldHit || piercedShield) {
            int arcCount = MathUtils.getRandomNumberInRange(EMP_ARC_MIN, EMP_ARC_MAX);
            for (int i = 0; i < arcCount; i++) {
                engine.spawnEmpArcPierceShields(
                        source, point, target, target,
                        DamageType.ENERGY,
                        0f,
                        (empDamage * EMP_DAMAGE_FRACTION) / arcCount,
                        100000f,
                        "tachyon_lance_emp_impact",
                        EMP_ARC_THICKNESS + (float) Math.random() * 5.2f,
                        ARC_FRINGE,
                        ARC_CORE
                );
            }
        }

        // Spawn EMP explosion (deals EMP damage in area)
        engine.spawnDamagingExplosion(createEMPExplosionSpec(empDamage), source, point);

        // Spawn visual explosion
        engine.spawnDamagingExplosion(VISUAL_EXPLOSION_SPEC, source, point);

        // Spawn EMP visual effects
        spawnEMPVisuals(point);
    }

    /**
     * Creates the main EMP explosion spec
     */
    private static DamagingExplosionSpec createEMPExplosionSpec(float empDamage) {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.2f,              // duration
                75f,              // max radius
                50f,              // core radius
                empDamage * 0.2f,  // full damage
                empDamage * 0.05f, // min damage
                CollisionClass.PROJECTILE_FF,
                CollisionClass.PROJECTILE_FIGHTER,
                4f,                // particle size min
                6f,                // particle size range
                0.4f,              // particle duration
                20,                // particle count
                COLOR_FRINGE,
                COLOR_CORE
        );
        spec.setDamageType(DamageType.ENERGY);
        spec.setSoundSetId("system_emp_emitter_activate");
        spec.setShowGraphic(false); // damage-only; VISUAL_EXPLOSION_SPEC + spawnEMPVisuals handle the look
        return spec;
    }

    /**
     * Creates the cached visual explosion spec
     */
    private static DamagingExplosionSpec createCachedVisualExplosionSpec() {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.5f,
                75f,
                50f,
                0f, // no damage
                0f,
                CollisionClass.NONE,
                CollisionClass.NONE,
                5f,
                10f,
                1.0f,
                60,
                new Color(80, 130, 255, 180),
                new Color(180, 210, 255, 80)
        );

        spec.setUseDetailedExplosion(true);
        spec.setDetailedExplosionFlashDuration(1.0f);
        spec.setDetailedExplosionRadius(100f);
        spec.setDetailedExplosionFlashRadius(100f);
        spec.setDetailedExplosionFlashColorCore(new Color(200, 220, 255, 255));
        spec.setDetailedExplosionFlashColorFringe(new Color(100, 150, 255, 160));
        spec.setDamageType(DamageType.ENERGY);
        spec.setSoundSetId(null);

        return spec;
    }

    /**
     * Spawns EMP-themed visual effects: layered blob/afterglow blast (per
     * XLII_LargeTorpOnHitEffect) plus a distortion shockwave (per
     * XLII_NukeOnHitEffect), all re-themed electric blue. No arc sprites or lens
     * flares - both read as arc-like streaks, so this is deliberately just the
     * soft blob/ring/haze layers plus the ripple.
     */
    private static void spawnEMPVisuals(Vector2f center) {
        SpriteAPI spr = Global.getSettings().getSprite("fx", "XLII_explosion");

        // Single shared rotation so every layer reads as one coherent blast.
        float angle = 360f * (float) Math.random();

        // Soft growing blob.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(28, 28), new Vector2f(220, 220),
                angle, 0, COLOR_FRINGE, false, 0, 0.1f, 0.15f);
        // Denser, contracting core.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(40, 40), new Vector2f(-30, -30),
                angle, 0, COLOR_CORE, false, 0.1f, 0.2f, 0.5f);

        // Fast, subtle shockwave ring.
        SpriteAPI ring = Global.getSettings().getSprite("graphics/fx/explosion_ring0.png");
        MagicRender.battlespace(ring, center, new Vector2f(), new Vector2f(50, 50), new Vector2f(650, 650),
                angle, 0, COLOR_RING, true, 0, 0.05f, 0.2f);

        // Afterglow - appears near the flash blobs' peak size and just hangs, dimming
        // slowly, giving the blast its lingering hang time instead of a quick flash.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(85, 85), new Vector2f(25, 25),
                angle, 0, COLOR_AFTERGLOW, true, 0.15f, 0.6f, 1.8f);

        // Lingering ionized haze.
        SpriteAPI haze = Global.getSettings().getSprite("graphics/fx/explosion3.png");
        MagicRender.battlespace(haze, center, new Vector2f(), new Vector2f(55, 55), new Vector2f(40, 40),
                angle, 5, COLOR_HAZE, false, 0.3f, 1.2f, 2.2f);

        // Electric distortion shockwave.
        spawnDistortionShockwave(center);
    }

    private static void spawnDistortionShockwave(Vector2f point) {
        float startSize = 30f;
        float finalSize = 150f;
        float intensity = 60f;
        float duration = 0.5f;
        float expansionTime = 0.35f;
        float fadeTime = 0.6f;

        RippleDistortion ripple = new RippleDistortion(point, new Vector2f(0f, 0f));
        ripple.setSize(finalSize);
        ripple.setIntensity(intensity);
        ripple.setFrameRate(60f / duration);
        ripple.fadeInSize(expansionTime);
        ripple.fadeOutIntensity(fadeTime);
        ripple.setSize(startSize);

        DistortionShader.addDistortion(ripple);
    }
}