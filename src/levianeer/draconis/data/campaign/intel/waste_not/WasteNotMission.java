package levianeer.draconis.data.campaign.intel.waste_not;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission.GlobalBooleanChecker;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.rulecmd.XLII_IsWasteNotHookMarket;

import java.awt.Color;
import java.util.Set;

/**
 * Quest tracker for "Waste Not" - the prologue thread that precedes {@code ShapesOfOldMission}.
 * Walt Hollis, an independent investigator, hires the player (gated on Korrin being aboard) to
 * find out why an independent colony went dark. What's actually found - a small AIO site running
 * people as a cut-rate substitute for AI cores - is what gives Korrin, and the player, their
 * actual reason to go looking for precedent across the sector, reframing the vanilla-site tour
 * that follows from an unmotivated checklist into a deliberate search for pattern.
 * <p>
 * Created via {@code BeginMission WasteNotMission} fired from Walt's own bar hook, built on
 * vanilla's {@code AddBarEvents}/{@code AddBarEvent} rules.csv mechanism (confirmed against the
 * base game's own rules.csv - the Luddic Path and League questline bar hooks use the identical
 * pattern) rather than a hand-rolled {@code BaseBarEventCreator}/{@code BaseBarEvent} Java pair -
 * an earlier pass built it that way, modeled on {@code XLII_AIOOperativeBarEvent}, but that
 * proved unreliable in live testing and was replaced; see {@code # [WASTE NOT] Hook} in
 * rules.csv. Gated on Korrin aboard and {@code $XLII_nanoforgeQuestComplete} (Chain of Custody
 * complete - see {@code OfficeReferralMission}). Ends on {@code $global.XLII_wasteNotComplete},
 * set by Walt's report-back ({@code # [WASTE NOT] Report Back}), a second {@code AddBarEvents}
 * bookend - he has no comm-directory presence to be found at instead.
 * <p>
 * <b>Does not itself hand off to {@code ShapesOfOldMission}.</b> That mission is still created
 * the same way it already was before this arc existed - from Korrin's own tour-nudge Talk-menu
 * conversation ({@code korrin_tour_nudge} in {@code korrin_topics.csv}, {@code
 * XLII_korrin_a_tour_nudge_in}/{@code _why} in rules.csv) - only the flag that nudge topic
 * watches changed, from {@code $XLII_nanoforgeQuestComplete} to this mission's own
 * {@code $XLII_wasteNotComplete}, so the nudge (and therefore the tour) can't fire before Waste
 * Not has actually run. {@code XLII_nanoforge_deliver_success} itself was never touched - by the
 * time this arc was built, {@code BeginMission ShapesOfOldMission} had already been moved off
 * that row onto the tour-nudge conversation in an earlier session (confirmed by reading that
 * row's own notes column directly - {@code .claude/systems/longsight.md}'s quest-flow table
 * describing the handoff as living on {@code XLII_nanoforge_deliver_success} was already stale
 * before this session).
 * <p>
 * Stage transitions use {@code setStageOnCustomCondition}, never {@code setStageOnGlobalFlag} -
 * see {@code .claude/systems/intel.md} for why the latter silently unsets every flag it watches
 * the instant the mission reaches {@code Stage.COMPLETED}.
 */
public class WasteNotMission extends HubMissionWithBarEvent {

    public enum Stage {
        /** Search the marked system for the colony. */
        SEARCH_SYSTEM,
        /** The colony's manifest points to a second site in-system - go resolve it, either branch. */
        INSTALLATION,
        /** Installation resolved (either branch) and Korrin has reacted - report back to Walt. */
        REPORT_BACK,
        /** Reported back; hands off into Shapes of Old. */
        COMPLETED,
    }

    /** Sector-memory key holding the colony planet's own (engine-generated) entity id. */
    public static final String MEM_COLONY_PLANET_ID = "$XLII_wasteNotColonyPlanetId";
    /** Sector-memory key holding the installation planet's own (engine-generated) entity id. */
    public static final String MEM_INSTALLATION_PLANET_ID = "$XLII_wasteNotInstallationPlanetId";

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$XLII_wasteNot_missionRef")) return false;

        if (!Global.getSector().getMemoryWithoutUpdate().contains(MEM_COLONY_PLANET_ID)) {
            if (!pickPlanets()) return false;
        }

        setStartingStage(determineStartingStage());
        setSuccessStage(Stage.COMPLETED);
        setNoAbandon();
        setStoryMission();
        setImportant(true);

        setStageOnCustomCondition(Stage.INSTALLATION, new GlobalBooleanChecker("$XLII_wasteNotManifestFound"));
        setStageOnCustomCondition(Stage.REPORT_BACK,  new GlobalBooleanChecker("$XLII_wasteNotInstallationResolved"));
        setStageOnCustomCondition(Stage.COMPLETED,    new GlobalBooleanChecker("$XLII_wasteNotComplete"));

        addStageMarkers();

        return true;
    }

    /**
     * Picks two distinct, unpopulated planets in the same system - one for the colony, one for
     * the installation - the same way vanilla's own search-type missions anchor content directly
     * to a real body instead of spawning something nearby. Modeled on
     * {@code GADataFromRuins.create()} (the Galatia Academy "retrieve data from ruins" mission),
     * which calls {@code requirePlanetUnpopulated()}/{@code pickPlanet()} and then
     * {@code makeImportant(planet, ...)} directly on the planet itself - no custom entity, no
     * separate station. {@code requirePlanetWithRuins()} (also used there) was deliberately
     * <i>not</i> copied: that tags a world with vanilla's own ancient-Domain-ruins flavor, which
     * would collide with this arc's own "abandoned two months ago" framing rather than support it.
     * <p>
     * <b>Rebuilt after live testing, twice.</b> The first version spawned a custom station entity
     * in orbit of a picked planet; the second version spawned two custom stations orbiting the
     * *same* planet at different radii. Both read as arbitrary floating points with nothing
     * tying them to an actual place - confirmed by the author directly. This version drops the
     * custom entities entirely: the planets themselves are the interaction targets (see
     * {@code XLII_CampaignPlugin.pickInteractionDialogPlugin}, keyed off the ids stored here),
     * exactly the way vanilla's own ruins/derelict missions do it. {@code custom_entities.json}'s
     * now-unused {@code XLII_waste_not_colony}/{@code XLII_waste_not_installation} entries were
     * removed along with this.
     * <p>
     * Deliberately calls {@code pickPlanet()} alone, not {@code pickSystem()} first:
     * {@code pickPlanet()} re-runs its own system search internally against whatever
     * {@code requireSystem*}/{@code preferSystem*} filters are set, and the default
     * {@code resetSearch=true} on {@code pickSystem()} would clear those filters before a second
     * call could reuse them. Confirmed by reading {@code HubMissionWithSearch.pickPlanet()}'s
     * source directly, not assumed.
     */
    private boolean pickPlanets() {
        requireSystemNotAlreadyUsedForStory();
        requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_CORE);
        requireSystemHasNumPlanets(2);
        preferSystemOnFringeOfSector();
        requirePlanetNotStar();
        requirePlanetUnpopulated();

        PlanetAPI colonyPlanet = pickPlanet();
        if (colonyPlanet == null) return false;

        requireSystemIs(colonyPlanet.getStarSystem());
        requirePlanetNotStar();
        requirePlanetUnpopulated();
        requirePlanetNot(colonyPlanet);

        PlanetAPI installationPlanet = pickPlanet();
        if (installationPlanet == null) return false;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(MEM_COLONY_PLANET_ID, colonyPlanet.getId());
        mem.set(MEM_INSTALLATION_PLANET_ID, installationPlanet.getId());
        return true;
    }

    private void addStageMarkers() {
        String colonyId = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLONY_PLANET_ID);
        SectorEntityToken colony = colonyId == null ? null : Global.getSector().getEntityById(colonyId);
        if (colony != null) {
            makeImportant(colony, null, Stage.SEARCH_SYSTEM);
        }
    }

    /**
     * Not serialised: forces {@link #syncInstallationMarker} to run once per game load, same
     * pattern as {@code OfficeReferralMission.bastionMarkerSynced} - the installation planet
     * already exists (it's a real body, picked by {@link #pickPlanets}), but shouldn't be
     * pointed to as "important" until the colony scene's manifest beat actually reveals it.
     */
    private transient boolean installationMarkerSynced = false;

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        syncInstallationMarker();
        syncReportBackMarker();
    }

    @Override
    protected void updateInteractionDataImpl() {
        syncInstallationMarker();
        syncReportBackMarker();
    }

    private void syncInstallationMarker() {
        if (installationMarkerSynced) return;
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean("$XLII_wasteNotManifestFound")) return;
        // Bug fix: without this, a save reloaded after the mission has already moved past
        // INSTALLATION (REPORT_BACK/COMPLETED) would still register a fresh ImportanceData entry
        // for this stage on every load (installationMarkerSynced resets to false each load, by
        // design, to backfill old saves) - harmless to the live marker itself (makeImportant()'s
        // own inCurrentStage check already keeps it from actually lighting up again here), but a
        // dead entry accumulates in that stage's own importance list every single load for the
        // rest of the save's life. Once the mission is past this stage, there's nothing left to
        // backfill - skip entirely instead of registering something that will never be visited
        // again by setCurrentStage()'s own cleanup pass.
        if (currentStage == Stage.REPORT_BACK || currentStage == Stage.COMPLETED) {
            installationMarkerSynced = true;
            return;
        }
        if (currentStage != Stage.INSTALLATION) {
            // Bug fix: the manifest flag is set from rules.csv before BaseHubMission's own
            // periodic tracker re-evaluates setStageOnCustomCondition() and advances currentStage
            // to INSTALLATION, so this can run while currentStage is still SEARCH_SYSTEM. Don't
            // latch installationMarkerSynced here - retry on the next tick instead, or the marker
            // never gets registered for the rest of the session.
            return;
        }
        installationMarkerSynced = true;

        String installationId = Global.getSector().getMemoryWithoutUpdate().getString(MEM_INSTALLATION_PLANET_ID);
        SectorEntityToken installation = installationId == null ? null : Global.getSector().getEntityById(installationId);
        if (installation != null) {
            makeImportant(installation, null, Stage.INSTALLATION);
        }
    }

    /**
     * Not serialised: same once-per-load pattern as {@link #installationMarkerSynced}. Walt has no
     * fixed home market the way {@code OfficeReferralMission}'s August does - he's locked to
     * whichever bar he first turned up at (see {@link XLII_IsWasteNotHookMarket}) - so the
     * REPORT_BACK marker has to resolve that locked market id rather than point at a hardcoded one.
     */
    private transient boolean reportBackMarkerSynced = false;

    /**
     * Registers Walt's locked bar market as REPORT_BACK's objective marker/map location, same
     * stage-race fix as {@link #syncInstallationMarker}: only latch {@link #reportBackMarkerSynced}
     * once the mission has actually reached REPORT_BACK (or moved past it, to skip stale backfill
     * once COMPLETED), not on the frame the resolved-installation flag fires but before
     * {@code BaseHubMission}'s periodic tracker has advanced {@code currentStage} to match.
     */
    private void syncReportBackMarker() {
        if (reportBackMarkerSynced) return;
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean("$XLII_wasteNotInstallationResolved")) return;
        if (currentStage == Stage.COMPLETED) {
            reportBackMarkerSynced = true;
            return;
        }
        if (currentStage != Stage.REPORT_BACK) {
            return;
        }
        reportBackMarkerSynced = true;

        MarketAPI market = resolveWaltMarket();
        if (market != null) {
            makeImportant(market, null, Stage.REPORT_BACK);
        }
    }

    private MarketAPI resolveWaltMarket() {
        String marketId = Global.getSector().getMemoryWithoutUpdate().getString(XLII_IsWasteNotHookMarket.MEM_KEY);
        if (marketId == null || marketId.isEmpty()) return null;
        return Global.getSector().getEconomy().getMarket(marketId);
    }

    /**
     * Bug fix (author report): the colony/installation map markers could survive the quest ending.
     * Belt-and-suspenders on top of vanilla's own per-stage {@code setCurrentStage()} cleanup
     * (which unmarks a stage's own {@code makeImportant} entries the moment the mission transitions
     * off of it) - mirrors the identical pattern already established for this exact class of bug in
     * {@code OfficeReferralMission.clearStageMarkers()}, called from both the mission's own natural
     * end (success) and the manual "Delete entry" path, so neither route can leave either planet
     * stuck marked important regardless of which one actually needs it.
     */
    private void clearStageMarkers() {
        String colonyId = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLONY_PLANET_ID);
        SectorEntityToken colony = colonyId == null ? null : Global.getSector().getEntityById(colonyId);
        if (colony != null) makeUnimportant(colony);

        String installationId = Global.getSector().getMemoryWithoutUpdate().getString(MEM_INSTALLATION_PLANET_ID);
        SectorEntityToken installation = installationId == null ? null : Global.getSector().getEntityById(installationId);
        if (installation != null) makeUnimportant(installation);

        MarketAPI waltMarket = resolveWaltMarket();
        if (waltMarket != null) makeUnimportant(waltMarket);
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        clearStageMarkers();
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_DELETE) {
            clearStageMarkers();
        }
        super.buttonPressConfirmed(buttonId, ui);
    }

    private Stage determineStartingStage() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        if (g.getBoolean("$XLII_wasteNotComplete"))               return Stage.COMPLETED;
        if (g.getBoolean("$XLII_wasteNotInstallationResolved"))   return Stage.REPORT_BACK;
        if (g.getBoolean("$XLII_wasteNotManifestFound"))          return Stage.INSTALLATION;
        return Stage.SEARCH_SYSTEM;
    }

    @Override
    public String getBaseName() {
        return "Waste Not";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_smuggling");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add(Tags.INTEL_IMPORTANT);
        tags.add(Tags.INTEL_STORY);
        return tags;
    }

    @Override
    public String getPostfixForState() {
        if (startingStage != null) return "";
        return super.getPostfixForState();
    }

    // =========================================================================
    // Delete entry
    // =========================================================================

    private static final String QUEST_OVERVIEW =
            "Walt Hollis, an independent investigator, paid for a straight answer about a colony "
                    + "that stopped talking. Whatever's actually out there, it's bigger than the "
                    + "job he hired for.";

    /**
     * No manual portrait/crest block needed - unlike {@code ShapesOfOldMission}'s Korrin-specific
     * workaround, this one gets the automatic block for free. {@code XLII_WasteNotSetWaltActive}
     * (rules.csv, the hook's accept row) sets Walt - a randomly-generated, {@code
     * Factions.INDEPENDENT} {@code PersonAPI}, created once and cached via {@code
     * ImportantPeopleAPI} - as the bar's active person immediately before {@code BeginMission
     * WasteNotMission} fires, so {@code personOverride} resolves to him and {@code
     * BaseHubMission.createSmallDescription()} renders his portrait and the Independent crest
     * automatically, right after this class' own overview text - matching the other three
     * trackers in this chain, all of which get the same block for free from a live conversation
     * with August. He stays text-only in the live bar scene itself; this only affects the mission
     * panel.
     */
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(QUEST_OVERVIEW, Misc.getTextColor(), 0f);
        super.createSmallDescription(info, width, height);
        addDeleteButton(info, width, "Delete entry");
    }

    @Override
    protected void createDeleteConfirmationPrompt(TooltipMakerAPI prompt) {
        prompt.addPara("Deleting this entry removes the tracker only - the questline itself is "
                        + "unaffected and can still be completed. It cannot be restored.",
                Misc.getTextColor(), 0f);
    }

    // =========================================================================
    // Player-facing guidance
    // =========================================================================

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == null) return false;
        switch ((Stage) currentStage) {
            case SEARCH_SYSTEM:
                info.addPara("Search the system Walt pointed you to", tc, pad);
                return true;
            case INSTALLATION:
                info.addPara("Resolve the second site", tc, pad);
                return true;
            case REPORT_BACK:
                info.addPara("Report back to Walt Hollis", tc, pad);
                return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage == null) return;
        float opad = 10f;
        switch ((Stage) currentStage) {
            case SEARCH_SYSTEM:
                info.addPara("Walt paid for a look into a colony that stopped answering calls. "
                        + "No distress signal, no wreckage reported - just silence. Search the "
                        + "marked system to find it.", opad);
                break;
            case INSTALLATION:
                info.addPara("The colony's own manifest points to an unregistered site elsewhere "
                        + "in the system. How you approach it is your call to make.", opad);
                break;
            case REPORT_BACK:
                info.addPara("Whatever was found, Walt is still waiting for a straight answer. "
                        + "He said he'd pay for the truth, whatever it turned out to be.", opad);
                break;
        }
    }
}
