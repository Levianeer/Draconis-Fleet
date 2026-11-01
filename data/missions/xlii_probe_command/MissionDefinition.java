package data.missions.xlii_probe_command;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import levianeer.draconis.data.scripts.combat.probe.CommandProbe;
import levianeer.draconis.data.scripts.combat.testharness.TestMissions;

public class MissionDefinition implements MissionDefinitionPlugin {

    public void defineMission(MissionDefinitionAPI api) {
        TestMissions.define(api, "xlii_probe_command", TestMissions.Opponent.MIRROR);
        api.addBriefingItem("Probe: the AI side's admiral is replaced; ships warp out and back in at 20-49s, two retreat at 80s");
        api.addPlugin(new CommandProbe());
    }
}
