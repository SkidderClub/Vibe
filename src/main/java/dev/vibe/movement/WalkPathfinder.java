package dev.vibe.movement;

import java.util.*;
import net.minecraft.util.BlockPos;

/** Bounded walking A*: solid ground, one-block jumps and at most three-block drops. */
public final class WalkPathfinder {
    public interface Terrain {
        boolean canStand(BlockPos feet);
        boolean canMove(BlockPos from, BlockPos to);
    }

    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1}};
    private static final int[][] DIAGONALS = {{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final double SQRT_TWO = Math.sqrt(2);
    private static final int[] HEIGHTS = {0,1,-1,-2,-3};

    private WalkPathfinder() { }

    /** Excludes the starting node. A partial route is allowed only if it gets closer. */
    public static List<BlockPos> find(Terrain terrain, BlockPos start, BlockPos goal, int radius, int budget) {
        Search search = new Search(terrain, start, goal, radius, budget, 8, true, 3);
        search.advance(budget, Long.MAX_VALUE);
        return search.path();
    }

    /** Resumable A*: all terrain reads remain on the caller's client thread, never the render loop. */
    public static final class Search {
        private final Terrain terrain;
        private final BlockPos start, goal;
        private final int radius, budget, verticalRange, maxDrop;
        private final boolean jumps, diagonals;
        private final double costLimit;
        // Equal-cost frontiers prefer progress, then fixed coordinates: fewer expansions and reproducible routes.
        private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.<Node>comparingDouble(n -> n.score)
                .thenComparingDouble(n -> -n.cost).thenComparingInt(n -> n.pos.getX())
                .thenComparingInt(n -> n.pos.getY()).thenComparingInt(n -> n.pos.getZ()));
        private final Map<BlockPos, Double> costs = new HashMap<>();
        private final Node first;
        private Node best;
        private int visited;
        private boolean done, reached;

        public Search(Terrain terrain, BlockPos start, BlockPos goal, int radius, int budget,
                      int verticalRange, boolean jumps, int maxDrop) {
            this(terrain, start, goal, radius, budget, verticalRange, jumps, maxDrop, false);
        }

        /** Diagonals are opt-in so existing walking controllers retain their cardinal routes. */
        public Search(Terrain terrain, BlockPos start, BlockPos goal, int radius, int budget,
                      int verticalRange, boolean jumps, int maxDrop, boolean diagonals) {
            this(terrain, start, goal, radius, budget, verticalRange, jumps, maxDrop, diagonals, Double.POSITIVE_INFINITY);
        }

        /** Optional strict cost bound: don't explore routes that cannot beat an existing valid route. */
        public Search(Terrain terrain, BlockPos start, BlockPos goal, int radius, int budget,
                      int verticalRange, boolean jumps, int maxDrop, boolean diagonals, double costLimit) {
            this.terrain = terrain; this.start = start; this.goal = goal;
            this.radius = Math.max(1, radius); this.budget = Math.max(1, budget);
            this.verticalRange = Math.max(1, verticalRange); this.jumps = jumps; this.diagonals = diagonals;
            this.maxDrop = Math.max(0, Math.min(3, maxDrop));
            this.costLimit = Double.isNaN(costLimit) ? Double.POSITIVE_INFINITY : Math.max(0, costLimit);
            first = best = new Node(start, null, 0, distance(start, goal));
            open.add(first); costs.put(start, 0D);
        }

        /** Stops at both caps; one node expansion is the smallest non-interruptible unit. */
        public void advance(int maxNodes, long deadlineNanos) {
            int stop = visited + Math.max(0, Math.min(maxNodes, budget - visited));
            while (!done && !open.isEmpty() && visited < stop && System.nanoTime() < deadlineNanos) {
                if (open.peek().score >= costLimit - 1e-9) { done = true; break; }
                Node current = open.poll(); visited++;
                if (current.cost > costs.get(current.pos)) continue;
                if (distance(current.pos, goal) < distance(best.pos, goal)) best = current;
                if (current.pos.equals(goal)) { best = current; reached = done = true; break; }
                for (int i = 0; i < (diagonals ? 8 : 4); i++) {
                    int[] direction = i < 4 ? DIRECTIONS[i] : DIAGONALS[i - 4];
                    for (int dy : HEIGHTS) {
                        if (dy > 0 && !jumps || -dy > maxDrop) continue;
                        BlockPos next = current.pos.add(direction[0], dy, direction[1]);
                        double cost = current.cost + (i < 4 ? 1 : SQRT_TWO) + Math.abs(dy) * .5;
                        double score = cost + distance(next, goal);
                        if (Math.abs(next.getX()-start.getX()) > radius || Math.abs(next.getZ()-start.getZ()) > radius
                                || Math.abs(next.getY()-start.getY()) > verticalRange
                                || score >= costLimit - 1e-9
                                || !canStep(terrain, current.pos, next, jumps, maxDrop, diagonals)) continue;
                        Double old = costs.get(next);
                        if (old == null || cost < old) {
                            costs.put(next, cost);
                            open.add(new Node(next, current, cost, score));
                        }
                        break;
                    }
                }
            }
            if (open.isEmpty() || visited >= budget) done = true;
        }

        public boolean isDone() { return done; }
        public boolean reachedGoal() { return reached; }
        public int getVisited() { return visited; }
        public List<BlockPos> path() { return best == first ? Collections.emptyList() : WalkPathfinder.path(best); }

        private double distance(BlockPos a, BlockPos b) {
            return estimate(a, b, diagonals);
        }
    }

    /** Admissible lower bound using the same units as the walking edge costs. */
    public static double estimate(BlockPos a, BlockPos b, boolean diagonals) {
        int x = Math.abs(a.getX()-b.getX()), z = Math.abs(a.getZ()-b.getZ());
        return x + z + (diagonals ? (SQRT_TWO - 2) * Math.min(x, z) : 0)
                + Math.abs(a.getY()-b.getY()) * .5;
    }

    public static double stepCost(BlockPos a, BlockPos b) {
        return (a.getX()!=b.getX() && a.getZ()!=b.getZ() ? SQRT_TWO : 1) + Math.abs(a.getY()-b.getY())*.5;
    }

    /** Same safety rules for new edges and reused edges. Both sides of a diagonal must be walkable. */
    public static boolean canStep(Terrain terrain, BlockPos from, BlockPos to,
                                  boolean jumps, int maxDrop, boolean diagonals) {
        int x = to.getX()-from.getX(), y = to.getY()-from.getY(), z = to.getZ()-from.getZ();
        boolean diagonal = x != 0 && z != 0;
        if (Math.abs(x) > 1 || Math.abs(z) > 1 || x == 0 && z == 0 || y > (jumps ? 1 : 0)
                || -y > maxDrop || diagonal && !diagonals || !terrain.canStand(to)
                || !terrain.canMove(from, to)) return false;
        if (!diagonal) return true;
        return canPassSide(terrain, from, to, x, 0) && canPassSide(terrain, from, to, 0, z);
    }

    private static boolean canPassSide(Terrain terrain, BlockPos from, BlockPos to, int x, int z) {
        // At a step, the side may be supported at either the departure or landing level.
        BlockPos side = from.add(x, 0, z);
        if (terrain.canStand(side) && terrain.canMove(from, side) && terrain.canMove(side, to)) return true;
        if (from.getY() == to.getY()) return false;
        side = from.add(x, to.getY()-from.getY(), z);
        return terrain.canStand(side) && terrain.canMove(from, side) && terrain.canMove(side, to);
    }
    private static List<BlockPos> path(Node node) {
        LinkedList<BlockPos> result = new LinkedList<>();
        for (;node.parent!=null;node=node.parent) result.addFirst(node.pos);
        return result;
    }
    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double cost,score;
        Node(BlockPos pos,Node parent,double cost,double score) {
            this.pos=pos;this.parent=parent;this.cost=cost;this.score=score;
        }
    }
}
