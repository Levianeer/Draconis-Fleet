package data.missions.highorbit;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

public class MissionDefinition implements MissionDefinitionPlugin {

	public void defineMission(MissionDefinitionAPI api) {

		// Set up the fleets
		api.initFleet(FleetSide.PLAYER, "DNS", FleetGoal.ATTACK, false);
		api.initFleet(FleetSide.ENEMY, "HSS", FleetGoal.ATTACK, true);

		// Fleet taglines from the narrative
		api.setFleetTagline(FleetSide.PLAYER, "Admiral August's Strike Fleet");
		api.setFleetTagline(FleetSide.ENEMY, "Hegemony Inspection Fleet Battlegroup");

		// Briefing items - tactical objectives
		api.addBriefingItem("Defend Draconis' right to operate AI cores freely");
		api.addBriefingItem("Achieve a decisive victory to deter future Hegemony interference");

		// Set up the player's fleet - Draconis Strike Fleet
        // Flagship
        api.addToFleet(FleetSide.PLAYER, "XLII_altais_Elite", FleetMemberType.SHIP, "Kori's Wrath", true);

        // Capital ships
        api.addToFleet(FleetSide.PLAYER, "XLII_dziban_Elite", FleetMemberType.SHIP, "Abstract Endurance", false);
        api.addToFleet(FleetSide.PLAYER, "XLII_alwaid_Elite", FleetMemberType.SHIP, "Apostle's Creed", false);

        // Cruiser line
        api.addToFleet(FleetSide.PLAYER, "XLII_eltanin_Elite", FleetMemberType.SHIP, "Steadfast Resolve", false);
        api.addToFleet(FleetSide.PLAYER, "XLII_juni_Elite", FleetMemberType.SHIP, "Defiant", false);
        api.addToFleet(FleetSide.PLAYER, "XLII_juza_Elite", FleetMemberType.SHIP, "Atlas", false);

        // Destroyer screen
		api.addToFleet(FleetSide.PLAYER, "XLII_giausar_Elite", FleetMemberType.SHIP, "Iron Curtain", false);
		api.addToFleet(FleetSide.PLAYER, "XLII_errakis_Elite", FleetMemberType.SHIP, "Vengeance", false);
        api.addToFleet(FleetSide.PLAYER, "XLII_rastaban_Elite", FleetMemberType.SHIP, "Calcutta", false);
        api.addToFleet(FleetSide.PLAYER, "XLII_shaobi_Elite", FleetMemberType.SHIP, "Thunderbolt", false);

		// Fast frigate support
        api.addToFleet(FleetSide.PLAYER, "XLII_alruba_Elite", FleetMemberType.SHIP, "Swift Justice", false);
        api.addToFleet(FleetSide.PLAYER, "XLII_alruba_mk2_Elite", FleetMemberType.SHIP, "Dark Was the Night", false);
        api.addToFleet(FleetSide.PLAYER, "XLII_thuban_Elite", FleetMemberType.SHIP, "Do You Feel Lucky?", false);

		// Set up the enemy fleet - Hegemony Compliance Battlegroup
		// Flagship
		api.addToFleet(FleetSide.ENEMY, "onslaught_Elite", FleetMemberType.SHIP, "Hegemon's Will", false);

        // Capital ships
        api.addToFleet(FleetSide.ENEMY, "legion_Strike", FleetMemberType.SHIP, "Babylonian", false);
        api.addToFleet(FleetSide.ENEMY, "legion_Strike", FleetMemberType.SHIP, "All Of Our Hopes", false);

		// Cruiser line
		api.addToFleet(FleetSide.ENEMY, "dominator_Assault", FleetMemberType.SHIP, "Joseph", false);
        api.addToFleet(FleetSide.ENEMY, "dominator_Assault", FleetMemberType.SHIP, "Enforcer", false);
        api.addToFleet(FleetSide.ENEMY, "eagle_Assault", FleetMemberType.SHIP, "Voyageur", false);
        api.addToFleet(FleetSide.ENEMY, "eagle_Assault", FleetMemberType.SHIP, "Home Away", false);
		api.addToFleet(FleetSide.ENEMY, "falcon_Attack", FleetMemberType.SHIP, "Compliance", false);
		api.addToFleet(FleetSide.ENEMY, "falcon_Attack", FleetMemberType.SHIP, "Authority", false);

		// Destroyer screen
		api.addToFleet(FleetSide.ENEMY, "hammerhead_Balanced", FleetMemberType.SHIP, "Retribution", false);
        api.addToFleet(FleetSide.ENEMY, "hammerhead_Balanced", FleetMemberType.SHIP, "Bright Tide", false);
		api.addToFleet(FleetSide.ENEMY, "enforcer_Balanced", FleetMemberType.SHIP, "Lawbringer", false);
		api.addToFleet(FleetSide.ENEMY, "sunder_Assault", FleetMemberType.SHIP, "Storm", false);

		// Picket frigates
		api.addToFleet(FleetSide.ENEMY, "lasher_CS", FleetMemberType.SHIP, "Watchful", false);
		api.addToFleet(FleetSide.ENEMY, "wolf_CS", FleetMemberType.SHIP, "Guardian", false);

		// Set up the map - Kori High Orbit
		float width = 10000f;
		float height = 10000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);

		float minX = -width/2;
		float minY = -height/2;

		// Add planet in center - Kori (frozen moon)
		// Scaled up from campaign size (40f) for mission visibility
		api.addPlanet(0, 0, 250f, "frozen", 0f, false);

		// Add asteroid/debris field for tactical cover
		// Positioned to the side, not blocking main engagement area
		api.addAsteroidField(minX + 3000, minY + height / 2, 45, 6000f, 20f, 70f, 40);

		// Additional smaller debris field
		api.addAsteroidField(minX + width - 4000, minY + height / 2, -30, 5000f, 15f, 60f, 20);
	}
}