package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class fsdf_Jammer_SuiteStats extends BaseShipSystemScript {

    public static final float EW_BONUS = 5f;
    private float textTimer = 0f;
    private boolean textDisplayed = false;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

        stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(id, EW_BONUS);

        if (state == State.ACTIVE) {
            if (!textDisplayed) {
                // Convert the Color to java.awt.Color
                java.awt.Color textColor = new java.awt.Color(200, 200, 200);
                
                // Adjust the parameters as needed
                ((ShipAPI) stats.getEntity()).getFluxTracker().showOverloadFloatyIfNeeded("Active Jamming!", textColor, 1f, true);
                textDisplayed = true;
            }

            textTimer += Global.getCombatEngine().getElapsedInLastFrame();
            if (textTimer >= 6) {  // Adjust the duration as needed
                textDisplayed = false;
                textTimer = 0f;
            }
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Jamming Systems!", false);
        }
        return null;
    }
}