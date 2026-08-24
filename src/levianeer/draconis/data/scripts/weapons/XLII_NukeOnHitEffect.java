package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class XLII_NukeOnHitEffect implements OnHitEffectPlugin {

    private static final DamagingExplosionSpec VISUAL_EXPLOSION_SPEC = createCachedVisualExplosionSpec();

    // Layered blast FX, styled after XLII_LargeTorpOnHitEffect
    private static final Color COLOR_FRINGE    = new Color(255, 140, 200, 140);
    private static final Color COLOR_MID       = new Color(255,  40, 160, 200);
    private static final Color COLOR_RING      = new Color(255, 210, 235,  90);
    private static final Color COLOR_HAZE      = new Color( 70,  20,  55, 150);
    private static final Color COLOR_AFTERGLOW = new Color(255, 120, 190, 120);

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

        // Layered blast visuals
        spawnBlastVisuals(point);
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
                1f,
                1f,
                1f,
                1,
                new Color(255, 161, 201, 255),
                new Color(255, 59, 141, 255)
        );
        spec.setDamageType(DamageType.FRAGMENTATION);
        spec.setSoundSetId("XLII_halberd_explosion");
        spec.setShowGraphic(false); // damage-only; VISUAL_EXPLOSION_SPEC + spawnBlastVisuals handle the look
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
                3.0f,
                1.0f,
                1.0f,
                1280,
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

    /**
     * Layered blob/ring/haze/afterglow blast (per XLII_LargeTorpOnHitEffect), scaled 1.5x for
     * the nuke's larger blast radius and re-themed pink/magenta. No distortion ripple - this
     * weapon's "storm" identity comes from the lens flares above, not a shockwave.
     */
    private static void spawnBlastVisuals(Vector2f center) {
        SpriteAPI spr = Global.getSettings().getSprite("fx", "XLII_explosion");

        // Single shared rotation so every layer reads as one coherent blast.
        float angle = 360f * (float) Math.random();

        // Soft growing blob.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(108, 108), new Vector2f(900, 900),
                angle, 0, COLOR_FRINGE, false, 0, 0.1f, 0.15f);
        // Denser, contracting core.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(144, 144), new Vector2f(-120, -120),
                angle, 0, COLOR_MID, false, 0.1f, 0.2f, 0.5f);

        // Fast, subtle shockwave ring.
        SpriteAPI ring = Global.getSettings().getSprite("graphics/fx/explosion_ring0.png");
        MagicRender.battlespace(ring, center, new Vector2f(), new Vector2f(180, 180), new Vector2f(2400, 2400),
                angle, 0, COLOR_RING, true, 0, 0.05f, 0.2f);

        // Afterglow - appears near the flash blobs' peak size and just hangs, dimming
        // slowly, giving the blast its lingering hang time instead of a quick flash.
        MagicRender.battlespace(spr, center, new Vector2f(), new Vector2f(300, 300), new Vector2f(90, 90),
                angle, 0, COLOR_AFTERGLOW, true, 0.15f, 0.6f, 1.8f);

        // Lingering haze.
        SpriteAPI haze = Global.getSettings().getSprite("graphics/fx/explosion3.png");
        MagicRender.battlespace(haze, center, new Vector2f(), new Vector2f(195, 195), new Vector2f(135, 135),
                angle, 5, COLOR_HAZE, false, 0.3f, 1.2f, 2.2f);
    }
}