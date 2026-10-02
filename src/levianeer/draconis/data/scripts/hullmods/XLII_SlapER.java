package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.XLII_MistCloudConstants;
import levianeer.draconis.data.scripts.XLII_MistCloudsPlugin;
import org.apache.log4j.Logger;

import java.awt.Color;

public class XLII_SlapER extends BaseHullMod {

    private static final Logger log = Global.getLogger(XLII_SlapER.class);

    private static final String FORTYSECOND_ID = "XLII_fortysecond";

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getVariant().hasHullMod(FORTYSECOND_ID);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (!ship.getVariant().hasHullMod(FORTYSECOND_ID)) {
            return "Requires Forty Second Battlegroup";
        }
        return super.getUnapplicableReason(ship);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // Register Mist Clouds plugin once per combat per side, but only once the ship is
        // actually on the field. In mirror matches both sides need their own plugin instance.
        String pluginKey = "XLII_MIST_CLOUDS_PLUGIN_" + ship.getOwner();
        if (!engine.getCustomData().containsKey(pluginKey) && engine.isEntityInPlay(ship)) {
            registerMistCloudsPlugin(engine, ship.getOwner(), pluginKey);
        }
    }

    private void registerMistCloudsPlugin(CombatEngineAPI engine, int ownerSide, String pluginKey) {
        try {
            XLII_MistCloudsPlugin plugin = new XLII_MistCloudsPlugin(ownerSide);
            engine.addPlugin(plugin);
            engine.getCustomData().put(pluginKey, plugin);
            log.info("Draconis: Mist Clouds plugin registered for side " + ownerSide);
        } catch (Exception e) {
            log.error("Draconis: Failed to register Mist Clouds plugin: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Deploys several cruise missiles that strike from outside of the combat zone - spreading nanomist clouds in a %s radius, lasting %s-%s seconds.",
                opad, h,
                (int) XLII_MistCloudConstants.CLOUD_RADIUS + "",
                (int) XLII_MistCloudConstants.CLOUD_MIN_LIFETIME + "",
                (int) XLII_MistCloudConstants.CLOUD_MAX_LIFETIME + "");

        tooltip.addPara("Each deployed ship equipped with this hull mod adds %s/%s/%s/%s missiles, depending on hull size, to a finite supply deployed %s at a time until exhausted.",
                opad, h,
                "1", "2", "3", "4", "2");

        tooltip.addPara("Allies inside these clouds recover %s hull/s; enemies take %s hull damage/s. Each cloud has a finite %s-point hull pool shared between healing and damage. Phased ships are immune while in P-space.",
                opad, h,
                "+" + Math.round(XLII_MistCloudConstants.HEAL_PERCENT_PER_SEC * 100f) + "%",
                Math.round(XLII_MistCloudConstants.DOT_PERCENT_PER_SEC * 100f) + "%",
                (int) XLII_MistCloudConstants.CLOUD_POOL_HP + "");
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return XLII_MistCloudConstants.SMOD_MISSILE_BONUS + "";
        if (index == 1) return (int) XLII_MistCloudConstants.SMOD_POOL_HP_BONUS + "";
        return null;
    }
}
