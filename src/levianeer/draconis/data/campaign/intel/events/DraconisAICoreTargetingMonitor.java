package levianeer.draconis.data.campaign.intel.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.*;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Monitors enemy markets for AI cores and creates intelligence reports
 * that bias Draconis raids toward AI core-rich targets
 */
public class DraconisAICoreTargetingMonitor implements EveryFrameScript {

    private static final float SCAN_INTERVAL = 15f; // Scan every 15 days
    private static final float INTEL_DECAY_TIME = 60f; // Intel expires after 60 days
    private static final int MIN_MARKET_SIZE = 4; // Only scan size 4+ markets

    // Memory flags for tracking
    public static final String AI_CORE_TARGET_FLAG = "$draconis_aiCoreTarget";
    public static final String AI_CORE_COUNT_FLAG = "$draconis_aiCoreCount";
    public static final String LAST_SCAN_DATE_FLAG = "$draconis_lastScanDate";
    public static final String INTEL_REPORTED_FLAG = "$draconis_intelReported";

    private float daysElapsed = 0f;
    private boolean hasNexerelin = false;

    // Intel notification queue to prevent spam
    private final Queue<PendingIntelReport> intelQueue = new LinkedList<>();
    private float intelReleaseTimer = 0f;
    private static final float INTEL_RELEASE_INTERVAL = 1.5f; // Release 1 intel every 1.5 days

    public DraconisAICoreTargetingMonitor() {
        hasNexerelin = Global.getSettings().getModManager().isModEnabled("nexerelin");
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
        if (!hasNexerelin) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;
        intelReleaseTimer += days;

        // Release queued intel reports gradually
        if (intelReleaseTimer >= INTEL_RELEASE_INTERVAL && !intelQueue.isEmpty()) {
            intelReleaseTimer = 0f;
            releaseNextIntelReport();
        }

        if (daysElapsed < SCAN_INTERVAL) return;
        daysElapsed = 0f;

        scanMarketsForAICores();
        cleanupExpiredIntel();
    }

    /**
     * Main scanning routine - checks all enemy markets for AI cores
     */
    private void scanMarketsForAICores() {
        long currentDate = Global.getSector().getClock().getTimestamp();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            // Skip Draconis/allied markets
            if (isDraconisOrAlly(market)) continue;

            // Skip small markets
            if (market.getSize() < MIN_MARKET_SIZE) continue;

            // Skip hidden/decivilized markets
            if (market.isHidden() || !market.isInEconomy()) continue;

            // Count actual installed AI cores
            AICoreIntelData intelData = scanMarketForCores(market);

            if (intelData.totalCores > 0) {
                flagMarketAsTarget(market, intelData, currentDate);

                // Queue intel event for gradual release instead of creating immediately
                if (!market.getMemoryWithoutUpdate().getBoolean(INTEL_REPORTED_FLAG)) {
                    queueIntelReport(market, intelData);
                    market.getMemoryWithoutUpdate().set(INTEL_REPORTED_FLAG, true);
                }
            } else {
                // Clear flags if cores were removed
                clearMarketFlags(market);
            }
        }
    }

    /**
     * Scans a market and counts AI cores by type
     */
    private AICoreIntelData scanMarketForCores(MarketAPI market) {
        AICoreIntelData data = new AICoreIntelData();

        for (Industry industry : market.getIndustries()) {
            if (industry == null || !industry.isFunctional()) continue;

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

    /**
     * Flags a market as a high-priority target and applies market condition
     */
    private void flagMarketAsTarget(MarketAPI market, AICoreIntelData data, long timestamp) {
        market.getMemoryWithoutUpdate().set(AI_CORE_TARGET_FLAG, true);
        market.getMemoryWithoutUpdate().set(AI_CORE_COUNT_FLAG, data.totalCores);
        market.getMemoryWithoutUpdate().set(LAST_SCAN_DATE_FLAG, timestamp);

        // Apply custom market condition if not present
        if (!market.hasCondition("draconis_ai_core_detected")) {
            market.addCondition("draconis_ai_core_detected");
        }

        // Add vengeance points if Nexerelin is present
        if (hasNexerelin) {
            addVengeancePoints(market, data);
        }

        Global.getLogger(this.getClass()).info(
                String.format("Flagged %s as AI core target: %d cores (%d alpha, %d beta, %d gamma)",
                        market.getName(), data.totalCores, data.alphaCores, data.betaCores, data.gammaCores)
        );
    }

    /**
     * Adds vengeance points to encourage raids on this market
     */
    private void addVengeancePoints(MarketAPI market, AICoreIntelData data) {
        try {
            // Use reflection to access Nexerelin's RevengeanceManager
            Class<?> revengeManagerClass = Class.forName("exerelin.campaign.RevengeanceManager");
            Object manager = revengeManagerClass.getMethod("getManager").invoke(null);

            // Calculate points based on core value (alpha = 15, beta = 10, gamma = 5)
            float points = (data.alphaCores * 15f) + (data.betaCores * 10f) + (data.gammaCores * 5f);

            // Add vengeance points from Draconis toward this faction
            revengeManagerClass.getMethod("modifyVengeance", String.class, String.class, float.class)
                    .invoke(manager, DRACONIS, market.getFactionId(), points);

            Global.getLogger(this.getClass()).info(
                    String.format("Added %.1f vengeance points from Draconis toward %s for AI cores at %s",
                            points, market.getFaction().getDisplayName(), market.getName())
            );

        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn(
                    "Failed to add vengeance points (Nexerelin not available or incompatible): " + e.getMessage()
            );
        }
    }

    /**
     * Creates an intel report for newly discovered AI core markets
     */
    private void createIntelReport(MarketAPI market, AICoreIntelData data) {
        DraconisAICoreIntel intel = new DraconisAICoreIntel(market, data);
        Global.getSector().getIntelManager().addIntel(intel);

        Global.getLogger(this.getClass()).info(
                "Generated intel report for AI cores at " + market.getName()
        );
    }

    /**
     * Clears targeting flags from a market
     */
    private void clearMarketFlags(MarketAPI market) {
        if (market.getMemoryWithoutUpdate().contains(AI_CORE_TARGET_FLAG)) {
            market.getMemoryWithoutUpdate().unset(AI_CORE_TARGET_FLAG);
            market.getMemoryWithoutUpdate().unset(AI_CORE_COUNT_FLAG);
            market.getMemoryWithoutUpdate().unset(LAST_SCAN_DATE_FLAG);
            market.getMemoryWithoutUpdate().unset(INTEL_REPORTED_FLAG);

            if (market.hasCondition("draconis_ai_core_detected")) {
                market.removeCondition("draconis_ai_core_detected");
            }

            // Remove from intel queue if present
            intelQueue.removeIf(report -> report.market == market);
        }
    }

    /**
     * Removes expired intelligence data
     */
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

    /**
     * Queues an intel report for gradual release
     */
    private void queueIntelReport(MarketAPI market, AICoreIntelData data) {
        // Calculate priority (higher value cores = higher priority)
        int priority = (data.alphaCores * 3) + (data.betaCores * 2) + data.gammaCores;

        PendingIntelReport report = new PendingIntelReport(market, data, priority);
        intelQueue.add(report);

        // Sort queue by priority (highest first)
        List<PendingIntelReport> sortedList = new ArrayList<>(intelQueue);
        sortedList.sort((a, b) -> Integer.compare(b.priority, a.priority));
        intelQueue.clear();
        intelQueue.addAll(sortedList);

        Global.getLogger(this.getClass()).info(
                String.format("Queued intel report for %s (priority %d, queue size: %d)",
                        market.getName(), priority, intelQueue.size())
        );
    }

    /**
     * Releases the next intel report from the queue
     */
    private void releaseNextIntelReport() {
        PendingIntelReport report = intelQueue.poll();
        if (report == null) return;

        // Verify market still has cores (could have been removed since queuing)
        if (!report.market.getMemoryWithoutUpdate().getBoolean(AI_CORE_TARGET_FLAG)) {
            Global.getLogger(this.getClass()).info(
                    "Skipping intel report for " + report.market.getName() + " (cores no longer present)"
            );
            return;
        }

        createIntelReport(report.market, report.data);

        Global.getLogger(this.getClass()).info(
                String.format("Released intel report for %s (%d reports remaining in queue)",
                        report.market.getName(), intelQueue.size())
        );
    }

    /**
     * Checks if a market belongs to Draconis or an ally
     */
    private boolean isDraconisOrAlly(MarketAPI market) {
        String factionId = market.getFactionId();

        // Check if it's directly Draconis
        if (DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId)) {
            return true;
        }

        // Check if faction is allied with Draconis
        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI marketFaction = market.getFaction();

        return draconisFaction.isAtBest(marketFaction, RepLevel.getLevelFor(-0.5f)); // Friendly or better
    }

    /**
     * Data class for AI core intelligence
     */
    public static class AICoreIntelData {
        public int totalCores = 0;
        public int alphaCores = 0;
        public int betaCores = 0;
        public int gammaCores = 0;
        public List<String> industries = new ArrayList<>();
    }

    /**
         * Data class for pending intel reports
         */
        private record PendingIntelReport(MarketAPI market, AICoreIntelData data, int priority) {
    }
}