package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreFleetInflater;
import levianeer.draconis.data.campaign.intel.blind_eye.BlindEyeQuestMission;
import levianeer.draconis.data.campaign.intel.longsight.LongsightQuestMission;
import levianeer.draconis.data.campaign.intel.longsight.OfficeReferralMission;
import levianeer.draconis.data.campaign.intel.longsight.ShapesOfOldMission;
import levianeer.draconis.data.campaign.intel.longsight.XLII_LongsightOfficerPlugin;
import levianeer.draconis.data.scripts.skills.XLII_EchoIllusionEffect;
import levianeer.draconis.data.scripts.world.systems.XLII_System;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.*;

/**
 * The Fafnir Mirror assault: destroying Stellar Array Alpha/Bravo/Charlie (the "Mirror" survey
 * content in rules.csv, {@code # Fafnir Mirror}) turns a latent Draconis guard presence at the
 * array into a hand-authored fleet fight, same FID shape as {@link XLII_KoriStrike}'s own
 * Battlegroup fight. Reached only via {@code XLII_LaunchMirrorAssault <alpha|bravo|charlie>}
 * (rules.csv rulecmd), gated behind that array already being surveyed and
 * {@code $global.XLII_stellarMirrorQuestActive} (Faller's Stellar Mirror quest offer at Ring-Port
 * - see {@code levianeer.draconis.data.campaign.intel.stellar_mirror.StellarMirrorQuestMission}).
 * <p>
 * Deliberately has no quest relation to the Longsight/Burn the Machine questline - destroying a
 * Mirror is an outright hostile act against Draconis, independent of (and fatal to) whatever
 * Draconis quest progress the player already has; see {@link #failAllDraconisQuests()}.
 * <p>
 * The guard fleet's ships are themselves the "echoes" - real, killable combatants wearing
 * {@link XLII_EchoIllusionEffect} (translucent, color-cycling, no salvage on death), not real hulls that
 * spawn separate illusions mid-fight. The fleet is a one-shot per attempt: a win destroys it for
 * good, but a loss or disengage despawns it too - the next attempt always spawns a fresh,
 * full-strength fleet rather than resuming a battle-damaged one. Winning the fleet fight only
 * clears the guard, though - see {@link #onGuardDestroyed} and {@link #arrayRaided} for the
 * finishing marine raid this now requires before the array is actually destroyed.
 */
public class XLII_MirrorAssault {

    private static final Logger log = Logger.getLogger(XLII_MirrorAssault.class);

    public static final String[] KEYS = {"alpha", "bravo", "charlie"};

    private static final Map<String, String> ENTITY_ID = new HashMap<>();
    static {
        ENTITY_ID.put("alpha", "fafnir_mirror1");
        ENTITY_ID.put("bravo", "fafnir_mirror2");
        ENTITY_ID.put("charlie", "fafnir_mirror3");
    }

    /** Sector memory - fires the quest-failure/hostility consequence exactly once. */
    private static final String MEM_HOSTILE_DECLARED = "$XLII_mirrorAssaultHostileDeclared";

    /** Set once all three arrays are actually destroyed (i.e. raided, not just guard-cleared). */
    public static final String MEM_ALL_DESTROYED = "$XLII_mirrorAllDestroyed";

    /**
     * Days the player has to land the finishing marine raid after clearing an array's guard, before
     * Draconis reinforces it and the fight has to be won again. Implemented purely as the expiry on
     * {@link #guardDownFlag} - memory expiry is engine-driven and save-safe, so no timer script is
     * needed. Starting value, not final - see tools/CLAUDE.md.
     */
    public static final float RAID_WINDOW_DAYS = 30f;

    /** Read by the guard-down rules.csv rows so their prose can't drift from RAID_WINDOW_DAYS. */
    public static final String MEM_RAID_WINDOW_DAYS = "$XLII_mirrorRaidWindowDays";

    public static String destroyedFlag(String key) {
        return "$XLII_mirror" + capitalize(key) + "Destroyed";
    }

    /**
     * Set on a guard-fleet win, before the finishing marine raid - see {@link #onGuardDestroyed}.
     * Expires after {@link #RAID_WINDOW_DAYS}; once it lapses the array's raid-menu rules.csv rows
     * stop matching and the live approach row (with its destroy option) wins again, so the player
     * re-fights a fresh guard. That fall-through is the whole reinforcement mechanic.
     */
    public static String guardDownFlag(String key) {
        return "$XLII_mirror" + capitalize(key) + "GuardDown";
    }

    private static String capitalize(String key) {
        return key.substring(0, 1).toUpperCase() + key.substring(1);
    }

    /**
     * Size of each array's raid market. Only ever read as {@code market.getSize()} by raid
     * listeners - it does not represent a population, and nothing economic consumes it.
     */
    private static final int RAID_MARKET_SIZE = 3;

    /**
     * Gives each Stellar Array a hidden market, because vanilla's ground-raid pipeline assumes the
     * raided entity has one and third-party listeners dereference it without checking.
     * <p>
     * Vanilla itself tolerates a null market - {@code MarketCMD.init()} and
     * {@code raidNonMarket()} both guard it - but nothing upstream actually exercises that path:
     * every vanilla "non-market" raid targets a planet, which has a market. ("nonMarket" is the
     * raid <i>type</i>, not a claim about the entity.) So the null case is effectively untested,
     * and with Nexerelin installed it is fatal in two separate places:
     * <ul>
     *   <li>{@code Nex_MarketCMD.initForInvasion()} - an unguarded {@code market.getMemoryWithoutUpdate()},
     *       two lines after {@code init()} null-checks the very same field. Worked around in
     *       rules.csv by out-scoring Nexerelin's rows, but that only covers rulecmds.</li>
     *   <li>{@code RevengeanceManager.reportRaidForValuablesFinishedBeforeCargoShown()} -
     *       {@code market.getSize()} on a listener fired by vanilla's own
     *       {@code MarketCMD.raidConfirmContinue()}. Listeners do not go through rules.csv at all,
     *       so no scoring trick can reach it, and the call is unavoidable: reaching the objectives
     *       screen requires {@code mktRaidValuable}, which sets {@code temp.raidType = VALUABLE},
     *       which is exactly the branch that fires it.</li>
     * </ul>
     * Giving the arrays a market fixes that whole class of failure at the source instead of
     * chasing each dereference.
     * <p>
     * Deliberately <b>not</b> registered with {@code getEconomy()}: it has no industries, trade,
     * stability or patrols, and every system that enumerates real markets iterates the economy, so
     * an unregistered market stays invisible to all of them - including Nexerelin's own invasion
     * and diplomacy targeting. {@code entity.getMarket()} still returns it, which is the only thing
     * the raid needs.
     * <p>
     * One intended side effect: raid listeners now actually run, so Nexerelin books vengeance
     * points and war weariness against the array's faction. That is correct - the player just
     * demolished a Draconis megastructure - and small, since a raid uses
     * {@code size * 0.75} without the size-squaring bombardment applies.
     * <p>
     * Called once per array from {@code XLII_System}'s worldgen, where the arrays themselves are
     * created.
     */
    public static void addRaidMarket(SectorEntityToken mirror) {
        MarketAPI market = Global.getFactory().createMarket(
                mirror.getId() + "_market", mirror.getName(), RAID_MARKET_SIZE);
        // setPlanetConditionMarketOnly is the vanilla/house idiom for a market that is a carrier
        // rather than a colony - same shape as XLII_OfficeSystem.addVritraRuins(), which creates
        // one purely to hold a hazard condition. Faction is taken from the entity rather than
        // hardcoded so the two cannot drift apart.
        market.setPlanetConditionMarketOnly(true);
        market.setFactionId(mirror.getFaction().getId());
        market.setPrimaryEntity(mirror);
        market.setHidden(true);
        mirror.setMarket(market);
    }

    /**
     * Launches the fight for the given array. Called from the {@code XLII_LaunchMirrorAssault}
     * rulecmd once the player confirms past the hostility warning. Always builds a fresh guard
     * fleet - see the class javadoc for why this is a one-shot per attempt, not a persistent one.
     */
    public static void launch(final InteractionDialogAPI dialog, final String key) {
        if (dialog == null || key == null || !ENTITY_ID.containsKey(key)) return;

        declareHostilityIfNeeded();

        final CampaignFleetAPI guard = createGuardFleet(key);
        if (guard == null) {
            log.error("Draconis: XLII_MirrorAssault - failed to create guard fleet for " + key);
            dialog.dismiss();
            return;
        }

        FIDConfig config = new FIDConfig();
        config.showCommLinkOption = false;
        config.showWarningDialogWhenNotHostile = false;
        config.dismissOnLeave = true;
        // Deliberately does NOT set noLeaveOptionOnFirstEngagement - leaving mid-fight stays legal,
        // it just despawns this attempt's fleet (see notifyLeave below) rather than ending the fight.
        config.delegate = new BaseFIDDelegate() {
            private boolean won = false;

            @Override
            public void postPlayerSalvageGeneration(InteractionDialogAPI d,
                    FleetEncounterContext ctx, CargoAPI salvage) {
                // Deliberately does not call through to generate real salvage - these ships are
                // illusions (XLII_EchoIllusion); nothing is left behind to recover.
                won = true;
            }

            @Override
            public void notifyLeave(InteractionDialogAPI d) {
                if (won) {
                    onGuardDestroyed(d, key, guard);
                } else if (guard.getContainingLocation() != null) {
                    // A loss or disengage despawns this attempt's fleet outright - no persistent,
                    // battle-damaged fleet left sitting in the system. The next attempt builds a
                    // fresh, full-strength one from scratch.
                    guard.despawn();
                }
            }
        };

        final FleetInteractionDialogPluginImpl fid = new FleetInteractionDialogPluginImpl(config);

        dialog.dismiss();

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override
            public boolean isDone() { return done; }

            @Override
            public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(fid, guard);
                }
            }
        });
    }

    /**
     * Floor on the guard fleet's {@code combatPts} - what a player below the scaling threshold
     * gets, and the smallest this fight is ever allowed to be. Deliberately well under the 600 this
     * encounter was first tuned at: at {@link #PLAYER_SCALE} the guard tracks the player's own
     * fleet from 300 FP upward, so the floor only has to cover players who arrive under-strength
     * rather than carry the whole fight on its own. Starting value, not final - see tools/CLAUDE.md.
     */
    private static final float BASE_COMBAT_PTS = 300f;

    /**
     * How much of the player's own combat fleet points the guard tries to match once they exceed
     * {@link #BASE_COMBAT_PTS}. Starting value, not final.
     * <p>
     * 1.0 is parity in fleet points, which is <i>not</i> parity in strength and is not meant to be:
     * every hull here is rolled at {@code qualityOverride = 777} (best available variants, no
     * d-mods) and crewed by an Alpha Core, with Longsight itself on the flagship. An equal-FP echo
     * fleet still outclasses the player's line ship for ship - the scaling matches the size of the
     * engagement, and the quality and officers are what make it hard.
     */
    private static final float PLAYER_SCALE = 1.0f;

    /**
     * Guard fleet budget: a fixed floor for under-strength players, tracking the player's own
     * combat fleet above it so the fight neither trivialises late in a campaign nor becomes a wall
     * for someone who arrives early.
     * <p>
     * Measured in combat fleet points on both sides deliberately. {@code combatPts} is spent by
     * {@code FleetFactoryV3} as a fleet-point budget split across warships/carriers/phase by
     * doctrine, so the only apples-to-apples player-side figure is the summed
     * {@link FleetMemberAPI#getFleetPointCost()} of their combat hulls - civilians and fighter
     * wings are excluded because they are not what {@code combatPts} buys. Fleet points rather than
     * {@code getEffectiveStrength()} for the same reason {@code XLII_LongsightBastionIntel} gives:
     * strength swings with CR and damage, so a player who limped in from another fight would get a
     * softer guard for it.
     * <p>
     * No upper clamp, and at {@link #PLAYER_SCALE} of 1.0 none is needed: above the floor the guard
     * is by construction never allocated more combat fleet points than the player's own combat
     * fleet has, so it is self-limiting against whatever the player actually brings. Raising
     * {@code PLAYER_SCALE} above 1.0 would break that property and is where a cap would start to
     * earn its place.
     * <p>
     * Known limitation: fleet points do not see officer skills, s-mods or player level, so a small
     * fleet of elite-captained capitals reads lower than it fights and will be met at its nominal
     * FP rather than its real strength. With the floor at 300 this bites across most of the
     * campaign rather than only at the extremes, so it is the first thing to look at if the fight
     * plays softer than intended for a strong, compact fleet.
     * <p>
     * Re-evaluated per attempt, not cached: {@link #createGuardFleet} runs on every
     * {@link #launch}, so coming back later with a bigger fleet means a bigger guard. That matches
     * the one-shot-per-attempt design in the class javadoc.
     */
    private static float pickCombatPts() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return BASE_COMBAT_PTS;

        float playerCombatFP = 0f;
        for (FleetMemberAPI member : player.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing() || member.isCivilian()) continue;
            playerCombatFP += member.getFleetPointCost();
        }

        return Math.max(BASE_COMBAT_PTS, playerCombatFP * PLAYER_SCALE);
    }

    private static CampaignFleetAPI createGuardFleet(String key) {
        StarSystemAPI fafnir = Global.getSector().getStarSystem("Fafnir");
        SectorEntityToken mirror = Global.getSector().getEntityById(ENTITY_ID.get(key));
        if (fafnir == null || mirror == null) return null;

        // Same Draconis hull pool the AIO shadow-fleet system already draws from
        // (DraconisFleetHostileActivityFactor.createFleet()) - these are Longsight's puppets, not
        // a separate faction. Quality/combatPts are starting values, not final - see tools/CLAUDE.md.
        FleetParamsV3 params = new FleetParamsV3();
        params.factionId = INTELLIGENCE_OFFICE;
        params.fleetType = FleetTypes.TASK_FORCE;
        params.qualityOverride = 777f;
        params.combatPts = pickCombatPts();
        params.random = new Random();

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) return null;

        // Draconis is not hostile to the player by default - explicit override, same pattern as
        // XLII_KoriStrike.createBattlegroupFleet()/XLII_OfficeSystem.applyHostility().
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        fleet.setNoFactionInName(true);
        fleet.setName("A Thought & Whisper");

        // Alpha Core first, then Longsight overrides the flagship's captain - same order
        // XLII_LongsightCrisisGarrisonManager.inflateWithAICores()/assignLongsightCommander() use
        // for the same "everyone's an Alpha Core except the one Longsight is riding" shape.
        assignAlphaCoreOfficers(fleet);
        assignLongsightAsCommander(fleet);

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            // Echo Illusion skill goes to whoever ended up captaining this ship (Alpha Core or
            // Longsight) - see applyEchoSkill(). VARIANT_UNBOARDABLE keeps these illusions from
            // being offered as recoverable ships after the fight - postPlayerSalvageGeneration not
            // calling through only suppresses cargo loot, not FleetEncounterContext's separate
            // ship-recovery pipeline (confirmed by reading Misc.isUnboardable(), which is exactly
            // what FleetEncounterContext.getRecoverableShips() checks).
            member.getVariant().addTag(Tags.VARIANT_UNBOARDABLE);
            member.getVariant().addTag(Tags.UNRECOVERABLE);
            applyEchoSkill(member);
        }

        fafnir.addEntity(fleet);
        fleet.setLocation(mirror.getLocation().x, mirror.getLocation().y);

        return fleet;
    }

    /**
     * Every combat ship gets an Alpha Core captain - same shape as
     * XLII_LongsightCrisisGarrisonManager.inflateWithAICores(). assignLongsightAsCommander() runs
     * after this and overwrites the flagship's Alpha Core with Longsight itself.
     */
    private static void assignAlphaCoreOfficers(CampaignFleetAPI fleet) {
        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE);
        if (plugin == null) {
            log.warn("Draconis: XLII_MirrorAssault - could not find AICoreOfficerPlugin for Alpha Core");
            return;
        }

        Random random = new Random();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing() || member.isCivilian()) continue;

            PersonAPI existing = member.getCaptain();
            if (existing != null) fleet.getFleetData().removeOfficer(existing);

            PersonAPI core = plugin.createPerson(Commodities.ALPHA_CORE, fleet.getFaction().getId(), random);
            if (core == null) continue;
            fleet.getFleetData().addOfficer(core);
            member.setCaptain(core);
        }
    }

    /**
     * Makes Longsight the fleet's commander (shown in the FID pre-battle screen and comm) and the
     * flagship's captain - same AICoreOfficerPlugin-backed person
     * {@link DraconisAICoreFleetInflater} already uses to seat Longsight as a flagship captain
     * elsewhere, rather than the bare synthetic portrait {@code XLII_ShowLongsightVisual} builds
     * for dialogue-only scenes (no stats, not a real officer - unsuitable for a combat seat).
     */
    private static void assignLongsightAsCommander(CampaignFleetAPI fleet) {
        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(XLII_LongsightOfficerPlugin.CORE_ID);
        if (plugin == null) {
            log.warn("Draconis: XLII_MirrorAssault - could not find AICoreOfficerPlugin for Longsight");
            return;
        }

        PersonAPI longsight = plugin.createPerson(
                XLII_LongsightOfficerPlugin.CORE_ID, fleet.getFaction().getId(), new Random());
        FleetMemberAPI flagship = fleet.getFlagship();
        if (longsight == null || flagship == null) return;

        PersonAPI existing = flagship.getCaptain();
        if (existing != null) fleet.getFleetData().removeOfficer(existing);
        fleet.getFleetData().addOfficer(longsight);
        flagship.setCaptain(longsight);
        fleet.setCommander(longsight);
    }

    /**
     * Grants the Echo Illusion skill to whoever captains this ship - its existing officer (e.g.
     * Longsight, already seated on the flagship by {@link #assignLongsightAsCommander}) if it has
     * one, or a bare throwaway person otherwise. See {@link XLII_EchoIllusionEffect} for why this
     * is a skill rather than the dynamically-applied hullmod this used to be.
     */
    private static void applyEchoSkill(FleetMemberAPI member) {
        PersonAPI captain = member.getCaptain();
        if (captain == null) {
            captain = Global.getSettings().createPerson();
            member.setCaptain(captain);
        }
        captain.getStats().setSkillLevel(XLII_EchoIllusionEffect.SKILL_ID, 1f);
    }

    /**
     * Combat win against the array's Echo Fleet guard - no longer the full destruction. The array
     * itself is still standing; a marine raid (vanilla's non-market ground raid menu, opened from
     * the array's own approach interaction while this flag holds) is what actually finishes it off,
     * via {@link #arrayRaided}. Gated the same way at the quest level as before this change - see
     * "# [FAFNIR LORE] Stellar Arrays"'s destroy-option rows for the
     * {@code $global.XLII_stellarMirrorQuestActive} gate.
     * <p>
     * The guard-down flag is deliberately set with an expiry rather than permanently: killing the
     * guard tells Draconis exactly which array to reinforce, so the demolition is on a clock. See
     * {@link #guardDownFlag} for why that needs no timer script of its own.
     * <p>
     * Does not re-open the raid menu in this dialog on purpose. {@code AddRaidObjective} binds its
     * objective to {@code dialog.getInteractionTarget()}, which here is the guard fleet this method
     * has just despawned - the raid has to be opened from a fresh interaction with the array entity
     * itself, which is what the array's own {@code OpenInteractionDialog} raid rows are for.
     */
    private static void onGuardDestroyed(InteractionDialogAPI dialog, String key, CampaignFleetAPI guard) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(guardDownFlag(key), true, RAID_WINDOW_DAYS);
        mem.set(MEM_RAID_WINDOW_DAYS, (int) RAID_WINDOW_DAYS);

        if (guard.getContainingLocation() != null) {
            guard.despawn();
        }

        dialog.setPlugin(new RuleBasedInteractionDialogPluginImpl("XLII_mirror_" + key + "_guard_down"));
    }

    /**
     * Called from the {@code XLII_MirrorMarineRaid arrayRaided <key>} rulecmd once the marine raid
     * against the array succeeds (the raid itself - danger level, marine cost, pass/fail - is
     * entirely vanilla's own non-market ground raid menu; this only resolves the mechanical
     * outcome). Marks the array destroyed and, once all three are down, reuses the Burn the
     * Machine win path's own Rift-collapse plumbing - same alternate, hostile route to the same
     * mechanical outcome the old single-step onMirrorDestroyed() used to take directly off the
     * guard-fleet win.
     */
    public static void arrayRaided(String key) {
        if (key == null || !ENTITY_ID.containsKey(key)) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(destroyedFlag(key), true);
        // The window this flag was counting down no longer applies - destroyedFlag governs from here.
        mem.unset(guardDownFlag(key));

        boolean allDestroyed = true;
        for (String k : KEYS) {
            if (!mem.getBoolean(destroyedFlag(k))) {
                allDestroyed = false;
                break;
            }
        }

        if (allDestroyed) {
            // Reuses the Burn the Machine win path's own collapse plumbing - this is an alternate,
            // hostile route to the same mechanical outcome, not a separate Rift-shutdown mechanism.
            mem.set(XLII_KoriStrike.MEM_RIFT_COLLAPSED, true);
            XLII_System.disableRiftTerrain();
            XLII_System.syncFafnirInvasionExclusion();
            mem.set(MEM_ALL_DESTROYED, true);
        }
    }

    /**
     * Fires once, the first time the player commits to destroying any array: fails every active
     * Draconis mission tracker outright and flips Draconis hostile. No quest relation to Burn the
     * Machine - this is a separate, mutually-exclusive hostile path (author decision).
     * <p>
     * MEM_HOSTILE_DECLARED is also watched directly by data/config/korrin_leave_triggers.csv
     * (action forever) - turning on the player's own faction is treated the same as witnessing
     * August's death in XLII_KoriStrike. No local presence flag needed here: KorrinLeaveTriggers
     * only fires while he's actually aboard, which already covers "was he there for it."
     */
    private static void declareHostilityIfNeeded() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.getBoolean(MEM_HOSTILE_DECLARED)) return;
        mem.set(MEM_HOSTILE_DECLARED, true);

        failAllDraconisQuests();

        FactionAPI draconis = Global.getSector().getFaction(DRACONIS);
        if (draconis != null) {
            draconis.setRelationship(Factions.PLAYER, RepLevel.HOSTILE);
        }
    }

    private static void failAllDraconisQuests() {
        IntelManagerAPI intel = Global.getSector().getIntelManager();

        OfficeReferralMission office = (OfficeReferralMission) intel.getFirstIntel(OfficeReferralMission.class);
        if (office != null) office.failForMirrorAssault();

        ShapesOfOldMission tour = (ShapesOfOldMission) intel.getFirstIntel(ShapesOfOldMission.class);
        if (tour != null) tour.failForMirrorAssault();

        LongsightQuestMission longsight = (LongsightQuestMission) intel.getFirstIntel(LongsightQuestMission.class);
        if (longsight != null) longsight.failForMirrorAssault();

        BlindEyeQuestMission blindEye = (BlindEyeQuestMission) intel.getFirstIntel(BlindEyeQuestMission.class);
        if (blindEye != null) blindEye.failForMirrorAssault();
    }
}
