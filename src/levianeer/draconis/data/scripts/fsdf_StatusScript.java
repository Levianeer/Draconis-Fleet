package levianeer.draconis.data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

public class fsdf_StatusScript implements EveryFrameCombatPlugin {

    private final ShipAPI ship;

    public fsdf_StatusScript(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void advance(float amount, List events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null || !ship.isAlive()) {
            assert engine != null;
            engine.removePlugin(this);
            return;
        }

        // Only show for player ship
        if (ship != engine.getPlayerShip()) return;

        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        float scaledFluxLevel = Math.min(fluxLevel / 0.9f, 1f);

        float minDamageBonus = 0f; // %
        float maxDamageBonus = 20f; // %

        float minDamageReduction = 10f; // %
        float maxDamageReduction = 30f; // %

        float damageBonus = minDamageBonus + (maxDamageBonus - minDamageBonus) * scaledFluxLevel;
        float damageReduction = minDamageReduction + (maxDamageReduction - minDamageReduction) * scaledFluxLevel;

        String text = String.format("Offense: +%.0f%% | Defense: +%.0f%%", damageBonus, damageReduction);

        engine.maintainStatusForPlayerShip(
                "fsdf_DynamicBuffStat",
                "graphics/icons/hullsys/damper_field.png",
                "Adaptive Flux Matrix",
                text,
                false
        );
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        // do nothing
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        // do nothing
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        // do nothing
    }

    @Deprecated
    public void init(CombatEngineAPI engine) {
        // do nothing
    }
}