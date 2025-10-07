package levianeer.draconis.data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class XLII_MassDamageOnHit implements OnHitEffectPlugin {

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (!shieldHit && target instanceof ShipAPI) {
            float mass = target.getMass();
            float damageModifier = 1f + Math.min(4f, (mass / 1000f) + (5f / (float)Math.sqrt(mass)));
            //float damageModifier = (mass / 2500) + (float)(8.5 / Math.sqrt(mass));  // Calculates the modifier. Thanks Owen for the equation <3

            // Clamp damageModifier between 0.5 and 10
            damageModifier = Math.max(0.5f, Math.min(damageModifier, 10f));

            float damage = projectile.getDamageAmount();
            float newDamage = damage * damageModifier;

            engine.applyDamage(target, point, newDamage, DamageType.HIGH_EXPLOSIVE, 0, false, false, target, false);
        }
    }
}