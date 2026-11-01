package data.missions.xlii_test_hegemony;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import levianeer.draconis.data.scripts.combat.testharness.TestMissions;

public class MissionDefinition implements MissionDefinitionPlugin {

    public void defineMission(MissionDefinitionAPI api) {
        TestMissions.define(api, "xlii_test_hegemony", TestMissions.Opponent.HEGEMONY);
    }
}
