package levianeer.draconis.data.scripts.combat.commander;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AdmiralAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Replaces the side's vanilla admiral, which would otherwise keep issuing orders and retreats over
 * the commander's ships. Phase 2 stand-in: deploys everything in reserve at once, in a line along our
 * own edge facing the enemy. The engine calls nothing on an admiral but {@code advance}, and nothing
 * is deployed before a combat plugin's first frame (probe 3), so this owns deployment from the start.
 */
final class CommanderAdmiral implements AdmiralAIPlugin {

    private static final float EDGE_INSET = 1500f;
    private static final float SPACING = 800f;

    private final CombatFleetManagerAPI fleet;
    private final int side;

    CommanderAdmiral(CombatFleetManagerAPI fleet, int side) {
        this.fleet = fleet;
        this.side = side;
    }

    @Override
    public void preCombat() {
    }

    @Override
    public void advance(float amount) {
        int count = 0;
        for (FleetMemberAPI member : fleet.getReservesCopy()) {
            if (!member.isFighterWing()) count++;
        }
        if (count == 0) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        // Owner 1 deploys along the top edge, owner 0 along the bottom, as the engine does.
        float sign = side == 1 ? 1f : -1f;
        float y = sign * (engine.getMapHeight() / 2f - EDGE_INSET);
        float facing = side == 1 ? 270f : 90f;
        float x = -SPACING * (count - 1) / 2f;
        for (FleetMemberAPI member : fleet.getReservesCopy()) {
            if (member.isFighterWing()) continue;
            fleet.spawnFleetMember(member, new Vector2f(x, y), facing, 0f);
            x += SPACING;
        }
    }
}
