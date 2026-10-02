package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * Capacitor Overdraw: a toggle that draws its buff directly off the ship's
 * current hard flux level instead of banking damage over time - the harder
 * the shields are already working, the harder the ballistic mounts hit.
 */
public class XLII_CapacitorOverdrawStats extends BaseShipSystemScript implements DamageDealtModifier {

    // ==================== TUNING PARAMETERS ====================

    // Ballistic rate-of-fire bonus at 100% hard flux.
    private static final float MAX_ROF_BONUS = 0.6f;

    // Bonus energy damage per ballistic impact, as a fraction of that shot's damage, at 100% hard flux.
    private static final float MAX_ENERGY_DAMAGE_FRACTION = 0.4f;

    // Ballistic weapon flux cost increase at 100% hard flux.
    private static final float MAX_FLUX_COST_INCREASE = 0.5f;

    private static final Color ARC_COLOR = new Color(255, 210, 140, 255);

    // ==================== INSTANCE STATE ====================

    private boolean listenerRegistered = false;
    private boolean active = false;
    private float hardFluxFraction = 0f; // cached for modifyDamageDealt and getStatusData

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        if (!listenerRegistered) {
            ship.addListener(this);
            listenerRegistered = true;
        }

        if (state == State.IN || state == State.ACTIVE || state == State.OUT) {
            active = true;

            float maxFlux = ship.getFluxTracker().getMaxFlux();
            float rawFraction = maxFlux > 0f ? ship.getFluxTracker().getHardFlux() / maxFlux : 0f;
            hardFluxFraction = Math.min(1f, rawFraction) * effectLevel;

            stats.getBallisticRoFMult().modifyMult(id, 1f + MAX_ROF_BONUS * hardFluxFraction);
            stats.getBallisticWeaponFluxCostMod().modifyPercent(id, MAX_FLUX_COST_INCREASE * 100f * hardFluxFraction);
        } else {
            active = false;
            hardFluxFraction = 0f;
            stats.getBallisticRoFMult().unmodify(id);
            stats.getBallisticWeaponFluxCostMod().unmodify(id);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Never called - runScriptWhileIdle:true in XLII_capacitor_overdraw.system.
    }

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (!active || hardFluxFraction <= 0f) return null;
        if (!(param instanceof DamagingProjectileAPI proj)) return null;
        if (!(target instanceof ShipAPI)) return null;

        WeaponAPI weapon = proj.getWeapon();
        if (weapon == null || weapon.getType() != WeaponType.BALLISTIC) return null;

        float bonusDamage = damage.getDamage() * MAX_ENERGY_DAMAGE_FRACTION * hardFluxFraction;
        if (bonusDamage <= 0f) return null;

        Global.getCombatEngine().applyDamage(target, point, bonusDamage, DamageType.ENERGY,
                0f, false, true, proj.getSource(), false);
        Global.getCombatEngine().addHitParticle(point, new Vector2f(), 6f + 6f * hardFluxFraction,
                0.8f, 0.2f, ARC_COLOR);

        return null;
    }

    @Override
    public String getDisplayNameOverride(State state, float effectLevel) {
        if (state == State.IN || state == State.ACTIVE || state == State.OUT) {
            return "capacitor overdraw - active";
        }
        return null;
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.IN || state == State.ACTIVE || state == State.OUT) {
            if (index == 0) return new StatusData(String.format("+%.0f%% ballistic rate of fire", MAX_ROF_BONUS * 100f * hardFluxFraction), false);
            if (index == 1) return new StatusData(String.format("+%.0f%% bonus energy damage on hit", MAX_ENERGY_DAMAGE_FRACTION * 100f * hardFluxFraction), false);
        }
        return null;
    }
}
