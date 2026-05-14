package levianeer.draconis.data.campaign.intel.events.crisis.util;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisFleetHostileActivityFactor;

/**
 * Manages colony crisis hostile activity factor registration
 * Includes both armaments competition and AI core acquisition causes
 * Separate from NPC-faction AI core raids (handled by aicore/raids/)
 */
public class DraconisHostileActivityManager implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisHostileActivityManager.class);

    private boolean added = false;

    @Override
    public boolean isDone() {
        return added;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (!added) {
            HostileActivityEventIntel intel = HostileActivityEventIntel.get();
            if (intel != null) {
                DraconisFleetHostileActivityFactor existingFactor = (DraconisFleetHostileActivityFactor) intel.getFactorOfClass(DraconisFleetHostileActivityFactor.class);

                if (existingFactor == null) {
                    DraconisFleetHostileActivityFactor factor = new DraconisFleetHostileActivityFactor(intel);
                    intel.addFactor(factor);
                    log.info("Draconis: Draconis colony crisis factor registered");
                }

                added = true;
            }
        }
    }
}
