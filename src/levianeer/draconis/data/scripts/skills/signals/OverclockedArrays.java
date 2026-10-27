package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfareScript;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;

public class OverclockedArrays extends SignalsSkill {

    public static final float CAP_BONUS = 5f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        int base = (int) ElectronicWarfareScript.BASE_MAXIMUM;
        tooltip.addPara("%s to the fleet's maximum ECM advantage*, from %s to %s", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(),
                "+" + (int) CAP_BONUS + "%", base + "%", (base + (int) CAP_BONUS) + "%");
        tooltip.addPara("   - Raises the cap on the enemy weapon range penalty", 0f, Misc.getTextColor(), Misc.getHighlightColor());
        tooltip.addPara("   - Signals effects keep scaling past their listed values, up to %s of them", 0f, Misc.getTextColor(), Misc.getHighlightColor(),
                Math.round((base + CAP_BONUS) / base * 100f) + "%");
        addAdvantageNote(tooltip);
    }

    // Raises the commander's cap, which ElectronicWarfareScript reads directly, so vanilla's own
    // range penalty and status line pick it up too. Owning fleet only.
    @Override
    public void onActivation(SCData data) {
        data.getCommander().getStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_MAX).modifyFlat(getId(), CAP_BONUS);
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getCommander().getStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_MAX).unmodify(getId());
    }
}
