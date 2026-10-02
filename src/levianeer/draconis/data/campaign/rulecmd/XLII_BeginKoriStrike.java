package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_KoriStrike;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Action command: activates the Burn the Machine strike on Kori and immediately
 * opens the XLII_KoriStrike dialog on Kori's own entity.
 * <p>
 * Mirrors XLII_BeginAssault - sets $XLII_burnTheMachineActive, dismisses the
 * current dialog, then defers opening the strike plugin by one frame.
 * <p>
 * Usage in rules.csv actions column:
 *   XLII_BeginKoriStrike
 */
@SuppressWarnings("unused")
public class XLII_BeginKoriStrike extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_BeginKoriStrike.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_burnTheMachineActive", true);

        final SectorEntityToken kori =
                Global.getSector().getEntityById(XLII_KoriStrike.PLANET_ENTITY_ID);
        if (kori == null) {
            log.error("Draconis: XLII_BeginKoriStrike - Kori entity not found");
            return false;
        }

        dialog.dismiss();

        final XLII_KoriStrike strike = new XLII_KoriStrike();
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(strike, kori);
                }
            }
        });

        return true;
    }
}
