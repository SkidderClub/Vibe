package dev.vibe.game.gta;

import java.util.ArrayList;
import java.util.Random;
import static dev.vibe.game.gta.Gta7Regions.Region;

/** Seeded street dressing, interiors and biome scenery; soft vegetation never blocks a route. */
final class Gta7Details {
    private final Gta7World w;
    private final Random random=new Random(71092026);
    private Gta7Details(Gta7World w){this.w=w;}
    static void build(Gta7World w){new Gta7Details(w).build();}
    private void build(){
        for(Gta7World.Building b:new ArrayList<Gta7World.Building>(w.buildings)) {
            w.chunk(b.x,b.z);
            if(b.width==9)house(b);
        }
        for(Region region:Region.values()) {
            landscape(region);
            w.chunk(region.x(),region.z());
            district(region);
        }
    }
    private void box(double x,double y,double z,double xx,double yy,double zz,int color){w.box(x,y,z,xx,yy,zz,color,false);}
    private void prop(String type,double x,double z,double size,int variant){
        double rx=0,rz=0,height=0;
        switch(type){
            case "crate":rx=.6;rz=.55;height=1.15;break;
            case "barrel":rx=rz=.4;height=1;break;
            case "bin":rx=rz=.32;height=.9;break;
            case "bench":rx=1;rz=.3;height=.55;break;
            case "planter":rx=rz=size*.6;height=size*.64;break;
            case "wagon":case "sled":rx=.95;rz=1.15;height=1;break;
            case "stall":case "kiosk":rx=1.45;rz=.8;height=1;break;
            case "cake":rx=rz=size;height=1;break;
            case "pine":case "palm":rx=rz=.2;height=size*.7;break;
            case "crystal":rx=rz=size*.25;height=size;break;
            default:break;
        }
        if(rx>0){
            if(w.blocked(x,.16,z,Math.max(rx,rz),height))return;
            w.collider(new Gta7Bounds(x-rx,.08,z-rz,x+rx,.08+height,z+rz));
        }
        if(type.equals("hill")||type.equals("icehill")||type.equals("mesa")){
            w.relief(x,.08-size*.32,z,size*.58,size*.55,size*.53);
            w.relief(x+size*.3,.08-size*.18,z+size*.16,size*.42,size*.37,size*.4);
        }
        w.props.add(new Gta7World.Prop(type,x,z,size,variant));
    }
    private void house(Gta7World.Building b){
        double x=b.x,z=b.z;int variant=random.nextInt(4);
        Region region=Gta7Regions.at(x,z);
        int trim=region==Region.CANDY?0xFFF0CB:region==Region.IMPERIAL?0x445964:0xD7CAB1;
        // Recessed jambs, sills, corner courses and a foundation give walls depth.
        for(double y=.14;y<b.height-1;y+=3.2){
            box(x-.045,y,z-.045,x+.18,y+.18,z+9.04,trim);
            box(x+8.82,y,z-.045,x+9.045,y+.18,z+9.04,trim);
            if(y>1)box(x-.055,y-.14,z-.07,x+9.055,y+.025,z-.025,trim);
            for(double col:new double[]{.8,6.4}){
                box(x+col-.11,y+.8,z-.15,x+col+1.91,y+.91,z+.12,trim);
                box(x+col-.09,y+.91,z-.08,x+col-.015,y+2.45,z+.28,trim);
                box(x+col+1.815,y+.91,z-.08,x+col+1.89,y+2.45,z+.28,trim);
                box(x+col+.86,y+.9,z+.07,x+col+.94,y+2.4,z+.095,trim);
                if((variant+(int)y)%3==0){
                    box(x+col+.1,y+.91,z+.32,x+col+.42,y+2.37,z+.35,0xBA9A82);
                    box(x+col+1.38,y+.91,z+.32,x+col+1.7,y+2.37,z+.35,0xBA9A82);
                }
            }
            // Furniture stays clear of the central passage, ladder and staircase.
            if(y<.2) {
                cabinet(x+.42,y,z+7.7,2.5,variant);
                w.box(x+7.8,y,z+.55,x+8.6,y+.9,z+1.8,0x806A53,true);
                box(x+7.72,y+.9,z+.5,x+8.65,y+.98,z+1.85,0xD6C8AE);
                box(x+7.9,y+1.04,z+.75,x+8.25,y+1.65,z+.91,0x253F4B);
                box(x+7.94,y+1.1,z+.735,x+8.21,y+1.6,z+.746,0x6FA6B2);
            } else {
                cabinet(x+.4,y,z+3.7,1.7,variant);
                box(x+3.25,y+.03,z+4.1,x+5.15,y+.045,z+5.9,0x896D77);
            }
            // Framed wall art and ceiling pendant with a warm pool inside.
            box(x+.32,y+1.2,z+4.2,x+.36,y+2.22,z+5.4,0x735947);
            box(x+.365,y+1.28,z+4.29,x+.38,y+2.14,z+5.31,0xCBB58F);
            box(x+.382,y+1.45,z+4.4,x+.39,y+1.9,z+5.2,0x517C83);
            box(x+4.47,y+2.7,z+3.97,x+4.53,y+3,z+4.03,0x465158);
            box(x+4.15,y+2.62,z+3.65,x+4.85,y+2.7,z+4.35,0xEFE0BD);
            w.lights.add(new Gta7World.Light(x+4.5,y+2.6,z+4,2.5,0xF6CA88));
        }
        box(x+8.48,.18,z-.16,x+8.59,b.height,z-.065,0x526B70);
        box(x-.13,b.height-.12,z-.17,x+9.13,b.height+.04,z-.06,0x596D73);
        if(region==Region.CITY||region==Region.IMPERIAL){
            box(x+1,b.height+.22,z+4,x+3,b.height+.85,z+5.6,0x88989A);
            for(int vent=0;vent<7;vent++)box(x+1.12,b.height+.851,z+4.1+vent*.19,x+2.88,b.height+.87,z+4.18+vent*.19,0x394B53);
            box(x+7.2,b.height+.22,z+7,x+7.28,b.height+2.5,z+7.08,0x6C7E82);
            box(x+6.5,b.height+2.12,z+7,x+8,b.height+2.17,z+7.08,0x6C7E82);
        }
        if(region==Region.CITY){
            // Canvas awning leaves the entrance at full walking height.
            for(int stripe=0;stripe<12;stripe++)box(x+.2+stripe*.72,2.6,z-1.0,x+.92+stripe*.72,2.74,z-.3,stripe%2==0?0x397D79:0xE5D5B6);
            prop("planter",x+.5,z-.8,.65,variant);
            prop("planter",x+8.5,z-.8,.65,variant);
            if(variant==0)prop("bike",x+9.65,z+2,1,variant);
            if(variant==1){prop("barrel",x+9.6,z+6,1,0);prop("crate",x+9.6,z+7.5,1,1);}
            if(variant==2)prop("bin",x+9.5,z+4,1,0);
        }
        w.lights.add(new Gta7World.Light(x+4.5,2.52,z-.4,2.5,region==Region.FOREST?0x92EBD6:0xFFD69C));
    }
    private void cabinet(double x,double y,double z,double width,int variant){
        w.box(x,y,z,x+width,y+1.65,z+.35,0x796247,true);
        for(int row=0;row<4;row++){
            box(x,y+.1+row*.4,z-.1,x+width,y+.17+row*.4,z+.37,0xB69567);
            for(int i=0;i<(int)(width*5);i++)box(x+.1+i*.18,y+.17+row*.4,z-.09,x+.23+i*.18,y+.43+row*.4,z+.14,
                    new int[]{0x648D8A,0xC39869,0x976978,0xD0BE93}[(i+row+variant)%4]);
        }
    }
    private boolean free(double x,double z,double radius){
        if(w.blocked(x,.16,z,radius,2))return false;
        for(Gta7World.Building b:w.buildings)if(x>b.x-1.5&&x<b.x+b.width+1.5&&z>b.z-2.5&&z<b.z+b.depth+1.5)return false;
        return true;
    }
    private void landscape(Region region){
        double cx=region.x(),cz=region.z();
        for(int bz=0;bz<5;bz++)for(int bx=0;bx<5;bx++){
            double x=cx-90+bx*36,z=cz-90+bz*36;w.chunk(x+18,z+18);
            int count=region==Region.CITY?12:region==Region.FOREST||region==Region.IMPERIAL||region==Region.GOBLINS?65:36;
            for(int i=0;i<count;i++){
                double px=x+2+random.nextDouble()*32,pz=z+2+random.nextDouble()*32;
                if(region!=Region.CITY&&(Math.abs(px-cx)<4.5||Math.abs(pz-cz)<4.5))continue;
                if(region==Region.CITY&&(px%36<7||px%36>29||pz%36<7||pz%36>29))continue;
                if(region==Region.MIRAGE||region==Region.EMOJI||!free(px,pz,.5))continue;
                int v=random.nextInt(4);
                if(region==Region.FOREST||region==Region.IMPERIAL||region==Region.GOBLINS){
                    prop(i%9==0?"rock":i%5==0?"fern":"grass",px,pz,.3+random.nextDouble()*.7,v);
                    if(i%23==0){prop("mushroom",px+.7,pz,.5,v);if(region==Region.FOREST)prop("firefly",px,pz,1.1,v);}
                }else if(region==Region.SNOW){
                    prop(i%7==0?"ice":"snowdrift",px,pz,.3+random.nextDouble(),v);
                    if(i%18==0)prop("pine",px,pz,4+random.nextDouble()*3,v);
                }else if(region==Region.DESERT){
                    prop(i%6==0?"rock":i%4==0?"drygrass":"sandpatch",px,pz,.3+random.nextDouble()*.8,v);
                }else if(region==Region.CANDY){
                    prop(i%4==0?"gumdrop":"flowers",px,pz,.4+random.nextDouble()*.6,v);
                }else prop(i%6==0?"flowers":"grass",px,pz,.35,v);
            }
            if(region!=Region.CITY&&region!=Region.MIRAGE&&region!=Region.EMOJI){
                // Low relief beyond the settlement breaks the ruler-flat horizon.
                if((bx==0||bx==4||bz==0||bz==4)&&Math.abs(x+18-cx)>18&&Math.abs(z+18-cz)>18)
                    prop(region==Region.SNOW?"icehill":region==Region.DESERT?"mesa":"hill",x+18,z+18,7+random.nextDouble()*7,bx+bz);
            }
        }
    }
    private void district(Region r){
        double x=r.x(),z=r.z();
        if(r==Region.DESERT){
            for(int i=0;i<8;i++){double xx=x-42+i*13;prop("barrel",xx,z-12,1,0);prop("wagon",xx,z+14,1,i);}
            prop("windmill",x+55,z-55,9,0);
            for(int i=0;i<3;i++) {double cx=x-62+i*53;prop("campfire",cx,z+55,1,0);w.lights.add(new Gta7World.Light(cx,.7,z+55,5,0xFFAF60));}
            for(int i=0;i<5;i++)prop("palm",x-57+i*26,z-17,5,i);
        }else if(r==Region.FOREST){
            for(int i=0;i<14;i++){double a=i*Math.PI/7;prop("mushroom",x+Math.cos(a)*17,z+Math.sin(a)*17,.7,i%4);prop("firefly",x+Math.cos(a)*17,z+Math.sin(a)*17,1.8,i);}
            for(int i=0;i<4;i++){double a=i*Math.PI/2;prop("crystal",x+Math.cos(a)*8,z+Math.sin(a)*8,1.6,i);}
            w.lights.add(new Gta7World.Light(x,2,z,7,0x81D4E3));
        }else if(r==Region.SNOW){
            for(int i=0;i<6;i++){double xx=x-36+(i%3)*28,zz=z-35+(i/3)*60;prop("sled",xx+7,zz,1,0);prop("barrel",xx+7,zz+3,1,1);}
            prop("frozenpond",x+43,z+52,12,0);
            for(int i=0;i<7;i++)prop("ice",x+33+i*4,z+63,1.8+i%3,0);
        }else if(r==Region.CANDY){
            for(int i=0;i<12;i++)prop("gumdrop",x-52+i*9,z-9,1.2,i%4);
            prop("cake",x,z+17,3,0);
            for(int i=0;i<6;i++)prop("stall",x-40+i*16,z-18,1,2);
        }else if(r==Region.IMPERIAL){
            for(int i=0;i<12;i++){prop("crate",x-32+(i%4)*20,z+13+(i/4)*4,1,i);}
            for(int side:new int[]{-1,1}){
                double xx=x+side*34;
                box(xx-3,4,z-38,xx+3,4.3,z-32,0x556975);
                box(xx-2.5,4.3,z-37.5,xx+2.5,7,z-32.5,0x839598);
                box(xx-2.35,5,z-37.56,xx+2.35,6.5,z-37.51,0x405E6B);
                prop("dish",xx,z-35,7.2,0);w.lights.add(new Gta7World.Light(xx,6.2,z-31.8,8,0xBCDDE6));
            }
            for(int i=0;i<12;i++)box(x-12+i*2,.085,z-12,x-11.4+i*2,.095,z-11.5,0xE2D2A1);
        }else if(r==Region.MIRAGE){
            for(double[] p:new double[][]{{-38,-11},{26,46},{-9,-30},{8,21}}){prop("crate",x+p[0],z+p[1],1,0);prop("barrel",x+p[0]+4,z+p[1],1,0);}
            for(int i=0;i<5;i++)prop("palm",x-53+i*23,z-50,6,i);
            prop("stall",x-37,z+39,1,0);prop("stall",x-25,z+39,1,1);
            for(int i=0;i<6;i++){box(x-50+i*1.5,5.72,z-29,x-49.3+i*1.5,6,z-26,0xB59A74);}
        }else if(r==Region.GOBLINS){
            for(int i=0;i<8;i++){
                prop("crate",x-48+i*14,z-11,1,i);prop("barrel",x-44+i*14,z-11,1,0);
                prop("mushroom",x-48+i*14,z+13,.8,i);
            }
            for(int i=0;i<3;i++){
                prop("campfire",x-24+i*24,z+18,1,0);
                w.lights.add(new Gta7World.Light(x-24+i*24,.7,z+18,5,0xFDC271));
            }
            prop("wagon",x+32,z+14,1,0);prop("stall",x-24,z-16,1,1);
        }else if(r==Region.EMOJI){
            for(int i=0;i<8;i++){prop("planter",x-58+i*16,z+60,1,0);prop("bench",x-58+i*16,z+64,1,0);}
            for(int i=0;i<10;i++){prop("bollard",x-55+i*12,z-62,1,0);w.lamp(x-55+i*12,z-65);}
        }else{
            for(int bz=0;bz<5;bz++)for(int bx=0;bx<5;bx++){
                double px=bx*36+6,pz=bz*36+18;
                if(free(px,pz,.5))prop("bin",px,pz,1,0);
            }
        }
    }
}
