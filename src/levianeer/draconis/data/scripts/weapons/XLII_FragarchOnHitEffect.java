package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;

public class XLII_FragarchOnHitEffect implements OnHitEffectPlugin {

    private static final Color COLOR_FRINGE = new Color(185, 128, 110, 255);
    private static final Color COLOR_MID    = new Color(255, 75, 35, 175);
    private static final Color COLOR_CORE   = new Color(255, 188, 137, 200);

    private final XLII_SystemBurnOnHitEffect burnEffect = new XLII_SystemBurnOnHitEffect();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (target instanceof MissileAPI || target instanceof AsteroidAPI) return;

        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point,
                new Vector2f(),
                new Vector2f(48, 48), // Size
                new Vector2f(400, 400),
                360 * (float) Math.random(),
                0,
                COLOR_FRINGE,
                true,
                0,
                0.1f,
                0.15f
        );
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point,
                new Vector2f(),
                new Vector2f(64, 64), // Size
                new Vector2f(200, 200),
                360 * (float) Math.random(),
                0,
                COLOR_MID,
                true,
                0.2f,
                0.0f,
                0.3f
        );
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point,
                new Vector2f(),
                new Vector2f(98, 98), // Size
                new Vector2f(100, 100),
                360 * (float) Math.random(),
                0,
                COLOR_CORE,
                true,
                0.4f,
                0.0f,
                0.6f
        );

        engine.addHitParticle(point, new Vector2f(), 125, 0.1f, 1f, COLOR_FRINGE);
        engine.addSmoothParticle(point, new Vector2f(), 175, 2f, 0.25f, Color.white);
        engine.addSmoothParticle(point, new Vector2f(), 250, 2f, 0.1f, Color.white);

        burnEffect.onHit(projectile, target, point, shieldHit, damageResult, engine);
    }
}