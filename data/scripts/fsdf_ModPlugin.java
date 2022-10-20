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
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain.RingParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
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
		system.addAsteroidBelt(star, 100, 6300, 256, 150, 250, Terrain.ASTEROID_BELT, null);
		system.addAsteroidBelt(star, 100, 6700, 256, 150, 250, Terrain.ASTEROID_BELT, null);
		
		system.addAsteroidBelt(star, 100, 7150, 128, 200, 300, Terrain.ASTEROID_BELT, null);
		system.addAsteroidBelt(star, 100, 7450, 188, 200, 300, Terrain.ASTEROID_BELT, null);
		system.addAsteroidBelt(star, 100, 7675, 256, 200, 300, Terrain.ASTEROID_BELT, null);
			
		system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, 6200, 80f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 6400, 100f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, 6600, 130f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 6800, 80f);
		
		// add one ring that covers all of the above
		SectorEntityToken ring = system.addTerrain(Terrain.RING, new RingParams(600 + 256, 6500, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);
		
		
		system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, 7000, 80f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 7100, 120f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, 7200, 160f);
		
		// add one ring that covers all of the above
		ring = system.addTerrain(Terrain.RING, new RingParams(200 + 256, 7100, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);
		
		system.addRingBand(star, "misc", "rings_dust0", 256f, 3, Color.white, 256f, 7300, 140f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.white, 256f, 7400, 180f);
		system.addRingBand(star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 7500, 220f);
		
		// add one ring that covers all of the above
		ring = system.addTerrain(Terrain.RING, new RingParams(200 + 256, 7400, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);
		
		
		system.addRingBand(star, "misc", "rings_ice0", 256f, 0, Color.white, 256f, 7500, 100f);
		system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, 7600, 140f);
		system.addRingBand(star, "misc", "rings_ice0", 256f, 1, Color.white, 256f, 7700, 160f);
		system.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color.white, 256f, 7800, 180f);
		
		// add one ring that covers all of the above
		ring = system.addTerrain(Terrain.RING, new RingParams(300 + 256, 8650, null, "Fafnir's Rings"));
		ring.setCircularOrbit(star, 0, 0, 100);

		//	--------------------------------------	Break	--------------------------------------	//

		system.setBackgroundTextureFilename("graphics/mod/backgrounds/modbg.jpg");

		// Athebyne
		PlanetAPI athebyne = system.addPlanet("athebyne", star, "Athebyne","barren_venuslike", 200, 300, 2500, 300);  //id, focus, name, type, angle, radius, orbit radius, orbit days
		athebyne.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_athebyne"));
		athebyne.setCustomDescriptionId("planet_athebyne");
		athebyne.applySpecChanges();

		// Drytron
		PlanetAPI drytron = system.addPlanet("drytron", star, "Drytron", "terran", 0, 300, 5000, 360);	//id, focus, name, type, angle, radius, orbit radius, orbit days
		drytron.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_drytron"));
		drytron.setCustomDescriptionId("planet_drytron");
		drytron.applySpecChanges();

		// Drytron's System Jump Point
		JumpPointAPI fsdf_fafnir_jp_1 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_in", "Drytron's Jump Point");
		fsdf_fafnir_jp_1.setCircularOrbit(system.getEntityById("drytron"), 315, 800, 30);   //focus, angle, orbit radius, orbit days
		fsdf_fafnir_jp_1.setStandardWormholeToHyperspaceVisual();
		system.addEntity(fsdf_fafnir_jp_1);

		// Kori
		PlanetAPI kori = system.addPlanet("kori", drytron, "Kori","frozen", 135, 150, 800, 30);  //id, focus, name, type, angle, radius, orbit radius, orbit days
		kori.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_kori"));
		kori.setCustomDescriptionId("planet_kori");
		kori.applySpecChanges();

		// Pirate Station
		SectorEntityToken pirateStation = system.addCustomEntity("fafnir_pirate_station","Ringport Station", "station_lowtech1", "pirates");
		pirateStation.setCircularOrbitPointingDown(system.getEntityById("fsdf_fafnir"), 120, 6900, 220);   //focus, angle, orbit radius, orbit days
		pirateStation.setCustomDescriptionId("station_ringport");
		pirateStation.setInteractionImage("illustrations", "pirate_station");

		// Fafnir's System Jump Point
		JumpPointAPI fsdf_fafnir_jp_2 = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point_out", "Fringe Jump Point");
		fsdf_fafnir_jp_2.setCircularOrbit(system.getEntityById("fsdf_fafnir"), 300, 8500, 540);   //focus, angle, orbit radius, orbit days
		fsdf_fafnir_jp_2.setStandardWormholeToHyperspaceVisual();
		system.addEntity(fsdf_fafnir_jp_2);

		system.autogenerateHyperspaceJumpPoints(false,false); //gas giant = false, fringe = false / generates star gravity well
		
		//Gate
		SectorEntityToken fsdf_fafnir_gate = system.addCustomEntity("fsdf_fafnir_gate",
				 "Fafnir Gate",
				 "inactive_gate",
				 null);
		fsdf_fafnir_gate.setCircularOrbit(star, 180, 10000, 560); //focus, angle, orbit radius, orbit days

		DebrisFieldParams params = new DebrisFieldParams(
				500f,
				-1f,
				10000000f,
				0f);
		params.source = DebrisFieldSource.MIXED;
		params.baseSalvageXP = 250;
		SectorEntityToken debrisNextToGate = Misc.addDebrisField(system, params, StarSystemGenerator.random);
		debrisNextToGate.setSensorProfile(null);
		debrisNextToGate.setDiscoverable(null);
		debrisNextToGate.setCircularOrbit(fsdf_fafnir_gate, 0f, 0f, 100f);
		debrisNextToGate.setId("fafnir_gate_debris");

		//Buoy
		SectorEntityToken buoy = system.addCustomEntity("fsdf_fafnir_buoy",
				 "Fafnir Buoy",
				 "nav_buoy",
				 "fsdf_draconis");
		buoy.setCircularOrbitPointingDown(star, 60, 1850, 50); //focus, angle, orbit radius, orbit days

		//Relay
		SectorEntityToken relay = system.addCustomEntity("fsdf_fafnir_relay",
				 "Fafnir Relay",
				 "comm_relay",
				 "fsdf_draconis");
		relay.setCircularOrbitPointingDown(star, 180, 1850, 50); //focus, angle, orbit radius, orbit days

		//Array
		SectorEntityToken array = system.addCustomEntity("fsdf_fafnir_array",
				 "Fafnir Array",
				 "sensor_array",
				 "fsdf_draconis");
		array.setCircularOrbitPointingDown(star, 300, 1850, 50); //focus, angle, orbit radius, orbit days

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