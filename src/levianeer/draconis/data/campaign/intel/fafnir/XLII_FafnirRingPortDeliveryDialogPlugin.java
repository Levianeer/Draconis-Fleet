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
 * Post-entry acknowledgement for the Ring-Port contractor path. Fires on the player's
 * first dock at Ring-Port Station after entering Fafnir via the pirate jump point.
 * A dock contact confirms receipt of the weapons shipment.
 * <p>
 * Triggered by {@link XLII_CampaignPlugin#pickInteractionDialogPlugin} when
 * {@code $fafnirEntryPath = "ring_port"} and {@code $fafnirRingPortDeliveryDone} is not set.
 * <p>
 * On "Complete delivery": sets {@code $fafnirRingPortDeliveryDone}, then re-opens the
 * normal Ring-Port Station interaction via a transient next-frame script.
 * On "Leave": no flag change; dialog fires again on the next dock attempt.
 */
public class XLII_FafnirRingPortDeliveryDialogPlugin implements InteractionDialogPlugin {

    private static final String OPT_DELIVER = "deliver";
    private static final String OPT_LEAVE   = "leave";

    private InteractionDialogAPI dialog;
    private final SectorEntityToken target;

    public XLII_FafnirRingPortDeliveryDialogPlugin(SectorEntityToken target) {
        this.target = target;
    }

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        showDelivery();
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        String key = (String) optionData;
        dialog.dismiss();

        if (OPT_DELIVER.equals(key)) {
            Global.getSector().getMemoryWithoutUpdate()
                    .set(FafnirAccessStrings.MEM_RP_DELIVERY_DONE, true);
            FafnirAccessMissionIntel intel = FafnirAccessMissionIntel.get();
            if (intel != null) intel.complete();
            openMarketNextFrame();
        }
        // OPT_LEAVE: no flag changes; dialog fires again on next dock attempt
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

    private void showDelivery() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();

        text.addPara(FafnirAccessStrings.RP_DELIVERY_DIALOG_PARA1);
        text.addPara(FafnirAccessStrings.RP_DELIVERY_DIALOG_PARA2);
        text.addPara(FafnirAccessStrings.RP_DELIVERY_DIALOG_PARA3);
        text.addPara(FafnirAccessStrings.RP_DELIVERY_DIALOG_PARA4);

        options.addOption("Complete the delivery and dock.", OPT_DELIVER);
        options.addOption("Leave.", OPT_LEAVE);
    }

    /**
     * Re-opens the normal interaction with Ring-Port Station on the next frame,
     * after the current dialog has fully dismissed. Uses the same guard pattern
     * as {@code Wait.java}: only fires when {@code isShowingDialog()} is false.
     */
    private void openMarketNextFrame() {
        final SectorEntityToken station = target;
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
                    Global.getSector().getCampaignUI().showInteractionDialog(station);
                }
            }
        });
    }
}