package data.missions.xlii_test_swarm;

import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import levianeer.draconis.data.scripts.combat.carrierdoctrine.TestMissionFleets;

public class MissionDefinition implements MissionDefinitionPlugin {

    public void defineMission(MissionDefinitionAPI api) {
        TestMissionFleets.setUp(api, "xlii_test_swarm", "Frigate and destroyer swarm (146 DP)");

        api.addToFleet(FleetSide.PLAYER, "fury_Attack", FleetMemberType.SHIP, "Swarm Fury", false);
        api.addToFleet(FleetSide.PLAYER, "medusa_Attack", FleetMemberType.SHIP, "Swarm Medusa", false);
        api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, "Swarm Hammerhead 1", false);
        api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, "Swarm Hammerhead 2", false);
        api.addToFleet(FleetSide.PLAYER, "sunder_Assault", FleetMemberType.SHIP, "Swarm Sunder", false);
        api.addToFleet(FleetSide.PLAYER, "shrike_Attack", FleetMemberType.SHIP, "Swarm Shrike", false);
        api.addToFleet(FleetSide.PLAYER, "tempest_Attack", FleetMemberType.SHIP, "Swarm Tempest 1", false);
        api.addToFleet(FleetSide.PLAYER, "tempest_Attack", FleetMemberType.SHIP, "Swarm Tempest 2", false);
        api.addToFleet(FleetSide.PLAYER, "scarab_Experimental", FleetMemberType.SHIP, "Swarm Scarab 1", false);
        api.addToFleet(FleetSide.PLAYER, "scarab_Experimental", FleetMemberType.SHIP, "Swarm Scarab 2", false);
        api.addToFleet(FleetSide.PLAYER, "omen_PD", FleetMemberType.SHIP, "Swarm Omen", false);
        api.addToFleet(FleetSide.PLAYER, "wolf_Assault", FleetMemberType.SHIP, "Swarm Wolf 1", false);
        api.addToFleet(FleetSide.PLAYER, "wolf_Assault", FleetMemberType.SHIP, "Swarm Wolf 2", false);
        api.addToFleet(FleetSide.PLAYER, "wolf_Assault", FleetMemberType.SHIP, "Swarm Wolf 3", false);
        api.addToFleet(FleetSide.PLAYER, "lasher_Assault", FleetMemberType.SHIP, "Swarm Lasher 1", false);
        api.addToFleet(FleetSide.PLAYER, "lasher_Assault", FleetMemberType.SHIP, "Swarm Lasher 2", false);
        api.addToFleet(FleetSide.PLAYER, "lasher_Assault", FleetMemberType.SHIP, "Swarm Lasher 3", false);
        api.addToFleet(FleetSide.PLAYER, "brawler_Elite", FleetMemberType.SHIP, "Swarm Brawler 1", false);
        api.addToFleet(FleetSide.PLAYER, "brawler_Elite", FleetMemberType.SHIP, "Swarm Brawler 2", false);
    }
}
