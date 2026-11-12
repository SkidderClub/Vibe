package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.features.WorldDraw;
import dev.vibe.module.impl.client.*;
import dev.vibe.ui.GuiClip;
import dev.vibe.ui.effect.EffectState;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import org.lwjgl.opengl.*;

/** Passive instruments share the existing theme surfaces and HUD scaling path. */
public final class DashboardWidgets {
    public static final String[] IDS={"radar","networkgraph","killfeed","sessiontimeline","candle"};
    public static String title(String id){switch(id){case "radar":return "Radar";case "networkgraph":return "Network Graph";case "killfeed":return "Kill Feed";case "sessiontimeline":return "Session Timeline";case "candle":return "Candle";default:return id;}}
    public static boolean contains(String id){return Arrays.asList(IDS).contains(id);}
    private final Minecraft mc=Minecraft.getMinecraft();private final RadarMapCache map=new RadarMapCache();private final NetworkSamples network=new NetworkSamples();
    private Object world,connection;private long sampleAt,lastTick;private boolean stalled;private float lastYaw,lastPitch,bend,velocity;
    public void tick(HudManager manager){
        long now=System.nanoTime()/1000000;if(lastTick!=0&&now-lastTick>250)stalled=true;lastTick=now;
        Object channel=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();if(channel!=connection){connection=channel;network.reset();sampleAt=0;stalled=false;}
        if(mc.thePlayer!=null){if(world!=mc.theWorld){world=mc.theWorld;lastYaw=mc.thePlayer.rotationYaw;lastPitch=mc.thePlayer.rotationPitch;bend=velocity=0;}
            float turn=MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw-lastYaw),pitch=mc.thePlayer.rotationPitch-lastPitch;lastYaw=mc.thePlayer.rotationYaw;lastPitch=mc.thePlayer.rotationPitch;velocity+=(turn*.003F+pitch*.0015F);velocity=(velocity-bend*.2F)*.78F;bend=MathHelper.clamp_float(bend+velocity,-.5F,.5F);
            if(now>=sampleAt){NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());double ping=info==null?Double.NaN:Math.max(0,info.getResponseTime());network.add(ping,stalled);stalled=false;sampleAt=now+500;}
        }
        HudModule hud=Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        if(hud!=null&&hud.isEnabled()&&manager.isEnabled("radar")&&hud.dashboards.get("radar").mode.is("Map"))map.tick(hud.dashboards.get("radar").range.getDouble()/hud.dashboards.get("radar").zoom.getDouble()*2.9);else map.suspend();
    }
    public void draw(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,boolean preview){DashboardSettings s=hud.dashboards.get(e.getId());if(s==null)return;String id=e.getId(),theme=manager.theme(e,hud);int step=id.equals("killfeed")&&theme.equals("Fortnite")?17:24;int w=id.equals("candle")?60:s.width.getInt(),h=id.equals("candle")?88:id.equals("networkgraph")?s.height.getInt():id.equals("radar")?s.height.getInt():s.rows.getInt()*step+8;
        List<SessionJournal.Entry> entries=SessionJournal.INSTANCE.entries();long now=System.nanoTime()/1000000;
        if(id.equals("killfeed")||id.equals("sessiontimeline")){entries.removeIf(v->id.equals("killfeed")?!(v.kind.equals("Kill")||v.kind.equals("Death"))||now-v.time>s.lifetime.getDouble()*1000:now-v.time>s.lifetime.getDouble()*1000);if(!preview&&entries.isEmpty()){e.setBounds(e.left(res,w),e.top(res,0),w,0);return;}h=Math.min(s.rows.getInt(),preview?3:entries.size())*step+8;}
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),y=e.top(res,h);e.setBounds(x,y,w,h);
        if(!id.equals("killfeed")||!theme.equals("CounterStrike")&&!theme.equals("Fortnite"))HudOverlayRenderer.panel(manager,hud,e,s,x,y,w,h);
        if(id.equals("radar")){if(preview&&s.mode.is("Map")&&mc.theWorld!=null)map.tick(s.range.getDouble()/s.zoom.getDouble()*2.9);radar(s,x,y,w,h,preview);}
        else if(id.equals("networkgraph"))graph(s,x,y,w,h,preview);
        else if(id.equals("candle"))candle(s,x,y,w,h,preview);
        else events(manager.theme(e,hud),s,x,y,w,h,entries,preview,id.equals("killfeed"),now);
    }
    private void text(DashboardSettings s,String text,float x,float y,int color){mc.fontRendererObj.drawString(text,x,y,color,s.shadow.isEnabled());}
    private String protect(String text){NameProtectModule n=Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);return n==null?text:n.protectText(text);}
    private void radar(DashboardSettings s,int x,int y,int w,int h,boolean preview){float cx=x+w/2F,cy=y+h/2F,scale=(float)(Math.min(w,h)/2./(s.range.getDouble()/s.zoom.getDouble())),angle=s.rotate.isEnabled()&&mc.thePlayer!=null?180-mc.thePlayer.rotationYaw:0;
        try(GuiClip clip=GuiClip.local(x+3,y+3,w-6,h-6)){
            if(s.mode.is("Map")&&map.ready&&mc.thePlayer!=null){GL11.glPushMatrix();try{GL11.glTranslatef(cx,cy,0);GL11.glRotatef(angle,0,0,1);float dx=(float)(map.x-mc.thePlayer.posX)*scale,dz=(float)(map.z-mc.thePlayer.posZ)*scale,span=RadarMapCache.SIZE*map.stride*scale;try(EffectState state=new EffectState()){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,map.texture());GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glColor4f(1,1,1,.85F);GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(dx-span/2,dz-span/2);GL11.glTexCoord2f(1,0);GL11.glVertex2f(dx+span/2,dz-span/2);GL11.glTexCoord2f(1,1);GL11.glVertex2f(dx+span/2,dz+span/2);GL11.glTexCoord2f(0,1);GL11.glVertex2f(dx-span/2,dz+span/2);GL11.glEnd();}}finally{GL11.glPopMatrix();}}
            else if(s.mode.is("Map"))text(s,"Loading terrain…",x+9,cy-4,0xFF9CA9BB);
            else{for(int i=1;i<=3;i++)circle(cx,cy,Math.min(w,h)*.145F*i,0x383E607D);Gui.drawRect(x+4,(int)cy,x+w-4,(int)cy+1,0x253F607B);Gui.drawRect((int)cx,y+4,(int)cx+1,y+h-4,0x253F607B);
                if(s.sweep.isEnabled()){double a=(System.nanoTime()/1E9*.8)%(Math.PI*2);try(EffectState st=new EffectState()){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);GL11.glBegin(GL11.GL_TRIANGLE_FAN);WorldDraw.color(s.accent.getArgb()&0xFFFFFF|0x45000000);GL11.glVertex2f(cx,cy);for(int i=0;i<=32;i++){WorldDraw.color(s.accent.getArgb()&0xFFFFFF|((i*48/32)<<24));double b=a-.65+i*.65/32;GL11.glVertex2d(cx+Math.cos(b)*Math.min(w,h)*.47,cy+Math.sin(b)*Math.min(w,h)*.47);}GL11.glEnd();}}
            }
            if(mc.theWorld!=null&&mc.thePlayer!=null){int drawn=0;double rad=Math.toRadians(angle);for(Entity entity:mc.theWorld.loadedEntityList){if(entity==mc.thePlayer||!(entity instanceof EntityPlayer&&s.players.isEnabled()||entity instanceof EntityItem&&s.items.isEnabled()))continue;double dx=(entity.posX-mc.thePlayer.posX)*scale,dz=(entity.posZ-mc.thePlayer.posZ)*scale;if(Math.hypot(dx,dz)>Math.min(w,h)*.48)continue;if(++drawn>128)break;
                    float px=cx+(float)(dx*Math.cos(rad)-dz*Math.sin(rad)),py=cy+(float)(dx*Math.sin(rad)+dz*Math.cos(rad));int c=entity instanceof EntityItem?s.accent.getArgb():Vibe.getInstance().getFriendManager().isFriend(entity.getName())?s.friends.getArgb():Vibe.getInstance().getTargetManager().isTarget(entity.getName())?s.targets.getArgb():s.hostile.getArgb();marker(s,entity,px,py,c,angle+entity.rotationYaw);}}
            else if(preview){marker(s,null,cx-22,cy-31,s.friends.getArgb(),45);marker(s,null,cx+36,cy+12,s.targets.getArgb(),210);}
            marker(s,null,cx,cy,s.accent.getArgb(),s.rotate.isEnabled()?180:mc.thePlayer==null?180:mc.thePlayer.rotationYaw);
        }
    }
    private void marker(DashboardSettings s,Entity entity,float x,float y,int color,float angle){float size=s.markerSize.getFloat();if(s.markers.is("Head")&&entity instanceof EntityPlayer){face(entity.getUniqueID(),(int)(x-size),(int)(y-size),(int)(size*2));return;}if(s.markers.is("Dot")||s.markers.is("Head")){HudShapes.round(x-size/2,y-size/2,size,size,size/2,color);return;}try(EffectState state=new EffectState()){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);WorldDraw.color(color);GL11.glPushMatrix();GL11.glTranslatef(x,y,0);GL11.glRotatef(angle,0,0,1);if(s.markers.is("Square")){GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(-size/2,-size/2);GL11.glVertex2f(size/2,-size/2);GL11.glVertex2f(size/2,size/2);GL11.glVertex2f(-size/2,size/2);}else{GL11.glBegin(GL11.GL_TRIANGLES);GL11.glVertex2f(0,size);GL11.glVertex2f(-size*.65F,-size*.65F);GL11.glVertex2f(size*.65F,-size*.65F);}GL11.glEnd();GL11.glPopMatrix();}}
    private static void circle(float x,float y,float radius,int color){try(EffectState state=new EffectState()){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);WorldDraw.color(color);GL11.glLineWidth(1);GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<64;i++){double a=i*Math.PI/32;GL11.glVertex2d(x+Math.cos(a)*radius,y+Math.sin(a)*radius);}GL11.glEnd();}}
    private void graph(DashboardSettings s,int x,int y,int w,int h,boolean preview){double ping=network.count==0?Double.NaN:network.ping[network.at(network.count-1)],jitter=network.count==0?Double.NaN:network.jitter[network.at(network.count-1)];Object channel=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager().channel();double tps=ServerTickRate.value(channel,System.nanoTime()/1000000);String head="Ping "+number(ping," ms")+(s.jitter.isEnabled()?"   Jitter "+number(jitter," ms"):"");text(s,mc.fontRendererObj.trimStringToWidth(head,w-14),x+7,y+7,s.text.getArgb());text(s,"TPS estimate "+number(tps,""),x+7,y+h-13,0xFF9CAAB8);
        int left=x+7,top=y+22,bottom=y+h-20;double max=100;for(double n:network.ping)if(!Double.isNaN(n))max=Math.max(max,n*1.1);
        try(EffectState st=new EffectState()){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1.4F);for(int pass=0;pass<(s.jitter.isEnabled()?2:1);pass++){WorldDraw.color(pass==0?s.accent.getArgb():s.friends.getArgb());boolean strip=false;int count=preview?60:network.count;for(int i=0;i<count;i++){double value=preview?35+Math.sin(i*.25)*(pass==0?12:3):pass==0?network.ping[network.at(i)]:network.jitter[network.at(i)];if(Double.isNaN(value)){if(strip){GL11.glEnd();strip=false;}continue;}if(!strip){GL11.glBegin(GL11.GL_LINE_STRIP);strip=true;}GL11.glVertex2d(left+i*(w-14.)/Math.max(1,count-1),bottom-Math.min(1,value/max)*(bottom-top));}if(strip)GL11.glEnd();}
            if(s.stalls.isEnabled()){GL11.glBegin(GL11.GL_LINES);WorldDraw.color(0xBFFF7D72);for(int i=0;i<network.count;i++)if(network.stall[network.at(i)]){double xx=left+i*(w-14.)/Math.max(1,network.count-1);GL11.glVertex2d(xx,top);GL11.glVertex2d(xx,bottom);}GL11.glEnd();}}
    }
    private static String number(double d,String suffix){return Double.isNaN(d)?"—":String.format(Locale.ROOT,"%.0f%s",d,suffix);}
    private void events(String theme,DashboardSettings s,int x,int y,int w,int h,List<SessionJournal.Entry> entries,boolean preview,boolean feed,long now){if(preview){entries=new ArrayList<>();entries.add(new SessionJournal.Entry("Kill","You","Opponent",null,null,null,true,"You → Opponent","You"));entries.add(new SessionJournal.Entry(feed?"Kill":"Bookmark",feed?"Friend":"",feed?"Opponent":"",null,null,null,false,feed?"Friend → Opponent":"Bookmark","You"));entries.add(new SessionJournal.Entry(feed?"Kill":"Profile","You","Target",null,null,null,true,feed?"You → Target":"Profile: Default","You"));}
        for(int i=0;i<Math.min(s.rows.getInt(),entries.size());i++){SessionJournal.Entry e=entries.get(i);int yy=y+4+i*(feed&&theme.equals("Fortnite")?17:24);float alpha=preview?1:Math.max(0,Math.min(1,(float)((s.lifetime.getDouble()*1000-(now-e.time))/700.)));if(alpha<=0)continue;int ink=(s.text.getArgb()&0xFFFFFF)|((int)((s.text.getArgb()>>>24)*alpha)<<24),left=x+7;
            if(feed&&(theme.equals("CounterStrike")||theme.equals("Fortnite"))){KillFeedRenderer.draw(theme,s,e,x+4,yy,w-8,alpha,preview);continue;}
            if(feed&&s.avatars.isEnabled()){face(e.actorId,left,yy+3,14);left+=18;}
            int right=x+w-7;if(feed&&s.weapons.isEnabled()&&e.weapon!=null){dev.vibe.ui.GuiRenderState.prepare(false);mc.getRenderItem().renderItemAndEffectIntoGUI(e.weapon,right-16,yy+2);right-=20;}
            String label=protect(e.label);if(feed&&!e.confirmed)label="~ "+label;if(!feed&&!label.startsWith(e.kind))label=e.kind+" · "+label;label=mc.fontRendererObj.trimStringToWidth(label,Math.max(10,right-left));text(s,label,left,yy+6,ink);
            if(!e.confirmed&&feed)Gui.drawRect(x+4,yy+20,x+w-4,yy+21,0x285E6A79);
        }
    }
    private void face(UUID uuid,int x,int y,int size){NetworkPlayerInfo info=uuid==null||mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(uuid);ResourceLocation skin=info==null?net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkinLegacy():info.getLocationSkin();mc.getTextureManager().bindTexture(skin);GlStateManager.color(1,1,1,1);Gui.drawScaledCustomSizeModalRect(x,y,8,8,8,8,size,size,64,64);Gui.drawScaledCustomSizeModalRect(x,y,40,8,8,8,size,size,64,64);}
    private void candle(DashboardSettings s,int x,int y,int w,int h,boolean preview){float cx=x+w/2F,base=y+h-11;HudShapes.gradient(cx-11,base-36,22,36,4,0xFFE1D5BE,0xFFA59883);HudShapes.round(cx-12,base-39,24,7,3,0xFFF1E3C8);Gui.drawRect((int)cx-1,(int)base-44,(int)cx+1,(int)base-37,0xFF38322D);double t=System.nanoTime()/1E9;float lean=(preview?(float)Math.sin(t)*.15F:bend)*s.flameStrength.getFloat();int c=s.flame.getArgb();for(int i=3;i>=0;i--)flame(cx,base-43,10+i*3,24+i*2,lean,c,i==0?.9F:.06F);flame(cx,base-43,5,16,lean,0xFFFFF5BF,.95F);}
    private static void flame(float x,float y,float width,float height,float lean,int color,float opacity){try(EffectState st=new EffectState()){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);WorldDraw.color((color&0xFFFFFF)|((int)((color>>>24)*opacity)<<24));GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(x,y-height*.35F);for(int i=0;i<=40;i++){double a=i*Math.PI/20;float yy=(float)((Math.sin(a)+1)*.5),xx=(float)Math.cos(a)*width*.5F*(.9F-yy*.6F);GL11.glVertex2f(x+xx+lean*height*yy*yy,y-height*yy);}GL11.glEnd();}}
}
