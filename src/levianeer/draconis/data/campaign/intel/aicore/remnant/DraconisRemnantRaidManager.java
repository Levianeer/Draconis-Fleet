package levianeer.draconis.data.campaign.intel.aicore.remnant;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Manages Draconis raids on Remnant stations for AI core acquisition
 * Sends task forces to engage Remnant defenses and recover technology
 */
public class DraconisRemnantRaidManager implements EveryFrameScript {

    private static final float CHECK_INTERVAL = 45f; // Check every 45 days
    private static final float RAID_CHANCE = 0.5f; // 50% chance to raid when target available

    private float daysSinceLastCheck = 0f;

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
        daysSinceLastCheck += days;

        if (daysSinceLastCheck < CHECK_INTERVAL) return;
        daysSinceLastCheck = 0f;

        considerRemnantRaid();
    }

    /**
     * Check if we should launch a raid on Remnant targets
     */
    private void considerRemnantRaid() {
        // Check if there's already an active raid fleet
        if (hasActiveRaidFleet()) {
            Global.getLogger(this.getClass()).info(
                "Active raid fleet already exists - skipping raid check"
            );
            return;
        }

        // Get current Remnant target
        StarSystemAPI target = DraconisRemnantTargetScanner.getCurrentTarget();

        if (target == null) {
            Global.getLogger(this.getClass()).info(
                "No Remnant target available - skipping raid check"
            );
            return;
        }

        Global.getLogger(this.getClass()).info(
            "Remnant target found: " + target.getName()
        );

        // Random chance to trigger raid
        Random random = new Random();
        if (random.nextFloat() > RAID_CHANCE) {
            Global.getLogger(this.getClass()).info(
                "Raid check failed (chance: " + (RAID_CHANCE * 100) + "%)"
            );
            return;
        }

        // Find a Draconis source market for the fleet
        com.fs.starfarer.api.campaign.econ.MarketAPI source = getDraconisSource();
        if (source == null) {
            Global.getLogger(this.getClass()).info(
                "No Draconis source market available for raid"
            );
            return;
        }

        Global.getLogger(this.getClass()).info("Source market: " + source.getName());

        // Spawn raid fleet
        spawnRemnantRaidFleet(source, target);
    }

    /**
     * Check if there's already an active raid fleet
     */
    private boolean hasActiveRaidFleet() {
        // Check all fleets in the sector
        for (CampaignFleetAPI fleet : Global.getSector().getCurrentLocation().getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean("$draconis_remnantRaid")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a Draconis market to serve as the raid source
     */
    private com.fs.starfarer.api.campaign.econ.MarketAPI getDraconisSource() {
        // Prefer larger markets with military capability
        com.fs.starfarer.api.campaign.econ.MarketAPI bestSource = null;
        int bestScore = 0;

        for (com.fs.starfarer.api.campaign.econ.MarketAPI market :
             Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            // Score based on size and military industries
            int score = market.getSize();

            // Bonus for military base or high command
            if (market.hasIndustry("militarybase") ||
                market.hasIndustry("highcommand") ||
                market.hasIndustry("XLII_highcommand")) {
                score += 3;
            }

            if (score > bestScore) {
                bestScore = score;
                bestSource = market;
            }
        }

        return bestSource;
    }

    /**
     * Spawn a Draconis fleet to raid Remnant installations
     */
    private void spawnRemnantRaidFleet(com.fs.starfarer.api.campaign.econ.MarketAPI source,
                                       StarSystemAPI target) {
        Global.getLogger(this.getClass()).info("========================================");
        Global.getLogger(this.getClass()).info("=== SPAWNING REMNANT RAID FLEET ===");
        Global.getLogger(this.getClass()).info("Source: " + source.getName());
        Global.getLogger(this.getClass()).info("Target: " + target.getName());

        // Calculate fleet strength based on priority
        float priority = target.getMemoryWithoutUpdate().getFloat(
            DraconisRemnantTargetScanner.TARGET_PRIORITY_FLAG
        );

        // Scale fleet power based on priority (minimum 200, maximum 500)
        float fleetPoints = Math.max(200f, Math.min(500f, 160f + priority * 2));

        // Add cargo capacity for bringing back AI cores
        // Small support flotilla - just enough to carry cores and supplies
        float freighterFP = 30f;  // Small freighter contingent
        float tankerFP = 20f;     // Fuel for long-range operations

        Global.getLogger(this.getClass()).info("Fleet points: " + fleetPoints);
        Global.getLogger(this.getClass()).info("Freighter FP: " + freighterFP + ", Tanker FP: " + tankerFP);

        // Create fleet parameters
        FleetParamsV3 params = new FleetParamsV3(
            source,                          // Source market
            null,                            // Location (will be set after creation)
            DRACONIS,                        // Faction
            null,                            // Route (none, this is a raid)
            FleetTypes.TASK_FORCE,           // Fleet type
            fleetPoints,                     // Combat FP
            freighterFP,                     // Freighter FP (for cargo capacity)
            tankerFP,                        // Tanker FP (for fuel reserves)
            0f,                              // Transport FP
            0f,                              // Liner FP
            0f,                              // Utility FP
            0f                               // Quality bonus
        );

        // Set quality mod based on source market
        params.qualityMod = source.getShipQualityFactor();

        // Officer quality
        params.officerNumberMult = 1.5f; // More officers for dangerous mission
        params.officerLevelBonus = 5; // Better officers for dangerous mission

        // Create the fleet
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null) {
            Global.getLogger(this.getClass()).error(
                "Failed to create Remnant raid fleet!"
            );
            return;
        }

        // Configure fleet
        fleet.setName("Expeditionary Strike Group");
        fleet.setNoFactionInName(true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set("$draconis_remnantRaid", true);
        fleet.getMemoryWithoutUpdate().set("$draconis_raidTarget", target);

        // Add behavior script to manage combat engagement based on assignment
        Global.getSector().addScript(new RemnantRaidFleetBehavior(fleet));

        // Add sustained burn for faster travel
        fleet.addAbility(Abilities.SUSTAINED_BURN);
        fleet.addAbility(Abilities.EMERGENCY_BURN);
        fleet.addAbility(Abilities.SENSOR_BURST);

        // Set starting location near source market
        SectorEntityToken sourceEntity = source.getPrimaryEntity();
        fleet.setLocation(sourceEntity.getLocation().x, sourceEntity.getLocation().y);
        sourceEntity.getContainingLocation().addEntity(fleet);

        // Clear any existing assignments
        fleet.clearAssignments();

        // Assignment 1: Travel to target system
        // Use long duration - fleets should keep trying to reach target
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION,
            target.getCenter(),
            1000f, // Take as long as needed
            "traveling to " + target.getName()
        );

        // Assignment 2: Patrol target system hunting Remnant
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.PATROL_SYSTEM,
            target.getCenter(),
            14f, // Patrol for 14 days
            "hunting Remnant forces in " + target.getName()
        );

        // Assignment 3: Return to base
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION,
            sourceEntity,
            1000f, // Take as long as needed
            "returning to " + source.getName()
        );

        // Assignment 4: Orbit base briefly
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.ORBIT_PASSIVE,
            sourceEntity,
            5f,
            "standing down"
        );

        // Assignment 5: Despawn
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
            sourceEntity,
            1000f
        );

        Global.getLogger(this.getClass()).info("Fleet spawned successfully");
        Global.getLogger(this.getClass()).info("Fleet strength: " + fleet.getFleetPoints() + " FP");
        Global.getLogger(this.getClass()).info("Officers: " + fleet.getCommander().getStats().getOfficerNumber().getModifiedInt());
        Global.getLogger(this.getClass()).info("========================================");

        // Notify player if they're in the area
        if (shouldPlayerKnowAboutRaid(source, target)) {
            Global.getSector().getCampaignUI().addMessage(
                "Draconis Alliance forces have launched an expedition to " + target.getName() +
                " to engage Remnant installations.",
                com.fs.starfarer.api.util.Misc.getTextColor()
            );
        }
    }

    /**
     * Determine if player should be notified about the raid
     */
    private boolean shouldPlayerKnowAboutRaid(com.fs.starfarer.api.campaign.econ.MarketAPI source,
                                               StarSystemAPI target) {
        // Player knows if they're in source system or target system
        SectorEntityToken playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return false;

        StarSystemAPI playerSystem = playerFleet.getStarSystem();
        if (playerSystem == null) return false;

        return playerSystem == source.getStarSystem() || playerSystem == target;
    }

    /**
     * Manages fleet behavior during different phases of the raid
     * Makes fleet passive during transit, aggressive during patrol
     * Handles AI core acquisition and delivery
     */
    private static class RemnantRaidFleetBehavior implements EveryFrameScript {
        private final CampaignFleetAPI fleet;
        private FleetAssignment lastAssignment = null;
        private boolean coresAcquired = false;
        private float patrolTimeElapsed = 0f;
        private static final float MIN_PATROL_TIME_FOR_CORES = 3f; // 3 days minimum patrol

        public RemnantRaidFleetBehavior(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        @Override
        public boolean isDone() {
            // Clean up when fleet is gone or despawning
            return fleet == null || !fleet.isAlive() || fleet.getCurrentAssignment() == null;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            if (fleet == null || !fleet.isAlive()) return;

            com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
            if (assignment == null) return;

            FleetAssignment assignmentType = assignment.getAssignment();

            // Track when assignment changes
            if (lastAssignment != assignmentType) {
                handleAssignmentChange(lastAssignment, assignmentType);
                lastAssignment = assignmentType;
            }

            // During GO_TO_LOCATION (traveling), fleet should avoid combat
            if (assignmentType == FleetAssignment.GO_TO_LOCATION) {
                // Make fleet passive - ignore other fleets unless attacked
                if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                }
            }
            // During PATROL_SYSTEM (hunting), fleet should engage enemies
            else if (assignmentType == FleetAssignment.PATROL_SYSTEM) {
                // Track patrol time for AI core scaling
                float days = Global.getSector().getClock().convertToDays(amount);
                patrolTimeElapsed += days;

                // Make fleet aggressive - hunt down enemies
                if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
                    fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
                }

                // Note: AI cores are now acquired when transitioning to return phase
                // (in handleAssignmentChange), not during patrol
            }
            // During ORBIT_PASSIVE at base, check for AI core delivery
            else if (assignmentType == FleetAssignment.ORBIT_PASSIVE) {
                // Return to passive state
                if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                    fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT);
                }

                // Deliver AI cores if we have them and we're at base
                if (coresAcquired && hasAICoresInCargo()) {
                    deliverAICores();
                }
            }
            // During other assignments, make passive again
            else {
                // Return to passive state
                if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                    fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT);
                }
            }
        }

        private void handleAssignmentChange(FleetAssignment from, FleetAssignment to) {
            if (to == FleetAssignment.PATROL_SYSTEM) {
                Global.getLogger(RemnantRaidFleetBehavior.class).info(
                    "Raid fleet entering patrol phase in target system"
                );
            } else if (from == FleetAssignment.PATROL_SYSTEM && to == FleetAssignment.GO_TO_LOCATION) {
                // Fleet survived patrol and is returning - acquire cores now
                if (!coresAcquired) {
                    Global.getLogger(RemnantRaidFleetBehavior.class).info(
                        "Raid fleet survived patrol and is returning - acquiring AI cores"
                    );
                    acquireAICores();
                }
                Global.getLogger(RemnantRaidFleetBehavior.class).info(
                    "Raid fleet transitioning to return journey. Cores acquired: " + coresAcquired
                );
            }
        }

        /**
         * Acquire AI cores from defeated Remnant forces
         * Amount based on fleet strength and patrol time
         */
        private void acquireAICores() {
            CargoAPI cargo = fleet.getCargo();

            // Calculate AI core rewards based on patrol time and fleet strength
            // Longer patrol = more cores (representing more battles won)
            int alphaCount = 0;
            int betaCount = 0;
            int gammaCount = 0;

            // Base rewards on patrol time (5 days minimum, rewards scale up to 30+ days)
            if (patrolTimeElapsed >= 5f) {
                gammaCount = 1 + (int)(patrolTimeElapsed / 10f); // 1-4 gamma cores
            }
            if (patrolTimeElapsed >= 15f) {
                betaCount = 1 + (int)((patrolTimeElapsed - 15f) / 15f); // 1-2 beta cores
            }
            if (patrolTimeElapsed >= 30f) {
                alphaCount = 1; // 1 alpha core for long patrols
            }

            // Add cores to cargo
            if (alphaCount > 0) {
                cargo.addCommodity(Commodities.ALPHA_CORE, alphaCount);
                Global.getLogger(RemnantRaidFleetBehavior.class).info(
                    "Fleet acquired " + alphaCount + " Alpha AI core(s)"
                );
            }
            if (betaCount > 0) {
                cargo.addCommodity(Commodities.BETA_CORE, betaCount);
                Global.getLogger(RemnantRaidFleetBehavior.class).info(
                    "Fleet acquired " + betaCount + " Beta AI core(s)"
                );
            }
            if (gammaCount > 0) {
                cargo.addCommodity(Commodities.GAMMA_CORE, gammaCount);
                Global.getLogger(RemnantRaidFleetBehavior.class).info(
                    "Fleet acquired " + gammaCount + " Gamma AI core(s)"
                );
            }

            // Make AI cores lootable if player defeats the fleet
            // Use ExtraSalvage system (same as vanilla special cargo)
            CargoAPI extraSalvage = Global.getFactory().createCargo(true);

            if (alphaCount > 0) {
                extraSalvage.addCommodity(Commodities.ALPHA_CORE, alphaCount);
            }
            if (betaCount > 0) {
                extraSalvage.addCommodity(Commodities.BETA_CORE, betaCount);
            }
            if (gammaCount > 0) {
                extraSalvage.addCommodity(Commodities.GAMMA_CORE, gammaCount);
            }

            // Add to fleet's extra salvage - guaranteed drops when fleet is defeated
            com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial.addExtraSalvage(
                extraSalvage, fleet.getMemoryWithoutUpdate(), -1
            );

            Global.getLogger(RemnantRaidFleetBehavior.class).info(
                "AI cores added to fleet extra salvage - guaranteed loot if fleet is defeated"
            );

            coresAcquired = true;
            fleet.getMemoryWithoutUpdate().set("$draconis_coresAcquired", true);

            // Notify player if they can see the fleet
            if (shouldPlayerSeeFleet()) {
                int totalCores = alphaCount + betaCount + gammaCount;
                Global.getSector().getCampaignUI().addMessage(
                    "Draconis expedition fleet has secured AI cores from Remnant forces (" +
                    totalCores + " core" + (totalCores > 1 ? "s" : "") + ")",
                    com.fs.starfarer.api.util.Misc.getHighlightColor()
                );
            }
        }

        /**
         * Check if fleet still has AI cores in cargo
         */
        private boolean hasAICoresInCargo() {
            CargoAPI cargo = fleet.getCargo();
            return cargo.getCommodityQuantity(Commodities.ALPHA_CORE) > 0 ||
                   cargo.getCommodityQuantity(Commodities.BETA_CORE) > 0 ||
                   cargo.getCommodityQuantity(Commodities.GAMMA_CORE) > 0;
        }

        /**
         * Deliver AI cores to Draconis faction when fleet returns to base
         */
        private void deliverAICores() {
            CargoAPI cargo = fleet.getCargo();

            int alphaDelivered = (int) cargo.getCommodityQuantity(Commodities.ALPHA_CORE);
            int betaDelivered = (int) cargo.getCommodityQuantity(Commodities.BETA_CORE);
            int gammaDelivered = (int) cargo.getCommodityQuantity(Commodities.GAMMA_CORE);

            if (alphaDelivered == 0 && betaDelivered == 0 && gammaDelivered == 0) {
                Global.getLogger(RemnantRaidFleetBehavior.class).warn(
                    "Fleet marked as having cores but none found in cargo - possibly stolen by player/pirates"
                );
                return;
            }

            // Remove cores from fleet
            cargo.removeCommodity(Commodities.ALPHA_CORE, alphaDelivered);
            cargo.removeCommodity(Commodities.BETA_CORE, betaDelivered);
            cargo.removeCommodity(Commodities.GAMMA_CORE, gammaDelivered);

            int totalCores = alphaDelivered + betaDelivered + gammaDelivered;

            Global.getLogger(RemnantRaidFleetBehavior.class).info(
                "===================================================="
            );
            Global.getLogger(RemnantRaidFleetBehavior.class).info(
                "=== AI CORES SUCCESSFULLY DELIVERED ==="
            );
            Global.getLogger(RemnantRaidFleetBehavior.class).info(
                "Alpha cores: " + alphaDelivered
            );
            Global.getLogger(RemnantRaidFleetBehavior.class).info(
                "Beta cores: " + betaDelivered
            );
            Global.getLogger(RemnantRaidFleetBehavior.class).info(
                "Gamma cores: " + gammaDelivered
            );
            Global.getLogger(RemnantRaidFleetBehavior.class).info(
                "===================================================="
            );

            // Notify player
            if (shouldPlayerSeeFleet()) {
                Global.getSector().getCampaignUI().addMessage(
                    "Draconis expedition fleet has successfully delivered " + totalCores +
                    " AI core" + (totalCores > 1 ? "s" : "") + " to Alliance command",
                    com.fs.starfarer.api.util.Misc.getPositiveHighlightColor()
                );
            }

            // Mark cores as delivered
            fleet.getMemoryWithoutUpdate().set("$draconis_coresDelivered", true);
        }

        /**
         * Check if player should be able to see fleet notifications
         */
        private boolean shouldPlayerSeeFleet() {
            SectorEntityToken player = Global.getSector().getPlayerFleet();
            if (player == null || fleet == null) return false;

            // Player can see if in same system or close enough
            if (player.getContainingLocation() != fleet.getContainingLocation()) {
                return false;
            }

            float distance = com.fs.starfarer.api.util.Misc.getDistance(
                player.getLocation(), fleet.getLocation()
            );

            // Within sensor range (roughly)
            return distance < 5000f;
        }
    }
}