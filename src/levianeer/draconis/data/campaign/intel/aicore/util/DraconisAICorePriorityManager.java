package levianeer.draconis.data.campaign.intel.aicore.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;

import org.apache.log4j.Logger;

import java.util.List;

/**
 * Centralized priority system for AI core installation
 * Manages both industry-level priorities and administrator assignments
 */
public class DraconisAICorePriorityManager {

    private static final Logger log = Global.getLogger(DraconisAICorePriorityManager.class);

    /**
     * Get priority value for core types (higher = more valuable)
     */
    public static int getCorePriority(String coreId) {
        return switch (coreId) {
            case Commodities.ALPHA_CORE -> 3;
            case Commodities.BETA_CORE -> 2;
            case Commodities.GAMMA_CORE -> 1;
            default -> 0;
        };
    }

    /**
     * Check if a core can be upgraded by installing a better core
     *
     * @param currentCoreId Currently installed core (null = no core)
     * @param newCoreId Core being installed
     * @return true if newCore is better than currentCore
     */
    public static boolean canUpgradeCore(String currentCoreId, String newCoreId) {
        if (currentCoreId == null || currentCoreId.isEmpty()) return true; // Empty slot
        return getCorePriority(newCoreId) > getCorePriority(currentCoreId);
    }

    /**
     * Pick target industry based on priority with market size weighting
     * Considers both empty slots and upgrade opportunities
     * Uses DETERMINISTIC selection - always picks highest priority industry
     *
     * @param emptyIndustries List of industries without AI cores
     * @param upgradeableIndustries List of industries with lower-tier cores that can be displaced
     * @param coreId AI core type to install
     * @return Selected industry, or null if no suitable target found
     */
    public static Industry pickTargetIndustryByPriority(List<Industry> emptyIndustries,
                                                        List<Industry> upgradeableIndustries,
                                                        String coreId) {
        if ((emptyIndustries == null || emptyIndustries.isEmpty()) &&
            (upgradeableIndustries == null || upgradeableIndustries.isEmpty())) {
            log.debug("No available industries for " + getCoreDisplayName(coreId));
            return null;
        }

        float sizeWeight = DraconisAICoreConfig.getMarketSizeWeight();
        Industry bestIndustry = null;
        float bestWeight = -1f;
        boolean bestIsUpgrade = false;

        if (log.isDebugEnabled()) {
            log.debug("=========================================");
            log.debug("Selecting target for " + getCoreDisplayName(coreId));
            log.debug("Available empty slots: " + (emptyIndustries != null ? emptyIndustries.size() : 0));
            log.debug("Available upgrades: " + (upgradeableIndustries != null ? upgradeableIndustries.size() : 0));
        }

        if (emptyIndustries != null) {
            for (Industry industry : emptyIndustries) {
                float basePriority = getIndustryPriority(industry, coreId);
                float marketSizeBonus = (float) Math.pow(industry.getMarket().getSize(), sizeWeight * 0.5f);
                float weight = basePriority * marketSizeBonus;

                if (log.isDebugEnabled()) {
                    log.debug(String.format("  [EMPTY] %s at %s - Priority: %.1f, Market: %d, Bonus: %.2f, Weight: %.2f",
                        industry.getCurrentName(),
                        industry.getMarket().getName(),
                        basePriority,
                        industry.getMarket().getSize(),
                        marketSizeBonus,
                        weight));
                }

                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestIndustry = industry;
                    bestIsUpgrade = false;
                }
            }
        }

        // Upgradeable industries get 80% weight - prefers empty slots but still upgrades a
        // high-priority industry.
        if (upgradeableIndustries != null) {
            for (Industry industry : upgradeableIndustries) {
                float basePriority = getIndustryPriority(industry, coreId);
                float marketSizeBonus = (float) Math.pow(industry.getMarket().getSize(), sizeWeight * 0.5f);
                float weight = basePriority * marketSizeBonus * 0.8f;

                if (log.isDebugEnabled()) {
                    String currentCore = industry.getAICoreId();
                    log.debug(String.format("  [UPGRADE] %s at %s (has %s) - Priority: %.1f, Market: %d, Bonus: %.2f, Weight: %.2f",
                        industry.getCurrentName(),
                        industry.getMarket().getName(),
                        getCoreDisplayName(currentCore),
                        basePriority,
                        industry.getMarket().getSize(),
                        marketSizeBonus,
                        weight));
                }

                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestIndustry = industry;
                    bestIsUpgrade = true;
                }
            }
        }

        if (bestIndustry != null) {
            log.debug(String.format("Selected %s at %s for %s (%s)",
                    bestIndustry.getCurrentName(),
                    bestIndustry.getMarket().getName(),
                    getCoreDisplayName(coreId),
                    bestIsUpgrade ? "upgrade" : "empty slot"));
        } else {
            log.warn("No suitable industry found for " + getCoreDisplayName(coreId));
        }

        return bestIndustry;
    }

    /**
     * Legacy method for backward compatibility
     * Only considers empty industries (no upgrades)
     *
     * @param industries List of available industries
     * @param coreId AI core type to install
     * @return Selected industry, or null if list is empty
     */
    public static Industry pickTargetIndustryByPriority(List<Industry> industries, String coreId) {
        return pickTargetIndustryByPriority(industries, null, coreId);
    }

    /**
     * Pick target market for administrator installation with market size weighting
     * Prefers larger markets when config enabled
     *
     * @param markets List of available markets
     * @return Selected market, or null if list is empty
     */
    public static MarketAPI pickTargetAdminMarket(List<MarketAPI> markets) {
        if (markets == null || markets.isEmpty()) return null;

        if (!DraconisAICoreConfig.preferLargeMarkets()) {
            return markets.get((int)(Math.random() * markets.size()));
        }

        WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
        float sizeWeight = DraconisAICoreConfig.getMarketSizeWeight();

        for (MarketAPI market : markets) {
            float weight = (float) Math.pow(market.getSize(), sizeWeight);
            picker.add(market, weight);
        }

        return picker.pick();
    }

    /**
     * Get priority weight for an industry
     * <p>
     * UNIFIED PRIORITY ORDER (applies to ALL core types):
     * 1. Administrator (11.0) - Market administrator (Alpha cores ONLY - handled separately)
     * 2. Orbital Works / Heavy Industry (10.0) - Ship/Equipment production
     * 3. Population & Infrastructure (9.0) - Population growth
     * 4. High Command (8.5) - Military command
     * 5. Commerce (8.0) - Trade (basic; active on all market sizes, highest marginal gain)
     * 6. Fuel Production (7.5) - Critical resource
     * 7. Refining (7.0) - Resource processing
     * 8. Light Industry (6.5) - Manufacturing
     * 9. Waystation (6.0) - Strategic infrastructure
     * 10. Mining (5.5) - Resource extraction
     * 11. Farming (5.0) - Food production
     * 12. Aquaculture (4.5) - Alternative food
     * 13. Megaport (4.0) - Large-market trade hub (lower marginal gain; already highly efficient)
     * 14. Everything else (3.0) - Default priority
     * <p>
     * NOTE: This priority order is the SAME for all core types.
     * Lower-tier cores should be displaced from high-priority industries by higher-tier cores.
     *
     * @param industry Industry to evaluate
     * @param coreId AI core type being installed (used for logging only, priority is the same)
     * @return Priority weight (higher = more important industry)
     */
    public static float getIndustryPriority(Industry industry, String coreId) {
        String industryId = industry.getId().toLowerCase();

        if (industryId.contains("orbitalworks")) return 10.0f;
        if (industryId.contains("heavyindustry")) return 10.0f;

        if (industryId.contains("population")) return 9.0f;

        if (industryId.contains("xlii_highcommand")) return 8.5f;
        if (industryId.contains("highcommand")) return 8.5f;
        if (industryId.contains("militarybase")) return 8.3f; // Slightly lower than High Command

        if (industryId.contains("commerce")) return 8.0f;

        if (industryId.contains("fuelprod")) return 7.5f;

        if (industryId.contains("refining")) return 7.0f;

        if (industryId.contains("lightindustry")) return 6.5f;

        if (industryId.contains("waystation")) return 6.0f;

        if (industryId.contains("mining")) return 5.5f;

        if (industryId.contains("farming")) return 5.0f;

        if (industryId.contains("aquaculture")) return 4.5f;

        if (industryId.contains("megaport")) return 4.0f;

        return 3.0f;
    }

    /**
     * Get human-readable core display name
     *
     * @param coreId AI core commodity ID
     * @return Display name for the core
     */
    public static String getCoreDisplayName(String coreId) {
        return switch (coreId) {
            case Commodities.ALPHA_CORE -> "Alpha Core";
            case Commodities.BETA_CORE -> "Beta Core";
            case Commodities.GAMMA_CORE -> "Gamma Core";
            default -> "AI Core";
        };
    }

    /**
     * Debug logging for industry priority selection.
     *
     * @param industry Industry to analyze
     * @param coreId Core type being considered
     */
    public static void logIndustryPriority(Industry industry, String coreId) {
        float priority = getIndustryPriority(industry, coreId);
        float marketSize = industry.getMarket().getSize();
        float sizeBonus = (float) Math.pow(marketSize, DraconisAICoreConfig.getMarketSizeWeight() * 0.5f);
        float totalWeight = priority * sizeBonus;

        if (log.isDebugEnabled()) {
            log.debug(String.format("Industry: %s (%s) - Base Priority: %.1f, Market Size: %.0f, Size Bonus: %.2f, Total Weight: %.2f",
                industry.getCurrentName(),
                industry.getMarket().getName(),
                priority,
                marketSize,
                sizeBonus,
                totalWeight));
        }
    }

    /**
     * Grants HYPERCOGNITION to the market administrator instead of replacing them, so player
     * contacts and quest references to that admin aren't broken.
     *
     * @param market Target market whose admin receives the skill
     * @param coreId AI core type (only Alpha cores grant HYPERCOGNITION)
     * @param factionId Faction ID (unused, kept for API compatibility)
     * @return true if skill granted successfully, false otherwise
     */
    public static boolean installAICoreAdmin(MarketAPI market, String coreId, String factionId) {
        try {
            PersonAPI admin = market.getAdmin();

            if (admin == null) {
                log.warn("No administrator at " + market.getName() + " to grant HYPERCOGNITION");
                return false;
            }

            if (Commodities.ALPHA_CORE.equals(coreId)) {
                if (admin.getStats().getSkillLevel(Skills.HYPERCOGNITION) <= 0) {
                    admin.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);
                    log.debug("Granted HYPERCOGNITION to " + admin.getNameString() +
                        " at " + market.getName() + " (Alpha Core integration)");
                } else {
                    log.debug(admin.getNameString() + " at " + market.getName() +
                        " already has HYPERCOGNITION - skipping");
                }
                return true;
            } else {
                log.debug("Non-Alpha core (" + getCoreDisplayName(coreId) +
                    ") - HYPERCOGNITION not granted at " + market.getName());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to grant HYPERCOGNITION at " + market.getName(), e);
            return false;
        }
    }
}