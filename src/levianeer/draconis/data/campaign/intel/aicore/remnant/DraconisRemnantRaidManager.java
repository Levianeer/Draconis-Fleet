package levianeer.draconis.data.campaign.intel.aicore.remnant;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Manages Draconis raids on Remnant stations for AI core acquisition
 * Sends task forces to engage Remnant defenses and recover technology
 */
public class DraconisRemnantRaidManager implements EveryFrameScript {

    private static final float CHECK_INTERVAL = 30f; // Check every 30 days
    private static final float RAID_CHANCE = 0.4f; // 40% chance to raid when target available

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

        Global.getLogger(this.getClass()).info("Fleet points: " + fleetPoints);

        // Create fleet parameters
        FleetParamsV3 params = new FleetParamsV3(
            source,                          // Source market
            null,                            // Location (will be set after creation)
            DRACONIS,                        // Faction
            null,                            // Route (none, this is a raid)
            FleetTypes.TASK_FORCE,          // Fleet type
            fleetPoints,                     // Combat FP
            0f,                              // Freighter FP
            0f,                              // Tanker FP
            0f,                              // Transport FP
            0f,                              // Liner FP
            0f,                              // Utility FP
            0f                               // Quality bonus
        );

        // Set quality mod based on source market
        params.qualityMod = source.getShipQualityFactor();

        // Officer quality
        params.officerNumberMult = 1.5f; // More officers for dangerous mission
        params.officerLevelBonus = 1;

        // Create the fleet
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null) {
            Global.getLogger(this.getClass()).error(
                "Failed to create Remnant raid fleet!"
            );
            return;
        }

        // Configure fleet
        fleet.setName("Expedition");
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set("$draconis_remnantRaid", true);
        fleet.getMemoryWithoutUpdate().set("$draconis_raidTarget", target);

        // Make fleet pursue enemies aggressively
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
        // Draconis is now hostile to Remnant via DraconisWorldGen, so normal faction hostility applies

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
            1000f, // Give them plenty of time to reach target
            "traveling to " + target.getName()
        );

        // Assignment 2: Patrol target system hunting Remnant
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.PATROL_SYSTEM,
            target.getCenter(),
            90f, // Patrol for 90 days
            "hunting Remnant forces in " + target.getName()
        );

        // Assignment 3: Return to base
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION,
            sourceEntity,
            1000f, // Indefinite - take as long as needed
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
}