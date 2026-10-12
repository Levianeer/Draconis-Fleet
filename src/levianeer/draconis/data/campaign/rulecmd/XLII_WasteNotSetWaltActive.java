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
 * Rule command: creates Walt Hollis (once, cached via {@code ImportantPeopleAPI}) and sets him
 * as the bar's active person, so {@code BeginMission}'s own {@code personOverride} resolution
 * (see {@code .claude/systems/intel.md}'s "BeginMission auto-supplies a portrait+crest" section)
 * picks him up and {@code WasteNotMission}'s intel panel gets the same automatic portrait+crest+
 * "given by" block every other tracker in this chain gets for free.
 * <p>
 * Portrait and face are generated, not custom art - {@code FactionAPI.createRandomPerson(Gender,
 * Random)} is the same mechanism vanilla's own {@code BaseBarEventWithPerson} uses for a one-off
 * bar NPC, and {@code Factions.INDEPENDENT} is that class' own default faction for exactly this
 * case (an unaffiliated contact, no letterhead) - not a guess, confirmed by reading that class'
 * source directly. Call this from a row's script, before {@code BeginMission WasteNotMission} in
 * the same cell, so the active person is set before {@code BeginMission} reads it.
 * <p>
 * Deliberately does not call {@code ShowPersonVisual}/{@code dialog.getVisualPanel()} anywhere -
 * Walt stays text-only for the live bar scene itself (matches {@code XLII_AIOOperativeBarEvent}'s
 * own "no portrait until revealed" precedent for Ancker); this only affects the mission panel.
 */
public class XLII_WasteNotSetWaltActive extends BaseCommandPlugin {

    public static final String PERSON_ID = "XLII_waste_not_walt";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        PersonAPI walt = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (walt == null) {
            walt = Global.getSector().getFaction(Factions.INDEPENDENT)
                    .createRandomPerson(FullName.Gender.MALE, new Random());
            walt.setId(PERSON_ID);
            walt.getName().setFirst("Walt");
            walt.getName().setLast("Hollis");
            walt.setRankId(Ranks.CITIZEN);
            walt.setPostId(Ranks.CITIZEN);
            Global.getSector().getImportantPeople().addPerson(walt);
        }

        dialog.getInteractionTarget().setActivePerson(walt);
        return true;
    }
}
