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
    private final List<Panel> panels = new ArrayList<>();
    private final Map<Category,Panel> categories=new EnumMap<>(Category.class);
    private final Map<Module,Float> enabled = new IdentityHashMap<>();
    private final Map<Module,Float> hovering = new IdentityHashMap<>();
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
        float ease=1-(float)Math.exp(-20*dt);style=new DropdownStyle(settings,System.currentTimeMillis());
        int count=Category.values().length;
        panelWidth=Math.max(116,Math.min(style.novo?136:148,(width-24-12*(count-1))/count));
        int gap=12;
        if(panels.isEmpty()) {
            Map<String,int[]> saved=SAVED.get(theme);
            int[] savedSize=SAVED_SIZES.get(theme);
            boolean reflow=savedSize!=null&&(savedSize[0]!=width||savedSize[1]!=height);
            for(int i=0;i<count;i++) {
                Category category=Category.values()[i];
                int x=12+i*(panelWidth+gap),y=style.packet?36:style.suizide?20:8;
                int[] old=saved==null?null:saved.get(category.name());Panel panel=new Panel(category,i,old==null||reflow?x:old[0],clamp(old==null||reflow?y:old[1],4,height-54));
                panel.collapsed=old!=null&&old[2]!=0;panels.add(panel);categories.put(category,panel);
            }
        }
        if(resized) {for(Panel p:panels){p.x=12+p.order*(panelWidth+gap);p.y=clamp(p.y,4,Math.max(4,height-70));}}
        int far=0;for(Panel p:panels)far=Math.max(far,p.x+panelWidth+12);
        horizontalMax=Math.max(0,far-width);targetHorizontal=Math.max(0,Math.min(horizontalMax,targetHorizontal));horizontal+=(targetHorizontal-horizontal)*ease;
        if(dragging!=null){dragging.x=clamp(mx+Math.round(horizontal)-dragX,0,Math.max(width,far)-panelWidth);dragging.y=clamp(my-dragY,4,height-54);}
        if(scrolling!=null&&scrollbar!=null)scrolling.target=scrolling.scroll=scrollbar.at(my);
        hovered=null;
        for(Panel p:panels)p.modules.clear();
        for(Module module:Vibe.getInstance().getModuleManager().getModules())categories.get(module.getCategory()).modules.add(module);
        for(Panel p:panels) {
            p.content=2;for(Module module:p.modules)p.content+=style.rowHeight+(host.dropdownExpanded(module)?host.dropdownSettingsHeight(module):0);
            int margin=horizontalMax>0?37:22;
            int minimum=style.headerHeight+style.rowHeight+style.footerHeight()+2;
            if(!p.collapsed)p.y=clamp(p.y,4,height-minimum-margin);
            int available=Math.max(minimum,height-p.y-margin);
            p.h=p.collapsed?style.headerHeight:Math.min(available,style.headerHeight+p.content+style.footerHeight());
            p.max=Math.max(0,p.content-Math.max(0,p.h-style.headerHeight-style.footerHeight()));p.target=Math.max(0,Math.min(p.max,p.target));p.scroll=Math.max(0,Math.min(p.max,p.scroll+(p.target-p.scroll)*ease));
        }
        for(Panel p:panels)drawPanelGlow(p);
        for(Panel p:panels) drawPanel(host,p,mx,my,ease,now);
        if(horizontalMax>0)drawNavigation(mx,my);
        if(mode!=null&&!inlineMode())drawMode(mx,my);
        if(hovered!=oldHovered){oldHovered=hovered;hoverAt=System.currentTimeMillis();}
        if(mode==null&&dragging==null&&hovered!=null&&System.currentTimeMillis()-hoverAt>750)host.dropdownTooltip(hovered,mx,my);
    }
    private void drawPanelGlow(Panel p){
        int x=screenX(p);if(x+panelWidth<0||x>width)return;
        if(style.novo){SoftPanelGlow.draw(x,p.y,panelWidth,p.h,2,12,0x8A000000);
            if(style.settings.getNovolineGlow().isEnabled())SoftPanelGlow.draw(x,p.y,panelWidth,p.h,2,9,RenderUtils.alpha(style.accent(.5F,p.y),24));}
        else SoftPanelGlow.draw(x,p.y,panelWidth,p.h,style.radius,12,RenderUtils.alpha(style.accent(.5F,p.y),style.light?42:53));
    }
    private int screenX(Panel p){return p.x-Math.round(horizontal);}
    private void drawPanel(VibeClickGui host,Panel p,int mx,int my,float ease,long now) {
        int x=screenX(p),y=p.y;
        if(x+panelWidth<0||x>width)return;
        int radius=style.radius;
        float intro=Math.min(1,(now-opened)/160e6F);
        // Entrance changes the surface tint, leaving input and text precisely aligned.
        int body=RenderUtils.blend(style.body,style.light?0xFFF2F9F3:0xFF24242A,(1-intro)*.15F);
        rect(x,y,panelWidth,p.h,radius,body);
        if(style.suizide&&style.light&&!p.collapsed) {
            // Subtle mint at the base of the translucent light panels in the reference.
            dev.vibe.ui.MenuRoundedRenderer.verticalGradient(x,y,panelWidth,p.h,radius,body,0xE5C7FFE6);
        }
        if(style.suizide) {
            try(GuiClip clip=GuiClip.local(x,y,panelWidth,style.headerHeight)){roundedGradient(x,y,panelWidth,style.headerHeight+(p.collapsed?0:radius),radius,style.accent(0,y),style.accent(1,y));}
        } else {
            try(GuiClip clip=GuiClip.local(x,y,panelWidth,style.headerHeight)){rect(x,y,panelWidth,style.headerHeight+(p.collapsed?0:radius),radius,style.novo?0xF20D0D13:0xF7111113);}
        }
        String title=p.category.getLabel();
        if(style.novo){NeverLoseStyle.icon(p.category,x+8,y+7,0xFFD3D2DF);label(title,x+25,y+4,panelWidth-38,0xFFE8E7ED);}
        else ScrollingText.draw(HEADER,title,x+9,y+4,panelWidth-(p.collapsed?30:18),0xFFF7F7F9,ScrollingText.Alignment.CENTER);
        if(p.collapsed){NeverLoseStyle.chevron(x+panelWidth-12,y+11,false,0xFFAAAAAE);return;}
        int top=y+style.headerHeight,bottom=y+p.h-style.footerHeight();
        host.dropdownClip(p.name,top,bottom);
        try(GuiClip clip=GuiClip.local(x,top,panelWidth,bottom-top)) {
            int rowY=top+2-Math.round(p.scroll);
            for(Module module:p.modules) {
                boolean expanded=host.dropdownExpanded(module),over=accepts(p.name,mx,my)&&my>=rowY&&my<rowY+style.rowHeight;
                float on=enabled.getOrDefault(module,module.isEnabled()?1F:0F);on+=((module.isEnabled()?1:0)-on)*ease;enabled.put(module,on);
                float hover=hovering.getOrDefault(module,0F);hover+=((over?1:0)-hover)*ease;hovering.put(module,hover);
                if(rowY+style.rowHeight>=top&&rowY<bottom) {
                    int inset=style.suizide?4:0,rx=x+inset,rw=panelWidth-inset*2;
                    if(style.packet) {
                        if(on>.01F) {
                            int c1=RenderUtils.blend(body,style.accent(0,rowY),on),c2=RenderUtils.blend(body,style.accent(1,rowY),on);
                            gradient(rx,rowY,rw,style.rowHeight,c1,c2);
                        } else if(hover>.01F) Gui.drawRect(rx,rowY,rx+rw,rowY+style.rowHeight,RenderUtils.alpha(0xFFFFFFFF,Math.round(hover*12)));
                    } else if(style.suizide) {
                        int inactive=RenderUtils.blend(style.row,style.light?0xFFFFFFFF:0xFF30303A,hover*.6F);
                        roundedGradient(rx,rowY+1,rw,style.rowHeight-3,5,RenderUtils.blend(inactive,style.accent(0,rowY),on),RenderUtils.blend(inactive,style.accent(1,rowY),on));
                    } else if(hover>.01F)Gui.drawRect(rx,rowY,rx+rw,rowY+style.rowHeight,RenderUtils.alpha(0xFFFFFFFF,Math.round(hover*9)));
                    int color=style.novo?RenderUtils.blend(style.text,style.accent(0,rowY),on):module.isEnabled()?0xFFFFFFFF:style.text;
                    String name=host.dropdownBinding(module)?"Press a key...":module.getName();
                    // One text pass: the optional glow belongs behind the panel, never offset glyphs.
                    center(name,rx,rowY+(style.rowHeight-14)/2,rw-(!style.packet&&host.dropdownHasSettings(module)?13:0),color);
                    if(!style.packet&&host.dropdownHasSettings(module))NeverLoseStyle.chevron(rx+rw-11,rowY+style.rowHeight/2-1,expanded,color);
                    host.dropdownModuleTarget(module,x,rowY,x+panelWidth,rowY+style.rowHeight,p.name);
                    if(over)hovered=module;
                }
                rowY+=style.rowHeight;
                if(expanded) {
                    int settingH=host.dropdownSettingsHeight(module);
                    if(rowY+settingH>=top&&rowY<bottom)host.drawDropdownSettings(module,x+(style.suizide?5:4),rowY,panelWidth-(style.suizide?10:8),style,p.name);
                    rowY+=settingH;
                }
            }
        }
        if(style.novo) {
            int color=style.accent(0,y);Gui.drawRect(x,y+p.h-3,x+panelWidth,y+p.h-1,color);
            Gui.drawRect(x,y+p.h-1,x+panelWidth,y+p.h,RenderUtils.alpha(color,100));
        }
        else Gui.drawRect(x+radius,y+p.h-5,x+panelWidth-radius,y+p.h-4,RenderUtils.alpha(style.accent(.5F,y),55));
        if(p.max>0){int track=bottom-top,thumb=ScrollbarDrag.thumb(track,p.max,15),sy=top+Math.round((track-thumb)*p.scroll/p.max);
            rect(x+panelWidth-3,sy,2,thumb,1,RenderUtils.alpha(style.muted,150));}
    }
    boolean accepts(String owner,int mx,int my) {Panel p=at(mx,my);return p!=null&&p.name.equals(owner)&&!p.collapsed&&my>=p.y+style.headerHeight&&my<p.y+p.h-style.footerHeight();}
    private Panel at(int mx,int my){for(int i=panels.size()-1;i>=0;i--){Panel p=panels.get(i);if(mx>=screenX(p)&&mx<screenX(p)+panelWidth&&my>=p.y&&my<p.y+p.h)return p;}return null;}
    boolean click(int mx,int my,int button) {
        if(mode!=null&&!inlineMode()) {
            if(mx>=modeX&&mx<modeX+modeW&&my>=modeY&&my<modeY+modeH){int option=(my-modeY-3+modeScroll)/20;if(option>=0&&option<mode.getModes().size()){mode.setValue(mode.getModes().get(option));mode=null;}}
            else mode=null;
            return true;
        }
        if(horizontalMax>0&&my>=height-28) {
            int index=clamp(mx*panels.size()/Math.max(1,width),0,panels.size()-1);for(Panel p:panels)if(p.order==index){targetHorizontal=Math.max(0,Math.min(horizontalMax,p.x-(width-panelWidth)/2));break;}return true;
        }
        Panel p=at(mx,my);if(p==null)return false;
        if(my<p.y+style.headerHeight) {
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
    private void drawNavigation(int mx,int my){int tab=Math.max(1,width/panels.size());for(Panel p:panels){int x=p.order*tab;boolean selected=screenX(p)>=0&&screenX(p)+panelWidth<=width;
        rect(x+2,height-26,tab-4,22,4,style.light?0xEBF0F4F3:0xED17171E);label(p.category.getLabel(),x+7,height-22,tab-13,selected?style.accent(.5F,0):style.muted);}}
    void release(){dragging=scrolling=null;scrollbar=null;}
    void close(){save();release();mode=null;}
    private void save(){if(theme.isEmpty())return;Map<String,int[]> values=new HashMap<>();for(Panel p:panels)values.put(p.category.name(),new int[]{p.x,p.y,p.collapsed?1:0});SAVED.put(theme,values);SAVED_SIZES.put(theme,new int[]{width,height});}
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(Math.max(min,max),n));}
    private static final class Panel {
        final Category category;final String name;final int order;int x,y,h,max,content;boolean collapsed;float scroll,target;final List<Module> modules=new ArrayList<>();
        Panel(Category category,int order,int x,int y){this.category=category;this.name=category.name().substring(0,1)+category.name().substring(1).toLowerCase(Locale.ROOT);this.order=order;this.x=x;this.y=y;}
    }
}
