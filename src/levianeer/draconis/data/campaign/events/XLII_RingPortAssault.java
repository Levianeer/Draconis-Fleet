package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * InteractionDialogPlugin for the Act 3 Ring-Port station assault.
 * Registered by XLII_CampaignPlugin.pickInteractionDialogPlugin when the player
 * docks at Ring-Port while it is still pirate-controlled.
 * <p>
 * Flow:
 *   Player chooses "Launch assault" -> FID opens on next frame with
 *   playerAttackingStation = true -> on victory, post-assault dialog re-opens
 *   for five sequential decision beats -> marines deducted at each beat ->
 *   performance flags set -> market faction changes to XLII_draconis.
 */
public class XLII_RingPortAssault implements InteractionDialogPlugin {

    private static final Logger log = Global.getLogger(XLII_RingPortAssault.class);

    public static final String STATION_ENTITY_ID = "fafnir_pirate_station";
    public static final String MARKET_ID         = "pirateStation_market";
    public static final String MEM_TAKEN         = "$XLII_ringPortTaken";
    public static final String MEM_MARINES_LOST  = "$XLII_raidMarinesLost";
    public static final String MEM_RAID_PRECISE  = "$XLII_raidPrecise";
    public static final String MEM_RAID_COSTLY   = "$XLII_raidCostly";
    public static final String MEM_BEAT3_RISKY   = "$XLII_beat3Risky";
    public static final String MEM_BEAT5_SP      = "$XLII_beat5SP";
    public static final String MEM_BEAT5_RISKY   = "$XLII_beat5Risky";
    public static final String MEM_BEAT5_SAFE    = "$XLII_beat5Safe";

    // Use fully-qualified path to avoid collision with com.fs.starfarer.api.impl.campaign.ids.Factions
    private static final String DRACONIS_FACTION = levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

    public static final int MINIMUM_MARINES = 500;

    // Briefing options
    private static final String OPT_ASSAULT  = "xlii_assault_launch";
    private static final String OPT_LEAVE    = "xlii_assault_leave";

    // Post-assault beat options
    private static final String OPT_RISKY    = "xlii_beat_risky";
    private static final String OPT_SAFE     = "xlii_beat_safe";
    private static final String OPT_SP       = "xlii_beat_sp";
    private static final String OPT_CONTINUE     = "xlii_raid_continue";
    private static final String OPT_BEAT_ADVANCE = "xlii_beat_advance";
    private static final String OPT_CONFIRM      = "xlii_beat_confirm";
    private static final String OPT_BACK         = "xlii_beat_back";

    // Marine loss ranges [min, max] per beat (index 0 = beat 1, ... index 4 = beat 5)
    private static final int[][] RISKY_RANGE = {    {75,90},    {55,70},    {45,60},    {20,30},    {15,25} };
    private static final int[][] SAFE_RANGE  = {    {10,20},    {20,30},    {30,45},    {40,60},    {20,40} };
    private static final int[][] SP_RANGE    = {    {5,10},     {5,10},     {5,10},     {10,15},    {0,3}   };

    private static final Color SP_COLOR = new Color(163, 239, 128);

    // Beat resolution text [beatIdx 0-4][choice: RISKY=0, SAFE=1, SP=2]
    private static final String[][] BEAT_RESOLUTION_TEXT = {
        { // Beat 1 - Breach
            // Risky
            "Your lead elements hit the blast doors running and the defenders were ready for exactly that. The kill zone " +
                    "is a forty-metre corridor and the fire lanes cross it at angles designed to negate cover - your first " +
                    "stack takes casualties before the second one is through the threshold. Powered armour absorbs what " +
                    "it can. Some of it isn't enough. By the time your force has numbers on the other side of the choke " +
                    "the equation flips - hard - open corridors, close quarters, and the defenders losing the geometry that " +
                    "made them dangerous. You're through in six minutes. The station knows it happened. " +
                    "Every position ahead just heard it.",
            // Safe
            "Two marines per cycle. The port-side airlocks were built for maintenance crews moving equipment, not heavy assault " +
                    "teams moving fast, and the mechanism doesn't care about your timeline. Your first fireteam is inside " +
                    "for eleven minutes before the second cycle completes - eleven minutes holding a junction in a damaged " +
                    "station with emergency lighting and no backup. A defender patrol finds them at minute eight. The " +
                    "engagement is brief and contained but it costs you the clean entry. By the time your force has critical " +
                    "mass on the inside the station's alert posture has shifted - not to your exact position, not yet, " +
                    "but something has changed and the defenders further in can feel it.",
            // SP
            "Korrin's maintenance window opens on schedule and your fireteams cycle through the cargo lock four at a " +
                    "time, quiet, spreading through the starboard section in the thirty minutes it takes to get your " +
                    "full complement inside. The defenders' patrol rotation has a gap on the starboard approach that " +
                    "Korrin's people created three weeks earlier and maintained since. Nobody checks the manifest. Nobody " +
                    "checks the lock. The first indication something is wrong is the sound of powered armour in " +
                    "corridors that should be running empty - and by then your lead elements are already past " +
                    "the first internal checkpoint and moving."
        },
        { // Beat 2 - First Resistance
            // Risky
            "The kill zone earns its name. The barricade geometry is deliberate - interlocking angles that force your " +
                    "lead stack into a compressed approach before they can spread out and use their numbers, and the " +
                    "defenders have every inch of it pre-sighted. Your first marines through take the worst of it. " +
                    "Powered armour handles direct hits but the volume of fire at that range finds the gaps. You're through " +
                    "the first barricade in four minutes and the second in six - faster than the defenders planned for, " +
                    "slower than you needed. The ones that broke fell back in good order, not routing, withdrawing with " +
                    "discipline toward the station's interior. The station heard every second of this engagement and " +
                    "the positions ahead have adjusted accordingly.",
            // Safe
            "The flanking team enters the maintenance corridor and goes off comms. Four minutes and twelve seconds of " +
                    "sustained fire from your pinning force at the junction mouth, burning ammunition and holding position " +
                    "while the rest of the station listens to an engagement that isn't resolving. The flanking team comes " +
                    "out exactly where Korrin's schematics said they would, behind the left barricade, and the junction " +
                    "clears in under ninety seconds once the defenders are taking fire from two directions. Clean. " +
                    "The cost is the clock - the engagement ran long enough for the station's deeper positions to hear it " +
                    "and start consolidating. The junction was cheaper than a direct push. What the junction " +
                    "bought for the defenders further in is harder to price.",
            // SP
            "The lights die across 200 feet of corridor simultaneously and the ventilation surge hits a second later " +
                    "- a disorienting combination that isn't lethal but breaks the defenders' disciplined spacing in the " +
                    "seconds it takes them to process what's happening. Your lead elements are already moving. The kill " +
                    "zone that the barricade geometry created stops existing the moment the defenders stop being able to " +
                    "reliably place targets in it. Thirty seconds is enough - your stack is through the first barricade " +
                    "before the emergency lighting kicks in and the geometry has changed entirely. The defenders that " +
                    "broke contact did so in better order than you'd have liked, but the junction " +
                    "is yours and it cost you almost nothing to take it."
        },
        { // Beat 3 - Civilians
            // Risky
            "Your marines move and the crowd breaks around them in the way that crowds break when they have nowhere " +
                    "organised to go - in every direction at once, the loudest people moving fastest and the rest following " +
                    "sound rather than sense. Your formation holds its line as best it can but two hundred panicked people " +
                    "in a corridor designed for a quarter of that number is chaos regardless of discipline. The retreating " +
                    "defender unit uses every second of it, moving against the flow with their weapons down and their " +
                    "heads low, and by the time your lead elements have line of sight again the unit has broken contact " +
                    "cleanly somewhere in the next section. You lost them. Some civilians were in the wrong place when " +
                    "your formation pushed through - not targeted, not deliberate, but present when the margins closed. " +
                    "The command centre is going to be harder than the numbers suggested, and the defenders " +
                    "arriving there will be angry in a way that has nothing to do with tactics but their defense paid in blood",
            // Safe
            "Twenty-three minutes to clear a corridor that your timeline allocated four. Your marines establish a perimeter " +
                    "at both ends and begin directing civilians through the side passages methodically - two at a time " +
                    "through the left branch, larger groups when the right branch clears, nobody moving faster than the " +
                    "slowest person who needs help moving. It works. Nobody who isn't a combatant gets hurt. The retreating " +
                    "defender unit walks through your perimeter's gap and reaches the command centre with time to spare, " +
                    "time to brief the veterans inside on what they'd observed about your force composition, your equipment, " +
                    "your approach patterns. The command centre knows exactly what's coming. It has had time to prepare " +
                    "for exactly what's coming. You bought twenty minutes of clean conduct at a price that compounds.",
            // SP
            "Your marines move into the corridor in full kit at a pace that is neither charge nor hesitation - deliberate, " +
                    "tight formation, weapons visible and pointed at the deck. It's a specific posture that takes training " +
                    "to hold under pressure and the civilians read it faster than words would have moved them. The crowd " +
                    "parts ahead of the formation like it has somewhere to go, pressing to the walls, clearing the corridor " +
                    "in under ninety seconds. The retreating defender unit loses the cover they'd planned on before they're " +
                    "halfway to the next junction - your lead elements are on them in open corridor with nowhere to " +
                    "consolidate, and the brief engagement there is as clean as anything you've run today. The command " +
                    "centre gets fewer defenders than it expected and no useful intelligence on your approach."
        },
        { // Beat 4 - Command Center
            // Risky
            "Both doors blow within four seconds of each other and both breach teams go in simultaneously against the " +
                    "most prepared fighting position in the station. The veterans inside had allocated their strength " +
                    "assuming exactly this - the entry points are brutal for the first marines through each door, a few " +
                    "seconds of concentrated fire before the teams can spread and the room's geometry starts working against " +
                    "the defenders instead of for them. It takes forty minutes to clear the command centre. Room by room, " +
                    "position by position, against people who trained everyone you've fought through to get here and who " +
                    "fight that way until the last position falls. You're through. The cost is paid. The inner sanctum is ahead.",
            // Safe
            "Full force on the primary door. The defenders had allocated thirty to forty percent of their strength to " +
                    "the secondary corridor expecting a split assault - hitting the primary entrance with everything you " +
                    "have means superior numbers against their main position rather than equal numbers at two points " +
                    "simultaneously. The primary door comes down and your force is through before the secondary defenders " +
                    "fully process what's happening. They collapse inward, falling back from a prepared position they " +
                    "can no longer justify holding, and your marines are waiting in the corridors. The fighting there is " +
                    "ugly in the way corridor fighting is always ugly - close range, no cover worth speaking of, powered " +
                    "armour making the difference - but it's fighting on ground you chose rather than ground the defenders " +
                    "prepared. Slower than a simultaneous breach. Significantly cleaner at every individual point of contact.",
            // SP
            "Fifty-eight seconds to get your comms specialist into the mining network and another four to confirm the " +
                    "command centre's internal channels are dark. The defenders inside can see each other across the " +
                    "room but they can't coordinate between the primary and secondary positions - can't confirm whether " +
                    "the other door is under assault, can't redistribute strength in response to what's happening somewhere " +
                    "they can't see. Both doors blow simultaneously and the secondary breach team is inside before the " +
                    "secondary defenders know the primary entrance is gone. Thirty seconds of confused veterans in a " +
                    "sealed room against a force that knows exactly what it's doing is enough. The command centre " +
                    "clears faster than any other approach would have managed."
        },
        { // Beat 5 - Last Stand
            // Risky
            "Your breach team goes in hard and the veterans make them earn every inch of it. Twelve people in a " +
                    "room the size of a ship's bridge with nowhere to go and nothing left to lose fight with a " +
                    "specificity that comes from knowing exactly what they're fighting for. The room clears in under two " +
                    "minutes. Some of your marines don't come back out. The ones that do are quiet in the way that " +
                    "soldiers get quiet when the arithmetic resolves and the numbers are what they are. You check the room. " +
                    "You count what it cost. The station is yours and this room is done and neither of those facts makes the other one easier.",
            // Safe
            "Most of them comply. Weapons down, hands visible, they walk out through your line one at a time and your " +
                    "marines process them with the efficiency of people who have been doing this for hours and are dead " +
                    "tired. Several come out in the first five minutes. Then a pause. Then three more, slower, like the decision " +
                    "was harder. The remaining holdouts make your marines come in for them - brief, costly engagements in " +
                    "the corners of the room where the veterans chose to make their stand. It takes twenty-three minutes " +
                    "total and costs you marines in the margins of a station that was already secured. The ones who walked " +
                    "out alive will remember the corridor they walked through, the marines who processed them, the choice " +
                    "they were given. Whether that memory matters to anything is a question for later.",
            // SP
            "The voice on the frequency is the station's commanding officer - you ask and she answers without hesitation, " +
                    "rank and posting from a fleet that hasn't existed in thirty cycles. You offer formal military terms. " +
                    "Full honours. Weapons collected not confiscated, personnel treated as prisoners of war under the " +
                    "Interstellar Transit Accords rather than criminals under Alliance law. There is a pause on the " +
                    "frequency that lasts long enough to mean something. Then: acknowledged. The door opens from the " +
                    "inside. The commander walks out first, unarmed, hands visible, spine straight. Eleven veterans " +
                    "follow in the same posture. Your marines receive them in silence. Every one of Korrin's embedded " +
                    "staff is watching. Nobody says out loud that August will care about how this looks. Nobody needs to."
        }
    };

    // Transition text between beats; index 4 (beat 5) is null - no transition follows
    private static final String[] BEAT_TRANSITION_TEXT = {
        "Your force consolidates and pushes inward through the station's older sections, following Korrin's schematics " +
                "into the original platform. The corridors narrow. Ahead, thermals pick up massed heat signatures at the primary junction.",
        "Your force pushes deeper, following the fastest route toward the command centre. The resistance behind you has " +
                "collapsed or withdrawn. Ahead the corridor opens into a wider transit section - and stops making sense on the thermals.",
        "The final approach to the command centre runs through the oldest section of the station - original platform " +
                "construction, built to survive industrial accidents. The blast doors are visible at the end of the corridor. " +
                "Thermals show the strongest concentration of the assault behind them.",
        "The command centre is yours. The organised resistance is broken. Your marines push through into the inner " +
                "operations room - the original platform core, smaller than the rest, one sealed door between you and the end of it.",
        null
    };

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private enum State { BRIEFING, POST_ASSAULT }
    private enum BeatChoice { RISKY, SAFE, SP }

    private InteractionDialogAPI dialog;
    private State state = State.BRIEFING;
    private int currentBeat = 0;
    private int totalMarinesLost = 0;
    private int startingMarines = 0;
    private String pendingOption = null;
    private final Random beatRandom = new Random();
    private final BeatChoice[] beatChoices = new BeatChoice[4]; // index 0 = beat 1's choice
    private BeatChoice beat5Choice = null;
    private int storyPointsSpent = 0;
    private int beatModifier = 0;

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        if (state == State.POST_ASSAULT) {
            showBeat(currentBeat);
        } else {
            showBriefing();
        }
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        if (text != null) {
            dialog.addOptionSelectedText(optionData);
        }
        String key = (String) optionData;

        if (state == State.POST_ASSAULT) {
            if (OPT_RISKY.equals(key)) {
                if (pendingOption == null) {
                    showBeatDetail(currentBeat, OPT_RISKY);
                } else {
                    advanceBeat(RISKY_RANGE, BeatChoice.RISKY);
                }
            } else if (OPT_SAFE.equals(key)) {
                if (pendingOption == null) {
                    showBeatDetail(currentBeat, OPT_SAFE);
                } else {
                    advanceBeat(SAFE_RANGE, BeatChoice.SAFE);
                }
            } else if (OPT_SP.equals(key)) {
                if (pendingOption == null) {
                    showBeatDetail(currentBeat, OPT_SP);
                }
                // When pendingOption != null: real SP option is on detail screen,
                // handled by SetStoryOption delegate - not routed here.
            } else if (OPT_CONFIRM.equals(key)) {
                String opt = pendingOption;
                pendingOption = null;
                if (OPT_RISKY.equals(opt)) {
                    advanceBeat(RISKY_RANGE, BeatChoice.RISKY);
                } else if (OPT_SAFE.equals(opt)) {
                    advanceBeat(SAFE_RANGE, BeatChoice.SAFE);
                }
            } else if (OPT_BACK.equals(key)) {
                pendingOption = null;
                showBeat(currentBeat);
            } else if (OPT_BEAT_ADVANCE.equals(key)) {
                if (currentBeat == 0) {
                    currentBeat = 1;
                    showBeat(1);
                } else if (currentBeat <= 5) {
                    showBeat(currentBeat);
                } else {
                    showRaidOutcome();
                }
            } else if (OPT_CONTINUE.equals(key)) {
                finalizeRaid();
            }
            return;
        }

        if (OPT_ASSAULT.equals(key)) {
            launchAssault();
        } else {
            leaveToNormalInteraction();
        }
    }

    @Override public void optionMousedOver(String text, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI result) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }

    // -------------------------------------------------------------------------
    // Interaction Visuals
    // -------------------------------------------------------------------------

    private static final InteractionDialogImageVisual APPROACH_IMAGE =
            new InteractionDialogImageVisual("illustrations", "XLII_orbital", 640, 400);

    private static final InteractionDialogImageVisual START_IMAGE =
            new InteractionDialogImageVisual("illustrations", "XLII_raid_preparation", 640, 400);

    private static final InteractionDialogImageVisual ASSAULT_IMAGE =
            new InteractionDialogImageVisual("illustrations", "XLII_covert_squad", 640, 400);

    private static final InteractionDialogImageVisual ENDGAME_IMAGE =
            new InteractionDialogImageVisual("illustrations", "XLII_facility_explosion", 640, 400);

    // -------------------------------------------------------------------------
    // Briefing
    // -------------------------------------------------------------------------

    private void showBriefing() {
        dialog.getVisualPanel().showImageVisual(APPROACH_IMAGE);
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara(
            "Ring-Port. Former Athebyne mining platform, expanded twice since the war ended. " +
            "The veterans who hold it rebuilt the docking arms themselves - assume Korrin's " +
            "schematics are more accurate than official records."
        );
        text.addPara(
            "First Fleet remnants. The originals are mostly gone. Their children run this " +
            "operation now, and the children have inherited the training and the grievance in " +
            "equal measure."
        );

        int marines = Global.getSector().getPlayerFleet().getCargo().getMarines();
        boolean canAssault = marines >= MINIMUM_MARINES;

        if (!canAssault) {
            text.addPara(
                "Minimum complement for a viable assault: " + MINIMUM_MARINES + " marines. " +
                "Your fleet only has " + marines + "."
            );
        }

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.addOption("Continue.", OPT_ASSAULT);
        if (!canAssault) {
            opts.setEnabled(OPT_ASSAULT, false);
        }
        opts.addOption("Leave.", OPT_LEAVE);
    }

    // -------------------------------------------------------------------------
    // Leave - reopen normal station interaction
    // -------------------------------------------------------------------------

    private void leaveToNormalInteraction() {
        final SectorEntityToken station = Global.getSector().getEntityById(STATION_ENTITY_ID);
        dialog.dismiss();
        if (station == null) return;

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;
            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    // Use explicit plugin to bypass pickInteractionDialogPlugin,
                    // so the assault briefing does not re-trigger.
                    Global.getSector().getCampaignUI().showInteractionDialog(
                            new RuleBasedInteractionDialogPluginImpl(), station);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Combat setup
    // -------------------------------------------------------------------------

    private void launchAssault() {
        startingMarines = Global.getSector().getPlayerFleet().getCargo().getMarines();
        final CampaignFleetAPI defenders = createDefenderFleet();
        if (defenders == null) {
            log.error("Draconis: XLII_RingPortAssault - failed to create defender fleet, aborting assault");
            dialog.dismiss();
            return;
        }

        // Place defender fleet at the station's location
        SectorEntityToken station = Global.getSector().getEntityById(STATION_ENTITY_ID);
        if (station != null && station.getContainingLocation() != null) {
            station.getContainingLocation().addEntity(defenders);
            defenders.setLocation(station.getLocation().x, station.getLocation().y);
        } else {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player.getContainingLocation() != null) {
                player.getContainingLocation().addEntity(defenders);
                defenders.setLocation(player.getLocation().x, player.getLocation().y);
            }
        }

        FIDConfig config = new FIDConfig();
        config.playerAttackingStation = true;
        config.showCommLinkOption = false;
        config.showWarningDialogWhenNotHostile = false;
        config.dismissOnLeave = true;
        config.delegate = new BaseFIDDelegate() {
            private boolean won = false;

            @Override
            public void postPlayerSalvageGeneration(InteractionDialogAPI d,
                    FleetEncounterContext ctx, CargoAPI salvage) {
                won = true;
            }

            @Override
            public void notifyLeave(InteractionDialogAPI d) {
                if (won) {
                    state = State.POST_ASSAULT;
                    showPostAssaultDialog();
                } else {
                    // Player left without winning - despawn the defender fleet so it
                    // doesn't wander the sector as an orphaned pirate patrol.
                    if (defenders.getContainingLocation() != null) {
                        defenders.despawn();
                    }
                    log.info("Draconis: XLII_RingPortAssault - player did not complete assault, defenders despawned");
                }
            }
        };

        final FleetInteractionDialogPluginImpl fid = new FleetInteractionDialogPluginImpl(config);

        dialog.dismiss();

        // Open FID on next frame after dialog has fully closed
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(fid, defenders);
                }
            }
        });
    }

    private CampaignFleetAPI createDefenderFleet() {
        MarketAPI market = Global.getSector().getEconomy().getMarket(MARKET_ID);
        FleetParamsV3 params = new FleetParamsV3();
        // Create with Draconis faction to get Draconis ship hulls, then immediately switch
        // to pirates so the FID station assault sees a hostile fleet and resolves correctly.
        // Ship composition is locked at createFleet() time; faction is metadata after that.
        params.factionId  = DRACONIS_FACTION;
        params.fleetType  = FleetTypes.PATROL_LARGE;
        params.quality    = 0.5f;
        params.combatPts  = 200f;
        params.random     = new Random();
        if (market != null) {
            params.source = market;
        }
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet != null) {
            fleet.setFaction(Factions.PIRATES, true); // hostile for FID; ship pool already set
            fleet.setName("First Fleet");
            fleet.getMemoryWithoutUpdate().set("$XLII_ringPortDefenders", true);
        }
        return fleet;
    }

    // -------------------------------------------------------------------------
    // Post-assault dialog - re-open self after FID closes
    // -------------------------------------------------------------------------

    private void showPostAssaultDialog() {
        final SectorEntityToken station = Global.getSector().getEntityById(STATION_ENTITY_ID);
        final XLII_RingPortAssault self = this;

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;
            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    SectorEntityToken target = station != null ? station
                            : Global.getSector().getPlayerFleet();
                    Global.getSector().getCampaignUI().showInteractionDialog(self, target);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Beat modifier resolution
    // -------------------------------------------------------------------------

    private int computeModifierForBeat(int beat) {
        int mod = 0;
        BeatChoice c1 = beatChoices[0], c2 = beatChoices[1],
                   c3 = beatChoices[2], c4 = beatChoices[3];
        if (beat == 2 && c1 != null) {
            if (c1 == BeatChoice.RISKY)     mod += 20;
            else if (c1 == BeatChoice.SAFE) mod -= 15;
            else if (c1 == BeatChoice.SP)   mod -= 10;
        }
        if (beat == 3) {
            if (c1 == BeatChoice.SAFE)  mod += 25;
            // c2==SAFE: no modifier (self-evacuation justification doesn't hold for Beat 3's non-evacuating civilians)
        }
        if (beat == 4) {
            if (c2 != null) {
                if (c2 == BeatChoice.RISKY)      mod += 25;
                else if (c2 == BeatChoice.SAFE)  mod += 30;
                else if (c2 == BeatChoice.SP)    mod -= 15;
            }
            if (c3 != null) {
                if (c3 == BeatChoice.RISKY)     mod += 35;
                else if (c3 == BeatChoice.SAFE) mod += 20;
                else if (c3 == BeatChoice.SP)   mod -= 15;
            }
        }
        if (beat == 5) {
            if (c3 != null) {
                if (c3 == BeatChoice.RISKY)     mod += 15;
                else if (c3 == BeatChoice.SAFE) mod -= 20;
                else if (c3 == BeatChoice.SP)   mod -= 25;
            }
            if (c4 != null) {
                if (c4 == BeatChoice.RISKY)     mod += 20;
                // SAFE: 0
                else if (c4 == BeatChoice.SP)   mod -= 20;
            }
        }
        return mod;
    }

    // -------------------------------------------------------------------------
    // Beat context lines - narrative feedback explaining why a beat is harder/easier
    // based on prior choices; mirrors computeModifierForBeat but outputs prose.
    // Empty list = no prior choice had a non-zero modifier on this beat.
    // -------------------------------------------------------------------------

    private List<String> getBeatContextLines(int beat) {
        List<String> lines = new ArrayList<>();
        BeatChoice c1 = beatChoices[0], c2 = beatChoices[1],
                   c3 = beatChoices[2], c4 = beatChoices[3];

        if (beat == 2 && c1 != null) {
            if (c1 == BeatChoice.RISKY) {
                lines.add("The frontal breach announced your entry to the entire station - every position ahead has had time to adjust.");
            } else if (c1 == BeatChoice.SAFE) {
                lines.add("The airlock feed kept your entry quiet - the defenders further in don't have an accurate count on your numbers.");
            } else if (c1 == BeatChoice.SP) {
                lines.add("Korrin's route brought you in through the starboard section - the junction's fire lanes " +
                        "were set for an assault from the docking approaches. You're not coming from where they planned.");
            }
        }
        if (beat == 3 && c1 == BeatChoice.SAFE) {
            lines.add("The airlock's cycle time gave the retreating unit a head start your force couldn't close.");
        }
        if (beat == 4) {
            if (c2 == BeatChoice.RISKY) {
                lines.add("The junction engagement ran long enough for the command centre to hear it - they've had time to consolidate.");
            } else if (c2 == BeatChoice.SAFE) {
                lines.add("The suppress-and-flank took longer than a direct push would have - the command centre used every minute of the extended engagement.");
            } else if (c2 == BeatChoice.SP) {
                lines.add("Korrin's cut resolved the junction before the rest of the station could react - " +
                        "the command centre had less time to consolidate than it expected.");
            }
            if (c3 == BeatChoice.RISKY) {
                lines.add("The unit that broke contact through the civilian corridor reached the command centre intact, " +
                        "with full intelligence on your force and time to use it.");
            } else if (c3 == BeatChoice.SAFE) {
                lines.add("The retreating unit reached the command centre unimpeded and had time to brief the defenders on what they'd observed.");
            } else if (c3 == BeatChoice.SP) {
                lines.add("The retreating unit didn't make it to the command centre - fewer defenders inside, and nothing to brief them on.");
            }
        }
        if (beat == 5) {
            if (c3 == BeatChoice.RISKY) {
                lines.add("The feeds from the civilian corridor have been running on every screen in this room. These " +
                        "veterans know the people who work those sections.");
            } else if (c3 == BeatChoice.SAFE) {
                lines.add("Your conduct in the civilian corridor was observed - the veterans in this room have been watching the station feeds.");
            } else if (c3 == BeatChoice.SP) {
                lines.add("The disciplined advance through the civilian corridor was on camera - the veterans here have " +
                        "had time to consider what kind of force moves like that.");
            }
            if (c4 == BeatChoice.RISKY) {
                lines.add("The command centre was cleared room by room - the veterans here know what happened to the people who trained them.");
            } else if (c4 == BeatChoice.SP) {
                lines.add("The comms blackout at the command centre was clean and witnessed - everyone on the station saw how that ended.");
            }
            // c4 == SAFE: no modifier on beat 5
        }
        return lines;
    }

    // -------------------------------------------------------------------------
    // Beat dispatcher
    // -------------------------------------------------------------------------

    private void showBeat(int n) {
        beatModifier = computeModifierForBeat(n);
        dialog.getOptionPanel().clearOptions();
        switch (n) {
            case 0: showBeat0(); break;
            case 1: showBeat1(); break;
            case 2: showBeat2(); break;
            case 3: showBeat3(); break;
            case 4: showBeat4(); break;
            case 5: showBeat5(); break;
        }
    }

    // -------------------------------------------------------------------------
    // Beat 0 - Intro
    // -------------------------------------------------------------------------

    private void showBeat0() {
        dialog.getVisualPanel().showImageVisual(APPROACH_IMAGE);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();

        text.addPara("The station is venting from three sections. Your tactical display shows Ring-Port's defensive "
                + "grid dark - the orbital engagement gutted it. What's left isn't an organised military defence. It's "
                + "survivors in familiar corridors with nothing left to lose.");

        text.addPara("Korrin's last transmission was forty minutes ago. His people are in position, staged, and "
                + "waiting on your signal. The civilian population is sheltering in the station's lower decks - the "
                + "fighting, when it comes, will be in the upper corridors and operations sections.");

        text.addPara("Your marines are staged in the forward bays. Five hundred combat-ready personnel in powered "
                + "armor, waiting on your order. The defenders that remain are professionals - fewer than they were an "
                + "hour ago, but trained by people who fought a thirty-year war and survived it. The station's layout is "
                + "what levels the odds back in their favour.");

        opts.addOption("Continue.", OPT_BEAT_ADVANCE);
    }

    // -------------------------------------------------------------------------
    // Beat 1 - Breach Point
    // -------------------------------------------------------------------------

    private void showBeat1() {
        dialog.getVisualPanel().showImageVisual(START_IMAGE);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();

        text.addPara("Korrin's schematics are on your display. Three viable entry points.");

        text.addPara("The main docking bay is the obvious approach - wide, direct access to primary corridors. "
                + "The defenders know it. Thermals show prepared positions behind the blast doors, "
                + "overlapping fields of fire set up for a power-armoured assault. "
                + "They've had decades to think about this.");

        text.addPara("The secondary option: a cluster of maintenance airlocks on the port side - "
                + "old mining access, cycling two marines at a time. The defenders aren't watching them closely. "
                + "The problem is the feed rate - committed the moment the first team is inside, "
                + "and if they're discovered early, that team holds the beachhead alone.");

        text.addPara("Korrin flagged a third option. A cargo transfer lock on the starboard side, "
                + "maintenance cycle his people arranged, cycling a full fireteam at once. "
                + "The defenders pulled that watch rotation three weeks ago.");

        opts.addOption("Main docking bay assault.", OPT_RISKY);
        opts.addOption("Maintenance airlock breach.", OPT_SAFE);
        opts.addOption("Korrin's cargo lock.", OPT_SP, SP_COLOR, null);
    }

    // -------------------------------------------------------------------------
    // Beat 2 - First Resistance
    // -------------------------------------------------------------------------

    private void showBeat2() {
        dialog.getVisualPanel().showImageVisual(ASSAULT_IMAGE);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();

        text.addPara("Your lead elements push in and stop. The junction ahead is a three-way chokepoint "
                + "the defenders have fortified properly - not improvised. Interlocking barricades designed to "
                + "eliminate your advantage, overlapping fire lanes, two dozen defenders with "
                + "disciplined spacing. These ones trained for this kind of engagement.");

        for (String line : getBeatContextLines(2)) { text.addPara(line); }

        text.addPara("A direct push means eating fire at the worst angle - the barricade geometry channels "
                + "you into a kill zone before you can spread. Viable. They know it and have planned for it.");

        text.addPara("A parallel maintenance corridor runs alongside the junction - tight, single file, "
                + "exits behind the left barricade. Your flanking team goes off comms for four minutes "
                + "while the rest of your force pins the front. Four minutes is long if something goes wrong.");

        text.addPara("Korrin's people are still in position. One controls the junction's lighting and "
                + "ventilation from a maintenance terminal two sections back.");

        opts.addOption("Direct breach.", OPT_RISKY);
        opts.addOption("Suppress and flank", OPT_SAFE);
        opts.addOption("Korrin's infrastructure.", OPT_SP, SP_COLOR, null);
    }

    // -------------------------------------------------------------------------
    // Beat 3 - Civilians
    // -------------------------------------------------------------------------

    private void showBeat3() {
        dialog.getVisualPanel().showImageVisual(ASSAULT_IMAGE);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();

        text.addPara("Your lead elements round a corner and stop.");

        for (String line : getBeatContextLines(3)) { text.addPara(line); }

        text.addPara("The fastest route to the command centre is blocked by civilians - two hundred station workers "
                + "moving in both directions, panicked. Moving through them in the opposite direction: a defender unit. "
                + "Ten, maybe twenty fighters in civilian-adjacent gear, weapons down, "
                + "falling back toward the command centre at a controlled pace.");

        text.addPara("They're not using the civilians as shields. Not explicitly. But they know you won't push "
                + "through a crowd, and they're buying time to consolidate. Every second here is another second "
                + "the command centre's defenders have to prepare.");

        opts.addOption("Maintain pressure.", OPT_RISKY);
        opts.addOption("Hold and clear.", OPT_SAFE);
        opts.addOption("Disciplined advance.", OPT_SP, SP_COLOR, null);
    }

    // -------------------------------------------------------------------------
    // Beat 4 - Command Centre
    // -------------------------------------------------------------------------

    private void showBeat4() {
        dialog.getVisualPanel().showImageVisual(ASSAULT_IMAGE);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();

        text.addPara("The command centre is the station's original operations core - thick walls, military-grade "
                + "blast doors, defenders with time to turn it into a proper strongpoint.");

        for (String line : getBeatContextLines(4)) { text.addPara(line); }

        text.addPara("Thermal gives you thirty to forty fighters stacked deep, overlapping fire covering both approaches. "
                + "Ex-special forces, First Fleet veterans. People who've had twenty cycles to think about exactly this moment.");

        text.addPara("Two approach vectors - a wide primary corridor to the main entrance, narrower secondary to a side "
                + "access. Both are covered. Your breach charges are sufficient for one door at a time.");

        text.addPara("Your comms specialist flags something: the command centre's internal network is running on old "
                + "mining hardware. Accessible with the right equipment and sixty seconds.");

        opts.addOption("Simultaneous two-point breach.", OPT_RISKY);
        opts.addOption("Single point breach, full force.", OPT_SAFE);
        opts.addOption("Cut their comms first.", OPT_SP, SP_COLOR, null);
    }

    // -------------------------------------------------------------------------
    // Beat 5 - Last Stand
    // -------------------------------------------------------------------------

    private void showBeat5() {
        dialog.getVisualPanel().showImageVisual(ASSAULT_IMAGE);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();

        text.addPara("The inner sanctum's door is sealed but not fortified. Your marines have the room surrounded "
                + "on three sides, no viable exit. Thermals show multiple contacts, tight spacing, weapons still present. "
                + "They haven't fired in four minutes.");

        for (String line : getBeatContextLines(5)) { text.addPara(line); }

        text.addPara("Your comms crackles - old Alliance military encryption, vintage even. "
                + "The voice is older, measured and undisturbed.");

        text.addPara("\"You've gotten this far, Commander.\""
                + " A brief hum of static fills the empty line. "
                + "\"What happens next is your decision...\"");

        text.addPara("A final regiment, Veteran soldiers - the kind that trained everyone you've fought through to get here. "
                + "They seem to be waiting, not for mercy. Asking you to choose with full information. "
                + "Korrin's people are at every checkpoint. This room is the last thing standing between you and a clean handover.");

        opts.addOption("Breach and clear.", OPT_RISKY);
        opts.addOption("Demand a full surrender.", OPT_SAFE);
        opts.addOption("The station commander surrenders formally.", OPT_SP, SP_COLOR, null);
    }

    // -------------------------------------------------------------------------
    // Beat detail screens - confirm/decline for each option across all beats
    // -------------------------------------------------------------------------

    private void showBeatDetail(int beat, String option) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();
        pendingOption = option;

        switch (beat) {
            case 1:
                if (OPT_RISKY.equals(option)) {
                    text.addPara("Powered armor through the front door. The defenders have overlapping fire lanes "
                            + "set up for exactly this and the blast corridor gives them a killing ground before "
                            + "your marines can spread out - you're accepting that. The bet is that once you're "
                            + "through the choke and into the station proper the advantage flips hard and fast. "
                            + "Speed is the logic. The entry point is where it costs you.");
                    opts.addOption("Commit to the main bay assault.", OPT_CONFIRM);
                } else if (OPT_SAFE.equals(option)) {
                    text.addPara("Two marines per cycle, port side. Slow. The prepared positions aren't "
                            + "waiting for you because nobody builds a fire lane for a maintenance lock. "
                            + "The risk is the feed rate - your first teams inside are isolated until "
                            + "enough have cycled through to hold ground. If a defender patrol finds them "
                            + "before that threshold, those marines are holding a corridor alone against "
                            + "people who know every angle of it. You're trading exposure time for a clean entry point.");
                    opts.addOption("Commit to the airlock breach.", OPT_CONFIRM);
                } else if (OPT_SP.equals(option)) {
                    text.addPara("Full fireteam per cycle, starboard side. Korrin's people pulled the watch "
                            + "rotation three weeks ago and the maintenance window is yours. The question is "
                            + "what happens after - you're inside and spreading before anyone is looking, but "
                            + "Korrin's cooperation means Korrin has leverage. That's a problem for after the "
                            + "station is secured.");
                    addStoryPointOption(opts, "Commit to Korrin's lock.", "Korrin's intel");
                }
                break;
            case 2:
                if (OPT_RISKY.equals(option)) {
                    text.addPara("Stack on the junction and push hard. Powered armor is the only reason this is "
                            + "even viable - the barricade angles are going to hurt your lead stack before they clear "
                            + "the first position. You're accepting that cost. The logic is that once you're through "
                            + "and the defenders lose their prepared geometry the advantage shifts fast. Getting "
                            + "through is where it bleeds you.");
                    opts.addOption("Commit to the direct breach.", OPT_CONFIRM);
                } else if (OPT_SAFE.equals(option)) {
                    text.addPara("Pin the junction with sustained fire while a fireteam takes the maintenance corridor "
                            + "and comes out behind the left barricade. Clean tactical logic - the defenders can't hold "
                            + "prepared positions against pressure from two directions simultaneously. The cost is the four "
                            + "minute comms blackout on your flanking team and the time it takes to execute. The rest of the "
                            + "station hears this engagement running long and starts consolidating. The junction is cheaper. "
                            + "What comes after isn't.");
                    opts.addOption("Commit to the suppress and flank.", OPT_CONFIRM);
                } else if (OPT_SP.equals(option)) {
                    text.addPara("One call to Korrin's contact. The junction lights cut simultaneously with a ventilation "
                            + "surge - disorienting, not lethal, but it breaks the defenders' disciplined spacing for approximately "
                            + "thirty seconds. You're betting that thirty seconds is enough. At close range with a stack "
                            + "already moving through the kill zone, in their own disorientation, it probably is. "
                            + "The infrastructure access is Korrin's to arrange "
                            + "and Korrin's to revoke.");
                    addStoryPointOption(opts, "Commit to Korrin's plan.", "Intel: lights out");
                }
                break;
            case 3:
                if (OPT_RISKY.equals(option)) {
                    text.addPara("Push through. Full advance in close formation, direct line through the crowd - the civilians "
                            + "scatter, most get clear, but the chaos is total and you're not stopping to manage it. The "
                            + "retreating unit uses the confusion to break contact and you lose visibility on them for several "
                            + "minutes. Some people get caught in the margins. The command centre gets its defenders back intact "
                            + "and they arrive angry. You're trading clean conduct for time, and you're not getting a good rate.");
                    opts.addOption("Commit to maintain pressure", OPT_CONFIRM);
                } else if (OPT_SAFE.equals(option)) {
                    text.addPara("Stop the advance. Establish a perimeter at both ends and direct civilians out through "
                            + "the side passages. Controlled, methodical, nobody gets hurt who isn't a combatant. The "
                            + "retreating unit reaches the command centre unmolested and has full time to integrate into the "
                            + "defence. You're standing still while the clock runs. That's the cost and it compounds - "
                            + "every minute here is a minute the command centre has to prepare.");
                    opts.addOption("Commit to hold and clear", OPT_CONFIRM);
                } else if (OPT_SP.equals(option)) {
                    text.addPara("Move into the corridor in full kit at a deliberate pace. Not charging. Not hesitating. "
                            + "Weapons visible but pointed down, formation tight and its intentions legible. Civilians in a "
                            + "crisis read confidence and direction faster than words. Your marines have trained for exactly this - "
                            + "the crowd parts ahead of the formation, the retreating unit loses their cover before they reach "
                            + "the next junction, and you're through in under a minute.");
                    addStoryPointOption(opts, "Commit to disciplined advance.", "Disciplined advance");
                }
                break;
            case 4:
                if (OPT_RISKY.equals(option)) {
                    text.addPara("Split your force and hit both doors at the same time. The tactical logic is sound - the defenders "
                            + "have allocated for this and splitting their fire is the point. The problem is splitting your own force thin "
                            + "against the most concentrated and experienced fighters in the station. Each breach team is going in undermanned "
                            + "relative to what's waiting on the other side. Powered armor carries the day eventually but the entry points "
                            + "are going to be brutal for the first marines through each door.");
                    opts.addOption("Commit to the simultaneous two-point breach.", OPT_CONFIRM);
                } else if (OPT_SAFE.equals(option)) {
                    text.addPara("Concentrate everything on the primary entrance. The defenders have allocated thirty to forty "
                            + "percent of their strength to the secondary corridor - don't split to meet them. Hitting the primary "
                            + "door with everything you have means superior numbers against their main position rather than equal "
                            + "numbers against two positions simultaneously. Slower to clear, but your marines aren't going in "
                            + "undermanned anywhere. The secondary defenders realise what's happening and collapse inward - you're "
                            + "fighting them in the corridors afterward rather than at a prepared door. That's the better problem.");
                    opts.addOption("Commit to the single point breach, full force.", OPT_CONFIRM);
                } else if (OPT_SP.equals(option)) {
                    text.addPara("Sixty seconds on the mining network. The assumption is that your comms specialist gets "
                            + "in clean and the command centre goes dark internally - defenders can see each other but can't "
                            + "coordinate between positions. If that holds, the two-point breach becomes viable at full force "
                            + "because the secondary defenders don't know the primary door is blown until your marines are "
                            + "already inside. Thirty seconds of confused defenders in a sealed room is decisive. "
                            + "The sixty seconds before that are where it either works or doesn't.");
                    addStoryPointOption(opts, "Commit to comms blackout.", "Comms cut");
                }
                break;
            case 5:
                if (OPT_RISKY.equals(option)) {
                    text.addPara("End it fast. The veterans fight and some of your marines don't come back out, but the "
                            + "room is clear in under two minutes. You're accepting that arithmetic. Clean in the tactical "
                            + "sense - no negotiations, no complications, no loose ends for August to manage later. The cost "
                            + "is paid here and it's final.");
                    opts.addOption("Commit to breach and clear", OPT_CONFIRM);
                } else if (OPT_SAFE.equals(option)) {
                    text.addPara("Respond on the frequency and give them the terms - weapons down, hands visible, they walk "
                            + "out through your line. Some comply immediately. Three or four won't, and your marines deal with "
                            + "those individually as they come out. It takes twenty minutes and it costs you marines when the "
                            + "holdouts decide to make it expensive. The bet is that most of them come out alive, and the ones "
                            + "who do remember who gave them the option. That has value. Whether it has enough value is what "
                            + "you're deciding.");
                    opts.addOption("Commit to forcing a full surrender", OPT_CONFIRM);
                } else if (OPT_SP.equals(option)) {
                    text.addPara("Respond on the frequency directly. Not terms - a question. Ask who you're speaking to.");
                    text.addPara("If it's the station's commanding officer you can offer something the breach team can't - a formal military surrender. "
                            + "Full honours. Weapons collected not confiscated. Their people treated as prisoners of war rather than criminals. It takes time and "
                            + "it will be witnessed by every marine and every one of Korrin's embedded staff on the station.");
                    text.addPara("The alternative is a sealed room with twelve veterans who stopped firing four minutes ago. Your call.");
                    text.addPara("Nobody says out loud that August will appreciate the optics of a clean handover with no massacres to manage. Nobody needs to.");
                    addStoryPointOption(opts, "Accept a formal surrender", "Formal surrender");
                }
                break;
        }

        opts.addOption("Reconsider.", OPT_BACK);
    }

    // -------------------------------------------------------------------------
    // Story point option helper - shared across all five beats
    // -------------------------------------------------------------------------

    private void addStoryPointOption(OptionPanelAPI opts, String label, String spLabel) {
        opts.addOption(label, OPT_CONFIRM);
        SetStoryOption.StoryOptionParams spParams = new SetStoryOption.StoryOptionParams(
                OPT_CONFIRM, 1, "", null, spLabel);
        SetStoryOption.set(dialog, spParams,
                new SetStoryOption.BaseOptionStoryPointActionDelegate(dialog, spParams) {
                    @Override
                    public void confirm() {
                        advanceBeat(SP_RANGE, BeatChoice.SP);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Beat advancement - deduct marines, move to next beat or outcome
    // -------------------------------------------------------------------------

    private void advanceBeat(int[][] ranges, BeatChoice choice) {
        if (currentBeat <= 4) {
            beatChoices[currentBeat - 1] = choice;
        } else {
            beat5Choice = choice;
        }
        if (choice == BeatChoice.SP) storyPointsSpent++;
        pendingOption = null;
        int beatIdx = currentBeat - 1;
        // SP options represent skill/resource expenditure that bypasses accumulated penalties
        int modifier = (choice == BeatChoice.SP) ? 0 : beatModifier;
        int min = Math.max(0, ranges[beatIdx][0] + modifier);
        int max = Math.max(min, ranges[beatIdx][1] + modifier);
        int lost = min + beatRandom.nextInt(max - min + 1);

        // Never remove more marines than the player has
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        lost = Math.min(lost, cargo.getMarines());
        cargo.removeMarines(lost);
        totalMarinesLost += lost;

        currentBeat++;
        showBeatResolution(beatIdx, choice, lost);
    }

    private void showBeatResolution(int beatIdx, BeatChoice choice, int lost) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(BEAT_RESOLUTION_TEXT[beatIdx][choice.ordinal()]);
        if (lost > 0) {
            AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, lost, text);
        } else {
            text.addPara("Your marines suffered no casualties.", new Color(153, 252, 0));
        }
        if (BEAT_TRANSITION_TEXT[beatIdx] != null) {
            text.addPara(BEAT_TRANSITION_TEXT[beatIdx]);
        }

        opts.addOption("Continue.", OPT_BEAT_ADVANCE);
    }

    // -------------------------------------------------------------------------
    // Raid outcome - summary screen before finalizing
    // -------------------------------------------------------------------------

    private void showRaidOutcome() {
        dialog.getVisualPanel().showImageVisual(ENDGAME_IMAGE);
        dialog.getOptionPanel().clearOptions();
        TextPanelAPI text = dialog.getTextPanel();

        text.addPara("Ring-Port is yours. The Alliance administration is already moving into the upper sections.");

        if (totalMarinesLost < startingMarines * 0.2f) {
            text.addPara("Ring-Port stood for two decades on the strength of people who knew exactly what they were "
                    + "defending. Your marines cleared it in a day. There are worse things to put in an after-action report.");
        } else if (totalMarinesLost > startingMarines * 0.5f) {
            text.addPara("Whatever the First Fleet built here, it was something worth dying for. Over half your marines found "
                    + "that out directly. Ring-Port is cleared. The weight of that doesn't expire when the paperwork does.");
        } else {
            text.addPara("Some of your marines cleared a station that had trained twenty cycles to stop exactly this. "
                    + "They won't be returning to their ships. Ring-Port is held. Both things are true.");
        }

        if (beatChoices[2] == BeatChoice.RISKY) {
            text.addPara("The lower decks are quiet in the way that follows something that can't be walked back. "
                    + "Word spreads on stations before the orders do.");
        }
        if (beat5Choice == BeatChoice.SP) {
            text.addPara("The veterans from the inner sanctum surrendered under the Transit Accords - formally, "
                    + "observed, documented. They're in the secondary bay now. Cycles of armed resistance, and they "
                    + "chose to walk out. That means something.");
        } else if (beat5Choice == BeatChoice.RISKY) {
            text.addPara("The inner sanctum was cleared. No survivors from that room. Years and years of training, "
                    + "and it came down to a few minutes. They chose not to surrender, and your marines obliged them.");
        } else if (beat5Choice == BeatChoice.SAFE) {
            text.addPara("Most of the inner sanctum's veterans walked out. Not all. The ones in custody will outlive "
                    + "this day. The ones who didn't chose that - they had their reasons. The reasons died with them.");
        }

        text.addPara("Comms reports the Office is already moving, but that was always the plan. "
                + "August's people are already here, which marks a job done. August will likely want to speak with you.");

        dialog.getOptionPanel().addOption("Understood.", OPT_CONTINUE);
    }

    // -------------------------------------------------------------------------
    // Raid finalisation - set performance flags, then complete
    // -------------------------------------------------------------------------

    private void finalizeRaid() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(MEM_MARINES_LOST, totalMarinesLost);

        // Performance tiers relative to the 500-marine minimum
        if (totalMarinesLost < (startingMarines * 0.2f)) {
            // Precise: under 20% lost
            mem.set(MEM_RAID_PRECISE, true);
        } else if (totalMarinesLost > (startingMarines * 0.5f)) {
            // Costly: over 50% lost
            mem.set(MEM_RAID_COSTLY, true);
        }
        // Acceptable (100-250 lost): neither flag set - rules.csv default branch

        // Reward: credits scaled by marine losses, SP = half of what was spent
        float lossRatio = (float) totalMarinesLost / startingMarines;
        long creditReward = Math.round(2_000_000L * Math.max(0.5f, 1f - lossRatio));
        int spReward = storyPointsSpent / 2;
        mem.set("$XLII_raidCreditReward", creditReward);
        mem.set("$XLII_raidCreditRewardFormatted", String.format("%,.0f", (double) creditReward));
        mem.set("$XLII_raidSPReward", spReward);

        // Beat choice flags for August's debrief dialog
        if (beatChoices[2] == BeatChoice.RISKY) {
            mem.set(MEM_BEAT3_RISKY, true);
        }
        if (beat5Choice == BeatChoice.SP) {
            mem.set(MEM_BEAT5_SP, true);
        } else if (beat5Choice == BeatChoice.RISKY) {
            mem.set(MEM_BEAT5_RISKY, true);
        } else if (beat5Choice == BeatChoice.SAFE) {
            mem.set(MEM_BEAT5_SAFE, true);
        }

        log.info("Draconis: XLII_RingPortAssault - raid complete, marines lost: " + totalMarinesLost);
        completeVictory();
        dialog.dismiss();
    }

    // -------------------------------------------------------------------------
    // Victory handling
    // -------------------------------------------------------------------------

    private void completeVictory() {
        MarketAPI market = Global.getSector().getEconomy().getMarket(MARKET_ID);
        if (market == null) {
            log.error("Draconis: XLII_RingPortAssault - market not found after victory");
            return;
        }

        transferMarketDirect(market);

        Global.getSector().getMemoryWithoutUpdate().set(MEM_TAKEN, true);
        // Mission is complete - clear the active flag so Elias's dialog transitions
        // from the mission-active state to the post-victory acknowledgment.
        Global.getSector().getMemoryWithoutUpdate().unset("$XLII_blindEyeMissionActive");
        log.info("Draconis: XLII_RingPortAssault - Ring-Port captured, faction set to " + DRACONIS_FACTION);
    }

    private void transferMarketDirect(MarketAPI market) {
        market.setFactionId(DRACONIS_FACTION);

        // BaseSubmarketPlugin.isBlackMarket() returns market.getFaction().isHostileTo(submarket.getFaction()).
        // The open market submarket stores its faction at creation time (Pirates). If left stale, Draconis
        // being hostile to Pirates causes the open market to be misidentified as a black market after capture.
        if (market.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
            market.getSubmarket(Submarkets.SUBMARKET_OPEN)
                  .setFaction(Global.getSector().getFaction(DRACONIS_FACTION));
        }

        SectorEntityToken entity = market.getPrimaryEntity();
        if (entity == null) return;

        entity.setFaction(DRACONIS_FACTION);

        // The OrbitalStation (BATTLESTATION) industry maintains a hidden stationFleet
        // whose faction is set at creation time and doesn't update automatically when
        // the market faction changes. Update it directly so the station is fully captured.
        MemoryAPI entityMem = entity.getMemoryWithoutUpdate();
        Object stationFleetObj = entityMem.get(MemFlags.STATION_FLEET);
        if (stationFleetObj instanceof CampaignFleetAPI) {
            ((CampaignFleetAPI) stationFleetObj).setFaction(DRACONIS_FACTION, true);
            log.info("Draconis: XLII_RingPortAssault - updated STATION_FLEET faction");
        }
        Object baseFleetObj = entityMem.get(MemFlags.STATION_BASE_FLEET);
        if (baseFleetObj instanceof CampaignFleetAPI) {
            ((CampaignFleetAPI) baseFleetObj).setFaction(DRACONIS_FACTION, true);
            log.info("Draconis: XLII_RingPortAssault - updated STATION_BASE_FLEET faction");
        }

        // Clear pirate personnel - admin, roster, and comm directory entries
        market.setAdmin(null);
        for (PersonAPI person : market.getPeopleCopy()) {
            market.removePerson(person);
        }
        market.getCommDirectory().clear();
        log.info("Draconis: XLII_RingPortAssault - cleared pirate personnel and comm directory");

        // Create a Draconis station administrator and add them to the comm directory
        com.fs.starfarer.api.campaign.FactionAPI dracFaction =
                Global.getSector().getFaction(DRACONIS_FACTION);
        PersonAPI admin = OfficerManagerEvent.createAdmin(dracFaction, 0, new Random());
        admin.setFaction(DRACONIS_FACTION);
        admin.setRankId(com.fs.starfarer.api.impl.campaign.ids.Ranks.SPACE_COMMANDER);
        admin.setPostId(com.fs.starfarer.api.impl.campaign.ids.Ranks.POST_BASE_COMMANDER);
        market.setAdmin(admin);
        market.addPerson(admin);
        market.getCommDirectory().addPerson(admin, 0);
        log.info("Draconis: XLII_RingPortAssault - created Draconis admin: " + admin.getNameString());

        // Station commander (size 6 -> SPACE_ADMIRAL rank, military contact tag)
        PersonAPI stationCommander = dracFaction.createRandomPerson(new Random());
        stationCommander.setRankId(com.fs.starfarer.api.impl.campaign.ids.Ranks.SPACE_ADMIRAL);
        stationCommander.setPostId(com.fs.starfarer.api.impl.campaign.ids.Ranks.POST_STATION_COMMANDER);
        stationCommander.addTag(com.fs.starfarer.api.impl.campaign.ids.Tags.CONTACT_MILITARY);
        market.addPerson(stationCommander);
        market.getCommDirectory().addPerson(stationCommander);
        log.info("Draconis: XLII_RingPortAssault - created station commander: " + stationCommander.getNameString());

        // Portmaster (trade contact tag, no rank - matches vanilla pattern)
        PersonAPI portmaster = dracFaction.createRandomPerson(new Random());
        portmaster.setPostId(com.fs.starfarer.api.impl.campaign.ids.Ranks.POST_PORTMASTER);
        portmaster.addTag(com.fs.starfarer.api.impl.campaign.ids.Tags.CONTACT_TRADE);
        market.addPerson(portmaster);
        market.getCommDirectory().addPerson(portmaster);
        log.info("Draconis: XLII_RingPortAssault - created portmaster: " + portmaster.getNameString());

        // Restore Elias Korrin as a visible comm directory contact
        XLII_Characters.updateCharacterPlacements();
    }
}
