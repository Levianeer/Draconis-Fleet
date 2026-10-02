package levianeer.draconis.data.campaign.intel.blind_eye;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.INTELLIGENCE_OFFICE;

/**
 * EveryFrameScript that spawns a small Office fleet to intercept the player once
 * {@code $XLII_officeReferralPending} is set, following the same shape as
 * {@code XLII_FafnirSystemMonitor} (tagged fleet, re-issued INTERCEPT assignment,
 * {@code XLII_CampaignPlugin} catches the resulting interaction). Unlike that monitor, this one
 * spawns its own fleet rather than commandeering an existing patrol - the Office has no roaming
 * fleets in normal space to borrow.
 * <p>
 * The dispatched fleet is forced non-hostile ({@link MemFlags#MEMORY_KEY_MAKE_NON_HOSTILE}) so
 * the player can actually reach it to talk, regardless of any faction-relationship swings from
 * the AIO crisis systems elsewhere in the mod.
 * <p>
 * The fleet is kept on a short leash: if the player outruns it and the gap grows past
 * {@link #MAX_LEASH_DISTANCE}, it's repositioned back to {@link #SPAWN_DISTANCE} of the player
 * instead of being left to search the system on its own. A one-shot story courier isn't meant to
 * be evadable, and plain {@link FleetAssignment#INTERCEPT} re-issued at a fleet that has lost
 * contact just makes it wander toward the player's last known position and search from there -
 * fine for an ordinary patrol, not for a scripted beat that has to land.
 * <p>
 * Registered immediately by {@code XLII_BeginOfficeContact} (fired from
 * {@code XLII_nanoforge_discuss_eligible}'s script) and re-registered on load by
 * {@code XLII_ModPlugin.onGameLoad()} via {@link #shouldRegister()} while work remains.
 */
public class XLII_OfficeContactMonitor implements EveryFrameScript {

    private static final Logger log = Global.getLogger(XLII_OfficeContactMonitor.class);

    /** Memory key on sector: set once the courier dialogue has been delivered. */
    public static final String CONTACT_DONE_FLAG = "$XLII_officeContactDone";

    /** Tag on the spawned fleet itself; XLII_CampaignPlugin looks for this. */
    public static final String MEM_FLEET_TAG = "$XLII_officeContactFleetTag";

    private static final float COMBAT_POINTS = 80f;
    private static final float INTERCEPT_ASSIGNMENT_DAYS = 3f;
    private static final float SPAWN_DISTANCE = 3000f;

    /** If the fleet falls further than this from the player, it's repositioned rather than left to search. */
    private static final float MAX_LEASH_DISTANCE = 6000f;

    /** Safety cap on how long onContactFired() waits for the Transverse Jump ability before despawning anyway. */
    private static final float DESPAWN_TIMEOUT_SECONDS = 15f;

    private final IntervalUtil checkInterval = new IntervalUtil(5f, 5f);
    private final Random random = new Random();

    private CampaignFleetAPI interceptingFleet = null;
    private boolean done = false;

    @Override
    public boolean isDone() { return done; }

    @Override
    public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        if (done) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.getBoolean(CONTACT_DONE_FLAG)) {
            done = true;
            return;
        }
        if (!mem.getBoolean("$XLII_officeReferralPending")) return;

        checkInterval.advance(amount);
        if (!checkInterval.intervalElapsed()) return;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return;

        LocationAPI loc = playerFleet.getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) return; // wait until the player is in a real system

        if (!isFleetAlive(interceptingFleet, loc)) {
            interceptingFleet = spawnContactFleet((StarSystemAPI) loc, playerFleet);
            if (interceptingFleet == null) return;
            log.info("Draconis: Office contact - dispatching " + interceptingFleet.getNameWithFaction());
        }

        if (interceptingFleet.getBattle() == null) {
            if (Misc.getDistance(interceptingFleet, playerFleet) > MAX_LEASH_DISTANCE) {
                repositionNearPlayer(interceptingFleet, playerFleet);
            }
            interceptingFleet.clearAssignments();
            interceptingFleet.addAssignment(
                    FleetAssignment.INTERCEPT, playerFleet, INTERCEPT_ASSIGNMENT_DAYS, (Script) null);
        }
    }

    // -------------------------------------------------------------------------
    // Effects applied by XLII_CampaignPlugin when the interaction fires
    // -------------------------------------------------------------------------

    /**
     * Called by {@link levianeer.draconis.data.campaign.XLII_CampaignPlugin} when it intercepts
     * an interaction with the tagged fleet. Clears assignments/tag, then has the fleet perform a
     * Transverse Jump out before despawning - it has nothing left to do once the message is
     * delivered, but a Draconis courier snapping instantly out of existence read as a bug rather
     * than a diegetic exit.
     */
    public static void onContactFired(CampaignFleetAPI fleet) {
        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().unset(MEM_FLEET_TAG);
        despawnAfterTransverseJump(fleet);
    }

    /**
     * Activates the vanilla Transverse Jump ability ({@link Abilities#TRANSVERSE_JUMP},
     * {@code FractureJumpAbility}) on the fleet, then despawns it once the jump has played out.
     * Works on a non-player fleet the same way {@code RemnantThemeGenerator} arms
     * {@link Abilities#TRANSPONDER} on a Remnant fleet - {@link SectorEntityToken#addAbility} /
     * {@code getAbility(id).activate()} isn't player-only.
     * <p>
     * {@code FractureJumpAbility.isUsable()} allows any AI-mode fleet regardless of fuel
     * ({@code getFleet().isAIMode() ||} short-circuits the fuel check), so this always succeeds
     * for the contact fleet in practice; the timeout below is a pure leak-prevention fallback in
     * case the ability can't activate for some other reason (e.g. mid hyperspace transition).
     */
    private static void despawnAfterTransverseJump(final CampaignFleetAPI fleet) {
        if (fleet.isDespawning()) return;

        if (!fleet.hasAbility(Abilities.TRANSVERSE_JUMP)) {
            fleet.addAbility(Abilities.TRANSVERSE_JUMP);
        }
        AbilityPlugin ability = fleet.getAbility(Abilities.TRANSVERSE_JUMP);
        if (ability != null && ability.isUsable()) {
            ability.activate();
        }

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;
            private float elapsed = 0f;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return false; }

            @Override
            public void advance(float amount) {
                elapsed += amount;

                AbilityPlugin ability = fleet.getAbility(Abilities.TRANSVERSE_JUMP);
                boolean jumpFinished = ability == null || !ability.isInProgress();

                if (fleet.isDespawning() || jumpFinished || elapsed > DESPAWN_TIMEOUT_SECONDS) {
                    done = true;
                    if (!fleet.isDespawning()) fleet.despawn();
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Fleet helpers
    // -------------------------------------------------------------------------

    private static boolean isFleetAlive(CampaignFleetAPI fleet, LocationAPI loc) {
        return fleet != null
                && !fleet.isDespawning()
                && loc.equals(fleet.getContainingLocation())
                && fleet.getMemoryWithoutUpdate().getBoolean(MEM_FLEET_TAG);
    }

    private CampaignFleetAPI spawnContactFleet(StarSystemAPI system, CampaignFleetAPI playerFleet) {
        FleetParamsV3 params = new FleetParamsV3(
                null,
                INTELLIGENCE_OFFICE,
                50f,
                FleetTypes.INVESTIGATORS,
                COMBAT_POINTS,
                0f, 0f, 0f, 0f, 0f,
                0f);
        params.officerLevelBonus = 5;
        params.quality = 6;
        params.random = random;
        params.timestamp = Global.getSector().getClock().getTimestamp();

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.getMemoryWithoutUpdate().set(MEM_FLEET_TAG, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);

        system.addEntity(fleet);
        repositionNearPlayer(fleet, playerFleet);

        return fleet;
    }

    /** Drops {@code fleet} at {@link #SPAWN_DISTANCE} of {@code playerFleet}, random bearing. */
    private void repositionNearPlayer(CampaignFleetAPI fleet, CampaignFleetAPI playerFleet) {
        float angle = random.nextFloat() * 360f;
        SectorEntityToken anchor = playerFleet;
        float x = anchor.getLocation().x + SPAWN_DISTANCE * (float) Math.cos(Math.toRadians(angle));
        float y = anchor.getLocation().y + SPAWN_DISTANCE * (float) Math.sin(Math.toRadians(angle));
        fleet.setLocation(x, y);
    }

    // -------------------------------------------------------------------------
    // Registration guard
    // -------------------------------------------------------------------------

    /** True if there is still pending work: referral made, courier hasn't delivered yet. */
    public static boolean shouldRegister() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        return mem.getBoolean("$XLII_officeReferralPending") && !mem.getBoolean(CONTACT_DONE_FLAG);
    }
}
