package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.XLII_SigmaOctantisWatchdog;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Handles the Pristine Nanoforge -> Sigma Octantis exchange for the Admiral August quest.
 * <p>
 * Usage in rules.csv:
 *   Conditions column:  XLII_NanoforgeExchange check
 *     -> returns true if the player's cargo contains a Pristine Nanoforge
 * <p>
 *   Script column:      XLII_NanoforgeExchange give
 *     -> removes the Pristine Nanoforge, adds the Octantis Uplink, shows cargo notifications
 */
@SuppressWarnings("unused")
public class XLII_NanoforgeExchange extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_NanoforgeExchange.class);

    private static final String NANOFORGE_ID = "pristine_nanoforge";
    private static final String REWARD_ID = "draconis_sigma_octantis";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.isEmpty() ? "check" : params.get(0).getString(memoryMap);
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        SpecialItemData nanoforge = new SpecialItemData(NANOFORGE_ID, null);

        if ("check".equals(action)) {
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                if (!stack.isSpecialStack()) continue;
                SpecialItemData item = stack.getSpecialDataIfSpecial();
                if (item != null && NANOFORGE_ID.equals(item.getId())) {
                    log.debug("Draconis: XLII_NanoforgeExchange check - Pristine Nanoforge found");
                    return true;
                }
            }
            log.debug("Draconis: XLII_NanoforgeExchange check - Pristine Nanoforge not found");
            return false;

        } else if ("give".equals(action)) {
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, nanoforge, 1f);
            cargo.addCommodity(REWARD_ID, 1);

            installPristineNanoforgeOnKori();

            // Start monitoring rep now that the core is in the player's hands
            Global.getSector().addScript(new XLII_SigmaOctantisWatchdog());
            log.info("Draconis: XLII_NanoforgeExchange - Sigma Octantis Watchdog registered");

            // Show cargo change notifications in the dialog panel
            dialog.getTextPanel().addPara("Transferred Pristine Nanoforge to the Alliance.",
                    Misc.getNegativeHighlightColor(), "Pristine Nanoforge");
            AddRemoveCommodity.addCommodityGainText(REWARD_ID, 1, dialog.getTextPanel());

            log.info("Draconis: XLII_NanoforgeExchange - Pristine Nanoforge exchanged for Sigma Octantis");
            return true;
        }

        return false;
    }

    /**
     * Upgrades Kori's nanoforge to Pristine. If Orbital Works already has any nanoforge
     * installed, it is replaced. If nothing is installed, the Pristine Nanoforge is
     * installed directly onto Orbital Works.
     */
    private void installPristineNanoforgeOnKori() {
        MarketAPI kori = Global.getSector().getEconomy().getMarket("kori_market");
        if (kori == null) {
            log.warn("Draconis: XLII_NanoforgeExchange - kori_market not found, skipping nanoforge upgrade");
            return;
        }

        SpecialItemData pristine = new SpecialItemData(NANOFORGE_ID, null);

        // First pass: replace any existing nanoforge on any industry
        for (Industry industry : kori.getIndustries()) {
            SpecialItemData current = industry.getSpecialItem();
            if (current != null && current.getId() != null && current.getId().contains("nanoforge")) {
                industry.setSpecialItem(pristine);
                log.info("Draconis: XLII_NanoforgeExchange - replaced " + current.getId()
                        + " with pristine_nanoforge on industry " + industry.getId());
                return;
            }
        }

        // Second pass: no nanoforge found - install on Orbital Works directly
        Industry orbitalWorks = kori.getIndustry(Industries.ORBITALWORKS);
        if (orbitalWorks != null) {
            orbitalWorks.setSpecialItem(pristine);
            log.info("Draconis: XLII_NanoforgeExchange - installed pristine_nanoforge on Orbital Works (no prior nanoforge found)");
        } else {
            log.warn("Draconis: XLII_NanoforgeExchange - Orbital Works not found on Kori, nanoforge not installed");
        }
    }
}