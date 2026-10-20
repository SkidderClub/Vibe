package dev.vibe.path;

import dev.vibe.movement.WalkPathfinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.BlockPos;

/** Tick-sliced repair plus hysteretic optimization. Reuse is an incumbent, not a permanent constraint. */
public final class PathPlanner {
    private final WalkPathfinder.Terrain terrain;
    private final BlockPos start, goal;
    private final int radius, budget, verticalRange, maxDrop;
    private final boolean jumps, diagonals;
    private final boolean optimize;
    private final double stability;
    private final List<BlockPos> candidate;
    private final boolean[] standing, edges;
    private final List<List<BlockPos>> pieces = new ArrayList<>();
    private List<BlockPos> result = new ArrayList<>(), alternative = Collections.emptyList();
    private WalkPathfinder.Search search;
    private int checked, work, expanded, nextPiece, reusedNodes, alternativeReused;
    private boolean validating, fullSearch, optimizing, done, reached;

    public PathPlanner(WalkPathfinder.Terrain terrain, BlockPos start, BlockPos goal, int radius, int budget,
                       int verticalRange, boolean jumps, int maxDrop, boolean diagonals, List<BlockPos> previous) {
        this(terrain, start, goal, radius, budget, verticalRange, jumps, maxDrop, diagonals, previous, true, .08);
    }

    public PathPlanner(WalkPathfinder.Terrain terrain, BlockPos start, BlockPos goal, int radius, int budget,
                       int verticalRange, boolean jumps, int maxDrop, boolean diagonals, List<BlockPos> previous,
                       boolean optimize, double stability) {
        this.start = start; this.goal = goal; this.radius = Math.max(1, radius);
        this.budget = Math.max(1, budget); this.verticalRange = Math.max(1, verticalRange);
        this.jumps = jumps; this.maxDrop = Math.max(0, Math.min(3, maxDrop)); this.diagonals = diagonals;
        this.optimize = optimize;
        this.stability = Double.isNaN(stability) ? .08 : Math.max(0, Math.min(.25, stability));
        // Every repair remains inside the original player-centred search bounds, including diagonal sides.
        this.terrain = new WalkPathfinder.Terrain() {
            @Override public boolean canStand(BlockPos p) { return inBounds(p) && terrain.canStand(p); }
            @Override public boolean canMove(BlockPos a, BlockPos b) { return terrain.canMove(a, b); }
        };
        candidate = candidate(previous);
        standing = new boolean[candidate.size()]; edges = new boolean[candidate.size()];
        validating = !candidate.isEmpty(); fullSearch = !validating;
        result.add(start);
    }

    private boolean inBounds(BlockPos p) {
        return Math.abs(p.getX()-start.getX()) <= radius && Math.abs(p.getZ()-start.getZ()) <= radius
                && Math.abs(p.getY()-start.getY()) <= verticalRange;
    }

    private List<BlockPos> candidate(List<BlockPos> previous) {
        if (previous == null || previous.size() < 2) return Collections.emptyList();
        int first = nearest(previous, start), last = nearest(previous, goal), length = Math.abs(last-first)+1;
        // Don't spend the entire search budget validating a long old route, or chase a distant stale anchor.
        if (length < 2 || length > Math.min(512, budget / 3)
                || previous.get(first).distanceSq(start) > 16 || previous.get(last).distanceSq(goal) > 64)
            return Collections.emptyList();
        List<BlockPos> nodes = new ArrayList<>(length);
        int direction = last >= first ? 1 : -1;
        for (int i = first; ; i += direction) { nodes.add(previous.get(i)); if (i == last) break; }
        return nodes;
    }

    private static int nearest(List<BlockPos> nodes, BlockPos point) {
        int best = 0;
        for (int i = 1; i < nodes.size(); i++)
            if (nodes.get(i).distanceSq(point) < nodes.get(best).distanceSq(point)) best = i;
        return best;
    }

    /** Validation and all connector searches share the same per-tick and total work/time budgets. */
    public void advance(int maxNodes, long deadlineNanos) {
        int stop = work + Math.max(0, Math.min(maxNodes, budget-work));
        while (!done && work < stop && System.nanoTime() < deadlineNanos) {
            if (optimizing) {
                int before = search.getVisited();
                search.advance(stop-work, deadlineNanos);
                int used = search.getVisited()-before; work += used; expanded += used;
                if (search.isDone()) {
                    if (search.reachedGoal()) {
                        result = new ArrayList<>(); result.add(start); result.addAll(search.path());
                        reusedNodes = 0;
                    }
                    search = null; optimizing = false; done = true;
                }
                break;
            }
            if (validating) {
                BlockPos node = candidate.get(checked);
                standing[checked] = terrain.canStand(node);
                edges[checked] = checked > 0 && standing[checked-1] && standing[checked]
                        && WalkPathfinder.canStep(terrain, candidate.get(checked-1), node, jumps, maxDrop, diagonals);
                checked++; work++;
                if (checked == candidate.size()) { validating = false; choosePieces(); }
                continue;
            }
            BlockPos destination = nextPiece < pieces.size() ? pieces.get(nextPiece).get(0) : goal;
            BlockPos cursor = result.get(result.size()-1);
            if (cursor.equals(destination)) { finishLeg(); continue; }
            if (search == null) {
                // The wrapper enforces player-centred bounds; a connector may start at either edge of them.
                int remaining = budget-work;
                // A bad retained anchor must not consume all capacity before the fresh-search fallback.
                int legBudget = fullSearch ? remaining : Math.min(remaining, Math.max(16, remaining/2));
                search = new WalkPathfinder.Search(terrain, cursor, destination, radius*2, legBudget,
                        verticalRange*2, jumps, maxDrop, diagonals);
            }
            int before = search.getVisited();
            search.advance(stop-work, deadlineNanos);
            int used = search.getVisited()-before; work += used; expanded += used;
            if (!search.isDone()) break;
            result.addAll(search.path());
            if (search.reachedGoal()) { search = null; finishLeg(); }
            else if (!fullSearch) fallback();
            else { search = null; done = true; preferAlternative(); }
        }
        if (!done && work >= budget) {
            // A partial optimization is never a reason to replace a valid complete incumbent.
            if (search != null && !optimizing) result.addAll(search.path());
            search = null; optimizing = false; done = true;
            if (!reached) preferAlternative();
        }
    }

    private void choosePieces() {
        int prefix = 0;
        if (standing[0]) { prefix = 1; while (prefix < candidate.size() && edges[prefix]) prefix++; }
        if (prefix == candidate.size()) pieces.add(candidate);
        else {
            // Keep safe ends, but replace the whole broken middle with one bounded repair search.
            if (prefix >= 2) pieces.add(candidate.subList(0, prefix));
            int suffix = candidate.size()-1;
            if (standing[suffix]) {
                while (suffix > 0 && edges[suffix]) suffix--;
                if (candidate.size()-suffix >= 2) pieces.add(candidate.subList(suffix, candidate.size()));
            }
        }
        fullSearch = pieces.isEmpty();
    }

    private void finishLeg() {
        if (nextPiece < pieces.size()) {
            List<BlockPos> piece = pieces.get(nextPiece++);
            result.addAll(piece.subList(1, piece.size())); reusedNodes += piece.size()-1;
            if (nextPiece == pieces.size() && result.get(result.size()-1).equals(goal)) finishRoute();
        } else finishRoute();
    }

    private void finishRoute() {
        reached = done = true;
        if (!optimize || fullSearch || reusedNodes == 0 || work >= budget) return;
        result = compact(result);
        double cost = routeCost(result);
        double saving = stability == 0 ? 0 : Math.max(.5, cost*stability);
        double limit = cost-saving;
        // Already competitive with the obstacle-free lower bound: no shortcut search or world reads needed.
        if (limit <= WalkPathfinder.estimate(start, goal, diagonals)+1e-9) return;
        search = new WalkPathfinder.Search(terrain,start,goal,radius,budget-work,verticalRange,jumps,maxDrop,diagonals,limit);
        optimizing = true; done = false;
    }

    private void fallback() {
        alternative = progress(result); alternativeReused = reusedNodes;
        result = new ArrayList<>(); result.add(start);
        pieces.clear(); nextPiece = reusedNodes = 0; search = null; fullSearch = true;
    }

    private void preferAlternative() {
        if (!alternative.isEmpty() && distance(alternative.get(alternative.size()-1)) < distance(result.get(result.size()-1))) {
            result = alternative; reusedNodes = alternativeReused;
        }
    }

    private double distance(BlockPos p) {
        return WalkPathfinder.estimate(p, goal, diagonals);
    }

    public static double routeCost(List<BlockPos> nodes) {
        double cost = 0;
        for (int i=1;i<nodes.size();i++) cost += WalkPathfinder.stepCost(nodes.get(i-1),nodes.get(i));
        return cost;
    }

    private List<BlockPos> progress(List<BlockPos> nodes) {
        int best = 0;
        for (int i = 1; i < nodes.size(); i++) if (distance(nodes.get(i)) < distance(nodes.get(best))) best = i;
        return new ArrayList<>(nodes.subList(0, best+1));
    }

    /** Excludes start, and erases loops if a connector intersected a retained section. No world reads. */
    public List<BlockPos> path() {
        List<BlockPos> nodes = new ArrayList<>(result);
        if (search != null && !optimizing) nodes.addAll(search.path());
        if (!reached) {
            nodes = progress(nodes);
            if (!alternative.isEmpty() && distance(alternative.get(alternative.size()-1)) < distance(nodes.get(nodes.size()-1)))
                nodes = alternative;
        }
        List<BlockPos> compact = compact(nodes);
        return new ArrayList<>(compact.subList(1, compact.size()));
    }

    private static List<BlockPos> compact(List<BlockPos> nodes) {
        List<BlockPos> compact = new ArrayList<>();
        Map<BlockPos, Integer> indices = new HashMap<>();
        for (BlockPos node : nodes) {
            Integer index = indices.get(node);
            if (index != null) {
                while (compact.size() > index+1) indices.remove(compact.remove(compact.size()-1));
            } else { indices.put(node, compact.size()); compact.add(node); }
        }
        return compact;
    }

    public boolean isDone() { return done; }
    public boolean reachedGoal() { return reached; }
    public int getWork() { return work; }
    public int getExpanded() { return expanded; }
    public int getReusedNodes() { return reusedNodes; }
}
