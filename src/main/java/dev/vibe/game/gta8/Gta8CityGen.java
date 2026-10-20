package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8World.Area;
import dev.vibe.game.gta8.Gta8World.Block;
import dev.vibe.game.gta8.Gta8World.Building;
import dev.vibe.game.gta8.Gta8World.District;
import dev.vibe.game.gta8.Gta8World.Light;
import dev.vibe.game.gta8.Gta8World.ParkingSpot;
import dev.vibe.game.gta8.Gta8World.Pickup;
import dev.vibe.game.gta8.Gta8World.Poi;
import dev.vibe.game.gta8.Gta8World.Prop;
import dev.vibe.game.gta8.Gta8World.Volume;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static dev.vibe.game.gta8.Gta8World.CURB;
import static dev.vibe.game.gta8.Gta8World.LINES;
import static dev.vibe.game.gta8.Gta8World.SIDEWALK;
import static dev.vibe.game.gta8.Gta8World.line;

/** Deterministic districts, landmarks and street furniture for {@link Gta8World}. */
final class Gta8CityGen {
    static final int[] PLASTER = {0xE6DCC8, 0xD9C7A7, 0xEDE6DA, 0xC9B79C, 0xD8B4A0, 0xB9C4B8, 0xA9B8C4, 0xE2D2A8};
    static final int[] BRICK = {0x8E4B3A, 0x9C5A42, 0x7A4A3C, 0xA86A4E, 0x6E3F33, 0x8A5A48};
    static final int[] CONCRETE = {0xB5B3AD, 0xA3A29E, 0xC9C5BC, 0x8F918F};
    static final int[] STONE = {0xC8B690, 0xB7A88A, 0xD6CCB5};
    static final int[] GLASS = {0x4E6E85, 0x5C7C74, 0x6E6250, 0x8C979F, 0x3A4552, 0x6B8AA6};
    static final int[] METAL = {0x8C9298, 0x4F6E8A, 0xB8AE98, 0x9A5A3E, 0xD8D4CC, 0x6B7B5E};
    static final int[] PASTEL = {0xF2E8DC, 0xB9E0D2, 0xF2C4CE, 0xF4D3B0, 0xBFD8EA, 0xF0E2A6};
    static final int[] TILE = {0xA0523A, 0x8A4A36, 0x4A4E54, 0x6A4A38, 0xB0664A};
    static final int[] CONTAINER = {0xB03A2E, 0x2E5E8C, 0x2F7A4A, 0xD08A2A, 0x6B6E73, 0x7A3E6A, 0xC9B458, 0x1F3A5A};
    static final String[] SHOPS = {"BURGER BARN", "PIZZA PALACE", "SUNSET DINER", "NIGHT OWL BAR", "CITY PHARMACY", "BLUE WAVE SURF",
            "METRO BANK", "LUCKY LAUNDRY", "TECH HUB", "GREEN LEAF CAFE", "BOOKWORM", "NOODLE HOUSE", "TACO LOCO", "IRON GYM",
            "FLOWER POWER", "PAWN & GOLD", "DONUT KING", "SUSHI GO", "VINYL VAULT", "BARBER CO", "NAIL STUDIO", "PET PALACE",
            "LIQUOR", "DELI 88", "PHONE FIX", "YOGA LOFT", "CAFE ROMA", "RAMEN BAR", "OPTICIAN", "SHOE CITY", "JEWELRY", "BAKERY"};
    static final String[] HOTELS = {"HOTEL VIBE", "OCEAN VIEW", "SEASIDE INN", "THE PALMS", "CORAL HOTEL", "MIAMI BLUE", "SANDS", "BELLA VISTA",
            "SURF MOTEL", "PACIFIC"};
    static final String[] BILLBOARDS = {"VIBE CLIENT", "DRINK FIZZZ", "VIBE RADIO 88.1", "LOS VIBES", "BURGER BARN", "SUNSET DINER"};

    private final Gta8World w;
    private final Random random = new Random(0x10571BE5L);
    private final List<double[]> reserved = new ArrayList<double[]>();
    private final boolean[][] custom = new boolean[LINES - 1][LINES - 1];
    private int shopIndex, hotelIndex, billboardIndex;

    Gta8CityGen(Gta8World w) { this.w = w; }

    void generate() {
        for (int bx = 0; bx < LINES - 1; bx++) for (int bz = 0; bz < LINES - 1; bz++)
            w.blocks[bx][bz] = new Block(bx, bz, classify(bx, bz), w.lot(bx, bz));
        specials();
        for (int bx = 0; bx < LINES - 1; bx++) for (int bz = 0; bz < LINES - 1; bz++) {
            Block b = w.blocks[bx][bz];
            if (custom[bx][bz]) continue;
            switch (b.district) {
                case DOWNTOWN: downtown(b); break;
                case SUBURB: suburb(b); break;
                case INDUSTRIAL: industrial(b); break;
                case COAST: if (bz == LINES - 2) coast(b); else midtown(b, b.x0, b.z0, b.x1, b.z1, .75); break;
                case NORTH: midtown(b, b.x0, b.z0, b.x1, b.z1, .35); break;
                case PARK: park(b, "Park"); break;
                default: midtown(b, b.x0, b.z0, b.x1, b.z1, .8); break;
            }
        }
        streetFurniture();
        signals();
        streetParking();
        beach();
        pier();
        harbor();
        hills();
        pickups();
    }

    private District classify(int bx, int bz) {
        if ((bx == 7 && bz == 6) || (bx == 0 && bz == 3) || (bx == 1 && bz == 8) || (bx == 4 && bz == 4)) return District.PARK;
        if (bx >= 3 && bx <= 6 && bz >= 3 && bz <= 6) return District.DOWNTOWN;
        if (bz >= 8) return District.COAST;
        if (bx >= 8) return District.INDUSTRIAL;
        if (bx <= 1) return District.SUBURB;
        if (bz <= 1) return bx <= 4 ? District.SUBURB : District.NORTH;
        return District.MIDTOWN;
    }

    // ------------------------------------------------------------------ helpers
    private Building building(int kind, double x0, double z0, double x1, double z1) {
        Building b = new Building(kind, x0, z0, x1, z1, random.nextInt(256));
        w.buildings.add(b);
        return b;
    }
    private Volume vol(Building b, double x0, double y0, double z0, double x1, double y1, double z1, int kind, int style, int color, int blank, boolean solid) {
        Volume v = new Volume(x0, y0, z0, x1, y1, z1, kind, style, color);
        v.blank = blank;
        b.volumes.add(v);
        if (solid) w.solid(x0, y0, z0, x1, y1, z1);
        return v;
    }
    private Prop prop(int type, double x, double y, double z, double yaw, double sx, double sy, double sz, int variant) {
        Prop p = new Prop(type, x, y, z, yaw, sx, sy, sz, variant, random.nextInt(1 << 20));
        w.props.add(p);
        return p;
    }
    /** Prop with an axis-aligned collision box of half extents (hx,hz) and height h. */
    private Prop solidProp(int type, double x, double y, double z, double yaw, double sx, double sy, double sz, int variant, double hx, double hz, double h, boolean breakable) {
        Prop p = prop(type, x, y, z, yaw, sx, sy, sz, variant);
        boolean rotated = Math.abs(Math.sin(Math.toRadians(yaw))) > .7;
        double ax = rotated ? hz : hx, az = rotated ? hx : hz;
        p.solids.add(w.solid(x - ax, y, z - az, x + ax, y + h, z + az, p));
        p.breakable = breakable;
        return p;
    }
    private Area area(double x0, double z0, double x1, double z1, int type) {
        Area a = new Area(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), null, type);
        w.areas.add(a);
        return a;
    }
    private void named(double x0, double z0, double x1, double z1, String name) { w.areas.add(0, new Area(x0, z0, x1, z1, name, Area.NAMED)); }
    private void light(double x, double y, double z, double radius, int color, int kind) { w.lights.add(new Light(x, y, z, radius, color, kind)); }
    private boolean free(double x0, double z0, double x1, double z1) {
        for (double[] r : reserved) if (x1 > r[0] && x0 < r[2] && z1 > r[1] && z0 < r[3]) return false;
        return true;
    }
    private void reserve(double x0, double z0, double x1, double z1) { reserved.add(new double[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)}); }
    private double range(double a, double b) { return a + random.nextDouble() * (b - a); }
    private int pick(int[] values) { return values[random.nextInt(values.length)]; }
    private String nextShop() { return SHOPS[(shopIndex++ * 7 + 3) % SHOPS.length]; }
    private static int faceBit(int face) { return 1 << face; }

    /** Maps a street-relative rectangle (u along the street, v inwards) of a parcel into world space. */
    private static double[] local(double px0, double pz0, double px1, double pz1, int face, double u0, double v0, double u1, double v1) {
        double ax, az, bx, bz;
        switch (face) {
            case 0: ax = px0 + u0; bx = px0 + u1; az = pz0 + v0; bz = pz0 + v1; break;
            case 2: ax = px1 - u0; bx = px1 - u1; az = pz1 - v0; bz = pz1 - v1; break;
            case 3: az = pz1 - u0; bz = pz1 - u1; ax = px0 + v0; bx = px0 + v1; break;
            default: az = pz0 + u0; bz = pz0 + u1; ax = px1 - v0; bx = px1 - v1; break;
        }
        return new double[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
    }
    private static double[] localPoint(double px0, double pz0, double px1, double pz1, int face, double u, double v) {
        switch (face) {
            case 0: return new double[]{px0 + u, pz0 + v};
            case 2: return new double[]{px1 - u, pz1 - v};
            case 3: return new double[]{px0 + v, pz1 - u};
            default: return new double[]{px1 - v, pz0 + u};
        }
    }
    /** Yaw (degrees) of something facing out of the given face towards the street. */
    private static double faceYaw(int face) { return face == 0 ? 0 : face == 1 ? 90 : face == 2 ? 180 : 270; }

    // ------------------------------------------------------------------ districts
    private void downtown(Block b) {
        b.ground = Block.PAVED;
        double cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
        double centrality = 1 - Gta8Math.clamp(Math.hypot(cx, cz) / 280, 0, 1);
        int layout = random.nextInt(10);
        double mx = (b.x0 + b.x1) / 2, mz = (b.z0 + b.z1) / 2;
        if (layout < 4) tower(b.x0, b.z0, b.x1, b.z1, centrality, 1.0);
        else if (layout < 6) { tower(b.x0, b.z0, mx, b.z1, centrality, .8); tower(mx, b.z0, b.x1, b.z1, centrality, .8); }
        else if (layout < 8) { tower(b.x0, b.z0, b.x1, mz, centrality, .8); tower(b.x0, mz, b.x1, b.z1, centrality, .8); }
        else { tower(b.x0, b.z0, mx, mz, centrality, .7); tower(mx, b.z0, b.x1, mz, centrality, .7); tower(b.x0, mz, mx, b.z1, centrality, .7); tower(mx, mz, b.x1, b.z1, centrality, .7); }
        plazaDetails(b.x0, b.z0, b.x1, b.z1);
    }

    private void tower(double x0, double z0, double x1, double z1, double centrality, double scale) {
        if (!free(x0, z0, x1, z1)) return;
        double margin = range(3, 7);
        double fx0 = x0 + margin, fz0 = z0 + margin, fx1 = x1 - margin, fz1 = z1 - margin;
        if (fx1 - fx0 < 14 || fz1 - fz0 < 14) return;
        double h = (48 + random.nextDouble() * 60 + centrality * 150 * range(.55, 1)) * scale;
        boolean glass = random.nextDouble() < .62;
        int color = glass ? pick(GLASS) : random.nextBoolean() ? pick(CONCRETE) : pick(STONE);
        int layout = random.nextInt(6);
        int style = glass ? random.nextInt(8) : Volume.facadeStyle(random.nextBoolean() ? 2 : 3, layout, 4 + random.nextInt(2));
        Building bd = building(Building.TOWER, fx0, fz0, fx1, fz1);
        bd.shopHeight = 7.2;
        bd.frontFaces = 15;
        vol(bd, fx0, CURB, fz0, fx1, CURB + 7.2, fz1, Volume.LOBBY, random.nextInt(4), glass ? 0x2E3A44 : color, 0, true);
        int tiers = 1 + (h > 110 ? 1 : 0) + (h > 175 ? 1 : 0);
        double y = CURB + 7.2, ix0 = fx0, iz0 = fz0, ix1 = fx1, iz1 = fz1;
        for (int t = 0; t < tiers; t++) {
            double top = t == tiers - 1 ? h : y + (h - y) * range(.45, .65);
            vol(bd, ix0, y, iz0, ix1, top, iz1, glass ? Volume.GLASS : Volume.FACADE, style, color, 0, true);
            y = top;
            double inset = range(2.5, 5.5);
            if (ix1 - ix0 - inset * 2 < 14 || iz1 - iz0 - inset * 2 < 14) break;
            ix0 += inset; iz0 += inset; ix1 -= inset; iz1 -= inset;
        }
        // Crown: mechanical penthouse, antennas, a helipad and aviation beacons.
        double cx = (ix0 + ix1) / 2, cz = (iz0 + iz1) / 2, pw = (ix1 - ix0) * range(.3, .5), pd = (iz1 - iz0) * range(.3, .5);
        double roof = y;
        int crown = random.nextInt(4);
        if (crown == 0 && ix1 - ix0 > 20 && iz1 - iz0 > 20) {
            prop(Prop.HELIPAD, cx, roof, cz, 0, Math.min(ix1 - ix0, iz1 - iz0) * .8, .4, 0, 0);
        } else {
            vol(bd, cx - pw / 2, roof, cz - pd / 2, cx + pw / 2, roof + range(3.5, 6), cz + pd / 2, Volume.PLAIN, 0, 0x8F918F, 15, false);
            if (crown == 1 || h > 150) prop(Prop.ANTENNA, cx, roof + 5, cz, 0, .4, range(12, 36), .4, 0);
        }
        for (int i = 0; i < 4; i++) {
            double lx = i % 2 == 0 ? ix0 + .5 : ix1 - .5, lz = i / 2 == 0 ? iz0 + .5 : iz1 - .5;
            if (h > 90) light(lx, roof + .6, lz, 0, 0xFF3020, Light.BEACON);
        }
        for (int i = 0, n = 1 + random.nextInt(3); i < n; i++)
            prop(Prop.AC_UNIT, range(ix0 + 2, ix1 - 2), roof, range(iz0 + 2, iz1 - 2), random.nextInt(2) * 90, range(1.6, 3), range(1.2, 2), range(1.2, 2), random.nextInt(2));
    }

    private void plazaDetails(double x0, double z0, double x1, double z1) {
        // Planters with palms and benches around the tower bases.
        for (int i = 0; i < 6; i++) {
            double px = range(x0 + 2, x1 - 2), pz = range(z0 + 2, z1 - 2);
            if (insideBuilding(px, pz, 3)) continue;
            solidProp(Prop.PLANTER, px, CURB, pz, 0, 2.4, .6, 2.4, 0, 1.2, 1.2, .6, false);
            prop(random.nextBoolean() ? Prop.PALM : Prop.TREE, px, CURB + .6, pz, random.nextDouble() * 360, 1, range(7, 11), 1, random.nextInt(3));
        }
        for (int i = 0; i < 4; i++) {
            double px = range(x0 + 2, x1 - 2), pz = range(z0 + 2, z1 - 2);
            if (insideBuilding(px, pz, 2)) continue;
            solidProp(Prop.BENCH, px, CURB, pz, random.nextInt(4) * 90, 1.8, .9, .6, 0, .9, .3, .5, true);
        }
    }
    private boolean insideBuilding(double x, double z, double margin) {
        for (Building b : w.buildings) if (x > b.x0 - margin && x < b.x1 + margin && z > b.z0 - margin && z < b.z1 + margin) return true;
        return false;
    }

    private void midtown(Block b, double x0, double z0, double x1, double z1, double shopChance) {
        b.ground = Block.PAVED;
        double r = Math.hypot((b.x0 + b.x1) / 2, (b.z0 + b.z1) / 2);
        double boost = Gta8Math.clamp((330 - r) / 45, 0, 5);
        double d = Math.min(range(16, 22), (Math.min(x1 - x0, z1 - z0) - 8) / 2);
        row(x0, z0, x1, z0 + d, 0, true, boost, shopChance);
        row(x0, z1 - d, x1, z1, 2, true, boost, shopChance);
        row(x0, z0 + d, x0 + d, z1 - d, 3, false, boost, shopChance * .6);
        row(x1 - d, z0 + d, x1, z1 - d, 1, false, boost, shopChance * .6);
        // Service alley furniture in the courtyard.
        for (int i = 0; i < 3; i++) {
            double px = range(x0 + d + 2, x1 - d - 2), pz = range(z0 + d + 2, z1 - d - 2);
            solidProp(Prop.DUMPSTER, px, CURB, pz, random.nextInt(2) * 90, 1.9, 1.3, 1.1, random.nextInt(3), .95, .55, 1.3, true);
        }
    }

    /** A continuous street frontage, split into individual buildings with occasional alleys. */
    private void row(double x0, double z0, double x1, double z1, int face, boolean corners, double boost, double shopChance) {
        boolean alongX = face == 0 || face == 2;
        double start = alongX ? x0 : z0, end = alongX ? x1 : z1;
        double a = start;
        boolean first = true;
        while (end - a > 6) {
            double len = range(13, 26);
            if (end - a - len < 11) len = end - a;
            boolean last = a + len >= end - .01;
            if (!first && !last && random.nextDouble() < .12) { a += 4; continue; }
            double bx0 = alongX ? a : x0, bx1 = alongX ? a + len : x1, bz0 = alongX ? z0 : a, bz1 = alongX ? z1 : a + len;
            if (free(bx0, bz0, bx1, bz1)) {
                int street = faceBit(face);
                if (corners && first) street |= faceBit(alongX ? 3 : 0);
                if (corners && last) street |= faceBit(alongX ? 1 : 2);
                int sides = alongX ? (faceBit(1) | faceBit(3)) : (faceBit(0) | faceBit(2));
                int blank = sides & ~street;
                midrise(bx0, bz0, bx1, bz1, street, face, blank, boost, shopChance);
            }
            a += len;
            first = false;
        }
    }

    private Building midrise(double x0, double z0, double x1, double z1, int streetFaces, int face, int blank, double boost, double shopChance) {
        int floors = 2 + random.nextInt(5) + (int) Math.round(boost * range(.4, 1.2));
        double pickWall = random.nextDouble();
        int wall = pickWall < .45 ? 0 : pickWall < .8 ? 1 : pickWall < .92 ? 2 : 3;
        int color = wall == 0 ? pick(PLASTER) : wall == 1 ? pick(BRICK) : wall == 2 ? pick(CONCRETE) : pick(STONE);
        int style = Volume.facadeStyle(wall, random.nextInt(6), 1 + random.nextInt(3));
        double fh = Volume.floorHeight(style), shopH = 4.4;
        double top = CURB + shopH + floors * fh;
        Building bd = building(Building.MIDRISE, x0, z0, x1, z1);
        bd.frontFaces = streetFaces;
        bd.shopHeight = shopH;
        if (random.nextDouble() < shopChance) {
            vol(bd, x0, CURB, z0, x1, CURB + shopH, z1, Volume.SHOPFRONT, random.nextInt(8), color, 15 & ~streetFaces, true);
            bd.name = nextShop();
            bd.nameFace = face;
            if (random.nextDouble() < .55) bd.awningColor = pick(new int[]{0x8E2A2A, 0x2A4E6E, 0x2E5E3A, 0xC9A23A, 0x3A3A3A, 0x6E2E5E});
        } else {
            vol(bd, x0, CURB, z0, x1, CURB + shopH, z1, Volume.FACADE, style, color, blank, true);
        }
        vol(bd, x0, CURB + shopH, z0, x1, top, z1, Volume.FACADE, style, color, blank, true);
        bd.roof = Building.ROOF_FLAT;
        double w0 = x1 - x0, d0 = z1 - z0;
        for (int i = 0, n = random.nextInt(3); i < n; i++)
            prop(Prop.AC_UNIT, range(x0 + 1.5, x1 - 1.5), top, range(z0 + 1.5, z1 - 1.5), random.nextInt(2) * 90, range(1.2, 2.2), range(.9, 1.4), range(.9, 1.4), random.nextInt(2));
        if (random.nextDouble() < .14 && w0 > 10 && d0 > 10) prop(Prop.WATER_TOWER, range(x0 + 4, x1 - 4), top, range(z0 + 4, z1 - 4), 0, 3.2, 7, 3.2, 0);
        if (random.nextDouble() < .06 && floors >= 4) billboard(x0, z0, x1, z1, top, face);
        return bd;
    }

    private void billboard(double x0, double z0, double x1, double z1, double top, int face) {
        double[] c = localPoint(x0, z0, x1, z1, face, (face == 0 || face == 2 ? x1 - x0 : z1 - z0) / 2, 3);
        Prop p = prop(Prop.BILLBOARD, c[0], top, c[1], faceYaw(face), 12, 5, .4, 0);
        p.text = BILLBOARDS[billboardIndex++ % BILLBOARDS.length];
        light(c[0], top + 4, c[1], 10, 0xFFE6C0, Light.FLOOD);
    }

    private void suburb(Block b) {
        b.ground = Block.GRASS;
        double pd = Math.min(32, (b.z1 - b.z0) / 2), pdx = Math.min(32, (b.x1 - b.x0) / 2);
        parcels(b.x0, b.z0, b.x1, b.z0 + pd, 0);
        parcels(b.x0, b.z1 - pd, b.x1, b.z1, 2);
        parcels(b.x0, b.z0 + pd, b.x0 + pdx, b.z1 - pd, 3);
        parcels(b.x1 - pdx, b.z0 + pd, b.x1, b.z1 - pd, 1);
    }
    private void parcels(double x0, double z0, double x1, double z1, int face) {
        boolean alongX = face == 0 || face == 2;
        double start = alongX ? x0 : z0, end = alongX ? x1 : z1;
        for (double a = start; end - a > 15; ) {
            double len = range(19, 25);
            if (end - a - len < 15) len = end - a;
            double px0 = alongX ? a : x0, px1 = alongX ? a + len : x1, pz0 = alongX ? z0 : a, pz1 = alongX ? z1 : a + len;
            if (free(px0, pz0, px1, pz1)) house(px0, pz0, px1, pz1, face);
            a += len;
        }
    }

    private void house(double px0, double pz0, double px1, double pz1, int face) {
        boolean alongX = face == 0 || face == 2;
        double frontage = alongX ? px1 - px0 : pz1 - pz0, depth = alongX ? pz1 - pz0 : px1 - px0;
        double hw = Math.min(frontage - 9, range(9.5, 12.5)), hd = Math.min(depth - 12, range(8.5, 11)), sb = range(6, 8);
        boolean garageLeft = random.nextBoolean();
        double gw = 6.0;
        double u0 = garageLeft ? gw + 1.5 : 1.5 + range(0, frontage - hw - gw - 3.5);
        if (u0 + hw > frontage - 1.2) u0 = frontage - 1.2 - hw;
        double gu0 = garageLeft ? 1.2 : u0 + hw;
        int stories = random.nextDouble() < .35 ? 2 : 1;
        double top = CURB + stories * 3.0;
        int color = random.nextDouble() < .7 ? pick(PASTEL) : pick(PLASTER);
        boolean siding = random.nextDouble() < .3;
        double[] r = local(px0, pz0, px1, pz1, face, u0, sb, u0 + hw, sb + hd);
        Building bd = building(Building.HOUSE, r[0], r[1], r[2], r[3]);
        bd.frontFaces = faceBit(face);
        bd.nameFace = face;
        bd.roof = random.nextDouble() < .45 ? Building.ROOF_HIP : alongX ? Building.ROOF_GABLE_X : Building.ROOF_GABLE_Z;
        bd.roofColor = pick(TILE);
        bd.roofPitch = range(22, 32);
        vol(bd, r[0], CURB, r[1], r[2], top, r[3], siding ? Volume.SIDING : Volume.FACADE, siding ? random.nextInt(4) : Volume.facadeStyle(0, 6 + random.nextInt(2), 0), color, 0, true);
        double[] g = local(px0, pz0, px1, pz1, face, gu0, sb + 1, gu0 + gw, sb + 1 + 6.5);
        Building garage = building(Building.HOUSE, g[0], g[1], g[2], g[3]);
        garage.roof = Building.ROOF_FLAT; garage.frontFaces = faceBit(face); garage.nameFace = face; garage.shopHeight = -1;
        vol(garage, g[0], CURB, g[1], g[2], CURB + 2.9, g[3], Volume.PLAIN, 1, color, 15 & ~faceBit(face), true);
        double[] drive = local(px0, pz0, px1, pz1, face, gu0 + .4, -SIDEWALK, gu0 + gw - .4, sb + 1);
        area(drive[0], drive[1], drive[2], drive[3], Area.DRIVEWAY);
        if (random.nextDouble() < .55) {
            double[] spot = localPoint(px0, pz0, px1, pz1, face, gu0 + gw / 2, sb - 2.2);
            w.parking.add(new ParkingSpot(spot[0], spot[1], faceYaw(face) + 180, ParkingSpot.DRIVEWAY));
        }
        double[] path = local(px0, pz0, px1, pz1, face, u0 + hw / 2 - .6, 0, u0 + hw / 2 + .6, sb);
        area(path[0], path[1], path[2], path[3], Area.PATH);
        double[] tree = localPoint(px0, pz0, px1, pz1, face, garageLeft ? frontage - 3.2 : 3.2, sb * .45);
        if (random.nextDouble() < .6) prop(Prop.PALM, tree[0], CURB, tree[1], random.nextDouble() * 360, 1, range(8, 13), 1, random.nextInt(3));
        else prop(Prop.TREE, tree[0], CURB, tree[1], random.nextDouble() * 360, 1, range(5.5, 8), 1, random.nextInt(3));
        for (int i = 0; i < 3; i++) {
            double[] bush = localPoint(px0, pz0, px1, pz1, face, u0 + .8 + i * (hw - 1.6) / 2, sb - .7);
            prop(Prop.BUSH, bush[0], CURB, bush[1], random.nextDouble() * 360, range(.8, 1.3), range(.7, 1.1), range(.8, 1.3), random.nextInt(3));
        }
        double[] box = localPoint(px0, pz0, px1, pz1, face, gu0 - .9 < 0 ? gu0 + gw + .9 : gu0 - .9, .6);
        solidProp(Prop.MAILBOX, box[0], CURB, box[1], faceYaw(face), .25, 1.2, .4, 1, .15, .15, 1.2, true);
        // Backyard: fences, maybe a pool.
        double backStart = sb + hd + 1;
        double[] backL = local(px0, pz0, px1, pz1, face, 0, backStart, .12, depth);
        double[] backR = local(px0, pz0, px1, pz1, face, frontage - .12, backStart, frontage, depth);
        double[] back = local(px0, pz0, px1, pz1, face, 0, depth - .12, frontage, depth);
        fence(backL); fence(backR); fence(back);
        if (depth - backStart > 9 && random.nextDouble() < .4) {
            double[] pool = local(px0, pz0, px1, pz1, face, frontage / 2 - 3.5, backStart + 2, frontage / 2 + 3.5, backStart + 6);
            w.addPool(new Gta8World.Pool(pool[0], pool[1], pool[2], pool[3], CURB - .12));
            area(pool[0] - 1.2, pool[1] - 1.2, pool[2] + 1.2, pool[1], Area.TILE);
            area(pool[0] - 1.2, pool[3], pool[2] + 1.2, pool[3] + 1.2, Area.TILE);
            area(pool[0] - 1.2, pool[1], pool[0], pool[3], Area.TILE);
            area(pool[2], pool[1], pool[2] + 1.2, pool[3], Area.TILE);
        }
    }
    private void fence(double[] r) {
        Prop p = prop(Prop.FENCE, (r[0] + r[2]) / 2, CURB, (r[1] + r[3]) / 2, r[2] - r[0] > r[3] - r[1] ? 0 : 90, Math.max(r[2] - r[0], r[3] - r[1]), 1.8, .1, random.nextInt(2));
        p.solids.add(w.solid(r[0], CURB, r[1], r[2], CURB + 1.8, r[3], p));
    }

    private void industrial(Block b) {
        b.ground = Block.ASPHALT;
        double m = range(8, 12);
        boolean split = random.nextBoolean();
        double x0 = b.x0 + m, z0 = b.z0 + m, x1 = b.x1 - m, z1 = b.z1 - m;
        if (split) {
            double mid = (x0 + x1) / 2;
            warehouse(x0, z0, mid - 5, z0 + (z1 - z0) * range(.5, .7));
            warehouse(mid + 5, z0 + (z1 - z0) * range(.3, .45), x1, z1);
            containers(x0, z0 + (z1 - z0) * .75, mid - 6, z1, true);
        } else {
            double z = z0 + (z1 - z0) * range(.45, .6);
            warehouse(x0, z0, x1, z);
            containers(x0 + 2, z + 8, x1 - 2, z1, false);
        }
        if (random.nextDouble() < .35) for (int i = 0; i < 2; i++) {
            double tx = b.x1 - 6 - i * 9, tz = b.z1 - 7;
            if (free(tx - 4, tz - 4, tx + 4, tz + 4) && !insideBuilding(tx, tz, 4)) solidProp(Prop.TANK, tx, CURB, tz, 0, 3.8, range(8, 12), 3.8, 0, 3.8, 3.8, 11, false);
        }
        // Perimeter fence with gates on each street.
        double fx0 = b.x0 + .6, fz0 = b.z0 + .6, fx1 = b.x1 - .6, fz1 = b.z1 - .6, cx = (fx0 + fx1) / 2, cz = (fz0 + fz1) / 2;
        for (double[] seg : new double[][]{{fx0, fz0, cx - 5, fz0}, {cx + 5, fz0, fx1, fz0}, {fx0, fz1, cx - 5, fz1}, {cx + 5, fz1, fx1, fz1},
                {fx0, fz0, fx0, cz - 5}, {fx0, cz + 5, fx0, fz1}, {fx1, fz0, fx1, cz - 5}, {fx1, cz + 5, fx1, fz1}}) {
            double[] r = {Math.min(seg[0], seg[2]) - .05, Math.min(seg[1], seg[3]) - .05, Math.max(seg[0], seg[2]) + .05, Math.max(seg[1], seg[3]) + .05};
            if (!free(r[0], r[1], r[2], r[3])) continue;
            Prop p = prop(Prop.FENCE, (r[0] + r[2]) / 2, CURB, (r[1] + r[3]) / 2, r[2] - r[0] > r[3] - r[1] ? 0 : 90, Math.max(r[2] - r[0], r[3] - r[1]), 2.2, .1, 2);
            p.solids.add(w.solid(r[0], CURB, r[1], r[2], CURB + 2.2, r[3], p));
        }
        for (int i = 0; i < 3; i++) {
            double px = range(b.x0 + 4, b.x1 - 4), pz = range(b.z0 + 4, b.z1 - 4);
            if (!insideBuilding(px, pz, 4) && free(px - 4, pz - 4, px + 4, pz + 4) && !w.blocked(px, CURB, pz, 3, 2)) w.parking.add(new ParkingSpot(px, pz, random.nextInt(4) * 90, ParkingSpot.TRUCK));
        }
    }
    private void warehouse(double x0, double z0, double x1, double z1) {
        if (x1 - x0 < 12 || z1 - z0 < 12 || !free(x0, z0, x1, z1)) return;
        Building bd = building(Building.WAREHOUSE, x0, z0, x1, z1);
        double h = range(8.5, 13.5);
        bd.roof = random.nextDouble() < .35 ? Building.ROOF_SAWTOOTH : Building.ROOF_FLAT;
        bd.frontFaces = 15;
        bd.roofColor = 0x7E8387;
        bd.name = random.nextDouble() < .5 ? new String[]{"PACIFIC FREIGHT", "BAY LOGISTICS", "VIBE STORAGE", "ATLAS STEEL", "COASTAL FOODS", "ROCKFORD SUPPLY"}[random.nextInt(6)] : null;
        bd.nameFace = random.nextInt(4);
        vol(bd, x0, CURB, z0, x1, CURB + h, z1, Volume.CORRUGATED, random.nextInt(4), pick(METAL), 0, true);
        // Loading dock along the longest free side.
        boolean alongX = x1 - x0 > z1 - z0;
        double[] dock = alongX ? new double[]{x0 + 2, z1, x1 - 2, z1 + 3} : new double[]{x1, z0 + 2, x1 + 3, z1 - 2};
        Prop p = prop(Prop.BARRIER, (dock[0] + dock[2]) / 2, CURB, (dock[1] + dock[3]) / 2, alongX ? 0 : 90, Math.max(dock[2] - dock[0], dock[3] - dock[1]), 1.1, 3, 1);
        p.solids.add(w.solid(dock[0], CURB, dock[1], dock[2], CURB + 1.1, dock[3], p));
        light(alongX ? (x0 + x1) / 2 : x1 + .3, CURB + 6, alongX ? z1 + .3 : (z0 + z1) / 2, 14, 0xFFD9A0, Light.FLOOD);
    }
    private void containers(double x0, double z0, double x1, double z1, boolean alongX) {
        double len = 12.2, wid = 2.5, gap = .4;
        for (double a = alongX ? z0 : x0; a + wid < (alongX ? z1 : x1); a += wid + gap) {
            for (double c = alongX ? x0 : z0; c + len < (alongX ? x1 : z1); c += len + 1.5) {
                if (random.nextDouble() < .25) continue;
                int stack = 1 + random.nextInt(3);
                double cx = alongX ? c + len / 2 : a + wid / 2, cz = alongX ? a + wid / 2 : c + len / 2;
                if (insideBuilding(cx, cz, 2) || !free(cx - 7, cz - 7, cx + 7, cz + 7)) continue;
                for (int s = 0; s < stack; s++) {
                    Prop p = prop(Prop.CONTAINER, cx, CURB + s * 2.6, cz, alongX ? 90 : 0, wid, 2.6, len, random.nextInt(CONTAINER.length));
                    if (s == 0) p.solids.add(w.solid(cx - (alongX ? len : wid) / 2, CURB, cz - (alongX ? wid : len) / 2, cx + (alongX ? len : wid) / 2, CURB + stack * 2.6, cz + (alongX ? wid : len) / 2, p));
                }
            }
        }
    }

    private void coast(Block b) {
        b.ground = Block.PAVED;
        double d = Math.min(20, (b.z1 - b.z0) / 2 - 6);
        row(b.x0, b.z0, b.x1, b.z0 + d, 0, true, 0, .85);
        double z = b.z1 - range(20, 24);
        for (double a = b.x0; b.x1 - a > 14; ) {
            double len = Math.min(b.x1 - a, range(22, 32));
            if (b.x1 - a - len < 14) len = b.x1 - a;
            if (free(a, z, a + len, b.z1)) hotel(a + 1, z, a + len - 1, b.z1 - 1);
            a += len;
        }
        for (int i = 0; i < 5; i++) {
            double px = range(b.x0 + 3, b.x1 - 3), pz = range(b.z0 + d + 3, z - 3);
            if (!insideBuilding(px, pz, 2)) prop(Prop.PALM, px, CURB, pz, random.nextDouble() * 360, 1, range(9, 14), 1, random.nextInt(3));
        }
    }
    private void hotel(double x0, double z0, double x1, double z1) {
        int floors = 5 + random.nextInt(10);
        int style = Volume.facadeStyle(random.nextBoolean() ? 0 : 2, 5, 1);
        int color = pick(PASTEL);
        double top = CURB + 4.6 + floors * Volume.floorHeight(style);
        Building bd = building(Building.HOTEL, x0, z0, x1, z1);
        bd.frontFaces = faceBit(2); bd.shopHeight = 4.6;
        bd.name = HOTELS[hotelIndex++ % HOTELS.length]; bd.nameFace = 2;
        vol(bd, x0, CURB, z0, x1, CURB + 4.6, z1, Volume.SHOPFRONT, 8, color, 15 & ~faceBit(2), true);
        vol(bd, x0, CURB + 4.6, z0, x1, top, z1, Volume.FACADE, style, color, faceBit(1) | faceBit(3), true);
        light((x0 + x1) / 2, CURB + 5, z1 + 1, 9, random.nextBoolean() ? 0xFF4FB0 : 0x40E0FF, Light.NEON);
    }

    private void park(Block b, String name) {
        b.ground = Block.GRASS;
        double cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
        boolean plaza = b.bx == 4 && b.bz == 4;
        String title = plaza ? "Vibe Plaza" : b.bx == 7 ? "Grove Park" : b.bx == 0 ? "Lakeside Park" : "Palm Park";
        named(b.x0, b.z0, b.x1, b.z1, title);
        if (plaza) {
            b.ground = Block.PAVED;
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                area(cx + Math.cos(a) * 20 - 7, cz + Math.sin(a) * 20 - 7, cx + Math.cos(a) * 20 + 7, cz + Math.sin(a) * 20 + 7, Area.PARK);
            }
        } else {
            area(cx - 1.6, b.z0, cx + 1.6, b.z1, Area.PATH);
            area(b.x0, cz - 1.6, b.x1, cz + 1.6, Area.PATH);
            area(b.x0 + 3, b.z0 + 3, b.x1 - 3, b.z0 + 5.4, Area.PATH);
            area(b.x0 + 3, b.z1 - 5.4, b.x1 - 3, b.z1 - 3, Area.PATH);
        }
        solidProp(Prop.FOUNTAIN, cx, CURB, cz, 0, plaza ? 9 : 5, 3, plaza ? 9 : 5, plaza ? 1 : 0, plaza ? 4.5 : 2.5, plaza ? 4.5 : 2.5, .7, false);
        light(cx, CURB + 1.2, cz, 8, 0xB0E0FF, Light.WARM);
        for (int i = 0; i < (plaza ? 16 : 30); i++) {
            double px = range(b.x0 + 3, b.x1 - 3), pz = range(b.z0 + 3, b.z1 - 3);
            if (Math.abs(px - cx) < 4 || Math.abs(pz - cz) < 4 || Math.hypot(px - cx, pz - cz) < (plaza ? 12 : 8)) continue;
            int type = plaza || b.bz >= 8 ? Prop.PALM : random.nextDouble() < .8 ? Prop.TREE : Prop.PINE;
            prop(type, px, CURB, pz, random.nextDouble() * 360, 1, type == Prop.PALM ? range(8, 13) : range(6, 10), 1, random.nextInt(3));
            w.solid(px - .25, CURB, pz - .25, px + .25, CURB + 3, pz + .25);
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            double bx = cx + Math.cos(a) * (plaza ? 13 : 9), bz = cz + Math.sin(a) * (plaza ? 13 : 9);
            solidProp(Prop.BENCH, bx, CURB, bz, Math.toDegrees(Math.atan2(cx - bx, -(cz - bz))) + 180, 1.8, .9, .6, 0, .7, .7, .5, true);
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + .3;
            double lx = cx + Math.cos(a) * (plaza ? 24 : 18), lz = cz + Math.sin(a) * (plaza ? 24 : 18);
            solidProp(Prop.LAMP_PARK, lx, CURB, lz, 0, .3, 4.2, .3, 0, .12, .12, 4.2, true);
            light(lx, CURB + 4, lz, 9, 0xFFD8A0, Light.WARM);
        }
    }

    // ------------------------------------------------------------------ special landmarks
    private void specials() {
        hospital(w.blocks[6][2]);
        police(w.blocks[3][6]);
        int[][] stores = {{1, 5}, {7, 4}, {5, 8}, {3, 1}};
        for (int[] s : stores) storeCorner(w.blocks[s[0]][s[1]], Poi.STORE, "VIBE MART 24/7");
        storeCorner(w.blocks[5][7], Poi.GUN_SHOP, "AMMO & ARMS");
        gasStation(w.blocks[1][7]);
        gasStation(w.blocks[8][5]);
        spray(w.blocks[8][2]);
        safehouse(w.blocks[2][4]);
    }

    private void hospital(Block b) {
        custom[b.bx][b.bz] = true;
        b.ground = Block.PAVED;
        named(b.x0, b.z0, b.x1, b.z1, "Mount Vibe Medical");
        double x0 = b.x0 + 8, x1 = b.x1 - 8, z0 = b.z0 + 6, z1 = b.z0 + 36;
        Building bd = building(Building.CIVIC, x0, z0, x1, z1);
        int style = Volume.facadeStyle(2, 3, 2);
        bd.frontFaces = 15; bd.shopHeight = 5; bd.name = "MEDICAL CENTER"; bd.nameFace = 0;
        vol(bd, x0, CURB, z0, x1, CURB + 5, z1, Volume.LOBBY, 1, 0xDCE3E6, 0, true);
        vol(bd, x0, CURB + 5, z0, x1, CURB + 27, z1, Volume.FACADE, style, 0xE8ECEC, 0, true);
        Building wing = building(Building.CIVIC, x0 + 10, z1, x0 + 34, b.z1 - 8);
        vol(wing, x0 + 10, CURB, z1, x0 + 34, CURB + 18, b.z1 - 8, Volume.FACADE, style, 0xDCE2E2, 0, true);
        prop(Prop.HELIPAD, (x0 + x1) / 2, CURB + 27, (z0 + z1) / 2, 0, 18, .4, 0, 1);
        Prop c = prop(Prop.CANOPY, (x0 + x1) / 2, CURB, z0 - 4, 0, 16, 4.2, 7, 1);
        c.solids.add(w.solid((x0 + x1) / 2 - 8, CURB, z0 - 7.5, (x0 + x1) / 2 - 7.6, CURB + 4.2, z0 - 7.1, c));
        c.solids.add(w.solid((x0 + x1) / 2 + 7.6, CURB, z0 - 7.5, (x0 + x1) / 2 + 8, CURB + 4.2, z0 - 7.1, c));
        Poi poi = new Poi(Poi.HOSPITAL, (x0 + x1) / 2, z0 - 3, 180, "Mount Vibe Medical");
        w.pois.add(poi);
        area(b.x1 - 30, z1 + 2, b.x1 - 2, b.z1 - 2, Area.PARKING);
        for (int i = 0; i < 5; i++) w.parking.add(new ParkingSpot(b.x1 - 27 + i * 5.5, b.z1 - 8, 180, ParkingSpot.LOT));
        light((x0 + x1) / 2, CURB + 4, z0 - 4, 14, 0xE0F0FF, Light.FLOOD);
        w.pickups.add(new Pickup(Pickup.HEALTH, 100, (x0 + x1) / 2 + 6, CURB, z0 - 6));
    }

    private void police(Block b) {
        custom[b.bx][b.bz] = true;
        b.ground = Block.PAVED;
        named(b.x0, b.z0, b.x1, b.z1, "LVPD Central");
        double x0 = b.x0 + 6, x1 = b.x1 - 6, z0 = b.z0 + 6, z1 = b.z0 + 32;
        Building bd = building(Building.CIVIC, x0, z0, x1, z1);
        bd.frontFaces = 15; bd.shopHeight = 4.6; bd.name = "LOS VIBES POLICE"; bd.nameFace = 0;
        int style = Volume.facadeStyle(3, 2, 3);
        vol(bd, x0, CURB, z0, x1, CURB + 4.6, z1, Volume.LOBBY, 2, 0x9FA8AE, 0, true);
        vol(bd, x0, CURB + 4.6, z0, x1, CURB + 17, z1, Volume.FACADE, style, 0xC4BBA8, 0, true);
        prop(Prop.FLAG, x0 - 3, CURB, z0 - 3, 0, .2, 11, .2, 0);
        w.pois.add(new Poi(Poi.POLICE, (x0 + x1) / 2, z0 - 3, 180, "LVPD Central"));
        area(b.x0 + 4, z1 + 4, b.x1 - 4, b.z1 - 4, Area.PARKING);
        for (int i = 0; i < 8; i++) w.parking.add(new ParkingSpot(b.x0 + 10 + i * 6, b.z1 - 10, 0, ParkingSpot.POLICE));
        for (int i = 0; i < 4; i++) light(b.x0 + 12 + i * 16, CURB + 7, (z1 + b.z1) / 2, 16, 0xE8F0FF, Light.FLOOD);
        w.pickups.add(new Pickup(Pickup.ARMOR, 50, x1 + 2, CURB, z1 + 6));
        for (int i = 0; i < 6; i++) solidProp(Prop.BOLLARD, x0 + 4 + i * (x1 - x0 - 8) / 5, CURB, z0 - 6.5, 0, .3, .9, .3, 0, .15, .15, .9, false);
    }

    /** A standalone, enterable convenience store or gun shop on a block corner with parking in front. */
    private void storeCorner(Block b, int type, String name) {
        int face = b.bz % 2 == 0 ? 0 : 2;
        double pw = 30, pd = 30;
        double px0 = b.x1 - pw, px1 = b.x1, pz0 = face == 0 ? b.z0 : b.z1 - pd, pz1 = face == 0 ? b.z0 + pd : b.z1;
        reserve(px0 - 1, pz0 - 1, px1 + 1, pz1 + 1);
        double[] r = local(px0, pz0, px1, pz1, face, 6, 13, 24, 27);
        enterable(r[0], r[1], r[2], r[3], face, type, name, type == Poi.GUN_SHOP ? 0x3E4A3E : 0xE8E0D0);
        double[] lot = local(px0, pz0, px1, pz1, face, 0, 0, 30, 12.5);
        area(lot[0], lot[1], lot[2], lot[3], Area.PARKING);
        for (int i = 0; i < 4; i++) {
            double[] s = localPoint(px0, pz0, px1, pz1, face, 5 + i * 6.5, 6);
            w.parking.add(new ParkingSpot(s[0], s[1], faceYaw(face), ParkingSpot.LOT));
        }
        double[] pole = localPoint(px0, pz0, px1, pz1, face, 2, 1.5);
        light(pole[0], CURB + 6, pole[1], 14, 0xFFF0D8, Light.FLOOD);
    }

    /** Walls with a real doorway and glazed shopfront, a counter and shelves; lit from inside at night. */
    private Poi enterable(double x0, double z0, double x1, double z1, int face, int type, String name, int color) {
        Building bd = building(Building.SHOP, x0, z0, x1, z1);
        bd.interior = true; bd.name = name; bd.nameFace = face; bd.frontFaces = faceBit(face); bd.shopHeight = 4.4;
        double h = CURB + 4.4, t = .3;
        boolean alongX = face == 0 || face == 2;
        double len = alongX ? x1 - x0 : z1 - z0;
        for (int f = 0; f < 4; f++) {
            double[] wall = f == 0 ? new double[]{x0, z0, x1, z0 + t} : f == 2 ? new double[]{x0, z1 - t, x1, z1}
                    : f == 3 ? new double[]{x0, z0, x0 + t, z1} : new double[]{x1 - t, z0, x1, z1};
            if (f != face) { vol(bd, wall[0], CURB, wall[1], wall[2], h, wall[3], Volume.PLAIN, 3, color, 0, true); continue; }
            // Front: piers, low sills, glazing and a central doorway.
            double c = len / 2;
            double[][] spans = {{0, .8, 0}, {.8, c - 1.3, 1}, {c - 1.3, c - 1.0, 0}, {c - 1.0, c + 1.0, 2}, {c + 1.0, c + 1.3, 0}, {c + 1.3, len - .8, 1}, {len - .8, len, 0}};
            for (double[] s : spans) {
                double[] a = segment(wall, alongX, s[0], s[1]);
                if (s[2] == 0) vol(bd, a[0], CURB, a[1], a[2], h, a[3], Volume.PLAIN, 3, color, 0, true);
                else {
                    vol(bd, a[0], CURB + 2.9, a[1], a[2], h, a[3], Volume.PLAIN, 3, color, 0, true);
                    if (s[2] == 1) {
                        vol(bd, a[0], CURB, a[1], a[2], CURB + .55, a[3], Volume.PLAIN, 3, color, 0, true);
                        Prop g = prop(Prop.GLASS, (a[0] + a[2]) / 2, CURB + .55, (a[1] + a[3]) / 2, faceYaw(face), alongX ? a[2] - a[0] : a[3] - a[1], 2.35, .04, 0);
                        g.breakable = true;
                        double gx = alongX ? 0 : .02, gz = alongX ? .02 : 0;
                        g.solids.add(w.solid((a[0] + a[2]) / 2 - (alongX ? (a[2] - a[0]) / 2 : gx), CURB + .55, (a[1] + a[3]) / 2 - (alongX ? gz : (a[3] - a[1]) / 2),
                                (a[0] + a[2]) / 2 + (alongX ? (a[2] - a[0]) / 2 : gx), CURB + 2.9, (a[1] + a[3]) / 2 + (alongX ? gz : (a[3] - a[1]) / 2), g));
                    }
                }
            }
        }
        vol(bd, x0, h, z0, x1, h + .35, z1, Volume.PLAIN, 0, 0x6E6E6A, 0, true);
        area(x0 + t, z0 + t, x1 - t, z1 - t, Area.TILE).y = CURB + .01;
        // Interior layout in the parcel frame: u along the frontage, v from the front wall inwards.
        double depth = alongX ? z1 - z0 : x1 - x0;
        double[] counter = local(x0, z0, x1, z1, face, len - 5.5, depth - 5.2, len - 1.0, depth - 4.4);
        Prop cp = prop(Prop.COUNTER, (counter[0] + counter[2]) / 2, CURB, (counter[1] + counter[3]) / 2, faceYaw(face), 4.5, 1.05, .8, type == Poi.GUN_SHOP ? 1 : 0);
        cp.solids.add(w.solid(counter[0], CURB, counter[1], counter[2], CURB + 1.05, counter[3], cp));
        double[] reg = localPoint(x0, z0, x1, z1, face, len - 3.2, depth - 4.8);
        prop(Prop.REGISTER, reg[0], CURB + 1.05, reg[1], faceYaw(face) + 180, .45, .3, .4, 0);
        double[] clerk = localPoint(x0, z0, x1, z1, face, len - 3.2, depth - 3.4);
        for (int i = 0; i < 3; i++) {
            double[] shelf = local(x0, z0, x1, z1, face, 1.6, 3.2 + i * 2.6, len - 7.5, 3.8 + i * 2.6);
            if (type == Poi.GUN_SHOP && i > 0) continue;
            Prop sp = prop(Prop.SHELF, (shelf[0] + shelf[2]) / 2, CURB, (shelf[1] + shelf[3]) / 2, faceYaw(face), alongX ? shelf[2] - shelf[0] : shelf[3] - shelf[1], 1.7, .6, type == Poi.GUN_SHOP ? 1 : 0);
            sp.solids.add(w.solid(shelf[0], CURB, shelf[1], shelf[2], CURB + 1.7, shelf[3], sp));
        }
        double[] back = local(x0, z0, x1, z1, face, .4, depth - 1.1, len - 6.5, depth - .35);
        Prop fr = prop(type == Poi.GUN_SHOP ? Prop.RACK : Prop.FRIDGE, (back[0] + back[2]) / 2, CURB, (back[1] + back[3]) / 2, faceYaw(face), alongX ? back[2] - back[0] : back[3] - back[1], 2.3, .75, 0);
        fr.solids.add(w.solid(back[0], CURB, back[1], back[2], CURB + 2.3, back[3], fr));
        for (int i = 0; i < 3; i++) {
            double[] lp = localPoint(x0, z0, x1, z1, face, len * (i + 1) / 4, depth / 2);
            light(lp[0], h - .3, lp[1], 9, 0xF4F8FF, Light.INTERIOR);
        }
        double[] door = localPoint(x0, z0, x1, z1, face, len / 2, -2.5);
        double[] interact = localPoint(x0, z0, x1, z1, face, len - 3.2, depth - 6.2);
        Poi poi = new Poi(type, door[0], door[1], faceYaw(face), name);
        poi.interactX = interact[0]; poi.interactZ = interact[1];
        poi.clerkX = clerk[0]; poi.clerkZ = clerk[1]; poi.clerkYaw = faceYaw(face);
        w.pois.add(poi);
        double[] sign = localPoint(x0, z0, x1, z1, face, len / 2, -.5);
        light(sign[0], CURB + 3.8, sign[1], 7, type == Poi.GUN_SHOP ? 0xFF6040 : 0x60FF90, Light.NEON);
        return poi;
    }
    private static double[] segment(double[] wall, boolean alongX, double a, double b) {
        return alongX ? new double[]{wall[0] + a, wall[1], wall[0] + b, wall[3]} : new double[]{wall[0], wall[1] + a, wall[2], wall[1] + b};
    }

    private void gasStation(Block b) {
        int face = 3;
        double px0 = b.x0, px1 = b.x0 + 40, pz0 = (b.z0 + b.z1) / 2 - 20, pz1 = (b.z0 + b.z1) / 2 + 20;
        if (b.district == District.INDUSTRIAL) { face = 1; px0 = b.x1 - 40; px1 = b.x1; }
        reserve(px0 - 1, pz0 - 1, px1 + 1, pz1 + 1);
        double[] lot = local(px0, pz0, px1, pz1, face, 0, 0, 40, 40);
        area(lot[0], lot[1], lot[2], lot[3], Area.PARKING);
        double[] canopy = localPoint(px0, pz0, px1, pz1, face, 20, 12);
        Prop c = prop(Prop.CANOPY, canopy[0], CURB, canopy[1], faceYaw(face), 20, 5.4, 12, 0);
        for (int i = 0; i < 3; i++) {
            double[] pump = localPoint(px0, pz0, px1, pz1, face, 10 + i * 10, 12);
            solidProp(Prop.PUMP, pump[0], CURB, pump[1], faceYaw(face), .8, 1.8, .5, 0, .45, .45, 1.8, false);
            light(pump[0], CURB + 5.2, pump[1], 11, 0xF0F6FF, Light.FLOOD);
        }
        for (double[] uv : new double[][]{{5, 8}, {35, 8}, {5, 16}, {35, 16}}) {
            double[] pillar = localPoint(px0, pz0, px1, pz1, face, uv[0], uv[1]);
            c.solids.add(w.solid(pillar[0] - .25, CURB, pillar[1] - .25, pillar[0] + .25, CURB + 5.4, pillar[1] + .25, c));
        }
        double[] shop = local(px0, pz0, px1, pz1, face, 8, 24, 32, 38);
        enterable(shop[0], shop[1], shop[2], shop[3], face, Poi.GAS, "GAS & GO", 0xE4E6E8);
        double[] sign = localPoint(px0, pz0, px1, pz1, face, 2.5, 2);
        Prop s = prop(Prop.BILLBOARD, sign[0], CURB, sign[1], faceYaw(face), 2.2, 7, .3, 1);
        s.text = "GAS & GO";
    }

    private void spray(Block b) {
        double px0 = b.x1 - 34, px1 = b.x1, pz0 = b.z0, pz1 = b.z0 + 30;
        reserve(px0 - 1, pz0 - 1, px1 + 1, pz1 + 1);
        double x0 = px0 + 6, x1 = px1 - 6, z0 = pz0 + 8, z1 = pz1 - 2;
        Building bd = building(Building.SHOP, x0, z0, x1, z1);
        bd.name = "QUICK SPRAY"; bd.nameFace = 0; bd.frontFaces = 1; bd.interior = true;
        double h = CURB + 6;
        double c = (x0 + x1) / 2;
        vol(bd, x0, CURB, z0, c - 3, h, z0 + .3, Volume.PLAIN, 4, 0xB9C0C4, 0, true);
        vol(bd, c + 3, CURB, z0, x1, h, z0 + .3, Volume.PLAIN, 4, 0xB9C0C4, 0, true);
        vol(bd, c - 3, CURB + 4.4, z0, c + 3, h, z0 + .3, Volume.PLAIN, 4, 0xB9C0C4, 0, true);
        vol(bd, x0, CURB, z0, x0 + .3, h, z1, Volume.PLAIN, 4, 0xB9C0C4, 0, true);
        vol(bd, x1 - .3, CURB, z0, x1, h, z1, Volume.PLAIN, 4, 0xB9C0C4, 0, true);
        vol(bd, x0, CURB, z1 - .3, x1, h, z1, Volume.PLAIN, 4, 0xB9C0C4, 0, true);
        vol(bd, x0, h, z0, x1, h + .3, z1, Volume.PLAIN, 0, 0x7A7E80, 0, true);
        area(x0 + .3, z0 + .3, x1 - .3, z1 - .3, Area.TILE).y = CURB + .01;
        area(px0, pz0, px1, z0, Area.PARKING);
        w.pois.add(new Poi(Poi.SPRAY, c, (z0 + z1) / 2 + 1, 0, "Quick Spray"));
        light(c, h - .5, (z0 + z1) / 2, 10, 0xF4F8FF, Light.INTERIOR);
        light(c, CURB + 5, z0 - 1, 9, 0x40C0FF, Light.NEON);
    }

    private void safehouse(Block b) {
        double px0 = b.x0, px1 = b.x0 + 26, pz0 = b.z1 - 24, pz1 = b.z1;
        reserve(px0 - 1, pz0 - 1, px1 + 1, pz1 + 1);
        Building bd = midrise(px0, pz0, px1, pz1, faceBit(2) | faceBit(3), 2, faceBit(1), 1, 0);
        bd.name = "ALTA APARTMENTS"; bd.nameFace = 2;
        Poi poi = new Poi(Poi.SAFEHOUSE, (px0 + px1) / 2, pz1 + 2, 180, "Safehouse");
        w.pois.add(poi);
        w.spawnX = poi.x; w.spawnZ = pz1 + 2.6; w.spawnYaw = 180;
        w.pois.add(new Poi(Poi.JOB, px0 + 4, pz1 + 2, 180, "Job board"));
        w.pickups.add(new Pickup(Pickup.HEALTH, 100, px1 - 2, CURB, pz1 + 2.5));
        light((px0 + px1) / 2, CURB + 3.4, pz1 + .6, 6, 0xFFD8A0, Light.WARM);
        midtown(b, b.x0, b.z0, b.x1, b.z1, .8);
        custom[b.bx][b.bz] = true;
    }

    // ------------------------------------------------------------------ street furniture
    /** Street lamp positions are shared with the shaders' analytic street lighting. */
    static double[] lampFractions() { return new double[]{.03, .5, .97}; }

    private void streetFurniture() {
        for (int i = 0; i < LINES; i++) for (int side = -1; side <= 1; side += 2) {
            double curb = line(i) + side * w.nsHalf(i);
            for (int bz = 0; bz < LINES - 1; bz++) {
                double a0 = line(bz) + w.ewHalf(bz) + SIDEWALK, a1 = line(bz + 1) - w.ewHalf(bz + 1) - SIDEWALK;
                int bx = side < 0 ? i - 1 : i;
                District d = bx >= 0 && bx < LINES - 1 ? w.blocks[bx][bz].district : District.SUBURB;
                edgeFurniture(true, curb, side, a0, a1, d, w.nsLanes[i], w.nsParking(i), i + bz);
            }
        }
        for (int j = 0; j < LINES; j++) for (int side = -1; side <= 1; side += 2) {
            double curb = line(j) + side * w.ewHalf(j);
            for (int bx = 0; bx < LINES - 1; bx++) {
                double a0 = line(bx) + w.nsHalf(bx) + SIDEWALK, a1 = line(bx + 1) - w.nsHalf(bx + 1) - SIDEWALK;
                int bz = side < 0 ? j - 1 : j;
                District d = bz >= 0 && bz < LINES - 1 ? w.blocks[bx][bz].district : j == LINES - 1 ? District.COAST : District.SUBURB;
                edgeFurniture(false, curb, side, a0, a1, d, w.ewLanes[j], w.ewParking(j), j * 3 + bx);
            }
        }
    }

    /** Furniture along one sidewalk edge. {@code side} points from the road towards the sidewalk. */
    private void edgeFurniture(boolean northSouth, double curb, int side, double a0, double a1, District d, int lanes, boolean parking, int salt) {
        double len = a1 - a0;
        double faceRoad = northSouth ? (side < 0 ? 90 : 270) : (side < 0 ? 180 : 0);
        List<Double> used = new ArrayList<Double>();
        for (double f : lampFractions()) {
            double a = a0 + len * f;
            double[] p = at(northSouth, curb + side * .55, a);
            Prop lamp = solidProp(Prop.STREET_LIGHT, p[0], CURB, p[1], faceRoad, .3, 8.6, .3, lanes, .14, .14, 8.6, true);
            lamp.dir = side;
            double[] l = at(northSouth, curb - side * 1.25, a);
            light(l[0], 8.25, l[1], 22, 0xFFC98A, Light.STREET);
            used.add(a);
        }
        boolean urban = d == District.DOWNTOWN || d == District.MIDTOWN || d == District.NORTH || d == District.COAST;
        if (d != District.INDUSTRIAL) for (double f : new double[]{1 / 3.0, 2 / 3.0}) {
            double a = a0 + len * f;
            double[] p = at(northSouth, curb + side * 1.15, a);
            boolean palm = d == District.COAST || d == District.SUBURB || (salt + (int) (f * 3)) % 3 == 0;
            prop(palm ? Prop.PALM : Prop.TREE, p[0], CURB, p[1], random.nextDouble() * 360, 1, palm ? range(9, 14) : range(6.5, 9), 1, random.nextInt(3));
            w.solid(p[0] - .22, CURB, p[1] - .22, p[0] + .22, CURB + 3, p[1] + .22);
            used.add(a);
        }
        double[] h = at(northSouth, curb + side * .5, a0 + len * .07);
        solidProp(Prop.HYDRANT, h[0], CURB, h[1], faceRoad, .35, .8, .35, 0, .16, .16, .8, true);
        if (urban) {
            double[] t = at(northSouth, curb + side * .6, a0 + len * .42);
            solidProp(Prop.TRASH, t[0], CURB, t[1], faceRoad, .6, 1, .6, 0, .28, .28, 1, true);
            if (random.nextBoolean()) {
                double[] n = at(northSouth, curb + side * .6, a0 + len * .93);
                solidProp(Prop.NEWSBOX, n[0], CURB, n[1], faceRoad, .55, 1.1, .5, random.nextInt(3), .26, .24, 1.1, true);
            }
            if (random.nextDouble() < .3) {
                double[] m = at(northSouth, curb + side * .6, a0 + len * .09);
                solidProp(Prop.MAILBOX, m[0], CURB, m[1], faceRoad, .55, 1.2, .55, 0, .27, .27, 1.2, true);
            }
            if (parking && d != District.COAST) for (double a = a0 + 5; a < a1 - 5; a += 6.2) {
                boolean clear = true;
                for (double u : used) if (Math.abs(u - a) < 1.8) clear = false;
                if (!clear) continue;
                double[] m = at(northSouth, curb + side * .45, a);
                solidProp(Prop.METER, m[0], CURB, m[1], faceRoad, .2, 1.35, .2, 0, .07, .07, 1.35, true);
            }
        }
        if (lanes == 2 && (salt % 3 == 0) && d != District.INDUSTRIAL) {
            double a = a0 + len * .6;
            double[] s = at(northSouth, curb + side * 1.9, a);
            Prop stop = prop(Prop.BUS_STOP, s[0], CURB, s[1], faceRoad, 4.2, 2.6, 1.6, 0);
            stop.text = "VIBE CLIENT";
            double[] b0 = at(northSouth, curb + side * 2.6, a - 2.1), b1 = at(northSouth, curb + side * 2.8, a + 2.1);
            stop.solids.add(w.solid(Math.min(b0[0], b1[0]), CURB, Math.min(b0[1], b1[1]), Math.max(b0[0], b1[0]), CURB + 2.6, Math.max(b0[1], b1[1]), stop));
            light(s[0], CURB + 2.4, s[1], 5, 0xE8F4FF, Light.WARM);
        }
    }
    private static double[] at(boolean northSouth, double across, double along) {
        return northSouth ? new double[]{across, along} : new double[]{along, across};
    }

    private void signals() {
        for (Gta8World.Signal s : w.signals) {
            if (!s.active) continue;
            for (int d = 0; d < 4; d++) {
                if (w.incoming(s.i, s.j, d).length == 0) continue;
                boolean ns = d == Gta8World.NORTH || d == Gta8World.SOUTH;
                double across = ns ? w.nsHalf(s.i) : w.ewHalf(s.j), alongHalf = ns ? w.ewHalf(s.j) : w.nsHalf(s.i);
                double rx = -Gta8World.DIR_Z[d], rz = Gta8World.DIR_X[d];
                double px = s.x() + rx * (across + .7) + Gta8World.DIR_X[d] * (alongHalf + .7);
                double pz = s.z() + rz * (across + .7) + Gta8World.DIR_Z[d] * (alongHalf + .7);
                // The heads face approaching traffic; the mast arm spans the approach lanes.
                Prop p = solidProp(Prop.SIGNAL, px, CURB, pz, d * 90 + 180, ns ? w.nsLanes[s.i] * Gta8World.LANE + 1.2 : w.ewLanes[s.j] * Gta8World.LANE + 1.2, 6.2, .3, 0, .16, .16, 6.2, false);
                p.signalId = s.id(); p.dir = d;
                p.text = ns ? w.ewNames[s.j] : w.nsNames[s.i];
            }
        }
    }

    private void streetParking() {
        for (Gta8World.Lane lane : w.lanes) {
            if (lane.lanes != 1) continue;
            double rx = -Gta8World.DIR_Z[lane.dir], rz = Gta8World.DIR_X[lane.dir];
            for (double t = 9; t < lane.length - 7; t += 6.3) {
                double x = lane.x0 + Gta8World.DIR_X[lane.dir] * t + rx * 3.05, z = lane.z0 + Gta8World.DIR_Z[lane.dir] * t + rz * 3.05;
                w.parking.add(new ParkingSpot(x, z, lane.yaw(), ParkingSpot.STREET));
            }
        }
    }

    // ------------------------------------------------------------------ outskirts
    private void beach() {
        double oz1 = w.gridZ1() + SIDEWALK, x0 = w.gridX0() - SIDEWALK, x1 = w.gridX1() + SIDEWALK;
        named(x0 - 200, oz1, x1, oz1 + 300, "Del Mar Beach");
        for (double x = x0 + 6; x < x1 - 6; x += 13) {
            if (x > Gta8World.PIER_X0 - 3 && x < Gta8World.PIER_X1 + 3) continue;
            prop(Prop.PALM, x + range(-2, 2), w.terrain(x, oz1 + 3), oz1 + 3 + range(0, 2), random.nextDouble() * 360, 1, range(10, 15), 1, random.nextInt(3));
        }
        for (double x = x0 + 60; x < x1 - 40; x += 150) {
            double z = oz1 + 21;
            solidProp(Prop.LIFEGUARD, x, w.terrain(x, z), z, 180, 3, 4.2, 3, 0, 1.5, 1.5, 3.2, false);
        }
        for (int i = 0; i < 70; i++) {
            double x = range(x0 + 10, x1 - 10), z = oz1 + range(7, 25);
            if (x > Gta8World.PIER_X0 - 4 && x < Gta8World.PIER_X1 + 4) continue;
            double y = w.terrain(x, z);
            if (random.nextDouble() < .5) prop(Prop.UMBRELLA, x, y, z, random.nextDouble() * 360, 2.4, 2.4, 2.4, random.nextInt(5));
            prop(Prop.TOWEL, x + range(-1.2, 1.2), y + .02, z + range(-1, 1.5), random.nextDouble() * 360, .9, .02, 1.9, random.nextInt(6));
        }
    }

    private void pier() {
        double oz1 = w.gridZ1() + SIDEWALK, x0 = Gta8World.PIER_X0, x1 = Gta8World.PIER_X1, end = Gta8World.PIER_END;
        double deck = CURB;
        w.addPlatform(new Gta8World.Platform(x0, oz1 - 1, x1, end, deck));
        w.addPlatform(new Gta8World.Platform(x0 - 34, end - 64, x1 + 34, end, deck));
        area(x0, oz1, x1, end - 64, Area.DECK).y = deck + .02;
        area(x0 - 34, end - 64, x1 + 34, end, Area.DECK).y = deck + .02;
        named(x0 - 34, oz1 + 20, x1 + 34, end, "Del Mar Pier");
        rail(x0 - .1, oz1 + 8, x0 + .1, end - 64);
        rail(x1 - .1, oz1 + 8, x1 + .1, end - 64);
        rail(x0 - 34, end - 64 - .1, x0, end - 64 + .1);
        rail(x1, end - 64 - .1, x1 + 34, end - 64 + .1);
        rail(x0 - 34, end - .1, x1 + 34, end + .1);
        rail(x0 - 34 - .1, end - 64, x0 - 34 + .1, end);
        rail(x1 + 34 - .1, end - 64, x1 + 34 + .1, end);
        for (double z = oz1 + 30; z < end; z += 6) for (double x : new double[]{x0 + .3, x1 - .3}) prop(Prop.PILING, x, deck, z, 0, .45, 12, .45, 0);
        for (double z = end - 60; z < end; z += 8) for (double x = x0 - 32; x <= x1 + 32; x += 8) prop(Prop.PILING, x, deck, z, 0, .45, 14, .45, 0);
        for (double z = oz1 + 16; z < end - 64; z += 22) for (double x : new double[]{x0 + .5, x1 - .5}) {
            solidProp(Prop.LAMP_PARK, x, deck, z, 0, .3, 4.2, .3, 1, .12, .12, 4.2, false);
            light(x, deck + 4, z, 9, 0xFFD8A0, Light.WARM);
        }
        double fx = x0 - 16, fz = end - 30;
        Prop ferris = prop(Prop.FERRIS, fx, deck, fz, 90, 1, 38, 1, 0);
        ferris.solids.add(w.solid(fx - 11, deck, fz - 3.5, fx - 9, deck + 20, fz + 3.5, ferris));
        ferris.solids.add(w.solid(fx + 9, deck, fz - 3.5, fx + 11, deck + 20, fz + 3.5, ferris));
        light(fx, deck + 22, fz, 30, 0xFF60C0, Light.NEON);
        Building grill = building(Building.SHOP, x1 + 8, end - 50, x1 + 30, end - 30);
        grill.name = "PIER GRILL"; grill.nameFace = 3; grill.frontFaces = faceBit(3); grill.shopHeight = 4.2;
        vol(grill, x1 + 8, deck, end - 50, x1 + 30, deck + 4.2, end - 30, Volume.SHOPFRONT, 3, 0xE8DCC0, 15 & ~faceBit(3), true);
        vol(grill, x1 + 8, deck + 4.2, end - 50, x1 + 30, deck + 5.2, end - 30, Volume.PLAIN, 0, 0x5A7A8A, 0, true);
        for (int i = 0; i < 6; i++) solidProp(Prop.TABLE, x1 + 2 + (i % 3) * 3, deck, end - 46 + (i / 3) * 6, 0, 1.2, .75, 1.2, 1, .6, .6, .75, true);
        w.pickups.add(new Pickup(Pickup.CASH, 500, x1 + 32, deck, end - 3));
    }
    private void rail(double x0, double z0, double x1, double z1) {
        Prop p = prop(Prop.RAILING, (x0 + x1) / 2, CURB, (z0 + z1) / 2, x1 - x0 > z1 - z0 ? 0 : 90, Math.max(x1 - x0, z1 - z0), 1.1, .1, 0);
        p.solids.add(w.solid(x0, CURB, z0, x1, CURB + 1.1, z1, p));
    }

    private void harbor() {
        double x0 = w.gridX1() + SIDEWALK, z0 = w.gridZ0() - SIDEWALK, q = Gta8World.QUAY;
        named(x0, z0, q + 400, 560, "Port of Los Vibes");
        for (int i = 0; i < 4; i++) {
            double wz = z0 + 30 + i * 230;
            warehouse(x0 + 14, wz, x0 + 56, wz + 70);
        }
        for (double z = z0 + 20; z < 520; z += 70) containers(x0 + 70, z, q - 36, z + 52, false);
        for (double cz : new double[]{-330, -90, 150, 390}) {
            Prop crane = prop(Prop.CRANE, q - 13, CURB, cz, 90, 24, 52, 18, 0);
            for (double dx : new double[]{-10, 10}) for (double dz : new double[]{-8, 8})
                crane.solids.add(w.solid(q - 13 + dx - .6, CURB, cz + dz - .6, q - 13 + dx + .6, CURB + 40, cz + dz + .6, crane));
            light(q - 13, 40, cz, 30, 0xFFE0B0, Light.FLOOD);
        }
        for (double z = z0 + 5; z < 555; z += 9) solidProp(Prop.BOLLARD, q - 1.1, CURB, z, 0, .45, .7, .45, 1, .22, .22, .7, false);
        for (double x = x0 + 5; x < q; x += 9) solidProp(Prop.BOLLARD, x, CURB, 558.9, 0, .45, .7, .45, 1, .22, .22, .7, false);
        Prop ship = prop(Prop.SHIP, q + 24, -1.2, -40, 0, 30, 22, 210, 0);
        ship.solids.add(w.solid(q + 9, -12, -145, q + 39, 16, 65, ship));
        for (double z = z0 + 40; z < 540; z += 60) light(x0 + 64, 18, z, 34, 0xFFE8C8, Light.FLOOD);
        w.pickups.add(new Pickup(Pickup.WEAPON, 3, q - 30, CURB, 540));
        w.pickups.add(new Pickup(Pickup.ARMOR, 50, x0 + 60, CURB, z0 + 10));
    }

    private void hills() {
        double base = -640, cx = -110;
        String letters = "VIBE";
        for (int i = 0; i < 4; i++) {
            double x = cx - 25 + i * 16.5, y = w.terrain(x, base) - .8;
            Prop p = prop(Prop.VIBE_LETTER, x, y, base, 180, 13, 16, .6, letters.charAt(i));
            p.solids.add(w.solid(x - 6.5, y, base - .6, x + 6.5, y + 16, base + .6, p));
            light(x, y + 1, base + 6, 20, 0xFFF4E0, Light.FLOOD);
        }
        named(cx - 60, base - 40, cx + 60, base + 40, "VIBE Sign");
        w.pickups.add(new Pickup(Pickup.WEAPON, 2, cx + 30, w.terrain(cx + 30, base + 8), base + 8));
        // Radio mast on a summit with an aviation beacon.
        double mx = -420, mz = -720, my = w.terrain(mx, mz);
        prop(Prop.ANTENNA, mx, my, mz, 0, 1.4, 70, 1.4, 1);
        light(mx, my + 70, mz, 0, 0xFF2010, Light.BEACON);
        // Vegetation, denser near the city edge.
        Random veg = new Random(77);
        for (int i = 0; i < 2600; i++) {
            double x = -780 + veg.nextDouble() * 1560, z = -780 + veg.nextDouble() * 1560;
            double north = w.gridZ0() - SIDEWALK - z, west = w.gridX0() - SIDEWALK - x, d = Math.max(north, west);
            if (d < 6 || d > 330) continue;
            if (Math.abs(x - cx) < 70 && Math.abs(z - base) < 40) continue;
            if (x > w.gridX1() + SIDEWALK) continue;
            double y = w.terrain(x, z);
            double slope = Math.abs(w.terrain(x + 2, z) - y) + Math.abs(w.terrain(x, z + 2) - y);
            if (slope > 3.2 || veg.nextDouble() > .75 - d / 700) continue;
            int type = veg.nextDouble() < .5 ? Prop.PINE : veg.nextDouble() < .6 ? Prop.TREE : Prop.BUSH;
            if (z > w.gridZ1()) type = Prop.PALM;
            prop(type, x, y - .2, z, veg.nextDouble() * 360, 1, type == Prop.BUSH ? 1.2 : 6 + veg.nextDouble() * 7, 1, veg.nextInt(3));
            if (type != Prop.BUSH) w.solid(x - .3, y, z - .3, x + .3, y + 3, z + .3);
        }
        for (int i = 0; i < 260; i++) {
            double x = -780 + veg.nextDouble() * 1560, z = -780 + veg.nextDouble() * 1560;
            double d = Math.max(w.gridZ0() - SIDEWALK - z, w.gridX0() - SIDEWALK - x);
            if (d < 15 || d > 360 || x > w.gridX1()) continue;
            double s = 1 + veg.nextDouble() * 3.5;
            Prop rock = prop(Prop.ROCK, x, w.terrain(x, z) - s * .3, z, veg.nextDouble() * 360, s * (1 + veg.nextDouble()), s, s * (1 + veg.nextDouble()), veg.nextInt(4));
            rock.solids.add(w.solid(x - s * .7, rock.y, z - s * .7, x + s * .7, rock.y + s * 1.2, z + s * .7, rock));
        }
    }

    private void pickups() {
        // Hidden stashes in courtyards and parks.
        int[][] spots = {{3, 3}, {6, 5}, {2, 7}, {7, 1}, {5, 2}};
        int n = 0;
        for (int[] s : spots) {
            Block b = w.blocks[s[0]][s[1]];
            double x = (b.x0 + b.x1) / 2 + 3, z = (b.z0 + b.z1) / 2 + 3;
            if (insideBuilding(x, z, 1) || w.blocked(x, CURB, z, .4, 1.8)) { x = b.x0 + 2; z = b.z0 + 2; }
            if (insideBuilding(x, z, .5)) continue;
            int type = n % 3 == 0 ? Pickup.CASH : n % 3 == 1 ? Pickup.ARMOR : Pickup.WEAPON;
            w.pickups.add(new Pickup(type, type == Pickup.CASH ? 750 : type == Pickup.ARMOR ? 50 : 1, x, CURB, z));
            n++;
        }
        for (Block[] column : w.blocks) for (Block b : column) if (b.district == District.PARK) {
            w.pickups.add(new Pickup(Pickup.HEALTH, 50, b.x0 + 4, CURB, b.z0 + 4));
        }
    }
}
