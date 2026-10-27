package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class CommsDisruption extends SignalsSkill {

    public static final float SYSTEM_COOLDOWN_PER_ADVANTAGE = 2f;
    public static final float SYSTEM_REGEN_PER_ADVANTAGE = 2f;

    @Override
    public String getAffectsString() {
        return "all enemy ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s enemy ship system cooldown", atCap("+", SYSTEM_COOLDOWN_PER_ADVANTAGE));
        addEffect(tooltip, "%s enemy ship system charge regeneration", atCap("-", SYSTEM_REGEN_PER_ADVANTAGE));
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
    }
}
