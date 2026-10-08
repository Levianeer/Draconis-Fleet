package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import org.apache.log4j.Logger;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * NISA - Regin Armaments' customer-facing support algorithm, reached through Itoron's comm
 * directory. Handles the credits-for-story-point exchange moved off Daniel Ancker (see
 * rules.csv's "[NISA] System" section). Faction is DRACONIS, not the Intelligence Office -
 * per the personality guide (Downloads/NISA — Personality & Voice Guide.md), NISA is the
 * Alliance's clean public-facing voice specifically because she has no knowledge of or tie to
 * the Office; tagging her Office-faction would contradict that.
 * <p>
 * Present on Itoron from the start (she's permanent Regin/Draconis storefront furniture, there to
 * onboard new players) and removed only if Itoron stops being Draconis-owned - same
 * add/remove-on-condition shape as {@link XLII_PersonEmilAugust#updatePlacement()}. The
 * credits-for-story-point option is the only part gated on the Pristine Nanoforge handover
 * ({@code $global.XLII_nanoforgeQuestComplete}) - see rules.csv's XLII_nisa_main_opt_buy.
 */
public class XLII_PersonNisa {

    private static final Logger log = Global.getLogger(XLII_PersonNisa.class);

    public static final String PERSON_ID = "XLII_regin_nisa";
    private static final String CREATED_FLAG = "$XLII_regin_nisa_created";
    private static final String MARKET_ID = "itoron_market";

    /**
     * Creates NISA and registers with ImportantPeopleAPI.
     * Only places on Itoron if it's Draconis-owned already (see {@link #isDraconisOwned(MarketAPI)}).
     * On existing saves, ensures registration with ImportantPeopleAPI.
     */
    public static void createOrEnsureRegistered() {
        MarketAPI itoronMarket = Global.getSector().getEconomy().getMarket(MARKET_ID);

        if (itoronMarket == null) {
            log.warn("Draconis: Itoron market not found - cannot create NISA");
            return;
        }

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            ensureRegistered(itoronMarket);
            return;
        }

        PersonAPI nisa = Global.getFactory().createPerson();
        nisa.setId(PERSON_ID);
        nisa.setFaction(DRACONIS);
        nisa.setGender(FullName.Gender.FEMALE);
        nisa.setRankId(Ranks.AGENT);
        nisa.setPostId(Ranks.POST_COMMODITIES_AGENT);

        nisa.addTag("XLII_regin_nisa");

        nisa.getName().setFirst("N.I.S.A.");
        nisa.getName().setLast("");

        // Placeholder pending dedicated art for NISA/Regin Armaments.
        nisa.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_NISA"));

        Global.getSector().getImportantPeople().addPerson(nisa);

        if (isDraconisOwned(itoronMarket)) {
            itoronMarket.addPerson(nisa);
            itoronMarket.getCommDirectory().addPerson(nisa, 0);
        }

        Global.getSector().getMemoryWithoutUpdate().set(CREATED_FLAG, true);

        log.info("Draconis: NISA created");
    }

    /**
     * Adds/removes NISA from Itoron's comm directory based on Draconis ownership of Itoron.
     */
    public static void updatePlacement() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            return;
        }

        MarketAPI itoronMarket = Global.getSector().getEconomy().getMarket(MARKET_ID);
        if (itoronMarket == null) return;

        PersonAPI nisa = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (nisa == null) return;

        boolean eligible = isDraconisOwned(itoronMarket);
        boolean isOnMarket = isOnMarket(itoronMarket);

        if (eligible && !isOnMarket) {
            itoronMarket.addPerson(nisa);
            itoronMarket.getCommDirectory().addPerson(nisa, 0);
            log.info("Draconis: NISA added to Itoron comm directory");
        } else if (!eligible && isOnMarket) {
            itoronMarket.removePerson(nisa);
            itoronMarket.getCommDirectory().removePerson(nisa);
            log.info("Draconis: NISA removed from Itoron comm directory");
        }
    }

    private static boolean isDraconisOwned(MarketAPI itoronMarket) {
        return DRACONIS.equals(itoronMarket.getFactionId());
    }

    private static void ensureRegistered(MarketAPI itoronMarket) {
        PersonAPI nisa = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (nisa != null) return;

        for (PersonAPI person : itoronMarket.getPeopleCopy()) {
            if (PERSON_ID.equals(person.getId())) {
                Global.getSector().getImportantPeople().addPerson(person);
                log.info("Draconis: Migrated NISA to ImportantPeopleAPI");
                return;
            }
        }

        log.warn("Draconis: NISA flagged as created but not found anywhere");
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
