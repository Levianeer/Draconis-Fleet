package levianeer.draconis.data.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.ids.Factions;

import java.util.Random;

public class fsdf_HighCommand extends BaseIndustry implements RouteFleetSpawner, FleetEventListener {

    private static final float OFFICER_PROB = 5f;
    private static final float DEFENSE_BONUS = 4f;

    private final IntervalUtil tracker = new IntervalUtil(
            Global.getSettings().getFloat("averagePatrolSpawnInterval") * 0.7f,
            Global.getSettings().getFloat("averagePatrolSpawnInterval") * 1.3f);

    @Override
    public boolean isHidden() {
        return !market.getFactionId().equals(Factions.DRACONIS);
    }

    @Override
    public boolean isFunctional() {
        return super.isFunctional() && market.getFactionId().equals(Factions.DRACONIS);
    }

    @Override
    public void apply() {
        int size = market.getSize();
        boolean isPatrol = getSpec().hasTag(Industries.TAG_PATROL);
        boolean isMilitary = getSpec().hasTag(Industries.TAG_MILITARY);
        boolean isCommand = getSpec().hasTag(Industries.TAG_COMMAND);

        super.apply(!isPatrol);
        if (isPatrol) applyIncomeAndUpkeep(3);

        int extraDemand = isPatrol ? 0 : (isMilitary ? 2 : (isCommand ? 3 : 0));

        int light, medium = 0, heavy = 0;
        if (isPatrol) {
            light = 2;
        } else {
            light = 2 + (size >= 6 ? 1 : 0);
            medium = Math.max(0, size / 2 - 1);
            heavy = Math.max(0, medium - 1);
            if (isCommand) { medium++; heavy++; }
        }

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(getModId(), light);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(getModId(), medium);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).modifyFlat(getModId(), heavy);

        demand(Commodities.SUPPLIES, size - 1 + extraDemand);
        demand(Commodities.FUEL, size - 1 + extraDemand);
        demand(Commodities.SHIPS, size - 1 + extraDemand);
        supply(Commodities.CREW, size);
        if (!isPatrol) supply(Commodities.MARINES, size);

        modifyStabilityWithBaseMod();

        float mult = getDeficitMult(Commodities.SUPPLIES);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(getModId(), 1f + DEFENSE_BONUS * mult, getSpec().getName());

        Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.MARKET_PATROL, getModId(), true, -1);
        if (isMilitary || isCommand) {
            Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.MARKET_MILITARY, getModId(), true, -1);
        }

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(0), OFFICER_PROB);

        if (!isFunctional()) { supply.clear(); unapply(); }
    }

    @Override
    public void unapply() {
        super.unapply();
        Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.MARKET_PATROL, getModId(), false, -1);
        Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.MARKET_MILITARY, getModId(), false, -1);
        unmodifyStabilityWithBaseMod();

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId());
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId(0));
    }

    @Override
    protected int getBaseStabilityMod() {
        return getSpec().hasTag(Industries.TAG_PATROL) ? 1 : 2;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (!isFunctional() || Global.getSector().getEconomy().isSimMode()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        float spawnRate = market.getStats().getDynamic().getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).getModifiedValue();
        tracker.advance(days * spawnRate);

        if (tracker.intervalElapsed()) {
            WeightedRandomPicker<PatrolType> picker = new WeightedRandomPicker<>();
            for (PatrolType type : PatrolType.values()) {
                int count = getCount();
                int max = getMaxPatrols(type);
                if (count < max) picker.add(type, max - count);
            }
            if (!picker.isEmpty()) spawnPatrol(picker.pick());
        }
    }

    private void spawnPatrol(PatrolType type) {
        OptionalFleetData extra = new OptionalFleetData(market);
        extra.fleetType = type.getFleetType();
        RouteData route = RouteManager.getInstance().addRoute(getRouteSourceId(), market, Misc.genRandomSeed(), extra, this, new Object());
        extra.strength = (float) getPatrolCombatFP(type, route.getRandom());
        route.addSegment(new RouteSegment(35f + (float) Math.random() * 10f, market.getPrimaryEntity()));
    }

    public static int getPatrolCombatFP(PatrolType type, Random random) {
        return switch (type) {
            case FAST -> Math.round(3 + random.nextFloat() * 2) * 5;
            case COMBAT -> Math.round(6 + random.nextFloat() * 3) * 5;
            case HEAVY -> Math.round(10 + random.nextFloat() * 5) * 5;
        };
    }

    private int getCount() {
        return (int) RouteManager.getInstance().getRoutesForSource(getRouteSourceId()).stream()
                .filter(r -> r.getCustom() != null) // dummy match
                .count();
    }

    private int getMaxPatrols(PatrolType type) {
        return switch (type) {
            case FAST -> (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).computeEffective(0);
            case COMBAT -> (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).computeEffective(0);
            case HEAVY -> (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).computeEffective(0);
        };
    }

    private String getRouteSourceId() {
        return market.getId() + "_military";
    }

    @Override
    public boolean isAvailableToBuild() {
        return market.getIndustries().stream().anyMatch(ind -> ind.getSpec().hasTag(Industries.TAG_SPACEPORT) && ind.isFunctional());
    }

    @Override
    public String getUnavailableReason() {
        return "Requires a functional spaceport";
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {

    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        return null;
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
        return false;
    }

    @Override
    public boolean shouldRepeat(RouteData route) {
        return false;
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteData route) {

    }
}