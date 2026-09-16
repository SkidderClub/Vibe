package dev.vibe.ui;

import dev.vibe.game.battlefront.*;
import java.awt.Font;
import java.util.Locale;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Screen-edge combat instruments, with a clear center and camera-relative hit feedback. */
public final class BattlefrontCombatHud {
    private static final int WHITE=0xFFF4F5EE,MUTED=0xFFBDC6C5,ALLY=0xFF8BDDD8,ENEMY=0xFFFF8195,GOLD=0xFFFFD38A;
    private final NeverLoseFont regular=new NeverLoseFont("Segoe UI",Font.PLAIN,22),bold=new NeverLoseFont("Segoe UI",Font.BOLD,22);
    private final BattlefrontMapTexture terrain=new BattlefrontMapTexture();
    private int zoom=1;private static final double[] RADII={55,85,130};
    public void cycleZoom(){zoom=(zoom+1)%RADII.length;}
    public double radius(){return RADII[zoom];}
    public void close(){terrain.close();regular.close();bold.close();}
    public void terrain(BattlefrontWorld world,int x,int y,int size){terrain.draw(world,x,y,size,0,0,BattlefrontWorld.TERRAIN_EDGE);}

    public void draw(BattlefrontGame g,BattlefrontIcons icons,int left,int top,int right,int bottom,boolean active){
        final int cx=480,cy=270;
        // A single slim score strip, with objective ownership embedded beneath the tickets.
        plate(cx-143,top+17,286,46,0xAA11191E);
        text(Integer.toString((int)Math.ceil(Math.max(0,g.tickets[g.side]))),cx-126,top+22,1.8f,ALLY,true);
        rightText(Integer.toString((int)Math.ceil(Math.max(0,g.tickets[1-g.side]))),cx+126,top+22,1.8f,ENEMY,true);
        int remaining=Math.max(0,420-(int)g.time);
        centered(g.mode.title.toUpperCase(Locale.ROOT),cx,top+24,.72f,MUTED,false);
        centered(String.format(Locale.ROOT,"%d:%02d",remaining/60,remaining%60),cx,top+40,1,WHITE,true);
        for(int i=0;i<3;i++){BattlefrontGame.Post p=g.posts[i];int x=cx-111+i*103,c=team(g,p.owner);
            rect(x-18,top+57,x+18,top+59,0x80667577);rect(x-18,top+57,x-18+36*p.captureRatio(),top+59,c);
            centered(""+(char)('A'+i),x,top+64,.76f,c,true);}
        compass(g,cx,top+86);

        icons.draw(g.scenario==Scenario.ENDOR?"forest":"planet",left+25,top+24,18,WHITE);
        text(g.scenario==Scenario.ENDOR?"ENDOR":"GEONOSIS",left+51,top+23,1.05f,WHITE,true);
        text(g.faction.title,left+25,top+46,.81f,MUTED,false);
        icons.draw("army",left+24,top+69,15,ALLY);text(g.order+"  /  TAB",left+48,top+70,.8f,ALLY,true);
        text("+"+g.earned+" CR  /  "+g.kills+" eliminations",left+25,top+91,.78f,MUTED,false);

        minimap(g,icons,right-176,top+24,150);
        BattlefrontArchitecture.Building inside=g.world.buildingAt(g.x,g.y,g.z);
        if(inside!=null){text(inside.name,left+25,top+116,.84f,GOLD,true);
            if(Math.hypot(g.x-inside.cacheX(),g.z-inside.cacheZ())<2.8)centered(g.searchedCaches.contains(inside.name)?"SUPPLIES RECOVERED":"[ E ]  Search supply cache",cx,cy+91,.95f,GOLD,true);}

        // Vital information sits on the lower left, equipment on the lower right.
        int vx=left+25,vy=bottom-83;plate(vx-9,vy-8,223,64,0x8411191E);
        icons.draw("support",vx,vy+6,17,g.health<g.maxHealth()*.3?ENEMY:ALLY);
        text(Integer.toString((int)Math.ceil(g.health)),vx+26,vy-2,2.25f,WHITE,true);
        text("/ "+(int)g.maxHealth(),vx+95,vy+12,.86f,MUTED,false);
        meter(vx,vy+31,202,5,g.health/g.maxHealth(),g.health<g.maxHealth()*.3?ENEMY:ALLY);
        icons.draw("shield",vx,vy+43,12,MUTED);text(g.progress.armor().title+"  /  "+Math.round(g.progress.resistance()*100)+"% resistance",vx+20,vy+42,.69f,MUTED,false);
        if(g.progress.armor()==Armor.BESKAR){icons.draw("pack",vx,vy-29,13,ALLY);meter(vx+23,vy-22,122,3,g.jetFuel/4,ALLY);text("FUEL",vx+155,vy-27,.67f,MUTED,false);}
        if(g.protection>0&&g.alive())text("Insertion protection  "+(int)Math.ceil(g.protection)+"s",vx,vy-47,.76f,ALLY,false);
        text("M  Atlas    Z  Radar zoom    F1  Help",left+25,bottom-16,.70f,MUTED,false);

        int wx=right-222,wy=bottom-81;plate(wx-9,wy-8,205,66,0x8411191E);
        icons.draw("weapon",wx,wy+3,34,WHITE);text(g.weapon(),wx+47,wy,.78f,MUTED,true);
        text(Integer.toString(g.ammo),wx+46,wy+15,2.6f,g.ammo==0?ENEMY:WHITE,true);text("/ "+g.magazine(),wx+113,wy+32,1,MUTED,false);
        if(g.reload>0){meter(wx+47,wy+55,139,3,g.reloadProgress(),GOLD);rightText(String.format(Locale.ROOT,"RELOADING  %.1fs",g.reload),right-30,wy-24,.72f,GOLD,true);}
        else text(g.ammo<=g.magazine()/4?"[ R ]  Reload":"[ R ]  Power cell",wx+47,wy+54,.68f,g.ammo<=g.magazine()/4?GOLD:MUTED,false);
        ability(icons,"operations","G",g.grenadeCooldown,wx-8,wy-67,false);
        ability(icons,"support","Q",g.healCooldown,wx+61,wy-67,false);
        ability(icons,"deploy","V",g.kills<6?6-g.kills:g.strikeCooldown,wx+130,wy-67,g.kills<6);
        rightText("ESC  Pause",right-26,bottom-16,.70f,MUTED,false);

        if(active&&g.alive()){
            damageFeedback(g,left,top,right,bottom,cx,cy);
            BattlefrontReticle.draw(g,cx,cy);
            damageNumbers(g,right-left,bottom-top,cx,cy);
            for(int i=0;i<3;i++){BattlefrontGame.Post p=g.posts[i];if(Math.hypot(g.x-p.x,g.z-p.z)<9){
                boolean enemy=false;for(BattlefrontGame.Soldier s:g.soldiers)if(s.alive()&&s.side!=g.side&&Math.hypot(s.x-p.x,s.z-p.z)<8)enemy=true;
                String state=enemy?"CONTESTED":p.control*(g.side==0?1:-1)<0?"NEUTRALIZING":p.owner==g.side?"SECURED":"CAPTURING";
                centered((char)('A'+i)+"  /  "+state,cx,cy+57,.77f,enemy?ENEMY:team(g,p.owner),true);
                meter(cx-76,cy+76,152,3,p.captureRatio(),team(g,p.owner));
            }}
        }
        if(g.noticeTime>g.time){float fade=(float)Math.min(1,(g.noticeTime-g.time)/.4);String msg=regular.fit(g.notice,380);
            centered(msg,cx,bottom-50,.86f,alpha(WHITE,fade),false);}
        if(!g.alive()&&!g.finished){plate(cx-150,cy-40,300,84,0xCD11191E);centered("REDEPLOYING",cx,cy-24,1.6f,WHITE,true);centered("Reinsertion in "+Math.max(1,(int)Math.ceil(g.respawn))+"s",cx,cy+6,1,MUTED,false);}
    }
    private void compass(BattlefrontGame g,int cx,int y){
        double heading=(g.yaw%360+360)%360;
        for(int a=0;a<360;a+=15){double delta=Math.IEEEremainder(a-heading,360);if(Math.abs(delta)>59)continue;int x=(int)(cx+delta*2);rect(x,y,x+1,y+(a%45==0?5:3),a%45==0?0xDDFFFFFF:0x557F979A);
            if(a%45==0)centered(new String[]{"N","NE","E","SE","S","SW","W","NW"}[a/45],x,y+9,.64f,MUTED,false);}
        rect(cx-1,y-5,cx+1,y-1,GOLD);
    }
    private void ability(BattlefrontIcons icons,String icon,String key,double cooldown,int x,int y,boolean locked){
        plate(x,y,62,34,0x8711191E);icons.draw(icon,x+8,y+8,18,cooldown>0?MUTED:WHITE);
        text(cooldown>0?(int)Math.ceil(cooldown)+(locked?"K":"s"):key,x+34,y+10,.9f,cooldown>0?MUTED:WHITE,true);
        if(cooldown==0)rect(x+8,y+32,x+54,y+33,ALLY);
    }
    private void minimap(BattlefrontGame g,BattlefrontIcons icons,int x,int y,int size){
        plate(x-5,y-5,size+10,size+40,0xC411191E);terrain.draw(g.world,x,y,size,g.x,g.z,radius());
        for(int i=1;i<4;i++){double at=i*size/4.;rect(x+at,y,x+at+1,y+size,0x1935DFD1);rect(x,y+at,x+size,y+at+1,0x1935DFD1);}
        for(BattlefrontGame.Soldier s:g.soldiers)if(s.alive()){
            double dx=(s.x-g.x)*size/(radius()*2),dz=(s.z-g.z)*size/(radius()*2);if(Math.abs(dx)>size/2-5||Math.abs(dz)>size/2-5)continue;
            double sx=x+size/2.+dx,sy=y+size/2.+dz;disk(sx,sy,s.side==g.side?2.3:2.6,0xFF10191D);disk(sx,sy,s.side==g.side?1.4:1.7,team(g,s.side));
        }
        for(int i=0;i<2;i++){double dx=(g.world.bases[i][0]-g.x)*size/(radius()*2),dz=(g.world.bases[i][1]-g.z)*size/(radius()*2);
            if(Math.max(Math.abs(dx),Math.abs(dz))<size/2-9)icons.draw("deploy",x+size/2+(int)dx-6,y+size/2+(int)dz-6,12,team(g,i));}
        for(int i=0;i<3;i++){BattlefrontGame.Post p=g.posts[i];mapMarker(g,x,y,size,p.x,p.z,""+(char)('A'+i),team(g,p.owner));}
        if(g.waypoint)mapMarker(g,x,y,size,g.waypointX,g.waypointZ,"+",GOLD);
        double a=Math.toRadians(g.yaw);double px=x+size/2.,py=y+size/2.;
        triangle(px,py,px+Math.sin(a-.45)*28,py-Math.cos(a-.45)*28,px+Math.sin(a+.45)*28,py-Math.cos(a+.45)*28,0x358BDDD8);
        arrow(px,py,a,7,0xFF152229);arrow(px,py,a,5,WHITE);
        plate(x+size/2-8,y-17,16,14,0xDD11191E);centered("N",x+size/2,y-15,.75f,WHITE,true);
        text("Z  "+(int)radius()+"m",x+3,y+size+9,.71f,MUTED,false);rightText("M  ATLAS",x+size-2,y+size+9,.71f,WHITE,true);
        if(g.waypoint)rightText("Rally  "+(int)Math.hypot(g.x-g.waypointX,g.z-g.waypointZ)+"m",x+size,y+size+43,.78f,GOLD,true);
        else{text("ALLY",x+1,y+size+43,.62f,ALLY,true);rightText("HOSTILE",x+size,y+size+43,.62f,ENEMY,true);}
    }
    private void mapMarker(BattlefrontGame g,int x,int y,int size,double wx,double wz,String label,int color){
        double dx=(wx-g.x)*size/(radius()*2),dz=(wz-g.z)*size/(radius()*2),limit=size/2.-11,factor=Math.max(1,Math.max(Math.abs(dx),Math.abs(dz))/limit);
        int px=(int)(x+size/2.+dx/factor),py=(int)(y+size/2.+dz/factor);
        plate(px-7,py-7,14,15,0xE511191E);centered(label,px,py-5,.78f,color,true);
        if(factor>1){double a=Math.atan2(dx,-dz);arrow(px+Math.sin(a)*9,py-Math.cos(a)*9,a,2.4,color);}
    }
    private void damageNumbers(BattlefrontGame g,int width,int height,int cx,int cy){
        for(BattlefrontGame.DamageNumber n:g.damageNumbers){double[] p=BattlefrontProjection.project(g,n.x,n.y+Math.min(.8,n.age*.55),n.z,width/(double)height);if(p==null||Math.abs(p[0])>.94||Math.abs(p[1])>.9)continue;
            int x=(int)(cx+p[0]*width/2),y=(int)(cy-p[1]*height/2)-9;float size=(float)(1.25+.42*Math.max(0,1-n.age/.14));
            centered(Integer.toString((int)Math.ceil(n.amount)),x,y,size,alpha(n.kill?ENEMY:n.head?GOLD:WHITE,Math.min(1,n.life/.25)),true);
        }
    }
    private void damageFeedback(BattlefrontGame g,int left,int top,int right,int bottom,int cx,int cy){
        if(g.hurtFlash>0){int color=alpha(0xFFFF536F,g.hurtFlash/.45*.22);gradient(left,top,left+48,bottom,color,0,true);gradient(right-48,top,right,bottom,0,color,true);gradient(left,top,right,top+36,color,0,false);gradient(left,bottom-36,right,bottom,0,color,false);}
        for(BattlefrontGame.DamageDirection cue:g.damageDirections){double angle=Math.toRadians(cue.yaw-g.yaw)-Math.PI/2,r=68+cue.strength*9;int color=alpha(ENEMY,Math.min(1,cue.life/.35));
            arc(cx,cy,r-1,r+5,angle-.28,angle+.28,alpha(0xFF10151B,Math.min(1,cue.life/.35)));
            arc(cx,cy,r,r+3,angle-.26,angle+.26,color);arrow(cx+Math.cos(angle)*(r+12),cy+Math.sin(angle)*(r+12),angle+Math.PI/2,3.5,color);
        }
    }
    private static int team(BattlefrontGame g,int side){return side<0?GOLD:side==g.side?ALLY:ENEMY;}
    private static int alpha(int c,double opacity){return ((int)(Math.max(0,Math.min(1,opacity))*255)<<24)|(c&0xFFFFFF);}
    private void text(String s,int x,int y,float size,int color,boolean heavy){NeverLoseFont font=heavy?bold:regular;GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(size,size,1);font.draw(s,.65f,.85f,(color&0xFF000000)|0x0B1014);font.draw(s,0,0,color);GlStateManager.popMatrix();}
    private void centered(String s,int x,int y,float size,int color,boolean heavy){text(s,x-(int)((heavy?bold:regular).width(s)*size/2),y,size,color,heavy);}
    private void rightText(String s,int x,int y,float size,int color,boolean heavy){text(s,x-(int)((heavy?bold:regular).width(s)*size),y,size,color,heavy);}
    private static void plate(int x,int y,int w,int h,int c){RenderUtils.roundedRect(x,y,x+w,y+h,4,c);}
    private static void rect(double x,double y,double X,double Y,int c){Gui.drawRect((int)x,(int)y,(int)X,(int)Y,c);}
    private static void meter(int x,int y,int w,int h,double ratio,int color){rect(x-1,y-1,x+w+1,y+h+1,0xA511191E);rect(x,y,x+w,y+h,0xA85E7377);rect(x,y,x+w*Math.max(0,Math.min(1,ratio)),y+h,color);}
    private static void begin(){GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LIGHTING_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(770,771);}
    private static void color(int c){GL11.glColor4d((c>>16&255)/255.,(c>>8&255)/255.,(c&255)/255.,(c>>>24)/255.);}
    private static void triangle(double x,double y,double a,double b,double c,double d,int ink){begin();color(ink);GL11.glBegin(GL11.GL_TRIANGLES);GL11.glVertex2d(x,y);GL11.glVertex2d(a,b);GL11.glVertex2d(c,d);GL11.glEnd();GL11.glPopAttrib();}
    private static void arrow(double x,double y,double a,double size,int c){triangle(x+Math.sin(a)*size,y-Math.cos(a)*size,x+Math.sin(a+2.5)*size*.78,y-Math.cos(a+2.5)*size*.78,x+Math.sin(a-2.5)*size*.78,y-Math.cos(a-2.5)*size*.78,c);}
    private static void disk(double x,double y,double r,int c){begin();color(c);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2d(x,y);for(int i=0;i<=16;i++){double a=i*Math.PI/8;GL11.glVertex2d(x+Math.cos(a)*r,y+Math.sin(a)*r);}GL11.glEnd();GL11.glPopAttrib();}
    private static void arc(double x,double y,double r,double R,double from,double to,int color){begin();color(color);GL11.glBegin(GL11.GL_QUAD_STRIP);for(int i=0;i<=18;i++){double a=from+(to-from)*i/18;GL11.glVertex2d(x+Math.cos(a)*r,y+Math.sin(a)*r);GL11.glVertex2d(x+Math.cos(a)*R,y+Math.sin(a)*R);}GL11.glEnd();GL11.glPopAttrib();}
    private static void gradient(int x,int y,int X,int Y,int first,int second,boolean horizontal){begin();GL11.glShadeModel(GL11.GL_SMOOTH);GL11.glBegin(GL11.GL_QUADS);color(first);GL11.glVertex2i(x,y);color(horizontal?second:first);GL11.glVertex2i(X,y);color(second);GL11.glVertex2i(X,Y);color(horizontal?first:second);GL11.glVertex2i(x,Y);GL11.glEnd();GL11.glPopAttrib();}
}
