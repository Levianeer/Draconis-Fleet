package levianeer.draconis.data.campaign.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.aicores.XLII_LiteCoreOfficerPlugin;
import levianeer.draconis.data.campaign.intel.longsight.XLII_LongsightOfficerPlugin;
import levianeer.draconis.data.campaign.econ.conditions.DraconManager;
import levianeer.draconis.data.campaign.ids.Factions;
import org.apache.log4j.Logger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Monitors Draconis and Forty-Second fleets and fills empty officer slots
 * with Lite Core officers ({@link XLII_LiteCoreOfficerPlugin}).
 */
public class DraconisAICoreFleetInflater implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisAICoreFleetInflater.class);

    private static final String MEMORY_KEY = "$draconisAICoreScaling_processed"; // DEPRECATED: Old boolean key for save compatibility
    private static final String MEMORY_KEY_TIMESTAMP = "$draconisAICoreScaling_lastProcessed";
    private static final float CHECK_INTERVAL = 30.0f;

    private float daysElapsed = 0f;

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
        if (Global.getSector().getEconomy().isSimMode()) return;

        DraconisAICoreScalingConfig config = DraconisAICoreScalingConfig.getInstance();
        if (!config.isEnabled()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;

        if (daysElapsed < CHECK_INTERVAL) return;
        daysElapsed = 0f;

        // Defensive copies to prevent ConcurrentModificationException
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CampaignFleetAPI fleet : new ArrayList<>(system.getFleets())) {
                if (shouldProcessFleet(fleet)) {
                    processFleet(fleet);
                }
            }
        }

        for (CampaignFleetAPI fleet : new ArrayList<>(Global.getSector().getHyperspace().getFleets())) {
            if (shouldProcessFleet(fleet)) {
                processFleet(fleet);
            }
        }
    }

    private boolean shouldProcessFleet(CampaignFleetAPI fleet) {
        DraconisAICoreScalingConfig config = DraconisAICoreScalingConfig.getInstance();

        if (fleet == null || !fleet.isAlive()) return false;

        if (fleet.isStationMode()) return false;

        // Check reprocess timing
        float currentTime = Global.getSector().getClock().getElapsedDaysSince(0);

        if (fleet.getMemoryWithoutUpdate().contains(MEMORY_KEY_TIMESTAMP)) {
            float lastProcessed = fleet.getMemoryWithoutUpdate().getFloat(MEMORY_KEY_TIMESTAMP);
            float timeSinceLastCheck = currentTime - lastProcessed;

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

        String factionId = fleet.getFaction().getId();
        if (!Factions.DRACONIS.equals(factionId) && !Factions.FORTYSECOND.equals(factionId)) {
            return false;
        }

        return isMilitaryFleet(fleet);
    }

    private boolean isMilitaryFleet(CampaignFleetAPI fleet) {
        String fleetType = fleet.getMemoryWithoutUpdate().getString("$fleetType");

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

        FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
        if (assignment != null) {
            String assignmentType = assignment.getAssignment().toString();
            if (assignmentType.contains("TRADE") ||
                assignmentType.contains("SMUGGLE") ||
                assignmentType.contains("DELIVER")) {
                return false;
            }
        }

        float combatFP = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.isCivilian() && !member.isFighterWing()) {
                combatFP += member.getFleetPointCost();
            }
        }

        return combatFP >= 20f;
    }

    /**
     * DEPRECATED: No longer used - fleets are now processed on spawn, not when standing down
     * <p>
     * Check if fleet is currently at a base/station (for resupply/refit)
     * AI cores are only installed when fleets return to base
     */
    @Deprecated
    @SuppressWarnings("unused")
    private boolean isFleetAtBase(CampaignFleetAPI fleet) {
        com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
        if (assignment == null) return false;

        FleetAssignment assignmentType = assignment.getAssignment();
        return assignmentType == FleetAssignment.STANDING_DOWN;
    }

    private static List<FleetMemberAPI> findEmptyOfficerSlots(CampaignFleetAPI fleet, boolean forceReplace) {
        List<FleetMemberAPI> emptySlots = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing() || member.isCivilian()) continue;

            // Real officers (human or AI core) have trained skills (level > 0);
            // default/placeholder captains have skill entries but all at level 0
            PersonAPI captain = member.getCaptain();
            if (captain != null && captain.isAICore()) continue;

            boolean hasTrainedSkills = false;
            if (captain != null) {
                for (MutableCharacterStatsAPI.SkillLevelAPI skill : captain.getStats().getSkillsCopy()) {
                    if (skill.getLevel() > 0) {
                        hasTrainedSkills = true;
                        break;
                    }
                }
            }

            if (!hasTrainedSkills || forceReplace) {
                emptySlots.add(member);
            }
        }
        return emptySlots;
    }

    private void processFleet(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> emptySlots = findEmptyOfficerSlots(fleet, false);

        Random random = new Random();
        for (FleetMemberAPI member : emptySlots) {
            PersonAPI liteCore = createLiteCoreOfficer(fleet.getFaction().getId(), random);
            if (liteCore != null) {
                fleet.getFleetData().addOfficer(liteCore);
                member.setCaptain(liteCore);
            }
        }

        // DRACON 1: Assign Longsight to the flagship regardless of whether any empty slots existed
        if (getCurrentDraconLevel() == 1) {
            assignLongsightFlagship(fleet);
        }

        float currentTime = Global.getSector().getClock().getElapsedDaysSince(0);
        fleet.getMemoryWithoutUpdate().set(MEMORY_KEY_TIMESTAMP, currentTime);

        if (!emptySlots.isEmpty()) {
            log.debug(String.format("Draconis: Assigned %d Lite Cores to %s",
                    emptySlots.size(), fleet.getNameWithFaction()));
        }
    }

    private PersonAPI createLiteCoreOfficer(String factionId, Random random) {
        try {
            return new XLII_LiteCoreOfficerPlugin().createPerson(
                    XLII_LiteCoreOfficerPlugin.CORE_ID, factionId, random);
        } catch (Exception e) {
            log.error("Draconis: Failed to create Lite Core officer", e);
            return null;
        }
    }

    /** Returns 5 (COLD FORGE) by default. */
    private static int getCurrentDraconLevel() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(DraconManager.LEVEL_KEY);
        if (stored instanceof Number) {
            return ((Number) stored).intValue();
        }
        return 5;
    }

    /**
     * DRACON 1 (DEAD LIGHT): Assign the Longsight AI core to the highest-deployment-cost
     * ship in the fleet that is currently crewed by any AI core. If no AI core officers are
     * present yet, this is a no-op (empty slots will be filled by the normal path).
     */
    private void assignLongsightFlagship(CampaignFleetAPI fleet) {
        FleetMemberAPI target = null;
        float highestCost = -1f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            PersonAPI captain = member.getCaptain();
            if (captain == null || !captain.isAICore()) continue;

            // Already has Longsight - skip
            if (XLII_LongsightOfficerPlugin.CORE_ID.equals(captain.getAICoreId())) continue;

            float cost = member.getFleetPointCost();
            if (cost > highestCost) {
                highestCost = cost;
                target = member;
            }
        }

        if (target == null) return;

        fleet.getFleetData().removeOfficer(target.getCaptain());

        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(XLII_LongsightOfficerPlugin.CORE_ID);
        if (plugin == null) {
            log.warn("Draconis: DRACON 1 - Could not find AICoreOfficerPlugin for Longsight");
            return;
        }

        PersonAPI sigmaCore = plugin.createPerson(
                XLII_LongsightOfficerPlugin.CORE_ID, fleet.getFaction().getId(), new Random());
        if (sigmaCore != null) {
            fleet.getFleetData().addOfficer(sigmaCore);
            target.setCaptain(sigmaCore);
            log.debug(String.format(
                "Draconis: DRACON 1 - Assigned Longsight to %s (%.0f FP) in %s",
                target.getShipName(), highestCost, fleet.getNameWithFaction()));
        }
    }

    /**
     * Immediately fill empty officer slots with Lite Core officers and assign
     * Longsight as flagship captain.
     * Called at spawn time for hostile-activity patrol fleets and raid fleets.
     * Uses forceReplace=false; existing trained human officers are preserved.
     */
    public static void inflateFleetNow(CampaignFleetAPI fleet) {
        inflateFleetNow(fleet, false);
    }

    /**
     * Immediately fill officer slots with Lite Core officers and assign Longsight as flagship captain.
     *
     * @param forceReplace when true, human officers (trained or not) are displaced by Lite Cores;
     *                     use this for freshly spawned crisis/raid fleets that may have
     *                     quality-assigned officers from FleetCreatorMission.
     *                     When false, only ships with no trained captain are eligible.
     */
    public static void inflateFleetNow(CampaignFleetAPI fleet, boolean forceReplace) {
        if (fleet == null || !fleet.isAlive()) return;

        DraconisAICoreScalingConfig config = DraconisAICoreScalingConfig.getInstance();
        if (!config.isEnabled()) return;

        List<FleetMemberAPI> emptySlots = findEmptyOfficerSlots(fleet, forceReplace);

        Random random = new Random();
        int coresAssigned = 0;

        for (FleetMemberAPI member : emptySlots) {
            try {
                PersonAPI liteCore = new XLII_LiteCoreOfficerPlugin().createPerson(
                        XLII_LiteCoreOfficerPlugin.CORE_ID, fleet.getFaction().getId(), random);
                // Remove displaced human officer from fleet data before assigning Lite Core
                PersonAPI existing = member.getCaptain();
                if (existing != null) fleet.getFleetData().removeOfficer(existing);
                fleet.getFleetData().addOfficer(liteCore);
                member.setCaptain(liteCore);
                coresAssigned++;
            } catch (Exception e) {
                log.error("Draconis: inflateFleetNow - failed to create Lite Core officer", e);
            }
        }

        // DRACON 1: Assign Longsight to the flagship regardless of whether any empty slots existed
        if (getCurrentDraconLevel() == 1) {
            assignLongsightToFlagship(fleet);
        }

        if (coresAssigned > 0) {
            log.debug(String.format("Draconis: inflateFleetNow assigned %d Lite Cores to %s",
                    coresAssigned, fleet.getNameWithFaction()));
        }
    }

    /**
     * One-shot script attached to a fleet at spawn time.
     * Waits one advance frame (so the Starsector FleetInflater can assign its officers first),
     * then calls inflateFleetNow to replace those human officers with AI cores.
     */
    public static class DeferredInflateScript implements EveryFrameScript, java.io.Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final CampaignFleetAPI fleet;
        private final boolean forceReplace;
        private transient boolean applied = false;
        private transient boolean done = false;

        public DeferredInflateScript(CampaignFleetAPI fleet, boolean forceReplace) {
            this.fleet = fleet;
            this.forceReplace = forceReplace;
        }

        @Override public boolean isDone() { return done; }
        @Override public boolean runWhilePaused() { return false; }

        @Override
        public void advance(float amount) {
            if (fleet == null || !fleet.isAlive()) { done = true; return; }
            if (!applied) {
                applied = true;
                return; // Skip one frame so the Starsector FleetInflater runs first
            }
            inflateFleetNow(fleet, forceReplace);
            done = true;
        }
    }

    /**
     * Assign Longsight as the captain of the highest-FP combat ship in the fleet.
     * Properly adds Sigma to the fleet's officer list and sets them as ship captain,
     * displacing any existing officer. Works regardless of whether the ship has an
     * AI core or human officer already assigned.
     */
    private static void assignLongsightToFlagship(CampaignFleetAPI fleet) {
        FleetMemberAPI target = null;
        float highestCost = -1f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing() || member.isCivilian()) continue;
            PersonAPI captain = member.getCaptain();
            // Skip ships that already have Sigma
            if (captain != null && XLII_LongsightOfficerPlugin.CORE_ID.equals(captain.getAICoreId())) continue;
            float cost = member.getFleetPointCost();
            if (cost > highestCost) {
                highestCost = cost;
                target = member;
            }
        }

        if (target == null) return;

        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(XLII_LongsightOfficerPlugin.CORE_ID);
        if (plugin == null) {
            log.warn("Draconis: inflateFleetNow - could not find AICoreOfficerPlugin for Longsight");
            return;
        }

        PersonAPI sigma = plugin.createPerson(
                XLII_LongsightOfficerPlugin.CORE_ID, fleet.getFaction().getId(), new Random());
        if (sigma != null) {
            PersonAPI existing = target.getCaptain();
            if (existing != null) fleet.getFleetData().removeOfficer(existing);
            fleet.getFleetData().addOfficer(sigma);
            target.setCaptain(sigma);
            fleet.setCommander(sigma);
            log.debug(String.format("Draconis: Assigned Longsight as flagship captain of %s (%.0f FP)",
                    fleet.getNameWithFaction(), highestCost));
        }
    }
}
