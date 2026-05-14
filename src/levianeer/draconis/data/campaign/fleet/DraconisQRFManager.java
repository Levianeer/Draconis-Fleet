package levianeer.draconis.data.campaign.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase.PatrolFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import levianeer.draconis.data.campaign.econ.XLII_HighCommand;
import levianeer.draconis.data.campaign.econ.conditions.DraconManager;
import levianeer.draconis.data.campaign.ids.Factions;

import java.util.*;

/**
 * Global EveryFrameScript that owns all QRF fleet assignment logic.
 * <p>
 * Implements a two-tier posting system (forward posts at jump points/gates, rear posts at markets)
 * with proportional threat response, busy-fleet awareness, and adaptive tick rate.
 * Fleets at different posts naturally converge on threats from multiple angles.
 * <p>
 * Threat awareness is persistent: a threat spotted by any DDA or FortySecond fleet is remembered
 * for up to ~10 days after it leaves sensor range (DATALINK). Fleets already in sensor range
 * of hostiles are treated as "busy" and not reassigned — Starsector's fleet AI handles them.
 */
public class DraconisQRFManager implements EveryFrameScript {

    private enum QRFState { IDLE, PATROL, APPROACH, INTERCEPT, DEFEND, REFIT }

    private static class AssignmentRecord {
        final QRFState state;
        final String targetId;
        AssignmentRecord(QRFState state, String targetId) {
            this.state = state;
            this.targetId = targetId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AssignmentRecord r)) return false;
            return state == r.state && Objects.equals(targetId, r.targetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, targetId);
        }
    }

    private static class ThreatEntry {
        final CampaignFleetAPI fleet;
        int ticksUnseen;
        float lastKnownStrength;

        ThreatEntry(CampaignFleetAPI fleet, float strength) {
            this.fleet = fleet;
            this.ticksUnseen = 0;
            this.lastKnownStrength = strength;
        }
    }

    private static final int THREAT_TTL_TICKS = 25;
    private static final float REFIT_FP_THRESHOLD = 0.8f;
    private static final float REFIT_CYCLE_DAYS = 75f;
    /** Don't assign more than 1.5x the threat's strength to a single target. */
    private static final float OVERKILL_CAP_MULT = 1.5f;
    /** Fleets below this FP fraction are posted to rear (reserves) instead of forward. */
    private static final float RESERVE_FP_THRESHOLD = 0.9f;

    private final IntervalUtil normalInterval = new IntervalUtil(0.3f, 0.5f);
    private final IntervalUtil alertInterval = new IntervalUtil(0.1f, 0.2f);
    private final IntervalUtil forceRefreshInterval = new IntervalUtil(4f, 6f);
    private final Map<Long, AssignmentRecord> lastAssignment = new HashMap<>();
    private final Map<Long, Float> daysActive = new HashMap<>();
    private final Map<LocationAPI, Map<String, ThreatEntry>> knownThreats = new HashMap<>();
    private final Random patrolRandom = new Random();

    private float elapsedSinceLastCheck = 0f;
    private boolean alertMode = false;

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        if (Global.getSector().getEconomy().isSimMode()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        forceRefreshInterval.advance(days);
        if (forceRefreshInterval.intervalElapsed()) lastAssignment.clear();

        elapsedSinceLastCheck += days;
        IntervalUtil activeInterval = alertMode ? alertInterval : normalInterval;
        activeInterval.advance(days);
        if (!activeInterval.intervalElapsed()) return;

        float tickElapsed = elapsedSinceLastCheck;
        elapsedSinceLastCheck = 0f;

        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();

        // Collect all live route timestamps to evict orphaned daysActive entries
        Set<Long> allActiveKeys = new HashSet<>();
        for (MarketAPI market : allMarkets) {
            Industry ind = market.getIndustry("XLII_highcommand");
            if (!(ind instanceof XLII_HighCommand hq) || !hq.isFunctional()) continue;
            for (RouteData route : RouteManager.getInstance().getRoutesForSource(hq.getRouteSourceId())) {
                CampaignFleetAPI fleet = route.getActiveFleet();
                if (fleet != null && !fleet.isDespawning()) allActiveKeys.add(route.getTimestamp());
            }
        }
        daysActive.keySet().retainAll(allActiveKeys);

        // Update alert mode based on whether any threats exist across all systems
        alertMode = false;
        for (MarketAPI market : allMarkets) {
            Industry ind = market.getIndustry("XLII_highcommand");
            if (!(ind instanceof XLII_HighCommand hq)) continue;
            if (!hq.isFunctional()) continue;
            processMarket(hq, market, allMarkets, tickElapsed);
        }
    }

    private void processMarket(XLII_HighCommand hq, MarketAPI market, List<MarketAPI> allMarkets, float tickElapsed) {
        List<CampaignFleetAPI> qrfFleets = new ArrayList<>();
        Map<CampaignFleetAPI, RouteData> fleetRoutes = new LinkedHashMap<>();

        for (RouteData route : RouteManager.getInstance().getRoutesForSource(hq.getRouteSourceId())) {
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null || fleet.isDespawning()) continue;
            qrfFleets.add(fleet);
            fleetRoutes.put(fleet, route);
        }
        if (qrfFleets.isEmpty()) return;

        for (RouteData route : fleetRoutes.values()) daysActive.merge(route.getTimestamp(), tickElapsed, Float::sum);

        LocationAPI loc = qrfFleets.get(0).getContainingLocation();
        List<ThreatEntry> threats = updateKnownThreats(loc);

        // Set alert mode if any threats exist in this system
        if (!threats.isEmpty()) alertMode = true;

        boolean hasRaider = threats.stream()
                .anyMatch(e -> e.fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER));

        // Partition fleets into available vs busy (in sensor range of hostile / in combat)
        List<CampaignFleetAPI> availableFleets = new ArrayList<>();
        Map<CampaignFleetAPI, ThreatEntry> busyFleetThreats = new LinkedHashMap<>();
        partitionFleets(qrfFleets, threats, availableFleets, busyFleetThreats);

        // Count busy fleet strength toward threat coverage
        Map<ThreatEntry, Float> preCoverage = new HashMap<>();
        for (var entry : busyFleetThreats.entrySet()) {
            ThreatEntry threat = entry.getValue();
            if (threat != null) {
                preCoverage.merge(threat, entry.getKey().getEffectiveStrength(), Float::sum);
            }
        }

        Map<CampaignFleetAPI, ThreatEntry> assignment = buildThreatAssignments(threats, availableFleets, hasRaider, preCoverage);

        int draconLevel = getDraconLevel();
        PostLists posts = collectPosts(loc, market, allMarkets);
        Map<CampaignFleetAPI, SectorEntityToken> postAssignments = buildPostAssignments(loc, availableFleets, market, allMarkets, fleetRoutes);

        for (CampaignFleetAPI qrf : qrfFleets) {
            long key = fleetRoutes.get(qrf).getTimestamp();

            // Busy fleets keep their current orders — don't interfere with fleet AI
            if (busyFleetThreats.containsKey(qrf)) continue;

            ThreatEntry threatEntry = assignment.get(qrf);

            QRFState state;
            String targetId;
            SectorEntityToken anchor;

            RouteData route = fleetRoutes.get(qrf);
            boolean needsRefit = (route.getCustom() instanceof PatrolFleetData custom
                    && custom.spawnFP > 0
                    && (float) qrf.getFleetPoints() / custom.spawnFP < REFIT_FP_THRESHOLD)
                    || daysActive.getOrDefault(key, 0f) >= REFIT_CYCLE_DAYS;

            if (needsRefit) {
                state = QRFState.REFIT;
                targetId = null;
                anchor = market.getPrimaryEntity();
            } else if (hasRaider) {
                state = QRFState.DEFEND;
                targetId = null;
                anchor = market.getPrimaryEntity();
            } else if (threatEntry != null) {
                boolean inDirectRange = threatEntry.fleet.isVisibleToSensorsOf(qrf);
                state = inDirectRange ? QRFState.INTERCEPT : QRFState.APPROACH;
                targetId = threatEntry.fleet.getId();
                anchor = market.getPrimaryEntity();
            } else if (draconLevel <= 2) {
                state = QRFState.PATROL;
                List<SectorEntityToken> allPosts = posts.all();
                if (!allPosts.isEmpty()) {
                    anchor = allPosts.get(patrolRandom.nextInt(allPosts.size()));
                } else {
                    anchor = market.getPrimaryEntity();
                }
                targetId = anchor.getId();
            } else {
                state = QRFState.IDLE;
                anchor = postAssignments.getOrDefault(qrf, market.getPrimaryEntity());
                targetId = anchor.getId();
            }

            AssignmentRecord desired = new AssignmentRecord(state, targetId);
            if (!desired.equals(lastAssignment.get(key))) {
                applyAssignment(qrf, anchor, state, threatEntry != null ? threatEntry.fleet : null);
                lastAssignment.put(key, desired);
            }
        }
    }

    /**
     * Partitions QRF fleets into available (can be reassigned) and busy (in sensor range of hostile or in combat).
     * Busy fleets are mapped to the threat they are nearest to (for coverage accounting).
     */
    private void partitionFleets(List<CampaignFleetAPI> qrfFleets, List<ThreatEntry> threats,
                                 List<CampaignFleetAPI> available, Map<CampaignFleetAPI, ThreatEntry> busy) {
        for (CampaignFleetAPI qrf : qrfFleets) {
            if (isFleetBusy(qrf)) {
                // Find which threat this busy fleet is near (for coverage tracking)
                ThreatEntry nearest = null;
                for (ThreatEntry threat : threats) {
                    if (threat.fleet.isVisibleToSensorsOf(qrf)) {
                        nearest = threat;
                        break; // threats are sorted by strength desc — take the strongest visible one
                    }
                }
                busy.put(qrf, nearest);
            } else {
                available.add(qrf);
            }
        }
    }

    /** A fleet is "busy" if it is in combat or has a hostile within its sensor range (fleet AI has taken over). */
    private boolean isFleetBusy(CampaignFleetAPI qrf) {
        if (qrf.getBattle() != null) return true;
        LocationAPI loc = qrf.getContainingLocation();
        if (loc == null) return false;
        for (CampaignFleetAPI fleet : loc.getFleets()) {
            if (!fleet.getFaction().isHostileTo(Factions.DRACONIS)
                    && !fleet.getFaction().isHostileTo(Factions.FORTYSECOND)) continue;
            if (fleet.isStationMode() || fleet.isDespawning()) continue;
            if (fleet.isVisibleToSensorsOf(qrf)) return true;
        }
        return false;
    }

    /**
     * Distributes available QRF fleets against threats using greedy coverage by strength.
     * Caps coverage at {@link #OVERKILL_CAP_MULT}x threat strength to avoid dogpiling small threats.
     * Pre-existing coverage from busy fleets is subtracted from what's needed.
     */
    private Map<CampaignFleetAPI, ThreatEntry> buildThreatAssignments(
            List<ThreatEntry> threats, List<CampaignFleetAPI> qrfFleets, boolean hasRaider,
            Map<ThreatEntry, Float> preCoverage) {
        Map<CampaignFleetAPI, ThreatEntry> assignment = new LinkedHashMap<>();
        if (hasRaider || threats.isEmpty()) return assignment;

        qrfFleets.sort((a, b) -> Float.compare(b.getEffectiveStrength(), a.getEffectiveStrength()));

        for (ThreatEntry threatEntry : threats) {
            float cap = threatEntry.lastKnownStrength * OVERKILL_CAP_MULT;
            float covered = preCoverage.getOrDefault(threatEntry, 0f);
            if (covered >= cap) continue; // busy fleets already cover this threat

            for (CampaignFleetAPI qrf : qrfFleets) {
                if (assignment.containsKey(qrf)) continue;
                assignment.put(qrf, threatEntry);
                covered += qrf.getEffectiveStrength();
                if (covered >= cap) break;
            }
        }
        return assignment;
    }

    private List<ThreatEntry> updateKnownThreats(LocationAPI loc) {
        Map<String, ThreatEntry> locThreats = knownThreats.computeIfAbsent(loc, k -> new HashMap<>());

        List<CampaignFleetAPI> friendlyFleets = new ArrayList<>();
        for (CampaignFleetAPI f : loc.getFleets()) {
            String fid = f.getFaction().getId();
            if (fid.equals(Factions.DRACONIS) || fid.equals(Factions.FORTYSECOND)) {
                friendlyFleets.add(f);
            }
        }

        Set<String> visibleThisTick = new HashSet<>();
        for (CampaignFleetAPI fleet : loc.getFleets()) {
            if (!fleet.getFaction().isHostileTo(Factions.DRACONIS)
                    && !fleet.getFaction().isHostileTo(Factions.FORTYSECOND)) continue;
            if (fleet.isStationMode() || fleet.isDespawning()) continue;
            if (fleet.getFleetPoints() < 30) continue;
            if (!isDetectedByAnyFriendly(fleet, friendlyFleets)) continue;

            visibleThisTick.add(fleet.getId());
            ThreatEntry entry = locThreats.get(fleet.getId());
            if (entry == null) {
                locThreats.put(fleet.getId(), new ThreatEntry(fleet, fleet.getEffectiveStrength()));
            } else {
                entry.ticksUnseen = 0;
                entry.lastKnownStrength = fleet.getEffectiveStrength();
            }
        }

        locThreats.entrySet().removeIf(e -> {
            ThreatEntry entry = e.getValue();
            if (entry.fleet.getContainingLocation() != loc) return true;
            if (entry.fleet.isDespawning()) return true;
            if (!visibleThisTick.contains(e.getKey())) entry.ticksUnseen++;
            return entry.ticksUnseen > THREAT_TTL_TICKS;
        });

        List<ThreatEntry> result = new ArrayList<>(locThreats.values());
        result.sort((a, b) -> Float.compare(b.lastKnownStrength, a.lastKnownStrength));
        return result;
    }

    private void applyAssignment(CampaignFleetAPI qrf, SectorEntityToken anchor, QRFState state, CampaignFleetAPI threat) {
        qrf.getAI().clearAssignments();
        switch (state) {
            case INTERCEPT -> {
                qrf.getAI().addAssignment(FleetAssignment.INTERCEPT, threat, 30f, null);
                qrf.getAI().addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, anchor, Float.MAX_VALUE, null);
            }
            case APPROACH -> {
                qrf.getAI().addAssignment(FleetAssignment.GO_TO_LOCATION, threat, 30f, null);
                qrf.getAI().addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, anchor, Float.MAX_VALUE, null);
            }
            case REFIT -> qrf.getAI().addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, Float.MAX_VALUE, null);
            case PATROL -> qrf.getAI().addAssignment(FleetAssignment.GO_TO_LOCATION, anchor, 10f, null);
            default -> qrf.getAI().addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, anchor, Float.MAX_VALUE, null);
        }
    }

    private boolean isDetectedByAnyFriendly(CampaignFleetAPI target, List<CampaignFleetAPI> friendlyFleets) {
        for (CampaignFleetAPI friendly : friendlyFleets) {
            if (target.isVisibleToSensorsOf(friendly)) return true;
        }
        return false;
    }

    private static class PostLists {
        final List<SectorEntityToken> forward;
        final List<SectorEntityToken> rear;
        PostLists(List<SectorEntityToken> forward, List<SectorEntityToken> rear) {
            this.forward = forward;
            this.rear = rear;
        }
        List<SectorEntityToken> all() {
            List<SectorEntityToken> combined = new ArrayList<>(forward);
            combined.addAll(rear);
            return combined;
        }
    }

    /** Collects forward posts (jump points, gates) and rear posts (friendly markets) in a system. */
    private PostLists collectPosts(LocationAPI loc, MarketAPI homeMarket, List<MarketAPI> allMarkets) {
        List<SectorEntityToken> forwardPosts = new ArrayList<>();
        List<SectorEntityToken> rearPosts = new ArrayList<>();

        for (SectorEntityToken entity : loc.getAllEntities()) {
            if (entity instanceof JumpPointAPI) {
                forwardPosts.add(entity);
            } else if (entity.getId() != null && entity.getId().contains("gate")) {
                forwardPosts.add(entity);
            }
        }

        SectorEntityToken homeEntity = homeMarket.getPrimaryEntity();
        if (homeEntity != null) rearPosts.add(homeEntity);
        for (MarketAPI m : allMarkets) {
            if (m == homeMarket) continue;
            if (m.getContainingLocation() != loc) continue;
            if (!m.getFactionId().equals(Factions.DRACONIS) && !m.getFactionId().equals(Factions.FORTYSECOND)) continue;
            SectorEntityToken entity = m.getPrimaryEntity();
            if (entity != null && !rearPosts.contains(entity)) rearPosts.add(entity);
        }

        if (forwardPosts.isEmpty()) forwardPosts.addAll(rearPosts);
        if (rearPosts.isEmpty() && homeEntity != null) rearPosts.add(homeEntity);

        return new PostLists(forwardPosts, rearPosts);
    }

    /**
     * Builds post assignments for idle fleets using a two-tier system:
     * <p>
     * Forward posts: jump points and gates (system entry vectors).
     * Rear posts: DDA/FortySecond market entities (garrison/resupply).
     * <p>
     * Healthy fleets are assigned forward; damaged/worn fleets stay at rear as reserves.
     * Distribution is round-robin across available posts.
     */
    private Map<CampaignFleetAPI, SectorEntityToken> buildPostAssignments(
            LocationAPI loc, List<CampaignFleetAPI> availableFleets, MarketAPI homeMarket,
            List<MarketAPI> allMarkets, Map<CampaignFleetAPI, RouteData> fleetRoutes) {
        Map<CampaignFleetAPI, SectorEntityToken> posts = new LinkedHashMap<>();

        PostLists postLists = collectPosts(loc, homeMarket, allMarkets);

        // Partition available fleets into forward-eligible and reserve based on health
        List<CampaignFleetAPI> forwardEligible = new ArrayList<>();
        List<CampaignFleetAPI> reserves = new ArrayList<>();
        for (CampaignFleetAPI qrf : availableFleets) {
            RouteData route = fleetRoutes.get(qrf);
            if (route != null && route.getCustom() instanceof PatrolFleetData custom && custom.spawnFP > 0) {
                float healthFraction = (float) qrf.getFleetPoints() / custom.spawnFP;
                if (healthFraction < RESERVE_FP_THRESHOLD) {
                    reserves.add(qrf);
                    continue;
                }
            }
            forwardEligible.add(qrf);
        }

        // Assign forward-eligible fleets round-robin across forward posts
        int fwdIdx = 0;
        for (CampaignFleetAPI qrf : forwardEligible) {
            if (postLists.forward.isEmpty()) break;
            posts.put(qrf, postLists.forward.get(fwdIdx % postLists.forward.size()));
            fwdIdx++;
        }

        // Assign reserve fleets round-robin across rear posts
        int rearIdx = 0;
        for (CampaignFleetAPI qrf : reserves) {
            if (postLists.rear.isEmpty()) break;
            posts.put(qrf, postLists.rear.get(rearIdx % postLists.rear.size()));
            rearIdx++;
        }

        return posts;
    }

    private int getDraconLevel() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(DraconManager.LEVEL_KEY);
        return (stored instanceof Number n) ? n.intValue() : 5;
    }
}
