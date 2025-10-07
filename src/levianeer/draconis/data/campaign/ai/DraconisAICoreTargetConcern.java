package levianeer.draconis.data.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.concern.MarketRelatedConcern;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import levianeer.draconis.data.campaign.intel.events.DraconisAICoreTargetingMonitor;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DraconisAICoreTargetConcern extends MarketRelatedConcern {

    private static final Logger log = Global.getLogger(DraconisAICoreTargetConcern.class);

    public static final float AI_CORE_VALUE_MULT = 3.0f; // Major multiplier for AI core priority
    public static final int MAX_MARKETS_FOR_PICKER = 6;

    @Override
    public boolean generate() {
        List<Pair<MarketAPI, Float>> targetsSorted = new ArrayList<>();
        Set<MarketAPI> alreadyConcernMarkets = getExistingConcernItems();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (alreadyConcernMarkets.contains(market)) continue;

            // Must be flagged as AI core target
            if (!market.getMemoryWithoutUpdate().getBoolean(
                    DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG)) {
                continue;
            }

            // Must be valid target for raids/invasions
            boolean canUse = InvasionFleetManager.getManager().isValidInvasionOrRaidTarget(
                    ai.getFaction(), null, market, null, false);
            if (!canUse) continue;

            // Get AI core strategic value
            float coreValue = market.getMemoryWithoutUpdate().getFloat(
                    DraconisAICoreTargetingMonitor.AI_CORE_VALUE_FLAG);

            if (coreValue <= 0) continue;

            // Calculate priority considering defenses AND AI core value
            int size = market.getSize();
            float marketValue = getMarketValue(market);
            float sd = getSpaceDefenseValue(market);
            float gd = getGroundDefenseValue(market);

            // Base value calculation (like VulnerableEnemyTargetConcern)
            float valueMod = marketValue / (sd * 2 + gd) / SAIConstants.MARKET_VALUE_DIVISOR;
            valueMod *= (1 + 0.5f * size / 5);

            // CRITICAL: Multiply by AI core value to prioritize these targets
            valueMod *= (1 + coreValue * AI_CORE_VALUE_MULT);

            if (valueMod < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) continue;

            targetsSorted.add(new Pair<>(market, valueMod));
        }

        if (targetsSorted.isEmpty()) return false;

        // Sort by priority
        targetsSorted.sort((a, b) -> Float.compare(b.two, a.two));

        // Pick from top targets
        int max = Math.min(targetsSorted.size(), MAX_MARKETS_FOR_PICKER);
        WeightedRandomPicker<Pair<MarketAPI, Float>> picker = new WeightedRandomPicker<>();
        for (int i = 0; i < max; i++) {
            Pair<MarketAPI, Float> pair = targetsSorted.get(i);
            picker.add(pair, pair.two);
        }

        Pair<MarketAPI, Float> goal = picker.pick();
        if (goal != null) {
            market = goal.one;
            float coreValue = market.getMemoryWithoutUpdate().getFloat(
                    DraconisAICoreTargetingMonitor.AI_CORE_VALUE_FLAG);

            priority.modifyFlat("aiCoreValue", goal.two,
                    StrategicAI.getString("statDefenseAdjustedValue", true));
            priority.modifyFlat("aiCoreBonus", coreValue * 10, "AI Core Strategic Value");

            log.info(String.format("Draconis AI generated concern for AI core target: %s (priority: %.1f, cores value: %.1f)",
                    market.getName(), priority.getModifiedValue(), coreValue));
        }

        return market != null;
    }

    @Override
    public void update() {
        // Verify market still has AI cores
        if (!market.getMemoryWithoutUpdate().getBoolean(
                DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG)) {
            log.info("Market " + market.getName() + " no longer has AI cores, ending concern");
            end();
            return;
        }

        // Recalculate priority
        float coreValue = market.getMemoryWithoutUpdate().getFloat(
                DraconisAICoreTargetingMonitor.AI_CORE_VALUE_FLAG);
        int size = market.getSize();
        float marketValue = getMarketValue(market);
        float sd = getSpaceDefenseValue(market);
        float gd = getGroundDefenseValue(market);

        float valueMod = marketValue / (sd * 2 + gd) / SAIConstants.MARKET_VALUE_DIVISOR;
        valueMod *= (1 + 0.5f * size / 5);
        valueMod *= (1 + coreValue * AI_CORE_VALUE_MULT);

        priority.modifyFlat("aiCoreValue", valueMod,
                StrategicAI.getString("statDefenseAdjustedValue", true));
        priority.modifyFlat("aiCoreBonus", coreValue * 10, "AI Core Strategic Value");

        super.update();
    }

    @Override
    public void modifyActionPriority(StrategicAction action) {
        // Boost priority for raids and invasions against AI core targets
        if (action.getDef().hasTag(SAIConstants.TAG_MILITARY)) {
            action.getPriority().modifyMult("aiCoreTarget", 1.5f, "High-Value AI Core Target");

            log.info(String.format("Boosting priority for %s action against AI core target %s",
                    action.getName(), market.getName()));
        }
    }

    @Override
    public boolean isValid() {
        return market != null &&
                market.getFaction().isHostileTo(ai.getFaction()) &&
                market.getMemoryWithoutUpdate().getBoolean(
                        DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG);
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof DraconisAICoreTargetConcern) {
            return otherConcern.getMarket() == this.market;
        }
        return false;
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        Color h = Misc.getHighlightColor();

        float coreValue = market.getMemoryWithoutUpdate().getFloat(
                DraconisAICoreTargetingMonitor.AI_CORE_VALUE_FLAG);
        int alphaCount = market.getMemoryWithoutUpdate().getInt(
                DraconisAICoreTargetingMonitor.AI_CORE_ALPHA_COUNT_FLAG);
        int betaCount = market.getMemoryWithoutUpdate().getInt(
                DraconisAICoreTargetingMonitor.AI_CORE_BETA_COUNT_FLAG);
        int gammaCount = market.getMemoryWithoutUpdate().getInt(
                DraconisAICoreTargetingMonitor.AI_CORE_GAMMA_COUNT_FLAG);

        String str = String.format("Intelligence confirms %d AI cores at %s (Alpha: %d, Beta: %d, Gamma: %d). " +
                        "Strategic value: %.0f. This target is designated as high priority for acquisition operations.",
                alphaCount + betaCount + gammaCount, market.getName(),
                alphaCount, betaCount, gammaCount, coreValue);

        return tooltip.addPara(str, pad, h,
                String.valueOf(alphaCount + betaCount + gammaCount),
                market.getName(),
                String.valueOf(alphaCount),
                String.valueOf(betaCount),
                String.valueOf(gammaCount),
                String.format("%.0f", coreValue));
    }
}