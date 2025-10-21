package levianeer.draconis.data.campaign.ai.concerns;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.concern.MarketRelatedConcern;
import exerelin.campaign.ai.concern.StrategicConcern;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.Set;

/**
 * Strategic AI concern for the single high-value AI core target
 * Extends MarketRelatedConcern for proper market handling
 */
public class DraconisHighValueTargetConcern extends MarketRelatedConcern {

    private static final Logger log = Global.getLogger(DraconisHighValueTargetConcern.class);

    // Priority calculation constants
    public static final float CORE_PRIORITY_MULT = 2.5f;  // Multiplier for AI core value
    public static final float BASE_VALUE_REDUCTION = 0.1f;  // Reduce market value contribution

    @Override
    public boolean generate() {
        log.info("=== Draconis High-Value Target Concern: Starting generation ===");

        Set alreadyConcernMarkets = getExistingConcernItems();

        // Find the market marked as high-value target
        for (MarketAPI targetMarket : Global.getSector().getEconomy().getMarketsCopy()) {
            // Skip if already have a concern for this market
            if (alreadyConcernMarkets.contains(targetMarket)) {
                continue;
            }

            // Must be flagged as high-value target
            if (!targetMarket.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG)) {
                continue;
            }

            log.info("Found flagged high-value target: " + targetMarket.getName());

            // Verify it's a valid target for raids (custom validation)
            boolean canRaid = targetMarket.isInEconomy()
                    && !targetMarket.isHidden()
                    && targetMarket.getSize() >= 3
                    && targetMarket.getPrimaryEntity() != null;

            if (!canRaid) {
                log.info("  REJECTED: not a valid raid target");
                continue;
            }

            // Verify still hostile
            if (!targetMarket.getFaction().isHostileTo(ai.getFaction())) {
                log.info("  REJECTED: not hostile");
                continue;
            }

            // This is our target
            market = targetMarket;

            // Get AI core data
            float coreValue = market.getMemoryWithoutUpdate().getFloat(
                    DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);

            int alphaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisSingleTargetScanner.TARGET_ALPHA_COUNT_FLAG);
            int betaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisSingleTargetScanner.TARGET_BETA_COUNT_FLAG);
            int gammaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisSingleTargetScanner.TARGET_GAMMA_COUNT_FLAG);

            // Calculate priority - AI cores are the primary driver
            int size = market.getSize();
            float marketValue = getMarketValue(market);
            float sd = getSpaceDefenseValue(market);
            float gd = getGroundDefenseValue(market);

            // Small base value from market characteristics
            float baseValue = marketValue / (sd * 2 + gd) / SAIConstants.MARKET_VALUE_DIVISOR;
            baseValue *= (1 + 0.5f * size / 5);
            baseValue *= BASE_VALUE_REDUCTION;  // Reduce base contribution significantly

            // Primary priority comes from AI cores
            float corePriority = coreValue * CORE_PRIORITY_MULT;

            log.info(String.format("  Base market value: %.2f", baseValue));
            log.info(String.format("  AI core priority: %.2f (from %d cores, value %.1f)",
                    corePriority, alphaCount + betaCount + gammaCount, coreValue));

            // Set priority
            priority.modifyFlat("baseValue", baseValue, "Market Value");
            priority.modifyFlat("aiCores", corePriority, "AI Core Strategic Value");

            log.info(String.format(">>> CONCERN GENERATED for %s (%s)",
                    market.getName(), market.getFaction().getDisplayName()));
            log.info(String.format("    AI Cores: %d Alpha, %d Beta, %d Gamma (value: %.1f)",
                    alphaCount, betaCount, gammaCount, coreValue));
            log.info(String.format("    Total priority: %.2f", priority.getModifiedValue()));

            return true;
        }

        log.info("No high-value target found - concern generation FAILED");
        return false;
    }

    @Override
    public boolean isValid() {
        boolean valid = market != null &&
                market.getFaction().isHostileTo(ai.getFaction()) &&
                market.getMemoryWithoutUpdate().getBoolean(
                        DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG);

        if (!valid && market != null) {
            log.info("Concern invalid for " + market.getName());
        }

        return valid;
    }

    @Override
    public void update() {
        log.info("Updating high-value target concern for: " +
                (market != null ? market.getName() : "null"));

        if (market == null) {
            end();
            return;
        }

        // Check if still marked as target
        if (!market.getMemoryWithoutUpdate().getBoolean(
                DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG)) {
            log.info("  Market no longer marked - ENDING");
            end();
            return;
        }

        // Check if still hostile
        if (!market.getFaction().isHostileTo(ai.getFaction())) {
            log.info("  Market no longer hostile - ENDING");
            end();
            return;
        }

        // Recalculate priority
        float coreValue = market.getMemoryWithoutUpdate().getFloat(
                DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);
        int size = market.getSize();
        float marketValue = getMarketValue(market);
        float sd = getSpaceDefenseValue(market);
        float gd = getGroundDefenseValue(market);

        // Small base value from market characteristics
        float baseValue = marketValue / (sd * 2 + gd) / SAIConstants.MARKET_VALUE_DIVISOR;
        baseValue *= (1 + 0.5f * size / 5);
        baseValue *= BASE_VALUE_REDUCTION;

        // Primary priority comes from AI cores
        float corePriority = coreValue * CORE_PRIORITY_MULT;

        priority.modifyFlat("baseValue", baseValue, "Market Value");
        priority.modifyFlat("aiCores", corePriority, "AI Core Strategic Value");

        log.info(String.format("  Updated priority: %.2f", priority.getModifiedValue()));

        super.update();
    }

    @Override
    public void reapplyPriorityModifiers() {
        super.reapplyPriorityModifiers();

        if (market == null) return;

        float coreValue = market.getMemoryWithoutUpdate().getFloat(
                DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);
        int size = market.getSize();
        float marketValue = getMarketValue(market);
        float sd = getSpaceDefenseValue(market);
        float gd = getGroundDefenseValue(market);

        // Small base value from market characteristics
        float baseValue = marketValue / (sd * 2 + gd) / SAIConstants.MARKET_VALUE_DIVISOR;
        baseValue *= (1 + 0.5f * size / 5);
        baseValue *= BASE_VALUE_REDUCTION;

        // Primary priority comes from AI cores
        float corePriority = coreValue * CORE_PRIORITY_MULT;

        priority.modifyFlat("baseValue", baseValue, "Market Value");
        priority.modifyFlat("aiCores", corePriority, "AI Core Strategic Value");
    }

    @Override
    public void modifyActionPriority(StrategicAction action) {
        // Boost priority for military actions against AI core targets
        if (action.getDef().hasTag(SAIConstants.TAG_MILITARY)) {
            float oldPriority = action.getPriority().getModifiedValue();
            action.getPriority().modifyMult("aiCoreTarget", 1.5f, "High-Value AI Core Target");
            float newPriority = action.getPriority().getModifiedValue();

            log.info(String.format("Boosting %s action priority for %s: %.2f -> %.2f",
                    action.getName(), market.getName(), oldPriority, newPriority));
        }
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof DraconisHighValueTargetConcern) {
            boolean same = otherConcern.getMarket() == this.market;
            if (same) {
                log.info("Duplicate concern detected for: " + market.getName());
            }
            return same;
        }
        return false;
    }

    @Override
    public void end() {
        log.info("*** High-Value Target Concern ENDING for: " +
                (market != null ? market.getName() : "null") + " ***");
        super.end();
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        if (market == null) {
            return tooltip.addPara("No target selected.", pad);
        }

        Color h = Misc.getHighlightColor();

        float coreValue = market.getMemoryWithoutUpdate().getFloat(
                DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);
        int alphaCount = market.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_ALPHA_COUNT_FLAG);
        int betaCount = market.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_BETA_COUNT_FLAG);
        int gammaCount = market.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_GAMMA_COUNT_FLAG);
        int totalCores = alphaCount + betaCount + gammaCount;

        String str = String.format(
                "Intelligence confirms %d AI cores at %s (Alpha: %d, Beta: %d, Gamma: %d). " +
                        "Strategic value: %.0f. This target is designated as high priority for acquisition operations.",
                totalCores, market.getName(), alphaCount, betaCount, gammaCount, coreValue
        );

        return tooltip.addPara(str, pad, h,
                String.valueOf(totalCores),
                market.getName(),
                String.valueOf(alphaCount),
                String.valueOf(betaCount),
                String.valueOf(gammaCount),
                String.format("%.0f", coreValue)
        );
    }
}