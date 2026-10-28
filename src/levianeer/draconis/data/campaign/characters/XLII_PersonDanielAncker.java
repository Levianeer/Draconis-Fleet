package levianeer.draconis.data.campaign.characters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import org.apache.log4j.Logger;

import static levianeer.draconis.data.campaign.ids.Factions.INTELLIGENCE_OFFICE;

/**
 * Daniel Ancker - AIO operative, Records and Audit Group custody element (compartment IRON MOTH;
 * see {@code .claude/lore/rag.md}). Registered once with ImportantPeopleAPI so he can be looked
 * up by id wherever he actually appears: the AIO bastion escort scene ({@code XLII_ancker_escort_*}
 * rows, rules.csv, dispatched from {@code XLII_CampaignPlugin}'s bastion interception) and the dynamic crisis
 * payment-deal bar event ({@code DraconisAIOPaymentBarEvent}, any player-owned colony).
 * <p>
 * No fixed market placement or comm-directory entry (author request, this session) - he used to
 * be placed at Ring-Port specifically (hidden until revealed), which stopped serving any purpose
 * once both of those scenes moved onto the bastion/dynamic bar event instead. See
 * {@link #migrateOffRingPort()} for the one-time cleanup this change needs on existing saves, and
 * {@code XLII_AIOOperativeBarEventCreator}'s own doc comment for the retired Ring-Port bar scene.
 */
public class XLII_PersonDanielAncker {

    private static final Logger log = Global.getLogger(XLII_PersonDanielAncker.class);

    public static final String PERSON_ID = "XLII_aio_operative_Daniel";
    private static final String CREATED_FLAG = "$XLII_aio_operative_created";
    private static final String MIGRATED_OFF_RING_PORT_FLAG = "$XLII_aio_operative_migrated_off_ring_port";

    /** Only needed by {@link #migrateOffRingPort()}, to strip any pre-existing placement. */
    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    /**
     * Set by {@link #markGone()} once Ancker's escape is established (XLII_KoriStrike's
     * showEscapeReveal(), Burn the Machine win path only - he never appears in the Uplink to God
     * failure branch). Reserved for any future gating that needs to know he's no longer reachable;
     * nothing currently reads it, since he has no placement left for it to protect. Same bug shape
     * as XLII_PersonEmilAugust.GONE_FLAG.
     */
    private static final String GONE_FLAG = "$XLII_aio_operative_gone";

    /**
     * Creates AIO Operative Daniel Ancker and registers with ImportantPeopleAPI. On existing
     * saves, ensures registration and runs the one-time Ring-Port placement cleanup.
     */
    public static void createOrEnsureRegistered() {
        migrateOffRingPort();

        if (Global.getSector().getImportantPeople().getPerson(PERSON_ID) != null) {
            return;
        }

        PersonAPI daniel = Global.getFactory().createPerson();
        daniel.setId(PERSON_ID);
        daniel.setFaction(INTELLIGENCE_OFFICE);
        daniel.setGender(FullName.Gender.MALE);
        daniel.setRankId(Ranks.SPECIAL_AGENT);
        daniel.setPostId(Ranks.POST_SPECIAL_AGENT);

        daniel.addTag("XLII_aio_operative");

        daniel.getName().setFirst("Daniel");
        daniel.getName().setLast("Ancker");

        daniel.setPortraitSprite(Global.getSettings().getSpriteName("characters", "XLII_daniel_ancker"));

        Global.getSector().getImportantPeople().addPerson(daniel);
        Global.getSector().getMemoryWithoutUpdate().set(CREATED_FLAG, true);

        log.info("Draconis: AIO Operative Daniel Ancker created (no market placement)");
    }

    /**
     * One-time cleanup (author request) for saves from before Ring-Port placement was retired:
     * strips Daniel from Ring-Port's person list and comm directory if he's still there, so the
     * stale entry doesn't linger forever just because nothing ever touches that market again.
     * Idempotent - gated on {@link #MIGRATED_OFF_RING_PORT_FLAG} so it only does real work once.
     */
    public static void migrateOffRingPort() {
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(MIGRATED_OFF_RING_PORT_FLAG)) return;
        Global.getSector().getMemoryWithoutUpdate().set(MIGRATED_OFF_RING_PORT_FLAG, true);

        MarketAPI ringPortMarket = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (ringPortMarket == null) return;

        PersonAPI daniel = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (daniel == null) {
            for (PersonAPI person : ringPortMarket.getPeopleCopy()) {
                if (PERSON_ID.equals(person.getId())) {
                    daniel = person;
                    break;
                }
            }
        }
        if (daniel == null) return;

        if (ringPortMarket.getCommDirectory().getEntryForPerson(daniel) != null) {
            ringPortMarket.getCommDirectory().removePerson(daniel);
        }
        ringPortMarket.removePerson(daniel);
        log.info("Draconis: AIO Operative Daniel Ancker migrated off Ring-Port (one-time cleanup)");
    }

    /**
     * Marks Ancker permanently gone - called once his escape is established
     * (XLII_KoriStrike.showEscapeReveal(), fires on all three of August's fates). Does not remove
     * him from ImportantPeopleAPI, since his parting taunt (showAnckerTaunt()) still looks him up
     * by id for the portrait.
     */
    public static void markGone() {
        Global.getSector().getMemoryWithoutUpdate().set(GONE_FLAG, true);
    }
}
