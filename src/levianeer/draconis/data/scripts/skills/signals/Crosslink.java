package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class Crosslink extends SignalsSkill {

    public static final float DAMAGE_TO_CRUISERS_PER_ADVANTAGE = 1f;
    public static final float DAMAGE_TO_CAPITALS_PER_ADVANTAGE = 2f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s damage to cruisers", atCap("+", DAMAGE_TO_CRUISERS_PER_ADVANTAGE));
        addEffect(tooltip, "%s damage to capital ships", atCap("+", DAMAGE_TO_CAPITALS_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        stats.getDamageToCruisers().modifyPercent(getId(), advantage * DAMAGE_TO_CRUISERS_PER_ADVANTAGE);
        stats.getDamageToCapital().modifyPercent(getId(), advantage * DAMAGE_TO_CAPITALS_PER_ADVANTAGE);
    }
}
