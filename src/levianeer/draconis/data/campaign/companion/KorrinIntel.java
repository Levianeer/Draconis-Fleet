package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Korrin's contact entry - the player's only authority over whether he is with them, and the only
 * place he can be reached from anywhere.
 * <p>
 * This intel never ends. It is created once he has been met and lives for the rest of the game,
 * so the endAfterDelay/expiry discipline in .claude/systems/intel.md does not apply - but
 * notifyEnded() still unregisters the script, for consistency with the rest of the mod.
 * <p>
 * The panel follows vanilla {@code ContactIntel}: portrait and crest side by side, his personal
 * standing under the portrait and Draconis' under the crest.
 */
public class KorrinIntel extends BaseIntelPlugin {

    /** rules.csv trigger for the "talk to him" conversation. */
    public static final String TALK_TRIGGER = "XLII_KorrinTalk";

    private static final String BUTTON_TALK = "korrin_talk";
    private static final String BUTTON_TAKE_COMMAND = "korrin_take_command";
    private static final String BUTTON_STAND_DOWN = "korrin_stand_down";
    private static final String BUTTON_RETURN_TO_POST = "korrin_return_to_post";

    /** Creates the entry if it does not exist yet. Safe to call on every game load. */
    public static void ensureExists() {
        if (Global.getSector().getIntelManager().hasIntelOfClass(KorrinIntel.class)) return;
        new KorrinIntel();
    }

    private KorrinIntel() {
        Global.getSector().getIntelManager().addIntel(this, false);
        Global.getSector().addScript(this);
    }

    @Override
    public String getName() {
        return KorrinStrings.INTEL_NAME;
    }

    @Override
    public String getIcon() {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        return korrin != null ? korrin.getPortraitSprite() : super.getIcon();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(DRACONIS);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_CONTACTS);
        tags.add(DRACONIS);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null || KorrinCompanion.isAboard()) return null;
        return korrin.getMarket() != null ? korrin.getMarket().getPrimaryEntity() : null;
    }

    /**
     * The contact entry is the companion channel, and it only exists while he is with the fleet.
     * Once he is back at his post he is an ordinary Ring-Port NPC again - reachable in person,
     * not over the relay - so the player has to go and get him.
     * <p>
     * Derived from state rather than toggled via setHidden(), so no transition can forget to
     * update it. super.isHidden() is preserved to keep the tutorial suppression.
     */
    @Override
    public boolean isHidden() {
        return super.isHidden() || !KorrinCompanion.isAboard();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    /**
     * The list-row and notification bullet points - vanilla ContactIntel's "Faction:" / "Type:"
     * / "Importance:" subsection, so his entry in the intel list and his message-log notifications
     * read the same way any other contact's do. Faction is pinned to Draconis (see
     * {@link #crestForState()} for why) and coloured with its own UI color; the other two are
     * fixed, since he is always the same kind of contact.
     */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                    Color tc, float initPad) {
        Color h = Misc.getHighlightColor();
        FactionAPI draconis = Global.getSector().getFaction(DRACONIS);

        info.addPara("Faction: " + draconis.getDisplayName(), initPad, tc,
                draconis.getBaseUIColor(), draconis.getDisplayName());
        initPad = 0f;

        info.addPara("Type: %s", initPad, tc, h, "Personal");
        initPad = 0f;

        info.addPara("Importance: %s", initPad, tc, h, "High");
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null) return;

        float opad = 10f;

        info.addImages(width, 128, opad, opad, korrin.getPortraitSprite(), crestForState());

        float indent = 25f;
        info.addSpacer(0).getPosition().setXAlignOffset(indent);

        float barWidth = (128f * 2f + 10f - 10f) / 2f;
        info.addRelationshipBar(korrin, barWidth, opad);
        float barHeight = info.getPrev().getPosition().getHeight();
        info.addRelationshipBar(Global.getSector().getFaction(DRACONIS), barWidth, 0f);
        UIComponentAPI prev = info.getPrev();
        prev.getPosition().setYAlignOffset(barHeight);
        prev.getPosition().setXAlignOffset(barWidth + 10f);
        info.addSpacer(0f);
        info.getPrev().getPosition().setXAlignOffset(-(barWidth + 10f));
        info.addSpacer(0).getPosition().setXAlignOffset(-indent);

        info.addPara(postingText(), opad);

        int backlog = KorrinTopicQueue.getBacklog().size();
        if (backlog == 1) {
            info.addPara(KorrinStrings.BACKLOG_ONE, opad);
        } else if (backlog > 1) {
            info.addPara(KorrinStrings.BACKLOG_MANY, opad, Misc.getHighlightColor(), String.valueOf(backlog));
        }

        addGenericButton(info, width, KorrinStrings.BUTTON_TALK_TEXT, BUTTON_TALK);

        // -10f before each button after the first tightens addGenericButton's default 20f pad to
        // 10f, matching ContactIntel's stacked-button gap. No STATIONED case: the entry is hidden
        // then, and he's re-recruited in person at Ring-Port via rules.csv. No GONE case either -
        // terminal (see KorrinCompanion's class doc) - so no action buttons are offered; falls
        // through to default, same as STATIONED.
        switch (KorrinCompanion.getState()) {
            case ABOARD_RESERVE:
                info.addSpacer(-10f);
                addGenericButton(info, width, KorrinStrings.BUTTON_TAKE_COMMAND_TEXT, BUTTON_TAKE_COMMAND);
                info.addSpacer(-10f);
                addGenericButton(info, width, KorrinStrings.BUTTON_RETURN_TO_POST_TEXT, BUTTON_RETURN_TO_POST);
                break;
            case ABOARD_OFFICER:
                info.addSpacer(-10f);
                addGenericButton(info, width, KorrinStrings.BUTTON_STAND_DOWN_TEXT, BUTTON_STAND_DOWN);
                info.addSpacer(-10f);
                addGenericButton(info, width, KorrinStrings.BUTTON_RETURN_TO_POST_TEXT, BUTTON_RETURN_TO_POST);
                break;
            default:
                break;
        }
    }

    /**
     * The crest shown beside his portrait. Always a Draconis crest, never korrin.getFaction() -
     * his faction follows him onto the player's roster, which would swap the flag mid-panel.
     * <p>
     * The colour tracks the same three situations as {@link #postingText()}: green while he is
     * helming a ship, red while he holds a commission with nothing to put it on, white while he
     * is idle and not an acting officer.
     */
    private String crestForState() {
        String id;
        if (KorrinCompanion.getState() == KorrinCompanion.State.ABOARD_OFFICER) {
            id = KorrinCompanion.hasCommand() ? "XLII_korrin_crest_green" : "XLII_korrin_crest_red";
        } else {
            id = "XLII_korrin_crest_white";
        }
        return Global.getSettings().getSpriteName("misc", id);
    }

    /** Plain current status. Scene-setting lives in the Talk conversation, not on the panel. */
    private String postingText() {
        switch (KorrinCompanion.getState()) {
            case ABOARD_OFFICER: return KorrinCompanion.hasCommand() ? KorrinStrings.STATUS_COMMANDING
                                                                     : KorrinStrings.STATUS_OFFICER_NO_SHIP;
            case ABOARD_RESERVE: return KorrinStrings.STATUS_RESERVE;
            // Unreachable in practice today - isHidden() already hides the whole entry once he's
            // GONE (isAboard() is false for that state too) - kept for correctness if that ever
            // changes (e.g. a future pass wants a visible "closed" memorial entry instead of hiding).
            case GONE:            return KorrinStrings.STATUS_GONE;
            default:             return KorrinStrings.STATUS_STATIONED;
        }
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BUTTON_TALK.equals(buttonId)) {
            openTalkDialog(ui);
            return;
        }

        if (BUTTON_TAKE_COMMAND.equals(buttonId)) {
            KorrinCompanion.takeCommand();
        } else if (BUTTON_STAND_DOWN.equals(buttonId)) {
            KorrinCompanion.standDown();
        } else if (BUTTON_RETURN_TO_POST.equals(buttonId)) {
            KorrinCompanion.returnToStation();
        } else {
            return;
        }

        if (isHidden()) {
            ui.recreateIntelUI();
        } else {
            ui.updateUIForItem(this);
        }
    }

    /**
     * Opens a rules.csv-driven conversation from the intel screen.
     * <p>
     * The player fleet is the interaction target because RuleBasedInteractionDialogPluginImpl
     * dereferences it - a null target would NPE. Nothing else is read off that target: the rules
     * reach Korrin through the XLII_Korrin command, not through dialog memory scoping.
     */
    private void openTalkDialog(IntelUIAPI ui) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null) return;

        ui.showDialog(Global.getSector().getPlayerFleet(), TALK_TRIGGER);
    }
}
