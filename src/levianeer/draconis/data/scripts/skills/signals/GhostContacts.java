package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class GhostContacts extends SignalsSkill {

    public static final float SPEED_PER_ADVANTAGE = 1f;
    public static final float MANEUVERABILITY_PER_ADVANTAGE = 2f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s top speed", atCap("+", SPEED_PER_ADVANTAGE));
        addEffect(tooltip, "%s maneuverability", atCap("+", MANEUVERABILITY_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        float maneuverability = advantage * MANEUVERABILITY_PER_ADVANTAGE;
        stats.getMaxSpeed().modifyPercent(getId(), advantage * SPEED_PER_ADVANTAGE);
        stats.getAcceleration().modifyPercent(getId(), maneuverability);
        stats.getDeceleration().modifyPercent(getId(), maneuverability);
        stats.getMaxTurnRate().modifyPercent(getId(), maneuverability);
        stats.getTurnAcceleration().modifyPercent(getId(), maneuverability);
    }
}
