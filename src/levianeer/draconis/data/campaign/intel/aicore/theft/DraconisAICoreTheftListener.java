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

public class DraconisAICoreTheftListener {

    public static void checkAndStealAICores(MarketAPI raidedMarket, boolean isPlayerMarket, String actionType) {
        if (raidedMarket == null) {
            Global.getLogger(DraconisAICoreTheftListener.class).error(
                    "Attempted AI core theft on null market"
            );
            return;
        }

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

        List<String> stolenCores = new ArrayList<>();
        List<Industry> industries = raidedMarket.getIndustries();

        if (industries == null || industries.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "No industries on market - checking fallback generation"
            );
        } else {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Scanning " + industries.size() + " industries for AI cores"
            );

            // First pass: Steal actual installed AI cores
            for (Industry industry : industries) {
                if (industry == null) continue;

                String coreId = industry.getAICoreId();
                if (coreId != null && !coreId.isEmpty()) {
                    stolenCores.add(coreId);
                    industry.setAICoreId(null);

                    Global.getLogger(DraconisAICoreTheftListener.class).info(
                            "STOLEN: " + coreId + " from " + industry.getCurrentName()
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

            int installed = installStolenCores(stolenCores, raidedMarket, isPlayerMarket);

            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "=== AI CORE THEFT COMPLETE ==="
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Total stolen: " + stolenCores.size()
            );
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "Successfully installed: " + installed
            );
        } else {
            Global.getLogger(DraconisAICoreTheftListener.class).info(
                    "=== AI CORE THEFT COMPLETE - NO CORES FOUND ==="
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
                                          boolean isPlayerMarket) {
        List<Industry> availableIndustries = findAvailableDraconisIndustries();

        Global.getLogger(DraconisAICoreTheftListener.class).info(
                "Found " + availableIndustries.size() + " available Draconis industries for installation"
        );

        if (availableIndustries.isEmpty()) {
            Global.getLogger(DraconisAICoreTheftListener.class).error(
                    "NO DRACONIS INDUSTRIES AVAILABLE! " + stolenCores.size() + " cores will be lost!"
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
        for (String coreId : stolenCores) {
            if (availableIndustries.isEmpty()) {
                Global.getLogger(DraconisAICoreTheftListener.class).warn(
                        "Ran out of available industries. " + (stolenCores.size() - coresInstalled) +
                                " cores could not be installed"
                );
                break;
            }

            Industry targetIndustry = pickTargetIndustry(availableIndustries);

            if (targetIndustry != null && tryInstallCore(targetIndustry, coreId)) {
                availableIndustries.remove(targetIndustry);
                coresInstalled++;
                sendTheftMessage(coreId, raidedMarket, targetIndustry.getMarket(), isPlayerMarket);
            }
        }

        return coresInstalled;
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
}