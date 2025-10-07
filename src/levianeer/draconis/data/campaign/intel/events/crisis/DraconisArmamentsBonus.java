package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * Grants heavy armaments production bonus after defeating Draconis expedition
 */
public class DraconisArmamentsBonus {

    public static String KEY = "$draconisArmamentsBonus";
    public static String BONUS_FROM_DEFEAT_KEY = "$draconisArmamentsBonusFromDefeat";
    public static String MOD_ID = "draconis_armaments_bonus";

    public static float EXPORT_BONUS = Global.getSettings().getFloat("draconisArmamentsExportBonus");

    public static void grantBonus(boolean fromDefeat) {
        if (Global.getSector().getMemoryWithoutUpdate().contains(KEY)) {
            return;
        }

        Global.getSector().getMemoryWithoutUpdate().set(KEY, true);

        // Track whether this bonus came from defeating the expedition (permanent)
        // or from commission+rep (removable if commission lost)
        if (fromDefeat) {
            Global.getSector().getMemoryWithoutUpdate().set(BONUS_FROM_DEFEAT_KEY, true);
        }

        sendGainedMessage();

        // Apply export bonus to heavy armaments
        Global.getSector().getPlayerStats().getDynamic().getStat(
                        Stats.getCommodityExportCreditsMultId(Commodities.HAND_WEAPONS))
                .modifyMult(MOD_ID, 1f + EXPORT_BONUS,
                        "Proven stable source (due to Draconis resolution)");
    }

    public static boolean isBonusFromDefeat() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(BONUS_FROM_DEFEAT_KEY);
    }

    public static void removeBonus() {
        if (!Global.getSector().getMemoryWithoutUpdate().contains(KEY)) {
            return;
        }
        sendLostMessage();

        Global.getSector().getPlayerStats().getDynamic().getStat(
                        Stats.getCommodityExportCreditsMultId(Commodities.HAND_WEAPONS))
                .unmodify(MOD_ID);

        Global.getSector().getMemoryWithoutUpdate().unset(KEY);
        Global.getSector().getMemoryWithoutUpdate().unset(BONUS_FROM_DEFEAT_KEY);
    }

    public static void sendGainedMessage() {
        MessageIntel msg = new MessageIntel();
        msg.addLine("Heavy armaments exports increased", Misc.getBasePlayerColor());
        msg.addLine(BaseIntelPlugin.BULLET + "%s income from heavy armaments exports", Misc.getTextColor(),
                new String[] {"+" + Math.round(EXPORT_BONUS * 100f) + "%"},
                Misc.getHighlightColor());

        msg.setIcon(Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS).getIconName());
        msg.setSound(Sounds.REP_GAIN);
        Global.getSector().getCampaignUI().addMessage(msg, MessageClickAction.COLONY_INFO);
    }

    public static void sendLostMessage() {
        MessageIntel msg = new MessageIntel();
        msg.addLine("Heavy armaments bonus lost", Misc.getBasePlayerColor());
        msg.setIcon(Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS).getIconName());
        msg.setSound(Sounds.REP_LOSS);
        Global.getSector().getCampaignUI().addMessage(msg, MessageClickAction.COLONY_INFO);
    }
}