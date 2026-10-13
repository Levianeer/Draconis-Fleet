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
 * (rules.csv rulecmd), gated behind that array already being surveyed.
 * <p>
 * Deliberately has no quest relation to the Longsight/Burn the Machine questline - destroying a
 * Mirror is an outright hostile act against Draconis, independent of (and fatal to) whatever
 * Draconis quest progress the player already has; see {@link #failAllDraconisQuests()}.
 * <p>
 * The guard fleet's ships are themselves the "echoes" - real, killable combatants wearing
 * {@link XLII_EchoIllusionEffect} (translucent, color-cycling, no salvage on death), not real hulls that
 * spawn separate illusions mid-fight. The fleet is a one-shot per attempt: a win destroys it for
 * good, but a loss or disengage despawns it too - the next attempt always spawns a fresh,
 * full-strength fleet rather than resuming a battle-damaged one.
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

    public static String destroyedFlag(String key) {
        return "$XLII_mirror" + capitalize(key) + "Destroyed";
    }

    private static String capitalize(String key) {
        return key.substring(0, 1).toUpperCase() + key.substring(1);
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
                    onMirrorDestroyed(d, key, guard);
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
        params.combatPts = 600f;
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

    private static void onMirrorDestroyed(InteractionDialogAPI dialog, String key, CampaignFleetAPI guard) {
        Global.getSector().getMemoryWithoutUpdate().set(destroyedFlag(key), true);

        if (guard.getContainingLocation() != null) {
            guard.despawn();
        }

        boolean allDestroyed = true;
        for (String k : KEYS) {
            if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(destroyedFlag(k))) {
                allDestroyed = false;
                break;
            }
        }

        if (allDestroyed) {
            // Reuses the Burn the Machine win path's own collapse plumbing - this is an alternate,
            // hostile route to the same mechanical outcome, not a separate Rift-shutdown mechanism.
            Global.getSector().getMemoryWithoutUpdate().set(XLII_KoriStrike.MEM_RIFT_COLLAPSED, true);
            XLII_System.disableRiftTerrain();
            XLII_System.syncFafnirInvasionExclusion();
            dialog.setPlugin(new RuleBasedInteractionDialogPluginImpl("XLII_mirror_all_destroyed"));
        } else {
            dialog.setPlugin(new RuleBasedInteractionDialogPluginImpl("XLII_mirror_" + key + "_destroyed"));
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
