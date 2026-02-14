package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import org.apache.log4j.Logger;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Manages creation and placement of important Draconis Alliance characters.
 * Handles dynamic removal/restoration of characters based on market ownership.
 */
public class XLII_Characters {

    private static final Logger log = Global.getLogger(XLII_Characters.class);
    private static final String ADMIRAL_CREATED_FLAG = "$XLII_admiral_emil_created";
    public static final String ADMIRAL_ID = "XLII_fleet_admiral_emil";

    /**
     * Creates Fleet Admiral Emil August and registers with ImportantPeopleAPI.
     * Only places on Kori if Draconis controls it.
     */
    public static void createFleetAdmiralEmilAugust() {
        MarketAPI koriMarket = Global.getSector().getEconomy().getMarket("kori_market");

        if (koriMarket == null) {
            log.warn("Draconis: Kori market not found - cannot create Fleet Admiral");
            return;
        }

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(ADMIRAL_CREATED_FLAG)) {
            // Existing save - ensure admiral is registered with ImportantPeopleAPI
            ensureAdmiralRegistered(koriMarket);
            return;
        }

        // Create the character
        PersonAPI admiral = Global.getFactory().createPerson();
        admiral.setId(ADMIRAL_ID);
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

        // Memory flags for dialogue system
        admiral.getMemoryWithoutUpdate().set("$XLII_admiral_initialized", true);
        admiral.getMemoryWithoutUpdate().set("$XLII_rank", "Fleet Admiral");

        // Register with ImportantPeopleAPI for persistent lookup across market changes
        Global.getSector().getImportantPeople().addPerson(admiral);

        // Only place on Kori if Draconis controls it
        if (DRACONIS.equals(koriMarket.getFactionId())) {
            koriMarket.addPerson(admiral);
            koriMarket.getCommDirectory().addPerson(admiral, 0);
        }

        // Mark as created in global memory
        Global.getSector().getMemoryWithoutUpdate().set(ADMIRAL_CREATED_FLAG, true);

        log.info("Draconis: Fleet Admiral Emil August created");
    }

    /**
     * Migration for existing saves: ensures the admiral is registered with
     * ImportantPeopleAPI so we can find them after market ownership changes.
     */
    private static void ensureAdmiralRegistered(MarketAPI koriMarket) {
        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson(ADMIRAL_ID);
        if (admiral != null) return;

        // Search on Kori market (pre-update saves have the admiral here)
        for (PersonAPI person : koriMarket.getPeopleCopy()) {
            if (ADMIRAL_ID.equals(person.getId())) {
                Global.getSector().getImportantPeople().addPerson(person);
                log.info("Draconis: Migrated Fleet Admiral to ImportantPeopleAPI");
                return;
            }
        }

        log.warn("Draconis: Fleet Admiral flagged as created but not found anywhere");
    }

    /**
     * Checks Kori market ownership and adds/removes the admiral accordingly.
     * Called on game load and periodically by DraconisSteelCurtainMonitor.
     */
    public static void updateCharacterPlacements() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(ADMIRAL_CREATED_FLAG)) {
            return;
        }

        MarketAPI koriMarket = Global.getSector().getEconomy().getMarket("kori_market");
        if (koriMarket == null) return;

        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson(ADMIRAL_ID);
        if (admiral == null) return;

        boolean isDraconisOwned = DRACONIS.equals(koriMarket.getFactionId());
        boolean isOnMarket = isAdmiralOnMarket(koriMarket);

        if (isDraconisOwned && !isOnMarket) {
            koriMarket.addPerson(admiral);
            koriMarket.getCommDirectory().addPerson(admiral, 0);
            log.info("Draconis: Fleet Admiral Emil August restored to Kori");
        } else if (!isDraconisOwned && isOnMarket) {
            koriMarket.removePerson(admiral);
            koriMarket.getCommDirectory().removePerson(admiral);
            log.info("Draconis: Fleet Admiral Emil August removed from Kori (no longer Draconis-controlled)");
        }
    }

    private static boolean isAdmiralOnMarket(MarketAPI market) {
        for (PersonAPI person : market.getPeopleCopy()) {
            if (ADMIRAL_ID.equals(person.getId())) {
                return true;
            }
        }
        return false;
    }

    public static void initializeAllCharacters() {
        log.info("Draconis: Initializing core characters");
        createFleetAdmiralEmilAugust();
        updateCharacterPlacements();
    }
}
