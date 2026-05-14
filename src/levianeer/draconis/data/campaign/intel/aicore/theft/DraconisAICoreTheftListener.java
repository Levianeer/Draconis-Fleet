package levianeer.draconis.data.campaign.intel.aicore.theft;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;
import levianeer.draconis.data.campaign.intel.aicore.intel.DraconisAICoreTheftIntel;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICorePriorityManager;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICoreStockpile;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

public class DraconisAICoreTheftListener {

    private static final Logger log = Global.getLogger(DraconisAICoreTheftListener.class);

    public static void checkAndStealAICores(MarketAPI raidedMarket, boolean isPlayerMarket, String actionType) {
        if (raidedMarket == null) {
            log.error(
                    "Attempted AI core theft on null market"
            );
            return;
        }

        // Don't steal from our own facilities!
        if (raidedMarket.getFactionId().equals(DRACONIS)) {
            log.info(
                    "Skipping AI core theft - market is now Draconis-owned: " + raidedMarket.getName()
            );
            return;
        }

        // Check if we already stole from this market today
        long currentDay = Global.getSector().getClock().getDay();
        String lastTheftKey = "$draconis_lastTheftDay";
        long lastTheftDay = raidedMarket.getMemoryWithoutUpdate().getLong(lastTheftKey);

        if (lastTheftDay == currentDay) {
            log.info(
                    "!!! DUPLICATE THEFT ATTEMPT BLOCKED !!!"
            );
            log.info(
                    "Already stole from " + raidedMarket.getName() + " today (day " + currentDay + ")"
            );
            log.info(
                    "=========================================="
            );
            return;
        }

        // Mark this market as stolen from today
        raidedMarket.getMemoryWithoutUpdate().set(lastTheftKey, currentDay);

        log.info(
                "=========================================="
        );
        log.info(
                "=== AI CORE THEFT STARTING ==="
        );
        log.info(
                "Action: " + actionType
        );
        log.info(
                "Target: " + raidedMarket.getName() + " (" + raidedMarket.getFactionId() + ")"
        );
        log.info(
                "Player owned: " + isPlayerMarket
        );
        log.info(
                "Market size: " + raidedMarket.getSize()
        );

        List<String> stolenCores = new ArrayList<>();

        // Check for AI core installed as administrator
        if (raidedMarket.getAdmin() != null && raidedMarket.getAdmin().getAICoreId() != null) {
            String adminCoreId = raidedMarket.getAdmin().getAICoreId();

            log.info(
                    "  Checking Administrator: " + raidedMarket.getAdmin().getNameString()
            );
            log.info(
                    "    Is AI core persona: " + adminCoreId
            );

            stolenCores.add(adminCoreId);

            // Replace the AI core admin with a basic NPC so the market isn't left adminless
            PersonAPI replacement = Global.getFactory().createPerson();
            replacement.setFaction(raidedMarket.getFactionId());
            raidedMarket.setAdmin(replacement);

            log.info(
                    "    >>> STOLEN: " + adminCoreId + " (AI core admin replaced with NPC)"
            );
        }

        // Defensive copy to prevent ConcurrentModificationException
        List<Industry> industries = new ArrayList<>(raidedMarket.getIndustries());

        if (industries.isEmpty()) {
            log.info(
                    "No industries on market"
            );
        } else {
            log.info(
                    "Scanning " + industries.size() + " industries for AI cores"
            );

            // Steal actual installed AI cores from industries
            for (Industry industry : industries) {
                if (industry == null) continue;

                log.info(
                        "  Checking industry: " + industry.getCurrentName()
                );

                String coreId = industry.getAICoreId();

                log.info(
                        "    AI Core: " + (coreId != null && !coreId.isEmpty() ? coreId : "none")
                );

                if (coreId != null && !coreId.isEmpty()) {
                    stolenCores.add(coreId);
                    industry.setAICoreId(null);

                    log.info(
                            "    >>> STOLEN: " + coreId + " from " + industry.getCurrentName()
                    );
                }
            }
        }

        log.info(
                "Actual cores stolen: " + stolenCores.size()
        );

        // Fallback: Generate cores if none found
        if (stolenCores.isEmpty()) {
            log.info(
                    "No actual cores found - attempting fallback generation"
            );

            if (DraconisAICoreConfig.preferActualCores()) {
                stolenCores = generateFallbackCores(raidedMarket);

                if (!stolenCores.isEmpty()) {
                    log.info(
                            "Generated " + stolenCores.size() + " fallback cores"
                    );
                    for (String core : stolenCores) {
                        log.info(
                                "  - " + core
                        );
                    }
                } else {
                    log.info(
                            "Fallback generation produced no cores"
                    );
                }
            } else {
                log.info(
                        "Fallback generation disabled in config"
                );
            }
        }

        // Install stolen cores on Draconis industries
        if (!stolenCores.isEmpty()) {
            log.info(
                    "Installing " + stolenCores.size() + " stolen cores on Draconis facilities"
            );

            int installed = installStolenCores(stolenCores, raidedMarket, isPlayerMarket, actionType);

            // Apply diplomatic strain (Nexerelin integration)
            // Cores were stolen regardless of whether they could be immediately installed
            if (!stolenCores.isEmpty()) {
                int alphaCount = 0;
                int betaCount = 0;
                int gammaCount = 0;

                for (String coreId : stolenCores) {
                    switch (coreId) {
                        case Commodities.ALPHA_CORE -> alphaCount++;
                        case Commodities.BETA_CORE -> betaCount++;
                        case Commodities.GAMMA_CORE -> gammaCount++;
                    }
                }

                levianeer.draconis.data.campaign.intel.aicore.diplomacy.DraconisDiplomacyStrain.applyAICoreStrain(
                        raidedMarket.getFactionId(), alphaCount, betaCount, gammaCount
                );
            }

            log.info(
                    "=== AI CORE THEFT COMPLETE ==="
            );
            log.info(
                    "Total stolen: " + stolenCores.size()
            );
            log.info(
                    "Successfully installed: " + installed
            );
            log.info(
                    "=========================================="
            );
        } else {
            log.info(
                    "=== AI CORE THEFT COMPLETE - NO CORES FOUND ==="
            );
            log.info(
                    "=========================================="
            );
        }
    }

    private static List<String> generateFallbackCores(MarketAPI market) {
        List<String> cores = new ArrayList<>();

        String factionId = market.getFactionId();
        int marketSize = market.getSize();

        log.info(
                "Generating fallback cores for " + factionId + " market size " + marketSize
        );

        DraconisAICoreConfig.FactionCoreChances chances =
                DraconisAICoreConfig.getFactionCoreChances(factionId);

        log.info(
                "Chances: Alpha=" + chances.alphaChance +
                        ", Beta=" + chances.betaChance +
                        ", Gamma=" + chances.gammaChance +
                        ", MinSize=" + chances.minMarketSize
        );

        if (marketSize < chances.minMarketSize) {
            log.info(
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
            log.info(
                    "Generated Alpha Core"
            );
        }

        if (chances.betaChance > 0 && Math.random() < chances.betaChance && coresGenerated < maxCores) {
            cores.add(Commodities.BETA_CORE);
            coresGenerated++;
            log.info(
                    "Generated Beta Core"
            );
        }

        if (chances.gammaChance > 0 && Math.random() < chances.gammaChance && coresGenerated < maxCores) {
            cores.add(Commodities.GAMMA_CORE);
            coresGenerated++;
            log.info(
                    "Generated Gamma Core"
            );
        }

        if (chances.gammaChance > 0.4f && Math.random() < (chances.gammaChance * 0.5f) && coresGenerated < maxCores) {
            cores.add(Commodities.GAMMA_CORE);
            coresGenerated++;
            log.info(
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

        log.info(
                "Found " + availableAdminMarkets.size() + " available Draconis markets for administrator installation"
        );
        log.info(
                "Found " + availableIndustries.size() + " empty Draconis industries"
        );
        log.info(
                "Found " + upgradeableIndustries.size() + " upgradeable Draconis industries"
        );

        if (availableAdminMarkets.isEmpty() && availableIndustries.isEmpty() && upgradeableIndustries.isEmpty()) {
            // No slots available right now - persist stolen cores to stockpile for future installation
            for (String coreId : sortedCores) {
                DraconisAICoreStockpile.add(coreId, 1);
            }
            log.info(
                    "No Draconis facilities available - " + stolenCores.size() +
                    " stolen core(s) added to stockpile for future installation"
            );

            if (isPlayerMarket) {
                Global.getSector().getCampaignUI().addMessage(
                        "Intelligence reports: Draconis forces seized AI cores from " + raidedMarket.getName() +
                                " and are holding them in reserve pending facility availability.",
                        Misc.getTextColor()
                );
            }

            // Cores were stolen (diplomatic strain applied by caller), but none installed yet
            return 0;
        }

        int coresInstalled = 0;
        Map<MarketAPI, Integer> installationMap = new HashMap<>(); // Track where cores were installed
        List<String> failedToInstall = new ArrayList<>(); // Cores that couldn't be placed in any slot

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
                        log.info(
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
                            log.info(
                                    "Re-queuing displaced " + DraconisAICorePriorityManager.getCoreDisplayName(displacedCore) +
                                    " for installation elsewhere"
                            );
                        }
                    }
                }
            }

            if (!installed) {
                failedToInstall.add(coreId);
            }

            coreIndex++;
        }

        // Persist stolen cores that couldn't be installed - the daily drain will retry
        if (!failedToInstall.isEmpty()) {
            for (String coreId : failedToInstall) {
                DraconisAICoreStockpile.add(coreId, 1);
            }
            log.info(
                    "Persisted " + failedToInstall.size() + " uninstalled stolen core(s) to stockpile"
            );
        }

        // Send intel notification for the theft (empty installationMap = stockpile-only, still reports)
        if (!stolenCores.isEmpty()) {
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
                log.info(
                        "Skipping Kori for administrator enhancement (capital exception)"
                );
                continue;
            }

            // Skip markets with no administrator - installAICoreAdmin would fail silently
            if (market.getAdmin() == null) continue;

            // Skip if admin already has HYPERCOGNITION
            if (market.getAdmin().getStats().getSkillLevel(Skills.HYPERCOGNITION) > 0) {
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
            log.info(
                    "INSTALLED: " + coreId + " on " + industry.getCurrentName() +
                            " at " + industry.getMarket().getName()
            );
            return true;
        }

        industry.setAICoreId(oldCore);
        log.error(
                "FAILED to install " + coreId + " on " + industry.getCurrentName()
        );
        return false;
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
        // Constructor self-registers via addIntel + addScript
        new DraconisAICoreTheftIntel(stolenFrom, installationMap, stolenCores, actionType, isPlayerMarket);

        log.info(
                "Created theft intel notification for " + stolenFrom.getName() +
                " (player market: " + isPlayerMarket + ", cores installed at " +
                installationMap.size() + " location(s))"
        );
    }
}