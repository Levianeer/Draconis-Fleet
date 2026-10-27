package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class SeekerBlinding extends SignalsSkill {

    public static final float MISSILE_SPEED_PER_ADVANTAGE = 2f;
    public static final float MISSILE_MANEUVERABILITY_PER_ADVANTAGE = 2f;

    @Override
    public String getAffectsString() {
        return "all enemy ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s enemy missile speed", atCap("-", MISSILE_SPEED_PER_ADVANTAGE));
        addEffect(tooltip, "%s enemy missile maneuverability", atCap("-", MISSILE_MANEUVERABILITY_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public boolean affectsEnemy() {
        return true;
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        float speed = 1f - advantage * MISSILE_SPEED_PER_ADVANTAGE / 100f;
        float maneuverability = 1f - advantage * MISSILE_MANEUVERABILITY_PER_ADVANTAGE / 100f;
        stats.getMissileMaxSpeedBonus().modifyMult(getId(), speed);
        stats.getMissileAccelerationBonus().modifyMult(getId(), speed);
        stats.getMissileMaxTurnRateBonus().modifyMult(getId(), maneuverability);
        stats.getMissileTurnAccelerationBonus().modifyMult(getId(), maneuverability);
    }
}
