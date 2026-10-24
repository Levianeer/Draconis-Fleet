package levianeer.draconis.data.campaign.aicores;

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
 * Officer plugin for the AuxCog Core ({@code XLII_ai_core_bio}, renamed this session from
 * "Auxiliary Cognition Unit" - author request, needed "Core" in the name to read as an AI core
 * rather than some other kind of item) - first recovered
 * from Waste Not's installation, but not scoped to that arc: lives in its own package (moved out
 * of {@code intel.waste_not}, author request) since this is meant to be reused by other future
 * content, not a one-quest mechanic. Modeled directly on {@code XLII_LongsightOfficerPlugin}
 * (intel/longsight - that one stays put, it's a genuinely unique one-off Domain item, unlike this
 * one), not on a borrow-and-downgrade approach some other mods use (e.g. Tahlan Shipworks' {@code
 * DaemonOfficerPlugin}, which calls a real core's own plugin and relabels the result) - this mod's
 * own precedent builds the person from scratch, so this does too.
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
        person.setPersonality(Personalities.RECKLESS);

        person.getStats().setSkipRefresh(true);

        person.getStats().setLevel(2);
        person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
        person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);

        person.getStats().setSkipRefresh(false);

        person.getMemoryWithoutUpdate().set(AUTOMATED_POINTS_MULT, 1.2f);

        person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_bio_ai"));

        return person;
    }

    @Override
    public void createPersonalitySection(PersonAPI person, TooltipMakerAPI tooltip) {
        tooltip.addPara(
            "A technologically advanced but limited cerebral networking core created from nervous tissue. " +
            "Supports the integration of the body with inorganic machinery.",
            0f
        );
    }
}
