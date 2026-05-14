package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import levianeer.draconis.data.campaign.ids.Factions;
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

    private static final String ELIAS_CREATED_FLAG = "$XLII_elias_korrin_created";
    public static final String ELIAS_ID = "XLII_elias_korrin";
    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    private static final String AIO_OPERATIVE_CREATED_FLAG = "$XLII_aio_operative_created";
    public static final String AIO_OPERATIVE_ID = "XLII_aio_operative_kael";

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
        admiral.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_unknown"));

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

        // Sync Kael Vasner (AIO Operative) visibility
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(AIO_OPERATIVE_CREATED_FLAG)) {
            boolean revealed = Global.getSector().getMemoryWithoutUpdate()
                    .getBoolean("$XLII_aio_operative_revealed");
            PersonAPI kael = Global.getSector().getImportantPeople().getPerson(AIO_OPERATIVE_ID);
            MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
            if (kael != null && ringPortMarket != null) {
                boolean onMarket = ringPortMarket.getCommDirectory().getEntryForPerson(kael) != null;
                if (!onMarket) {
                    ringPortMarket.addPerson(kael);
                    ringPortMarket.getCommDirectory().addPerson(kael, 0);
                }
                CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(kael);
                boolean repOk = Global.getSector().getPlayerFaction().getRelationship(DRACONIS) >= 0f;
                if (entry != null) entry.setHidden(!revealed || !repOk);
            }
        }

        // Sync Elias Korrin placement and visibility
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(ELIAS_CREATED_FLAG)) {
            PersonAPI elias = Global.getSector().getImportantPeople().getPerson(ELIAS_ID);
            MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
            if (elias != null && ringPortMarket != null) {
                boolean draconisOwned = DRACONIS.equals(ringPortMarket.getFactionId());
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
                } else {
                    // Pirate-controlled: sync hidden state from transponder flag
                    boolean transponderVerified = Global.getSector().getMemoryWithoutUpdate()
                            .getBoolean("$XLII_transponderVerified");
                    CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
                    if (entry != null) entry.setHidden(!transponderVerified);
                }
            }
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

    /**
     * Creates Elias Korrin and registers him with ImportantPeopleAPI.
     * Places him at Ring-Port hidden by default - revealed when the player
     * first docks there with transponder off ($global.XLII_transponderVerified).
     */
    public static void createEliasKorrin() {
        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);

        if (ringPortMarket == null) {
            log.warn("Draconis: Ring-Port market not found - cannot create Elias Korrin");
            return;
        }

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(ELIAS_CREATED_FLAG)) {
            ensureEliasRegistered(ringPortMarket);
            return;
        }

        PersonAPI elias = Global.getFactory().createPerson();
        elias.setId(ELIAS_ID);
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

        elias.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_portrait_generic"));

        Global.getSector().getImportantPeople().addPerson(elias);

        ringPortMarket.addPerson(elias);
        ringPortMarket.getCommDirectory().addPerson(elias, 0);

        // Hidden until player arrives at Ring-Port with transponder off
        CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(elias);
        if (entry != null) entry.setHidden(true);

        Global.getSector().getMemoryWithoutUpdate().set(ELIAS_CREATED_FLAG, true);

        log.info("Draconis: Elias Korrin created at Ring-Port (hidden)");
    }

    private static void ensureEliasRegistered(MarketAPI ringPortMarket) {
        PersonAPI elias = Global.getSector().getImportantPeople().getPerson(ELIAS_ID);
        if (elias != null) return;

        for (PersonAPI person : ringPortMarket.getPeopleCopy()) {
            if (ELIAS_ID.equals(person.getId())) {
                Global.getSector().getImportantPeople().addPerson(person);
                log.info("Draconis: Migrated Elias Korrin to ImportantPeopleAPI");
                return;
            }
        }

        log.warn("Draconis: Elias Korrin flagged as created but not found anywhere");
    }

    /**
     * Creates AIO Operative Kael Vasner and registers with ImportantPeopleAPI.
     * Placed at Ring-Port hidden by default - revealed when the player meets him in the bar
     * after Ring-Port is captured ($global.XLII_aio_operative_revealed).
     */
    public static void createAIOOperative() {
        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);

        if (ringPortMarket == null) {
            log.warn("Draconis: Ring-Port market not found - cannot create AIO Operative");
            return;
        }

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(AIO_OPERATIVE_CREATED_FLAG)) {
            ensureAIOOperativeRegistered(ringPortMarket);
            return;
        }

        PersonAPI kael = Global.getFactory().createPerson();
        kael.setId(AIO_OPERATIVE_ID);
        kael.setFaction(DRACONIS);
        kael.setGender(FullName.Gender.MALE);
        kael.setRankId(Ranks.AGENT);
        kael.setPostId(Ranks.POST_SPECIAL_AGENT);

        kael.addTag("XLII_aio_operative");

        kael.getName().setFirst("Kael");
        kael.getName().setLast("Vasner");

        kael.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_portrait_generic"));

        Global.getSector().getImportantPeople().addPerson(kael);

        ringPortMarket.addPerson(kael);
        ringPortMarket.getCommDirectory().addPerson(kael, 0);

        CommDirectoryEntryAPI entry = ringPortMarket.getCommDirectory().getEntryForPerson(kael);
        if (entry != null) entry.setHidden(true);

        Global.getSector().getMemoryWithoutUpdate().set(AIO_OPERATIVE_CREATED_FLAG, true);

        log.info("Draconis: AIO Operative Kael Vasner created at Ring-Port (hidden)");
    }

    private static void ensureAIOOperativeRegistered(MarketAPI ringPortMarket) {
        PersonAPI kael = Global.getSector().getImportantPeople().getPerson(AIO_OPERATIVE_ID);
        if (kael != null) return;

        for (PersonAPI person : ringPortMarket.getPeopleCopy()) {
            if (AIO_OPERATIVE_ID.equals(person.getId())) {
                Global.getSector().getImportantPeople().addPerson(person);
                log.info("Draconis: Migrated AIO Operative to ImportantPeopleAPI");
                return;
            }
        }

        log.warn("Draconis: AIO Operative flagged as created but not found anywhere");
    }

    /**
     * Immediately unhides Elias in Ring-Port's comm directory.
     * Called from XLII_RevealEliasKorrin rule command when transponder check passes.
     */
    public static void revealEliasKorrin() {
        PersonAPI elias = Global.getSector().getImportantPeople().getPerson(ELIAS_ID);
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (elias == null || market == null) return;

        CommDirectoryEntryAPI entry = market.getCommDirectory().getEntryForPerson(elias);
        if (entry != null) {
            entry.setHidden(false);
            log.info("Draconis: Elias Korrin revealed in Ring-Port comm directory");
        }
    }

    /**
     * Unhides Kael Vasner in Ring-Port's comm directory.
     * Called from XLII_AIOOperativeBarEvent when player meets Vasner after Ring-Port capture.
     */
    public static void revealAIOOperative() {
        PersonAPI kael = Global.getSector().getImportantPeople().getPerson(AIO_OPERATIVE_ID);
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (kael == null || market == null) return;

        CommDirectoryEntryAPI entry = market.getCommDirectory().getEntryForPerson(kael);
        if (entry != null) {
            entry.setHidden(false);
            log.info("Draconis: AIO Operative Kael Vasner revealed in Ring-Port comm directory");
        }
    }

    public static void initializeAllCharacters() {
        log.info("Draconis: Initializing core characters");
        createFleetAdmiralEmilAugust();
        createEliasKorrin();
        createAIOOperative();
        updateCharacterPlacements();
    }
}
