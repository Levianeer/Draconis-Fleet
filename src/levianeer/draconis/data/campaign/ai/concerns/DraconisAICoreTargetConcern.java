package levianeer.draconis.data.campaign.ai.concerns;

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
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisAICoreTargetingMonitor;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DraconisAICoreTargetConcern extends MarketRelatedConcern {

    private static final Logger log = Global.getLogger(DraconisAICoreTargetConcern.class);

    public static final float AI_CORE_VALUE_MULT = 3.0f;
    public static final int MAX_MARKETS_FOR_PICKER = 6;

    @Override
    public boolean generate() {
        log.info("=== Draconis AI Core Concern: Starting generation for " + ai.getFaction().getDisplayName() + " ===");

        List<Pair<MarketAPI, Float>> targetsSorted = new ArrayList<>();
        Set alreadyConcernMarkets = getExistingConcernItems();

        log.info("Existing concerns for similar markets: " + alreadyConcernMarkets.size());

        int totalMarketsScanned = 0;
        int flaggedMarkets = 0;
        int validTargets = 0;
        int rejectedByValue = 0;

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            totalMarketsScanned++;

            if (alreadyConcernMarkets.contains(market)) {
                continue;
            }

            // Must be flagged as AI core target
            if (!market.getMemoryWithoutUpdate().getBoolean(
                    DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG)) {
                continue;
            }

            flaggedMarkets++;

            log.info(String.format("  Found flagged AI core market: %s (%s)",
                    market.getName(), market.getFaction().getDisplayName()));

            // Must be valid target for raids/invasions
            boolean canUse = InvasionFleetManager.getManager().isValidInvasionOrRaidTarget(
                    ai.getFaction(), null, market, null, false);

            if (!canUse) {
                log.info(String.format("    REJECTED: %s - not a valid invasion/raid target", market.getName()));
                continue;
            }

            // Get AI core strategic value
            float coreValue = market.getMemoryWithoutUpdate().getFloat(
                    DraconisAICoreTargetingMonitor.AI_CORE_VALUE_FLAG);

            if (coreValue <= 0) {
                log.info(String.format("    REJECTED: %s - core value is 0", market.getName()));
                continue;
            }

            // Calculate priority considering defenses AND AI core value
            int size = market.getSize();
            float marketValue = getMarketValue(market);
            float sd = getSpaceDefenseValue(market);
            float gd = getGroundDefenseValue(market);

            log.info(String.format("    %s stats - Size: %d, MarketValue: %.0f, SpaceDef: %.0f, GroundDef: %.0f, CoreValue: %.1f",
                    market.getName(), size, marketValue, sd, gd, coreValue));

            // Base value calculation (like VulnerableEnemyTargetConcern)
            float valueMod = marketValue / (sd * 2 + gd) / SAIConstants.MARKET_VALUE_DIVISOR;
            valueMod *= (1 + 0.5f * size / 5);

            float baseValueMod = valueMod; // Store for logging

            // CRITICAL: Multiply by AI core value to prioritize these targets
            valueMod *= (1 + coreValue * AI_CORE_VALUE_MULT);

            log.info(String.format("    Priority calculation: base=%.2f, after AI core mult=%.2f (threshold: %.2f)",
                    baseValueMod, valueMod, SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE));

            if (valueMod < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) {
                log.info(String.format("    REJECTED: %s - priority %.2f below threshold %.2f",
                        market.getName(), valueMod, SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE));
                rejectedByValue++;
                continue;
            }

            validTargets++;
            targetsSorted.add(new Pair<>(market, valueMod));
            log.info(String.format("    ACCEPTED: %s - priority %.2f", market.getName(), valueMod));
        }

        log.info(String.format("Scan complete: %d markets scanned, %d flagged with AI cores, %d valid targets, %d rejected by value",
                totalMarketsScanned, flaggedMarkets, validTargets, rejectedByValue));

        if (targetsSorted.isEmpty()) {
            log.info("No valid AI core targets found - concern generation FAILED");
            return false;
        }

        // Sort by priority
        targetsSorted.sort((a, b) -> Float.compare(b.two, a.two));

        log.info("Top targets by priority:");
        for (int i = 0; i < Math.min(5, targetsSorted.size()); i++) {
            Pair<MarketAPI, Float> target = targetsSorted.get(i);
            log.info(String.format("  %d. %s - priority: %.2f",
                    i + 1, target.one.getName(), target.two));
        }

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

            int alphaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisAICoreTargetingMonitor.AI_CORE_ALPHA_COUNT_FLAG);
            int betaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisAICoreTargetingMonitor.AI_CORE_BETA_COUNT_FLAG);
            int gammaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisAICoreTargetingMonitor.AI_CORE_GAMMA_COUNT_FLAG);

            priority.modifyFlat("aiCoreValue", goal.two,
                    StrategicAI.getString("statDefenseAdjustedValue", true));
            priority.modifyFlat("aiCoreBonus", coreValue * 10, "AI Core Strategic Value");

            log.info(String.format(">>> CONCERN GENERATED: Target selected: %s (%s)",
                    market.getName(), market.getFaction().getDisplayName()));
            log.info(String.format("    AI Cores: %d Alpha, %d Beta, %d Gamma (total value: %.1f)",
                    alphaCount, betaCount, gammaCount, coreValue));
            log.info(String.format("    Final priority: %.2f", priority.getModifiedValue()));
            log.info("=== Concern generation SUCCESS ===");
        }

        return market != null;
    }

    @Override
    public void update() {
        log.info("Updating AI core concern for market: " + market.getName());

        // Verify market still has AI cores
        if (!market.getMemoryWithoutUpdate().getBoolean(
                DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG)) {
            log.info("  Market " + market.getName() + " no longer flagged with AI cores - ENDING CONCERN");
            end();
            return;
        }

        // Check if still hostile
        if (!market.getFaction().isHostileTo(ai.getFaction())) {
            log.info("  Market " + market.getName() + " is no longer hostile - ENDING CONCERN");
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

        float oldPriority = priority.getModifiedValue();

        priority.modifyFlat("aiCoreValue", valueMod,
                StrategicAI.getString("statDefenseAdjustedValue", true));
        priority.modifyFlat("aiCoreBonus", coreValue * 10, "AI Core Strategic Value");

        float newPriority = priority.getModifiedValue();

        log.info(String.format("  Priority updated: %.2f -> %.2f (core value: %.1f)",
                oldPriority, newPriority, coreValue));

        super.update();
    }

    @Override
    public void modifyActionPriority(StrategicAction action) {
        // Boost priority for raids and invasions against AI core targets
        if (action.getDef().hasTag(SAIConstants.TAG_MILITARY)) {
            float oldPriority = action.getPriority().getModifiedValue();
            action.getPriority().modifyMult("aiCoreTarget", 1.5f, "High-Value AI Core Target");
            float newPriority = action.getPriority().getModifiedValue();

            log.info(String.format("Boosting %s action priority for AI core target %s: %.2f -> %.2f",
                    action.getName(), market.getName(), oldPriority, newPriority));
        }
    }

    @Override
    public boolean isValid() {
        boolean valid = market != null &&
                market.getFaction().isHostileTo(ai.getFaction()) &&
                market.getMemoryWithoutUpdate().getBoolean(
                        DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG);

        if (!valid) {
            log.info("Concern validity check FAILED for " + (market != null ? market.getName() : "null market"));
            if (market != null) {
                log.info(String.format("  - Hostile: %s, Flagged: %s",
                        market.getFaction().isHostileTo(ai.getFaction()),
                        market.getMemoryWithoutUpdate().getBoolean(
                                DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG)));
            }
        }

        return valid;
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof DraconisAICoreTargetConcern) {
            boolean same = otherConcern.getMarket() == this.market;
            if (same) {
                log.info("Duplicate concern detected for market: " + market.getName());
            }
            return same;
        }
        return false;
    }

    @Override
    public void end() {
        log.info("*** AI Core Concern ENDING for market: " + (market != null ? market.getName() : "null") + " ***");
        super.end();
    }

    @Override
    public void advance(float days) {
        // Log periodically during long-running concerns
        super.advance(days);
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