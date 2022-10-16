package data.missions.letyoudown;

import java.util.List;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

public class MissionDefinition implements MissionDefinitionPlugin {

	public void defineMission(MissionDefinitionAPI api) {

		
		// Set up the fleets so we can add ships and fighter wings to them.
		// In this scenario, the fleets are attacking each other, but
		// in other scenarios, a fleet may be defending or trying to escape
		api.initFleet(FleetSide.PLAYER, "ISS", FleetGoal.ATTACK, false);
		api.initFleet(FleetSide.ENEMY, "DSS", FleetGoal.ATTACK, true);

		// Set a small blurb for each fleet that shows up on the mission detail and
		// mission results screens to identify each side.
		api.setFleetTagline(FleetSide.PLAYER, "Combined Independent Fleet");
		api.setFleetTagline(FleetSide.ENEMY, "Draconis Home Defense Fleet");
		
		// These show up as items in the bulleted list under 
		// "Tactical Objectives" on the mission detail screen
		api.addBriefingItem("Defeat the enemy forces");
		api.addBriefingItem("The enemy relies on missiles and high burst damage, bring strong Point Defense");
		
		// Set up the player's fleet.  Variant names come from the
		// files in data/variants and data/variants/fighters

		// Capitals
		api.addToFleet(FleetSide.PLAYER, "conquest_Elite", FleetMemberType.SHIP, "ISS Ace Pilot Bile", true);				//	15 OP	// 15
		// Cruisers
		api.addToFleet(FleetSide.PLAYER, "champion_Assault", FleetMemberType.SHIP, "ISS Dunkirk Prize", false);				//	25 OP	// 115
		api.addToFleet(FleetSide.PLAYER, "champion_Assault", FleetMemberType.SHIP, "ISS Bulwark", false);					//	25 OP	//
		api.addToFleet(FleetSide.PLAYER, "champion_Assault", FleetMemberType.SHIP, "ISS Stalwart", false);					//	25 OP	//
		api.addToFleet(FleetSide.PLAYER, "heron_Strike", FleetMemberType.SHIP, "ISS Enterprise", false);					//	20 OP	//
		api.addToFleet(FleetSide.PLAYER, "heron_Strike", FleetMemberType.SHIP, "ISS Ark Royal", false);						//	20 OP	//
		// Destroyers
		api.addToFleet(FleetSide.PLAYER, "medusa_Attack", FleetMemberType.SHIP, "ISS Karma", false);						//	12 OP	// 12
		// Frigates
		api.addToFleet(FleetSide.PLAYER, "afflictor_Strike", FleetMemberType.SHIP, "ISS Blind Justice", false);				//	10 OP	// 58
		api.addToFleet(FleetSide.PLAYER, "afflictor_Strike", FleetMemberType.SHIP, "ISS Blind Consequence", false);			//	10 OP	//
		api.addToFleet(FleetSide.PLAYER, "afflictor_Strike", FleetMemberType.SHIP, "ISS Normal Consequence", false);		//	10 OP	//
		api.addToFleet(FleetSide.PLAYER, "afflictor_Strike", FleetMemberType.SHIP, "ISS Normal Justice", false);			//	10 OP	//
		api.addToFleet(FleetSide.PLAYER, "omen_PD", FleetMemberType.SHIP, "ISS Fortune", false);							//	6 OP 	//
		api.addToFleet(FleetSide.PLAYER, "omen_PD", FleetMemberType.SHIP, "ISS Quaker", false);								//	6 OP	//
		api.addToFleet(FleetSide.PLAYER, "omen_PD", FleetMemberType.SHIP, "ISS Feroze", false);								//	6 OP	//
		
		api.defeatOnShipLoss("ISS Ace Pilot Bile");
		// Set up the enemy fleet.

		// Cruisers
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, "DSS Armageddon's Edge", true);		//	22 OP	// 110
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, "DSS Lancelot", false);				//	22 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, "DSS Tharsis", false);					//	22 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, "DSS Persephone", false);				//	22 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, "DSS Brasidas", false);				//	22 OP	//
		// Destroyers
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, "DSS Aegis Fate", false);				//	11 OP	// 66
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, "DSS The Heart of Midlothian", false);//	11 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, "DSS Everest", false);				//	11 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, "DSS Leviathan", false);				//	11 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, "DSS Cascadia", false);				//	11 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, "DSS Marathon", false);				//	11 OP	//
		// Frigates
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, "DSS Ready or Not", false);				//	5 OP	// 25
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, "DSS Seattle", false);					//	5 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, "DSS Dresden", false);					//	5 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, "DSS Glasgow Kiss", false);				//	5 OP	//
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, "DSS Say My Name", false);				//	5 OP	//
		
		// Set up the map.
		float width = 24000f;
		float height = 18000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);
		
		float minX = -width/2;
		float minY = -height/2;
		
		api.addNebula(minX + width * 0.5f - 300, minY + height * 0.5f, 1000);
		api.addNebula(minX + width * 0.5f + 300, minY + height * 0.5f, 1000);
		
		for (int i = 0; i < 5; i++) {
			float x = (float) Math.random() * width - width/2;
			float y = (float) Math.random() * height - height/2;
			float radius = 100f + (float) Math.random() * 400f; 
			api.addNebula(x, y, radius);
		}
		
		// Add an asteroid field
		api.addAsteroidField(minX + width/2f, minY + height/2f, 0, 8000f,
								20f, 70f, 100);
		
		api.addPlugin(new BaseEveryFrameCombatPlugin() {
			public void init(CombatEngineAPI engine) {
				engine.getContext().setStandoffRange(6000f);
			}
			public void advance(float amount, List events) {
			}
		});
		
	}

}