package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeSystem;
import org.apache.log4j.Logger;

/**
 * Polls for Ladon's Office bastion being destroyed, once Burn the Machine's Kori strike has
 * concluded. {@code XLII_KoriStrike.finalizeStrike()} unsets
 * {@link XLII_OfficeSystem#ACCESS_GRANTED_FLAG} (making the bastion hostile again -
 * {@code XLII_OfficeGarrisonManager} re-applies that hostility every tick) and registers this
 * monitor. The fight itself needs no custom combat plugin - the bastion is already a real,
 * combat-capable {@code BATTLESTATION} fleet, so this only detects the outcome and delivers the
 * closing scene once the entity is gone.
 */
public class XLII_BastionDestructionMonitor implements EveryFrameScript {

    private static final Logger log = Global.getLogger(XLII_BastionDestructionMonitor.class);

    /** Set once the closing scene has fired. One-shot; the finale never repeats. */
    public static final String FINALE_FLAG = "$XLII_burnTheMachineFinale";

    private boolean done = false;

    @Override public boolean isDone() { return done; }
    @Override public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        if (done) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.getBoolean(FINALE_FLAG)) {
            done = true;
            return;
        }

        Object entity = Global.getSector().getEntityById(XLII_OfficeSystem.BASTION_ID);
        if (entity instanceof CampaignFleetAPI) {
            CampaignFleetAPI bastion = (CampaignFleetAPI) entity;
            if (!bastion.isDespawning()) return; // still alive
        }
        // entity == null or despawning: destroyed. Wait for any battle/loot dialog to clear
        // before opening the epilogue - showInteractionDialog silently no-ops while busy.
        if (Global.getSector().getCampaignUI().isShowingDialog()) return;

        mem.set(FINALE_FLAG, true);
        done = true;

        CampaignFleetAPI target = Global.getSector().getPlayerFleet();
        if (target == null) return;
        log.info("Draconis: XLII_BastionDestructionMonitor - Ladon bastion destroyed, delivering Burn the Machine epilogue");
        Global.getSector().getCampaignUI().showInteractionDialog(new XLII_BurnTheMachineEpilogue(), target);
    }

    /**
     * True only for a player partway through Burn the Machine's finale: the Kori strike is done
     * (that's what unsets ACCESS_GRANTED_FLAG and makes the bastion attackable) but the closing
     * scene hasn't fired yet. Gating on MEM_COMPLETE keeps this from registering for every other
     * save in the sector.
     */
    public static boolean shouldRegister() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        return mem.getBoolean(XLII_KoriStrike.MEM_COMPLETE) && !mem.getBoolean(FINALE_FLAG);
    }
}
