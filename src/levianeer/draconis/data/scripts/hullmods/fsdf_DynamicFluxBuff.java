package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import levianeer.draconis.data.scripts.fsdf_StatusScript;

import java.awt.*;

public class fsdf_DynamicFluxBuff extends BaseHullMod {

    private static final float MIN_DAMAGE_BONUS = 0f;
    private static final float MAX_DAMAGE_BONUS = 0.2f;

    private static final float MIN_DAMAGE_REDUCTION = 0.1f;
    private static final float MAX_DAMAGE_REDUCTION = 0.30f;

    private static final float SYSTEM_COOLDOWN_DECREASE_PERCENT = 1.5f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSystemRegenBonus().modifyMult(id, 1f / (1f + SYSTEM_COOLDOWN_DECREASE_PERCENT));
        stats.getSystemUsesBonus().modifyFlat(id, -1f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) {
            return;
        }

        if (ship.getCustomData().get("fsdf_CustomStatusData") == null) {
            ship.setCustomData("fsdf_CustomStatusData", true);
            Global.getCombatEngine().addPlugin(new fsdf_StatusScript(ship));
        }

        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        float scaledFluxLevel = Math.min(fluxLevel / 0.9f, 1f);

        // Buffs
        float damageBonus = MIN_DAMAGE_BONUS + (MAX_DAMAGE_BONUS - MIN_DAMAGE_BONUS) * scaledFluxLevel;

        MutableShipStatsAPI stats = ship.getMutableStats();

        stats.getBallisticWeaponDamageMult().unmodify("fsdf_DynamicBuffStat");
        stats.getEnergyWeaponDamageMult().unmodify("fsdf_DynamicBuffStat");
        stats.getMissileWeaponDamageMult().unmodify("fsdf_DynamicBuffStat");

        stats.getBallisticWeaponFluxCostMod().unmodify("fsdf_DynamicBuffStat");
        stats.getEnergyWeaponFluxCostMod().unmodify("fsdf_DynamicBuffStat");
        stats.getMissileWeaponFluxCostMod().unmodify("fsdf_DynamicBuffStat");

        if (damageBonus > 0f) {
            stats.getBallisticWeaponDamageMult().modifyPercent("fsdf_DynamicBuffStat", damageBonus * 100f);
            stats.getEnergyWeaponDamageMult().modifyPercent("fsdf_DynamicBuffStat", damageBonus * 100f);
            stats.getMissileWeaponDamageMult().modifyPercent("fsdf_DynamicBuffStat", damageBonus * 100f);

            stats.getBallisticWeaponFluxCostMod().modifyPercent("fsdf_DynamicBuffStat", damageBonus * 100f);
            stats.getEnergyWeaponFluxCostMod().modifyPercent("fsdf_DynamicBuffStat", damageBonus * 100f);
            stats.getMissileWeaponFluxCostMod().modifyPercent("fsdf_DynamicBuffStat", damageBonus * 100f);
        }

        float damageReduction = MIN_DAMAGE_REDUCTION + (MAX_DAMAGE_REDUCTION - MIN_DAMAGE_REDUCTION) * scaledFluxLevel;
        float damageTakenMult = 1f - damageReduction;

        stats.getShieldDamageTakenMult().unmodify("fsdf_DynamicBuffStat");
        stats.getArmorDamageTakenMult().unmodify("fsdf_DynamicBuffStat");
        stats.getHullDamageTakenMult().unmodify("fsdf_DynamicBuffStat");

        if (damageReduction > 0f) {
            stats.getShieldDamageTakenMult().modifyMult("fsdf_DynamicBuffStat", damageTakenMult);
            stats.getArmorDamageTakenMult().modifyMult("fsdf_DynamicBuffStat", damageTakenMult);
            stats.getHullDamageTakenMult().modifyMult("fsdf_DynamicBuffStat", damageTakenMult);
        }

        // FX
        Color jitterUnderColor = new Color(255, 165, 90, 155);
        Color jitterColor = new Color(255, 165, 90, 55);

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
        if (index == 0) return "0%"; // Min offense
        if (index == 1) return "20%"; // Max offense
        if (index == 2) return "10%"; // Min defense
        if (index == 3) return "30%"; // Max defense
        if (index == 4) return "100%"; // Cooldown Increase
        return null;
    }
}