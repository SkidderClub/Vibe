package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8World.Solid;
import java.util.ArrayList;
import java.util.List;

/**
 * A car or van. Player and pursuit vehicles run a planar bicycle model with Pacejka-style tyre
 * forces, weight transfer, an automatic gearbox and impulse collisions. Traffic cars follow lanes
 * kinematically until something knocks them off course.
 */
public final class Gta8Vehicle {
    public enum Model {
        SEDAN("Norden Stanza", 4.85, 1.86, 1.46, 2.85, 1.58, 1550, 135, 52, false, 0),
        COMPACT("Kaito Blip", 4.05, 1.74, 1.5, 2.52, 1.5, 1150, 88, 47, false, 1),
        SUV("Atlas Ranger", 4.95, 1.98, 1.78, 2.95, 1.66, 2150, 205, 53, true, 2),
        SPORTS("Vero Fulmine", 4.55, 1.96, 1.22, 2.7, 1.66, 1450, 430, 84, true, 3),
        MUSCLE("Stallion GT", 4.9, 1.92, 1.34, 2.88, 1.6, 1680, 330, 71, true, 4),
        TAXI("Norden Stanza Cab", 4.85, 1.86, 1.46, 2.85, 1.58, 1580, 135, 50, false, 0),
        POLICE("Norden Interceptor", 4.95, 1.88, 1.5, 2.9, 1.6, 1750, 290, 67, true, 0),
        PICKUP("Atlas Rancher", 5.4, 2.0, 1.86, 3.3, 1.7, 2300, 240, 50, true, 5),
        VAN("Norden Cargo", 5.3, 2.0, 2.35, 3.25, 1.72, 2450, 125, 42, false, 6);
        public final String title;
        public final double length, width, height, wheelbase, track, mass, powerKw, topSpeed;
        public final boolean rearDrive;
        public final int body;
        Model(String title, double length, double width, double height, double wheelbase, double track, double mass, double powerKw, double topSpeed, boolean rearDrive, int body) {
            this.title = title; this.length = length; this.width = width; this.height = height; this.wheelbase = wheelbase; this.track = track;
            this.mass = mass; this.powerKw = powerKw; this.topSpeed = topSpeed; this.rearDrive = rearDrive; this.body = body;
        }
        public double wheelRadius() { return this == SUV || this == PICKUP ? .38 : this == VAN ? .36 : this == SPORTS ? .34 : .32; }
    }

    private static final double G = 9.81;
    private static final double[] GEARS = {3.3, 2.05, 1.45, 1.1, .88, .72};
    public final Model model;
    public int paint, paint2;
    public double x, y, z, yaw, vx, vy, vz, yawRate;
    public double prevX, prevY, prevZ, prevYaw, prevPitch, prevRoll;
    public double steer, throttle, brake, pitch, roll, bodyPitch, bodyRoll;
    public boolean handbrake, grounded = true;
    public double rpm = 800, wheelSpin, wheelSpinRate, slip, skid;
    public int gear = 1;
    public double health = 1000, fire, burnTimer;
    public boolean exploded, dynamic, parked, siren, lightsOn, horn, reversing, locked;
    public int indicator;
    public final boolean[] burst = new boolean[4];
    public Gta8Ped driver;
    public Gta8Traffic.Brain ai;
    public double stuck, lastImpact, lastImpactSpeed, sinceHit = 99, sirenPhase, submerged;
    public boolean mission;
    public List<double[]> route;
    public double routeTime;
    private final double inertia;
    private final List<Solid> near = new ArrayList<Solid>();

    public Gta8Vehicle(Model model, double x, double z, double yaw, int paint) {
        this.model = model; this.x = prevX = x; this.z = prevZ = z; this.yaw = prevYaw = yaw; this.paint = paint;
        this.paint2 = model == Model.POLICE ? 0xF2F2EE : model == Model.TAXI ? 0x1A1A1A : paint;
        inertia = model.mass * (model.length * model.length + model.width * model.width) / 12 * 1.1;
    }

    // ------------------------------------------------------------------ geometry
    public double forwardX() { return Math.sin(Math.toRadians(yaw)); }
    public double forwardZ() { return -Math.cos(Math.toRadians(yaw)); }
    public double rightX() { return Math.cos(Math.toRadians(yaw)); }
    public double rightZ() { return Math.sin(Math.toRadians(yaw)); }
    public double speed() { return vx * forwardX() + vz * forwardZ(); }
    public double speedKmh() { return Math.hypot(vx, vz) * 3.6; }
    public double halfLength() { return model.length / 2; }
    public double halfWidth() { return model.width / 2; }
    public boolean destroyed() { return exploded || health <= 0; }
    /** World position of a point in car space (right, forward). */
    public double worldX(double right, double forward) { return x + rightX() * right + forwardX() * forward; }
    public double worldZ(double right, double forward) { return z + rightZ() * right + forwardZ() * forward; }
    /** Driver door, on the left side. */
    public double doorX() { return worldX(-halfWidth() - .55, model.wheelbase * .12); }
    public double doorZ() { return worldZ(-halfWidth() - .55, model.wheelbase * .12); }
    public boolean contains(double px, double pz, double margin) {
        double dx = px - x, dz = pz - z;
        double f = dx * forwardX() + dz * forwardZ(), r = dx * rightX() + dz * rightZ();
        return Math.abs(f) < halfLength() + margin && Math.abs(r) < halfWidth() + margin;
    }
    /** Ray distance to the oriented body box, or {@code limit}. */
    public double ray(double px, double py, double pz, double dx, double dy, double dz, double limit) {
        double ox = px - x, oz = pz - z;
        double lf = ox * forwardX() + oz * forwardZ(), lr = ox * rightX() + oz * rightZ();
        double df = dx * forwardX() + dz * forwardZ(), dr = dx * rightX() + dz * rightZ();
        double near = 0, far = limit;
        double[] o = {lr, py - y, lf}, d = {dr, dy, df}, lo = {-halfWidth(), .25, -halfLength()}, hi = {halfWidth(), model.height, halfLength()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) { if (o[i] < lo[i] || o[i] > hi[i]) return limit; continue; }
            double a = (lo[i] - o[i]) / d[i], b = (hi[i] - o[i]) / d[i];
            near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
        }
        return far >= near ? near : limit;
    }

    public void remember() { prevX = x; prevY = y; prevZ = z; prevYaw = yaw; prevPitch = pitch; prevRoll = roll; }

    // ------------------------------------------------------------------ physics
    /** Advances the rigid body; {@code grip} scales tyre friction (wet roads). */
    public void physics(double dt, Gta8World world, double grip) {
        double fx = forwardX(), fz = forwardZ(), rx = rightX(), rz = rightZ();
        double u = vx * fx + vz * fz, w = vx * rx + vz * rz;
        double L = model.wheelbase, a = L * .47, b = L - a, h = model.height * .38, m = model.mass;
        boolean drivable = !destroyed() && submerged < .6;
        double mu = 1.05 * grip * (onRoadSurface(world) ? 1 : .8);
        // Engine and gearbox.
        double wheelR = model.wheelRadius(), finalDrive = 3.6;
        double wheelRpm = Math.abs(u) / wheelR * 60 / (2 * Math.PI);
        double drive = 0;
        if (drivable) {
            if (throttle < -.05 && u < 1.5) reversing = true;
            if (throttle > .05 && u > -1.5) reversing = false;
            double ratio = reversing ? 3.2 : GEARS[gear - 1];
            rpm = Gta8Math.clamp(wheelRpm * ratio * finalDrive, 850, 7200);
            if (!reversing) {
                if (rpm > 6300 && gear < GEARS.length) gear++;
                else if (gear > 1 && rpm < 2300) gear--;
            }
            double pedal = Math.abs(throttle);
            if (pedal > .02 && (reversing ? throttle < 0 : throttle > 0)) {
                double torqueCurve = .72 + .28 * Math.sin(Gta8Math.clamp((rpm - 1000) / 5200, 0, 1) * Math.PI);
                double peakForce = model.powerKw * 1000 / Math.max(8, model.topSpeed * .42);
                double force = peakForce * torqueCurve * (reversing ? .45 : GEARS[0] / ratio * .55 + .45);
                double powerLimit = model.powerKw * 1000 * .9 / Math.max(2.5, Math.abs(u));
                drive = Math.min(force, powerLimit) * pedal * (reversing ? -1 : 1);
                rpm = Math.max(rpm, 1500 + 3500 * pedal * (Math.abs(u) < 3 ? 1 : 0));
            }
        } else rpm = Gta8Math.approach(rpm, 0, dt * 3000);
        // Aerodynamic drag sized for the rated top speed; rolling resistance.
        double dragK = model.powerKw * 1000 * .9 / Math.pow(model.topSpeed, 3);
        double resist = -dragK * u * Math.abs(u) - Math.signum(u) * m * G * .013;
        double brakeForce = 0;
        if (brake > 0) brakeForce = -Math.signum(u) * Math.min(Math.abs(u) / dt * m, m * G * .95 * mu * brake);
        if (!drivable && throttle == 0) brakeForce += -Math.signum(u) * Math.min(Math.abs(u) / dt * m, m * G * .2);
        // Longitudinal load transfer from the last step's acceleration.
        double ax = (drive + resist + brakeForce) / m;
        double fzFront = m * G * b / L - m * ax * h / L, fzRear = m * G * a / L + m * ax * h / L;
        fzFront = Math.max(fzFront, m * G * .15); fzRear = Math.max(fzRear, m * G * .15);
        // Slip angles; below walking pace fall back to kinematic steering to avoid singularities.
        double speed = Math.hypot(u, w);
        double alphaF = Math.atan2(w + a * yawRate, Math.max(1.5, Math.abs(u))) - steer * Math.signum(u == 0 ? 1 : u);
        double alphaR = Math.atan2(w - b * yawRate, Math.max(1.5, Math.abs(u)));
        double muRear = mu * (handbrake ? .45 : 1);
        double tyreF = burst[0] || burst[1] ? .55 : 1, tyreR = burst[2] || burst[3] ? .55 : 1;
        double fyF = -mu * fzFront * tyreF * pacejka(alphaF);
        double fyR = -muRear * fzRear * tyreR * pacejka(alphaR);
        // Friction circle at the driven axle: wheelspin eats lateral grip.
        double driveF = model.rearDrive ? 0 : drive, driveR = model.rearDrive ? drive : 0;
        if (handbrake) { brakeForce += -Math.signum(u) * Math.min(Math.abs(u) / dt * m * .5, muRear * fzRear * .8); driveR *= .3; }
        double capR = muRear * fzRear, capF = mu * fzFront;
        double longR = driveR + brakeForce * .35, longF = driveF + brakeForce * .65;
        double usedR = Math.hypot(longR, fyR), usedF = Math.hypot(longF, fyF);
        slip = 0;
        if (usedR > capR) { double k = capR / usedR; fyR *= k; longR *= k; slip = Math.max(slip, 1 - k); }
        if (usedF > capF) { double k = capF / usedF; fyF *= k; longF *= k; slip = Math.max(slip, 1 - k); }
        double lateralSlip = Math.abs(w) > 1.5 && speed > 4 ? Gta8Math.clamp((Math.abs(w) - 1.5) / 4, 0, 1) : 0;
        skid = Math.max(slip, lateralSlip) * (grounded ? 1 : 0);
        if (!grounded) { fyF = fyR = longF = longR = 0; }
        double cs = Math.cos(steer), sn = Math.sin(steer);
        double forceLong = longF * cs - fyF * sn + longR + resist;
        double forceLat = fyF * cs + longF * sn + fyR;
        double torque = a * (fyF * cs + longF * sn) - b * fyR;
        // Slope: gravity along the ground.
        double slopeF = groundSlope(world, fx, fz), slopeR = groundSlope(world, rx, rz);
        forceLong -= m * G * slopeF;
        forceLat -= m * G * slopeR;
        double du = forceLong / m + w * yawRate, dw = forceLat / m - u * yawRate;
        u += du * dt; w += dw * dt;
        yawRate += torque / inertia * dt;
        if (speed < 2.5 && grounded) {
            // Low-speed kinematic blend keeps parking manoeuvres precise.
            double k = Gta8Math.clamp(1 - speed / 2.5, 0, 1);
            yawRate = Gta8Math.lerp(yawRate, u * Math.tan(steer) / L, k);
            w *= 1 - k * Math.min(1, dt * 12);
        }
        yawRate *= Math.exp(-dt * (grounded ? .5 : .2));
        vx = fx * u + rx * w; vz = fz * u + rz * w;
        x += vx * dt; z += vz * dt;
        yaw = Gta8Math.wrap(yaw + Math.toDegrees(yawRate * dt));
        suspension(world, dt, forceLong / m, forceLat / m);
        wheelSpinRate = u / wheelR + (drivable && Math.abs(drive) > 0 && slip > .3 ? Math.signum(drive) * 30 : 0);
        wheelSpin += wheelSpinRate * dt;
        sinceHit += dt;
        if (submerged > 0 && !destroyed()) health = Math.max(0, health - dt * 40 * submerged);
    }
    /** Simplified Pacejka magic formula, normalised to peak 1 near 7 degrees of slip. */
    static double pacejka(double alpha) {
        double B = 10, C = 1.45, E = -.4;
        double x = B * alpha;
        return Math.sin(C * Math.atan(x - E * (x - Math.atan(x))));
    }
    private boolean onRoadSurface(Gta8World world) {
        double g = world.groundHeight(x, z);
        return g < .2 || world.onSidewalk(x, z);
    }
    private double groundSlope(Gta8World world, double dx, double dz) {
        double d = 1.2;
        return Gta8Math.clamp((world.groundHeight(x + dx * d, z + dz * d) - world.groundHeight(x - dx * d, z - dz * d)) / (2 * d), -.6, .6) * (grounded ? 1 : 0);
    }
    /** Wheel contact heights, body pitch/roll from terrain and springs, and airborne ballistics. */
    private void suspension(Gta8World world, double dt, double ax, double ay) {
        double hl = model.wheelbase / 2, hw = model.track / 2;
        double fl = world.groundHeight(worldX(-hw, hl), worldZ(-hw, hl)), fr = world.groundHeight(worldX(hw, hl), worldZ(hw, hl));
        double rl = world.groundHeight(worldX(-hw, -hl), worldZ(-hw, -hl)), rr = world.groundHeight(worldX(hw, -hl), worldZ(hw, -hl));
        double ground = (fl + fr + rl + rr) / 4;
        double water = world.waterLevelAt(x, z);
        submerged = Gta8Math.clamp((water - ground - .3) / 1.2, 0, 1) * (ground < water ? 1 : 0);
        if (y > ground + .08 && !grounded || y > ground + .5) {
            vy -= G * dt;
            y += vy * dt;
            if (y <= ground) {
                double impact = -vy;
                y = ground; vy = 0; grounded = true;
                if (impact > 7) damage((impact - 7) * 22);
            } else grounded = false;
        } else {
            // Kerbs push the car up; it can leave the ground over crests at speed.
            double target = ground;
            if (target < y - .02) { vy = Math.min(vy, 0) - G * dt; y = Math.max(target, y + vy * dt); if (y > target + .08) grounded = false; }
            else { vy = (target - y) / Math.max(dt, 1e-4) * .5; y = target; grounded = true; }
        }
        if (submerged > 0) { vx *= Math.exp(-dt * 2.5); vz *= Math.exp(-dt * 2.5); y = Math.max(y - dt * .6 * submerged, water - model.height * .7); }
        double terrainPitch = Math.toDegrees(Math.atan2((fl + fr) / 2 - (rl + rr) / 2, model.wheelbase));
        double terrainRoll = Math.toDegrees(Math.atan2((fr + rr) / 2 - (fl + rl) / 2, model.track));
        // Body squat under power, dive under braking and outward lean in corners, spring-damped.
        double targetPitch = Gta8Math.clamp(ax * .35, -4.5, 3.5), targetRoll = Gta8Math.clamp(ay * .45, -5, 5);
        bodyPitch = Gta8Math.damp(bodyPitch, targetPitch, 9, dt);
        bodyRoll = Gta8Math.damp(bodyRoll, targetRoll, 8, dt);
        pitch = terrainPitch + bodyPitch;
        roll = terrainRoll + bodyRoll;
    }

    public void damage(double amount) {
        if (exploded || amount <= 0) return;
        health = Math.max(0, health - amount);
        sinceHit = 0;
        if (health < 250 && fire <= 0 && health > 0 && amount > 1) fire = health < 120 ? 1 : 0;
        if (health <= 0 && burnTimer <= 0) burnTimer = 4.5;
    }

    // ------------------------------------------------------------------ collisions
    /** Separating-axis collision against static solids, with impulse response. Returns impact speed. */
    public double collideWorld(Gta8World world) {
        double hl = halfLength(), hw = halfWidth();
        double ext = Math.abs(forwardX()) * hl + Math.abs(rightX()) * hw, extZ = Math.abs(forwardZ()) * hl + Math.abs(rightZ()) * hw;
        world.solidsNear(x - ext - .5, z - extZ - .5, x + ext + .5, z + extZ + .5, near);
        double worst = 0;
        for (Solid s : near) {
            if (s.y1 < y + .35 || s.y0 > y + model.height) continue;
            if (s.owner != null && s.owner.breakable && Math.hypot(vx, vz) > 3.5) {
                // Lamp posts, meters and hydrants give way instead of stopping the car dead.
                world.knock(s.owner);
                knocked.add(s.owner);
                double k = Math.max(.82, 1 - 60 / model.mass);
                vx *= k; vz *= k;
                damage(15);
                continue;
            }
            double[] mtv = obbVsBox(s.x0, s.z0, s.x1, s.z1);
            if (mtv == null) continue;
            x += mtv[0]; z += mtv[1];
            double nx = mtv[0] / mtv[2], nz = mtv[1] / mtv[2];
            double cx = mtv[3], cz = mtv[4];
            double hit = impulse(nx, nz, cx, cz, .18);
            worst = Math.max(worst, hit);
        }
        if (worst > 3.5) damage((worst - 3.5) * (worst - 3.5) * 5.5);
        if (worst > .5) { lastImpact = 0; lastImpactSpeed = worst; }
        return worst;
    }
    public final List<Gta8World.Prop> knocked = new ArrayList<Gta8World.Prop>();

    /** Applies a collision impulse against an immovable surface at contact point (cx, cz). */
    private double impulse(double nx, double nz, double cx, double cz, double restitution) {
        double rx = cx - x, rz = cz - z;
        double wv = yawRate;
        // Velocity of the contact point (planar: v + omega x r, with omega about -Y for our yaw sense).
        double pvx = vx - wv * rz, pvz = vz + wv * rx;
        double vn = pvx * nx + pvz * nz;
        if (vn >= 0) return 0;
        double rn = rx * nz - rz * nx;
        double invMass = 1 / model.mass, invI = 1 / inertia;
        double j = -(1 + restitution) * vn / (invMass + rn * rn * invI);
        vx += j * nx * invMass; vz += j * nz * invMass;
        yawRate += j * rn * invI;
        // Scrape friction along the wall.
        double tx = -nz, tz = nx, vt = pvx * tx + pvz * tz;
        double jt = Gta8Math.clamp(-vt / (invMass * 2), -j * .35, j * .35);
        vx += jt * tx * invMass; vz += jt * tz * invMass;
        return -vn;
    }

    /** Minimum translation (dx, dz, depth, contactX, contactZ) separating this car from an AABB, or null. */
    double[] obbVsBox(double bx0, double bz0, double bx1, double bz1) {
        double hl = halfLength(), hw = halfWidth();
        double fx = forwardX(), fz = forwardZ(), rx = rightX(), rz = rightZ();
        double bcx = (bx0 + bx1) / 2, bcz = (bz0 + bz1) / 2, bhx = (bx1 - bx0) / 2, bhz = (bz1 - bz0) / 2;
        double dx = bcx - x, dz = bcz - z;
        double[][] axes = {{1, 0}, {0, 1}, {fx, fz}, {rx, rz}};
        double best = Double.POSITIVE_INFINITY, ax = 0, az = 0;
        for (double[] axis : axes) {
            double ra = hl * Math.abs(fx * axis[0] + fz * axis[1]) + hw * Math.abs(rx * axis[0] + rz * axis[1]);
            double rb = bhx * Math.abs(axis[0]) + bhz * Math.abs(axis[1]);
            double d = dx * axis[0] + dz * axis[1];
            double overlap = ra + rb - Math.abs(d);
            if (overlap <= 0) return null;
            if (overlap < best) { best = overlap; double s = d > 0 ? -1 : 1; ax = axis[0] * s; az = axis[1] * s; }
        }
        // Contact: the car corner deepest along -axis, clamped into the box.
        double cx = x, cz = z, deepest = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            double sf = i < 2 ? hl : -hl, sr = i % 2 == 0 ? hw : -hw;
            double px = x + fx * sf + rx * sr, pz = z + fz * sf + rz * sr;
            double depth = -(px * ax + pz * az);
            if (depth > deepest) { deepest = depth; cx = px; cz = pz; }
        }
        cx = Gta8Math.clamp(cx, bx0, bx1); cz = Gta8Math.clamp(cz, bz0, bz1);
        return new double[]{ax * best, az * best, best, cx, cz};
    }

    /** Car-versus-car separating axis test and two-body impulse. Returns impact speed. */
    public static double collide(Gta8Vehicle a, Gta8Vehicle b) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double reach = a.halfLength() + b.halfLength();
        if (dx * dx + dz * dz > reach * reach || Math.abs(a.y - b.y) > 2) return 0;
        double[][] axes = {{a.forwardX(), a.forwardZ()}, {a.rightX(), a.rightZ()}, {b.forwardX(), b.forwardZ()}, {b.rightX(), b.rightZ()}};
        double best = Double.POSITIVE_INFINITY, nx = 0, nz = 0;
        for (double[] axis : axes) {
            double ra = a.halfLength() * Math.abs(a.forwardX() * axis[0] + a.forwardZ() * axis[1]) + a.halfWidth() * Math.abs(a.rightX() * axis[0] + a.rightZ() * axis[1]);
            double rb = b.halfLength() * Math.abs(b.forwardX() * axis[0] + b.forwardZ() * axis[1]) + b.halfWidth() * Math.abs(b.rightX() * axis[0] + b.rightZ() * axis[1]);
            double d = dx * axis[0] + dz * axis[1];
            double overlap = ra + rb - Math.abs(d);
            if (overlap <= 0) return 0;
            if (overlap < best) { best = overlap; double s = d > 0 ? 1 : -1; nx = axis[0] * s; nz = axis[1] * s; }
        }
        // n points from a to b. Contact near the midpoint of the overlap.
        double cx = (a.x + b.x) / 2, cz = (a.z + b.z) / 2;
        double ma = a.dynamic ? a.model.mass : 1e9, mb = b.dynamic ? b.model.mass : 1e9;
        double share = mb / (ma + mb);
        a.x -= nx * best * share; a.z -= nz * best * share;
        b.x += nx * best * (1 - share); b.z += nz * best * (1 - share);
        double rax = cx - a.x, raz = cz - a.z, rbx = cx - b.x, rbz = cz - b.z;
        double vax = a.vx - a.yawRate * raz, vaz = a.vz + a.yawRate * rax;
        double vbx = b.vx - b.yawRate * rbz, vbz = b.vz + b.yawRate * rbx;
        double vn = (vbx - vax) * nx + (vbz - vaz) * nz;
        if (vn >= 0) return 0;
        double rna = rax * nz - raz * nx, rnb = rbx * nz - rbz * nx;
        double invA = a.dynamic ? 1 / a.model.mass : 0, invB = b.dynamic ? 1 / b.model.mass : 0;
        double invIA = a.dynamic ? 1 / a.inertia : 0, invIB = b.dynamic ? 1 / b.inertia : 0;
        double j = -(1.25) * vn / (invA + invB + rna * rna * invIA + rnb * rnb * invIB);
        a.vx -= j * nx * invA; a.vz -= j * nz * invA; a.yawRate -= j * rna * invIA;
        b.vx += j * nx * invB; b.vz += j * nz * invB; b.yawRate += j * rnb * invIB;
        double impact = -vn;
        if (impact > 3) { double dmg = (impact - 3) * (impact - 3) * 4.5; a.damage(dmg); b.damage(dmg); }
        a.lastImpactSpeed = b.lastImpactSpeed = impact; a.lastImpact = b.lastImpact = 0;
        return impact;
    }

    /** Headlights on at dusk; traffic signals indicators; siren phase. */
    public void cosmetics(double dt, double night, double time) {
        lightsOn = night > .3 || lightsOn && night > .2;
        if (siren) sirenPhase += dt;
        lastImpact += dt;
    }
}
