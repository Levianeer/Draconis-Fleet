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
		
		api.initFleet(FleetSide.PLAYER, "ISS", FleetGoal.ATTACK, true);
		api.initFleet(FleetSide.ENEMY, "DNS", FleetGoal.ATTACK, true);

		api.setFleetTagline(FleetSide.PLAYER, "ADD DESCRIPTION.");
		api.setFleetTagline(FleetSide.ENEMY, "ADD DESCRIPTION.");
		
		//Briefing
		api.addBriefingItem("ADD DESCRIPTION.");
		api.addBriefingItem("");
		api.addBriefingItem("");

		//Player team
		api.addToFleet(FleetSide.PLAYER, "conquest_Elite", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "conquest_Elite", FleetMemberType.SHIP, true);

		api.addToFleet(FleetSide.PLAYER, "gryphon_Standard", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "gryphon_Standard", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "gryphon_Standard", FleetMemberType.SHIP, true);

		api.addToFleet(FleetSide.PLAYER, "sunder_Assault", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "sunder_Assault", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "sunder_Assault", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "sunder_Assault", FleetMemberType.SHIP, true);

		api.addToFleet(FleetSide.PLAYER, "vigilance_Support1", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "vigilance_Support1", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "vigilance_Support1", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "vigilance_Support1", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.PLAYER, "vigilance_Support1", FleetMemberType.SHIP, true);


		//Enemy team
		api.addToFleet(FleetSide.ENEMY, "fsdf_altais_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_altais_strike", FleetMemberType.SHIP, false);

		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_eltanin_strike", FleetMemberType.SHIP, false);

		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_rastaban_strike", FleetMemberType.SHIP, false);

		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "fsdf_thuban_strike", FleetMemberType.SHIP, false);
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