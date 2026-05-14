package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class XLII_FluxReactiveArmor extends BaseHullMod {

    private static final float HULL_DAMAGE_REDUCTION = 0.5f;
    private static final float FLUX_CONVERSION = 0.25f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.addListener(new FluxReactiveArmorListener());
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;

        tooltip.addPara(
                "Integrated flux-reactive plating that absorbs and redirects kinetic energy from hull impacts into the ship's flux grid.",
                opad);

        tooltip.addPara(
                "Reduces all hull damage taken by %s. In exchange %s of the original damage is converted into soft flux.",
                opad, Misc.getHighlightColor(),
                Math.round(HULL_DAMAGE_REDUCTION * 100f) + "%",
                Math.round(FLUX_CONVERSION * 100f) + "%");

        tooltip.addPara(
                "Does not function while the ship is overloaded.",
                opad, Misc.getNegativeHighlightColor(), "overloaded");
    }

    private static class FluxReactiveArmorListener implements HullDamageAboutToBeTakenListener {

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            if (ship.getFluxTracker().isOverloaded()) {
                return false;
            }

            float reducedDamage = damageAmount * (1f - HULL_DAMAGE_REDUCTION);

            if (reducedDamage >= ship.getHitpoints()) {
                return false;
            }

            ship.setHitpoints(ship.getHitpoints() - reducedDamage);
            ship.getFluxTracker().increaseFlux(damageAmount * FLUX_CONVERSION, false);

            return true;
        }
    }
}