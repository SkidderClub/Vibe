package dev.vibe.game.gta8;

/** A person: the player, a pedestrian, a police officer, a gang member or a shop clerk. */
public final class Gta8Ped {
    public enum Kind { PLAYER, CIVILIAN, COP, GANG, CLERK, TARGET }
    public enum State { WANDER, IDLE, WAIT_CROSSING, CROSSING, FLEE, COWER, ATTACK, ARREST, DRIVE, ENTER_CAR, EXIT_CAR, RAGDOLL, DEAD, HANDS_UP, WORK, CHASE }

    public final Kind kind;
    /** 0 male, 1 female. */
    public final int body;
    /** Clothing style: 0 casual, 1 business, 2 police, 3 sport, 4 gang, 5 beach, 6 work. */
    public int outfit;
    /** Skin, top, trousers, shoes, hair, accent, second top, accessory; alpha channel unused. */
    public final int[] palette = new int[8];
    public boolean hat, glasses, bag, longHair;
    public double x, y, z, yaw, vx, vy, vz;
    public double prevX, prevY, prevZ, prevYaw;
    public double health, maxHealth, armor;
    public boolean dead, grounded = true;
    public double deadTime, fall, fallYaw, fallSide;
    // Animation drivers read by the renderer.
    public double phase, moveSpeed, aim, crouch, flinch, punch, reloadAnim, recoil, handsUp, cower, phone, sit, lookYaw, lookPitch, jump;
    public State state = State.WANDER;
    public Gta8Weapon weapon = Gta8Weapon.FISTS;
    public int clip;
    public Gta8Vehicle vehicle;
    public double enterTimer;
    // Ragdoll-lite: a tumbling body until it settles.
    public double tumbleX, tumbleZ, tumbleSpin;
    // Artificial intelligence.
    public Gta8World.WalkNode node, goal;
    public double lateral, waitTimer, thinkTimer, fleeTimer, alertTimer, reportTimer, fleeX, fleeZ, shotTimer, burstLeft, strafe;
    public double targetX, targetZ, speedPreference = 1.4, courage;
    public boolean witness, provoked, hostile, arresting;
    public Gta8Ped enemy;
    public int cash;
    public double spawnTime, stuck;
    public int id;

    public Gta8Ped(Kind kind, int body, double x, double y, double z) {
        this.kind = kind; this.body = body;
        this.x = prevX = x; this.y = prevY = y; this.z = prevZ = z;
        maxHealth = health = kind == Kind.COP ? 130 : kind == Kind.PLAYER ? 200 : kind == Kind.GANG ? 120 : kind == Kind.TARGET ? 160 : 100;
        if (kind == Kind.COP) armor = 40;
    }
    public void remember() { prevX = x; prevY = y; prevZ = z; prevYaw = yaw; }
    public double eyeY() { return y + (crouch > .5 ? 1.1 : 1.62) - fall * 1.2; }
    public double chestY() { return y + (crouch > .5 ? .8 : 1.25) - fall * .9; }
    public double height() { return crouch > .5 ? 1.2 : 1.78; }
    public double speed() { return Math.hypot(vx, vz); }
    public boolean alive() { return !dead; }
    public double forwardX() { return Math.sin(Math.toRadians(yaw)); }
    public double forwardZ() { return -Math.cos(Math.toRadians(yaw)); }
    public boolean inVehicle() { return vehicle != null && (state == State.DRIVE); }

    /**
     * Ray against a head sphere and a body capsule. Returns the distance, or {@code limit};
     * {@code zone[0]} is set to 2 (head), 1 (torso) or 0 (limbs).
     */
    public double ray(double px, double py, double pz, double dx, double dy, double dz, double limit, int[] zone) {
        if (dead && fall > .5) {
            double t = sphere(px, py, pz, dx, dy, dz, x, y + .25, z, .45, limit);
            if (zone != null && t < limit) zone[0] = 1;
            return t;
        }
        double h = height();
        double headY = y + h - .12;
        double best = limit;
        double th = sphere(px, py, pz, dx, dy, dz, x, headY, z, .13, limit);
        if (th < best) { best = th; if (zone != null) zone[0] = 2; }
        // Body capsule approximated by stacked spheres from the feet to the shoulders.
        for (double yy = y + .2; yy < headY - .2; yy += .22) {
            double t = sphere(px, py, pz, dx, dy, dz, x, yy, z, .26, best);
            if (t < best) { best = t; if (zone != null) zone[0] = yy > y + h * .5 ? 1 : 0; }
        }
        return best;
    }
    static double sphere(double px, double py, double pz, double dx, double dy, double dz, double cx, double cy, double cz, double r, double limit) {
        double ox = px - cx, oy = py - cy, oz = pz - cz;
        double b = ox * dx + oy * dy + oz * dz, c = ox * ox + oy * oy + oz * oz - r * r;
        double disc = b * b - c;
        if (disc < 0) return limit;
        double t = -b - Math.sqrt(disc);
        if (t < 0) t = c < 0 ? 0 : limit;
        return t < limit ? t : limit;
    }

    /** Applies damage, armour first. Returns true if this killed the ped. */
    public boolean damage(double amount) {
        if (dead || amount <= 0) return false;
        double absorbed = Math.min(armor, amount * .7);
        armor -= absorbed;
        health -= amount - absorbed;
        flinch = .25;
        if (health <= 0) {
            health = 0; dead = true; state = State.DEAD; deadTime = 0;
            return true;
        }
        return false;
    }
    /** Starts a tumble, e.g. when struck by a car or blasted by an explosion. */
    public void knock(double ivx, double ivy, double ivz) {
        vx = ivx; vy = ivy; vz = ivz;
        grounded = false;
        if (state != State.DEAD) state = State.RAGDOLL;
        tumbleSpin = (Math.random() - .5) * 12;
        fallYaw = Math.toDegrees(Math.atan2(ivx, -ivz));
    }
}
