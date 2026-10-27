package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.BattleObjectives;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfareScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import levianeer.draconis.DraconisSettings;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recomputes each side's ECM advantage the way ElectronicWarfareScript does (it doesn't expose the
 * value) and pushes every registered Signals skill's modifiers onto both fleets.
 */
public class SignalsCombatManager extends BaseEveryFrameCombatPlugin {

    private static final String ENGINE_KEY = "XLII_signals_manager";
    private static final String STATUS_KEY = "XLII_signals_status";
    private static final String DEBUG_SETTING = "draconisSignalsDebug";
    private static final Logger log = Global.getLogger(SignalsCombatManager.class);

    private final IntervalUtil interval = new IntervalUtil(0.5f, 0.5f);
    private final Map<ShipAPI, Set<SignalsSkill>> pending = new HashMap<>();
    private final List<Set<SignalsSkill>> sideSkills = new ArrayList<>();
    private final float[] advantage = new float[2];
    private final float[] maximum = new float[2];
    private final boolean debug = DraconisSettings.getBoolean(DEBUG_SETTING, false);
    private final Map<ShipAPI, String> debugApplied = new HashMap<>();

    private SignalsCombatManager() {
        sideSkills.add(new HashSet<SignalsSkill>());
        sideSkills.add(new HashSet<SignalsSkill>());
    }

    public static SignalsCombatManager getOrCreate(CombatEngineAPI engine) {
        SignalsCombatManager mgr = (SignalsCombatManager) engine.getCustomData().get(ENGINE_KEY);
        if (mgr == null) {
            mgr = new SignalsCombatManager();
            engine.getCustomData().put(ENGINE_KEY, mgr);
            engine.addPlugin(mgr);
        }
        return mgr;
    }

    // Owner is resolved later rather than at registration, in case it isn't final when SiC fires
    // applyEffectsAfterShipCreation.
    public void register(ShipAPI ship, SignalsSkill skill) {
        Set<SignalsSkill> skills = pending.get(ship);
        if (skills == null) {
            skills = new HashSet<>();
            pending.put(ship, skills);
        }
        skills.add(skill);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        resolvePending();
        maintainStatus(engine);
        if (debug) maintainDebugStatus(engine);

        if (engine.isPaused()) return;
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        computeAdvantage(engine);
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isHulk()) continue;
            int side = ship.getOwner();
            if (side != 0 && side != 1) continue;
            applyTo(ship, side, 1 - side);
        }
    }

    private void resolvePending() {
        Iterator<Map.Entry<ShipAPI, Set<SignalsSkill>>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ShipAPI, Set<SignalsSkill>> entry = it.next();
            int side = entry.getKey().getOwner();
            if (side != 0 && side != 1) continue;
            sideSkills.get(side).addAll(entry.getValue());
            if (debug) log.info("Signals: " + entry.getKey().getName() + " registered on side " + side + " " + names(entry.getValue()));
            it.remove();
        }
    }

    private void applyTo(ShipAPI ship, int side, int enemy) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        StringBuilder applied = debug ? new StringBuilder() : null;
        for (SignalsSkill skill : sideSkills.get(side)) {
            if (skill.affectsEnemy()) continue;
            skill.applyAdvantage(stats, advantage[side], maximum[side]);
            if (applied != null) appendApplied(applied, "+", skill, advantage[side]);
        }
        float againstShip = penaltyAfterEccm(stats, advantage[enemy], maximum[enemy]);
        for (SignalsSkill skill : sideSkills.get(enemy)) {
            if (!skill.affectsEnemy()) continue;
            skill.applyAdvantage(stats, againstShip, maximum[enemy]);
            if (applied != null) appendApplied(applied, "-", skill, againstShip);
        }
        if (applied != null) debugApplied.put(ship, applied.length() == 0 ? "none" : applied.toString());
    }

    // Mirrors ElectronicWarfareScript.applyPenalty, so ECCM resists Signals debuffs exactly as it
    // resists the vanilla range penalty.
    private static float penaltyAfterEccm(MutableShipStatsAPI stats, float penalty, float maxPenalty) {
        float curr = penalty * stats.getDynamic().getValue(Stats.ELECTRONIC_WARFARE_PENALTY_MULT);
        curr = stats.getDynamic().getValue(Stats.ELECTRONIC_WARFARE_PENALTY_MOD, curr);
        float maxMod = penalty * stats.getDynamic().getValue(Stats.ELECTRONIC_WARFARE_PENALTY_MAX_FOR_SHIP_MOD, 0f);
        return Math.max(0f, Math.min(curr, maxPenalty + maxMod));
    }

    private void computeAdvantage(CombatEngineAPI engine) {
        int[] total = new int[2];
        for (int side = 0; side < 2; side++) {
            CombatFleetManagerAPI manager = engine.getFleetManager(side);
            total[side] = manager == null ? 0 : (int) rating(engine, manager);
            maximum[side] = manager == null ? ElectronicWarfareScript.BASE_MAXIMUM : (int) maximum(manager);
        }
        for (int side = 0; side < 2; side++) {
            int ours = total[side];
            int theirs = total[1 - side];
            float penalty = (int) Math.min(ours, maximum[side]);
            if (theirs > 0 && penalty > 0) penalty *= (float) ours / (ours + theirs);
            advantage[side] = Math.round(penalty);
        }
    }

    private static float rating(CombatEngineAPI engine, CombatFleetManagerAPI manager) {
        float total = 0f;
        for (DeployedFleetMemberAPI member : manager.getDeployedCopyDFM()) {
            if (member.isFighterWing() || member.isStationModule()) continue;
            if (member.getShip() == null) continue;
            total += member.getShip().getMutableStats().getDynamic().getValue(Stats.ELECTRONIC_WARFARE_FLAT, 0f);
        }
        for (BattleObjectiveAPI obj : engine.getObjectives()) {
            if (obj.getOwner() == manager.getOwner() && BattleObjectives.SENSOR_JAMMER.equals(obj.getType())) {
                total += ElectronicWarfareScript.PER_JAMMER;
            }
        }
        return total;
    }

    private static float maximum(CombatFleetManagerAPI manager) {
        float max = 0f;
        for (PersonAPI commander : manager.getAllFleetCommanders()) {
            max = Math.max(max, ElectronicWarfareScript.BASE_MAXIMUM
                    + commander.getStats().getDynamic().getValue(Stats.ELECTRONIC_WARFARE_MAX, 0f));
        }
        return max;
    }

    private void maintainStatus(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) return;
        int side = player.getOwner();
        if (side != 0 && side != 1) return;
        if (sideSkills.get(side).isEmpty()) return;

        String text = (int) advantage[side] + "% ECM advantage";
        if (advantage[side] > 0 && advantage[side] >= maximum[side]) text += " (max)";
        String icon = Global.getSettings().getSpriteName("ui", "icon_tactical_electronic_warfare");
        engine.maintainStatusForPlayerShip(STATUS_KEY, icon, "Signals", text, false);
    }

    // "+" marks a buff from the ship's own side, "-" a debuff from the opposing side; the number is
    // the advantage the skill scaled with (after ECCM for debuffs).
    private void maintainDebugStatus(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) return;
        int side = player.getOwner();
        if (side != 0 && side != 1) return;
        int enemy = 1 - side;
        String icon = Global.getSettings().getSpriteName("ui", "icon_tactical_electronic_warfare");

        engine.maintainStatusForPlayerShip(STATUS_KEY + "_dbg_sides", icon, "Signals debug: sides",
                "you " + sideSummary(side) + " | enemy " + sideSummary(enemy), false);
        engine.maintainStatusForPlayerShip(STATUS_KEY + "_dbg_self", icon, "Signals debug: your ship",
                appliedTo(player), false);
        ShipAPI target = player.getShipTarget();
        if (target != null) {
            engine.maintainStatusForPlayerShip(STATUS_KEY + "_dbg_target", icon,
                    "Signals debug: " + target.getHullSpec().getHullName() + " (side " + target.getOwner() + ")",
                    appliedTo(target), target.getOwner() != side);
        }
    }

    private String sideSummary(int side) {
        return "A " + (int) advantage[side] + "/" + (int) maximum[side] + ", " + sideSkills.get(side).size() + " skills";
    }

    private String appliedTo(ShipAPI ship) {
        if (ship.isFighter()) return "fighter, skipped like vanilla ECM";
        String applied = debugApplied.get(ship);
        return applied == null ? "not updated yet" : applied;
    }

    private static void appendApplied(StringBuilder out, String sign, SignalsSkill skill, float value) {
        if (out.length() > 0) out.append(", ");
        out.append(sign).append(skill.getName()).append(' ').append(Math.round(value * 10f) / 10f);
    }

    private static String names(Set<SignalsSkill> skills) {
        List<String> names = new ArrayList<>();
        for (SignalsSkill skill : skills) names.add(skill.getName());
        return names.toString();
    }
}
