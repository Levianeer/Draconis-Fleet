package levianeer.draconis.data.campaign.intel.events.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.ids.FleetTypes;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Custom raid intel for AI Core acquisition raids
 * Uses Shadow Fleet (FORTYSECOND) faction via params
 */
public class DraconisAICoreRaidIntel extends GenericRaidFGI {

    private final MarketAPI target;
    private final IntervalUtil interval = new IntervalUtil(0.1f, 0.3f);

    // Custom win condition tracking
    private boolean payloadActionStarted = false;
    private boolean customWinConditionMet = false;
    private boolean coresAlreadyStolen = false;
    private boolean successIntelSent = false;
    private float timeAtTarget = 0f;
    private static final float MIN_TIME_AT_TARGET = 2f;
    private static final float SUCCESS_TRIGGER_DELAY = 12f;  // day delay before triggering success
    private float successTriggerTimer = 0f;
    private boolean successConditionsMetWaitingForDelay = false;

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
     * Override failure check to prevent premature failure
     * Raid should not fail while success conditions are being met or are in progress
     */
    @Override
    public boolean isFailed() {
        // If we're actively engaging or waiting for success delay, prevent failure
        if (successConditionsMetWaitingForDelay) {
            Global.getLogger(this.getClass()).info(
                "Raid failure check blocked - success conditions met, waiting for delay to complete"
            );
            return false;
        }

        // If we already succeeded, definitely not failed
        if (customWinConditionMet) {
            return false;
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

        // Track custom win condition
        if (!customWinConditionMet) {
            // Check if we're at the payload action
            if (isCurrent(PAYLOAD_ACTION)) {
                if (!payloadActionStarted) {
                    payloadActionStarted = true;
                    timeAtTarget = 0f;
                    Global.getLogger(this.getClass()).info("AI Core raid PAYLOAD_ACTION started - beginning custom win condition tracking");
                }

                // Accumulate time at target
                timeAtTarget += days;

                // Check custom win condition: fleet reached target and spent minimum time engaging
                if (timeAtTarget >= MIN_TIME_AT_TARGET) {
                    Global.getLogger(this.getClass()).info(
                        "=== CHECKING ENGAGEMENT CONDITIONS (after " + String.format("%.2f", timeAtTarget) + " days) ==="
                    );

                    // Check if at least one fleet is actively engaging the target
                    boolean hasEngagedFleet = false;
                    int fleetsChecked = 0;
                    int emptyFleets = 0;
                    int wrongSystemFleets = 0;
                    int notInCombatFleets = 0;
                    int tooFarFleets = 0;

                    for (CampaignFleetAPI fleet : getFleets()) {
                        fleetsChecked++;

                        if (fleet.isEmpty()) {
                            emptyFleets++;
                            Global.getLogger(this.getClass()).info(
                                "  Fleet " + fleetsChecked + ": " + fleet.getName() + " - EMPTY (destroyed)"
                            );
                            continue;
                        }

                        if (fleet.getContainingLocation() != target.getContainingLocation()) {
                            wrongSystemFleets++;
                            Global.getLogger(this.getClass()).info(
                                "  Fleet " + fleetsChecked + ": " + fleet.getName() + " - WRONG SYSTEM"
                            );
                            continue;
                        }

                        // Check if fleet is in combat AND near the target market
                        boolean inCombat = fleet.getBattle() != null;
                        float distanceToTarget = Misc.getDistance(fleet.getLocation(), target.getPrimaryEntity().getLocation());
                        boolean veryCloseToTarget = distanceToTarget < 200f; // Very close - actually at the target

                        Global.getLogger(this.getClass()).info(
                            "  Fleet " + fleetsChecked + ": " + fleet.getName() +
                            " | Combat: " + inCombat +
                            " | Distance: " + String.format("%.0f", distanceToTarget) + " units" +
                            " | Close enough: " + veryCloseToTarget
                        );

                        // MUST be BOTH in combat AND very close to target (and time requirement already met)
                        // This ensures fleet is actually engaging the target, not just fighting nearby
                        if (inCombat && veryCloseToTarget) {
                            hasEngagedFleet = true;
                            Global.getLogger(this.getClass()).info(
                                "  >>> ENGAGEMENT CONDITIONS MET! <<<"
                            );
                            break;
                        } else {
                            if (!inCombat) notInCombatFleets++;
                            if (!veryCloseToTarget) tooFarFleets++;
                        }
                    }

                    Global.getLogger(this.getClass()).info(
                        "Engagement check summary: " + fleetsChecked + " total fleets | " +
                        emptyFleets + " empty | " + wrongSystemFleets + " wrong system | " +
                        notInCombatFleets + " not in combat | " + tooFarFleets + " too far | " +
                        "Result: " + (hasEngagedFleet ? "SUCCESS" : "FAILED")
                    );

                    if (hasEngagedFleet) {
                        // Mark that success conditions are met, but start delay timer
                        if (!successConditionsMetWaitingForDelay) {
                            successConditionsMetWaitingForDelay = true;
                            successTriggerTimer = 0f;
                            Global.getLogger(this.getClass()).info(
                                "=== AI CORE RAID SUCCESS CONDITIONS MET ==="
                            );
                            Global.getLogger(this.getClass()).info(
                                "Time at target: " + String.format("%.2f", timeAtTarget) + " days"
                            );
                            Global.getLogger(this.getClass()).info(
                                "Active fleets at target: confirmed"
                            );
                            Global.getLogger(this.getClass()).info(
                                "Starting " + SUCCESS_TRIGGER_DELAY + " day delay before triggering success..."
                            );
                        }
                    }
                }
            }
        }

        // Handle success trigger delay
        if (successConditionsMetWaitingForDelay && !customWinConditionMet) {
            // Check if fleet is still viable - cancel success if all fleets are completely destroyed
            // Don't check location - fleet can leave after accomplishing the mission
            boolean hasViableFleet = false;
            for (CampaignFleetAPI fleet : getFleets()) {
                // Fleet is viable if it's not empty (still has ships)
                if (!fleet.isEmpty()) {
                    hasViableFleet = true;
                    break;
                }
            }

            if (!hasViableFleet) {
                // All fleets completely destroyed - cancel the success countdown
                successConditionsMetWaitingForDelay = false;
                successTriggerTimer = 0f;
                Global.getLogger(this.getClass()).info(
                    "=== AI CORE RAID FLEET DESTROYED ==="
                );
                Global.getLogger(this.getClass()).info(
                    "All raid fleets completely destroyed during success delay - cancelling success, raid will fail"
                );
                // Let the raid fail naturally through parent's failure logic
                return;
            }

            successTriggerTimer += days;

            if (successTriggerTimer >= SUCCESS_TRIGGER_DELAY) {
                customWinConditionMet = true;
                Global.getLogger(this.getClass()).info(
                    "=== AI CORE RAID SUCCESS DELAY COMPLETED ==="
                );
                Global.getLogger(this.getClass()).info(
                    "Raid will be considered successful regardless of bombardment mechanics"
                );

                // STEAL AI CORES NOW - after delay is complete
                if (!coresAlreadyStolen && target != null) {
                    coresAlreadyStolen = true;
                    Global.getLogger(this.getClass()).info(
                        "Success delay complete - proceeding with AI core theft from " + target.getName()
                    );

                    boolean isPlayerMarket = target.isPlayerOwned();
                    levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener.checkAndStealAICores(
                        target, isPlayerMarket, "ai_core_raid"
                    );

                    // Clear high-value target flags after theft
                    levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner.clearTargetAfterRaid(target);

                    Global.getLogger(this.getClass()).info(
                        "AI core theft completed and target flags cleared"
                    );
                }

                // Send intel update notification when raid succeeds (only once)
                if (!successIntelSent) {
                    successIntelSent = true;
                    Global.getLogger(this.getClass()).info(
                        "Sending intel update notification for successful AI core raid"
                    );
                    sendUpdateIfPlayerHasIntel(new Object(), false);
                }
            } else {
                Global.getLogger(this.getClass()).info(
                    "Success delay in progress: " + String.format("%.2f", successTriggerTimer) + "/" + SUCCESS_TRIGGER_DELAY + " days"
                );
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

        // Ensure friendly to Draconis and Shadow Fleet
        m.triggerMakeNonHostileToFaction(DRACONIS);
        m.triggerMakeNonHostileToFaction(FORTYSECOND);

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