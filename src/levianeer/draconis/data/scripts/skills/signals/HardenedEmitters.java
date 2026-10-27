package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class HardenedEmitters extends SignalsSkill {

    public static final float SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE = 1f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s shield damage taken", atCap("-", SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        stats.getShieldDamageTakenMult().modifyMult(getId(), 1f - advantage * SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE / 100f);
    }
}
