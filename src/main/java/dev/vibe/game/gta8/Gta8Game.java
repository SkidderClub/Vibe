package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8Ped.Kind;
import dev.vibe.game.gta8.Gta8Ped.State;
import dev.vibe.game.gta8.Gta8World.Poi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** GTA8 simulation: a 60 Hz fixed-step world with interpolated rendering. No Minecraft dependencies. */
public final class Gta8Game {
    public enum Event {
        GUNSHOT, IMPACT, EXPLOSION, HORN, WANTED, EVADED, CASH, PICKUP, CRASH, GLASS, PUNCH, WASTED, BUSTED, MISSION_START, MISSION_PASSED,
        MISSION_FAILED, THUNDER, ENTER, EXIT, RELOAD, EMPTY, HIT, KILL, BUY, SPLASH, DOOR, ALARM, JUMP, BAIL, BREAK_WINDOW
    }
    public static final class GameEvent {
        public final Event type; public final double x, y, z, value; public final Gta8Weapon weapon;
        GameEvent(Event type, double x, double y, double z, double value, Gta8Weapon weapon) { this.type = type; this.x = x; this.y = y; this.z = z; this.value = value; this.weapon = weapon; }
    }
    /** Held controls plus edge-triggered actions queued by the GUI. */
    public static final class Input {
        public boolean forward, back, left, right, jump, sprint, crouch, aim, attack, horn;
        public boolean enterExit, reload, interact, nextWeapon, prevWeapon, toggleCamera;
        public int slot = -1;
        public void clearActions() { enterExit = reload = interact = nextWeapon = prevWeapon = toggleCamera = false; slot = -1; }
    }
    public static final class Pickup {
        public final int type, value; public final Gta8Weapon weapon; public double x, y, z, life; public final Gta8World.Pickup source;
        Pickup(int type, int value, Gta8Weapon weapon, double x, double y, double z, double life, Gta8World.Pickup source) {
            this.type = type; this.value = value; this.weapon = weapon; this.x = x; this.y = y; this.z = z; this.life = life; this.source = source;
        }
    }

    public static final double STEP = 1 / 60.0;
    public final Gta8World world;
    public final Gta8Progress progress;
    public final Gta8Weather weather = new Gta8Weather();
    public final Gta8Camera camera = new Gta8Camera();
    public final Random random = new Random(0x6A8);
    public final List<Gta8Vehicle> vehicles = new ArrayList<Gta8Vehicle>();
    public final List<Gta8Ped> peds = new ArrayList<Gta8Ped>();
    public final List<Pickup> pickups = new ArrayList<Pickup>();
    public final List<GameEvent> events = new ArrayList<GameEvent>();
    public final Gta8Particles particles = new Gta8Particles();
    public final Gta8Decals decals = new Gta8Decals();
    public final Gta8Traffic traffic;
    public final Gta8Pedestrians pedestrians;
    public final Gta8Police police;
    public final Gta8Missions missions;
    public final Gta8Ped player;
    public double time, alpha, timeScale = 1, dayNight;
    public boolean cameraIndoors, firstPerson, dead, busted, sleeping;
    public double deathTimer, bustTimer, hurtFlash, stamina = 1, bloom, shotCooldown, reloadTimer, lastDamage = 99, flashLight, lastWaypointX, lastWaypointZ;
    public double cameraYaw = 180, cameraPitch = 8, cameraDistance = 3.4, freeLook, zoneTimer, lastLook;
    public String message = "", messageSub = "", bigTitle = "", bigSub = "", help = "", zone = "";
    public double messageUntil, bigUntil, helpUntil;
    public boolean waypoint;
    public double waypointX, waypointZ;
    public Poi shop;
    public boolean jobsOpen;
    public int density = 1;
    public int nextId = 1;
    private double accumulator, spawnTimer, lastRegen;
    private boolean attackHeld, sirenHeld;
    private final Map<Gta8World.ParkingSpot, Gta8Vehicle> parked = new HashMap<Gta8World.ParkingSpot, Gta8Vehicle>();
    private final Map<Poi, Gta8Ped> clerks = new HashMap<Poi, Gta8Ped>();
    private Poi robbery;
    private double robberyTimer;
    private final Solid1 solidHit = new Solid1();
    private final int[] zoneHit = new int[1];
    private double camX, camY, camZ;
    private boolean cameraReady;
    private Gta8Vehicle entering;

    private static final class Solid1 { final Gta8World.Solid[] hit = new Gta8World.Solid[1]; }

    public Gta8Game(Gta8World world, Gta8Progress progress) {
        this.world = world;
        this.progress = progress;
        traffic = new Gta8Traffic(this, new Random(11));
        pedestrians = new Gta8Pedestrians(this, new Random(12));
        police = new Gta8Police(this, new Random(13));
        missions = new Gta8Missions(this, new Random(14));
        player = new Gta8Ped(Kind.PLAYER, 0, world.spawnX, Gta8World.CURB, world.spawnZ);
        player.id = nextId++;
        int[] c = player.palette;
        c[0] = 0xD2A07A; c[1] = 0xE6E4DE; c[2] = 0x3E5A86; c[3] = 0xE8E8E4; c[4] = 0x3A2A1E; c[5] = 0x6A4A32; c[6] = 0x8A96A8; c[7] = 0x2A2A2A;
        player.state = State.IDLE;
        player.armor = progress.armor;
        player.weapon = progress.owns(Gta8Weapon.PISTOL) ? Gta8Weapon.PISTOL : Gta8Weapon.FISTS;
        player.clip = Math.min(player.weapon.magazine, progress.ammo(player.weapon));
        progress.ammo.put(player.weapon, progress.ammo(player.weapon) - player.clip);
        peds.add(player);
        weather.hours = progress.hours;
        cameraYaw = world.spawnYaw; player.yaw = world.spawnYaw;
        for (Gta8World.Pickup p : world.pickups) pickups.add(new Pickup(p.type, p.value, p.type == Gta8World.Pickup.WEAPON ? weaponFor(p.value) : null, p.x, p.y, p.z, -1, p));
        message("Welcome to Los Vibes. Press F near a car to take it, E to interact.");
        updateCamera(0);
    }

    private static Gta8Weapon weaponFor(int value) { return value == 2 ? Gta8Weapon.SNIPER : value == 3 ? Gta8Weapon.SHOTGUN : Gta8Weapon.SMG; }

    // ------------------------------------------------------------------ public API
    public void look(double dx, double dy) {
        if (dead || busted) return;
        cameraYaw = Gta8Math.wrap(cameraYaw + dx);
        cameraPitch = Gta8Math.clamp(cameraPitch + dy, -70, 75);
        if (Math.abs(dx) + Math.abs(dy) > .01) { freeLook = 1.6; lastLook = time; }
    }
    public void advance(double seconds, Input input) {
        if (!Double.isFinite(seconds) || seconds <= 0) return;
        accumulator += Math.min(.1, seconds) * timeScale;
        int steps = 0;
        while (accumulator >= STEP && steps < 8) {
            tick(STEP, input);
            input.clearActions();
            accumulator -= STEP;
            steps++;
        }
        alpha = accumulator / STEP;
        updateCamera(seconds);
    }
    public void event(Event type, double x, double y, double z, double value) { event(type, x, y, z, value, null); }
    public void event(Event type, double x, double y, double z, double value, Gta8Weapon weapon) { if (events.size() < 256) events.add(new GameEvent(type, x, y, z, value, weapon)); }
    public void message(String text) { message = text; messageUntil = time + 5; }
    public void help(String text) { help = text; helpUntil = time + .3; }
    public void bigMessage(String title, String sub) { bigTitle = title; bigSub = sub; bigUntil = time + 4.5; }
    public double renderNight() { return Gta8Math.smooth(.06, -.08, sunHeight()); }
    public double sunHeight() { double[] d = new double[3]; Gta8Atmosphere.position(weather.hours, d); return d[1]; }

    // ------------------------------------------------------------------ simulation
    private void tick(double dt, Input in) {
        time += dt;
        progress.playSeconds += dt;
        weather.advance(dt * (sleeping ? 60 : 1));
        progress.hours = weather.hours;
        if (weather.thunderIn >= 0 && weather.thunderIn < dt) event(Event.THUNDER, player.x, player.y, player.z, weather.thunderVolume);
        for (Gta8Vehicle v : vehicles) v.remember();
        for (Gta8Ped p : peds) p.remember();
        hurtFlash = Math.max(0, hurtFlash - dt);
        lastDamage += dt;
        flashLight = Math.max(0, flashLight - dt);
        bloom = Math.max(0, bloom - dt * .9 * Math.max(.02, bloom));
        shotCooldown -= dt;
        freeLook = Math.max(0, freeLook - dt);
        if (dead) { deathTimer += dt / timeScale; timeScale = deathTimer < 3 ? .35 : 1; if (deathTimer > 5.5) respawnHospital(); }
        else if (busted) { bustTimer += dt; if (bustTimer > 4.5) respawnPolice(); }
        else playerControl(dt, in);
        vehiclesStep(dt);
        for (int i = 0; i < peds.size(); i++) {
            Gta8Ped p = peds.get(i);
            if (p == player) continue;
            if (p.vehicle != null && (p.state == State.DRIVE)) { seat(p); continue; }
            if (p.state == State.RAGDOLL) { pedestrians.ragdoll(p, dt); continue; }
            switch (p.kind) {
                case COP: police.officer(p, dt); break;
                case GANG: case TARGET: gangster(p, dt); break;
                default: pedestrians.update(p, dt);
            }
        }
        police.update(dt);
        missions.update(dt);
        pickupsStep(dt);
        robberyStep(dt);
        particles.update(dt, weather.windX, weather.windZ, world);
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = .25;
            traffic.target = new int[]{24, 36, 48, 62}[Math.max(0, Math.min(3, density))];
            pedestrians.target = new int[]{38, 60, 82, 105}[Math.max(0, Math.min(3, density))];
            traffic.populate(dt);
            pedestrians.populate();
            parkedCars();
            clerks();
            if (random.nextInt(8) == 0) gangs();
        }
        if (!dead && !busted) {
            if (police.arresting(player) && player.vehicle == null && player.speed() < 1.3) { bustTimer += dt; if (bustTimer > 2.6) arrest(); }
            else if (!busted) bustTimer = Math.max(0, bustTimer - dt);
            if (player.health < player.maxHealth * .5 && lastDamage > 6 && time - lastRegen > .2) { player.health = Math.min(player.maxHealth * .5, player.health + 1.5); lastRegen = time; }
        }
        progress.maxWanted = Math.max(progress.maxWanted, police.wanted);
        progress.armor = (int) Math.round(player.armor);
        zoneTimer -= dt;
        if (zoneTimer <= 0) {
            zoneTimer = 1;
            double px = player.vehicle != null ? player.vehicle.x : player.x, pz = player.vehicle != null ? player.vehicle.z : player.z;
            String z = world.districtName(px, pz);
            String street = world.streetName(px, pz);
            zone = street.isEmpty() ? z : z + " | " + street;
            cameraIndoors = indoors(camera.x, camera.z) && camera.y < 5;
        }
    }

    private boolean indoors(double x, double z) {
        for (Gta8World.Building b : world.buildings) if (b.interior && b.contains(x, z)) return true;
        return false;
    }

    // ------------------------------------------------------------------ player
    private void playerControl(double dt, Input in) {
        if (in.toggleCamera) firstPerson = !firstPerson;
        if (in.slot >= 0) selectSlot(in.slot);
        if (in.nextWeapon) cycleWeapon(1);
        if (in.prevWeapon) cycleWeapon(-1);
        if (in.reload) startReload();
        if (reloadTimer > 0) {
            reloadTimer -= dt;
            player.reloadAnim = 1;
            if (reloadTimer <= 0) finishReload();
        } else player.reloadAnim = Math.max(0, player.reloadAnim - dt * 4);
        if (player.state == State.RAGDOLL) {
            pedestrians.ragdoll(player, dt);
            if (player.dead) die();
            return;
        }
        if (player.state == State.ENTER_CAR) { enterStep(dt); return; }
        if (player.vehicle != null && player.state == State.DRIVE) { drive(dt, in); return; }
        if (in.enterExit) { tryEnter(); if (player.state == State.ENTER_CAR) return; }
        if (in.interact) interact();
        onFoot(dt, in);
    }

    private void onFoot(double dt, Input in) {
        double yaw = Math.toRadians(cameraYaw);
        double fx = Math.sin(yaw), fz = -Math.cos(yaw), rx = Math.cos(yaw), rz = Math.sin(yaw);
        double mf = (in.forward ? 1 : 0) - (in.back ? 1 : 0), ms = (in.right ? 1 : 0) - (in.left ? 1 : 0);
        double dx = fx * mf + rx * ms, dz = fz * mf + rz * ms, len = Math.hypot(dx, dz);
        if (len > 0) { dx /= len; dz /= len; }
        double water = world.waterLevelAt(player.x, player.z);
        boolean swimming = water - world.groundHeight(player.x, player.z) > 1.25 && player.y < water - .6;
        boolean armed = player.weapon != Gta8Weapon.FISTS;
        boolean aiming = in.aim && !swimming;
        player.crouch = Gta8Math.approach(player.crouch, in.crouch && !swimming ? 1 : 0, dt * 5);
        boolean sprinting = in.sprint && len > 0 && !aiming && player.crouch < .5 && stamina > .05;
        stamina = Gta8Math.clamp(stamina + (sprinting ? -dt / 9 : dt / 5), 0, 1);
        double speed = len == 0 ? 0 : swimming ? (sprinting ? 2.3 : 1.4) : aiming ? 1.9 : player.crouch > .5 ? 1.5 : sprinting ? 6.4 : 3.3;
        walk(player, dx, dz, speed, dt, false);
        if (swimming) { player.y = Gta8Math.damp(player.y, water - 1.35, 6, dt); player.vy = 0; player.grounded = false; }
        progress.distanceWalked += player.speed() * dt;
        if (aiming || in.attack && armed) player.yaw = player.yaw + Gta8Math.angleDelta(player.yaw, cameraYaw) * Math.min(1, dt * 18);
        else if (len > 0) player.yaw = player.yaw + Gta8Math.angleDelta(player.yaw, Math.toDegrees(Math.atan2(dx, -dz))) * Math.min(1, dt * 11);
        player.aim = Gta8Math.approach(player.aim, aiming || in.attack && armed ? 1 : 0, dt * 7);
        player.lookPitch = cameraPitch;
        if (in.jump && player.grounded && !swimming && player.crouch < .5) { player.vy = 4.4; player.grounded = false; player.jump = 1; event(Event.JUMP, player.x, player.y, player.z, 0); }
        player.jump = Math.max(0, player.jump - dt * 1.5);
        boolean trigger = player.weapon.automatic ? in.attack : in.attack && !attackHeld;
        attackHeld = in.attack;
        if (trigger && !swimming && shotCooldown <= 0) fire();
        if (aiming && armed) threaten();
    }

    /** Shared locomotion: acceleration, wall sliding, kerb steps, gravity and vehicles as obstacles. */
    public void walk(Gta8Ped p, double dirX, double dirZ, double speed, double dt, boolean avoid) {
        double tvx = dirX * speed, tvz = dirZ * speed;
        double k = Math.min(1, dt * (p.grounded ? 11 : 2.5));
        p.vx += (tvx - p.vx) * k; p.vz += (tvz - p.vz) * k;
        if (avoid) {
            for (int i = 0; i < peds.size(); i++) {
                Gta8Ped o = peds.get(i);
                if (o == p || o.dead || o.vehicle != null) continue;
                double ox = p.x - o.x, oz = p.z - o.z, d2 = ox * ox + oz * oz;
                if (d2 > .64 || d2 < 1e-6) continue;
                double d = Math.sqrt(d2), push = (.8 - d) * 3;
                p.vx += ox / d * push * dt * 8; p.vz += oz / d * push * dt * 8;
            }
        }
        double nx = p.x + p.vx * dt, nz = p.z + p.vz * dt;
        if (canStand(p, nx, p.z)) p.x = nx; else p.vx = 0;
        if (canStand(p, p.x, nz)) p.z = nz; else p.vz = 0;
        double ground = world.support(p.x, p.z, p.y + .45, .22);
        if (p.y > ground + .03 || p.vy > 0) {
            p.vy -= 9.81 * dt;
            double ny = p.y + p.vy * dt;
            if (p.vy > 0 && world.blocked(p.x, ny, p.z, .22, p.height())) { p.vy = 0; ny = p.y; }
            if (ny <= ground) {
                if (p.vy < -9 && p == player) damagePed(p, (-p.vy - 9) * 14, null, 1, 0, 0);
                ny = ground; p.vy = 0; p.grounded = true;
            } else p.grounded = false;
            p.y = ny;
        } else { p.y = ground; p.vy = 0; p.grounded = true; }
        double actual = Math.hypot(p.vx, p.vz);
        p.moveSpeed = actual;
        p.phase += actual * dt / (actual > 4.5 ? 2.2 : actual > 2.2 ? 1.75 : 1.35) * Math.PI;
        if (p != player && actual > .2) p.yaw = p.yaw + Gta8Math.angleDelta(p.yaw, Math.toDegrees(Math.atan2(p.vx, -p.vz))) * Math.min(1, dt * 8);
    }
    private boolean canStand(Gta8Ped p, double x, double z) {
        double step = world.support(x, z, p.y + .42, .22);
        if (step > p.y + .42) return false;
        if (world.blocked(x, Math.max(p.y, step) + .05, z, .24, p.height() - .1)) return false;
        for (int i = 0; i < vehicles.size(); i++) {
            Gta8Vehicle v = vehicles.get(i);
            if (v == p.vehicle || Math.abs(v.x - x) > 4 || Math.abs(v.z - z) > 4) continue;
            if (v.contains(x, z, .25) && p.y < v.y + v.model.height && !v.contains(p.x, p.z, .25)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ weapons
    private void selectSlot(int slot) {
        Gta8Weapon[] all = Gta8Weapon.values();
        if (slot < 0 || slot >= all.length) return;
        if (progress.owns(all[slot]) || all[slot] == Gta8Weapon.FISTS) equip(all[slot]);
    }
    private void cycleWeapon(int dir) {
        Gta8Weapon[] all = Gta8Weapon.values();
        int i = player.weapon.ordinal();
        for (int n = 0; n < all.length; n++) {
            i = (i + dir + all.length) % all.length;
            if (all[i] == Gta8Weapon.FISTS || progress.owns(all[i])) { equip(all[i]); return; }
        }
    }
    public void equip(Gta8Weapon w) {
        if (w == player.weapon) return;
        // Unused rounds go back into the reserve.
        progress.ammo.put(player.weapon, progress.ammo(player.weapon) + player.clip);
        if (player.weapon == Gta8Weapon.FISTS) progress.ammo.put(Gta8Weapon.FISTS, 0);
        player.weapon = w;
        player.clip = Math.min(w.magazine, progress.ammo(w));
        progress.ammo.put(w, progress.ammo(w) - player.clip);
        if (w == Gta8Weapon.FISTS) { player.clip = 0; progress.ammo.put(w, 0); }
        reloadTimer = 0;
        shotCooldown = .3;
    }
    private void startReload() {
        Gta8Weapon w = player.weapon;
        if (w.melee || reloadTimer > 0 || player.clip >= w.magazine || progress.ammo(w) <= 0) return;
        reloadTimer = w.reload;
        event(Event.RELOAD, player.x, player.y, player.z, 0, w);
    }
    private void finishReload() {
        Gta8Weapon w = player.weapon;
        int need = w.magazine - player.clip, take = Math.min(need, progress.ammo(w));
        if (w == Gta8Weapon.SHOTGUN) { take = Math.min(1, take); if (take > 0 && player.clip + 1 < w.magazine && progress.ammo(w) > 1) reloadTimer = .55; }
        player.clip += take;
        progress.ammo.put(w, progress.ammo(w) - take);
    }

    /** Camera-ray aiming corrected for the muzzle position; pellets, glass, vehicles, head shots. */
    void fire() {
        Gta8Weapon w = player.weapon;
        if (w.melee) { punch(); return; }
        if (reloadTimer > 0) return;
        if (player.clip <= 0) {
            if (progress.ammo(w) > 0) startReload(); else { event(Event.EMPTY, player.x, player.y, player.z, 0, w); shotCooldown = .35; }
            return;
        }
        player.clip--;
        progress.shots++;
        shotCooldown = w.interval;
        player.recoil = 1;
        boolean inCar = player.vehicle != null;
        double cx = camera.x, cy = camera.y, cz = camera.z, dx = camera.forwardX(), dy = camera.forwardY(), dz = camera.forwardZ();
        double moving = inCar ? 1.6 : player.speed() > 2 ? 1.6 : 1;
        double spread = (w.spread * (player.aim > .6 ? 1 : 2.3) * moving * (player.crouch > .5 ? .7 : 1) + bloom) * (w == Gta8Weapon.SNIPER && player.aim < .6 ? 60 : 1);
        double yaw = Math.toRadians(player.yaw);
        double mx, my, mz;
        if (firstPerson && !inCar) { mx = cx + dx * .5; my = cy - .12 + dy * .5; mz = cz + dz * .5; }
        else { mx = player.x + Math.cos(yaw) * .3 + Math.sin(yaw) * .55; my = inCar ? player.vehicle.y + 1.3 : player.y + 1.38; mz = player.z + Math.sin(yaw) * .3 - Math.cos(yaw) * .55; }
        for (int pellet = 0; pellet < w.pellets; pellet++) {
            double[] d = perturb(dx, dy, dz, spread);
            Hit aimHit = trace(cx, cy, cz, d[0], d[1], d[2], w.range, player, inCar ? player.vehicle : null);
            double tx = cx + d[0] * aimHit.distance, ty = cy + d[1] * aimHit.distance, tz = cz + d[2] * aimHit.distance;
            double bx = tx - mx, by = ty - my, bz = tz - mz, bl = Math.sqrt(bx * bx + by * by + bz * bz);
            if (bl < .05) continue;
            bx /= bl; by /= bl; bz /= bl;
            Hit hit = trace(mx, my, mz, bx, by, bz, bl + .1, player, inCar ? player.vehicle : null);
            applyHit(hit, mx, my, mz, bx, by, bz, w, player);
            if (pellet == 0 && random.nextInt(3) == 0) particles.tracer(mx, my, mz, mx + bx * hit.distance, my + by * hit.distance, mz + bz * hit.distance);
        }
        particles.muzzle(mx + Math.sin(yaw) * .15, my, mz - Math.cos(yaw) * .15, dx, dy, dz, w == Gta8Weapon.SHOTGUN ? 1.6 : w == Gta8Weapon.PISTOL ? .9 : 1.2);
        flashLight = .06;
        bloom = Math.min(.08, bloom + w.recoil * .0035);
        cameraPitch = Gta8Math.clamp(cameraPitch - w.recoil * (.55 + random.nextDouble() * .45), -70, 75);
        cameraYaw += (random.nextDouble() - .5) * w.recoil * .35;
        event(Event.GUNSHOT, mx, my, mz, 1, w);
        pedestrians.alarm(player.x, player.z, w == Gta8Weapon.SNIPER ? 80 : 50, true);
        police.crime(Gta8Police.Crime.SHOOTING, player.x, player.z);
        if (player.clip == 0 && progress.ammo(w) > 0) startReload();
    }
    private double[] perturb(double dx, double dy, double dz, double spread) {
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * spread;
        double ux = -dz, uz = dx, ul = Math.hypot(ux, uz);
        if (ul < 1e-6) { ux = 1; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        double vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
        double ox = dx + (ux * Math.cos(a) + vx * Math.sin(a)) * r, oy = dy + vy * Math.sin(a) * r, oz = dz + (uz * Math.cos(a) + vz * Math.sin(a)) * r;
        double l = Math.sqrt(ox * ox + oy * oy + oz * oz);
        return new double[]{ox / l, oy / l, oz / l};
    }

    /** Result of a combined world, vehicle and character ray cast. */
    public static final class Hit {
        public double distance, nx, ny = 1, nz;
        public Gta8Ped ped; public Gta8Vehicle vehicle; public Gta8World.Solid solid; public int zone;
    }
    public Hit trace(double x, double y, double z, double dx, double dy, double dz, double range, Gta8Ped ignore, Gta8Vehicle ignoreCar) {
        Hit h = new Hit();
        double d = range;
        for (int pass = 0; pass < 3; pass++) {
            d = world.ray(x, y, z, dx, dy, dz, range, solidHit.hit);
            Gta8World.Solid s = solidHit.hit[0];
            if (s != null && s.owner != null && s.owner.type == Gta8World.Prop.GLASS) {
                world.knock(s.owner);
                particles.glass(x + dx * d, y + dy * d, z + dz * d, dx, dz);
                event(Event.GLASS, x + dx * d, y + dy * d, z + dz * d, 1);
                continue;
            }
            h.solid = s;
            break;
        }
        h.distance = d;
        if (h.solid != null) {
            Gta8World.Solid s = h.solid;
            double px = x + dx * d, py = y + dy * d, pz = z + dz * d;
            double[] faces = {Math.abs(px - s.x0), Math.abs(px - s.x1), Math.abs(py - s.y0), Math.abs(py - s.y1), Math.abs(pz - s.z0), Math.abs(pz - s.z1)};
            int best = 0;
            for (int i = 1; i < 6; i++) if (faces[i] < faces[best]) best = i;
            h.nx = best == 0 ? -1 : best == 1 ? 1 : 0; h.ny = best == 2 ? -1 : best == 3 ? 1 : 0; h.nz = best == 4 ? -1 : best == 5 ? 1 : 0;
        }
        for (int i = 0; i < vehicles.size(); i++) {
            Gta8Vehicle v = vehicles.get(i);
            if (v == ignoreCar) continue;
            double cx = v.x - x, cz = v.z - z;
            double along = cx * dx + cz * dz;
            if (along < -4 || along > h.distance + 4) continue;
            if (Math.abs(cx * dz - cz * dx) > 4) continue;
            double t = v.ray(x, y, z, dx, dy, dz, h.distance);
            if (t < h.distance) { h.distance = t; h.vehicle = v; h.ped = null; h.solid = null; h.nx = -dx; h.ny = 0; h.nz = -dz; }
        }
        for (int i = 0; i < peds.size(); i++) {
            Gta8Ped p = peds.get(i);
            if (p == ignore || p.vehicle != null && p.state == State.DRIVE) continue;
            double cx = p.x - x, cz = p.z - z;
            double along = cx * dx + cz * dz;
            if (along < -1 || along > h.distance + 1 || Math.abs(cx * dz - cz * dx) > 1.2) continue;
            double t = p.ray(x, y, z, dx, dy, dz, h.distance, zoneHit);
            if (t < h.distance) { h.distance = t; h.ped = p; h.vehicle = null; h.solid = null; h.zone = zoneHit[0]; }
        }
        return h;
    }
    private void applyHit(Hit hit, double x, double y, double z, double dx, double dy, double dz, Gta8Weapon w, Gta8Ped shooter) {
        double px = x + dx * hit.distance, py = y + dy * hit.distance, pz = z + dz * hit.distance;
        if (hit.ped != null) {
            double mult = hit.zone == 2 ? (w == Gta8Weapon.SNIPER || w == Gta8Weapon.RIFLE ? 3.2 : 2.6) : hit.zone == 1 ? 1 : .68;
            particles.blood(px, py, pz, dx, dy, dz);
            if (shooter == player) { progress.hits++; if (hit.zone == 2) progress.headshots++; }
            damagePed(hit.ped, w.damageAt(hit.distance) * mult, shooter, hit.zone, dx, dz);
        } else if (hit.vehicle != null) {
            particles.impact(px, py, pz, -dx, 0, -dz, true);
            Gta8Vehicle v = hit.vehicle;
            v.damage(w.damage * 1.1);
            if (py < v.y + .75) {
                for (int i = 0; i < 4; i++) {
                    double wx = v.worldX(i % 2 == 0 ? -v.model.track / 2 : v.model.track / 2, i < 2 ? v.model.wheelbase / 2 : -v.model.wheelbase / 2);
                    double wz = v.worldZ(i % 2 == 0 ? -v.model.track / 2 : v.model.track / 2, i < 2 ? v.model.wheelbase / 2 : -v.model.wheelbase / 2);
                    if (Math.hypot(wx - px, wz - pz) < .55 && !v.burst[i]) { v.burst[i] = true; event(Event.IMPACT, px, py, pz, 2); }
                }
            }
            if (v.ai != null) { Gta8Traffic.release(v); if (v.driver != null && v.driver.kind == Kind.CIVILIAN) { exitVehicle(v.driver, v); v.driver.state = State.FLEE; v.driver.fleeX = x; v.driver.fleeZ = z; v.driver.fleeTimer = 12; } }
            v.lastImpact = 0;
            if (shooter == player && v.model == Gta8Vehicle.Model.POLICE) police.crime(Gta8Police.Crime.ATTACK_COP, player.x, player.z);
            event(Event.IMPACT, px, py, pz, 1);
        } else if (hit.distance < w.range - .5) {
            particles.impact(px, py, pz, hit.nx, hit.ny, hit.nz, false);
            decals.add(Gta8Decals.BULLET, px + hit.nx * .01, py + hit.ny * .01, pz + hit.nz * .01, hit.nx, hit.ny, hit.nz, 0, 0, .05 + random.nextDouble() * .03, 0, 1);
            event(Event.IMPACT, px, py, pz, 0);
        }
    }
    private void punch() {
        shotCooldown = Gta8Weapon.FISTS.interval;
        player.punch = 1;
        double fx = player.forwardX(), fz = player.forwardZ();
        Gta8Ped best = null;
        double bestD = 1.6;
        for (Gta8Ped p : peds) {
            if (p == player || p.dead || p.vehicle != null) continue;
            double dx = p.x - player.x, dz = p.z - player.z, d = Math.hypot(dx, dz);
            if (d < bestD && (dx * fx + dz * fz) / Math.max(.01, d) > .5 && Math.abs(p.y - player.y) < 1) { best = p; bestD = d; }
        }
        event(Event.PUNCH, player.x, player.y + 1.4, player.z, best != null ? 1 : 0);
        if (best != null) {
            damagePed(best, Gta8Weapon.FISTS.damage * (.8 + random.nextDouble() * .5), player, 1, fx, fz);
            if (!best.dead && random.nextDouble() < .25) best.knock(fx * 3, 1.5, fz * 3);
        }
    }
    /** Aiming a gun at people: civilians put their hands up, clerks start emptying the till. */
    private void threaten() {
        double dx = camera.forwardX(), dz = camera.forwardZ();
        for (Gta8Ped p : peds) {
            if (p == player || p.dead || p.vehicle != null) continue;
            double ox = p.x - player.x, oz = p.z - player.z, d = Math.hypot(ox, oz);
            if (d > 14 || (ox * dx + oz * dz) / Math.max(.01, d) < .95) continue;
            if (p.kind == Kind.CLERK) startRobbery(p);
            else if (p.kind == Kind.CIVILIAN && p.state != State.HANDS_UP && p.state != State.FLEE) { p.state = State.HANDS_UP; p.fleeTimer = 2 + random.nextDouble() * 2; p.witness = true; p.reportTimer = 6; police.crime(Gta8Police.Crime.ASSAULT, player.x, player.z); }
            else if (p.kind == Kind.GANG) p.provoked = true;
        }
    }

    /** Central damage handler: armour, reactions, crimes, deaths, drops and statistics. */
    public void damagePed(Gta8Ped p, double amount, Gta8Ped attacker, int zone, double dirX, double dirZ) {
        if (p.dead) return;
        boolean killed = p.damage(amount);
        if (p == player) {
            hurtFlash = .5; lastDamage = 0;
            if (killed) die();
            return;
        }
        event(Event.HIT, p.x, p.y + 1, p.z, zone);
        if (attacker == player) {
            Gta8Police.Crime crime = p.kind == Kind.COP ? (killed ? Gta8Police.Crime.KILL_COP : Gta8Police.Crime.ATTACK_COP)
                    : killed ? Gta8Police.Crime.KILL : Gta8Police.Crime.ASSAULT;
            if (p.kind != Kind.GANG && p.kind != Kind.TARGET) police.crime(crime, player.x, player.z);
            else police.crime(killed ? Gta8Police.Crime.KILL : Gta8Police.Crime.ASSAULT, player.x, player.z);
            pedestrians.alarm(p.x, p.z, 30, true);
            if (p.kind == Kind.GANG || p.kind == Kind.TARGET) { p.provoked = true; for (Gta8Ped o : peds) if ((o.kind == Kind.GANG || o.kind == Kind.TARGET) && Math.hypot(o.x - p.x, o.z - p.z) < 40) o.provoked = true; }
        }
        if (killed) {
            p.fallYaw = Math.toDegrees(Math.atan2(dirX, -dirZ));
            p.fallSide = random.nextBoolean() ? 1 : -1;
            if (attacker == player) { progress.kills++; if (p.kind == Kind.COP) progress.copKills++; event(Event.KILL, p.x, p.y, p.z, p.kind == Kind.COP ? 1 : 0); }
            int cash = p.kind == Kind.COP ? 0 : p.kind == Kind.TARGET ? 250 : 5 + random.nextInt(p.kind == Kind.GANG ? 120 : 60);
            if (cash > 0) pickups.add(new Pickup(Gta8World.Pickup.CASH, cash, null, p.x + .3, p.y, p.z + .2, 60, null));
            if (p.kind == Kind.COP || p.kind == Kind.GANG) pickups.add(new Pickup(Gta8World.Pickup.WEAPON, 0, p.weapon == Gta8Weapon.FISTS ? Gta8Weapon.PISTOL : p.weapon, p.x - .3, p.y, p.z, 60, null));
            decals.add(Gta8Decals.BLOOD, p.x, world.groundHeight(p.x, p.z) + .01, p.z, 0, 1, 0, 1, 0, .6 + random.nextDouble() * .4, 0, .9);
            if (p.vehicle != null) { Gta8Vehicle v = p.vehicle; if (v.driver == p) { Gta8Traffic.release(v); v.throttle = 0; v.brake = .2; } }
        } else if (p.kind == Kind.CIVILIAN) {
            p.state = State.FLEE; p.fleeX = attacker != null ? attacker.x : p.x - dirX; p.fleeZ = attacker != null ? attacker.z : p.z - dirZ; p.fleeTimer = 14;
        }
    }

    /** An NPC fires at a target; misses are drawn as near-misses. */
    public void npcShot(Gta8Ped shooter, Gta8Ped target, boolean hit, Gta8Weapon w) {
        double mx = shooter.x + shooter.forwardX() * .5, my = shooter.y + 1.35, mz = shooter.z + shooter.forwardZ() * .5;
        double tx = target.x, ty = target.chestY(), tz = target.z;
        if (target.vehicle != null) { ty = target.vehicle.y + 1.1; }
        if (!hit) { tx += (random.nextDouble() - .5) * 2.4; ty += (random.nextDouble() - .3) * 1.2; tz += (random.nextDouble() - .5) * 2.4; }
        double dx = tx - mx, dy = ty - my, dz = tz - mz, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l; dy /= l; dz /= l;
        shooter.recoil = 1;
        shooter.aim = 1;
        particles.muzzle(mx, my, mz, dx, dy, dz, 1);
        if (random.nextInt(3) == 0) particles.tracer(mx, my, mz, tx, ty, tz);
        event(Event.GUNSHOT, mx, my, mz, .8, w);
        double blocked = world.ray(mx, my, mz, dx, dy, dz, l);
        if (blocked < l - .3) { particles.impact(mx + dx * blocked, my + dy * blocked, mz + dz * blocked, -dx, -dy, -dz, false); return; }
        if (!hit) { particles.impact(tx, Math.max(ty, world.groundHeight(tx, tz)), tz, -dx, .3, -dz, false); event(Event.IMPACT, tx, ty, tz, 3); return; }
        if (target.vehicle != null && target.state == State.DRIVE) {
            target.vehicle.damage(w.damage * .8);
            if (random.nextDouble() < .45) damagePed(target, w.damage * .45, shooter, 1, dx, dz);
            particles.impact(tx, ty, tz, -dx, 0, -dz, true);
            return;
        }
        particles.blood(tx, ty, tz, dx, dy, dz);
        damagePed(target, w.damage * (target == player ? .55 : 1), shooter, random.nextInt(8) == 0 ? 2 : 1, dx, dz);
    }

    // ------------------------------------------------------------------ vehicles
    private void vehiclesStep(double dt) {
        double grip = weather.grip();
        for (int i = 0; i < vehicles.size(); i++) {
            Gta8Vehicle v = vehicles.get(i);
            if (v.ai != null && !v.dynamic) {
                if (v.driver == null || v.driver.dead) Gta8Traffic.release(v);
                else { traffic.drive(v, dt); v.cosmetics(dt, renderNight(), time); continue; }
            }
            if (v.parked && v.driver == null && Math.hypot(v.vx, v.vz) < .01 && Math.abs(v.yawRate) < .01) { v.cosmetics(dt, 0, time); continue; }
            if (v.driver == null || v.driver.dead) { v.throttle = 0; v.brake = v.parked ? 1 : .08; v.handbrake = false; v.steer *= .95; }
            double[] wheelsBefore = skidPoints(v);
            int sub = v == player.vehicle || Math.hypot(v.vx, v.vz) > 8 ? 3 : 2;
            for (int s = 0; s < sub; s++) {
                v.physics(dt / sub, world, grip);
                double impact = v.collideWorld(world);
                if (impact > 4 && v == player.vehicle) { event(Event.CRASH, v.x, v.y + .5, v.z, impact); progress.crashes++; }
                else if (impact > 6) event(Event.CRASH, v.x, v.y + .5, v.z, impact);
            }
            for (Gta8World.Prop knocked : v.knocked) {
                knocked.fallYaw = Math.toDegrees(Math.atan2(v.vx, -v.vz));
                knocked.fallTime = time;
                event(Event.CRASH, knocked.x, knocked.y + .5, knocked.z, 3);
                for (int k = 0; k < 6; k++) particles.spawn(Gta8Particles.DEBRIS, knocked.x, knocked.y + 1, knocked.z, v.vx * .4 + particles.s() * 2, 2 + particles.r() * 3, v.vz * .4 + particles.s() * 2, 1.5, .08, 0, 0x5A5E62, 1);
                if (knocked.type == Gta8World.Prop.HYDRANT) for (int k = 0; k < 40; k++) particles.spawn(Gta8Particles.SPLASH, knocked.x, knocked.y + .6, knocked.z, particles.s() * .8, 8 + particles.r() * 4, particles.s() * .8, 1.2 + particles.r(), .12, .3, 0xC8D8E0, .5);
            }
            v.knocked.clear();
            if (v.skid > .3 && v.grounded) {
                double[] after = skidPoints(v);
                for (int w = 0; w < 4; w += (v.handbrake ? 1 : 2)) decals.skid(wheelsBefore[w * 2], wheelsBefore[w * 2 + 1], after[w * 2], after[w * 2 + 1], world.groundHeight(after[w * 2], after[w * 2 + 1]), .22, Math.min(1, v.skid));
                if (random.nextDouble() < v.skid * .5) particles.spawn(Gta8Particles.SMOKE, after[4], v.y + .2, after[5], particles.s() * .5, .4, particles.s() * .5, 1.5, .4, 1.2, 0xD8D8D4, .25 * v.skid);
            }
            v.cosmetics(dt, renderNight(), time);
            burn(v, dt);
        }
        // Pairwise contacts.
        for (int i = 0; i < vehicles.size(); i++) for (int j = i + 1; j < vehicles.size(); j++) {
            Gta8Vehicle a = vehicles.get(i), b = vehicles.get(j);
            if (!a.dynamic && !b.dynamic) continue;
            if (Math.abs(a.x - b.x) > 7 || Math.abs(a.z - b.z) > 7) continue;
            boolean aKinematic = a.ai != null && !a.dynamic, bKinematic = b.ai != null && !b.dynamic;
            double impact = Gta8Vehicle.collide(a, b);
            if (impact > .8) {
                if (aKinematic) Gta8Traffic.release(a);
                if (bKinematic) Gta8Traffic.release(b);
                if (impact > 3) event(Event.CRASH, (a.x + b.x) / 2, a.y + .6, (a.z + b.z) / 2, impact);
                boolean playerCar = a == player.vehicle || b == player.vehicle;
                Gta8Vehicle other = a == player.vehicle ? b : a;
                if (playerCar && other.model == Gta8Vehicle.Model.POLICE && impact > 4) police.crime(Gta8Police.Crime.ATTACK_COP, player.x, player.z);
                if (playerCar && impact > 5 && other.driver != null && other.driver.kind == Kind.CIVILIAN && random.nextDouble() < .3) other.horn = true;
            }
        }
        // Vehicles against people.
        for (Gta8Vehicle v : vehicles) {
            double speed = Math.hypot(v.vx, v.vz);
            if (speed < .6) continue;
            for (int i = 0; i < peds.size(); i++) {
                Gta8Ped p = peds.get(i);
                if (p.vehicle != null && p.state == State.DRIVE || p.state == State.RAGDOLL && p.fall > .5) continue;
                if (Math.abs(p.x - v.x) > 4 || Math.abs(p.z - v.z) > 4 || !v.contains(p.x, p.z, .3) || p.y > v.y + v.model.height) continue;
                double rel = speed;
                if (rel > 3.2) {
                    p.knock(v.vx * .85 + (p.x - v.x) * .5, 2.2 + rel * .18, v.vz * .85 + (p.z - v.z) * .5);
                    boolean byPlayer = v == player.vehicle;
                    damagePed(p, rel * rel * .9, byPlayer ? player : null, 1, v.vx / speed, v.vz / speed);
                    if (byPlayer && !p.dead) police.crime(Gta8Police.Crime.RUN_OVER, player.x, player.z);
                    if (byPlayer && p.kind == Kind.COP) police.crime(Gta8Police.Crime.ATTACK_COP, player.x, player.z);
                    event(Event.HIT, p.x, p.y + 1, p.z, 3);
                    if (v.ai != null) Gta8Traffic.release(v);
                    v.vx *= .93; v.vz *= .93;
                } else {
                    double fx = p.x - v.x, fz = p.z - v.z, l = Math.max(.01, Math.hypot(fx, fz));
                    p.x += fx / l * .06; p.z += fz / l * .06;
                }
            }
        }
    }
    private static double[] skidPoints(Gta8Vehicle v) {
        double hl = v.model.wheelbase / 2, hw = v.model.track / 2;
        return new double[]{v.worldX(-hw, hl), v.worldZ(-hw, hl), v.worldX(hw, hl), v.worldZ(hw, hl), v.worldX(-hw, -hl), v.worldZ(-hw, -hl), v.worldX(hw, -hl), v.worldZ(hw, -hl)};
    }
    private void burn(Gta8Vehicle v, double dt) {
        if (v.exploded) {
            if (random.nextDouble() < dt * 6) particles.spawn(Gta8Particles.SMOKE, v.x + particles.s(), v.y + 1.2, v.z + particles.s(), 0, 1.5, 0, 5, .8, .9, 0x1E1C1A, .5);
            return;
        }
        double engineX = v.worldX(0, v.halfLength() - .9), engineZ = v.worldZ(0, v.halfLength() - .9);
        if (v.health < 380 && random.nextDouble() < dt * (v.health < 200 ? 18 : 6))
            particles.spawn(Gta8Particles.SMOKE, engineX, v.y + v.model.height * .75, engineZ, particles.s() * .3, 1.2, particles.s() * .3, 2.5, .3, .8, v.health < 200 ? 0x2A2826 : 0x9A9894, .35);
        if (v.fire > 0 || v.burnTimer > 0) {
            if (random.nextDouble() < dt * 30) particles.spawn(Gta8Particles.FIRE, engineX + particles.s() * .4, v.y + v.model.height * .7, engineZ + particles.s() * .4, particles.s() * .3, 1.5, particles.s() * .3, .6, .45, .4, 0xFF8030, 1);
            v.health = Math.max(0, v.health - dt * 18);
            if (v.health <= 0 && v.burnTimer <= 0) v.burnTimer = 4.5;
        }
        if (v.burnTimer > 0) { v.burnTimer -= dt; if (v.burnTimer <= 0) explode(v); }
    }
    void explode(Gta8Vehicle v) {
        if (v.exploded) return;
        v.exploded = true; v.health = 0; v.fire = 0; v.siren = false;
        v.vy = 5.5; v.grounded = false; v.yawRate += (random.nextDouble() - .5) * 2;
        Gta8Traffic.release(v);
        particles.explosion(v.x, v.y + .8, v.z);
        decals.add(Gta8Decals.SCORCH, v.x, world.groundHeight(v.x, v.z) + .015, v.z, 0, 1, 0, 1, 0, 4, 0, 1);
        event(Event.EXPLOSION, v.x, v.y + 1, v.z, 1);
        flashLight = .25;
        pedestrians.alarm(v.x, v.z, 60, false);
        for (Gta8Ped p : peds) {
            if (p.vehicle == v && p.state == State.DRIVE) { p.state = State.DEAD; p.dead = true; p.health = 0; if (p == player) die(); continue; }
            double d = Math.hypot(p.x - v.x, p.z - v.z);
            if (d > 9) continue;
            double k = 1 - d / 9, dx = (p.x - v.x) / Math.max(.1, d), dz = (p.z - v.z) / Math.max(.1, d);
            p.knock(dx * 9 * k, 4 + 5 * k, dz * 9 * k);
            damagePed(p, 220 * k, null, 1, dx, dz);
        }
        for (Gta8Vehicle o : vehicles) {
            if (o == v) continue;
            double d = Math.hypot(o.x - v.x, o.z - v.z);
            if (d > 11) continue;
            double k = 1 - d / 11;
            o.damage(600 * k);
            if (o.ai != null) Gta8Traffic.release(o);
            o.vx += (o.x - v.x) / Math.max(.1, d) * 8 * k; o.vz += (o.z - v.z) / Math.max(.1, d) * 8 * k; o.vy += 3 * k; o.grounded = false;
        }
    }

    private void drive(double dt, Input in) {
        Gta8Vehicle v = player.vehicle;
        player.x = v.x; player.z = v.z; player.y = v.y + .35; player.yaw = v.yaw;
        if (in.enterExit) { leaveVehicle(); return; }
        double speed = v.speed();
        double throttle = 0, brake = 0;
        if (in.forward) { if (speed < -1) brake = 1; else throttle = 1; }
        if (in.back) { if (speed > 1) brake = 1; else throttle = -1; }
        v.throttle = throttle; v.brake = brake; v.handbrake = in.jump; v.horn = in.horn || in.interact;
        if (v.horn && time % .5 < dt) event(Event.HORN, v.x, v.y, v.z, 2);
        double maxSteer = .62 / (1 + speed * speed / 520);
        double target = ((in.right ? 1 : 0) - (in.left ? 1 : 0)) * maxSteer;
        double rate = target == 0 || Math.signum(target) != Math.signum(v.steer) ? 3.2 : 2.1;
        v.steer = Gta8Math.approach(v.steer, target, dt * rate);
        progress.distanceDriven += Math.abs(speed) * dt;
        // The siren toggles once per press, not on every tick the key is held.
        if (v.model == Gta8Vehicle.Model.POLICE && in.crouch && !sirenHeld) v.siren = !v.siren;
        sirenHeld = in.crouch;
        player.aim = Gta8Math.approach(player.aim, in.aim ? 1 : 0, dt * 6);
        boolean driveBy = in.aim && (player.weapon == Gta8Weapon.PISTOL || player.weapon == Gta8Weapon.SMG);
        if (driveBy && (player.weapon.automatic ? in.attack : in.attack && !attackHeld) && shotCooldown <= 0) fire();
        attackHeld = in.attack;
        if (v.destroyed() && v.burnTimer > 0) help("Get out! The car is on fire.");
        if (v.submerged > .5) { help("The car is sinking. Press F to get out."); }
        spray(v);
    }
    private void tryEnter() {
        Gta8Vehicle best = null;
        double bestD = 5.5;
        for (Gta8Vehicle v : vehicles) {
            if (v.destroyed()) continue;
            double d = Math.hypot(v.doorX() - player.x, v.doorZ() - player.z);
            double centre = Math.hypot(v.x - player.x, v.z - player.z);
            if (Math.min(d, centre - 1) < bestD && Math.abs(v.y - player.y) < 1.5) { best = v; bestD = Math.min(d, centre - 1); }
        }
        if (best == null) return;
        entering = best;
        player.state = State.ENTER_CAR;
        player.enterTimer = 0;
    }
    private void enterStep(double dt) {
        Gta8Vehicle v = entering;
        if (v == null || v.destroyed()) { player.state = State.IDLE; entering = null; return; }
        double dx = v.doorX() - player.x, dz = v.doorZ() - player.z, d = Math.hypot(dx, dz);
        if (d > .45 && player.enterTimer == 0) {
            walk(player, dx / d, dz / d, d > 2 ? 3.3 : 1.8, dt, false);
            player.yaw = Math.toDegrees(Math.atan2(dx, -dz));
            if (Math.abs(v.speed()) > 3) { player.state = State.IDLE; entering = null; }
            return;
        }
        if (player.enterTimer == 0) {
            if (v.driver != null && v.driver != player) {
                Gta8Ped victim = v.driver;
                exitVehicle(victim, v);
                victim.knock(-v.rightX() * 2.5, 1.2, -v.rightZ() * 2.5);
                victim.state = victim.kind == Kind.COP ? State.CHASE : State.RAGDOLL;
                victim.fleeX = player.x; victim.fleeZ = player.z; victim.fleeTimer = 15;
                police.crime(v.model == Gta8Vehicle.Model.POLICE ? Gta8Police.Crime.ATTACK_COP : Gta8Police.Crime.CARJACK, player.x, player.z);
                pedestrians.alarm(player.x, player.z, 25, true);
                progress.carsStolen++;
            } else if (v.locked) {
                v.locked = false;
                event(Event.BREAK_WINDOW, v.x, v.y + 1, v.z, 1);
                particles.glass(v.doorX(), v.y + 1.1, v.doorZ(), v.rightX(), v.rightZ());
                police.crime(Gta8Police.Crime.CARJACK, player.x, player.z);
                pedestrians.alarm(player.x, player.z, 18, true);
                progress.carsStolen++;
                player.enterTimer = -.8;
            }
            event(Event.DOOR, v.x, v.y + 1, v.z, 0);
        }
        player.enterTimer += dt;
        player.yaw = v.yaw;
        if (player.enterTimer > .9) {
            Gta8Traffic.release(v);
            v.dynamic = true; v.parked = false;
            v.driver = player;
            player.vehicle = v; player.state = State.DRIVE; player.sit = 1;
            entering = null;
            cameraYaw = v.yaw;
            event(Event.ENTER, v.x, v.y, v.z, 0);
        }
    }
    private void leaveVehicle() {
        Gta8Vehicle v = player.vehicle;
        double speed = Math.hypot(v.vx, v.vz);
        exitVehicle(player, v);
        if (speed > 5) {
            player.knock(v.vx * .6 - v.rightX() * 2, 1.5, v.vz * .6 - v.rightZ() * 2);
            damagePed(player, speed * 1.4, null, 1, 0, 0);
            event(Event.BAIL, player.x, player.y, player.z, speed);
        } else player.state = State.IDLE;
        v.throttle = 0; v.brake = speed > 5 ? 0 : 1; v.handbrake = false; v.horn = false;
        cameraYaw = v.yaw;
    }
    /** Puts a ped out of a vehicle on the driver's side, or the passenger side if blocked. */
    public void exitVehicle(Gta8Ped p, Gta8Vehicle v) {
        double[] sides = {-1, 1};
        p.vehicle = null;
        p.sit = 0;
        for (double side : sides) {
            double x = v.worldX(side * (v.halfWidth() + .6), v.model.wheelbase * .1), z = v.worldZ(side * (v.halfWidth() + .6), v.model.wheelbase * .1);
            double y = world.support(x, z, v.y + 1.2, .25);
            if (!world.blocked(x, y + .05, z, .25, 1.6)) { p.x = x; p.z = z; p.y = y; break; }
            p.x = x; p.z = z; p.y = Math.max(y, v.y);
        }
        if (v.driver == p) v.driver = null;
        p.state = State.IDLE;
        p.yaw = v.yaw;
        event(Event.EXIT, v.x, v.y, v.z, 0);
    }
    private void seat(Gta8Ped p) {
        Gta8Vehicle v = p.vehicle;
        boolean driver = v.driver == p;
        p.x = v.worldX(driver ? -.4 : .4, .1); p.z = v.worldZ(driver ? -.4 : .4, .1); p.y = v.y + .35; p.yaw = v.yaw; p.sit = 1;
    }

    // ------------------------------------------------------------------ interactions
    private void interact() {
        for (Poi poi : world.pois) {
            double d = Math.hypot(poi.interactX - player.x, poi.interactZ - player.z);
            if (poi.type == Poi.GUN_SHOP && d < 2.2) { shop = poi; return; }
            if ((poi.type == Poi.STORE || poi.type == Poi.GAS) && d < 2.2) { shop = poi; return; }
            if (poi.type == Poi.SAFEHOUSE && Math.hypot(poi.x - player.x, poi.z - player.z) < 3) { sleep(); return; }
            if (poi.type == Poi.JOB && Math.hypot(poi.x - player.x, poi.z - player.z) < 3) { jobsOpen = true; return; }
        }
    }
    /** Contextual prompt for the HUD. */
    public String prompt() {
        if (player.vehicle != null || dead || busted) return "";
        for (Poi poi : world.pois) {
            double d = Math.hypot(poi.interactX - player.x, poi.interactZ - player.z);
            if ((poi.type == Poi.GUN_SHOP || poi.type == Poi.STORE || poi.type == Poi.GAS) && d < 2.2) return "Press E to shop at " + poi.name;
            if (poi.type == Poi.SAFEHOUSE && Math.hypot(poi.x - player.x, poi.z - player.z) < 3) return "Press E to save and sleep";
            if (poi.type == Poi.JOB && Math.hypot(poi.x - player.x, poi.z - player.z) < 3) return "Press E to view jobs";
        }
        for (Gta8Vehicle v : vehicles) if (!v.destroyed() && Math.hypot(v.doorX() - player.x, v.doorZ() - player.z) < 3) return v.locked ? "Press F to break into the " + v.model.title : "Press F to enter the " + v.model.title;
        return "";
    }
    public boolean buyWeapon(Gta8Weapon w) {
        if (w == Gta8Weapon.FISTS) return false;
        boolean owned = progress.owns(w);
        int cost = owned ? w.ammoBox() * w.ammoPrice : w.price;
        if (!progress.spend(cost)) { message("You can't afford that."); return false; }
        progress.ammo.put(w, progress.ammo(w) + w.ammoBox() + (owned ? 0 : w.magazine));
        event(Event.BUY, player.x, player.y, player.z, cost);
        message(owned ? "Bought " + w.ammoBox() + " rounds for the " + w.title + "." : "Bought the " + w.title + ".");
        if (!owned) equip(w);
        progress.save();
        return true;
    }
    public boolean buyArmor() {
        if (player.armor >= 99) { message("You're already wearing full body armour."); return false; }
        if (!progress.spend(500)) { message("You can't afford that."); return false; }
        player.armor = 100; progress.armor = 100;
        event(Event.BUY, player.x, player.y, player.z, 500);
        progress.save();
        return true;
    }
    public boolean buySnack() {
        if (!progress.spend(6)) return false;
        player.health = Math.min(player.maxHealth, player.health + 35);
        event(Event.BUY, player.x, player.y, player.z, 6);
        return true;
    }
    private void sleep() {
        if (police.wanted > 0) { message("You can't rest with the police after you."); return; }
        sleeping = false;
        weather.hours = (weather.hours + 6) % 24;
        player.health = player.maxHealth;
        progress.save();
        bigMessage("SAVED", "Six hours later...");
    }
    private void spray(Gta8Vehicle v) {
        for (Poi poi : world.pois) {
            if (poi.type != Poi.SPRAY || Math.hypot(poi.x - v.x, poi.z - v.z) > 4.5) continue;
            if (police.wanted == 0 && v.health > 990) { help("Quick Spray: nothing to fix."); return; }
            if (police.seen && police.wanted > 0) { help("The police can see you."); return; }
            if (Math.abs(v.speed()) > 1.5) { help("Stop inside the garage."); return; }
            if (!progress.spend(250)) { help("A respray costs $250."); return; }
            v.paint = Gta8Traffic.PAINTS[random.nextInt(Gta8Traffic.PAINTS.length)];
            v.health = 1000; v.fire = 0; v.burnTimer = 0; java.util.Arrays.fill(v.burst, false);
            police.clear();
            bigMessage("RESPRAYED", "-$250");
            event(Event.BUY, v.x, v.y, v.z, 250);
        }
    }
    private void startRobbery(Gta8Ped clerk) {
        Poi poi = null;
        for (Map.Entry<Poi, Gta8Ped> e : clerks.entrySet()) if (e.getValue() == clerk) poi = e.getKey();
        if (poi == null || robbery != null || poi.robbedUntil > time) return;
        robbery = poi; robberyTimer = 0;
        clerk.state = State.HANDS_UP; clerk.handsUp = 0;
        message("Keep the clerk covered while they empty the register.");
    }
    private void robberyStep(double dt) {
        if (robbery == null) return;
        Gta8Ped clerk = clerks.get(robbery);
        robberyTimer += dt;
        if (clerk == null || clerk.dead || Math.hypot(player.x - robbery.interactX, player.z - robbery.interactZ) > 14) { robbery = null; return; }
        if (robberyTimer > 2 && robberyTimer - dt <= 2) police.crime(Gta8Police.Crime.ROBBERY, player.x, player.z);
        if (robberyTimer > 5.5) {
            int cash = 300 + random.nextInt(1200);
            pickups.add(new Pickup(Gta8World.Pickup.CASH, cash, null, robbery.interactX, Gta8World.CURB + 1.1, robbery.interactZ, 120, null));
            robbery.robbedUntil = time + 900;
            progress.robberies++;
            event(Event.ALARM, robbery.x, 2, robbery.z, 1);
            robbery = null;
            clerk.state = State.COWER; clerk.fleeTimer = 60;
        }
    }

    // ------------------------------------------------------------------ pickups
    private void pickupsStep(double dt) {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            if (p.life > 0) { p.life -= dt; if (p.life <= 0) { pickups.remove(i); continue; } }
            if (p.source != null && p.source.respawnAt > time) continue;
            if (dead || Math.hypot(p.x - player.x, p.z - player.z) > 1.3 || Math.abs(p.y - player.y) > 1.8) continue;
            if (!collect(p)) continue;
            if (p.source != null) p.source.respawnAt = time + 240;
            else pickups.remove(i);
        }
    }
    private boolean collect(Pickup p) {
        switch (p.type) {
            case Gta8World.Pickup.HEALTH:
                if (player.health >= player.maxHealth) return false;
                player.health = Math.min(player.maxHealth, player.health + p.value);
                message("Health restored.");
                break;
            case Gta8World.Pickup.ARMOR:
                if (player.armor >= 100) return false;
                player.armor = Math.min(100, player.armor + p.value);
                message("Body armour collected.");
                break;
            case Gta8World.Pickup.CASH:
                progress.money += p.value;
                event(Event.CASH, p.x, p.y, p.z, p.value);
                message("+$" + p.value);
                return true;
            default: {
                Gta8Weapon w = p.weapon == null ? Gta8Weapon.PISTOL : p.weapon;
                boolean had = progress.owns(w);
                progress.ammo.put(w, progress.ammo(w) + (p.source != null ? w.magazine * 2 : Math.max(4, w.magazine / 2)));
                message((had ? "Picked up ammunition: " : "Picked up a ") + w.title);
                if (!had && player.weapon == Gta8Weapon.FISTS) equip(w);
            }
        }
        event(Event.PICKUP, p.x, p.y, p.z, p.type);
        return true;
    }

    // ------------------------------------------------------------------ death and arrest
    private void die() {
        if (dead) return;
        dead = true; deathTimer = 0;
        player.dead = true; player.state = State.DEAD;
        if (player.vehicle != null) exitVehicle(player, player.vehicle);
        progress.deaths++;
        bigMessage("WASTED", "");
        event(Event.WASTED, player.x, player.y, player.z, 0);
        missions.cancel();
    }
    private void arrest() {
        busted = true; bustTimer = 0;
        player.state = State.HANDS_UP; player.handsUp = 1;
        progress.arrests++;
        bigMessage("BUSTED", "");
        event(Event.BUSTED, player.x, player.y, player.z, 0);
        missions.cancel();
    }
    private void respawnHospital() {
        Poi hospital = world.nearestPoi(Poi.HOSPITAL, player.x, player.z);
        long fee = Math.min(500, progress.money);
        progress.money -= fee;
        respawnAt(hospital, "Hospital bill: $" + fee);
    }
    private void respawnPolice() {
        Poi station = world.nearestPoi(Poi.POLICE, player.x, player.z);
        long fine = Math.min(1000, progress.money);
        progress.money -= fine;
        for (Gta8Weapon w : Gta8Weapon.values()) if (progress.owns(w)) progress.ammo.put(w, progress.ammo(w) / 2);
        player.clip = player.clip / 2;
        respawnAt(station, "Bail and fines: $" + fine + ". Some ammunition was confiscated.");
    }
    private void respawnAt(Poi poi, String note) {
        dead = busted = false; timeScale = 1;
        police.clear();
        player.dead = false; player.health = player.maxHealth; player.state = State.IDLE; player.fall = 0; player.handsUp = 0; player.vehicle = null;
        player.vx = player.vz = player.vy = 0;
        player.x = poi.x; player.z = poi.z; player.y = world.support(poi.x, poi.z, 5, .25);
        player.yaw = poi.yaw; cameraYaw = poi.yaw; cameraPitch = 8;
        weather.hours = (weather.hours + 4) % 24;
        for (int i = peds.size() - 1; i >= 0; i--) if (peds.get(i).kind == Kind.COP) peds.remove(i);
        for (int i = vehicles.size() - 1; i >= 0; i--) if (vehicles.get(i).model == Gta8Vehicle.Model.POLICE && vehicles.get(i).siren) removeVehicle(vehicles.get(i));
        message(note);
        progress.save();
        cameraReady = false;
    }

    // ------------------------------------------------------------------ population helpers
    Gta8Ped createCivilian(double x, double z) {
        Gta8Ped p = new Gta8Ped(Kind.CIVILIAN, random.nextInt(2), x, world.groundHeight(x, z), z);
        p.id = nextId++;
        p.speedPreference = 1.2 + random.nextDouble() * .4;
        pedestrians.dress(p, district(x, z));
        p.spawnTime = time;
        return p;
    }
    Gta8Ped createCop(double x, double z) {
        Gta8Ped p = new Gta8Ped(Kind.COP, random.nextInt(5) == 0 ? 1 : 0, x, world.groundHeight(x, z), z);
        p.id = nextId++;
        pedestrians.dress(p, null);
        p.weapon = Gta8Weapon.PISTOL;
        return p;
    }
    Gta8Ped spawnGangster(double x, double z, Kind kind) {
        Gta8Ped p = new Gta8Ped(kind, 0, x, world.groundHeight(x, z), z);
        p.id = nextId++;
        pedestrians.dress(p, null);
        if (kind == Kind.TARGET) { p.palette[1] = 0xE8E6E0; p.palette[2] = 0x1C1C1E; p.outfit = 1; p.glasses = true; }
        p.weapon = random.nextBoolean() ? Gta8Weapon.PISTOL : Gta8Weapon.SMG;
        p.state = State.IDLE;
        p.yaw = random.nextDouble() * 360;
        peds.add(p);
        return p;
    }
    private Gta8World.District district(double x, double z) {
        int bx = (int) Gta8Math.clamp(Math.floor((x - Gta8World.FIRST) / Gta8World.SPACING), 0, Gta8World.LINES - 2);
        int bz = (int) Gta8Math.clamp(Math.floor((z - Gta8World.FIRST) / Gta8World.SPACING), 0, Gta8World.LINES - 2);
        return world.blocks[bx][bz].district;
    }
    public void addVehicle(Gta8Vehicle v) { vehicles.add(v); }
    public void removeVehicle(Gta8Vehicle v) {
        vehicles.remove(v);
        for (int i = peds.size() - 1; i >= 0; i--) if (peds.get(i).vehicle == v && peds.get(i) != player) peds.remove(i);
        parked.values().remove(v);
    }
    private void parkedCars() {
        double px = player.x, pz = player.z;
        for (Gta8World.ParkingSpot s : world.parking) {
            double d = Math.hypot(s.x - px, s.z - pz);
            Gta8Vehicle existing = parked.get(s);
            if (existing != null) {
                if (d > 190 && existing != player.vehicle && !existing.mission && !visible(existing.x, 1, existing.z, 3)) { vehicles.remove(existing); parked.remove(s); }
                continue;
            }
            if (d > 120 || d < 35 && visible(s.x, 1, s.z, 3)) continue;
            double chance = s.kind == Gta8World.ParkingSpot.STREET ? .42 : s.kind == Gta8World.ParkingSpot.POLICE ? .7 : .6;
            if (Gta8Math.hash01((int) (s.x * 10), (int) (s.z * 10), 77) > chance) continue;
            if (occupiedByCar(s.x, s.z)) continue;
            Gta8Vehicle.Model model = s.kind == Gta8World.ParkingSpot.POLICE ? Gta8Vehicle.Model.POLICE : s.kind == Gta8World.ParkingSpot.TRUCK ? (random.nextBoolean() ? Gta8Vehicle.Model.VAN : Gta8Vehicle.Model.PICKUP)
                    : Gta8Vehicle.Model.values()[(int) (Gta8Math.hash01((int) s.x, (int) s.z, 5) * 5)];
            int paint = model == Gta8Vehicle.Model.POLICE ? 0x16181C : Gta8Traffic.PAINTS[(int) (Gta8Math.hash01((int) s.x, (int) s.z, 9) * Gta8Traffic.PAINTS.length)];
            Gta8Vehicle v = new Gta8Vehicle(model, s.x, s.z, s.yaw, paint);
            v.parked = true; v.dynamic = true; v.locked = Gta8Math.hash01((int) s.z, (int) s.x, 3) < .6 || model == Gta8Vehicle.Model.POLICE;
            v.y = world.groundHeight(s.x, s.z);
            v.remember();
            vehicles.add(v);
            parked.put(s, v);
        }
    }
    private boolean occupiedByCar(double x, double z) {
        for (Gta8Vehicle v : vehicles) if (Math.abs(v.x - x) < 5 && Math.abs(v.z - z) < 5) return true;
        return false;
    }
    private void clerks() {
        for (Poi poi : world.pois) {
            if (poi.type != Poi.STORE && poi.type != Poi.GUN_SHOP && poi.type != Poi.GAS) continue;
            Gta8Ped c = clerks.get(poi);
            double d = Math.hypot(poi.x - player.x, poi.z - player.z);
            if (c != null && (d > 160 || c.dead && c.deadTime > 30 && d > 60)) { peds.remove(c); clerks.remove(poi); continue; }
            if (c == null && d < 90) {
                c = new Gta8Ped(Kind.CLERK, random.nextInt(2), poi.clerkX, Gta8World.CURB, poi.clerkZ);
                c.id = nextId++;
                pedestrians.dress(c, null);
                c.state = State.WORK; c.yaw = poi.clerkYaw;
                peds.add(c);
                clerks.put(poi, c);
            }
        }
    }
    private double gangCooldown;
    private void gangs() {
        if (time < gangCooldown) return;
        int gangsters = 0;
        for (Gta8Ped p : peds) if (p.kind == Kind.GANG && !p.dead) gangsters++;
        if (gangsters >= 6) return;
        for (Gta8World.Block[] column : world.blocks) for (Gta8World.Block b : column) {
            if (b.district != Gta8World.District.INDUSTRIAL) continue;
            double cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2, d = Math.hypot(cx - player.x, cz - player.z);
            if (d < 90 || d > 210 || random.nextInt(4) != 0) continue;
            double x = b.x0 + 3 + random.nextDouble() * (b.x1 - b.x0 - 6), z = b.z0 + 3 + random.nextDouble() * (b.z1 - b.z0 - 6);
            if (world.blocked(x, Gta8World.CURB, z, .5, 1.8)) continue;
            for (int i = 0; i < 3; i++) {
                double gx = x + Math.cos(i * 2.1) * 1.4, gz = z + Math.sin(i * 2.1) * 1.4;
                if (!world.blocked(gx, Gta8World.CURB, gz, .3, 1.8)) { Gta8Ped g = spawnGangster(gx, gz, Kind.GANG); g.yaw = Math.toDegrees(Math.atan2(x - gx, -(z - gz))); }
            }
            gangCooldown = time + 60;
            return;
        }
    }
    private void gangster(Gta8Ped p, double dt) {
        if (p.dead) { p.deadTime += dt; p.fall = Math.min(1, p.fall + dt * 2.2); return; }
        double d = Math.hypot(player.x - p.x, player.z - p.z);
        if (!p.provoked && player.aim > .5 && d < 12 && player.weapon != Gta8Weapon.FISTS) p.provoked = true;
        if (p.kind == Kind.TARGET && d < 22 && canSee(p, player)) p.provoked = true;
        if (d > 250 && !visible(p.x, p.y + 1, p.z, 1) && p.kind == Kind.GANG) { peds.remove(p); return; }
        if (!p.provoked || dead) { walk(p, 0, 0, 0, dt, false); return; }
        p.thinkTimer -= dt;
        if (p.thinkTimer <= 0) { p.thinkTimer = .35; p.hostile = canSee(p, player); }
        double dx = player.x - p.x, dz = player.z - p.z, dl = Math.max(.1, d);
        double speed = 0, mx = 0, mz = 0;
        if (!p.hostile || d > 22) { speed = 4.4; mx = dx / dl; mz = dz / dl; }
        else if (d < 7) { speed = 2.2; mx = -dx / dl; mz = -dz / dl; }
        walk(p, mx, mz, speed, dt, false);
        p.yaw = Math.toDegrees(Math.atan2(dx, -dz));
        p.aim = 1;
        if (p.hostile) {
            p.shotTimer -= dt;
            if (p.shotTimer <= 0) {
                p.shotTimer = p.weapon.automatic ? .14 + random.nextDouble() * .5 : .55 + random.nextDouble() * .6;
                double acc = Gta8Math.clamp(.45 - d * .012 - player.speed() * .03, .06, .5);
                npcShot(p, player, random.nextDouble() < acc, p.weapon);
            }
        }
    }

    // ------------------------------------------------------------------ perception
    /** Rough camera visibility for spawning decisions. */
    public boolean visible(double x, double y, double z, double radius) {
        double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < radius) return true;
        double cos = (dx * camera.forwardX() + dy * camera.forwardY() + dz * camera.forwardZ()) / d;
        return cos > Math.cos(Math.toRadians(camera.fov * .85)) - radius / d;
    }
    public boolean canSee(Gta8Ped a, Gta8Ped b) {
        double ax = a.x, ay = a.eyeY(), az = a.z, bx = b.x, by = b.vehicle != null ? b.vehicle.y + 1.2 : b.chestY(), bz = b.z;
        double dx = bx - ax, dy = by - ay, dz = bz - az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < .5) return true;
        if (d > 130) return false;
        return world.ray(ax, ay, az, dx / d, dy / d, dz / d, d) >= d - .4;
    }

    // ------------------------------------------------------------------ camera
    public void updateCamera(double frameDt) {
        double a = alpha;
        Gta8Vehicle v = player.vehicle != null && player.state == State.DRIVE ? player.vehicle : null;
        double tx, ty, tz;
        if (v != null) {
            double vx = Gta8Math.lerp(v.prevX, v.x, a), vy = Gta8Math.lerp(v.prevY, v.y, a), vz = Gta8Math.lerp(v.prevZ, v.z, a);
            double vyaw = v.prevYaw + Gta8Math.angleDelta(v.prevYaw, v.yaw) * a;
            double speed = Math.hypot(v.vx, v.vz);
            if (freeLook <= 0 && speed > 1.5) {
                double travel = speed > 4 ? Math.toDegrees(Math.atan2(v.vx, -v.vz)) : vyaw;
                if (v.speed() < -1) travel = vyaw;
                cameraYaw = cameraYaw + Gta8Math.angleDelta(cameraYaw, travel) * Math.min(1, frameDt * 3.2);
                cameraPitch = Gta8Math.damp(cameraPitch, 11, 2, frameDt);
            }
            if (firstPerson) {
                camera.set(v.worldX(0, v.halfLength() - 1.2), vy + v.model.height + .15, 0, vyaw, cameraPitch * .3);
                camera.x = Gta8Math.lerp(v.prevX, v.x, a) + (v.worldX(0, v.halfLength() - 1.2) - v.x);
                camera.z = Gta8Math.lerp(v.prevZ, v.z, a) + (v.worldZ(0, v.halfLength() - 1.2) - v.z);
                camera.fov = 72;
                return;
            }
            double dist = v.model.length * 1.15 + 3.2 + Math.min(2.5, speed * .05);
            tx = vx; ty = vy + v.model.height + .55; tz = vz;
            orbit(tx, ty, tz, dist, 0, 68 + Math.min(12, speed * .25), frameDt);
            return;
        }
        double px = Gta8Math.lerp(player.prevX, player.x, a), py = Gta8Math.lerp(player.prevY, player.y, a), pz = Gta8Math.lerp(player.prevZ, player.z, a);
        if (dead || busted) {
            double t = time * .15;
            camera.set(px + Math.cos(t) * 5, py + 2.8, pz + Math.sin(t) * 5, Math.toDegrees(Math.atan2(-Math.cos(t), Math.sin(t))) + 180, 22);
            camera.fov = 60;
            return;
        }
        if (firstPerson) {
            double bob = player.grounded ? Math.sin(player.phase * 2) * .03 * Math.min(1, player.moveSpeed / 3) : 0;
            camera.set(px + player.forwardX() * .12, py + (player.crouch > .5 ? 1.1 : 1.64) + bob, pz + player.forwardZ() * .12, cameraYaw, cameraPitch);
            camera.fov = player.aim > .5 && player.weapon == Gta8Weapon.SNIPER ? 22 : 75 - player.aim * 20;
            return;
        }
        boolean sniper = player.weapon == Gta8Weapon.SNIPER && player.aim > .7;
        double aim = player.aim;
        double dist = Gta8Math.lerp(3.4, sniper ? 1.1 : 1.55, aim);
        double shoulder = Gta8Math.lerp(.52, .62, aim);
        tx = px; ty = py + (player.crouch > .5 ? 1.2 : 1.58); tz = pz;
        orbit(tx, ty, tz, dist, shoulder, sniper ? 18 : Gta8Math.lerp(70, 50, aim), frameDt);
    }
    /** Spring-arm camera with collision against the world. */
    private void orbit(double tx, double ty, double tz, double dist, double shoulder, double fov, double frameDt) {
        double yaw = Math.toRadians(cameraYaw), pitch = Math.toRadians(cameraPitch);
        double fx = Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = -Math.cos(yaw) * Math.cos(pitch);
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        double px = tx + rx * shoulder, pz = tz + rz * shoulder;
        double bx = -fx, by = -fy, bz = -fz;
        double hit = world.ray(px, ty, pz, bx, by, bz, dist + .3);
        double d = Math.max(.3, Math.min(dist, hit - .3));
        if (!cameraReady) { cameraDistance = d; cameraReady = true; }
        cameraDistance = d < cameraDistance ? d : Gta8Math.damp(cameraDistance, d, 4, frameDt);
        double cx = px + bx * cameraDistance, cy = ty + by * cameraDistance, cz = pz + bz * cameraDistance;
        double ground = world.groundHeight(cx, cz);
        if (cy < ground + .25) cy = ground + .25;
        camera.set(cx, cy, cz, cameraYaw, cameraPitch);
        camera.fov = Gta8Math.damp(camera.fov, fov, 10, frameDt);
    }
}
