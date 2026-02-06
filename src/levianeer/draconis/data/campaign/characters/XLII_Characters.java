package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import org.apache.log4j.Logger;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Manages creation of important Draconis Alliance characters
 */
public class XLII_Characters {

    private static final Logger log = Global.getLogger(XLII_Characters.class);
    private static final String ADMIRAL_CREATED_FLAG = "$XLII_admiral_emil_created";

    /**
     * Creates Fleet Admiral Emil August on Kori
     */
    public static void createFleetAdmiralEmilAugust() {
        MarketAPI koriMarket = Global.getSector().getEconomy().getMarket("kori_market");

        if (koriMarket == null) {
            Global.getLogger(XLII_Characters.class).warn("Kori market not found - cannot create Fleet Admiral");
            return;
        }

        // Check if character already exists using memory flag
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(ADMIRAL_CREATED_FLAG)) {
            Global.getLogger(XLII_Characters.class).info("Fleet Admiral Emil August already exists");
            return;
        }

        // Create the character
        PersonAPI admiral = Global.getFactory().createPerson();
        admiral.setId("XLII_fleet_admiral_emil");
        admiral.setFaction(DRACONIS);
        admiral.setGender(FullName.Gender.MALE);
        admiral.setRankId("factionLeader");
        admiral.setPostId("factionLeader");

        // Tags for rules.csv
        admiral.addTag("XLII_fleet_admiral_emil");
        admiral.addTag("XLII_military_command");

        // Set name
        admiral.getName().setFirst("Emil");
        admiral.getName().setLast("August");

        // Set portrait
        admiral.setPortraitSprite("graphics/portraits/characters/XLII_unknown.png");

        // Set skills
        admiral.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
        admiral.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);

        // Market
        koriMarket.addPerson(admiral);
        koriMarket.getCommDirectory().addPerson(admiral, 0);

        // Memory flags for dialogue system
        admiral.getMemoryWithoutUpdate().set("$XLII_admiral_initialized", true);
        admiral.getMemoryWithoutUpdate().set("$XLII_rank", "Fleet Admiral");

        // Mark as created in global memory
        Global.getSector().getMemoryWithoutUpdate().set(ADMIRAL_CREATED_FLAG, true);

        Global.getLogger(XLII_Characters.class).info("Fleet Admiral Emil August created on Kori");
    }

    public static void initializeAllCharacters() {
        log.info("Draconis: Initializing core characters");
        createFleetAdmiralEmilAugust();
    }
}