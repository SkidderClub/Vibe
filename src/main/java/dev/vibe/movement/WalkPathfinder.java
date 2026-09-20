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
    private static final int[] HEIGHTS = {0,1,-1,-2,-3};

    private WalkPathfinder() { }

    /** Excludes the starting node. A partial route is allowed only if it gets closer. */
    public static List<BlockPos> find(Terrain terrain, BlockPos start, BlockPos goal, int radius, int budget) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.score));
        Map<BlockPos,Double> costs = new HashMap<>();
        Node first = new Node(start, null, 0, distance(start,goal));
        Node best = first;
        open.add(first); costs.put(start,0D);
        int visited=0;
        while (!open.isEmpty() && visited++ < budget) {
            Node current = open.poll();
            if (current.cost > costs.get(current.pos)) continue;
            if (distance(current.pos,goal) < distance(best.pos,goal)) best = current;
            if (current.pos.equals(goal)) return path(current);
            for (int[] direction : DIRECTIONS) {
                for (int dy : HEIGHTS) {
                    BlockPos next = current.pos.add(direction[0],dy,direction[1]);
                    if (Math.abs(next.getX()-start.getX())>radius || Math.abs(next.getZ()-start.getZ())>radius
                            || Math.abs(next.getY()-start.getY())>8 || !terrain.canStand(next)
                            || !terrain.canMove(current.pos,next)) continue;
                    double cost = current.cost + 1 + Math.abs(dy)*.5;
                    Double old = costs.get(next);
                    if (old == null || cost < old) {
                        costs.put(next,cost);
                        open.add(new Node(next,current,cost,cost+distance(next,goal)));
                    }
                    break;
                }
            }
        }
        return best == first ? Collections.emptyList() : path(best);
    }

    private static double distance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())+Math.abs(a.getY()-b.getY())*.5;
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
