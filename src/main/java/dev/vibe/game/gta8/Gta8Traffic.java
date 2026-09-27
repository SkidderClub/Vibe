package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8World.Connector;
import dev.vibe.game.gta8.Gta8World.Lane;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lane-following traffic using the Intelligent Driver Model: signals, yellow-light decisions,
 * permissive left turns, "don't block the box", indicators, and pulling over for sirens.
 */
public final class Gta8Traffic {
    /** Per-vehicle driving state for lane-following cars. */
    public static final class Brain {
        public Lane lane;
        public Connector connector, next;
        public double s, speed, offset, waitTime, honkTimer;
        public final double aggression;
        public boolean panic;
        Brain(Lane lane, double s, double aggression) { this.lane = lane; this.s = s; this.aggression = aggression; }
    }

    static final int[] PAINTS = {0xE8E8E6, 0xF4F4F2, 0x1C1D20, 0x2A2C30, 0x8E9296, 0xB8BCC0, 0x6E7276, 0x1E2E4E, 0x2C4A7A, 0x8A1C1C,
            0xB02A22, 0x3A4A2E, 0x5A1E2A, 0xC8B89A, 0x7A6248, 0xD8D0B8, 0x4A5A6A, 0x2A3A3A};
    private final Gta8Game game;
    private final Random random;
    private final List<Gta8Vehicle> nearby = new ArrayList<Gta8Vehicle>();
    private final List<Gta8Ped> nearbyPeds = new ArrayList<Gta8Ped>();
    private final double[] px = new double[32], pz = new double[32], pd = new double[32];
    private final double[] tmp = new double[2];
    public int target = 42;

    Gta8Traffic(Gta8Game game, Random random) { this.game = game; this.random = random; }

    // ------------------------------------------------------------------ population
    void populate(double dt) {
        Gta8Ped player = game.player;
        double cx = player.x, cz = player.z;
        int count = 0;
        for (int i = game.vehicles.size() - 1; i >= 0; i--) {
            Gta8Vehicle v = game.vehicles.get(i);
            if (v.ai == null || v.mission || v == game.player.vehicle) continue;
            double d = Math.hypot(v.x - cx, v.z - cz);
            if (d > 300 && !game.visible(v.x, v.y + 1, v.z, 4) || d > 420) { game.removeVehicle(v); continue; }
            if (v.ai.waitTime > 45 && d > 120 && !game.visible(v.x, v.y + 1, v.z, 4)) { game.removeVehicle(v); continue; }
            count++;
        }
        if (count >= target) return;
        for (int attempt = 0; attempt < 6; attempt++) {
            Lane lane = game.world.lanes.get(random.nextInt(game.world.lanes.size()));
            double s = 6 + random.nextDouble() * Math.max(1, lane.length - 20);
            double x = lane.x0 + Gta8World.DIR_X[lane.dir] * s, z = lane.z0 + Gta8World.DIR_Z[lane.dir] * s;
            double d = Math.hypot(x - cx, z - cz);
            if (d < 75 || d > 240) continue;
            if (d < 170 && game.visible(x, 1, z, 3)) continue;
            if (occupied(x, z, 14)) continue;
            spawn(lane, s);
            return;
        }
    }
    private boolean occupied(double x, double z, double radius) {
        for (Gta8Vehicle v : game.vehicles) if (Math.abs(v.x - x) < radius && Math.abs(v.z - z) < radius && Math.hypot(v.x - x, v.z - z) < radius) return true;
        return false;
    }
    Gta8Vehicle spawn(Lane lane, double s) {
        Gta8Vehicle.Model model = randomModel();
        Gta8Vehicle v = new Gta8Vehicle(model, 0, 0, lane.yaw(), model == Gta8Vehicle.Model.TAXI ? 0xE8B81C : PAINTS[random.nextInt(PAINTS.length)]);
        v.ai = new Brain(lane, s, .82 + random.nextDouble() * .38);
        v.ai.speed = lane.speedLimit * .7;
        Gta8Ped driver = game.createCivilian(0, 0);
        driver.state = Gta8Ped.State.DRIVE;
        driver.vehicle = v;
        v.driver = driver;
        place(v, 0);
        v.remember();
        game.addVehicle(v);
        game.peds.add(driver);
        return v;
    }
    private Gta8Vehicle.Model randomModel() {
        int r = random.nextInt(100);
        if (r < 28) return Gta8Vehicle.Model.SEDAN;
        if (r < 46) return Gta8Vehicle.Model.COMPACT;
        if (r < 60) return Gta8Vehicle.Model.SUV;
        if (r < 68) return Gta8Vehicle.Model.TAXI;
        if (r < 78) return Gta8Vehicle.Model.PICKUP;
        if (r < 86) return Gta8Vehicle.Model.VAN;
        if (r < 93) return Gta8Vehicle.Model.MUSCLE;
        return Gta8Vehicle.Model.SPORTS;
    }

    // ------------------------------------------------------------------ driving
    void drive(Gta8Vehicle car, double dt) {
        Brain b = car.ai;
        if (b.next == null && b.connector == null) b.next = choose(b.lane);
        gatherObstacles(car);
        double gap = 60, leadSpeed = b.speed;
        int samples = samplePath(car, 55);
        double hw = car.halfWidth() + .35;
        for (Gta8Vehicle o : nearby) {
            double r = Math.max(o.halfWidth(), Math.min(o.halfLength(), 2.2));
            for (int i = 1; i < samples; i++) {
                double dx = o.x - px[i], dz = o.z - pz[i];
                if (dx * dx + dz * dz > (r + hw) * (r + hw)) continue;
                double dist = pd[i] - car.halfLength() - r * .9;
                if (dist < gap) { gap = dist; leadSpeed = Math.max(0, o.speed()); }
                break;
            }
        }
        for (Gta8Ped p : nearbyPeds) {
            for (int i = 1; i < samples; i++) {
                double dx = p.x - px[i], dz = p.z - pz[i];
                if (dx * dx + dz * dz > (.5 + hw) * (.5 + hw)) continue;
                double dist = pd[i] - car.halfLength() - .8;
                if (dist < gap) { gap = dist; leadSpeed = 0; }
                if (p == game.player && dist < 8 && b.speed < 1) b.honkTimer += dt;
                break;
            }
        }
        // Signals, left-turn yielding and keeping the junction clear.
        if (b.connector == null && b.next != null) {
            double stop = b.lane.length - b.s - car.halfLength() + 2.0;
            Gta8World.Signal signal = game.world.signalGrid[b.lane.toI][b.lane.toJ];
            int state = game.world.signalState(signal, b.lane.dir, game.time);
            boolean mustStop = false;
            if (!b.panic) {
                if (state == Gta8World.RED_LIGHT) mustStop = stop > -.5;
                else if (state == Gta8World.YELLOW_LIGHT) mustStop = stop > b.speed * b.speed / (2 * 3.8) + 1.5;
                else if (b.next.turn == -1 && oncoming(b, signal)) mustStop = stop > -.2;
                if (!mustStop && stop < 18 && exitBlocked(b.next.to)) mustStop = stop > -.2;
                if (!signal.active && stop < 8 && crossTraffic(car, b)) mustStop = stop > -.2;
            }
            if (mustStop && stop < gap) { gap = Math.max(0, stop); leadSpeed = 0; }
        }
        double limit = b.connector != null ? (b.connector.turn == 0 ? b.lane.speedLimit : 6.8) : b.lane.speedLimit;
        double v0 = limit * b.aggression * (b.panic ? 1.35 : 1) * (1 - .25 * game.weather.wet);
        if (b.connector == null && b.next != null && b.next.turn != 0) {
            double toTurn = b.lane.length - b.s;
            if (toTurn < 30) v0 = Math.min(v0, 6.8 + toTurn * .35);
        }
        // Pull over for an approaching siren.
        boolean yield = sirenBehind(car);
        b.offset = Gta8Math.approach(b.offset, yield ? 1.3 : 0, dt * .8);
        if (yield) v0 = Math.min(v0, 4);
        double amax = 1.9 * b.aggression, comfort = 2.9, s0 = 2.3, headway = 1.35 / b.aggression;
        double v = b.speed;
        double sStar = s0 + v * headway + v * (v - leadSpeed) / (2 * Math.sqrt(amax * comfort));
        double acc = amax * (1 - Math.pow(v / Math.max(.1, v0), 4) - Math.pow(Math.max(0, sStar) / Math.max(.1, gap), 2));
        acc = Gta8Math.clamp(acc, -9, amax);
        v = Math.max(0, v + acc * dt);
        if (gap < .25) v = 0;
        b.waitTime = v < .3 ? b.waitTime + dt : 0;
        b.speed = v;
        advance(car, b, v * dt);
        place(car, dt);
        car.brake = acc < -1.2 || v < .05 ? 1 : 0;
        car.throttle = acc > .2 ? Math.min(1, acc / amax) : 0;
        car.indicator = 0;
        Connector turning = b.connector != null ? b.connector : b.next;
        if (turning != null && turning.turn != 0 && (b.connector != null || b.lane.length - b.s < 35)) car.indicator = turning.turn;
        if (b.honkTimer > 2.5) { car.horn = true; b.honkTimer = -3; game.event(Gta8Game.Event.HORN, car.x, car.y, car.z, 1); }
        else car.horn = false;
    }

    private void gatherObstacles(Gta8Vehicle car) {
        nearby.clear(); nearbyPeds.clear();
        for (Gta8Vehicle o : game.vehicles) {
            if (o == car) continue;
            double dx = o.x - car.x, dz = o.z - car.z;
            if (dx * dx + dz * dz > 62 * 62) continue;
            // Only things ahead of us matter.
            if (dx * car.forwardX() + dz * car.forwardZ() < -2) continue;
            nearby.add(o);
        }
        for (Gta8Ped p : game.peds) {
            if (p.dead && p.fall < .5 || p.vehicle != null) continue;
            double dx = p.x - car.x, dz = p.z - car.z;
            if (dx * dx + dz * dz > 40 * 40 || dx * car.forwardX() + dz * car.forwardZ() < 0) continue;
            nearbyPeds.add(p);
        }
        Gta8Ped player = game.player;
        if (player.vehicle == null && !nearbyPeds.contains(player)) {
            double dx = player.x - car.x, dz = player.z - car.z;
            if (dx * dx + dz * dz < 40 * 40 && dx * car.forwardX() + dz * car.forwardZ() > 0) nearbyPeds.add(player);
        }
    }

    /** Samples the path ahead into px/pz with cumulative distance pd. Returns the sample count. */
    private int samplePath(Gta8Vehicle car, double range) {
        Brain b = car.ai;
        int n = 0;
        px[n] = car.x; pz[n] = car.z; pd[n] = 0; n++;
        double s = b.s, travelled = 0;
        Lane lane = b.lane; Connector con = b.connector, next = b.next;
        double step = 2.2;
        while (travelled < range && n < px.length) {
            travelled += step; s += step;
            double len = con != null ? con.length : lane.length;
            if (s > len) {
                s -= len;
                if (con != null) { lane = con.to; con = null; next = null; }
                else if (next != null) { con = next; next = null; }
                else break;
            }
            if (con != null) { con.point(Gta8Math.clamp(s / con.length, 0, 1), tmp); px[n] = tmp[0]; pz[n] = tmp[1]; }
            else { px[n] = lane.x0 + Gta8World.DIR_X[lane.dir] * s; pz[n] = lane.z0 + Gta8World.DIR_Z[lane.dir] * s; }
            pd[n] = travelled; n++;
        }
        return n;
    }

    private boolean oncoming(Brain b, Gta8World.Signal signal) {
        int opposite = (b.lane.dir + 2) % 4;
        for (Lane l : game.world.incoming(b.lane.toI, b.lane.toJ, opposite)) {
            for (Gta8Vehicle o : game.vehicles) {
                if (o.ai == null || o.ai.lane != l && (o.ai.connector == null || o.ai.connector.from != l)) continue;
                if (o.ai.connector != null && o.ai.connector.turn != -1) return true;
                double toStop = l.length - o.ai.s;
                if (o.ai.connector == null && toStop < 42 && o.ai.speed > 1.5 && (o.ai.next == null || o.ai.next.turn != -1)) return true;
            }
        }
        return false;
    }
    private boolean exitBlocked(Lane exit) {
        for (Gta8Vehicle o : game.vehicles) {
            if (o.ai != null && o.ai.lane == exit && o.ai.connector == null && o.ai.s < 9 && o.ai.speed < 1.5) return true;
            if (o.ai == null && exitArea(exit, o.x, o.z)) return true;
        }
        return false;
    }
    private static boolean exitArea(Lane l, double x, double z) {
        double along = (x - l.x0) * Gta8World.DIR_X[l.dir] + (z - l.z0) * Gta8World.DIR_Z[l.dir];
        double across = Math.abs((x - l.x0) * -Gta8World.DIR_Z[l.dir] + (z - l.z0) * Gta8World.DIR_X[l.dir]);
        return along > -2 && along < 8 && across < 2;
    }
    private boolean crossTraffic(Gta8Vehicle car, Brain b) {
        for (Gta8Vehicle o : game.vehicles) {
            if (o == car || o.ai == null || o.ai.connector == null) continue;
            if (o.ai.connector.from.toI == b.lane.toI && o.ai.connector.from.toJ == b.lane.toJ) return true;
        }
        return false;
    }
    private boolean sirenBehind(Gta8Vehicle car) {
        for (Gta8Vehicle o : game.vehicles) {
            if (!o.siren || o == car) continue;
            double dx = o.x - car.x, dz = o.z - car.z;
            if (dx * dx + dz * dz > 45 * 45) continue;
            if (dx * car.forwardX() + dz * car.forwardZ() < 0) return true;
        }
        return false;
    }

    private Connector choose(Lane lane) {
        if (lane.next.isEmpty()) return null;
        double total = 0;
        for (Connector c : lane.next) total += c.turn == 0 ? 3 : 1;
        double r = random.nextDouble() * total;
        for (Connector c : lane.next) { r -= c.turn == 0 ? 3 : 1; if (r <= 0) return c; }
        return lane.next.get(0);
    }
    private void advance(Gta8Vehicle car, Brain b, double distance) {
        b.s += distance;
        for (int guard = 0; guard < 4; guard++) {
            double len = b.connector != null ? b.connector.length : b.lane.length;
            if (b.s <= len) return;
            b.s -= len;
            if (b.connector != null) { b.lane = b.connector.to; b.connector = null; b.next = choose(b.lane); }
            else if (b.next != null) { b.connector = b.next; b.next = null; }
            else { b.s = len; b.speed = 0; return; }
        }
    }
    /** Sets pose and velocity from the path; steering follows the path curvature. */
    void place(Gta8Vehicle car, double dt) {
        Brain b = car.ai;
        double x, z, tx, tz;
        if (b.connector != null) {
            double t = Gta8Math.clamp(b.s / b.connector.length, 0, 1);
            b.connector.point(t, tmp); x = tmp[0]; z = tmp[1];
            b.connector.tangent(t, tmp); double l = Math.hypot(tmp[0], tmp[1]); tx = tmp[0] / l; tz = tmp[1] / l;
        } else {
            tx = Gta8World.DIR_X[b.lane.dir]; tz = Gta8World.DIR_Z[b.lane.dir];
            x = b.lane.x0 + tx * b.s; z = b.lane.z0 + tz * b.s;
        }
        x += -tz * b.offset; z += tx * b.offset;
        double yaw = Math.toDegrees(Math.atan2(tx, -tz));
        double yawRate = dt > 0 ? Math.toRadians(Gta8Math.angleDelta(car.yaw, yaw)) / dt : 0;
        car.steer = Gta8Math.damp(car.steer, b.speed > .5 ? Math.atan(yawRate * car.model.wheelbase / Math.max(.5, b.speed)) : car.steer, 10, dt);
        car.vx = tx * b.speed; car.vz = tz * b.speed; car.yawRate = yawRate;
        double accel = dt > 0 ? (b.speed - Math.hypot(car.vx, car.vz)) / dt : 0;
        car.x = x; car.z = z; car.yaw = yaw;
        car.y = game.world.groundHeight(x, z);
        car.bodyPitch = Gta8Math.damp(car.bodyPitch, Gta8Math.clamp((car.brake > 0 ? -2.2 : 0) + (car.throttle > .5 ? .8 : 0), -3, 2), 6, Math.max(dt, 1e-3));
        car.bodyRoll = Gta8Math.damp(car.bodyRoll, Gta8Math.clamp(b.speed * yawRate * .45, -4, 4), 6, Math.max(dt, 1e-3));
        car.pitch = car.bodyPitch; car.roll = car.bodyRoll;
        car.wheelSpin += b.speed / car.model.wheelRadius() * dt;
        car.rpm = 850 + b.speed * 110;
    }
    /** Hands the car over to rigid-body physics (crashes, carjackings). */
    static void release(Gta8Vehicle car) {
        if (car.ai == null) return;
        car.dynamic = true;
        car.ai = null;
    }
}
