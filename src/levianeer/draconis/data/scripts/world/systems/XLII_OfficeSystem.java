package levianeer.draconis.data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.AccretionDiskGenPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.NameGenData;
import com.fs.starfarer.api.impl.campaign.procgen.ProcgenUsedNames;
import com.fs.starfarer.api.impl.campaign.procgen.ProcgenUsedNames.NamePick;
import com.fs.starfarer.api.impl.campaign.procgen.StarGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.GenContext;
import com.fs.starfarer.api.impl.campaign.procgen.TerrainGenDataSpec;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin.CoronaParams;
import org.lwjgl.util.vector.Vector2f;
import levianeer.draconis.data.campaign.characters.XLII_PersonHaspelMonroe;

import java.awt.Color;
import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.INTELLIGENCE_OFFICE;

/**
 * A remote system holding an Alliance Intelligence Office black site. Placed at a random
 * far-edge location and tagged {@link Tags#THEME_HIDDEN} - the same flag vanilla uses for the
 * Ziggurat's system, Limbo, and the other off-map black sites.
 */
public class XLII_OfficeSystem implements SectorGeneratorPlugin {

    /** Stable lookup id, independent of the randomized display name - see {@link StarSystemAPI#setOptionalUniqueId}. */
    public static final String SYSTEM_ID = "XLII_office_system";
    public static final String LADON_ID = "ladon";

    /** The actual vanilla Remnant Nexus hull (remnant_station2), not a ship standing in for one. */
    private static final String BASTION_VARIANT = "remnant_station2_Standard";
    /** Stable lookup id - see {@code XLII_ModPlugin} for how this is re-found on load. */
    public static final String BASTION_ID = "XLII_office_bastion";
    private static final float BASTION_ORBIT_RADIUS = 150f;

    /**
     * Global memory flag. Unset, Ladon's defenders are hostile to everything that comes near -
     * player, other factions, all of it. A future story beat sets this once the site is no
     * longer a secret to be shot at, and the bastion/fleet re-check it every tick
     * (see {@link #applyHostility} and {@code XLII_OfficeGarrisonManager.advance()}), so flipping
     * it mid-game stands down defenders that already exist, not just ones spawned afterward.
     */
    public static final String ACCESS_GRANTED_FLAG = "$XLII_officeAccessGranted";

    public static boolean isAccessGranted() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(ACCESS_GRANTED_FLAG);
    }

    /**
     * The system's actual display name, picked once at new-game start from vanilla's own star
     * name pool (see {@link #pickSystemName}) rather than a fixed string. Stored in global
     * memory - unlike {@link #SYSTEM_ID}, which is fixed - so other code, Java or rules.csv, can
     * look up the current name instead of assuming "Ouroboros".
     */
    public static final String SYSTEM_NAME_FLAG = "$XLII_officeSystemName";

    public static String getSystemName() {
        return Global.getSector().getMemoryWithoutUpdate().getString(SYSTEM_NAME_FLAG);
    }

    /** Forces universal hostility (not just vs. the player) unless {@link #ACCESS_GRANTED_FLAG} is set. */
    public static void applyHostility(CampaignFleetAPI fleet) {
        boolean hostile = !isAccessGranted();
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, hostile);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, hostile);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, hostile);
    }

    /**
     * Own fixed radius for the black hole - deliberately not read from the {@code black_hole}
     * row in vanilla's {@code star_gen_data.csv} ({@link StarGenDataSpec#getMinRadius}/{@code
     * getMaxRadius}), the same table actual stars are sized from, so this stays fixed regardless of
     * vanilla or other-mod changes there.
     */
    private static final float BLACK_HOLE_RADIUS = 150f;

    /** Scales the black hole's radius, event horizon, and accretion disk up together. */
    private static final float BLACK_HOLE_SIZE_MULT = 2f;

    /** Accretion disk radius passed into vanilla's {@link AccretionDiskGenPlugin}. */
    private static final float ACCRETION_DISK_RADIUS = 550f;

    private static final float MIN_RADIUS = 36000f;
    private static final float MAX_RADIUS = 42000f;
    private static final float MIN_SEPARATION = 1750f;

    @Override
    public void generate(SectorAPI sector) {

        Vector2f loc = pickRemoteLocation(sector);

        String name = pickSystemName();
        sector.getMemoryWithoutUpdate().set(SYSTEM_NAME_FLAG, name);
        StarSystemAPI system = sector.createStarSystem(name);
        system.setOptionalUniqueId(SYSTEM_ID);
        system.addTag(Tags.THEME_HIDDEN);
        system.addTag(Tags.THEME_SPECIAL);

        // Same black-site ambience vanilla uses for the Ziggurat's system - see TTBlackSite.generate().
        system.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "music_campaign_alpha_site");

        system.setBackgroundTextureFilename("graphics/mod/backgrounds/ouroboros.png");

        final float ladonAngle = 60f;
        final float ladonSize = 50f;
        final float ladonDistance = 2800;
        final float ladonOrbit = 380f;

        final float vritraAngle = 200f;
        final float vritraSize = 40f;
        final float vritraDistance = 4200;
        final float vritraOrbit = 260f;

        PlanetAPI star = system.initStar(
                "XLII_ouroboros",
                "star_red_supergiant",
                480,
                loc.x,
                loc.y,
                220);
        star.setCustomDescriptionId("star_ouroboros");
        star.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
        star.getSpec().setGlowColor(new Color(200, 70, 50, 140));
        star.getSpec().setAtmosphereThickness(0.7f);
        star.applySpecChanges();
        system.setLightColor(new Color(255, 200, 175));

        addShroud(system, star);
        addBlackHole(system, star);

        // Ladon - buried Office black site. Unclaimed - no market, no owning faction; the
        // battlestation below is the only visible sign anything is here.
        PlanetAPI ladon = system.addPlanet(LADON_ID, star, "Ladon",
                "frozen", ladonAngle, ladonSize, ladonDistance, ladonOrbit);
        ladon.setCustomDescriptionId("planet_ladon");

        // Vritra - uninhabited, unconfirmed Domain remnants
        PlanetAPI vritra = system.addPlanet("vritra", star, "Vritra",
                "frozen", vritraAngle, vritraSize, vritraDistance, vritraOrbit);
        vritra.setCustomDescriptionId("planet_vritra");
        addVritraRuins(vritra);

        // The Cordon - dust and rock sitting under Ladon's own orbit, just visual clutter.
        system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, ladonDistance, 150f);
        system.addAsteroidBelt(star, 80, ladonDistance, 150, 90, 160, Terrain.ASTEROID_BELT, "The Cordon");

        system.autogenerateHyperspaceJumpPoints(false, false);

        addBastion(system, ladon);
        addOfficeEntities(system, star);
    }

    /**
     * A dense magnetic field wrapping the outer system, past Ladon's orbit and the bastions'
     * patrol radius. In-universe cover for why Ladon doesn't show up on sensor logs; visually,
     * an ominous violet/blue aurora rather than the warmer hues vanilla uses for inhabited
     * systems - matches Eos/Duzahk's magnetic field setup but themed for the Office black site.
     */
    private void addShroud(StarSystemAPI system, PlanetAPI star) {
        SectorEntityToken shroud = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldParams(1600f, // terrain effect band width
                        4200f, // terrain effect middle radius
                        star, // entity that it's around
                        3200f, // visual band start
                        4000f, // visual band end
                        new Color(55, 25, 20, 45), // base color
                        0.75f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
                        new Color(120, 60, 50),
                        new Color(160, 55, 80),
                        new Color(200, 60, 120),
                        new Color(240, 90, 160),
                        new Color(255, 140, 190),
                        new Color(255, 150, 130),
                        new Color(200, 90, 70)));
        shroud.setCircularOrbit(star, 0, 0, 100);
    }

    /**
     * Layers black-hole visuals (corona + accretion disk) directly onto Ouroboros instead of
     * spawning a separate {@code black_hole}-typed {@link PlanetAPI} co-located with the star
     * (as vanilla's {@code AbyssalRogueStellarObjectEPEC.addBlackHole()} does) - that approach
     * puts two clickable entities on the same point, showing up as a duplicate in the system
     * list/tooltips. {@link CoronaParams#relatedEntity} and {@link GenContext#parent}/{@code
     * center} accept any {@link SectorEntityToken}/{@link PlanetAPI}, not just the black hole
     * planet type, so the event horizon ({@link Terrain#EVENT_HORIZON}) and accretion disk
     * ({@link AccretionDiskGenPlugin}) can anchor on {@code star} directly.
     * <p>
     * The black hole "look" (dark sphere, violet halo) is just {@link PlanetSpecAPI} fields, so
     * this copies the black hole spec's corona sprite/color/size onto the star's own spec rather
     * than calling {@link PlanetSpecAPI#setBlackHole}, which is a gameplay flag other systems key
     * off of at runtime, not a render toggle.
     */
    private void addBlackHole(StarSystemAPI system, PlanetAPI star) {
        StarGenDataSpec starData = (StarGenDataSpec)
                Global.getSettings().getSpec(StarGenDataSpec.class, StarTypes.BLACK_HOLE, false);

        PlanetSpecAPI blackHoleSpec = null;
        for (PlanetSpecAPI spec : Global.getSettings().getAllPlanetSpecs()) {
            if (spec.getPlanetType().equals(StarTypes.BLACK_HOLE)) {
                blackHoleSpec = spec;
                break;
            }
        }
        if (blackHoleSpec != null) {
            star.getSpec().setCoronaTexture(blackHoleSpec.getCoronaTexture());
            star.getSpec().setCoronaColor(new Color(155, 25, 25));
            star.getSpec().setCoronaSize(blackHoleSpec.getCoronaSize());
            star.applySpecChanges();
        }

        float radius = BLACK_HOLE_RADIUS * BLACK_HOLE_SIZE_MULT;

        float corona = radius * starData.getCoronaMult();
        if (corona < starData.getCoronaMin()) corona = starData.getCoronaMin();
        corona *= BLACK_HOLE_SIZE_MULT;

        float flare = (starData.getMinFlare() + starData.getMaxFlare()) / 2f;

        SectorEntityToken eventHorizon = system.addTerrain(Terrain.EVENT_HORIZON,
                new CoronaParams(radius + corona, (radius + corona) / 4f,
                        star, starData.getSolarWind(),
                        flare,
                        starData.getCrLossMult()));
        eventHorizon.setCircularOrbit(star, 0, 0, 100);

        TerrainGenDataSpec accretionDiskData = null;
        for (TerrainGenDataSpec spec : Global.getSettings().getAllSpecs(TerrainGenDataSpec.class)) {
            if (spec.getId().equals(Tags.ACCRETION_DISK)) {
                accretionDiskData = spec;
                break;
            }
        }
        if (accretionDiskData != null) {
            GenContext context = new GenContext(null, system, star, starData, star,
                    -1, "ANY", ACCRETION_DISK_RADIUS * BLACK_HOLE_SIZE_MULT, 0f, null, 0);
            new AccretionDiskGenPlugin().generate(accretionDiskData, context);
        }
    }

    /**
     * Vritra's "unconfirmed Domain remnants" made tangible - a condition-only market (no economy,
     * same {@code planetConditionMarketOnly} setup vanilla uses for uninhabited ruin worlds, e.g.
     * {@code PlanetConditionGenerator}) just to carry the {@link Conditions#RUINS_SCATTERED}
     * hazard condition, surveyable/salvageable the normal way.
     */
    private void addVritraRuins(PlanetAPI vritra) {
        MarketAPI market = Global.getFactory().createMarket("market_" + vritra.getId(), vritra.getName(), 1);
        market.setPlanetConditionMarketOnly(true);
        market.setPrimaryEntity(vritra);
        market.setFactionId(Factions.NEUTRAL);
        vritra.setMarket(market);

        market.addCondition(Conditions.RUINS_SCATTERED);
        market.reapplyConditions();
    }

    /**
     * Ladon's sole permanent guardian - a Remnant Nexus hull in station mode, orbiting close in.
     * Faction-owned but not tied to any market. Mirrors vanilla's Remnant nexus battlestation
     * setup exactly (same MEMORY_KEY_NO_JUMP + stationMode + no-AI setup). Gets a stable id
     * ({@link #BASTION_ID}) so {@code XLII_ModPlugin} can re-find it after a save/load to
     * register its {@link XLII_OfficeGarrisonManager}, which stops for good once the station
     * is destroyed.
     */
    private void addBastion(StarSystemAPI system, PlanetAPI ladon) {
        CampaignFleetAPI bastion = FleetFactoryV3.createEmptyFleet(INTELLIGENCE_OFFICE, FleetTypes.BATTLESTATION, null);
        bastion.setId(BASTION_ID);

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, BASTION_VARIANT);
        bastion.getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());

        bastion.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        bastion.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        applyHostility(bastion);
        bastion.setStationMode(true);
        bastion.setAI(null);
        bastion.clearAbilities();
        bastion.getDetectedRangeMod().modifyFlat("gen", 500f);

        // Ladon is Monroe's personal black site, so its commander is her, not the anonymous
        // "Longsight" AI officer used by Office Takeover crisis fleets (see
        // XLII_LongsightBastionIntel's class doc for why those are deliberately impersonal).
        // createOrEnsureRegistered() is called here because addBastion() runs during world gen,
        // before XLII_ModPlugin.onGameLoad()'s character-init would otherwise register her; it's
        // idempotent, so this is a no-op on a normal load.
        XLII_PersonHaspelMonroe.createOrEnsureRegistered();
        PersonAPI commander = Global.getSector().getImportantPeople()
                .getPerson(XLII_PersonHaspelMonroe.PERSON_ID);
        bastion.setCommander(commander);
        bastion.getFlagship().setCaptain(commander);

        system.addEntity(bastion);
        bastion.setCircularOrbitPointingDown(ladon, 0f, BASTION_ORBIT_RADIUS, 50f);
    }

    /**
     * Office-owned infrastructure orbiting close in around Ouroboros itself - comm relay, sensor
     * array, and nav buoy sharing one tight orbit, spaced 120 degrees apart so they move together
     * as a loose ring. Ordinary faction-owned strategic objectives - not tied to the hidden/
     * hostile status of the site, so they're capturable like any other Remnant-system objective.
     */
    private void addOfficeEntities(StarSystemAPI system, PlanetAPI star) {
        final float orbitRadius = 1000f;
        final float orbitDays = 90f;

        SectorEntityToken commRelay = system.addCustomEntity(null, null,
                Entities.COMM_RELAY, INTELLIGENCE_OFFICE, null);
        commRelay.setCircularOrbit(star, 0f, orbitRadius, orbitDays);

        SectorEntityToken sensorArray = system.addCustomEntity(null, null,
                Entities.SENSOR_ARRAY, INTELLIGENCE_OFFICE, null);
        sensorArray.setCircularOrbit(star, 120f, orbitRadius, orbitDays);

        SectorEntityToken navBuoy = system.addCustomEntity(null, null,
                Entities.NAV_BUOY, INTELLIGENCE_OFFICE, null);
        navBuoy.setCircularOrbit(star, 240f, orbitRadius, orbitDays);
    }

    /**
     * Picks an unused star name from vanilla's own procgen pool ({@code
     * data/campaign/procgen/name_gen_data.csv}, tag {@code star}) via the same {@link
     * ProcgenUsedNames#pickName} core sector generation uses - checked against every name
     * already claimed sector-wide, so this can't collide with a vanilla or other-mod system.
     * Marks the pick used (unless the CSV row is flagged {@code reusable}) so nothing generated
     * afterward claims it either.
     */
    private String pickSystemName() {
        NamePick pick = ProcgenUsedNames.pickName(NameGenData.TAG_STAR, null, null);
        if (pick == null) return "Ouroboros";

        if (!pick.spec.isReusable()) {
            ProcgenUsedNames.notifyUsed(pick.nameWithRomanSuffixIfAny);
        }
        return pick.nameWithRomanSuffixIfAny;
    }

    /**
     * Picks a location far past Fafnir, retrying against every star system already
     * in the sector so it doesn't land on top of one. Falls back to the first candidate
     * after enough retries - at this radius a collision is vanishingly unlikely.
     */
    private Vector2f pickRemoteLocation(SectorAPI sector) {
        Random random = new Random();
        Vector2f candidate = null;

        for (int attempt = 0; attempt < 50; attempt++) {
            float angle = random.nextFloat() * 360f;
            float radius = MIN_RADIUS + random.nextFloat() * (MAX_RADIUS - MIN_RADIUS);
            candidate = new Vector2f(
                    (float) Math.cos(Math.toRadians(angle)) * radius,
                    (float) Math.sin(Math.toRadians(angle)) * radius);

            boolean tooClose = false;
            for (StarSystemAPI other : sector.getStarSystems()) {
                float dx = candidate.x - other.getLocation().x;
                float dy = candidate.y - other.getLocation().y;
                if (dx * dx + dy * dy < MIN_SEPARATION * MIN_SEPARATION) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) return candidate;
        }
        return candidate;
    }
}
