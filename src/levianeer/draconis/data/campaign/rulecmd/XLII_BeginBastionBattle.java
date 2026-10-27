package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeGarrisonManager;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeSystem;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Action command: fired by rules.csv's XLII_monroe_finale_engage once Monroe's final transmission
 * closes (see rules.csv, "Monroe Confrontation" section). Guarantees the bastion a defense fleet
 * ({@link XLII_OfficeGarrisonManager#ensureStationBattleFleet}), dismisses the current dialog and
 * opens the vanilla fleet encounter against the bastion one frame later - same deferred-reopen
 * pattern as XLII_BeginBattlegroupFight.
 * <p>
 * Usage in rules.csv actions column:
 *   XLII_BeginBastionBattle
 */
@SuppressWarnings("unused")
public class XLII_BeginBastionBattle extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_BeginBastionBattle.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (!(Global.getSector().getEntityById(XLII_OfficeSystem.BASTION_ID) instanceof CampaignFleetAPI bastion)) {
            log.error("Draconis: XLII_BeginBastionBattle - bastion entity not found");
            dialog.dismiss();
            return false;
        }

        XLII_OfficeGarrisonManager.ensureStationBattleFleet(bastion);
        dialog.dismiss();

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;

            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(
                            new FleetInteractionDialogPluginImpl(), bastion);
                }
            }
        });

        return true;
    }
}
