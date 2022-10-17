package data.scripts;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.econ.AbandonedStation;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.combat.entities.terrain.Planet;
import java.awt.*;

public class fsdf_ModPlugin extends BaseModPlugin {
    @Override
    public void onNewGame() {

    	//Change this to false to turn off preset conditions and allow random conditions for all the planets.
    	boolean presetConditions = true;

		SectorAPI sector = Global.getSector();
		StarSystemAPI system = sector.createStarSystem("Fafnir");

		
		//Star
		PlanetAPI star = system.initStar(
			"fsdf_fafnir",
			"star_yellow", //star types located in starsector-core\data\config\planets.json
			800,
			-750, //was 11000
			-5250, //was -11000
			600);  //id, type, radius, x coordinate, y coordiante, corona radius
			

		system.setBackgroundTextureFilename("graphics/mod/backgrounds/modbg.jpg");


		// Vesta
		PlanetAPI vesta = system.addPlanet("vesta", star, "Vesta", "terran", 0, 300, 5000, 500);  //id, focus, name, type (starsector-core\data\config\planets.json), angle, radius, orbit radius, orbit days
		vesta.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_vesta")); //graphics\mod\planets\fsdf_vesta.jpg
		vesta.applySpecChanges();

		// Fafnir System Jump Point
		JumpPointAPI fsdf_fafnir_jp = Global.getFactory().createJumpPoint("fsdf_fafnir_jump_point", "Fringe Jump Point");
		fsdf_fafnir_jp.setCircularOrbit(system.getEntityById("vesta"), 0, 1600, 90);  //id, angle, orbit radius, orbit days
		fsdf_fafnir_jp.setStandardWormholeToHyperspaceVisual();
		system.addEntity(fsdf_fafnir_jp);
		

		// Vulcan
		PlanetAPI vulcan = system.addPlanet("vulcan", fsdf_fafnir_jp, "Vulcan","lava", 180, 150, 350, 30);  //id, focus, name, type (starsector-core\data\config\planets.json), angle, radius, orbit radius, orbit days
		vulcan.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_vulcan")); //graphics\mod\planets\fsdf_vulcan.jpg
		vulcan.applySpecChanges();
		
		
		// Minerva
		PlanetAPI minerva = system.addPlanet("minerva", fsdf_fafnir_jp, "Minerva","frozen", 0, 150, 350, 30);  //id, focus, name, type (starsector-core\data\config\planets.json), angle, radius, orbit radius, orbit days
		minerva.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "fsdf_minerva")); //graphics\mod\planets\fsdf_minerva.jpg
		minerva.applySpecChanges();

		system.autogenerateHyperspaceJumpPoints(false,false); //gas giant = false, fringe = false / generates star gravity well
		

		//Gate
		SectorEntityToken fsdf_fafnir_gate = system.addCustomEntity("fsdf_fafnir_gate",
				 "Fafnir Gate",
				 "inactive_gate",
				 null);
		fsdf_fafnir_gate.setCircularOrbit(vesta, 180, 1600, 90); //focus, angle, orbit radius, orbit days

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
    }
}