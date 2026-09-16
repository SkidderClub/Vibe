package dev.vibe.game.battlefront;

import org.lwjgl.opengl.GL11;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Original articulated native meshes. No downloaded model or runtime network dependency. */
public final class BattlefrontMeshes {
    private BattlefrontMeshes(){}
    public static void color(int c){GL11.glColor3d((c>>16&255)/255.0,(c>>8&255)/255.0,(c&255)/255.0);}
    public static void box(double x,double y,double z,double X,double Y,double Z,int c){
        color(c);GL11.glBegin(GL11.GL_QUADS);
        GL11.glNormal3d(0,1,0);tv(x,Y,z,x,z);tv(x,Y,Z,x,Z);tv(X,Y,Z,X,Z);tv(X,Y,z,X,z);
        GL11.glNormal3d(0,-1,0);tv(x,y,z,x,z);tv(X,y,z,X,z);tv(X,y,Z,X,Z);tv(x,y,Z,x,Z);
        GL11.glNormal3d(0,0,-1);tv(x,y,z,x,y);tv(x,Y,z,x,Y);tv(X,Y,z,X,Y);tv(X,y,z,X,y);
        GL11.glNormal3d(0,0,1);tv(x,y,Z,x,y);tv(X,y,Z,X,y);tv(X,Y,Z,X,Y);tv(x,Y,Z,x,Y);
        GL11.glNormal3d(-1,0,0);tv(x,y,z,z,y);tv(x,y,Z,Z,y);tv(x,Y,Z,Z,Y);tv(x,Y,z,z,Y);
        GL11.glNormal3d(1,0,0);tv(X,y,z,z,y);tv(X,Y,z,z,Y);tv(X,Y,Z,Z,Y);tv(X,y,Z,Z,y);GL11.glEnd();
    }
    private static void tv(double x,double y,double z,double u,double v){GL11.glTexCoord2d(u/2,v/2);v(x,y,z);}
    public static void v(double x,double y,double z){GL11.glVertex3d(x,y,z);}
    public static void sphere(double x,double y,double z,double rx,double ry,double rz,int c){
        color(c);GL11.glPushMatrix();GL11.glTranslated(x,y,z);GL11.glScaled(rx,ry,rz);
        for(int j=0;j<7;j++){double a=-Math.PI/2+j*Math.PI/7,b=a+Math.PI/7;GL11.glBegin(GL11.GL_QUAD_STRIP);
            for(int i=0;i<=12;i++){double t=i*Math.PI/6;sv(a,t);sv(b,t);}GL11.glEnd();}GL11.glPopMatrix();
    }
    private static void sv(double a,double t){double x=Math.cos(a)*Math.sin(t),y=Math.sin(a),z=Math.cos(a)*Math.cos(t);GL11.glNormal3d(x,y,z);GL11.glTexCoord2d(t/Math.PI,a/Math.PI);v(x,y,z);}
    public static void cone(double radius,double top,double height,int c){
        color(c);GL11.glBegin(GL11.GL_QUADS);for(int i=0;i<12;i++){
            double a=i*Math.PI/6,b=(i+1)*Math.PI/6,m=(a+b)/2;
            GL11.glNormal3d(Math.sin(m),Math.max(0,radius-top)/Math.max(.1,height),Math.cos(m));
            GL11.glTexCoord2d(a*radius,0);v(Math.sin(a)*radius,0,Math.cos(a)*radius);GL11.glTexCoord2d(b*radius,0);v(Math.sin(b)*radius,0,Math.cos(b)*radius);
            GL11.glTexCoord2d(b*radius,height/4);v(Math.sin(b)*top,height,Math.cos(b)*top);GL11.glTexCoord2d(a*radius,height/4);v(Math.sin(a)*top,height,Math.cos(a)*top);
        }GL11.glEnd();
    }
    private static void limb(double x,double y,double z,double angle,double width,double length,int c){
        GL11.glPushMatrix();GL11.glTranslated(x,y,z);GL11.glRotated(angle,1,0,0);
        box(-width/2,-length,-width/2,width/2,0,width/2,c);sphere(0,-length,0,width*.56,width*.56,width*.56,0x343A3F);GL11.glPopMatrix();
    }
    private static void leg(double x,double angle,double width,int c){
        GL11.glPushMatrix();GL11.glTranslated(x,.92,0);GL11.glRotated(angle,1,0,0);
        box(-width/2,-.43,-width/2,width/2,0,width/2,c);GL11.glTranslated(0,-.43,0);sphere(0,0,0,width*.55,width*.55,width*.55,0x394549);
        GL11.glRotated(-angle*.65,1,0,0);box(-width*.42,-.4,-width*.42,width*.42,0,width*.42,c);box(-width*.6,-.49,-.24,width*.6,-.39,.1,c);GL11.glPopMatrix();
    }
    public static void soldier(Faction faction,Role role,double walk,double time){soldierBody(faction,role,walk,time,true);}
    private static void soldierBody(Faction faction,Role role,double walk,double time,boolean armed){
        soldierBody(faction,role,walk,time,armed,null);
    }
    private static void soldierBody(Faction faction,Role role,double walk,double time,boolean armed,Appearance look){
        soldierBody(faction,role,walk,time,armed,look,BattlefrontReloadPose.at(-1));
    }
    private static void soldierBody(Faction faction,Role role,double walk,double time,boolean armed,Appearance look,BattlefrontReloadPose pose){
        double swing=Math.sin(walk)*24;int accent=look==null?faction.color&0xFFFFFF:look.color(faction);
        boolean droid=faction==Faction.SEPARATISTS,ewok=faction==Faction.REBELS&&role==Role.SCOUT;
        if(ewok){
            sphere(0,.55,0,.34,.48,.27,0x77503A);sphere(0,1.08,0,.36,.36,.30,0x90613E);
            sphere(-.29,1.32,0,.14,.16,.09,0xA57A4C);sphere(.29,1.32,0,.14,.16,.09,0xA57A4C);
            sphere(0,1.09,-.23,.24,.25,.1,0xC3A078);sphere(-.1,1.16,-.326,.036,.044,.02,0x15171A);sphere(.1,1.16,-.326,.036,.044,.02,0x15171A);
            sphere(0,1.06,-.35,.053,.039,.036,0x31241C);box(-.24,.75,-.30,.24,.85,-.25,0xB78045);
            limb(-.17,.28,0,swing,.17,.26,0x79563D);limb(.17,.28,0,-swing,.17,.26,0x79563D);
            limb(-.3,.76,0,55,.13,.34,0x906641);limb(.3,.76,0,72,.13,.34,0x906641);
            if(armed){box(.29,.46,-.6,.34,1.04,-.55,0x5E381D);box(.31,.7,-.92,.34,.73,-.25,0xCDB385);}
            for(int i=0;i<5;i++)box(-.19+i*.08,.79,-.31,-.14+i*.08,.88,-.29,0x77634B);
            return;
        }
        if(droid&&role!=Role.HEAVY){
            int tan=look!=null&&look.paint>0?accent:role==Role.SCOUT?0x737A76:0xC8B18A;
            box(-.18,1.04,-.13,.18,1.46,.12,tan);box(-.1,1.43,-.08,.1,1.69,.07,0x777866);
            sphere(0,1.82,-.09,.17,.21,.18,tan);box(-.12,1.69,-.4,.12,1.8,-.04,tan);
            box(-.11,1.82,-.24,-.055,1.87,-.21,0x291F16);box(.055,1.82,-.24,.11,1.87,-.21,0x291F16);
            box(-.18,1.14,.13,.18,1.49,.27,0x92866B);box(-.02,1.4,.25,.02,2.08,.28,0x6E6B59);
            box(-.15,.9,-.1,.15,1.04,.1,0x76776B);
            for(int s=-1;s<=1;s+=2){leg(s*.14,s*swing,.10,tan);reloadArm(s,.25,1.41,62,.085,.49,tan,pose);}
            box(-.16,1.27,-.145,.16,1.42,-.13,role==Role.COMMANDER?0xE5BB44:role==Role.MEDIC?0x779FAD:tan);
            for(int i=0;i<4;i++)box(-.12+i*.065,1.08,-.148,-.09+i*.065,1.21,-.137,0x676B60);
            for(int s:new int[]{-1,1}){sphere(s*.2,1.41,0,.07,.07,.065,0x7C8279);box(s*.13-.018,.96,-.12,s*.13+.018,1.05,-.10,0xDED1A7);}
        }else if(droid){
            sphere(0,1.31,0,.5,.54,.31,look!=null&&look.paint>0?accent:0x697A89);box(-.42,.92,-.24,.42,1.48,.21,0x7D8D9A);
            box(-.18,1.69,-.14,.18,1.88,.13,0x6B7C8B);box(-.13,1.72,-.15,.13,1.77,-.14,0xC54643);
            for(int s=-1;s<=1;s+=2){sphere(s*.51,1.48,0,.2,.22,.21,0x596B7C);limb(s*.52,1.39,0,67,.23,.55,0x7D8D9A);leg(s*.23,s*swing,.26,0x6D7D8B);}
            box(-.27,1.36,-.319,.11,1.48,-.31,0x3A4C59);box(-.21,1.39,-.326,-.12,1.44,-.32,0xF35D47);
            box(.37,1.05,-.8,.60,1.22,-.37,0x3A4B5D);box(.40,1.11,-.86,.46,1.16,-.79,0xEC725D);
            for(int i=0;i<5;i++)box(.08+i*.065,1.20,-.33,.12+i*.065,1.36,-.315,0x2F444F);
            for(int s:new int[]{-1,1}){box(s*.30-.07,1.00,-.255,s*.30+.07,1.14,-.24,0xB3BDC0);box(s*.28-.11,1.2,.26,s*.28+.11,1.58,.36,0x4E6572);}
        }else{
            boolean rebel=faction==Faction.REBELS,officer=role==Role.COMMANDER,scout=role==Role.SCOUT;
            int armor=look!=null&&look.paint>0?accent:rebel?(officer?0x777F56:0xB6B292):0xE3E4DC,cloth=rebel?0x606F56:0x242C32;
            box(-.29,.9,-.18,.29,1.5,.18,cloth);sphere(0,1.3,-.06,.33,.30,.23,armor);
            box(-.25,.91,-.22,.25,1.08,.17,armor);box(-.28,.88,-.23,.28,.96,.20,0x6A7272);
            for(int s=-1;s<=1;s+=2){
                leg(s*.18,s*swing,.23,armor);
                sphere(s*.36,1.44,0,.16,.18,.18,officer?accent:armor);reloadArm(s,.39,1.40,52,.18,.48,armor,pose);
            }
            if(rebel){sphere(0,1.76,-.025,.22,.25,.21,0xC9A17D);sphere(0,1.9,.01,.265,.16,.25,0x747B59);box(-.23,1.78,-.24,.23,1.84,-.2,0x484E42);box(-.27,1.11,-.27,-.03,1.42,-.18,0x7C6D4F);box(.03,1.11,-.27,.27,1.42,-.18,0x7C6D4F);}
            else{
                sphere(0,1.78,0,.265,.275,.23,armor);box(-.24,1.64,-.20,.24,1.81,-.27,armor);
                box(-.22,1.81,-.244,.22,1.875,-.23,0x162A34);
                if(faction==Faction.REPUBLIC){box(-.039,1.63,-.282,.039,1.84,-.269,0x19323D);box(-.042,1.90,-.225,.042,2.08,.13,officer?accent:0xD1D6CE);}
                else{box(-.08,1.65,-.29,.08,1.72,-.27,0x27343C);sphere(-.17,1.62,-.22,.07,.07,.065,0x52616A);sphere(.17,1.62,-.22,.07,.07,.065,0x52616A);}
                if(scout)box(-.27,1.79,-.30,.27,1.90,-.25,0x293B42);
            }
            if(role==Role.MEDIC){box(-.085,1.17,-.31,.085,1.38,-.27,0x79CBC2);box(-.15,1.24,-.315,.15,1.31,-.27,0x79CBC2);}
            if(officer){box(-.33,1.42,-.25,.33,1.50,.20,accent);box(-.27,.59,.19,.27,1.43,.23,0x505567);}
            if(role==Role.HEAVY)box(-.26,1.06,.19,.26,1.48,.40,0x555D5D);
            for(int i=-1;i<=1;i++)box(i*.16-.045,.9,-.24,i*.16+.045,1.02,-.20,0x7B8583);
            if(!rebel){box(-.055,1.26,-.296,.055,1.42,-.28,officer?accent:0xC1CECD);box(-.14,1.10,-.251,.14,1.15,-.24,0x4C6167);}
            for(int s:new int[]{-1,1}){for(int i=0;i<3;i++)box(s*.16-.034,1.66+i*.032,-.286,s*.16+.034,1.678+i*.032,-.273,0x596970);sphere(s*.23,1.15,-.22,.019,.019,.019,0xCBD6D5);}
            for(int i=0;i<5;i++)box(-.22+i*.09,.923,-.25,-.17+i*.09,.95,-.234,0xC4CECB);
        }
        if(armed&&!(droid&&role==Role.HEAVY)){
            box(.12,1.06,-.73,.29,1.21,-.24,0x24313D);box(.16,1.1,role==Role.SCOUT?-1.11:-.96,.25,1.17,-.64,0x4D5B66);
            box(.17,.97,-.4,.24,1.12,-.32,0x202A30);box(.16,1.20,-.65,.24,1.28,-.43,0x182A37);
        }
    }
    /** Equipment silhouettes are visible on the playable third-person character. */
    public static void equippedSoldier(Faction faction,Armor armor,Weapon weapon,double walk,double time,int weaponLevel,int armorLevel,boolean jetting){
        soldierBody(faction,Role.ASSAULT,walk,time,false);
        armorModel(armor,armorLevel,jetting);
        GL11.glPushMatrix();GL11.glTranslated(.25,1.19,-.37);weaponModel(weapon,weaponLevel);GL11.glPopMatrix();
    }
    public static void customizedSoldier(Faction faction,Role role,BattlefrontProgress progress,boolean player,double walk,double time,boolean jetting){
        customizedSoldier(faction,role,progress,player,walk,time,jetting,-1);
    }
    public static void customizedSoldier(Faction faction,Role role,BattlefrontProgress progress,boolean player,double walk,double time,boolean jetting,double reloadProgress){
        Appearance look=progress.appearance(player?null:role);Armor armor=player?progress.armor():progress.armyArmor(role);Weapon weapon=player?progress.weapon():progress.armyWeapon(role);
        BattlefrontReloadPose pose=BattlefrontReloadPose.at(player?reloadProgress:-1);
        soldierBody(faction,role,walk,time,weapon==null,look,pose);
        GL11.glPushMatrix();
        if(faction==Faction.REBELS&&role==Role.SCOUT&&!player)GL11.glScaled(.9,.66,.9);
        if(faction==Faction.SEPARATISTS&&role==Role.HEAVY)GL11.glScaled(1.38,1,1.25);
        armorModel(armor,player?progress.level(armor,ArmorMod.PLATING):0,jetting);
        appearanceModel(faction,look,player?progress.rank()-1:progress.veteran(role));
        if(weapon!=null){GL11.glPushMatrix();GL11.glTranslated(.25,1.19-.13*pose.lift,-.37+.13*pose.lift+pose.seat);GL11.glRotated(-32*pose.lift,0,0,1);GL11.glRotated(23*pose.lift,1,0,0);weaponModel(weapon,progress.level(weapon,WeaponMod.POWER),pose.cellTravel);GL11.glPopMatrix();}
        GL11.glPopMatrix();
    }
    private static void reloadArm(int side,double shoulder,double y,double angle,double width,double length,int color,BattlefrontReloadPose pose){
        if(pose.lift<=0){limb(side*shoulder,y,0,angle,width,length,color);return;}
        double[] hand=pose.point(side>0?0:-.10,side>0?-.10:-.16-.38*pose.cellTravel,side>0?.03:-.18+.12*pose.cellTravel);
        double mix=pose.lift,hx=side*shoulder*(1-mix)+hand[0]*mix,hy=(y-length*Math.cos(Math.toRadians(angle)))*(1-mix)+hand[1]*mix,hz=-length*Math.sin(Math.toRadians(angle))*(1-mix)+hand[2]*mix;
        double ex=(side*shoulder+hx)/2+side*.10*mix,ey=(y+hy)/2-.17*mix,ez=hz*(.5-.12*mix);
        armSegment(side*shoulder,y,0,ex,ey,ez,width,color);sphere(ex,ey,ez,width*.57,width*.57,width*.57,0x343A3F);
        armSegment(ex,ey,ez,hx,hy,hz,width*.8,color);sphere(hx,hy,hz,width*.52,width*.52,width*.52,0x343A3F);
    }
    private static void armSegment(double x,double y,double z,double X,double Y,double Z,double width,int color){
        double dx=X-x,dy=Y-y,dz=Z-z,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        GL11.glPushMatrix();GL11.glTranslated(x,y,z);double horizontal=Math.hypot(dx,dz);
        if(horizontal>.0001)GL11.glRotated(Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,-dy/length)))),-dz,0,dx);
        box(-width/2,-length,-width/2,width/2,0,width/2,color);GL11.glPopMatrix();
    }
    private static void armorModel(Armor armor,int armorLevel,boolean jetting){
        int c=armor.color,edge=0xC3D1D1,dark=0x30434D;
        if(armor!=Armor.FIELD||armorLevel>0){
            double bulk=armor==Armor.HEAVY?.095:armor==Armor.RECON?.025:.055;
            box(-.27,1.1,-.25-bulk,.27,1.48,-.23,c);box(-.25,1.12,.18,.25,1.48,.26+bulk,dark);
            for(int s=-1;s<=1;s+=2){
                box(s*.38-.13,1.40,-.20,s*.38+.13,1.58,.19,c);
                box(s*.38-.11,1.56,-.18,s*.38+.11,1.595,.16,edge);
                box(s*.18-.105,.34,-.135,s*.18+.105,.51,-.105,c);
                box(s*.21-.055,1.18,-.27-bulk,s*.21+.055,1.37,-.25-bulk,dark);
                for(int i=0;i<3;i++)sphere(s*.22,1.18+i*.10,-.279-bulk,.017,.017,.014,edge);
            }
            for(int i=0;i<4;i++)box(-.12+i*.07,1.13,-.29-bulk,-.08+i*.07,1.20,-.27-bulk,0x728C96);
            box(-.28,1.80,-.255,.28,1.84,-.24,c);box(-.22,1.64,-.283,.22,1.665,-.26,dark);
        }
        if(armor==Armor.RECON){box(-.30,1.86,-.28,.30,1.94,-.23,0x273F47);box(-.25,1.87,-.285,-.05,1.9,-.28,0x78C7BF);box(.21,1.36,.22,.33,1.91,.32,0x657C73);}
        if(armor==Armor.HEAVY){
            box(-.36,1.13,.24,.36,1.55,.48,0x696F66);
            for(int s=-1;s<=1;s+=2){box(s*.41-.16,1.33,-.22,s*.41+.16,1.54,.23,c);box(s*.23-.12,.72,-.23,s*.23+.12,.94,-.15,c);}
            for(int i=0;i<6;i++)box(-.28+i*.1,1.14,.48,-.23+i*.1,1.47,.50,0x343F3E);
        }
        if(armor==Armor.COMMANDO){
            box(-.31,.56,.19,.31,1.1,.23,0x385267);box(-.23,1.18,.23,.23,1.53,.43,dark);
            for(int i=0;i<3;i++)box(-.19,1.23+i*.09,.433,.19,1.26+i*.09,.448,c);
            box(.265,1.70,-.02,.31,2.11,.04,c);sphere(.29,2.11,.01,.055,.055,.05,0xA7F1EA);
        }
        if(armor==Armor.BESKAR){
            box(-.21,1.09,.23,.21,1.51,.46,0x697C91);
            for(int s=-1;s<=1;s+=2){GL11.glPushMatrix();GL11.glTranslated(s*.26,1.00,.37);cone(.105,.105,.52,c);GL11.glTranslated(0,.52,0);cone(.105,0,.15,0xC5CED0);GL11.glPopMatrix();
                if(jetting){sphere(s*.26,.87,.37,.09,.22,.09,0x92DFF2);sphere(s*.26,.72,.37,.06,.2,.06,0xFFB55B);}}
            box(-.255,1.83,-.275,.255,1.88,-.245,0x202F40);box(-.033,1.63,-.285,.033,1.85,-.26,0x202F40);
        }
        for(int i=0;i<Math.min(armorLevel,8);i++)box(-.22+i*.058,1.46,-.33,-.18+i*.058,1.49,-.31,0xBCE8DC);
    }
    private static void appearanceModel(Faction faction,Appearance a,int rank){
        int accent=a.color(faction),ink=a.paint==6?0xCFDAD7:0xDBDED0,dark=0x344653;
        if(a.marking>0){
            if(a.marking==1){box(-.055,1.10,-.356,.055,1.47,-.347,ink);box(-.044,1.67,-.293,.044,1.99,-.284,accent);}
            else if(a.marking==2){for(int s:new int[]{-1,1}){box(s*.14-.025,1.12,-.358,s*.14+.025,1.45,-.345,ink);box(s*.10-.025,1.89,-.24,s*.10+.025,2.01,-.21,ink);}}
            else for(int i=0;i<3;i++){GL11.glPushMatrix();GL11.glTranslated(0,1.24+i*.07,-.362);for(int s:new int[]{-1,1}){GL11.glPushMatrix();GL11.glRotated(s*22,0,0,1);box(s<0?-.14:0,-.014,-.005,s<0?0:.14,.014,.005,ink);GL11.glPopMatrix();}GL11.glPopMatrix();}
        }
        if(a.helmet==1){box(.26,1.76,-.03,.29,2.14,.025,dark);box(.18,2.12,-.14,.33,2.20,.04,accent);box(.19,2.14,-.15,.27,2.18,-.141,0x73DAD5);}
        if(a.helmet==2){box(-.29,1.81,-.32,.29,1.94,-.25,dark);for(int s:new int[]{-1,1}){sphere(s*.12,1.865,-.334,.085,.061,.025,0x95D9D0);box(s*.27-.022,1.76,-.2,s*.27+.022,1.94,.10,accent);}}
        if(a.helmet==3){for(int s:new int[]{-1,1}){sphere(s*.255,1.8,.025,.06,.105,.085,dark);box(s*.27-.013,1.82,.035,s*.27+.013,2.22,.065,accent);}box(-.31,1.69,-.30,-.27,1.72,.02,dark);}
        if(a.pack>0){
            box(-.27,1.01,.24,.27,1.53,.48,dark);box(-.22,1.09,.49,.22,1.45,.54,accent);
            if(a.pack==1){box(-.32,1.06,.27,-.27,1.39,.46,0x9B9C85);box(.27,1.06,.27,.32,1.39,.46,0x9B9C85);for(int i=0;i<4;i++)box(-.23,1.14+i*.08,.545,.23,1.16+i*.08,.565,0xB5B8A0);}
            if(a.pack==2){box(-.19,1.51,.35,-.16,2.39,.38,dark);box(.16,1.51,.35,.18,2.17,.38,dark);sphere(-.175,2.40,.365,.03,.03,.03,0x90EEDB);box(-.1,1.26,.55,.12,1.4,.565,0x80C7D1);}
            if(a.pack==3){for(int s:new int[]{-1,1}){GL11.glPushMatrix();GL11.glTranslated(s*.16,1.04,.55);cone(.10,.10,.4,0x89B4B5);GL11.glPopMatrix();box(s*.16-.07,1.2,.63,s*.16+.07,1.26,.65,0xBDF0D7);}}
        }
        if(a.shoulder==1){box(-.55,1.43,-.23,-.25,1.57,.23,accent);box(-.565,1.25,-.20,-.53,1.5,.19,ink);box(-.51,1.55,-.23,-.28,1.58,.23,ink);}
        if(a.shoulder==2){for(int s:new int[]{-1,1}){box(s*.4-.15,1.40,-.23,s*.4+.15,1.58,.23,accent);for(int i=0;i<3;i++)box(s*.4-.13,1.57,-.18+i*.14,s*.4+.13,1.60,-.11+i*.14,dark);}}
        for(int i=0;i<Math.min(5,rank);i++)box(.05+i*.037,1.40,-.374,.071+i*.037,1.435,-.36,0xEDCF87);
        for(int i=0;i<a.wear*7;i++){double xx=Math.sin(i*7.17)*.24,yy=1.12+(i%5)*.067;box(xx,yy,-.376,xx+.045,yy+.012,-.369,i%3==0?0xB9BDB2:0x435157);}
        if(a.wear==2){box(-.21,1.74,-.301,-.11,1.765,-.294,0x596362);box(.12,1.89,-.255,.19,1.908,-.246,0xC2C7BD);}
    }
    /** Eight individual hard-surface weapons, with receivers, rails, optics and feed systems. */
    public static void weaponModel(Weapon weapon,int level){
        weaponModel(weapon,level,0);
    }
    private static void weaponModel(Weapon weapon,int level,double cellTravel){
        int body=weapon==Weapon.BOWCASTER?0x6C5540:weapon==Weapon.ION?0x526C7F:0x35434C,metal=0x83959D,dark=0x1C2B32;
        boolean pistol=weapon==Weapon.DL44,longGun=weapon==Weapon.DLT19X,heavy=weapon==Weapon.DC15LE;
        double front=pistol?-.34:longGun?-1.35:heavy?-.95:weapon==Weapon.E11?-.53:weapon==Weapon.DC15A?-.94:-.75;
        box(-.075,-.065,-.35,.075,.075,.14,body);box(-.055,-.03,front,.055,.035,-.25,metal);
        box(-.038,-.22,-.03,.038,-.025,.065,weapon==Weapon.DL44?0x96704E:dark);
        // Open trigger guard and visible trigger.
        box(-.042,-.13,-.18,.042,-.105,-.035,dark);box(-.041,-.105,-.19,.041,-.035,-.17,dark);box(-.01,-.098,-.1,.01,-.03,-.08,metal);
        if(!pistol){box(-.063,-.06,.14,.063,.047,.38,body);box(-.08,-.13,.35,.08,.055,.41,dark);}
        if(weapon==Weapon.DC15A||weapon==Weapon.A280){for(int i=0;i<5;i++)box(-.08,.012,-.34+i*.059,-.076,.045,-.315+i*.059,metal);}
        if(weapon==Weapon.E11){box(.07,-.06,-.3,.095,.015,.1,metal);box(.082,-.032,.10,.099,-.012,.42,metal);box(.082,-.12,.40,.099,-.012,.44,metal);}
        if(weapon==Weapon.A280){box(-.10,.04,-.32,.10,.105,.11,0x65787C);box(-.085,-.10,.12,.085,.06,.31,0x52676C);box(-.095,-.15,.29,.095,.04,.44,dark);box(-.09,-.05,-.60,.09,.07,-.34,0x496168);}
        if(weapon==Weapon.DC15A){GL11.glPushMatrix();GL11.glTranslated(0,0,-.89);GL11.glRotated(-90,1,0,0);cone(.068,.061,.08,metal);GL11.glPopMatrix();box(-.065,.06,-.19,.065,.095,.10,0x6A7881);}
        if(heavy){for(int i=0;i<8;i++)box(-.079,-.025,-.80+i*.066,.079,.067,-.77+i*.066,body);}
        if(longGun){box(-.052,.077,-.46,.052,.16,.03,dark);sphere(0,.12,-.47,.043,.043,.012,0x67C9D7);for(int s=-1;s<=1;s+=2){GL11.glPushMatrix();GL11.glTranslated(s*.04,-.02,-.65);GL11.glRotated(s*23,0,0,1);box(-.015,-.29,-.02,.015,0,.02,metal);GL11.glPopMatrix();}}
        else{box(-.034,.075,-.24,.034,.125,-.03,dark);sphere(0,.10,-.25,.025,.025,.012,0x70C1CA);}
        if(weapon==Weapon.BOWCASTER){
            box(-.4,-.025,-.35,.4,.03,-.25,0x5F7072);
            for(int s=-1;s<=1;s+=2){for(int i=0;i<5;i++){double xx=s*(.2+i*.055),zz=-.28-Math.sin(i*.45)*.2;box(xx-.028,-.026,zz-.08,xx+.028,.033,zz+.08,metal);}sphere(s*.45,.01,-.48,.095,.095,.095,0x93B7B2);}
            box(-.42,.005,-.15,.42,.014,-.145,0xB3C4BD);
        }
        if(weapon==Weapon.ION){for(int i=0;i<5;i++){GL11.glPushMatrix();GL11.glTranslated(0,0,-.39-i*.065);GL11.glRotated(-90,1,0,0);cone(.09,.09,.035,0x67BBCB);GL11.glPopMatrix();}}
        // The feed component detaches and seats again; each weapon keeps its own silhouette.
        GL11.glPushMatrix();GL11.glTranslated(0,-.38*cellTravel,.12*cellTravel);GL11.glRotated(12*cellTravel,0,0,1);
        if(heavy){sphere(-.02,-.14,-.10,.17,.16,.14,dark);sphere(-.02,-.14,-.20,.12,.12,.024,metal);}
        else if(weapon==Weapon.E11)box(-.17,-.055,-.21,-.06,.034,-.04,dark);
        else if(weapon==Weapon.ION)box(-.08,-.17,-.14,.08,-.05,.045,0x6198AC);
        else{double depth=pistol?.12:.24;box(-.058,-depth,-.24,.058,-.04,-.11,dark);box(-.041,-depth+.025,-.247,.041,-depth+.06,-.239,0x88D9DB);}
        GL11.glPopMatrix();
        GL11.glPushMatrix();GL11.glTranslated(0,0,front-.06);GL11.glRotated(-90,1,0,0);cone(pistol?.075:.063,pistol?.07:.06,.08,dark);GL11.glPopMatrix();
        for(int i=0;i<4;i++)box(-.07,.076,-.3+i*.065,.07,.087,-.278+i*.065,metal);
        for(int i=0;i<Math.min(8,level);i++)box(.078,-.028,-.30+i*.045,.091,.025,-.28+i*.045,0x7DDFE5);
        for(int s=-1;s<=1;s+=2){sphere(s*.076,.04,.04,.012,.012,.012,metal);sphere(s*.076,-.025,-.27,.012,.012,.012,metal);}
    }
    static void prop(BattlefrontWorld.Prop p,boolean forest,BattlefrontMaterials materials){
        GL11.glPushMatrix();GL11.glTranslated(p.x,p.y,p.z);GL11.glRotated(p.variant*39,0,1,0);double s=p.size;
        switch(p.type){
            case "generator":
                box(-1.2,0,-.9,1.2,.3,.9,0x45545B);box(-1,.3,-.72,1,1.9,.72,0x7D8C89);
                for(int i=0;i<8;i++)box(-.86,.45+i*.16,-.76,.5,.52+i*.16,-.73,0x34484E);
                box(.62,.6,-.79,.87,1.65,-.74,0x88D5CB);box(-.8,1.9,-.5,.8,2.25,.5,0x576D73);break;
            case "beacon":box(-.15,0,-.15,.15,1.5,.15,0x596D6C);box(-.20,1.38,-.20,.20,1.62,.20,forest?0xBBD6A1:0xEDC07D);break;
            case "vaporator":
                cone(.45,.33,4.1,0xBBC1B1);for(int i=0;i<4;i++)box(-.65,2.6+i*.35,-.65,.65,2.71+i*.35,.65,0x8B9C9A);
                box(-.13,4.1,-.13,.13,5,.13,0x657D85);break;
            case "log":
                GL11.glTranslated(-3,.6,0);GL11.glRotated(-90,0,0,1);cone(.65,.5,6,0x685240);cone(.45,.45,.04,0xC6A473);break;
            case "tree":
                cone(p.radius,.48,s,0x695443);for(int i=0;i<5;i++){GL11.glPushMatrix();GL11.glRotated(i*72,0,1,0);box(-.24,0,-.2,.24,1.2,3,0x6C5844);GL11.glPopMatrix();}
                materials.bind(BattlefrontMaterials.LEAF);
                for(int i=0;i<5;i++){GL11.glPushMatrix();GL11.glTranslated(0,s*.55+i*s*.09,0);cone(7-i*.85,.1,s*.24,0x385B49+i*0x020301);GL11.glPopMatrix();}
                break;
            case "spire":
                for(int i=0;i<5;i++){GL11.glPushMatrix();GL11.glTranslated(Math.sin(i*.8+p.variant)*p.radius*.15,i*s/5,Math.cos(i*.9)*p.radius*.12);GL11.glRotated(i*11+p.variant*17,0,1,0);cone(p.radius*(1.45-i*.23),p.radius*(1.15-i*.21),s*.235,i%2==0?0xAD7959:0xC08B63);GL11.glPopMatrix();}break;
            case "rock":sphere(0,s*.35,0,s*.7,s*.55,s*.6,forest?0x6B7769:0xB68460);sphere(s*.5,s*.2,.3,s*.4,s*.32,s*.45,forest?0x7B8070:0xC29470);break;
            case "crate":box(-.85,0,-.7,.85,1.4,.7,0x68736F);box(-.9,.15,-.73,.9,.27,.73,0xB8BCAD);box(-.9,1.12,-.73,.9,1.24,.73,0xB8BCAD);box(-.1,.53,-.75,.1,.87,-.73,0x7DDDDD);break;
            case "barricade":box(-2.5,0,-.6,2.5,1.1,.6,0x687775);box(-2.7,.95,-.7,2.7,1.25,.7,0xAEB9AC);break;
            case "antenna":box(-.6,0,-.6,.6,.5,.6,0x6A7A80);cone(.16,.1,7,0xA2B3B4);sphere(0,6.6,0,1,.7,.16,0x8C9C9E);box(-.04,7,-.04,.04,7.4,.04,0xFF9271);break;
            case "factory":
                box(-10,0,-8,10,10,8,0x8D6B58);box(-11,9,-9,11,11,9,0xB38767);
                for(int i=-1;i<=1;i++){box(i*5-1,0,-8.2,i*5+1,7,-7.8,0x382F2A);box(i*5-.8,7,-8.3,i*5+.8,7.3,-8.2,0xE4A968);}
                for(int i=-1;i<=1;i+=2){GL11.glPushMatrix();GL11.glTranslated(i*7,11,2);cone(2.6,1.7,11,0x715347);GL11.glPopMatrix();}break;
            case "bunker":
                box(-10,0,-7,10,5,7,0x697B78);box(-11,4,-8,11,6.2,8,0x7E8B7C);box(-4,0,7.05,4,3.7,7.13,0x1D353B);box(-4,3.5,7.15,4,3.7,7.2,0x82DDD8);
                for(int i=-1;i<=1;i+=2)box(i*7-1,0,7,i*7+1,4,9,0x94A098);break;
            case "walker":
                for(int i=-1;i<=1;i+=2){box(i*1.4-.4,0,-1,i*1.4+.4,.5,1.5,0x788887);limb(i*1.4,4.8,0,i*9,.45,4.6,0xA4AFAC);sphere(i*1.4,4.8,0,.6,.6,.6,0x61716F);}
                box(-1.6,5,-1.7,1.6,8.3,1.5,0x9FAAA4);box(-1.4,6.9,-1.78,1.4,7.5,-1.7,0x263E43);box(-.65,5.9,-3,.65,6.2,-1.4,0x566D6E);break;
            case "village":
                cone(1.7,1,17,0x66513E);box(-5,9,-5,5,9.4,5,0x9A7B53);
                GL11.glTranslated(0,9.4,0);cone(3.1,3.1,3.2,0x95764E);GL11.glTranslated(0,3,0);cone(4.4,0,4,0xB29860);break;
            case "gunship":case "wreck":case "shuttle":
                if(p.type.equals("wreck"))GL11.glRotated(22,0,0,1);
                box(-2,1,-6,2,3.4,6,0xAEBAB5);sphere(0,3,-4.9,1.8,1.45,2,0x607C80);
                box(-8,2,-1,8,2.45,2.5,p.type.equals("shuttle")?0xC2C8BB:0xA87969);
                for(int i=-1;i<=1;i+=2){box(i*2.5-.7,3,0,i*2.5+.7,4.3,5,0xB5BCB3);sphere(i*2.5,3.65,5,.55,.55,.2,0x84DEF3);box(i*1.5-.15,0,-4,i*1.5+.15,1.3,-3,0x536969);}
                if(p.type.equals("shuttle"))box(-.28,3,0,.28,14,5,0xAFBBB3);break;
            case "crawler":box(-5,0,-5,5,1.5,5,0x463E37);sphere(0,3,0,5.3,3,4,0x947E60);box(-.5,4,-9,.5,4.6,-2,0x665D4F);break;
            case "fern":for(int i=0;i<8;i++){GL11.glPushMatrix();GL11.glRotated(i*45,0,1,0);GL11.glRotated(-35,1,0,0);box(-.16,0,0,.16,.06,1.8,0x568365);GL11.glPopMatrix();}break;
            case "grass":
                for(int i=0;i<5;i++){GL11.glPushMatrix();GL11.glRotated(i*73+p.variant,0,1,0);color(forest?0x68825B:0xB9A077);GL11.glBegin(GL11.GL_TRIANGLES);GL11.glNormal3d(0,.4,1);v(-.14,0,0);v(.14,0,0);v(.27,.65+(i%3)*.19,.2);GL11.glEnd();GL11.glPopMatrix();}break;
            default:break;
        }GL11.glPopMatrix();
    }
}
