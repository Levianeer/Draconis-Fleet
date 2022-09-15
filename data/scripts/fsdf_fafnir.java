package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.econ.AbandonedStation;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.combat.entities.terrain.Planet;

import java.awt.*;

public class fsdf_fafnir extends BaseModPlugin {
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


		////////////////////////////////
		// Planet & Entity Generation //
		////////////////////////////////


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
		
		
		////////////////////////
		// Structure Entities //
		////////////////////////
		
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

		/////////////////////////////////////
		// Preset Planet & Moon Conditions //
		/////////////////////////////////////

		if (presetConditions) {

			// Vesta Conditions - ??? Hazard Rating
			MarketAPI vesta_market = Global.getFactory().createMarket("vesta_market", vesta.getName(), 0);
			vesta_market.setPlanetConditionMarketOnly(true);
			vesta_market.addCondition(Conditions.HABITABLE);
			vesta_market.addCondition(Conditions.MILD_CLIMATE);
			vesta_market.addCondition(Conditions.FARMLAND_BOUNTIFUL);  //planet conditions located in: starsector-core\data\campaign\market_conditions.csv
			vesta_market.addCondition(Conditions.ORE_ULTRARICH);
			vesta_market.addCondition(Conditions.RARE_ORE_ULTRARICH);
			vesta_market.addCondition(Conditions.RUINS_VAST);
			vesta_market.addCondition(Conditions.ORGANICS_PLENTIFUL);
			//vesta_market.addCondition(Conditions.DECIVILIZED);
			vesta_market.setPrimaryEntity(vesta);
			vesta.setMarket(vesta_market);
			

			// Vulcan Conditions ??? Hazard Rating
			MarketAPI vulcan_market = Global.getFactory().createMarket("vulcan_market", vulcan.getName(), 0);
			vulcan_market.setPlanetConditionMarketOnly(true);
			//vulcan_market.addCondition(Conditions.HABITABLE);
			vulcan_market.addCondition(Conditions.NO_ATMOSPHERE);
			vulcan_market.addCondition(Conditions.MILD_CLIMATE);
			vulcan_market.addCondition(Conditions.ORE_ULTRARICH);
			vulcan_market.addCondition(Conditions.RARE_ORE_ULTRARICH);  //planet conditions located in: starsector-core\data\campaign\market_conditions.csv
			vulcan_market.addCondition(Conditions.RUINS_VAST);
			vulcan_market.addCondition(Conditions.VOLATILES_PLENTIFUL);
			vulcan_market.addCondition(Conditions.VERY_HOT);
			//vulcan_market.addCondition(Conditions.LOW_GRAVITY);
			vulcan_market.setPrimaryEntity(vulcan);
			vulcan.setMarket(vulcan_market);
			
			
			// Minerva Conditions ??? Hazard Rating
			MarketAPI minerva_market = Global.getFactory().createMarket("minerva_market", minerva.getName(), 0);
			minerva_market.setPlanetConditionMarketOnly(true);
			//minerva_market.addCondition(Conditions.HABITABLE);
			minerva_market.addCondition(Conditions.NO_ATMOSPHERE);
			minerva_market.addCondition(Conditions.MILD_CLIMATE);
			minerva_market.addCondition(Conditions.ORE_ULTRARICH);  //planet conditions located in: starsector-core\data\campaign\market_conditions.csv
			minerva_market.addCondition(Conditions.RARE_ORE_ULTRARICH);
			minerva_market.addCondition(Conditions.RUINS_VAST);
			minerva_market.addCondition(Conditions.VOLATILES_PLENTIFUL);
			minerva_market.addCondition(Conditions.VERY_COLD);
			//minerva_market.addCondition(Conditions.LOW_GRAVITY);
			minerva_market.setPrimaryEntity(minerva);
			minerva.setMarket(minerva_market);

		}
    }
}