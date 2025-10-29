package levianeer.draconis.data.campaign.intel.events.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.ids.FleetTypes;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Custom raid intel for AI Core acquisition raids
 * Uses Draconis faction via params
 */
public class DraconisAICoreRaidIntel extends GenericRaidFGI {

    private final MarketAPI target;
    private final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    // Custom win condition tracking - Spec Ops distraction timer
    private boolean payloadActionStarted = false;
    private boolean customWinConditionMet = false;
    private boolean coresAlreadyStolen = false;
    private float timeInSystem = 0f;

    // Spec ops mission duration
    private static final float BASE_MISSION_TIME = 30f;
    private static final float CLOSE_MISSION_TIME = 14f;
    private static final float PROXIMITY_RANGE = 2000f;

    // Progress notifications (only shown when player is in same system)
    private boolean notification25Sent = false;
    private boolean notification50Sent = false;
    private boolean notification75Sent = false;
    private boolean notificationCompleteSent = false;

    public DraconisAICoreRaidIntel(GenericRaidParams params, MarketAPI target) {
        super(params);
        this.target = target;
    }

    @Override
    public String getBaseName() {
        return "Shadow Fleet AI Core Raid";
    }

    public MarketAPI getTarget() {
        return target;
    }

    /**
     * Custom success check for AI core raids
     * Uses our own win condition instead of relying on base game bombardment mechanics
     */
    @Override
    public boolean isSucceeded() {
        // Use custom win condition if it's been met
        if (customWinConditionMet) {
            return true;
        }

        // Fallback: if parent reports success, accept it
        if (super.isSucceeded()) {
            customWinConditionMet = true;
            Global.getLogger(this.getClass()).info("AI Core raid succeeded via parent class success check");
            return true;
        }

        return false;
    }

    /**
     * Override failure check
     * Raid only fails if ALL fleets are destroyed while in-system before mission completes
     */
    @Override
    public boolean isFailed() {
        // If we already succeeded, definitely not failed
        if (customWinConditionMet) {
            return false;
        }

        // During payload action (in-system), check if all fleets destroyed
        if (payloadActionStarted && isCurrent(PAYLOAD_ACTION)) {
            boolean allDestroyed = true;
            for (CampaignFleetAPI fleet : getFleets()) {
                if (!fleet.isEmpty()) {
                    allDestroyed = false;
                    break;
                }
            }

            if (allDestroyed) {
                Global.getLogger(this.getClass()).info(
                    "=== AI CORE RAID FAILED ==="
                );
                Global.getLogger(this.getClass()).info(
                    "All raid fleets destroyed in-system - mission failed"
                );
                return true;
            }
        }

        // Otherwise, use parent's failure logic
        return super.isFailed();
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();

        // Handle cleanup for standalone raids (those without a listener)
        // If there's a listener, it will handle the cleanup
        if (getListener() == null) {
            Global.getLogger(this.getClass()).info("Standalone AI Core Raid ending");

            boolean success = isSucceeded();

            // If raid succeeded but cores weren't already stolen (fallback for edge cases)
            if (success && target != null && !coresAlreadyStolen) {
                Global.getLogger(this.getClass()).info("Standalone raid succeeded - fallback AI core theft from " + target.getName());

                coresAlreadyStolen = true;
                boolean isPlayerMarket = target.isPlayerOwned();
                levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener.checkAndStealAICores(
                    target, isPlayerMarket, "ai_core_raid"
                );

                // Clear high-value target flags after successful raid
                levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner.clearTargetAfterRaid(target);
            } else if (success && coresAlreadyStolen) {
                Global.getLogger(this.getClass()).info("Standalone raid succeeded - cores already stolen during payload action");
            } else if (!success) {
                Global.getLogger(this.getClass()).info("Standalone raid failed - no AI cores will be stolen");
            }

            // Decrement active raid count
            DraconisAICoreRaidManager.decrementActiveRaidCount();

            // Start cooldown based on success/failure
            DraconisAICoreRaidManager.startCooldown(success);

            Global.getLogger(this.getClass()).info("Standalone raid " + (success ? "succeeded" : "failed") + " - cooldown started");
        }
    }

    public static String RAIDER_FLEET = "$draconisRaider";

    /**
     * Advance method to handle:
     * 1. Custom win condition tracking
     * 2. Delayed hostility and Go Dark deactivation
     * Makes fleets hostile only when they arrive at the target (during PAYLOAD_ACTION)
     * Deactivates Go Dark when beginning the attack
     */
    @Override
    public void advance(float amount) {
        super.advance(amount);

        float days = Misc.getDays(amount);
        interval.advance(days);

        // Track spec ops mission timer - simple and reliable!
        if (!customWinConditionMet && isCurrent(PAYLOAD_ACTION)) {
            if (!payloadActionStarted) {
                payloadActionStarted = true;
                timeInSystem = 0f;
                Global.getLogger(this.getClass()).info(
                    "=== SPEC OPS MISSION STARTED ==="
                );
                Global.getLogger(this.getClass()).info(
                    "Fleet providing distraction while spec ops infiltrate " + target.getName()
                );
                sendPlayerNotification("Draconis spec ops team infiltrating " + target.getName());
            }

            // Accumulate time in system
            timeInSystem += days;

            // Check proximity to target for time bonus
            boolean isCloseToTarget = false;
            for (CampaignFleetAPI fleet : getFleets()) {
                if (fleet.isEmpty()) continue;
                if (fleet.getContainingLocation() != target.getContainingLocation()) continue;

                float distance = Misc.getDistance(fleet.getLocation(), target.getPrimaryEntity().getLocation());
                if (distance < PROXIMITY_RANGE) {
                    isCloseToTarget = true;
                    break;
                }
            }

            // Determine required time (7 days if close, 14 days baseline)
            float requiredTime = isCloseToTarget ? CLOSE_MISSION_TIME : BASE_MISSION_TIME;

            // Calculate progress percentage
            float progress = (timeInSystem / requiredTime) * 100f;

            // Send progress notifications (only when player in same system)
            if (progress >= 25f && !notification25Sent) {
                notification25Sent = true;
                sendPlayerNotification("Spec ops progress: 25% complete");
            }
            if (progress >= 50f && !notification50Sent) {
                notification50Sent = true;
                sendPlayerNotification("Spec ops progress: 50% complete - data extraction underway");
            }
            if (progress >= 75f && !notification75Sent) {
                notification75Sent = true;
                sendPlayerNotification("Spec ops progress: 75% complete - preparing extraction");
            }

            // Check if mission time complete
            if (timeInSystem >= requiredTime) {
                customWinConditionMet = true;

                Global.getLogger(this.getClass()).info(
                    "=== SPEC OPS MISSION COMPLETE ==="
                );
                Global.getLogger(this.getClass()).info(
                    "Time in system: " + String.format("%.2f", timeInSystem) + " days"
                );
                Global.getLogger(this.getClass()).info(
                    "Proximity bonus active: " + isCloseToTarget
                );
                Global.getLogger(this.getClass()).info(
                    "AI cores successfully acquired by spec ops team!"
                );

                // Steal AI cores
                if (!coresAlreadyStolen && target != null) {
                    coresAlreadyStolen = true;

                    boolean isPlayerMarket = target.isPlayerOwned();
                    levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener.checkAndStealAICores(
                        target, isPlayerMarket, "ai_core_raid"
                    );

                    // Clear high-value target flags
                    levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner.clearTargetAfterRaid(target);

                    Global.getLogger(this.getClass()).info(
                        "AI core theft completed and target flags cleared"
                    );
                }

                // Notify player
                if (!notificationCompleteSent) {
                    notificationCompleteSent = true;
                    sendPlayerNotification("Spec ops mission complete - AI cores secured!");
                    sendUpdateIfPlayerHasIntel(new Object(), false);
                }
            } else {
                // Log progress periodically
                if (timeInSystem % 2f < days) { // Every ~2 days
                    Global.getLogger(this.getClass()).info(
                        "Spec ops progress: " + String.format("%.1f", progress) + "% " +
                        "(" + String.format("%.1f", timeInSystem) + "/" + String.format("%.1f", requiredTime) + " days)" +
                        (isCloseToTarget ? " [PROXIMITY BONUS ACTIVE]" : "")
                    );
                }
            }
        }

        // Manage Go Dark and hostility based on current action
        if (interval.intervalElapsed()) {
            // During transit, ensure Go Dark stays active
            if (isCurrent(PREPARE_ACTION) || isCurrent(TRAVEL_ACTION)) {
                for (CampaignFleetAPI fleet : getFleets()) {
                    if (fleet.getAbility(Abilities.GO_DARK) != null &&
                        !fleet.getAbility(Abilities.GO_DARK).isActive()) {
                        fleet.getAbility(Abilities.GO_DARK).activate();
                        Global.getLogger(this.getClass()).info("Re-activated Go Dark on fleet during transit: " + fleet.getName());
                    }
                }
            }

            // When arriving at target, deactivate Go Dark and become hostile
            if (isCurrent(PAYLOAD_ACTION)) {
                // Deactivate Go Dark and make hostile when beginning the attack
                String reason = "DraconisAICoreRaid";
                for (CampaignFleetAPI fleet : getFleets()) {
                    // Deactivate Go Dark - no longer need stealth, time to attack
                    if (fleet.getAbility(Abilities.GO_DARK) != null &&
                        fleet.getAbility(Abilities.GO_DARK).isActive()) {
                        fleet.getAbility(Abilities.GO_DARK).deactivate();
                        Global.getLogger(this.getClass()).info("Deactivated Go Dark on fleet for attack: " + fleet.getName());
                    }

                    // Set the make hostile flag with a 1-day duration (refreshes each check)
                    Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(),
                            MemFlags.MEMORY_KEY_MAKE_HOSTILE,
                            reason, true, 1f);

                    // Also make hostile to the specific target faction
                    if (target != null) {
                        Misc.makeHostileToFaction(fleet, target.getFactionId(), 1f);
                    }
                }
            }
        }
    }

    /**
     * Send notification to player only if they're in the same system as the target
     */
    private void sendPlayerNotification(String message) {
        if (Global.getSector().getPlayerFleet() == null) return;
        if (target == null) return;

        // Only notify if player is in same system as target
        if (Global.getSector().getPlayerFleet().getContainingLocation() == target.getContainingLocation()) {
            Global.getSector().getCampaignUI().addMessage(
                message,
                Misc.getTextColor()
            );
        }
    }

    /**
     * Override bullet points to explain the spec ops win condition
     */
    @Override
    protected void addNonUpdateBulletPoints(com.fs.starfarer.api.ui.TooltipMakerAPI info,
                                           java.awt.Color tc, Object param,
                                           ListInfoMode mode, float initPad) {
        // Call parent to add standard info (ETA, targeting, etc.)
        super.addNonUpdateBulletPoints(info, tc, param, mode, initPad);

        // Add spec ops mission progress if raid is in progress
        if (payloadActionStarted && !customWinConditionMet && isCurrent(PAYLOAD_ACTION)) {
            java.awt.Color h = Misc.getHighlightColor();

            // Check proximity bonus status
            boolean isCloseToTarget = false;
            for (CampaignFleetAPI fleet : getFleets()) {
                if (fleet.isEmpty()) continue;
                if (fleet.getContainingLocation() != target.getContainingLocation()) continue;

                float distance = Misc.getDistance(fleet.getLocation(), target.getPrimaryEntity().getLocation());
                if (distance < PROXIMITY_RANGE) {
                    isCloseToTarget = true;
                    break;
                }
            }

            float requiredTime = isCloseToTarget ? CLOSE_MISSION_TIME : BASE_MISSION_TIME;
            float progress = (timeInSystem / requiredTime) * 100f;
            int daysRemaining = (int) Math.ceil(requiredTime - timeInSystem);

            // Main progress bullet
            String progressText = "Spec ops infiltration: %s complete";
            info.addPara(progressText, 3f, tc, h,
                        String.format("%.0f", progress) + "%");

            // Time remaining
            String timeText = "Estimated %s until extraction";
            String daysText = daysRemaining + " " + (daysRemaining == 1 ? "day" : "days");
            info.addPara(timeText, 0f, tc, h, daysText);

            // Proximity bonus status
            if (isCloseToTarget) {
                info.addPara("Fleet proximity bonus: Active", 0f, tc,
                           Misc.getPositiveHighlightColor(), "Active");
            } else {
                String proximityText = "Fleet within 2000 units of target reduces mission time to 14 days";
                info.addPara(proximityText, 0f, Misc.getGrayColor(), h, "2000 units", "14 days");
            }
        }
    }

    /**
     * Configure fleet during creation to have Shadow Fleet characteristics
     * Sets fleet type and behavior flags
     * Uses delayed hostility - fleets become hostile on arrival, not during transit
     */
    @Override
    protected void configureFleet(int size, FleetCreatorMission m) {
        super.configureFleet(size, m);

        // Set fleet type for Shadow Fleet identification
        m.triggerSetFleetType(FleetTypes.SHADOW_FLEET);

        // No faction name in fleet name (covert ops)
        m.triggerFleetSetNoFactionInName();

        // NOTE: Not making hostile during creation - delayed until arrival (like Tri-Tachyon)
        // Hostility is set in the raid action when they reach the target

        // Ensure friendly to Draconis
        m.triggerMakeNonHostileToFaction(DRACONIS);

        // No reputation impact from combat (covert ops)
        m.triggerMakeNoRepImpact();

        // Allow long pursuit (committed raiders)
        m.triggerFleetAllowLongPursuit();

        // Mark as raider fleet for AI behavior
        m.triggerSetFleetFlag(RAIDER_FLEET);

        // Make fleet faster for hit-and-run tactics
        m.triggerFleetMakeFaster(true, 0, true);
    }

    /**
     * Configure spawned fleet to ensure transponders remain off and Go Dark is active
     * Sets smuggler flag so Go Dark AI keeps the ability active during transit
     */
    @Override
    protected void configureFleet(int size, CampaignFleetAPI fleet) {
        super.configureFleet(size, fleet);

        // Ensure transponders are off and Go Dark is active for stealth approach
        if (fleet != null) {
            fleet.setTransponderOn(false);

            // Mark as smuggler so Go Dark AI keeps ability active when near hostile markets
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SMUGGLER, true);

            // Add Go Dark ability if not present
            if (fleet.getAbility(Abilities.GO_DARK) == null) {
                fleet.addAbility(Abilities.GO_DARK);
                Global.getLogger(this.getClass()).info("Added Go Dark ability to fleet: " + fleet.getName());
            }

            // Activate Go Dark immediately for stealth transit
            if (fleet.getAbility(Abilities.GO_DARK) != null) {
                fleet.getAbility(Abilities.GO_DARK).activate();
                Global.getLogger(this.getClass()).info("Activated Go Dark on fleet: " + fleet.getName());
            } else {
                Global.getLogger(this.getClass()).warn("Failed to get Go Dark ability for fleet: " + fleet.getName());
            }
        }
    }
}