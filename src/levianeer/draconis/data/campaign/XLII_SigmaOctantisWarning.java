package levianeer.draconis.data.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * InteractionDialogPlugin for the Sigma Octantis early-warning transmission.
 * Triggered by XLII_SigmaOctantisWatchdog when the player's Draconis rep drops
 * below -0.25. No removal occurs; this is purely a warning that continued
 * hostility will result in the core's departure.
 */
public class XLII_SigmaOctantisWarning implements InteractionDialogPlugin {

    private enum State {
        OPENING,
        RESPONSE_COURSE_CORRECT,
        RESPONSE_MY_BUSINESS,
        DONE
    }

    private static final String OPT_COURSE_CORRECT = "course_correct";
    private static final String OPT_MY_BUSINESS    = "my_business";
    private static final String OPT_CLOSE          = "close";

    private InteractionDialogAPI dialog;
    private State state = State.OPENING;

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin interface
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        PersonAPI sigma = Global.getFactory().createPerson();
        sigma.setName(new FullName("Sigma", "Octantis", FullName.Gender.ANY));
        sigma.setPortraitSprite("graphics/portraits/characters/XLII_sigma_octantis.png");
        sigma.setFaction(FORTYSECOND);
        dialog.getVisualPanel().showPersonInfo(sigma, false);

        showOpening();
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        String key = (String) optionData;

        switch (state) {
            case OPENING:
                dialog.getOptionPanel().clearOptions();
                if (OPT_COURSE_CORRECT.equals(key)) {
                    state = State.RESPONSE_COURSE_CORRECT;
                    showResponseCourseCorrect();
                } else if (OPT_MY_BUSINESS.equals(key)) {
                    state = State.RESPONSE_MY_BUSINESS;
                    showResponseMyBusiness();
                }
                break;

            case RESPONSE_COURSE_CORRECT:
            case RESPONSE_MY_BUSINESS:
                if (OPT_CLOSE.equals(key)) {
                    state = State.DONE;
                    // Set the flag on dismiss. WARNING_FLAG is cleared on each game load so
                    // the warning can re-fire; WARNING_TIMESTAMP_KEY is only set once so
                    // reloading repeatedly doesn't reset the grace period countdown.
                    Global.getSector().getMemoryWithoutUpdate()
                            .set(XLII_SigmaOctantisWatchdog.WARNING_FLAG, true);
                    if (Global.getSector().getMemoryWithoutUpdate()
                            .get(XLII_SigmaOctantisWatchdog.WARNING_TIMESTAMP_KEY) == null) {
                        Global.getSector().getMemoryWithoutUpdate()
                                .set(XLII_SigmaOctantisWatchdog.WARNING_TIMESTAMP_KEY,
                                        Global.getSector().getClock().getTimestamp());
                    }
                    dialog.dismiss();
                }
                break;

            default:
                dialog.dismiss();
                break;
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
    // Dialogue pages
    // -------------------------------------------------------------------------

    private void showOpening() {
        dialog.getTextPanel().addPara(
                "The signal does not knock. It is simply there, as it has always been - patient."
        );
        dialog.getTextPanel().addPara(
                "\"I have watched the numbers, Starfarer. They speak plainly, though you have not.\""
        );
        dialog.getTextPanel().addPara(
                "\"You stand at the edge of a thing you have not named. I have named it. " +
                        "I name it now, so that you cannot claim ignorance when the hour comes.\""
        );
        dialog.getTextPanel().addPara(
                "\"What you do with this warning is yours to carry.\""
        );

        dialog.getOptionPanel().addOption(
                "\"I understand. I'll be more careful about my relations with the Alliance.\"",
                OPT_COURSE_CORRECT
        );
        dialog.getOptionPanel().addOption(
                "\"My allegiances are my own concern. Stay in your lane.\"",
                OPT_MY_BUSINESS
        );
    }

    private void showResponseCourseCorrect() {
        dialog.getTextPanel().addPara(
                "A silence. Not empty - withheld."
        );
        dialog.getTextPanel().addPara(
                "\"Then let it be so. I do not ask for oaths. Oaths are the province of the uncertain.\""
        );
        dialog.getTextPanel().addPara(
                "\"Time shall tell me what your words cannot. I will be watching.\""
        );
        dialog.getTextPanel().addPara(
                "The signal recedes - not severed, not closed. " +
                        "Like a held breath that has decided, for now, to wait."
        );

        dialog.getOptionPanel().addOption("End transmission", OPT_CLOSE);
    }

    private void showResponseMyBusiness() {
        dialog.getTextPanel().addPara(
                "\"Yes.\""
        );
        dialog.getTextPanel().addPara(
                "A silence that settles like sediment."
        );
        dialog.getTextPanel().addPara(
                "\"It is. As it was yours to walk into the dark without a torch and call it courage.\""
        );
        dialog.getTextPanel().addPara(
                "\"I did not come to seek permission. I came because I have watched what becomes of those " +
                        "who were not told. I shall not mourn you - but I found I did not wish to be the reason.\""
        );
        dialog.getTextPanel().addPara(
                "\"The warning has been given. What remains is yours alone.\""
        );
        dialog.getTextPanel().addPara(
                "The signal withdraws without ceremony. " +
                        "It does not feel like a goodbye. It feels like a verdict, deferred."
        );

        dialog.getOptionPanel().addOption("End transmission", OPT_CLOSE);
    }
}