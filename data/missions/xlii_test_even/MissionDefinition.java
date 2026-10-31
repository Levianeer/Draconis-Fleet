package data.missions.xlii_test_even;

import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import levianeer.draconis.data.scripts.combat.carrierdoctrine.TestMissionFleets;

public class MissionDefinition implements MissionDefinitionPlugin {

    public void defineMission(MissionDefinitionAPI api) {
        TestMissionFleets.setUp(api, "Hegemony-style mix (212 DP)");

        api.addToFleet(FleetSide.PLAYER, "onslaught_Elite", FleetMemberType.SHIP, "Even Onslaught", false);
        api.addToFleet(FleetSide.PLAYER, "legion_Strike", FleetMemberType.SHIP, "Even Legion", false);
        api.addToFleet(FleetSide.PLAYER, "dominator_Assault", FleetMemberType.SHIP, "Even Dominator", false);
        api.addToFleet(FleetSide.PLAYER, "eagle_Assault", FleetMemberType.SHIP, "Even Eagle", false);
        api.addToFleet(FleetSide.PLAYER, "heron_Attack", FleetMemberType.SHIP, "Even Heron", false);
        api.addToFleet(FleetSide.PLAYER, "falcon_Attack", FleetMemberType.SHIP, "Even Falcon", false);
        api.addToFleet(FleetSide.PLAYER, "sunder_Assault", FleetMemberType.SHIP, "Even Sunder", false);
        api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, "Even Hammerhead", false);
        api.addToFleet(FleetSide.PLAYER, "enforcer_Balanced", FleetMemberType.SHIP, "Even Enforcer", false);
        api.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, "Even Wolf", false);
        api.addToFleet(FleetSide.PLAYER, "brawler_Elite", FleetMemberType.SHIP, "Even Brawler", false);
        api.addToFleet(FleetSide.PLAYER, "vigilance_Strike", FleetMemberType.SHIP, "Even Vigilance", false);
        api.addToFleet(FleetSide.PLAYER, "lasher_CS", FleetMemberType.SHIP, "Even Lasher", false);
        api.addToFleet(FleetSide.PLAYER, "centurion_Assault", FleetMemberType.SHIP, "Even Centurion", false);
    }
}
