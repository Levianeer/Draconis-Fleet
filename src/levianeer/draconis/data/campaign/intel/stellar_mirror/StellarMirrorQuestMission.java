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
            "Faller flew for Athebyne's First Fleet, and has spent thirty cycles at Ring-Port "
                    + "looking at the same wall on the same chart. He wants all three Stellar "
                    + "Arrays destroyed and the Fafnir Rift gone with them - the Rift is the only "
                    + "reason the Alliance has never had to defend this approach against anyone "
                    + "who mattered.";

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
                info.addPara("Destroy the three Stellar Arrays in Fafnir ("
                        + getArraysDestroyedCount() + " of 3)", tc, pad);
                return true;
            case RETURN_TO_FALLER:
                info.addPara("Return to Faller at Ring-Port", tc, pad);
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
        return "All three arrays are dead, the Rift is receding, and First Fleet has paid you "
                + "out of everything it had left. Faller's own price was never the credits - the "
                + "survey returns went to the Hegemony, who have wanted a reason to come through "
                + "Fafnir for a century and now hold the charts for it. Whatever understanding "
                + "the Alliance had in Chicomoztoc is gone with the wall that kept it safe.";
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage == null) return;
        float opad = 10f;
        switch ((Stage) currentStage) {
            case DESTROY_ARRAYS:
                info.addPara("The three arrays can be taken in any order. Each is held by a guard "
                        + "detachment, and killing that detachment does not touch the array "
                        + "itself - the collector spine sits deep in the structure and has to be "
                        + "cut out by a ground team standing on it.", opad);
                info.addPara("Carry the marines out with you. A dead guard tells the Alliance "
                        + "which array to reinforce, and there is no time to go back to a market "
                        + "for a ground team afterward.", 5f);
                info.addPara("Once an array's guard is down you have %s days to get the ground "
                                + "team aboard and finish the job. After that a fresh detachment "
                                + "is on station and has to be fought again before you get "
                                + "another shot at it.", 5f,
                        Misc.getHighlightColor(), "" + (int) XLII_MirrorAssault.RAID_WINDOW_DAYS);
                break;
            case RETURN_TO_FALLER:
                info.addPara("The third array is down and the Rift is already receding off the "
                        + "hyperspace plot. Faller has not left Ring-Port's bar in thirty cycles "
                        + "and is not going to start now.", opad);
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
