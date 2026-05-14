//Unused
package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class XLII_BardicheOnFireEffect implements OnHitEffectPlugin {

    private static final Color ARC_FRINGE = new Color(35, 105, 155, 255);
    private static final Color ARC_CORE = new Color(255, 255, 255, 255);

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if ((float) Math.random() < 0.25f && !shieldHit && target instanceof ShipAPI) {

            float emp = projectile.getEmpAmount();
            float dam = 0;

            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    dam,
                    emp, // emp
                    100000f, // max range
                    "shock_repeater_emp_impact",
                    20f, // thickness
                    ARC_FRINGE,
                    ARC_CORE
            );
        }
    }
}