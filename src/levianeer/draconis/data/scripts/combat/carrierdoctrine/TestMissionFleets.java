package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;

/**
 * Shared setup for the admiral test missions ({@code data/missions/xlii_test_*}). SIM cannot replay
 * the same opponent, and opponent strength swamped every A/B run there, so these missions fix both
 * fleets: the FortySecond fleet below is identical in every mission, defined once here so the
 * missions cannot drift apart. It is the AI side (owner 1), where the admiral runs; the opponent is
 * the player side, flown on autopilot. No objectives or terrain, to keep runs comparable.
 * <p>
 * Kept at 155 DP so the whole fleet deploys at the start of every mission: the game splits the
 * battle size between the sides by fleet DP, so both fleets together must fit in the maximum battle
 * size of 400, and the hard mission's 230 DP plus this is 385. At 200 DP a different ship stayed in
 * reserve every run, a capital in the hard mission.
 */
public final class TestMissionFleets {

    public static final float MAP_SIZE = 14000f;

    private TestMissionFleets() {
    }

    /** Both fleets initialised, the FortySecond side filled and a plain map laid out. */
    public static void setUp(MissionDefinitionAPI api, String missionId, String opponentTagline) {
        api.initFleet(FleetSide.PLAYER, "TST", FleetGoal.ATTACK, true);
        api.initFleet(FleetSide.ENEMY, "DNS", FleetGoal.ATTACK, true);
        api.setFleetTagline(FleetSide.PLAYER, opponentTagline);
        api.setFleetTagline(FleetSide.ENEMY, "FortySecond test fleet (155 DP)");
        api.addBriefingItem("Test mission: turn on autopilot - your side is the opponent");
        api.addBriefingItem("Set battle size to 400 so both fleets deploy in full");
        for (String line : TestRunLedger.briefing(missionId)) api.addBriefingItem(line);

        // 1 capital, 2 cruisers, 2 destroyers, 2 frigates; 3 carriers (Alwaid, Alrakis, Shaobi)
        add(api, "FortySecond_alwaid_Elite", "Test Alwaid");
        add(api, "FortySecond_alrakis_Elite", "Test Alrakis");
        add(api, "FortySecond_juza_Elite", "Test Juza");
        add(api, "FortySecond_shaobi_Elite", "Test Shaobi");
        add(api, "FortySecond_errakis_Elite", "Test Errakis");
        add(api, "FortySecond_alruba_Elite", "Test Alruba");
        add(api, "FortySecond_thuban_Elite", "Test Thuban");

        api.initMap(-MAP_SIZE / 2f, MAP_SIZE / 2f, -MAP_SIZE / 2f, MAP_SIZE / 2f);
    }

    private static void add(MissionDefinitionAPI api, String variant, String name) {
        api.addToFleet(FleetSide.ENEMY, variant, FleetMemberType.SHIP, name, false);
    }
}
