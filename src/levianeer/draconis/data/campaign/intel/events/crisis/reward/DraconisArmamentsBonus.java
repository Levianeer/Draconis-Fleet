package levianeer.draconis.data.campaign.intel.events.crisis.reward;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;

/**
 * Grants heavy armaments production bonus after defeating Draconis expedition
 */
public class DraconisArmamentsBonus {

    public static final String KEY = "$draconisArmamentsBonus";
    public static final String BONUS_FROM_DEFEAT_KEY = "$draconisArmamentsBonusFromDefeat";
    public static final String MOD_ID = "draconis_armaments_bonus";

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
        float exportBonus = Global.getSettings().getFloat("draconisArmamentsExportBonus");
        Global.getSector().getPlayerStats().getDynamic().getStat(
                        Stats.getCommodityExportCreditsMultId(Commodities.HAND_WEAPONS))
                .modifyMult(MOD_ID, 1f + exportBonus, AIOStrings.REWARD_STAT_LABEL);
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
        msg.addLine(AIOStrings.REWARD_GAINED_LINE1, Misc.getBasePlayerColor());
        float exportBonus = Global.getSettings().getFloat("draconisArmamentsExportBonus");
        msg.addLine(BaseIntelPlugin.BULLET + AIOStrings.REWARD_GAINED_LINE2_FMT, Misc.getTextColor(),
                new String[] {"+" + Math.round(exportBonus * 100f) + "%"},
                Misc.getHighlightColor());

        msg.setIcon(Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS).getIconName());
        msg.setSound(Sounds.REP_GAIN);
        Global.getSector().getCampaignUI().addMessage(msg, MessageClickAction.COLONY_INFO);
    }

    public static void sendLostMessage() {
        MessageIntel msg = new MessageIntel();
        msg.addLine(AIOStrings.REWARD_LOST_LINE1, Misc.getBasePlayerColor());
        msg.setIcon(Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS).getIconName());
        msg.setSound(Sounds.REP_LOSS);
        Global.getSector().getCampaignUI().addMessage(msg, MessageClickAction.COLONY_INFO);
    }
}
