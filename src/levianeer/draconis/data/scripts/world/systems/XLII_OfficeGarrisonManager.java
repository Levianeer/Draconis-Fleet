package levianeer.draconis.data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.SeededFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.Misc;

import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.INTELLIGENCE_OFFICE;

/**
 * Ladon's sole standing defense fleet, spawned Remnant-nexus style instead of through a market's
 * industries. {@link SeededFleetManager} only inflates the fleet from its seed once the player
 * fleet is within {@code inflateRangeLY} and despawns it again once they leave - so the garrison
 * exists without needing any economy behind it.
 * <p>
 * The anchor is Ladon's bastion (a {@link CampaignFleetAPI}) - this manager stops for good once
 * it's destroyed (see {@link #isAnchorGone}).
 * <p>
 * Losses respawn - a destroyed seed is dropped by the base class, and {@link #tickRespawn} adds a
 * fresh one back after a fixed cooldown rather than leaving Ladon permanently undefended.
 * <p>
 * The initial orbit order only lasts 30 days, and the base game's own fleet AI can pull the fleet
 * off it entirely (chasing a raider, responding to a distress call, etc.) with nothing here to
 * bring it back. {@link #tickReorbit} re-issues the order every {@link #REORBIT_INTERVAL_DAYS}
 * days so the fleet can't wander off station indefinitely - skipped while it's mid-fight so combat
 * isn't interrupted.
 */
public class XLII_OfficeGarrisonManager extends SeededFleetManager {

    private static final float COMBAT_POINTS = 600f;

    private static final float RESPAWN_MIN_DAYS = 6f;
    private static final float RESPAWN_MAX_DAYS = 12f;

    private static final float REORBIT_INTERVAL_DAYS = 7f;

    private final SectorEntityToken anchor;
    private final Random random = new Random();

    private float respawnTimer = 0f;
    private float nextRespawnTarget = -1f;
    private float reorbitTimer = 0f;

    public XLII_OfficeGarrisonManager(StarSystemAPI system, SectorEntityToken anchor, float inflateRangeLY) {
        super(system, inflateRangeLY);
        this.anchor = anchor;
        addSeed(Misc.genRandomSeed());
    }

    @Override
    public void advance(float amount) {
        if (isAnchorGone()) {
            fleets.clear(); // isDone() -> true; the game unregisters this script on its own
            return;
        }

        // Re-checked every tick (not just at spawn) so a mid-game flip of ACCESS_GRANTED_FLAG
        // stands down defenders that already exist, not just ones spawned afterward.
        if (anchor instanceof CampaignFleetAPI stationFleet) {
            XLII_OfficeSystem.applyHostility(stationFleet);
        }
        for (SeededFleet curr : fleets) {
            if (curr.fleet != null) XLII_OfficeSystem.applyHostility(curr.fleet);
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        tickRespawn(days);
        tickReorbit(days);

        super.advance(amount);
    }

    /** Replaces the fleet lost to destruction (not just distance-despawn) after a fixed cooldown. */
    private void tickRespawn(float days) {
        if (!fleets.isEmpty()) {
            respawnTimer = 0f;
            nextRespawnTarget = -1f;
            return;
        }
        if (nextRespawnTarget < 0f) {
            float span = RESPAWN_MAX_DAYS - RESPAWN_MIN_DAYS;
            nextRespawnTarget = RESPAWN_MIN_DAYS + random.nextFloat() * span;
        }
        respawnTimer += days;
        if (respawnTimer >= nextRespawnTarget) {
            addSeed(Misc.genRandomSeed());
            respawnTimer = 0f;
            nextRespawnTarget = -1f;
        }
    }

    /** Forces the fleet back onto its orbit order periodically so it can't stay distracted forever. */
    private void tickReorbit(float days) {
        reorbitTimer += days;
        if (reorbitTimer < REORBIT_INTERVAL_DAYS) return;
        reorbitTimer = 0f;

        for (SeededFleet curr : fleets) {
            if (curr.fleet == null || curr.fleet.getBattle() != null) continue;
            curr.fleet.getAI().clearAssignments();
            curr.fleet.getAI().addAssignment(FleetAssignment.ORBIT_PASSIVE, anchor, 30f, null);
        }
    }

    private boolean isAnchorGone() {
        if (anchor instanceof CampaignFleetAPI fleet) {
            return fleet.isDespawning() || fleet.isEmpty() || fleet.getContainingLocation() == null;
        }
        return anchor.getContainingLocation() == null;
    }

    @Override
    protected CampaignFleetAPI spawnFleet(long seed) {
        Random seedRandom = new Random(seed);

        // No MarketAPI source - these patrols aren't tied to a colony's economy at all.
        FleetParamsV3 params = new FleetParamsV3(
                null,                     // loc in hyper; not needed, fleet spawns in-system
                INTELLIGENCE_OFFICE,
                50f,                     // quality override
                FleetTypes.PATROL_LARGE,
                COMBAT_POINTS,
                0f, 0f, 0f, 0f, 0f,
                0f);
        params.officerLevelBonus = 7;
        params.quality = 7;
        params.averageSMods = 7;
        params.random = seedRandom;
        params.timestamp = Global.getSector().getClock().getTimestamp();

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setNoFactionInName(false);
        fleet.setName("");
        inflateWithAICores(fleet, seedRandom);
        XLII_OfficeSystem.applyHostility(fleet);

        system.addEntity(fleet);
        fleet.setLocation(anchor.getLocation().x, anchor.getLocation().y);
        fleet.getAI().addAssignment(FleetAssignment.ORBIT_PASSIVE, anchor, 30f, null);

        return fleet;
    }

    /** Every combat ship gets an Alpha Core captain - no human officers, no partial coverage. */
    private void inflateWithAICores(CampaignFleetAPI fleet, Random random) {
        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE);
        if (plugin == null) return;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing() || member.isCivilian()) continue;

            PersonAPI existing = member.getCaptain();
            if (existing != null) fleet.getFleetData().removeOfficer(existing);

            PersonAPI core = plugin.createPerson(Commodities.ALPHA_CORE, fleet.getFaction().getId(), random);
            if (core == null) continue;
            fleet.getFleetData().addOfficer(core);
            member.setCaptain(core);
        }

        if (fleet.getFlagship() != null) {
            fleet.setCommander(fleet.getFlagship().getCaptain());
        }
    }
}
