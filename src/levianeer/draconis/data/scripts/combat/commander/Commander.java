package levianeer.draconis.data.scripts.combat.commander;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;

/**
 * The Draconis commander (third combat AI, {@code .claude/systems/draconis-commander.md}), built as
 * the floor plus additions. The floor replaces the side's admiral with {@link CommanderAdmiral}
 * (deploy everything, no orders, no retreats) and leaves every ship to vanilla's ship AI; it won the
 * test mirror 5 of 5, where vanilla's own admiral won 1 of 5. Each addition must beat the floor in the
 * test missions or it is removed. Current addition: {@link JumpOut}.
 * <p>
 * Only ever touches ships of its own side, and never the player's ship.
 */
public class Commander extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(Commander.class);

    private final int side;
    /** Null = the floor alone. */
    private final JumpOut jumpOut;
    private boolean admiralSet;
    private boolean reported;

    /** {@code additions} false = the floor alone. */
    public Commander(int side, boolean additions) {
        this.side = side;
        this.jumpOut = additions ? new JumpOut(side) : null;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        if (!admiralSet) {
            admiralSet = true;
            engine.getFleetManager(side).setAdmiralAI(new CommanderAdmiral(engine.getFleetManager(side), side));
            log.info(String.format("DraconisCommander,start,side=%d,additions=%s", side, jumpOut != null));
        }
        if (jumpOut != null) jumpOut.advance(engine, amount);

        if (!reported && engine.isCombatOver()) {
            reported = true;
            if (jumpOut != null) jumpOut.report(engine, engine.getTotalElapsedTime(false));
        }
    }

    /** Only ships of our side, never fighters, stations or the player's ship. */
    static boolean controllable(CombatEngineAPI engine, ShipAPI ship, int side) {
        return ship.getOwner() == side && !ship.isFighter() && !ship.isStation() && !ship.isStationModule()
                && ship != engine.getPlayerShip() && ship.isAlive() && !ship.isHulk();
    }
}
