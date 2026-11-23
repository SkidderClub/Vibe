package dev.vibe.ui.clickgui;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.setting.ModeSetting;
import dev.vibe.ui.*;
import java.util.*;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;
import static dev.vibe.ui.clickgui.DropdownStyle.*;

/** Three reference layouts sharing the host's real setting controls and config model. */
final class DropdownWorkspace {
    private static final Map<String,Map<String,int[]>> SAVED = new HashMap<>();
    private static final Map<String,int[]> SAVED_SIZES = new HashMap<>();
    private static final Map<String,Float> SAVED_HORIZONTAL = new HashMap<>();
    private final List<Panel> panels = new ArrayList<>();
    private final Map<Category,Panel> categories=new EnumMap<>(Category.class);
    private final Map<Module,Float> enabled = new IdentityHashMap<>();
    private final Map<Module,Float> hovering = new IdentityHashMap<>();
    private final Map<Module,Float> diabloSettings=new IdentityHashMap<>();
    private float frameSeconds;
    private String theme = "";
    private DropdownStyle style;
    private int width,height,panelWidth,horizontalMax;
    private float horizontal,targetHorizontal;
    private Panel dragging,scrolling;
    private ScrollbarDrag scrollbar;
    private int dragX,dragY;
    private long previous,opened;
    private ModeSetting mode;
    private int modeX,modeY,modeW,modeH,modeScroll;
    private Module hovered;
    private long hoverAt;
    private Module oldHovered;

    void draw(VibeClickGui host,int w,int h,int mx,int my,ClickGuiModule settings) {
        if(!theme.equals(settings.getTheme().getValue())) {save();panels.clear();categories.clear();theme=settings.getTheme().getValue();mode=null;release();opened=System.nanoTime();}
        boolean resized=width!=0&&(width!=w||height!=h); width=w;height=h;
        if(resized){release();mode=null;}
        long now=System.nanoTime();float dt=previous==0?1/60F:Math.min(.05F,(now-previous)/1e9F);previous=now;
        float ease=1-(float)Math.exp(-20*dt);style=new DropdownStyle(settings,System.currentTimeMillis());frameSeconds=dt;
        int count=Category.values().length;
        panelWidth=style.novo||style.diablo?100:Math.max(116,Math.min(style.wurst?126:148,(width-24-12*(count-1))/count));
        int gap=style.diablo?5:style.novo?10:12;
        if(panels.isEmpty()) {
            Map<String,int[]> saved=SAVED.get(theme);
            horizontal=targetHorizontal=SAVED_HORIZONTAL.getOrDefault(theme,0F);
            int[] savedSize=SAVED_SIZES.get(theme);
            boolean reflow=savedSize!=null&&(savedSize[0]!=width||savedSize[1]!=height);
            for(int i=0;i<count;i++) {
                Category category=Category.values()[i];
                int x=(style.diablo?10:style.novo?75:12)+i*(panelWidth+gap),y=style.diablo?5:style.novo?10:style.packet?36:style.suizide?20:8;
                int[] old=saved==null?null:saved.get(category.name());Panel panel=new Panel(category,i,old==null||reflow?x:old[0],clamp(old==null||reflow?y:old[1],4,height-54));
                panel.collapsed=old!=null&&old[2]!=0;panels.add(panel);categories.put(category,panel);
                if(old!=null&&old.length>3)panel.scroll=panel.target=Math.max(0,old[3]);
            }
        }
        if(resized) {for(Panel p:panels){p.x=12+p.order*(panelWidth+gap);p.y=clamp(p.y,4,Math.max(4,height-70));}}
        int far=0;for(Panel p:panels)far=Math.max(far,p.x+panelWidth+12);
        horizontalMax=Math.max(0,far-width);targetHorizontal=Math.max(0,Math.min(horizontalMax,targetHorizontal));horizontal+=(targetHorizontal-horizontal)*ease;
        if(dragging!=null){dragging.x=clamp(mx+Math.round(horizontal)-dragX,0,Math.max(width,far)-panelWidth);dragging.y=clamp(my-dragY,4,height-54);}
        if(scrolling!=null&&scrollbar!=null)scrolling.target=scrolling.scroll=scrollbar.at(my);
        hovered=null;
        for(Panel p:panels)p.modules.clear();
        for(Module module:ClickGuiModule.sortedModules(Vibe.getInstance().getModuleManager().getModules()))categories.get(module.getCategory()).modules.add(module);
        for(Panel p:panels) {
            p.content=p.detail==null?2:host.dropdownSettingsHeight(p.detail)+4;for(Module module:p.modules)p.content+=style.rowHeight+settingsHeight(host,module);
            int margin=horizontalMax>0?37:22;
            int minimum=style.headerHeight+style.rowHeight+style.footerHeight()+2;
            if(!p.collapsed)p.y=clamp(p.y,4,height-minimum-margin);
            int available=Math.max(minimum,height-p.y-margin);
            int fullHeight=Math.min(available,style.headerHeight+p.content+style.footerHeight());
            int goal=p.collapsed?style.headerHeight:fullHeight;
            if(style.fan){
                if(p.animatedHeight==0)p.animatedHeight=style.headerHeight;
                p.animatedHeight+=(goal-p.animatedHeight)*ease;
                p.h=Math.round(p.animatedHeight);
            }else p.h=goal;
            p.max=Math.max(0,p.content-Math.max(0,(style.fan?fullHeight:p.h)-style.headerHeight-style.footerHeight()));p.target=Math.max(0,Math.min(p.max,p.target));p.scroll=Math.max(0,Math.min(p.max,p.scroll+(p.target-p.scroll)*ease));
        }
        for(Panel p:panels)drawPanelGlow(p);
        for(Panel p:panels) drawPanel(host,p,mx,my,ease,now);
        if(horizontalMax>0)drawNavigation(mx,my);
        if(mode!=null&&!inlineMode())drawMode(mx,my);
        if(hovered!=oldHovered){oldHovered=hovered;hoverAt=System.currentTimeMillis();}
        if(mode==null&&dragging==null&&hovered!=null&&System.currentTimeMillis()-hoverAt>750)host.dropdownTooltip(hovered,mx,my);
    }
    private int settingsHeight(VibeClickGui host,Module module){
        if(!style.diablo&&!style.fan)return !style.wurst&&host.dropdownExpanded(module)?host.dropdownSettingsHeight(module):0;
        int target=host.dropdownExpanded(module)?host.dropdownSettingsHeight(module):0;
        float previous=diabloSettings.getOrDefault(module,style.fan?0F:(float)target),delta=Math.max(200,Math.abs(target-previous)/.22F)*frameSeconds;
        float next=previous<target?Math.min(target,previous+delta):Math.max(target,previous-delta);diabloSettings.put(module,next);return Math.round(next);
    }
    private void drawPanelGlow(Panel p){
        if(style.wurst||style.diablo)return;
        int x=screenX(p);if(x+panelWidth<0||x>width)return;
        if(style.novo){
            if(style.settings.getNovolineGlow().isEnabled())SoftPanelGlow.draw(x,p.y,panelWidth,p.h,2,9,RenderUtils.alpha(style.accent(.5F,p.y),24));}
        else SoftPanelGlow.draw(x,p.y,panelWidth,p.h,style.radius,12,RenderUtils.alpha(style.accent(.5F,p.y),style.light?42:53));
    }
    private int screenX(Panel p){return p.x-Math.round(horizontal);}
    private void drawPanel(VibeClickGui host,Panel p,int mx,int my,float ease,long now) {
        style.category=p.category;
        int x=screenX(p),y=p.y;
        if(x+panelWidth<0||x>width)return;
        int radius=style.radius;
        float intro=Math.min(1,(now-opened)/160e6F);
        // Entrance changes the surface tint, leaving input and text precisely aligned.
        int body=style.novo?style.body:RenderUtils.blend(style.body,style.light?0xFFF2F9F3:0xFF24242A,(1-intro)*.15F);
        rect(x-(style.novo?1:0),y,panelWidth+(style.novo?2:0),p.h,radius,body);
        if(style.suizide&&style.light&&!p.collapsed) {
            // Subtle mint at the base of the translucent light panels in the reference.
            dev.vibe.ui.MenuRoundedRenderer.verticalGradient(x,y,panelWidth,p.h,radius,body,0xE5C7FFE6);
        }
        if(style.diablo){try(GuiClip clip=GuiClip.local(x,y,panelWidth,style.headerHeight)){rect(x,y,panelWidth,style.headerHeight+5,5,style.accent(0,y));}} else if(style.suizide) {
            try(GuiClip clip=GuiClip.local(x,y,panelWidth,style.headerHeight)){roundedGradient(x,y,panelWidth,style.headerHeight+(p.collapsed?0:radius),radius,style.accent(0,y),style.accent(1,y));}
        } else if(style.fan) {
            try(GuiClip clip=GuiClip.local(x,y,panelWidth,style.headerHeight)){
                roundedGradient(x,y,panelWidth,style.headerHeight+(p.h>style.headerHeight?radius:0),radius,0xFF34385C,0xFF2D3150);
                rect(x+8,y+6,3,13,2,style.accent(0,y));
            }
        } else {
            try(GuiClip clip=GuiClip.local(x,y,panelWidth,style.headerHeight)){rect(x,y,panelWidth,style.headerHeight+(p.collapsed?0:radius),radius,style.novo?0xFF1D1D1D:0xF7111113);}
        }
        String title=p.detail==null?p.category.getLabel():p.detail.getName();
        if(style.wurst){net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.drawString(title,x+4,y+3,0xFFF0F0F0);net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.drawString(p.detail==null?(p.collapsed?"+":"-"):"x",x+panelWidth-10,y+3,0xFFF0F0F0);}
        else if(style.diablo){
            dev.vibe.ui.diablo.DiabloFonts.text(dev.vibe.ui.diablo.DiabloFonts.LABEL,title,x+17,y+.5F+(style.headerHeight-dev.vibe.ui.diablo.DiabloFonts.height(dev.vibe.ui.diablo.DiabloFonts.LABEL))/2F,panelWidth-20,0xFFFFFFFF);
            dev.vibe.ui.diablo.DiabloFonts.text(dev.vibe.ui.diablo.DiabloFonts.ICON,DiabloStyle.icon(p.category),x+3,y+1+(style.headerHeight-dev.vibe.ui.diablo.DiabloFonts.height(dev.vibe.ui.diablo.DiabloFonts.ICON))/2F,14,0xFFFFFFFF);
        }
        else if(style.novo){
            dev.vibe.ui.novoline.NovolineFonts.text(dev.vibe.ui.novoline.NovolineFonts.HEADER,title,x+4,y+4,panelWidth-19,0xFFFFFFFF);
            String icon=p.category==Category.COMBAT?"D":p.category==Category.MOVEMENT?"A":p.category==Category.VISUAL?"C":p.category==Category.WORLD?"B":p.category==Category.MEME?"F":"G";
            dev.vibe.ui.novoline.NovolineFonts.ICON.draw(icon,x+88,y+1,0xFFFFFFFF);
        }
        else if(style.fan){ScrollingText.draw(HEADER,title,x+16,y+4,panelWidth-35,style.text,ScrollingText.Alignment.LEFT);NeverLoseStyle.chevron(x+panelWidth-13,y+11,!p.collapsed,style.muted);}
        else ScrollingText.draw(HEADER,title,x+9,y+4,panelWidth-(p.collapsed?30:18),0xFFF7F7F9,ScrollingText.Alignment.CENTER);
        if(p.collapsed&&(!style.fan||p.h<=style.headerHeight+style.footerHeight())){if(style.diablo)RenderUtils.roundedOutline(x,y,x+panelWidth,y+p.h,5,4,style.accent(0,y));if(!style.wurst&&!style.novo&&!style.diablo&&!style.fan)NeverLoseStyle.chevron(x+panelWidth-12,y+11,false,0xFFAAAAAE);return;}
        int top=y+style.headerHeight,bottom=y+p.h-style.footerHeight();
        if (bottom <= top) return;
        host.dropdownClip(p.name,top,bottom);
        try(GuiClip clip=GuiClip.local(x,top,panelWidth,bottom-top)) {
            if(p.detail!=null)host.drawDropdownSettings(p.detail,x+4,top+2-Math.round(p.scroll),panelWidth-8,style,p.name);
            int rowY=top+(style.novo||style.diablo?0:2)-Math.round(p.scroll);
            for(Module module:p.modules) {
                boolean expanded=!style.wurst&&host.dropdownExpanded(module),over=accepts(p.name,mx,my)&&my>=rowY&&my<rowY+style.rowHeight;
                float on=enabled.getOrDefault(module,module.isEnabled()?1F:0F);on+=((module.isEnabled()?1:0)-on)*ease;enabled.put(module,on);
                float hover=hovering.getOrDefault(module,0F);hover+=((over?1:0)-hover)*ease;hovering.put(module,hover);
                if(rowY+style.rowHeight>=top&&rowY<bottom) {
                    int inset=style.suizide?4:0,rx=x+inset,rw=panelWidth-inset*2;
                    if(style.fan){
                        int tile=RenderUtils.blend(0xFF292C3D,0xFF363D57,hover);
                        roundedGradient(rx+4,rowY+1,rw-8,style.rowHeight-2,4,
                                RenderUtils.blend(tile,style.accent(0,rowY),on*.72F),RenderUtils.blend(tile,0xFF383E7B,on));
                        if(on>.01F)rect(rx+6,rowY+5,2,style.rowHeight-10,1,RenderUtils.alpha(style.accent(1,rowY),Math.round(255*on)));
                    } else if(style.diablo){Gui.drawRect(rx,rowY,rx+rw,rowY+style.rowHeight,module.isEnabled()?style.accent(0,rowY):over?0xFF1C1C1C:0xFF1A1A1A);} else if(style.packet) {
                        if(on>.01F) {
                            int c1=RenderUtils.blend(body,style.accent(0,rowY),on),c2=RenderUtils.blend(body,style.accent(1,rowY),on);
                            gradient(rx,rowY,rw,style.rowHeight,c1,c2);
                        } else if(hover>.01F) Gui.drawRect(rx,rowY,rx+rw,rowY+style.rowHeight,RenderUtils.alpha(0xFFFFFFFF,Math.round(hover*12)));
                    } else if(style.suizide) {
                        int inactive=RenderUtils.blend(style.row,style.light?0xFFFFFFFF:0xFF30303A,hover*.6F);
                        roundedGradient(rx,rowY+1,rw,style.rowHeight-3,5,RenderUtils.blend(inactive,style.accent(0,rowY),on),RenderUtils.blend(inactive,style.accent(1,rowY),on));
                    } else if(style.novo){Gui.drawRect(rx,rowY,rx+rw,rowY+style.rowHeight,RenderUtils.blend(RenderUtils.blend(0xFF282828,0xFF1D1D1D,hover),style.accent(0,rowY),on));
                    } else if(hover>.01F)Gui.drawRect(rx,rowY,rx+rw,rowY+style.rowHeight,RenderUtils.alpha(0xFFFFFFFF,Math.round(hover*9)));
                    int color=style.novo?0xFFFFFFFF:module.isEnabled()?0xFFFFFFFF:style.text;
                    String name=host.dropdownBinding(module)?"Press a key...":module.getName();
                    int arrowSpace=!style.diablo&&!style.packet&&host.dropdownHasSettings(module)?13:0;
                    int leftReserve=style.novo?arrowSpace:0;
                    // One text pass: the optional glow belongs behind the panel, never offset glyphs.
                    if(style.wurst){Gui.drawRect(rx,rowY,rx+rw,rowY+style.rowHeight, module.isEnabled()?0xB040AA40:over?0xB0606060:0x70404040);Gui.drawRect(rx,rowY,rx+rw,rowY+1,0x80101010);net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.drawString(net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.trimStringToWidth(name,rw-17),rx+3,rowY+4,0xFFF0F0F0);}
                    else if(style.diablo)dev.vibe.ui.diablo.DiabloFonts.text(dev.vibe.ui.diablo.DiabloFonts.LABEL,name,rx+3,rowY+.5F+(style.rowHeight-dev.vibe.ui.diablo.DiabloFonts.height(dev.vibe.ui.diablo.DiabloFonts.LABEL))/2F,rw-6,module.isEnabled()?0xFFFFFFFF:0x80FFFFFF);
                    else if(style.novo)dev.vibe.ui.novoline.NovolineFonts.text(dev.vibe.ui.novoline.NovolineFonts.LABEL,name,rx+2,rowY+4,rw-arrowSpace-4,color);
                    else if(style.fan)label(name,rx+12,rowY+(style.rowHeight-14)/2,rw-arrowSpace-20,color);
                    else center(name,rx+leftReserve,rowY+(style.rowHeight-14)/2,rw-arrowSpace-leftReserve,color);
                    if(arrowSpace>0)NeverLoseStyle.chevron(rx+rw-11,rowY+style.rowHeight/2-1,expanded,color);
                    host.dropdownModuleTarget(module,x,rowY,x+panelWidth,rowY+style.rowHeight,p.name);
                    if(over)hovered=module;
                }
                rowY+=style.rowHeight;
                int shown=style.diablo||style.fan?Math.round(diabloSettings.getOrDefault(module,0F)):expanded?host.dropdownSettingsHeight(module):0;
                if(shown>0){
                    if(rowY+shown>=top&&rowY<bottom){
                        host.dropdownClip(p.name,Math.max(top,rowY),Math.min(bottom,rowY+shown));host.dropdownInput(expanded);
                        try(GuiClip settingsClip=GuiClip.local(x,rowY,panelWidth,shown)){
                            host.drawDropdownSettings(module,x+(style.novo||style.diablo?0:style.suizide?5:4),rowY,panelWidth-(style.novo||style.diablo?0:style.suizide?10:8),style,p.name);
                        }finally{host.dropdownClip(p.name,top,bottom);host.dropdownInput(true);}
                    }
                    rowY+=shown;
                }
            }
        }
        if(style.diablo){RenderUtils.roundedOutline(x,y,x+panelWidth,y+p.h,5,4,style.accent(0,y));if(p.h>style.headerHeight+1)RenderUtils.roundedOutline(x+1,y+style.headerHeight-1,x+panelWidth-1,y+p.h-1,5,3,0xFF333333);}
        if(p.max>0){int track=bottom-top,thumb=ScrollbarDrag.thumb(track,p.max,15),sy=top+Math.round((track-thumb)*p.scroll/p.max);
            rect(x+panelWidth-3,sy,2,thumb,1,RenderUtils.alpha(style.muted,150));}
    }
    void openWurstSettings(Module module){if(!style.wurst)return;for(Iterator<Panel> it=panels.iterator();it.hasNext();)if(it.next().detail==module){it.remove();return;}Panel parent=categories.get(module.getCategory());Panel p=new Panel(module.getCategory(),panels.size(),parent.x+panelWidth+6,parent.y);p.detail=module;p.name="Settings:"+module.getId();p.modules.clear();panels.add(p);}
    boolean accepts(String owner,int mx,int my) {Panel p=at(mx,my);return p!=null&&p.name.equals(owner)&&!p.collapsed&&my>=p.y+style.headerHeight&&my<p.y+p.h-style.footerHeight();}
    private Panel at(int mx,int my){for(int i=panels.size()-1;i>=0;i--){Panel p=panels.get(i);if(mx>=screenX(p)&&mx<screenX(p)+panelWidth&&my>=p.y&&my<p.y+p.h)return p;}return null;}
    boolean click(int mx,int my,int button) {
        if(mode!=null&&!inlineMode()) {
            if(mx>=modeX&&mx<modeX+modeW&&my>=modeY&&my<modeY+modeH){int option=(my-modeY-3+modeScroll)/20;if(option>=0&&option<mode.getModes().size()){mode.setValue(mode.getModes().get(option));mode=null;}}
            else mode=null;
            return true;
        }
        if(horizontalMax>0&&my>=height-28) {
            int index=clamp(mx*panels.size()/Math.max(1,navigationWidth()),0,panels.size()-1);for(Panel p:panels)if(p.order==index){targetHorizontal=Math.max(0,Math.min(horizontalMax,p.x-(width-panelWidth)/2));break;}return true;
        }
        Panel p=at(mx,my);if(p==null)return false;
        if(my<p.y+style.headerHeight) {
            if(style.wurst&&button==0&&mx>=screenX(p)+panelWidth-13){if(p.detail!=null)panels.remove(p);else p.collapsed=!p.collapsed;return true;}
            mode=null;
            if(button==1)p.collapsed=!p.collapsed;
            else if(button==0){dragging=p;dragX=mx+Math.round(horizontal)-p.x;dragY=my-p.y;panels.remove(p);panels.add(p);}
            return true;
        }
        if(button==0&&p.max>0&&mx>=screenX(p)+panelWidth-6){scrolling=p;scrollbar=new ScrollbarDrag(p.y+style.headerHeight,p.y+p.h-style.footerHeight(),Math.round(p.scroll),p.max,15,my);p.target=p.scroll=scrollbar.at(my);return true;}
        return false;
    }
    boolean wheelPopup(int mx,int my,int delta){if(mode==null||inlineMode())return false;modeScroll=clamp(modeScroll-(delta>0?20:-20),0,Math.max(0,mode.getModes().size()*20+6-modeH));return true;}
    void wheel(int mx,int my,int delta) {
        if(dragging!=null||scrollbar!=null)return;
        Panel p=at(mx,my);if(p!=null&&!p.collapsed)p.target=Math.max(0,Math.min(p.max,p.target+(delta>0?-32:32)));
    }
    boolean panWheel(int mx,int my,int delta){
        if(horizontalMax<=0||dragging!=null||scrollbar!=null)return false;
        if(my>=height-28||Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))){targetHorizontal+=delta>0?-100:100;return true;}
        return false;
    }
    boolean escape(){if(mode!=null){mode=null;return true;}return false;}
    void openMode(ModeSetting setting,int x,int y,int w){if(mode==setting){mode=null;return;}mode=setting;modeW=Math.max(116,w);modeH=Math.min(height-16,Math.min(206,setting.getModes().size()*20+6));modeX=clamp(x,4,width-modeW-4);modeY=clamp(y,4,height-modeH-4);modeScroll=0;}
    private boolean inlineMode(){return style!=null&&(style.novo||style.packet);}
    boolean modeOpen(ModeSetting setting){return mode==setting;}
    int inlineHeight(ModeSetting setting){return inlineMode()&&mode==setting?setting.getModes().size()*20+4:0;}
    void closeMode(){mode=null;}
    void drawInlineMode(VibeClickGui host,ModeSetting setting,int x,int y,int w,String owner){
        if(inlineHeight(setting)==0)return;
        for(int i=0;i<setting.getModes().size();i++){
            String value=setting.getModes().get(i);int rowY=y+i*20;
            if(!host.dropdownVisible(rowY,20))continue;
            if(style.packet){
                if(setting.is(value))roundedGradient(x+2,rowY+1,w-4,19,4,style.accent(0,rowY),style.accent(1,rowY));
                else rect(x+2,rowY+1,w-4,19,4,0xFF202025);
                center(LanguageManager.translate(value),x+2,rowY+3,w-4,setting.is(value)?0xFFFFFFFF:style.muted);
            }else{
                if(setting.is(value)){rect(x+2,rowY+1,w-4,19,3,0xFF282335);rect(x+3,rowY+5,2,10,1,style.accent(.5F,rowY));}
                label(LanguageManager.translate(value),x+11,rowY+3,w-18,setting.is(value)?style.accent(.5F,rowY):style.muted);
            }
            host.dropdownModeOption(setting,x+2,rowY,x+w-2,rowY+20,i,owner);
        }
    }
    private void drawMode(int mx,int my) {
        int fill=style.light?0xFFF4F7F6:0xFF17171E;
        SoftPanelGlow.draw(modeX,modeY,modeW,modeH,6,10,0x65000000);rect(modeX,modeY,modeW,modeH,6,style.light?0xFFB7C8C3:0xFF41414B);rect(modeX+1,modeY+1,modeW-2,modeH-2,5,fill);
        try(GuiClip clip=GuiClip.local(modeX+2,modeY+2,modeW-4,modeH-4)) {
            for(int i=0;i<mode.getModes().size();i++){int y=modeY+3+i*20-modeScroll;if(y+20<modeY||y>modeY+modeH)continue;
                String option=mode.getModes().get(i);boolean over=mx>=modeX&&mx<modeX+modeW&&my>=y&&my<y+20;
                if(over||mode.is(option))rect(modeX+3,y,modeW-6,19,3,RenderUtils.blend(fill,style.accent(.5F,y),.24F));
                label(LanguageManager.translate(option),modeX+9,y+3,modeW-16,mode.is(option)?style.accent(.5F,y):style.text);}
        }
    }
    private int navigationWidth(){return style.diablo?Math.max(100,width-215):width;}
    private void drawNavigation(int mx,int my){int tab=Math.max(1,navigationWidth()/panels.size());for(Panel p:panels){int x=p.order*tab;boolean selected=screenX(p)>=0&&screenX(p)+panelWidth<=width;
        rect(x+2,height-26,tab-4,22,4,style.light?0xEBF0F4F3:0xED17171E);label(p.detail==null?p.category.getLabel():p.detail.getName(),x+7,height-22,tab-13,selected?style.accent(.5F,0):style.muted);}}
    void release(){dragging=scrolling=null;scrollbar=null;}
    void opened(){opened=System.nanoTime();for(Panel panel:panels)panel.animatedHeight=0;}
    void close(){for(Panel p:panels)p.target=p.scroll;targetHorizontal=horizontal;save();release();if(!inlineMode())mode=null;}
    private void save(){if(theme.isEmpty())return;Map<String,int[]> values=new HashMap<>();for(Panel p:panels)if(p.detail==null)values.put(p.category.name(),new int[]{p.x,p.y,p.collapsed?1:0,Math.round(p.scroll)});SAVED.put(theme,values);SAVED_SIZES.put(theme,new int[]{width,height});SAVED_HORIZONTAL.put(theme,horizontal);}
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(Math.max(min,max),n));}
    private static final class Panel {
        float animatedHeight;
        Module detail;final Category category;String name;final int order;int x,y,h,max,content;boolean collapsed;float scroll,target;final List<Module> modules=new ArrayList<>();
        Panel(Category category,int order,int x,int y){this.category=category;this.name=category.name().substring(0,1)+category.name().substring(1).toLowerCase(Locale.ROOT);this.order=order;this.x=x;this.y=y;}
    }
}
