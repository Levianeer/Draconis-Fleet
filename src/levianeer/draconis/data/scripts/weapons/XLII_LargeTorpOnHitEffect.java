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

    private static final Color COLOR_FRINGE = new Color(185, 128, 110, 255);
    private static final Color COLOR_MID    = new Color(255,  75,  35, 225);
    private static final Color COLOR_CORE   = new Color(255, 188, 137, 235);

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

        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(72, 72),   new Vector2f(600, 600),
                360 * (float) Math.random(), 0, COLOR_FRINGE, false, 0,    0.1f, 0.15f);
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(96, 96),   new Vector2f(300, 300),
                360 * (float) Math.random(), 0, COLOR_MID,    false, 0.2f, 0.0f,  0.3f);
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(150, 150), new Vector2f(150, 150),
                360 * (float) Math.random(), 0, COLOR_CORE,   false, 0.4f, 0.0f,  0.6f);

        engine.addHitParticle(point, new Vector2f(), 175, 0.1f, 1f, COLOR_FRINGE);
        engine.addSmoothParticle(point, new Vector2f(), 225, 2f, 0.25f, Color.white);
        engine.addSmoothParticle(point, new Vector2f(), 300, 2f, 0.1f,  Color.white);

        Global.getSoundPlayer().playSound("mine_explosion", 1f, 1f, point, new Vector2f());
    }
}
