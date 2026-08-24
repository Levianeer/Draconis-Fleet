package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;

public class XLII_SwordbreakerOnHitEffect implements OnHitEffectPlugin {

    // Small conventional blast, styled after XLII_EMPBlastOnHitEffect's layered
    // blob recipe but scaled down and re-themed orange/white for PD frag missile.
    private static final Color COLOR_FRINGE = new Color(255, 140, 50, 190);
    private static final Color COLOR_CORE   = new Color(255, 220, 160, 210);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        SpriteAPI spr = Global.getSettings().getSprite("fx", "XLII_explosion");
        float angle = 360f * (float) Math.random();

        // Soft growing blob.
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(8, 8), new Vector2f(60, 60),
                angle, 0, COLOR_FRINGE, false, 0, 0.05f, 0.1f);
        // Denser, contracting core.
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(12, 12), new Vector2f(-8, -8),
                angle, 0, COLOR_CORE, false, 0.03f, 0.08f, 0.25f);

        engine.addHitParticle(point, new Vector2f(), 20f, 0.5f, 0.4f, COLOR_FRINGE);
        engine.addSmoothParticle(point, new Vector2f(), 25f, 1.5f, 0.15f, Color.white);
    }
}
