package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rule command: creates Faller (once, cached via {@code ImportantPeopleAPI}) and sets him as the
 * bar's active person, so {@code BeginMission}'s own {@code personOverride} resolution picks him up
 * and {@code StellarMirrorQuestMission}'s intel panel gets the automatic portrait + crest + "given
 * by" block for free. Direct copy of {@link XLII_WasteNotSetWaltActive}'s shape - see
 * {@code .claude/systems/intel.md}'s "BeginMission auto-supplies a portrait+crest" section.
 * <p>
 * Call it from a row's script <i>before</i> {@code BeginMission StellarMirrorQuestMission} in the
 * same cell, so the active person is set before {@code BeginMission} reads it.
 * <p>
 * {@code Factions.PIRATES} rather than Walt's {@code INDEPENDENT}: Faller is an Athebynian First
 * Fleet veteran living at Ring-Port, and the mod's own history has that remnant "refusing
 * integration, scattering to Ring-Port - a pirate haven". The faction only picks the portrait pool
 * and the crest on the intel panel; it carries no mechanical allegiance.
 * <p>
 * Gender is pinned via {@code createRandomPerson(Gender, Random)} rather than fixed afterwards with
 * {@code setGender()}, because the generator picks the first name and portrait to match whatever
 * gender it is handed - setting it after the fact would leave a male character wearing a female
 * portrait and first name. Every line Faller has is written for a man, so this is required, not
 * cosmetic.
 * <p>
 * Does not touch {@code dialog.getVisualPanel()} itself - showing the portrait is the caller's
 * job. The bar scenes pair this with a bare {@code ShowPersonVisual false} on the following line:
 * because this command has just set the active person, the bare form resolves to Faller without
 * being handed an id. Prefer that over {@code ShowPersonVisual <minimal> <personId>}, whose
 * implementation does {@code getImportantPeople().getData(id).getPerson()} with no null check and
 * would NPE on the first meeting, before this command has created him.
 * <p>
 * The {@code false} there is {@code minimalMode}, which is the inverse of "show the faction" -
 * {@code BaseHubMission.showPersonInfo()} is literally
 * {@code showPersonInfo(person, !withFaction, withRelBar)} (BaseHubMission.java:1019-1021). So
 * {@code false} means portrait <i>with</i> the faction crest. This is why {@code PIRATES} above is
 * a visible choice and not just a portrait pool: it is the crest the player sees in the bar and on
 * the intel panel.
 * <p>
 * Note Walt Hollis is text-only in his own bar scene, but that is an authoring choice in those
 * rows, not a constraint of the {@code AddBarEvents} mechanism: an {@code AddBarEvent} option is
 * selected inside the ordinary market interaction, so every visual command works there exactly as
 * it would anywhere else.
 */
public class XLII_SetFallerActive extends BaseCommandPlugin {

    public static final String PERSON_ID = "XLII_stellar_mirror_faller";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        PersonAPI faller = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (faller == null) {
            faller = Global.getSector().getFaction(Factions.PIRATES)
                    .createRandomPerson(FullName.Gender.MALE, new Random());
            faller.setId(PERSON_ID);
            faller.getName().setLast("Faller");
            faller.setRankId(Ranks.CITIZEN);
            faller.setPostId(Ranks.CITIZEN);
            Global.getSector().getImportantPeople().addPerson(faller);
        }

        dialog.getInteractionTarget().setActivePerson(faller);
        return true;
    }
}
