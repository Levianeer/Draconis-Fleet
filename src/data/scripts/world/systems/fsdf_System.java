package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

public class fsdf_System implements SectorGeneratorPlugin { //A SectorGeneratorPlugin is a class from the game, that identifies this as a script that will have a 'generate' method
        @Override
        public void generate(SectorAPI sector) { //the parameter sector is passed. This is the instance of the campaign map that this script will add a star system to
                //initialise system
                StarSystemAPI system = sector.createStarSystem("Fafnir"); //create a new variable called system. this is assigned an instance of the new star system added to the Sector at the same time
                system.getLocation().set(-750,-5250); //sets location of system in hyperspace. map size is in the order of 100000x100000, and 0, 0 is the center of the map, this will set the location to the east and slightly south of the center
                system.setBackgroundTextureFilename("graphics/mod/backgrounds/fafnirbg.jpg"); //sets the background image for when in the system. this is a filepath to an image in the core game files

                //Distances
                final float asteroidsDist = 7500f;
                final float athebyneDist = 2750f;
                final float doraitoronDist = 5000f;
                final float koriDist = 750f;
                final float jp1Dist = 750f;
                final float jp2Dist = 8500f;
                final float gateDist = 10000f;
                final float arrayDist = 2000f;

                //Orbit days
                final float asteroidsOrb = 460f;
                final float athebyneOrb = 300f;
                final float doraitoronOrb = 360f;
                final float koriOrb = 60f;
                final float gateOrb = 560f;
                final float arrayOrb = 50f;

                //Star
                PlanetAPI star = system.initStar(
                        "fsdf_fafnir",
                        "star_yellow",	//id, type, radius, x coordinate, y coordiante, corona radius. Star types located in starsector-core\data\config\planets.json
                        800,
                        -750,
                        -5250,
                        600);
    
                //Asteroid belt. asteroids are separate entities inside these, it will randomly distribute a defined number of them around the ring
                system.addAsteroidBelt(
                        star, //orbit focus
                        1000, //number of asteroid entities
                        asteroidsDist - 435, //orbit radius is 500 gap for outer randomly generated entity above
                        350, //width of band
                        600, //minimum and maximum visual orbit speeds of asteroids
                        1200,
                        Terrain.ASTEROID_BELT, //ID of the terrain type that appears in the section above the abilities bar
                        "Fafnir's Asteroid Belt" //display name
                );

                //This mess hurts me emotionally
                system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, asteroidsDist - 1300, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDist - 1100, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, asteroidsDist - 900, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDist - 700, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, asteroidsDist + 500, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDist + 400, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, asteroidsDist + 300, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_ice0", 256f, 0, Color.white, 256f, asteroidsDist, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, asteroidsDist + 100, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_ice0", 256f, 1, Color.white, 256f, asteroidsDist + 200, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, asteroidsDist + 300, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 3, Color.white, 256f, asteroidsDist - 200, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, asteroidsDist - 100, asteroidsOrb);
                system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, asteroidsDist, asteroidsOrb);

    
                // Athebyne
                PlanetAPI athebyne = system.addPlanet("athebyne", star, "Athebyne","barren_venuslike", 200, 200, athebyneDist, athebyneOrb);  //id, focus, name, type, angle, radius, orbit radius, orbit days
                athebyne.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_athebyne"));
                athebyne.setCustomDescriptionId("planet_athebyne");
                athebyne.applySpecChanges();
    
                // Doraitoron
                PlanetAPI doraitoron = system.addPlanet("doraitoron", star, "Doraitoron", "terran", 0, 200, doraitoronDist, doraitoronOrb);	//id, focus, name, type, angle, radius, orbit radius, orbit days
                doraitoron.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_doraitoron"));
                doraitoron.setCustomDescriptionId("planet_doraitoron");
                doraitoron.applySpecChanges();
    
                // Kori
                PlanetAPI kori = system.addPlanet("kori", doraitoron, "Kori","frozen", 135, 100, koriDist, koriOrb);  //id, focus, name, type, angle, radius, orbit radius, orbit days
                kori.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_kori"));
                kori.setCustomDescriptionId("planet_kori");
                kori.applySpecChanges();

                // Doraitoron's System Jump Point
                JumpPointAPI fsdf_fafnir_jp_1 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_in", "Doraitoron's Jump Point");
                fsdf_fafnir_jp_1.setCircularOrbit(system.getEntityById("doraitoron"), 315, jp1Dist, koriOrb);   //focus, angle, orbit radius, orbit days
                fsdf_fafnir_jp_1.setStandardWormholeToHyperspaceVisual();
                system.addEntity(fsdf_fafnir_jp_1);
    
                // Fafnir's System Jump Point
                JumpPointAPI fsdf_fafnir_jp_2 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_out", "Fringe Jump Point");
                fsdf_fafnir_jp_2.setCircularOrbit(system.getEntityById("fsdf_fafnir"), 360, jp2Dist, asteroidsOrb);   //focus, angle, orbit radius, orbit days
                fsdf_fafnir_jp_2.setStandardWormholeToHyperspaceVisual();
                system.addEntity(fsdf_fafnir_jp_2);

                // Pirate Station
                SectorEntityToken pirateStation = system.addCustomEntity("fafnir_pirate_station","Ring-Port Station", "station_lowtech1", "pirates");
                pirateStation.setCircularOrbitPointingDown(system.getEntityById("fsdf_fafnir"), 180, jp2Dist, asteroidsOrb);   //focus, angle, orbit radius, orbit days
                pirateStation.setCustomDescriptionId("station_ringport");
                pirateStation.setInteractionImage("illustrations", "pirate_station");
    
                system.autogenerateHyperspaceJumpPoints(false,false); //gas giant = false, fringe = false / generates star gravity well
                
                //Gate
                SectorEntityToken fsdf_fafnir_gate = system.addCustomEntity("fsdf_fafnir_gate",
                                 "Fafnir Gate",
                                 "inactive_gate",
                                 null);
                fsdf_fafnir_gate.setCircularOrbit(star, 180, gateDist, gateOrb); //focus, angle, orbit radius, orbit days
    
                //Buoy
                SectorEntityToken buoy = system.addCustomEntity("fsdf_fafnir_buoy",
                                 "Fafnir Buoy",
                                 "nav_buoy",
                                 "fsdf_draconis");
                buoy.setCircularOrbitPointingDown(star, 60, arrayDist, arrayOrb); //focus, angle, orbit radius, orbit days
    
                //Relay
                SectorEntityToken relay = system.addCustomEntity("fsdf_fafnir_relay",
                                 "Fafnir Relay",
                                 "comm_relay",
                                 "fsdf_draconis");
                relay.setCircularOrbitPointingDown(star, 180, arrayDist, arrayOrb); //focus, angle, orbit radius, orbit days
    
                //Array
                SectorEntityToken array = system.addCustomEntity("fsdf_fafnir_array",
                                 "Fafnir Array",
                                 "sensor_array",
                                 "fsdf_draconis");
                array.setCircularOrbitPointingDown(star, 300, arrayDist, arrayOrb); //focus, angle, orbit radius, orbit days
    
                //Sets up hyperspace editor plugin
                HyperspaceTerrainPlugin hyperspaceTerrainPlugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin(); //get instance of hyperspace terrain
                NebulaEditor nebulaEditor = new NebulaEditor(hyperspaceTerrainPlugin); //object used to make changes to hyperspace nebula
    
                //Sets up radiuses in hyperspace of system
                float minHyperspaceRadius = hyperspaceTerrainPlugin.getTileSize() * 2f; //minimum radius is two 'tiles'
                float maxHyperspaceRadius = system.getMaxRadiusInHyperspace();
    
                //Hyperstorm-b-gone (around system in hyperspace)
                nebulaEditor.clearArc(system.getLocation().x, system.getLocation().y, 0, minHyperspaceRadius + maxHyperspaceRadius, 0f, 360f, 0.25f);
    
        }
    }
