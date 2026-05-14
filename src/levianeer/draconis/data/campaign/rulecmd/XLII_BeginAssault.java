package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_RingPortAssault;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Action command: activates the Blind Eye assault mission and immediately opens
 * the XLII_RingPortAssault dialog on the Ring-Port station.
 * <p>
 * Sets $XLII_blindEyeMissionActive, dismisses the current dialog, then defers
 * opening the assault plugin by one frame (the same pattern used in
 * XLII_RingPortAssault.leaveToNormalInteraction and showPostAssaultDialog).
 * <p>
 * Usage in rules.csv actions column:
 *   XLII_BeginAssault
 */
@SuppressWarnings("unused")
public class XLII_BeginAssault extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_BeginAssault.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_blindEyeMissionActive", true);

        final SectorEntityToken station =
                Global.getSector().getEntityById(XLII_RingPortAssault.STATION_ENTITY_ID);
        if (station == null) {
            log.error("Draconis: XLII_BeginAssault - station entity not found");
            return false;
        }

        dialog.dismiss();

        final XLII_RingPortAssault assault = new XLII_RingPortAssault();
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(assault, station);
                }
            }
        });

        return true;
    }
}
