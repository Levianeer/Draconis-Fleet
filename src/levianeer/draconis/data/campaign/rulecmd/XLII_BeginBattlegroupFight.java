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
 * Action command: fired by rules.csv's XLII_interception_begin_fight, which
 * XLII_burn_elias_stays/_leaves's own "Understood" option leads into once Elias's fork resolves on
 * the Commit path of the interception (see rules.csv, "Burn the Machine" section). Dismisses the
 * current dialog and opens a fresh XLII_KoriStrike at FLEET_FIGHT one frame later - same
 * deferred-reopen pattern as XLII_BeginKoriStrike.
 * <p>
 * Usage in rules.csv actions column:
 *   XLII_BeginBattlegroupFight
 */
@SuppressWarnings("unused")
public class XLII_BeginBattlegroupFight extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_BeginBattlegroupFight.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        final SectorEntityToken kori =
                Global.getSector().getEntityById(XLII_KoriStrike.PLANET_ENTITY_ID);
        if (kori == null) {
            log.error("Draconis: XLII_BeginBattlegroupFight - Kori entity not found");
            return false;
        }

        dialog.dismiss();

        final XLII_KoriStrike strike = XLII_KoriStrike.forFleetFight();
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
