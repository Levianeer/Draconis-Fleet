package levianeer.draconis.data.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase.PatrolFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidDangerLevel;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import levianeer.draconis.data.campaign.ids.Factions;

import java.util.Random;

public class XLII_HighCommand extends BaseIndustry implements RouteFleetSpawner, FleetEventListener {

    @Override
    public boolean isHidden() {
        return !market.getFactionId().equals(Factions.DRACONIS);
    }

    @Override
    public boolean isFunctional() {
        return super.isFunctional() && market.getFactionId().equals(Factions.DRACONIS);
    }

    public void apply() {
        super.apply(true);

        int size = market.getSize();

        demand(Commodities.SUPPLIES, size - 1);
        demand(Commodities.FUEL, size - 1);
        demand(Commodities.SHIPS, size - 1);

        supply(Commodities.CREW, size);

        demand(Commodities.HAND_WEAPONS, size);
        supply(Commodities.MARINES, size);

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.HAND_WEAPONS);
        applyDeficitToProduction(1, deficit, Commodities.MARINES);

        modifyStabilityWithBaseMod();

        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyFlat(getModId(), 500, getNameForModifier());

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, MemFlags.MARKET_PATROL, getModId(), true, -1);
        Misc.setFlagWithReason(memory, MemFlags.MARKET_MILITARY, getModId(), true, -1);

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }

    }

    @Override
    public void unapply() {
        super.unapply();

        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId());

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, MemFlags.MARKET_PATROL, getModId(), false, -1);
        Misc.setFlagWithReason(memory, MemFlags.MARKET_MILITARY, getModId(), false, -1);

        unmodifyStabilityWithBaseMod();
    }

    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        return mode != IndustryTooltipMode.NORMAL || isFunctional();
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            addStabilityPostDemandSection(tooltip, hasDemand, mode);
        }
    }

    @Override
    protected int getBaseStabilityMod() {
        return 2;
    }

    public String getNameForModifier() {
        if (getSpec().getName().contains("HQ")) {
            return getSpec().getName();
        }
        return Misc.ucFirst(getSpec().getName());
    }

    @Override
    protected Pair<String, Integer> getStabilityAffectingDeficit() {
        return getMaxDeficit(Commodities.SUPPLIES, Commodities.FUEL, Commodities.SHIPS, Commodities.HAND_WEAPONS);
    }

    @Override
    public String getCurrentImage() {
        return super.getCurrentImage();
    }


    public boolean isDemandLegal(CommodityOnMarketAPI com) {
        return true;
    }

    public boolean isSupplyLegal(CommodityOnMarketAPI com) {
        return true;
    }

    protected IntervalUtil tracker = new IntervalUtil(Global.getSettings().getFloat("averagePatrolSpawnInterval") * 0.7f,
            Global.getSettings().getFloat("averagePatrolSpawnInterval") * 1.3f);

    protected float returningPatrolValue = 0f;

    @Override
    protected void buildingFinished() {
        super.buildingFinished();

        tracker.forceIntervalElapsed();
    }

    @Override
    protected void upgradeFinished(Industry previous) {
        super.upgradeFinished(previous);

        tracker.forceIntervalElapsed();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Global.getSector().getEconomy().isSimMode()) return;

        if (!isFunctional()) return;

        float days = Global.getSector().getClock().convertToDays(amount);

        float spawnRate = 1f;
        float rateMult = market.getStats().getDynamic().getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).getModifiedValue();
        spawnRate *= rateMult;


        float extraTime = 0f;
        if (returningPatrolValue > 0) {
            // apply "returned patrols" to spawn rate, at a maximum rate of 1 interval per day
            float interval = tracker.getIntervalDuration();
            extraTime = interval * days;
            returningPatrolValue -= days;
            if (returningPatrolValue < 0) returningPatrolValue = 0;
        }
        tracker.advance(days * spawnRate + extraTime);

        if (tracker.intervalElapsed()) {
            String sid = getRouteSourceId();

            int heavy = getCount(PatrolType.HEAVY);
            int maxHeavy = 1; // Number of fleets

            if (heavy >= maxHeavy) return;

            PatrolType type = PatrolType.HEAVY;
            PatrolFleetData custom = new PatrolFleetData(type);

            OptionalFleetData extra = new OptionalFleetData(market);
            extra.fleetType = type.getFleetType();

            RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, this, custom);
            float patrolDays = 35f + (float) Math.random() * 10f;

            route.addSegment(new RouteSegment(patrolDays, market.getPrimaryEntity()));
        }
    }

    public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
    }

    public boolean shouldRepeat(RouteData route) {
        return false;
    }

    public int getCount(PatrolType ... types) {
        int count = 0;
        for (RouteData data : RouteManager.getInstance().getRoutesForSource(getRouteSourceId())) {
            if (data.getCustom() instanceof PatrolFleetData custom) {
                for (PatrolType type : types) {
                    if (type == custom.type) {
                        count++;
                        break;
                    }
                }
            }
        }
        return count;
    }

    public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
        return false;
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (!isFunctional()) return;

        if (reason == FleetDespawnReason.REACHED_DESTINATION) {
            RouteData route = RouteManager.getInstance().getRoute(getRouteSourceId(), fleet);
            if (route.getCustom() instanceof PatrolFleetData custom) {
                if (custom.spawnFP > 0) {
                    float fraction  = (float) fleet.getFleetPoints() / custom.spawnFP;
                    returningPatrolValue += fraction;
                }
            }
        }
    }

    public CampaignFleetAPI spawnFleet(RouteData route) {

        PatrolFleetData custom = (PatrolFleetData) route.getCustom();
        PatrolType type = custom.type;

        Random random = route.getRandom();

        float combat = Math.round(10f + random.nextFloat() * 5f) * 20f; // Difficulty
        float tanker = Math.round(random.nextFloat()) * 10f;
        float freighter = Math.round(random.nextFloat()) * 10f;
        String fleetType = type.getFleetType();

        FleetParamsV3 params = new FleetParamsV3(
                market,
                null,         // loc in hyper; don't need if have market
                Factions.FORTYSECOND,
                null,      // quality override
                fleetType,
                combat,                 // combatPts
                freighter,              // freighterPts
                tanker,                 // tankerPts
                0f,                     // transportPts
                0f,                     // linerPts
                0f,                     // utilityPts
                0f                      // quality mod
        );
        params.officerLevelBonus = 7;       //   7 7 7
        params.quality = 7;                 //     7
        params.averageSMods = 7;            //    7
        params.timestamp = route.getTimestamp();
        params.random = random;
        Misc.getShipPickMode(market);
        params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setFaction(market.getFactionId(), true);
        fleet.setNoFactionInName(true);
        fleet.setName("XLII Battlegroup");

        fleet.addEventListener(this);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_CUSTOMS_INSPECTOR, true);

        String postId = Ranks.POST_PATROL_COMMANDER;
        String rankId = Ranks.SPACE_CAPTAIN;

        fleet.getCommander().setPostId(postId);
        fleet.getCommander().setRankId(rankId);

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isCapital()) {
                member.setVariant(member.getVariant().clone(), false, false);

                member.getVariant().setSource(VariantSource.REFIT);
                member.getVariant().addTag(Tags.TAG_NO_AUTOFIT);
                member.getVariant().addTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS);
            }
        }

        market.getContainingLocation().addEntity(fleet);
        fleet.setFacing((float) Math.random() * 360f);
        // this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));

        if (custom.spawnFP <= 0) {
            custom.spawnFP = fleet.getFleetPoints();
        }

        return fleet;
    }

    public String getRouteSourceId() {
        return getMarket().getId() + "_" + "XLII_the_42nd";
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    public boolean canImprove() {
        return false;
    }

    @Override
    public RaidDangerLevel adjustCommodityDangerLevel(String commodityId, RaidDangerLevel level) {
        return level.next();
    }

    @Override
    public RaidDangerLevel adjustItemDangerLevel(String itemId, String data, RaidDangerLevel level) {
        return level.next();
    }
}