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
		api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true);
		api.initFleet(FleetSide.ENEMY, "DSS", FleetGoal.ATTACK, true);

		// Set a small blurb for each fleet that shows up on the mission detail and
		// mission results screens to identify each side.
		api.setFleetTagline(FleetSide.PLAYER, "The Great Vlorik Pirate Armata");
		api.setFleetTagline(FleetSide.ENEMY, "Draconis Detachment");
		
		// These show up as items in the bulleted list under 
		// "Tactical Objectives" on the mission detail screen
		api.addBriefingItem("Defeat the enemy forces and keep the 'Fist of Vlorik alive'.");
		api.addBriefingItem("Draconis ships rely on torpedos, missiles and spinally mounted MACs.");
		api.addBriefingItem("Bring strong Point Defense and brace for some hard punches, this is why we drink, boys.");
		
		// Player's fleet.
		api.addToFleet(FleetSide.PLAYER, "atlas2_Standard", FleetMemberType.SHIP, "Fist of Vlorik", true);					//	31

		api.addToFleet(FleetSide.PLAYER, "champion_Assault", FleetMemberType.SHIP, "Space Dog",false);						//	25	// 45 Total
		api.addToFleet(FleetSide.PLAYER, "eradicator_pirates_Attack", FleetMemberType.SHIP, "Moral Ambivalence", false);	//	20

		api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, false);								//	10	// 20 Total 
		api.addToFleet(FleetSide.PLAYER, "condor_Strike", FleetMemberType.SHIP, false);										//	10

		api.addToFleet(FleetSide.PLAYER, "afflictor_d_pirates_Strike", FleetMemberType.SHIP, false);						//	10	// 15 Total
		api.addToFleet(FleetSide.PLAYER, "wolf_Assault", FleetMemberType.SHIP, false);										//	5
		
		api.defeatOnShipLoss("Fist of Vlorik");

		// Enemy fleet.
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, true);		//	20 ea	// Total 40
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, false);

		api.addToFleet(FleetSide.ENEMY, "fsdf_altais_standard", FleetMemberType.SHIP, false);		//	22
		
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);	//	10 ea	// Total 30
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);

		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);		//	6 ea	// Total 18
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);
		
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