package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import levianeer.draconis.data.campaign.companion.KorrinCompanion;
import levianeer.draconis.data.scripts.skills.XLII_SignalsDiscipline;
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

        elias.getStats().setSkillLevel(XLII_SignalsDiscipline.SKILL_ID, 1);

        elias.addTag("XLII_elias_korrin");

        elias.getName().setFirst("Elias");
        elias.getName().setLast("Korrin");

        // Character notes: August's intended heir - suspected by Korrin, never stated by either.
        // Post-war generation, born on Itoron; third-generation Athebynian by descent.
        // No AIO file: two recruitment flags withdrawn, and he did not withdraw them.
        // See .claude/characters/character_profile-korrin.md.
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
     * Syncs Elias Korrin's placement and visibility based on Ring-Port ownership, or hides him
     * outright while he is travelling with the player.
     */
    public static void updatePlacement() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            return;
        }

        PersonAPI elias = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (elias == null || ringPortMarket == null) return;

        // He cannot be at his post and in the player's fleet at once. Ownership does not matter
        // while he is aboard; returnToStation() calls back in here and the branches below restore
        // him. This is also why every other caller routes through updatePlacement().
        if (KorrinCompanion.isAboard()) {
            CommDirectoryEntryAPI aboardEntry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
            if (aboardEntry != null) aboardEntry.setHidden(true);
            return;
        }

        boolean draconisOwned = DRACONIS.equals(ringPortMarket.getFactionId());
        boolean pirateOwned = com.fs.starfarer.api.impl.campaign.ids.Factions.PIRATES
                .equals(ringPortMarket.getFactionId());
        if (draconisOwned) {
            boolean eliasOnMarket = ringPortMarket.getCommDirectory().getEntryForPerson(elias) != null;
            if (!eliasOnMarket) {
                ringPortMarket.addPerson(elias);
                ringPortMarket.getCommDirectory().addPerson(elias, 0);
                log.info("Draconis: Elias Korrin re-added to Ring-Port after capture");
            }
            CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
            if (entry != null) entry.setHidden(false);
        } else if (pirateOwned) {
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
