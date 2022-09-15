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

public class MODSystem_modPlugin extends BaseModPlugin {
    @Override
    public void onNewGame() {

    	//Change this to false to turn off preset conditions and allow random conditions for all the planets.
    	boolean presetConditions = true;

		SectorAPI sector = Global.getSector();
		StarSystemAPI system = sector.createStarSystem("Calvera");

		//Star
		PlanetAPI star = system.initStar(
			"calvera",
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
		vesta.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "mod_vesta")); //graphics\mod\planets\mod_vesta.jpg
		vesta.applySpecChanges();
		
		
		// Calvera System Jump Point
		JumpPointAPI calvera_jp = Global.getFactory().createJumpPoint("calvera_jump_point", "Fringe Jump Point");
		calvera_jp.setCircularOrbit(system.getEntityById("vesta"), 0, 1600, 90);  //id, angle, orbit radius, orbit days
		calvera_jp.setStandardWormholeToHyperspaceVisual();
		system.addEntity(calvera_jp);
		

		// Vulcan
		PlanetAPI vulcan = system.addPlanet("vulcan", calvera_jp, "Vulcan","lava", 180, 150, 350, 30);  //id, focus, name, type (starsector-core\data\config\planets.json), angle, radius, orbit radius, orbit days
		vulcan.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "mod_vulcan")); //graphics\mod\planets\mod_vulcan.jpg
		vulcan.applySpecChanges();
		
		
		// Minerva
		PlanetAPI minerva = system.addPlanet("minerva", calvera_jp, "Minerva","frozen", 0, 150, 350, 30);  //id, focus, name, type (starsector-core\data\config\planets.json), angle, radius, orbit radius, orbit days
		minerva.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "mod_minerva")); //graphics\mod\planets\mod_minerva.jpg
		minerva.applySpecChanges();
		

		system.autogenerateHyperspaceJumpPoints(false,false); //gas giant = false, fringe = false / generates star gravity well
		
		
		////////////////////////
		// Structure Entities //
		////////////////////////
		
		//Gate
		SectorEntityToken calvera_gate = system.addCustomEntity("calvera_gate",
				 "Calvera Gate",
				 "inactive_gate",
				 null);
		calvera_gate.setCircularOrbit(vesta, 180, 1600, 90); //focus, angle, orbit radius, orbit days

		//Buoy
		SectorEntityToken buoy = system.addCustomEntity("calvera_buoy",
				 "Calvera Buoy",
				 "nav_buoy",
				 "draconis");
		buoy.setCircularOrbitPointingDown(calvera_gate, 60, 200, 45); //focus, angle, orbit radius, orbit days

		//Relay
		SectorEntityToken relay = system.addCustomEntity("calvera_relay",
				 "Calvera Relay",
				 "comm_relay",
				 "draconis");
		relay.setCircularOrbitPointingDown(calvera_gate, 180, 200, 45); //focus, angle, orbit radius, orbit days

		//Array
		SectorEntityToken array = system.addCustomEntity("calvera_array",
				 "Calvera Array",
				 "sensor_array",
				 "draconis");
		array.setCircularOrbitPointingDown(calvera_gate, 300, 200, 45); //focus, angle, orbit radius, orbit days
		
		//Cryosleeper
		SectorEntityToken ccryosleeper = system.addCustomEntity("calvera_cryosleeper",
				 "Domain-era Cryosleeper",
				 "derelict_cryosleeper",  //custom entity types located in: starsector-core\data\config\custom_entities.json
				 "draconis");
		ccryosleeper.setCircularOrbitPointingDown(star, 180, 10000, 500); //focus, angle, orbit radius, orbit days
		
		//Coronal Hypershunt
		SectorEntityToken ccoronaltap = system.addCustomEntity("calvera_coronal_hypershunt",
				 "Coronal Hypershunt",
				 "coronal_tap",  //custom entity types located in: starsector-core\data\config\custom_entities.json
				 "draconis");
		ccoronaltap.setCircularOrbitPointingDown(star, 180, 1200, 500); //focus, angle, orbit radius, orbit days
		
		//-----------Research Stations-----------//
		
		//Research Station 1
		SectorEntityToken cresearchstation1 = system.addCustomEntity("calvera_research_station_1",
				 "Research Station",
				 "station_research",  //custom entity types located in: starsector-core\data\config\custom_entities.json
				 "draconis");
		cresearchstation1.setCircularOrbitPointingDown(star, 90, 1300, 500); //focus, angle, orbit radius, orbit days
		
		//Research Station 2
		SectorEntityToken cresearchstation2 = system.addCustomEntity("calvera_research_station_2",
				 "Research Station",
				 "station_research",  //custom entity types located in: starsector-core\data\config\custom_entities.json
				 "draconis");
		cresearchstation2.setCircularOrbitPointingDown(star, 270, 1300, 500); //focus, angle, orbit radius, orbit days
		
		//-----------Loot Around Cryosleeper-----------//
		
		//Technology Cache
		SectorEntityToken ctechcache = system.addCustomEntity("calvera_tech_cache",
				 "Technology Cache",
				 "technology_cache",  //custom entity types located in: starsector-core\data\config\custom_entities.json
				 "draconis");
		ctechcache.setCircularOrbitPointingDown(ccryosleeper, 180, 400, 15); //focus, angle, orbit radius, orbit days
		
		//Equipment Cache
		SectorEntityToken cequipcache = system.addCustomEntity("calvera_equpment_cache",
				 "Equipment Cache",
				 "equipment_cache",  //custom entity types located in: starsector-core\data\config\custom_entities.json
				 "draconis");
		cequipcache.setCircularOrbitPointingDown(ccryosleeper, 60, 400, 15); //focus, angle, orbit radius, orbit days
		
		//Alpha Site Weapons Cache
		SectorEntityToken calphacache = system.addCustomEntity("calvera_alpha_cache",
				 "Heavily Shielded Cache",
				 "alpha_site_weapons_cache",  //custom entity types located in: starsector-core\data\config\custom_entities.json
				 "draconis");
		calphacache.setCircularOrbitPointingDown(ccryosleeper, 300, 400, 15); //focus, angle, orbit radius, orbit days
		

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