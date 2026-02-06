package levianeer.draconis.data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

public class XLII_System implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {

        // SCALING FACTOR
        // 1.0 = original, 1.5 = 50% larger, 2.0 = twice as large, etc.
        final float SCALE = 2.0f; // 2x is probably to highest this should go

        // Hyperspace Location
        final float systemLocX = 4200f; // New location: -4200,9600
        final float systemLocY = 9600f;  // Old location: -750,-5250

        StarSystemAPI system = sector.createStarSystem("Fafnir");
        system.setBackgroundTextureFilename("graphics/mod/backgrounds/fafnirbg.png");

        // Scaled distances - multiply by SCALE
        final float asteroidsDistance = 7500f * SCALE;
        final float asteroidsOrbit = 460f;

        final float athebyneAngle = 0f;
        final float athebyneSize = 140f;
        final float athebyneDistance = 2200f * SCALE;
        final float athebyneOrbit = 225f;

        final float itoronAngle = 0f;
        final float itoronSize = 180f;
        final float itoronDistance = 3400f * SCALE;
        final float itoronOrbit = 365f;

        final float koriAngle = 0f;
        final float koriSize = 40f;
        final float koriDistance = 350f * SCALE;
        final float koriOrbit = 30f;

        final float voriumAngle = 30f;
        final float voriumSize = 500f;
        final float voriumDistance = 8800f * SCALE;
        final float voriumOrbit = 800f;

        final float pirateStationAngle = 170f;
        final float pirateStationDistance = 12000f * SCALE;
        final float pirateStationOrbit = 640f;

        final float jp1Angle = 315f;
        final float jp1Distance = 750f * SCALE;
        final float jp1Orbit = 60f;

        final float jp2Angle = 360f;
        final float jp2Distance = 850f * SCALE;
        final float jp2Orbit = 90f;

        final float jp3Angle = 200f;
        final float jp3Distance = 1000f * SCALE;
        final float jp3Orbit = 75f;

        final float gateAngle = 180f;
        final float gateDistance = 10000f * SCALE;
        final float gateOrbit = 560f;

        final float arrayDistance = 4100f * SCALE;
        final float arrayOrbit = 400f;

        // NEW CELESTIAL BODIES - Expansion

        // Nidhogg - Inner scorched planet
        final float nidhoggAngle = 45f;
        final float nidhoggSize = 60f;
        final float nidhoggDistance = 1500f * SCALE;
        final float nidhoggOrbit = 90f;

        // Shard - Athebyne's bombarded moon
        final float shardAngle = 180f;
        final float shardDistance = 220f * SCALE;
        final float shardOrbit = 28f;

        // Tiamat - Vorium's metallic moon
        final float tiamatAngle = 120f;
        final float tiamatSize = 35f;
        final float tiamatDistance = 670f * SCALE;
        final float tiamatOrbit = 20f;

        // Jormungandr - Outer ice giant
        final float jormungandrAngle = 90f;
        final float jormungandrSize = 400f;
        final float jormungandrDistance = 9000f * SCALE;
        final float jormungandrOrbit = 7300f;

        // Hel - Jormungandr's frozen moon
        final float helAngle = 0f;
        final float helSize = 45f;
        final float helDistance = 900f * SCALE;
        final float helOrbit = 26f;

        // Ladon - Distant dwarf planet
        final float ladonAngle = 135f;
        final float ladonSize = 28f;
        final float ladonDistance = 11000f * SCALE;
        final float ladonOrbit = 12000f;

        // Vritra - Elliptical dwarf planet
        final float vritraAngle = 315f;
        final float vritraSize = 26f;
        final float vritraDistance = 12000f * SCALE;
        final float vritraOrbit = 15000f;

        PlanetAPI star = system.initStar(
                "XLII_fafnir",
                "star_yellow",
                1000,
                systemLocX,
                systemLocY,
                500);
        star.setCustomDescriptionId("star_fafnir");

        system.addAsteroidBelt(
                star,
                1000,
                asteroidsDistance - 435,
                350,
                320,
                1280,
                Terrain.ASTEROID_BELT,
                "Fafnir's Inner Belt"
        );

        system.addAsteroidBelt(
                star,
                2000,
                pirateStationDistance,
                400,
                1200,
                2400,
                Terrain.ASTEROID_BELT,
                "Fafnir's Outer Belt"
        );

        // ASTEROID BELT - Offsets stay the same for visual consistency
        system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDistance, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, asteroidsDistance - 1300, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDistance - 1100, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, asteroidsDistance - 900, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDistance - 700, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, asteroidsDistance + 500, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDistance + 400, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, asteroidsDistance + 300, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 3, Color.white, 256f, asteroidsDistance - 200, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, asteroidsDistance - 100, asteroidsOrbit);

        system.addRingBand(star, "misc", "rings_ice0", 256f, 0, Color.white, 256f, asteroidsDistance, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, asteroidsDistance + 100, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_ice0", 256f, 1, Color.white, 256f, asteroidsDistance + 200, asteroidsOrbit);
        system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, asteroidsDistance + 300, asteroidsOrbit);

        // Fafnir Mirror system
        SectorEntityToken fafnir_mirror1 = system.addCustomEntity("fafnir_mirror1", "Fafnir Stellar Mirror", "stellar_mirror", DRACONIS);
        fafnir_mirror1.setCircularOrbitPointingDown(system.getEntityById("XLII_fafnir"), 0, 1500, 90);
        fafnir_mirror1.setCustomDescriptionId("XLII_stellar_mirror");

        SectorEntityToken fafnir_mirror2 = system.addCustomEntity("fafnir_mirror2", "Fafnir Stellar Mirror", "stellar_mirror", DRACONIS);
        fafnir_mirror2.setCircularOrbitPointingDown(system.getEntityById("XLII_fafnir"), 120, 1500, 90);
        fafnir_mirror2.setCustomDescriptionId("XLII_stellar_mirror");

        SectorEntityToken fafnir_mirror3 = system.addCustomEntity("fafnir_mirror3", "Fafnir Stellar Mirror", "stellar_mirror", DRACONIS);
        fafnir_mirror3.setCircularOrbitPointingDown(system.getEntityById("XLII_fafnir"), 240, 1500, 90);
        fafnir_mirror3.setCustomDescriptionId("XLII_stellar_mirror");

        SectorEntityToken fafnir_mirror4 = system.addCustomEntity("fafnir_mirror1", "Fafnir Stellar Mirror", "stellar_mirror", DRACONIS);
        fafnir_mirror4.setCircularOrbitPointingDown(system.getEntityById("XLII_fafnir"), 60, 1500, 90);
        fafnir_mirror4.setCustomDescriptionId("XLII_stellar_mirror");

        SectorEntityToken fafnir_mirror5 = system.addCustomEntity("fafnir_mirror2", "Fafnir Stellar Mirror", "stellar_mirror", DRACONIS);
        fafnir_mirror5.setCircularOrbitPointingDown(system.getEntityById("XLII_fafnir"), 180, 1500, 90);
        fafnir_mirror5.setCustomDescriptionId("XLII_stellar_mirror");

        SectorEntityToken fafnir_mirror6 = system.addCustomEntity("fafnir_mirror3", "Fafnir Stellar Mirror", "stellar_mirror", DRACONIS);
        fafnir_mirror6.setCircularOrbitPointingDown(system.getEntityById("XLII_fafnir"), 300, 1500, 90);
        fafnir_mirror6.setCustomDescriptionId("XLII_stellar_mirror");

        // PLANETS

        // Nidhogg - Scorched inner planet (tidally locked)
        PlanetAPI nidhogg = system.addPlanet("nidhogg", star, "Nidhogg",
                "barren-desert", nidhoggAngle, nidhoggSize, nidhoggDistance, nidhoggOrbit);
        nidhogg.setCustomDescriptionId("planet_nidhogg");

        PlanetAPI athebyne = system.addPlanet("athebyne", star, "Athebyne",
                "barren-bombarded", athebyneAngle, athebyneSize, athebyneDistance, athebyneOrbit);
        athebyne.setFaction(DRACONIS);
        athebyne.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "XLII_athebyne"));
        athebyne.setCustomDescriptionId("planet_athebyne");
        athebyne.applySpecChanges();

        // Shard - Abandoned Terraforming Platform
        SectorEntityToken shardStation = system.addCustomEntity("shard_abandoned_station",
                "Abandoned Terraforming Platform", "station_side06", "neutral");

        shardStation.setCircularOrbitPointingDown(system.getEntityById("athebyne"), shardAngle, shardDistance, shardOrbit);
        shardStation.setCustomDescriptionId("shard_abandoned_station");
        shardStation.setInteractionImage("illustrations", "abandoned_station2");
        Misc.setAbandonedStationMarket("shard_abandoned_station_market", shardStation);

        // Itoron
        PlanetAPI itoron = system.addPlanet("itoron", star, "Itoron",
                "terran", itoronAngle, itoronSize, itoronDistance, itoronOrbit);
        itoron.setFaction(DRACONIS);
        itoron.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "XLII_itoron"));
        itoron.setCustomDescriptionId("planet_itoron");
        itoron.applySpecChanges();

        JumpPointAPI jp1 = Global.getFactory().createJumpPoint("XLII_fafnir_jump_point_in", "Itoron's Jump Point");
        jp1.setCircularOrbit(itoron, jp1Angle, jp1Distance, jp1Orbit);
        jp1.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jp1);

        PlanetAPI kori = system.addPlanet("kori", itoron, "Kori",
                "frozen", koriAngle, koriSize, koriDistance, koriOrbit);
        kori.setFaction(DRACONIS);
        kori.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "XLII_kori"));
        kori.setCustomDescriptionId("planet_kori");
        kori.applySpecChanges();

        PlanetAPI vorium = system.addPlanet("vorium", star, "Vorium",
                "gas_giant", voriumAngle, voriumSize, voriumDistance, voriumOrbit);
        vorium.setFaction(DRACONIS);
        vorium.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "XLII_vorium"));
        vorium.setCustomDescriptionId("planet_vorium");
        vorium.applySpecChanges();

        // Tiamat - Vorium's metallic lava moon
        PlanetAPI tiamat = system.addPlanet("tiamat", vorium, "Tiamat",
                "lava_minor", tiamatAngle, tiamatSize, tiamatDistance, tiamatOrbit);
        tiamat.setCustomDescriptionId("planet_tiamat");

        // Vorium ring systems (Jupiter/Saturn-like)
        system.addRingBand(vorium, "misc", "rings_dust0", 256f, 0, Color.white, 256f, 550f * SCALE, 15f);
        system.addRingBand(vorium, "misc", "rings_ice0", 256f, 1, Color.white, 256f, 600f * SCALE, 15f);
        system.addRingBand(vorium, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 750f * SCALE, 15f);

        JumpPointAPI jp2 = Global.getFactory().createJumpPoint("XLII_fafnir_jump_point_out", "Fringe Jump Point");
        jp2.setCircularOrbit(vorium, jp2Angle, jp2Distance, jp2Orbit);
        jp2.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jp2);

        // Jormungandr - Massive outer ice giant
        PlanetAPI jormungandr = system.addPlanet("jormungandr", star, "Jormungandr",
                "ice_giant", jormungandrAngle, jormungandrSize, jormungandrDistance, jormungandrOrbit);
        jormungandr.setCustomDescriptionId("planet_jormungandr");

        // Hel - Jormungandr's frozen moon with listening post
        PlanetAPI hel = system.addPlanet("hel", jormungandr, "Hel",
                "frozen", helAngle, helSize, helDistance, helOrbit);
        hel.setCustomDescriptionId("planet_hel");

        // Jormungandr ring systems (Uranus/Neptune-like faint rings)
        system.addRingBand(jormungandr, "misc", "rings_ice0", 256f, 0, new Color(100, 150, 200, 100), 256f, 450f * SCALE, 10f);
        system.addRingBand(jormungandr, "misc", "rings_dust0", 256f, 1, new Color(120, 170, 220, 80), 256f, 500f * SCALE, 10f);

        // Ladon - Distant dwarf planet
        PlanetAPI ladon = system.addPlanet("ladon", star, "Ladon",
                "frozen", ladonAngle, ladonSize, ladonDistance, ladonOrbit);
        ladon.setCustomDescriptionId("planet_ladon");

        // Vritra - Elliptical dwarf planet
        PlanetAPI vritra = system.addPlanet("vritra", star, "Vritra",
                "frozen", vritraAngle, vritraSize, vritraDistance, vritraOrbit);
        vritra.setCustomDescriptionId("planet_vritra");

        // Outer asteroid belt (Kuiper belt analog)
        system.addAsteroidBelt(
                star,
                1500,
                21000f, // No SCALE - keeps within system bounds
                600,
                1800,
                3600,
                Terrain.ASTEROID_BELT,
                "Fafnir's Outer Ice Belt"
        );

        system.addRingBand(star, "misc", "rings_ice0", 256f, 0, Color.white, 256f, 21000f, 800f);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 21500f, 800f);
        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 22000f, 800f);

        SectorEntityToken pirateStation = system.addCustomEntity("fafnir_pirate_station", "Ring-Port Station",
                "station_lowtech3", Factions.PIRATES);
        pirateStation.setCircularOrbitPointingDown(star, pirateStationAngle, pirateStationDistance, pirateStationOrbit);
        pirateStation.setCustomDescriptionId("station_ringport");
        pirateStation.setInteractionImage("illustrations", "pirate_station");

        JumpPointAPI jp3 = Global.getFactory().createJumpPoint("XLII_fafnir_jump_point_pirate", "Ring-Port Jump Point");
        jp3.setCircularOrbit(pirateStation, jp3Angle, jp3Distance, jp3Orbit);
        jp3.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jp3);

        system.autogenerateHyperspaceJumpPoints(false, false);

        SectorEntityToken XLII_fafnir_gate = system.addCustomEntity("XLII_fafnir_gate",
                "Fafnir's Lost Gate",
                "XLII_fafnir_gate",
                null);
        XLII_fafnir_gate.setCircularOrbit(star, gateAngle, gateDistance, gateOrbit);

        SectorEntityToken buoy = system.addCustomEntity("XLII_fafnir_buoy",
                "Fafnir Buoy",
                "nav_buoy_makeshift",
                "XLII_draconis");
        buoy.setCircularOrbitPointingDown(star, 60, arrayDistance, arrayOrbit);

        SectorEntityToken relay = system.addCustomEntity("XLII_fafnir_relay",
                "Fafnir Relay",
                "comm_relay_makeshift",
                "XLII_draconis");
        relay.setCircularOrbitPointingDown(star, 180, arrayDistance, arrayOrbit);

        SectorEntityToken array = system.addCustomEntity("XLII_fafnir_array",
                "Fafnir Array",
                "sensor_array_makeshift",
                "XLII_draconis");
        array.setCircularOrbitPointingDown(star, 300, arrayDistance, arrayOrbit);

        // MARKETS

        // Athebyne
        MarketAPI athebyneMarket = Global.getFactory().createMarket("athebyne_market", "Athebyne", 6);
        athebyneMarket.setPrimaryEntity(athebyne);
        athebyneMarket.setFactionId(DRACONIS);
        athebyneMarket.addCondition(Conditions.POPULATION_6);
        athebyneMarket.addCondition(Conditions.ORE_RICH);
        athebyneMarket.addCondition(Conditions.RARE_ORE_ABUNDANT);
        athebyneMarket.addCondition(Conditions.RUINS_SCATTERED);
        athebyneMarket.addCondition(Conditions.IRRADIATED);

        athebyneMarket.addIndustry(Industries.POPULATION);
        athebyneMarket.addIndustry(Industries.SPACEPORT);
        athebyneMarket.addIndustry(Industries.MINING);
        athebyneMarket.addIndustry(Industries.REFINING);
        athebyneMarket.addIndustry(Industries.FUELPROD);
        athebyneMarket.addIndustry(Industries.TECHMINING);
        athebyneMarket.addIndustry(Industries.GROUNDDEFENSES);

        athebyneMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        athebyneMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
        athebyneMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
        athebyneMarket.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        athebyneMarket.setFreePort(false);
        Global.getSector().getEconomy().addMarket(athebyneMarket, true);
        athebyne.setMarket(athebyneMarket);

        // Itoron
        MarketAPI itoronMarket = Global.getFactory().createMarket("itoron_market", "Itoron", 7);
        itoronMarket.setPrimaryEntity(itoron);
        itoronMarket.setFactionId(DRACONIS);
        itoronMarket.addCondition(Conditions.POPULATION_7);
        itoronMarket.addCondition(Conditions.HABITABLE);
        itoronMarket.addCondition(Conditions.MILD_CLIMATE);
        itoronMarket.addCondition(Conditions.FARMLAND_BOUNTIFUL);
        itoronMarket.addCondition(Conditions.ORGANICS_PLENTIFUL);

        itoronMarket.addIndustry(Industries.POPULATION);
        itoronMarket.addIndustry(Industries.MEGAPORT);
        itoronMarket.addIndustry(Industries.FARMING);
        itoronMarket.addIndustry(Industries.LIGHTINDUSTRY);
        itoronMarket.addIndustry(Industries.COMMERCE);
        itoronMarket.addIndustry(Industries.STARFORTRESS_MID);
        itoronMarket.addIndustry(Industries.GROUNDDEFENSES);

        itoronMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        itoronMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
        itoronMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
        itoronMarket.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        itoronMarket.setFreePort(false);
        Global.getSector().getEconomy().addMarket(itoronMarket, true);
        itoron.setMarket(itoronMarket);

        // Kori
        MarketAPI koriMarket = Global.getFactory().createMarket("kori_market", "Kori", 6);
        koriMarket.setPrimaryEntity(kori);
        koriMarket.setFactionId(DRACONIS);
        koriMarket.addCondition(Conditions.POPULATION_6);
        koriMarket.addCondition(Conditions.COLD);
        koriMarket.addCondition(Conditions.RUINS_SCATTERED);
        koriMarket.addCondition(Conditions.VOLATILES_PLENTIFUL);

        koriMarket.addIndustry(Industries.POPULATION);
        koriMarket.addIndustry(Industries.MEGAPORT);
        koriMarket.addIndustry(Industries.MINING);
        koriMarket.addIndustry(Industries.REFINING);
        koriMarket.addIndustry(Industries.ORBITALWORKS, new ArrayList<>(List.of(Items.CORRUPTED_NANOFORGE)));
        koriMarket.addIndustry(Industries.HEAVYBATTERIES);
        koriMarket.addIndustry(Industries.FUELPROD);
        koriMarket.addIndustry("XLII_highcommand", new ArrayList<>(List.of(Commodities.ALPHA_CORE)));

        koriMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        koriMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
        koriMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
        koriMarket.addSubmarket(Submarkets.GENERIC_MILITARY);
        koriMarket.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        koriMarket.setFreePort(false);
        Global.getSector().getEconomy().addMarket(koriMarket, false);
        kori.setMarket(koriMarket);

        // Vorium
        MarketAPI voriumMarket = Global.getFactory().createMarket("vorium_market", "Vorium", 5);
        voriumMarket.setPrimaryEntity(vorium);
        voriumMarket.setFactionId(DRACONIS);
        voriumMarket.addCondition(Conditions.POPULATION_5);
        voriumMarket.addCondition(Conditions.HIGH_GRAVITY);
        voriumMarket.addCondition(Conditions.VOLATILES_PLENTIFUL);

        voriumMarket.addIndustry(Industries.POPULATION);
        voriumMarket.addIndustry(Industries.SPACEPORT);
        voriumMarket.addIndustry(Industries.MINING);
        voriumMarket.addIndustry(Industries.HEAVYINDUSTRY);
        voriumMarket.addIndustry(Industries.HIGHCOMMAND);
        voriumMarket.addIndustry(Industries.PATROLHQ);
        voriumMarket.addIndustry(Industries.GROUNDDEFENSES);

        voriumMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        voriumMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
        voriumMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
        voriumMarket.addSubmarket(Submarkets.GENERIC_MILITARY);
        voriumMarket.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        voriumMarket.setFreePort(false);
        Global.getSector().getEconomy().addMarket(voriumMarket, true);
        vorium.setMarket(voriumMarket);

        // Pirate Station
        MarketAPI pirateStationMarket = Global.getFactory().createMarket("pirateStation_market", "Ring-Port Station", 6);
        pirateStationMarket.setPrimaryEntity(pirateStation);
        pirateStationMarket.setFactionId(Factions.PIRATES);
        pirateStationMarket.addCondition(Conditions.POPULATION_6);
        pirateStationMarket.addCondition(Conditions.ORE_RICH);
        pirateStationMarket.addCondition(Conditions.RARE_ORE_MODERATE);

        pirateStationMarket.addIndustry(Industries.POPULATION);
        pirateStationMarket.addIndustry(Industries.SPACEPORT);
        pirateStationMarket.addIndustry(Industries.MINING);
        pirateStationMarket.addIndustry(Industries.GROUNDDEFENSES);
        pirateStationMarket.addIndustry(Industries.BATTLESTATION);

        pirateStationMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        pirateStationMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
        pirateStationMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
        pirateStationMarket.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        pirateStationMarket.setFreePort(false);
        Global.getSector().getEconomy().addMarket(pirateStationMarket, true);
        pirateStation.setMarket(pirateStationMarket);

        // MAGNETIC FIELDS

        // Itoron magnetosphere (Earth-like)
        SectorEntityToken itoronField = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldTerrainPlugin.MagneticFieldParams(itoronSize + 50f, // middleRadius
                        itoronSize + 110f, // outerRadius
                        itoron, // focus
                        itoronSize, // orbitRadius
                        itoronSize + 160f, // bandWidth
                        new Color(50, 175, 200, 100), // base color
                        0.25f, // aurora frequency
                        new Color[]{
                                new Color(0, 255, 255, 35),
                                new Color(191, 255, 0, 35),
                                new Color(15, 226, 85, 35),
                                new Color(50, 150, 255, 35),
                                new Color(25, 250, 100, 150)
                        }
                ));
        itoronField.setCircularOrbit(itoron, 0, 0, 100);

        // Vorium magnetosphere (Jupiter-like)
        SectorEntityToken voriumField = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldTerrainPlugin.MagneticFieldParams(voriumSize + 50f,
                        voriumSize + 250f,
                        vorium,
                        voriumSize,
                        voriumSize + 300f,
                        new Color(250, 125, 100, 150),
                        0.25f,
                        new Color[]{
                                new Color(25, 250, 100, 150),
                                new Color(0, 155, 205, 75),
                                new Color(91, 155, 0, 85),
                                new Color(15, 126, 75, 35),
                                new Color(100, 75, 155, 65)
                        }
                ));
        voriumField.setCircularOrbit(vorium, 0, 0, 100);

        // Jormungandr magnetosphere (Ice giant - cyan/blue)
        SectorEntityToken jormungandrField = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldTerrainPlugin.MagneticFieldParams(jormungandrSize + 50f,
                        jormungandrSize + 200f,
                        jormungandr,
                        jormungandrSize,
                        jormungandrSize + 250f,
                        new Color(100, 150, 255, 120),
                        0.2f,
                        new Color[]{
                                new Color(100, 200, 255, 35),
                                new Color(150, 220, 255, 35),
                                new Color(80, 170, 255, 35),
                                new Color(120, 190, 255, 50)
                        }
                ));
        jormungandrField.setCircularOrbit(jormungandr, 0, 0, 100);

        // Hyperspace cleanup - clear storms around the system
        HyperspaceTerrainPlugin hyperspaceTerrainPlugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor nebulaEditor = new NebulaEditor(hyperspaceTerrainPlugin);
        float minHyperspaceRadius = hyperspaceTerrainPlugin.getTileSize() * 2f;
        float maxHyperspaceRadius = system.getMaxRadiusInHyperspace();
        // Clear all storms in system area for safe navigation
        nebulaEditor.clearArc(system.getLocation().x, system.getLocation().y, 0,
                minHyperspaceRadius + maxHyperspaceRadius, 0f, 360f, 0f);

        // Create the Rift - an artificial hyperspace anomaly around Fafnir
        createRiftTerrain(sector, system);
    }

    /**
     * Creates the Rift terrain in hyperspace around the Fafnir system.
     */
    private void createRiftTerrain(SectorAPI sector, StarSystemAPI system) {
        LocationAPI hyperspace = sector.getHyperspace();
        Vector2f location = system.getLocation();

        // Create the terrain plugin
        levianeer.draconis.data.campaign.terrain.XLII_RiftTerrainPlugin riftPlugin =
                new levianeer.draconis.data.campaign.terrain.XLII_RiftTerrainPlugin();

        // Add the terrain entity
        SectorEntityToken riftTerrain = hyperspace.addTerrain(
                "XLII_rift",
                riftPlugin
        );

        // Set location and ID
        riftTerrain.setFixedLocation(location.x, location.y);
        riftTerrain.setId("XLII_rift_storm");

        // Initialize the plugin
        riftPlugin.init("XLII_rift", riftTerrain, null);

        // Add visual corona effects
        SectorEntityToken innerGlow = hyperspace.addCustomEntity(
                "XLII_rift_storm_inner_glow",
                null,
                "XLII_rift_inner_corona",
                null
        );
        innerGlow.setFixedLocation(location.x, location.y);

        SectorEntityToken outerHaze = hyperspace.addCustomEntity(
                "XLII_rift_storm_outer_haze",
                null,
                "XLII_rift_outer_corona",
                null
        );
        outerHaze.setFixedLocation(location.x, location.y);
    }
}