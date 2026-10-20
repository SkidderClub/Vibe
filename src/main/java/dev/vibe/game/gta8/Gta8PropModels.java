package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8World.Prop;
import java.util.Random;

import static dev.vibe.game.gta8.Gta8Materials.*;

/**
 * Street furniture, vegetation and landmark models in local space (base at the origin, facing -Z).
 * Solid parts go to {@code b}, alpha-tested foliage cards to {@code f}; both share the caller's transform.
 */
final class Gta8PropModels {
    private Gta8PropModels() { }

    static void model(Gta8MeshBuilder b, Gta8MeshBuilder f, Prop p, Gta8Textures.SignAtlas atlas) {
        Random r = new Random(p.seed * 31L + p.type);
        b.seed(p.seed & 255).extra(0).ao(1);
        f.seed(p.seed & 255).extra(0).ao(1);
        switch (p.type) {
            case Prop.STREET_LIGHT: streetLight(b); break;
            case Prop.SIGNAL: signal(b, p, atlas); break;
            case Prop.HYDRANT: hydrant(b, r); break;
            case Prop.TRASH: b.material(METAL, 140).color(0x2F4A3A); b.cylinder(0, 0, 0, .27, .29, .95, 12, true); b.color(0x22342A); b.cylinder(0, .95, 0, .3, .26, .08, 12, true); break;
            case Prop.BENCH: bench(b); break;
            case Prop.BUS_STOP: busStop(b, p, atlas); break;
            case Prop.NEWSBOX: newsbox(b, p.variant); break;
            case Prop.METER: b.material(METAL, 120).color(0x4A4E52); b.cylinder(0, 0, 0, .04, .04, 1.1, 6, false); b.color(0x8A9096); b.rounded(-.08, 1.08, -.07, .08, 1.36, .07, .03, 2); b.material(CARGLASS); b.box(-.05, 1.2, -.072, .05, 1.3, -.07, Gta8MeshBuilder.FACE_N); break;
            case Prop.PALM: palm(b, f, p, r); break;
            case Prop.TREE: tree(b, f, p, r); break;
            case Prop.PINE: pine(b, f, p, r); break;
            case Prop.BUSH: bush(b, f, p, r); break;
            case Prop.MAILBOX: mailbox(b, p.variant); break;
            case Prop.BILLBOARD: billboard(b, p, atlas); break;
            case Prop.BOLLARD:
                if (p.variant == 1) { b.material(METAL, 170).color(0x2A2D30); b.lathe(0, 0, 0, new double[]{.2, .17, .13, .13, .24, .24, 0}, new double[]{0, .05, .15, .5, .56, .66, .7}, 12); }
                else { b.material(METAL, 120).color(0x2C2F33); b.cylinder(0, 0, 0, .1, .1, .9, 10, true); b.material(EMISSIVE, NIGHT).color(0xFFE8A0); b.cylinder(0, .72, 0, .102, .102, .05, 10, false); }
                break;
            case Prop.DUMPSTER: dumpster(b, p.variant); break;
            case Prop.CONTAINER: container(b, p); break;
            case Prop.CRANE: crane(b); break;
            case Prop.TANK: tank(b, p); break;
            case Prop.LIFEGUARD: lifeguard(b, r); break;
            case Prop.UMBRELLA: umbrella(b, p.variant); break;
            case Prop.TOWEL: b.material(CLOTH, 1).color(new int[]{0xD8423A, 0x2E7BC4, 0xF2C230, 0x3AAE72, 0xE070B0, 0xF2F2F2}[p.variant % 6]); b.box(-.45, 0, -.95, .45, .012, .95, Gta8MeshBuilder.FACE_UP); break;
            case Prop.ROCK: rock(b, p, r); break;
            case Prop.VIBE_LETTER: letter(b, (char) p.variant); break;
            case Prop.ANTENNA: antenna(b, p); break;
            case Prop.WATER_TOWER: waterTower(b); break;
            case Prop.AC_UNIT: acUnit(b, p); break;
            case Prop.PUMP: pump(b); break;
            case Prop.FOUNTAIN: fountain(b, p); break;
            case Prop.FENCE: fence(b, f, p); break;
            case Prop.PLANTER: b.material(CONCRETE_WALL).color(0xB8B2A6); b.box(-1.2, 0, -1.2, 1.2, .6, 1.2, Gta8MeshBuilder.SIDES); b.material(DIRT).color(0x4A3A2C); b.box(-1.1, .55, -1.1, 1.1, .58, 1.1, Gta8MeshBuilder.FACE_UP); b.material(CONCRETE_WALL).color(0xC4BEB2); b.box(-1.25, .58, -1.25, 1.25, .64, -1.1); b.box(-1.25, .58, 1.1, 1.25, .64, 1.25); b.box(-1.25, .58, -1.1, -1.1, .64, 1.1); b.box(1.1, .58, -1.1, 1.25, .64, 1.1); break;
            case Prop.BARRIER:
                if (p.variant == 1) {
                    b.material(CONCRETE_WALL).color(0xA8A49C); b.box(-p.sx / 2, 0, -p.sz / 2, p.sx / 2, p.sy, p.sz / 2);
                    b.material(RUBBER).color(0x1A1A1A);
                    for (double x = -p.sx / 2 + 2; x < p.sx / 2 - 1; x += 4) b.box(x - .2, .3, p.sz / 2, x + .2, .9, p.sz / 2 + .12);
                } else { b.material(CONCRETE_WALL).color(0xC8C4BA); b.box(-p.sx / 2, 0, -.3, p.sx / 2, .8, .3); }
                break;
            case Prop.RAILING: railing(b, p); break;
            case Prop.TABLE: table(b, p.variant); break;
            case Prop.LAMP_PARK: parkLamp(b, p.variant); break;
            case Prop.HELIPAD: helipad(b, p); break;
            case Prop.PILING: b.material(WOOD).color(0x4A3C30); b.cylinder(0, -p.sy, 0, .24, .22, p.sy, 8, false); break;
            case Prop.CANOPY: canopy(b, p); break;
            case Prop.SHIP: ship(b, r); break;
            case Prop.COUNTER: counter(b, p.variant); break;
            case Prop.SHELF: shelf(b, p, r); break;
            case Prop.FRIDGE: fridge(b, p); break;
            case Prop.RACK: rack(b, p, r); break;
            case Prop.REGISTER: b.material(PLASTIC | INDOOR, 60).color(0x2A2C30); b.box(-.22, 0, -.2, .22, .12, .2); b.box(-.18, .12, .05, .18, .38, .12); b.material(EMISSIVE | INDOOR, ALWAYS).color(0x40A0E0); b.box(-.15, .16, .04, .15, .34, .05, Gta8MeshBuilder.FACE_N); break;
            case Prop.FLAG: flag(b); break;
            case Prop.CONE: b.material(PLASTIC, 80).color(0xE8601C); b.cylinder(0, 0, 0, .17, .03, .7, 10, false); b.color(0x202020); b.box(-.22, 0, -.22, .22, .03, .22); break;
            default: break;
        }
        b.ao(1).extra(0);
        f.ao(1).extra(0);
    }

    // ------------------------------------------------------------------ street furniture
    private static void streetLight(Gta8MeshBuilder b) {
        b.material(METAL, 110).color(0x505559);
        b.cylinder(0, 0, 0, .19, .16, .55, 10, true);
        b.cylinder(0, .55, 0, .13, .08, 7.65, 10, false);
        b.tube(0, 8.1, 0, 0, 8.45, -.7, .065, .055, 8, false);
        b.tube(0, 8.45, -.7, 0, 8.42, -1.9, .055, .05, 8, false);
        b.push(); b.translate(0, 8.32, -2.05);
        b.color(0x5A6064); b.rounded(-.24, -.08, -.5, .24, .1, .5, .12, 3);
        b.material(LAMP).color(0xFFD9A8); b.box(-.18, -.1, -.42, .18, -.08, .38, Gta8MeshBuilder.FACE_DOWN);
        b.pop();
    }

    private static void signal(Gta8MeshBuilder b, Prop p, Gta8Textures.SignAtlas atlas) {
        boolean ew = p.dir == Gta8World.EAST || p.dir == Gta8World.WEST;
        int axis = ew ? 8 : 0;
        double arm = p.sx;
        b.material(METAL, 110).color(0x3A3E42);
        b.cylinder(0, 0, 0, .22, .18, .5, 10, true);
        b.cylinder(0, .5, 0, .15, .12, 5.9, 10, true);
        b.box(0, 5.85, -.09, arm, 6.08, .09);
        b.tube(0, 4.8, 0, arm * .35, 5.9, 0, .04, .04, 6, false);
        double[] heads = arm > 5 ? new double[]{arm - .9, arm * .45} : new double[]{arm - .9};
        for (double hx : heads) {
            b.material(METAL, 150).color(0x23262A);
            b.box(hx - .2, 4.9, -.16, hx + .2, 5.85, .16);
            b.box(hx - .34, 4.8, .16, hx + .34, 5.95, .19);
            for (int lamp = 0; lamp < 3; lamp++) {
                double y = 5.63 - lamp * .31;
                b.material(METAL, 150).color(0x1A1C1E);
                b.box(hx - .15, y + .1, -.3, hx + .15, y + .13, -.16);
                b.material(EMISSIVE, SIGNAL).extra(lamp + axis).color(lamp == 0 ? 0xFF2A1A : lamp == 1 ? 0xFFB020 : 0x30FF90);
                b.seed(p.signalId);
                b.box(hx - .11, y - .11, -.17, hx + .11, y + .11, -.16, Gta8MeshBuilder.FACE_N);
                b.extra(0);
            }
        }
        // Street name blade facing approaching traffic.
        if (p.text != null) {
            float[] rect = atlas.get(Gta8Textures.SignAtlas.STREET, p.text.toUpperCase(java.util.Locale.ROOT));
            double h = .42, w = Math.min(arm * .45, h * rect[4]);
            b.material(SIGN, 0).color(0xFFFFFF);
            double x0 = 1.0, x1 = x0 + w, y0 = 6.18, y1 = y0 + h;
            b.quad(x1, y0, -.1, x0, y0, -.1, x0, y1, -.1, x1, y1, -.1, rect[2], rect[3], rect[0], rect[3], rect[0], rect[1], rect[2], rect[1]);
            b.quad(x0, y0, .1, x1, y0, .1, x1, y1, .1, x0, y1, .1, rect[0], rect[3], rect[2], rect[3], rect[2], rect[1], rect[0], rect[1]);
            b.material(METAL, 120).color(0x2A5A3A);
            b.box(x0, y0, -.09, x1, y1, .09, Gta8MeshBuilder.FACE_UP | Gta8MeshBuilder.FACE_DOWN | Gta8MeshBuilder.FACE_E | Gta8MeshBuilder.FACE_W);
        }
        // Pedestrian signal facing across the approach road.
        b.material(METAL, 150).color(0x23262A);
        b.box(.16, 2.55, -.17, .5, 3.25, .17);
        b.seed(p.signalId);
        b.material(EMISSIVE, SIGNAL).extra(3 + axis).color(0xE8F2FF);
        b.box(.5, 2.95, -.12, .505, 3.18, .12, Gta8MeshBuilder.FACE_E);
        b.material(EMISSIVE, SIGNAL).extra(4 + axis).color(0xFF7A20);
        b.box(.5, 2.62, -.12, .505, 2.85, .12, Gta8MeshBuilder.FACE_E);
        b.extra(0);
        b.material(METAL, 150).color(0xCFCFC8);
        b.box(-.19, 1.0, -.08, -.15, 1.25, .08);
    }

    private static void hydrant(Gta8MeshBuilder b, Random r) {
        int c = r.nextInt(3) == 0 ? 0xD8B020 : 0xB8322A;
        b.material(METAL, 130).color(c);
        b.lathe(0, 0, 0, new double[]{.16, .16, .12, .115, .115, .1, .07, 0}, new double[]{0, .06, .09, .5, .56, .64, .72, .76}, 12);
        b.tube(-.2, .42, 0, .2, .42, 0, .055, .055, 8, true);
        b.tube(0, .42, 0, 0, .42, -.2, .07, .07, 8, true);
    }
    private static void bench(Gta8MeshBuilder b) {
        b.material(METAL, 120).color(0x1F2224);
        for (double x : new double[]{-.75, .75}) { b.box(x - .04, 0, -.25, x + .04, .45, -.21); b.box(x - .04, 0, .2, x + .04, .9, .24); b.box(x - .04, .4, -.25, x + .04, .45, .24); }
        b.material(WOOD).color(0x8A6A48);
        for (int i = 0; i < 4; i++) b.box(-.9, .45, -.25 + i * .12, .9, .49, -.16 + i * .12);
        for (int i = 0; i < 3; i++) b.box(-.9, .58 + i * .12, .22, .9, .67 + i * .12, .26);
    }
    private static void busStop(Gta8MeshBuilder b, Prop p, Gta8Textures.SignAtlas atlas) {
        b.material(METAL, 100).color(0x3E4448);
        for (double x : new double[]{-2.05, 2.05}) { b.box(x - .05, 0, .6, x + .05, 2.55, .7); b.box(x - .05, 0, -.7, x + .05, 2.55, -.6); }
        b.color(0x4E555A);
        b.box(-2.2, 2.55, -.85, 2.2, 2.68, .85);
        b.material(CARGLASS).color(0x202A30);
        b.box(-2.0, .15, .62, .9, 2.4, .66);
        float[] rect = atlas.get(Gta8Textures.SignAtlas.BILLBOARD, p.text == null ? "VIBE CLIENT" : p.text);
        b.material(SIGN, 1).color(0xFFFFFF);
        b.quad(2.0, .3, .6, .95, .3, .6, .95, 2.35, .6, 2.0, 2.35, .6, rect[2], rect[3], rect[0], rect[3], rect[0], rect[1], rect[2], rect[1]);
        b.material(METAL, 100).color(0x3E4448);
        b.box(.9, .2, .6, 2.05, 2.45, .7, Gta8MeshBuilder.FACE_S | Gta8MeshBuilder.FACE_UP | Gta8MeshBuilder.FACE_W);
        b.push(); b.translate(-.6, 0, .2); bench(b); b.pop();
        b.material(EMISSIVE, NIGHT).color(0xDDEBFF);
        b.box(-1.8, 2.52, -.4, 1.8, 2.55, .4, Gta8MeshBuilder.FACE_DOWN);
    }
    private static void newsbox(Gta8MeshBuilder b, int variant) {
        b.material(METAL, 110).color(new int[]{0x2A5CA8, 0xB8322A, 0xE0B020}[variant % 3]);
        b.box(-.25, .35, -.22, .25, 1.05, .22);
        b.color(0x303030); b.box(-.2, 0, -.18, -.16, .35, .18); b.box(.16, 0, -.18, .2, .35, .18);
        b.material(CARGLASS); b.box(-.18, .7, -.225, .18, .98, -.22, Gta8MeshBuilder.FACE_N);
    }
    private static void mailbox(Gta8MeshBuilder b, int variant) {
        if (variant == 1) {
            b.material(WOOD).color(0x6E5A48); b.box(-.05, 0, -.05, .05, 1.05, .05);
            b.material(METAL, 110).color(0x262626); b.rounded(-.12, 1.05, -.28, .12, 1.28, .28, .1, 3);
            b.color(0xC8322A); b.box(.12, 1.18, .05, .14, 1.36, .09);
            return;
        }
        b.material(METAL, 110).color(0x234E8C);
        b.rounded(-.26, .3, -.24, .26, 1.1, .24, .05, 2);
        b.sphere(0, 1.1, 0, .26, .12, .24, 4, 10);
        b.color(0x1C2A3A);
        for (double x : new double[]{-.22, .22}) for (double z : new double[]{-.2, .2}) b.box(x - .03, 0, z - .03, x + .03, .3, z + .03);
    }
    private static void billboard(Gta8MeshBuilder b, Prop p, Gta8Textures.SignAtlas atlas) {
        if (p.variant == 1) {
            b.material(METAL, 110).color(0x3A3E42); b.box(-.15, 0, -.15, .15, 5.4, .15);
            b.color(0xD8D8D0); b.box(-1.1, 5.4, -.2, 1.1, 7.2, .2);
            float[] rect = atlas.get(Gta8Textures.SignAtlas.SHOP, p.text == null ? "GAS" : p.text);
            b.material(SIGN, 1).color(0xFFFFFF);
            b.quad(1.0, 6.4, -.21, -1.0, 6.4, -.21, -1.0, 7.1, -.21, 1.0, 7.1, -.21, rect[2], rect[3], rect[0], rect[3], rect[0], rect[1], rect[2], rect[1]);
            b.quad(-1.0, 6.4, .21, 1.0, 6.4, .21, 1.0, 7.1, .21, -1.0, 7.1, .21, rect[0], rect[3], rect[2], rect[3], rect[2], rect[1], rect[0], rect[1]);
            b.material(EMISSIVE, NIGHT).color(0xFFE080);
            b.box(-.95, 5.5, -.21, .95, 6.3, -.2, Gta8MeshBuilder.FACE_N);
            return;
        }
        double w = p.sx, h = p.sy;
        b.material(METAL, 110).color(0x4A4E52);
        for (double x : new double[]{-w * .3, w * .3}) b.box(x - .18, 0, -.18, x + .18, 3.2, .18);
        b.box(-w / 2, 3.1, -.1, w / 2, 3.3, 1.1);
        b.color(0x3A3E42);
        b.box(-w / 2 - .1, 3.2, .1, w / 2 + .1, 3.3 + h + .2, .3);
        float[] rect = atlas.get(Gta8Textures.SignAtlas.BILLBOARD, p.text == null ? "VIBE CLIENT" : p.text);
        b.material(SIGN, 1).color(0xFFFFFF);
        double y0 = 3.3, y1 = 3.3 + h;
        b.quad(w / 2, y0, .09, -w / 2, y0, .09, -w / 2, y1, .09, w / 2, y1, .09, rect[2], rect[3], rect[0], rect[3], rect[0], rect[1], rect[2], rect[1]);
        b.material(LAMP).color(0xFFF0D0);
        for (double x = -w * .35; x <= w * .36; x += w * .35) { b.tube(x, 3.3, -.1, x, 3.0, -1.2, .03, .03, 5, false); b.box(x - .25, 2.9, -1.4, x + .25, 3.05, -1.1); }
    }
    private static void dumpster(Gta8MeshBuilder b, int variant) {
        b.material(METAL, 170).color(new int[]{0x2E5A3A, 0x2A4A78, 0x5A5E60}[variant % 3]);
        b.box(-.95, .15, -.55, .95, 1.2, .55);
        b.color(0x1E2A22); b.quad(-.97, 1.2, -.58, .97, 1.2, -.58, .97, 1.32, .58, -.97, 1.32, .58, 0, 0, 1.9, 0, 1.9, 1.1, 0, 1.1);
        b.material(RUBBER).color(0x111111);
        for (double x : new double[]{-.8, .8}) for (double z : new double[]{-.4, .4}) b.box(x - .06, 0, z - .06, x + .06, .15, z + .06);
    }
    private static void container(Gta8MeshBuilder b, Prop p) {
        int c = Gta8CityGen.CONTAINER[p.variant % Gta8CityGen.CONTAINER.length];
        double hx = p.sx / 2, hz = p.sz / 2, h = p.sy;
        b.material(CORRUGATED).color(c);
        b.box(-hx, 0, -hz, hx, h, hz, Gta8MeshBuilder.FACE_E | Gta8MeshBuilder.FACE_W | Gta8MeshBuilder.FACE_UP | Gta8MeshBuilder.FACE_N);
        b.material(METAL, 150).color(c);
        b.box(-hx, 0, hz - .02, hx, h, hz, Gta8MeshBuilder.FACE_S);
        b.color(0x2A2A2A);
        for (double x = -hx + .3; x < hx; x += .6) b.box(x - .03, .1, hz, x + .03, h - .1, hz + .04, Gta8MeshBuilder.FACE_S | Gta8MeshBuilder.FACE_E | Gta8MeshBuilder.FACE_W);
        b.box(-hx - .02, 0, -hz, hx + .02, .12, hz, Gta8MeshBuilder.SIDES);
    }
    private static void crane(Gta8MeshBuilder b) {
        int red = 0xC8452E, white = 0xE8E6E0;
        b.material(METAL, 120).color(red);
        for (double x : new double[]{-8, 8}) for (double z : new double[]{-10, 10}) b.box(x - .6, 0, z - .6, x + .6, 40, z + .6);
        for (double x : new double[]{-8, 8}) { b.box(x - .5, 12, -10, x + .5, 13.2, 10); b.box(x - .5, 38.5, -10, x + .5, 40, 10); }
        b.box(-8, 38.5, -.8, 8, 40.5, .8);
        b.color(white);
        b.box(-2.2, 40, -58, 2.2, 42.5, 16);
        b.box(-4, 42.5, 4, 4, 47, 14);
        b.material(METAL, 120).color(red);
        b.tube(-2, 42.5, 10, -.3, 54, 8, .35, .3, 6, false);
        b.tube(2, 42.5, 10, .3, 54, 8, .35, .3, 6, false);
        b.tube(0, 54, 8, 0, 42.5, -50, .08, .08, 4, false);
        b.tube(0, 54, 8, 0, 42.5, 15, .08, .08, 4, false);
        b.material(CARGLASS).color(0x203040); b.box(-1.4, 38.2, -20, 1.4, 40, -17);
        b.material(EMISSIVE, BEACON).color(0xFF2A10); b.box(-.2, 54, 7.8, .2, 54.4, 8.2);
    }
    private static void tank(Gta8MeshBuilder b, Prop p) {
        double rr = p.sx, h = p.sy;
        b.material(METAL, 90).color(0xE2E2DC);
        b.cylinder(0, 0, 0, rr, rr, h, 24, false);
        b.lathe(0, h, 0, new double[]{rr, rr * .92, rr * .7, rr * .35, 0}, new double[]{0, .5, 1.1, 1.5, 1.65}, 24);
        b.color(0x6A6E72);
        for (double y = 1; y < h; y += 2.2) b.cylinder(0, y, 0, rr + .03, rr + .03, .08, 24, false);
        b.box(rr - .05, 0, -.3, rr + .15, h + .3, -.25);
        b.box(rr - .05, 0, .25, rr + .15, h + .3, .3);
    }
    private static void lifeguard(Gta8MeshBuilder b, Random r) {
        b.material(WOOD).color(0xB8A080);
        for (double x : new double[]{-1.0, 1.0}) for (double z : new double[]{-.8, .8}) b.box(x - .08, 0, z - .08, x + .08, 1.9, z + .08);
        b.material(PAINT).color(new int[]{0x7EC8E0, 0xF2D25A, 0xF09A6A}[r.nextInt(3)]);
        b.box(-1.2, 1.9, -1.0, 1.2, 3.6, 1.0);
        b.material(CARGLASS).color(0x203040);
        b.box(-.9, 2.6, -1.01, .9, 3.3, -1.0, Gta8MeshBuilder.FACE_N);
        b.material(PAINT).color(0xE8E4DA);
        b.quad(-1.4, 3.6, -1.3, 1.4, 3.6, -1.3, 1.4, 4.0, 1.2, -1.4, 4.0, 1.2, 0, 0, 2.8, 0, 2.8, 2.5, 0, 2.5);
        b.material(WOOD).color(0xA89070);
        b.quad(-.5, .05, -3.6, .5, .05, -3.6, .5, 1.9, -1.0, -.5, 1.9, -1.0, 0, 0, 1, 0, 1, 3.1, 0, 3.1);
        b.box(-1.3, 1.85, -1.3, 1.3, 1.95, 1.1);
    }
    private static void umbrella(Gta8MeshBuilder b, int variant) {
        b.material(WOOD).color(0xD8D0C0);
        b.cylinder(0, -.3, 0, .025, .025, 2.6, 6, false);
        b.material(CLOTH, 1).color(new int[]{0xD8423A, 0x2E7BC4, 0xF2C230, 0x3AAE72, 0xE070B0}[variant % 5]);
        b.lathe(0, 1.95, 0, new double[]{1.25, 1.1, .6, .01}, new double[]{0, .1, .35, .5}, 12);
    }
    private static void rock(Gta8MeshBuilder b, Prop p, Random r) {
        b.material(TERRAIN).color(0x8A8278);
        int rings = 6, seg = 9, base = b.vertices;
        double[] phase = {r.nextDouble() * 9, r.nextDouble() * 9, r.nextDouble() * 9};
        for (int j = 0; j <= rings; j++) {
            double ph = -Math.PI / 2 + j * Math.PI / rings;
            for (int i = 0; i <= seg; i++) {
                double a = i * Math.PI * 2 / seg;
                double k = .75 + .3 * Math.sin(a * 2 + phase[0]) * Math.cos(ph * 3 + phase[1]) + .15 * Math.sin(a * 5 + phase[2]);
                if (i == seg) k = .75 + .3 * Math.sin(phase[0]) * Math.cos(ph * 3 + phase[1]) + .15 * Math.sin(phase[2]);
                double x = Math.cos(ph) * Math.cos(a) * p.sx / 2 * k, y = (Math.sin(ph) * .5 + .5) * p.sy * k, z = Math.cos(ph) * Math.sin(a) * p.sz / 2 * k;
                b.vertex(x, y, z, x / p.sx, (y - p.sy * .4) / p.sy, z / p.sz, x, y);
            }
        }
        for (int j = 0; j < rings; j++) for (int i = 0; i < seg; i++) {
            int a = base + j * (seg + 1) + i, c = a + seg + 1;
            b.quadIndices(a + 1, a, c, c + 1);
        }
    }
    private static void letter(Gta8MeshBuilder b, char c) {
        b.material(PAINT).color(0xF0EFE8);
        double t = .5;
        switch (c) {
            case 'V':
                for (int side = -1; side <= 1; side += 2) {
                    b.push(); b.translate(side * 2.55, 8.2, 0); b.rotateZ(-side * 17.4);
                    b.box(-1.3, -8.6, -t, 1.3, 8.6, t);
                    b.pop();
                }
                break;
            case 'I': b.box(-1.8, 0, -t, 1.8, 16, t); break;
            case 'B':
                b.box(-6, 0, -t, -3, 16, t); b.box(-3, 0, -t, 4, 2.6, t); b.box(-3, 6.9, -t, 3.4, 9.1, t); b.box(-3, 13.4, -t, 3.4, 16, t);
                b.box(3.4, 9.1, -t, 5.8, 13.4, t); b.box(4, 2.6, -t, 6.4, 6.9, t); b.box(3.2, 1.2, -t, 5.2, 8, t); b.box(2.8, 8.4, -t, 4.8, 14.6, t);
                break;
            default:
                b.box(-5.5, 0, -t, -2.5, 16, t); b.box(-2.5, 0, -t, 5.5, 2.8, t); b.box(-2.5, 6.8, -t, 4, 9.3, t); b.box(-2.5, 13.2, -t, 5.5, 16, t);
        }
        b.material(METAL, 120).color(0x5A5E62);
        for (double x = -5; x <= 5; x += 5) { b.box(x - .1, 0, t, x + .1, 14, t + .2); b.tube(x, 0, 4, x, 12, t + .1, .08, .08, 5, false); }
        b.material(LAMP).color(0xFFF4E0);
        for (double x = -4; x <= 4; x += 4) b.box(x - .3, 0, -4.4, x + .3, .5, -3.9);
    }
    private static void antenna(Gta8MeshBuilder b, Prop p) {
        b.material(METAL, 120).color(p.variant == 1 ? 0xC8452E : 0x8A8E92);
        if (p.variant == 1) {
            for (int leg = 0; leg < 3; leg++) {
                double a = leg * Math.PI * 2 / 3, x0 = Math.cos(a) * 4, z0 = Math.sin(a) * 4;
                b.tube(x0, 0, z0, x0 * .1, p.sy, z0 * .1, .25, .12, 5, false);
            }
            for (double y = 6; y < p.sy; y += 6) {
                double k = 1 - y / p.sy * .9, rr = 4 * k;
                b.color((int) (y / 6) % 2 == 0 ? 0xE8E6E0 : 0xC8452E);
                for (int leg = 0; leg < 3; leg++) {
                    double a = leg * Math.PI * 2 / 3, a2 = (leg + 1) * Math.PI * 2 / 3;
                    b.tube(Math.cos(a) * rr, y, Math.sin(a) * rr, Math.cos(a2) * rr, y, Math.sin(a2) * rr, .08, .08, 4, false);
                }
            }
        } else {
            b.cylinder(0, 0, 0, .18, .06, p.sy, 8, false);
            for (double y = 2; y < p.sy - 2; y += 4) b.tube(-.8, y, 0, .8, y, 0, .03, .03, 4, false);
        }
        b.material(EMISSIVE, BEACON).color(0xFF2A10);
        b.sphere(0, p.sy + .15, 0, .22, .22, .22, 4, 8);
    }
    private static void waterTower(Gta8MeshBuilder b) {
        b.material(METAL, 140).color(0x3A3632);
        for (double x : new double[]{-1.2, 1.2}) for (double z : new double[]{-1.2, 1.2}) b.box(x - .08, 0, z - .08, x + .08, 3, z + .08);
        b.box(-1.4, 2.9, -1.4, 1.4, 3.05, 1.4);
        b.material(WOOD).color(0x7A5E44);
        b.cylinder(0, 3.05, 0, 1.6, 1.55, 3.2, 16, false);
        b.material(METAL, 140).color(0x2A2826);
        for (double y = 3.4; y < 6.2; y += .7) b.cylinder(0, y, 0, 1.62, 1.62, .06, 16, false);
        b.material(WOOD).color(0x5E4A38);
        b.lathe(0, 6.25, 0, new double[]{1.7, 1.2, .1}, new double[]{0, .5, 1.1}, 16);
    }
    private static void acUnit(Gta8MeshBuilder b, Prop p) {
        b.material(METAL, 90).color(0xB8BCBE);
        b.box(-p.sx / 2, 0, -p.sz / 2, p.sx / 2, p.sy, p.sz / 2);
        b.material(METAL, 200).color(0x2A2C2E);
        double rr = Math.min(p.sx, p.sz) * .32;
        b.cylinder(0, p.sy, 0, rr, rr, .06, 12, true);
        b.color(0x6A6E72);
        for (double x = -p.sx / 2 + .1; x < p.sx / 2; x += .12) b.box(x - .01, .1, -p.sz / 2 - .01, x + .01, p.sy - .1, -p.sz / 2, Gta8MeshBuilder.FACE_N);
    }
    private static void pump(Gta8MeshBuilder b) {
        b.material(PAINT).color(0xE8E8E4);
        b.rounded(-.4, 0, -.25, .4, 1.8, .25, .06, 2);
        b.color(0xC8322A); b.box(-.41, 1.55, -.26, .41, 1.8, .26);
        b.material(EMISSIVE, ALWAYS).color(0x70C0FF); b.box(-.25, 1.15, -.26, .25, 1.4, -.255, Gta8MeshBuilder.FACE_N);
        b.material(RUBBER).color(0x151515); b.tube(.35, 1.2, -.2, .5, .5, -.35, .03, .03, 5, false);
        b.material(CONCRETE_WALL).color(0xB8B4AA); b.box(-.6, 0, -.45, .6, .18, .45);
    }
    private static void fountain(Gta8MeshBuilder b, Prop p) {
        double rr = p.sx / 2;
        b.material(STONE).color(0xD4CCBA);
        b.lathe(0, 0, 0, new double[]{rr, rr, rr - .05, rr - .35, rr - .35}, new double[]{0, .55, .65, .65, .5}, 32);
        b.material(POOL).color(0x5AA8B8);
        b.disc(0, .48, 0, rr - .35, 32, true);
        b.material(STONE).color(0xC8C0AE);
        b.lathe(0, .48, 0, new double[]{.5, .45, .3, .3, 1.4 * (p.variant + 1), 1.3 * (p.variant + 1), .2, .2, .7, .6, .1}, new double[]{0, .3, .5, 1.6, 1.7, 1.9, 2.0, 2.8, 2.9, 3.05, 3.2}, 20);
        b.material(POOL).color(0x5AA8B8);
        b.disc(0, 2.35, 0, 1.25 * (p.variant + 1), 20, true);
    }
    private static void fence(Gta8MeshBuilder b, Gta8MeshBuilder f, Prop p) {
        double len = p.sx / 2, h = p.sy;
        if (p.variant == 2) {
            b.material(METAL, 110).color(0x8A8E90);
            for (double x = -len; x <= len + .01; x += 3) b.cylinder(x, 0, 0, .04, .04, h, 6, true);
            b.tube(-len, h - .05, 0, len, h - .05, 0, .03, .03, 5, false);
            // UVs in metres along the fence so the wire pattern keeps its scale.
            f.material(CHAINLINK).color(0xA0A4A6);
            f.quad(-len, 0, 0, len, 0, 0, len, h, 0, -len, h, 0, 0, 0, len * 2, 0, len * 2, h, 0, h);
            f.quad(len, 0, 0, -len, 0, 0, -len, h, 0, len, h, 0, len * 2, 0, 0, 0, 0, h, len * 2, h);
            return;
        }
        int c = p.variant == 1 ? 0xE2DED4 : 0x8A6E52;
        b.material(WOOD).color(c);
        b.box(-len, 0, -.03, len, h, .03);
        b.color(p.variant == 1 ? 0xD2CEC4 : 0x6E563E);
        for (double x = -len; x <= len + .01; x += 2.4) b.box(x - .05, 0, -.06, x + .05, h + .05, .06);
        b.box(-len, h - .1, -.05, len, h, .05);
    }
    private static void railing(Gta8MeshBuilder b, Prop p) {
        double len = p.sx / 2;
        b.material(METAL, 90).color(0xE0E0DA);
        for (double x = -len; x <= len + .01; x += 2) b.box(x - .03, 0, -.03, x + .03, 1.1, .03);
        b.box(-len, 1.05, -.04, len, 1.1, .04);
        b.box(-len, .55, -.02, len, .58, .02);
    }
    private static void table(Gta8MeshBuilder b, int variant) {
        b.material(PAINT).color(0xE8E8E4);
        b.cylinder(0, 0, 0, .25, .04, .72, 10, false);
        b.cylinder(0, .72, 0, .6, .6, .04, 16, true);
        if (variant == 1) {
            b.material(WOOD).color(0xD8D0C0); b.cylinder(0, .76, 0, .025, .025, 1.6, 6, false);
            b.material(CLOTH, 1).color(0x2E7BC4); b.lathe(0, 2.1, 0, new double[]{1.1, .9, .02}, new double[]{0, .15, .4}, 10);
        }
        b.material(METAL, 120).color(0x3A3E42);
        for (int i = 0; i < 2; i++) { double z = i == 0 ? -.85 : .85; b.box(-.2, .45, z - .2, .2, .5, z + .2); b.box(-.2, .5, z + (z > 0 ? .15 : -.2), .2, .95, z + (z > 0 ? .2 : -.15)); }
    }
    private static void parkLamp(Gta8MeshBuilder b, int variant) {
        b.material(METAL, 110).color(variant == 1 ? 0xE8E8E2 : 0x1E3A2E);
        b.cylinder(0, 0, 0, .12, .07, 3.6, 8, true);
        if (variant == 1) { b.material(LAMP).color(0xFFE0B0); b.sphere(0, 3.95, 0, .28, .28, .28, 6, 10); return; }
        b.box(-.18, 3.6, -.18, .18, 3.65, .18);
        b.material(LAMP).color(0xFFD8A0);
        b.box(-.15, 3.65, -.15, .15, 4.05, .15);
        b.material(METAL, 110).color(0x1E3A2E);
        b.lathe(0, 4.05, 0, new double[]{.25, .15, .02}, new double[]{0, .15, .3}, 8);
    }
    private static void helipad(Gta8MeshBuilder b, Prop p) {
        double rr = p.sx / 2;
        b.material(PAINT).color(0x3A3D40); b.disc(0, .06, 0, rr, 32, true);
        b.color(0xE0C030);
        int ring = 48;
        for (int i = 0; i < ring; i++) {
            double a0 = i * Math.PI * 2 / ring, a1 = (i + 1) * Math.PI * 2 / ring, r0 = rr * .8, r1 = rr * .86;
            b.quad(Math.cos(a0) * r0, .07, Math.sin(a0) * r0, Math.cos(a0) * r1, .07, Math.sin(a0) * r1, Math.cos(a1) * r1, .07, Math.sin(a1) * r1, Math.cos(a1) * r0, .07, Math.sin(a1) * r0, 0, 0, 1, 0, 1, 1, 0, 1);
        }
        b.color(p.variant == 1 ? 0xD8262E : 0xF0F0EA);
        double s = rr * .35;
        b.box(-s, .06, -s, -s * .6, .08, s); b.box(s * .6, .06, -s, s, .08, s); b.box(-s * .6, .06, -s * .15, s * .6, .08, s * .15);
        b.material(EMISSIVE, NIGHT).color(0x40FF70);
        for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; b.box(Math.cos(a) * rr - .1, .05, Math.sin(a) * rr - .1, Math.cos(a) * rr + .1, .2, Math.sin(a) * rr + .1); }
    }
    private static void canopy(Gta8MeshBuilder b, Prop p) {
        if (p.variant == 1) {
            b.material(PAINT).color(0xE4E8EA); b.box(-8, 4.0, -3.5, 8, 4.3, 3.5);
            b.material(METAL, 100).color(0xB0B4B8); b.box(-8, 0, -3.3, -7.6, 4.0, -2.9); b.box(7.6, 0, -3.3, 8, 4.0, -2.9);
            b.material(EMISSIVE, NIGHT).color(0xF0F6FF); b.box(-6, 3.98, -2, 6, 4.0, 2, Gta8MeshBuilder.FACE_DOWN);
            return;
        }
        b.material(PAINT).color(0xECECE8);
        b.box(-17, 4.5, -6, 17, 5.4, 6);
        b.color(0xC8322A); b.box(-17.02, 4.8, -6.02, 17.02, 5.2, 6.02, Gta8MeshBuilder.SIDES);
        b.material(METAL, 90).color(0xD0D2D4);
        for (double x : new double[]{-15, 15}) for (double z : new double[]{-4, 4}) b.box(x - .25, 0, z - .25, x + .25, 4.5, z + .25);
        b.material(EMISSIVE, NIGHT).color(0xF4F8FF);
        for (double x = -14; x <= 14; x += 7) b.box(x - 1.4, 4.48, -3.5, x + 1.4, 4.5, 3.5, Gta8MeshBuilder.FACE_DOWN);
    }
    private static void ship(Gta8MeshBuilder b, Random r) {
        double hw = 15, len = 105;
        double[] px = {-hw, -hw * .8, 0, hw * .8, hw, hw, -hw}, pz = {len - 20, -len + 25, -len, -len + 25, len - 20, len, len};
        // Outline is clockwise seen from above; reverse to the builder's winding.
        double[] rx = new double[px.length], rz = new double[pz.length];
        for (int i = 0; i < px.length; i++) { rx[i] = px[px.length - 1 - i]; rz[i] = pz[pz.length - 1 - i]; }
        b.material(PAINT).color(0x8A2A22); b.prism(rx, rz, rx.length, -9, 1.5, false, false);
        b.color(0x1E2A38); b.prism(rx, rz, rx.length, 1.5, 11, true, false);
        b.material(PAINT).color(0xECEAE4);
        b.box(-13, 11, len - 30, 13, 29, len - 12);
        b.material(CARGLASS).color(0x152030);
        for (double y = 13; y < 28; y += 3) b.box(-13.01, y, len - 30.01, 13.01, y + 1.2, len - 12.01, Gta8MeshBuilder.FACE_N | Gta8MeshBuilder.FACE_E | Gta8MeshBuilder.FACE_W);
        b.material(PAINT).color(0x2A2A2A); b.box(-2, 29, len - 22, 2, 36, len - 17);
        for (double z = -len + 32; z < len - 36; z += 13) for (double x = -12.5; x < 12.5; x += 2.5) {
            int stack = 2 + r.nextInt(4);
            b.material(CORRUGATED).color(Gta8CityGen.CONTAINER[r.nextInt(Gta8CityGen.CONTAINER.length)]);
            b.box(x, 11, z, x + 2.44, 11 + stack * 2.6, z + 12.2, Gta8MeshBuilder.SIDES | Gta8MeshBuilder.FACE_UP);
        }
        b.material(EMISSIVE, NIGHT).color(0xFFFFFF); b.box(-.2, 36, len - 20, .2, 36.4, len - 19.6);
        b.material(EMISSIVE, BEACON).color(0xFF2010); b.box(-.2, 20, -len + 2, .2, 20.4, -len + 2.4);
        b.material(METAL, 120).color(0x8A8E90); b.tube(0, 11, -len + 3, 0, 20, -len + 2.2, .15, .1, 5, false);
    }

    // ------------------------------------------------------------------ interiors
    private static void counter(Gta8MeshBuilder b, int variant) {
        b.material((variant == 1 ? METAL : WOOD) | INDOOR, 120).color(variant == 1 ? 0x2A2D30 : 0x8A6A4A);
        b.box(-2.25, 0, -.4, 2.25, .98, .4);
        b.material((variant == 1 ? CARGLASS : MARBLE) | INDOOR).color(variant == 1 ? 0x303840 : 0xE8E4DC);
        b.box(-2.3, .98, -.45, 2.3, 1.05, .45);
        if (variant == 1) { b.material(EMISSIVE | INDOOR, ALWAYS).color(0xD8E8FF); b.box(-2.1, .9, -.38, 2.1, .95, .38, Gta8MeshBuilder.FACE_UP); }
    }
    private static void shelf(Gta8MeshBuilder b, Prop p, Random r) {
        double len = p.sx / 2;
        b.material(METAL | INDOOR, 120).color(0xD8DADC);
        b.box(-len, 0, -.08, len, 1.7, .08);
        b.box(-len, 0, -.3, len, .12, .3);
        for (int level = 0; level < 4; level++) {
            double y = .15 + level * .4;
            b.material(METAL | INDOOR, 120).color(0xE0E2E4);
            b.box(-len, y, -.3, len, y + .03, .3);
            for (int side = -1; side <= 1; side += 2) for (double x = -len + .1; x < len - .15; x += .16 + r.nextDouble() * .1) {
                double w = .08 + r.nextDouble() * .1, h = .12 + r.nextDouble() * .2;
                b.material((p.variant == 1 ? METAL : PAINT) | INDOOR, 60).color(p.variant == 1 ? 0x303234 : java.awt.Color.HSBtoRGB(r.nextFloat(), .5f + r.nextFloat() * .4f, .5f + r.nextFloat() * .45f));
                b.box(x, y + .03, side * .12 - .07, x + w, y + .03 + h, side * .12 + .07);
            }
        }
    }
    private static void fridge(Gta8MeshBuilder b, Prop p) {
        double len = p.sx / 2;
        b.material(METAL | INDOOR, 100).color(0xE8EAEC);
        b.box(-len, 0, -.35, len, 2.3, .38);
        b.material(EMISSIVE | INDOOR, ALWAYS).color(0x90A8C0);
        b.box(-len + .1, .2, -.36, len - .1, 2.05, -.355, Gta8MeshBuilder.FACE_N);
        b.material(CARGLASS | INDOOR).color(0x405060);
        for (double x = -len; x < len; x += 1.1) b.box(x - .02, .15, -.38, x + .02, 2.1, -.36);
    }
    private static void rack(Gta8MeshBuilder b, Prop p, Random r) {
        double len = p.sx / 2;
        b.material(WOOD | INDOOR).color(0x5A4636);
        b.box(-len, 0, .2, len, 2.3, .38);
        b.material(METAL | INDOOR, 180).color(0x1E2022);
        for (int row = 0; row < 3; row++) for (double x = -len + .4; x < len - .6; x += .9) {
            double y = .6 + row * .55;
            b.box(x, y, .12, x + .7, y + .08, .2);
            b.box(x + .45, y - .12, .12, x + .52, y + .02, .2);
        }
    }
    private static void flag(Gta8MeshBuilder b) {
        b.material(METAL, 60).color(0xD8D8D2);
        b.cylinder(0, 0, 0, .06, .04, 11, 8, true);
        b.material(CLOTH, 1).color(0x2A3E7A);
        b.card(.05, 9.2, 0, 2.6, 9.3, 0, 2.6, 10.8, 0, .05, 10.8, 0);
    }

    // ------------------------------------------------------------------ vegetation
    private static void palm(Gta8MeshBuilder b, Gta8MeshBuilder f, Prop p, Random r) {
        double h = p.sy, lean = .4 + r.nextDouble() * 1.4, dir = r.nextDouble() * Math.PI * 2;
        double lx = Math.cos(dir) * lean, lz = Math.sin(dir) * lean;
        b.material(BARK).color(0x8C7A62);
        int segs = 7;
        double px = 0, py = 0, pz = 0;
        for (int i = 1; i <= segs; i++) {
            double t = i / (double) segs, nx = lx * t * t, ny = h * t, nz = lz * t * t;
            b.tube(px, py, pz, nx, ny, nz, .24 - .09 * (t - 1.0 / segs), .24 - .09 * t, 8, i == segs);
            px = nx; py = ny; pz = nz;
        }
        b.material(PAINT).color(0x5A4A30);
        b.sphere(px, py - .2, pz, .45, .5, .45, 4, 8);
        int fronds = 11 + r.nextInt(4);
        f.material(FOLIAGE, FROND).color(r.nextBoolean() ? 0x6E8A44 : 0x5E7A3C);
        for (int i = 0; i < fronds; i++) {
            double a = i * Math.PI * 2 / fronds + r.nextDouble() * .3;
            double up = Math.toRadians(35 - r.nextDouble() * 25), len = 3.6 + r.nextDouble() * 1.2, width = 1.3;
            double cx = Math.cos(a), cz = Math.sin(a), sx = -cz, sz = cx;
            double x0 = px, y0 = py, z0 = pz;
            int steps = 4;
            for (int s = 0; s < steps; s++) {
                double t0 = s / (double) steps, t1 = (s + 1) / (double) steps;
                double[] p0 = frondPoint(x0, y0, z0, cx, cz, up, len, t0), p1 = frondPoint(x0, y0, z0, cx, cz, up, len, t1);
                double w0 = width * Math.sin(Math.min(1, t0 * 1.3 + .08) * Math.PI) * .5 + .05, w1 = width * Math.sin(Math.min(1, t1 * 1.3 + .08) * Math.PI) * .5 + .05;
                // The texture's rib runs down the card's V axis.
                f.quad(p0[0] - sx * w0, p0[1], p0[2] - sz * w0, p0[0] + sx * w0, p0[1], p0[2] + sz * w0, p1[0] + sx * w1, p1[1], p1[2] + sz * w1, p1[0] - sx * w1, p1[1], p1[2] - sz * w1,
                        0, t0, 1, t0, 1, t1, 0, t1);
            }
        }
    }
    private static double[] frondPoint(double x, double y, double z, double cx, double cz, double up, double len, double t) {
        double d = t * len, drop = t * t * len * .55;
        return new double[]{x + cx * d * Math.cos(up), y + Math.sin(up) * d - drop, z + cz * d * Math.cos(up)};
    }
    private static void tree(Gta8MeshBuilder b, Gta8MeshBuilder f, Prop p, Random r) {
        double h = p.sy;
        b.material(BARK).color(0x5E4E40);
        double tx = (r.nextDouble() - .5) * .6, tz = (r.nextDouble() - .5) * .6;
        b.tube(0, 0, 0, tx, h * .5, tz, .2 + h * .012, .14, 8, false);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + r.nextDouble();
            b.tube(tx, h * .45, tz, tx + Math.cos(a) * h * .22, h * .72, tz + Math.sin(a) * h * .22, .1, .04, 5, false);
        }
        double cy = h * .68, rx = h * .34, ry = h * .3;
        b.material(PAINT).color(0x2C3E22);
        b.sphere(tx, cy, tz, rx * .62, ry * .62, rx * .62, 5, 8);
        int[] greens = {0x6A8A44, 0x5A7A3A, 0x7A9048, 0x4E6E34};
        f.material(FOLIAGE, LEAVES).color(greens[p.variant % greens.length]);
        int clusters = 16;
        for (int i = 0; i < clusters; i++) {
            double u = r.nextDouble() * Math.PI * 2, v = Math.acos(r.nextDouble() * 1.6 - .6);
            double x = tx + Math.cos(u) * Math.sin(v) * rx * .78, y = cy + Math.cos(v) * ry * .78, z = tz + Math.sin(u) * Math.sin(v) * rx * .78;
            double s = (1.6 + r.nextDouble() * 1.1) * h / 8;
            star(f, x, y, z, s, r.nextDouble() * Math.PI);
        }
    }
    private static void star(Gta8MeshBuilder f, double x, double y, double z, double s, double a) {
        for (int k = 0; k < 3; k++) {
            double ang = a + k * Math.PI / 3, c = Math.cos(ang) * s, d = Math.sin(ang) * s;
            double tilt = k == 2 ? s * .6 : 0;
            f.quad(x - c, y - s + tilt, z - d, x + c, y - s - tilt, z + d, x + c, y + s - tilt, z + d, x - c, y + s + tilt, z - d, 0, 1, 1, 1, 1, 0, 0, 0);
        }
        f.quad(x - s, y, z - s, x + s, y, z - s, x + s, y, z + s, x - s, y, z + s, 0, 1, 1, 1, 1, 0, 0, 0);
    }
    private static void pine(Gta8MeshBuilder b, Gta8MeshBuilder f, Prop p, Random r) {
        double h = p.sy;
        b.material(BARK).color(0x4E3E30);
        b.cylinder(0, 0, 0, .2, .05, h, 7, false);
        f.material(FOLIAGE, NEEDLES).color(0x3E5E3A);
        int tiers = 6;
        for (int t = 0; t < tiers; t++) {
            double y = h * (.25 + .7 * t / tiers), rad = h * .3 * (1 - t / (double) tiers) + .5;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + t * .5 + r.nextDouble() * .3, c = Math.cos(a), s = Math.sin(a);
                double tip = y - rad * .45;
                f.quad(-s * .5, y + .6, c * .5, s * .5, y + .6, -c * .5, s * .5 + c * rad, tip, -c * .5 + s * rad, -s * .5 + c * rad, tip, c * .5 + s * rad, 0, 0, 1, 0, 1, 1, 0, 1);
            }
        }
    }
    private static void bush(Gta8MeshBuilder b, Gta8MeshBuilder f, Prop p, Random r) {
        b.material(PAINT).color(0x2E4426);
        b.sphere(0, p.sy * .45, 0, p.sx * .35, p.sy * .38, p.sz * .35, 4, 8);
        f.material(FOLIAGE, SHRUB).color(0x5E7E3E);
        star(f, 0, p.sy * .5, 0, Math.max(p.sx, p.sy) * .55, r.nextDouble() * 3);
    }
}
