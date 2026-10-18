package levianeer.draconis.data.campaign.intel.stellar_mirror;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission.GlobalBooleanChecker;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_MirrorAssault;

import java.awt.Color;
import java.util.Set;

/**
 * Quest tracker for Faller's Stellar Mirror contract - the gate in front of destroying the three
 * Stellar Arrays and collapsing the Fafnir Rift (see {@code # [STELLAR MIRROR] Quest} and
 * "# [FAFNIR LORE] Stellar Arrays", rules.csv, and {@link XLII_MirrorAssault}).
 * <p>
 * Created via {@code BeginMission StellarMirrorQuestMission} from the quest's bar-accept row at
 * Ring-Port. Unlike every other Draconis quest tracker, this one is never a target of
 * {@code XLII_MirrorAssault.failAllDraconisQuests()} - it's the tracker for the hostile act
 * itself, not a casualty of it.
 */
public class StellarMirrorQuestMission extends HubMissionWithBarEvent {

    public enum Stage {
        /** Destroy all three arrays - guard fleet, then marine raid, any order. */
        DESTROY_ARRAYS,
        /** All three down. Return to Ring-Port and tell Faller it's done. */
        RETURN_TO_FALLER,
        COMPLETED,
    }

    private static final String QUEST_OVERVIEW =
            "Faller flew for Athebyne's First Fleet. He wants all three Stellar Arrays gone, and "
                    + "the Rift with them - so that the Alliance which burned his world and then "
                    + "inherited it stops being the only thing behind that wall. He has never had "
                    + "the hulls to try it himself.";

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$XLII_stellarMirror_missionRef")) return false;

        setStartingStage(determineStartingStage());
        setSuccessStage(Stage.COMPLETED);
        setNoAbandon();
        setImportant(true);

        setStageOnCustomCondition(Stage.RETURN_TO_FALLER, new GlobalBooleanChecker(XLII_MirrorAssault.MEM_ALL_DESTROYED));
        setStageOnCustomCondition(Stage.COMPLETED,        new GlobalBooleanChecker("$XLII_stellarMirrorComplete"));

        return true;
    }

    private Stage determineStartingStage() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        if (g.getBoolean("$XLII_stellarMirrorComplete")) return Stage.COMPLETED;
        if (g.getBoolean(XLII_MirrorAssault.MEM_ALL_DESTROYED)) return Stage.RETURN_TO_FALLER;
        return Stage.DESTROY_ARRAYS;
    }

    @Override
    public String getBaseName() {
        return "A Debt From the First Fleet";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_disruption_found");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add(Tags.INTEL_IMPORTANT);
        return tags;
    }

    /**
     * No manual portrait/crest block needed - the automatic one applies. {@code XLII_SetFallerActive}
     * (rules.csv, the bar accept row) sets Faller as the bar's active person immediately before
     * {@code BeginMission} fires, so {@code personOverride} resolves to him and
     * {@code BaseHubMission.createSmallDescription()} renders his portrait and the pirate crest
     * right after this class' own overview text. Same arrangement as {@code WasteNotMission}/Walt
     * Hollis; see {@code .claude/systems/intel.md}.
     */
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(QUEST_OVERVIEW, Misc.getTextColor(), 0f);
        super.createSmallDescription(info, width, height);
        addDeleteButton(info, width, "Delete entry");
    }

    @Override
    protected void createDeleteConfirmationPrompt(TooltipMakerAPI prompt) {
        prompt.addPara("Deleting this entry removes the tracker only - any arrays already "
                        + "destroyed stay destroyed, and the questline itself can still be "
                        + "completed. It cannot be restored.",
                Misc.getTextColor(), 0f);
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == null) return false;
        switch ((Stage) currentStage) {
            case DESTROY_ARRAYS:
                info.addPara("Destroy the three Stellar Arrays (" + getArraysDestroyedCount() + " of 3)", tc, pad);
                return true;
            case RETURN_TO_FALLER:
                info.addPara("Return to Ring-Port and tell Faller it's done", tc, pad);
                return true;
        }
        return false;
    }

    /**
     * Returning null for every non-terminal stage falls the base class through to
     * {@link #addDescriptionForNonEndStage} as normal. The COMPLETED string exists so the quest's
     * actual consequence is stated somewhere the player will read it - the Hegemony shift is
     * otherwise a number between two factions the player has no panel for.
     */
    @Override
    public String getStageDescriptionText() {
        if (currentStage != Stage.COMPLETED) return null;
        return "The Rift is down and Faller has been paid out of what the Hegemony gave him for "
                + "the survey returns. Fafnir is an open approach for the first time in three "
                + "hundred years, and the Hegemony holds the charts for it. Whatever standing the "
                + "Alliance still had in Chicomoztoc is gone with the wall that kept it safe.";
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage == null) return;
        float opad = 10f;
        switch ((Stage) currentStage) {
            case DESTROY_ARRAYS:
                info.addPara("Each array is guarded. Breaking the guard does not break the array - "
                        + "the collector spine has to be demolished by a ground team, and killing "
                        + "the guard tells the Alliance which array to reinforce. Marines should "
                        + "be aboard before the shooting starts, not fetched afterward.", opad);
                info.addPara("Any order. A cleared array stays open to demolition for %s days "
                                + "before a fresh guard replaces the one you killed.", 5f,
                        Misc.getHighlightColor(), "" + (int) XLII_MirrorAssault.RAID_WINDOW_DAYS);
                info.addPara(getArraysDestroyedCount() + " of 3 arrays destroyed.", 5f);
                break;
            case RETURN_TO_FALLER:
                info.addPara("All three arrays are down and the Rift is already collapsing. "
                        + "Faller's waiting at Ring-Port's bar for word of it.", opad);
                break;
        }
    }

    private int getArraysDestroyedCount() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        int count = 0;
        for (String key : XLII_MirrorAssault.KEYS) {
            if (mem.getBoolean(XLII_MirrorAssault.destroyedFlag(key))) count++;
        }
        return count;
    }
}
