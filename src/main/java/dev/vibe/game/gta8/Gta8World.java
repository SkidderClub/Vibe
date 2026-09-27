package dev.vibe.game.gta8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Los Vibes: a seeded coastal city. Roads, lanes, signals, sidewalks, terrain and collision are
 * generated here without any OpenGL dependency so gameplay tests can run headless.
 * Coordinates: X east, Y up, Z south; one unit is one metre.
 */
public final class Gta8World {
    public static final int LINES = 11;
    public static final double FIRST = -480, SPACING = 96, SIDEWALK = 4.5, CURB = .15, LANE = 3.5;
    public static final double LIMIT = 770, WATER = -1.2, QUAY = 612, PIER_X0 = -112, PIER_X1 = -96, PIER_END = 700;
    public static final double CELL = 8, MIN = -800, SPAN = 1600;
    public static final int CELLS = (int) (SPAN / CELL);
    public static final double SIGNAL_CYCLE = 38, GREEN = 14.5, YELLOW = 3.2;

    public final int[] nsLanes = {2, 1, 2, 1, 1, 2, 1, 1, 2, 1, 2};
    public final int[] ewLanes = {2, 1, 1, 2, 1, 2, 1, 2, 1, 1, 2};
    public final String[] nsNames = {"Aurora Ave", "Bellamy Ave", "Cypress Ave", "Dune Ave", "Elgin Ave", "Fairmont Blvd",
            "Grove Ave", "Harbor Ave", "Ivy Ave", "Juniper Ave", "Kensington Ave"};
    public final String[] ewNames = {"Summit Dr", "2nd St", "3rd St", "4th St", "5th St", "Vibe Blvd",
            "7th St", "8th St", "9th St", "10th St", "Ocean Dr"};

    public final List<Building> buildings = new ArrayList<Building>();
    public final List<Prop> props = new ArrayList<Prop>();
    public final List<Light> lights = new ArrayList<Light>();
    public final List<Poi> pois = new ArrayList<Poi>();
    public final List<Solid> solids = new ArrayList<Solid>();
    public final List<Platform> platforms = new ArrayList<Platform>();
    public final List<Pool> pools = new ArrayList<Pool>();
    public final List<Area> areas = new ArrayList<Area>();
    public final List<Lane> lanes = new ArrayList<Lane>();
    public final List<Signal> signals = new ArrayList<Signal>();
    public final List<WalkNode> walkNodes = new ArrayList<WalkNode>();
    public final List<ParkingSpot> parking = new ArrayList<ParkingSpot>();
    public final List<Pickup> pickups = new ArrayList<Pickup>();
    public final Block[][] blocks = new Block[LINES - 1][LINES - 1];
    public final Signal[][] signalGrid = new Signal[LINES][LINES];
    /** Lanes that end at each intersection approach: [i][j][direction]. */
    final Lane[][][][] incoming = new Lane[LINES][LINES][4][];
    @SuppressWarnings("unchecked") private final List<Solid>[] grid = (List<Solid>[]) new List<?>[CELLS * CELLS];
    private int rayStamp;
    public double spawnX, spawnZ, spawnYaw;

    public Gta8World() {
        for (int i = 0; i < grid.length; i++) grid[i] = new ArrayList<Solid>(2);
        buildRoads();
        new Gta8CityGen(this).generate();
        buildWalkGraph();
    }

    // ---------------------------------------------------------------- road geometry
    public static double line(int i) { return FIRST + i * SPACING; }
    public double nsHalf(int i) { return nsLanes[i] * LANE + (nsLanes[i] == 1 ? 2.6 : .4); }
    public double ewHalf(int j) { return ewLanes[j] * LANE + (ewLanes[j] == 1 ? 2.6 : .4); }
    public boolean nsParking(int i) { return nsLanes[i] == 1; }
    public boolean ewParking(int j) { return ewLanes[j] == 1; }
    public double gridX0() { return line(0) - nsHalf(0); }
    public double gridX1() { return line(LINES - 1) + nsHalf(LINES - 1); }
    public double gridZ0() { return line(0) - ewHalf(0); }
    public double gridZ1() { return line(LINES - 1) + ewHalf(LINES - 1); }
    public static int nearestLine(double v) { return (int) Gta8Math.clamp(Math.round((v - FIRST) / SPACING), 0, LINES - 1); }

    /** Lot rectangle of a block, i.e. the area inside the sidewalks. */
    public double[] lot(int bx, int bz) {
        return new double[]{line(bx) + nsHalf(bx) + SIDEWALK, line(bz) + ewHalf(bz) + SIDEWALK,
                line(bx + 1) - nsHalf(bx + 1) - SIDEWALK, line(bz + 1) - ewHalf(bz + 1) - SIDEWALK};
    }

    public boolean onRoad(double x, double z) {
        if (x < gridX0() || x > gridX1() || z < gridZ0() || z > gridZ1()) return false;
        int i = nearestLine(x), j = nearestLine(z);
        return Math.abs(x - line(i)) < nsHalf(i) || Math.abs(z - line(j)) < ewHalf(j);
    }
    public boolean inIntersection(double x, double z) {
        int i = nearestLine(x), j = nearestLine(z);
        return Math.abs(x - line(i)) < nsHalf(i) && Math.abs(z - line(j)) < ewHalf(j);
    }
    public boolean onSidewalk(double x, double z) {
        return !onRoad(x, z) && x > gridX0() - SIDEWALK && x < gridX1() + SIDEWALK && z > gridZ0() - SIDEWALK && z < gridZ1() + SIDEWALK
                && !insideLot(x, z);
    }
    public boolean insideLot(double x, double z) {
        int bx = (int) Math.floor((x - FIRST) / SPACING), bz = (int) Math.floor((z - FIRST) / SPACING);
        if (bx < 0 || bz < 0 || bx >= LINES - 1 || bz >= LINES - 1) return false;
        double[] l = lot(bx, bz);
        return x > l[0] && x < l[2] && z > l[1] && z < l[3];
    }

    /** Walkable/drivable ground, excluding solids. Roads are at 0, sidewalks and lots at the curb height. */
    public double groundHeight(double x, double z) {
        double best = baseGround(x, z);
        List<Object> list = features[cell(z) * CELLS + cell(x)];
        if (list != null) for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof Platform) {
                Platform p = (Platform) o;
                if (x >= p.x0 && x <= p.x1 && z >= p.z0 && z <= p.z1) best = Math.max(best, p.y);
            } else {
                Pool p = (Pool) o;
                if (x > p.x0 && x < p.x1 && z > p.z0 && z < p.z1) return p.level - 1.5;
            }
        }
        return best;
    }
    @SuppressWarnings("unchecked") private final List<Object>[] features = (List<Object>[]) new List<?>[CELLS * CELLS];
    void addPlatform(Platform p) { platforms.add(p); index(p, p.x0, p.z0, p.x1, p.z1); }
    void addPool(Pool p) { pools.add(p); index(p, p.x0, p.z0, p.x1, p.z1); }
    private void index(Object o, double x0, double z0, double x1, double z1) {
        for (int iz = cell(z0); iz <= cell(z1); iz++) for (int ix = cell(x0); ix <= cell(x1); ix++) {
            if (features[iz * CELLS + ix] == null) features[iz * CELLS + ix] = new ArrayList<Object>(1);
            features[iz * CELLS + ix].add(o);
        }
    }
    public double baseGround(double x, double z) {
        double gx0 = gridX0(), gx1 = gridX1(), gz0 = gridZ0(), gz1 = gridZ1();
        if (x >= gx0 && x <= gx1 && z >= gz0 && z <= gz1) return onRoad(x, z) ? 0 : CURB;
        return terrain(x, z);
    }
    /** Natural terrain outside the street grid: hills north and west, beach south, harbour and sea east. */
    public double terrain(double x, double z) {
        double ox0 = gridX0() - SIDEWALK, ox1 = gridX1() + SIDEWALK, oz0 = gridZ0() - SIDEWALK, oz1 = gridZ1() + SIDEWALK;
        double north = oz0 - z, west = ox0 - x;
        double hill = 0;
        double d = Math.max(north, west);
        if (d > 0) {
            double rise = Gta8Math.smooth(0, 190, d);
            double shape = 30 + 190 * Gta8Math.ridge(x * .0042, z * .0042, 5, 91) + 40 * Gta8Math.fbm(x * .011, z * .011, 3, 7);
            hill = rise * shape * Gta8Math.smooth(0, 60, d) + d * .02;
            // A flattened shelf carries the VIBE sign above the city.
            double sx = x + 110, sz = z + 640;
            double shelf = Math.exp(-(sx * sx) / (2 * 70 * 70) - (sz * sz) / (2 * 26 * 26));
            hill = Gta8Math.lerp(hill, 118, shelf * .92);
        }
        double base;
        if (x > ox1) {
            // Harbour apron, then vertical quay walls into deep water.
            boolean land = x < QUAY && z < 560;
            base = land ? CURB : -9 - Math.min(20, Math.max(0, x - QUAY) * .03 + Math.max(0, z - 560) * .03);
            if (!land) hill *= Gta8Math.smooth(QUAY + 140, QUAY - 20, x);
        } else if (z > oz1) {
            double s = z - oz1;
            base = Math.max(CURB - s * .045, -16 - s * .01);
        } else base = CURB;
        return d > 0 ? Math.max(base, hill + CURB) : base;
    }
    public double waterLevelAt(double x, double z) {
        List<Object> list = features[cell(z) * CELLS + cell(x)];
        if (list != null) for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof Pool) { Pool p = (Pool) o; if (x > p.x0 && x < p.x1 && z > p.z0 && z < p.z1) return p.level; }
        }
        return WATER;
    }
    public boolean isWater(double x, double z) { return groundHeight(x, z) < waterLevelAt(x, z) - .05; }

    // ---------------------------------------------------------------- collision
    public Solid solid(double x0, double y0, double z0, double x1, double y1, double z1) { return solid(x0, y0, z0, x1, y1, z1, null); }
    public Solid solid(double x0, double y0, double z0, double x1, double y1, double z1, Prop owner) {
        Solid s = new Solid(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), owner);
        solids.add(s);
        for (int iz = cell(s.z0); iz <= cell(s.z1); iz++) for (int ix = cell(s.x0); ix <= cell(s.x1); ix++) grid[iz * CELLS + ix].add(s);
        return s;
    }
    static int cell(double v) { return (int) Gta8Math.clamp(Math.floor((v - MIN) / CELL), 0, CELLS - 1); }

    public boolean blocked(double x, double y, double z, double radius, double height) {
        if (Math.abs(x) > LIMIT || Math.abs(z) > LIMIT) return true;
        for (int iz = cell(z - radius); iz <= cell(z + radius); iz++) for (int ix = cell(x - radius); ix <= cell(x + radius); ix++) {
            List<Solid> list = grid[iz * CELLS + ix];
            for (int k = 0; k < list.size(); k++) { Solid s = list.get(k); if (s.enabled && s.intersects(x, y, z, radius, height)) return true; }
        }
        return false;
    }
    /** First solid that overlaps the vertical cylinder, or null. */
    public Solid overlapping(double x, double y, double z, double radius, double height) {
        for (int iz = cell(z - radius); iz <= cell(z + radius); iz++) for (int ix = cell(x - radius); ix <= cell(x + radius); ix++) {
            List<Solid> list = grid[iz * CELLS + ix];
            for (int k = 0; k < list.size(); k++) { Solid s = list.get(k); if (s.enabled && s.intersects(x, y, z, radius, height)) return s; }
        }
        return null;
    }
    /** Highest walkable surface under a cylinder whose top is not above {@code ceiling}. */
    public double support(double x, double z, double ceiling, double radius) {
        double result = groundHeight(x, z);
        for (int iz = cell(z - radius); iz <= cell(z + radius); iz++) for (int ix = cell(x - radius); ix <= cell(x + radius); ix++) {
            List<Solid> list = grid[iz * CELLS + ix];
            for (int k = 0; k < list.size(); k++) {
                Solid s = list.get(k);
                if (s.enabled && s.y1 <= ceiling + 1e-6 && s.y1 > result && x + radius > s.x0 && x - radius < s.x1 && z + radius > s.z0 && z - radius < s.z1) result = s.y1;
            }
        }
        return result;
    }
    /** Solids touched by an oriented rectangle footprint (vehicles); appends to {@code out}. */
    public void solidsNear(double x0, double z0, double x1, double z1, List<Solid> out) {
        out.clear();
        int stamp = ++rayStamp;
        for (int iz = cell(z0); iz <= cell(z1); iz++) for (int ix = cell(x0); ix <= cell(x1); ix++) {
            List<Solid> list = grid[iz * CELLS + ix];
            for (int k = 0; k < list.size(); k++) {
                Solid s = list.get(k);
                if (s.enabled && s.stamp != stamp && s.x1 > x0 && s.x0 < x1 && s.z1 > z0 && s.z0 < z1) { s.stamp = stamp; out.add(s); }
            }
        }
    }

    /** Distance to the first solid or ground along a unit ray, capped at range. Grid DDA traversal. */
    public double ray(double x, double y, double z, double dx, double dy, double dz, double range) { return ray(x, y, z, dx, dy, dz, range, null); }
    public synchronized double ray(double x, double y, double z, double dx, double dy, double dz, double range, Solid[] hit) {
        double distance = groundRay(x, y, z, dx, dy, dz, range);
        if (hit != null) hit[0] = null;
        int stamp = ++rayStamp;
        int cx = (int) Math.floor((x - MIN) / CELL), cz = (int) Math.floor((z - MIN) / CELL);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : (MIN + (cx + (sx > 0 ? 1 : 0)) * CELL - x) / dx;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : (MIN + (cz + (sz > 0 ? 1 : 0)) * CELL - z) / dz;
        double stepX = sx == 0 ? Double.POSITIVE_INFINITY : CELL / Math.abs(dx), stepZ = sz == 0 ? Double.POSITIVE_INFINITY : CELL / Math.abs(dz), entry = 0;
        while (entry <= distance) {
            if (cx < 0 || cz < 0 || cx >= CELLS || cz >= CELLS) break;
            List<Solid> list = grid[cz * CELLS + cx];
            for (int k = 0; k < list.size(); k++) {
                Solid s = list.get(k);
                if (!s.enabled || s.stamp == stamp) continue;
                s.stamp = stamp;
                double t = s.ray(x, y, z, dx, dy, dz, distance);
                if (t < distance) { distance = t; if (hit != null) hit[0] = s; }
            }
            if (tx == Double.POSITIVE_INFINITY && tz == Double.POSITIVE_INFINITY) break;
            if (tx <= tz) { entry = tx; tx += stepX; cx += sx; } else { entry = tz; tz += stepZ; cz += sz; }
        }
        return distance;
    }
    /** Ground intersection by marching the height field (roads, curbs, hills); refined by bisection. */
    public double groundRay(double x, double y, double z, double dx, double dy, double dz, double range) {
        if (y < groundHeight(x, z) - .01) return 0;
        double step = .5, t = 0, prev = 0;
        while (t < range) {
            t = Math.min(range, t + step);
            double px = x + dx * t, py = y + dy * t, pz = z + dz * t;
            if (py <= groundHeight(px, pz)) {
                double a = prev, b = t;
                for (int i = 0; i < 12; i++) { double m = (a + b) * .5; if (y + dy * m <= groundHeight(x + dx * m, z + dz * m)) b = m; else a = m; }
                return b;
            }
            prev = t;
            double clearance = py - Math.max(0, groundHeight(px, pz));
            step = Gta8Math.clamp(clearance * .5, .25, 6);
            if (dy > 0 && py > 260) return range;
        }
        return range;
    }

    // ---------------------------------------------------------------- traffic lanes and signals
    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    public static final double[] DIR_X = {0, 1, 0, -1}, DIR_Z = {-1, 0, 1, 0};

    private void buildRoads() {
        for (int i = 0; i < LINES; i++) for (int j = 0; j < LINES; j++) {
            int approaches = (i > 0 ? 1 : 0) + (i < LINES - 1 ? 1 : 0) + (j > 0 ? 1 : 0) + (j < LINES - 1 ? 1 : 0);
            Signal s = new Signal(i, j, approaches >= 3, Gta8Math.hash01(i, j, 5) * SIGNAL_CYCLE);
            signals.add(s); signalGrid[i][j] = s;
            for (int d = 0; d < 4; d++) incoming[i][j][d] = new Lane[0];
        }
        // Directed lanes along every road segment. Right-hand traffic.
        for (int i = 0; i < LINES; i++) for (int j = 0; j < LINES - 1; j++) {
            double x = line(i), za = line(j) + ewHalf(j), zb = line(j + 1) - ewHalf(j + 1);
            for (int k = 0; k < nsLanes[i]; k++) {
                double off = (k + .5) * LANE;
                addLane(i, j + 1, SOUTH, k, x - off, za, x - off, zb, nsLanes[i]);   // southbound, west side
                addLane(i, j, NORTH, k, x + off, zb, x + off, za, nsLanes[i]);       // northbound, east side
            }
        }
        for (int j = 0; j < LINES; j++) for (int i = 0; i < LINES - 1; i++) {
            double z = line(j), xa = line(i) + nsHalf(i), xb = line(i + 1) - nsHalf(i + 1);
            for (int k = 0; k < ewLanes[j]; k++) {
                double off = (k + .5) * LANE;
                addLane(i + 1, j, EAST, k, xa, z + off, xb, z + off, ewLanes[j]);    // eastbound, south side
                addLane(i, j, WEST, k, xb, z - off, xa, z - off, ewLanes[j]);        // westbound, north side
            }
        }
        for (Lane lane : lanes) {
            List<Lane> list = new ArrayList<Lane>(Arrays.asList(incoming[lane.toI][lane.toJ][lane.dir]));
            list.add(lane);
            incoming[lane.toI][lane.toJ][lane.dir] = list.toArray(new Lane[0]);
        }
        // Connectors through each intersection.
        for (Lane in : lanes) {
            int i = in.toI, j = in.toJ;
            for (int turn = -1; turn <= 1; turn++) {
                int outDir = (in.dir + turn + 4) % 4;
                int ni = i + (int) DIR_X[outDir], nj = j + (int) DIR_Z[outDir];
                if (ni < 0 || nj < 0 || ni >= LINES || nj >= LINES) continue;
                int lanesOut = outDir == NORTH || outDir == SOUTH ? nsLanes[i] : ewLanes[j];
                int lanesIn = in.lanes;
                // Left turns from the inner lane, right turns from the outer lane, straight keeps its lane.
                if (turn == -1 && in.index != 0) continue;
                if (turn == 1 && in.index != lanesIn - 1) continue;
                int target = turn == -1 ? 0 : turn == 1 ? lanesOut - 1 : Math.min(in.index, lanesOut - 1);
                Lane out = null;
                for (Lane candidate : lanes) {
                    if (candidate.fromI == i && candidate.fromJ == j && candidate.dir == outDir && candidate.index == target) { out = candidate; break; }
                }
                if (out == null) continue;
                in.next.add(new Connector(in, out, turn));
            }
            // Corners and T-junctions: a lane without a legal movement may take any available exit.
            if (in.next.isEmpty()) for (Lane out : lanes) {
                if (out.fromI != i || out.fromJ != j || (out.dir + 2) % 4 == in.dir) continue;
                int turn = out.dir == in.dir ? 0 : (out.dir - in.dir + 4) % 4 == 1 ? 1 : -1;
                in.next.add(new Connector(in, out, turn));
            }
        }
    }
    private void addLane(int toI, int toJ, int dir, int index, double x0, double z0, double x1, double z1, int count) {
        int fromI = toI - (int) DIR_X[dir], fromJ = toJ - (int) DIR_Z[dir];
        Lane lane = new Lane(lanes.size(), fromI, fromJ, toI, toJ, dir, index, count, x0, z0, x1, z1);
        lane.speedLimit = count == 2 ? 16.7 : 13.4;
        lanes.add(lane);
    }
    public Lane[] incoming(int i, int j, int dir) { return incoming[i][j][dir]; }

    /** Signal state for traffic travelling along the given direction into the intersection. */
    public int signalState(Signal s, int dir, double time) {
        if (!s.active) return GREEN_LIGHT;
        double t = ((time + s.offset) % SIGNAL_CYCLE + SIGNAL_CYCLE) % SIGNAL_CYCLE;
        boolean ns = dir == NORTH || dir == SOUTH;
        double local = ns ? t : (t + SIGNAL_CYCLE / 2) % SIGNAL_CYCLE;
        if (local < GREEN) return GREEN_LIGHT;
        if (local < GREEN + YELLOW) return YELLOW_LIGHT;
        return RED_LIGHT;
    }
    public static final int GREEN_LIGHT = 0, YELLOW_LIGHT = 1, RED_LIGHT = 2;
    /** Pedestrians crossing a road parallel to green traffic may walk; last seconds flash. */
    public int walkState(Signal s, boolean crossingNorthSouthRoad, double time) {
        if (!s.active) return WALK;
        int parallel = signalState(s, crossingNorthSouthRoad ? EAST : NORTH, time);
        if (parallel != GREEN_LIGHT) return DONT_WALK;
        double t = ((time + s.offset) % SIGNAL_CYCLE + SIGNAL_CYCLE) % SIGNAL_CYCLE;
        double local = crossingNorthSouthRoad ? (t + SIGNAL_CYCLE / 2) % SIGNAL_CYCLE : t;
        return local < GREEN - 6 ? WALK : FLASHING;
    }
    public static final int WALK = 0, FLASHING = 1, DONT_WALK = 2;

    // ---------------------------------------------------------------- GPS routing over the road grid
    /** Shortest intersection path; returns centre points including the start and goal intersections. */
    public List<double[]> route(double fromX, double fromZ, double toX, double toZ) {
        int si = nearestLine(fromX), sj = nearestLine(fromZ), gi = nearestLine(toX), gj = nearestLine(toZ);
        int n = LINES * LINES;
        double[] cost = new double[n]; int[] parent = new int[n]; boolean[] closed = new boolean[n];
        Arrays.fill(cost, Double.POSITIVE_INFINITY); Arrays.fill(parent, -1);
        int start = sj * LINES + si, goal = gj * LINES + gi;
        cost[start] = 0;
        for (int iter = 0; iter < n; iter++) {
            int best = -1; double bestScore = Double.POSITIVE_INFINITY;
            for (int k = 0; k < n; k++) if (!closed[k] && cost[k] < Double.POSITIVE_INFINITY) {
                double h = (Math.abs(k % LINES - gi) + Math.abs(k / LINES - gj)) * SPACING;
                if (cost[k] + h < bestScore) { bestScore = cost[k] + h; best = k; }
            }
            if (best < 0 || best == goal) break;
            closed[best] = true;
            int bi = best % LINES, bj = best / LINES;
            for (int d = 0; d < 4; d++) {
                int ni = bi + (int) DIR_X[d], nj = bj + (int) DIR_Z[d];
                if (ni < 0 || nj < 0 || ni >= LINES || nj >= LINES) continue;
                int next = nj * LINES + ni;
                // Wider roads are faster, so the route prefers boulevards like a real navigation system.
                int lanesOnRoad = d == NORTH || d == SOUTH ? nsLanes[bi] : ewLanes[bj];
                double c = cost[best] + SPACING * (lanesOnRoad == 2 ? .8 : 1);
                if (c < cost[next]) { cost[next] = c; parent[next] = best; }
            }
        }
        List<double[]> path = new ArrayList<double[]>();
        for (int k = goal; k >= 0; k = parent[k]) { path.add(new double[]{line(k % LINES), line(k / LINES)}); if (k == start) break; }
        Collections.reverse(path);
        return path;
    }

    // ---------------------------------------------------------------- pedestrian network
    private void buildWalkGraph() {
        WalkNode[][][] corners = new WalkNode[LINES - 1][LINES - 1][4];
        double m = SIDEWALK / 2;
        for (int bx = 0; bx < LINES - 1; bx++) for (int bz = 0; bz < LINES - 1; bz++) {
            double[] l = lot(bx, bz);
            corners[bx][bz][0] = node(l[0] - m, l[1] - m);   // NW
            corners[bx][bz][1] = node(l[2] + m, l[1] - m);   // NE
            corners[bx][bz][2] = node(l[2] + m, l[3] + m);   // SE
            corners[bx][bz][3] = node(l[0] - m, l[3] + m);   // SW
            for (int c = 0; c < 4; c++) link(corners[bx][bz][c], corners[bx][bz][(c + 1) % 4], null, false);
        }
        for (int bx = 0; bx < LINES - 1; bx++) for (int bz = 0; bz < LINES - 1; bz++) {
            if (bx + 1 < LINES - 1) {
                // Cross the north-south road between two blocks, at both ends.
                link(corners[bx][bz][1], corners[bx + 1][bz][0], signalGrid[bx + 1][bz], true);
                link(corners[bx][bz][2], corners[bx + 1][bz][3], signalGrid[bx + 1][bz + 1], true);
            }
            if (bz + 1 < LINES - 1) {
                link(corners[bx][bz][3], corners[bx][bz + 1][0], signalGrid[bx][bz + 1], false);
                link(corners[bx][bz][2], corners[bx][bz + 1][1], signalGrid[bx + 1][bz + 1], false);
            }
        }
    }
    private WalkNode node(double x, double z) { WalkNode n = new WalkNode(walkNodes.size(), x, z); walkNodes.add(n); return n; }
    private static void link(WalkNode a, WalkNode b, Signal signal, boolean crossesNorthSouthRoad) {
        a.links.add(new WalkLink(b, signal, crossesNorthSouthRoad)); b.links.add(new WalkLink(a, signal, crossesNorthSouthRoad));
    }
    public WalkNode nearestWalkNode(double x, double z) {
        WalkNode best = null; double d = Double.POSITIVE_INFINITY;
        for (WalkNode n : walkNodes) { double e = (n.x - x) * (n.x - x) + (n.z - z) * (n.z - z); if (e < d) { d = e; best = n; } }
        return best;
    }

    // ---------------------------------------------------------------- queries
    public String districtName(double x, double z) {
        for (Area a : areas) if (a.name != null && x >= a.x0 && x <= a.x1 && z >= a.z0 && z <= a.z1) return a.name;
        if (x > gridX1() + SIDEWALK) return "Port of Los Vibes";
        if (z > gridZ1() + SIDEWALK) return "Del Mar Beach";
        if (x < gridX0() - SIDEWALK || z < gridZ0() - SIDEWALK) return "Vibewood Hills";
        int bx = (int) Gta8Math.clamp(Math.floor((x - FIRST) / SPACING), 0, LINES - 2), bz = (int) Gta8Math.clamp(Math.floor((z - FIRST) / SPACING), 0, LINES - 2);
        Block b = blocks[bx][bz];
        return b == null ? "Los Vibes" : b.district.title;
    }
    public String streetName(double x, double z) {
        int i = nearestLine(x), j = nearestLine(z);
        double dx = Math.abs(x - line(i)), dz = Math.abs(z - line(j));
        if (x < gridX0() - 20 || x > gridX1() + 20 || z < gridZ0() - 20 || z > gridZ1() + 20) return "";
        return dx < dz ? nsNames[i] : ewNames[j];
    }
    public Poi nearestPoi(int type, double x, double z) {
        Poi best = null; double d = Double.POSITIVE_INFINITY;
        for (Poi p : pois) if (p.type == type) { double e = Math.hypot(p.x - x, p.z - z); if (e < d) { d = e; best = p; } }
        return best;
    }
    public void knock(Prop prop) {
        if (prop.broken) return;
        prop.broken = true;
        for (Solid s : prop.solids) s.enabled = false;
        brokenRevision++;
    }
    public int brokenRevision;
    public void restoreProps() {
        for (Prop p : props) if (p.broken) { p.broken = false; for (Solid s : p.solids) s.enabled = true; brokenRevision++; }
    }

    // ---------------------------------------------------------------- data
    public enum District {
        DOWNTOWN("Downtown"), MIDTOWN("Midtown"), SUBURB("Mirror Heights"), INDUSTRIAL("Cypress Flats"),
        COAST("Vespucci Shores"), NORTH("Alta"), PARK("Vibe Park");
        public final String title;
        District(String title) { this.title = title; }
    }
    public static final class Block {
        public final int bx, bz; public final District district; public final double x0, z0, x1, z1;
        public int ground; public static final int PAVED = 0, GRASS = 1, ASPHALT = 2, DIRT = 3;
        Block(int bx, int bz, District district, double[] lot) { this.bx = bx; this.bz = bz; this.district = district; x0 = lot[0]; z0 = lot[1]; x1 = lot[2]; z1 = lot[3]; }
    }
    /** Ground finishes drawn on top of a lot, or a named locality when {@code type == NAMED}. */
    public static final class Area {
        public final double x0, z0, x1, z1; public final String name; public final int type; public double y = CURB + .025;
        Area(double x0, double z0, double x1, double z1, String name, int type) { this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.name = name; this.type = type; }
        public static final int PARK = 0, PLAZA = 1, BEACH = 2, YARD = 3, NAMED = 4, PARKING = 5, PATH = 6, DRIVEWAY = 7, DECK = 8, TILE = 9;
    }
    /** Building masses. Facade detail is shaded procedurally from these parameters. */
    public static final class Building {
        public static final int TOWER = 0, MIDRISE = 1, HOUSE = 2, WAREHOUSE = 3, SHOP = 4, HOTEL = 5, CIVIC = 6, CANOPY = 7;
        public final int kind, seed;
        public final double x0, z0, x1, z1;
        public final List<Volume> volumes = new ArrayList<Volume>();
        public String name; public int nameFace = -1, frontFaces;
        public double shopHeight; public int awningColor = -1; public boolean interior;
        public int roof; public int roofColor; public double roofPitch;
        Building(int kind, double x0, double z0, double x1, double z1, int seed) { this.kind = kind; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.seed = seed; }
        public double top() { double h = 0; for (Volume v : volumes) h = Math.max(h, v.y1); return h; }
        public boolean contains(double x, double z) { return x > x0 && x < x1 && z > z0 && z < z1; }
        public static final int ROOF_FLAT = 0, ROOF_GABLE_X = 1, ROOF_GABLE_Z = 2, ROOF_HIP = 3, ROOF_SAWTOOTH = 4;
    }
    public static final class Volume {
        public static final int FACADE = 0, GLASS = 1, PLAIN = 2, LOBBY = 3, SHOPFRONT = 4, CORRUGATED = 5, SIDING = 6;
        public final double x0, y0, z0, x1, y1, z1; public final int kind, style, color;
        /** Faces without openings (N, E, S, W bits), e.g. party walls against a neighbour. */
        public int blank;
        public static int facadeStyle(int wall, int layout, int floor) { return (wall & 3) | ((layout & 7) << 2) | ((floor & 7) << 5); }
        public static double floorHeight(int style) { return 3.0 + ((style >> 5) & 7) * .2; }
        Volume(double x0, double y0, double z0, double x1, double y1, double z1, int kind, int style, int color) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0; this.x1 = x1; this.y1 = y1; this.z1 = z1; this.kind = kind; this.style = style; this.color = color;
        }
    }
    public static final class Solid {
        public final double x0, y0, z0, x1, y1, z1; public final Prop owner; public boolean enabled = true; int stamp;
        Solid(double x0, double y0, double z0, double x1, double y1, double z1, Prop owner) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0; this.x1 = x1; this.y1 = y1; this.z1 = z1; this.owner = owner;
        }
        public boolean intersects(double x, double y, double z, double r, double h) {
            return x + r > x0 && x - r < x1 && z + r > z0 && z - r < z1 && y + h > y0 && y < y1;
        }
        public double ray(double x, double y, double z, double dx, double dy, double dz, double limit) {
            double near = 0, far = limit;
            if (Math.abs(dx) < 1e-9) { if (x < x0 || x > x1) return limit; }
            else { double a = (x0 - x) / dx, b = (x1 - x) / dx; near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b)); }
            if (Math.abs(dy) < 1e-9) { if (y < y0 || y > y1) return limit; }
            else { double a = (y0 - y) / dy, b = (y1 - y) / dy; near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b)); }
            if (Math.abs(dz) < 1e-9) { if (z < z0 || z > z1) return limit; }
            else { double a = (z0 - z) / dz, b = (z1 - z) / dz; near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b)); }
            return far >= near ? near : limit;
        }
    }
    public static final class Prop {
        public static final int STREET_LIGHT = 0, SIGNAL = 1, HYDRANT = 2, TRASH = 3, BENCH = 4, BUS_STOP = 5, NEWSBOX = 6, METER = 7,
                PALM = 8, TREE = 9, BUSH = 10, MAILBOX = 11, BILLBOARD = 12, STREET_SIGN = 13, BOLLARD = 14, DUMPSTER = 15,
                CONTAINER = 16, CRANE = 17, TANK = 18, LIFEGUARD = 19, UMBRELLA = 20, ROCK = 21, VIBE_LETTER = 22, ANTENNA = 23,
                WATER_TOWER = 24, AC_UNIT = 25, PUMP = 26, FERRIS = 27, FOUNTAIN = 28, FENCE = 29, HEDGE = 30, PINE = 31,
                COUNTER = 32, SHELF = 33, PLANTER = 34, BARRIER = 35, CONE = 36, PHONE = 37, RAILING = 38, TABLE = 39, LAMP_PARK = 40,
                HELIPAD = 41, SPOTLIGHT = 42, BOAT = 43, GLASS = 44, CANOPY = 45, SHIP = 46, TOWEL = 47, PILING = 48, FRIDGE = 49,
                RACK = 50, REGISTER = 51, FLAG = 52;
        public final int type, variant, seed; public final double x, y, z, yaw, sx, sy, sz;
        public boolean breakable, broken; public int signalId = -1, dir = -1; public String text;
        /** Set when knocked over: direction of the fall and the time it happened. */
        public double fallYaw, fallTime = -1;
        public final List<Solid> solids = new ArrayList<Solid>(1);
        Prop(int type, double x, double y, double z, double yaw, double sx, double sy, double sz, int variant, int seed) {
            this.type = type; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.sx = sx; this.sy = sy; this.sz = sz; this.variant = variant; this.seed = seed;
        }
    }
    public static final class Light {
        public static final int STREET = 0, WARM = 1, NEON = 2, BEACON = 3, FLOOD = 4, INTERIOR = 5;
        public final double x, y, z, radius; public final int color, kind;
        Light(double x, double y, double z, double radius, int color, int kind) { this.x = x; this.y = y; this.z = z; this.radius = radius; this.color = color; this.kind = kind; }
    }
    public static final class Poi {
        public static final int STORE = 0, GUN_SHOP = 1, HOSPITAL = 2, POLICE = 3, SAFEHOUSE = 4, GAS = 5, SPRAY = 6, JOB = 7, CLOTHES = 8;
        public final int type; public final double x, z, yaw; public final String name;
        public double interactX, interactZ, clerkX, clerkZ, clerkYaw; public double robbedUntil;
        Poi(int type, double x, double z, double yaw, String name) { this.type = type; this.x = x; this.z = z; this.yaw = yaw; this.name = name; interactX = x; interactZ = z; }
    }
    public static final class Platform {
        public final double x0, z0, x1, z1, y;
        Platform(double x0, double z0, double x1, double z1, double y) { this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.y = y; }
    }
    public static final class Pool {
        public final double x0, z0, x1, z1, level;
        Pool(double x0, double z0, double x1, double z1, double level) { this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.level = level; }
    }
    public static final class Signal {
        public final int i, j; public final boolean active; public final double offset;
        Signal(int i, int j, boolean active, double offset) { this.i = i; this.j = j; this.active = active; this.offset = offset; }
        public double x() { return line(i); } public double z() { return line(j); }
        public int id() { return j * LINES + i; }
    }
    public static final class Lane {
        public final int id, fromI, fromJ, toI, toJ, dir, index, lanes;
        public final double x0, z0, x1, z1, length; public double speedLimit;
        public final List<Connector> next = new ArrayList<Connector>(3);
        Lane(int id, int fromI, int fromJ, int toI, int toJ, int dir, int index, int lanes, double x0, double z0, double x1, double z1) {
            this.id = id; this.fromI = fromI; this.fromJ = fromJ; this.toI = toI; this.toJ = toJ; this.dir = dir; this.index = index; this.lanes = lanes;
            this.x0 = x0; this.z0 = z0;
            // Lanes end at the stop line, set back from the crosswalk.
            double back = 5.6;
            this.x1 = x1 - DIR_X[dir] * back; this.z1 = z1 - DIR_Z[dir] * back;
            length = Math.hypot(this.x1 - x0, this.z1 - z0);
        }
        public double yaw() { return dir * 90.0; }
    }
    /** Cubic Bezier path through an intersection between two lanes. */
    public static final class Connector {
        public final Lane from, to; public final int turn; public final double length;
        final double[] px = new double[4], pz = new double[4];
        Connector(Lane from, Lane to, int turn) {
            this.from = from; this.to = to; this.turn = turn;
            px[0] = from.x1; pz[0] = from.z1; px[3] = to.x0; pz[3] = to.z0;
            double span = Math.hypot(px[3] - px[0], pz[3] - pz[0]);
            double k = turn == 0 ? span / 3 : span * .42;
            px[1] = px[0] + DIR_X[from.dir] * k; pz[1] = pz[0] + DIR_Z[from.dir] * k;
            px[2] = px[3] - DIR_X[to.dir] * k; pz[2] = pz[3] - DIR_Z[to.dir] * k;
            double len = 0, lx = px[0], lz = pz[0];
            for (int i = 1; i <= 24; i++) { double[] p = point(i / 24.0, new double[2]); len += Math.hypot(p[0] - lx, p[1] - lz); lx = p[0]; lz = p[1]; }
            length = len;
        }
        public double[] point(double t, double[] out) {
            double u = 1 - t;
            out[0] = u * u * u * px[0] + 3 * u * u * t * px[1] + 3 * u * t * t * px[2] + t * t * t * px[3];
            out[1] = u * u * u * pz[0] + 3 * u * u * t * pz[1] + 3 * u * t * t * pz[2] + t * t * t * pz[3];
            return out;
        }
        public double[] tangent(double t, double[] out) {
            double u = 1 - t;
            out[0] = 3 * u * u * (px[1] - px[0]) + 6 * u * t * (px[2] - px[1]) + 3 * t * t * (px[3] - px[2]);
            out[1] = 3 * u * u * (pz[1] - pz[0]) + 6 * u * t * (pz[2] - pz[1]) + 3 * t * t * (pz[3] - pz[2]);
            return out;
        }
    }
    public static final class WalkNode {
        public final int id; public final double x, z; public final List<WalkLink> links = new ArrayList<WalkLink>(4);
        WalkNode(int id, double x, double z) { this.id = id; this.x = x; this.z = z; }
    }
    public static final class WalkLink {
        public final WalkNode to; public final Signal signal; public final boolean crossesNorthSouthRoad;
        WalkLink(WalkNode to, Signal signal, boolean crossesNorthSouthRoad) { this.to = to; this.signal = signal; this.crossesNorthSouthRoad = crossesNorthSouthRoad; }
        public boolean crossing() { return signal != null; }
    }
    public static final class ParkingSpot {
        public final double x, z, yaw; public final int kind;
        public static final int STREET = 0, DRIVEWAY = 1, LOT = 2, POLICE = 3, TRUCK = 4;
        ParkingSpot(double x, double z, double yaw, int kind) { this.x = x; this.z = z; this.yaw = yaw; this.kind = kind; }
    }
    public static final class Pickup {
        public static final int HEALTH = 0, ARMOR = 1, CASH = 2, WEAPON = 3;
        public final int type, value; public final double x, y, z; public double respawnAt;
        Pickup(int type, int value, double x, double y, double z) { this.type = type; this.value = value; this.x = x; this.y = y; this.z = z; }
    }
}
