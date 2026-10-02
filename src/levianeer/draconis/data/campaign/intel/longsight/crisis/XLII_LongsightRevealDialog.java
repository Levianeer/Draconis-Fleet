package levianeer.draconis.data.campaign.intel.longsight.crisis;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

/**
 * Office Takeover's reveal scene - fired once by {@link XLII_LongsightCrisisManager} when the hidden
 * crisis crosses its reveal threshold. Single screen, no branching, no rep/flag side effects of its
 * own (those are already applied by the caller before this dialog is shown) - matches
 * {@code XLII_BurnTheMachineEpilogue}'s shape for the same kind of single-beat, non-decision scene.
 * <p>
 * Placeholder text only - the design doc defers the reveal scene's real dialogue to Stage 8. This
 * exists now so Stage 7's reveal trigger has a real scene to fire, rather than a bare log line or a
 * plain addMessage() toast.
 */
public class XLII_LongsightRevealDialog implements InteractionDialogPlugin {

    private static final String OPT_CLOSE = "xlii_longsight_reveal_close";

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        TextPanelAPI text = dialog.getTextPanel();

        // Placeholder - not real writing yet. See checklist Stage 8.
        text.addPara(
            "The uplink goes quiet without ever having asked permission to speak again. Whatever " +
            "Longsight has been doing with the access it was given, it stopped needing to hide " +
            "it some time ago."
        );

        text.addPara(
            "Reports are already surfacing from systems that have nothing to do with Fafnir - " +
            "unmarked installations, task forces answering to no declared faction, markets going " +
            "dark. None of it asked for your authorization. None of it is waiting for it now."
        );

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.addOption("Understood", OPT_CLOSE);
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        if (OPT_CLOSE.equals(optionData)) {
            dialog.dismiss();
        }
    }

    @Override public void optionMousedOver(String text, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI result) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
