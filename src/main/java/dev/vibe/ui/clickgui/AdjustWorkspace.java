package dev.vibe.ui.clickgui;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.*;
import dev.vibe.setting.*;
import dev.vibe.ui.*;
import dev.vibe.ui.adjust.AdjustFonts;
import java.awt.Color;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Adjust's narrow gradient-header columns, text controls, eye buttons and inline HSBA picker.
 * Uses Vibe's Module/Setting objects directly; no second configuration or input model. */
final class AdjustWorkspace {
    private static final Map<Category,int[]> SAVED=new EnumMap<>(Category.class);
    private static final Set<String> EXPANDED=new HashSet<>();
    private static float savedPan;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final List<Panel> panels=new ArrayList<>();
    private final List<Hit> hits=new ArrayList<>();
    private final Set<Setting<?>> open=Collections.newSetFromMap(new IdentityHashMap<Setting<?>,Boolean>());
    private final Map<ColorSetting,Picker> pickers=new IdentityHashMap<>();
    private Panel moving;private int dx,dy,width,height,panelWidth=132;private float pan,panMax;
    private Hit dragging;private RangeSetting.Drag rangeDrag;
    private Module binding;
    private Setting<?> editing;private GuiTextField editor,search;
    private long previous,opened=System.nanoTime();
    private int accent,secondary;private String oldQuery="";

    void draw(int w,int h,int mx,int my,float partial){
        dev.vibe.ui.GuiRenderState.prepare(false);width=w;height=h;
        ClickGuiModule gui=Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        accent=gui.getPrimaryColor().getArgb();secondary=gui.getSecondaryColor().getArgb();
        if(mc.theWorld==null)Gui.drawRect(0,0,w,h,0xFF131315);
        float intro=Math.min(1,(System.nanoTime()-opened)/220e6F);
        Gui.drawRect(0,0,w,h,Math.round(80*intro)<<24);
        if(search==null){search=new GuiTextField(410,mc.fontRendererObj,0,0,120,16);search.setEnableBackgroundDrawing(false);search.setMaxStringLength(100);}
        int sw=Math.min(180,w-20);search.xPosition=(w-sw)/2+7;search.yPosition=h-21;search.width=sw-14;
        Gui.drawRect((w-sw)/2,h-26,(w+sw)/2,h-5,0xEE111113);RenderUtils.roundedOutline((w-sw)/2,h-26,(w+sw)/2,h-5,0,1,0xFF35353A);
        net.minecraft.client.renderer.GlStateManager.disableBlend();net.minecraft.client.renderer.GlStateManager.enableBlend();
        if(search.getText().isEmpty()&&!search.isFocused())AdjustFonts.LABEL.draw("Search...",search.xPosition,h-23,0xFF85858C);else search.drawTextBox();
        String query=search.getText().trim().toLowerCase(Locale.ROOT);
        if(!query.equals(oldQuery)){for(Panel p:panels)p.target=p.scroll=0;oldQuery=query;}
        panelWidth=Math.max(104,Math.min(132,(w-20)/Category.values().length-4));
        if(panels.isEmpty()){
            int i=0;pan=savedPan;
            for(Category c:Category.values()){
                int[] saved=SAVED.get(c);Panel p=new Panel(c,saved==null?10+i*(panelWidth+4):saved[0],saved==null?38:Math.max(4,Math.min(h-64,saved[1])));
                if(saved!=null){p.collapsed=saved[2]!=0;p.scroll=p.target=saved[3];}panels.add(p);i++;
            }
        }
        int far=0;for(Panel p:panels)far=Math.max(far,p.x+panelWidth+10);panMax=Math.max(0,far-w);pan=Math.max(0,Math.min(panMax,pan));
        if(moving!=null){moving.x=Math.max(0,mx+Math.round(pan)-dx);moving.y=Math.max(4,Math.min(h-60,my-dy));}
        if(dragging!=null){if(!Mouse.isButtonDown(0)||!dragging.setting.isVisible())release(0);else drag(mx,my);}
        long now=System.nanoTime();float dt=previous==0?1/60F:Math.min(.05F,(now-previous)/1e9F);previous=now;float ease=1-(float)Math.exp(-22*dt);
        hits.clear();
        for(Panel p:panels){
            p.modules.clear();for(Module m:ClickGuiModule.sortedModules(Vibe.getInstance().getModuleManager().getModules(p.category)))if(matches(m,query))p.modules.add(m);
            int content=0;for(Module m:p.modules){content+=22;if(expanded(m,query))content+=settingsHeight(m);}
            p.h=p.collapsed?24:Math.min(Math.max(46,h-p.y-34),24+content+3);p.max=Math.max(0,content-(p.h-27));p.target=Math.max(0,Math.min(p.max,p.target));p.scroll+=(p.target-p.scroll)*ease;
            panel(p,mx,my,query);
        }
        if(panMax>0){Gui.drawRect(10,h-31,w-10,h-29,0x77303036);float size=(w-20)*(w/(w+panMax)),x=10+(w-20-size)*(pan/panMax);Gui.drawRect((int)x,h-31,(int)(x+size),h-29,accent);}
        dev.vibe.ui.GuiRenderState.prepare(false);
    }
    private static boolean matches(Module m,String q){if(q.isEmpty()||m.getRawName().toLowerCase(Locale.ROOT).contains(q))return true;for(Setting<?> s:m.getSettings())if(s.getRawName().toLowerCase(Locale.ROOT).contains(q))return true;return false;}
    private boolean expanded(Module m,String q){return EXPANDED.contains(m.getId())||!q.isEmpty();}
    private void panel(Panel p,int mx,int my,String query){
        int x=p.x-Math.round(pan),y=p.y;if(x+panelWidth<0||x>width)return;
        Gui.drawRect(x,y,x+panelWidth,y+p.h,0xEC101013);
        DropdownStyle.gradient(x,y,panelWidth,24,RenderUtils.blend(accent,0xFFFFFFFF,.28F),RenderUtils.blend(secondary,0xFF000000,.2F));
        RenderUtils.roundedOutline(x,y,x+panelWidth,y+24,0,1,0x64232323);
        net.minecraft.client.renderer.GlStateManager.disableBlend();net.minecraft.client.renderer.GlStateManager.enableBlend();
        AdjustFonts.TITLE.draw(p.category.getLabel(),x+(panelWidth-AdjustFonts.TITLE.width(p.category.getLabel()))/2,y+5,0xFFEEEEF2);
        hit(p,x,y,panelWidth,24,Kind.HEADER,null,null,null,0);
        if(p.collapsed)return;
        try(GuiClip clip=GuiClip.local(x,y+24,panelWidth,p.h-25)){
            int yy=y+24-Math.round(p.scroll);
            HudModule hud=Vibe.getInstance().getModuleManager().getModule(HudModule.class);
            for(Module m:p.modules){
                boolean hover=inside(mx,my,x,yy,panelWidth,22)&&my>=y+24&&my<y+p.h;
                if(hover)Gui.drawRect(x,yy,x+panelWidth,yy+22,0x2CFFFFFF);
                boolean visible=hud==null||hud.getArrayListModules().isSelected(m.getRawName());
                int reserve=hover||!visible?42:6;
                label(m.getName(),x+5,yy+5,panelWidth-reserve,m.isEnabled()?0xFFF2F2F5:0xFF929297);
                hit(p,x,yy,panelWidth,22,Kind.MODULE,m,null,null,0);
                if(hover||!visible){eye(x+panelWidth-30,yy+11,visible,visible?0xFFCBCBD1:0xFF74747B);hit(p,x+panelWidth-40,yy,20,22,Kind.EYE,m,null,null,0);}
                if(hover||expanded(m,query)){NeverLoseStyle.chevron(x+panelWidth-10,yy+11,expanded(m,query),0xFFB5B5BC);hit(p,x+panelWidth-20,yy,20,22,Kind.EXPAND,m,null,null,0);}
                yy+=22;
                if(expanded(m,query)){
                    Gui.drawRect(x+1,yy,x+panelWidth-1,yy+settingsHeight(m),0xE918181C);
                    label("Bind: "+(binding==m?"Press a key...":m.getKey()==0?"None":Keyboard.getKeyName(m.getKey())),x+5,yy+3,panelWidth-10,0xFFAAAAB2);
                    hit(p,x,yy,panelWidth,18,Kind.BIND,m,null,null,0);yy+=18;
                    for(Setting<?> s:m.getSettings())if(s.isVisible()){setting(p,s,x,yy,mx,my);yy+=settingHeight(s);}
                }
            }
        }
        if(p.max>0){float track=p.h-27,thumb=Math.max(12,track*track/(track+p.max)),sy=y+25+(track-thumb)*p.scroll/p.max;Gui.drawRect(x+panelWidth-2,(int)sy,x+panelWidth-1,(int)(sy+thumb),0x889E9EA7);}
    }
    private int settingsHeight(Module m){int h=18;for(Setting<?> s:m.getSettings())if(s.isVisible())h+=settingHeight(s);return h;}
    private int settingHeight(Setting<?> s){if(s instanceof ColorSetting)return 18+(open.contains(s)?108:0);if(s instanceof MultiSelectSetting)return 18+(open.contains(s)?((MultiSelectSetting)s).getOptions().size()*18:0);return s instanceof NumberSetting||s instanceof RangeSetting?24:s instanceof StringSetting?34:18;}
    private void setting(Panel p,Setting<?> s,int x,int y,int mx,int my){
        int w=panelWidth,muted=0xFFAFAFB7;
        if(s instanceof BooleanSetting){boolean on=((BooleanSetting)s).isEnabled();Gui.drawRect(x+4,y+4,x+15,y+15,0x620F0F13);if(on)Gui.drawRect(x+5,y+5,x+14,y+14,accent);label(s.getName()+": "+(on?"Yes":"No"),x+19,y+3,w-24,on?0xFFD3D3DC:muted);hit(p,x,y,w,18,Kind.SETTING,null,s,null,0);}
        else if(s instanceof NumberSetting||s instanceof RangeSetting){
            double min,max,low,high;if(s instanceof NumberSetting){NumberSetting n=(NumberSetting)s;min=n.getMinimum();max=n.getMaximum();low=min;high=n.getDouble();}else{RangeSetting n=(RangeSetting)s;min=n.getMinimum();max=n.getMaximum();low=n.getMin();high=n.getMax();}
            String value=s instanceof RangeSetting?number(low)+" - "+number(high):number(high);float tw=AdjustFonts.SMALL.width(value);
            label(s.getName(),x+4,y+2,w-tw-13,muted);AdjustFonts.SMALL.draw(value,x+w-5-tw,y+3,0xFFC9C9D0);
            if(editing==s&&editor!=null){Gui.drawRect(x+3,y,x+w-3,y+15,0xFF101013);editor.xPosition=x+5;editor.yPosition=y+3;editor.width=w-10;editor.drawTextBox();}
            float a=(float)((low-min)/Math.max(.00001,max-min)),b=(float)((high-min)/Math.max(.00001,max-min));int left=x+4,right=x+w-4;
            Gui.drawRect(left,y+17,right,y+19,0xFF35353B);DropdownStyle.gradient(left+Math.round(a*(right-left)),y+17,Math.max(1,Math.round((b-a)*(right-left))),2,accent,RenderUtils.blend(accent,secondary,.65F));
            Gui.drawRect(left+Math.round(b*(right-left))-1,y+15,left+Math.round(b*(right-left))+1,y+21,0xFFD8D8DE);
            if(s instanceof RangeSetting)Gui.drawRect(left+Math.round(a*(right-left))-1,y+15,left+Math.round(a*(right-left))+1,y+21,0xFFD8D8DE);
            hit(p,left,y,right-left,24,Kind.SLIDER,null,s,null,0);
        }else if(s instanceof ModeSetting){label(s.getName()+": "+((ModeSetting)s).getValue(),x+4,y+3,w-8,muted);hit(p,x,y,w,18,Kind.SETTING,null,s,null,0);}
        else if(s instanceof MultiSelectSetting){MultiSelectSetting multi=(MultiSelectSetting)s;label(s.getName()+": "+multi.getValue().size(),x+4,y+3,w-20,muted);NeverLoseStyle.chevron(x+w-10,y+9,open.contains(s),muted);hit(p,x,y,w,18,Kind.SETTING,null,s,null,0);if(open.contains(s)){int yy=y+18;for(String option:multi.getOptions()){Gui.drawRect(x+8,yy+4,x+19,yy+15,0x620F0F13);if(multi.isSelected(option))Gui.drawRect(x+9,yy+5,x+18,yy+14,accent);label(option,x+23,yy+3,w-28,multi.isSelected(option)?0xFFDDDEE5:muted);hit(p,x,yy,w,18,Kind.OPTION,null,s,option,0);yy+=18;}}}
        else if(s instanceof ColorSetting){ColorSetting c=(ColorSetting)s;label(s.getName(),x+4,y+3,w-35,muted);RenderUtils.transparencyGrid(x+w-27,y+5,x+w-5,y+14,3);Gui.drawRect(x+w-27,y+5,x+w-5,y+14,c.getArgb());hit(p,x,y,w,18,Kind.SETTING,null,s,null,0);if(open.contains(s))picker(p,c,x+4,y+20,w-8);}
        else if(s instanceof StringSetting){label(s.getName(),x+4,y+1,w-8,muted);Gui.drawRect(x+4,y+16,x+w-4,y+32,0xFF101013);if(editing==s&&editor!=null){editor.xPosition=x+7;editor.yPosition=y+20;editor.width=w-14;editor.drawTextBox();}else label(((StringSetting)s).getValue(),x+7,y+18,w-14,0xFFCCCCD3);hit(p,x+4,y+16,w-8,17,Kind.TEXT,null,s,null,0);}
    }
    private void picker(Panel p,ColorSetting c,int x,int y,int w){
        Picker v=pickers.computeIfAbsent(c,k->new Picker());v.sync(c);int hue=Color.HSBtoRGB(v.hue,1,1);
        DropdownStyle.gradient(x,y,w,72,0xFFFFFFFF,hue);DropdownStyle.verticalGradient(x,y,w,72,0x00000000,0xFF000000);
        int px=x+Math.round(v.saturation*w),py=y+Math.round((1-v.brightness)*72);RenderUtils.roundedOutline(px-2,py-2,px+3,py+3,0,1,0xFFFFFFFF);
        for(int i=0;i<6;i++)DropdownStyle.gradient(x+i*w/6,y+76,(i+1)*w/6-i*w/6,10,Color.HSBtoRGB(i/6F,1,1),Color.HSBtoRGB((i+1)/6F,1,1));
        Gui.drawRect(x+Math.round(v.hue*w)-1,y+75,x+Math.round(v.hue*w)+1,y+87,0xFFFFFFFF);
        RenderUtils.transparencyGrid(x,y+90,x+w,y+100,3);DropdownStyle.gradient(x,y+90,w,10,c.getArgb()&0xFFFFFF,c.getArgb()|0xFF000000);
        int ax=x+Math.round(c.getAlpha()/255F*w);Gui.drawRect(ax-1,y+89,ax+1,y+101,0xFFFFFFFF);
        hit(p,x,y,w,72,Kind.COLOR,null,c,null,0);hit(p,x,y+76,w,10,Kind.COLOR,null,c,null,1);hit(p,x,y+90,w,10,Kind.COLOR,null,c,null,2);
    }
    void click(int mx,int my,int button){
        if(search!=null&&my>=height-26){commit();search.mouseClicked(mx,my,button);return;}
        if(search!=null)search.setFocused(false);
        for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(!hit.contains(mx,my))continue;
            if(hit.kind==Kind.HEADER){commit();if(button==0){moving=hit.panel;dx=mx+Math.round(pan)-moving.x;dy=my-moving.y;}else if(button==1)hit.panel.collapsed=!hit.panel.collapsed;return;}
            if(hit.kind==Kind.EYE&&button==0){HudModule hud=Vibe.getInstance().getModuleManager().getModule(HudModule.class);if(hud!=null){hud.synchronizeArrayListModules();hud.getArrayListModules().toggle(hit.module.getRawName());saveConfig();}return;}
            if(hit.kind==Kind.MODULE){commit();if(button==0)hit.module.toggle();else if(button==1)toggleModule(hit.module);else if(button==2)binding=hit.module;saveConfig();return;}
            if(hit.kind==Kind.EXPAND){toggleModule(hit.module);return;}
            if(hit.kind==Kind.BIND){commit();binding=hit.module;return;}
            if(hit.kind==Kind.SLIDER&&button==0){commit();dragging=hit;if(hit.setting instanceof RangeSetting)rangeDrag=((RangeSetting)hit.setting).beginDrag(sliderValue(hit,mx));drag(mx,my);return;}
            if(hit.kind==Kind.SLIDER&&button==2&&hit.setting instanceof NumberSetting){beginEdit(hit,number(((NumberSetting)hit.setting).getDouble()),64);return;}
            if(hit.kind==Kind.COLOR&&button==0){commit();dragging=hit;drag(mx,my);return;}
            if(hit.kind==Kind.TEXT&&button==0){StringSetting value=(StringSetting)hit.setting;beginEdit(hit,value.getValue(),value.getMaxLength());return;}
            if(hit.kind==Kind.OPTION&&button==0){((MultiSelectSetting)hit.setting).toggle(hit.option);saveConfig();return;}
            if(hit.kind==Kind.SETTING){commit();Setting<?> s=hit.setting;if(s instanceof BooleanSetting&&button==0)((BooleanSetting)s).setValue(!((BooleanSetting)s).isEnabled());else if(s instanceof ModeSetting){ModeSetting m=(ModeSetting)s;int next=Math.floorMod(m.getModes().indexOf(m.getValue())+(button==1?-1:1),m.getModes().size());m.setValue(m.getModes().get(next));}else if(s instanceof ColorSetting||s instanceof MultiSelectSetting){if(!open.add(s))open.remove(s);}saveConfig();return;}
        }
        commit();
    }
    private static void toggleModule(Module m){if(!EXPANDED.add(m.getId()))EXPANDED.remove(m.getId());}
    void wheel(int mx,int my,int wheel){if(wheel==0||dragging!=null)return;for(int i=panels.size()-1;i>=0;i--){Panel p=panels.get(i);if(inside(mx,my,p.x-Math.round(pan),p.y,panelWidth,p.h)&&!(Keyboard.isCreated()&&GuiScreen.isShiftKeyDown())){p.target=Math.max(0,Math.min(p.max,p.target+(wheel>0?-36:36)));return;}}pan=Math.max(0,Math.min(panMax,pan+(wheel>0?-80:80)));}
    boolean key(char ch,int key){
        if(binding!=null){binding.setKey(key==Keyboard.KEY_ESCAPE||key==Keyboard.KEY_DELETE?0:key);binding=null;saveConfig();return true;}
        if(editing!=null){if(key==Keyboard.KEY_ESCAPE){editing=null;editor=null;return true;}if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){commit();return true;}editor.textboxKeyTyped(ch,key);return true;}
        if(search!=null&&search.isFocused()){if(key==Keyboard.KEY_ESCAPE){if(!search.getText().isEmpty())search.setText("");else search.setFocused(false);return true;}search.textboxKeyTyped(ch,key);return true;}
        if(key==Keyboard.KEY_F&&GuiScreen.isCtrlKeyDown()){search.setFocused(true);return true;}return false;
    }
    void tick(){if(editor!=null)editor.updateCursorCounter();if(search!=null)search.updateCursorCounter();}
    void release(int button){if(button==0){if(dragging!=null)saveConfig();moving=null;dragging=null;rangeDrag=null;}}
    void close(){commit();release(0);binding=null;for(Panel p:panels)SAVED.put(p.category,new int[]{p.x,p.y,p.collapsed?1:0,Math.round(p.scroll)});savedPan=pan;}
    private void beginEdit(Hit hit,String value,int max){commit();editing=hit.setting;editor=new GuiTextField(411,mc.fontRendererObj,hit.x+3,hit.y+4,hit.w-6,12);editor.setEnableBackgroundDrawing(false);editor.setMaxStringLength(max);editor.setText(value);editor.setFocused(true);}
    private void commit(){if(editing!=null&&editor!=null){if(editing instanceof StringSetting)((StringSetting)editing).setValue(editor.getText());else if(editing instanceof NumberSetting)try{double value=Double.parseDouble(editor.getText());if(!Double.isFinite(value))throw new NumberFormatException();((NumberSetting)editing).setValue(value);}catch(NumberFormatException invalid){dev.vibe.feedback.ModuleFeedback.message("adjust-number","Invalid value","Enter a valid number for "+editing.getName()+".");}saveConfig();}editing=null;editor=null;}
    private static void saveConfig(){if(Vibe.getInstance().getConfig()!=null)Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());}
    private double sliderValue(Hit h,int mx){double min=h.setting instanceof NumberSetting?((NumberSetting)h.setting).getMinimum():((RangeSetting)h.setting).getMinimum(),max=h.setting instanceof NumberSetting?((NumberSetting)h.setting).getMaximum():((RangeSetting)h.setting).getMaximum();return min+Math.max(0,Math.min(1,(mx-h.x)/(double)Math.max(1,h.w)))*(max-min);}
    private void drag(int mx,int my){Hit h=dragging;if(h.kind==Kind.SLIDER){double v=sliderValue(h,mx);if(h.setting instanceof NumberSetting)((NumberSetting)h.setting).setValue(v);else if(rangeDrag!=null)rangeDrag.move(v);return;}
        ColorSetting c=(ColorSetting)h.setting;Picker v=pickers.get(c);if(v==null)return;float xx=Math.max(0,Math.min(1,(mx-h.x)/(float)h.w)),yy=Math.max(0,Math.min(1,(my-h.y)/(float)h.h));int alpha=c.getAlpha();if(h.part==0){v.saturation=xx;v.brightness=1-yy;}else if(h.part==1)v.hue=xx;else alpha=Math.round(xx*255);int rgb=Color.HSBtoRGB(v.hue,v.saturation,v.brightness);c.setValue(alpha<<24|rgb&0xFFFFFF);v.last=c.getArgb();}
    private void hit(Panel p,int x,int y,int w,int h,Kind kind,Module m,Setting<?> s,String option,int part){int top=kind==Kind.HEADER?y:Math.max(y,p.y+24),bottom=kind==Kind.HEADER?y+h:Math.min(y+h,p.y+p.h-1);if(bottom>top)hits.add(new Hit(p,x,y,w,h,top,bottom,kind,m,s,option,part));}
    private static boolean inside(int x,int y,int l,int t,int w,int h){return x>=l&&x<l+w&&y>=t&&y<t+h;}
    private static String number(double n){return java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();}
    private static void label(String s,float x,float y,float w,int c){ScrollingText.draw(AdjustFonts.LABEL,s,x,y,Math.max(1,w),c,ScrollingText.Alignment.LEFT);}
    private static void eye(float x,float y,boolean shown,int c){
        try(dev.vibe.ui.effect.EffectState ignored=new dev.vibe.ui.effect.EffectState()){
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(770,771);GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1.2F);GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,1);
            GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<24;i++){double a=i*Math.PI*2/24;GL11.glVertex2d(x+Math.cos(a)*6,y+Math.sin(a)*3);}GL11.glEnd();GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<16;i++){double a=i*Math.PI*2/16;GL11.glVertex2d(x+Math.cos(a)*1.6,y+Math.sin(a)*1.6);}GL11.glEnd();if(!shown){GL11.glBegin(GL11.GL_LINES);GL11.glVertex2f(x-6,y-5);GL11.glVertex2f(x+6,y+5);GL11.glEnd();}
        }
    }
    private enum Kind{HEADER,MODULE,EYE,EXPAND,BIND,SETTING,SLIDER,COLOR,OPTION,TEXT}
    private static final class Panel{final Category category;final List<Module> modules=new ArrayList<>();int x,y,h,max;float scroll,target;boolean collapsed;Panel(Category c,int x,int y){category=c;this.x=x;this.y=y;}}
    private static final class Picker{float hue,saturation,brightness;int last;boolean initialized;void sync(ColorSetting c){if(!initialized||last!=c.getArgb()){float[] h=Color.RGBtoHSB(c.getRed(),c.getGreen(),c.getBlue(),null);hue=h[0];saturation=h[1];brightness=h[2];last=c.getArgb();initialized=true;}}}
    private static final class Hit{final Panel panel;final int x,y,w,h,top,bottom,part;final Kind kind;final Module module;final Setting<?> setting;final String option;Hit(Panel p,int x,int y,int w,int h,int top,int bottom,Kind k,Module m,Setting<?> s,String o,int part){panel=p;this.x=x;this.y=y;this.w=w;this.h=h;this.top=top;this.bottom=bottom;kind=k;module=m;setting=s;option=o;this.part=part;}boolean contains(int mx,int my){return mx>=x&&mx<x+w&&my>=top&&my<bottom;}}
}
