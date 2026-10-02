package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import levianeer.draconis.data.campaign.companion.KorrinCompanion;
import levianeer.draconis.data.campaign.companion.KorrinTopicQueue;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Listens for player victories against the four vanilla sites the sector-tour arc sends the player
 * to look at with Elias Korrin aboard - the Remnants (faction id "remnant"), the Omega ("omega"),
 * the Threat ("threat"), and the Shrouded Dweller ("dweller"). See
 * work/outline/main-story-endings.md's "0b. The sector tour" for the full narrative design and
 * work/drafts/sector-tour-arc-dialogue-draft.md for the drafted (not yet ported) reaction dialogue -
 * this class only detects and records the stop; it queues a Korrin topic id for that content to
 * eventually consume, but does not carry any dialogue itself.
 * <p>
 * Each site counts once, ever, and only if Elias is aboard at the moment of victory and the player
 * fleet is the primary winner - the tour is framed as something the two of them do together, not a
 * fleet action he happens to be along for, and a fled or lost engagement is not a completed stop.
 * <p>
 * Registered as a transient FleetEventListener in XLII_ModPlugin.onGameLoad(), same pattern as
 * DraconisFleetCombatListener (see intel/events/crisis/listener/) - not saved, re-added fresh each
 * game load, no cleanup needed.
 */
public class XLII_SectorTourListener implements FleetEventListener {

    private static final Logger log = Global.getLogger(XLII_SectorTourListener.class);

    /** Running count of distinct sites completed (0-4). Read by the archive-hint gate. */
    public static final String MEM_STOPS_COMPLETE = "$XLII_tourStopsComplete";

    /**
     * Migration: builds before this one stored the counter under the literal key
     * {@code "$global.XLII_tourStopsComplete"} - a Java string mistake, since "global." in
     * rules.csv is a memory-scope selector stripped by the rule engine before lookup, not part of
     * the stored key (every other global flag in this mod, e.g. {@code $XLII_koriInfiltrationOffered},
     * is already named correctly without it). rules.csv's own {@code $global.XLII_tourStopsComplete
     * >= 4} check was therefore reading a key that was never written, so the archive-hint gate and
     * the tour-completion reaction line could never fire. Carries over any count already earned
     * under the old key so an in-progress save doesn't appear to lose that progress.
     */
    public static void migrate() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String oldKey = "$global.XLII_tourStopsComplete";
        if (!mem.contains(oldKey)) return;

        if (!mem.contains(MEM_STOPS_COMPLETE)) {
            mem.set(MEM_STOPS_COMPLETE, mem.getInt(oldKey));
        }
        mem.unset(oldKey);
    }

    private static final String[] SITE_FACTIONS = { "remnant", "omega", "threat", "dweller" };

    /** Per-site one-shot flag for the given vanilla faction id, or null if it isn't one of the four. */
    public static String flagForSite(String factionId) {
        for (String f : SITE_FACTIONS) {
            if (f.equals(factionId)) return "$global.XLII_tour_" + f;
        }
        return null;
    }

    public static boolean isTourComplete() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        for (String f : SITE_FACTIONS) {
            if (!mem.getBoolean("$global.XLII_tour_" + f)) return false;
        }
        return true;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) return;
        if (primaryWinner == null || !primaryWinner.isPlayerFleet()) return;
        if (!KorrinCompanion.isAboard()) return;

        List<CampaignFleetAPI> opposing = battle.getNonPlayerSideSnapshot();
        if (opposing == null) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        for (CampaignFleetAPI enemy : opposing) {
            if (enemy == null || enemy.getFaction() == null) continue;

            String factionId = enemy.getFaction().getId();
            String flag = flagForSite(factionId);
            if (flag == null) continue;
            if (mem.getBoolean(flag)) continue;

            mem.set(flag, true);
            int complete = mem.getInt(MEM_STOPS_COMPLETE) + 1;
            mem.set(MEM_STOPS_COMPLETE, complete);

            KorrinTopicQueue.queue("korrin_tour_" + factionId);

            log.info("Draconis: XLII_SectorTourListener - tour stop complete: " + factionId
                    + " (" + complete + "/4)");
        }
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        // no-op
    }
}
