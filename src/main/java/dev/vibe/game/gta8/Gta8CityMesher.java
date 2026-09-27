package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8World.Area;
import dev.vibe.game.gta8.Gta8World.Block;
import dev.vibe.game.gta8.Gta8World.Building;
import dev.vibe.game.gta8.Gta8World.Prop;
import dev.vibe.game.gta8.Gta8World.Volume;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static dev.vibe.game.gta8.Gta8Materials.*;
import static dev.vibe.game.gta8.Gta8MeshBuilder.*;
import static dev.vibe.game.gta8.Gta8World.CURB;
import static dev.vibe.game.gta8.Gta8World.LINES;
import static dev.vibe.game.gta8.Gta8World.SIDEWALK;
import static dev.vibe.game.gta8.Gta8World.line;

/**
 * Builds the static city into 96 m chunks on a worker thread. Each chunk has a main mesh (ground,
 * buildings, terrain), a detail mesh (street furniture, drawn nearer) and an alpha-tested foliage mesh.
 */
final class Gta8CityMesher {
    static final double CHUNK = 96, ORIGIN = -480 - 96 * 12;
    static final int CHUNKS = 34;
    final Gta8World world;
    final Gta8Textures.SignAtlas atlas = new Gta8Textures.SignAtlas();
    final Chunk[] chunks = new Chunk[CHUNKS * CHUNKS];
    final List<Chunk> active = new ArrayList<Chunk>();
    final Gta8MeshBuilder horizon = new Gta8MeshBuilder();
    Gta8Mesh horizonMesh;
    volatile double progress;

    static final class Chunk {
        final int cx, cz;
        final Gta8MeshBuilder main = new Gta8MeshBuilder(), detail = new Gta8MeshBuilder(), foliage = new Gta8MeshBuilder();
        Gta8Mesh mainMesh, detailMesh, foliageMesh;
        final List<Prop> props = new ArrayList<Prop>();
        int propRevision = -1;
        double minX, minY, minZ, maxX, maxY, maxZ, radius, centerX, centerY, centerZ;
        Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }
        void bounds() {
            minX = Math.min(main.minX, Math.min(detail.minX, foliage.minX)); minY = Math.min(main.minY, Math.min(detail.minY, foliage.minY));
            minZ = Math.min(main.minZ, Math.min(detail.minZ, foliage.minZ)); maxX = Math.max(main.maxX, Math.max(detail.maxX, foliage.maxX));
            maxY = Math.max(main.maxY, Math.max(detail.maxY, foliage.maxY)); maxZ = Math.max(main.maxZ, Math.max(detail.maxZ, foliage.maxZ));
            centerX = (minX + maxX) / 2; centerY = (minY + maxY) / 2; centerZ = (minZ + maxZ) / 2;
            radius = .5 * Math.sqrt((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY) + (maxZ - minZ) * (maxZ - minZ));
        }
        double distanceSquared(double x, double y, double z) {
            double dx = Math.max(0, Math.max(minX - x, x - maxX)), dy = Math.max(0, Math.max(minY - y, y - maxY)), dz = Math.max(0, Math.max(minZ - z, z - maxZ));
            return dx * dx + dy * dy + dz * dz;
        }
    }

    Gta8CityMesher(Gta8World world) {
        this.world = world;
        for (Building b : world.buildings) if (b.name != null) signFor(b);
        for (Prop p : world.props) if (p.text != null) {
            if (p.type == Prop.SIGNAL) atlas.get(Gta8Textures.SignAtlas.STREET, p.text.toUpperCase(java.util.Locale.ROOT));
            else if (p.type == Prop.BILLBOARD && p.variant == 1) atlas.get(Gta8Textures.SignAtlas.SHOP, p.text);
            else atlas.get(Gta8Textures.SignAtlas.BILLBOARD, p.text);
        }
        atlas.get(Gta8Textures.SignAtlas.CROSS, "+");
        atlas.get(Gta8Textures.SignAtlas.BILLBOARD, "VIBE CLIENT");
    }
    private float[] signFor(Building b) {
        int kind = b.kind == Building.HOTEL ? Gta8Textures.SignAtlas.HOTEL : b.kind == Building.CIVIC || b.kind == Building.WAREHOUSE ? Gta8Textures.SignAtlas.CIVIC
                : b.name.contains("BAR") || b.name.contains("DINER") || b.name.contains("MOTEL") ? Gta8Textures.SignAtlas.NEON : Gta8Textures.SignAtlas.SHOP;
        return atlas.get(kind, b.name);
    }

    Chunk chunk(double x, double z) {
        int cx = (int) Gta8Math.clamp(Math.floor((x - ORIGIN) / CHUNK), 0, CHUNKS - 1), cz = (int) Gta8Math.clamp(Math.floor((z - ORIGIN) / CHUNK), 0, CHUNKS - 1);
        Chunk c = chunks[cz * CHUNKS + cx];
        if (c == null) { c = new Chunk(cx, cz); chunks[cz * CHUNKS + cx] = c; }
        return c;
    }

    /** CPU work only; safe to run off the render thread. */
    void build() {
        progress = .02;
        roads();
        progress = .12;
        blocks();
        progress = .22;
        for (int i = 0; i < world.buildings.size(); i++) {
            building(world.buildings.get(i));
            if (i % 50 == 0) progress = .22 + .3 * i / world.buildings.size();
        }
        progress = .52;
        for (Prop p : world.props) if (p.type != Prop.GLASS && p.type != Prop.FERRIS) chunk(p.x, p.z).props.add(p);
        int n = 0;
        for (Chunk c : chunks) if (c != null) { propsInto(c); if (++n % 20 == 0) progress = .52 + .2 * n / 600.0; }
        progress = .74;
        harbour();
        terrain();
        progress = .95;
        horizon();
        for (Chunk c : chunks) if (c != null) { c.bounds(); active.add(c); }
        atlas.finish();
        progress = 1;
    }

    /** GL thread: upload every chunk and free CPU copies. */
    void upload() {
        for (Chunk c : active) {
            c.mainMesh = Gta8Mesh.upload(c.main);
            c.detailMesh = Gta8Mesh.upload(c.detail);
            c.foliageMesh = Gta8Mesh.upload(c.foliage);
            c.main.clear(); c.foliage.clear(); c.detail.clear();
            c.propRevision = world.brokenRevision;
        }
        horizonMesh = Gta8Mesh.upload(horizon);
        horizon.clear();
    }
    /** Rebuilds detail meshes of chunks with knocked-over props. */
    void refreshProps(Chunk c) {
        c.detail.clear(); c.foliage.clear();
        propsInto(c);
        if (c.detailMesh != null) c.detailMesh.close();
        if (c.foliageMesh != null) c.foliageMesh.close();
        c.detailMesh = Gta8Mesh.upload(c.detail);
        c.foliageMesh = Gta8Mesh.upload(c.foliage);
        c.detail.clear(); c.foliage.clear();
        c.propRevision = world.brokenRevision;
    }
    void close() {
        for (Chunk c : active) {
            if (c.mainMesh != null) c.mainMesh.close();
            if (c.detailMesh != null) c.detailMesh.close();
            if (c.foliageMesh != null) c.foliageMesh.close();
        }
        if (horizonMesh != null) horizonMesh.close();
    }

    // ------------------------------------------------------------------ roads and ground
    private void roads() {
        for (int i = 0; i < LINES; i++) for (int j = 0; j < LINES - 1; j++) {
            double x = line(i), h = world.nsHalf(i), z0 = line(j) + world.ewHalf(j), z1 = line(j + 1) - world.ewHalf(j + 1);
            Gta8MeshBuilder b = chunk(x, (z0 + z1) / 2).main;
            b.material(ASPHALT, world.nsLanes[i] + (world.nsParking(i) ? 4 : 0)).extra((int) Math.round(z1 - z0)).color(0x3B3D40).ao(1);
            // u: offset to the right of southbound travel (west), v: distance south along the segment.
            b.quad(x + h, 0, z0, x - h, 0, z0, x - h, 0, z1, x + h, 0, z1, -h, 0, h, 0, h, z1 - z0, -h, z1 - z0);
        }
        for (int j = 0; j < LINES; j++) for (int i = 0; i < LINES - 1; i++) {
            double z = line(j), h = world.ewHalf(j), x0 = line(i) + world.nsHalf(i), x1 = line(i + 1) - world.nsHalf(i + 1);
            Gta8MeshBuilder b = chunk((x0 + x1) / 2, z).main;
            b.material(ASPHALT, world.ewLanes[j] + (world.ewParking(j) ? 4 : 0)).extra((int) Math.round(x1 - x0)).color(0x3B3D40).ao(1);
            b.quad(x0, 0, z + h, x1, 0, z + h, x1, 0, z - h, x0, 0, z - h, h, 0, h, x1 - x0, -h, x1 - x0, -h, 0);
        }
        for (int i = 0; i < LINES; i++) for (int j = 0; j < LINES; j++) {
            double x = line(i), z = line(j), hx = world.nsHalf(i), hz = world.ewHalf(j);
            Gta8MeshBuilder b = chunk(x, z).main;
            b.material(ROADPLAIN).extra(0).color(0x3A3C3F);
            b.quad(x - hx, 0, z + hz, x + hx, 0, z + hz, x + hx, 0, z - hz, x - hx, 0, z - hz, 0, 0, 1, 0, 1, 1, 0, 1);
        }
        b0().extra(0);
    }
    private Gta8MeshBuilder b0() { return chunk(0, 0).main; }

    private void blocks() {
        for (int bx = 0; bx < LINES - 1; bx++) for (int bz = 0; bz < LINES - 1; bz++) {
            Block blk = world.blocks[bx][bz];
            double ax0 = line(bx) + world.nsHalf(bx), ax1 = line(bx + 1) - world.nsHalf(bx + 1);
            double az0 = line(bz) + world.ewHalf(bz), az1 = line(bz + 1) - world.ewHalf(bz + 1);
            Gta8MeshBuilder b = chunk((ax0 + ax1) / 2, (az0 + az1) / 2).main;
            sidewalkRing(b, ax0, az0, ax1, az1, blk.x0, blk.z0, blk.x1, blk.z1);
            int material = blk.ground == Block.GRASS ? GRASS : blk.ground == Block.ASPHALT ? ROADPLAIN : blk.district == Gta8World.District.DOWNTOWN ? PAVERS : CONCRETE;
            int color = blk.ground == Block.GRASS ? 0x5E7A3A : blk.ground == Block.ASPHALT ? 0x44464A : material == PAVERS ? 0xB8AE9E : 0xAEACA6;
            ground(b, blk.x0, blk.z0, blk.x1, blk.z1, CURB, material, color);
        }
        // Outer sidewalks around the street grid.
        double gx0 = world.gridX0(), gx1 = world.gridX1(), gz0 = world.gridZ0(), gz1 = world.gridZ1(), s = SIDEWALK;
        for (double x = gx0 - s; x < gx1 + s - .01; x += CHUNK) {
            double xe = Math.min(gx1 + s, x + CHUNK);
            slab(chunk((x + xe) / 2, gz0).main, x, gz0 - s, xe, gz0, 2, 0, 0, 0);
            slab(chunk((x + xe) / 2, gz1).main, x, gz1, xe, gz1 + s, 0, 0, 2, 0);
        }
        for (double z = gz0; z < gz1 - .01; z += CHUNK) {
            double ze = Math.min(gz1, z + CHUNK);
            slab(chunk(gx0, (z + ze) / 2).main, gx0 - s, z, gx0, ze, 0, 2, 0, 0);
            slab(chunk(gx1, (z + ze) / 2).main, gx1, z, gx1 + s, ze, 0, 0, 0, 2);
        }
        for (Area a : world.areas) area(a);
    }
    /** Four kerbed sidewalk strips around a lot. Kerb faces point at the road. */
    private void sidewalkRing(Gta8MeshBuilder b, double ax0, double az0, double ax1, double az1, double lx0, double lz0, double lx1, double lz1) {
        slab(b, ax0, az0, ax1, lz0, 2, 2, 0, 2);
        slab(b, ax0, lz1, ax1, az1, 0, 2, 2, 2);
        slab(b, ax0, lz0, lx0, lz1, 0, 0, 0, 2);
        slab(b, lx1, lz0, ax1, lz1, 0, 2, 0, 0);
    }
    /** Sidewalk slab; kerb flags per side (N, E, S, W): 2 = exposed kerb face. */
    private void slab(Gta8MeshBuilder b, double x0, double z0, double x1, double z1, int n, int e, int s, int w) {
        if (x1 - x0 < .01 || z1 - z0 < .01) return;
        b.material(CONCRETE).color(0xB4B1AA).ao(1);
        b.box(x0, 0, z0, x1, CURB, z1, FACE_UP);
        b.color(0xC4C1BA);
        int faces = (n > 0 ? FACE_N : 0) | (e > 0 ? FACE_E : 0) | (s > 0 ? FACE_S : 0) | (w > 0 ? FACE_W : 0);
        if (faces != 0) b.box(x0, 0, z0, x1, CURB, z1, faces);
    }
    /** Ground finish with holes cut for pools. */
    private void ground(Gta8MeshBuilder b, double x0, double z0, double x1, double z1, double y, int material, int color) {
        List<double[]> pieces = new ArrayList<double[]>();
        pieces.add(new double[]{x0, z0, x1, z1});
        for (Gta8World.Pool pool : world.pools) {
            if (pool.x1 <= x0 || pool.x0 >= x1 || pool.z1 <= z0 || pool.z0 >= z1) continue;
            List<double[]> next = new ArrayList<double[]>();
            for (double[] r : pieces) subtract(r, pool.x0, pool.z0, pool.x1, pool.z1, next);
            pieces = next;
        }
        b.material(material).color(color).ao(1);
        for (double[] r : pieces) b.quad(r[0], y, r[3], r[2], y, r[3], r[2], y, r[1], r[0], y, r[1], r[0], r[3], r[2], r[3], r[2], r[1], r[0], r[1]);
        for (Gta8World.Pool pool : world.pools) {
            if (pool.x1 <= x0 || pool.x0 >= x1 || pool.z1 <= z0 || pool.z0 >= z1) continue;
            b.material(FLOORTILE).color(0x9AD0DA);
            // Inner walls face into the basin.
            b.quad(pool.x0, pool.level - 1.5, pool.z0, pool.x1, pool.level - 1.5, pool.z0, pool.x1, y, pool.z0, pool.x0, y, pool.z0, 0, 0, 1, 0, 1, 1, 0, 1);
            b.quad(pool.x1, pool.level - 1.5, pool.z1, pool.x0, pool.level - 1.5, pool.z1, pool.x0, y, pool.z1, pool.x1, y, pool.z1, 0, 0, 1, 0, 1, 1, 0, 1);
            b.quad(pool.x0, pool.level - 1.5, pool.z1, pool.x0, pool.level - 1.5, pool.z0, pool.x0, y, pool.z0, pool.x0, y, pool.z1, 0, 0, 1, 0, 1, 1, 0, 1);
            b.quad(pool.x1, pool.level - 1.5, pool.z0, pool.x1, pool.level - 1.5, pool.z1, pool.x1, y, pool.z1, pool.x1, y, pool.z0, 0, 0, 1, 0, 1, 1, 0, 1);
            b.box(pool.x0, pool.level - 1.55, pool.z0, pool.x1, pool.level - 1.5, pool.z1, FACE_UP);
            b.material(POOL).color(0x5AB8C8);
            b.box(pool.x0, pool.level - .1, pool.z0, pool.x1, pool.level, pool.z1, FACE_UP);
        }
    }
    private static void subtract(double[] r, double hx0, double hz0, double hx1, double hz1, List<double[]> out) {
        if (hx1 <= r[0] || hx0 >= r[2] || hz1 <= r[1] || hz0 >= r[3]) { out.add(r); return; }
        if (hz0 > r[1]) out.add(new double[]{r[0], r[1], r[2], hz0});
        if (hz1 < r[3]) out.add(new double[]{r[0], hz1, r[2], r[3]});
        double z0 = Math.max(r[1], hz0), z1 = Math.min(r[3], hz1);
        if (hx0 > r[0]) out.add(new double[]{r[0], z0, hx0, z1});
        if (hx1 < r[2]) out.add(new double[]{hx1, z0, r[2], z1});
    }
    private void area(Area a) {
        if (a.type == Area.NAMED) return;
        Gta8MeshBuilder b = chunk((a.x0 + a.x1) / 2, (a.z0 + a.z1) / 2).main;
        switch (a.type) {
            case Area.PARK: b.material(GRASS).color(0x5E7E3A); break;
            case Area.PATH: b.material(PAVERS).color(0xC2B8A4); break;
            case Area.DRIVEWAY: b.material(CONCRETE).color(0xB8B4AC); break;
            case Area.PARKING: b.material(ROADPLAIN).color(0x3E4043); break;
            case Area.TILE: b.material(FLOORTILE | (a.y < CURB + .015 ? INDOOR : 0)).color(a.y < CURB + .015 ? 0xD8D4CC : 0xD6D0C4); break;
            case Area.DECK: deck(a); return;
            default: b.material(PAVERS).color(0xB8AE9E);
        }
        b.ao(1);
        if (a.y <= CURB + .015) { b.quad(a.x0, a.y, a.z1, a.x1, a.y, a.z1, a.x1, a.y, a.z0, a.x0, a.y, a.z0, a.x0, a.z1, a.x1, a.z1, a.x1, a.z0, a.x0, a.z0); return; }
        b.box(a.x0, CURB, a.z0, a.x1, a.y, a.z1, FACE_UP | SIDES);
        if (a.type == Area.PARKING) {
            // Painted bays along the long edge.
            b.material(PAINT).color(0xD8D8D0);
            boolean alongX = a.x1 - a.x0 > a.z1 - a.z0;
            if (alongX) for (double x = a.x0 + 1; x < a.x1 - 1; x += 2.8) b.box(x, a.y, a.z1 - 5.2, x + .1, a.y + .004, a.z1 - .4, FACE_UP);
            else for (double z = a.z0 + 1; z < a.z1 - 1; z += 2.8) b.box(a.x1 - 5.2, a.y, z, a.x1 - .4, a.y + .004, z + .1, FACE_UP);
        }
    }
    private void deck(Area a) {
        for (double z = a.z0; z < a.z1 - .01; z += 32) {
            double ze = Math.min(a.z1, z + 32);
            Gta8MeshBuilder b = chunk((a.x0 + a.x1) / 2, (z + ze) / 2).main;
            b.material(DECK).color(0x9C8466).ao(1);
            b.box(a.x0, a.y - .45, z, a.x1, a.y, ze);
            b.material(WOOD).color(0x5E4E3E);
            for (double x = a.x0 + 1; x < a.x1; x += 4) b.box(x - .15, a.y - 1.1, z, x + .15, a.y - .45, ze, FACE_E | FACE_W | FACE_DOWN);
        }
    }

    // ------------------------------------------------------------------ buildings
    private void building(Building bd) {
        Gta8MeshBuilder b = chunk((bd.x0 + bd.x1) / 2, (bd.z0 + bd.z1) / 2).main;
        Gta8MeshBuilder d = chunk((bd.x0 + bd.x1) / 2, (bd.z0 + bd.z1) / 2).detail;
        b.seed(bd.seed).extra(0).ao(1);
        d.seed(bd.seed).extra(0).ao(1);
        double top = bd.top();
        for (Volume v : bd.volumes) volume(b, bd, v, top);
        if (bd.kind == Building.HOUSE) { house(b, d, bd); return; }
        Volume topVolume = null;
        for (Volume v : bd.volumes) if (topVolume == null || v.y1 > topVolume.y1 && v.kind != Volume.PLAIN) topVolume = v;
        if (bd.kind == Building.MIDRISE || bd.kind == Building.HOTEL || bd.kind == Building.CIVIC) {
            Volume upper = bd.volumes.get(bd.volumes.size() - 1);
            int wallType = upper.style & 3;
            int c = shade(upper.color, .92);
            // Cornice at the roof and a belt course above the shopfronts.
            b.material(wall(wallType)).color(c);
            b.box(bd.x0 - .35, upper.y1 - .55, bd.z0 - .35, bd.x1 + .35, upper.y1 - .15, bd.z1 + .35, SIDES | FACE_DOWN | FACE_UP);
            if (bd.shopHeight > 0) b.box(bd.x0 - .12, CURB + bd.shopHeight - .05, bd.z0 - .12, bd.x1 + .12, CURB + bd.shopHeight + .3, bd.z1 + .12, SIDES | FACE_DOWN | FACE_UP);
            parapet(b, bd.x0, bd.z0, bd.x1, bd.z1, upper.y1 - .15, .9, wall(wallType), c);
            if (bd.awningColor >= 0) awnings(d, bd);
            if (bd.kind == Building.HOTEL) balconies(d, bd, upper);
        } else if (bd.kind == Building.TOWER && topVolume != null) {
            parapet(b, topVolume.x0, topVolume.z0, topVolume.x1, topVolume.z1, topVolume.y1, 1.2, METAL, 0x5A6066);
            // Entrance canopies on the street faces.
            d.material(METAL, 60).color(0x3A4046);
            double cx = (bd.x0 + bd.x1) / 2, cz = (bd.z0 + bd.z1) / 2;
            d.box(cx - 4, CURB + 4.2, bd.z0 - 2.6, cx + 4, CURB + 4.5, bd.z0);
            d.box(cx - 4, CURB + 4.2, bd.z1, cx + 4, CURB + 4.5, bd.z1 + 2.6);
            d.material(EMISSIVE, NIGHT).color(0xFFF2DC);
            d.box(cx - 3.6, CURB + 4.18, bd.z0 - 2.3, cx + 3.6, CURB + 4.2, bd.z0 - .2, FACE_DOWN);
            d.box(cx - 3.6, CURB + 4.18, bd.z1 + .2, cx + 3.6, CURB + 4.2, bd.z1 + 2.3, FACE_DOWN);
        } else if (bd.kind == Building.WAREHOUSE) {
            warehouseDetails(b, d, bd);
        } else if (bd.kind == Building.SHOP && bd.interior) {
            parapet(b, bd.x0, bd.z0, bd.x1, bd.z1, top, .5, PAINT, 0x8A8A86);
        }
        if (bd.name != null && bd.nameFace >= 0) sign(d, bd, top);
    }

    private void volume(Gta8MeshBuilder b, Building bd, Volume v, double buildingTop) {
        int wallType = v.style & 3;
        boolean interiorShell = bd.interior;
        for (int face = 0; face < 4; face++) {
            int bit = 1 << face;
            boolean blank = (v.blank & bit) != 0;
            int material;
            int param = 0, extra = 0;
            switch (v.kind) {
                case Volume.FACADE: material = blank ? wall(wallType) : FACADE; param = v.style; break;
                case Volume.GLASS: material = CURTAIN; param = v.style; break;
                case Volume.LOBBY: material = SHOPFRONT; param = 16 + v.style; extra = (int) Math.round((v.y1 - v.y0) * 4); break;
                case Volume.SHOPFRONT: material = blank ? wall(0) : SHOPFRONT; param = v.style; extra = (int) Math.round((v.y1 - v.y0) * 4); break;
                case Volume.CORRUGATED: material = CORRUGATED; break;
                case Volume.SIDING: material = SIDING; break;
                default: material = v.style == 1 ? PLASTER : v.style == 3 ? PLASTER : v.style == 4 ? CORRUGATED : CONCRETE_WALL;
            }
            if (interiorShell && faceInside(bd, v, face)) material = (v.style == 4 ? CORRUGATED : PLASTER) | INDOOR;
            b.material(material, param).extra(extra).color(v.color);
            b.box(v.x0, v.y0, v.z0, v.x1, v.y1, v.z1, faceFlag(face), 0);
        }
        b.extra(0);
        boolean exposedTop = v.y1 >= buildingTop - .01 || v.kind == Volume.PLAIN || isRoofStep(bd, v);
        if (exposedTop) {
            b.material(ROOF).color(0x8A8884);
            b.box(v.x0, v.y0, v.z0, v.x1, v.y1, v.z1, FACE_UP);
        }
        if (interiorShell && v.y0 > CURB + 2) {
            b.material(PLASTER | INDOOR).color(0xEEEEEA);
            b.box(v.x0, v.y0, v.z0, v.x1, v.y1, v.z1, FACE_DOWN);
            if (v.x1 - v.x0 > 6 && v.z1 - v.z0 > 6) {
                b.material(EMISSIVE | INDOOR, ALWAYS).color(0xE8F0FF);
                double cx = (v.x0 + v.x1) / 2, cz = (v.z0 + v.z1) / 2;
                for (int i = -1; i <= 1; i++) b.box(cx + i * (v.x1 - v.x0) / 4 - .6, v.y0 - .02, cz - .15, cx + i * (v.x1 - v.x0) / 4 + .6, v.y0, cz + .15, FACE_DOWN);
            }
        }
        if (v.y0 > CURB + .5 && v.kind != Volume.PLAIN && !bd.interior) {
            b.material(PLASTER).color(shade(v.color, .7));
            b.box(v.x0, v.y0, v.z0, v.x1, v.y1, v.z1, FACE_DOWN);
        }
    }
    private static int faceFlag(int face) { return face == 0 ? FACE_N : face == 1 ? FACE_E : face == 2 ? FACE_S : FACE_W; }
    /** Does this face of a wall volume point into the building interior? */
    private static boolean faceInside(Building bd, Volume v, int face) {
        switch (face) {
            case 0: return v.z0 > bd.z0 + .05;
            case 1: return v.x1 < bd.x1 - .05;
            case 2: return v.z1 < bd.z1 - .05;
            default: return v.x0 > bd.x0 + .05;
        }
    }
    private static boolean isRoofStep(Building bd, Volume v) {
        for (Volume o : bd.volumes) if (o != v && Math.abs(o.y0 - v.y1) < .01 && o.x0 >= v.x0 - .01 && o.x1 <= v.x1 + .01 && o.z0 >= v.z0 - .01 && o.z1 <= v.z1 + .01
                && (o.x0 > v.x0 + .05 || o.x1 < v.x1 - .05 || o.z0 > v.z0 + .05 || o.z1 < v.z1 - .05)) return true;
        return false;
    }
    private static int shade(int rgb, double k) {
        int r = (int) Math.min(255, (rgb >> 16 & 255) * k), g = (int) Math.min(255, (rgb >> 8 & 255) * k), b = (int) Math.min(255, (rgb & 255) * k);
        return r << 16 | g << 8 | b;
    }
    private static void parapet(Gta8MeshBuilder b, double x0, double z0, double x1, double z1, double y, double h, int material, int color) {
        double t = .25;
        b.material(material).color(color);
        b.box(x0, y, z0, x1, y + h, z0 + t, ALL & ~FACE_DOWN);
        b.box(x0, y, z1 - t, x1, y + h, z1, ALL & ~FACE_DOWN);
        b.box(x0, y, z0 + t, x0 + t, y + h, z1 - t, ALL & ~FACE_DOWN);
        b.box(x1 - t, y, z0 + t, x1, y + h, z1 - t, ALL & ~FACE_DOWN);
    }
    private void awnings(Gta8MeshBuilder d, Building bd) {
        double y = CURB + bd.shopHeight - .45, depth = 1.35, drop = .7;
        d.material(CLOTH, (bd.seed & 1)).color(bd.awningColor).ao(1);
        for (int face = 0; face < 4; face++) {
            if ((bd.frontFaces & (1 << face)) == 0) continue;
            double[] e = edge(bd, face);
            double ax = e[0], az = e[1], bx = e[2], bz = e[3], nx = e[4], nz = e[5];
            double len = Math.hypot(bx - ax, bz - az);
            double ux = (bx - ax) / len, uz = (bz - az) / len;
            ax += ux * .5; az += uz * .5; bx -= ux * .5; bz -= uz * .5;
            d.quad(ax + nx * depth, y - drop, az + nz * depth, bx + nx * depth, y - drop, bz + nz * depth, bx, y, bz, ax, y, az, 0, 0, len, 0, len, 1.5, 0, 1.5);
            d.quad(bx, y, bz, bx + nx * depth, y - drop, bz + nz * depth, ax + nx * depth, y - drop, az + nz * depth, ax, y, az, len, 1.5, len, 0, 0, 0, 0, 1.5);
            d.quad(ax + nx * depth, y - drop - .25, az + nz * depth, bx + nx * depth, y - drop - .25, bz + nz * depth, bx + nx * depth, y - drop, bz + nz * depth, ax + nx * depth, y - drop, az + nz * depth, 0, 0, len, 0, len, .25, 0, .25);
        }
    }
    /** Face edge from left to right seen from outside, plus the outward normal. */
    private static double[] edge(Building bd, int face) {
        switch (face) {
            case 0: return new double[]{bd.x1, bd.z0, bd.x0, bd.z0, 0, -1};
            case 1: return new double[]{bd.x1, bd.z1, bd.x1, bd.z0, 1, 0};
            case 2: return new double[]{bd.x0, bd.z1, bd.x1, bd.z1, 0, 1};
            default: return new double[]{bd.x0, bd.z0, bd.x0, bd.z1, -1, 0};
        }
    }
    private void balconies(Gta8MeshBuilder d, Building bd, Volume upper) {
        double fh = Volume.floorHeight(upper.style), bay = 3.6;
        d.ao(1);
        for (double y = upper.y0 + fh; y < upper.y1 - 1; y += fh) {
            for (double x = bd.x0 + bay / 2; x < bd.x1 - 1; x += bay) {
                d.material(CONCRETE_WALL).color(0xE8E4DC);
                d.box(x - 1.3, y - .12, bd.z1, x + 1.3, y + .05, bd.z1 + 1.2);
                d.material(CARGLASS).color(0x405868);
                d.box(x - 1.3, y + .05, bd.z1 + 1.15, x + 1.3, y + 1.05, bd.z1 + 1.2, FACE_S | FACE_N);
                d.material(METAL, 60).color(0xE0E0DA);
                d.box(x - 1.3, y + 1.0, bd.z1 + 1.12, x + 1.3, y + 1.08, bd.z1 + 1.22);
            }
        }
    }
    private void warehouseDetails(Gta8MeshBuilder b, Gta8MeshBuilder d, Building bd) {
        Volume v = bd.volumes.get(0);
        if (bd.roof == Building.ROOF_SAWTOOTH) {
            double h = 2.6;
            for (double z = v.z0; z < v.z1 - .5; z += 6) {
                double ze = Math.min(v.z1, z + 6);
                b.material(CORRUGATED).color(0x9AA0A4);
                b.quad(v.x0, v.y1, ze, v.x1, v.y1, ze, v.x1, v.y1 + h, z, v.x0, v.y1 + h, z, 0, 0, v.x1 - v.x0, 0, v.x1 - v.x0, 6.5, 0, 6.5);
                b.material(CARGLASS).color(0x5A7080);
                b.quad(v.x1, v.y1, z, v.x0, v.y1, z, v.x0, v.y1 + h, z, v.x1, v.y1 + h, z, 0, 0, 1, 0, 1, 1, 0, 1);
                b.material(CORRUGATED).color(0x9AA0A4);
                b.tri(v.x0, v.y1, z, v.x0, v.y1, ze, v.x0, v.y1 + h, z, 0, 0, 6, 0, 0, h);
                b.tri(v.x1, v.y1, ze, v.x1, v.y1, z, v.x1, v.y1 + h, z, 0, 0, 6, 0, 6, h);
            }
        }
        // Roll-up doors on the loading side.
        boolean alongX = v.x1 - v.x0 > v.z1 - v.z0;
        d.material(CORRUGATED).color(0x6E7478).ao(1);
        if (alongX) for (double x = v.x0 + 4; x < v.x1 - 4; x += 7) d.box(x, CURB + 1.1, v.z1, x + 4, CURB + 5.4, v.z1 + .05, FACE_S);
        else for (double z = v.z0 + 4; z < v.z1 - 4; z += 7) d.box(v.x1, CURB + 1.1, z, v.x1 + .05, CURB + 5.4, z + 4, FACE_E);
        parapet(b, v.x0, v.z0, v.x1, v.z1, v.y1, .6, METAL, 0x6A6E72);
    }

    private void house(Gta8MeshBuilder b, Gta8MeshBuilder d, Building bd) {
        Volume v = bd.volumes.get(0);
        boolean garage = bd.shopHeight < 0;
        int face = bd.nameFace;
        if (garage) {
            b.material(ROOF).color(0x6A6A66);
            b.box(v.x0 - .3, v.y1, v.z0 - .3, v.x1 + .3, v.y1 + .25, v.z1 + .3);
            double[] e = edge(bd, face);
            double len = Math.hypot(e[2] - e[0], e[3] - e[1]), ux = (e[2] - e[0]) / len, uz = (e[3] - e[1]) / len;
            double ax = e[0] + ux * .6 + e[4] * .02, az = e[1] + uz * .6 + e[5] * .02, bx = e[2] - ux * .6 + e[4] * .02, bz = e[3] - uz * .6 + e[5] * .02;
            d.material(METAL, 90).color(0xE8E6E0);
            d.quad(ax, CURB, az, bx, CURB, bz, bx, CURB + 2.3, bz, ax, CURB + 2.3, az, 0, 0, len, 0, len, 2.3, 0, 2.3);
            d.color(0xC8C6C0);
            for (double y = CURB + .5; y < CURB + 2.3; y += .52) d.quad(ax, y, az, bx, y, bz, bx, y + .03, bz, ax, y + .03, az, 0, 0, 1, 0, 1, 1, 0, 1);
            return;
        }
        roof(b, bd, v);
        // Front door with a small porch light.
        double[] e = edge(bd, face);
        double len = Math.hypot(e[2] - e[0], e[3] - e[1]), ux = (e[2] - e[0]) / len, uz = (e[3] - e[1]) / len;
        double mx = (e[0] + e[2]) / 2 + e[4] * .03, mz = (e[1] + e[3]) / 2 + e[5] * .03;
        d.material(WOOD).color(0x5A3A2A);
        d.quad(mx - ux * .5, CURB, mz - uz * .5, mx + ux * .5, CURB, mz + uz * .5, mx + ux * .5, CURB + 2.15, mz + uz * .5, mx - ux * .5, CURB + 2.15, mz - uz * .5, 0, 0, 1, 0, 1, 2.1, 0, 2.1);
        d.material(LAMP).color(0xFFD8A0);
        d.box(mx + ux * .8 - .08, CURB + 2.0, mz + uz * .8 - .08, mx + ux * .8 + .08, CURB + 2.25, mz + uz * .8 + .08);
        d.material(CONCRETE).color(0xC8C4BC);
        d.box(Math.min(mx - ux * 1.2, mx + ux * 1.2 + e[4] * 1.4), CURB, Math.min(mz - uz * 1.2, mz + uz * 1.2 + e[5] * 1.4),
                Math.max(mx - ux * 1.2 + e[4] * 1.4, mx + ux * 1.2), CURB + .18, Math.max(mz - uz * 1.2 + e[5] * 1.4, mz + uz * 1.2));
        if (v.kind == Volume.SIDING) sidingWindows(d, bd, v);
    }
    private void sidingWindows(Gta8MeshBuilder d, Building bd, Volume v) {
        int floors = (int) Math.round((v.y1 - v.y0) / 3.0);
        for (int face = 0; face < 4; face++) {
            double[] e = edge(bd, face);
            double len = Math.hypot(e[2] - e[0], e[3] - e[1]), ux = (e[2] - e[0]) / len, uz = (e[3] - e[1]) / len;
            for (int f = 0; f < floors; f++) for (double u = 1.6; u < len - 1.2; u += 3.2) {
                if (face == bd.nameFace && f == 0 && Math.abs(u - len / 2) < 1.4) continue;
                double y0 = v.y0 + f * 3 + .9, y1 = y0 + 1.3;
                double ax = e[0] + ux * (u - .6) + e[4] * .04, az = e[1] + uz * (u - .6) + e[5] * .04, bx = e[0] + ux * (u + .6) + e[4] * .04, bz = e[1] + uz * (u + .6) + e[5] * .04;
                d.material(PAINT).color(0xF2F0EA);
                d.quad(ax - ux * .08, y0 - .08, az - uz * .08, bx + ux * .08, y0 - .08, bz + uz * .08, bx + ux * .08, y1 + .08, bz + uz * .08, ax - ux * .08, y1 + .08, az - uz * .08, 0, 0, 1, 0, 1, 1, 0, 1);
                d.material(CARGLASS).color(0x2A3A48);
                d.quad(ax + e[4] * .01, y0, az + e[5] * .01, bx + e[4] * .01, y0, bz + e[5] * .01, bx + e[4] * .01, y1, bz + e[5] * .01, ax + e[4] * .01, y1, az + e[5] * .01, 0, 0, 1, 0, 1, 1, 0, 1);
            }
        }
    }
    /** Gable or hip roof with overhanging eaves; UVs run along the eave and up the slope. */
    private void roof(Gta8MeshBuilder b, Building bd, Volume v) {
        double o = .45, x0 = v.x0 - o, x1 = v.x1 + o, z0 = v.z0 - o, z1 = v.z1 + o, y = v.y1;
        double pitch = Math.tan(Math.toRadians(bd.roofPitch));
        b.material(TILEROOF).color(bd.roofColor).ao(1);
        boolean ridgeX = bd.roof == Building.ROOF_GABLE_X || (bd.roof == Building.ROOF_HIP && x1 - x0 >= z1 - z0);
        double span = ridgeX ? (z1 - z0) / 2 : (x1 - x0) / 2, h = span * pitch;
        double slope = Math.hypot(span, h);
        if (bd.roof == Building.ROOF_HIP) {
            double inset = Math.min(span, ridgeX ? (x1 - x0) / 2 : (z1 - z0) / 2);
            if (ridgeX) {
                double rz = (z0 + z1) / 2, rx0 = x0 + inset, rx1 = x1 - inset;
                b.quad(x0, y, z1, x1, y, z1, rx1, y + h, rz, rx0, y + h, rz, 0, 0, x1 - x0, 0, rx1 - x0, slope, rx0 - x0, slope);
                b.quad(x1, y, z0, x0, y, z0, rx0, y + h, rz, rx1, y + h, rz, 0, 0, x1 - x0, 0, x1 - rx0, slope, x1 - rx1, slope);
                b.tri(x0, y, z0, x0, y, z1, rx0, y + h, rz, 0, 0, z1 - z0, 0, (z1 - z0) / 2, slope);
                b.tri(x1, y, z1, x1, y, z0, rx1, y + h, rz, 0, 0, z1 - z0, 0, (z1 - z0) / 2, slope);
            } else {
                double rx = (x0 + x1) / 2, rz0 = z0 + inset, rz1 = z1 - inset;
                b.quad(x1, y, z1, x1, y, z0, rx, y + h, rz0, rx, y + h, rz1, 0, 0, z1 - z0, 0, z1 - rz0, slope, z1 - rz1, slope);
                b.quad(x0, y, z0, x0, y, z1, rx, y + h, rz1, rx, y + h, rz0, 0, 0, z1 - z0, 0, rz1 - z0, slope, rz0 - z0, slope);
                b.tri(x1, y, z0, x0, y, z0, rx, y + h, rz0, 0, 0, x1 - x0, 0, (x1 - x0) / 2, slope);
                b.tri(x0, y, z1, x1, y, z1, rx, y + h, rz1, 0, 0, x1 - x0, 0, (x1 - x0) / 2, slope);
            }
        } else if (ridgeX) {
            double rz = (z0 + z1) / 2;
            b.quad(x0, y, z1, x1, y, z1, x1, y + h, rz, x0, y + h, rz, 0, 0, x1 - x0, 0, x1 - x0, slope, 0, slope);
            b.quad(x1, y, z0, x0, y, z0, x0, y + h, rz, x1, y + h, rz, 0, 0, x1 - x0, 0, x1 - x0, slope, 0, slope);
            b.material(wall(0)).color(v.color);
            b.tri(v.x0, y, v.z0, v.x0, y, v.z1, v.x0, y + h, rz, 0, 0, v.z1 - v.z0, 0, (v.z1 - v.z0) / 2, h);
            b.tri(v.x1, y, v.z1, v.x1, y, v.z0, v.x1, y + h, rz, 0, 0, v.z1 - v.z0, 0, (v.z1 - v.z0) / 2, h);
        } else {
            double rx = (x0 + x1) / 2;
            b.quad(x1, y, z1, x1, y, z0, rx, y + h, z0, rx, y + h, z1, 0, 0, z1 - z0, 0, z1 - z0, slope, 0, slope);
            b.quad(x0, y, z0, x0, y, z1, rx, y + h, z1, rx, y + h, z0, 0, 0, z1 - z0, 0, z1 - z0, slope, 0, slope);
            b.material(wall(0)).color(v.color);
            b.tri(v.x1, y, v.z0, v.x0, y, v.z0, rx, y + h, v.z0, 0, 0, v.x1 - v.x0, 0, (v.x1 - v.x0) / 2, h);
            b.tri(v.x0, y, v.z1, v.x1, y, v.z1, rx, y + h, v.z1, 0, 0, v.x1 - v.x0, 0, (v.x1 - v.x0) / 2, h);
        }
        b.material(WOOD).color(0xE8E4DA);
        b.box(x0, y - .2, z0, x1, y, z1, FACE_DOWN | SIDES);
    }

    /** Box sign on the band above the shopfront, or a rooftop neon sign for hotels. */
    private void sign(Gta8MeshBuilder d, Building bd, double top) {
        float[] rect = signFor(bd);
        double[] e = edge(bd, bd.nameFace);
        double len = Math.hypot(e[2] - e[0], e[3] - e[1]), ux = (e[2] - e[0]) / len, uz = (e[3] - e[1]) / len;
        double h, y0, out;
        boolean lit = true;
        if (bd.kind == Building.HOTEL) { h = 2.8; y0 = top + .6; out = -1.2; }
        else if (bd.kind == Building.WAREHOUSE) { h = 1.4; y0 = top - 2.4; out = .02; lit = false; }
        else if (bd.kind == Building.CIVIC) { h = 1.1; y0 = CURB + bd.shopHeight + .5; out = .05; }
        else { h = .78; y0 = CURB + bd.shopHeight - .92; out = .16; }
        double w = Math.min(len * (bd.kind == Building.HOTEL ? .85 : .72), h * rect[4]);
        h = w / rect[4];
        double mx = (e[0] + e[2]) / 2 + e[4] * out, mz = (e[1] + e[3]) / 2 + e[5] * out;
        double ax = mx - ux * w / 2, az = mz - uz * w / 2, bx = mx + ux * w / 2, bz = mz + uz * w / 2;
        d.material(SIGN, lit ? 1 : 0).color(0xFFFFFF).ao(1);
        d.quad(ax, y0, az, bx, y0, bz, bx, y0 + h, bz, ax, y0 + h, az, rect[0], rect[3], rect[2], rect[3], rect[2], rect[1], rect[0], rect[1]);
        if (bd.kind == Building.HOTEL) {
            d.quad(bx, y0, bz, ax, y0, az, ax, y0 + h, az, bx, y0 + h, bz, rect[0], rect[3], rect[2], rect[3], rect[2], rect[1], rect[0], rect[1]);
            d.material(METAL, 120).color(0x3A3E42);
            for (double t = 0; t <= 1.001; t += .25) {
                double px = ax + (bx - ax) * t, pz = az + (bz - az) * t;
                d.box(px - .06, top, pz - .06, px + .06, y0 + h, pz + .06);
            }
        } else if (out > .1) {
            d.material(METAL, 120).color(0x2A2C2E);
            double dx = e[4] * out, dz = e[5] * out;
            d.quad(ax - dx, y0 + h, az - dz, bx - dx, y0 + h, bz - dz, bx, y0 + h, bz, ax, y0 + h, az, 0, 0, 1, 0, 1, 1, 0, 1);
            d.quad(ax, y0, az, bx, y0, bz, bx - dx, y0, bz - dz, ax - dx, y0, az - dz, 0, 0, 1, 0, 1, 1, 0, 1);
            d.quad(ax - dx, y0, az - dz, ax, y0, az, ax, y0 + h, az, ax - dx, y0 + h, az - dz, 0, 0, 1, 0, 1, 1, 0, 1);
            d.quad(bx, y0, bz, bx - dx, y0, bz - dz, bx - dx, y0 + h, bz - dz, bx, y0 + h, bz, 0, 0, 1, 0, 1, 1, 0, 1);
        }
        if (bd.kind == Building.CIVIC && bd.name.contains("MEDICAL")) {
            float[] cross = atlas.get(Gta8Textures.SignAtlas.CROSS, "+");
            double s = 5, cy = top + 1;
            double cx = (e[0] + e[2]) / 2 + e[4] * -.5, cz = (e[1] + e[3]) / 2 + e[5] * -.5;
            d.material(SIGN, 1).color(0xFFFFFF);
            d.quad(cx - ux * s / 2, cy, cz - uz * s / 2, cx + ux * s / 2, cy, cz + uz * s / 2, cx + ux * s / 2, cy + s, cz + uz * s / 2, cx - ux * s / 2, cy + s, cz - uz * s / 2,
                    cross[0], cross[3], cross[2], cross[3], cross[2], cross[1], cross[0], cross[1]);
        }
    }

    // ------------------------------------------------------------------ props
    private void propsInto(Chunk c) {
        for (Prop p : c.props) {
            if (p.broken) continue;
            c.detail.push(); c.foliage.push();
            c.detail.translate(p.x, p.y, p.z); c.foliage.translate(p.x, p.y, p.z);
            c.detail.yaw(p.yaw); c.foliage.yaw(p.yaw);
            Gta8PropModels.model(c.detail, c.foliage, p, atlas);
            c.detail.pop(); c.foliage.pop();
        }
    }

    // ------------------------------------------------------------------ harbour, terrain and horizon
    private void harbour() {
        double x0 = world.gridX1() + SIDEWALK, q = Gta8World.QUAY, z0 = world.gridZ0() - SIDEWALK, z1 = 560;
        for (double x = x0; x < q - .01; x += 40) for (double z = z0; z < z1 - .01; z += 48) {
            double xe = Math.min(q, x + 40), ze = Math.min(z1, z + 48);
            Gta8MeshBuilder b = chunk((x + xe) / 2, (z + ze) / 2).main;
            b.material(CONCRETE).color(0xA8A6A0).ao(1);
            b.quad(x, CURB, ze, xe, CURB, ze, xe, CURB, z, x, CURB, z, x, ze, xe, ze, xe, z, x, z);
        }
        for (double z = z0; z < z1 - .01; z += 48) {
            double ze = Math.min(z1, z + 48);
            Gta8MeshBuilder b = chunk(q, (z + ze) / 2).main;
            b.material(CONCRETE_WALL).color(0x8A8680);
            b.box(q - .6, -10, z, q, CURB, ze, FACE_E);
            b.material(PAINT).color(0xD8B020);
            b.box(q - .6, CURB, z, q, CURB + .02, ze, FACE_UP);
        }
        for (double x = x0; x < q - .01; x += 40) {
            double xe = Math.min(q, x + 40);
            Gta8MeshBuilder b = chunk((x + xe) / 2, z1).main;
            b.material(CONCRETE_WALL).color(0x8A8680);
            b.box(x, -10, z1, xe, CURB, z1 + .6, FACE_S);
        }
    }

    private boolean explicitGround(double x, double z) {
        double gx0 = world.gridX0() - SIDEWALK, gx1 = world.gridX1() + SIDEWALK, gz0 = world.gridZ0() - SIDEWALK, gz1 = world.gridZ1() + SIDEWALK;
        if (x > gx0 + .01 && x < gx1 - .01 && z > gz0 + .01 && z < gz1 - .01) return true;
        return x > gx1 - .01 && x < Gta8World.QUAY - .01 && z > gz0 + .01 && z < 560 - .01;
    }
    private void terrain() {
        double gz1 = world.gridZ1() + SIDEWALK, gx1 = world.gridX1() + SIDEWALK;
        for (int cz = 0; cz < CHUNKS; cz++) for (int cx = 0; cx < CHUNKS; cx++) {
            double x0 = ORIGIN + cx * CHUNK, z0 = ORIGIN + cz * CHUNK;
            double centerDist = Math.max(Math.abs(x0 + CHUNK / 2), Math.abs(z0 + CHUNK / 2));
            double cell = centerDist < 900 ? 4 : 12;
            int n = (int) Math.round(CHUNK / cell);
            double[][] h = new double[n + 1][n + 1];
            boolean any = false;
            for (int j = 0; j <= n; j++) for (int i = 0; i <= n; i++) h[j][i] = world.terrain(x0 + i * cell, z0 + j * cell);
            Gta8MeshBuilder b = null;
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                double ax = x0 + i * cell, az = z0 + j * cell, bx = ax + cell, bz = az + cell;
                if (explicitGround(ax, az) && explicitGround(bx, az) && explicitGround(ax, bz) && explicitGround(bx, bz)) continue;
                if (explicitGround((ax + bx) / 2, (az + bz) / 2)) continue;
                double h00 = h[j][i], h10 = h[j][i + 1], h01 = h[j + 1][i], h11 = h[j + 1][i + 1];
                if (Math.max(Math.max(h00, h10), Math.max(h01, h11)) < Gta8World.WATER - 5) continue;
                if (b == null) { b = chunk(x0 + CHUNK / 2, z0 + CHUNK / 2).main; any = true; }
                boolean beach = (az > gz1 - 2 && ax < gx1 && Math.max(h00, h11) < 4) || Math.max(Math.max(h00, h10), Math.max(h01, h11)) < Gta8World.WATER + .6;
                b.material(beach ? SAND : TERRAIN).color(beach ? 0xD2C29C : 0x9A8E72).ao(1);
                int v00 = terrainVertex(b, ax, h00, az, cell), v10 = terrainVertex(b, bx, h10, az, cell);
                int v01 = terrainVertex(b, ax, h01, bz, cell), v11 = terrainVertex(b, bx, h11, bz, cell);
                b.quadIndices(v01, v11, v10, v00);
            }
        }
    }
    private int terrainVertex(Gta8MeshBuilder b, double x, double y, double z, double cell) {
        double e = Math.max(1, cell * .5);
        double nx = world.terrain(x - e, z) - world.terrain(x + e, z), nz = world.terrain(x, z - e) - world.terrain(x, z + e);
        return b.vertex(x, y, z, nx, 2 * e, nz, x, z);
    }
    /** Distant terrain beyond the chunked map, continuous with it, drawn at every distance. */
    private void horizon() {
        horizon.material(TERRAIN).color(0x8A8068).ao(1);
        double edge = ORIGIN + CHUNKS * CHUNK, extent = 9600, cell = 96;
        int n = (int) Math.round(extent * 2 / cell);
        double[][] h = new double[n + 1][n + 1];
        for (int j = 0; j <= n; j++) for (int i = 0; i <= n; i++) h[j][i] = world.terrain(-extent + i * cell, -extent + j * cell);
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double ax = -extent + i * cell, az = -extent + j * cell, bx = ax + cell, bz = az + cell;
            if (ax >= ORIGIN - .01 && bx <= edge + .01 && az >= ORIGIN - .01 && bz <= edge + .01) continue;
            double h00 = h[j][i], h10 = h[j][i + 1], h01 = h[j + 1][i], h11 = h[j + 1][i + 1];
            if (Math.max(Math.max(h00, h10), Math.max(h01, h11)) < Gta8World.WATER - 3) continue;
            int v00 = terrainVertex(horizon, ax, h00, az, cell), v10 = terrainVertex(horizon, bx, h10, az, cell);
            int v01 = terrainVertex(horizon, ax, h01, bz, cell), v11 = terrainVertex(horizon, bx, h11, bz, cell);
            horizon.quadIndices(v01, v11, v10, v00);
        }
    }

    /** Deterministic, test-friendly count of all vertices built (before upload). */
    int vertexCount() {
        int n = horizon.vertices;
        for (Chunk c : chunks) if (c != null) n += c.main.vertices + c.detail.vertices + c.foliage.vertices;
        return n;
    }
    static Random random(long seed) { return new Random(seed); }
}
