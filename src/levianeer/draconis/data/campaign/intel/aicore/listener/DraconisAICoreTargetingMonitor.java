package levianeer.draconis.data.campaign.intel.aicore.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import levianeer.draconis.data.campaign.intel.aicore.intel.DraconisAICoreIntel;

import java.util.*;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

public class DraconisAICoreTargetingMonitor implements EveryFrameScript {

    private static final float SCAN_INTERVAL = 15f;
    private static final float INTEL_DECAY_TIME = 60f;
    private static final int MIN_MARKET_SIZE = 4;

    public static final String AI_CORE_TARGET_FLAG = "$draconis_aiCoreTarget";
    public static final String AI_CORE_COUNT_FLAG = "$draconis_aiCoreCount";
    public static final String AI_CORE_VALUE_FLAG = "$draconis_aiCoreValue";
    public static final String AI_CORE_ALPHA_COUNT_FLAG = "$draconis_aiCoreAlphaCount";
    public static final String AI_CORE_BETA_COUNT_FLAG = "$draconis_aiCoreBetaCount";
    public static final String AI_CORE_GAMMA_COUNT_FLAG = "$draconis_aiCoreGammaCount";
    public static final String LAST_SCAN_DATE_FLAG = "$draconis_lastScanDate";
    public static final String INTEL_REPORTED_FLAG = "$draconis_intelReported";

    private float daysElapsed = 0f;
    private final boolean hasNexerelin;
    private boolean firstScanDone = false;

    private final Queue<PendingIntelReport> intelQueue = new LinkedList<>();
    private float intelReleaseTimer = 0f;
    private static final float INTEL_RELEASE_INTERVAL = 1.5f;

    public DraconisAICoreTargetingMonitor() {
        hasNexerelin = Global.getSettings().getModManager().isModEnabled("nexerelin");

        // CRITICAL DEBUG LOG
        Global.getLogger(this.getClass()).info("=== DraconisAICoreTargetingMonitor CONSTRUCTOR ===");
        Global.getLogger(this.getClass()).info("Nexerelin enabled: " + hasNexerelin);
    }

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
        if (!hasNexerelin) {
            // This should only log once to avoid spam
            if (!firstScanDone) {
                Global.getLogger(this.getClass()).warn("AI Core Monitor not running - Nexerelin not detected!");
                firstScanDone = true;
            }
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;
        intelReleaseTimer += days;

        if (intelReleaseTimer >= INTEL_RELEASE_INTERVAL && !intelQueue.isEmpty()) {
            intelReleaseTimer = 0f;
            releaseNextIntelReport();
        }

        if (daysElapsed < SCAN_INTERVAL) return;

        // CRITICAL: Log that scan is starting
        if (!firstScanDone) {
            Global.getLogger(this.getClass()).info("=== FIRST AI CORE SCAN STARTING ===");
            firstScanDone = true;
        } else {
            Global.getLogger(this.getClass()).info("=== AI CORE SCAN CYCLE ===");
        }

        daysElapsed = 0f;
        scanMarketsForAICores();
        cleanupExpiredIntel();
    }

    private void scanMarketsForAICores() {
        int totalMarkets = 0;
        int skippedDraconis = 0;
        int skippedSmall = 0;
        int skippedHidden = 0;
        int scannedMarkets = 0;
        int marketsWithCores = 0;
        int debugCounter = 0;

        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        Global.getLogger(this.getClass()).info("Scanning " + allMarkets.size() + " total markets...");

        for (MarketAPI market : allMarkets) {
            totalMarkets++;

            if (isDraconisOrAlly(market, debugCounter < 10)) {
                skippedDraconis++;
                debugCounter++;
                continue;
            }

            if (market.getSize() < MIN_MARKET_SIZE) {
                skippedSmall++;
                continue;
            }

            if (market.isHidden() || !market.isInEconomy()) {
                skippedHidden++;
                continue;
            }

            scannedMarkets++;

            AICoreIntelData intelData = scanMarketForCores(market);

            if (intelData.totalCores > 0) {
                marketsWithCores++;

                Global.getLogger(this.getClass()).info(
                        String.format("FOUND CORES at %s (%s): %d total (%d alpha, %d beta, %d gamma)",
                                market.getName(),
                                market.getFaction().getDisplayName(),
                                intelData.totalCores,
                                intelData.alphaCores,
                                intelData.betaCores,
                                intelData.gammaCores)
                );

                // Only queue if not already reported AND not already in queue
                if (!market.getMemoryWithoutUpdate().getBoolean(INTEL_REPORTED_FLAG)
                        && !isMarketInQueue(market)) {
                    queueIntelReport(market, intelData);
                }
            }
        }

        Global.getLogger(this.getClass()).info(
                String.format("=== SCAN COMPLETE: %d total markets | %d scanned | %d with cores | Skipped: %d ally, %d small, %d hidden ===",
                        totalMarkets, scannedMarkets, marketsWithCores,
                        skippedDraconis, skippedSmall, skippedHidden)
        );
    }

    /**
     * Check if a market is already queued for intel report
     */
    private boolean isMarketInQueue(MarketAPI market) {
        for (PendingIntelReport report : intelQueue) {
            if (report.market.getId().equals(market.getId())) {
                return true;
            }
        }
        return false;
    }

    private AICoreIntelData scanMarketForCores(MarketAPI market) {
        AICoreIntelData data = new AICoreIntelData();

        List<Industry> industries = market.getIndustries();
        if (industries == null || industries.isEmpty()) {
            return data;
        }

        for (Industry industry : industries) {
            if (industry == null) continue;

            String coreId = industry.getAICoreId();
            if (coreId != null && !coreId.isEmpty()) {
                data.totalCores++;

                switch (coreId) {
                    case Commodities.ALPHA_CORE:
                        data.alphaCores++;
                        break;
                    case Commodities.BETA_CORE:
                        data.betaCores++;
                        break;
                    case Commodities.GAMMA_CORE:
                        data.gammaCores++;
                        break;
                }

                data.industries.add(industry.getCurrentName());
            }
        }

        return data;
    }

    private void flagMarketAsTarget(MarketAPI market, AICoreIntelData data, long timestamp) {
        market.getMemoryWithoutUpdate().set(AI_CORE_TARGET_FLAG, true);
        market.getMemoryWithoutUpdate().set(AI_CORE_COUNT_FLAG, data.totalCores);
        market.getMemoryWithoutUpdate().set(LAST_SCAN_DATE_FLAG, timestamp);

        float strategicValue = (data.alphaCores * 15f) + (data.betaCores * 10f) + (data.gammaCores * 5f);
        market.getMemoryWithoutUpdate().set(AI_CORE_VALUE_FLAG, strategicValue);

        market.getMemoryWithoutUpdate().set(AI_CORE_ALPHA_COUNT_FLAG, data.alphaCores);
        market.getMemoryWithoutUpdate().set(AI_CORE_BETA_COUNT_FLAG, data.betaCores);
        market.getMemoryWithoutUpdate().set(AI_CORE_GAMMA_COUNT_FLAG, data.gammaCores);

        if (!market.hasCondition("draconis_ai_core_detected")) {
            market.addCondition("draconis_ai_core_detected");
        }

        Global.getLogger(this.getClass()).info(
                String.format("Flagged %s as AI core target: %d cores (value: %.1f)",
                        market.getName(), data.totalCores, strategicValue)
        );
    }

    private void createIntelReport(MarketAPI market, AICoreIntelData data) {
        // Apply reputation penalty first
        applyReputationPenalty(market, data);

        // Create the intel
        DraconisAICoreIntel intel = new DraconisAICoreIntel(market, data);
        Global.getSector().getIntelManager().addIntel(intel);

        Global.getLogger(this.getClass()).info(
                "Generated intel report for AI cores at " + market.getName()
        );
    }

    /**
     * Applies reputation penalty when AI cores are discovered
     */
    private void applyReputationPenalty(MarketAPI market, AICoreIntelData data) {
        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI targetFaction = market.getFaction();

        // Calculate reputation loss based on core quality and quantity
        // Alpha cores are the most threatening, gamma cores least
        float repLoss = -((data.alphaCores * 0.05f) + (data.betaCores * 0.03f) + (data.gammaCores * 0.02f));

        // Apply the reputation change
        draconisFaction.adjustRelationship(targetFaction.getId(), repLoss);

        float newRep = draconisFaction.getRelationship(targetFaction.getId());

        Global.getLogger(this.getClass()).info(
                String.format("Applied reputation penalty: %s vs %s: %.3f (new rep: %.2f)",
                        draconisFaction.getDisplayName(),
                        targetFaction.getDisplayName(),
                        repLoss,
                        newRep)
        );
    }

    private void clearMarketFlags(MarketAPI market) {
        if (market.getMemoryWithoutUpdate().contains(AI_CORE_TARGET_FLAG)) {
            market.getMemoryWithoutUpdate().unset(AI_CORE_TARGET_FLAG);
            market.getMemoryWithoutUpdate().unset(AI_CORE_COUNT_FLAG);
            market.getMemoryWithoutUpdate().unset(LAST_SCAN_DATE_FLAG);
            market.getMemoryWithoutUpdate().unset(INTEL_REPORTED_FLAG);
            market.getMemoryWithoutUpdate().unset(AI_CORE_VALUE_FLAG);
            market.getMemoryWithoutUpdate().unset(AI_CORE_ALPHA_COUNT_FLAG);
            market.getMemoryWithoutUpdate().unset(AI_CORE_BETA_COUNT_FLAG);
            market.getMemoryWithoutUpdate().unset(AI_CORE_GAMMA_COUNT_FLAG);

            if (market.hasCondition("draconis_ai_core_detected")) {
                market.removeCondition("draconis_ai_core_detected");
            }

            intelQueue.removeIf(report -> report.market == market);
        }
    }

    private void cleanupExpiredIntel() {
        long currentDate = Global.getSector().getClock().getTimestamp();
        long expirationThreshold = (long)(INTEL_DECAY_TIME * Global.getSector().getClock().getSecondsPerDay());

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getMemoryWithoutUpdate().contains(LAST_SCAN_DATE_FLAG)) continue;

            long lastScan = market.getMemoryWithoutUpdate().getLong(LAST_SCAN_DATE_FLAG);

            if (currentDate - lastScan > expirationThreshold) {
                clearMarketFlags(market);
                Global.getLogger(this.getClass()).info(
                        "Expired AI core intelligence for " + market.getName()
                );
            }
        }
    }

    private void queueIntelReport(MarketAPI market, AICoreIntelData data) {
        int priority = (data.alphaCores * 3) + (data.betaCores * 2) + data.gammaCores;

        PendingIntelReport report = new PendingIntelReport(market, data, priority);
        intelQueue.add(report);

        List<PendingIntelReport> sortedList = new ArrayList<>(intelQueue);
        sortedList.sort((a, b) -> Integer.compare(b.priority, a.priority));
        intelQueue.clear();
        intelQueue.addAll(sortedList);

        Global.getLogger(this.getClass()).info(
                String.format("Queued intel report for %s (priority %d, queue size: %d)",
                        market.getName(), priority, intelQueue.size())
        );
    }

    private void releaseNextIntelReport() {
        PendingIntelReport report = intelQueue.poll();
        if (report == null) return;

        // Skip if already reported (prevents duplicates from old queue)
        if (report.market.getMemoryWithoutUpdate().getBoolean(INTEL_REPORTED_FLAG)) {
            Global.getLogger(this.getClass()).info(
                    "Skipping intel report for " + report.market.getName() + " (already reported)"
            );
            return;
        }

        // Re-scan the market to verify cores still exist
        AICoreIntelData currentData = scanMarketForCores(report.market);

        if (currentData.totalCores == 0) {
            Global.getLogger(this.getClass()).info(
                    "Skipping intel report for " + report.market.getName() + " (cores no longer present)"
            );
            return;
        }

        // NOW apply the market flags and condition (delayed until intel is released)
        long currentDate = Global.getSector().getClock().getTimestamp();
        flagMarketAsTarget(report.market, currentData, currentDate);

        // Mark as reported so we don't queue it again
        report.market.getMemoryWithoutUpdate().set(INTEL_REPORTED_FLAG, true);

        // Create intel report (this will also apply reputation penalty)
        createIntelReport(report.market, currentData);

        Global.getLogger(this.getClass()).info(
                String.format("Released intel report for %s (%d reports remaining in queue)",
                        report.market.getName(), intelQueue.size())
        );
    }

    private boolean isDraconisOrAlly(MarketAPI market, boolean debug) {
        String factionId = market.getFactionId();

        // Direct Draconis check
        if (DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId)) {
            if (debug) {
                Global.getLogger(this.getClass()).info(
                        String.format("SKIPPED (Direct Draconis): %s (%s)", market.getName(), factionId)
                );
            }
            return true;
        }

        // Check reputation - use isAtWorst() to find actual allies
        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI marketFaction = market.getFaction();

        float rep = draconisFaction.getRelationship(marketFaction.getId());
        boolean isAlly = draconisFaction.isAtWorst(marketFaction, RepLevel.FAVORABLE); // FIXED: was isAtBest

        // DEBUG: Log to see what's happening
        if (debug) {
            Global.getLogger(this.getClass()).info(
                    String.format("Market: %s (%s) | Rep: %.2f | isAtWorst(FAVORABLE): %s | Action: %s",
                            market.getName(), factionId, rep, isAlly,
                            isAlly ? "SKIPPED (Ally)" : "SCAN")
            );
        }

        return isAlly;
    }

    public static class AICoreIntelData {
        public int totalCores = 0;
        public int alphaCores = 0;
        public int betaCores = 0;
        public int gammaCores = 0;
        public List<String> industries = new ArrayList<>();
    }

    private record PendingIntelReport(MarketAPI market, AICoreIntelData data, int priority) {
    }
}