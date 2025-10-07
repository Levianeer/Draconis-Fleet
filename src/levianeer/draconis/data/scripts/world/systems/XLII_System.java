package levianeer.draconis.data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

public class XLII_System implements SectorGeneratorPlugin {

        @Override
        public void generate(SectorAPI sector) {
                StarSystemAPI system = sector.createStarSystem("Fafnir");
                system.getLocation().set(-750, -5250);
                system.setBackgroundTextureFilename("graphics/mod/backgrounds/fafnirbg.png");

                final float asteroidsDistance = 7500f;
                final float asteroidsOrbit = 460f;

                final float athebyneAngle = 0f;
                final float athebyneSize = 140f;
                final float athebyneDistance = 2200f;
                final float athebyneOrbit = 225f;

                final float itoronAngle = 0f;
                final float itoronSize = 180f;
                final float itoronDistance = 3400f;
                final float itoronOrbit = 365f;

                final float koriAngle = 0f;
                final float koriSize = 40f;
                final float koriDistance = 350f;
                final float koriOrbit = 30f;

                final float voriumAngle = 30f;
                final float voriumSize = 500f;
                final float voriumDistance = 8800f;
                final float voriumOrbit = 800f;

                final float pirateStationAngle = 170f;
                final float pirateStationDistance = 12000f;
                final float pirateStationOrbit = 640f;

                final float jp1Angle = 315f;
                final float jp1Distance = 750f;
                final float jp1Orbit = 60f;

                final float jp2Angle = 360f;
                final float jp2Distance = 850f;
                final float jp2Orbit = 90f;

                final float jp3Angle = 200f;
                final float jp3Distance = 1000f;
                final float jp3Orbit = 75f;

                final float gateAngle = 180f;
                final float gateDistance = 10000f;
                final float gateOrbit = 560f;

                final float arrayDistance = 4100f;
                final float arrayOrbit = 400f;

                PlanetAPI star = system.initStar(
                        "XLII_fafnir",
                        "star_yellow",
                        1000,
                        -750,
                        -5250,
                        500);

                system.addAsteroidBelt(
                        star,
                        1000,
                        asteroidsDistance - 435,
                        350,
                        320,
                        1280,
                        Terrain.ASTEROID_BELT,
                        "Fafnir's Belt"
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

                // ASTEROID BELT (I hate this mess)
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

                // PLANETS

                PlanetAPI athebyne = system.addPlanet("athebyne", star, "Athebyne",
                        "barren-bombarded", athebyneAngle, athebyneSize, athebyneDistance, athebyneOrbit);
                athebyne.setFaction(DRACONIS);
                athebyne.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "XLII_athebyne"));
                athebyne.setCustomDescriptionId("planet_athebyne");
                athebyne.applySpecChanges();

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

                JumpPointAPI jp2 = Global.getFactory().createJumpPoint("XLII_fafnir_jump_point_out", "Fringe Jump Point");
                jp2.setCircularOrbit(vorium, jp2Angle, jp2Distance, jp2Orbit);
                jp2.setStandardWormholeToHyperspaceVisual();
                system.addEntity(jp2);

                SectorEntityToken pirateStation = system.addCustomEntity("fafnir_pirate_station", "Ring-Port Station",
                        "station_lowtech3", "pirates");
                pirateStation.setCircularOrbitPointingDown(star, pirateStationAngle, pirateStationDistance, pirateStationOrbit);
                pirateStation.setCustomDescriptionId("station_ringport");
                pirateStation.setInteractionImage("illustrations", "pirate_station");

                JumpPointAPI jp3 = Global.getFactory().createJumpPoint("XLII_fafnir_jump_point_pirate", "Ring-Port Jump Point");
                jp3.setCircularOrbit(pirateStation, jp3Angle, jp3Distance, jp3Orbit);
                jp3.setStandardWormholeToHyperspaceVisual();
                system.addEntity(jp3);

                system.autogenerateHyperspaceJumpPoints(false, false);

                SectorEntityToken XLII_fafnir_gate = system.addCustomEntity("XLII_fafnir_gate",
                        "Fafnir Gate",
                        "inactive_gate",
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
                athebyneMarket.addCondition(Conditions.RUINS_EXTENSIVE);
                athebyneMarket.addCondition(Conditions.NO_ATMOSPHERE);

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

                // Hyperspace cleanup
                HyperspaceTerrainPlugin hyperspaceTerrainPlugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
                NebulaEditor nebulaEditor = new NebulaEditor(hyperspaceTerrainPlugin);
                float minHyperspaceRadius = hyperspaceTerrainPlugin.getTileSize() * 2f;
                float maxHyperspaceRadius = system.getMaxRadiusInHyperspace();
                nebulaEditor.clearArc(system.getLocation().x, system.getLocation().y, 0, minHyperspaceRadius + maxHyperspaceRadius, 0f, 360f, 0.25f);
        }
}