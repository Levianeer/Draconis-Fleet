package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

public class fsdf_System implements SectorGeneratorPlugin {

        @Override
        public void generate(SectorAPI sector) { 
                StarSystemAPI system = sector.createStarSystem("Fafnir");
                system.getLocation().set(-750,-5250);
                system.setBackgroundTextureFilename("graphics/mod/backgrounds/fafnirbg.jpg");

                // Angle, Size, Distance & Orbit
                final float asteroidsDistance = 7500f;
                final float asteroidsOrbit = 460f;

                final float athebyneAngle = 200f;
                final float athebyneSize = 180f;
                final float athebyneDistance = 2750f;
                final float athebyneOrbit = 300f;

                final float itoronAngle = 0f;
                final float itoronSize = 220f;
                final float itoronDistance = 5000f;
                final float itoronOrbit = 360f;

                        final float koriAngle = 135f;
                        final float koriSize = 100f;
                        final float koriDistance = 750f;
                        final float koriOrbit = 60f;

                        final float jp1Angle = 315f;
                        final float jp1Distance = 750f;
                        final float jp1Orbit = 60f;

                final float voriumAngle = 360f;
                final float voriumSize = 500f;
                final float voriumDistance = 9000f;
                final float voriumOrbit = 490f;

                        final float jp2Angle = 360f;
                        final float jp2Distance = 850f;
                        final float jp2Orbit = 90f;

                final float pirateStationAngle = 170f;
                final float pirateStationDistance = 8500f;
                final float pirateStationOrbit = 460f;

                final float gateAngle = 180f;
                final float gateDistance = 10000f;
                final float gateOrbit = 560f;

                final float arrayDistance = 2000f;
                final float arrayOrbit = 50f;

                // Star
                PlanetAPI star = system.initStar(
                        "fsdf_fafnir",
                        "star_yellow",	//id, type, radius, x coordinate, y coordiante, corona radius. Star types located in starsector-core\data\config\planets.json
                        800,
                        -750,
                        -5250,
                        600);
    
                // Asteroid belt
                system.addAsteroidBelt(
                        star, //orbit focus
                        1000, //number of asteroid entities
                        asteroidsDistance - 435, //orbit radius is 500 gap for outer randomly generated entity above
                        350, //width of band
                        600, //minimum and maximum visual orbit speeds of asteroids
                        1200,
                        Terrain.ASTEROID_BELT, //ID of the terrain type that appears in the section above the abilities bar
                        "Fafnir's Asteroid Belt" //display name
                );

                // Asteroid belt cont. This mess hurts me emotionally, looks pretty though
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
    
                // Athebyne
                PlanetAPI athebyne = system.addPlanet("athebyne", star, "Athebyne","barren-bombarded", athebyneAngle, athebyneSize, athebyneDistance, athebyneOrbit);  //id, focus, name, type, angle, radius, orbit radius, orbit days
                athebyne.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_athebyne"));
                athebyne.setCustomDescriptionId("planet_athebyne");
                athebyne.applySpecChanges();
    
                // Itoron
                PlanetAPI itoron = system.addPlanet("itoron", star, "Itoron", "terran", itoronAngle, itoronSize, itoronDistance, itoronOrbit);	//id, focus, name, type, angle, radius, orbit radius, orbit days
                itoron.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_itoron"));
                itoron.setCustomDescriptionId("planet_itoron");
                itoron.applySpecChanges();

                        // Itoron's Jump Point
                        JumpPointAPI fsdf_fafnir_jp_1 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_in", "Itoron's Jump Point");
                        fsdf_fafnir_jp_1.setCircularOrbit(system.getEntityById("itoron"), jp1Angle, jp1Distance, jp1Orbit);   //focus, angle, orbit radius, orbit days
                        fsdf_fafnir_jp_1.setStandardWormholeToHyperspaceVisual();
                        system.addEntity(fsdf_fafnir_jp_1);
                                
                        // Kori
                        PlanetAPI kori = system.addPlanet("kori", itoron, "Kori","frozen", koriAngle, koriSize, koriDistance, koriOrbit);  //id, focus, name, type, angle, radius, orbit radius, orbit days
                        kori.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_kori"));
                        kori.setCustomDescriptionId("planet_kori");
                        kori.applySpecChanges();

                // Vorium
                PlanetAPI vorium = system.addPlanet("vorium", star, "Vorium","gas_giant", voriumAngle, voriumSize, voriumDistance, voriumOrbit);  //id, focus, name, type, angle, radius, orbit radius, orbit days
                vorium.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_vorium"));
                vorium.setCustomDescriptionId("planet_vorium");
                vorium.applySpecChanges();

                        // Vorium's Jump Point
                        JumpPointAPI fsdf_fafnir_jp_2 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_out", "Fringe Jump Point");
                        fsdf_fafnir_jp_2.setCircularOrbit(system.getEntityById("vorium"), jp2Angle, jp2Distance, jp2Orbit);   //focus, angle, orbit radius, orbit days
                        fsdf_fafnir_jp_2.setStandardWormholeToHyperspaceVisual();
                        system.addEntity(fsdf_fafnir_jp_2);

                // Pirate Station
                SectorEntityToken pirateStation = system.addCustomEntity("fafnir_pirate_station","Ring-Port Station", "station_lowtech1", "pirates");
                pirateStation.setCircularOrbitPointingDown(system.getEntityById("fsdf_fafnir"), pirateStationAngle, pirateStationDistance, pirateStationOrbit);   //focus, angle, orbit radius, orbit days
                pirateStation.setCustomDescriptionId("station_ringport");
                pirateStation.setInteractionImage("illustrations", "pirate_station");
    
                // Hyperspace Jump Points
                system.autogenerateHyperspaceJumpPoints(false,false); //gas giant, fringe / generates star gravity well
                
                // Gate
                SectorEntityToken fsdf_fafnir_gate = system.addCustomEntity("fsdf_fafnir_gate",
                                 "Fafnir Gate",
                                 "inactive_gate",
                                 null);
                fsdf_fafnir_gate.setCircularOrbit(star, gateAngle, gateDistance, gateOrbit); //focus, angle, orbit radius, orbit days
    
                // Buoy
                SectorEntityToken buoy = system.addCustomEntity("fsdf_fafnir_buoy",
                                 "Fafnir Buoy",
                                 "nav_buoy",
                                 "fsdf_draconis");
                buoy.setCircularOrbitPointingDown(star, 60, arrayDistance, arrayOrbit); //focus, angle, orbit radius, orbit days
    
                // Relay
                SectorEntityToken relay = system.addCustomEntity("fsdf_fafnir_relay",
                                 "Fafnir Relay",
                                 "comm_relay",
                                 "fsdf_draconis");
                relay.setCircularOrbitPointingDown(star, 180, arrayDistance, arrayOrbit); //focus, angle, orbit radius, orbit days
    
                // Array
                SectorEntityToken array = system.addCustomEntity("fsdf_fafnir_array",
                                 "Fafnir Array",
                                 "sensor_array",
                                 "fsdf_draconis");
                array.setCircularOrbitPointingDown(star, 300, arrayDistance, arrayOrbit); //focus, angle, orbit radius, orbit days
    
                // Sets up hyperspace editor plugin
                HyperspaceTerrainPlugin hyperspaceTerrainPlugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin(); // get instance of hyperspace terrain
                NebulaEditor nebulaEditor = new NebulaEditor(hyperspaceTerrainPlugin); // object used to make changes to hyperspace nebula
    
                // Sets up radiuses in hyperspace of system
                float minHyperspaceRadius = hyperspaceTerrainPlugin.getTileSize() * 2f; //minimum radius is two 'tiles'
                float maxHyperspaceRadius = system.getMaxRadiusInHyperspace();
    
                // Hyperstorm-b-gone (around system in hyperspace)
                nebulaEditor.clearArc(system.getLocation().x, system.getLocation().y, 0, minHyperspaceRadius + maxHyperspaceRadius, 0f, 360f, 0.25f);
    
        }
}