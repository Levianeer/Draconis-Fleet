package levianeer.draconis.data.campaign.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.ids.Factions;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Monitors Draconis and Forty-Second fleets and fills empty officer slots
 * with AI cores based on game progression (cycles).
 */
@SuppressWarnings("unused")
public class DraconisAICoreFleetInflater implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisAICoreFleetInflater.class);

    private static final String MEMORY_KEY = "$draconisAICoreScaling_processed"; // DEPRECATED: Old boolean key for save compatibility
    private static final String MEMORY_KEY_TIMESTAMP = "$draconisAICoreScaling_lastProcessed"; // New timestamp-based key
    private static final float CHECK_INTERVAL = 30.0f; // Check every 30 days (once per month)

    private float daysElapsed = 0f;
    private boolean firstRun = true; // Track first run for initialization logging

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
        // Don't run in simulation mode
        if (Global.getSector().getEconomy().isSimMode()) return;

        DraconisAICoreScalingConfig config = DraconisAICoreScalingConfig.getInstance();
        if (!config.isEnabled()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;

        // Only check periodically
        if (daysElapsed < CHECK_INTERVAL) return;
        daysElapsed = 0f;

        // Get current game cycle (may be overridden for testing)
        float actualCycle = Global.getSector().getClock().getCycle();
        float currentCycle = config.getEffectiveCycle(actualCycle);

        // Calculate current coverage percentage
        float coveragePercent = config.calculateCoveragePercent(currentCycle);

        // One-time initialization log on first run
        if (firstRun) {
            firstRun = false;
            log.info("Draconis: === AI Core Fleet Inflater First Run ===");
            if (actualCycle != currentCycle) {
                log.info(String.format("Draconis:   Actual cycle: %.2f | TEST OVERRIDE: %.2f", actualCycle, currentCycle));
            } else {
                log.info(String.format("Draconis:   Current cycle: %.2f", currentCycle));
            }
            log.info(String.format("Draconis:   Coverage percent: %.0f%%", coveragePercent * 100));
            if (coveragePercent <= 0f) {
                log.info("Draconis:   Coverage is 0% - no AI cores will be assigned yet");
            } else {
                log.info("Draconis:   System is active - will process eligible fleets");
            }
        }

        // If 0% coverage, skip processing entirely
        if (coveragePercent <= 0f) return;

        // Process all fleets in the sector
        // Check all star systems
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CampaignFleetAPI fleet : system.getFleets()) {
                if (shouldProcessFleet(fleet)) {
                    processFleet(fleet, currentCycle, coveragePercent, config);
                }
            }
        }

        // Check hyperspace
        for (CampaignFleetAPI fleet : Global.getSector().getHyperspace().getFleets()) {
            if (shouldProcessFleet(fleet)) {
                processFleet(fleet, currentCycle, coveragePercent, config);
            }
        }
    }

    /**
     * Determine if this fleet should be processed for AI core assignment
     */
    private boolean shouldProcessFleet(CampaignFleetAPI fleet) {
        DraconisAICoreScalingConfig config = DraconisAICoreScalingConfig.getInstance();

        if (fleet == null || !fleet.isAlive()) return false;

        // Don't process stations (orbital stations, mining stations, etc.)
        if (fleet.isStationMode()) return false;

        // Check reprocess timing (skip this check in test mode to allow re-processing)
        if (!config.isTestModeActive()) {
            float currentTime = Global.getSector().getClock().getElapsedDaysSince(0);

            // Check if we have a timestamp from when fleet was last processed
            if (fleet.getMemoryWithoutUpdate().contains(MEMORY_KEY_TIMESTAMP)) {
                float lastProcessed = fleet.getMemoryWithoutUpdate().getFloat(MEMORY_KEY_TIMESTAMP);
                float timeSinceLastCheck = currentTime - lastProcessed;

                // Don't reprocess if not enough time has passed
                if (timeSinceLastCheck < config.getRecheckIntervalDays()) {
                    return false;
                }
            }
            // SAVE COMPATIBILITY: Migrate old boolean key to timestamp
            else if (fleet.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY)) {
                // Fleet was processed in old system - set timestamp to now and skip this time
                fleet.getMemoryWithoutUpdate().set(MEMORY_KEY_TIMESTAMP, currentTime);
                return false;
            }
        }

        // Only process Draconis and Forty-Second fleets
        String factionId = fleet.getFaction().getId();
        if (!Factions.DRACONIS.equals(factionId) && !Factions.FORTYSECOND.equals(factionId)) {
            return false;
        }

        // Only process military/combat fleets
        return isMilitaryFleet(fleet);
    }

    /**
     * Check if this is a military/combat fleet (not civilian, trade, etc.)
     */
    private boolean isMilitaryFleet(CampaignFleetAPI fleet) {
        // Check fleet type
        String fleetType = fleet.getMemoryWithoutUpdate().getString("$fleetType");

        // Exclude civilian fleet types
        if (FleetTypes.TRADE.equals(fleetType) ||
            FleetTypes.TRADE_SMALL.equals(fleetType) ||
            FleetTypes.TRADE_SMUGGLER.equals(fleetType) ||
            FleetTypes.TRADE_LINER.equals(fleetType) ||
            FleetTypes.SCAVENGER_SMALL.equals(fleetType) ||
            FleetTypes.SCAVENGER_MEDIUM.equals(fleetType) ||
            FleetTypes.SCAVENGER_LARGE.equals(fleetType) ||
            "tradeFleet".equals(fleetType) ||
            "civilianFleet".equals(fleetType)) {
            return false;
        }

        // Check assignment - exclude non-combat assignments
        FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
        if (assignment != null) {
            String assignmentType = assignment.getAssignment().toString();
            if (assignmentType.contains("TRADE") ||
                assignmentType.contains("SMUGGLE") ||
                assignmentType.contains("DELIVER")) {
                return false;
            }
        }

        // Check if fleet has significant combat power
        float combatFP = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.isCivilian() && !member.isFighterWing()) {
                combatFP += member.getFleetPointCost();
            }
        }

        // Require at least 20 FP in combat ships
        return combatFP >= 20f;
    }

    /**
     * DEPRECATED: No longer used - fleets are now processed on spawn, not when standing down
     *
     * Check if fleet is currently at a base/station (for resupply/refit)
     * AI cores are only installed when fleets return to base
     */
    @Deprecated
    @SuppressWarnings("unused")
    private boolean isFleetAtBase(CampaignFleetAPI fleet) {
        // Check if fleet is orbiting a market (station/planet)
        com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
        if (assignment == null) return false;

        FleetAssignment assignmentType = assignment.getAssignment();
        // Fleet must be standing down at a base
        return assignmentType == FleetAssignment.STANDING_DOWN;
    }

    /**
     * Process a fleet and assign AI cores to empty officer slots
     */
    private void processFleet(CampaignFleetAPI fleet, float currentCycle,
                              float coveragePercent, DraconisAICoreScalingConfig config) {

        // Find all ships without officers (empty captain slots)
        List<FleetMemberAPI> emptySlots = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            // Skip silly stuff
            if (member.isFighterWing()) continue;
            if (member.isCivilian()) continue;

            // Only process ships without assigned officers
            PersonAPI captain = member.getCaptain();

            // Skip ships that already have a real officer or AI core assigned
            // Default captains have empty/blank names
            // Real officers and AI cores have actual names
            String captainName = captain != null ? captain.getNameString() : null;
            boolean hasRealOfficer = captain != null &&
                captainName != null &&
                !captainName.trim().isEmpty();

            // Only add ships with default/empty captains
            if (!hasRealOfficer) {
                emptySlots.add(member);
            }
        }

        // If no empty slots, mark as processed and return
        if (emptySlots.isEmpty()) {
            float currentTime = Global.getSector().getClock().getElapsedDaysSince(0);
            fleet.getMemoryWithoutUpdate().set(MEMORY_KEY_TIMESTAMP, currentTime);
            return;
        }

        // Sort by fleet points (largest ships first)
        emptySlots.sort(Comparator.comparing(FleetMemberAPI::getFleetPointCost).reversed());

        // Calculate how many slots to fill
        int slotsToFill = Math.round(emptySlots.size() * coveragePercent);

        if (slotsToFill <= 0) {
            float currentTime = Global.getSector().getClock().getElapsedDaysSince(0);
            fleet.getMemoryWithoutUpdate().set(MEMORY_KEY_TIMESTAMP, currentTime);
            return;
        }

        // Assign AI cores to the calculated number of ships
        Random random = new Random();
        int coresAssigned = 0;
        int gammaCount = 0, betaCount = 0, alphaCount = 0;

        for (int i = 0; i < slotsToFill && i < emptySlots.size(); i++) {
            FleetMemberAPI member = emptySlots.get(i);

            // Roll for core type based on current cycle
            String coreType = config.rollCoreType(currentCycle, random.nextFloat());

            // Create the AI core officer
            PersonAPI aiCore = createAICoreOfficer(coreType, fleet.getFaction().getId(), random);

            if (aiCore != null) {
                // Step 1: Add to fleet's officer pool
                fleet.getFleetData().addOfficer(aiCore);

                // Step 2: Assign as captain to this specific ship
                member.setCaptain(aiCore);

                coresAssigned++;

                // Count by type for logging
                switch (coreType) {
                    case com.fs.starfarer.api.impl.campaign.ids.Commodities.GAMMA_CORE -> gammaCount++;
                    case com.fs.starfarer.api.impl.campaign.ids.Commodities.BETA_CORE -> betaCount++;
                    case com.fs.starfarer.api.impl.campaign.ids.Commodities.ALPHA_CORE -> alphaCount++;
                }
            }
        }

        // Mark fleet as processed with current timestamp
        float currentTime = Global.getSector().getClock().getElapsedDaysSince(0);
        fleet.getMemoryWithoutUpdate().set(MEMORY_KEY_TIMESTAMP, currentTime);

        // Log assignment
        if (coresAssigned > 0) {
            log.info(String.format(
                "Draconis: Assigned %d AI cores to %s: %d Gamma, %d Beta, %d Alpha",
                coresAssigned,
                fleet.getNameWithFaction(),
                gammaCount,
                betaCount,
                alphaCount
            ));
        }
    }

    /**
     * Create an AI core officer of the specified type
     */
    private PersonAPI createAICoreOfficer(String coreType, String factionId, Random random) {
        try {
            AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(coreType);
            if (plugin != null) {
                return plugin.createPerson(coreType, factionId, random);
            }
        } catch (Exception e) {
            log.error("Draconis: Failed to create AI core officer of type " + coreType, e);
        }
        return null;
    }
}