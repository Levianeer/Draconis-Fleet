package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_KoriStrike;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Script command: renders an inline ship-composition preview of the Forty-Second Battlegroup
 * directly into the current dialog's text panel - part of the pre-commit stakes-telegraphing added
 * in the Uplink to God endgame redesign (see .claude/systems/uplink-to-god-endgame-redesign.md).
 * <p>
 * Same mechanism vanilla bounty bar missions use for their own "intel assessment" scene - confirmed
 * against the decompiled source, {@code BaseCustomBountyCreator.addIntelAssessment()}
 * (com.fs.starfarer.api.impl.campaign.missions.cb): {@code TextPanelAPI.beginTooltip()} returns a
 * {@code TooltipMakerAPI} that renders inline in the dialogue text (not restricted to the intel
 * screen), fill it with {@code addShipList()}, then commit it with {@code text.addTooltip()}.
 * Sampling shape (partial list, weighted toward cost, flagship always included, "may contain
 * upwards of N other ships" for the remainder) copied from that same vanilla method.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_ShowBattlegroupAssessment
 * <p>
 * No parameters - reads the fleet from XLII_KoriStrike.previewBattlegroupFleet(), a throwaway
 * fleet built with the same params as the real fight but never spawned into the world.
 * <p>
 * The actual rendering lives in the static {@link #render(InteractionDialogAPI)} - this class's own
 * {@code execute()} just delegates to it - so {@code XLII_KoriStrike.showBriefingMain()} (hand-authored
 * Java, not rules.csv) can call the same logic directly rather than going through a rulecmd
 * invocation with no real parameters to pass. Moved here so the player sees this before the
 * marine-raid confirm rather than after - the raid launch is the real point of no return, so
 * that's where full information needs to be available, not later at the interception.
 */
@SuppressWarnings("unused")
public class XLII_ShowBattlegroupAssessment extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        return render(dialog);
    }

    public static boolean render(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        CampaignFleetAPI battlegroup = XLII_KoriStrike.previewBattlegroupFleet();
        if (battlegroup == null) return false;

        TextPanelAPI text = dialog.getTextPanel();

        int max = 7;
        int cols = 7;
        float iconSize = 440f / cols;

        List<FleetMemberAPI> members = battlegroup.getFleetData().getMembersListCopy();
        List<FleetMemberAPI> list = new ArrayList<FleetMemberAPI>();
        for (FleetMemberAPI member : members) {
            if (list.size() >= max) break;
            if (member.isFighterWing()) continue;

            FleetMemberAPI copy = Global.getFactory().createFleetMember(FleetMemberType.SHIP, member.getVariant());
            if (member.isFlagship()) {
                copy.setCaptain(battlegroup.getCommander());
            }
            list.add(copy);
        }

        if (list.isEmpty()) return false;

        TooltipMakerAPI info = text.beginTooltip();
        info.setParaSmallInsignia();
        info.addPara(
            "Your Comms Officer has partial read on the nearest XLII " +
            "deployment. They recommend you review it before committing.",
            0f
        );
        info.addShipList(cols, 1, iconSize, battlegroup.getFaction().getBaseUIColor(), list, 10f);

        int remaining = members.size() - list.size();
        if (remaining >= 5) {
            String numStr = remaining < 10 ? "5" : remaining < 20 ? "10" : "20";
            info.addPara("The assessment notes the Battlegroup may contain upwards of %s other ships " +
                    "of lesser significance.", 10f, Misc.getHighlightColor(), numStr);
        } else if (remaining > 0) {
            info.addPara("The assessment notes the Battlegroup may contain several other ships of " +
                    "lesser significance.", 10f);
        } else {
            info.addPara("The assessment appears to be complete.", 10f);
        }

        text.addTooltip();
        return true;
    }
}