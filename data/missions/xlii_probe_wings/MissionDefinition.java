package data.missions.xlii_probe_wings;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import levianeer.draconis.data.scripts.combat.probe.WingProbe;
import levianeer.draconis.data.scripts.combat.testharness.TestMissions;

public class MissionDefinition implements MissionDefinitionPlugin {

    public void defineMission(MissionDefinitionAPI api) {
        TestMissions.define(api, "xlii_probe_wings", TestMissions.Opponent.MIRROR);
        api.addBriefingItem("Probe: AI-side wings recall at 30s, strike the Alwaid at 50s, the Juza at 70s, escort at 90s");
        api.addPlugin(new WingProbe());
    }
}
