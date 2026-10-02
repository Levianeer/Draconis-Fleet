package levianeer.draconis.data.campaign.intel.longsight.crisis;

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
import levianeer.draconis.data.campaign.ids.Factions;
import levianeer.draconis.data.campaign.intel.longsight.XLII_LongsightOfficerPlugin;

import java.util.Random;

/**
 * A single Office Takeover crisis Bastion's standing patrol garrison. Adapted from
 * {@code XLII_OfficeGarrisonManager} (Ladon's own garrison): same {@link SeededFleetManager} base,
 * respawn/reorbit shape, and {@code XLII_intelligence_office} faction (the active-combat identity
 * for every crisis fleet - see {@link XLII_LongsightBastionIntel}'s class doc; only
 * captured/destroyed *markets* end up Draconis, per Stage 4). Hostility is forced unconditionally
 * via {@link XLII_LongsightBastionIntel#applyCrisisHostility}, unlike Ladon's access-flag-gated
 * {@code XLII_OfficeSystem.applyHostility()}.
 * <p>
 * Persists across save/reload with no special re-registration path: scripts added once via
 * {@code addScript()} and never unconditionally recreated persist on their own (see the
 * persistence note on {@link XLII_LongsightCrisisManager}'s class doc for the fuller explanation).
 */
public class XLII_LongsightCrisisGarrisonManager extends SeededFleetManager {

    private static final float COMBAT_POINTS = 600f;

    private static final float RESPAWN_MIN_DAYS = 6f;
    private static final float RESPAWN_MAX_DAYS = 12f;

    private static final float REORBIT_INTERVAL_DAYS = 7f;

    private final SectorEntityToken anchor;
    private final Random random = new Random();

    private float respawnTimer = 0f;
    private float nextRespawnTarget = -1f;
    private float reorbitTimer = 0f;

    public XLII_LongsightCrisisGarrisonManager(StarSystemAPI system, SectorEntityToken anchor, float inflateRangeLY) {
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

        if (anchor instanceof CampaignFleetAPI stationFleet) {
            XLII_LongsightBastionIntel.applyCrisisHostility(stationFleet);
        }
        for (SeededFleet curr : fleets) {
            if (curr.fleet != null) XLII_LongsightBastionIntel.applyCrisisHostility(curr.fleet);
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
                Factions.INTELLIGENCE_OFFICE,
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

        fleet.setNoFactionInName(true);
        fleet.setName("Unmarked Battlegroup");
        fleet.getMemoryWithoutUpdate().set(XLII_LongsightBastionIntel.CRISIS_FLEET_FLAG, true);
        inflateWithAICores(fleet, seedRandom);
        assignLongsightCommander(fleet, seedRandom);
        XLII_LongsightBastionIntel.applyCrisisHostility(fleet);

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

    /**
     * Every fleet this crisis spawns is commanded by the same "Sigma Longsight" officer already
     * used for the Bastion itself ({@code XLII_LongsightBastionIntel.spawnBastionFleet()}), not
     * just crewed hull-by-hull with anonymous Alpha Core officers (see {@link #inflateWithAICores}).
     * Replaces the flagship's own captain and elevates it to fleet commander, so the entity shown
     * as commanding the fleet (fleet info, radio calls) reads as the same recurring device driving
     * the whole crisis, not an interchangeable core.
     */
    private void assignLongsightCommander(CampaignFleetAPI fleet, Random random) {
        FleetMemberAPI flagship = fleet.getFlagship();
        if (flagship == null) return;

        PersonAPI existing = flagship.getCaptain();
        if (existing != null) fleet.getFleetData().removeOfficer(existing);

        PersonAPI commander = new XLII_LongsightOfficerPlugin().createPerson(
                XLII_LongsightOfficerPlugin.CORE_ID, Factions.INTELLIGENCE_OFFICE, random);
        fleet.getFleetData().addOfficer(commander);
        flagship.setCaptain(commander);
        fleet.setCommander(commander);
    }
}
