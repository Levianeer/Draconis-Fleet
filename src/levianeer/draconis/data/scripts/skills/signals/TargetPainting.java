package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class TargetPainting extends SignalsSkill {

    public static final float MISSILE_DAMAGE_PER_ADVANTAGE = 1.5f;
    public static final float MISSILE_PERFORMANCE_PER_ADVANTAGE = 3f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s missile damage", atCap("+", MISSILE_DAMAGE_PER_ADVANTAGE));
        addEffect(tooltip, "%s missile speed, maneuverability and hitpoints", atCap("+", MISSILE_PERFORMANCE_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        float performance = advantage * MISSILE_PERFORMANCE_PER_ADVANTAGE;
        stats.getMissileWeaponDamageMult().modifyPercent(getId(), advantage * MISSILE_DAMAGE_PER_ADVANTAGE);
        stats.getMissileMaxSpeedBonus().modifyPercent(getId(), performance);
        stats.getMissileAccelerationBonus().modifyPercent(getId(), performance);
        stats.getMissileMaxTurnRateBonus().modifyPercent(getId(), performance);
        stats.getMissileTurnAccelerationBonus().modifyPercent(getId(), performance);
        stats.getMissileHealthBonus().modifyPercent(getId(), performance);
    }
}
