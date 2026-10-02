package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;

// Armor-piercing rounds: by the time onHit fires, the engine has already
// applied the shot's full damage to armor (ApplyDamageResultAPI's setters
// don't retroactively change that - see BreachOnHitEffect/DisintegratorEffect
// in the vanilla source, which both manipulate ArmorGridAPI/hitpoints
// directly rather than the damage result). So instead we restore half the
// armor damage the engine just applied and redirect it straight to hull,
// bypassing armor mitigation entirely. Total damage output per shot is
// unchanged, just split 50/50 between armor and hull.
public class XLII_ChaingunOnHitEffect implements OnHitEffectPlugin {

    private static final float BYPASS_FRACTION = 0.5f;

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (shieldHit) return;
        if (!(target instanceof ShipAPI)) return;

        float armorDamage = damageResult.getTotalDamageToArmor();
        if (armorDamage <= 0f) return;

        ShipAPI ship = (ShipAPI) target;
        float bypassDamage = armorDamage * BYPASS_FRACTION;

        ArmorGridAPI grid = ship.getArmorGrid();
        int[] cell = grid.getCellAtLocation(point);
        if (cell != null) {
            float current = grid.getArmorValue(cell[0], cell[1]);
            float restored = Math.min(bypassDamage, grid.getMaxArmorInCell() - current);
            if (restored > 0f) {
                grid.setArmorValue(cell[0], cell[1], current + restored);
                ship.syncWithArmorGridState();
            }
        }

        ship.setHitpoints(ship.getHitpoints() - bypassDamage);
        if (ship.getHitpoints() <= 0f && !ship.isHulk()) {
            ship.setSpawnDebris(false);
            engine.applyDamage(ship, point, 1f, DamageType.FRAGMENTATION, 0f,
                    true, false, projectile.getSource(), false);
        }
    }
}
