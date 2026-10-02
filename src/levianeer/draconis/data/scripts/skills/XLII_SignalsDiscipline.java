package levianeer.draconis.data.scripts.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Officer skills only ever apply to the ship the officer is piloting (there is no
 * ALL_SHIPS_IN_FLEET propagation for a combat-officer skill, only for admiral/personal ones -
 * see electronic_warfare.skill vs gunnery_implants.skill in the base game data). Fleet-wide
 * reach - to both sides - is done manually here, same pattern as XLII_MarginalAllocation's
 * MalfunctionManager.
 */
public class XLII_SignalsDiscipline {

    public static final String SKILL_ID = "XLII_signals_discipline";

    private static final float EW_RATING_BOOST_MULT = 2f;
    private static final float EW_RATING_DEBUFF_MULT = 0.5f;

    // ECCM Package parity - see data/hullmods/ECCMPackage.java
    private static final float ECCM_CHANCE = 0.35f;

    public static class JammingBoost implements ShipSkillEffect, AfterShipCreationSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {}

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {}

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;
            FleetWideManager.getOrCreate(engine).registerSide(ship.getOwner());
        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {}

        @Override
        public String getEffectDescription(float level) {
            return "Doubles EW of friendly ships, and decreases the chance " +
                    "friendly missiles are affected by ECM and flares by 35%";
        }

        @Override
        public String getEffectPerLevelDescription() { return null; }

        @Override
        public ScopeDescription getScopeDescription() { return ScopeDescription.ALL_SHIPS; }
    }

    public static class JammingResistance implements ShipSkillEffect, AfterShipCreationSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {}

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {}

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;
            FleetWideManager.getOrCreate(engine).registerSide(ship.getOwner());
        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {}

        @Override
        public String getEffectDescription(float level) {
            return "Halves EW of enemy ships, and increase the chance " +
                    "enemy missiles are affected by ECM and flares by 35%";
        }

        @Override
        public String getEffectPerLevelDescription() { return null; }

        @Override
        public ScopeDescription getScopeDescription() { return ScopeDescription.ALL_SHIPS; }
    }

    /**
     * Both effects register the skill-holder's own side here; the manager then buffs every
     * ship on that side and debuffs every ship on the opposing one (standard two-sided combat,
     * same 0-vs-1 assumption ElectronicWarfareScript itself makes).
     */
    public static class FleetWideManager extends BaseEveryFrameCombatPlugin {

        private static final String ENGINE_KEY = SKILL_ID + "_manager";

        private final Set<Integer> friendlySides = new HashSet<>();
        private final Set<Integer> enemySides = new HashSet<>();
        private final Set<ShipAPI> buffed = new HashSet<>();
        private final Set<ShipAPI> debuffed = new HashSet<>();

        public static FleetWideManager getOrCreate(CombatEngineAPI engine) {
            FleetWideManager mgr = (FleetWideManager) engine.getCustomData().get(ENGINE_KEY);
            if (mgr == null) {
                mgr = new FleetWideManager();
                engine.getCustomData().put(ENGINE_KEY, mgr);
                engine.addPlugin(mgr);
            }
            return mgr;
        }

        public void registerSide(int side) {
            friendlySides.add(side);
            enemySides.add(side == 0 ? 1 : 0);
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (friendlySides.isEmpty()) return;
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            for (ShipAPI ship : engine.getShips()) {
                if (ship.isFighter()) continue;
                int owner = ship.getOwner();

                if (friendlySides.contains(owner) && !buffed.contains(ship)) {
                    MutableShipStatsAPI stats = ship.getMutableStats();
                    stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyMult(SKILL_ID, EW_RATING_BOOST_MULT);
                    stats.getEccmChance().modifyFlat(SKILL_ID, ECCM_CHANCE);
                    buffed.add(ship);

                } else if (enemySides.contains(owner) && !debuffed.contains(ship)) {
                    MutableShipStatsAPI stats = ship.getMutableStats();
                    stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyMult(SKILL_ID, EW_RATING_DEBUFF_MULT);
                    stats.getEccmChance().modifyFlat(SKILL_ID, -ECCM_CHANCE);
                    debuffed.add(ship);
                }
            }
        }
    }
}
