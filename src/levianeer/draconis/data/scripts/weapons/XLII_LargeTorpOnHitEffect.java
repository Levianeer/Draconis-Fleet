package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;

public class XLII_LargeTorpOnHitEffect implements OnHitEffectPlugin {

    // Two blob layers, one soft and growing, one denser and contracting
    private static final Color COLOR_FRINGE    = new Color(185, 128, 110, 140);
    private static final Color COLOR_MID       = new Color(255,  75,  35, 200);
    private static final Color COLOR_RING      = new Color(255, 255, 255, 90);
    private static final Color COLOR_SMOKE     = new Color(45,   40,  35, 150);
    private static final Color COLOR_AFTERGLOW = new Color(255, 130,  70, 120);

    private static final int   DEBRIS_COUNT     = 24;
    private static final float DEBRIS_SPEED_MIN = 150f;
    private static final float DEBRIS_SPEED_MAX = 400f;

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit,
                      ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (target instanceof MissileAPI || target instanceof AsteroidAPI) return;
        spawnVisuals(point, engine);
    }

    /** Shared by on-hit and by the flare effect's range-expiry handler. */
    public static void spawnVisuals(Vector2f point, CombatEngineAPI engine) {
        SpriteAPI spr = Global.getSettings().getSprite("fx", "XLII_explosion");

        // Single shared rotation
        float angle = 360f * (float) Math.random();

        // Soft growing blob
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(72, 72), new Vector2f(600, 600),
                angle, 0, COLOR_FRINGE, false, 0, 0.1f, 0.15f);
        // Denser, contracting blob
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(96, 96), new Vector2f(-80, -80),
                angle, 0, COLOR_MID, false, 0.1f, 0.2f, 0.5f);

        // Fast shockwave ring
        SpriteAPI ring = Global.getSettings().getSprite("graphics/fx/explosion_ring0.png");
        MagicRender.battlespace(ring, point, new Vector2f(), new Vector2f(120, 120), new Vector2f(1600, 1600),
                angle, 0, COLOR_RING, true, 0, 0.05f, 0.2f);

        // Afterglow - appears near the flash blobs' peak size with barely any further growth,
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(200, 200), new Vector2f(60, 60),
                angle, 0, COLOR_AFTERGLOW, true, 0.15f, 0.6f, 1.8f);

        // Lingering smoke plume
        SpriteAPI smoke = Global.getSettings().getSprite("graphics/fx/explosion3.png");
        MagicRender.battlespace(smoke, point, new Vector2f(), new Vector2f(130, 130), new Vector2f(90, 90),
                angle, 5, COLOR_SMOKE, false, 0.3f, 1.2f, 2.2f);

        engine.addHitParticle(point, new Vector2f(), 180, 0.1f, 1f, COLOR_FRINGE);
        engine.addSmoothParticle(point, new Vector2f(), 230, 2f, 0.25f, Color.white);
        engine.addSmoothParticle(point, new Vector2f(), 300, 2f, 0.1f,  Color.white);

        // Radial debris burst.
        for (int i = 0; i < DEBRIS_COUNT; i++) {
            double dice = Math.random() * Math.PI * 2;
            float speed = DEBRIS_SPEED_MIN + (float) Math.random() * (DEBRIS_SPEED_MAX - DEBRIS_SPEED_MIN);
            Vector2f vel = new Vector2f((float) Math.cos(dice) * speed, (float) Math.sin(dice) * speed);
            float size = 4f + (float) Math.random() * 6f;
            engine.addHitParticle(point, vel, size, 1f, 0.6f + (float) Math.random() * 0.8f, COLOR_FRINGE);
        }

        Global.getSoundPlayer().playSound("mine_explosion", 1f, 1f, point, new Vector2f());
    }
}
