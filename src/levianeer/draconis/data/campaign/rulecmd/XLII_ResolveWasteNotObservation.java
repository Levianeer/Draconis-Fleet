package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Rule command: resolves the stealth/observe branch of the Waste Not installation scene. Grants
 * a flat 1 AuxCog Core (author direction - lower and un-randomized compared to the forced-entry
 * branch's 2-4 roll, see {@code XLII_ResolveWasteNotInstallation}, since running silent recovers
 * readings rather than the boarding party's physical haul), announces the gain the same way via
 * {@code AddRemoveCommodity.addCommodityGainText}, then sets the outcome/resolved flags
 * {@code WasteNotMission} watches.
 * <p>
 * Fired from the installation scene's observe row (rules.csv, {@code waste_not_installation_observe}).
 */
public class XLII_ResolveWasteNotObservation extends BaseCommandPlugin {

    private static final String ITEM_ID = "XLII_ai_core_bio";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int count = 1;
        cargo.addCommodity(ITEM_ID, count);

        if (dialog != null) {
            AddRemoveCommodity.addCommodityGainText(ITEM_ID, count, dialog.getTextPanel());
        }

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$XLII_wasteNotOutcome", "observed");
        mem.set("$XLII_wasteNotInstallationResolved", true);

        return true;
    }
}
