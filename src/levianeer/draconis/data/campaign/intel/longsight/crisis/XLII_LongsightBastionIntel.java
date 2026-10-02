package levianeer.draconis.data.campaign.intel.longsight.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ArrowData;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;
import levianeer.draconis.data.campaign.econ.XLII_MarketTransfer;
import levianeer.draconis.data.campaign.ids.Factions;
import levianeer.draconis.data.campaign.intel.aicore.raids.DraconisAICoreRaidFactor;
import levianeer.draconis.data.campaign.intel.longsight.XLII_LongsightOfficerPlugin;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A single Office Takeover crisis Bastion - a standing, hostile battlestation fleet, plus its own
 * patrol garrison. Modeled on vanilla's {@code PirateBaseIntel} for the overall shape, and on
 * {@code XLII_OfficeSystem.addBastion()}/{@code XLII_OfficeGarrisonManager} for the fleet-as-station
 * mechanics - like Ladon's bastion, this isn't a real economy market with industries.
 * <p>
 * <b>Deliberately not self-registering as a script</b> - ticked only through
 * {@link XLII_LongsightCrisisManager}'s own {@code active} list. A second, independent registration
 * here would double-tick {@link #advanceImpl(float)} every frame.
 * <p>
 * <b>Active fleets are {@code XLII_intelligence_office}, not {@code XLII_draconis}</b> - avoids these
 * fleets registering hostile to themselves/to Ladon's own bastion (a real bug), and to ordinary
 * Draconis assets. The territory still ends up Draconis-owned: once Stage 4 resolves a capture, the
 * market transfers to real {@code XLII_draconis}. Hostility to everyone, including the player, is
 * forced independently of faction via {@link #applyCrisisHostility(CampaignFleetAPI)}.
 */
public class XLII_LongsightBastionIntel extends BaseIntelPlugin implements FleetEventListener {

    private static final Logger log = Global.getLogger(XLII_LongsightBastionIntel.class);

    /**
     * Set on every crisis-owned fleet (Bastion, garrison, invasion) at spawn - lets
     * {@link XLII_LongsightCrisisCombatListener} tell these apart from Ladon's own pre-existing
     * Intelligence Office bastion/garrison, which share the same faction tag but are a different,
     * already-resolved story beat (Burn the Machine) with its own separate consequences.
     */
    public static final String CRISIS_FLEET_FLAG = "$XLII_longsightCrisisFleet";

    /** The vanilla Remnant Nexus hull, same as Ladon's bastion - not a new asset. */
    private static final String BASTION_VARIANT = "remnant_station2_Standard";

    /** Placeholder - not derived from the star's actual radius yet. See checklist Stage 8. */
    private static final float BASTION_ORBIT_RADIUS = 3000f;

    // ==================== Stage 6.5 (revised): intel-linked notifications ====================
    // Matches vanilla's PirateBaseIntel/PunitiveExpeditionIntel idiom: a sentinel Object per event
    // type, fired via sendUpdateIfPlayerHasIntel(), read back by getName()/addBulletPoints() via
    // getListInfoParam() to render event-specific text, so clicking the notification opens this
    // intel entry directly. (Plain addMessage() toasts were tried first but were disconnected from
    // the intel list - clicking one did nothing.)
    public static final Object INVASION_LAUNCHED_PARAM = new Object();
    public static final Object SIEGE_BEGUN_PARAM = new Object();
    public static final Object INVASION_CAPTURED_PARAM = new Object();
    public static final Object INVASION_DESTROYED_PARAM = new Object();

    /**
     * Snapshot of the target's name/system at the moment of the most recent notable event - kept
     * independent of {@link #invasionTarget}, which {@link #finishInvasion} nulls out right after
     * {@link #resolveInvasionOutcome} runs, so {@code getName()}/{@code addBulletPoints()} can still
     * describe what happened after the live fields have moved on.
     */
    private String lastEventTargetName;
    private String lastEventTargetSystem;

    private final StarSystemAPI system;
    private final String fleetId;

    // NOT transient, deliberately - this is what caused the exact bug it looks like it would guard
    // against. XStream drops `transient` fields across save/load; with it here, bastionFleet came
    // back null after every reload, isBastionGone() read that as "destroyed", and advanceImpl()
    // self-ended the intel entry next tick - even though the actual fleet was untouched and still
    // there. Fixed by keeping both references as normal persisted fields.
    private CampaignFleetAPI bastionFleet;
    private XLII_LongsightCrisisGarrisonManager garrisonManager;

    // ==================== Stage 3: targeting + invasion dispatch ====================
    // TEMP, cranked extreme to speed-run testing the "Longsight wins" end state - see the matching
    // note in XLII_LongsightCrisisManager. NOT the real pacing intent; restore before Stage 8's
    // tuning pass. Only one invasion in flight per Bastion at a time either way.
    private static final float INVASION_INTERVAL_MIN_DAYS = 0.5f;
    private static final float INVASION_INTERVAL_MAX_DAYS = 1f;
    private static final float INVASION_TARGET_FAILURE_COOLDOWN_DAYS = 2f;
    private static final float INVASION_COMBAT_POINTS = 600f;

    // ==================== Siege pacing ====================
    // A single flat day-countdown ending in one dice roll could resolve almost instantly under time
    // compression, giving no real window to react even while actively present. Replaced with the same
    // pacing shape DraconisFleetHostileActivityFactor's punitive expeditions use (check frequently,
    // resolve through several small capped passes, not one all-or-nothing event) - not its
    // bombardment/stability-damage mechanics, which stay out of scope here.
    //
    // The siege is a fixed number of discrete "assault" passes. Each pass is a pure pacing gate - no
    // side effect on the market - and re-verifies the fleet is still viable before counting (reuses
    // getViableInvasionFleetOrNull(), the same check the resolution safety-net relies on).
    private static final int BASE_REQUIRED_ASSAULTS = 1;

    /**
     * Colony size and defense score both make a siege take longer. Placeholder scale factors, not
     * tuned - see checklist Stage 8.
     */
    private static final float SIZE_ASSAULTS_PER_POINT = 0.5f;
    private static final int DEFENSE_ASSAULTS_PER_TIER = 1;

    /**
     * Defense score proxy: which orbital defense station (if any) the market has, ascending by
     * vanilla strength. Same tiered list `DraconisPunitiveExpedition.disruptMilitaryStation()`/
     * `DraconisAIOTracker.disruptDefensesForInvasion()` use (there descending, to find the strongest
     * one to disrupt; here ascending, to score it).
     */
    private static final String[] DEFENSE_STATION_TIERS_ASCENDING = {
            Industries.ORBITALSTATION, Industries.ORBITALSTATION_MID, Industries.ORBITALSTATION_HIGH,
            Industries.BATTLESTATION, Industries.BATTLESTATION_MID, Industries.BATTLESTATION_HIGH,
            Industries.STARFORTRESS, Industries.STARFORTRESS_MID, Industries.STARFORTRESS_HIGH,
    };

    // TEMP, cranked for the same speed-run reason as the rest of Stage 3's numbers - restore to
    // something sane before Stage 8's real tuning pass.
    private static final float ASSAULT_INTERVAL_MIN_DAYS = 1f;
    private static final float ASSAULT_INTERVAL_MAX_DAYS = 2f;

    /**
     * Time-compression safety cap, deliberately less than ASSAULT_INTERVAL_MIN_DAYS so at least two
     * ticks are required per assault regardless of tick size - a single oversized tick (heavy time
     * compression) can no longer complete a whole siege in one step. This, not the assault count
     * alone, is what guarantees a real multi-tick window to react.
     */
    private static final float MAX_ASSAULT_PROGRESS_PER_TICK_DAYS = 0.4f;

    /**
     * How often the siege/defend fleet's {@code HOLD} order is re-issued - see
     * {@link #issueHoldAssignment(CampaignFleetAPI, MarketAPI)}'s doc for why this exists at all.
     * Vanilla's own {@code BaseAssignmentAI.checkRaid()} reissues on a similar ~0.3-0.7 day cadence;
     * this is deliberately a bit longer since our own assault-count pacing (not a raid-effect
     * callback) is what actually drives progress here.
     */
    private static final float HOLD_REISSUE_INTERVAL_DAYS = 1f;

    /** Deliberately short-lived - reissued well before it would expire on its own; see
     *  {@link #HOLD_REISSUE_INTERVAL_DAYS}. */
    private static final float HOLD_ASSIGNMENT_DURATION_DAYS = 3f;

    /**
     * Placeholder - not tuned. A fleet reduced below this fraction of its spawn strength is treated
     * as defeated even if a handful of ships technically survive - a mauled remnant can't credibly
     * hold a siege or carry out an invasion.
     */
    private static final float INVASION_DEFEAT_STRENGTH_FRACTION = 0.25f;

    private final IntervalUtil invasionInterval =
            new IntervalUtil(INVASION_INTERVAL_MIN_DAYS, INVASION_INTERVAL_MAX_DAYS);

    private CampaignFleetAPI invasionFleet;

    /**
     * Bug: the siege timer kept running (and the invasion still resolved) even after the invasion
     * fleet was destroyed mid-siege, since checking {@code isDespawning()} on the held
     * {@code invasionFleet} reference doesn't reliably reflect actual destruction. Fixed by looking
     * the fleet back up by its own stable id every tick instead - same pattern
     * {@code XLII_BastionDestructionMonitor} uses for detecting Ladon's own bastion's destruction.
     */
    private String invasionFleetId;
    private MarketAPI invasionTarget;

    /**
     * Existence alone doesn't mean the invasion fleet is still viable - a fleet mauled to a single
     * frigate still exists. Captured at spawn, compared against current
     * {@link com.fs.starfarer.api.campaign.CampaignFleetAPI#getFleetPoints()} every tick; dropping
     * below {@link #INVASION_DEFEAT_STRENGTH_FRACTION} of the original counts as defeated.
     * <p>
     * <b>Deliberately fleet points, not {@code getEffectiveStrength()}.</b> The latter factors in
     * each ship's current hull%/CR, which nosedive right after any real fight and only recover over
     * hours/days - so a fleet that just won a costly fight against a defense station would read as
     * defeated on the very next tick, abandoning it at the exact moment it should start the siege.
     * {@code getFleetPoints()} only drops when a ship is actually lost, matching the same stable
     * metric vanilla's {@code BaseAssignmentAI.giveRaidOrder()} uses for its own before/after check.
     */
    private float invasionFleetInitialFP = -1f;

    /** -1 means "not currently sieging". Only meaningful while invasionFleet is non-null. */
    private int assaultsRequired = -1;
    private int assaultsCompleted = 0;
    private final IntervalUtil assaultInterval =
            new IntervalUtil(ASSAULT_INTERVAL_MIN_DAYS, ASSAULT_INTERVAL_MAX_DAYS);

    /** Accumulator for {@link #HOLD_REISSUE_INTERVAL_DAYS}; reset in {@link #beginSiege}. */
    private float siegeHoldTimer = 0f;

    /**
     * How often the pre-siege travel order is re-issued - see {@link #issueTravelAssignment}'s doc
     * for why a one-shot assignment wasn't enough. Same cadence as the siege-phase HOLD reissue.
     */
    private static final float TRAVEL_REISSUE_INTERVAL_DAYS = HOLD_REISSUE_INTERVAL_DAYS;

    /** Accumulator for {@link #TRAVEL_REISSUE_INTERVAL_DAYS}; reset in {@link #launchInvasion} and
     *  {@link #finishInvasion}. */
    private float travelReissueTimer = 0f;

    // ==================== Post-resolution: defend, then despawn ====================
    // A resolved invasion fleet holds the market for a while, then leaves cleanly via
    // CampaignFleetAPI.despawn() (same call XLII_KoriStrike/XLII_RingPortAssault use), rather than
    // being abandoned in place or blocking this Bastion's next invasion.
    //
    // Decoupled from invasionFleet/invasionTarget/assaultsRequired, which finishInvasion() clears
    // immediately so the Bastion can launch its next invasion right away, independent of this fleet's
    // own defend-then-despawn arc. A list, not a single field, since a fast cadence can resolve a
    // second invasion before the first one's defend period ends.
    //
    // Known limitation: if this Bastion is destroyed while a defending fleet is still active, that
    // fleet is orphaned (never ticked again, never despawned) and just sits at the market
    // indefinitely - harmless, but unresolved. Would need tracking independent of any one Bastion.

    private static final float POST_RESOLUTION_DEFEND_DAYS = 60f;
    private static final String POST_RESOLUTION_DESPAWN_TIMESTAMP_KEY = "$XLII_longsightDefendDespawnAt";

    /**
     * Bug: fleet despawned instantly after besieging its target instead of defending it first.
     * {@code CampaignClockAPI.getTimestamp()} is a raw millisecond counter (86,400,000 ms/day,
     * confirmed against vanilla's decompiled {@code BaseIntelPlugin.createIntelInfo()}) - a
     * completely different unit from {@code convertToSeconds(days)}, which measures real wall-clock
     * seconds of unpaused play, not calendar time. The original code added a tiny
     * {@code convertToSeconds(30f)} offset to a millisecond-scale counter, so "30 days from now" was
     * effectively "now," and the next tick despawned the fleet immediately. Fixed by converting days
     * to timestamp units directly via this ms-per-day constant.
     * <p>
     * The same incorrect {@code getTimestamp() + (long) convertToSeconds(days)} idiom also exists in
     * {@code DraconisAICoreRaidFactor.setTargetFailureCooldown()} and
     * {@code DraconisAICoreRaidManager.startCooldown()} - not fixed here since that would change
     * already-live cooldown behavior outside this crisis system's scope.
     */
    private static final long MS_PER_DAY = 24L * 60L * 60L * 1000L;

    private static long daysToTimestampUnits(float days) {
        return (long) (days * MS_PER_DAY);
    }

    private static class DefendingFleet {
        final String fleetId;
        final MarketAPI market;
        /** Per-fleet accumulator for {@link #HOLD_REISSUE_INTERVAL_DAYS} - each defending fleet
         *  needs its own, since the list can hold several at once. */
        float holdTimer = 0f;
        DefendingFleet(String fleetId, MarketAPI market) {
            this.fleetId = fleetId;
            this.market = market;
        }
    }

    private final java.util.List<DefendingFleet> defendingFleets = new java.util.ArrayList<>();

    public XLII_LongsightBastionIntel(StarSystemAPI system) {
        this.system = system;
        this.fleetId = "XLII_longsight_bastion_" + Misc.genUID();

        PlanetAPI anchor = system.getStar();
        if (anchor == null) {
            endImmediately();
            return;
        }

        spawnBastionFleet(system, anchor);
        spawnGarrison(system);

        // forceNoMessage is conditional on the crisis-wide reveal state: a Bastion spawned before the
        // reveal must suppress its creation message (matches PirateBaseIntel's own hidden-base case);
        // one spawned after behaves like an ordinary addIntel(this) call.
        Global.getSector().getIntelManager().addIntel(this, !XLII_LongsightCrisisManager.isRevealed());

        // See updateImportance()'s own doc for why importance is gated instead of unconditionally
        // true - a freshly-spawned Bastion with no target starts unimportant either way.
        updateImportance();

        log.info("Draconis: Longsight Bastion intel created in [" + system.getName() + "], id=" + fleetId);
    }

    // -------------------------------------------------------------------------
    // Spawning
    // -------------------------------------------------------------------------

    private void spawnBastionFleet(StarSystemAPI system, PlanetAPI anchor) {
        Random random = new Random();

        CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(Factions.INTELLIGENCE_OFFICE, FleetTypes.BATTLESTATION, null);
        fleet.setId(fleetId);

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, BASTION_VARIANT);
        fleet.getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());

        fleet.getMemoryWithoutUpdate().set(CRISIS_FLEET_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        applyCrisisHostility(fleet);
        fleet.setStationMode(true);
        fleet.setAI(null);
        fleet.clearAbilities();
        fleet.getDetectedRangeMod().modifyFlat("gen", 500f);

        PersonAPI commander = new XLII_LongsightOfficerPlugin().createPerson(
                XLII_LongsightOfficerPlugin.CORE_ID, Factions.INTELLIGENCE_OFFICE, random);
        fleet.setCommander(commander);
        fleet.getFlagship().setCaptain(commander);

        system.addEntity(fleet);
        fleet.setCircularOrbitPointingDown(anchor, 0f, BASTION_ORBIT_RADIUS, 50f);

        // Hidden-until-reveal, matching vanilla's PirateBaseIntel (setDiscoverable(true) gates
        // "requires active discovery" rather than detectable on sight). Cleared for already-existing
        // Bastions at reveal time by onCrisisRevealed() below.
        if (!XLII_LongsightCrisisManager.isRevealed()) {
            fleet.setDiscoverable(true);
        }

        this.bastionFleet = fleet;
    }

    /**
     * Called by {@code XLII_LongsightCrisisManager.checkReveal()} when the crisis reveal fires -
     * clears this Bastion's entity-level discovery gate, matching vanilla's
     * {@code PirateBaseIntel.makeKnown()}. {@link #isHidden()} needs no matching update - it already
     * reads the crisis-wide reveal flag dynamically.
     */
    public void onCrisisRevealed() {
        if (bastionFleet != null) {
            bastionFleet.setDiscoverable(null);
        }
    }

    private void spawnGarrison(StarSystemAPI system) {
        garrisonManager = new XLII_LongsightCrisisGarrisonManager(system, bastionFleet, 5f);
        Global.getSector().addScript(garrisonManager);
    }

    /**
     * Forces hostility toward the player and every other faction, regardless of the fleet's real
     * (Intelligence Office) relationships - unconditional, unlike
     * {@code XLII_OfficeSystem.applyHostility()}, which only applies while Ladon's access flag is
     * unset.
     * <p>
     * The bare {@code MEMORY_KEY_MAKE_HOSTILE} flag only covers the player ({@code
     * Misc.isFleetMadeHostileToFaction()} special-cases it); every other faction needs the
     * per-faction-suffixed key ({@code MEMORY_KEY_MAKE_HOSTILE + "_" + factionId}), set by the loop
     * below.
     * <p>
     * Three exclusions, each a real bug found by testing: {@code XLII_intelligence_office} (without
     * it, every crisis fleet was hostile to its own kind - Bastion, garrison, and invasion fleet all
     * attacking each other, plus Ladon's own pre-existing IO bastion/garrison); {@code XLII_draconis}
     * (missing this turned invasion fleets hostile to ordinary Draconis patrols the moment they
     * reached their target); and anything {@code XLII_MarketTransfer.isProtectedFromCrisisInvasion()}
     * excludes (matches {@link #pickInvasionTarget()}'s own targeting exclusion - otherwise a fleet
     * would open fire on a protected ally just passing by, despite that ally's markets never being
     * eligible targets in the first place).
     * <p>
     * <b>Bug fix: the per-faction exclusion must actively {@code unset()}, not just skip
     * {@code set()}.</b> This method is called every tick on every live crisis fleet (garrison
     * patrols, the Bastion's own station fleet, an invasion fleet once it reaches its target's
     * system - see each call site), so a faction that becomes protected only *after* a fleet
     * already set its hostility flag true would otherwise stay permanently hostile on that fleet -
     * skipping the {@code set()} call on a later tick does nothing to a flag already persisted true
     * from an earlier one. This is why "never target allies" could look inconsistent: an older
     * fleet (flagged before the alliance/reputation formed) stays hostile to that faction forever,
     * while a fleet spawned after the fact correctly never flags it at all.
     */
    public static void applyCrisisHostility(CampaignFleetAPI fleet) {
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);

        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            String id = faction.getId();
            if (Factions.INTELLIGENCE_OFFICE.equals(id) || Factions.DRACONIS.equals(id)) continue;

            String key = MemFlags.MEMORY_KEY_MAKE_HOSTILE + "_" + id;
            if (XLII_MarketTransfer.isProtectedFromCrisisInvasion(id)) {
                fleet.getMemoryWithoutUpdate().unset(key);
            } else {
                fleet.getMemoryWithoutUpdate().set(key, true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // BaseIntelPlugin
    // -------------------------------------------------------------------------

    @Override
    protected void advanceImpl(float amount) {
        if (isBastionGone()) {
            log.info("Draconis: Longsight Bastion destroyed in [" + system.getName() + "]");
            XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
            if (manager != null) manager.recordBastionDestroyed();
            endImmediately();
            return;
        }

        advanceInvasion(amount);
        advanceDefendingFleets(amount);
    }

    private boolean isBastionGone() {
        if (bastionFleet == null) return true;
        return bastionFleet.isDespawning() || bastionFleet.isEmpty()
                || bastionFleet.getContainingLocation() == null;
    }

    /**
     * Hidden until the crisis-wide reveal fires - one shared flag every Bastion reads dynamically
     * (see {@code XLII_LongsightCrisisManager.isRevealed()}), not per-instance state. Matches
     * vanilla's {@code PirateBaseIntel.isHidden()} shape, just keyed off the crisis as a whole.
     */
    @Override
    public boolean isHidden() {
        if (super.isHidden()) return true;
        return !XLII_LongsightCrisisManager.isRevealed();
    }

    @Override
    protected String getName() {
        // Placeholder - not real writing yet. See checklist Stage 8. Suffixed with the system name
        // so multiple concurrently active Bastions are distinguishable on the intel list.
        String base = "Unmarked Installation - " + system.getBaseName();

        // State-reactive suffix, matching PirateBaseIntel's own layered getName(): a pending
        // notification's param takes priority, then whatever's actually happening, then the default.
        Object param = getListInfoParam();
        if (param == INVASION_LAUNCHED_PARAM) return base + " - Task Force Dispatched";
        if (param == SIEGE_BEGUN_PARAM) return base + " - Under Siege";
        if (param == INVASION_CAPTURED_PARAM) return base + " - Target Captured";
        if (param == INVASION_DESTROYED_PARAM) return base + " - Target Destroyed";

        if (assaultsRequired >= 0) return base + " - Under Siege";
        if (invasionFleet != null) return base + " - Task Force Dispatched";
        return base;
    }

    @Override
    public String getIcon() {
        // Reverted to XLII_security_codes - the XLII_smuggling swap didn't stick. Bastions are told
        // apart by name/location (see getName()) rather than by icon.
        return Global.getSettings().getSpriteName("intel", "XLII_security_codes");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(Factions.INTELLIGENCE_OFFICE);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Factions.INTELLIGENCE_OFFICE);
        tags.add(Tags.INTEL_MILITARY);
        // Conditional, matching PirateBaseIntel's own INTEL_COLONIES tag - only worth surfacing
        // under "Colonies" while there's a live invasion threatening a player market.
        if (invasionTarget != null && invasionTarget.isPlayerOwned()) {
            tags.add(Tags.INTEL_COLONIES);
        }
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return bastionFleet;
    }

    /**
     * Draws a map arrow from the Bastion to whatever it's currently invading, matching
     * {@code PirateBaseIntel.getArrowData()}'s "from" resolution ({@code map.getIntelIconEntity(this)},
     * falling back to the raw entity). No arrow once there's no live target -
     * {@code BaseIntelPlugin}'s default already returns null for that case.
     */
    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        if (invasionTarget == null || invasionTarget.getPrimaryEntity() == null) return null;

        SectorEntityToken from = bastionFleet;
        if (map != null) {
            SectorEntityToken iconEntity = map.getIntelIconEntity(this);
            if (iconEntity != null) from = iconEntity;
        }

        ArrowData arrow = new ArrowData(from, invasionTarget.getPrimaryEntity());
        arrow.color = new Color(255, 55, 55, 255);

        List<ArrowData> result = new ArrayList<>();
        result.add(arrow);
        return result;
    }

    /**
     * Compact intel-list row content - matches {@code PirateBaseIntel.addBulletPoints()}'s own shape
     * (a pending notification's param takes priority, then current live status) so the list itself
     * shows a live one-line summary without needing to open the full description. Also called from
     * {@link #createSmallDescription} so the full panel doesn't duplicate this text.
     */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float opad = 10f;
        float initPad = mode == ListInfoMode.IN_DESC ? opad : pad;
        Color tc = getBulletColorForMode(mode);

        bullet(info);

        Object param = getListInfoParam();
        if (param == INVASION_LAUNCHED_PARAM && lastEventTargetName != null) {
            info.addPara("Task force dispatched toward " + lastEventTargetName
                    + (lastEventTargetSystem != null ? " in " + lastEventTargetSystem : ""),
                    initPad, tc);
        } else if (param == SIEGE_BEGUN_PARAM && lastEventTargetName != null) {
            info.addPara("Siege of " + lastEventTargetName + " has begun", initPad, tc);
        } else if (param == INVASION_CAPTURED_PARAM && lastEventTargetName != null) {
            info.addPara(lastEventTargetName + " has fallen to Draconis control", initPad,
                    Misc.getHighlightColor());
        } else if (param == INVASION_DESTROYED_PARAM && lastEventTargetName != null) {
            info.addPara(lastEventTargetName + " has been destroyed", initPad,
                    Misc.getNegativeHighlightColor());
        } else if (invasionTarget != null) {
            String targetSystem = invasionTarget.getStarSystem() != null
                    ? invasionTarget.getStarSystem().getBaseName() : "unknown space";
            if (assaultsRequired >= 0) {
                info.addPara("Besieging " + invasionTarget.getName() + " in " + targetSystem
                        + " - assault " + assaultsCompleted + " of " + assaultsRequired, initPad, tc);
            } else {
                info.addPara("Task force en route to " + invasionTarget.getName() + " in "
                        + targetSystem, initPad, tc);
            }
        } else {
            info.addPara("No task force currently active", initPad, tc);
        }

        unindent(info);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        // Matches PirateBaseIntel.createSmallDescription()'s opening move - the faction crest at the
        // top of the panel, same 128-height image call.
        info.addImage(getFactionForUIColors().getLogo(), width, 128, 0f);

        // Placeholder - not real writing yet. See checklist Stage 8.
        info.addPara(
            "An installation of unclear origin, running dark. Hostile to everything that comes near.",
            10f
        );

        addBulletPoints(info, ListInfoMode.IN_DESC);
    }

    // -------------------------------------------------------------------------
    // Stage 3: targeting + invasion dispatch
    // -------------------------------------------------------------------------
    // Target-picking adapted from vanilla PirateBaseIntel.pickTarget() - a size/distance-weighted
    // random pick among eligible markets, re-evaluated on its own cadence. Reuses
    // DraconisAICoreRaidFactor's existing failure-cooldown helpers.
    //
    // Stage 4: resolveInvasionOutcome() below dispatches the capture-or-destroy outcome via
    // XLII_MarketTransfer (capture) or DecivTracker (destroy).

    private void advanceInvasion(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        if (invasionFleet != null) {
            checkInvasionArrival(days);
            return;
        }

        invasionInterval.advance(days);
        if (!invasionInterval.intervalElapsed()) return;

        launchInvasion();
    }

    private void launchInvasion() {
        MarketAPI target = pickInvasionTarget();
        if (target == null) return;

        CampaignFleetAPI fleet = spawnInvasionFleet(target);
        if (fleet == null) return;

        fleet.getEventListeners().add(this);
        travelReissueTimer = 0f;
        issueTravelAssignment(fleet, target);

        invasionFleet = fleet;
        invasionFleetId = fleet.getId();
        invasionFleetInitialFP = fleet.getFleetPoints();
        invasionTarget = target;
        target.getMemoryWithoutUpdate().set(INVASION_IN_PROGRESS_KEY, true);
        updateImportance();

        lastEventTargetName = target.getName();
        lastEventTargetSystem = target.getStarSystem() != null
                ? target.getStarSystem().getBaseName() : "unknown space";
        sendUpdateIfPlayerHasIntel(INVASION_LAUNCHED_PARAM, true);

        log.info("Draconis: Longsight Bastion in [" + system.getName() + "] launched an invasion at "
                + target.getName());
    }

    /**
     * Fix for notification spam: with several Bastions active at once, notifying unconditionally on
     * every launch/siege/resolution meant a popup for every step of every Bastion's business, even
     * ones with nothing to do with the player. {@code BaseIntelPlugin}'s {@code isImportant()} is the
     * right lever - checked by {@code sendUpdateIfPlayerHasIntel(param, onlyIfImportant)} - so
     * importance now tracks whether this Bastion's invasion target is player-owned, re-evaluated
     * whenever {@link #invasionTarget} changes.
     */
    private void updateImportance() {
        setImportant(invasionTarget != null && invasionTarget.isPlayerOwned());
    }

    /**
     * Polls for the invasion fleet's arrival/outcome rather than relying solely on
     * {@link #reportBattleOccurred} - many low-security markets have no defenders to fight at all,
     * so "arrived with nothing to fight" needs to start a siege too, not just a battle win.
     */
    private void checkInvasionArrival(float days) {
        if (invasionFleet == null) return;

        CampaignFleetAPI liveFleet = getViableInvasionFleetOrNull();
        if (liveFleet == null) {
            finishInvasion(false);
            return;
        }

        // Refreshed every tick, matching vanilla's FGTravelAction (short expiry, re-set continuously)
        // - fix for fleets getting pulled into fights with anything they pass along the way.
        liveFleet.getMemoryWithoutUpdate().set(
                MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, 1f);

        if (invasionTarget == null || invasionTarget.getPrimaryEntity() == null) return;

        // Hostility is deliberately NOT applied at spawn (see spawnInvasionFleet()) - matches vanilla's
        // SindrianDiktatPunitiveExpedition, which only force-sets hostility once in its attack phase,
        // not during travel. Mirrored here as "reached the target's star system" rather than a fixed
        // distance, re-applied every tick once true.
        if (liveFleet.getStarSystem() == invasionTarget.getStarSystem()) {
            applyCrisisHostility(liveFleet);
        }

        if (assaultsRequired >= 0) {
            advanceSiege(days, liveFleet);
            return;
        }

        // Bug: fleets got pulled into fights along the way and never resumed their approach, since
        // launchInvasion() only set this assignment once - a battle the fleet broke off from left it
        // with no order and no code path here to notice. Fixed by reissuing periodically, same as
        // the siege phase's HOLD reissue and vanilla's FGTravelAction.directFleets().
        travelReissueTimer += days;
        if (travelReissueTimer >= TRAVEL_REISSUE_INTERVAL_DAYS) {
            travelReissueTimer = 0f;
            issueTravelAssignment(liveFleet, invasionTarget);
        }

        float dist = Misc.getDistance(liveFleet, invasionTarget.getPrimaryEntity());
        // Bug: fleets ignored stations, going straight to a peaceful siege while a market's defense
        // station was still fully intact - beginSiege() used to trigger on proximity alone, with no
        // check for remaining defenders. Fixed by gating the siege start on hasActiveDefenders();
        // until clear, the fleet keeps reissuing ATTACK_LOCATION (above) so a real defense force
        // keeps getting fought rather than ignored.
        if (dist < 500f && !hasActiveDefenders(liveFleet, invasionTarget)) {
            beginSiege(liveFleet);
        }
    }

    /**
     * True if any fleet near {@code target}'s primary entity is still hostile to {@code liveFleet} -
     * a defense-station fleet, garrison, or any other defender not yet beaten. Same "is this place
     * actually contested" check vanilla's {@code BaseAssignmentAI.checkColonyAction()} does before
     * raiding, just answering yes/no instead of picking a target.
     */
    private boolean hasActiveDefenders(CampaignFleetAPI liveFleet, MarketAPI target) {
        SectorEntityToken anchor = target.getPrimaryEntity();
        if (anchor == null) return false;

        for (CampaignFleetAPI other : Misc.getNearbyFleets(anchor, 1000f)) {
            if (other == liveFleet) continue;
            if (other.isEmpty() || other.isDespawning()) continue;
            if (other.isHostileTo(liveFleet)) return true;
        }
        return false;
    }

    /**
     * Unconditional {@code clearAssignments()} + re-add, fleet-level (not {@code getAI()}) - matches
     * vanilla's own reissue idiom in {@code FGTravelAction.directFleets()}/{@code FGWaitAction.
     * directFleets()}. See {@link #checkInvasionArrival}'s call site for why this needs to happen
     * repeatedly rather than once at launch.
     */
    private void issueTravelAssignment(CampaignFleetAPI fleet, MarketAPI target) {
        if (fleet == null || target == null || target.getPrimaryEntity() == null) return;
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, target.getPrimaryEntity(), 90f, (String) null);
    }

    /**
     * Looks the invasion fleet up fresh by id and confirms it's alive and above the defeat-strength
     * threshold - see {@link #invasionFleetId}/{@link #invasionFleetInitialFP} for why a held
     * reference and existence alone aren't enough. Single source of truth for "is the invasion fleet
     * still viable" - both {@link #checkInvasionArrival} and {@link #finishInvasion} go through this.
     */
    private CampaignFleetAPI getViableInvasionFleetOrNull() {
        if (invasionFleetId == null) return null;

        Object entity = Global.getSector().getEntityById(invasionFleetId);
        if (!(entity instanceof CampaignFleetAPI)) return null;

        CampaignFleetAPI fleet = (CampaignFleetAPI) entity;
        if (fleet.isDespawning()) return null;

        if (invasionFleetInitialFP > 0f
                && fleet.getFleetPoints() < invasionFleetInitialFP * INVASION_DEFEAT_STRENGTH_FRACTION) {
            return null;
        }

        return fleet;
    }

    /**
     * Reaching the target no longer resolves the invasion immediately - see the "Siege pacing" block
     * of fields above. Starts the assault-count siege, sized to the target's size and defenses via
     * {@link #computeRequiredAssaults}.
     */
    private void beginSiege(CampaignFleetAPI liveFleet) {
        if (assaultsRequired >= 0) return; // already sieging, don't restart it
        assaultsRequired = computeRequiredAssaults(invasionTarget);
        assaultsCompleted = 0;
        siegeHoldTimer = 0f;

        // Guarded by the idempotency check above, so this fires once per siege, not on every HOLD
        // reissue. Importance was already set correctly in launchInvasion(), so no need to recompute.
        lastEventTargetName = invasionTarget.getName();
        sendUpdateIfPlayerHasIntel(SIEGE_BEGUN_PARAM, true);

        // None of ORBIT_PASSIVE/AGGRESSIVE/ATTACK_LOCATION/PATROL_SYSTEM are the right tool here -
        // they're all travel behaviors. The actual vanilla mechanism for "sit at this market" is
        // BaseAssignmentAI.giveRaidOrder(): spawn a small token orbiting tight against the target's
        // primary entity and issue FleetAssignment.HOLD there - the one assignment type built for
        // "stay put." See issueHoldAssignment() for the copied idiom.
        issueHoldAssignment(liveFleet, invasionTarget);

        log.info("Draconis: Longsight Bastion in [" + system.getName() + "] began a siege of "
                + invasionTarget.getName() + " requiring " + assaultsRequired + " assault(s)");
    }

    /**
     * One assault pass per {@code assaultInterval} elapsed, capped per tick by
     * {@link #MAX_ASSAULT_PROGRESS_PER_TICK_DAYS} - see that field's doc for why the cap prevents
     * this resolving in one oversized tick under time compression. Each pass is a pure pacing gate:
     * no side effect on the market, and a defeated fleet never reaches this method again since
     * {@code checkInvasionArrival}'s top-of-tick check already calls {@code finishInvasion(false)}.
     */
    private void advanceSiege(float days, CampaignFleetAPI liveFleet) {
        siegeHoldTimer += days;
        if (siegeHoldTimer >= HOLD_REISSUE_INTERVAL_DAYS) {
            siegeHoldTimer = 0f;
            issueHoldAssignment(liveFleet, invasionTarget);
        }

        float creditedDays = Math.min(days, MAX_ASSAULT_PROGRESS_PER_TICK_DAYS);
        assaultInterval.advance(creditedDays);
        if (!assaultInterval.intervalElapsed()) return;

        assaultsCompleted++;
        log.info("Draconis: Longsight siege of " + invasionTarget.getName() + " - assault "
                + assaultsCompleted + "/" + assaultsRequired);

        if (assaultsCompleted >= assaultsRequired) {
            finishInvasion(true);
        }
    }

    /**
     * Copied from {@code BaseAssignmentAI.giveRaidOrder()}/{@code giveCaptureOrder()} - the vanilla
     * mechanism behind "sit at this market" (see {@link #beginSiege}'s doc). Spawns a short-lived
     * token orbiting tight against the target's primary entity and issues
     * {@code FleetAssignment.HOLD} there - unconditional reissue, no battle guard (one was tried
     * before and was itself a bug).
     * <p>
     * Siege-phase convenience overload - action text is always "besieging X".
     */
    private void issueHoldAssignment(CampaignFleetAPI fleet, MarketAPI target) {
        issueHoldAssignment(fleet, target, "besieging " + target.getName());
    }

    /**
     * Post-resolution: once the market is taken, "besieging X" no longer describes what the fleet is
     * doing, so the defend phase calls this with "dormant" instead - matching the naming Remnant
     * fleets already use for the same idle/holding state.
     */
    private void issueHoldAssignment(CampaignFleetAPI fleet, MarketAPI target, String actionText) {
        if (fleet == null || target == null) return;

        SectorEntityToken anchor = target.getPrimaryEntity();
        if (anchor == null) return;

        Vector2f loc = Misc.getUnitVectorAtDegreeAngle(
                Misc.getAngleInDegrees(anchor.getLocation(), fleet.getLocation()));
        float holdRadius = fleet.getRadius() * 0.5f + anchor.getRadius();
        loc.scale(holdRadius);
        Vector2f.add(loc, anchor.getLocation(), loc);
        SectorEntityToken holdLoc = target.getContainingLocation().createToken(loc);
        holdLoc.setCircularOrbit(anchor,
                Misc.getAngleInDegrees(anchor.getLocation(), fleet.getLocation()),
                holdRadius, 1000000f);
        fleet.getContainingLocation().addEntity(holdLoc);
        Misc.fadeAndExpire(holdLoc, 5f);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.HOLD, holdLoc, HOLD_ASSIGNMENT_DURATION_DAYS,
                actionText, null);
    }

    /**
     * Bigger, better-defended colonies take longer. Placeholder scale factors (see the fields
     * above) - not tuned.
     */
    private int computeRequiredAssaults(MarketAPI target) {
        int sizeBonus = Math.round(target.getSize() * SIZE_ASSAULTS_PER_POINT);
        int defenseBonus = computeDefenseTier(target) * DEFENSE_ASSAULTS_PER_TIER;
        return Math.max(1, BASE_REQUIRED_ASSAULTS + sizeBonus + defenseBonus);
    }

    /** 0 = no defense station at all; higher = a stronger tier, per DEFENSE_STATION_TIERS_ASCENDING. */
    private int computeDefenseTier(MarketAPI market) {
        for (int i = DEFENSE_STATION_TIERS_ASCENDING.length - 1; i >= 0; i--) {
            if (market.hasIndustry(DEFENSE_STATION_TIERS_ASCENDING[i])) return i + 1;
        }
        return 0;
    }

    private void finishInvasion(boolean reachedTarget) {
        log.info("Draconis: Longsight Bastion in [" + system.getName() + "] invasion of "
                + (invasionTarget != null ? invasionTarget.getName() : "unknown target")
                + (reachedTarget ? " reached its target" : " failed to reach its target"));

        // Safety-net check, independent of whatever already ran earlier this tick - the
        // capture/destroy outcome must never fire without re-verifying viability right here, at the
        // moment of resolution, regardless of how finishInvasion(true) was reached.
        if (reachedTarget && invasionTarget != null && getViableInvasionFleetOrNull() != null) {
            resolveInvasionOutcome(invasionTarget);
        } else if (reachedTarget) {
            log.info("Draconis: Longsight invasion of "
                    + (invasionTarget != null ? invasionTarget.getName() : "unknown target")
                    + " aborted at resolution - fleet no longer viable");
        }

        if (invasionTarget != null) {
            invasionTarget.getMemoryWithoutUpdate().unset(INVASION_IN_PROGRESS_KEY);
            DraconisAICoreRaidFactor.setTargetFailureCooldown(invasionTarget, INVASION_TARGET_FAILURE_COOLDOWN_DAYS);
        }
        if (invasionFleet != null) {
            invasionFleet.getEventListeners().remove(this);
        }
        invasionFleet = null;
        invasionFleetId = null;
        invasionFleetInitialFP = -1f;
        invasionTarget = null;
        assaultsRequired = -1;
        assaultsCompleted = 0;
        travelReissueTimer = 0f;
        updateImportance(); // back to unimportant now that invasionTarget is null - see its own doc
    }

    /**
     * 75% capture / 25% destroy. Partly a balance call, partly a mitigation - see
     * {@link #isDestroyProtected} below for the actual safety net; this alone just makes destroy
     * rarer, it doesn't prevent it.
     */
    private static final float DESTROY_PROBABILITY = 0.25f;

    /**
     * Markets that must never be destroyed by this crisis, even if the roll lands on it - built after
     * a real crash: {@code DecivTracker.decivilize()} removes a market entirely, and United Aurora
     * Federation's "Holiday" event script does an unchecked lookup of its hardcoded home market
     * ({@code uaf_favonius_station_market}) - once that market was gone, every UAF holiday event
     * crashed thereafter. A bug in UAF's own code, not fixable here in general (vanilla
     * famine/plague decivilization or Nexerelin's invasion system would trip the same crash), but
     * avoidable for this crisis specifically. Two protections, either one is enough:
     * <ul>
     *   <li>{@link #NO_CRISIS_DESTROY_KEY} - a plain memory flag, settable on any market as new
     *       fragile markets are discovered.</li>
     *   <li>{@link #KNOWN_FRAGILE_MARKET_IDS} - a short hardcoded list for markets already known to
     *       be a problem.</li>
     * </ul>
     * Protected markets are still valid invasion targets and can still be captured - only the
     * destroy outcome is blocked, forced to capture instead.
     * <p>
     * A third protection, same two-outcomes-allowed shape: any market tagged
     * {@code XLII_MarketTransfer.ORIGINAL_DRACONIS_MARKET_FLAG} - one Draconis started the game
     * owning (see that flag's own doc). The Office shouldn't be razing Draconis's own founding
     * colonies even if one was lost to someone else in the meantime and came back up as a target;
     * recapturing it for Draconis is still a fine outcome, only destroying it is blocked.
     */
    public static final String NO_CRISIS_DESTROY_KEY = "$XLII_longsightNoDestroy";

    private static final String[] KNOWN_FRAGILE_MARKET_IDS = {
            "uaf_favonius_station_market", // United Aurora Federation - see the doc comment above
    };

    private static boolean isDestroyProtected(MarketAPI market) {
        if (market.getMemoryWithoutUpdate().getBoolean(NO_CRISIS_DESTROY_KEY)) return true;
        if (market.getMemoryWithoutUpdate().getBoolean(XLII_MarketTransfer.ORIGINAL_DRACONIS_MARKET_FLAG)) return true;
        for (String id : KNOWN_FRAGILE_MARKET_IDS) {
            if (id.equals(market.getId())) return true;
        }
        return false;
    }

    private void resolveInvasionOutcome(MarketAPI target) {
        boolean capture = isDestroyProtected(target) || new Random().nextFloat() >= DESTROY_PROBABILITY;

        // Captured before either branch below mutates the market - transferMarket()/decivilize()
        // would otherwise make isPlayerOwned() read false even when the player just lost this
        // colony. Not updateImportance() itself (that would read post-mutation state) - a direct
        // setImportant() using the pre-mutation state instead.
        setImportant(target.isPlayerOwned());

        lastEventTargetName = target.getName();

        // Snapshot before either branch below mutates it (transferMarket() overwrites it to
        // Draconis; decivilize() overwrites it to NEUTRAL) - both branches need this to tell the
        // tracker intel who actually lost the place.
        String previousOwnerId = target.getFactionId();

        if (capture) {
            log.info("Draconis: Longsight invasion captured " + target.getName() + " for Draconis");
            XLII_MarketTransfer.transferMarket(target, Factions.DRACONIS);
            target.getMemoryWithoutUpdate().set(XLII_LongsightCrisisManager.CAPTURED_FLAG, true);
            target.getMemoryWithoutUpdate().set(XLII_LongsightCrisisManager.PREVIOUS_OWNER_FLAG, previousOwnerId);
            sendUpdateIfPlayerHasIntel(INVASION_CAPTURED_PARAM, true);
        } else {
            log.info("Draconis: Longsight invasion destroyed " + target.getName());
            // Snapshotted here, not after - decivilize(fullDestroy=true) unconditionally removes
            // the market from the economy, so this is the last point target.getName() is reliable.
            XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
            if (manager != null) manager.recordMarketDestroyed(target.getName(), previousOwnerId);
            DecivTracker.decivilize(target, true, true);
            sendUpdateIfPlayerHasIntel(INVASION_DESTROYED_PARAM, true);
        }

        beginPostResolutionDefense(target);
    }

    /**
     * Called while invasionFleet/invasionFleetId are still set (before finishInvasion()'s own
     * cleanup runs) - hands the fleet off to the defend-then-despawn tracking above instead of
     * letting it fall out of this class's tracking with no further purpose.
     */
    private void beginPostResolutionDefense(MarketAPI target) {
        CampaignFleetAPI liveFleet = getViableInvasionFleetOrNull();
        if (liveFleet == null) return; // didn't survive its own victory - nothing left to defend with

        liveFleet.getEventListeners().remove(this);
        // Same HOLD-at-orbit-token pattern as the siege phase, but "dormant" instead of "besieging X"
        // - see issueHoldAssignment()'s doc.
        issueHoldAssignment(liveFleet, target, "dormant");

        long despawnAt = Global.getSector().getClock().getTimestamp() + daysToTimestampUnits(POST_RESOLUTION_DEFEND_DAYS);
        liveFleet.getMemoryWithoutUpdate().set(POST_RESOLUTION_DESPAWN_TIMESTAMP_KEY, despawnAt);

        defendingFleets.add(new DefendingFleet(invasionFleetId, target));

        log.info("Draconis: Longsight invasion fleet now defending " + target.getName() + " for "
                + (int) POST_RESOLUTION_DEFEND_DAYS + " days before despawning");
    }

    /**
     * Ticks every currently-defending fleet: refreshes the do-not-get-sidetracked flag, re-issues
     * its {@code HOLD} order on the same cadence as the siege phase (see
     * {@link #issueHoldAssignment(CampaignFleetAPI, MarketAPI)}), and despawns it once its stored
     * timestamp is reached - CampaignFleetAPI.despawn(), the same call already used for cleanly
     * removing a resolved engagement's fleet in XLII_KoriStrike/XLII_RingPortAssault.
     */
    private void advanceDefendingFleets(float amount) {
        if (defendingFleets.isEmpty()) return;

        long now = Global.getSector().getClock().getTimestamp();
        float days = Global.getSector().getClock().convertToDays(amount);

        java.util.Iterator<DefendingFleet> it = defendingFleets.iterator();
        while (it.hasNext()) {
            DefendingFleet defending = it.next();
            Object entity = Global.getSector().getEntityById(defending.fleetId);
            if (!(entity instanceof CampaignFleetAPI)) {
                it.remove(); // already gone
                continue;
            }

            CampaignFleetAPI fleet = (CampaignFleetAPI) entity;
            if (fleet.isDespawning()) {
                it.remove();
                continue;
            }

            long despawnAt = fleet.getMemoryWithoutUpdate().getLong(POST_RESOLUTION_DESPAWN_TIMESTAMP_KEY);
            if (now >= despawnAt) {
                if (fleet.getContainingLocation() != null) fleet.despawn();
                it.remove();
                continue;
            }

            defending.holdTimer += days;
            if (defending.holdTimer >= HOLD_REISSUE_INTERVAL_DAYS) {
                defending.holdTimer = 0f;
                issueHoldAssignment(fleet, defending.market, "dormant");
            }

            // Same reasoning as every other applyCrisisHostility() call site - this fleet can sit
            // here for up to POST_RESOLUTION_DEFEND_DAYS (60), easily long enough for relations to
            // change; without a refresh here it would keep whatever hostility it had at the moment
            // it started defending.
            applyCrisisHostility(fleet);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, 1f);
        }
    }

    private static final String INVASION_IN_PROGRESS_KEY = "$XLII_longsightInvasionInProgress";

    private MarketAPI pickInvasionTarget() {
        WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.DRACONIS.equals(market.getFactionId())) continue;
            // Invading an ally's market just led to Nexerelin handing it back repeatedly - see
            // XLII_MarketTransfer.isProtectedFromCrisisInvasion(). Allied/high-reputation markets are
            // simply never offered as targets in the first place.
            if (XLII_MarketTransfer.isProtectedFromCrisisInvasion(market.getFactionId())) continue;
            if (market.isHidden()) continue;
            if (market.hasCondition(Conditions.DECIVILIZED)) continue;
            if (market.getPrimaryEntity() == null) continue;
            if (market.getMemoryWithoutUpdate().getBoolean(INVASION_IN_PROGRESS_KEY)) continue;
            if (DraconisAICoreRaidFactor.isTargetOnFailureCooldown(market)) continue;

            float dist = Misc.getDistanceLY(bastionFleet, market.getPrimaryEntity());
            float distMult = 1f - Math.max(0f, dist - 10f) / 10f;
            if (distMult < 0.1f) distMult = 0.1f;
            if (distMult > 1f) distMult = 1f;

            float score = market.getSize() * distMult;
            if (score <= 0f) continue;

            picker.add(market, score);
        }

        return picker.pick();
    }

    private CampaignFleetAPI spawnInvasionFleet(MarketAPI target) {
        Random seedRandom = new Random();

        FleetParamsV3 params = new FleetParamsV3(
                null,
                Factions.INTELLIGENCE_OFFICE,
                50f,
                FleetTypes.TASK_FORCE,
                INVASION_COMBAT_POINTS,
                0f, 0f, 0f, 0f, 0f,
                0f);
        params.officerLevelBonus = 5;
        params.quality = 6;
        params.random = seedRandom;
        params.timestamp = Global.getSector().getClock().getTimestamp();

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setId("XLII_longsight_invasion_" + Misc.genUID());
        fleet.setNoFactionInName(true);
        fleet.setName("Unmarked Task Force");
        fleet.getMemoryWithoutUpdate().set(CRISIS_FLEET_FLAG, true);
        // Deliberately NOT force-hostile yet - see checkInvasionArrival()'s comment. It travels as
        // an ordinary (if oddly AI-crewed) Intelligence Office fleet until it reaches its target.
        inflateWithAlphaCores(fleet, seedRandom);
        assignLongsightCommander(fleet, seedRandom);

        system.addEntity(fleet);
        fleet.setLocation(bastionFleet.getLocation().x, bastionFleet.getLocation().y);

        return fleet;
    }

    /**
     * Same Alpha Core, no-human-crew treatment as Remnant fleets and Ladon's own garrison
     * (`XLII_LongsightCrisisGarrisonManager.inflateWithAICores()`) - duplicated rather than shared,
     * matching how this codebase already duplicates this exact method between
     * `XLII_OfficeGarrisonManager` and its own crisis-garrison counterpart.
     */
    private void inflateWithAlphaCores(CampaignFleetAPI fleet, Random random) {
        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE);
        if (plugin == null) return;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing() || member.isCivilian()) continue;

            PersonAPI existing = member.getCaptain();
            if (existing != null) fleet.getFleetData().removeOfficer(existing);

            PersonAPI core = plugin.createPerson(Commodities.ALPHA_CORE, fleet.getFaction().getId(), random);
            if (core == null) continue;
            fleet.getFleetData().addOfficer(core);
            member.setCaptain(core);
        }

        if (fleet.getFlagship() != null) {
            fleet.setCommander(fleet.getFlagship().getCaptain());
        }
    }

    /**
     * Every fleet this crisis spawns is commanded by the same Longsight officer used for the Bastion
     * itself ({@link #spawnBastionFleet}), not just crewed hull-by-hull with anonymous Alpha Core
     * officers (see {@link #inflateWithAlphaCores}). Replaces the flagship's own captain and elevates
     * it to fleet commander - persists through the whole invasion/siege/defend arc since it's the
     * same {@code CampaignFleetAPI} throughout. Duplicated rather than shared with the identical
     * method in {@code XLII_LongsightCrisisGarrisonManager}, matching this codebase's convention.
     */
    private void assignLongsightCommander(CampaignFleetAPI fleet, Random random) {
        FleetMemberAPI flagship = fleet.getFlagship();
        if (flagship == null) return;

        PersonAPI existing = flagship.getCaptain();
        if (existing != null) fleet.getFleetData().removeOfficer(existing);

        PersonAPI commander = new XLII_LongsightOfficerPlugin().createPerson(
                XLII_LongsightOfficerPlugin.CORE_ID, Factions.INTELLIGENCE_OFFICE, random);
        fleet.getFleetData().addOfficer(commander);
        flagship.setCaptain(commander);
        fleet.setCommander(commander);
    }

    // -------------------------------------------------------------------------
    // FleetEventListener
    // -------------------------------------------------------------------------

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (fleet != invasionFleet) return;

        if (primaryWinner != invasionFleet) {
            // A loss ends the invasion outright, siege or not - destroy the invasion fleet at any
            // point, including mid-siege, and the target is safe.
            finishInvasion(false);
            return;
        }

        // Only treat a win as reaching the target if it happened there, and only start the siege if
        // it actually cleared the place out - originally this checked only "same star system," so
        // winning any incidental fight (a pirate encountered en route, one wave of a multi-wave
        // defense) called beginSiege() immediately with the market's real defense station never
        // engaged. Same "ignores stations" bug hasActiveDefenders() fixes in checkInvasionArrival(),
        // fixed the same way here. beginSiege() itself is idempotent, so a win against a second wave
        // mid-siege is a harmless no-op.
        if (invasionTarget == null || invasionTarget.getPrimaryEntity() == null) return;
        if (invasionFleet.getStarSystem() != invasionTarget.getStarSystem()) return;

        float dist = Misc.getDistance(invasionFleet, invasionTarget.getPrimaryEntity());
        if (dist < 500f && !hasActiveDefenders(invasionFleet, invasionTarget)) {
            beginSiege(fleet);
        }
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (fleet != invasionFleet) return;
        finishInvasion(false);
    }
}
