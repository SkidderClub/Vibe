package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8Vehicle.Model;

import static dev.vibe.game.gta8.Gta8Materials.*;

/**
 * Lofted car bodies. Each model is a skinned mesh: bone 0 is the body, bones 1-4 the wheels
 * (front-left, front-right, rear-left, rear-right) centred on their hubs. Lights use emissive
 * groups driven per car: 0 head, 1 tail, 2 brake, 3 left indicator, 4 right indicator, 5 reverse,
 * 6 red and 7 blue emergency lights, 8 taxi sign.
 */
final class Gta8VehicleModels {
    private Gta8VehicleModels() { }

    /** Side profile keyframes: t (0 = front bumper, 1 = rear), belt height, roof height (or -1), half-width factor. */
    private static double[][] profile(Model m) {
        switch (m) {
            case COMPACT: return new double[][]{{0, .55, -1, .86}, {.05, .66, -1, .95}, {.25, .82, -1, 1}, {.33, .88, 1.06, 1}, {.5, .9, 1.5, 1}, {.8, .92, 1.47, 1}, {.93, .9, 1.02, .98}, {1, .7, -1, .9}};
            case SUV: return new double[][]{{0, .7, -1, .88}, {.05, .88, -1, .96}, {.22, 1.05, -1, 1}, {.32, 1.1, 1.24, 1}, {.44, 1.12, 1.76, 1}, {.9, 1.12, 1.74, 1}, {.97, 1.1, 1.3, .98}, {1, .85, -1, .92}};
            case SPORTS: return new double[][]{{0, .42, -1, .86}, {.06, .52, -1, .96}, {.3, .72, -1, 1}, {.38, .78, .84, 1}, {.52, .8, 1.2, .98}, {.66, .82, 1.17, .98}, {.8, .84, .86, 1}, {.95, .82, -1, .98}, {1, .62, -1, .9}};
            case MUSCLE: return new double[][]{{0, .56, -1, .9}, {.05, .76, -1, .97}, {.36, .86, -1, 1}, {.42, .9, .98, 1}, {.52, .92, 1.32, .99}, {.68, .94, 1.3, .99}, {.8, .96, .98, 1}, {.97, .92, -1, .98}, {1, .72, -1, .92}};
            case PICKUP: return new double[][]{{0, .74, -1, .9}, {.05, .95, -1, .97}, {.24, 1.12, -1, 1}, {.31, 1.16, 1.28, 1}, {.4, 1.18, 1.84, 1}, {.55, 1.18, 1.82, 1}, {.58, 1.16, -1, 1}, {1, 1.12, -1, .98}};
            case VAN: return new double[][]{{0, .78, -1, .9}, {.06, 1.0, -1, .97}, {.14, 1.12, 1.35, 1}, {.24, 1.16, 2.3, 1}, {.97, 1.18, 2.3, 1}, {1, 1.1, 2.2, .96}};
            default: return new double[][]{{0, .56, -1, .86}, {.05, .7, -1, .96}, {.28, .86, -1, 1}, {.33, .9, .98, 1}, {.44, .92, 1.44, .99}, {.7, .94, 1.42, .99}, {.8, .96, 1.0, 1}, {.97, .92, -1, .98}, {1, .72, -1, .92}};
        }
    }
    private static double[] sample(double[][] keys, double t) {
        for (int i = 0; i + 1 < keys.length; i++) {
            if (t > keys[i + 1][0] && i + 2 < keys.length) continue;
            double k = Gta8Math.clamp((t - keys[i][0]) / (keys[i + 1][0] - keys[i][0]), 0, 1);
            double s = k * k * (3 - 2 * k);
            double roof0 = keys[i][2], roof1 = keys[i + 1][2];
            double roof = roof0 < 0 && roof1 < 0 ? -1 : Gta8Math.lerp(roof0 < 0 ? Gta8Math.lerp(keys[i][1], keys[i + 1][1], k) : roof0, roof1 < 0 ? Gta8Math.lerp(keys[i][1], keys[i + 1][1], k) : roof1, k);
            if (roof0 < 0 && roof1 >= 0 || roof0 >= 0 && roof1 < 0) roof = Gta8Math.lerp(roof0 < 0 ? Gta8Math.lerp(keys[i][1], keys[i + 1][1], k) : roof0, roof1 < 0 ? keys[i + 1][1] : roof1, k);
            return new double[]{Gta8Math.lerp(keys[i][1], keys[i + 1][1], s), roof, Gta8Math.lerp(keys[i][3], keys[i + 1][3], s)};
        }
        double[] last = keys[keys.length - 1];
        return new double[]{last[1], last[2], last[3]};
    }

    static double frontAxle(Model m) { return -m.wheelbase / 2 - (m == Model.VAN ? .15 : .05); }
    /** Height the wings must reach over a wheel at body position z, or 0 away from the axles. */
    private static double fenderTop(Model m, double z) {
        double wr = m.wheelRadius(), archR = wr + .07, top = 0;
        for (double axle : new double[]{frontAxle(m), rearAxle(m)}) {
            double dz = z - axle;
            if (Math.abs(dz) < archR) top = Math.max(top, wr + Math.sqrt(archR * archR - dz * dz) + .06);
        }
        return top;
    }
    static double rearAxle(Model m) { return m.wheelbase / 2 - .05; }

    /** Returns {body, glass}; glass is drawn in the transparent pass so occupants stay visible. */
    static Gta8MeshBuilder[] build(Model m) {
        Gta8MeshBuilder b = new Gta8MeshBuilder(), glass = new Gta8MeshBuilder();
        b.bone(0).seed(0).ao(1).color(0xFFFFFF);
        glass.bone(0).seed(0).ao(1).color(0x101418).material(CARGLASS);
        double L = m.length, hw = m.width / 2, wb = m.wheelbase, wr = m.wheelRadius();
        double frontAxle = frontAxle(m), rearAxle = rearAxle(m);
        double[][] keys = profile(m);
        double clearance = m == Model.SUV || m == Model.PICKUP ? .42 : m == Model.SPORTS ? .2 : m == Model.VAN ? .36 : .3;
        int sections = 64;
        // Ring layout per section (right half, mirrored): 0 bottom-centre, 1 bottom edge, 2 sill, 3 lower side, 4 belt,
        // 5 window bottom, 6 window top, 7 roof edge, 8 roof centre.
        double[][] rx = new double[sections + 1][9], ry = new double[sections + 1][9];
        double[] zs = new double[sections + 1];
        boolean[] roofed = new boolean[sections + 1];
        for (int i = 0; i <= sections; i++) {
            double t = i / (double) sections, z = -L / 2 + t * L;
            zs[i] = z;
            double[] s = sample(keys, t);
            double belt = s[0], roof = s[1], w = hw * s[2];
            double bottom = clearance;
            for (double axle : new double[]{frontAxle, rearAxle}) {
                double dz = z - axle, archR = wr + .07;
                if (Math.abs(dz) < archR) bottom = Math.max(bottom, wr + Math.sqrt(archR * archR - dz * dz) - .02);
            }
            bottom = Math.min(bottom, belt - .12);
            boolean hasRoof = roof > belt + .05;
            roofed[i] = hasRoof;
            double tumble = hasRoof ? .8 : 1;
            double[] xs = {0, w * .9, w, w * 1.01, w * .99, w * .97, w * tumble * .96, w * tumble * .88, 0};
            double top = hasRoof ? roof : belt;
            double[] ys = {bottom - .02, bottom, bottom + .06, (bottom + belt) / 2, belt, belt + .02, hasRoof ? top - .11 : belt + .03, top, top + (hasRoof ? .025 : .01)};
            if (!hasRoof) { xs[5] = w * .95; xs[6] = w * .9; xs[7] = w * .75; ys[5] = belt + .01; ys[6] = belt + .02; ys[7] = belt + .025; ys[8] = belt + .035; }
            // Low bonnets bulge into wings over the tyres, like on real sports cars.
            double wing = fenderTop(m, z);
            if (!hasRoof && wing > belt) for (int k = 4; k <= 7; k++) ys[k] = Math.max(ys[k], wing + (k - 4) * .006);
            rx[i] = xs; ry[i] = ys;
        }
        double bPillar = (frontAxle + rearAxle) / 2 + (m == Model.COMPACT ? .1 : 0);
        // Rear end of the side glass: the C-pillar starts here (vans and pickups keep their windows short).
        double lastGlass = -L / 2;
        for (int i = 0; i <= sections; i++) if (roofed[i]) lastGlass = zs[i];
        for (int i = 0; i < sections; i++) {
            double za = zs[i], zb = zs[i + 1];
            boolean glassZone = roofed[i] && roofed[i + 1];
            for (int k = 0; k < 8; k++) {
                int material = CARPAINT, param = 0;
                boolean window = glassZone && (k == 5);
                double mid = (za + zb) / 2;
                boolean pillar = Math.abs(mid - bPillar) < .09 || mid > lastGlass - (m == Model.VAN ? .2 : m == Model.SUV ? .28 : .42) && m != Model.PICKUP;
                boolean windshield = glassZone && k == 7 && (ry[i][8] - ry[i + 1][8] > .02 || ry[i + 1][8] - ry[i][8] > .02) && Math.abs(ry[i][8] - ry[i + 1][8]) / (zb - za) > .35;
                if (window && !pillar) material = CARGLASS;
                if (windshield) material = CARGLASS;
                if (k == 0 || k == 1) { material = PLASTIC; param = 180; }
                if (m == Model.POLICE && k >= 2 && k <= 4 && (za + zb) / 2 > frontAxle + .55 && (za + zb) / 2 < rearAxle - .55) param = 1;
                if (m == Model.TAXI && k == 3) param = 1;
                Gta8MeshBuilder target = material == CARGLASS ? glass : b;
                if (material != CARGLASS) { b.material(material, param); b.color(material == CARPAINT ? 0xFFFFFF : 0x1A1B1D); }
                for (int side = -1; side <= 1; side += 2) {
                    double ax = rx[i][k] * side, ay = ry[i][k], bx = rx[i][k + 1] * side, by = ry[i][k + 1];
                    double cx = rx[i + 1][k + 1] * side, cy = ry[i + 1][k + 1], dx = rx[i + 1][k] * side, dy = ry[i + 1][k];
                    // uv: along the car and up, used for grime, door seams and stripes.
                    if (side > 0) target.quad(ax, ay, za, bx, by, za, cx, cy, zb, dx, dy, zb, za, ay, za, by, zb, cy, zb, dy);
                    else target.quad(ax, ay, za, dx, dy, zb, cx, cy, zb, bx, by, za, za, ay, zb, dy, zb, cy, za, by);
                }
            }
        }
        // Nose and tail caps.
        capEnd(b, rx[0], ry[0], zs[0], true);
        capEnd(b, rx[sections], ry[sections], zs[sections], false);
        // Wheel wells, bumpers, grille, lamps, mirrors, plates.
        b.material(PLASTIC, 220).color(0x0A0A0B);
        for (double axle : new double[]{frontAxle, rearAxle}) for (int side = -1; side <= 1; side += 2) {
            double x = side * (hw - .08);
            for (int s = 0; s < 10; s++) {
                double a0 = Math.PI * s / 10, a1 = Math.PI * (s + 1) / 10, r = wr + .08;
                double z0 = axle - Math.cos(a0) * r, y0 = wr + Math.sin(a0) * r, z1 = axle - Math.cos(a1) * r, y1 = wr + Math.sin(a1) * r;
                // The arch liner must stay under the bonnet and boot lid.
                y0 = Math.min(y0, Math.max(sample(keys, (z0 + L / 2) / L)[0], fenderTop(m, z0)) - .05);
                y1 = Math.min(y1, Math.max(sample(keys, (z1 + L / 2) / L)[0], fenderTop(m, z1)) - .05);
                double inner = x - side * .3;
                b.quad(x, y0, z0, x, y1, z1, inner, y1, z1, inner, y0, z0, 0, 0, 1, 0, 1, 1, 0, 1);
                b.quad(inner, y0, z0, inner, y1, z1, x, y1, z1, x, y0, z0, 0, 0, 1, 0, 1, 1, 0, 1);
            }
        }
        double nose = zs[0], tail = zs[sections];
        double[] front = sample(keys, 0), rear = sample(keys, 1);
        b.material(PLASTIC, 150).color(0x1C1D20);
        b.box(-hw * .92, clearance - .02, nose - .05, hw * .92, clearance + .2, nose + .25);
        b.box(-hw * .9, clearance - .02, tail - .25, hw * .9, clearance + .2, tail + .05);
        b.color(0x0E0F10);
        b.box(-hw * .42, clearance + .2, nose - .02, hw * .42, front[0] - .06, nose + .1, Gta8MeshBuilder.FACE_N);
        b.material(CHROME).color(0xFFFFFF);
        b.box(-hw * .44, front[0] - .07, nose - .03, hw * .44, front[0] - .04, nose + .1, Gta8MeshBuilder.FACE_N | Gta8MeshBuilder.FACE_UP);
        lamp(b, 0, 0xFFF4E0, -hw * .82, front[0] - .16, nose - .005, -hw * .5, front[0] - .04, true);
        lamp(b, 0, 0xFFF4E0, hw * .5, front[0] - .16, nose - .005, hw * .82, front[0] - .04, true);
        lamp(b, 3, 0xFF9A20, -hw * .9, front[0] - .24, nose + .01, -hw * .78, front[0] - .17, true);
        lamp(b, 4, 0xFF9A20, hw * .78, front[0] - .24, nose + .01, hw * .9, front[0] - .17, true);
        lamp(b, 1, 0xFF1A10, -hw * .88, rear[0] - .2, tail + .005, -hw * .55, rear[0] - .05, false);
        lamp(b, 1, 0xFF1A10, hw * .55, rear[0] - .2, tail + .005, hw * .88, rear[0] - .05, false);
        lamp(b, 2, 0xFF2A18, -hw * .52, rear[0] - .16, tail + .005, -hw * .42, rear[0] - .08, false);
        lamp(b, 2, 0xFF2A18, hw * .42, rear[0] - .16, tail + .005, hw * .52, rear[0] - .08, false);
        lamp(b, 5, 0xFFFFFF, -hw * .3, clearance + .06, tail + .06, -hw * .18, clearance + .14, false);
        lamp(b, 3, 0xFF9A20, -hw * .95, rear[0] - .28, tail + .006, -hw * .8, rear[0] - .21, false);
        lamp(b, 4, 0xFF9A20, hw * .8, rear[0] - .28, tail + .006, hw * .95, rear[0] - .21, false);
        b.material(PAINT).color(0xE8E8E0);
        b.box(-.26, clearance + .06, nose - .07, .26, clearance + .18, nose - .05, Gta8MeshBuilder.FACE_N);
        b.box(-.26, clearance + .22, tail + .05, .26, clearance + .34, tail + .07, Gta8MeshBuilder.FACE_S);
        double mirrorZ = zs[(int) (sections * (m == Model.VAN ? .16 : .34))];
        double belt = sample(keys, (mirrorZ + L / 2) / L)[0];
        b.material(CARPAINT, 0).color(0xFFFFFF);
        for (int side = -1; side <= 1; side += 2) b.box(side > 0 ? hw * .98 : -hw * 1.14, belt + .05, mirrorZ - .06, side > 0 ? hw * 1.14 : -hw * .98, belt + .16, mirrorZ + .06);
        // Exhaust and door handles.
        b.material(CHROME).color(0xFFFFFF);
        b.tube(hw * .5, clearance + .05, tail - .1, hw * .5, clearance + .05, tail + .08, .035, .035, 8, true);
        for (int side = -1; side <= 1; side += 2) for (double dz : new double[]{bPillar - .35, bPillar + .55}) b.box(side * (hw * 1.005), belt - .09, dz, side * (hw * 1.02), belt - .06, dz + .14);
        if (m == Model.POLICE) policeBits(b, sample(keys, .5)[1], hw, bPillar, nose);
        if (m == Model.TAXI) {
            double roof = sample(keys, .55)[1];
            b.material(PAINT).color(0xE8E0B0);
            b.box(-.3, roof, bPillar - .1, .3, roof + .08, bPillar + .25);
            b.material(EMISSIVE, VEHICLE).seed(8).color(0xFFE890);
            b.box(-.28, roof + .08, bPillar - .08, .28, roof + .28, bPillar + .23, Gta8MeshBuilder.SIDES | Gta8MeshBuilder.FACE_UP);
            b.seed(0);
        }
        if (m == Model.PICKUP) {
            // Load bed.
            double z0 = zs[(int) (sections * .58)], z1 = tail - .05, y = sample(keys, .8)[0];
            b.material(PLASTIC, 200).color(0x151618);
            b.box(-hw * .92, y - .45, z0, hw * .92, y - .42, z1, Gta8MeshBuilder.FACE_UP);
            b.material(CARPAINT, 0).color(0xFFFFFF);
            b.box(-hw * .98, y - .45, z0, -hw * .88, y + .02, z1, Gta8MeshBuilder.FACE_E);
            b.box(hw * .88, y - .45, z0, hw * .98, y + .02, z1, Gta8MeshBuilder.FACE_W);
            b.box(-hw * .92, y - .45, z0 - .02, hw * .92, y + .02, z0 + .06, Gta8MeshBuilder.FACE_S);
            b.box(-hw * .92, y - .45, z1 - .06, hw * .92, y + .02, z1, Gta8MeshBuilder.FACE_N);
        }
        // Cabin: dashboard, seats and steering wheel seen through the glass.
        double cabinTop = sample(keys, .5)[1];
        if (cabinTop > 0) {
            double beltMid = sample(keys, .5)[0];
            // The dashboard sits just behind the base of the windscreen, below the belt line.
            double screen = -L / 2;
            for (int i = 0; i <= sections; i++) if (roofed[i]) { screen = zs[i]; break; }
            double dashTop = sample(keys, (screen + L / 2) / L)[0] + .02;
            double seatZ = Math.max(bPillar - .3, screen + .75);
            b.material(PLASTIC, 220).color(0x151617);
            b.box(-hw * .9, dashTop - .38, screen + .02, hw * .9, dashTop, screen + .42);
            b.box(-hw * .9, clearance + .1, screen, hw * .9, clearance + .16, bPillar + 1.2, Gta8MeshBuilder.FACE_UP);
            b.color(0x3A3632);
            for (int side = -1; side <= 1; side += 2) {
                b.box(side * hw * .45 - .24, clearance + .18, seatZ, side * hw * .45 + .24, clearance + .42, seatZ + .5);
                b.box(side * hw * .45 - .24, clearance + .42, seatZ + .42, side * hw * .45 + .24, cabinTop - .25, seatZ + .54);
                b.box(side * hw * .45 - .12, cabinTop - .25, seatZ + .44, side * hw * .45 + .12, cabinTop - .1, seatZ + .54);
            }
            if (m != Model.SPORTS && seatZ + 1.4 < lastGlass + .3) b.box(-hw * .85, clearance + .18, seatZ + 1.0, hw * .85, beltMid, seatZ + 1.45);
            b.color(0x111111);
            b.tube(-hw * .45, dashTop - .08, screen + .52, -hw * .45, dashTop + .06, screen + .42, .18, .18, 12, false);
        }
        // Wheels: tyre, rim and hub, each on its own bone centred on the hub.
        for (int wheel = 0; wheel < 4; wheel++) {
            b.bone(wheel + 1);
            double side = wheel % 2 == 0 ? -1 : 1;
            b.push();
            b.rotateY(side > 0 ? 0 : 180);
            b.material(RUBBER).color(0x111111);
            b.tyre(wr, m == Model.SPORTS || m == Model.MUSCLE ? .27 : .22, .1, 18);
            b.material(m == Model.SPORTS || m == Model.MUSCLE ? CHROME : METAL, 60).color(m == Model.POLICE || m == Model.VAN ? 0x2A2C2E : 0xC8CACC);
            b.push(); b.rotateZ(-90); b.translate(0, .1, 0);
            b.cylinder(0, 0, 0, wr * .62, wr * .6, .02, 18, true);
            b.material(PLASTIC, 100).color(0x222426);
            for (int spoke = 0; spoke < 5; spoke++) {
                b.push(); b.rotateY(spoke * 72);
                b.box(-.025, .015, wr * .12, .025, .03, wr * .55);
                b.pop();
            }
            b.material(CHROME).color(0xFFFFFF);
            b.cylinder(0, .02, 0, .05, .04, .03, 10, true);
            b.pop();
            b.pop();
        }
        b.bone(0);
        return new Gta8MeshBuilder[]{b, glass};
    }

    private static void capEnd(Gta8MeshBuilder b, double[] xs, double[] ys, double z, boolean front) {
        b.material(CARPAINT, 0).color(0xFFFFFF);
        for (int k = 0; k < 8; k++) {
            if (k < 2) b.material(PLASTIC, 180).color(0x1A1B1D); else b.material(CARPAINT, 0).color(0xFFFFFF);
            double x0 = xs[k], y0 = ys[k], x1 = xs[k + 1], y1 = ys[k + 1];
            if (front) b.quad(x0, y0, z, -x0, y0, z, -x1, y1, z, x1, y1, z, 0, 0, 1, 0, 1, 1, 0, 1);
            else b.quad(-x0, y0, z, x0, y0, z, x1, y1, z, -x1, y1, z, 0, 0, 1, 0, 1, 1, 0, 1);
        }
    }
    private static void lamp(Gta8MeshBuilder b, int group, int rgb, double x0, double y0, double z, double x1, double y1, boolean front) {
        b.material(EMISSIVE, VEHICLE).seed(group).color(rgb);
        double off = front ? -.012 : .012;
        if (front) b.quad(x1, y0, z + off, x0, y0, z + off, x0, y1, z + off, x1, y1, z + off, 0, 0, 1, 0, 1, 1, 0, 1);
        else b.quad(x0, y0, z + off, x1, y0, z + off, x1, y1, z + off, x0, y1, z + off, 0, 0, 1, 0, 1, 1, 0, 1);
        b.seed(0);
    }
    private static void policeBits(Gta8MeshBuilder b, double roof, double hw, double bPillar, double nose) {
        b.material(METAL, 120).color(0x1A1C1E);
        b.box(-.62, roof, bPillar - .15, .62, roof + .06, bPillar + .15);
        b.material(EMISSIVE, VEHICLE).seed(6).color(0xFF1010);
        b.box(-.6, roof + .06, bPillar - .13, -.05, roof + .17, bPillar + .13);
        b.material(EMISSIVE, VEHICLE).seed(7).color(0x1A4AFF);
        b.box(.05, roof + .06, bPillar - .13, .6, roof + .17, bPillar + .13);
        b.seed(0);
        b.material(METAL, 150).color(0x111213);
        b.box(-hw * .6, .3, nose - .35, hw * .6, .72, nose - .25);
        for (double x : new double[]{-hw * .45, hw * .45}) b.box(x - .04, .3, nose - .35, x + .04, .72, nose - .05);
    }

    // ------------------------------------------------------------------ helicopter and Ferris wheel
    /** Police helicopter; bone 1 is the main rotor, bone 2 the tail rotor. */
    static Gta8MeshBuilder helicopter() {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.bone(0).ao(1);
        b.material(CARPAINT, 0).color(0xFFFFFF);
        b.sphere(0, 1.4, 0, 1.15, 1.05, 2.2, 10, 16);
        b.material(CARGLASS).color(0x101418);
        b.sphere(0, 1.55, -1.2, 1.0, .85, 1.3, 8, 14);
        b.material(CARPAINT, 1).color(0xFFFFFF);
        b.tube(0, 1.6, 1.8, 0, 2.0, 7.2, .38, .16, 10, true);
        b.box(-.05, 2.0, 6.6, .05, 3.1, 7.4);
        b.box(-1.1, 1.8, 6.8, 1.1, 1.9, 7.2);
        b.material(METAL, 120).color(0x2A2C2E);
        for (int side = -1; side <= 1; side += 2) {
            b.box(side * .95 - .04, 0, -1.6, side * .95 + .04, .06, 1.6);
            b.tube(side * .95, .05, -.8, side * .7, .5, -.8, .04, .04, 5, false);
            b.tube(side * .95, .05, .8, side * .7, .5, .8, .04, .04, 5, false);
        }
        b.box(-.2, 2.35, -.3, .2, 2.6, .3);
        b.material(EMISSIVE, BEACON).color(0xFF2010);
        b.box(-.08, 3.05, 7.25, .08, 3.15, 7.35);
        b.material(EMISSIVE, VEHICLE).seed(6).color(0xFF2010); b.box(-.1, .1, -.3, .1, .2, -.1);
        b.material(EMISSIVE, VEHICLE).seed(7).color(0x2A5AFF); b.box(-.1, .1, .1, .1, .2, .3);
        b.seed(0);
        b.bone(1);
        b.material(METAL, 140).color(0x1A1A1C);
        for (int blade = 0; blade < 4; blade++) { b.push(); b.rotateY(blade * 90); b.box(-.12, 2.62, 0, .12, 2.66, 5.2); b.pop(); }
        b.bone(2);
        for (int blade = 0; blade < 2; blade++) { b.push(); b.rotateX(blade * 90); b.box(.08, -.7, -.06, .1, .7, .06); b.pop(); }
        b.bone(0);
        return b;
    }
    /** Ferris wheel in the local XY plane (axle along X after yaw); bone 1 rotates the wheel. */
    static Gta8MeshBuilder ferris() {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.bone(0).ao(1);
        double r = 17.5, hub = 19.5;
        b.material(METAL, 90).color(0xE8E8E2);
        for (int side = -1; side <= 1; side += 2) {
            b.tube(side * 3.2, 0, -9, side * 1.2, hub, 0, .35, .25, 8, false);
            b.tube(side * 3.2, 0, 9, side * 1.2, hub, 0, .35, .25, 8, false);
        }
        b.tube(-1.4, hub, 0, 1.4, hub, 0, .45, .45, 10, true);
        b.bone(1);
        int spokes = 16;
        for (int ring = -1; ring <= 1; ring += 2) {
            double x = ring * .9;
            for (int i = 0; i < spokes; i++) {
                double a0 = i * Math.PI * 2 / spokes, a1 = (i + 1) * Math.PI * 2 / spokes;
                b.material(METAL, 90).color(0xE8E8E2);
                b.tube(x, hub + Math.sin(a0) * r, Math.cos(a0) * r, x, hub + Math.sin(a1) * r, Math.cos(a1) * r, .16, .16, 6, false);
                b.tube(x, hub, 0, x, hub + Math.sin(a0) * r, Math.cos(a0) * r, .07, .07, 5, false);
                b.material(EMISSIVE, NIGHT).color(new int[]{0xFF4080, 0x40C0FF, 0xFFD040, 0x80FF60}[i % 4]);
                for (int k = 0; k < 4; k++) {
                    double a = a0 + (a1 - a0) * k / 4;
                    b.box(x - .12, hub + Math.sin(a) * (r + .2) - .08, Math.cos(a) * (r + .2) - .08, x + .12, hub + Math.sin(a) * (r + .2) + .08, Math.cos(a) * (r + .2) + .08);
                }
            }
        }
        b.bone(0);
        return b;
    }
    /** A gondola hanging from its pivot at the origin. */
    static Gta8MeshBuilder gondola() {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.ao(1).bone(0);
        b.material(METAL, 90).color(0xD8D8D2);
        b.tube(0, 0, 0, 0, -1.2, 0, .05, .05, 5, false);
        b.material(CARPAINT, 0).color(0xFFFFFF);
        b.rounded(-1.1, -2.9, -.9, 1.1, -1.2, .9, .3, 3);
        b.material(CARGLASS).color(0x203040);
        b.box(-1.0, -2.2, -.91, 1.0, -1.4, .91, Gta8MeshBuilder.FACE_N | Gta8MeshBuilder.FACE_S);
        return b;
    }
}
