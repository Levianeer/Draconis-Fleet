package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.ids.Factions;
import levianeer.draconis.data.campaign.intel.waste_not.WasteNotMission;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rule command: launches the small AIO picket fight guarding the Waste Not installation, fired
 * from the installation scene's "Attack" option.
 * Mirrors {@code XLII_RingPortAssault.launchAssault()}'s FID-launch/dismiss/transient-reopen
 * shape - confirmed against that class' own source - scaled down: no multi-beat post-assault
 * RISKY/SAFE framework, just a single boarding scene on win, same simplification
 * {@code XLII_KoriStrike}'s "Boarding the flagship" already uses for its own fixed-outcome beats.
 * <p>
 * Uses the mod's real Intelligence Office faction directly rather than a faction-swap hack - see
 * {@code .claude/systems/longsight.md}'s "Check whether a fleet you're about to spawn represents
 * a real in-mod faction" gotcha. Hostility is forced via the {@code MemFlags} override, the same
 * mechanism {@code XLII_OfficeSystem.applyHostility()} uses, not a faction reassignment.
 * <p>
 * On a loss, the picket is despawned and {@code $XLII_wasteNotInstallationFailed} is set - there
 * is no retry. The installation scene picks the flag up on the next visit and offers only
 * "Survey the wreckage", which resolves the quest without the items.
 */
public class XLII_LaunchWasteNotInstallationFight extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_LaunchWasteNotInstallationFight.class);

    private static final String PICKET_SEED_KEY = "$XLII_wasteNotPicketSeed";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        final CampaignFleetAPI picket = createPicketFleet();
        if (picket == null) {
            log.error("Draconis: Waste Not installation fight - failed to create picket fleet, aborting");
            dialog.dismiss();
            return false;
        }

        SectorEntityToken installation = resolveInstallationEntity();
        if (installation != null && installation.getContainingLocation() != null) {
            installation.getContainingLocation().addEntity(picket);
            picket.setLocation(installation.getLocation().x, installation.getLocation().y);
        } else {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player.getContainingLocation() != null) {
                player.getContainingLocation().addEntity(picket);
                picket.setLocation(player.getLocation().x, player.getLocation().y);
            }
        }

        FIDConfig config = new FIDConfig();
        config.showCommLinkOption = false;
        config.showWarningDialogWhenNotHostile = false;
        config.dismissOnLeave = true;
        // Same bug fix as XLII_RingPortAssault/XLII_KoriStrike: without this, the pre-battle
        // screen offers a zero-combat disengage option. Safe to force here - see class doc above.
        config.noLeaveOptionOnFirstEngagement = true;
        config.delegate = new BaseFIDDelegate() {
            private boolean won = false;

            @Override
            public void postPlayerSalvageGeneration(InteractionDialogAPI d,
                    FleetEncounterContext ctx, CargoAPI salvage) {
                won = true;
            }

            @Override
            public void notifyLeave(InteractionDialogAPI d) {
                if (picket.getContainingLocation() != null) {
                    picket.despawn();
                }
                if (won) {
                    reopenForBoarding();
                } else {
                    // noLeaveOptionOnFirstEngagement forces real combat before any leave option
                    // appears, so reaching here always means a fought, lost engagement, never a
                    // zero-combat bail - safe to treat as a genuine, permanent defeat rather than
                    // a free retry. Does NOT reopen a dialog immediately - a real loss can trigger
                    // vanilla's own forced retreat/teleport-away on defeat, so the player may not
                    // even be at the installation by the time this callback fires. Instead this
                    // just flags the failure; the installation's own rules.csv scene
                    // (# [WASTE NOT] Installation) detects the flag and resolves the quest -
                    // without the items - the next time the player actually interacts with the
                    // planet again, however long that takes.
                    Global.getSector().getMemoryWithoutUpdate().set("$XLII_wasteNotInstallationFailed", true);
                    log.info("Draconis: Waste Not installation fight - player lost, flagged for resolution on next visit");
                }
            }
        };

        final FleetInteractionDialogPluginImpl fid = new FleetInteractionDialogPluginImpl(config);

        dialog.dismiss();

        // Open FID on next frame after dialog has fully closed.
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(fid, picket);
                }
            }
        });

        return true;
    }

    /** Resolves the installation planet from the id {@code WasteNotMission.pickPlanets()} stored - it's a real planet, not a custom entity. */
    private static SectorEntityToken resolveInstallationEntity() {
        String id = Global.getSector().getMemoryWithoutUpdate().getString(WasteNotMission.MEM_INSTALLATION_PLANET_ID);
        return id == null ? null : Global.getSector().getEntityById(id);
    }

    /** Reopens on the installation entity, once the FID dialog has fully closed, to narrate boarding. */
    private static void reopenForBoarding() {
        final SectorEntityToken installation = resolveInstallationEntity();
        if (installation == null) return;

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(
                            new RuleBasedInteractionDialogPluginImpl("XLII_WasteNotInstallationBoarding"),
                            installation);
                }
            }
        });
    }

    /**
     * Scaled well below the finale's Forty-Second Battlegroup - a hidden site trying not to be found.
     * <p>
     * Seeded from memory so XLII_ShowWasteNotPicket's preview builds the same ships the player
     * then fights. The preview's copy is never spawned, so its hostility flags are harmless.
     */
    public static CampaignFleetAPI createPicketFleet() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (!mem.contains(PICKET_SEED_KEY)) {
            mem.set(PICKET_SEED_KEY, new Random().nextLong());
        }

        FleetParamsV3 params = new FleetParamsV3();
        params.factionId = Factions.INTELLIGENCE_OFFICE;
        params.fleetType = FleetTypes.PATROL_SMALL;
        params.quality   = 7f;
        params.combatPts = 200f;
        params.random    = new Random(mem.getLong(PICKET_SEED_KEY));

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet != null) {
            fleet.setName("Site Security");
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
            // The Office won't make a fuss over a site it can't admit exists - no AIO rep hit.
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        }
        return fleet;
    }
}
