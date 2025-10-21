package levianeer.draconis.data.campaign.intel.aicore.scanner;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.campaign.ai.MilitaryAIModule;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import levianeer.draconis.data.campaign.ai.concerns.DraconisHighValueTargetConcern;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Scans all markets periodically and marks the SINGLE most valuable AI core target
 */
public class DraconisSingleTargetScanner implements EveryFrameScript {

    private static final Logger log = Global.getLogger(DraconisSingleTargetScanner.class);

    private static final float SCAN_INTERVAL = 30f; // Scan every 30 days
    private static final int MIN_MARKET_SIZE = 4;

    // Memory flags
    public static final String HIGH_VALUE_TARGET_FLAG = "$draconis_highValueAITarget";
    public static final String TARGET_CORE_VALUE_FLAG = "$draconis_targetCoreValue";
    public static final String TARGET_ALPHA_COUNT_FLAG = "$draconis_targetAlphaCount";
    public static final String TARGET_BETA_COUNT_FLAG = "$draconis_targetBetaCount";
    public static final String TARGET_GAMMA_COUNT_FLAG = "$draconis_targetGammaCount";

    private float daysElapsed = 0f;
    private MarketAPI currentTarget = null;
    private boolean firstScan = true;

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
        daysElapsed += days;

        if (daysElapsed < SCAN_INTERVAL) return;
        daysElapsed = 0f;

        if (firstScan) {
            log.info("=== DRACONIS AI CORE SCANNER: FIRST SCAN ===");
            firstScan = false;
        } else {
            log.info("=== DRACONIS AI CORE SCANNER: PERIODIC SCAN ===");
        }

        scanAndMarkBestTarget();
    }

    /**
     * Main scanning logic - finds and marks the best target
     * Made public for testing purposes
     */
    public void scanAndMarkBestTarget() {
        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);

        if (draconisFaction == null) {
            log.error("Draconis faction not found!");
            return;
        }

        List<MarketCandidate> candidates = new ArrayList<>();

        int totalMarkets = 0;
        int skippedDraconis = 0;
        int skippedSmall = 0;
        int skippedHidden = 0;
        int skippedFriendly = 0;
        int skippedNoCores = 0;

        // Scan all markets
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            totalMarkets++;

            // Skip Draconis markets
            if (isDraconisMarket(market)) {
                skippedDraconis++;
                continue;
            }

            // Skip hidden/invalid markets
            if (market.isHidden() || !market.isInEconomy()) {
                skippedHidden++;
                continue;
            }

            // Skip small markets
            if (market.getSize() < MIN_MARKET_SIZE) {
                skippedSmall++;
                continue;
            }

            // Must be hostile or at least unfriendly
            if (!market.getFaction().isHostileTo(draconisFaction)) {
                // Also skip if friendly (rep >= 0.25)
                if (market.getFaction().getRelationship(DRACONIS) >= 0.25f) {
                    skippedFriendly++;
                    continue;
                }
            }

            // Scan for AI cores
            CoreData cores = scanMarketCores(market);

            if (cores.totalCores == 0) {
                skippedNoCores++;
                continue;
            }

            float value = calculateValue(cores);
            candidates.add(new MarketCandidate(market, cores, value));

            log.info(String.format("  Candidate: %s (%s, size %d) - %d cores (A:%d B:%d G:%d), value: %.1f",
                    market.getName(),
                    market.getFaction().getDisplayName(),
                    market.getSize(),
                    cores.totalCores,
                    cores.alphaCores,
                    cores.betaCores,
                    cores.gammaCores,
                    value));
        }

        log.info(String.format("Scan summary: %d total markets | Skipped: %d Draconis, %d small, %d hidden, %d friendly, %d no cores | %d valid candidates",
                totalMarkets, skippedDraconis, skippedSmall, skippedHidden, skippedFriendly, skippedNoCores, candidates.size()));

        if (candidates.isEmpty()) {
            log.info("No valid AI core targets found - clearing any existing target");
            clearCurrentTarget();
            return;
        }

        // Sort by value (highest first)
        candidates.sort((a, b) -> Float.compare(b.value, a.value));

        // Log top 5 candidates
        log.info("Top candidates by value:");
        for (int i = 0; i < Math.min(5, candidates.size()); i++) {
            MarketCandidate candidate = candidates.get(i);
            log.info(String.format("  %d. %s - value %.1f (%d cores)",
                    i + 1, candidate.market.getName(), candidate.value, candidate.cores.totalCores));
        }

        MarketCandidate bestCandidate = candidates.get(0);

        log.info(String.format(">>> BEST TARGET: %s - %d cores (value: %.1f)",
                bestCandidate.market.getName(),
                bestCandidate.cores.totalCores,
                bestCandidate.value));

        // Clear old target if different
        if (currentTarget != null && currentTarget != bestCandidate.market) {
            log.info("Clearing old target: " + currentTarget.getName());
            clearMarketFlags(currentTarget);
            removeExistingConcern(currentTarget);
        }

        // Mark new target
        markAsHighValueTarget(bestCandidate.market, bestCandidate.cores, bestCandidate.value);
        currentTarget = bestCandidate.market;

        // Create Strategic AI concern for the new target
        createStrategicConcern(bestCandidate.market);
    }

    private CoreData scanMarketCores(MarketAPI market) {
        CoreData data = new CoreData();

        List<Industry> industries = market.getIndustries();
        if (industries == null) return data;

        for (Industry industry : industries) {
            if (industry == null) continue;

            String coreId = industry.getAICoreId();
            if (coreId == null || coreId.isEmpty()) continue;

            data.totalCores++;

            switch (coreId) {
                case Commodities.ALPHA_CORE:
                    data.alphaCores++;
                    data.industries.add(industry.getCurrentName() + " (Alpha)");
                    break;
                case Commodities.BETA_CORE:
                    data.betaCores++;
                    data.industries.add(industry.getCurrentName() + " (Beta)");
                    break;
                case Commodities.GAMMA_CORE:
                    data.gammaCores++;
                    data.industries.add(industry.getCurrentName() + " (Gamma)");
                    break;
            }
        }

        return data;
    }

    private float calculateValue(CoreData cores) {
        // Alpha cores are most valuable, gamma least
        return (cores.alphaCores * 15f) + (cores.betaCores * 10f) + (cores.gammaCores * 5f);
    }

    private void markAsHighValueTarget(MarketAPI market, CoreData cores, float value) {
        market.getMemoryWithoutUpdate().set(HIGH_VALUE_TARGET_FLAG, true);
        market.getMemoryWithoutUpdate().set(TARGET_CORE_VALUE_FLAG, value);
        market.getMemoryWithoutUpdate().set(TARGET_ALPHA_COUNT_FLAG, cores.alphaCores);
        market.getMemoryWithoutUpdate().set(TARGET_BETA_COUNT_FLAG, cores.betaCores);
        market.getMemoryWithoutUpdate().set(TARGET_GAMMA_COUNT_FLAG, cores.gammaCores);

        // Add condition
        if (!market.hasCondition("draconis_high_value_target")) {
            market.addCondition("draconis_high_value_target");
            log.info("Added 'Draconis Priority Target' condition to " + market.getName());
        }

        log.info(String.format("Marked %s as high-value target:", market.getName()));
        log.info(String.format("  Value: %.1f | Cores: %d Alpha, %d Beta, %d Gamma",
                value, cores.alphaCores, cores.betaCores, cores.gammaCores));

        if (!cores.industries.isEmpty()) {
            log.info("  Industries with AI cores:");
            for (String industryName : cores.industries) {
                log.info("    - " + industryName);
            }
        }
    }

    /**
     * Creates a Strategic AI concern for the marked target
     */
    private void createStrategicConcern(MarketAPI targetMarket) {
        log.info("=== CREATING STRATEGIC AI CONCERN ===");

        StrategicAI draconisAI = StrategicAI.getAI(DRACONIS);
        if (draconisAI == null) {
            log.warn("Draconis Strategic AI not found - concern cannot be created");
            log.warn("Strategic AI may not be initialized yet, or Draconis is not a valid faction");
            return;
        }

        MilitaryAIModule milModule = draconisAI.getMilModule();
        if (milModule == null) {
            log.error("Military AI module is null!");
            return;
        }

        // Check if we already have a concern for this market
        boolean alreadyHasConcern = false;
        for (StrategicConcern concern : milModule.getCurrentConcerns()) {
            if (concern instanceof DraconisHighValueTargetConcern hvConcern) {
                if (hvConcern.getMarket() == targetMarket) {
                    log.info("Strategic AI already has concern for " + targetMarket.getName());
                    alreadyHasConcern = true;
                    break;
                }
            }
        }

        if (alreadyHasConcern) {
            log.info("Skipping concern creation - already exists");
            return;
        }

        // Get the concern definition
        StrategicDefManager.StrategicConcernDef def =
                StrategicDefManager.getConcernDef("draconisHighValueTarget");

        if (def == null) {
            log.error("Concern definition 'draconisHighValueTarget' not found in strategicAIConfig.json!");
            log.error("Make sure your JSON file is properly loaded and the concern ID matches");
            return;
        }

        // Instantiate the concern
        DraconisHighValueTargetConcern concern =
                (DraconisHighValueTargetConcern) StrategicDefManager.instantiateConcern(def);

        if (concern == null) {
            log.error("Failed to instantiate DraconisHighValueTargetConcern!");
            return;
        }

        // Initialize it
        concern.setAI(draconisAI, milModule);

        // Try to generate
        log.info("Attempting to generate concern for " + targetMarket.getName() + "...");
        boolean generated = concern.generate();

        if (generated) {
            milModule.addConcern(concern);
            log.info(">>> SUCCESSFULLY CREATED AND ADDED CONCERN <<<");
            log.info("Target: " + targetMarket.getName());
            log.info("Priority: " + concern.getPriorityFloat());
        } else {
            log.warn("Concern generation returned false - concern NOT added");
            log.warn("Check DraconisHighValueTargetConcern.generate() method for why it failed");
        }
    }

    /**
     * Removes any existing Strategic AI concern for the given market
     */
    private void removeExistingConcern(MarketAPI market) {
        StrategicAI draconisAI = StrategicAI.getAI(DRACONIS);
        if (draconisAI == null) return;

        MilitaryAIModule milModule = draconisAI.getMilModule();
        if (milModule == null) return;

        // Find and remove any existing concerns for this market
        List<StrategicConcern> toRemove = new ArrayList<>();
        for (StrategicConcern concern : milModule.getCurrentConcerns()) {
            if (concern instanceof DraconisHighValueTargetConcern hvConcern) {
                if (hvConcern.getMarket() == market) {
                    toRemove.add(concern);
                }
            }
        }

        for (StrategicConcern concern : toRemove) {
            milModule.removeConcern(concern);
            log.info("Removed old Strategic AI concern for " + market.getName());
        }
    }

    private void clearCurrentTarget() {
        if (currentTarget != null) {
            clearMarketFlags(currentTarget);
            removeExistingConcern(currentTarget);
            currentTarget = null;
        }
    }

    private void clearMarketFlags(MarketAPI market) {
        market.getMemoryWithoutUpdate().unset(HIGH_VALUE_TARGET_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_CORE_VALUE_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_ALPHA_COUNT_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_BETA_COUNT_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_GAMMA_COUNT_FLAG);

        if (market.hasCondition("draconis_high_value_target")) {
            market.removeCondition("draconis_high_value_target");
            log.info("Removed 'Draconis Priority Target' condition from " + market.getName());
        }
    }

    /**
     * Clears target flags after a successful raid
     * Called by DraconisTargetedRaidMonitor
     */
    public static void clearTargetAfterRaid(MarketAPI market) {
        log.info("=== CLEARING TARGET AFTER SUCCESSFUL RAID ===");
        log.info("Market: " + market.getName());

        market.getMemoryWithoutUpdate().unset(HIGH_VALUE_TARGET_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_CORE_VALUE_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_ALPHA_COUNT_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_BETA_COUNT_FLAG);
        market.getMemoryWithoutUpdate().unset(TARGET_GAMMA_COUNT_FLAG);

        if (market.hasCondition("draconis_high_value_target")) {
            market.removeCondition("draconis_high_value_target");
            log.info("Removed condition - scanner will select new target on next cycle");
        }

        // Also remove the Strategic AI concern
        StrategicAI draconisAI = StrategicAI.getAI(DRACONIS);
        if (draconisAI != null) {
            MilitaryAIModule milModule = draconisAI.getMilModule();
            if (milModule != null) {
                List<StrategicConcern> toRemove = new ArrayList<>();
                for (StrategicConcern concern : milModule.getCurrentConcerns()) {
                    if (concern instanceof DraconisHighValueTargetConcern hvConcern) {
                        if (hvConcern.getMarket() == market) {
                            toRemove.add(concern);
                        }
                    }
                }

                for (StrategicConcern concern : toRemove) {
                    milModule.removeConcern(concern);
                    log.info("Removed Strategic AI concern after successful raid");
                }
            }
        }
    }

    private boolean isDraconisMarket(MarketAPI market) {
        String factionId = market.getFactionId();
        return DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId);
    }

    private static class CoreData {
        int totalCores = 0;
        int alphaCores = 0;
        int betaCores = 0;
        int gammaCores = 0;
        List<String> industries = new ArrayList<>();
    }

    private record MarketCandidate(MarketAPI market, CoreData cores, float value) {
    }
}