package levianeer.draconis.data.campaign.intel.aicore.theft;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;
import levianeer.draconis.data.campaign.intel.aicore.intel.DraconisAICoreTheftIntel;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICorePriorityManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

public class DraconisAICoreTheftListener {

    public static void checkAndStealAICores(MarketAPI raidedMarket, boolean isPlayerMarket, String actionType) {
        if (raidedMarket == null) {
            Global.getLogger(DraconisAICoreTheftListener.class).error(
                    "Attempted AI core theft on null market"
            );
            return;
        }

        // BULLETPROOF PROTECTION: Check if we already stole from this market today
        long currentDay = Global.getSector().getClock().getDay();
        String lastTheftKey = "$draconis_lastTheftDay";
        long lastTheftDay = raidedMarket.getMemoryWithoutUpdate().getLong(lastTheftKey);

        if (lastTheftDay == currentDay) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "!!! DUPLICATE THEFT ATTEMPT BLOCKED !!!"
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Already stole from " + raidedMarket.getName() + " today (day " + currentDay + ")"
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "=========================================="
            );
            return;
        }

        // Mark this market as stolen from today
        raidedMarket.getMemoryWithoutUpdate().set(lastTheftKey, currentDay);

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "=========================================="
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "=== AI CORE THEFT STARTING ==="
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Action: " + actionType
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Target: " + raidedMarket.getName() + " (" + raidedMarket.getFactionId() + ")"
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Player owned: " + isPlayerMarket
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Market size: " + raidedMarket.getSize()
        );

        List<String> stolenCores = new ArrayList<>();

        // Check for administrator with HYPERCOGNITION (representing Alpha Core integration)
        if (raidedMarket.getAdmin() != null &&
            raidedMarket.getAdmin().getStats().getSkillLevel(Skills.HYPERCOGNITION) > 0) {

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "  Checking Administrator: " + raidedMarket.getAdmin().getNameString()
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "    Has HYPERCOGNITION skill (Alpha Core integration detected)"
            );

            stolenCores.add(Commodities.ALPHA_CORE);

            // NOTE: We don't remove the admin or the skill - they keep their experience
            // This is much more player-friendly than losing trained admins

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "    >>> STOLEN: Alpha Core (admin keeps HYPERCOGNITION skill)"
            );
        }

        // Defensive copy to prevent ConcurrentModificationException
        List<Industry> industries = new ArrayList<>(raidedMarket.getIndustries());

        if (industries.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "No industries on market"
            );
        } else {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Scanning " + industries.size() + " industries for AI cores"
            );

            // Steal actual installed AI cores from industries
            for (Industry industry : industries) {
                if (industry == null) continue;

                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "  Checking industry: " + industry.getCurrentName()
                );

                String coreId = industry.getAICoreId();

                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "    AI Core: " + (coreId != null && !coreId.isEmpty() ? coreId : "none")
                );

                if (coreId != null && !coreId.isEmpty()) {
                    stolenCores.add(coreId);
                    industry.setAICoreId(null);

                    Global.getLogger(DraconisAICoreTheftListener.class).info(
                            "    >>> STOLEN: " + coreId + " from " + industry.getCurrentName()
                    );
                }
            }
        }

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Actual cores stolen: " + stolenCores.size()
        );

        // Fallback: Generate cores if none found
        if (stolenCores.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "No actual cores found - attempting fallback generation"
            );

            if (DraconisAICoreConfig.preferActualCores()) {
                stolenCores = generateFallbackCores(raidedMarket);

                if (!stolenCores.isEmpty()) {
                    Global.getLogger(DraconisAICoreTheftListener.class).info(
                            "Generated " + stolenCores.size() + " fallback cores"
                    );
                    for (String core : stolenCores) {
                        Global.getLogger(DraconisAICoreTheftListener.class).info(
                                "  - " + core
                        );
                    }
                } else {
                    Global.getLogger(DraconisAICoreTheftListener.class).info(
                            "Fallback generation produced no cores"
                    );
                }
            } else {
                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "Fallback generation disabled in config"
                );
            }
        }

        // Install stolen cores on Draconis industries
        if (!stolenCores.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Installing " + stolenCores.size() + " stolen cores on Draconis facilities"
            );

            int installed = installStolenCores(stolenCores, raidedMarket, isPlayerMarket, actionType);

            // Apply diplomatic strain (Nexerelin integration)
            if (installed > 0) {
                int alphaCount = 0;
                int betaCount = 0;
                int gammaCount = 0;

                for (String coreId : stolenCores) {
                    if (coreId.equals(Commodities.ALPHA_CORE)) alphaCount++;
                    else if (coreId.equals(Commodities.BETA_CORE)) betaCount++;
                    else if (coreId.equals(Commodities.GAMMA_CORE)) gammaCount++;
                }

                levianeer.draconis.data.campaign.intel.aicore.diplomacy.DraconisDiplomacyStrain.applyAICoreStrain(
                        raidedMarket.getFactionId(), alphaCount, betaCount, gammaCount
                );
            }

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "=== AI CORE THEFT COMPLETE ==="
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Total stolen: " + stolenCores.size()
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Successfully installed: " + installed
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "=========================================="
            );
        } else {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "=== AI CORE THEFT COMPLETE - NO CORES FOUND ==="
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "=========================================="
            );
        }
    }

    private static List<String> generateFallbackCores(MarketAPI market) {
        List<String> cores = new ArrayList<>();

        String factionId = market.getFactionId();
        int marketSize = market.getSize();

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Generating fallback cores for " + factionId + " market size " + marketSize
        );

        DraconisAICoreConfig.FactionCoreChances chances =
                DraconisAICoreConfig.getFactionCoreChances(factionId);

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Chances: Alpha=" + chances.alphaChance +
                        ", Beta=" + chances.betaChance +
                        ", Gamma=" + chances.gammaChance +
                        ", MinSize=" + chances.minMarketSize
        );

        if (marketSize < chances.minMarketSize) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Market too small for fallback cores (size " + marketSize +
                            " < min " + chances.minMarketSize + ")"
            );
            return cores;
        }

        int maxCores = DraconisAICoreConfig.getMaxCoresPerRaid();
        int coresGenerated = 0;

        if (chances.alphaChance > 0 && Math.random() < chances.alphaChance && coresGenerated < maxCores) {
            cores.add(Commodities.ALPHA_CORE);
            coresGenerated++;
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Generated Alpha Core"
            );
        }

        if (chances.betaChance > 0 && Math.random() < chances.betaChance && coresGenerated < maxCores) {
            cores.add(Commodities.BETA_CORE);
            coresGenerated++;
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Generated Beta Core"
            );
        }

        if (chances.gammaChance > 0 && Math.random() < chances.gammaChance && coresGenerated < maxCores) {
            cores.add(Commodities.GAMMA_CORE);
            coresGenerated++;
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Generated Gamma Core"
            );
        }

        if (chances.gammaChance > 0.4f && Math.random() < (chances.gammaChance * 0.5f) && coresGenerated < maxCores) {
            cores.add(Commodities.GAMMA_CORE);
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Generated additional Gamma Core"
            );
        }

        return cores;
    }

    private static int installStolenCores(List<String> stolenCores, MarketAPI raidedMarket,
                                          boolean isPlayerMarket, String actionType) {
        // Sort cores by priority (Alpha > Beta > Gamma)
        List<String> sortedCores = new ArrayList<>(stolenCores);
        sortedCores.sort((a, b) -> {
            int priorityA = DraconisAICorePriorityManager.getCorePriority(a);
            int priorityB = DraconisAICorePriorityManager.getCorePriority(b);
            return Integer.compare(priorityB, priorityA); // Descending order
        });

        List<MarketAPI> availableAdminMarkets = findAvailableDraconisAdminMarkets();
        List<Industry> availableIndustries = findAvailableDraconisIndustries();
        List<Industry> upgradeableIndustries = findUpgradeableDraconisIndustries();

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Found " + availableAdminMarkets.size() + " available Draconis markets for administrator installation"
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Found " + availableIndustries.size() + " empty Draconis industries"
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Found " + upgradeableIndustries.size() + " upgradeable Draconis industries"
        );

        if (availableAdminMarkets.isEmpty() && availableIndustries.isEmpty() && upgradeableIndustries.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).error(
                    "NO DRACONIS FACILITIES AVAILABLE! " + stolenCores.size() + " cores will be lost!"
            );

            if (isPlayerMarket) {
                Global.getSector().getCampaignUI().addMessage(
                        "Intelligence reports: Draconis forces stole AI cores from " + raidedMarket.getName() +
                                " but had no facilities available to utilize them. The technology was wasted.",
                        Misc.getTextColor()
                );
            }

            return 0;
        }

        int coresInstalled = 0;
        Map<MarketAPI, Integer> installationMap = new HashMap<>(); // Track where cores were installed

        // Use index-based loop to allow dynamic additions during iteration
        int coreIndex = 0;
        while (coreIndex < sortedCores.size()) {
            String coreId = sortedCores.get(coreIndex);
            boolean installed = false;

            // Try administrator installation first for Alpha cores (HIGHEST PRIORITY)
            if (coreId.equals(Commodities.ALPHA_CORE) && !availableAdminMarkets.isEmpty()) {
                MarketAPI targetMarket = DraconisAICorePriorityManager.pickTargetAdminMarket(availableAdminMarkets);
                if (targetMarket != null && DraconisAICorePriorityManager.installAICoreAdmin(targetMarket, coreId, DRACONIS)) {
                    // Track this installation
                    installationMap.put(targetMarket, installationMap.getOrDefault(targetMarket, 0) + 1);

                    availableAdminMarkets.remove(targetMarket);
                    coresInstalled++;
                    installed = true;
                }
            }

            // If not installed as admin, try industry installation with priority (considers both empty and upgradeable)
            if (!installed && (!availableIndustries.isEmpty() || !upgradeableIndustries.isEmpty())) {
                Industry targetIndustry = DraconisAICorePriorityManager.pickTargetIndustryByPriority(
                        availableIndustries, upgradeableIndustries, coreId
                );

                if (targetIndustry != null) {
                    // Check if this is an upgrade - if so, displaced core is returned to pool
                    String displacedCore = null;
                    if (targetIndustry.getAICoreId() != null && !targetIndustry.getAICoreId().isEmpty()) {
                        displacedCore = targetIndustry.getAICoreId();
                        Global.getLogger(DraconisAICoreTheftListener.class).info(
                                "Displacing " + DraconisAICorePriorityManager.getCoreDisplayName(displacedCore) +
                                " from " + targetIndustry.getCurrentName() + " with better " +
                                DraconisAICorePriorityManager.getCoreDisplayName(coreId)
                        );
                    }

                    if (tryInstallCore(targetIndustry, coreId)) {
                        // Track this installation
                        MarketAPI market = targetIndustry.getMarket();
                        installationMap.put(market, installationMap.getOrDefault(market, 0) + 1);

                        // Remove from appropriate list
                        availableIndustries.remove(targetIndustry);
                        upgradeableIndustries.remove(targetIndustry);

                        coresInstalled++;
                        installed = true;

                        // If we displaced a core, add it back to the sorted cores list for redistribution
                        if (displacedCore != null) {
                            sortedCores.add(displacedCore);
                            Global.getLogger(DraconisAICoreTheftListener.class).info(
                                    "Re-queuing displaced " + DraconisAICorePriorityManager.getCoreDisplayName(displacedCore) +
                                    " for installation elsewhere"
                            );
                        }
                    }
                }
            }

            if (!installed) {
                Global.getLogger(DraconisAICoreTheftListener.class).warn(
                        "Could not install " + coreId + " - no suitable facilities available"
                );
            }

            coreIndex++;
        }

        // Send single intel notification if any cores were successfully stolen
        if (coresInstalled > 0 && !installationMap.isEmpty()) {
            sendTheftIntel(stolenCores, raidedMarket, installationMap,
                    isPlayerMarket, actionType);
        }

        return coresInstalled;
    }

    /**
     * Find Draconis markets that can receive HYPERCOGNITION skill for administrators
     * Excludes Kori (capital) and markets where admin already has HYPERCOGNITION
     */
    private static List<MarketAPI> findAvailableDraconisAdminMarkets() {
        List<MarketAPI> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Exclude Kori - the capital should not have AI administrator enhancement
            if ("kori_market".equals(market.getId())) {
                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "Skipping Kori for administrator enhancement (capital exception)"
                );
                continue;
            }

            // Skip if admin already has HYPERCOGNITION
            if (market.getAdmin() != null &&
                market.getAdmin().getStats().getSkillLevel(Skills.HYPERCOGNITION) > 0) {
                continue; // Already enhanced with Alpha Core
            }

            available.add(market);
        }

        return available;
    }

    /**
     * Find Draconis industries WITHOUT AI cores
     */
    private static List<Industry> findAvailableDraconisIndustries() {
        List<Industry> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Defensive copy to prevent ConcurrentModificationException
            for (Industry industry : new ArrayList<>(market.getIndustries())) {
                // Only add if NO core installed
                if (industry.getAICoreId() != null && !industry.getAICoreId().isEmpty()) continue;
                if (!industry.isFunctional()) continue;

                available.add(industry);
            }
        }

        return available;
    }

    /**
     * Find Draconis industries WITH lower-tier cores that can be upgraded
     */
    private static List<Industry> findUpgradeableDraconisIndustries() {
        List<Industry> upgradeable = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Defensive copy to prevent ConcurrentModificationException
            for (Industry industry : new ArrayList<>(market.getIndustries())) {
                String currentCore = industry.getAICoreId();
                // Only add if has a core that could be upgraded
                if (currentCore == null || currentCore.isEmpty()) continue;

                // Can't upgrade an Alpha core (it's already the best)
                if (currentCore.equals(Commodities.ALPHA_CORE)) continue;

                if (!industry.isFunctional()) continue;

                upgradeable.add(industry);
            }
        }

        return upgradeable;
    }

    private static boolean tryInstallCore(Industry industry, String coreId) {
        String oldCore = industry.getAICoreId();
        industry.setAICoreId(coreId);

        if (coreId.equals(industry.getAICoreId())) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "INSTALLED: " + coreId + " on " + industry.getCurrentName() +
                            " at " + industry.getMarket().getName()
            );
            return true;
        }

        industry.setAICoreId(oldCore);
        Global.getLogger(DraconisAICoreTheftListener.class).error(
                "FAILED to install " + coreId + " on " + industry.getCurrentName()
        );
        return false;
    }

    private static void sendTheftMessage(String coreId, MarketAPI stolenFrom,
                                         MarketAPI installedAt, boolean isPlayerMarket) {
        String coreName = getCoreDisplayName(coreId);

        if (isPlayerMarket) {
            Global.getSector().getCampaignUI().addMessage(
                    "Intelligence reports: " + coreName + " stolen from " + stolenFrom.getName() +
                            " has been detected in use at " + installedAt.getName(),
                    Misc.getNegativeHighlightColor()
            );
        } else {
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

    private static boolean shouldPlayerKnowAboutTheft(MarketAPI raidedMarket) {
        for (MarketAPI playerMarket : Misc.getPlayerMarkets(false)) {
            if (playerMarket.getStarSystem() == raidedMarket.getStarSystem()) {
                return true;
            }
        }

        return Math.random() < 0.3f;
    }

    private static String getCoreDisplayName(String coreId) {
        return DraconisAICorePriorityManager.getCoreDisplayName(coreId);
    }

    private static void sendTheftIntel(List<String> stolenCores,
                                       MarketAPI stolenFrom,
                                       Map<MarketAPI, Integer> installationMap,
                                       boolean isPlayerMarket,
                                       String actionType) {
        // Create single intel notification for the entire raid
        DraconisAICoreTheftIntel intel = new DraconisAICoreTheftIntel(
                stolenFrom, installationMap, stolenCores, actionType, isPlayerMarket
        );

        // Always show notification - player should know about these significant events
        // Second parameter: false = show notification popup, true = silent add
        Global.getSector().getIntelManager().addIntel(intel, false);

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Created theft intel notification for " + stolenFrom.getName() +
                " (player market: " + isPlayerMarket + ", cores installed at " +
                installationMap.size() + " location(s))"
        );
    }
}