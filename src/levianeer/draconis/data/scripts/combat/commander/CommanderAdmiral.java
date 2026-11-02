package levianeer.draconis.data.scripts.combat.commander;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AdmiralAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Replaces the side's vanilla admiral, which would otherwise keep issuing orders and retreats over
 * the commander's ships. Phase 2 stand-in: deploys everything in reserve at once, in a line along our
 * own edge facing the enemy, group by group when there are task forces. The engine calls nothing on an admiral but {@code advance}, and nothing
 * is deployed before a combat plugin's first frame (probe 3), so this owns deployment from the start.
 */
final class CommanderAdmiral implements AdmiralAIPlugin {

    private static final float EDGE_INSET = 1500f;
    private static final float SPACING = 800f;

    private final CombatFleetManagerAPI fleet;
    private final int side;
    /** Null = the floor: deploy in fleet order. */
    private final TaskForces taskForces;

    CommanderAdmiral(CombatFleetManagerAPI fleet, int side, TaskForces taskForces) {
        this.fleet = fleet;
        this.side = side;
        this.taskForces = taskForces;
    }

    @Override
    public void preCombat() {
    }

    @Override
    public void advance(float amount) {
        List<FleetMemberAPI> ships = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getReservesCopy()) {
            if (!member.isFighterWing()) ships.add(member);
        }
        if (ships.isEmpty()) return;
        List<List<FleetMemberAPI>> order = taskForces == null ? Collections.singletonList(ships) : taskForces.plan(ships);
        CombatEngineAPI engine = Global.getCombatEngine();
        // Owner 1 deploys along the top edge, owner 0 along the bottom, as the engine does.
        float sign = side == 1 ? 1f : -1f;
        float y = sign * (engine.getMapHeight() / 2f - EDGE_INSET);
        float facing = side == 1 ? 270f : 90f;
        // One empty slot between groups.
        int slots = ships.size() + order.size() - 1;
        float x = -SPACING * (slots - 1) / 2f;
        for (List<FleetMemberAPI> group : order) {
            for (FleetMemberAPI member : group) {
                fleet.spawnFleetMember(member, new Vector2f(x, y), facing, 0f);
                x += SPACING;
            }
            x += SPACING;
        }
    }
}
