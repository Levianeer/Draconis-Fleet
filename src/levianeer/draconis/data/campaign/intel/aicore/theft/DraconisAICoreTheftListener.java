package levianeer.draconis.data.campaign.intel.aicore.theft;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;
import levianeer.draconis.data.campaign.intel.aicore.intel.DraconisAICoreTheftIntel;

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

            // Simply clear the admin - this is the safe way to remove AI core admins
            raidedMarket.setAdmin(null);

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
        Map<MarketAPI, Integer> installationMap = new HashMap<>(); // Track where cores were installed

        for (String coreId : sortedCores) {
            boolean installed = false;

            // Try administrator installation first for Alpha cores
            if (coreId.equals(Commodities.ALPHA_CORE) && !availableAdminMarkets.isEmpty()) {
                MarketAPI targetMarket = pickTargetAdminMarket(availableAdminMarkets);
                if (targetMarket != null && tryInstallAdminCore(targetMarket, coreId)) {
                    // Track this installation
                    installationMap.put(targetMarket, installationMap.getOrDefault(targetMarket, 0) + 1);

                    availableAdminMarkets.remove(targetMarket);
                    coresInstalled++;
                    installed = true;
                }
            }

            // If not installed as admin, try industry installation with priority
            if (!installed && !availableIndustries.isEmpty()) {
                Industry targetIndustry = pickTargetIndustryByPriority(availableIndustries, coreId);

                if (targetIndustry != null && tryInstallCore(targetIndustry, coreId)) {
                    // Track this installation
                    MarketAPI market = targetIndustry.getMarket();
                    installationMap.put(market, installationMap.getOrDefault(market, 0) + 1);

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

        // Send single intel notification if any cores were successfully stolen
        if (coresInstalled > 0 && !installationMap.isEmpty()) {
            sendTheftIntel(stolenCores, raidedMarket, installationMap,
                    isPlayerMarket, actionType);
        }

        return coresInstalled;
    }

    /**
     * Find Draconis markets that can receive administrator AI cores
     * Excludes Kori (capital world that shouldn't have AI admin for lore reasons)
     * Will replace existing human admins OR upgrade existing AI core admins
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

            // Accept markets with:
            // 1. No admin at all
            // 2. Human admin (will be replaced)
            // 3. AI admin with lower-tier core (will be upgraded)
            // Only skip if already has an Alpha Core admin
            if (market.getAdmin() != null &&
                market.getAdmin().getAICoreId() != null &&
                market.getAdmin().getAICoreId().equals(Commodities.ALPHA_CORE)) {
                continue; // Already has best possible admin
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
     * Based on vanilla implementation in CoreLifecyclePluginImpl
     */
    private static boolean tryInstallAdminCore(MarketAPI market, String coreId) {
        try {
            // Remove existing admin first
            for (com.fs.starfarer.api.characters.PersonAPI p : market.getPeopleCopy()) {
                if (com.fs.starfarer.api.impl.campaign.ids.Ranks.POST_ADMINISTRATOR.equals(p.getPostId())) {
                    market.removePerson(p);
                    Global.getSector().getImportantPeople().removePerson(p);
                    market.getCommDirectory().removePerson(p);
                    Global.getLogger(DraconisAICoreTheftListener.class).info(
                            "Removed existing administrator: " + p.getNameString()
                    );
                    break;
                }
            }

            // Create AI administrator using Starsector's built-in method
            com.fs.starfarer.api.characters.PersonAPI admin =
                    Global.getSector().getFaction(DRACONIS).createRandomPerson();

            // Set rank and post
            admin.setRankId(com.fs.starfarer.api.impl.campaign.ids.Ranks.CITIZEN);
            admin.setPostId(com.fs.starfarer.api.impl.campaign.ids.Ranks.POST_ADMINISTRATOR);

            // Set AI core ID - MUST be done before setImportanceAndVoice
            admin.setAICoreId(coreId);

            // Set importance and voice - CRITICAL to prevent OfficerManagerEvent from replacing
            // This must be called AFTER setAICoreId to properly register as AI admin
            admin.setImportanceAndVoice(
                com.fs.starfarer.api.campaign.PersonImportance.MEDIUM,
                new java.util.Random()
            );

            // Add skills if Alpha core
            if (coreId.equals(com.fs.starfarer.api.impl.campaign.ids.Commodities.ALPHA_CORE)) {
                admin.getStats().setSkillLevel(com.fs.starfarer.api.impl.campaign.ids.Skills.INDUSTRIAL_PLANNING, 1);
                admin.getStats().setSkillLevel(com.fs.starfarer.api.impl.campaign.ids.Skills.HYPERCOGNITION, 1);
            }

            // Add to important people BEFORE setting as admin
            // This ensures OfficerManagerEvent sees the admin as already present
            Global.getSector().getImportantPeople().addPerson(admin);
            Global.getSector().getImportantPeople().getData(admin).getLocation().setMarket(market);

            // Add to comm directory BEFORE setting as admin (position 0 = top of list)
            market.getCommDirectory().addPerson(admin, 0);

            // Add to market people BEFORE setting as admin
            market.addPerson(admin);

            // Set as market admin - do this AFTER adding to comm directory and market people
            market.setAdmin(admin);

            // Add the AI core admin condition if not present
            if (!market.hasCondition(com.fs.starfarer.api.impl.campaign.ids.Conditions.AI_CORE_ADMIN)) {
                market.addCondition(com.fs.starfarer.api.impl.campaign.ids.Conditions.AI_CORE_ADMIN);
            }

            // CRITICAL: Mark that this market has an admin to prevent OfficerManagerEvent from replacing it
            // This is the key to preventing the AI admin from being replaced by a human
            market.getMemoryWithoutUpdate().set(MemFlags.MARKET_DO_NOT_INIT_COMM_LISTINGS, true);

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "INSTALLED: " + coreId + " as Administrator at " + market.getName() +
                    " (admin: " + admin.getNameString() + ")"
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
     *
     * UNIFIED PRIORITY ORDER (applies to all core types):
     * 1. Administrator (handled separately - Alpha cores only)
     * 2. Orbital Works / Heavy Industry (10.0) - Ship/Equipment production
     * 3. Population & Infrastructure (9.0) - Population growth
     * 4. High Command (8.5) - Military command
     * 5. Megaport (8.0) - Trade hub
     * 6. Everything else (lower priorities based on industry type)
     *
     * Note: Administrator installation is handled separately in installStolenCores()
     * and only uses Alpha cores.
     */
    private static float getIndustryPriority(Industry industry, String coreId) {
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

        // Tier 5: Other important industries (vary slightly by core type for optimization)
        if (coreId.equals(Commodities.ALPHA_CORE)) {
            // Alpha cores: Focus on high-value infrastructure
            if (industryId.contains("fuelprod")) return 7.5f;            // Fuel Production
            if (industryId.contains("refining")) return 7.0f;            // Refining
            if (industryId.contains("waystation")) return 6.5f;          // Waystation
            if (industryId.contains("lightindustry")) return 6.0f;       // Light Industry
            if (industryId.contains("mining")) return 5.5f;              // Mining
            if (industryId.contains("farming")) return 5.0f;             // Farming
            return 3.0f; // Default for other industries
        } else if (coreId.equals(Commodities.BETA_CORE)) {
            // Beta cores: Focus on resource processing
            if (industryId.contains("fuelprod")) return 7.5f;            // Fuel Production
            if (industryId.contains("refining")) return 7.5f;            // Refining
            if (industryId.contains("lightindustry")) return 7.0f;       // Light Industry
            if (industryId.contains("mining")) return 6.5f;              // Mining
            if (industryId.contains("farming")) return 6.0f;             // Farming
            if (industryId.contains("aquaculture")) return 5.5f;         // Aquaculture
            return 2.0f;
        } else if (coreId.equals(Commodities.GAMMA_CORE)) {
            // Gamma cores: Focus on basic resource extraction
            if (industryId.contains("lightindustry")) return 7.5f;       // Light Industry
            if (industryId.contains("mining")) return 7.0f;              // Mining
            if (industryId.contains("farming")) return 7.0f;             // Farming
            if (industryId.contains("aquaculture")) return 6.5f;         // Aquaculture
            if (industryId.contains("refining")) return 6.0f;            // Refining
            if (industryId.contains("commerce")) return 5.0f;            // Commerce
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