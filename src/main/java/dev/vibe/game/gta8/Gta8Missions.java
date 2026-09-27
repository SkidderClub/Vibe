package dev.vibe.game.gta8;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Job board contracts: courier runs, vehicle theft to order and contract hits. */
public final class Gta8Missions {
    public enum Type { DELIVERY, CAR_THEFT, HIT }
    public static final class Offer {
        public final Type type; public final String title, description; public final int reward;
        final double ax, az, bx, bz; final Gta8Vehicle.Model model;
        Offer(Type type, String title, String description, int reward, double ax, double az, double bx, double bz, Gta8Vehicle.Model model) {
            this.type = type; this.title = title; this.description = description; this.reward = reward; this.ax = ax; this.az = az; this.bx = bx; this.bz = bz; this.model = model;
        }
    }

    private final Gta8Game game;
    private final Random random;
    public Offer active;
    public int stage;
    public double timer, limit;
    public Gta8Vehicle car;
    public Gta8Ped target;
    public final List<Gta8Ped> guards = new ArrayList<Gta8Ped>();
    public boolean marker;
    public double markerX, markerZ;
    public String objective = "";
    private final List<Offer> board = new ArrayList<Offer>();
    private double boardRefresh = -1;

    Gta8Missions(Gta8Game game, Random random) { this.game = game; this.random = random; }

    public List<Offer> offers() {
        if (board.isEmpty() || game.time > boardRefresh) {
            board.clear();
            boardRefresh = game.time + 240;
            Gta8World w = game.world;
            double[] a = randomPoint(250, 520), b = randomPoint(300, 700);
            int reward = (int) (450 + Math.hypot(a[0] - b[0], a[1] - b[1]) * 2.2) / 10 * 10;
            board.add(new Offer(Type.DELIVERY, "Courier run", "Collect a parcel in " + w.districtName(a[0], a[1]) + " and deliver it to " + w.districtName(b[0], b[1]) + " before the deadline.", reward, a[0], a[1], b[0], b[1], null));
            Gta8World.ParkingSpot spot = w.parking.get(random.nextInt(w.parking.size()));
            for (int i = 0; i < 30 && Math.hypot(spot.x - game.player.x, spot.z - game.player.z) < 250; i++) spot = w.parking.get(random.nextInt(w.parking.size()));
            Gta8Vehicle.Model model = random.nextBoolean() ? Gta8Vehicle.Model.SPORTS : Gta8Vehicle.Model.MUSCLE;
            board.add(new Offer(Type.CAR_THEFT, "Special order", "A client wants a " + model.title + " parked on " + w.streetName(spot.x, spot.z) + ". Deliver it to the Port garage with as little damage as possible.",
                    2800, spot.x, spot.z, 540, 60, model));
            double[] h = randomPoint(260, 560);
            board.add(new Offer(Type.HIT, "Contract", "Eliminate a loan shark and his bodyguards in " + w.districtName(h[0], h[1]) + ", then lose any police attention.", 4200, h[0], h[1], 0, 0, null));
        }
        return board;
    }
    private double[] randomPoint(double min, double max) {
        List<Gta8World.WalkNode> nodes = game.world.walkNodes;
        for (int i = 0; i < 80; i++) {
            Gta8World.WalkNode n = nodes.get(random.nextInt(nodes.size()));
            double d = Math.hypot(n.x - game.player.x, n.z - game.player.z);
            if (d > min && d < max) return new double[]{n.x, n.z};
        }
        Gta8World.WalkNode n = nodes.get(random.nextInt(nodes.size()));
        return new double[]{n.x, n.z};
    }

    public void start(Offer o) {
        if (active != null) cancel();
        active = o; stage = 0; timer = 0; limit = 0;
        board.remove(o);
        marker = true;
        switch (o.type) {
            case DELIVERY: markerX = o.ax; markerZ = o.az; objective = "Pick up the parcel."; break;
            case CAR_THEFT: {
                car = new Gta8Vehicle(o.model, o.ax, o.az, 0, 0x8A1C1C);
                for (Gta8World.ParkingSpot s : game.world.parking) if (s.x == o.ax && s.z == o.az) car.yaw = s.yaw;
                car.dynamic = true; car.parked = true; car.locked = true; car.mission = true;
                car.y = game.world.groundHeight(car.x, car.z);
                game.addVehicle(car);
                markerX = o.ax; markerZ = o.az; objective = "Steal the " + o.model.title + ".";
                break;
            }
            default: {
                target = game.spawnGangster(o.ax, o.az, Gta8Ped.Kind.TARGET);
                target.weapon = Gta8Weapon.SMG;
                for (int i = 0; i < 3; i++) guards.add(game.spawnGangster(o.ax + Math.cos(i * 2.1) * 3, o.az + Math.sin(i * 2.1) * 3, Gta8Ped.Kind.GANG));
                markerX = o.ax; markerZ = o.az; objective = "Eliminate the loan shark.";
            }
        }
        game.message("Job accepted: " + o.title + ". " + objective);
        game.event(Gta8Game.Event.MISSION_START, markerX, 0, markerZ, 0);
    }
    public void cancel() {
        if (active == null) return;
        if (car != null) car.mission = false;
        car = null; target = null; guards.clear(); active = null; marker = false; objective = "";
    }
    private void pass(int reward) {
        game.progress.money += reward;
        game.progress.missions++;
        game.bigMessage("JOB COMPLETE", "+$" + reward);
        game.event(Gta8Game.Event.MISSION_PASSED, game.player.x, game.player.y, game.player.z, reward);
        game.progress.save();
        cancel();
    }
    private void fail(String reason) {
        game.bigMessage("JOB FAILED", reason);
        game.event(Gta8Game.Event.MISSION_FAILED, game.player.x, game.player.y, game.player.z, 0);
        cancel();
    }

    void update(double dt) {
        if (active == null) return;
        timer += dt;
        Gta8Ped p = game.player;
        double px = p.vehicle != null ? p.vehicle.x : p.x, pz = p.vehicle != null ? p.vehicle.z : p.z;
        double d = Math.hypot(markerX - px, markerZ - pz);
        switch (active.type) {
            case DELIVERY:
                if (stage == 0 && d < 3.5) {
                    stage = 1; timer = 0; markerX = active.bx; markerZ = active.bz;
                    limit = Math.hypot(active.bx - active.ax, active.bz - active.az) / 11 + 45;
                    objective = "Deliver the parcel to " + game.world.districtName(markerX, markerZ) + ".";
                    game.message("Parcel collected. " + objective);
                    game.event(Gta8Game.Event.PICKUP, px, p.y, pz, 0);
                } else if (stage == 1) {
                    if (timer > limit) fail("You missed the deadline.");
                    else if (d < 3.5) pass(active.reward);
                }
                break;
            case CAR_THEFT:
                if (car == null || car.destroyed()) { fail("The vehicle was destroyed."); break; }
                if (stage == 0) { markerX = car.x; markerZ = car.z; if (p.vehicle == car) { stage = 1; markerX = active.bx; markerZ = active.bz; objective = "Deliver the car to the Port garage."; game.message(objective); } }
                else if (p.vehicle != car) { markerX = car.x; markerZ = car.z; objective = "Get back in the " + active.model.title + "."; }
                else {
                    markerX = active.bx; markerZ = active.bz; objective = "Deliver the car to the Port garage.";
                    if (d < 5 && Math.abs(car.speed()) < 2) {
                        int reward = (int) (active.reward * Gta8Math.clamp(car.health / 1000, .2, 1)) / 10 * 10;
                        game.exitVehicle(p, car);
                        car.mission = false; car.locked = true;
                        pass(reward);
                    }
                }
                break;
            default:
                if (target == null) break;
                if (stage == 0) {
                    markerX = target.x; markerZ = target.z;
                    if (target.dead) { stage = 1; marker = false; objective = game.police.wanted > 0 ? "Lose the cops." : "Leave the area."; game.message("Target eliminated. " + objective); }
                    else if (Math.hypot(target.x - p.x, target.z - p.z) > 900) fail("The target got away.");
                } else if (game.police.wanted == 0 && Math.hypot(target.x - p.x, target.z - p.z) > 60) pass(active.reward);
        }
    }
}
