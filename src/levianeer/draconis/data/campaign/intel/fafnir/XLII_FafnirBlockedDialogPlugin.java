package levianeer.draconis.data.campaign.intel.fafnir;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import levianeer.draconis.data.campaign.XLII_CampaignPlugin;
import org.apache.log4j.Logger;

import java.util.Map;

/**
 * Intercepts player interaction with any of the three Fafnir jump points when
 * {@code $fafnirAccessGranted} is false. Handles three unlock paths:
 * <ul>
 *   <li>TT Courier - player has {@code $fafnirTTQuestActive}; credential option offered at
 *       any Fafnir jump point</li>
 *   <li>Ring-Port contractor - player has {@code $fafnirRingPortQuestActive}; credential
 *       option offered only at the pirate jump point</li>
 *   <li>Brute Force - costs {@value #BRUTE_FORCE_SP_COST} Story Points and
 *       {@value #BRUTE_FORCE_CR_PENALTY_PCT}% fleet-wide CR; available at any jump point</li>
 * </ul>
 *
 * Registered by {@link XLII_CampaignPlugin#pickInteractionDialogPlugin}.
 */
public class XLII_FafnirBlockedDialogPlugin implements InteractionDialogPlugin {

    private static final Logger log = Global.getLogger(XLII_FafnirBlockedDialogPlugin.class);

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final String JP_ID_IN     = "XLII_fafnir_jump_point_in";
    public static final String JP_ID_OUT    = "XLII_fafnir_jump_point_out";
    public static final String JP_ID_PIRATE = "XLII_fafnir_jump_point_pirate";

    static final int   BRUTE_FORCE_SP_COST      = 10;
    static final float BRUTE_FORCE_CR_PENALTY   = 0.35f;
    static final int   BRUTE_FORCE_CR_PENALTY_PCT = 35;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private enum State {
        DENIED,
        ACCESS_GRANTED
    }

    private static final String OPT_ACKNOWLEDGE          = "acknowledge";
    private static final String OPT_TT_CREDENTIALS       = "tt_credentials";
    private static final String OPT_RING_PORT_CREDENTIALS = "ring_port_credentials";
    private static final String OPT_BRUTE_FORCE          = "brute_force";
    private static final String OPT_PROCEED              = "proceed";

    private InteractionDialogAPI dialog;
    private State state;
    private final boolean isPirateJP;
    private final SectorEntityToken jumpPoint;
    private String pendingPath;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public XLII_FafnirBlockedDialogPlugin(SectorEntityToken jumpPoint) {
        this.jumpPoint = jumpPoint;
        isPirateJP = isLinkedToInSystemJP(jumpPoint, JP_ID_PIRATE);
    }

    /**
     * Determines whether a hyperspace-side jump point corresponds to a specific in-system
     * jump point, by checking the in-system JP's destinations list.
     * <p>
     * {@code autogenerateHyperspaceJumpPoints()} populates each in-system JP's
     * {@code getDestinations()} with the associated hyperspace entry it generated.
     */
    private static boolean isLinkedToInSystemJP(SectorEntityToken hyperJP, String inSystemJPId) {
        StarSystemAPI fafnir = Global.getSector().getStarSystem("Fafnir");
        if (fafnir == null) return false;
        SectorEntityToken inSystemToken = fafnir.getEntityById(inSystemJPId);
        if (!(inSystemToken instanceof JumpPointAPI)) return false;
        JumpPointAPI inSystemJP = (JumpPointAPI) inSystemToken;
        for (JumpPointAPI.JumpDestination dest : inSystemJP.getDestinations()) {
            if (dest.getDestination() == hyperJP) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        showDenied();
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        String key = (String) optionData;
        dialog.getOptionPanel().clearOptions();

        switch (state) {
            case DENIED:
                if (OPT_ACKNOWLEDGE.equals(key)) {
                    dialog.dismiss();
                } else if (OPT_TT_CREDENTIALS.equals(key)) {
                    pendingPath = FafnirAccessStrings.PATH_TT_COURIER;
                    grantAccess(pendingPath);
                    showAccessGranted();
                } else if (OPT_RING_PORT_CREDENTIALS.equals(key)) {
                    pendingPath = FafnirAccessStrings.PATH_RING_PORT;
                    grantAccess(pendingPath);
                    showAccessGranted();
                }
                break;

            case ACCESS_GRANTED:
                if (OPT_PROCEED.equals(key)) {
                    dialog.dismiss();
                    openJPNextFrame();
                } else if (OPT_BRUTE_FORCE.equals(key)) {
                    // SetStoryOption calls optionSelected(OPT_BRUTE_FORCE) after confirm() returns.
                    // By then state is ACCESS_GRANTED and confirm() has already added OPT_PROCEED -
                    // but the clearOptions() at the top of this method wiped it. Re-add it.
                    dialog.getOptionPanel().addOption(FafnirAccessStrings.OPT_APPROACH_JP, OPT_PROCEED);
                }
                break;

            default:
                dialog.dismiss();
                break;
        }
    }

    @Override
    public void optionMousedOver(String text, Object optionData) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void backFromEngagement(EngagementResultAPI result) {}

    @Override
    public Object getContext() { return null; }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    // -------------------------------------------------------------------------
    // Dialog pages
    // -------------------------------------------------------------------------

    private void showDenied() {
        state = State.DENIED;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        boolean hasTT      = mem.getBoolean(FafnirAccessStrings.MEM_TT_QUEST_ACTIVE);
        boolean hasRingPort = mem.getBoolean(FafnirAccessStrings.MEM_RP_QUEST_ACTIVE);

        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();

        text.addPara(FafnirAccessStrings.MILITARY_DENIED_PARA1);
        text.addPara(FafnirAccessStrings.MILITARY_DENIED_PARA2);
        text.addPara(FafnirAccessStrings.MILITARY_DENIED_PARA3);

        if (hasTT) {
            options.addOption(FafnirAccessStrings.OPT_TT_CREDENTIALS, OPT_TT_CREDENTIALS);
        }
        if (hasRingPort && isPirateJP) {
            options.addOption(FafnirAccessStrings.OPT_RING_PORT_CREDENTIALS, OPT_RING_PORT_CREDENTIALS);
        }

        // Brute force - always shown; grayed out (not hidden) when player has insufficient SPs
        options.addOption(
                String.format(FafnirAccessStrings.OPT_BRUTE_FORCE_FMT, BRUTE_FORCE_SP_COST),
                OPT_BRUTE_FORCE
        );
        SetStoryOption.StoryOptionParams bruteParams = new SetStoryOption.StoryOptionParams(
                OPT_BRUTE_FORCE,
                BRUTE_FORCE_SP_COST,
                "",   // no bonus XP
                null,
                "Forced Rift transit"
        );
        SetStoryOption.set(dialog, bruteParams,
                new SetStoryOption.BaseOptionStoryPointActionDelegate(dialog, bruteParams) {
                    @Override
                    public void confirm() {
                        applyBruteForce();
                        pendingPath = FafnirAccessStrings.PATH_BRUTE_FORCE;
                        grantAccess(pendingPath);
                        showAccessGranted();
                    }
                }
        );

        options.addOption(FafnirAccessStrings.OPT_ACKNOWLEDGE, OPT_ACKNOWLEDGE);
    }

    private void showAccessGranted() {
        state = State.ACCESS_GRANTED;
        TextPanelAPI text    = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();

        if (FafnirAccessStrings.PATH_TT_COURIER.equals(pendingPath)) {
            text.addPara(FafnirAccessStrings.TT_AUTH_PARA1);
            text.addPara(FafnirAccessStrings.TT_AUTH_PARA2);
            text.addPara(FafnirAccessStrings.TT_AUTH_PARA3);
        } else if (FafnirAccessStrings.PATH_RING_PORT.equals(pendingPath)) {
            text.addPara(FafnirAccessStrings.RING_PORT_AUTH_PARA1);
            text.addPara(FafnirAccessStrings.RING_PORT_AUTH_PARA2);
            text.addPara(FafnirAccessStrings.RING_PORT_AUTH_PARA3);
        } else {
            // brute_force
            text.addPara(FafnirAccessStrings.BRUTE_SUCCESS_PARA1);
            text.addPara(FafnirAccessStrings.BRUTE_SUCCESS_PARA2);
            text.addPara(FafnirAccessStrings.BRUTE_SUCCESS_PARA3);
        }

        options.addOption(FafnirAccessStrings.OPT_APPROACH_JP, OPT_PROCEED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Re-opens the vanilla jump point interaction on the next frame after this dialog
     * has fully dismissed. Since {@code $fafnirAccessGranted} is true at this point,
     * {@code pickInteractionDialogPlugin} returns null and the vanilla jump dialog fires.
     */
    private void openJPNextFrame() {
        final SectorEntityToken jp = jumpPoint;
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(jp);
                }
            }
        });
    }

    private void grantAccess(String path) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(FafnirAccessStrings.MEM_ACCESS_GRANTED, true);
        mem.set(FafnirAccessStrings.MEM_ENTRY_PATH, path);
        if (FafnirAccessStrings.PATH_TT_COURIER.equals(path)) {
            mem.unset(FafnirAccessStrings.MEM_TT_QUEST_ACTIVE);
        }
        if (FafnirAccessStrings.PATH_RING_PORT.equals(path)) {
            mem.unset(FafnirAccessStrings.MEM_RP_QUEST_ACTIVE);
        }

        // Apply DDA reputation delta based on how the player entered
        float repDelta;
        if (FafnirAccessStrings.PATH_TT_COURIER.equals(path)) {
            repDelta = FafnirAccessStrings.REP_GRANT_TT;
        } else if (FafnirAccessStrings.PATH_RING_PORT.equals(path)) {
            repDelta = FafnirAccessStrings.REP_GRANT_RP;
        } else {
            repDelta = FafnirAccessStrings.REP_GRANT_BF;
        }
        if (repDelta != 0f) {
            FactionAPI player = Global.getSector().getPlayerFaction();
            player.adjustRelationship("XLII_draconis", repDelta);
        }

        log.info("Draconis: Fafnir access granted via path=" + path + ", repDelta=" + repDelta);
    }

    private void applyBruteForce() {
        // SP deduction is handled by the SetStoryOption framework before confirm() is called
        // Apply CR penalty to all player fleet ships
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                .getFleetData().getMembersListCopy()) {
            member.getRepairTracker().applyCREvent(-BRUTE_FORCE_CR_PENALTY, "Forced Rift transit");
        }

        log.info(String.format(
                "Draconis: Brute force transit - spent %d SP, applied %.0f%% CR penalty",
                BRUTE_FORCE_SP_COST, BRUTE_FORCE_CR_PENALTY * 100f
        ));
    }
}