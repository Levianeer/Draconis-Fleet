package levianeer.draconis.data.campaign.intel.waste_not;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.Random;

/**
 * Officer plugin for the Auxiliary Cognition Unit ({@code XLII_ai_core_bio}) - the item recovered
 * from Waste Not's installation. Modeled directly on {@code XLII_LongsightOfficerPlugin} (this
 * mod's own existing example of this mechanism, found while wiring this one), not on a
 * borrow-and-downgrade approach some other mods use (e.g. Tahlan Shipworks' {@code
 * DaemonOfficerPlugin}, which calls a real core's own plugin and relabels the result) - this mod's
 * own precedent builds the person from scratch, so this does too.
 * <p>
 * Deliberately weaker than even a real Gamma Core (vanilla: level 3, three skills at level 2) -
 * low level, one skill, no personality suited to combat. That's the point: a cut-rate substitute,
 * not a bargain find. The commodity itself (see {@code commodities.csv}) carries no {@code ai_core}
 * tag, unlike every real core - it doesn't draw the scrutiny a real one would, which is the whole
 * reason the operation it came from could run at volume in the first place.
 */
public class XLII_LiteCoreOfficerPlugin implements AICoreOfficerPlugin {

    public static final String CORE_ID = "XLII_ai_core_bio";

    @Override
    public PersonAPI createPerson(String aiCoreId, String factionId, Random random) {
        if (random == null) random = new Random();

        PersonAPI person = Global.getFactory().createPerson();
        person.setAICoreId(aiCoreId);
        person.setFaction(factionId);

        person.setName(new FullName(
            Global.getSettings().getCommoditySpec(aiCoreId).getName(), "", Gender.ANY
        ));

        person.setRankId(Ranks.SPACE_CAPTAIN);
        person.setPostId(null);
        person.setPersonality(Personalities.TIMID);

        person.getStats().setSkipRefresh(true);

        person.getStats().setLevel(1);
        person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);

        person.getStats().setSkipRefresh(false);

        person.getMemoryWithoutUpdate().set(AUTOMATED_POINTS_MULT, 2f);

        person.setPortraitSprite("graphics/portraits/portrait_ai1b.png");

        return person;
    }

    @Override
    public void createPersonalitySection(PersonAPI person, TooltipMakerAPI tooltip) {
        tooltip.addPara(
            "Provisional-grade substrate, installed as crew. Expected service life is short. "
            + "The manifest does not specify what it replaces.",
            0f
        );
    }
}
