package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class CommsDisruption extends SignalsSkill {

    public static final float SYSTEM_COOLDOWN_PER_ADVANTAGE = 3.5f;
    public static final float SYSTEM_REGEN_PER_ADVANTAGE = 3.5f;
    // Higher than the other lines because objectives only exist in battles over 100 DP.
    public static final float CAPTURE_RATE_PER_ADVANTAGE = 3f;

    @Override
    public String getAffectsString() {
        return "all enemy ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s enemy ship system cooldown", atCap("+", SYSTEM_COOLDOWN_PER_ADVANTAGE));
        addEffect(tooltip, "%s enemy ship system charge regeneration", atCap("-", SYSTEM_REGEN_PER_ADVANTAGE));
        addEffect(tooltip, "%s enemy objective capture speed", atCap("-", CAPTURE_RATE_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public boolean affectsEnemy() {
        return true;
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        stats.getSystemCooldownBonus().modifyPercent(getId(), advantage * SYSTEM_COOLDOWN_PER_ADVANTAGE);
        stats.getSystemRegenBonus().modifyMult(getId(), 1f - advantage * SYSTEM_REGEN_PER_ADVANTAGE / 100f);
        stats.getDynamic().getStat(Stats.SHIP_OBJECTIVE_CAP_RATE_MULT).modifyMult(getId(), 1f - advantage * CAPTURE_RATE_PER_ADVANTAGE / 100f);
    }
}
