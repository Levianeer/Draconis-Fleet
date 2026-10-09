package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.INTELLIGENCE_OFFICE;

/**
 * Script command: shows Longsight's portrait in the current dialog. Longsight has no registered
 * PersonAPI anywhere in the game's data files - every scene that needs its portrait builds one on
 * the fly with {@link Global#getFactory()}. Ported out of {@code XLII_LongsightContactDialog}'s
 * old {@code init()} so the Longsight Contact scene (rules.csv, {@code # [UPLINK TO GOD] Longsight
 * Contact}) can show it without a hand-rolled InteractionDialogPlugin.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_ShowLongsightVisual
 * <p>
 * The actual person-building lives in the static {@link #buildPerson()} - this class's own
 * {@code execute()} just delegates to it, showing the result as the primary portrait. Callers that
 * need it as a *second* person instead (e.g. {@code dialog.getVisualPanel().showSecondPerson(...)},
 * which takes a PersonAPI directly rather than an ImportantPeopleAPI-registered id - Longsight has
 * no such registration) should call {@link #buildPerson()} themselves. See
 * {@code XLII_KoriStrike.showFailureDeath()} for the second-person usage.
 */
@SuppressWarnings("unused")
public class XLII_ShowLongsightVisual extends BaseCommandPlugin {

    public static PersonAPI buildPerson() {
        PersonAPI sigma = Global.getFactory().createPerson();
        sigma.setName(new FullName("Longsight", "", FullName.Gender.ANY));
        sigma.setPortraitSprite("graphics/portraits/characters/XLII_longsight.png");
        sigma.setFaction(INTELLIGENCE_OFFICE);
        return sigma;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        dialog.getVisualPanel().showPersonInfo(buildPerson(), false);

        return true;
    }
}
