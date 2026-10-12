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
import java.util.Random;

/**
 * Rule command: resolves the forced-entry branch of the Waste Not installation fight. Grants
 * 1-3 Auxiliary Cognition Units (floor 1, flat random roll per author direction - not worth a
 * dedicated tracker for finer-grained outcome tiers, unlike the later Kori raid's loss-ratio
 * buckets), announces the gain via {@code AddRemoveCommodity.addCommodityGainText} (the same
 * "Gained Nx <item>" cargo-toast helper vanilla's own commodity-granting rules use - added after
 * a live-test report that the item was appearing in cargo with no on-screen acknowledgment at
 * all), then sets the outcome/resolved flags {@code WasteNotMission} watches.
 * <p>
 * Granted as a plain commodity ({@code cargo.addCommodity}), not a special item - AI cores in
 * Starsector are commodities, and {@code AICoreOfficerPlugin}/{@code AICoreAdminPlugin} (see
 * {@code XLII_LiteCoreOfficerPlugin}) are resolved by commodity id, not special-item id. An
 * earlier pass registered this in {@code special_items.csv} instead, which would have made the
 * item visually/narratively correct but mechanically inert for the "install as officer" purpose
 * the item was built for - caught and fixed while wiring the officer plugin, see
 * {@code commodities.csv}'s own {@code XLII_ai_core_bio} row.
 * <p>
 * Fired from the boarding scene's closing row (rules.csv, {@code XLII_WasteNotInstallationBoarding}).
 */
public class XLII_ResolveWasteNotInstallation extends BaseCommandPlugin {

    private static final String ITEM_ID = "XLII_ai_core_bio";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int count = 1 + new Random().nextInt(3); // 1-3, floor 1
        cargo.addCommodity(ITEM_ID, count);

        if (dialog != null) {
            AddRemoveCommodity.addCommodityGainText(ITEM_ID, count, dialog.getTextPanel());
        }

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$XLII_wasteNotOutcome", "forced");
        mem.set("$XLII_wasteNotInstallationResolved", true);

        return true;
    }
}
