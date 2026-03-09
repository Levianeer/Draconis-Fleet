package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipSystemAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XLII_IncomprehensibleHorrors extends BaseHullMod {

    private static final float COOLDOWN_REDUCTION = -35f;
    private static final float HULL_RESTORE_PERCENT = 0.20f; // % of max hull at full modules (20%)
    private static final int TOTAL_MODULES = 4;

    // Module hull IDs
    private static final String MODULE_FRONT = "XLII_module_sunsetter_armor_front";
    private static final String MODULE_BACK = "XLII_module_sunsetter_armor_back";
    private static final String MODULE_LEFT = "XLII_module_sunsetter_armor_left";
    private static final String MODULE_RIGHT = "XLII_module_sunsetter_armor_right";

    // Per-ship state — BaseHullMod instances are shared across all ships with this mod,
    // so instance fields would be corrupted when multiple Sunsetter ships are in combat.
    private static final Map<String, Boolean> wasSystemActiveMap = new HashMap<>();
    private static final Map<String, Float> hullRestorePerSecondMap = new HashMap<>();
    private static CombatEngineAPI lastEngine_Horrors;

    private static void checkClearStateMaps() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_Horrors) {
            lastEngine_Horrors = engine;
            wasSystemActiveMap.clear();
            hullRestorePerSecondMap.clear();
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSystemCooldownBonus().modifyPercent(id, COOLDOWN_REDUCTION);
    }

    /**
     * Counts the number of alive armor modules on the Sunsetter
     */
    private int countAliveModules(ShipAPI ship) {
        if (ship.getChildModulesCopy() == null) return 0;

        int aliveCount = 0;
        List<ShipAPI> modules = ship.getChildModulesCopy();

        for (ShipAPI module : modules) {
            if (module == null || module.getHullSpec() == null) continue;

            String hullId = module.getHullSpec().getHullId();
            if ((MODULE_FRONT.equals(hullId) || MODULE_BACK.equals(hullId) ||
                 MODULE_LEFT.equals(hullId) || MODULE_RIGHT.equals(hullId)) &&
                !module.isHulk()) {
                aliveCount++;
            }
        }

        return aliveCount;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;

        checkClearStateMaps();

        // Prevent engine flameouts
        ship.getMutableStats().getEngineHealthBonus().modifyFlat("XLII_IncomprehensibleHorrors", 1000f);

        ShipSystemAPI system = ship.getSystem();
        if (system == null) return;

        String shipId = ship.getId();
        boolean wasSystemActive = wasSystemActiveMap.getOrDefault(shipId, false);
        float hullRestorePerSecond = hullRestorePerSecondMap.getOrDefault(shipId, 0f);

        boolean isSystemActive = system.isActive();

        // System just activated - calculate restoration rate only once per activation
        if (isSystemActive && !wasSystemActive) {
            int aliveModules = countAliveModules(ship);

            // No healing if all modules are destroyed
            if (aliveModules == 0) {
                hullRestorePerSecond = 0f;
            } else {
                float duration = system.getSpecAPI().getIn();
                if (duration > 0) {
                    // Scale healing based on alive modules (1-4 modules = 25%-100% effectiveness)
                    float moduleMultiplier = (float) aliveModules / TOTAL_MODULES;
                    float totalHullToRestore = ship.getMaxHitpoints() * HULL_RESTORE_PERCENT * moduleMultiplier;
                    hullRestorePerSecond = totalHullToRestore / duration;
                } else {
                    hullRestorePerSecond = 0f;
                }
            }
        }

        // System deactivated - reset restoration rate
        if (!isSystemActive && wasSystemActive) {
            hullRestorePerSecond = 0f;
        }

        // Restore hull while system is active
        if (isSystemActive && hullRestorePerSecond > 0) {
            float hullToRestore = hullRestorePerSecond * amount;
            ship.setHitpoints(Math.min(ship.getHitpoints() + hullToRestore, ship.getMaxHitpoints()));
        }

        wasSystemActiveMap.put(shipId, isSystemActive);
        hullRestorePerSecondMap.put(shipId, hullRestorePerSecond);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) Math.round(HULL_RESTORE_PERCENT * 100f) + "%";
        if (index == 1) return "25%"; // 100% / 4 modules = 25% per module
        if (index == 2) return "" + (int) Math.round(-COOLDOWN_REDUCTION) + "%";
        return null;
    }
}