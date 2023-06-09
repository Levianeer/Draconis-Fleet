package data.missions.new_dawn;

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
		
		api.initFleet(FleetSide.PLAYER, "MSS", FleetGoal.ATTACK, true);
		api.initFleet(FleetSide.ENEMY, "DNS", FleetGoal.ATTACK, true);

		api.setFleetTagline(FleetSide.PLAYER, "ADD TAG.");
		api.setFleetTagline(FleetSide.ENEMY, "ADD TAG.");
		
		//Briefing
		api.addBriefingItem("ADD DESCRIPTION.");

		// Set up the player's fleet
		api.addToFleet(FleetSide.PLAYER, "conquest_Standard", FleetMemberType.SHIP, true);	// 40	// Total : 173

		api.addToFleet(FleetSide.PLAYER, "eagle_Assault", FleetMemberType.SHIP, false);	// 20
		api.addToFleet(FleetSide.PLAYER, "falcon_CS", FleetMemberType.SHIP, false);	// 14
		api.addToFleet(FleetSide.PLAYER, "falcon_CS", FleetMemberType.SHIP, false);	// 14
		api.addToFleet(FleetSide.PLAYER, "heron_Strike", FleetMemberType.SHIP, false);	// 20
		api.addToFleet(FleetSide.PLAYER, "heron_Strike", FleetMemberType.SHIP, false);	// 20

		api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, false);	// 10
		api.addToFleet(FleetSide.PLAYER, "sunder_CS", FleetMemberType.SHIP, false);	// 11
		api.addToFleet(FleetSide.PLAYER, "gemini_Standard", FleetMemberType.SHIP, false);	// 9

		api.addToFleet(FleetSide.PLAYER, "vigilance_FS", FleetMemberType.SHIP, false);	// 5
		api.addToFleet(FleetSide.PLAYER, "vigilance_FS", FleetMemberType.SHIP, false);	// 5
		api.addToFleet(FleetSide.PLAYER, "vigilance_FS", FleetMemberType.SHIP, false);	// 5


		// Set up the enemy fleet
		api.addToFleet(FleetSide.ENEMY, "fsdf_altais_strike", FleetMemberType.SHIP, false);	// 40	// Total : 170

		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, false);	// 25
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, false);	// 25
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, false);	// 25

		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);	// 10
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);	// 10
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);	// 10
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);	// 10

		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);	// 5
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);	// 5
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);	// 5

		// Set up the map.
		float width = 24000f;
		float height = 18000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);
		
		float minX = -width/2;
		float minY = -height/2;
		
		for (int i = 0; i < 15; i++) {
			float x = (float) Math.random() * width - width/2;
			float y = (float) Math.random() * height - height/2;
			float radius = 100f + (float) Math.random() * 900f; 
			api.addNebula(x, y, radius);
		}
		
		api.addNebula(minX + width * 0.8f - 1000, minY + height * 0.4f, 2000);
		api.addNebula(minX + width * 0.8f - 1000, minY + height * 0.5f, 2000);
		api.addNebula(minX + width * 0.8f - 1000, minY + height * 0.6f, 2000);
		
		api.addObjective(minX + width * 0.8f - 1000, minY + height * 0.4f, "nav_buoy");
		api.addObjective(minX + width * 0.8f - 1000, minY + height * 0.6f, "nav_buoy");
		api.addObjective(minX + width * 0.3f + 1000, minY + height * 0.3f, "comm_relay");
		api.addObjective(minX + width * 0.3f + 1000, minY + height * 0.7f, "comm_relay");
		api.addObjective(minX + width * 0.5f, minY + height * 0.5f, "sensor_array");
		api.addObjective(minX + width * 0.2f + 1000, minY + height * 0.5f, "sensor_array");
		
		// Add an asteroid field
		api.addAsteroidField(minX + width * 0.3f, minY, 90, 3000f,
								20f, 70f, 50);
		
		// Add some planets.  These are defined in data/config/planets.json.
		api.addPlanet(0, 0, 400f, "fsdf_itoron", 350f, true);
	}

}