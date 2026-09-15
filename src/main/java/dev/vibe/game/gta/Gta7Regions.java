package dev.vibe.game.gta;

import java.util.Random;
import static dev.vibe.game.gta.Gta7Game.Kind.*;

/** The eight adjoining districts use native geometry and deterministic resident populations. */
public final class Gta7Regions {
    public enum Region {
        EMOJI("Emoji Dome", -1,-1,0xB4B392), SNOW("Arctic villages",0,-1,0xDAE9ED),
        MIRAGE("Mirage",1,-1,0xD1B689), FOREST("Enchanted forest",-1,0,0x3D6458),
        CITY("Northside",0,0,0x789181), GOBLINS("Goblin Hollow",1,0,0x6C7850),
        CANDY("Candy land",-1,1,0xDFA5C7), DESERT("Dustwater frontier",0,1,0xD1AF72),
        IMPERIAL("Imperial forest",1,1,0x52664D);
        public final String title; public final int dx,dz,color;
        Region(String title,int dx,int dz,int color){this.title=title;this.dx=dx;this.dz=dz;this.color=color;}
        public double x(){return 90+dx*180;} public double z(){return 90+dz*180;}
    }
    public static Region at(double x,double z) {
        int dx=x<0?-1:x>=180?1:0,dz=z<0?-1:z>=180?1:0;
        for(Region region:Region.values())if(region.dx==dx&&region.dz==dz)return region;
        return Region.CITY;
    }
    private Gta7Regions(){ }
    static void build(Gta7World w) {
        Random random=new Random(700891);
        for(Region region:Region.values()) {
            if(region==Region.CITY)continue;
            double cx=region.x(),cz=region.z();
            for(int bz=0;bz<5;bz++)for(int bx=0;bx<5;bx++) {
                double x=cx-90+bx*36,z=cz-90+bz*36;
                w.chunk(x+18,z+18);
                w.box(x,-.3,z,x+36,0,z+36,region.color,false);
                if(bz==2)w.box(x,.005,cz-3,x+36,.015,cz+3,region==Region.SNOW?0xB5CAD5:0xA49C8A,false);
                if(bx==2)w.box(cx-3,.016,z,cx+3,.022,z+36,0xA49C8A,false);
                for(double[] p:new double[][]{{6,6},{30,6},{6,30},{30,30}})w.sidewalks.add(new double[]{x+p[0],z+p[1]});
                boolean woods=region==Region.FOREST||region==Region.IMPERIAL||region==Region.GOBLINS;
                if(Math.hypot(x+18-cx,z+18-cz)>48)for(int i=0;i<7;i++) {
                    double tx=x+5+random.nextDouble()*26,tz=z+5+random.nextDouble()*26;
                    if(Math.abs(tx-cx)<7||Math.abs(tz-cz)<7)continue;
                    if(woods)w.tree(tx,tz,5+random.nextDouble()*5);
                    else if(region==Region.DESERT) cactus(w,tx,tz);
                    else if(region==Region.CANDY)candy(w,tx,tz,i);
                    else if(region==Region.SNOW)w.box(tx,.02,tz,tx+2,.5,tz+2,0xB9DCE5,false);
                }
            }
            w.chunk(cx,cz);
            if(region!=Region.GOBLINS&&region!=Region.EMOJI)label(w,region.title.toUpperCase(java.util.Locale.ROOT),cx,5,cz-12,.09);
            switch(region) {
                case DESERT:
                    village(w,cx,cz,"DUSTWATER",0xA77D51,COWBOY,SHERIFF);
                    for(int i=0;i<3;i++)camp(w,cx-62+i*53,cz+60,OUTLAW,0xB68C60);
                    break;
                case SNOW:
                    for(int i=0;i<6;i++) {
                        double x=cx-36+(i%3)*28,z=cz-35+(i/3)*60;
                        igloo(w,x,z);resident(w,x+4,z-4,INUIT);resident(w,x-3,z-5,ARCTIC_COP);
                    }
                    for(int i=0;i<12;i++)resident(w,cx-65+i*11,cz+52,PENGUIN);
                    for(int i=0;i<5;i++)resident(w,cx-65+i*29,cz-65,POLAR_BEAR);
                    break;
                case GOBLINS:
                    village(w,cx,cz,"GOBLIN LODGE",0x88704B,GOBLIN,GOBLIN);
                    for(int i=0;i<12;i++)resident(w,cx-40+i*7,cz+9,GOBLIN);
                    for(int side:new int[]{-1,1}){
                        w.box(cx+side*8-.3,0,cz-15,cx+side*8+.3,6,cz-14.4,0x66513B,true);
                        w.props.add(new Gta7World.Prop("goblin_banner",cx+side*8,cz-14.8,5,side));
                    }
                    label(w,"GOBLIN HOLLOW / KEEP OUT",cx,5.4,cz-15,.065);
                    break;
                case FOREST:
                    village(w,cx,cz,"WIZARD COTTAGE",0x8572A4,WIZARD,WIZARD_GUARD);
                    for(int i=0;i<8;i++) {
                        double a=i*Math.PI/4,tx=cx+Math.cos(a)*11,tz=cz+Math.sin(a)*11;
                        w.box(tx-.4,0,tz-.4,tx+.4,3,tz+.4,0x779EA7,true);
                        w.box(tx-.6,3,tz-.6,tx+.6,3.7,tz+.6,0x9BE5D2,false);
                    }
                    label(w,"THE ARCANE CIRCLE",cx,4,cz-11,.06);
                    break;
                case IMPERIAL: imperial(w,cx,cz);break;
                case MIRAGE: mirage(w,cx,cz);break;
                case CANDY:
                    village(w,cx,cz,"GINGERBREAD HOUSE",0xBA8055,CANDY_KID,CANDY_KID);
                    for(int i=0;i<12;i++)resident(w,cx-50+i*9,cz+9,CANDY_KID);
                    for(int i=0;i<9;i++)candy(w,cx-48+i*12,cz-14,i);
                    break;
                case EMOJI:
                    Gta7EmojiBuilding.build(w,cx,cz);
                    for(int i=0;i<16;i++)resident(w,cx-44+(i%8)*12,cz+38+(i/8)*13,VISITOR);
                    for(int i=0;i<4;i++)resident(w,cx-36+i*24,cz-34,COP);
                    label(w,"EMOJI DOME",cx,3,cz+32,.065);
                    break;
                default:break;
            }
        }
    }
    private static void village(Gta7World w,double cx,double cz,String name,int color,Gta7Game.Kind civilian,Gta7Game.Kind guard) {
        for(int i=0;i<8;i++) {
            double x=cx-42+(i%4)*24,z=cz-34+(i/4)*60;
            Region region=at(cx,cz);
            int floors=2+i%2;
            String houseName=region==Region.DESERT?new String[]{"DUSTWATER SALOON","SHERIFF OFFICE","GENERAL STORE","FRONTIER BANK"}[i%4]:name+" "+(i+1);
            w.house(x,z,floors,houseName,color);
            architecture(w,x,z,floors,region,i);
            resident(w,x+4.5,z-3,civilian);resident(w,x-3,z+3,civilian);
            if(i%2==0)resident(w,x+13,z-4,guard);
            w.lamp(x-2,z-2);
        }
    }
    private static void architecture(Gta7World w,double x,double z,int floors,Region region,int variant) {
        double roof=.14+floors*3.2;
        if(region==Region.DESERT) {
            // Timber storefronts, covered boardwalks and false fronts.
            w.box(x-.2,.025,z-2,x+9.2,.13,z-.3,0x9D7B55,false);
            w.box(x-.3,2.5,z-2.1,x+9.3,2.68,z-.3,0x725239,false);
            for(double post:new double[]{.35,8.65})w.box(x+post-.09,.13,z-1.85,x+post+.09,2.5,z-1.67,0x705338,true);
            w.box(x-.15,roof,z-.1,x+9.15,roof+1.2,z+.22,0xAD885C,false);
            w.box(x+1.8,roof+1.2,z-.1,x+7.2,roof+1.8,z+.22,0xAD885C,false);
            for(int floor=0;floor<floors;floor++)for(int board=0;board<3;board++) {
                double y=.16+floor*3.2+board*.25;
                w.box(x+.1,y,z-.012,x+3.3,y+.025,z-.005,0x8B6647,false);
                w.box(x+5.7,y,z-.012,x+8.9,y+.025,z-.005,0x8B6647,false);
            }
        } else if(region==Region.FOREST||region==Region.CANDY) {
            // Stepped gables are native solid-colour meshes, matching the game's geometry.
            int trim=region==Region.CANDY?0xFFF0CF:0x3F5964;
            for(int tier=0;tier<12;tier++) {
                double inset=tier*.35;
                w.box(x-.4+inset,roof+.22+tier*.25,z-.4,x+9.4-inset,roof+.47+tier*.25,z+9.4,
                        region==Region.CANDY?(tier%3==0?0xFFF0CF:0xCE668D):0x514970,false);
            }
            for(double post:new double[]{.2,8.65})w.box(x+post,.15,z-.08,x+post+.15,roof,z-.015,trim,false);
            if(region==Region.FOREST) {
                w.box(x+6.8,roof,z+5,x+7.6,roof+4,z+5.8,0x766B83,false);
                w.props.add(new Gta7World.Prop("wisp",x+7.2,z+5.4,roof+4.4,variant));
                w.props.add(new Gta7World.Prop("wisp",x+4.5,z-3,1.6,variant));
            } else {
                for(double post:new double[]{3.15,5.7})for(int band=0;band<10;band++)
                    w.box(x+post,.15+band*.25,z-.35,x+post+.17,.4+band*.25,z-.15,band%2==0?0xFFF0CF:0xCA4B70,false);
                w.props.add(new Gta7World.Prop("candy",x+1,z+2,roof+1.8,variant%3));
                w.props.add(new Gta7World.Prop("candy",x+8,z+7,roof+1.8,(variant+1)%3));
                w.box(x-.2,roof+.2,z-.2,x+9.2,roof+.4,z+.2,trim,false);
            }
        } else if(region==Region.GOBLINS) {
            for(int tier=0;tier<11;tier++){
                double inset=tier*.37;
                w.box(x-.5+inset,roof+.2+tier*.28,z-.5,x+9.5-inset,roof+.48+tier*.28,z+9.5,tier%3==0?0x56613B:0x667C47,false);
            }
            for(double post:new double[]{.1,8.6}){
                w.box(x+post,.14,z-.2,x+post+.28,roof+.4,z+.12,0x59432D,true);
                w.props.add(new Gta7World.Prop("goblin_spike",x+post+.14,z-.05,roof+.4,variant));
            }
            for(int floor=0;floor<floors;floor++)for(int log=0;log<4;log++){
                double y=.25+floor*3.2+log*.19;
                w.box(x+.4,y,z-.04,x+3.2,y+.08,z-.015,0xB1935D,false);
                w.box(x+5.8,y,z-.04,x+8.6,y+.08,z-.015,0xB1935D,false);
            }
            w.props.add(new Gta7World.Prop("goblin_banner",x+4.5,z-.35,3.4,variant));
        }
    }

    private static void camp(Gta7World w,double x,double z,Gta7Game.Kind kind,int color) {
        for(int i=0;i<3;i++) {
            double tx=x-6+i*6;
            w.box(tx,0,z,tx+3,1.7,z+4,color,true);
            w.box(tx-.2,1.7,z-.2,tx+3.2,1.9,z+4.2,0x5E534C,false);
            resident(w,tx+1,z-4,kind);
        }
        w.box(x-1,.01,z-6,x+1,.35,z-4,0xEBA05A,false);
        label(w,"OUTLAW CAMP",x,3,z-2,.05);
    }
    private static void igloo(Gta7World w,double x,double z) {
        // Segmented snow dome with an open south-facing entrance and a furnished interior.
        for(int layer=0;layer<8;layer++) {
            double r=Math.sqrt(Math.max(0,16-Math.pow(layer*.45,2)));
            for(int i=0;i<24;i++) {
                double a=i*Math.PI/12;
                if(layer<5&&Math.sin(a)<-.85)continue;
                double xx=x+Math.cos(a)*r,zz=z+Math.sin(a)*r;
                w.box(xx-.52,layer*.45,zz-.52,xx+.52,layer*.45+.44,zz+.52,layer%2==0?0xEAF3EE:0xD5E6E7,true);
            }
        }
        w.box(x-1,3.6,z-1,x+1,4,z+1,0xEAF3EE,true);
        w.box(x-2,.05,z+1,x,.5,z+2.5,0x90B9C9,true);
        label(w,"ARCTIC HOME",x,2.5,z-4.6,.035);
    }
    private static void cactus(Gta7World w,double x,double z) {
        w.box(x-.2,0,z-.2,x+.2,3,z+.2,0x648453,true);
        w.box(x-.8,1.3,z-.18,x+.8,1.6,z+.18,0x648453,true);
        w.box(x-.8,1.4,z-.18,x-.5,2.3,z+.18,0x648453,true);
    }
    private static void candy(Gta7World w,double x,double z,int i) {
        w.box(x-.12,0,z-.12,x+.12,4,z+.12,0xF1E1D3,true);
        for(int band=0;band<7;band++)w.box(x-.13,band*.5,z-.13,x+.13,band*.5+.18,z+.13,0xCB527C,false);
        w.props.add(new Gta7World.Prop("candy",x,z,4,i%3));
    }
    private static void imperial(Gta7World w,double cx,double cz) {
        for(int side:new int[]{-1,1}) {
            w.box(cx+side*40-1,0,cz-40,cx+side*40+1,4,cz+40,0x7F8E92,true);
            w.box(cx-40,0,cz+side*40-1,cx-7,4,cz+side*40+1,0x7F8E92,true);
            w.box(cx+7,0,cz+side*40-1,cx+40,4,cz+side*40+1,0x7F8E92,true);
        }
        w.house(cx-30,cz-26,3,"IMPERIAL COMMAND",0x788A92);
        w.house(cx+20,cz-26,2,"STORMTROOPER BARRACKS",0xA8B4B7);
        w.house(cx-30,cz+20,2,"DETENTION BLOCK",0x647A82);
        w.box(cx-10,.02,cz-10,cx+10,.08,cz+10,0x6A7882,false);
        // TIE fighter on the central landing pad.
        w.props.add(new Gta7World.Prop("tie",cx,cz,3,0));
        for(int side:new int[]{-1,1})w.box(cx+side*6-.2,.1,cz-5,cx+side*6+.2,8,cz+5,0x283842,true);
        for(int i=0;i<16;i++)resident(w,cx-30+(i%4)*20,cz-32+(i/4)*20,i%7==0?IMPERIAL_OFFICER:STORMTROOPER);
        resident(w,cx+3,cz+17,VADER);resident(w,cx-3,cz+17,IMPERIAL_DROID);
        label(w,"IMPERIAL GARRISON",cx,5,cz-40,.075);
    }
    private static void mirage(Gta7World w,double cx,double cz) {
        // Compact native reconstruction of the three lanes and named Mirage connectors.
        double x=cx-65,z=cz-65;
        int sand=0xC9B58E;
        for(double[] b:new double[][]{{0,0,8,130},{122,0,130,130},{8,0,122,6},{8,124,48,130},{62,124,122,130},
                {12,35,42,40},{12,66,42,72},{45,10,51,55},{45,67,51,95},{64,25,70,76},
                {70,25,103,31},{82,45,116,51},{78,64,84,94},{55,95,82,101},{18,90,39,96},
                {95,80,101,113},{104,63,122,69}})w.box(x+b[0],0,z+b[1],x+b[2],5.7,z+b[3],sand,true);
        // A gate between the southern perimeter pieces connects to the surrounding world.
        w.house(x+14,z+12,2,"B APARTMENTS",0xB9A98C);
        w.house(x+103,z+92,3,"PALACE",0xD3BD94);
        w.house(x+25,z+100,2,"MARKET",0xC7B69A);
        for(double[] p:new double[][]{{27,53},{91,105},{56,35},{73,86}}) {
            w.box(x+p[0],0,z+p[1],x+p[0]+3,1.9,z+p[1]+3,0x8E805E,true);
            w.box(x+p[0]+.1,1.9,z+p[1]+.1,x+p[0]+2.9,2.05,z+p[1]+2.9,0xBBA784,false);
        }
        for(int i=0;i<10;i++)w.box(x+70,0,z+85-i*.45,x+73,(i+1)*.2,z+85.45-i*.45,0xBAA982,true);
        for(Object[] p:new Object[][]{{"B SITE",28,52},{"A SITE",91,111},{"MID",57,54},{"T SPAWN",105,16},
                {"CT SPAWN",53,116},{"CONNECTOR",73,89},{"CATWALK",39,73},{"UNDERPASS",38,32},
                {"WINDOW / JUNGLE",64,91},{"T RAMP",112,76}})label(w,(String)p[0],x+(Integer)p[1],3,z+(Integer)p[2],.06);

        for(int i=0;i<8;i++)resident(w,x+87+(i%4)*7,z+12+(i/4)*8,TERRORIST);
        for(int i=0;i<8;i++)resident(w,x+43+(i%4)*9,z+108+(i/4)*8,COUNTER_TERRORIST);
    }
    private static void resident(Gta7World w,double x,double z,Gta7Game.Kind kind) {w.residents.add(new Gta7World.Spawn(x,z,kind));}
    private static void label(Gta7World w,String text,double x,double y,double z,double scale) {w.landmarks.add(new Gta7World.Landmark(text,x,y,z,scale));}
}
