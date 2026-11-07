package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.feedback.ModuleNotifications;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;

/** One animated surface; content is clipped to its current geometry during every morph. */
final class DynamicIslandRenderer {
    enum View { IDLE,TAB,NOTIFICATION,TARGET }
    private final HudTween width=new HudTween(),height=new HudTween(),transition=new HudTween(),health=new HudTween();
    private final dev.vibe.core.startup.GhostArt logo=new dev.vibe.core.startup.GhostArt();
    private View current=View.IDLE,previous=View.IDLE;
    private Object context;
    private Payload shown,old;
    private Object identity;
    private boolean wasPreview;
    static boolean owns(HudModule hud,String view){return hud!=null&&hud.isEnabled()&&hud.getHudElements().isSelectedIgnoreCase(HudManager.DYNAMIC_ISLAND)&&hud.island.transforms.isSelectedIgnoreCase(view);}
    static View choose(boolean tab,boolean notification,boolean target){return tab?View.TAB:notification?View.NOTIFICATION:target?View.TARGET:View.IDLE;}
    void reset(){shown=old=null;identity=null;current=previous=View.IDLE;context=null;}
    void draw(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,HudOverlayRenderer overlays,TargetHudData target,boolean preview){
        Minecraft mc=Minecraft.getMinecraft();OverlaySettings.DynamicIsland s=hud.island;
        if(wasPreview!=preview){reset();wasPreview=preview;}
        long now=System.nanoTime()/1000000;
        if(!preview&&context!=mc.theWorld){reset();context=mc.theWorld;}
        ModuleNotifications.Entry notification=latest(hud,now);
        View view=choose(s.transforms.isSelectedIgnoreCase("Tab")&&overlays.tabRequested(),
                s.transforms.isSelectedIgnoreCase("Notification")&&notification!=null,
                s.transforms.isSelectedIgnoreCase("TargetHud")&&target!=null);
        if(preview){String sample=s.preview.getValue();view=sample.equals("Tab")?View.TAB:sample.equals("Notification")?View.NOTIFICATION:sample.equals("TargetHud")?View.TARGET:View.IDLE;
            if(view==View.TARGET)target=TargetHudData.sample();if(view==View.NOTIFICATION){ModuleNotifications queue=new ModuleNotifications();queue.add("scaffold","Scaffold",true,now-250);notification=queue.active(now).get(0);}}
        HudOverlayRenderer.TabLayout tab=view==View.TAB?overlays.measureTab(hud,res,e.getScale(),preview):null;
        Payload next=new Payload(view,notification,target,tab);
        FontRenderer f=mc.fontRendererObj;
        int idleW=Math.max(s.idleWidth.getInt(),f.getStringWidth(s.title.getValue())+(s.logo.isEnabled()?46:24));
        int targetW=view==View.TAB?tab.width:view==View.NOTIFICATION?Math.min(320,Math.max(180,f.getStringWidth(notification.name)+90)):view==View.TARGET?Math.max(188,hud.targetHud.width.getInt()):idleW;
        int targetH=view==View.TAB?tab.height:view==View.NOTIFICATION?46:view==View.TARGET?48:s.idleHeight.getInt();
        targetW=Math.min(targetW,Math.max(60,(int)(res.getScaledWidth()/e.getScale())-12));
        Object key=view==View.NOTIFICATION?notification:view==View.TARGET?target.id:view;
        float ms=s.animate.isEnabled()?s.duration.getFloat():0;
        if(shown==null){width.snap(preview?targetW:idleW);height.snap(preview?targetH:s.idleHeight.getInt());transition.snap(1);shown=next;identity=View.IDLE;}
        if(view!=current||!java.util.Objects.equals(identity,key)){
            previous=current;old=shown;current=view;identity=key;transition.snap(0);
        }
        shown=next;
        float blend=preview||!s.animate.isEnabled()?1:transition.update(1,now,ms,s.easing.getValue());
        int w=Math.round(preview?targetW:width.update(targetW,now,ms,s.easing.getValue()));
        int h=Math.round(preview?targetH:height.update(targetH,now,ms,s.easing.getValue()));
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),y=e.top(res,h);e.setBounds(x,y,w,h);
        String theme=manager.theme(e,hud);float radius=Math.min(s.radius.getFloat(),h/2F);
        if("LiquidGlass".equals(theme)||"Lavalamp".equals(theme))manager.drawOverlayGlass(hud,e,s,x,y,w,h,1);
        else{HudShapes.round(x,y,w,h,"Skeet".equals(theme)?1:radius,s.background.getArgb());
            if(s.border.isEnabled())RenderUtils.roundedOutline(x,y,x+w,y+h,"Skeet".equals(theme)?1:radius,1,fade(s.accent.getArgb(),.4F));
            if("Skeet".equals(theme))Gui.drawRect(x+2,y+2,x+w-2,y+3,s.accent.getArgb());}
        try(GuiClip clip=GuiClip.local(x+3,y+3,w-6,h-6)){
            if(old!=null&&blend<.5F)contents(hud,overlays,old,x,y,w,h,Math.max(0,1-blend*2),now);
            float reveal=Math.max(0,Math.min(1,(blend-.28F)/.72F));
            if(reveal>.015F)contents(hud,overlays,shown,x,y,w,h,reveal,now);
        }
        if(blend>=1)old=null;
        GlStateManager.color(1,1,1,1);
    }
    private ModuleNotifications.Entry latest(HudModule hud,long now){
        if(Vibe.getInstance().getFeedback()==null)return null;
        List<ModuleNotifications.Entry> entries=Vibe.getInstance().getFeedback().notifications().active(now);
        return entries.isEmpty()?null:entries.get(entries.size()-1);
    }
    private void contents(HudModule hud,HudOverlayRenderer overlays,Payload data,int x,int y,int w,int h,float a,long now){
        Minecraft mc=Minecraft.getMinecraft();FontRenderer f=mc.fontRendererObj;OverlaySettings.DynamicIsland s=hud.island;
        if(data.view==View.TAB){overlays.tabContents(hud,data.tab,x+(w-data.tab.width)/2,y,a);return;}
        if(data.view==View.IDLE){
            int tw=f.getStringWidth(s.title.getValue()),logoWidth=s.logo.isEnabled()?23:0,left=x+(w-tw-logoWidth)/2;
            if(s.logo.isEnabled())logo.draw(left,y+(h-18)/2F,18,18,false,false,false,false,0xFFFFFFFF,a,now,1);
            text(f,s.title.getValue(),left+logoWidth,y+(h-8)/2F,s.text.getArgb(),a,s.shadow.isEnabled());return;
        }
        if(data.view==View.NOTIFICATION){
            ModuleNotifications.Entry n=data.notification;int c=n.enabled?hud.notifications.enabledColor.getArgb():hud.notifications.disabledColor.getArgb();
            HudShapes.round(x+10,y+(h-22)/2F,22,22,7,fade(c,.16F*a));
            text(f,n.enabled?"+":"-",x+18,y+(h-8)/2F,c,a,false);
            text(f,f.trimStringToWidth(n.name,w-56),x+42,y+10,s.text.getArgb(),a,s.shadow.isEnabled());
            text(f,f.trimStringToWidth(n.message?n.detail:n.enabled?"Enabled":"Disabled",w-56),x+42,y+25,0xFF9DAFC1,a,false);
            HudShapes.round(x+12,y+h-4,(w-24)*n.remaining(now),1,0,fade(c,.65F*a));return;
        }
        TargetHudData target=data.target;
        HudShapes.face(target.skin,x+9,y+8,32,8,hud.targetHud.damageTint.isEnabled()?Math.min(1,target.hurtTicks/10F):0,a);
        String name=target.name;
        dev.vibe.module.impl.client.NameProtectModule protect=Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.client.NameProtectModule.class);
        if(protect!=null&&protect.isEnabled())name=protect.protectText(name);
        text(f,f.trimStringToWidth(name,w-63),x+51,y+9,s.text.getArgb(),a,s.shadow.isEnabled());
        float fraction=health.update(target.fraction(),now,hud.targetHud.smoothing.isEnabled()?180:0,"Cubic");
        HudShapes.round(x+51,y+25,w-63,4,2,fade(0xFF354153,a));
        HudShapes.gradient(x+51,y+25,(w-63)*fraction,4,2,fade(hud.targetHud.health.getArgb(),a),fade(hud.targetHud.gradient.isEnabled()?hud.targetHud.healthEnd.getArgb():hud.targetHud.health.getArgb(),a));
        text(f,String.format(java.util.Locale.ROOT,"%.1f",target.total(hud.targetHud.absorption.isEnabled())),x+51,y+34,0xFFACBDCE,a,false);
    }
    private static int fade(int c,float a){return Math.round((c>>>24)*a)<<24|c&0xFFFFFF;}
    private static void text(FontRenderer f,String t,float x,float y,int c,float a,boolean shadow){if((c>>>24)*a>=4)f.drawString(t,x,y,fade(c,a),shadow);}
    private static final class Payload{
        final View view;final ModuleNotifications.Entry notification;final TargetHudData target;final HudOverlayRenderer.TabLayout tab;
        Payload(View v,ModuleNotifications.Entry n,TargetHudData d,HudOverlayRenderer.TabLayout t){view=v;notification=n;target=d;tab=t;}
    }
}
