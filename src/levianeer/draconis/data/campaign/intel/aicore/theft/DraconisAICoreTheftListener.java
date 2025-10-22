package levianeer.draconis.data.campaign.intel.aicore.theft;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;
import levianeer.draconis.data.campaign.intel.aicore.intel.DraconisAICoreTheftIntel;

import java.util.ArrayList;
import java.util.List;

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

        // Check for administrator AI core first
        if (raidedMarket.getAdmin() != null && raidedMarket.getAdmin().getAICoreId() != null) {
            String adminCoreId = raidedMarket.getAdmin().getAICoreId();
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "  Checking Administrator: " + raidedMarket.getAdmin().getNameString()
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "    AI Core: " + adminCoreId
            );

            stolenCores.add(adminCoreId);
            raidedMarket.getAdmin().getStats().setSkillLevel(null, 0); // Remove admin core
            raidedMarket.setAdmin(null); // Clear the AI admin entirely

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "    >>> STOLEN: " + adminCoreId + " from Administrator"
            );
        }

        List<Industry> industries = raidedMarket.getIndustries();

        if (industries == null || industries.isEmpty()) {
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
            int priorityA = getCorePriority(a);
            int priorityB = getCorePriority(b);
            return Integer.compare(priorityB, priorityA); // Descending order
        });

        List<MarketAPI> availableAdminMarkets = findAvailableDraconisAdminMarkets();
        List<Industry> availableIndustries = findAvailableDraconisIndustries();

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Found " + availableAdminMarkets.size() + " available Draconis markets for administrator installation"
        );
        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Found " + availableIndustries.size() + " available Draconis industries for installation"
        );

        if (availableAdminMarkets.isEmpty() && availableIndustries.isEmpty()) {
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
        MarketAPI firstInstallLocation = null;

        for (String coreId : sortedCores) {
            boolean installed = false;

            // Try administrator installation first for Alpha cores
            if (coreId.equals(Commodities.ALPHA_CORE) && !availableAdminMarkets.isEmpty()) {
                MarketAPI targetMarket = pickTargetAdminMarket(availableAdminMarkets);
                if (targetMarket != null && tryInstallAdminCore(targetMarket, coreId)) {
                    if (firstInstallLocation == null) {
                        firstInstallLocation = targetMarket;
                    }
                    availableAdminMarkets.remove(targetMarket);
                    coresInstalled++;
                    installed = true;
                }
            }

            // If not installed as admin, try industry installation with priority
            if (!installed && !availableIndustries.isEmpty()) {
                Industry targetIndustry = pickTargetIndustryByPriority(availableIndustries, coreId);

                if (targetIndustry != null && tryInstallCore(targetIndustry, coreId)) {
                    if (firstInstallLocation == null) {
                        firstInstallLocation = targetIndustry.getMarket();
                    }
                    availableIndustries.remove(targetIndustry);
                    coresInstalled++;
                    installed = true;
                }
            }

            if (!installed) {
                Global.getLogger(DraconisAICoreTheftListener.class).warn(
                        "Could not install " + coreId + " - no suitable facilities available"
                );
            }
        }

        // Send intel notification if any cores were successfully stolen
        if (coresInstalled > 0 && firstInstallLocation != null) {
            sendTheftIntel(stolenCores, raidedMarket, firstInstallLocation,
                    isPlayerMarket, actionType);
        }

        return coresInstalled;
    }

    /**
     * Find Draconis markets that can receive administrator AI cores
     * Excludes Kori (capital world that shouldn't have AI admin for lore reasons)
     */
    private static List<MarketAPI> findAvailableDraconisAdminMarkets() {
        List<MarketAPI> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Exclude Kori - the capital should not have an AI administrator
            if ("kori_market".equals(market.getId())) {
                Global.getLogger(DraconisAICoreTheftListener.class).info(
                        "Skipping Kori for administrator installation (capital exception)"
                );
                continue;
            }

            // Check if market already has an admin
            if (market.getAdmin() != null && market.getAdmin().getAICoreId() != null) {
                continue;
            }

            available.add(market);
        }

        return available;
    }

    private static List<Industry> findAvailableDraconisIndustries() {
        List<Industry> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            for (Industry industry : market.getIndustries()) {
                if (industry.getAICoreId() != null && !industry.getAICoreId().isEmpty()) continue;
                if (!industry.isFunctional()) continue;

                available.add(industry);
            }
        }

        return available;
    }

    /**
     * Pick a target market for administrator installation
     * Prefers larger markets
     */
    private static MarketAPI pickTargetAdminMarket(List<MarketAPI> markets) {
        if (markets.isEmpty()) return null;

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
     * Try to install an AI core as administrator on a market
     */
    private static boolean tryInstallAdminCore(MarketAPI market, String coreId) {
        try {
            // Create AI administrator using Starsector's built-in method
            com.fs.starfarer.api.characters.PersonAPI admin =
                    Global.getSector().getFaction(DRACONIS).createRandomPerson();
            admin.setAICoreId(coreId);
            market.setAdmin(admin);

            // Add the AI core admin condition if not present
            if (!market.hasCondition(com.fs.starfarer.api.impl.campaign.ids.Conditions.AI_CORE_ADMIN)) {
                market.addCondition(com.fs.starfarer.api.impl.campaign.ids.Conditions.AI_CORE_ADMIN);
            }

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "INSTALLED: " + coreId + " as Administrator at " + market.getName()
            );
            return true;
        } catch (Exception e) {
            Global.getLogger(DraconisAICoreTheftListener.class).error(
                    "FAILED to install " + coreId + " as administrator at " + market.getName(), e
            );
            return false;
        }
    }

    /**
     * Get priority value for core types (higher = more valuable)
     */
    private static int getCorePriority(String coreId) {
        return switch (coreId) {
            case Commodities.ALPHA_CORE -> 3;
            case Commodities.BETA_CORE -> 2;
            case Commodities.GAMMA_CORE -> 1;
            default -> 0;
        };
    }

    /**
     * Pick target industry based on core type priority
     * Industry priority order (for each core type):
     *
     * Alpha Core priority:
     * 1. High Command / Military Base (strategic value)
     * 2. Heavy Industry (production boost)
     * 3. Fuel Production / Refining (critical resources)
     * 4. Other industries
     *
     * Beta Core priority:
     * 1. Heavy Industry
     * 2. Fuel Production / Refining
     * 3. Light Industry / Orbital Works
     * 4. Other industries
     *
     * Gamma Core priority:
     * 1. Light Industry
     * 2. Mining / Farming (resource production)
     * 3. Other industries
     */
    private static Industry pickTargetIndustryByPriority(List<Industry> industries, String coreId) {
        if (industries.isEmpty()) return null;

        WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
        float sizeWeight = DraconisAICoreConfig.getMarketSizeWeight();

        for (Industry industry : industries) {
            float basePriority = getIndustryPriority(industry, coreId);
            float marketSizeBonus = (float) Math.pow(industry.getMarket().getSize(), sizeWeight * 0.5f);
            float weight = basePriority * marketSizeBonus;

            picker.add(industry, weight);
        }

        return picker.pick();
    }

    /**
     * Get priority weight for an industry based on core type
     */
    private static float getIndustryPriority(Industry industry, String coreId) {
        String industryId = industry.getId();

        if (coreId.equals(Commodities.ALPHA_CORE)) {
            // Alpha cores: strategic and high-value installations
            if (industryId.contains("highcommand") || industryId.contains("militarybase")) return 10.0f;
            if (industryId.contains("heavyindustry")) return 8.0f;
            if (industryId.contains("fuelprod") || industryId.contains("refining")) return 7.0f;
            if (industryId.contains("orbitalworks")) return 6.0f;
            return 3.0f; // Default for other industries
        } else if (coreId.equals(Commodities.BETA_CORE)) {
            // Beta cores: production and resource processing
            if (industryId.contains("heavyindustry")) return 10.0f;
            if (industryId.contains("fuelprod") || industryId.contains("refining")) return 9.0f;
            if (industryId.contains("lightindustry") || industryId.contains("orbitalworks")) return 7.0f;
            if (industryId.contains("mining") || industryId.contains("farming")) return 5.0f;
            return 2.0f;
        } else if (coreId.equals(Commodities.GAMMA_CORE)) {
            // Gamma cores: basic production and resource extraction
            if (industryId.contains("lightindustry")) return 10.0f;
            if (industryId.contains("mining") || industryId.contains("farming")) return 8.0f;
            if (industryId.contains("refining")) return 6.0f;
            return 1.0f;
        }

        return 1.0f; // Default weight
    }

    private static Industry pickTargetIndustry(List<Industry> industries) {
        if (industries.isEmpty()) return null;

        if (!DraconisAICoreConfig.preferLargeMarkets()) {
            return industries.get((int)(Math.random() * industries.size()));
        }

        WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
        float sizeWeight = DraconisAICoreConfig.getMarketSizeWeight();

        for (Industry ind : industries) {
            float weight = (float) Math.pow(ind.getMarket().getSize(), sizeWeight);
            picker.add(ind, weight);
        }

        return picker.pick();
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
        return switch (coreId) {
            case Commodities.ALPHA_CORE -> "Alpha Core";
            case Commodities.BETA_CORE -> "Beta Core";
            case Commodities.GAMMA_CORE -> "Gamma Core";
            default -> "AI Core";
        };
    }

    private static void sendTheftIntel(List<String> stolenCores, MarketAPI stolenFrom,
                                       MarketAPI installedAt, boolean isPlayerMarket,
                                       String actionType) {
        // Create the intel notification
        DraconisAICoreTheftIntel intel = new DraconisAICoreTheftIntel(
                stolenFrom, installedAt, stolenCores, actionType, isPlayerMarket
        );

        // Always show notification - player should know about these significant events
        // Second parameter: false = show notification popup, true = silent add
        Global.getSector().getIntelManager().addIntel(intel, false);

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Created theft intel notification for " + stolenFrom.getName() +
                " (player market: " + isPlayerMarket + ")"
        );
    }
}