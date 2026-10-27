package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class Interlock extends SignalsSkill {

    public static final float ANTI_SMALLCRAFT_DAMAGE_PER_ADVANTAGE = 2f;
    public static final float WEAPON_TURN_RATE_PER_ADVANTAGE = 2f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s damage against missiles and fighters", atCap("+", ANTI_SMALLCRAFT_DAMAGE_PER_ADVANTAGE));
        addEffect(tooltip, "%s weapon turn rate", atCap("+", WEAPON_TURN_RATE_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    // No stat targets point-defense weapons specifically; these are the same stats vanilla's
    // Point Defense skill and SiC's Mobile Defenses use.
    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        float damage = advantage * ANTI_SMALLCRAFT_DAMAGE_PER_ADVANTAGE;
        stats.getDamageToMissiles().modifyPercent(getId(), damage);
        stats.getDamageToFighters().modifyPercent(getId(), damage);
        stats.getWeaponTurnRateBonus().modifyPercent(getId(), advantage * WEAPON_TURN_RATE_PER_ADVANTAGE);
    }
}
