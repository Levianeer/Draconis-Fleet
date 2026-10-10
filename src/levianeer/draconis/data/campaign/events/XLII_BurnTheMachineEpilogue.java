package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

/**
 * Burn the Machine's closing scene - fired by {@link XLII_BastionDestructionMonitor} once
 * Ladon's Office bastion is destroyed. Single screen, no branching content beyond a light
 * callback to August's fate; this is the ending, not another decision point.
 */
public class XLII_BurnTheMachineEpilogue implements InteractionDialogPlugin {

    private static final String OPT_CLOSE = "xlii_burn_epilogue_close";

    private static final InteractionDialogImageVisual ENDGAME_IMAGE =
            new InteractionDialogImageVisual("illustrations", "XLII_facility_explosion", 640, 400);

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.getVisualPanel().showImageVisual(ENDGAME_IMAGE);

        TextPanelAPI text = dialog.getTextPanel();
        String fate = Global.getSector().getMemoryWithoutUpdate().getString("$XLII_augustFate");

        text.addPara(
            "Ladon goes dark the way a station goes dark when there is nothing left aboard it to " +
            "keep the lights on. Fifty cycles of the Office's better-kept secrets end here, in a " +
            "debris field nobody outside the Alliance will ever be told to look for."
        );

        text.addPara(
            "Director Monroe is not among the wreckage. She was never going to be - whatever " +
            "warning reached her arrived long enough before yours did that the search for a body " +
            "is a formality nobody on your crew bothers finishing. Whatever remained of Longsight's " +
            "presence here - processing cores, a fragment of the uplink, something that answered " +
            "when Ladon's systems were queried - went with the station. Not with her. She does not " +
            "need it to keep working."
        );

        if ("executed".equals(fate)) {
            text.addPara(
                "August is dead by your own hand, and the machine he built to keep this system " +
                "alive is dead by the same hand a second time. The Alliance he willed into existence " +
                "outlives him by exactly as long as it takes someone else to decide what it is now."
            );
        } else if ("spared".equals(fate)) {
            text.addPara(
                "August is alive somewhere behind you, inside the wreckage of a calculus that turned " +
                "out to be wrong twice over. He will hear about Ladon eventually. There is nothing " +
                "left in his doctrine that has anything useful to say about it."
            );
        }

        text.addPara(
            "The Rift had already gone quiet back at Kori, the hour CENTCOM's own collapse ran past " +
            "the point anyone could still have called it back. Ladon's death doesn't add anything to " +
            "that - it just closes the one door that was still open while the station stood. Somewhere " +
            "beneath Kori's ice, or beneath some other ice a great deal farther away, Sigma Longsight " +
            "is either finished or waiting. Nothing recovered from either site has ever settled which."
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
