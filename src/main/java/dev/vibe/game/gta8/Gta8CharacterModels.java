package dev.vibe.game.gta8;

import static dev.vibe.game.gta8.Gta8Materials.*;

/**
 * Skinned people (13 rigid bones) with procedural animation, clothing styles and weapons.
 * Palette slots: 0 skin, 1 top, 2 trousers, 3 shoes, 4 hair, 5 accent, 6 sleeves, 7 accessory.
 */
final class Gta8CharacterModels {
    static final int PELVIS = 0, CHEST = 1, HEAD = 2, L_ARM = 3, L_FORE = 4, R_ARM = 5, R_FORE = 6, L_THIGH = 7, L_SHIN = 8, L_FOOT = 9, R_THIGH = 10, R_SHIN = 11, R_FOOT = 12;
    static final int BONES = 13;
    static final int[] PARENT = {-1, PELVIS, CHEST, CHEST, L_ARM, CHEST, R_ARM, PELVIS, L_THIGH, L_SHIN, PELVIS, R_THIGH, R_SHIN};

    /** Rest joint positions for a body type (feet at y = 0, facing -Z, right hand at +X). */
    static double[][] joints(int body) {
        double s = body == 1 ? .94 : 1, sh = body == 1 ? .17 : .195, hip = body == 1 ? .1 : .092;
        return new double[][]{
                {0, .95 * s, 0}, {0, 1.03 * s, 0}, {0, 1.5 * s, 0},
                {-sh, 1.43 * s, 0}, {-sh - .02, 1.14 * s, .01}, {sh, 1.43 * s, 0}, {sh + .02, 1.14 * s, .01},
                {-hip, .92 * s, 0}, {-hip - .005, .5 * s, 0}, {-hip - .01, .085 * s, 0},
                {hip, .92 * s, 0}, {hip + .005, .5 * s, 0}, {hip + .01, .085 * s, 0}};
    }
    static double[] wrist(int body, boolean right) { double s = body == 1 ? .94 : 1, sh = body == 1 ? .17 : .195; return new double[]{(right ? 1 : -1) * (sh + .03), .885 * s, .02}; }

    // ------------------------------------------------------------------ meshes
    static Gta8MeshBuilder body(int body, int outfit) {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.ao(1).seed(0).color(0xFFFFFF);
        double[][] j = joints(body);
        double s = body == 1 ? .94 : 1;
        boolean female = body == 1;
        boolean shortSleeves = outfit == 0 || outfit == 3 || outfit == 5 || outfit == 6;
        boolean shorts = outfit == 3 || outfit == 5;
        boolean jacket = outfit == 1 || outfit == 2;
        // Torso: lofted elliptical rings from the hips to the neck.
        double[] ringY = {.86, .95, 1.06, 1.18, 1.3, 1.39, 1.45, 1.485, 1.51};
        double[] ringW = female ? new double[]{.19, .185, .14, .145, .165, .17, .155, .085, .055} : new double[]{.175, .17, .155, .17, .19, .2, .18, .1, .06};
        double[] ringD = female ? new double[]{.12, .12, .1, .11, .13, .11, .09, .068, .05} : new double[]{.115, .115, .11, .12, .13, .12, .1, .075, .055};
        int seg = 14;
        for (int r = 0; r + 1 < ringY.length; r++) {
            int boneA = ringY[r] * s < j[CHEST][1] ? PELVIS : CHEST, boneB = ringY[r + 1] * s < j[CHEST][1] ? PELVIS : CHEST;
            boolean pants = ringY[r + 1] <= .95;
            boolean neck = r >= 7;
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                double[] p0 = ring(ringW[r], ringD[r], ringY[r] * s, a0), p1 = ring(ringW[r], ringD[r], ringY[r] * s, a1);
                double[] q0 = ring(ringW[r + 1], ringD[r + 1], ringY[r + 1] * s, a0), q1 = ring(ringW[r + 1], ringD[r + 1], ringY[r + 1] * s, a1);
                int slot = neck ? (outfit == 1 ? 5 : 0) : pants ? 2 : 1;
                if (female && !pants && !neck && outfit == 5 && ringY[r] < 1.28 && ringY[r] > 1.0) slot = 0;
                b.material(CHARACTER, slot);
                quadSkinned(b, p0, p1, q1, q0, boneA, boneB);
            }
        }
        // Shoulder caps and hip closure.
        b.material(CHARACTER, 1).bone(CHEST);
        b.sphere(j[L_ARM][0] + .025, j[L_ARM][1] - .015, 0, .058, .056, .064, 7, 12);
        b.sphere(j[R_ARM][0] - .025, j[R_ARM][1] - .015, 0, .058, .056, .064, 7, 12);
        b.material(CHARACTER, 2).bone(PELVIS);
        b.sphere(0, .87 * s, 0, .17 * (female ? 1.08 : 1), .07, .115, 4, 10);
        // Head: skull, jaw, nose, ears, eyes, hair.
        b.bone(HEAD);
        double hy = 1.66 * s;
        b.material(CHARACTER, 0);
        b.tube(0, 1.47 * s, 0, 0, 1.58 * s, -.01, .052, .048, 10, false);
        // Skull, jaw and chin; features sit on the skull ellipsoid via skullFront().
        b.sphere(0, hy, .005, .092, .118, .105, 10, 16);
        b.sphere(0, hy - .072, -.012, .066, .052, .072, 7, 12);
        b.sphere(0, hy - .1, -.062, .024, .016, .014, 4, 8);
        b.sphere(0, hy - .004, skullFront(0, -.004) - .002, .009, .022, .01, 4, 6);
        b.sphere(0, hy - .03, skullFront(0, -.03) - .008, .016, .013, .015, 4, 8);
        for (int side = -1; side <= 1; side += 2) {
            b.material(CHARACTER, 0);
            b.sphere(side * .09, hy - .005, .012, .014, .028, .019, 4, 6);
            b.material(CHARACTER, 9);
            b.sphere(side * .033, hy + .016, skullFront(.033, .016) + .003, .014, .0085, .006, 4, 8);
            b.material(CHARACTER, 10);
            b.sphere(side * .033, hy + .016, skullFront(.033, .016) - .0015, .0058, .0062, .003, 4, 8);
            b.material(CHARACTER, 4);
            b.sphere(side * .034, hy + .045, skullFront(.034, .045) + .002, .02, .0055, .006, 3, 8);
        }
        b.material(CHARACTER, 8);
        b.sphere(0, hy - .057, skullFront(0, -.057) + .002, .022, .0068, .006, 4, 8);
        if (outfit == 2) {
            b.material(CHARACTER, 5);
            b.sphere(0, hy + .06, 0, .1, .07, .11, 7, 14);
            b.box(-.1, hy + .045, -.2, .1, hy + .065, -.06);
        } else {
            b.material(CHARACTER, 4);
            // Hair sits back from the forehead so the hairline, brows and eyes stay visible.
            b.sphere(0, hy + .046, .024, .097, .09, .106, 8, 14);
            if (female) { b.rounded(-.098, hy - .19, .01, .098, hy + .06, .128, .04, 2); b.sphere(0, hy - .13, .09, .09, .09, .05, 5, 10); }
        }
        // Arms: sleeves on the upper arm, skin or sleeves on the forearm, hands.
        for (int side = -1; side <= 1; side += 2) {
            int arm = side < 0 ? L_ARM : R_ARM, fore = side < 0 ? L_FORE : R_FORE;
            double[] sj = j[arm], ej = j[fore], wj = wrist(body, side > 0);
            b.bone(arm).material(CHARACTER, 1);
            b.tube(sj[0], sj[1], sj[2], ej[0], ej[1], ej[2], female ? .045 : .052, female ? .038 : .044, 8, false);
            b.bone(fore).material(CHARACTER, shortSleeves ? 0 : 6);
            b.tube(ej[0], ej[1], ej[2], wj[0], wj[1], wj[2], female ? .036 : .042, female ? .028 : .033, 8, true);
            if (jacket) { b.material(CHARACTER, 5); b.tube(wj[0], wj[1] + .02, wj[2], wj[0], wj[1] - .005, wj[2], .036, .034, 8, false); }
            b.material(CHARACTER, 0);
            b.push(); b.translate(wj[0], wj[1] - .06, wj[2]);
            b.rounded(-.022, -.045, -.04, .022, .05, .04, .015, 2);
            b.box(-.018 + side * .01, -.02, -.065, .018 + side * .01, .03, -.03);
            b.pop();
        }
        // Legs: thighs, shins (skin when wearing shorts), shoes.
        for (int side = -1; side <= 1; side += 2) {
            int thigh = side < 0 ? L_THIGH : R_THIGH, shin = side < 0 ? L_SHIN : R_SHIN, foot = side < 0 ? L_FOOT : R_FOOT;
            double[] hj = j[thigh], kj = j[shin], aj = j[foot];
            b.bone(thigh).material(CHARACTER, 2);
            b.tube(hj[0], hj[1] + .02, hj[2], kj[0], kj[1], kj[2], female ? .085 : .082, .058, 9, false);
            b.bone(shin).material(CHARACTER, shorts ? 0 : 2);
            b.tube(kj[0], kj[1] + .02, kj[2], aj[0], aj[1] + .03, aj[2], .056, .04, 9, false);
            b.bone(foot).material(CHARACTER, 3);
            b.push(); b.translate(aj[0], 0, aj[2] - .045);
            b.rounded(-.048, 0, -.12, .048, .085, .1, .04, 3);
            b.pop();
        }
        // Outfit details.
        b.bone(CHEST);
        if (outfit == 1 || outfit == 2) {
            b.material(CHARACTER, 5);
            b.box(-.018, 1.2 * s, -.138 * s, .018, 1.43 * s, -.128 * s);
        }
        if (outfit == 2) {
            b.material(CHARACTER, 6); b.box(.06, 1.33 * s, -.137, .1, 1.37 * s, -.13);
            b.bone(PELVIS).material(CHARACTER, 5);
            b.box(-.19, .9 * s, -.13, .19, .96 * s, .13, Gta8MeshBuilder.SIDES);
            b.box(.17, .8 * s, -.05, .22, .95 * s, .05);
        }
        if (outfit == 4) {
            b.bone(CHEST).material(CHARACTER, 1);
            b.sphere(0, 1.46 * s, .09, .15, .09, .08, 4, 8);
        }
        if (outfit == 6) {
            b.bone(CHEST).material(CHARACTER, 5);
            b.box(-.16, .98 * s, -.135, .16, 1.34 * s, -.125);
        }
        b.bone(0);
        return b;
    }
    /** Z of the front of the skull ellipsoid at head-local (x, y); features are placed on it. */
    private static double skullFront(double x, double y) {
        double k = 1 - (x / .092) * (x / .092) - (y / .118) * (y / .118);
        return .005 - .105 * Math.sqrt(Math.max(0, k));
    }
    private static double[] ring(double w, double d, double y, double a) { return new double[]{Math.cos(a) * w, y, Math.sin(a) * d}; }
    private static void quadSkinned(Gta8MeshBuilder b, double[] p0, double[] p1, double[] q1, double[] q0, int boneA, int boneB) {
        double nx = (p0[0] + p1[0] + q0[0] + q1[0]) / 4, nz = (p0[2] + p1[2] + q0[2] + q1[2]) / 4;
        b.bone(boneA);
        int a = b.vertex(p0[0], p0[1], p0[2], p0[0], 0, p0[2], p0[1] * 7, Math.atan2(p0[2], p0[0]));
        int c = b.vertex(p1[0], p1[1], p1[2], p1[0], 0, p1[2], p1[1] * 7, Math.atan2(p1[2], p1[0]));
        b.bone(boneB);
        int d = b.vertex(q1[0], q1[1], q1[2], q1[0], 0, q1[2], q1[1] * 7, Math.atan2(q1[2], q1[0]));
        int e = b.vertex(q0[0], q0[1], q0[2], q0[0], 0, q0[2], q0[1] * 7, Math.atan2(q0[2], q0[0]));
        if (nx == 0 && nz == 0) return;
        b.quadIndices(c, a, e, d);
    }

    static Gta8MeshBuilder accessory(int kind) {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.ao(1).bone(0);
        double hy = 1.66;
        if (kind == 0) {
            b.material(CHARACTER, 5);
            b.sphere(0, hy + .06, 0, .1, .065, .11, 5, 10);
            b.box(-.08, hy + .04, -.2, .08, hy + .055, -.07);
        } else if (kind == 1) {
            b.material(CHARACTER, 7);
            b.box(-.07, hy + .01, -.112, -.005, hy + .045, -.106);
            b.box(.005, hy + .01, -.112, .07, hy + .045, -.106);
            b.box(-.07, hy + .038, -.112, .07, hy + .045, -.108);
        } else {
            b.material(CHARACTER, 7);
            b.rounded(-.13, 1.1, .1, .13, 1.42, .26, .04, 2);
        }
        return b;
    }

    // ------------------------------------------------------------------ weapons (grip at origin, barrel towards -Z)
    static Gta8MeshBuilder weapon(Gta8Weapon w) {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.ao(1).bone(0).seed(0);
        int dark = 0x1E2022, mid = 0x3A3E42;
        switch (w) {
            case PISTOL:
                b.material(METAL, 90).color(mid); b.box(-.014, .03, -.17, .014, .063, .03);
                b.material(PLASTIC, 120).color(dark); b.box(-.015, 0, -.15, .015, .032, .02);
                b.push(); b.rotateX(-14); b.box(-.015, -.1, -.01, .015, .01, .035); b.pop();
                b.box(-.004, -.012, -.05, .004, .005, -.02);
                break;
            case SMG:
                b.material(METAL, 90).color(mid); b.box(-.02, .02, -.26, .02, .07, .06);
                b.box(-.008, .035, -.34, .008, .05, -.26);
                b.material(PLASTIC, 120).color(dark);
                b.push(); b.rotateX(-10); b.box(-.016, -.1, -.01, .016, .02, .03); b.pop();
                b.box(-.013, -.2, -.11, .013, .02, -.07);
                b.box(-.012, .03, .06, .012, .05, .2);
                break;
            case RIFLE:
                b.material(METAL, 80).color(mid); b.box(-.024, .02, -.3, .024, .085, .1);
                b.cylinder(0, 0, 0, 0, 0, 0, 3, false);
                b.tube(0, .052, -.3, 0, .052, -.68, .012, .011, 8, true);
                b.material(PLASTIC, 140).color(0x2E3024); b.box(-.028, .01, -.5, .028, .08, -.3);
                b.material(PLASTIC, 120).color(dark);
                b.push(); b.rotateX(-12); b.box(-.018, -.11, -.01, .018, .02, .035); b.pop();
                b.push(); b.translate(0, .02, -.14); b.rotateX(12); b.box(-.018, -.2, -.03, .018, .0, .02); b.pop();
                b.box(-.02, .0, .1, .02, .08, .33);
                b.box(-.022, -.05, .28, .022, .09, .35);
                b.material(METAL, 60).color(0x151617); b.box(-.012, .085, -.12, .012, .11, .02);
                break;
            case SHOTGUN:
                b.material(METAL, 80).color(mid); b.box(-.022, .02, -.2, .022, .08, .08);
                b.tube(0, .065, -.2, 0, .065, -.72, .014, .014, 8, true);
                b.tube(0, .035, -.2, 0, .035, -.62, .016, .016, 8, true);
                b.material(WOOD).color(0x6A4A30);
                b.box(-.026, .01, -.46, .026, .05, -.3);
                b.push(); b.rotateX(-8); b.box(-.02, -.09, .06, .02, .06, .38); b.pop();
                break;
            case SNIPER:
                b.material(METAL, 80).color(0x2E3226); b.box(-.024, .02, -.3, .024, .08, .12);
                b.tube(0, .05, -.3, 0, .05, -.95, .014, .012, 8, true);
                b.material(METAL, 60).color(0x151617);
                b.tube(0, .13, -.2, 0, .13, .06, .022, .022, 10, true);
                b.tube(0, .13, -.25, 0, .13, -.2, .03, .022, 10, true);
                b.material(PLASTIC, 120).color(0x2E3226);
                b.push(); b.rotateX(-10); b.box(-.018, -.1, -.01, .018, .02, .035); b.pop();
                b.box(-.024, -.04, .12, .024, .08, .42);
                break;
            default: break;
        }
        return b;
    }

    // ------------------------------------------------------------------ procedural animation
    /** Fills 13 column-major bone matrices (4x4 each) into {@code out}; returns the right-hand weapon matrix in {@code weapon}. */
    static void pose(Gta8Ped p, double time, float[] out, float[] weapon) {
        double[][] j = joints(p.body);
        double[] rot = new double[BONES * 3];
        double speed = p.moveSpeed, phase = p.phase;
        double run = Gta8Math.smooth(2.2, 5.5, speed), walk = Gta8Math.clamp(speed / 1.4, 0, 1);
        double swing = Math.toRadians(22 + 18 * run) * walk, s = Math.sin(phase), c = Math.cos(phase);
        double bob = -(.018 + .035 * run) * s * s * walk;
        double lean = -(4 + 10 * run) * walk;
        double pelvisY = j[PELVIS][1] + bob;
        // Legs.
        double thighL = Math.toDegrees(swing * s), thighR = -thighL;
        double kneeL = -(10 + 70 * run) * Math.pow(Math.max(0, c), 1.4) * walk - 4, kneeR = -(10 + 70 * run) * Math.pow(Math.max(0, -c), 1.4) * walk - 4;
        rot[L_THIGH * 3] = thighL; rot[R_THIGH * 3] = thighR;
        rot[L_SHIN * 3] = kneeL; rot[R_SHIN * 3] = kneeR;
        rot[L_FOOT * 3] = -thighL * .3 - kneeL * .5; rot[R_FOOT * 3] = -thighR * .3 - kneeR * .5;
        // Arms swing against the legs.
        double armSwing = Math.toDegrees(swing) * (.7 + .5 * run);
        rot[L_ARM * 3] = -armSwing * s + 3; rot[R_ARM * 3] = armSwing * s + 3;
        rot[L_ARM * 3 + 2] = -6; rot[R_ARM * 3 + 2] = 6;
        rot[L_FORE * 3] = 12 + 70 * run * walk + Math.max(0, -s) * 15 * walk; rot[R_FORE * 3] = 12 + 70 * run * walk + Math.max(0, s) * 15 * walk;
        rot[CHEST * 3] = lean + (1 - walk) * Math.sin(time * 1.6 + p.id) * 1.2;
        rot[CHEST * 3 + 1] = 7 * s * walk;
        rot[PELVIS * 3 + 1] = -5 * s * walk;
        rot[HEAD * 3] = -lean * .6;
        // Crouch and cower.
        double crouch = Math.max(p.crouch, p.cower);
        if (crouch > 0) {
            pelvisY -= .38 * crouch;
            rot[L_THIGH * 3] += 70 * crouch; rot[R_THIGH * 3] += 70 * crouch;
            rot[L_SHIN * 3] -= 110 * crouch; rot[R_SHIN * 3] -= 110 * crouch;
            rot[L_FOOT * 3] += 40 * crouch; rot[R_FOOT * 3] += 40 * crouch;
            rot[CHEST * 3] -= 22 * crouch;
        }
        if (p.cower > 0) {
            double k = p.cower;
            for (int arm : new int[]{L_ARM, R_ARM}) { rot[arm * 3] = Gta8Math.lerp(rot[arm * 3], 150, k); rot[arm * 3 + 2] = Gta8Math.lerp(rot[arm * 3 + 2], arm == L_ARM ? 25 : -25, k); }
            rot[L_FORE * 3] = Gta8Math.lerp(rot[L_FORE * 3], 130, k); rot[R_FORE * 3] = Gta8Math.lerp(rot[R_FORE * 3], 130, k);
            rot[CHEST * 3] -= 25 * k; rot[HEAD * 3] -= 25 * k;
        }
        // Weapons and aiming.
        double aim = p.aim, look = -p.lookPitch;
        if (p.kind != Gta8Ped.Kind.PLAYER) look = 0;
        boolean armed = p.weapon != Gta8Weapon.FISTS;
        if (armed) {
            // Carry pose: gun held low when not aiming.
            double carry = 1 - aim;
            if (p.weapon.twoHanded()) {
                blend(rot, R_ARM, 38, 10, 12, carry * (1 - walk * .3)); blend(rot, R_FORE, 70, 0, 0, carry);
                blend(rot, L_ARM, 45, -30, -8, carry); blend(rot, L_FORE, 60, 0, 0, carry);
            } else blend(rot, R_ARM, 15, 0, 5, carry * .5);
            if (aim > 0) {
                if (p.weapon.twoHanded()) {
                    blend(rot, R_ARM, 72 + look, 22, 0, aim); blend(rot, R_FORE, 62, 0, 0, aim);
                    blend(rot, L_ARM, 82 + look, -34, 0, aim); blend(rot, L_FORE, 34, 0, 0, aim);
                    rot[CHEST * 3 + 1] = Gta8Math.lerp(rot[CHEST * 3 + 1], -12, aim);
                } else {
                    blend(rot, R_ARM, 88 + look, 8, 0, aim); blend(rot, R_FORE, 6, 0, 0, aim);
                    blend(rot, L_ARM, 80 + look, -30, 0, aim * .9); blend(rot, L_FORE, 20, 0, 0, aim * .9);
                }
                rot[HEAD * 3] = Gta8Math.lerp(rot[HEAD * 3], -look * .5, aim);
                rot[CHEST * 3] = Gta8Math.lerp(rot[CHEST * 3], -look * .25 - 3, aim);
            }
            rot[R_ARM * 3] -= p.recoil * 9;
            if (p.reloadAnim > 0) { blend(rot, L_ARM, 55, 40, 0, p.reloadAnim); blend(rot, L_FORE, 95, 0, 0, p.reloadAnim); }
        } else if (p.punch > 0) {
            double k = Math.sin(p.punch * Math.PI);
            blend(rot, R_ARM, 85, 10, 0, k); blend(rot, R_FORE, 10, 0, 0, k);
            blend(rot, L_ARM, 60, -20, 0, .6); blend(rot, L_FORE, 110, 0, 0, .6);
            rot[CHEST * 3 + 1] = -20 * k;
        }
        if (p.phone > 0) { blend(rot, R_ARM, 25, 10, 30, p.phone); blend(rot, R_FORE, 145, 0, 0, p.phone); rot[HEAD * 3 + 2] = 8 * p.phone; }
        if (p.handsUp > 0) {
            blend(rot, L_ARM, 165, 0, 18, p.handsUp); blend(rot, R_ARM, 165, 0, -18, p.handsUp);
            blend(rot, L_FORE, 35, 0, 0, p.handsUp); blend(rot, R_FORE, 35, 0, 0, p.handsUp);
        }
        if (p.sit > 0) {
            double k = p.sit;
            pelvisY = Gta8Math.lerp(pelvisY, .52, k);
            blend(rot, L_THIGH, 85, 4, 0, k); blend(rot, R_THIGH, 85, -4, 0, k);
            blend(rot, L_SHIN, -80, 0, 0, k); blend(rot, R_SHIN, -80, 0, 0, k);
            blend(rot, L_FOOT, 0, 0, 0, k); blend(rot, R_FOOT, 0, 0, 0, k);
            blend(rot, L_ARM, 58, -18, 0, k * (1 - aim)); blend(rot, R_ARM, 58, 18, 0, k * (1 - aim));
            blend(rot, L_FORE, 35, 0, 0, k * (1 - aim)); blend(rot, R_FORE, 35, 0, 0, k * (1 - aim));
            rot[CHEST * 3] = Gta8Math.lerp(rot[CHEST * 3], 6, k);
            rot[CHEST * 3 + 1] = Gta8Math.lerp(rot[CHEST * 3 + 1], 0, k);
        }
        if (p.flinch > 0) { rot[CHEST * 3] += p.flinch * 30; rot[HEAD * 3] += p.flinch * 25; }
        if (p.jump > 0 && !p.grounded) {
            blend(rot, L_THIGH, 35, 0, 0, .7); blend(rot, R_THIGH, 10, 0, 0, .7);
            blend(rot, L_SHIN, -70, 0, 0, .7); blend(rot, R_SHIN, -40, 0, 0, .7);
            rot[L_ARM * 3 + 2] -= 20; rot[R_ARM * 3 + 2] += 20;
        }
        // Falls: the body pivots onto the ground towards the hit direction.
        double fall = p.fall;
        double rootPitch = 0, rootYaw = 0, rootRoll = 0;
        if (fall > 0) {
            double k = fall * fall * (3 - 2 * fall);
            rootPitch = (p.fallSide >= 0 ? 88 : -88) * k + p.tumbleX;
            rootYaw = p.state == Gta8Ped.State.DEAD || p.state == Gta8Ped.State.RAGDOLL ? Gta8Math.angleDelta(p.yaw, p.fallYaw) * k : 0;
            pelvisY = Gta8Math.lerp(pelvisY, .14, k);
            for (int b = L_ARM; b <= R_FORE; b++) rot[b * 3] = Gta8Math.lerp(rot[b * 3], b % 2 == 1 ? 40 + 25 * Math.sin(p.id) : 20, k);
            blend(rot, L_THIGH, 8, 0, -6, k); blend(rot, R_THIGH, -4, 0, 8, k);
            blend(rot, L_SHIN, -12, 0, 0, k); blend(rot, R_SHIN, -25, 0, 0, k);
            if (p.state == Gta8Ped.State.RAGDOLL && !p.grounded) { rot[L_ARM * 3 + 2] = -60 * Math.sin(time * 9); rot[R_ARM * 3 + 2] = 60 * Math.sin(time * 8); }
        }
        // Forward kinematics.
        float[][] global = new float[BONES][];
        float[] tmp = new float[16], tmp2 = new float[16];
        for (int b = 0; b < BONES; b++) {
            float[] local = new float[16];
            if (b == PELVIS) {
                Gta8Math.translation(local, 0, pelvisY, 0);
                if (fall > 0) {
                    // Turn towards the fall direction, slide the hips away from the feet and tip over.
                    Gta8Math.multiply(local, Gta8Math.rotationY(tmp, -rootYaw), local);
                    Gta8Math.multiply(local, Gta8Math.translation(tmp2, 0, 0, (p.fallSide >= 0 ? -.55 : .55) * fall), local);
                    Gta8Math.multiply(local, Gta8Math.rotationX(tmp, -rootPitch), local);
                }
                euler(local, rot, b, tmp);
            } else {
                double[] jp = j[PARENT[b]], jb = j[b];
                Gta8Math.translation(local, jb[0] - jp[0], jb[1] - jp[1], jb[2] - jp[2]);
                euler(local, rot, b, tmp);
                Gta8Math.multiply(global[PARENT[b]], local, local);
            }
            global[b] = local;
            float[] skin = Gta8Math.multiply(local, Gta8Math.translation(tmp2, -j[b][0], -j[b][1], -j[b][2]), new float[16]);
            System.arraycopy(skin, 0, out, b * 16, 16);
        }
        if (weapon != null) {
            double[] w = wrist(p.body, true), e = j[R_FORE];
            float[] m = Gta8Math.translation(new float[16], w[0] - e[0], w[1] - e[1] - .07, w[2] - e[2] - .02);
            Gta8Math.multiply(m, Gta8Math.rotationX(tmp, -90), m);
            Gta8Math.multiply(global[R_FORE], m, weapon);
        }
    }
    private static void euler(float[] m, double[] rot, int b, float[] tmp) {
        if (rot[b * 3 + 1] != 0) Gta8Math.multiply(m, Gta8Math.rotationY(tmp, rot[b * 3 + 1]), m);
        if (rot[b * 3] != 0) Gta8Math.multiply(m, Gta8Math.rotationX(tmp, rot[b * 3]), m);
        if (rot[b * 3 + 2] != 0) Gta8Math.multiply(m, Gta8Math.rotationZ(tmp, rot[b * 3 + 2]), m);
    }
    private static void blend(double[] rot, int b, double pitch, double yaw, double roll, double k) {
        if (k <= 0) return;
        rot[b * 3] = Gta8Math.lerp(rot[b * 3], pitch, k);
        rot[b * 3 + 1] = Gta8Math.lerp(rot[b * 3 + 1], yaw, k);
        rot[b * 3 + 2] = Gta8Math.lerp(rot[b * 3 + 2], roll, k);
    }
}
