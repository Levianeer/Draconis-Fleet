package levianeer.draconis.data.campaign.intel.longsight.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import levianeer.draconis.data.campaign.ids.Factions;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * The player-vs-Draconis axis of the Office Takeover crisis's hostility model (see
 * "Hostility - two independent axes" in `work/outline/main-story-endings.md`). Sector-vs-Draconis
 * fallout is automatic once a market is captured to Draconis, but the crisis's active fleets are
 * tagged Intelligence Office, not Draconis (see {@code XLII_LongsightBastionIntel}'s class doc), so
 * raw combat doesn't trigger it. This listener is the other axis: the player's own standing with
 * Draconis takes a hit for personally attacking one of these fleets, reflecting Director Monroe's
 * institutional interest in the crisis succeeding (see "Director Monroe's calculus").
 * <p>
 * Modeled on {@code DraconisFleetCombatListener} (`intel/events/crisis/listener/`) - same
 * {@link FleetEventListener} registration shape and double-counting guard - but scoped to this
 * crisis via {@link XLII_LongsightBastionIntel#CRISIS_FLEET_FLAG} rather than a faction check,
 * since these fleets share their faction tag with Ladon's own pre-existing Intelligence Office
 * bastion/garrison, a different, already-resolved story beat (Burn the Machine) that this listener
 * must not also apply to.
 * <p>
 * Registered as a transient listener in {@code XLII_ModPlugin.onGameLoad()}, same as
 * {@code DraconisFleetCombatListener}.
 */
public class XLII_LongsightCrisisCombatListener implements FleetEventListener {

    private static final Logger log = Global.getLogger(XLII_LongsightCrisisCombatListener.class);

    /** Set on a fleet after processing to prevent double-counting across multiple callbacks. */
    private static final String PROCESSED_FLAG = "$XLII_longsightCrisisCombatProcessed";

    /** Placeholder - not tuned. See checklist Stage 8. */
    private static final float DRACONIS_REP_HIT = -0.02f;

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) return;

        List<CampaignFleetAPI> opposing = battle.getNonPlayerSideSnapshot();
        if (opposing == null) return;

        for (CampaignFleetAPI enemy : opposing) {
            if (enemy == null) continue;
            if (battle.onPlayerSide(enemy)) continue;
            if (!enemy.getMemoryWithoutUpdate().getBoolean(XLII_LongsightBastionIntel.CRISIS_FLEET_FLAG)) continue;

            if (enemy.getMemoryWithoutUpdate().getBoolean(PROCESSED_FLAG)) continue;
            enemy.getMemoryWithoutUpdate().set(PROCESSED_FLAG, true);

            CustomRepImpact impact = new CustomRepImpact();
            impact.delta = DRACONIS_REP_HIT;
            Global.getSector().adjustPlayerReputation(
                    new RepActionEnvelope(RepActions.CUSTOM, impact, null, null, true),
                    Factions.DRACONIS);

            log.info("Draconis: Longsight crisis combat - player engaged a crisis fleet, Draconis rep "
                    + DRACONIS_REP_HIT);
        }
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        // no-op
    }
}
