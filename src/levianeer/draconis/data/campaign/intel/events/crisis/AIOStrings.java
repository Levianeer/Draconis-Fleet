package levianeer.draconis.data.campaign.intel.events.crisis;

/**
 * All player-facing strings for the AIO Colony Crisis system, ordered
 * chronologically by when the player encounters them during a playthrough.
 */
public class AIOStrings {
    private AIOStrings() {}

    // =========================================================================
    // Intel Names
    // =========================================================================

    public static final String INTEL_NAME_AIO_ASSESSMENT         = "Draconis Black Operations - ";
    public static final String INTEL_NAME_AIO_PHASE_1            = "Phase I";
    public static final String INTEL_NAME_AIO_PHASE_2            = "Phase II";
    public static final String INTEL_NAME_AIO_PHASE_3            = "Phase III";
    public static final String INTEL_NAME_DEAL                   = "Alliance Intelligence Office Arrangement";
    public static final String INTEL_NAME_DISRUPTION_SUFFIX      = " - Disrupted";
    public static final String INTEL_EXPEDITION_BASE_NAME        = "Draconis Alliance Strike";
    public static final String DEAL_MONTHLY_REPORT_NODE_NAME     = "Alliance Intelligence Office - Arrangement";

    // =========================================================================
    // Phase: WATCHING (progress 0–34)
    // =========================================================================

    public static final String WATCHING_PARA1 =
            "The Alliance Intelligence Office is monitoring your operations. "
            + "Draconis analysts are tracking the threat your colonies represent "
            + "to their strategic interests.";

    /** %s = "minor supply chain disruptions" (highlighted). */
    public static final String WATCHING_PARA2_FMT       = "You are experiencing %s. These will worsen as the AIO's Counterintelligence advances.";
    public static final String WATCHING_PARA2_HIGHLIGHT = "minor supply chain disruptions";

    /** Four %s args: "accessibility", penalty%, "stability", penaltyPts, periodic disruptions. */
    public static final String WATCHING_PARA3_FMT =
            "At the crisis's height %s will be reduced by %s%% and %s will be reduced by %s points. "
            + "High risk industries will see %s by AIO operatives.";

    // =========================================================================
    // Tooltip Titles (stage transition popup headers)
    // =========================================================================

    public static final String TOOLTIP_TITLE_ACTIVE_MEASURES = "Active Countermeasures";
    public static final String TOOLTIP_TITLE_LIABILITY        = "Liability Designation";
    public static final String TOOLTIP_TITLE_INVASION         = "Punitive Expedition Authorised";

    // =========================================================================
    // Phase: ACTIVE_MEASURES (progress 35–64)
    // =========================================================================

    /** %s = "Shadow Fleets" (highlighted). */
    public static final String ACTIVE_PARA1_FMT       = "The AIO has escalated to active countermeasures. Unmarked %s have been deployed to your systems.";
    public static final String ACTIVE_PARA1_HIGHLIGHT = "Shadow Fleets";

    /** %s = "advances" (highlighted). */
    public static final String ACTIVE_PARA2_FMT =
            "The AIO updates its threat assessment whenever its fleets are repelled - "
            + "engaging them %s the crisis slightly. Not engaging means the shadow fleets "
            + "achieve their harassment objectives unchallenged.";
    public static final String ACTIVE_PARA2_HIGHLIGHT = "advances";

    // =========================================================================
    // Phase: LIABILITY (progress 65–99)
    // =========================================================================

    /** %s = "liability" (highlighted). */
    public static final String LIABILITY_PARA1_FMT       = "The AIO has classified you as a %s. Fleet pressure is intensifying and your colonies face severe operational disruption.";
    public static final String LIABILITY_PARA1_HIGHLIGHT = "liability";

    /** %s = "full punitive expedition" (highlighted). */
    public static final String LIABILITY_PARA2_FMT       = "Further escalation will authorize a %s against your colonies.";
    public static final String LIABILITY_PARA2_HIGHLIGHT = "full punitive expedition";

    // =========================================================================
    // Phase: INVASION (progress 100)
    // =========================================================================

    /** %s = "XLII Battlegroup" (highlighted). */
    public static final String INVASION_PARA1_FMT       = "The Alliance Intelligence Office has authorized a full punitive strike. The %s has been dispatched.";
    public static final String INVASION_PARA1_HIGHLIGHT = "XLII Battlegroup";

    /** Three %s args: "final", "end", "heavy armaments" (all highlighted). Used when defeats >= 2. */
    public static final String INVASION_FINAL_FMT =
            "This is the %s expedition. Defeating the strike force will %s the crisis permanently "
            + "and improve your %s export operations.";

    /** Two %s args: ordinal ("first"/"second"), reset floor value. Used when defeats < 2. */
    public static final String INVASION_NONFINAL_FMT =
            "This is the %s of three expeditions. Defeating the XLII Battlegroup will reset "
            + "the crisis to %s and reduce the escalation rate - but not end the crisis. "
            + "The heavy armaments export bonus is only granted after all three are defeated.";

    /** %s = remaining-expeditions string (highlighted). */
    public static final String INVASION_REMAINING_FMT = "%s before the Office stands down.";

    /** Shared between addStageDescriptionText and addBulletPoints defeat notifications. */
    public static final String INVASION_REMAINING_TWO = "Two more expeditions remain";
    public static final String INVASION_REMAINING_ONE = "One more expedition remains";

    // =========================================================================
    // Bullet Points: Stage Transitions (update mode)
    // =========================================================================

    /** %s = "Shadow Fleets" (highlighted). */
    public static final String BULLET_ACTIVE_DEPLOYED_FMT = "%s deployed to your systems";

    /** Shared highlight value used in both update-mode and standing-state bullets. */
    public static final String BULLET_ACTIVE_HIGHLIGHT    = "Shadow Fleets";

    /** %s = "LIABILITY" (highlighted). Shared between update-mode and standing-state bullets. */
    public static final String BULLET_LIABILITY_FMT       = "AIO assessment: %s";
    public static final String BULLET_LIABILITY_HIGHLIGHT = "LIABILITY";

    /** Two %s args: "XLII Battlegroup" (highlighted), expedition number string. Shared with standing-state. */
    public static final String BULLET_INVASION_FMT        = "%s inbound (expedition %s of 3)";
    public static final String BULLET_INVASION_HIGHLIGHT  = "XLII Battlegroup";

    // =========================================================================
    // Bullet Points: Defeat Notifications
    // =========================================================================

    public static final String DEFEAT1_PARA1 =
            "XLII Battlegroup has withdrawn. Alliance Intelligence is reassessing operational parameters.";

    /** Four %s args: "reset", floor value, rate-reduction word, remaining-expeditions string. Used by defeats 1 and 2. */
    public static final String DEFEAT_RESET_FMT     = "Assessment %s to %s. Escalation rate %s. %s.";
    public static final String DEFEAT_RESET_KEYWORD = "reset";
    public static final String DEFEAT1_RATE_REDUCED = "reduced";

    public static final String DEFEAT2_PARA1 =
            "XLII Battlegroup has withdrawn. Expeditionary doctrine under review. "
            + "Pressure continues through alternative channels.";
    public static final String DEFEAT2_RATE_REDUCED = "greatly reduced";

    public static final String DEFEAT3_PARA1 =
            "XLII Battlegroup has withdrawn. No further action authorised. "
            + "The Office has determined that continued engagement exceeds acceptable operational cost.";

    /** Two %s args: "over", "granted" (both highlighted). */
    public static final String DEFEAT3_PARA2_FMT = "The crisis is %s. Heavy armaments export bonus %s.";
    public static final String DEFEAT3_OVER      = "over";
    public static final String DEFEAT3_GRANTED   = "granted";

    // =========================================================================
    // Bullet Points: Standing State (non-update mode)
    // =========================================================================

    /** %s = "paused" (highlighted). */
    public static final String BULLET_PAUSED_FMT        = "AIO Counterintelligence %s (commissioned)";
    public static final String BULLET_PAUSED_HIGHLIGHT  = "paused";

    /** %s = "suppressed" (highlighted). */
    public static final String BULLET_SUPPRESSED_FMT        = "AIO Counterintelligence %s (payment active)";
    public static final String BULLET_SUPPRESSED_HIGHLIGHT  = "suppressed";

    public static final String BULLET_WATCHING_PASSIVE  = "The Draconis Defence Alliance has marked you as a threat";

    /** %s = "Shadow Fleets" (highlighted). */
    public static final String BULLET_ACTIVE_FLEETS_FMT = "%s active in your systems";

    // =========================================================================
    // Deal Intel Panel (DraconisAIOPaymentDealIntel)
    // =========================================================================

    /** %s = "DDA Intelligence Office" (highlighted). */
    public static final String DEAL_DESC_PARA1_FMT        = "An arrangement with a %s contact is in effect. Monthly payments are being made to keep the AIO's counterintelligence suppressed.";
    public static final String DEAL_DESC_PARA1_HIGHLIGHT  = "DDA Intelligence Office";

    /** %s = formatted cost string (highlighted). */
    public static final String DEAL_DESC_PARA2_FMT        = "Current monthly cost: %s (scales with AI core deployment across your colonies).";

    /** %s = "suppressed" (highlighted). */
    public static final String DEAL_DESC_PARA3_FMT        = "The AIO monthly tick is %s while this arrangement is active.";
    public static final String DEAL_DESC_PARA3_HIGHLIGHT  = "suppressed";

    /** %s = "colony" (highlighted). */
    public static final String DEAL_DESC_PARA4_FMT        = "To terminate this arrangement, return to a %s bar.";
    public static final String DEAL_DESC_PARA4_HIGHLIGHT  = "colony";

    public static final String DEAL_TOOLTIP_PARA1 =
            "A payment arrangement with a DDA Intelligence Office contact. "
            + "The AIO suppresses its counterintelligence effortds in exchange for a monthly fee.";

    /** Three %s args: base cost, 2× base, 3× base (all highlighted). */
    public static final String DEAL_TOOLTIP_PARA2_FMT     = "Cost scales with AI core deployment across your colonies: gamma cores at %s each, beta at %s, alpha at %s.";

    /** %s = core breakdown string. Use String.format at call site. */
    public static final String DEAL_TOOLTIP_PARA3_FMT     = "Currently deployed: %s.";

    public static final String DEAL_TOOLTIP_PARA4           = "No AI cores currently deployed - minimum flat rate applies.";
    public static final String DEAL_TOOLTIP_PARA4_HIGHLIGHT = "minimum flat rate";

    // =========================================================================
    // Disruption Intel (DraconisAIODisruptionIntel)
    // =========================================================================

    /** One %s arg: market name (highlighted). */
    public static final String DISRUPTION_BULLET_FMT     = "Target: %s";

    /** Three %s args: industry name, market name, disruption days (highlighted). */
    public static final String DISRUPTION_DESC_PARA1_FMT =
            "Alliance Intelligence Office operatives have sabotaged %s at %s, "
            + "disrupting operations for approximately %s days.";

    public static final String DISRUPTION_DESC_PARA2 =
            "This action is part of an ongoing AIO counterintelligence campaign targeting your "
            + "strategic infrastructure. The disruption will resolve on its own - but the "
            + "greater counterintelligence effort continues to advance regardless.";

    // =========================================================================
    // Factor Descriptors
    // =========================================================================

    public static final String FACTOR_AI_CORE_DESC  = "AI core usage";
    public static final String FACTOR_BASELINE_DESC = "Baseline scrutiny";

    /** Used by DraconisFleetHostileActivityFactor.getDesc() and getNameForThreatList(). */
    public static final String FACTOR_DDA_NAME      = "Draconis Alliance";

    // =========================================================================
    // Factor Tooltips: AI Core Factor
    // =========================================================================

    public static final String FACTOR_AI_CORE_TIP_PARA1 =
            "AI cores slotted into your industries draw significant AIO attention. "
            + "The DDA views large-scale AI core deployment as a strategic threat.";

    /** %s = "+1" (highlighted). */
    public static final String FACTOR_AI_CORE_TIP_PARA2 =
            "Even with no AI cores deployed, the AIO maintains a minimum scrutiny level of %s per month.";

    // =========================================================================
    // Factor Tooltips: Relations Multiplier Factor
    // =========================================================================

    public static final String FACTOR_RELATIONS_NAME_DEFAULT  = "DDA Relations";

    /** Table row labels for the relations threshold tooltip (negative to positive). */
    public static final String FACTOR_RELATIONS_ROW_VENGEFUL  = "Vengeful";
    public static final String FACTOR_RELATIONS_ROW_HOSTILE   = "Hostile";
    public static final String FACTOR_RELATIONS_ROW_SUSPICIOUS = "Suspicious";
    public static final String FACTOR_RELATIONS_ROW_NEUTRAL   = "Neutral";
    public static final String FACTOR_RELATIONS_ROW_FAVORABLE = "Favorable";
    public static final String FACTOR_RELATIONS_ROW_FRIENDLY  = "Friendly";
    public static final String FACTOR_RELATIONS_ROW_ALLIED    = "Allied";

    public static final String FACTOR_RELATIONS_TIP_PARA1 =
            "Your standing with the Draconis Defence Alliance affects the pace of AIO scrutiny. "
            + "Positive relations slow monthly advancement - the Alliance is reluctant to escalate "
            + "against an associate. Hostile relations have the opposite effect: the AIO prioritises "
            + "known adversaries, accelerating the crisis above its baseline rate.";

    /** Two %s args: both highlighted - "accelerate or slow" and "current DDA standing". */
    public static final String FACTOR_RELATIONS_TIP_PARA2_FMT =
            "This multiplier can %s the crisis. "
            + "The magnitude is determined by your %s with the DDA.";
    public static final String FACTOR_RELATIONS_TIP_PARA2_H1 = "accelerate or slow";
    public static final String FACTOR_RELATIONS_TIP_PARA2_H2 = "current standing";

    /** %s = the active multiplier value, e.g. "x0.3" (highlighted). */
    public static final String FACTOR_RELATIONS_TIP_CURRENT_FMT =
            "Your current standing reduces the monthly rate to %s of its base value.";

    /** %s = the active multiplier value, e.g. "x1.7" (highlighted). */
    public static final String FACTOR_RELATIONS_TIP_CURRENT_ACCEL_FMT =
            "Your current standing increases the monthly rate to %s of its base value.";

    // =========================================================================
    // Factor Tooltips: Baseline Factor
    // =========================================================================

    public static final String FACTOR_BASELINE_TIP_PARA1 =
            "The Alliance Intelligence Office maintains a baseline level of scrutiny over all known "
            + "colonies. Even with no AI cores deployed, the crisis "
            + "will advance at this minimum rate each month.";

    public static final String FACTOR_BASELINE_TIP_PARA2 =
            "Holding a DDA commission will pause all advancement, including this baseline. "
            + "A payment arrangement with an AIO contact will also suppress monthly advancement.";

    // =========================================================================
    // HAE Factor (DraconisFleetHostileActivityFactor)
    // =========================================================================

    public static final String HAE_MAIN_TIP_PARA1 =
            "The Alliance Intelligence Office is monitoring your colonies and assessing "
            + "the threat you represent to Draconis strategic interests.";

    public static final String HAE_MAIN_TIP_PARA2 =
            "The Alliance's investigation does not represent an immediate threat.";

    public static final String HAE_EXTRA_ROW_AI_CORE = "    AI core deployment";

    /** %s = "AI cores" */
    public static final String HAE_AI_CORE_TIP_PARA =
            "The %s installed in your industries draw significant AIO scrutiny. "
            + "The DDA views large-scale AI deployment as a direct strategic threat.";

    public static final String HAE_BULLET_STRIKE           = "Impending Draconis Alliance strike";
    public static final String HAE_BULLET_STRIKE_HIGHLIGHT = "Draconis Alliance";
    public static final String HAE_BULLET_AVERTED          = "Draconis Alliance attack averted";
    public static final String HAE_STAGE_DESC              = "The Alliance Intelligence Office has authorized a punitive strike.";
    public static final String HAE_EVENT_TOOLTIP_TITLE     = "Draconis Alliance punitive strike";

    // =========================================================================
    // Expedition Assessment (DraconisPunitiveExpedition)
    // =========================================================================

    /** %s = fleet count string (e.g. "2 fleets") (highlighted). */
    public static final String EXPEDITION_ASSESS_PARA1_FMT =
            "The Alliance Intelligence Office views your existence as a direct threat to "
            + "Fafnir's military alliance. An XLII Battlegroup detachment consisting of %s has been "
            + "dispatched to eliminate your production capabilities.";

    /** Two %s args: "ground raids", "orbital bombardment" (highlighted). */
    public static final String EXPEDITION_ASSESS_PARA2_FMT =
            "Unlike raiders, this is a professional military operation with clear strategic objectives. "
            + "The strike force will attempt to systematically destroy all heavy armaments production "
            + "facilities in the target system through %s and %s.";

    public static final String EXPEDITION_ASSESS_PARA2_H1 = "ground raids";
    public static final String EXPEDITION_ASSESS_PARA2_H2 = "orbital bombardment";

    /** %s = "steal AI cores" (highlighted). Final raid only - includes starbase disruption notice. */
    public static final String EXPEDITION_ASSESS_PARA3_FMT       =
            "A special forces element has disabled the starbase in preparation for the attack. Additionally, "
            + "local intelligence suggests they will attempt to %s from any colonies they attack.";
    /** %s = "steal AI cores" (highlighted). Raids 1–2 - no disruption mention. */
    public static final String EXPEDITION_ASSESS_PARA3_NO_DISRUPT_FMT =
            "Local intelligence suggests they will attempt to %s from any colonies they attack.";
    public static final String EXPEDITION_ASSESS_PARA3_HIGHLIGHT = "steal AI cores";

    // =========================================================================
    // Reward Messages (DraconisArmamentsBonus)
    // =========================================================================

    public static final String REWARD_GAINED_LINE1     = "Heavy armaments exports increased";
    /** %s = "+X%" (highlighted). */
    public static final String REWARD_GAINED_LINE2_FMT = "%s income from heavy armaments exports";
    public static final String REWARD_LOST_LINE1       = "Heavy armaments bonus lost";
    public static final String REWARD_STAT_LABEL       = "Stable source (due to Draconis resolution)";
}
