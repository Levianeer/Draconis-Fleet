package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;

import java.awt.*;

public class XLII_NukeOnHitEffect implements OnHitEffectPlugin {

    private static final DamagingExplosionSpec VISUAL_EXPLOSION_SPEC = createCachedVisualExplosionSpec();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (!(target instanceof ShipAPI)) return;

        float damage = projectile.getDamageAmount();
        ShipAPI source = projectile.getSource();

        if (shieldHit) {
            ShipAPI ship = (ShipAPI) target;

            float bypassDamage = damage * 0.1f;

            // Deal hull damage directly
            float newHP = Math.max(0f, ship.getHitpoints() - bypassDamage);
            ship.setHitpoints(newHP);

            // Show small EMP arc or spark to indicate it pierced
            engine.spawnEmpArcPierceShields(
                    projectile.getSource(),
                    point,
                    ship,
                    ship,
                    DamageType.ENERGY,
                    0f,
                    0f,
                    1000f,
                    "system_emp_emitter_impact",
                    20f,
                    new Color(255, 100, 255),
                    new Color(255, 255, 255)
            );
        }

        // Spawn damaging explosion
        engine.spawnDamagingExplosion(createExplosionSpec(), source, point);

        // Spawn visual explosion
        engine.spawnDamagingExplosion(VISUAL_EXPLOSION_SPEC, source, point);

        // Sharp lens flares
        spawnLensFlares(engine, source, point);

        // Nuclear shockwave distortion ring
        spawnNuclearShockwave(engine, point);
    }

    private static DamagingExplosionSpec createExplosionSpec() {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.125f,
                350f,         // max radius
                250f,               // core radius
                0,      // full damage
                0,     // min damage
                CollisionClass.NONE,
                CollisionClass.NONE,
                1f,   // particle size
                5f,  // duration
                0.1f,// particle count
                5,   // particle count (int)
                new Color(255, 161, 201, 255),
                new Color(255, 59, 141, 255)
        );
        spec.setDamageType(DamageType.FRAGMENTATION);
        spec.setSoundSetId("XLII_halberd_explosion");
        return spec;
    }

    private static DamagingExplosionSpec createCachedVisualExplosionSpec() {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.5f,
                350f,
                250f,
                0f,
                0f,
                CollisionClass.NONE,
                CollisionClass.NONE,
                5f,
                10f,
                1.0f,
                80,
                new Color(255, 161, 201, 255),
                new Color(255, 59, 141, 255)
        );

        spec.setUseDetailedExplosion(true);
        spec.setDetailedExplosionFlashDuration(1.0f);
        spec.setDetailedExplosionRadius(250f);
        spec.setDetailedExplosionFlashRadius(350f);
        spec.setDetailedExplosionFlashColorFringe(new Color(255, 255, 255, 255));
        spec.setDetailedExplosionFlashColorCore(new Color(49, 49, 255, 255));
        spec.setDamageType(DamageType.FRAGMENTATION); // harmless visual
        spec.setSoundSetId(null);

        return spec;
    }

    private static void spawnLensFlares(CombatEngineAPI engine, ShipAPI source, Vector2f center) {
        final int flareCount = 12;
        final float flareRange = 100f;

        for (int i = 6; i < flareCount; i++) {
            Vector2f flarePoint = MathUtils.getRandomPointInCircle(center, flareRange);
            float angle = (float) Math.random() * 360f;

            MagicLensFlare.createSharpFlare(
                    engine,
                    source,
                    flarePoint,
                    360f,
                    120f,
                    angle,
                    new Color(100, 100, 255),
                    new Color(255, 255, 255)
            );
        }
    }

    private static void spawnNuclearShockwave(CombatEngineAPI engine, Vector2f point) {
        // GraphicsLib radial distortion ring (nuclear shockwave effect)
        float startSize = 75f;
        float finalSize = 450f;
        float intensity = 150f;
        float duration = 0.8f;
        float expansionTime = 0.7f;
        float fadeTime = 1.2f;

        Vector2f zeroVel = new Vector2f(0, 0);

        RippleDistortion ripple = new RippleDistortion(point, zeroVel);
        ripple.setSize(finalSize);
        ripple.setIntensity(intensity);
        ripple.setFrameRate(60f / duration); // 50 fps animation
        ripple.fadeInSize(expansionTime); // Rapidly expanding wavefront
        ripple.fadeOutIntensity(fadeTime); // Fading distortion
        ripple.setSize(startSize); // Reset to starting size after setting final size

        DistortionShader.addDistortion(ripple);
    }
}