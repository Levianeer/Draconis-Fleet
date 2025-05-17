package levianeer.draconis.data.scripts.world.systems;

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
                system.setBackgroundTextureFilename("graphics/mod/backgrounds/fafnirbg.png");

                // Angle, Size, Distance & Orbit
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

                        final float jp1Angle = 315f;
                        final float jp1Distance = 750f;
                        final float jp1Orbit = 60f;

                final float voriumAngle = 30f;
                final float voriumSize = 500f;
                final float voriumDistance = 8800f;
                final float voriumOrbit = 800f;

                        final float jp2Angle = 360f;
                        final float jp2Distance = 850f;
                        final float jp2Orbit = 90f;

                final float pirateStationAngle = 170f;
                final float pirateStationDistance = 8500f;
                final float pirateStationOrbit = 460f;

                final float gateAngle = 180f;
                final float gateDistance = 10000f;
                final float gateOrbit = 560f;

                final float arrayDistance = 4100f;
                final float arrayOrbit = 400f;

                // Fafnir Star
                PlanetAPI star = system.initStar(
                        "fsdf_fafnir",
                        "star_yellow",	//ID, type, radius, x coordinate, y coordiante & corona radius
                        1000,
                        -750,
                        -5250,
                        500);
    
                // Asteroid belt
                system.addAsteroidBelt(
                        star, // Focus
                        1000, // Number of entities
                        asteroidsDistance - 435, // Orbit radius
                        350, // Width
                        600, // Minimum and maximum orbit speed
                        1200,
                        Terrain.ASTEROID_BELT,
                        "Fafnir's Belt"
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
                PlanetAPI athebyne = system.addPlanet("athebyne", star, "Athebyne","barren-bombarded", athebyneAngle, athebyneSize, athebyneDistance, athebyneOrbit);  // ID, focus, name, type, angle, radius, orbit radius & orbit days
                athebyne.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_athebyne"));
                athebyne.setCustomDescriptionId("planet_athebyne");
                athebyne.applySpecChanges();
    
                // Itoron
                PlanetAPI itoron = system.addPlanet("itoron", star, "Itoron", "terran", itoronAngle, itoronSize, itoronDistance, itoronOrbit);	// ID, focus, name, type, angle, radius, orbit radius & orbit days
                itoron.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_itoron"));
                itoron.setCustomDescriptionId("planet_itoron");
                itoron.applySpecChanges();

                        // Itoron's Jump Point
                        JumpPointAPI fsdf_fafnir_jp_1 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_in", "Itoron's Jump Point");
                        fsdf_fafnir_jp_1.setCircularOrbit(system.getEntityById("itoron"), jp1Angle, jp1Distance, jp1Orbit);   // Focus, angle, orbit radius & orbit days
                        fsdf_fafnir_jp_1.setStandardWormholeToHyperspaceVisual();
                        system.addEntity(fsdf_fafnir_jp_1);
                                
                        // Kori
                        PlanetAPI kori = system.addPlanet("kori", itoron, "Kori","frozen", koriAngle, koriSize, koriDistance, koriOrbit);  // ID, focus, name, type, angle, radius, orbit radius & orbit days
                        kori.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_kori"));
                        kori.setCustomDescriptionId("planet_kori");
                        kori.applySpecChanges();

                // Vorium
                PlanetAPI vorium = system.addPlanet("vorium", star, "Vorium","gas_giant", voriumAngle, voriumSize, voriumDistance, voriumOrbit);  // ID, focus, name, type, angle, radius, orbit radius & orbit days
                vorium.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_vorium"));
                vorium.setCustomDescriptionId("planet_vorium");
                vorium.applySpecChanges();

                        // Vorium's Jump Point
                        JumpPointAPI fsdf_fafnir_jp_2 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_out", "Fringe Jump Point");
                        fsdf_fafnir_jp_2.setCircularOrbit(system.getEntityById("vorium"), jp2Angle, jp2Distance, jp2Orbit);   // Focus, angle, orbit radius & orbit days
                        fsdf_fafnir_jp_2.setStandardWormholeToHyperspaceVisual();
                        system.addEntity(fsdf_fafnir_jp_2);

                // Pirate Station
                SectorEntityToken pirateStation = system.addCustomEntity("fafnir_pirate_station","Ring-Port Station", "station_lowtech1", "pirates");
                pirateStation.setCircularOrbitPointingDown(system.getEntityById("fsdf_fafnir"), pirateStationAngle, pirateStationDistance, pirateStationOrbit);   // Focus, angle, orbit radius & orbit days
                pirateStation.setCustomDescriptionId("station_ringport");
                pirateStation.setInteractionImage("illustrations", "pirate_station");
    
                // Hyperspace Jump Points
                system.autogenerateHyperspaceJumpPoints(false,false);
                
                // Gate
                SectorEntityToken fsdf_fafnir_gate = system.addCustomEntity("fsdf_fafnir_gate",
                                 "Fafnir Gate",
                                 "inactive_gate",
                                 null);
                fsdf_fafnir_gate.setCircularOrbit(star, gateAngle, gateDistance, gateOrbit); // Focus, angle, orbit radius & orbit days
    
                // Buoy
                SectorEntityToken buoy = system.addCustomEntity("fsdf_fafnir_buoy",
                                 "Fafnir Buoy",
                                 "nav_buoy_makeshift",
                                 "fsdf_draconis");
                buoy.setCircularOrbitPointingDown(star, 60, arrayDistance, arrayOrbit); // Focus, angle, orbit radius & orbit days
    
                // Relay
                SectorEntityToken relay = system.addCustomEntity("fsdf_fafnir_relay",
                                 "Fafnir Relay",
                                 "comm_relay_makeshift",
                                 "fsdf_draconis");
                relay.setCircularOrbitPointingDown(star, 180, arrayDistance, arrayOrbit); // Focus, angle, orbit radius & orbit days
    
                // Array
                SectorEntityToken array = system.addCustomEntity("fsdf_fafnir_array",
                                 "Fafnir Array",
                                 "sensor_array_makeshift",
                                 "fsdf_draconis");
                array.setCircularOrbitPointingDown(star, 300, arrayDistance, arrayOrbit); // Focus, angle, orbit radius & orbit days
    
                // Sets up hyperspace editor plugin
                HyperspaceTerrainPlugin hyperspaceTerrainPlugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
                NebulaEditor nebulaEditor = new NebulaEditor(hyperspaceTerrainPlugin);
    
                // Sets up radiuses in hyperspace of system
                float minHyperspaceRadius = hyperspaceTerrainPlugin.getTileSize() * 2f;
                float maxHyperspaceRadius = system.getMaxRadiusInHyperspace();
    
                // Hyperstorm-b-gone (around system in hyperspace)
                nebulaEditor.clearArc(system.getLocation().x, system.getLocation().y, 0, minHyperspaceRadius + maxHyperspaceRadius, 0f, 360f, 0.25f);
        }
}