package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8Ped.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Wanted levels with line-of-sight search areas: officers who see the player re-centre the search;
 * leaving the circle unseen long enough ends the pursuit. Patrol cars route through the street grid,
 * then chase directly and ram; officers arrest at one star and open fire from two stars.
 */
public final class Gta8Police {
    public enum Crime {
        ASSAULT(1, 1), SHOOTING(1, 1), CARJACK(1, 1), RUN_OVER(1, 2), ROBBERY(2, 3), KILL(1, 3), ATTACK_COP(2, 4), KILL_COP(3, 6), EXPLOSION(2, 3);
        final int stars; final double heat;
        Crime(int stars, double heat) { this.stars = stars; this.heat = heat; }
    }
    public static final class Helicopter {
        public double x, y, z, yaw, vx, vz, rotor, tilt, leaving, orbit;
        public double spotX, spotZ;
        public double prevX, prevY, prevZ, prevYaw;
    }

    private final Gta8Game game;
    private final Random random;
    public int wanted;
    public double heat, flash;
    public boolean searching, seen;
    public double searchX, searchZ, lastSeenX, lastSeenZ, unseenTimer, evadeTimer, sinceCrime = 999;
    public Helicopter heli;
    private double dispatchTimer, sightTimer;
    private final List<double[]> route = new ArrayList<double[]>();
    private double pendingCrimeX, pendingCrimeZ, pendingTime = -99;
    private Crime pendingCrime;
    public boolean playerHostile;
    private final double[] hitTmp = new double[1];

    Gta8Police(Gta8Game game, Random random) { this.game = game; this.random = random; }

    public double searchRadius() { return 55 + 38 * wanted; }

    /** A crime committed at (x, z). Officers who can see it raise the level immediately. */
    void crime(Crime c, double x, double z) {
        sinceCrime = 0;
        boolean copSaw = false;
        for (Gta8Ped p : game.peds) {
            if (p.kind != Gta8Ped.Kind.COP || p.dead) continue;
            double d = Math.hypot(p.x - x, p.z - z);
            if (d < 25 || d < 90 && game.canSee(p, game.player)) { copSaw = true; break; }
        }
        if (heli != null && Math.hypot(heli.x - x, heli.z - z) < 120) copSaw = true;
        if (c == Crime.ATTACK_COP || c == Crime.KILL_COP) { copSaw = true; playerHostile = true; }
        if (copSaw || wanted > 0) raise(c, x, z);
        else {
            // Unseen crimes can still be phoned in: gunfire and explosions are heard blocks away.
            boolean loud = c == Crime.SHOOTING || c == Crime.KILL || c == Crime.EXPLOSION || c == Crime.ROBBERY;
            boolean fresh = pendingCrime == null || game.time - pendingTime > 90;
            if (fresh || c.heat > pendingCrime.heat) { pendingCrime = c; pendingCrimeX = x; pendingCrimeZ = z; pendingTime = game.time; }
            if (fresh || callDelay < 0) callDelay = random.nextDouble() < (loud ? .8 : .3) ? 6 + random.nextDouble() * 9 : -1;
        }
    }
    private double callDelay = -1;
    /** A civilian finished a phone call about a recent crime. */
    void witnessReport(Gta8Ped witness) {
        if (pendingCrime != null && game.time - pendingTime < 90) {
            raise(pendingCrime, pendingCrimeX, pendingCrimeZ);
            pendingCrime = null;
            game.message("A witness reported you to the police.");
        }
    }
    private void raise(Crime c, double x, double z) {
        int before = wanted;
        heat += c.heat;
        int level = Math.max(c.stars, Math.min(5, 1 + (int) (heat / 7)));
        wanted = Math.min(5, Math.max(wanted, level));
        if (c == Crime.SHOOTING || c == Crime.ATTACK_COP || c == Crime.KILL || c == Crime.KILL_COP || c == Crime.ROBBERY || c == Crime.EXPLOSION) playerHostile = true;
        lastSeenX = x; lastSeenZ = z;
        searchX = x; searchZ = z;
        if (wanted > before) {
            flash = 3;
            dispatchTimer = 0;
            game.event(Gta8Game.Event.WANTED, x, 0, z, wanted);
        }
    }
    public void clear() {
        wanted = 0; heat = 0; searching = false; evadeTimer = unseenTimer = 0; playerHostile = false; pendingCrime = null;
        for (Gta8Ped p : game.peds) if (p.kind == Gta8Ped.Kind.COP) { p.arresting = false; if (p.state != State.DRIVE && !p.dead) p.state = State.WANDER; }
        for (Gta8Vehicle v : game.vehicles) if (v.model == Gta8Vehicle.Model.POLICE) v.siren = false;
        if (heli != null) heli.leaving = 1;
    }

    void update(double dt) {
        flash = Math.max(0, flash - dt);
        sinceCrime += dt;
        if (callDelay > 0 && pendingCrime != null) {
            callDelay -= dt;
            if (callDelay <= 0) { raise(pendingCrime, pendingCrimeX, pendingCrimeZ); pendingCrime = null; game.message("Someone called 911."); }
        }
        Gta8Ped player = game.player;
        if (wanted > 0) {
            sightTimer -= dt;
            if (sightTimer <= 0) {
                sightTimer = .25;
                seen = false;
                for (Gta8Ped p : game.peds) {
                    if (p.kind != Gta8Ped.Kind.COP || p.dead) continue;
                    double d = Math.hypot(p.x - player.x, p.z - player.z);
                    if (d < 90 && (d < 6 || game.canSee(p, player))) { seen = true; break; }
                }
                if (heli != null && heli.leaving == 0 && Math.hypot(heli.x - player.x, heli.z - player.z) < 70 && !game.cameraIndoors) seen = true;
            }
            if (seen) {
                lastSeenX = player.x; lastSeenZ = player.z; unseenTimer = 0; evadeTimer = 0;
                if (searching) { searching = false; flash = 1.5; }
            } else {
                unseenTimer += dt;
                if (unseenTimer > 3 && !searching) { searching = true; searchX = lastSeenX; searchZ = lastSeenZ; }
            }
            if (searching) {
                boolean outside = Math.hypot(player.x - searchX, player.z - searchZ) > searchRadius();
                evadeTimer = outside ? evadeTimer + dt : Math.max(0, evadeTimer - dt * .5);
                if (evadeTimer > 7 + 2.5 * wanted) {
                    game.message("You lost the police.");
                    game.event(Gta8Game.Event.EVADED, player.x, player.y, player.z, 0);
                    clear();
                }
            }
            dispatch(dt);
        }
        for (Gta8Vehicle v : game.vehicles) if (v.model == Gta8Vehicle.Model.POLICE && v.dynamic && v.driver != null && !v.driver.dead && v.driver.kind == Gta8Ped.Kind.COP) drive(v, dt);
        helicopter(dt);
    }

    // ------------------------------------------------------------------ dispatch
    private void dispatch(double dt) {
        dispatchTimer -= dt;
        if (dispatchTimer > 0) return;
        dispatchTimer = 5;
        int cars = 0, officers = 0;
        for (Gta8Vehicle v : game.vehicles) if (v.model == Gta8Vehicle.Model.POLICE && v.siren) cars++;
        for (Gta8Ped p : game.peds) if (p.kind == Gta8Ped.Kind.COP && !p.dead) officers++;
        int wantCars = Math.min(6, wanted + (wanted >= 3 ? 1 : 0));
        if (cars < wantCars && officers < 4 + wanted * 3) spawnCar();
        if (wanted >= 3 && heli == null) spawnHeli();
    }
    private void spawnCar() {
        Gta8Ped player = game.player;
        Gta8World.Lane best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 40; i++) {
            Gta8World.Lane l = game.world.lanes.get(random.nextInt(game.world.lanes.size()));
            double x = (l.x0 + l.x1) / 2, z = (l.z0 + l.z1) / 2;
            double d = Math.hypot(x - player.x, z - player.z);
            if (d < 110 || d > 260 || game.visible(x, 1, z, 3) && d < 200) continue;
            double score = Math.abs(d - 160);
            if (score < bestScore) { bestScore = score; best = l; }
        }
        if (best == null) return;
        double x = (best.x0 + best.x1) / 2, z = (best.z0 + best.z1) / 2;
        Gta8Vehicle car = new Gta8Vehicle(Gta8Vehicle.Model.POLICE, x, z, best.yaw(), 0x16181C);
        car.dynamic = true; car.siren = true;
        car.y = game.world.groundHeight(x, z);
        car.vx = car.forwardX() * 12; car.vz = car.forwardZ() * 12;
        Gta8Ped driver = game.createCop(x, z), partner = game.createCop(x, z);
        driver.vehicle = car; driver.state = State.DRIVE;
        partner.vehicle = car; partner.state = State.DRIVE;
        car.driver = driver;
        game.addVehicle(car);
        game.peds.add(driver); game.peds.add(partner);
    }
    private void spawnHeli() {
        heli = new Helicopter();
        Gta8Ped p = game.player;
        double a = random.nextDouble() * Math.PI * 2;
        heli.x = p.x + Math.cos(a) * 320; heli.z = p.z + Math.sin(a) * 320; heli.y = 90;
        heli.prevX = heli.x; heli.prevY = heli.y; heli.prevZ = heli.z;
        game.message("Police helicopter en route.");
    }

    // ------------------------------------------------------------------ patrol car driving
    private void drive(Gta8Vehicle car, double dt) {
        Gta8Ped player = game.player;
        car.siren = wanted > 0;
        if (wanted == 0) { car.throttle = 0; car.brake = 1; car.handbrake = false; return; }
        double tx = searching ? searchX : player.x, tz = searching ? searchZ : player.z;
        if (!searching && player.vehicle != null) { tx += player.vehicle.vx * .6; tz += player.vehicle.vz * .6; }
        double dist = Math.hypot(tx - car.x, tz - car.z);
        boolean direct = dist < 55 && !searching;
        double wx = tx, wz = tz;
        if (!direct) {
            if (car.route == null || game.time - car.routeTime > 1.2) { car.route = game.world.route(car.x, car.z, tx, tz); car.routeTime = game.time; }
            List<double[]> path = car.route;
            // Aim at the first intersection ahead of us, keeping right of the centre line.
            for (double[] p : path) {
                double dx = p[0] - car.x, dz = p[1] - car.z;
                if (Math.hypot(dx, dz) > 14 && dx * car.forwardX() + dz * car.forwardZ() > -4) { wx = p[0]; wz = p[1]; break; }
                wx = p[0]; wz = p[1];
            }
            if (Math.hypot(wx - car.x, wz - car.z) < 10 && path.size() <= 1) { wx = tx; wz = tz; }
        }
        // Officers on foot take over when the player is close and on foot.
        if (player.vehicle == null && dist < 20 && !searching) {
            car.throttle = 0; car.brake = 1;
            if (car.speed() < 1.5) disembark(car);
            return;
        }
        double lx = (wx - car.x) * car.rightX() + (wz - car.z) * car.rightZ();
        double lf = (wx - car.x) * car.forwardX() + (wz - car.z) * car.forwardZ();
        double ld = Math.max(6, Math.hypot(lx, lf));
        double curvature = 2 * lx / (ld * ld);
        double steer = Gta8Math.clamp(Math.atan(curvature * car.model.wheelbase), -.62, .62);
        double speed = car.speed();
        double bend = Math.abs(Math.atan2(lx, Math.max(.1, lf)));
        double desired = direct ? (player.vehicle != null ? 38 : 14) : 30;
        if (bend > .35) desired = Math.min(desired, Math.max(7, 22 - bend * 14));
        if (lf < 0) { desired = 8; steer = Math.signum(lx) * .62; }
        // Obstacle probe ahead.
        double probe = game.world.ray(car.worldX(0, car.halfLength()), car.y + .8, car.worldZ(0, car.halfLength()), car.forwardX(), 0, car.forwardZ(), 12);
        if (probe < 10) {
            double left = game.world.ray(car.x, car.y + .8, car.z, car.forwardX() - car.rightX() * .6, 0, car.forwardZ() - car.rightZ() * .6, 16);
            double right = game.world.ray(car.x, car.y + .8, car.z, car.forwardX() + car.rightX() * .6, 0, car.forwardZ() + car.rightZ() * .6, 16);
            steer = right > left ? .55 : -.55;
            desired = Math.min(desired, probe * 1.2);
        }
        car.stuck = speed < 1.2 && car.throttle > .3 ? car.stuck + dt : Math.max(0, car.stuck - dt);
        if (car.stuck > 1.8) { car.stuck = -1.6; }
        if (car.stuck < 0) {
            car.stuck += dt * 2;
            car.throttle = -.8; car.brake = 0; car.steer = -steer; car.handbrake = false;
            return;
        }
        car.steer = Gta8Math.approach(car.steer, steer, dt * 2.5);
        car.throttle = speed < desired ? Gta8Math.clamp((desired - speed) / 6, .25, 1) : 0;
        car.brake = speed > desired + 3 ? Gta8Math.clamp((speed - desired) / 8, 0, 1) : 0;
        car.handbrake = bend > 1.2 && speed > 12;
    }
    private void disembark(Gta8Vehicle car) {
        for (Gta8Ped p : game.peds) {
            if (p.vehicle != car || p.kind != Gta8Ped.Kind.COP) continue;
            game.exitVehicle(p, car);
            p.state = State.CHASE;
        }
        car.driver = null;
    }

    // ------------------------------------------------------------------ officers on foot
    void officer(Gta8Ped cop, double dt) {
        Gta8Ped player = game.player;
        if (cop.dead || cop.state == State.RAGDOLL || cop.state == State.DRIVE) return;
        cop.flinch = Math.max(0, cop.flinch - dt);
        if (wanted == 0) {
            cop.aim = Math.max(0, cop.aim - dt * 2);
            game.walk(cop, 0, 0, 0, dt, true);
            return;
        }
        if (cop.weapon == Gta8Weapon.FISTS) cop.weapon = wanted >= 4 ? Gta8Weapon.RIFLE : wanted >= 3 ? Gta8Weapon.SMG : Gta8Weapon.PISTOL;
        double tx = seen || !searching ? player.x : searchX, tz = seen || !searching ? player.z : searchZ;
        double dx = tx - cop.x, dz = tz - cop.z, dist = Math.hypot(dx, dz);
        cop.thinkTimer -= dt;
        if (cop.thinkTimer <= 0) { cop.thinkTimer = .3; cop.provoked = game.canSee(cop, player); }
        boolean sees = cop.provoked && !searching;
        boolean arrest = wanted == 1 && !playerHostile;
        double dirX = 0, dirZ = 0, speed = 0;
        if (dist > .01) { dirX = dx / dist; dirZ = dz / dist; }
        if (arrest) {
            cop.aim = Math.min(1, cop.aim + dt * 3);
            if (dist > 1.6) speed = dist > 8 ? 4.6 : 2.2;
            cop.arresting = dist < 2.6;
        } else if (sees) {
            cop.aim = Math.min(1, cop.aim + dt * 4);
            double ideal = cop.weapon == Gta8Weapon.PISTOL ? 11 : 16;
            if (dist > ideal + 4) speed = 4.8;
            else if (dist < ideal - 5) { speed = 2.5; dirX = -dirX; dirZ = -dirZ; }
            else {
                cop.strafe += dt;
                double side = Math.sin(cop.strafe * .8 + cop.id) > 0 ? 1 : -1;
                double sx = -dirZ * side, sz = dirX * side;
                dirX = sx; dirZ = sz; speed = 1.6;
            }
            shoot(cop, dt, dist);
        } else {
            cop.aim = Math.max(.4, cop.aim - dt);
            speed = dist > 3 ? 5.2 : 0;
            if (searching && dist < 6) { cop.targetX = searchX + (random.nextDouble() - .5) * searchRadius(); cop.targetZ = searchZ + (random.nextDouble() - .5) * searchRadius(); }
        }
        game.walk(cop, dirX, dirZ, speed, dt, false);
        if (sees || arrest) cop.yaw = Math.toDegrees(Math.atan2(player.x - cop.x, -(player.z - cop.z)));
    }
    private void shoot(Gta8Ped cop, double dt, double dist) {
        cop.shotTimer -= dt;
        if (cop.shotTimer > 0 || game.player.dead) return;
        Gta8Weapon w = cop.weapon;
        if (cop.burstLeft <= 0) { cop.burstLeft = w.automatic ? 3 + random.nextInt(4) : 1; cop.shotTimer = .5 + random.nextDouble() * .9; return; }
        cop.burstLeft--;
        cop.shotTimer = Math.max(w.interval * 1.3, w.automatic ? .09 : .45 + random.nextDouble() * .4);
        Gta8Ped player = game.player;
        double moving = player.vehicle != null ? player.vehicle.speed() : player.speed();
        double accuracy = Gta8Math.clamp(.6 - dist * .011 - Math.abs(moving) * .025 - (player.crouch > .5 ? .1 : 0), .07, .62) * (wanted >= 4 ? 1.15 : 1);
        game.npcShot(cop, player, random.nextDouble() < accuracy, w);
    }

    /** Ped currently able to arrest the player, used for busted checks. */
    boolean arresting(Gta8Ped player) {
        if (wanted != 1 || playerHostile) return false;
        for (Gta8Ped p : game.peds) if (p.kind == Gta8Ped.Kind.COP && p.arresting && !p.dead && Math.hypot(p.x - player.x, p.z - player.z) < 2.6) return true;
        return false;
    }

    // ------------------------------------------------------------------ helicopter
    private void helicopter(double dt) {
        if (heli == null) return;
        Helicopter h = heli;
        h.prevX = h.x; h.prevY = h.y; h.prevZ = h.z; h.prevYaw = h.yaw;
        h.rotor += dt * 26;
        Gta8Ped p = game.player;
        double tx, tz, ty;
        if (h.leaving > 0 || wanted < 3) {
            h.leaving = Math.max(h.leaving, 1);
            double a = Math.atan2(h.z - p.z, h.x - p.x);
            tx = h.x + Math.cos(a) * 200; tz = h.z + Math.sin(a) * 200; ty = 130;
            if (Math.hypot(h.x - p.x, h.z - p.z) > 600) { heli = null; return; }
        } else {
            h.orbit += dt * .22;
            double cx = searching ? searchX : p.x, cz = searching ? searchZ : p.z;
            tx = cx + Math.cos(h.orbit) * 38; tz = cz + Math.sin(h.orbit) * 38;
            ty = Math.max(game.world.groundHeight(tx, tz), 0) + 48;
            h.spotX = Gta8Math.damp(h.spotX, searching ? searchX + Math.sin(game.time * .7) * searchRadius() * .5 : p.x, 3, dt);
            h.spotZ = Gta8Math.damp(h.spotZ, searching ? searchZ + Math.cos(game.time * .5) * searchRadius() * .5 : p.z, 3, dt);
            if (wanted >= 4 && !searching && seen) {
                h.leaving = 0;
                shotClock += dt;
                if (shotClock > 2.2) {
                    shotClock = 0;
                    Gta8Ped sniper = heliSniper();
                    game.npcShot(sniper, p, random.nextDouble() < .28, Gta8Weapon.RIFLE);
                }
            }
        }
        double dx = tx - h.x, dz = tz - h.z, d = Math.hypot(dx, dz);
        double max = 26;
        double wantVx = d > .1 ? dx / d * Math.min(max, d * .8) : 0, wantVz = d > .1 ? dz / d * Math.min(max, d * .8) : 0;
        h.vx = Gta8Math.damp(h.vx, wantVx, 1.2, dt); h.vz = Gta8Math.damp(h.vz, wantVz, 1.2, dt);
        h.x += h.vx * dt; h.z += h.vz * dt;
        h.y = Gta8Math.damp(h.y, ty, .8, dt);
        double face = Math.toDegrees(Math.atan2(p.x - h.x, -(p.z - h.z)));
        h.yaw = h.yaw + Gta8Math.angleDelta(h.yaw, face) * Math.min(1, dt * 1.5);
        h.tilt = Gta8Math.clamp(Math.hypot(h.vx, h.vz) * .5, 0, 14);
    }
    private double shotClock;
    private Gta8Ped sniperPed;
    private Gta8Ped heliSniper() {
        if (sniperPed == null) sniperPed = new Gta8Ped(Gta8Ped.Kind.COP, 0, 0, 0, 0);
        sniperPed.x = heli.x; sniperPed.y = heli.y - 2.5; sniperPed.z = heli.z;
        return sniperPed;
    }
}
