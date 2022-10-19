package data.scripts;

import java.awt.Color;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain.RingParams;
import com.fs.starfarer.api.util.Misc;

public class fsdf_ModPlugin extends BaseModPlugin {
    @Override
    public void onNewGame() {

    	boolean presetConditions = true;

		SectorAPI sector = Global.getSector();
		StarSystemAPI system = sector.createStarSystem("Fafnir");
		
		//Star
		PlanetAPI star = system.initStar(
			"fsdf_fafnir",
			"star_yellow",	//id, type, radius, x coordinate, y coordiante, corona radius. Star types located in starsector-core\data\config\planets.json
			800,
			-750,
			-5250,
			600);

		//	--------------------------------------	Break	--------------------------------------	//

		// Asteroids
		system.addAsteroidBelt(star, 100, 7300, 256, 150, 250, Terrain.ASTEROID_BELT, null);
		system.addAsteroidBelt(star, 100, 7700, 256, 150, 250, Terrain.ASTEROID_BELT, null);
		
		system.addAsteroidBelt(star, 100, 8150, 128, 200, 300, Terrain.ASTEROID_BELT, null);
		system.addAsteroidBelt(star, 100, 8450, 188, 200, 300, Terrain.ASTEROID_BELT, null);
		system.addAsteroidBelt(star, 100, 8675, 256, 200, 300, Terrain.ASTEROID_BELT, null);
			
		system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, 7200, 80f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 7400, 100f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, 7600, 130f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 7800, 80f);
		
		// add one ring that covers all of the above
		SectorEntityToken ring = system.addTerrain(Terrain.RING, new RingParams(600 + 256, 7500, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);
		
		
		system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, 8000, 80f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 8100, 120f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, 8200, 160f);
		
		// add one ring that covers all of the above
		ring = system.addTerrain(Terrain.RING, new RingParams(200 + 256, 8100, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);
		
		system.addRingBand(star, "misc", "rings_dust0", 256f, 3, Color.white, 256f, 8300, 140f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, 8400, 180f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 8500, 220f);
		
		// add one ring that covers all of the above
		ring = system.addTerrain(Terrain.RING, new RingParams(200 + 256, 8400, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);
		
		
		system.addRingBand(star, "misc", "rings_ice0", 256f, 0, Color.white, 256f, 8500, 100f);
		system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, 8600, 140f);
		system.addRingBand(star, "misc", "rings_ice0", 256f, 1, Color.white, 256f, 8700, 160f);
		system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, 8800, 180f);
		
		// add one ring that covers all of the above
		ring = system.addTerrain(Terrain.RING, new RingParams(300 + 256, 4650, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);

		//	--------------------------------------	Break	--------------------------------------	//

		system.setBackgroundTextureFilename("graphics/mod/backgrounds/modbg.jpg");

		// Drytron
		PlanetAPI drytron = system.addPlanet("drytron", star, "Drytron", "terran", 0, 300, 5000, 500);	//id, focus, name, type, angle, radius, orbit radius, orbit days
		drytron.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_drytron"));
		drytron.setCustomDescriptionId("planet_drytron");
		drytron.applySpecChanges();

		// Close Fafnir System Jump Point
		JumpPointAPI fsdf_fafnir_jp_1 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_in", "Kori's Jump Point");
		fsdf_fafnir_jp_1.setCircularOrbit(system.getEntityById("drytron"), 60, 1600, 90);
		fsdf_fafnir_jp_1.setStandardWormholeToHyperspaceVisual();
		system.addEntity(fsdf_fafnir_jp_1);

		// Far Fafnir System Jump Point
		JumpPointAPI fsdf_fafnir_jp_2 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_out", "Fringe Jump Point");
		fsdf_fafnir_jp_2.setCircularOrbit(system.getEntityById("fsdf_fafnir"), 120, 9500, 300);
		fsdf_fafnir_jp_2.setStandardWormholeToHyperspaceVisual();
		system.addEntity(fsdf_fafnir_jp_2);
		
		// Athebyne
		PlanetAPI athebyne = system.addPlanet("athebyne", star, "Athebyne","barren_venuslike", 200, 300, 2500, 30);  //id, focus, name, type, angle, radius, orbit radius, orbit days
		athebyne.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_athebyne"));
		athebyne.setCustomDescriptionId("planet_athebyne");
		athebyne.applySpecChanges();
		
		// Kori
		PlanetAPI kori = system.addPlanet("kori", fsdf_fafnir_jp_1, "Kori","frozen", 270, 150, 350, 60);  //id, focus, name, type, angle, radius, orbit radius, orbit days
		kori.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_kori"));
		kori.setCustomDescriptionId("planet_kori");
		kori.applySpecChanges();

		// Pirate Station
		SectorEntityToken pirateStation = system.addCustomEntity("fafnir_pirate_station","Outer Reach Extraction Depot", "station_lowtech1", "pirates");
		pirateStation.setCircularOrbitPointingDown(system.getEntityById("fsdf_fafnir"), 160, 8500, 240);
		pirateStation.setCustomDescriptionId("station_pirate_base");
		pirateStation.setInteractionImage("illustrations", "pirate_station");

		system.autogenerateHyperspaceJumpPoints(false,false); //gas giant = false, fringe = false / generates star gravity well
		
		//Gate
		SectorEntityToken fsdf_fafnir_gate = system.addCustomEntity("fsdf_fafnir_gate",
				 "Fafnir Gate",
				 "inactive_gate",
				 null);
		fsdf_fafnir_gate.setCircularOrbit(drytron, 180, 1600, 90); //focus, angle, orbit radius, orbit days

		//Buoy
		SectorEntityToken buoy = system.addCustomEntity("fsdf_fafnir_buoy",
				 "Fafnir Buoy",
				 "nav_buoy",
				 "fsdf_draconis");
		buoy.setCircularOrbitPointingDown(fsdf_fafnir_gate, 60, 200, 45); //focus, angle, orbit radius, orbit days

		//Relay
		SectorEntityToken relay = system.addCustomEntity("fsdf_fafnir_relay",
				 "Fafnir Relay",
				 "comm_relay",
				 "fsdf_draconis");
		relay.setCircularOrbitPointingDown(fsdf_fafnir_gate, 180, 200, 45); //focus, angle, orbit radius, orbit days

		//Array
		SectorEntityToken array = system.addCustomEntity("fsdf_fafnir_array",
				 "Fafnir Array",
				 "sensor_array",
				 "fsdf_draconis");
		array.setCircularOrbitPointingDown(fsdf_fafnir_gate, 300, 200, 45); //focus, angle, orbit radius, orbit days

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