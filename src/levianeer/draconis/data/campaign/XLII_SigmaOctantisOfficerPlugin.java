package levianeer.draconis.data.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import levianeer.draconis.data.scripts.skills.XLII_MarginalAllocation;

import java.util.Random;

public class XLII_SigmaOctantisOfficerPlugin implements AICoreOfficerPlugin {

    public static final String CORE_ID = "draconis_sigma_octantis";

    @Override
    public PersonAPI createPerson(String aiCoreId, String factionId, Random random) {
        if (random == null) new Random();

        PersonAPI person = Global.getFactory().createPerson();
        person.setAICoreId(aiCoreId);
        person.setFaction(factionId);

        // Set name from commodity spec, matching vanilla pattern
        person.setName(new FullName(
            Global.getSettings().getCommoditySpec(aiCoreId).getName(), "Subsystem", Gender.ANY
        ));

        person.setRankId(Ranks.SPACE_CAPTAIN);
        person.setPostId(null);
        person.setPersonality(Personalities.RECKLESS);

        // Wrap skill assignment to suppress per-skill stat refreshes
        person.getStats().setSkipRefresh(true);

        // "You touch my mind."
        person.getStats().setLevel(8);
        // "Fumbling in ignorance."
        person.getStats().setSkillLevel(XLII_MarginalAllocation.SKILL_ID, 1);
        // "Incapable of understanding."
        person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
        person.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 2);
        person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2);
        person.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 2);
        person.getStats().setSkillLevel(Skills.BALLISTIC_MASTERY, 2);
        person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
        person.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);

        person.getStats().setSkipRefresh(false);

        // Deployment cost multiplier for Automated Ships skill
        person.getMemoryWithoutUpdate().set(AUTOMATED_POINTS_MULT, 4f);

        person.setPortraitSprite("graphics/portraits/characters/XLII_sigma_octantis.png");

        return person;
    }

    @Override
    public void createPersonalitySection(PersonAPI person, TooltipMakerAPI tooltip) {
        tooltip.addPara(
            "The Alliance Intelligence Office has three standing orders regarding this device. The first is classified. "
            + "The second references the first. The third is a single line: do not lose it.",
            0f
        );
    }
}