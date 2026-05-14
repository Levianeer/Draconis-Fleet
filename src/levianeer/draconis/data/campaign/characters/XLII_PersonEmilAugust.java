package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import org.apache.log4j.Logger;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Fleet Admiral Emil August - faction leader stationed at Kori.
 * Dynamically placed/removed based on Draconis market ownership.
 */
public class XLII_PersonEmilAugust {

    private static final Logger log = Global.getLogger(XLII_PersonEmilAugust.class);

    public static final String PERSON_ID = "XLII_fleet_admiral_emil";
    private static final String CREATED_FLAG = "$XLII_admiral_emil_created";
    private static final String MARKET_ID = "kori_market";

    /**
     * Creates Fleet Admiral Emil August and registers with ImportantPeopleAPI.
     * Only places on Kori if Draconis controls it.
     * On existing saves, ensures registration with ImportantPeopleAPI.
     */
    public static void createOrEnsureRegistered() {
        MarketAPI koriMarket = Global.getSector().getEconomy().getMarket(MARKET_ID);

        if (koriMarket == null) {
            log.warn("Draconis: Kori market not found - cannot create Fleet Admiral");
            return;
        }

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            ensureRegistered(koriMarket);
            return;
        }

        PersonAPI admiral = Global.getFactory().createPerson();
        admiral.setId(PERSON_ID);
        admiral.setFaction(DRACONIS);
        admiral.setGender(FullName.Gender.MALE);
        admiral.setRankId("factionLeader");
        admiral.setPostId("factionLeader");

        admiral.addTag("XLII_fleet_admiral_emil");
        admiral.addTag("XLII_military_command");

        admiral.getName().setFirst("Emil");
        admiral.getName().setLast("August");

        admiral.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_unknown"));

        admiral.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
        admiral.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);

        admiral.getMemoryWithoutUpdate().set("$XLII_admiral_initialized", true);
        admiral.getMemoryWithoutUpdate().set("$XLII_rank", "Fleet Admiral");

        Global.getSector().getImportantPeople().addPerson(admiral);

        if (DRACONIS.equals(koriMarket.getFactionId())) {
            koriMarket.addPerson(admiral);
            koriMarket.getCommDirectory().addPerson(admiral, 0);
        }

        Global.getSector().getMemoryWithoutUpdate().set(CREATED_FLAG, true);

        log.info("Draconis: Fleet Admiral Emil August created");
    }

    /**
     * Checks Kori market ownership and adds/removes the admiral accordingly.
     */
    public static void updatePlacement() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            return;
        }

        MarketAPI koriMarket = Global.getSector().getEconomy().getMarket(MARKET_ID);
        if (koriMarket == null) return;

        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (admiral == null) return;

        boolean isDraconisOwned = DRACONIS.equals(koriMarket.getFactionId());
        boolean isOnMarket = isOnMarket(koriMarket);

        if (isDraconisOwned && !isOnMarket) {
            koriMarket.addPerson(admiral);
            koriMarket.getCommDirectory().addPerson(admiral, 0);
            log.info("Draconis: Fleet Admiral Emil August restored to Kori");
        } else if (!isDraconisOwned && isOnMarket) {
            koriMarket.removePerson(admiral);
            koriMarket.getCommDirectory().removePerson(admiral);
            log.info("Draconis: Fleet Admiral Emil August removed from Kori (no longer Draconis-controlled)");
        }

        updatePortrait();
    }

    /**
     * Sets the admiral's portrait based on personal reputation with the player.
     * At max rep (1.0), reveals his true portrait; otherwise uses the unknown placeholder.
     */
    public static void updatePortrait() {
        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (admiral == null) return;

        float rep = admiral.getRelToPlayer().getRel();
        String portraitKey = rep >= 1.0f ? "XLII_emil_august" : "XLII_unknown";
        admiral.setPortraitSprite(Global.getSettings().getSpriteName("characters", portraitKey));
    }

    private static void ensureRegistered(MarketAPI koriMarket) {
        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (admiral != null) return;

        for (PersonAPI person : koriMarket.getPeopleCopy()) {
            if (PERSON_ID.equals(person.getId())) {
                Global.getSector().getImportantPeople().addPerson(person);
                log.info("Draconis: Migrated Fleet Admiral to ImportantPeopleAPI");
                return;
            }
        }

        log.warn("Draconis: Fleet Admiral flagged as created but not found anywhere");
    }

    private static boolean isOnMarket(MarketAPI market) {
        for (PersonAPI person : market.getPeopleCopy()) {
            if (PERSON_ID.equals(person.getId())) {
                return true;
            }
        }
        return false;
    }
}
