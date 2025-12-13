package levianeer.draconis.data.campaign.intel.aicore.remnant;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans for Remnant stations that Draconis can raid for AI cores
 * Prioritizes systems with multiple Remnant stations or special installations
 */
public class DraconisRemnantTargetScanner implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisRemnantTargetScanner.class);

    public static final String REMNANT_TARGET_FLAG = "$draconis_remnantTarget";
    public static final String TARGET_PRIORITY_FLAG = "$draconis_remnantPriority";
    public static final String LAST_RAID_DAY_FLAG = "$draconis_lastRemnantRaidDay";

    private static final float SCAN_INTERVAL = 60f; // Scan every 60 days
    private static final float COOLDOWN_AFTER_RAID = 120f; // 120 day cooldown after raiding a system

    private float daysSinceLastScan = 0f;

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
        daysSinceLastScan += days;

        if (daysSinceLastScan < SCAN_INTERVAL) return;
        daysSinceLastScan = 0f;

        scanForRemnantTargets();
    }

    /**
     * Simply pick a Remnant system that isn't on cooldown
     */
    private void scanForRemnantTargets() {
        // Clear previous target
        clearAllTargetFlags();

        List<StarSystemAPI> availableTargets = new ArrayList<>();
        long currentDay = Global.getSector().getClock().getDay();

        // Find all Remnant systems not on cooldown
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            // Check cooldown
            Long lastRaidDay = (Long) system.getMemoryWithoutUpdate().get(LAST_RAID_DAY_FLAG);
            if (lastRaidDay != null && (currentDay - lastRaidDay) < COOLDOWN_AFTER_RAID) {
                continue;
            }

            // Check if has Remnant presence
            List<SectorEntityToken> remnantStations = findRemnantStations(system);
            if (!remnantStations.isEmpty()) {
                availableTargets.add(system);
            }
        }

        if (availableTargets.isEmpty()) {
            return; // No targets available
        }

        // Pick a random target
        StarSystemAPI selectedTarget = availableTargets.get(
            (int) (Math.random() * availableTargets.size())
        );

        // Calculate priority for logging purposes
        float priority = calculateSystemPriority(selectedTarget, findRemnantStations(selectedTarget));
        markSystemAsTarget(selectedTarget, priority);

        log.info(
            "Draconis: Selected Remnant raid target: " + selectedTarget.getName() +
            " (priority: " + String.format("%.1f", priority) + ")"
        );
    }

    /**
     * Find all Remnant stations in a star system
     */
    private List<SectorEntityToken> findRemnantStations(StarSystemAPI system) {
        List<SectorEntityToken> stations = new ArrayList<>();

        for (SectorEntityToken entity : system.getAllEntities()) {
            // Check if this is a Remnant entity (any Remnant-owned entity counts)
            if (entity.getFaction() != null &&
                Factions.REMNANTS.equals(entity.getFaction().getId())) {
                stations.add(entity);
            }
        }

        return stations;
    }

    /**
     * Calculate priority for raiding a Remnant system
     * Higher priority = more valuable target
     */
    private float calculateSystemPriority(StarSystemAPI system, List<SectorEntityToken> stations) {
        float priority = 0f;

        // Base priority from number of stations
        priority += stations.size() * 10f;

        // Bonus for systems with multiple stations (more cores likely)
        if (stations.size() >= 3) {
            priority += 20f;
        }

        // Bonus for special station types
        for (SectorEntityToken station : stations) {
            // Battlestations are high priority
            if (station.hasTag(Tags.COMM_RELAY) ||
                station.getCustomEntityType() != null &&
                station.getCustomEntityType().contains("station")) {
                priority += 15f;
            }

            // Objectives give small bonus
            if (station.hasTag(Tags.OBJECTIVE)) {
                priority += 5f;
            }
        }

        // Distance penalty (prefer closer systems to Draconis space)
        SectorEntityToken draconisBase = findNearestDraconisBase();
        if (draconisBase != null) {
            float distance = com.fs.starfarer.api.util.Misc.getDistanceLY(
                draconisBase.getLocationInHyperspace(),
                system.getLocation()
            );

            // Penalty scales with distance (max -30 for very distant systems)
            float distancePenalty = Math.min(30f, distance / 2f);
            priority -= distancePenalty;
        }

        return Math.max(0f, priority);
    }

    /**
     * Find the nearest Draconis market for distance calculations
     */
    private SectorEntityToken findNearestDraconisBase() {
        for (com.fs.starfarer.api.campaign.econ.MarketAPI market :
             Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getFactionId().equals(levianeer.draconis.data.campaign.ids.Factions.DRACONIS) &&
                market.getPrimaryEntity() != null) {
                return market.getPrimaryEntity();
            }
        }
        return null;
    }

    /**
     * Mark a star system as the current Remnant raid target
     */
    private void markSystemAsTarget(StarSystemAPI system, float priority) {
        system.getMemoryWithoutUpdate().set(REMNANT_TARGET_FLAG, true);
        system.getMemoryWithoutUpdate().set(TARGET_PRIORITY_FLAG, priority);
    }

    /**
     * Clear all Remnant target flags
     */
    private void clearAllTargetFlags() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            system.getMemoryWithoutUpdate().unset(REMNANT_TARGET_FLAG);
            system.getMemoryWithoutUpdate().unset(TARGET_PRIORITY_FLAG);
        }
    }

    /**
     * Get the current Remnant raid target (if any)
     */
    public static StarSystemAPI getCurrentTarget() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getMemoryWithoutUpdate().getBoolean(REMNANT_TARGET_FLAG)) {
                return system;
            }
        }
        return null;
    }

    /**
     * Mark a system as recently raided (puts it on cooldown)
     */
    public static void markSystemAsRaided(StarSystemAPI system) {
        long currentDay = Global.getSector().getClock().getDay();
        system.getMemoryWithoutUpdate().set(LAST_RAID_DAY_FLAG, currentDay);
        system.getMemoryWithoutUpdate().unset(REMNANT_TARGET_FLAG);

        log.info(
            "Draconis: Marked " + system.getName() + " as raided (cooldown until day " +
            (currentDay + COOLDOWN_AFTER_RAID) + ")"
        );
    }

    /**
     * Simple data class for potential targets
     */
    private static class RemnantTarget {
        final StarSystemAPI system;
        final List<SectorEntityToken> stations;
        final float priority;

        RemnantTarget(StarSystemAPI system, List<SectorEntityToken> stations, float priority) {
            this.system = system;
            this.stations = stations;
            this.priority = priority;
        }
    }
}