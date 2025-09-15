package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class fsdf_SpearOnHitEffect implements OnHitEffectPlugin {

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        // FX
        Vector2f vel = target.getVelocity();
        engine.spawnExplosion(point, vel, Color.white, 90f, 0.6f);
        engine.spawnExplosion(point, vel, Color.white, 60f, 0.6f);
        engine.addNegativeNebulaParticle(point, vel, 30f, 2f, 0f, 0f, 0.5f, Color.white);

        if (!shieldHit) {
            float dir;
            float arc;
            dir = Misc.getAngleInDegrees(target.getLocation(), point);
            arc = 150f;
            engine.spawnDebrisSmall(point, vel, 6, dir, arc, 20f, 20f, 720f);
            engine.spawnDebrisMedium(point, vel, 3, dir, arc, 10f, 20f, 360f);
            engine.spawnDebrisLarge(point, vel, 1, dir, arc, 10f, 10f, 180f);
        }

        if (point == null) {
            return;
        }

        // EMP Arcs
        if (target instanceof ShipAPI empTarget) {

            boolean piercesShield = false;

            if (shieldHit) {
                float pierceChance = empTarget.getHardFluxLevel() * 0.75f - 0.1f;
                pierceChance *= empTarget.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);

                piercesShield = Math.random() < pierceChance;
            }

            if (!shieldHit || piercesShield) {

                float emp = projectile.getEmpAmount();
                float dam = projectile.getDamageAmount();

                engine.spawnEmpArcPierceShields(projectile.getSource(), point, empTarget, empTarget,
                        DamageType.ENERGY,
                        dam,
                        emp,
                        100000f,
                        "shock_repeater_emp_impact",
                        20f,
                        new Color(35,105,155,255),
                        new Color(255,255,255,255)
                );
            }
        }
        // SFX
        Global.getSoundPlayer().playSound("mine_explosion", 1f, 1f, point, new Vector2f());
    }
}