package levianeer.draconis.data.campaign.intel.aicore.donation;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Monitors player AI core donations to Draconis faction
 * When player donates cores, they get installed on Draconis facilities
 */
public class DraconisAICoreDonationListener implements EveryFrameScript {

    private static final String CORE_STOCKPILE_KEY = "$draconis_coreStockpile";
    private float checkInterval = 0f;
    private static final float CHECK_FREQUENCY = 1.0f; // Check every day

    // Track last known core counts to detect donations
    private int lastAlphaCores = 0;
    private int lastBetaCores = 0;
    private int lastGammaCores = 0;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);
        checkInterval += days;

        if (checkInterval < CHECK_FREQUENCY) return;
        checkInterval = 0f;

        // Check Draconis faction stockpile for AI cores
        checkForDonatedCores();
    }

    /**
     * Check if player has donated AI cores to Draconis
     * Vanilla stores donation counts in faction memory with keys like "$turnedIn_alpha_core"
     */
    private void checkForDonatedCores() {
        com.fs.starfarer.api.campaign.FactionAPI faction = Global.getSector().getFaction(DRACONIS);
        if (faction == null) return;

        // Get current donation counts from faction memory (vanilla stores these)
        int currentAlpha = (int) faction.getMemoryWithoutUpdate().getFloat("$turnedIn_" + Commodities.ALPHA_CORE);
        int currentBeta = (int) faction.getMemoryWithoutUpdate().getFloat("$turnedIn_" + Commodities.BETA_CORE);
        int currentGamma = (int) faction.getMemoryWithoutUpdate().getFloat("$turnedIn_" + Commodities.GAMMA_CORE);

        // Detect new donations since last check
        int newAlpha = Math.max(0, currentAlpha - lastAlphaCores);
        int newBeta = Math.max(0, currentBeta - lastBetaCores);
        int newGamma = Math.max(0, currentGamma - lastGammaCores);

        if (newAlpha > 0 || newBeta > 0 || newGamma > 0) {
            Global.getLogger(this.getClass()).info("========================================");
            Global.getLogger(this.getClass()).info("=== PLAYER AI CORE DONATION DETECTED ===");
            Global.getLogger(this.getClass()).info("Alpha Cores: " + newAlpha);
            Global.getLogger(this.getClass()).info("Beta Cores: " + newBeta);
            Global.getLogger(this.getClass()).info("Gamma Cores: " + newGamma);

            handleDonatedCores(newAlpha, newBeta, newGamma);

            Global.getLogger(this.getClass()).info("========================================");
        }

        // Update tracked counts
        lastAlphaCores = currentAlpha;
        lastBetaCores = currentBeta;
        lastGammaCores = currentGamma;
    }

    /**
     * Get current AI core stockpile from Draconis faction
     * Tries multiple sources: faction memory, stockpile tracker, etc.
     */
    private Map<String, Integer> getCurrentCoreStockpile() {
        Map<String, Integer> cores = new HashMap<>();

        com.fs.starfarer.api.campaign.FactionAPI faction = Global.getSector().getFaction(DRACONIS);
        if (faction == null) return cores;

        // Check faction memory for our custom stockpile tracking
        if (faction.getMemoryWithoutUpdate().contains(CORE_STOCKPILE_KEY)) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> stockpile = (Map<String, Integer>)
                faction.getMemoryWithoutUpdate().get(CORE_STOCKPILE_KEY);
            if (stockpile != null) {
                return stockpile;
            }
        }

        // Initialize stockpile tracking
        Map<String, Integer> stockpile = new HashMap<>();
        stockpile.put(Commodities.ALPHA_CORE, 0);
        stockpile.put(Commodities.BETA_CORE, 0);
        stockpile.put(Commodities.GAMMA_CORE, 0);
        faction.getMemoryWithoutUpdate().set(CORE_STOCKPILE_KEY, stockpile);

        return stockpile;
    }

    /**
     * Install donated cores on Draconis facilities
     */
    private void handleDonatedCores(int alphaCount, int betaCount, int gammaCount) {
        List<String> coresToInstall = new ArrayList<>();

        // Build list of cores to install (sorted by priority)
        for (int i = 0; i < alphaCount; i++) {
            coresToInstall.add(Commodities.ALPHA_CORE);
        }
        for (int i = 0; i < betaCount; i++) {
            coresToInstall.add(Commodities.BETA_CORE);
        }
        for (int i = 0; i < gammaCount; i++) {
            coresToInstall.add(Commodities.GAMMA_CORE);
        }

        if (coresToInstall.isEmpty()) return;

        // Find available Draconis industries
        List<Industry> availableIndustries = findAvailableDraconisIndustries();

        Global.getLogger(this.getClass()).info(
                "Found " + availableIndustries.size() + " available Draconis industries for donated cores"
        );

        if (availableIndustries.isEmpty()) {
            Global.getLogger(this.getClass()).warn(
                    "No available Draconis facilities for donated cores - cores will remain in stockpile"
            );
            return;
        }

        int installed = 0;
        Map<MarketAPI, List<String>> installationMap = new HashMap<>();

        for (String coreId : coresToInstall) {
            if (availableIndustries.isEmpty()) {
                Global.getLogger(this.getClass()).warn(
                        "Ran out of available industries - " + (coresToInstall.size() - installed) +
                        " cores remain uninstalled"
                );
                break;
            }

            Industry targetIndustry = pickTargetIndustryByPriority(availableIndustries, coreId);

            if (targetIndustry != null && tryInstallCore(targetIndustry, coreId)) {
                MarketAPI market = targetIndustry.getMarket();
                installationMap.computeIfAbsent(market, k -> new ArrayList<>()).add(coreId);

                availableIndustries.remove(targetIndustry);
                installed++;
            }
        }

        Global.getLogger(this.getClass()).info(
                "=== DONATION PROCESSING COMPLETE ==="
        );
        Global.getLogger(this.getClass()).info(
                "Total donated: " + coresToInstall.size()
        );
        Global.getLogger(this.getClass()).info(
                "Successfully installed: " + installed
        );
        Global.getLogger(this.getClass()).info(
                "Installed at " + installationMap.size() + " Draconis facilities"
        );

        // Show notification to player
        if (installed > 0) {
            showDonationNotification(installed, installationMap);
        }
    }

    /**
     * Find Draconis industries that can receive AI cores
     */
    private List<Industry> findAvailableDraconisIndustries() {
        List<Industry> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            for (Industry industry : market.getIndustries()) {
                // Skip if already has a core
                if (industry.getAICoreId() != null && !industry.getAICoreId().isEmpty()) continue;
                // Skip if not functional
                if (!industry.isFunctional()) continue;

                available.add(industry);
            }
        }

        return available;
    }

    /**
     * Pick target industry based on core type priority
     * Uses same priority system as theft system
     */
    private Industry pickTargetIndustryByPriority(List<Industry> industries, String coreId) {
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
     * Same priority system as DraconisAICoreTheftListener
     */
    private float getIndustryPriority(Industry industry, String coreId) {
        String industryId = industry.getId().toLowerCase();

        // Tier 1: Production (Orbital Works / Heavy Industry)
        if (industryId.contains("orbitalworks")) return 10.0f;
        if (industryId.contains("heavyindustry")) return 10.0f;

        // Tier 2: Population & Infrastructure
        if (industryId.contains("population")) return 9.0f;

        // Tier 3: High Command
        if (industryId.contains("xlii_highcommand")) return 8.5f;
        if (industryId.contains("highcommand")) return 8.5f;
        if (industryId.contains("militarybase")) return 8.3f;

        // Tier 4: Megaport
        if (industryId.contains("megaport")) return 8.0f;

        // Tier 5: Other industries (vary by core type)
        if (coreId.equals(Commodities.ALPHA_CORE)) {
            if (industryId.contains("fuelprod")) return 7.5f;
            if (industryId.contains("refining")) return 7.0f;
            if (industryId.contains("waystation")) return 6.5f;
            if (industryId.contains("lightindustry")) return 6.0f;
            if (industryId.contains("mining")) return 5.5f;
            if (industryId.contains("farming")) return 5.0f;
            return 3.0f;
        } else if (coreId.equals(Commodities.BETA_CORE)) {
            if (industryId.contains("fuelprod")) return 7.5f;
            if (industryId.contains("refining")) return 7.5f;
            if (industryId.contains("lightindustry")) return 7.0f;
            if (industryId.contains("mining")) return 6.5f;
            if (industryId.contains("farming")) return 6.0f;
            if (industryId.contains("aquaculture")) return 5.5f;
            return 2.0f;
        } else if (coreId.equals(Commodities.GAMMA_CORE)) {
            if (industryId.contains("lightindustry")) return 7.5f;
            if (industryId.contains("mining")) return 7.0f;
            if (industryId.contains("farming")) return 7.0f;
            if (industryId.contains("aquaculture")) return 6.5f;
            if (industryId.contains("refining")) return 6.0f;
            if (industryId.contains("commerce")) return 5.0f;
            return 1.0f;
        }

        return 1.0f;
    }

    /**
     * Try to install AI core on an industry
     */
    private boolean tryInstallCore(Industry industry, String coreId) {
        String oldCore = industry.getAICoreId();
        industry.setAICoreId(coreId);

        if (coreId.equals(industry.getAICoreId())) {
            Global.getLogger(this.getClass()).info(
                    "INSTALLED: " + coreId + " on " + industry.getCurrentName() +
                    " at " + industry.getMarket().getName()
            );
            return true;
        }

        industry.setAICoreId(oldCore);
        Global.getLogger(this.getClass()).error(
                "FAILED to install " + coreId + " on " + industry.getCurrentName()
        );
        return false;
    }

    /**
     * Show notification to player about donated cores being utilized
     */
    private void showDonationNotification(int coresInstalled, Map<MarketAPI, List<String>> installationMap) {
        StringBuilder message = new StringBuilder();
        message.append("Your donated AI cores have been put to use by Draconis Alliance forces. ");
        message.append(coresInstalled).append(" core").append(coresInstalled > 1 ? "s" : "");
        message.append(" installed across ").append(installationMap.size());
        message.append(" facilit").append(installationMap.size() > 1 ? "ies" : "y").append(".");

        Global.getSector().getCampaignUI().addMessage(
                message.toString(),
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor()
        );

        Global.getLogger(this.getClass()).info("Sent donation notification to player");
    }

    /**
     * Public API: Manually record a core donation
     * Call this when player donates cores through bar events, etc.
     */
    public static void recordCoreDonation(String coreId, int count) {
        if (count <= 0) return;

        com.fs.starfarer.api.campaign.FactionAPI faction = Global.getSector().getFaction(DRACONIS);
        if (faction == null) return;

        Map<String, Integer> stockpile;
        if (faction.getMemoryWithoutUpdate().contains(CORE_STOCKPILE_KEY)) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> existing = (Map<String, Integer>)
                faction.getMemoryWithoutUpdate().get(CORE_STOCKPILE_KEY);
            stockpile = existing != null ? existing : new HashMap<>();
        } else {
            stockpile = new HashMap<>();
        }

        int current = stockpile.getOrDefault(coreId, 0);
        stockpile.put(coreId, current + count);
        faction.getMemoryWithoutUpdate().set(CORE_STOCKPILE_KEY, stockpile);

        Global.getLogger(DraconisAICoreDonationListener.class).info(
                "Recorded donation: " + count + "x " + coreId + " to Draconis (total: " +
                stockpile.get(coreId) + ")"
        );
    }
}