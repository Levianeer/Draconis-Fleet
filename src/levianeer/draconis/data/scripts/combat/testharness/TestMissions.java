package levianeer.draconis.data.scripts.combat.testharness;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;

/**
 * Shared setup for the combat AI test missions ({@code data/missions/xlii_test_*}), Phase 0 of
 * {@code .claude/systems/draconis-commander.md}. Every fleet is defined once here so the missions
 * cannot drift apart. The FortySecond fleet is the AI side (owner 1), where the commander will run;
 * the opponent is the player side, flown on autopilot. Only the FortySecond is tested: every other
 * Draconis fleet is meant to be worse. Plain map, no objectives, no officers on either side, so runs
 * differ only by the AI.
 * <p>
 * The FortySecond's optimised loadouts already win most even fights under vanilla AI, so an even
 * fight cannot show whether the AI is any good. Two kinds of mission instead: the mirror, where the
 * same fleet on both sides cancels the loadout advantage, and outnumbered fights, where each
 * opponent's {@link Opponent#odds} is tuned until vanilla wins only about 2 runs in 5.
 * <p>
 * The game splits the battle size between the sides in proportion to fleet DP and the maximum is
 * 400, so both fleets together must stay under 400 or ships wait in reserve. The FortySecond fleet
 * is kept small (133 DP) to leave room for odds up to 2x.
 */
public final class TestMissions {

    public static final float MAP_SIZE = 14000f;

    /** Capital, heavy cruiser, light carrier, destroyer, three frigates. Variant, name, DP. */
    private static final String[][] FORTYSECOND = {
            {"FortySecond_alwaid_Elite", "Alwaid", "60"},
            {"FortySecond_juza_Elite", "Juza", "30"},
            {"FortySecond_shaobi_Elite", "Shaobi", "14"},
            {"FortySecond_errakis_Elite", "Errakis", "11"},
            {"FortySecond_alruba_Elite", "Alruba", "6"},
            {"FortySecond_thuban_Elite", "Thuban", "6"},
            {"FortySecond_thuban_Elite", "Thuban", "6"},
    };
    private static final float FORTYSECOND_DP = 133f;

    /**
     * Opponents. Most factions are too weak to be a useful test; see the build doc. Ships are taken
     * in list order while they fit the target DP, so each list leads with the core of the fleet and
     * runs on far enough for 2x odds.
     */
    public enum Opponent {
        HEGEMONY("Hegemony XIV battlegroup", 1.5f, new String[][]{
                {"onslaught_xiv_Elite", "Onslaught", "40"},
                {"legion_xiv_Elite", "Legion", "40"},
                {"dominator_XIV_Elite", "Dominator", "25"},
                {"eagle_xiv_Elite", "Eagle", "20"},
                {"enforcer_XIV_Elite", "Enforcer", "9"},
                {"enforcer_XIV_Elite", "Enforcer", "9"},
                {"hammerhead_Elite", "Hammerhead", "10"},
                {"falcon_xiv_Escort", "Falcon", "14"},
                {"wolf_CS", "Wolf", "5"},
                {"brawler_Elite", "Brawler", "5"},
                {"lasher_CS", "Lasher", "4"},
                {"condor_Strike", "Condor", "10"},
                {"hammerhead_Elite", "Hammerhead", "10"},
                {"eagle_xiv_Elite", "Eagle", "20"},
                {"dominator_XIV_Elite", "Dominator", "25"},
                {"enforcer_XIV_Elite", "Enforcer", "9"},
                {"wolf_CS", "Wolf", "5"},
                {"lasher_CS", "Lasher", "4"},
        }),
        REMNANT("Remnant ordo", 1.625f, new String[][]{
                {"radiant_Assault", "Radiant", "60"},
                {"nova_Attack", "Nova", "40"},
                {"brilliant_Standard", "Brilliant", "25"},
                {"scintilla_Strike", "Scintilla", "12"},
                {"fulgent_Assault", "Fulgent", "10"},
                {"fulgent_Assault", "Fulgent", "10"},
                {"glimmer_Assault", "Glimmer", "5"},
                {"glimmer_Assault", "Glimmer", "5"},
                {"glimmer_Assault", "Glimmer", "5"},
                {"lumen_Standard", "Lumen", "4"},
                {"lumen_Standard", "Lumen", "4"},
                {"brilliant_Standard", "Brilliant", "25"},
                {"fulgent_Assault", "Fulgent", "10"},
                {"scintilla_Strike", "Scintilla", "12"},
                {"nova_Attack", "Nova", "40"},
                {"glimmer_Assault", "Glimmer", "5"},
                {"fulgent_Assault", "Fulgent", "10"},
                {"fulgent_Assault", "Fulgent", "10"},
                {"scintilla_Strike", "Scintilla", "12"},
                {"lumen_Standard", "Lumen", "4"},
        }),
        /** The same FortySecond fleet under vanilla AI. */
        MIRROR("FortySecond battlegroup, vanilla AI", 1f, FORTYSECOND);

        final String tagline;
        /** Opponent DP as a multiple of the FortySecond's. Tuned in Phase 0; runs at different odds
         * are recorded apart. */
        final float odds;
        final String[][] ships;

        Opponent(String tagline, float odds, String[][] ships) {
            this.tagline = tagline;
            this.odds = odds;
            this.ships = ships;
        }
    }

    private TestMissions() {
    }

    /** Both fleets, the briefing, a plain map and the battle log for one test mission. */
    public static void define(MissionDefinitionAPI api, String missionId, Opponent opponent) {
        api.initFleet(FleetSide.PLAYER, "OPP", FleetGoal.ATTACK, true);
        api.initFleet(FleetSide.ENEMY, "DDA", FleetGoal.ATTACK, true);
        float ours = add(api, FleetSide.ENEMY, "DDA ", FORTYSECOND, Float.MAX_VALUE);
        float theirs = add(api, FleetSide.PLAYER, "Opp ", opponent.ships, FORTYSECOND_DP * opponent.odds);
        api.setFleetTagline(FleetSide.ENEMY, String.format("FortySecond battlegroup (%.0f DP)", ours));
        api.setFleetTagline(FleetSide.PLAYER, String.format("%s (%.0f DP, %.2fx)", opponent.tagline, theirs, theirs / ours));

        // Runs at different odds must not be counted together.
        String runKey = String.format("%s@%.2f", missionId, opponent.odds);
        api.addBriefingItem("Test mission: turn on autopilot - your side is the opponent");
        api.addBriefingItem("Set battle size to 400 so both fleets deploy in full");
        for (String line : TestRunLedger.briefing(runKey)) api.addBriefingItem(line);

        api.initMap(-MAP_SIZE / 2f, MAP_SIZE / 2f, -MAP_SIZE / 2f, MAP_SIZE / 2f);
        api.addPlugin(new TestBattleLog(runKey));
    }

    /** Adds ships in list order while they fit {@code maxDp}, numbering repeated names. Returns the DP added. */
    private static float add(MissionDefinitionAPI api, FleetSide side, String prefix, String[][] ships, float maxDp) {
        Map<String, Integer> seen = new HashMap<>();
        float total = 0f;
        for (String[] ship : ships) {
            float dp = Float.parseFloat(ship[2]);
            if (total + dp > maxDp) continue;
            total += dp;
            int n = seen.merge(ship[1], 1, Integer::sum);
            api.addToFleet(side, ship[0], FleetMemberType.SHIP, prefix + ship[1] + (n > 1 ? " " + n : ""), false);
        }
        return total;
    }
}
