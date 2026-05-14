package levianeer.draconis.data.campaign.intel.fafnir;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

/**
 * Fires when the player first docks at Kori after gaining access via the TT Courier path.
 * An AIO operative acknowledges the transit log delivery before the market interaction proceeds.
 * <p>
 * Triggered by {@link XLII_CampaignPlugin#pickInteractionDialogPlugin} when
 * {@code $fafnirEntryPath = "tt_courier"} and {@code $fafnirKoriArrivalDone} is not set.
 * <p>
 * On "Proceed to dock": sets {@code $fafnirKoriArrivalDone}, dismisses, and re-opens the
 * normal Kori entity interaction via a transient next-frame script.
 * On "Leave": sets the flag and dismisses without re-opening (they did not dock this time).
 */
public class XLII_FafnirKoriArrivalDialogPlugin implements InteractionDialogPlugin {

    private static final String OPT_PROCEED = "proceed";
    private static final String OPT_LEAVE   = "leave";

    private InteractionDialogAPI dialog;
    private final SectorEntityToken target;

    public XLII_FafnirKoriArrivalDialogPlugin(SectorEntityToken target) {
        this.target = target;
    }

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        showAcknowledgement();
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        String key = (String) optionData;
        Global.getSector().getMemoryWithoutUpdate()
                .set(FafnirAccessStrings.MEM_KORI_ARRIVAL_DONE, true);

        // Complete the mission and pay credits unconditionally: the operative has acknowledged,
        // so the delivery is done regardless of whether the player docks this visit.
        FafnirAccessMissionIntel intel = FafnirAccessMissionIntel.get();
        if (intel != null) intel.complete();

        dialog.dismiss();

        if (OPT_PROCEED.equals(key)) {
            openMarketNextFrame();
        }
    }

    @Override
    public void optionMousedOver(String text, Object optionData) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void backFromEngagement(EngagementResultAPI result) {}

    @Override
    public Object getContext() { return null; }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showAcknowledgement() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();

        text.addPara(FafnirAccessStrings.KORI_ARRIVAL_SCENE);
        text.addPara(FafnirAccessStrings.KORI_ARRIVAL_DIALOG_PARA1);
        text.addPara(FafnirAccessStrings.KORI_ARRIVAL_DIALOG_PARA2);
        text.addPara(FafnirAccessStrings.KORI_ARRIVAL_DIALOG_PARA3);

        options.addOption("Proceed to dock.", OPT_PROCEED);
        options.addOption("Leave.", OPT_LEAVE);
    }

    /**
     * Re-opens the normal interaction with Kori on the next frame, after the
     * current dialog has fully dismissed. Uses the same guard pattern as
     * {@code Wait.java}: only fires when {@code isShowingDialog()} is false.
     */
    private void openMarketNextFrame() {
        final SectorEntityToken kori = target;
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override
            public boolean isDone() { return done; }

            @Override
            public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(kori);
                }
            }
        });
    }
}
