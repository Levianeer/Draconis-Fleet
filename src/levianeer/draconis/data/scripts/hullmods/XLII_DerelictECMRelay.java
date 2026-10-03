package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XLII_DerelictECMRelay extends BaseHullMod {

    private static final float SECONDS_PER_ECM_PERCENT = 1f; // extra refit time per 1% of favorable ECM difference

    private static CombatEngineAPI lastEngine_DerelictECMRelay;
    private static final Map<ShipAPI, RelayState> states = new HashMap<>();

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_DerelictECMRelay) {
            lastEngine_DerelictECMRelay = engine;
            states.clear();
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        checkClearState();

        if (ship == null || !ship.isAlive()) {
            RelayState leftover = states.remove(ship);
            if (leftover != null) clearAffected(leftover);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        int myOwner = ship.getOwner();
        int enemyOwner = 1 - myOwner;
        if (myOwner != 0 && myOwner != 1) return; // only meaningful in a standard two-sided battle

        RelayState state = states.get(ship);
        if (state == null) {
            state = new RelayState("XLII_derelictEcmRelay_" + ship.getId());
            states.put(ship, state);
        }

        float favorableDiff = sumFleetEcm(engine, myOwner) - sumFleetEcm(engine, enemyOwner);

        Set<ShipAPI> newlyAffected = new HashSet<>();
        if (favorableDiff > 0f) {
            CombatFleetManagerAPI enemyManager = engine.getFleetManager(enemyOwner);
            if (enemyManager != null) {
                float extraSeconds = favorableDiff * SECONDS_PER_ECM_PERCENT;
                for (Map.Entry<ShipAPI, Float> entry : carrierBaseRefitTimes(enemyManager).entrySet()) {
                    ShipAPI carrier = entry.getKey();
                    float baseRefitTime = entry.getValue();
                    if (baseRefitTime <= 0f) continue;

                    float percent = (extraSeconds / baseRefitTime) * 100f;
                    carrier.getMutableStats().getFighterRefitTimeMult().modifyPercent(state.modId, percent);
                    newlyAffected.add(carrier);
                }
            }
        }

        for (ShipAPI previouslyAffected : state.affectedCarriers) {
            if (!newlyAffected.contains(previouslyAffected)) {
                previouslyAffected.getMutableStats().getFighterRefitTimeMult().unmodifyPercent(state.modId);
            }
        }
        state.affectedCarriers = newlyAffected;
    }

    private void clearAffected(RelayState state) {
        for (ShipAPI carrier : state.affectedCarriers) {
            carrier.getMutableStats().getFighterRefitTimeMult().unmodifyPercent(state.modId);
        }
    }

    /** Fleet-wide ECM rating total, same aggregation vanilla's own Electronic Warfare skill uses. */
    private float sumFleetEcm(CombatEngineAPI engine, int owner) {
        CombatFleetManagerAPI manager = engine.getFleetManager(owner);
        if (manager == null) return 0f;

        float total = 0f;
        for (DeployedFleetMemberAPI member : manager.getDeployedCopyDFM()) {
            if (member.isFighterWing() || member.isStationModule()) continue;
            ShipAPI deployedShip = member.getShip();
            if (deployedShip == null) continue;
            total += deployedShip.getMutableStats().getDynamic().getValue(Stats.ELECTRONIC_WARFARE_FLAT, 0f);
        }
        return total;
    }

    /**
     * Maps each currently-deployed enemy carrier to the average base refit time (seconds) of its
     * own currently-deployed wings, read straight from FighterWingSpecAPI - not an assumed
     * constant - so the seconds-per-%-ECM conversion is exact per wing type rather than
     * approximate. A carrier running several different wing types gets an averaged value, since
     * MutableShipStatsAPI.getFighterRefitTimeMult() applies one multiplier across all of a
     * carrier's bays - there's no public hook to target one bay's cycle independently of the rest.
     */
    private Map<ShipAPI, Float> carrierBaseRefitTimes(CombatFleetManagerAPI manager) {
        Map<ShipAPI, Float> sum = new HashMap<>();
        Map<ShipAPI, Integer> count = new HashMap<>();
        Set<String> seenWings = new HashSet<>();

        for (DeployedFleetMemberAPI member : manager.getDeployedCopyDFM()) {
            if (!member.isFighterWing()) continue;
            ShipAPI fighter = member.getShip();
            if (fighter == null) continue;

            FighterWingAPI wing = fighter.getWing();
            if (wing == null || !seenWings.add(wing.getWingId())) continue;

            ShipAPI carrier = wing.getSourceShip();
            FighterWingSpecAPI spec = wing.getSpec();
            if (carrier == null || spec == null) continue;

            float refit = spec.getRefitTime();
            if (refit <= 0f) continue;

            sum.merge(carrier, refit, Float::sum);
            count.merge(carrier, 1, Integer::sum);
        }

        Map<ShipAPI, Float> average = new HashMap<>();
        for (Map.Entry<ShipAPI, Float> entry : sum.entrySet()) {
            average.put(entry.getKey(), entry.getValue() / count.get(entry.getKey()));
        }
        return average;
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara(
                "Salvaged targeting-network hardware that latches onto enemy flight deck cycles. Whenever " +
                        "your fleet's ECM rating exceeds the enemy's, every point of that advantage adds %s to " +
                        "how long it takes the enemy to replace a lost fighter.",
                opad, h, Math.round(SECONDS_PER_ECM_PERCENT) + "s");

        tooltip.addPara(
                "Has no effect if the enemy's ECM rating is equal to or higher than your own. The effect of " +
                        "multiple relays, across any of your ships, is cumulative.",
                opad);
    }

    static class RelayState {
        final String modId;
        Set<ShipAPI> affectedCarriers = new HashSet<>();

        RelayState(String modId) {
            this.modId = modId;
        }
    }
}
