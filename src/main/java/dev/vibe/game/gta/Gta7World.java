package dev.vibe.game.gta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** A seeded, fully modelled city. Visual meshes and collision use the same building geometry. */
public final class Gta7World {
    public static final int BLOCK = 36, BLOCKS = 5, SIZE = BLOCK * BLOCKS;
    public static final int MIN = -180, MAX = 360, SPAN = MAX - MIN;
    private static final int CELL = 6, CELLS = SPAN / CELL + 1;
    public final List<Chunk> chunks = new ArrayList<Chunk>();
    public final List<Building> buildings = new ArrayList<Building>();
    public final List<Prop> props = new ArrayList<Prop>();
    public final List<double[]> sidewalks = new ArrayList<double[]>();
    private final List<Gta7Bounds>[] collision;
    private final List<Relief>[] terrain;
    final List<Relief> reliefs=new ArrayList<Relief>();
    private final boolean[] walkable = new boolean[SPAN * SPAN];
    public final List<Window> windows = new ArrayList<Window>();
    private final List<Gta7Bounds> solids=new ArrayList<Gta7Bounds>();
    public final List<Prop> animatedProps=new ArrayList<Prop>(),glowingProps=new ArrayList<Prop>();
    private int rayGeneration;
    public int lastRayCells,lastRayCandidates;
    private final Gta7SearchHeap searchHeap=new Gta7SearchHeap();
    public final List<Ladder> ladders = new ArrayList<Ladder>();
    public final List<Elevator> elevators = new ArrayList<Elevator>();
    public final List<Spawn> residents = new ArrayList<Spawn>();
    public final List<Landmark> landmarks = new ArrayList<Landmark>();
    private final double[] routeScores=new double[SPAN*SPAN];
    private final int[] routeParents=new int[SPAN*SPAN], routeSeen=new int[SPAN*SPAN], routeClosed=new int[SPAN*SPAN];
    private int routeGeneration;
    public int lastPathExpanded;
    public final List<Light> lights=new ArrayList<Light>();
    private Chunk active;
    private final Random random = new Random(0x71A7C17L);

    @SuppressWarnings("unchecked") public Gta7World() {
        collision = (List<Gta7Bounds>[]) new List<?>[CELLS * CELLS];
        terrain=(List<Relief>[])new List<?>[CELLS*CELLS];
        for (int i = 0; i < collision.length; i++){collision[i] = new ArrayList<Gta7Bounds>();terrain[i]=new ArrayList<Relief>();}
        active = new Chunk(SIZE / 2.0, SIZE / 2.0);
        chunks.add(active);
        roads();
        for (int z = 0; z < BLOCKS; z++) for (int x = 0; x < BLOCKS; x++) block(x, z);
        Gta7Regions.build(this);
        Gta7Details.build(this);
        for(Window window:windows) {
            Gta7Bounds b=window.bounds;b.window=window;
            for(int iz=cell(b.z0);iz<=cell(b.z1);iz++)for(int ix=cell(b.x0);ix<=cell(b.x1);ix++)collision[iz*CELLS+ix].add(b);
        }
        // One-metre navigation cells fit the two-metre open entrances, including furnished interiors.
        for (int z = 0; z < SPAN; z++) for (int x = 0; x < SPAN; x++)
            walkable[z * SPAN + x] = !blocked(x + MIN + .5, .16, z + MIN + .5, .28, 1.7);
        organizeChunks();
        for(Prop prop:props){
            if(prop.type.equals("signal")||prop.type.equals("fountain")||prop.type.equals("wisp")||prop.type.equals("windmill"))animatedProps.add(prop);
            if(prop.type.equals("firefly")||prop.type.equals("wisp")||prop.type.equals("campfire"))glowingProps.add(prop);
        }
    }

    private void roads() {
        box(-18, -.3, -18, SIZE + 18, -.04, SIZE + 18, 0x526454, false);
        box(-4.5, -.03, -4.5, SIZE + 4.5, .002, SIZE + 4.5, 0x343C44, false);
        for (int i = 0; i <= BLOCKS; i++) {
            double p = i * BLOCK;
            for (int t = 6; t < SIZE; t += 6) {
                if (t % BLOCK < 6 || t % BLOCK > 30) continue;
                box(p - .08, .011, t, p + .08, .014, t + 2.8, 0xDEC48C, false);
                box(t, .011, p - .08, t + 2.8, .014, p + .08, 0xDEC48C, false);
            }
            for (int j = 0; j <= BLOCKS; j++) {
                double q = j * BLOCK;
                for (int stripe = -3; stripe <= 3; stripe++) {
                    box(p + stripe * 1.05, .012, q + 4.8, p + stripe * 1.05 + .55, .016, q + 6.8, 0xBDC1B7, false);
                    box(p + 4.8, .012, q + stripe * 1.05, p + 6.8, .016, q + stripe * 1.05 + .55, 0xBDC1B7, false);
                }
            }
        }
    }

    private void block(int bx, int bz) {
        double x = bx * BLOCK, z = bz * BLOCK;
        active = new Chunk(x + 18, z + 18);
        chunks.add(active);
        box(x + 4.5, 0, z + 4.5, x + 31.5, .08, z + 31.5, 0xB6B4A6, false);
        // Paving seams live in the mipmapped surface texture, avoiding coplanar strips.
        for (double[] corner : new double[][]{{6,6},{30,6},{30,30},{6,30}})
            sidewalks.add(new double[]{x + corner[0], z + corner[1]});
        boolean park = bx == 2 && bz == 2 || (bx + bz * 3) % 9 == 2;
        if (park) park(x, z);
        else {
            building(x + 8, z + 8, bx + bz * BLOCKS, false);
            building(x + 21, z + 8, bx * 7 + bz + 3, true);
            building(x + 8, z + 21, bx + bz * 4 + 1, true);
            building(x + 21, z + 21, bx * 3 + bz * 7 + 2, false);
            tree(x + 19, z + 11, 3.4);
            tree(x + 19, z + 27, 3.1);
            bench(x + 17, z + 18);
            box(x + 18, .1, z + 15, x + 18.6, 1.1, z + 15.7, 0x3D6663, true);
        }
        lamp(x + 5.2, z + 12);
        lamp(x + 30.8, z + 25);
        props.add(new Prop("signal", x + 5, z + 5, (bx + bz) % 2, 0));
        box(x + 4.9, .08, z + 4.9, x + 5.1, 3.6, z + 5.1, 0x3A444A, true);
        box(x + 30, .08, z + 10, x + 30.45, .75, z + 10.45, 0xC95845, true);
        box(x + 29.87, .5, z + 10.13, x + 30.58, .66, z + 10.32, 0xE88758, false);
        // Storm drains, curb parking lines, utility cabinets and flower planters.
        box(x + 3.8, .01, z + 15, x + 4.3, .025, z + 16, 0x161F26, false);
        for (int i = 0; i < 6; i++) box(x + 3.83, .028, z + 15.05 + i * .15, x + 4.28, .03, z + 15.1 + i * .15, 0x647077, false);
        box(x + 30, .08, z + 19, x + 30.6, 1.2, z + 20, 0x7C8984, true);
        for (int i = 0; i < 2; i++) {
            double pz = z + 17 + i * 6;
            box(x + 1.3, .01, pz, x + 4.3, .02, pz + .08, 0xAFB9B4, false);
        }
        if ((bx + bz * 2) % 4 == 0) {
            // Sheltered bus stop with a timetable and a bench, leaving the pavement route open.
            box(x + 4.7, .08, z + 17, x + 4.85, 2.6, z + 17.15, 0x455F60, true);
            box(x + 4.7, .08, z + 21, x + 4.85, 2.6, z + 21.15, 0x455F60, true);
            box(x + 4.65, 2.6, z + 16.85, x + 6.9, 2.75, z + 21.3, 0x547878, false);
            box(x + 4.65, .12, z + 17.2, x + 4.78, 2.55, z + 21, 0x759B9D, true);
            box(x + 4.79, 1.2, z + 18, x + 4.81, 2.05, z + 18.7, 0xE8D5AA, false);
            for (int line = 0; line < 6; line++) box(x + 4.815, 1.28 + line * .1, z + 18.08, x + 4.82, 1.32 + line * .1, z + 18.6, 0x5B7874, false);
            box(x + 4.9, .08, z + 19, x + 5.55, .55, z + 20.7, 0x997E59, true);
        }
    }

    void building(double x, double z, int style, boolean home) {
        int[] palette = {0xB5A08A,0xC5C1B2,0x976C5D,0x839494,0xA6ABB6,0xC4AE84};
        String[] stores = {"NORTHSIDE COFFEE","VIBE RECORDS","CORNER MARKET","CITY BOOKS","STUDIO 07","LATE NIGHT DELI"};
        house(x,z,2 + style % 3,home ? "APARTMENTS " + (style+1) : stores[style%6],palette[style%6]);
        if(home) {
            box(x+.6,.14,z+5.5,x+2.7,.65,z+7.9,0x665A61,true);
            box(x+.7,.65,z+5.6,x+2.6,.8,z+7.8,0xAD9586,false);
            box(x+.8,.8,z+7.1,x+2.5,.92,z+7.7,0xE3DAC2,false);
            table(x+2,z+3,false);
        } else { table(x+2,z+2,true); table(x+2,z+5,true); }
    }

    /** Real apertures and open stairwells share exactly the same visual and collision geometry. */
    void house(double x,double z,int floors,String name,int wall) {
        double h=.14+floors*3.2;
        buildings.add(new Building(x,z,9,9,h,name));
        box(x,.08,z,x+9,.14,z+9,0xB4AA91,true);
        for(int floor=0;floor<floors;floor++) {
            double y=.14+floor*3.2;
            facade(x,y,z,9,wall,floor==0,false);
            facade(x,y,z+8.75,9,wall,false,false);
            facade(x,y,z,9,wall,false,true);
            facade(x+8.75,y,z,9,wall,false,true);
            if(floor>0) {
                box(x+.25,y-.18,z+.25,x+8.75,y,z+3.1,0xC8BEA8,true);
                box(x+.25,y-.18,z+3.1,x+5.6,y,z+8.75,0xC8BEA8,true);
                box(x+.7,y,z+6.5,x+2.8,y+.7,z+8,0x8E7866,true);
                box(x+.7,y+.7,z+6.5,x+2.8,y+.83,z+8,0xE0D3B8,false);
                box(x+3.2,y+.005,z+3.3,x+5.4,y+.015,z+6,0x597F83,false);
            }
            if(floor<floors-1) {
                for(int step=0;step<16;step++) {
                    double sz=z+7.9-step*.3375;
                    box(x+6.4,y,sz-.3375,x+8.6,y+(step+1)*.2,sz,0xA8A391,true);
                }
                ladders.add(new Ladder(x+5.95,z+8.2,y,y+3.2));
                for(int rung=0;rung<11;rung++) box(x+5.7,y+rung*.3,z+8.42,x+6.3,y+rung*.3+.06,z+8.5,0x6E7778,false);
            }
        }
        box(x-.12,h,z-.12,x+9.12,h+.22,z+9.12,0x57666F,true);
        box(x+2.2,2.72,z-.2,x+6.8,3.03,z-.1,0x153139,false);
        box(x+3.37,.14,z-.09,x+3.5,2.65,z+.3,0x485E61,true);
        box(x+5.5,.14,z-.09,x+5.63,2.65,z+.3,0x485E61,true);
        landmarks.add(new Landmark(name,x+4.5,2.98,z-.225,.025));
    }

    void facade(double x,double y,double z,double length,int color,boolean door,boolean side) {
        if(door) {
            panel(x,y,z,0,0,3.5,.9,color,side); panel(x,y,z,5.5,0,length,.9,color,side);
            panel(x,y,z,0,2.4,3.5,3.2,color,side); panel(x,y,z,5.5,2.4,length,3.2,color,side);
            panel(x,y,z,3.5,2.65,5.5,3.2,color,side);
        } else {panel(x,y,z,0,0,length,.9,color,side);panel(x,y,z,0,2.4,length,3.2,color,side);}
        panel(x,y,z,0,.9,.8,2.4,color,side);
        panel(x,y,z,length- .8,.9,length,2.4,color,side);
        if(door) {panel(x,y,z,2.6,.9,3.5,2.4,color,side);panel(x,y,z,5.5,.9,length-2.6,2.4,color,side);}
        else panel(x,y,z,2.6,.9,length-2.6,2.4,color,side);
        for(double a:new double[]{.8,length-2.6}) {
            Gta7Bounds glass=side?new Gta7Bounds(x+.105,y+.9,z+a,x+.145,y+2.4,z+a+1.8)
                    :new Gta7Bounds(x+a,y+.9,z+.105,x+a+1.8,y+2.4,z+.145);
            windows.add(new Window(glass));
            panel(x,y,z,a-.06,.84,a+1.86,.9,0xD8D5C3,side);
            panel(x,y,z,a-.06,2.4,a+1.86,2.46,0xD8D5C3,side);
        }
    }
    private void panel(double x,double y,double z,double a,double b,double aa,double bb,int color,boolean side) {
        if(aa<=a)return;
        if(side)box(x,y+b,z+a,x+.25,y+bb,z+aa,color,true);
        else box(x+a,y+b,z,x+aa,y+bb,z+.25,color,true);
    }
    void chunk(double x,double z) { active=new Chunk(x,z);chunks.add(active); }

    private void kitchen(double x, double z) {
        box(x, .12, z, x + 2, 1.02, z + 1.25, 0xD1C0A1, true);
        box(x - .06, 1.02, z - .05, x + 2.06, 1.12, z + 1.3, 0x444F55, false);
        box(x + .2, 1.12, z + .2, x + .9, 1.14, z + .9, 0x93AAA9, false);
        box(x + 1.35, 1.12, z + .45, x + 1.6, 1.65, z + .7, 0xB3B9AA, false);
        for (int i = 0; i < 3; i++) box(x + .15 + i * .65, .65, z - .025, x + .55 + i * .65, .69, z - .01, 0x7B827F, false);
    }

    private void table(double x, double z, boolean seats) {
        box(x - .58, .76, z - .58, x + .58, .85, z + .58, 0xB19B75, true);
        box(x - .1, .12, z - .1, x + .1, .76, z + .1, 0x3F4D50, true);
        box(x + .1, .85, z + .1, x + .25, 1.02, z + .25, 0xE9DBBA, false);
        if (seats) for (int direction : new int[]{-1, 1}) {
            double cz = z + direction * 1.05;
            box(x - .27, .12, cz - .25, x + .27, .5, cz + .25, 0x687B76, true);
            box(x - .27, .5, cz + direction * .2 - .04, x + .27, .98, cz + direction * .2 + .04, 0x687B76, true);
        }
    }

    private void park(double x, double z) {
        box(x + 7, .09, z + 7, x + 29, .12, z + 29, 0x648455, false);
        box(x + 16.5, .122, z + 5, x + 19.5, .126, z + 31, 0xC7BB9D, false);
        box(x + 5, .122, z + 16.5, x + 31, .126, z + 19.5, 0xC7BB9D, false);
        for (int tx = 10; tx <= 26; tx += 8) for (int tz = 10; tz <= 26; tz += 8)
            if (tx != 18 && tz != 18) tree(x + tx, z + tz, 4 + random.nextDouble());
        box(x + 15, .12, z + 15, x + 21, .6, z + 21, 0xADB5AF, true);
        box(x + 15.3, .6, z + 15.3, x + 20.7, .64, z + 20.7, 0x4E9CAB, false);
        box(x + 17.5, .64, z + 17.5, x + 18.5, 1.9, z + 18.5, 0xCFCCB4, true);
        props.add(new Prop("fountain", x + 18, z + 18, 1, 0));
        bench(x + 12, z + 15);
        bench(x + 24, z + 21);
        for (int i = 0; i < 10; i++) {
            double fx = x + 8 + i * 2;
            box(fx, .12, z + 28, fx + .4, .35, z + 28.4, (i % 2 == 0) ? 0xDCAB6E : 0xB7788F, false);
        }
    }

    void tree(double x, double z, double h) {
        box(x - .18, .08, z - .18, x + .18, h, z + .18, 0x77604B, true);
        props.add(new Prop("tree", x, z, h, random.nextInt(3)));
        box(x - .9, .085, z - .9, x + .9, .14, z + .9, 0x7B8966, false);
    }
    private void bench(double x, double z) {
        for (int i = 0; i < 4; i++) box(x - 1, .5, z - .3 + i * .18, x + 1, .58, z - .18 + i * .18, 0x987B53, true);
        for (int i = 0; i < 3; i++) box(x - 1, .7 + i * .16, z + .35, x + 1, .8 + i * .16, z + .45, 0x987B53, true);
        box(x - .8, .08, z - .25, x - .68, .5, z + .4, 0x354C4C, true);
        box(x + .68, .08, z - .25, x + .8, .5, z + .4, 0x354C4C, true);
    }
    void lamp(double x, double z) {
        box(x - .08, .08, z - .08, x + .08, 4.6, z + .08, 0x3E4C51, true);
        box(x - .1, 4.4, z - .1, x + 1.1, 4.55, z + .1, 0x3E4C51, false);
        box(x + .55, 4.3, z - .2, x + 1.2, 4.45, z + .2, 0xFFF0C1, false);
        lights.add(new Light(x+.85,4.28,z,5,0xFFCE86));
    }

    void box(double x, double y, double z, double xx, double yy, double zz, int rgb, boolean solid) {
        Gta7Bounds bounds = new Gta7Bounds(x, y, z, xx, yy, zz);
        active.boxes.add(new Box(bounds, rgb));
        if (solid) collider(bounds);
    }
    void collider(Gta7Bounds bounds){
        solids.add(bounds);
        for(int iz=cell(bounds.z0);iz<=cell(bounds.z1);iz++)for(int ix=cell(bounds.x0);ix<=cell(bounds.x1);ix++)collision[iz*CELLS+ix].add(bounds);
    }
    void relief(double x,double y,double z,double rx,double ry,double rz){
        Relief relief=new Relief(x,y,z,rx,ry,rz);reliefs.add(relief);
        for(int iz=cell(z-rz);iz<=cell(z+rz);iz++)for(int ix=cell(x-rx);ix<=cell(x+rx);ix++)terrain[iz*CELLS+ix].add(relief);
    }
    private static int cell(double coordinate) { return Math.max(0, Math.min(CELLS - 1, (int)Math.floor((coordinate - MIN) / CELL))); }
    public double groundHeight(double x,double z) {
        double bx=x-Math.floor(x/BLOCK)*BLOCK,bz=z-Math.floor(z/BLOCK)*BLOCK;
        if(x<0||z<0||x>SIZE||z>SIZE||bx<4.5||bx>31.5||bz<4.5||bz>31.5)return 0;
        for(Building building:buildings)if(building.contains(x,z))return .12;
        int ix=(int)(x/BLOCK),iz=(int)(z/BLOCK);
        boolean park=ix==2&&iz==2||(ix+iz*3)%9==2;
        return park&&bx>=7&&bx<=29&&bz>=7&&bz<=29?.126:.08;
    }
    public boolean blocked(double x, double y, double z, double radius, double height) {
        if (x < MIN + radius || z < MIN + radius || x > MAX - radius || z > MAX - radius) return true;
        for(int iz=cell(z-radius);iz<=cell(z+radius);iz++)for(int ix=cell(x-radius);ix<=cell(x+radius);ix++)
            for(Relief relief:terrain[iz*CELLS+ix])if(y+.00001<relief.height(x,z,radius))return true;
        for (int iz = cell(z - radius); iz <= cell(z + radius); iz++) for (int ix = cell(x - radius); ix <= cell(x + radius); ix++)
            for (Gta7Bounds bounds : collision[iz * CELLS + ix]) if ((bounds.window==null||!bounds.window.broken)&&bounds.intersects(x, y, z, radius, height)) return true;
        return false;
    }
    public double support(double x,double z,double ceiling,double radius) {
        double result=.14;
        for(int iz=cell(z-radius);iz<=cell(z+radius);iz++)for(int ix=cell(x-radius);ix<=cell(x+radius);ix++)
            for(Relief relief:terrain[iz*CELLS+ix]){double top=relief.height(x,z,radius);if(top<=ceiling+.000001)result=Math.max(result,top);}
        for(int iz=cell(z-radius);iz<=cell(z+radius);iz++)for(int ix=cell(x-radius);ix<=cell(x+radius);ix++)
            for(Gta7Bounds b:collision[iz*CELLS+ix])if(b.window==null&&b.y1<=ceiling+.000001&&b.y1>result&&x+radius>b.x0&&x-radius<b.x1&&z+radius>b.z0&&z-radius<b.z1)result=b.y1;
        return result;
    }
    public double ceiling(double x,double z,double feet,double radius,double height,double proposedY) {
        double result=proposedY;
        for(int iz=cell(z-radius);iz<=cell(z+radius);iz++)for(int ix=cell(x-radius);ix<=cell(x+radius);ix++)
            for(Gta7Bounds b:collision[iz*CELLS+ix])if(b.window==null&&b.y0>=feet+height-.000001&&b.y0<result+height&&x+radius>b.x0&&x-radius<b.x1&&z+radius>b.z0&&z-radius<b.z1)result=b.y0-height;
        return result;
    }
    public synchronized double ray(double x,double y,double z,double dx,double dy,double dz,double range){
        lastRayCells=lastRayCandidates=0;
        if(range<=0)return 0;
        double distance=dy < -1e-9?Math.min(range,Math.max(0,-y/dy)):range;
        if(++rayGeneration==Integer.MAX_VALUE){for(Gta7Bounds b:solids)b.rayStamp=0;for(Relief r:reliefs)r.rayStamp=0;rayGeneration=1;}
        int cx=(int)Math.floor((x-MIN)/CELL),cz=(int)Math.floor((z-MIN)/CELL);
        if(cx<0||cz<0||cx>=CELLS||cz>=CELLS){
            // External callers may start outside the playable grid; preserve exact slab semantics.
            for(Gta7Bounds box:solids)distance=Math.min(distance,box.ray(x,y,z,dx,dy,dz,distance));
            for(Relief r:reliefs)distance=Math.min(distance,r.ray(x,y,z,dx,dy,dz,distance));
            return distance;
        }
        int sx=dx>0?1:dx<0?-1:0,sz=dz>0?1:dz<0?-1:0;
        double tx=sx==0?Double.POSITIVE_INFINITY:(MIN+(cx+(sx>0?1:0))*CELL-x)/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:(MIN+(cz+(sz>0?1:0))*CELL-z)/dz;
        double stepX=sx==0?Double.POSITIVE_INFINITY:CELL/Math.abs(dx),stepZ=sz==0?Double.POSITIVE_INFINITY:CELL/Math.abs(dz),entry=0;
        while(cx>=0&&cz>=0&&cx<CELLS&&cz<CELLS&&entry<=distance){
            lastRayCells++;int cell=cz*CELLS+cx;
            for(Gta7Bounds box:collision[cell])if(box.window==null&&box.rayStamp!=rayGeneration){
                box.rayStamp=rayGeneration;lastRayCandidates++;distance=Math.min(distance,box.ray(x,y,z,dx,dy,dz,distance));
            }
            for(Relief r:terrain[cell])if(r.rayStamp!=rayGeneration){r.rayStamp=rayGeneration;distance=Math.min(distance,r.ray(x,y,z,dx,dy,dz,distance));}
            if(tx==Double.POSITIVE_INFINITY&&tz==Double.POSITIVE_INFINITY)break;
            if(tx<=tz){entry=tx;tx+=stepX;cx+=sx;}else{entry=tz;tz+=stepZ;cz+=sz;}
        }
        return distance;
    }

    /** A* supplies police with routes through streets and doorways rather than wall sliding. */
    public List<double[]> path(double fromX,double fromZ,double toX,double toZ){return path(fromX,fromZ,toX,toZ,walkable.length*2);}
    synchronized List<double[]> path(double fromX, double fromZ, double toX, double toZ,int budget) {
        lastPathExpanded=0;
        int start = nearest(fromX, fromZ), goal = nearest(toX, toZ);
        if (start < 0 || goal < 0 || start == goal) return Collections.emptyList();
        if(++routeGeneration==Integer.MAX_VALUE){Arrays.fill(routeSeen,0);Arrays.fill(routeClosed,0);routeGeneration=1;}
        final int generation=routeGeneration;
        double[] scores=routeScores;int[] parents=routeParents;
        Gta7SearchHeap queue=searchHeap;queue.clear();
        routeSeen[start]=generation;parents[start]=-1; scores[start] = 0; queue.add(start, heuristic(start, goal));
        int visited=0,limit=(int)Math.min(budget,Math.max(16000,Math.pow(heuristic(start,goal),2)*3));
        while (!queue.isEmpty() && visited++<limit) {
            int current = queue.poll();
            if (routeClosed[current]==generation) continue;
            lastPathExpanded++;
            if (current == goal) {
                List<double[]> route = new ArrayList<double[]>();
                for (int p = goal; p != start && p >= 0; p = parents[p]) route.add(new double[]{p % SPAN + MIN + .5, p / SPAN + MIN + .5});
                Collections.reverse(route); return route;
            }
            routeClosed[current]=generation;
            int cx = current % SPAN, cz = current / SPAN;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0 || cx + dx < 0 || cz + dz < 0 || cx + dx >= SPAN || cz + dz >= SPAN) continue;
                int next = (cz + dz) * SPAN + cx + dx;
                if (!walkable[next] || routeClosed[next]==generation) continue;
                if (dx != 0 && dz != 0 && (!walkable[cz * SPAN + cx + dx] || !walkable[(cz + dz) * SPAN + cx])) continue;
                double score = scores[current] + (dx != 0 && dz != 0 ? 1.41421356 : 1);
                if (routeSeen[next]!=generation||score < scores[next]) { routeSeen[next]=generation;scores[next] = score; parents[next] = current; queue.add(next, score + heuristic(next, goal)); }
            }
        }
        return Collections.emptyList();
    }
    private int nearest(double x, double z) {
        int cx = Math.max(0, Math.min(SPAN - 1, (int)Math.floor(x-MIN))), cz = Math.max(0, Math.min(SPAN - 1, (int)Math.floor(z-MIN)));
        for (int radius = 0; radius <= 3; radius++) for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            int ix = cx + dx, iz = cz + dz;
            if (ix >= 0 && iz >= 0 && ix < SPAN && iz < SPAN && walkable[iz * SPAN + ix]) return iz * SPAN + ix;
        }
        return -1;
    }
    private static double heuristic(int a,int b) {
        int dx=Math.abs(a%SPAN-b%SPAN),dz=Math.abs(a/SPAN-b/SPAN);
        // Slightly weighted octile A* bounds work on the much larger map.
        return 1.15*(Math.max(dx,dz)+.41421356*Math.min(dx,dz));
    }
    public void resetWindows() { for(Window window:windows) window.broken=false; }
    public int shatter(double x,double y,double z,double dx,double dy,double dz,double limit) {
        int count=0;
        for(Window window:windows) if(!window.broken && window.bounds.ray(x,y,z,dx,dy,dz,limit)<limit) {window.broken=true;count++;}
        return count;
    }
    public Ladder ladderAt(double x,double y,double z) {
        for(Ladder ladder:ladders)if(Math.abs(ladder.x-x)<.7&&Math.abs(ladder.z-z)<.65&&y>=ladder.bottom-.001&&y<=ladder.top+.001)return ladder;
        return null;
    }
    public Elevator elevatorAt(double x,double z) {
        for(Elevator elevator:elevators)if(Math.abs(elevator.x-x)<1.3&&Math.abs(elevator.z-z)<1.3)return elevator;
        return null;
    }
    public static final class Window { public final Gta7Bounds bounds; public boolean broken; Window(Gta7Bounds b){bounds=b;} }
    public static final class Ladder {
        public final double x,z,bottom,top;
        Ladder(double x,double z,double bottom,double top){this.x=x;this.z=z;this.bottom=bottom;this.top=top;}
    }
    public static final class Elevator {
        public final double x,z; public final int floors;
        Elevator(double x,double z,int floors){this.x=x;this.z=z;this.floors=floors;}
    }
    public static final class Spawn {
        public final double x,z; public final Gta7Game.Kind kind;
        Spawn(double x,double z,Gta7Game.Kind kind){this.x=x;this.z=z;this.kind=kind;}
    }
    public static final class Landmark {
        public final String name; public final double x,y,z,scale;
        Landmark(String name,double x,double y,double z,double scale){this.name=name;this.x=x;this.y=y;this.z=z;this.scale=scale;}
    }
    /** Each prop belongs to exactly one spatial mesh; bounds include tall or wide geometry. */
    private void organizeChunks() {
        java.util.Map<String,Chunk> groups=new java.util.LinkedHashMap<String,Chunk>();
        for(Chunk old:chunks)for(Box box:old.boxes) {
            Gta7Bounds b=box.bounds;
            Chunk chunk=renderChunk(groups,(b.x0+b.x1)/2,(b.y0+b.y1)/2,(b.z0+b.z1)/2);
            chunk.boxes.add(box);chunk.include(b.x0,b.y0,b.z0,b.x1,b.y1,b.z1);
        }
        for(Prop prop:props) {
            Chunk chunk=renderChunk(groups,prop.x,prop.size,prop.z);chunk.props.add(prop);
            double extent=Math.max(8,prop.size);chunk.include(prop.x-extent,0,prop.z-extent,prop.x+extent,prop.size+8,prop.z+extent);
        }
        chunks.clear();chunks.addAll(groups.values());
        for(Chunk c:chunks)c.renderRadius=.5*Math.sqrt(Math.pow(c.x1-c.x0,2)+Math.pow(c.y1-c.y0,2)+Math.pow(c.z1-c.z0,2));
    }
    private Chunk renderChunk(java.util.Map<String,Chunk> groups,double x,double y,double z) {
        int ix=(int)Math.floor(x/36),iy=(int)Math.floor(y/32),iz=(int)Math.floor(z/36);
        String key=ix+":"+iy+":"+iz;Chunk chunk=groups.get(key);
        if(chunk==null){chunk=new Chunk(ix*36+18,iz*36+18);groups.put(key,chunk);}return chunk;
    }
    static final class Relief {
        final double x,y,z,rx,ry,rz;int rayStamp;
        Relief(double x,double y,double z,double rx,double ry,double rz){this.x=x;this.y=y;this.z=z;this.rx=rx;this.ry=ry;this.rz=rz;}
        double height(double px,double pz,double radius){
            double dx=Math.max(0,Math.abs(px-x)-radius)/rx,dz=Math.max(0,Math.abs(pz-z)-radius)/rz,q=1-dx*dx-dz*dz;
            return q>0?y+Math.sqrt(q)*ry:0;
        }
        double ray(double px,double py,double pz,double dx,double dy,double dz,double limit){
            if(Math.abs(px-x)>rx+limit||Math.abs(pz-z)>rz+limit)return limit;
            px=(px-x)/rx;py=(py-y)/ry;pz=(pz-z)/rz;dx/=rx;dy/=ry;dz/=rz;
            double a=dx*dx+dy*dy+dz*dz,b=px*dx+py*dy+pz*dz,c=px*px+py*py+pz*pz-1,d=b*b-a*c;
            if(c<0)return 0;if(a<1e-12||d<0)return limit;
            double t=(-b-Math.sqrt(d))/a;return t>=0&&t<limit?t:limit;
        }
    }
    public static final class Light {
        public final double x,y,z,radius;public final int color;
        Light(double x,double y,double z,double radius,int color){this.x=x;this.y=y;this.z=z;this.radius=radius;this.color=color;}
    }
    public static final class Chunk {
        public final double x, z;
        public final List<Box> boxes = new ArrayList<Box>();
        public final List<Prop> props=new ArrayList<Prop>();
        double renderRadius;
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
        void include(double x,double y,double z,double xx,double yy,double zz){x0=Math.min(x0,x);y0=Math.min(y0,y);z0=Math.min(z0,z);x1=Math.max(x1,xx);y1=Math.max(y1,yy);z1=Math.max(z1,zz);}
        public double distanceSquared(double x,double y,double z){double dx=Math.max(0,Math.max(x0-x,x-x1)),dy=Math.max(0,Math.max(y0-y,y-y1)),dz=Math.max(0,Math.max(z0-z,z-z1));return dx*dx+dy*dy+dz*dz;}
        Chunk(double x, double z) { this.x = x; this.z = z; }
    }
    public static final class Box {
        public final Gta7Bounds bounds; public final int color;
        Box(Gta7Bounds bounds,int color){this.bounds=bounds;this.color=color;}
    }
    public static final class Prop {
        public final String type; public final double x, z, size; public final int variant;
        Prop(String type, double x, double z, double size, int variant) { this.type = type; this.x = x; this.z = z; this.size = size; this.variant = variant; }
    }
    public static final class Building {
        public final double x, z, width, depth, height,doorX,doorZ; public final String name;
        Building(double x, double z, double width, double depth, double height, String name) {
            this(x,z,width,depth,height,name,x+4.5,z);
        }
        Building(double x,double z,double width,double depth,double height,String name,double doorX,double doorZ){
            this.x=x;this.z=z;this.width=width;this.depth=depth;this.height=height;this.name=name;this.doorX=doorX;this.doorZ=doorZ;
        }
        public boolean contains(double px, double pz) { return px > x && px < x + width && pz > z && pz < z + depth; }
    }
}
