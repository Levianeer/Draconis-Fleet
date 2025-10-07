package levianeer.draconis.data.campaign.intel.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

import java.util.HashSet;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Monitors Nexerelin raids by Draconis Alliance factions against player colonies
 * and triggers AI core theft when raids complete successfully
 */
public class NexerelinRaidListener implements EveryFrameScript {

    private static final float CHECK_INTERVAL = 2f; // Check every 2 seconds
    private static final int MAX_PROCESSED_RAIDS = 100; // Memory management threshold

    private final Set<String> processedRaids = new HashSet<>();
    private float elapsed = 0f;

    @Override
    public boolean isDone() {
        return false; // Run continuously
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
        if (elapsed < CHECK_INTERVAL) {
            return;
        }
        elapsed = 0f;

        IntelManagerAPI intelManager = Global.getSector().getIntelManager();

        for (IntelInfoPlugin intel : intelManager.getIntel()) {
            if (intel instanceof RaidIntel raid) {

                // Generate unique ID for this raid
                String raidId = generateRaidId(raid);

                // Check if we've already processed this raid and if it's complete
                if (!processedRaids.contains(raidId) && isRaidComplete(raid)) {
                    FactionAPI raidingFaction = raid.getFaction();

                    // Check if raid is from Draconis or 42nd Fleet
                    if (isDraconisRaid(raidingFaction)) {
                        MarketAPI targetMarket = getTargetMarket(raid);

                        // Process ANY raided market (not just player colonies)
                        if (targetMarket != null) {
                            processedRaids.add(raidId);
                            handleDraconisRaidComplete(raid, targetMarket);
                        }
                    }
                }
            }
        }

        // Prevent memory leaks by cleaning up old raid IDs
        if (processedRaids.size() > MAX_PROCESSED_RAIDS) {
            cleanupOldRaidIds();
        }
    }

    /**
     * Checks if a raid has completed
     */
    private boolean isRaidComplete(RaidIntel raid) {
        // Check both isEnding() (just completed) and isEnded() (fully finished)
        return raid.isEnding() || raid.isEnded();
    }

    /**
     * Checks if raid is from Draconis Alliance or 42nd Fleet
     */
    private boolean isDraconisRaid(FactionAPI faction) {
        if (faction == null) return false;
        String factionId = faction.getId();
        return DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId);
    }

    /**
     * Gets the target market from a raid
     */
    private MarketAPI getTargetMarket(RaidIntel raid) {
        try {
            // Get the map location (the entity being raided)
            // Pass null for the map parameter as we just need the entity
            SectorEntityToken location = raid.getMapLocation(null);

            if (location != null && location.getMarket() != null) {
                return location.getMarket();
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Error getting target market from raid", e);
        }
        return null;
    }

    /**
     * Generates a unique ID for this raid to prevent duplicate processing
     */
    private String generateRaidId(RaidIntel raid) {
        FactionAPI faction = raid.getFaction();
        MarketAPI target = getTargetMarket(raid);
        long timestamp = Global.getSector().getClock().getTimestamp();

        return String.format("%s_%s_%d",
                faction != null ? faction.getId() : "unknown",
                target != null ? target.getId() : "unknown",
                timestamp / 100); // Round to nearest 100 days to group similar raids
    }

    /**
     * Handles a completed Draconis raid - steals AI cores from ANY faction
     */
    private void handleDraconisRaidComplete(RaidIntel raid, MarketAPI targetMarket) {
        boolean isPlayerTarget = targetMarket.isPlayerOwned();
        String actionType = determineActionType(targetMarket);

        Global.getLogger(this.getClass()).info(
                String.format("Nexerelin: Draconis Alliance %s completed against %s (%s)",
                        actionType,
                        targetMarket.getName(),
                        isPlayerTarget ? "Player" : targetMarket.getFaction().getDisplayName())
        );

        // Check if the raid was successful (not just ended)
        if (raid.isSucceeded() || raid.isEnding()) {
            // Call the core theft system - works on any market
            DraconisAICoreTheftListener.checkAndStealAICores(targetMarket, isPlayerTarget, actionType);
        }
    }

    /**
     * Determines if this was a raid or bombardment based on market conditions
     */
    private String determineActionType(MarketAPI market) {
        // Check if ground defenses were destroyed (indicates bombardment occurred)
        if (market.hasCondition("ground_defenses_destroyed") ||
                market.hasCondition("bombarded")) {
            return "bombardment";
        }

        // Check market stability - heavy bombardment causes major stability loss
        float stability = market.getStability().getModifiedValue();
        if (stability < 3f) {
            return "bombardment";
        }

        // Default to raid if we can't determine
        return "raid";
    }

    /**
     * Cleans up old raid IDs to prevent memory growth during long campaigns
     */
    private void cleanupOldRaidIds() {
        if (processedRaids.size() <= MAX_PROCESSED_RAIDS) return;

        Global.getLogger(this.getClass()).info(
                "Cleaning up processed raid IDs (current count: " + processedRaids.size() + ")"
        );

        // Keep only the most recent half
        Set<String> newSet = new HashSet<>();
        int keepCount = MAX_PROCESSED_RAIDS / 2;
        int count = 0;

        for (String id : processedRaids) {
            if (count++ >= keepCount) break;
            newSet.add(id);
        }

        processedRaids.clear();
        processedRaids.addAll(newSet);
    }
}