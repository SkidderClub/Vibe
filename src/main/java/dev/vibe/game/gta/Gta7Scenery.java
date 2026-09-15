package dev.vibe.game.gta;

import org.lwjgl.opengl.GL11;
import static dev.vibe.game.gta.Gta7Renderer.*;

/** Small native meshes shared by the district display lists. All coordinates here are local. */
final class Gta7Scenery {
    private static final int[] SWEETS={0xE887B6,0x87CDB9,0xC0A5D7,0xF0C776};
    private Gta7Scenery() { }
    static void draw(Gta7World.Prop p) {
        GL11.glPushMatrix();GL11.glTranslated(p.x,.08,p.z);
        int v=p.variant, tint=SWEETS[Math.floorMod(v,4)];double s=p.size;
        switch(p.type) {
            case "goblin_spike":
                GL11.glTranslated(0,s,0);cone(.3,1.1,0xD3C397);break;
            case "goblin_banner":
                GL11.glTranslated(0,s,0);box(-.95,-1.3,-.07,.95,.1,.07,0x753B32);
                for(int side:new int[]{-1,1}){
                    ellipsoid(0,-.5,side*.1,.3,.32,.09,0xD4C896);
                    box(-.2,-.8,side*.16-.03,.2,-.57,side*.16+.03,0xD4C896);
                    box(-.2,-.5,side*.2-.005,-.07,-.35,side*.2+.005,0x34271E);box(.07,-.5,side*.2-.005,.2,-.35,side*.2+.005,0x34271E);
                }break;
            case "grass": case "drygrass": case "flowers": case "fern":
                GL11.glRotated(v*47,0,1,0);vegetation(s,p.type,v);break;
            case "sandpatch":
                ellipsoid(0,-.025,0,s*2,.07,s,0xC39E67);break;
            case "rock":
                ellipsoid(0,s*.24,0,s*.85,s*.5,s*.7,0x8B8C7C);
                ellipsoid(s*.6,s*.1,-s*.3,s*.45,s*.3,s*.35,0xA3A28E);break;
            case "snowdrift":
                ellipsoid(0,-.06,0,s*1.7,s*.28,s,0xEBF1EC);break;
            case "ice": case "crystal":
                GL11.glRotated(v*31,0,1,0);
                crystal(s,p.type.equals("ice")?0xA7D9E3:0x78D4C3);
                GL11.glTranslated(s*.35,0,s*.3);GL11.glRotated(23,0,0,1);crystal(s*.5,0xBCECE6);break;
            case "pine":
                cylinder(.19,s*.74,0x665F52);
                for(int i=0;i<4;i++){
                    GL11.glPushMatrix();GL11.glTranslated(0,s*(.25+i*.15),0);
                    cone(s*(.24-i*.044),s*.32,0x527D77);
                    GL11.glTranslated(0,s*.07,0);cone(s*(.20-i*.036),s*.26,0xDBE8E5);GL11.glPopMatrix();
                }break;
            case "palm":
                cylinder(.19,s,0xA58D66);
                for(int i=0;i<9;i++){
                    GL11.glPushMatrix();GL11.glTranslated(0,s,0);GL11.glRotated(i*40,0,1,0);
                    GL11.glBegin(GL11.GL_TRIANGLES);color(0x668D61,1);GL11.glNormal3d(0,1,0);
                    vertex(0,0,0);vertex(1.45,.55,.42);vertex(3.1,-.4,0);
                    color(0x82A267,1);vertex(0,0,0);vertex(3.1,-.4,0);vertex(1.45,.55,-.42);
                    GL11.glEnd();GL11.glPopMatrix();
                }
                ellipsoid(.18,s-.2,.1,.3,.4,.3,0x846347);break;
            case "mushroom":
                cylinder(s*.13,s*.68,0xD5CEB2);ellipsoid(0,s*.7,0,s*.65,s*.3,s*.65,v%2==0?0xB16CA8:0x70B5B0);
                for(int i=0;i<5;i++){double a=i*Math.PI*.4;ellipsoid(Math.cos(a)*s*.34,s*.9,Math.sin(a)*s*.34,s*.08,s*.025,s*.08,0xE8E7C9);}break;
            case "gumdrop":
                ellipsoid(0,s*.4,0,s*.55,s*.7,s*.55,tint);
                for(int i=0;i<7;i++){double a=i*2.4;ellipsoid(Math.cos(a)*s*.44,s*(.35+(i%3)*.15),Math.sin(a)*s*.44,s*.045,s*.045,s*.045,0xFFE9DC);}break;
            case "hill": case "icehill": case "mesa":
                // Shallow peripheral relief: settlements and connecting roads stay open.
                int ground=p.type.equals("icehill")?0xCADFE1:p.type.equals("mesa")?0xBA9164:Gta7Regions.at(p.x,p.z).color;
                ellipsoid(0,-s*.32,0,s*.58,s*.55,s*.53,ground);
                ellipsoid(s*.3,-s*.18,s*.16,s*.42,s*.37,s*.4,ground);break;
            case "barrel":
                cylinder(.4,1,0x98744F);
                for(double y:new double[]{.1,.5,.9}){GL11.glPushMatrix();GL11.glTranslated(0,y,0);cylinder(.414,.055,0x465B61);GL11.glPopMatrix();}
                box(-.34,1,-.035,.34,1.015,.035,0xC09A65);break;
            case "crate":
                box(-.6,0,-.55,.6,1.15,.55,0x947952);
                for(int side:new int[]{-1,1}){
                    box(-.6,.08,side*.552-.035,.6,.19,side*.552+.035,0xC5A175);
                    box(-.6,.94,side*.552-.035,.6,1.07,side*.552+.035,0xC5A175);
                    box(side*.48-.06,0,-.59,side*.48+.06,1.15,.59,0xC5A175);
                }
                beam(-.52,.16,-.6,.52,.99,-.6,.045,0xC5A175);break;
            case "bench":
                for(int side:new int[]{-1,1}){box(side*.75-.045,0,-.28,side*.75+.045,.5,.28,0x455A62);box(side*.75-.045,.4,.23,side*.75+.045,1.05,.29,0x455A62);}
                for(int i=0;i<3;i++){box(-1,.48,-.3+i*.21,1,.55,-.13+i*.21,0xB2976C);box(-1,.67+i*.16,.24,1,.78+i*.16,.3,0xB2976C);}break;
            case "bin":
                cylinder(.32,.83,0x456C68);GL11.glTranslated(0,.83,0);cylinder(.36,.08,0x708F81);
                box(-.19,.085,-.12,.19,.095,.12,0x263D45);break;
            case "bollard":
                cylinder(.14,.83,0x465B63);GL11.glTranslated(0,.62,0);cylinder(.15,.09,0xDCC9A0);break;
            case "planter":
                box(-s*.55,0,-s*.55,s*.55,s*.6,s*.55,0xB39579);
                box(-s*.6,s*.5,-s*.6,s*.6,s*.64,s*.6,0xD0BCA0);
                ellipsoid(0,s*.9,0,s*.55,s*.48,s*.55,0x577F62);break;
            case "bike":
                GL11.glRotated(v*90,0,1,0);
                ring(-.65,.4,0,.37,.045,0x283E48);ring(.65,.4,0,.37,.045,0x283E48);
                beam(-.65,.4,0,-.2,1,0,.03,0xBE8A69);beam(-.65,.4,0,.1,.43,0,.03,0xBE8A69);
                beam(-.2,1,0,.1,.43,0,.03,0xBE8A69);beam(-.2,1,0,.42,.99,0,.03,0xBE8A69);
                beam(.42,.99,0,.1,.43,0,.03,0xBE8A69);beam(.65,.4,0,.35,1.25,0,.025,0x8FABB0);
                box(-.4,1.03,-.11,-.05,1.11,.11,0x30434A);box(.3,1.23,-.22,.4,1.27,.22,0x30434A);break;
            case "wagon": case "sled":
                boolean wagon=p.type.equals("wagon");
                box(-.9,.4,-1.1,.9,.55,1.1,0xA88B5D);
                for(int i=0;i<4;i++)box(-.92,.55+i*.13,-1.13,.92,.63+i*.13,-1.02,0xBFA577);
                for(int side:new int[]{-1,1}){
                    box(side*.86-.06,.5,-1.1,side*.86+.06,1.03,1.1,0xB09568);
                    if(wagon)for(int end:new int[]{-1,1}){
                        GL11.glPushMatrix();GL11.glTranslated(side*1.03,.38,end*.75);GL11.glRotated(90,0,1,0);
                        ring(0,0,0,.38,.05,0x584F42);for(int i=0;i<4;i++){double a=i*Math.PI/4;beam(Math.cos(a)*-.34,Math.sin(a)*-.34,0,Math.cos(a)*.34,Math.sin(a)*.34,0,.02,0xB99E70);}GL11.glPopMatrix();
                    }else box(side*.65-.05,.08,-1.4,side*.65+.05,.16,1.3,0x607C89);
                    box(side*.6-.045,.5,1.1,side*.6+.045,.58,2.3,0xA98E66);
                }break;
            case "campfire":
                for(int i=0;i<10;i++){double a=i*Math.PI/5;ellipsoid(Math.cos(a)*.72,.14,Math.sin(a)*.72,.2,.19,.2,0x787B72);}
                for(int i=0;i<3;i++){GL11.glPushMatrix();GL11.glRotated(i*60,0,1,0);box(-.57,.1,-.1,.57,.24,.1,0x6A503E);GL11.glPopMatrix();}break;
            case "windmill":
                for(int side:new int[]{-1,1})for(int end:new int[]{-1,1})beam(side*1.2,0,end*1.2,side*.5,s,end*.5,.06,0x78888A);
                for(int i=1;i<5;i++){box(-.8,i*s/5,-.82,.8,i*s/5+.07,-.74,0x78888A);}
                GL11.glTranslated(0,s,0);GL11.glRotated(90,1,0,0);cylinder(.33,.5,0xBDC4AE);break;
            case "dish":
                GL11.glTranslated(0,s,0);cylinder(.12,.7,0x697C84);GL11.glTranslated(0,1,0);GL11.glRotated(-30,1,0,0);
                ellipsoid(0,0,0,1.4,.15,1.4,0xC0CCCB);beam(0,0,0,0,1.3,0,.04,0x5E757D);break;
            case "frozenpond":
                ellipsoid(0,-.035,0,s,.08,s*.7,0x8DBBCB);
                GL11.glBegin(GL11.GL_LINES);color(0xD3E8E7,1);
                for(int i=0;i<8;i++){double a=i*2.4;vertex(Math.cos(a)*s*.8,.065,Math.sin(a)*s*.5);vertex(Math.cos(a+.4)*s*.25,.067,Math.sin(a+.4)*s*.2);}GL11.glEnd();break;
            case "cake":
                cylinder(s,.8,0xB68067);GL11.glTranslated(0,.8,0);cylinder(s*1.02,.2,0xFFF0D2);
                cylinder(s*.73,1,0xD78CAD);GL11.glTranslated(0,1,0);cylinder(s*.76,.18,0xFFF0D2);
                for(int i=0;i<6;i++){double a=i*Math.PI/3;box(Math.cos(a)*s*.5-.05,.15,Math.sin(a)*s*.5-.05,Math.cos(a)*s*.5+.05,.7,Math.sin(a)*s*.5+.05,0x81CFCA);}break;
            case "stall": case "kiosk":
                int cloth=p.type.equals("kiosk")?0x688B88:v%2==0?0xC19072:0x719DA1;
                for(int side:new int[]{-1,1})for(int end:new int[]{-1,1})box(side*1.4-.06,0,end*.8-.06,side*1.4+.06,2.55,end*.8+.06,0x876A50);
                box(-1.45,.55,-.8,1.45,1,.8,0xB2966F);
                for(int i=0;i<10;i++)box(-1.6+i*.32,2.53,-1, -1.28+i*.32,2.65,1,i%2==0?cloth:0xE9D9B5);
                for(int i=0;i<8;i++)ellipsoid(-1.1+i*.3,1.1,-.1,.16,.14,.16,i%2==0?0xBC805C:0x9CAB6D);break;
            default:break;
        }
        GL11.glPopMatrix();
    }
    private static void vegetation(double s,String type,int v){
        boolean dry=type.equals("drygrass"),fern=type.equals("fern"),flowers=type.equals("flowers");
        GL11.glBegin(GL11.GL_TRIANGLES);
        for(int i=0;i<(fern?12:7);i++){
            double a=i*2.4,c=Math.cos(a),z=Math.sin(a),r=.07*(i%3),h=s*(.45+(i%4)*.16);
            color(dry?0xB59C66:0x668861,.7+(i%3)*.13);GL11.glNormal3d(c*.3,1,z*.3);
            vertex(c*r-.04,0,z*r);vertex(c*r+.04,0,z*r);vertex(c*s*.35,h,z*s*.35);
            if(fern){vertex(0,.04,0);vertex(c*s*.7,h*.55,z*s*.7);vertex(c*s*.3+.12,h*.8,z*s*.3);}
        }GL11.glEnd();
        if(flowers)for(int i=0;i<3;i++){double a=i*2.4;ellipsoid(Math.cos(a)*s*.3,s*.6,Math.sin(a)*s*.3,s*.13,s*.045,s*.13,SWEETS[(v+i)%4]);}
    }
    static void cone(double radius,double height,int rgb){
        GL11.glBegin(GL11.GL_TRIANGLES);
        for(int i=0;i<10;i++){double a=i*Math.PI/5,b=(i+1)*Math.PI/5;color(rgb,.8+.15*Math.cos(a));GL11.glNormal3d(Math.cos(a),radius/height,Math.sin(a));vertex(Math.cos(a)*radius,0,Math.sin(a)*radius);vertex(Math.cos(b)*radius,0,Math.sin(b)*radius);vertex(0,height,0);}GL11.glEnd();
    }
    private static void crystal(double s,int rgb){
        GL11.glPushMatrix();GL11.glScaled(.32,1,.32);cone(s,s*1.7,rgb);GL11.glPopMatrix();
    }
    static void beam(double x,double y,double z,double xx,double yy,double zz,double radius,int rgb){
        double dx=xx-x,dy=yy-y,dz=zz-z,len=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len<.001)return;GL11.glPushMatrix();GL11.glTranslated(x,y,z);
        double horizontal=Math.hypot(dx,dz);
        if(horizontal>.0001)GL11.glRotated(Math.toDegrees(Math.acos(dy/len)),dz,0,-dx);else if(dy<0)GL11.glRotated(180,1,0,0);
        cylinder(radius,len,rgb);GL11.glPopMatrix();
    }
    private static void ring(double x,double y,double z,double radius,double width,int rgb){
        GL11.glBegin(GL11.GL_QUADS);color(rgb,1);GL11.glNormal3d(0,0,1);
        for(int i=0;i<16;i++){double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            vertex(x+Math.cos(a)*radius,y+Math.sin(a)*radius,z);vertex(x+Math.cos(b)*radius,y+Math.sin(b)*radius,z);
            vertex(x+Math.cos(b)*(radius-width),y+Math.sin(b)*(radius-width),z);vertex(x+Math.cos(a)*(radius-width),y+Math.sin(a)*(radius-width),z);}
        GL11.glEnd();
    }
    private static void vertex(double x,double y,double z){GL11.glVertex3d(x,y,z);}
}
