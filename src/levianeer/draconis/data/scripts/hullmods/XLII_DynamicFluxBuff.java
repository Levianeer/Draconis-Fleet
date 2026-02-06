package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;

import java.awt.*;

public class XLII_DynamicFluxBuff extends BaseHullMod {

    public static final float MIN_DAMAGE_BONUS = 0f;
    public static final float MAX_DAMAGE_BONUS = 0.5f;

    public static final float MIN_DAMAGE_REDUCTION = 0.1f;
    public static final float MAX_DAMAGE_REDUCTION = 0.4f;

    private static final float SYSTEM_COOLDOWN_DECREASE_PERCENT = 1f; // x+1 = %

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSystemRegenBonus().modifyMult(id, 1f / (1f + SYSTEM_COOLDOWN_DECREASE_PERCENT));
        stats.getSystemUsesBonus().modifyFlat(id, -1f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) {
            return;
        }

        if (ship.getCustomData().get("XLII_CustomStatusData") == null) {
            ship.setCustomData("XLII_CustomStatusData", true);
            Global.getCombatEngine().addPlugin(new XLII_DynamicFluxBuffStatusScript(ship));
        }

        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        float scaledFluxLevel = Math.min(fluxLevel / 0.8f, 1f);

        // Buffs
        float damageBonus = MIN_DAMAGE_BONUS + (MAX_DAMAGE_BONUS - MIN_DAMAGE_BONUS) * scaledFluxLevel;

        MutableShipStatsAPI stats = ship.getMutableStats();

        stats.getBallisticWeaponDamageMult().unmodify("XLII_DynamicBuffStat");
        stats.getEnergyWeaponDamageMult().unmodify("XLII_DynamicBuffStat");
        stats.getMissileWeaponDamageMult().unmodify("XLII_DynamicBuffStat");

        stats.getBallisticWeaponFluxCostMod().unmodify("XLII_DynamicBuffStat");
        stats.getEnergyWeaponFluxCostMod().unmodify("XLII_DynamicBuffStat");
        stats.getMissileWeaponFluxCostMod().unmodify("XLII_DynamicBuffStat");

        if (damageBonus > 0f) {
            stats.getBallisticWeaponDamageMult().modifyPercent("XLII_DynamicBuffStat", damageBonus * 100f);
            stats.getEnergyWeaponDamageMult().modifyPercent("XLII_DynamicBuffStat", damageBonus * 100f);
            stats.getMissileWeaponDamageMult().modifyPercent("XLII_DynamicBuffStat", damageBonus * 100f);

            stats.getBallisticWeaponFluxCostMod().modifyPercent("XLII_DynamicBuffStat", damageBonus * 100f);
            stats.getEnergyWeaponFluxCostMod().modifyPercent("XLII_DynamicBuffStat", damageBonus * 100f);
            stats.getMissileWeaponFluxCostMod().modifyPercent("XLII_DynamicBuffStat", damageBonus * 100f);
        }

        float damageReduction = MIN_DAMAGE_REDUCTION + (MAX_DAMAGE_REDUCTION - MIN_DAMAGE_REDUCTION) * scaledFluxLevel;
        float damageTakenMult = 1f - damageReduction;

        stats.getShieldDamageTakenMult().unmodify("XLII_DynamicBuffStat");
        stats.getArmorDamageTakenMult().unmodify("XLII_DynamicBuffStat");
        stats.getHullDamageTakenMult().unmodify("XLII_DynamicBuffStat");
        stats.getEmpDamageTakenMult().unmodify("XLII_DynamicBuffStat");

        if (damageReduction > 0f) {
            stats.getShieldDamageTakenMult().modifyMult("XLII_DynamicBuffStat", damageTakenMult);
            stats.getArmorDamageTakenMult().modifyMult("XLII_DynamicBuffStat", damageTakenMult);
            stats.getHullDamageTakenMult().modifyMult("XLII_DynamicBuffStat", damageTakenMult);
            stats.getEmpDamageTakenMult().modifyMult("XLII_DynamicBuffStat", damageTakenMult);
        }

        // FX
        Color jitterUnderColor = new Color(255,165,90,155);
        Color jitterColor = new Color(255,165,90,55);

        ship.setJitterUnder(
                this,
                jitterUnderColor,
                scaledFluxLevel,
                Math.round(25 * scaledFluxLevel),
                0f,
                7f * scaledFluxLevel
        );

        ship.setJitter(
                this,
                jitterColor,
                scaledFluxLevel,
                Math.max(1, Math.round(2 * scaledFluxLevel)),
                0f,
                5f * scaledFluxLevel
        );
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return Math.round(MIN_DAMAGE_BONUS * 100f) + "%"; // Min offense
        if (index == 1) return Math.round(MAX_DAMAGE_BONUS * 100f) + "%"; // Max offense
        if (index == 2) return Math.round(MIN_DAMAGE_REDUCTION * 100f) + "%"; // Min defense
        if (index == 3) return Math.round(MAX_DAMAGE_REDUCTION * 100f) + "%"; // Max defense
        if (index == 4) return Math.round(SYSTEM_COOLDOWN_DECREASE_PERCENT * 100f) + "%"; // Cooldown Increase
        return null;
    }
}