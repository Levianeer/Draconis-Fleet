package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import org.apache.log4j.Logger;

import static levianeer.draconis.data.campaign.ids.Factions.INTELLIGENCE_OFFICE;

/**
 * Haspel Monroe - Director of the Alliance Intelligence Office.
 * Registered with ImportantPeopleAPI only - not placed at any market or comm directory.
 * Her black site (XLII_OfficeSystem's Ladon) has no MarketAPI to place her on. Set up ahead
 * of a future story beat; no dialogue or reveal path exists yet.
 */
public class XLII_PersonHaspelMonroe {

    private static final Logger log = Global.getLogger(XLII_PersonHaspelMonroe.class);

    public static final String PERSON_ID = "XLII_aio_director_Haspel";
    private static final String CREATED_FLAG = "$XLII_aio_director_created";

    /**
     * Creates AIO Director Haspel Monroe and registers with ImportantPeopleAPI.
     * On existing saves, ensures registration with ImportantPeopleAPI.
     */
    public static void createOrEnsureRegistered() {
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(CREATED_FLAG)) {
            ensureRegistered();
            return;
        }

        PersonAPI Haspel = Global.getFactory().createPerson();
        Haspel.setId(PERSON_ID);
        Haspel.setFaction(INTELLIGENCE_OFFICE);
        Haspel.setGender(FullName.Gender.FEMALE);
        Haspel.setRankId(Ranks.SPECIAL_AGENT);
        Haspel.setPostId(Ranks.POST_INTELLIGENCE_DIRECTOR);

        Haspel.addTag("XLII_aio_director");

        Haspel.getName().setFirst("Haspel");
        Haspel.getName().setLast("Monroe");

        Haspel.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_haspel_monroe"));

        Global.getSector().getImportantPeople().addPerson(Haspel);

        Global.getSector().getMemoryWithoutUpdate().set(CREATED_FLAG, true);

        log.info("Draconis: AIO Director Haspel Monroe created");
    }

    private static void ensureRegistered() {
        PersonAPI Haspel = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (Haspel != null) return;

        log.warn("Draconis: AIO Director flagged as created but not found in ImportantPeopleAPI");
    }
}
