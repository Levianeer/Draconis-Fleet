package levianeer.draconis.data.campaign.intel.events.crisis.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import org.apache.log4j.Logger;

import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Listens for player combat against DDA/XLII fleets and applies immediate AIO tracker spikes.
 * <p>
 * Two cases:
 *   - Regular DDA/XLII fleet: fighting back costs you something, even when necessary (+hostilePoints)
 *   - Shadow fleet ($draconisRaider): defeating one slightly advances the tracker - the AIO
 *     notices you're capable (+shadowPoints)
 * <p>
 * Expedition fleets ($dda_expedition_fleet) are excluded; their defeat is handled separately
 * by DraconisAIOTracker.onExpeditionDefeated().
 * <p>
 * Registered as a transient FleetEventListener in XLII_ModPlugin.onGameLoad().
 */
public class DraconisFleetCombatListener implements FleetEventListener {

    private static final Logger log = Global.getLogger(DraconisFleetCombatListener.class);

    /** Set on a fleet after processing to prevent double-counting across multiple callbacks. */
    private static final String PROCESSED_FLAG = "$dda_aio_combat_processed";

    /** Set on punitive expedition fleets by DraconisPunitiveExpedition.configureFleet(). */
    private static final String EXPEDITION_FLAG = "$dda_expedition_fleet";

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        log.info("DDA: reportBattleOccurred called - playerInvolved=" + battle.isPlayerInvolved()
                + " trackerActive=" + (DraconisAIOTracker.get() != null));
        if (!battle.isPlayerInvolved()) return;

        DraconisAIOTracker tracker = DraconisAIOTracker.get();
        if (tracker == null) {
            log.info("DDA: Combat listener skipping - AIO tracker not yet active");
            return;
        }

        List<CampaignFleetAPI> opposing = battle.getNonPlayerSideSnapshot();
        if (opposing == null) return;

        for (CampaignFleetAPI enemy : opposing) {
            if (enemy == null) continue;
            if (battle.onPlayerSide(enemy)) continue;

            // Prevent double-counting if the callback fires more than once for the same fleet
            if (enemy.getMemoryWithoutUpdate().getBoolean(PROCESSED_FLAG)) continue;
            enemy.getMemoryWithoutUpdate().set(PROCESSED_FLAG, true);

            String factionId = enemy.getFaction() != null ? enemy.getFaction().getId() : null;
            if (!DRACONIS.equals(factionId) && !FORTYSECOND.equals(factionId)) continue;

            // Expedition defeat is handled by onExpeditionDefeated() - skip here
            if (enemy.getMemoryWithoutUpdate().getBoolean(EXPEDITION_FLAG)) continue;

            // Any engagement against a DDA/XLII fleet (shadow or otherwise) costs something
            int pts = getIntSetting("draconisAIOCombatHostilePoints", 5);
            tracker.addOneTimeFactor(pts, "Hostile ships destroyed");
            log.info("DDA: AIO +" + pts + " - hostile ships destroyed");
        }
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        // no-op
    }

    private int getIntSetting(String key, int defaultValue) {
        try {
            return (int) Global.getSettings().getFloat(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
