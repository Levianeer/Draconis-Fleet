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
 */
public class XLII_LongsightRevealDialog implements InteractionDialogPlugin {

    private static final String OPT_CLOSE = "xlii_longsight_reveal_close";

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        TextPanelAPI text = dialog.getTextPanel();

        text.addPara(
        "Scattered reports are surfacing from systems that have nothing to do with Fafnir - " +
            "unmarked installations, automated task forces answering to no declared faction, entire worlds going " +
            "dark. Entire colonies are swearing loyalty to the Draconis Alliance."
        );

        text.addPara(
            "It is clear that your failure to destroy what was buried under Kori has set in motion something " +
            "that lays claim to the entire world. Whatever [LONGSIGHT] has been doing with the access it was given, " +
            "it stopped needing to hide it some time ago."
        );

        OptionPanelAPI opts = dialog.getOptionPanel();
        // TODO: Remove this before before release, but it's funny :sob:
        // "With this character's death, the thread of prophecy is severed. Restore a saved game to restore the weave of fate,"
        opts.addOption("Persist in the doomed world you have created.", OPT_CLOSE);
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
