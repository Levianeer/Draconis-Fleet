package levianeer.draconis.data.campaign.intel.aicore.theft;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;

import java.util.ArrayList;
import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Handles AI core theft during Draconis raids on any colony
 */
public class DraconisAICoreTheftListener {

    /**
     * Main entry point - checks for AI cores and steals them
     */
    public static void checkAndStealAICores(MarketAPI raidedMarket, boolean isPlayerMarket, String actionType) {
        if (raidedMarket == null) {
            Global.getLogger(DraconisAICoreTheftListener.class).warn("Attempted AI core theft on null market");
            return;
        }

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Draconis " + actionType + " " + raidedMarket.getName() +
                        " (" + raidedMarket.getFactionId() + ")"
        );

        List<String> stolenCores = new ArrayList<>();
        List<Industry> industries = raidedMarket.getIndustries();

        if (industries == null || industries.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).info("No industries on market");
            return;
        }

        // First pass: Steal actual installed AI cores
        for (Industry industry : industries) {
            if (industry == null) continue;

            String coreId = industry.getAICoreId();
            if (coreId != null && !coreId.isEmpty()) {
                stolenCores.add(coreId);
                industry.setAICoreId(null);

                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "Stole " + coreId + " from " + industry.getCurrentName()
                );
            }
        }

        // Fallback: Generate cores if none found and faction should have them
        if (stolenCores.isEmpty() && DraconisAICoreConfig.preferActualCores()) {
            stolenCores = generateFallbackCores(raidedMarket);

            if (!stolenCores.isEmpty()) {
                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "Generated " + stolenCores.size() + " fallback cores from " +
                                raidedMarket.getFactionId() + " facilities"
                );
            }
        }

        // Install stolen cores on Draconis industries
        if (!stolenCores.isEmpty()) {
            int installed = installStolenCores(stolenCores, raidedMarket, isPlayerMarket);

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "AI Core Theft Summary: " + stolenCores.size() + " stolen, " +
                            installed + " installed on Draconis facilities"
            );
        } else {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "No AI cores found or generated"
            );
        }
    }

    /**
     * Generates fallback AI cores based on faction and market characteristics
     */
    private static List<String> generateFallbackCores(MarketAPI market) {
        List<String> cores = new ArrayList<>();

        String factionId = market.getFactionId();
        int marketSize = market.getSize();

        DraconisAICoreConfig.FactionCoreChances chances =
                DraconisAICoreConfig.getFactionCoreChances(factionId);

        // Check if market is large enough
        if (marketSize < chances.minMarketSize) {
            return cores; // Empty list
        }

        int maxCores = DraconisAICoreConfig.getMaxCoresPerRaid();
        int coresGenerated = 0;

        // Try to generate each type of core
        // Alpha cores (rarest)
        if (chances.alphaChance > 0 && Math.random() < chances.alphaChance && coresGenerated < maxCores) {
            cores.add(Commodities.ALPHA_CORE);
            coresGenerated++;
        }

        // Beta cores (uncommon)
        if (chances.betaChance > 0 && Math.random() < chances.betaChance && coresGenerated < maxCores) {
            cores.add(Commodities.BETA_CORE);
            coresGenerated++;
        }

        // Gamma cores (most common)
        if (chances.gammaChance > 0 && Math.random() < chances.gammaChance && coresGenerated < maxCores) {
            cores.add(Commodities.GAMMA_CORE);
            coresGenerated++;
        }

        // Potentially generate a second gamma if chances are high
        if (chances.gammaChance > 0.4f && Math.random() < (chances.gammaChance * 0.5f) && coresGenerated < maxCores) {
            cores.add(Commodities.GAMMA_CORE);
        }
        return cores;
    }

    /**
     * Installs stolen AI cores on random Draconis industries
     * @return Number of cores successfully installed
     */
    private static int installStolenCores(List<String> stolenCores, MarketAPI raidedMarket,
                                          boolean isPlayerMarket) {
        // Find all Draconis markets with industries that could use AI cores
        List<Industry> availableIndustries = findAvailableDraconisIndustries();

        if (availableIndustries.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).warn(
                    "No available Draconis industries to install " + stolenCores.size() + " stolen cores! " +
                            "Cores will be lost. Consider building more Draconis facilities."
            );

            // Send message to player about lost opportunity
            if (isPlayerMarket) {
                Global.getSector().getCampaignUI().addMessage(
                        "Intelligence reports: Draconis forces stole AI cores from " + raidedMarket.getName() +
                                " but had no facilities available to utilize them. The technology was wasted.",
                        Misc.getTextColor()
                );
            }

            return 0;
        }

        // Install each stolen core
        int coresInstalled = 0;
        for (String coreId : stolenCores) {
            if (availableIndustries.isEmpty()) {
                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "Ran out of available industries. " + (stolenCores.size() - coresInstalled) +
                                " cores could not be installed"
                );
                break;
            }

            Industry targetIndustry = pickTargetIndustry(availableIndustries);

            if (targetIndustry != null && tryInstallCore(targetIndustry, coreId)) {
                availableIndustries.remove(targetIndustry);
                coresInstalled++;

                // Show message to player
                sendTheftMessage(coreId, raidedMarket, targetIndustry.getMarket(), isPlayerMarket);
            }
        }

        return coresInstalled;
    }

    /**
     * Finds all Draconis industries that can accept AI cores
     */
    private static List<Industry> findAvailableDraconisIndustries() {
        List<Industry> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            for (Industry industry : market.getIndustries()) {
                // Skip if already has an AI core
                if (industry.getAICoreId() != null && !industry.getAICoreId().isEmpty()) continue;

                // Only consider functional industries
                if (!industry.isFunctional()) continue;

                available.add(industry);
            }
        }

        return available;
    }

    /**
     * Picks a target industry weighted by market size if configured
     */
    private static Industry pickTargetIndustry(List<Industry> industries) {
        if (industries.isEmpty()) return null;

        if (!DraconisAICoreConfig.preferLargeMarkets()) {
            // Random selection
            return industries.get((int)(Math.random() * industries.size()));
        }

        // Weighted selection by market size
        WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
        float sizeWeight = DraconisAICoreConfig.getMarketSizeWeight();

        for (Industry ind : industries) {
            float weight = (float) Math.pow(ind.getMarket().getSize(), sizeWeight);
            picker.add(ind, weight);
        }

        return picker.pick();
    }

    /**
     * Attempts to install a core on an industry
     * @return true if successful
     */
    private static boolean tryInstallCore(Industry industry, String coreId) {
        String oldCore = industry.getAICoreId();
        industry.setAICoreId(coreId);

        // Verify installation
        if (coreId.equals(industry.getAICoreId())) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Installed " + coreId + " on " + industry.getCurrentName() +
                            " at " + industry.getMarket().getName()
            );
            return true;
        }

        // Installation failed - restore old core
        industry.setAICoreId(oldCore);
        Global.getLogger(DraconisAICoreTheftListener.class).warn(
                "Failed to install " + coreId + " on " + industry.getCurrentName()
        );
        return false;
    }

    /**
     * Sends a message to the player about AI core theft
     */
    private static void sendTheftMessage(String coreId, MarketAPI stolenFrom,
                                         MarketAPI installedAt, boolean isPlayerMarket) {
        String coreName = getCoreDisplayName(coreId);

        if (isPlayerMarket) {
            // Direct message for player colonies
            Global.getSector().getCampaignUI().addMessage(
                    "Intelligence reports: " + coreName + " stolen from " + stolenFrom.getName() +
                            " has been detected in use at " + installedAt.getName(),
                    Misc.getNegativeHighlightColor()
            );
        } else {
            // Indirect intel report for other factions
            if (shouldPlayerKnowAboutTheft(stolenFrom)) {
                Global.getSector().getCampaignUI().addMessage(
                        "Intelligence reports: Draconis Alliance raided " + stolenFrom.getName() +
                                " (" + stolenFrom.getFaction().getDisplayName() +
                                ") and may have acquired advanced technology",
                        Misc.getTextColor()
                );
            }
        }
    }

    /**
     * Determines if the player should know about AI core theft from other factions
     */
    private static boolean shouldPlayerKnowAboutTheft(MarketAPI raidedMarket) {
        // Player always knows if market is in same system as their colonies
        for (MarketAPI playerMarket : Misc.getPlayerMarkets(false)) {
            if (playerMarket.getStarSystem() == raidedMarket.getStarSystem()) {
                return true;
            }
        }

        // 30% chance otherwise (intel network)
        return Math.random() < 0.3f;
    }

    /**
     * Gets friendly display name for AI core
     */
    private static String getCoreDisplayName(String coreId) {
        return switch (coreId) {
            case Commodities.ALPHA_CORE -> "Alpha Core";
            case Commodities.BETA_CORE -> "Beta Core";
            case Commodities.GAMMA_CORE -> "Gamma Core";
            default -> "AI Core";
        };
    }
}