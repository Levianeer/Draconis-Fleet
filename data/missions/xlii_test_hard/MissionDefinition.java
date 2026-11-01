package data.missions.xlii_test_hard;

import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import levianeer.draconis.data.scripts.combat.carrierdoctrine.TestMissionFleets;

public class MissionDefinition implements MissionDefinitionPlugin {

    public void defineMission(MissionDefinitionAPI api) {
        TestMissionFleets.setUp(api, "xlii_test_hard", "Capital-heavy battlegroup (230 DP)");

        api.addToFleet(FleetSide.PLAYER, "conquest_Elite", FleetMemberType.SHIP, "Hard Conquest", false);
        api.addToFleet(FleetSide.PLAYER, "onslaught_Elite", FleetMemberType.SHIP, "Hard Onslaught", false);
        api.addToFleet(FleetSide.PLAYER, "legion_Strike", FleetMemberType.SHIP, "Hard Legion", false);
        api.addToFleet(FleetSide.PLAYER, "dominator_Assault", FleetMemberType.SHIP, "Hard Dominator", false);
        api.addToFleet(FleetSide.PLAYER, "eagle_Assault", FleetMemberType.SHIP, "Hard Eagle", false);
        api.addToFleet(FleetSide.PLAYER, "mora_Assault", FleetMemberType.SHIP, "Hard Mora", false);
        api.addToFleet(FleetSide.PLAYER, "medusa_Attack", FleetMemberType.SHIP, "Hard Medusa", false);
        api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, "Hard Hammerhead 1", false);
        api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, "Hard Hammerhead 2", false);
        api.addToFleet(FleetSide.PLAYER, "tempest_Attack", FleetMemberType.SHIP, "Hard Tempest", false);
        api.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, "Hard Wolf", false);
    }
}
