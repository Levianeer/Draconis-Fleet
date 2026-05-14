package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import org.apache.log4j.Logger;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Daniel Ancker - AIO operative stationed at Ring-Port.
 * Hidden until the player meets him in a bar event after Ring-Port capture.
 * Visibility also gated on positive reputation with Draconis.
 */
public class XLII_PersonDanielAncker {

    private static final Logger log = Global.getLogger(XLII_PersonDanielAncker.class);

    public static final String PERSON_ID = "XLII_aio_operative_Daniel";
    private static final String CREATED_FLAG = "$XLII_aio_operative_created";
    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    /**
     * Creates AIO Operative Daniel Ancker and registers with ImportantPeopleAPI.
     * Placed at Ring-Port hidden by default.
     * On existing saves, ensures registration with ImportantPeopleAPI.
     */
    public static void createOrEnsureRegistered() {
        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);

        if (ringPortMarket == null) {
            log.warn("Draconis: Ring-Port market not found - cannot create AIO Operative");
            return;
        }

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            ensureRegistered(ringPortMarket);
            return;
        }

        PersonAPI Daniel = Global.getFactory().createPerson();
        Daniel.setId(PERSON_ID);
        Daniel.setFaction(DRACONIS);
        Daniel.setGender(FullName.Gender.MALE);
        Daniel.setRankId(Ranks.AGENT);
        Daniel.setPostId(Ranks.POST_SPECIAL_AGENT);

        Daniel.addTag("XLII_aio_operative");

        Daniel.getName().setFirst("Daniel");
        Daniel.getName().setLast("Ancker");

        Daniel.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_daniel_ancker"));

        Global.getSector().getImportantPeople().addPerson(Daniel);

        ringPortMarket.addPerson(Daniel);
        ringPortMarket.getCommDirectory().addPerson(Daniel, 0);

        CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(Daniel);
        if (entry != null) entry.setHidden(true);

        Global.getSector().getMemoryWithoutUpdate().set(CREATED_FLAG, true);

        log.info("Draconis: AIO Operative Daniel Ancker created at Ring-Port (hidden)");
    }

    /**
     * Syncs Daniel Ancker's visibility based on reveal flag and reputation.
     */
    public static void updatePlacement() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            return;
        }

        boolean revealed = Global.getSector().getMemoryWithoutUpdate()
                .getBoolean("$XLII_aio_operative_revealed");
        PersonAPI Daniel = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (Daniel == null || ringPortMarket == null) return;

        boolean onMarket = ringPortMarket.getCommDirectory().getEntryForPerson(Daniel) != null;
        if (!onMarket) {
            ringPortMarket.addPerson(Daniel);
            ringPortMarket.getCommDirectory().addPerson(Daniel, 0);
        }
        CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(Daniel);
        boolean repOk = Global.getSector().getPlayerFaction().getRelationship(DRACONIS) >= 0f;
        if (entry != null) entry.setHidden(!revealed || !repOk);
    }

    /**
     * Unhides Daniel Ancker in Ring-Port's comm directory.
     * Called from XLII_AIOOperativeBarEvent when player meets Ancker after Ring-Port capture.
     */
    public static void reveal() {
        PersonAPI Daniel = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (Daniel == null || market == null) return;

        CommDirectoryEntryAPI entry = market.getCommDirectory().getEntryForPerson(Daniel);
        if (entry != null) {
            entry.setHidden(false);
            log.info("Draconis: AIO Operative Daniel Ancker revealed in Ring-Port comm directory");
        }
    }

    private static void ensureRegistered(MarketAPI ringPortMarket) {
        PersonAPI Daniel = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (Daniel != null) return;

        for (PersonAPI person : ringPortMarket.getPeopleCopy()) {
            if (PERSON_ID.equals(person.getId())) {
                Global.getSector().getImportantPeople().addPerson(person);
                log.info("Draconis: Migrated AIO Operative to ImportantPeopleAPI");
                return;
            }
        }

        log.warn("Draconis: AIO Operative flagged as created but not found anywhere");
    }
}
