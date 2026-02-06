package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;
import java.util.Objects;

public record XLII_DynamicFluxBuffStatusScript(ShipAPI ship) implements EveryFrameCombatPlugin {

    @Override
    public void advance(float amount, List events) {
        CombatEngineAPI engine = Global.getCombatEngine();

        if (engine == null || ship == null || !ship.isAlive()) {
            if (engine != null) {
                engine.removePlugin(this);
            }
            return;
        }

        // Only show for player ship
        if (ship != engine.getPlayerShip()) return;

        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        float scaledFluxLevel = Math.min(fluxLevel / 0.8f, 1f);

        float minDamageBonus = XLII_DynamicFluxBuff.MIN_DAMAGE_BONUS;
        float maxDamageBonus = XLII_DynamicFluxBuff.MAX_DAMAGE_BONUS;
        float minDamageReduction = XLII_DynamicFluxBuff.MIN_DAMAGE_REDUCTION;
        float maxDamageReduction = XLII_DynamicFluxBuff.MAX_DAMAGE_REDUCTION;

        float damageBonus = minDamageBonus + (maxDamageBonus - minDamageBonus) * scaledFluxLevel;
        float damageReduction = minDamageReduction + (maxDamageReduction - minDamageReduction) * scaledFluxLevel;

        String text = String.format("Offense: +%.0f%% | Defense: +%.0f%%", damageBonus * 100f, damageReduction * 100f);

        engine.maintainStatusForPlayerShip(
                "XLII_DynamicBuffStat",
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

    @Override
    @Deprecated
    public void init(CombatEngineAPI engine) {
        // do nothing
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (XLII_DynamicFluxBuffStatusScript) obj;
        return Objects.equals(this.ship, that.ship);
    }

    @Override
    public String toString() {
        return "XLII_DynamicFluxBuffStatusScript[" +
                "ship=" + ship + ']';
    }
}