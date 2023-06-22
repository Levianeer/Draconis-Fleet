package data.missions.capital_test;

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
		
		api.initFleet(FleetSide.PLAYER, "DNS", FleetGoal.ATTACK, true);
		api.initFleet(FleetSide.ENEMY, "MSS", FleetGoal.ATTACK, true);
		
		//Briefing
		api.addBriefingItem("For Testing.");

		// Set up the player's fleet
		api.addToFleet(FleetSide.PLAYER, "fsdf_altais_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.PLAYER, "fsdf_altais_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.PLAYER, "fsdf_altais_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.PLAYER, "fsdf_altais_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.PLAYER, "fsdf_altais_strike", FleetMemberType.SHIP, false);


		// Set up the enemy fleet
		api.addToFleet(FleetSide.ENEMY, "conquest_Elite", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "conquest_Elite", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "conquest_Elite", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "conquest_Elite", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "conquest_Elite", FleetMemberType.SHIP, false);

		// Set up the map.
		float width = 12000f;
		float height = 9000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);
		
		float minX = -width/2;
		float minY = -height/2;
		
		for (int i = 0; i < 15; i++) {
			float x = (float) Math.random() * width - width/2;
			float y = (float) Math.random() * height - height/2;
			float radius = 100f + (float) Math.random() * 900f; 
			api.addNebula(x, y, radius);
		}
		
	}

}