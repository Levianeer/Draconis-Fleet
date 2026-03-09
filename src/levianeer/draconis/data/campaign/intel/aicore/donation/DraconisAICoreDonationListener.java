package levianeer.draconis.data.campaign.intel.aicore.donation;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.intel.aicore.config.DraconisAICoreConfig;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICorePriorityManager;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICoreStockpile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Monitors player AI core donations to Draconis faction
 * When player donates cores, they get installed on Draconis facilities
 */
public class DraconisAICoreDonationListener implements EveryFrameScript {

    private float checkInterval = 0f;
    private static final float CHECK_FREQUENCY = 1.0f; // Check every day

    // Track last known core counts to detect donations
    private int lastAlphaCores = -1;
    private int lastBetaCores = -1;
    private int lastGammaCores = -1;

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

        // Heartbeat drain: attempt to install any cores waiting in the stockpile
        // (covers cores from theft/remnant/donation that couldn't be installed previously)
        DraconisAICoreStockpile.tryInstallStockpiledCores();
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

        // First run after load: sync counters to current values without processing
        // Prevents re-processing all historical donations every save/load cycle
        if (lastAlphaCores < 0 || lastBetaCores < 0 || lastGammaCores < 0) {
            lastAlphaCores = currentAlpha;
            lastBetaCores = currentBeta;
            lastGammaCores = currentGamma;
            Global.getLogger(this.getClass()).info(
                    "Initialized donation tracker - Alpha: " + currentAlpha +
                    ", Beta: " + currentBeta + ", Gamma: " + currentGamma
            );
            return;
        }

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
     * Install donated cores on Draconis facilities
     * Prioritizes administrator positions for Alpha cores
     */
    private void handleDonatedCores(int alphaCount, int betaCount, int gammaCount) {
        List<String> coresToInstall = new ArrayList<>();

        // Build list of cores to install (sorted by priority - Alpha first)
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

        // Find available Draconis markets and industries
        List<MarketAPI> availableAdminMarkets = findAvailableDraconisAdminMarkets();
        List<Industry> availableIndustries = findAvailableDraconisIndustries();
        List<Industry> upgradeableIndustries = findUpgradeableDraconisIndustries();

        Global.getLogger(this.getClass()).info(
                "Found " + availableAdminMarkets.size() + " markets for admin installation"
        );
        Global.getLogger(this.getClass()).info(
                "Found " + availableIndustries.size() + " empty industries"
        );
        Global.getLogger(this.getClass()).info(
                "Found " + upgradeableIndustries.size() + " upgradeable industries"
        );

        if (availableAdminMarkets.isEmpty() && availableIndustries.isEmpty() && upgradeableIndustries.isEmpty()) {
            // No slots available — persist all donated cores to stockpile for future installation
            for (String coreId : coresToInstall) {
                DraconisAICoreStockpile.add(coreId, 1);
            }
            Global.getLogger(this.getClass()).info(
                    "No available Draconis facilities - " + coresToInstall.size() + " donated core(s) added to stockpile"
            );
            return;
        }

        int installed = 0;
        Map<MarketAPI, List<String>> installationMap = new HashMap<>();

        // Process cores in rounds to handle displaced cores without ConcurrentModificationException
        List<String> remainingCores = new ArrayList<>(coresToInstall);
        int maxRounds = 100; // Safety limit to prevent infinite loops
        int round = 0;
        List<String> finalFailedCores = new ArrayList<>(); // Cores that couldn't be installed this donation cycle

        while (!remainingCores.isEmpty() && round < maxRounds) {
            round++;
            List<String> displacedCores = new ArrayList<>();
            List<String> failedCores = new ArrayList<>();

            // Track industries and markets to remove AFTER the loop to avoid ConcurrentModificationException
            Set<Industry> industriesToRemove = new HashSet<>();
            Set<MarketAPI> marketsToRemove = new HashSet<>();

            int installedThisRound = 0;

            for (String coreId : remainingCores) {
                boolean coreInstalled = false;

                // Try administrator installation first for Alpha cores (HIGHEST PRIORITY)
                if (coreId.equals(Commodities.ALPHA_CORE) && !availableAdminMarkets.isEmpty()) {
                    MarketAPI targetMarket = DraconisAICorePriorityManager.pickTargetAdminMarket(availableAdminMarkets);
                    if (targetMarket != null && DraconisAICorePriorityManager.installAICoreAdmin(targetMarket, coreId, DRACONIS)) {
                        installationMap.computeIfAbsent(targetMarket, k -> new ArrayList<>()).add(coreId);
                        marketsToRemove.add(targetMarket);
                        installed++;
                        installedThisRound++;
                        coreInstalled = true;
                        Global.getLogger(this.getClass()).info(
                                "Installed " + coreId + " as administrator at " + targetMarket.getName()
                        );
                    }
                }

                // If not installed as admin, try industry installation
                if (!coreInstalled && (!availableIndustries.isEmpty() || !upgradeableIndustries.isEmpty())) {
                    Industry targetIndustry = DraconisAICorePriorityManager.pickTargetIndustryByPriority(
                            availableIndustries, upgradeableIndustries, coreId
                    );

                    if (targetIndustry != null) {
                        // If this is an upgrade, remove displaced core from the industry
                        String displacedCore = null;
                        if (targetIndustry.getAICoreId() != null && !targetIndustry.getAICoreId().isEmpty()) {
                            displacedCore = targetIndustry.getAICoreId();
                            Global.getLogger(this.getClass()).info(
                                    "Displacing " + displacedCore + " from " + targetIndustry.getCurrentName() +
                                    " with better " + coreId
                            );
                        }

                        if (tryInstallCore(targetIndustry, coreId)) {
                            MarketAPI market = targetIndustry.getMarket();
                            installationMap.computeIfAbsent(market, k -> new ArrayList<>()).add(coreId);

                            // Mark for removal after loop completes
                            industriesToRemove.add(targetIndustry);

                            installed++;
                            installedThisRound++;
                            coreInstalled = true;

                            // If we displaced a core, collect it for the next round
                            if (displacedCore != null) {
                                displacedCores.add(displacedCore);
                                Global.getLogger(this.getClass()).info(
                                        "Re-queuing displaced " + displacedCore + " for installation elsewhere"
                                );
                            }
                        }
                    }
                }

                if (!coreInstalled) {
                    failedCores.add(coreId);
                }
            }

            // NOW safe to remove industries and markets after iteration completes
            availableAdminMarkets.removeAll(marketsToRemove);
            availableIndustries.removeAll(industriesToRemove);
            upgradeableIndustries.removeAll(industriesToRemove);

            // Prepare for next round: only process displaced cores
            remainingCores.clear();
            remainingCores.addAll(displacedCores);

            // If nothing was installed this round and we have failed cores, stop to prevent infinite loop
            if (installedThisRound == 0 && !failedCores.isEmpty()) {
                for (String failedCore : failedCores) {
                    Global.getLogger(this.getClass()).warn(
                            "Could not install " + failedCore + " - no suitable facilities available"
                    );
                }
                finalFailedCores.addAll(failedCores);
                break;
            }
        }

        if (round >= maxRounds) {
            Global.getLogger(this.getClass()).error(
                    "Core installation exceeded maximum rounds - potential infinite loop prevented"
            );
        }

        // Persist donated cores that couldn't be installed — the daily drain in advance() will retry
        if (!finalFailedCores.isEmpty()) {
            for (String failedCore : finalFailedCores) {
                DraconisAICoreStockpile.add(failedCore, 1);
            }
            Global.getLogger(this.getClass()).info(
                    "Persisted " + finalFailedCores.size() + " uninstalled donated core(s) to stockpile"
            );
        }

        Global.getLogger(this.getClass()).info(
                "=== DONATION PROCESSING COMPLETE ==="
        );
        Global.getLogger(this.getClass()).info(
                "Total donated: " + (alphaCount + betaCount + gammaCount)
        );
        Global.getLogger(this.getClass()).info(
                "Successfully installed: " + installed
        );
        Global.getLogger(this.getClass()).info(
                "Installed at " + installationMap.size() + " Draconis facilities"
        );

        // Show notification to player
        if (installed > 0) {
            showDonationNotification();
        }
    }

    /**
     * Find Draconis markets that can receive HYPERCOGNITION skill for administrators
     */
    private List<MarketAPI> findAvailableDraconisAdminMarkets() {
        List<MarketAPI> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Exclude Kori - the capital should not have AI administrator enhancement
            if ("kori_market".equals(market.getId())) continue;

            // Skip if admin already has HYPERCOGNITION
            if (market.getAdmin() != null &&
                market.getAdmin().getStats().getSkillLevel(com.fs.starfarer.api.impl.campaign.ids.Skills.HYPERCOGNITION) > 0) {
                continue; // Already enhanced with Alpha Core
            }

            available.add(market);
        }

        return available;
    }

    /**
     * Find Draconis industries WITHOUT AI cores
     */
    private List<Industry> findAvailableDraconisIndustries() {
        List<Industry> available = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Defensive copy to prevent ConcurrentModificationException
            for (Industry industry : new ArrayList<>(market.getIndustries())) {
                // Only add if NO core installed
                if (industry.getAICoreId() != null && !industry.getAICoreId().isEmpty()) continue;
                // Skip if not functional
                if (!industry.isFunctional()) continue;

                available.add(industry);
            }
        }

        return available;
    }

    /**
     * Find Draconis industries WITH lower-tier cores that can be upgraded
     */
    private List<Industry> findUpgradeableDraconisIndustries() {
        List<Industry> upgradeable = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Defensive copy to prevent ConcurrentModificationException
            for (Industry industry : new ArrayList<>(market.getIndustries())) {
                // Only add if has a core that could be upgraded
                String currentCore = industry.getAICoreId();
                if (currentCore == null || currentCore.isEmpty()) continue;

                // Can't upgrade an Alpha core (it's already the best)
                if (currentCore.equals(Commodities.ALPHA_CORE)) continue;

                // Skip if not functional
                if (!industry.isFunctional()) continue;

                upgradeable.add(industry);
            }
        }

        return upgradeable;
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
    private void showDonationNotification() {
        Global.getSector().getCampaignUI().addMessage(
                "Your donated AI cores have been put to use by Draconis Alliance forces.",
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor()
        );

        Global.getLogger(this.getClass()).info("Sent donation notification to player");
    }

    /**
     * Public API: Manually record a core donation.
     * Call this when player donates cores through bar events, etc.
     * Delegates to DraconisAICoreStockpile for canonical persistence.
     */
    public static void recordCoreDonation(String coreId, int count) {
        if (count <= 0) return;
        DraconisAICoreStockpile.add(coreId, count);
        Global.getLogger(DraconisAICoreDonationListener.class).info(
                "Recorded donation: " + count + "x " + coreId +
                " (stockpile total: " + DraconisAICoreStockpile.getCount(coreId) + ")"
        );
        DraconisAICoreStockpile.tryInstallStockpiledCores();
    }
}