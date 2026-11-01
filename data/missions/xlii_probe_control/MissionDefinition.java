package data.missions.xlii_probe_control;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import levianeer.draconis.data.scripts.combat.probe.ControlProbe;
import levianeer.draconis.data.scripts.combat.testharness.TestMissions;

public class MissionDefinition implements MissionDefinitionPlugin {

    public void defineMission(MissionDefinitionAPI api) {
        TestMissions.define(api, "xlii_probe_control", TestMissions.Opponent.MIRROR);
        api.addBriefingItem("Probe: the AI side is steered by the test controller, not vanilla");
        api.addPlugin(new ControlProbe());
    }
}
