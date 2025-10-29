package levianeer.draconis.data.campaign.intel.aicore.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;

import java.util.List;

/**
 * Centralized priority system for AI core installation
 * Manages both industry-level priorities and administrator assignments
 */
public class DraconisAICorePriorityManager {

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
            Global.getLogger(DraconisAICorePriorityManager.class).info(
                "No available industries for " + getCoreDisplayName(coreId)
            );
            return null;
        }

        float sizeWeight = DraconisAICoreConfig.getMarketSizeWeight();
        Industry bestIndustry = null;
        float bestWeight = -1f;
        boolean bestIsUpgrade = false;

        Global.getLogger(DraconisAICorePriorityManager.class).info(
            "========================================="
        );
        Global.getLogger(DraconisAICorePriorityManager.class).info(
            "Selecting target for " + getCoreDisplayName(coreId)
        );
        Global.getLogger(DraconisAICorePriorityManager.class).info(
            "Available empty slots: " + (emptyIndustries != null ? emptyIndustries.size() : 0)
        );
        Global.getLogger(DraconisAICorePriorityManager.class).info(
            "Available upgrades: " + (upgradeableIndustries != null ? upgradeableIndustries.size() : 0)
        );

        // Evaluate empty industries with full priority
        if (emptyIndustries != null) {
            for (Industry industry : emptyIndustries) {
                float basePriority = getIndustryPriority(industry, coreId);
                float marketSizeBonus = (float) Math.pow(industry.getMarket().getSize(), sizeWeight * 0.5f);
                float weight = basePriority * marketSizeBonus;

                Global.getLogger(DraconisAICorePriorityManager.class).info(
                    String.format("  [EMPTY] %s at %s - Priority: %.1f, Market: %d, Bonus: %.2f, Weight: %.2f",
                        industry.getCurrentName(),
                        industry.getMarket().getName(),
                        basePriority,
                        industry.getMarket().getSize(),
                        marketSizeBonus,
                        weight)
                );

                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestIndustry = industry;
                    bestIsUpgrade = false;
                }
            }
        }

        // Evaluate upgradeable industries with slightly reduced priority (80% weight)
        // This ensures we prefer empty slots, but will upgrade if the industry is high-priority enough
        if (upgradeableIndustries != null) {
            for (Industry industry : upgradeableIndustries) {
                float basePriority = getIndustryPriority(industry, coreId);
                float marketSizeBonus = (float) Math.pow(industry.getMarket().getSize(), sizeWeight * 0.5f);
                float weight = basePriority * marketSizeBonus * 0.8f; // 80% weight for upgrades

                String currentCore = industry.getAICoreId();
                Global.getLogger(DraconisAICorePriorityManager.class).info(
                    String.format("  [UPGRADE] %s at %s (has %s) - Priority: %.1f, Market: %d, Bonus: %.2f, Weight: %.2f",
                        industry.getCurrentName(),
                        industry.getMarket().getName(),
                        getCoreDisplayName(currentCore),
                        basePriority,
                        industry.getMarket().getSize(),
                        marketSizeBonus,
                        weight)
                );

                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestIndustry = industry;
                    bestIsUpgrade = true;
                }
            }
        }

        if (bestIndustry != null) {
            Global.getLogger(DraconisAICorePriorityManager.class).info(
                String.format(">>> SELECTED: %s at %s (Weight: %.2f, Type: %s)",
                    bestIndustry.getCurrentName(),
                    bestIndustry.getMarket().getName(),
                    bestWeight,
                    bestIsUpgrade ? "UPGRADE" : "EMPTY")
            );
        } else {
            Global.getLogger(DraconisAICorePriorityManager.class).warn(
                "No suitable industry found for " + getCoreDisplayName(coreId)
            );
        }
        Global.getLogger(DraconisAICorePriorityManager.class).info(
            "========================================="
        );

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
     *
     * UNIFIED PRIORITY ORDER (applies to ALL core types):
     * 1. Administrator (11.0) - Market administrator (Alpha cores ONLY - handled separately)
     * 2. Orbital Works / Heavy Industry (10.0) - Ship/Equipment production
     * 3. Population & Infrastructure (9.0) - Population growth
     * 4. High Command (8.5) - Military command
     * 5. Megaport (8.0) - Trade hub
     * 6. Fuel Production (7.5) - Critical resource
     * 7. Refining (7.0) - Resource processing
     * 8. Light Industry (6.5) - Manufacturing
     * 9. Waystation (6.0) - Strategic infrastructure
     * 10. Mining (5.5) - Resource extraction
     * 11. Farming (5.0) - Food production
     * 12. Aquaculture (4.5) - Alternative food
     * 13. Commerce (4.0) - Trade
     * 14. Everything else (3.0) - Default priority
     *
     * NOTE: This priority order is the SAME for all core types.
     * Lower-tier cores should be displaced from high-priority industries by higher-tier cores.
     *
     * @param industry Industry to evaluate
     * @param coreId AI core type being installed (used for logging only, priority is the same)
     * @return Priority weight (higher = more important industry)
     */
    public static float getIndustryPriority(Industry industry, String coreId) {
        String industryId = industry.getId().toLowerCase();

        // Tier 1: Production (Orbital Works / Heavy Industry)
        if (industryId.contains("orbitalworks")) return 10.0f;           // Orbital Works (ship production)
        if (industryId.contains("heavyindustry")) return 10.0f;          // Heavy Industry (equipment production)

        // Tier 2: Population & Infrastructure
        if (industryId.contains("population")) return 9.0f;              // Population & Infrastructure

        // Tier 3: High Command
        if (industryId.contains("xlii_highcommand")) return 8.5f;        // Draconis High Command
        if (industryId.contains("highcommand")) return 8.5f;             // High Command
        if (industryId.contains("militarybase")) return 8.3f;            // Military Base (slightly lower)

        // Tier 4: Megaport
        if (industryId.contains("megaport")) return 8.0f;                // Megaport

        // Tier 5: Critical Resources
        if (industryId.contains("fuelprod")) return 7.5f;                // Fuel Production

        // Tier 6: Resource Processing
        if (industryId.contains("refining")) return 7.0f;                // Refining

        // Tier 7: Manufacturing
        if (industryId.contains("lightindustry")) return 6.5f;           // Light Industry

        // Tier 8: Strategic Infrastructure
        if (industryId.contains("waystation")) return 6.0f;              // Waystation

        // Tier 9: Resource Extraction
        if (industryId.contains("mining")) return 5.5f;                  // Mining

        // Tier 10: Food Production
        if (industryId.contains("farming")) return 5.0f;                 // Farming

        // Tier 11: Alternative Food
        if (industryId.contains("aquaculture")) return 4.5f;             // Aquaculture

        // Tier 12: Trade
        if (industryId.contains("commerce")) return 4.0f;                // Commerce

        // Default: Everything else
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
     * Log industry priority analysis for debugging
     * Useful for understanding why certain industries were selected
     *
     * @param industry Industry to analyze
     * @param coreId Core type being considered
     */
    public static void logIndustryPriority(Industry industry, String coreId) {
        float priority = getIndustryPriority(industry, coreId);
        float marketSize = industry.getMarket().getSize();
        float sizeBonus = (float) Math.pow(marketSize, DraconisAICoreConfig.getMarketSizeWeight() * 0.5f);
        float totalWeight = priority * sizeBonus;

        Global.getLogger(DraconisAICorePriorityManager.class).info(
            String.format("Industry: %s (%s) - Base Priority: %.1f, Market Size: %.0f, Size Bonus: %.2f, Total Weight: %.2f",
                industry.getCurrentName(),
                industry.getMarket().getName(),
                priority,
                marketSize,
                sizeBonus,
                totalWeight)
        );
    }

    /**
     * Grant HYPERCOGNITION skill to market administrator (representing Alpha Core integration)
     * Much more player-friendly than replacing admins
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
                Global.getLogger(DraconisAICorePriorityManager.class).warn(
                    "No administrator at " + market.getName() + " to grant HYPERCOGNITION"
                );
                return false;
            }

            // Only Alpha cores grant HYPERCOGNITION
            if (Commodities.ALPHA_CORE.equals(coreId)) {
                // Check if admin already has the skill
                if (admin.getStats().getSkillLevel(Skills.HYPERCOGNITION) <= 0) {
                    admin.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);

                    Global.getLogger(DraconisAICorePriorityManager.class).info(
                        "Granted HYPERCOGNITION to " + admin.getNameString() +
                        " at " + market.getName() + " (Alpha Core integration)"
                    );
                } else {
                    Global.getLogger(DraconisAICorePriorityManager.class).info(
                        admin.getNameString() + " at " + market.getName() +
                        " already has HYPERCOGNITION - skipping"
                    );
                }
                return true;
            } else {
                Global.getLogger(DraconisAICorePriorityManager.class).info(
                    "Non-Alpha core (" + getCoreDisplayName(coreId) +
                    ") - HYPERCOGNITION not granted at " + market.getName()
                );
                return false;
            }

        } catch (Exception e) {
            Global.getLogger(DraconisAICorePriorityManager.class).error(
                "Failed to grant HYPERCOGNITION at " + market.getName(), e
            );
            return false;
        }
    }
}