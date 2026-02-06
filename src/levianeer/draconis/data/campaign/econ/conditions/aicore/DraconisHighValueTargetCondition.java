package levianeer.draconis.data.campaign.econ.conditions.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.events.crisis.DraconisFleetHostileActivityFactor;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Applied to the single highest-value AI core target marked by Draconis intelligence
 * Self-validates periodically and removes itself if conditions are no longer met
 */
public class DraconisHighValueTargetCondition extends BaseMarketConditionPlugin {
    private static final Logger log = Global.getLogger(DraconisHighValueTargetCondition.class);

    private static final float STABILITY_PENALTY = 1f;
    private static final float GROUND_DEFENSE_PENALTY = -0.25f;

    // Validation interval - check every 7 days (same as scanner interval)
    private static final float CHECK_INTERVAL_DAYS = 7f;
    private static final int MIN_MARKET_SIZE = 4;
    private static final float FRIENDLY_THRESHOLD = 0.25f;

    private float daysSinceLastCheck = 0f;

    @Override
    public void apply(String id) {
        MarketAPI market = this.market;

        // Stability penalty from being marked as priority target
        market.getStability().modifyFlat(id, -STABILITY_PENALTY, getName());

        // Ground defense reduction from intelligence infiltration
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(id, 1f + GROUND_DEFENSE_PENALTY, getName());
    }

    @Override
    public void unapply(String id) {
        market.getStability().unmodifyFlat(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (market == null) {
            log.warn("Draconis: HighValueTargetCondition: market is null, cannot validate");
            return;
        }

        // Convert to days and track time
        float days = Global.getSector().getClock().convertToDays(amount);
        daysSinceLastCheck += days;

        // Only check periodically to avoid performance issues
        if (daysSinceLastCheck < CHECK_INTERVAL_DAYS) {
            return;
        }

        daysSinceLastCheck = 0f;

        // Run validation checks - remove condition if any fail
        String removalReason = validateCondition();
        if (removalReason != null) {
            log.info("Draconis: === REMOVING HIGH VALUE TARGET CONDITION ===");
            log.info("Draconis: Market: " + market.getName());
            log.info("Draconis: Reason: " + removalReason);
            removeCondition();
        }
    }

    /**
     * Validates whether this condition should still exist
     * @return null if valid, otherwise a string describing why it should be removed
     */
    private String validateCondition() {
        // Check 1: Draconis faction must exist
        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        if (draconisFaction == null) {
            return "Draconis faction does not exist";
        }

        // Check 2: Draconis faction must not be defeated (have at least one market)
        if (isDraconisFactionDefeated(draconisFaction)) {
            return "Draconis faction has been defeated (no markets remaining)";
        }

        // Check 3: Player markets must not have defeated crisis
        if (market.isPlayerOwned() && DraconisFleetHostileActivityFactor.isPlayerDefeatedDraconisAttack()) {
            return "Player market - crisis defeated";
        }

        // Check 4: Market must still have AI cores
        int coreCount = countAICores();
        if (coreCount == 0) {
            return "Market no longer has AI cores";
        }

        // Check 5: Relationship must not be friendly
        float relationship = market.getFaction().getRelationship(DRACONIS);
        if (relationship >= FRIENDLY_THRESHOLD) {
            return "Relationship with Draconis is now friendly (" + String.format("%.2f", relationship) + ")";
        }

        // Check 6: Market must be valid
        if (market.isHidden() || !market.isInEconomy()) {
            return "Market is hidden or not in economy";
        }

        // Check 7: Market must meet minimum size requirement
        if (market.getSize() < MIN_MARKET_SIZE) {
            return "Market size too small (" + market.getSize() + " < " + MIN_MARKET_SIZE + ")";
        }

        // Check 8: Market must not be decivilized
        if (market.hasCondition(Conditions.DECIVILIZED)) {
            return "Market is decivilized";
        }

        // Check 9: Verify scanner flag still exists (sanity check)
        if (!market.getMemoryWithoutUpdate().getBoolean(DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG)) {
            return "Scanner flag removed (scanner found better target or market no longer valid)";
        }

        // All checks passed
        return null;
    }

    /**
     * Counts the number of AI cores in this market's industries
     */
    private int countAICores() {
        int count = 0;
        List<Industry> industries = market.getIndustries();

        if (industries == null) {
            return 0;
        }

        for (Industry industry : industries) {
            if (industry == null) continue;

            String coreId = industry.getAICoreId();
            if (coreId == null || coreId.isEmpty()) continue;

            // Count any type of AI core (Alpha, Beta, Gamma)
            if (Commodities.ALPHA_CORE.equals(coreId) ||
                Commodities.BETA_CORE.equals(coreId) ||
                Commodities.GAMMA_CORE.equals(coreId)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Checks if Draconis faction is functionally defeated (has no markets)
     */
    private boolean isDraconisFactionDefeated(FactionAPI faction) {
        if (faction == null) {
            return true;
        }

        // Check if faction has any markets remaining
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (DRACONIS.equals(market.getFactionId())) {
                return false; // Found at least one Draconis market
            }
        }

        return true; // No Draconis markets found - faction is defeated
    }

    /**
     * Removes this condition from the market and cleans up memory flags
     */
    private void removeCondition() {
        // Clear scanner memory flags
        market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG);
        market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);
        market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_ALPHA_COUNT_FLAG);
        market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_BETA_COUNT_FLAG);
        market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_GAMMA_COUNT_FLAG);

        // Remove the condition itself
        market.removeCondition("draconis_high_value_target");

        log.info("Draconis: Condition and flags removed successfully");
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();
        Color draconis = Global.getSector().getFaction(DRACONIS).getBaseUIColor();

        float opad = 10f;

        tooltip.addPara(
                "The Alliance Intelligence office has designated this colony as their highest-priority AI core acquisition target. " +
                        "Deep cover operatives have compromised security protocols and are actively preparing for extraction operations.",
                opad, draconis
        );

        tooltip.addPara(
                "Infiltration operations have degraded defensive readiness, reducing effective ground defenses by %s.",
                opad, n,
                String.format("%.0f%%", Math.abs(GROUND_DEFENSE_PENALTY * 100))
        );

        tooltip.addPara(
                "The population is aware of the increased threat level, causing elevated civil unrest and reducing stability by %s.",
                opad, n,
                String.format("-%d", (int)STABILITY_PENALTY)
        );
    }
}
