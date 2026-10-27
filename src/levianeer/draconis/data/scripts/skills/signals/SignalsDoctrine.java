package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class SignalsDoctrine extends SignalsSkill {

    public static final float RANGE_PER_ADVANTAGE = 0.5f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s weapon range, including missiles", atCap("+", RANGE_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        float range = advantage * RANGE_PER_ADVANTAGE;
        stats.getBallisticWeaponRangeBonus().modifyPercent(getId(), range);
        stats.getEnergyWeaponRangeBonus().modifyPercent(getId(), range);
        stats.getMissileWeaponRangeBonus().modifyPercent(getId(), range);
    }
}
