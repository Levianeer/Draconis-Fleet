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
 * Elias Korrin - special agent stationed at Ring-Port.
 * Hidden until the player docks with transponder off; always visible after Draconis captures Ring-Port.
 */
public class XLII_PersonEliasKorrin {

    private static final Logger log = Global.getLogger(XLII_PersonEliasKorrin.class);

    public static final String PERSON_ID = "XLII_elias_korrin";
    private static final String CREATED_FLAG = "$XLII_elias_korrin_created";
    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    /**
     * Creates Elias Korrin and registers with ImportantPeopleAPI.
     * Places him at Ring-Port hidden by default.
     * On existing saves, ensures registration with ImportantPeopleAPI.
     */
    public static void createOrEnsureRegistered() {
        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);

        if (ringPortMarket == null) {
            log.warn("Draconis: Ring-Port market not found - cannot create Elias Korrin");
            return;
        }

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            ensureRegistered(ringPortMarket);
            return;
        }

        PersonAPI elias = Global.getFactory().createPerson();
        elias.setId(PERSON_ID);
        elias.setFaction(DRACONIS);
        elias.setGender(FullName.Gender.MALE);
        elias.setRankId(Ranks.AGENT);
        elias.setPostId(Ranks.POST_SPECIAL_AGENT);

        elias.addTag("XLII_elias_korrin");

        elias.getName().setFirst("Elias");
        elias.getName().setLast("Korrin");

        // Character notes: August's planned heir, raised during the final years of the war.
        // Predates AIO institutional memory - the Office has no file on him.
        // Deeply conflicted about Athebyne; loyal to August regardless.
        elias.getMemoryWithoutUpdate().set("$XLII_elias_heir_apparent", true);

        elias.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_elias_korrin"));

        Global.getSector().getImportantPeople().addPerson(elias);

        ringPortMarket.addPerson(elias);
        ringPortMarket.getCommDirectory().addPerson(elias, 0);

        // Hidden until player arrives at Ring-Port with transponder off
        CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
        if (entry != null) entry.setHidden(true);

        Global.getSector().getMemoryWithoutUpdate().set(CREATED_FLAG, true);

        log.info("Draconis: Elias Korrin created at Ring-Port (hidden)");
    }

    /**
     * Syncs Elias Korrin's placement and visibility based on Ring-Port ownership.
     */
    public static void updatePlacement() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            return;
        }

        PersonAPI elias = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (elias == null || ringPortMarket == null) return;

        boolean draconisOwned = DRACONIS.equals(ringPortMarket.getFactionId());
        boolean pirateOwned = com.fs.starfarer.api.impl.campaign.ids.Factions.PIRATES
                .equals(ringPortMarket.getFactionId());
        if (draconisOwned) {
            // After capture: ensure Elias is present and always visible
            boolean eliasOnMarket = ringPortMarket.getCommDirectory().getEntryForPerson(elias) != null;
            if (!eliasOnMarket) {
                ringPortMarket.addPerson(elias);
                ringPortMarket.getCommDirectory().addPerson(elias, 0);
                log.info("Draconis: Elias Korrin re-added to Ring-Port after capture");
            }
            CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
            if (entry != null) entry.setHidden(false);
        } else if (pirateOwned) {
            // Pirate-controlled: sync hidden state from transponder flag
            boolean transponderVerified = Global.getSector().getMemoryWithoutUpdate()
                    .getBoolean("$XLII_transponderVerified");
            CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
            if (entry != null) entry.setHidden(!transponderVerified);
        } else {
            // Third-party/player ownership (external capture): hide Elias
            CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
            if (entry != null) entry.setHidden(true);
        }
    }

    /**
     * Immediately unhides Elias in Ring-Port's comm directory.
     * Called from XLII_RevealEliasKorrin rule command when transponder check passes.
     */
    public static void reveal() {
        PersonAPI elias = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (elias == null || market == null) return;

        CommDirectoryEntryAPI entry = market.getCommDirectory().getEntryForPerson(elias);
        if (entry != null) {
            entry.setHidden(false);
            log.info("Draconis: Elias Korrin revealed in Ring-Port comm directory");
        }
    }

    /**
     * Hides Elias in Ring-Port's comm directory during an external capture.
     * Called from BlindEyeQuestMission when external capture is first detected in-session,
     * so Elias is hidden immediately without waiting for a game reload.
     */
    public static void hideIfExternalCapture() {
        PersonAPI elias = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (elias == null || market == null) return;

        CommDirectoryEntryAPI entry = market.getCommDirectory().getEntryForPerson(elias);
        if (entry != null && !entry.isHidden()) {
            entry.setHidden(true);
            log.info("Draconis: Elias Korrin hidden at Ring-Port (external capture)");
        }
    }

    private static void ensureRegistered(MarketAPI ringPortMarket) {
        PersonAPI elias = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (elias != null) return;

        for (PersonAPI person : ringPortMarket.getPeopleCopy()) {
            if (PERSON_ID.equals(person.getId())) {
                Global.getSector().getImportantPeople().addPerson(person);
                log.info("Draconis: Migrated Elias Korrin to ImportantPeopleAPI");
                return;
            }
        }

        log.warn("Draconis: Elias Korrin flagged as created but not found anywhere");
    }
}
