package levianeer.draconis.data.scripts.combat.commander;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;

/**
 * The Draconis commander (third combat AI, {@code .claude/systems/draconis-commander.md}). The floor
 * replaces the side's admiral with {@link CommanderAdmiral} (deploy everything, no orders, no retreats)
 * and leaves every ship to vanilla's ship AI; it won the test mirror 5 of 5, where vanilla's own admiral
 * won 1 of 5. The commander is the floor plus {@link TaskForces}, built and judged as a whole (section 11
 * of the build doc), with {@link CommanderOverlay} drawing its state.
 * <p>
 * Only ever touches ships of its own side, and never the player's ship.
 */
public class Commander extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(Commander.class);

    /**
     * Creating an assignment spends a command point and fails without one; the commander gives orders
     * all battle, so its side gets more than it can use.
     */
    private static final float COMMAND_POINTS = 1000f;

    private final int side;
    /** Null = the floor alone. */
    private final TaskForces taskForces;
    private boolean admiralSet;

    /** {@code additions} false = the floor alone. */
    public Commander(int side, boolean additions) {
        this.side = side;
        this.taskForces = additions ? new TaskForces(side) : null;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        if (!admiralSet) {
            admiralSet = true;
            CombatFleetManagerAPI fleet = engine.getFleetManager(side);
            fleet.setAdmiralAI(new CommanderAdmiral(fleet, side, taskForces));
            if (taskForces != null) {
                fleet.getTaskManager(false).getCommandPointsStat().modifyFlat("draconis_commander", COMMAND_POINTS);
                engine.addLayeredRenderingPlugin(new CommanderOverlay(taskForces));
            }
            log.info(String.format("DraconisCommander,start,side=%d,additions=%s", side, taskForces != null));
        }
        if (taskForces != null) taskForces.advance(engine, amount);
    }

    /** Only ships of our side, never fighters, stations or the player's ship. */
    static boolean controllable(CombatEngineAPI engine, ShipAPI ship, int side) {
        return ship.getOwner() == side && !ship.isFighter() && !ship.isStation() && !ship.isStationModule()
                && ship != engine.getPlayerShip() && ship.isAlive() && !ship.isHulk();
    }
}
