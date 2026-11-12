package dev.vibe.ui.clickgui;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.setting.*;
import dev.vibe.ui.*;
import dev.vibe.ui.menu.*;
import java.awt.Color;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Main-menu lava glass, with independent module and settings panes. */
final class LavalampWorkspace {
    private static final MenuFont FONT=MenuFont.BODY,SMALL=MenuFont.SMALL,BOLD=MenuFont.BOLD;
    private static final MenuFont BRAND=new MenuFont(java.awt.Font.BOLD,16);
    private static final int TEXT=0xFFF4F7FC,MUTED=0xFFC0CBDC;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final GuiScreen surface=new GuiScreen(){};
    private final List<Hit> hits=new ArrayList<>();
    private final Set<Setting<?>> open=Collections.newSetFromMap(new IdentityHashMap<Setting<?>,Boolean>());
    private final Map<Category,Float> moduleScroll=new EnumMap<>(Category.class);
    private final Map<String,Float> settingScroll=new HashMap<>();
    private final Map<ColorSetting,Float> hues=new IdentityHashMap<>();
    private final Map<Object,Motion> motions=new IdentityHashMap<>();
    private final Map<String,Float> hoverValues=new HashMap<>();
    private Category category=Category.COMBAT;
    private Module selected,binding;
    private MenuTextField search,editor;
    private Setting<?> editing;
    private Hit slider;
    private RangeSetting.Drag rangeDrag;
    private int x=Integer.MIN_VALUE,y,w=720,h=440,side,cx,cy,cw,bottom,mx,my,dragX,dragY;
    private int bodyTop,listTop,listMax,maxScroll,tabOffset,tabMaximum;
    private int clipLeft,clipTop,clipRight,clipBottom;
    private float listScroll,listTarget,scroll,target,frameDt;
    private boolean dragging,resizing,scrolling,scrollingList;
    private long last=System.nanoTime(),frame;
    private MenuPalette palette;
    private int accent;
    private String lastQuery="";

    void draw(int sw,int sh,int mouseX,int mouseY,float partial){
        mx=mouseX;my=mouseY;long now=System.nanoTime();frameDt=Math.min(.08F,(now-last)/1e9F);last=now;frame++;
        if(x==Integer.MIN_VALUE){w=Math.min(w,sw-24);h=Math.min(h,sh-24);x=(sw-w)/2;y=(sh-h)/2;}
        if(dragging){x=mx-dragX;y=my-dragY;}
        if(resizing){w=Math.max(Math.min(420,sw-16),Math.min(sw-x-8,mx-x));h=Math.max(Math.min(250,sh-16),Math.min(sh-y-8,my-y));}
        w=Math.min(w,sw-16);h=Math.min(h,sh-16);x=Math.max(8,Math.min(sw-w-8,x));y=Math.max(8,Math.min(sh-h-8,y));
        side=Math.max(108,Math.min(190,w*28/100));cx=x+side+18;cw=w-side-34;
        bodyTop=y+61;listTop=bodyTop+39;cy=bodyTop+96;bottom=y+h-17;
        if(scrolling)dragScroll(my,scrollingList);
        listScroll=ease(listScroll,listTarget,19);scroll=ease(scroll,target,19);
        if(search==null){search=new MenuTextField(0,mc.fontRendererObj,0,0,100,22);search.setEnableBackgroundDrawing(false);search.setMaxStringLength(80);}
        search.xPosition=x+39;search.yPosition=bodyTop+4;search.width=side-52;
        String query=search.getText().trim();
        if(!query.equals(lastQuery)){if(lastQuery.isEmpty())moduleScroll.put(category,listTarget);lastQuery=query;listScroll=listTarget=0;}
        List<Module> modules=visibleModules(ClickGuiModule.sortedModules(Vibe.getInstance().getModuleManager().getModules()),category,query);
        if(selected==null||!modules.contains(selected)){select(modules.isEmpty()?null:modules.get(0));}
        listMax=Math.max(0,modules.size()*35-(bottom-listTop));listTarget=clamp(listTarget,0,listMax);listScroll=clamp(listScroll,0,listMax);
        palette=MenuGlass.currentPalette();accent=palette.accent;hits.clear();hoverValues.entrySet().removeIf(e->hoverValues.size()>512);
        GuiRenderState.prepare(false);if(mc.theWorld==null)Gui.drawRect(0,0,sw,sh,0xFF101823);
        MenuGlass.beginLavalamp(surface,sw,sh);
        try{
            clipping(x,y,w,h);
            if(!MenuGlass.pane(x,y,w,h,14,palette.background,palette.accent,true))rect(x,y,w,h,14,0xC0192838);
            clipping(x+4,y+4,w-8,h-8);
            header();
            line(x+16,y+49,x+w-16,y+49,RenderUtils.alpha(accent,65));
            line(x+side+6,bodyTop+2,x+side+6,bottom,RenderUtils.alpha(accent,55));
            material("search",x+15,bodyTop,side-23,30,8,0,false);
            BreezeIcons.draw("search",x+23,bodyTop+8,14,MUTED);search.palette(TEXT,accent);search.drawTextBox();
            if(query.isEmpty()&&!search.isFocused())FONT.middle("Search",search.xPosition,search.yPosition,22,MUTED);
            moduleList(modules);
            if(selected!=null){settingsHeader();settings();}
            else{maxScroll=0;FONT.centered(query.isEmpty()?"Choose a module":"No matches",cx,bodyTop,cw,bottom-bodyTop,MUTED);}
            clipping(x,y,w,h);scrollbar(x+side+1,listTop,bottom-listTop,listScroll,listMax,true);scrollbar(x+w-9,cy,bottom-cy,scroll,maxScroll,false);
            resizeGrip();
            if(slider!=null)updateSlider(mx,my);
        }finally{MenuGlass.end();}
    }

    /** A search leaves the category filter; stable buckets keep name matches ahead of setting matches. */
    static List<Module> visibleModules(List<Module> modules,Category category,String query){
        String q=query.trim().toLowerCase(Locale.ROOT);List<Module> names=new ArrayList<>(),settings=new ArrayList<>();
        for(Module module:modules){
            if(q.isEmpty()){if(module.getCategory()==category)names.add(module);continue;}
            if(module.getName().toLowerCase(Locale.ROOT).contains(q)||module.getRawName().toLowerCase(Locale.ROOT).contains(q)){names.add(module);continue;}
            for(Setting<?> setting:module.getSettings())if(matches(setting,q)){settings.add(module);break;}
        }
        names.addAll(settings);return names;
    }
    private static boolean matches(Setting<?> setting,String query){
        if(setting.getName().toLowerCase(Locale.ROOT).contains(query)||setting.getRawName().toLowerCase(Locale.ROOT).contains(query))return true;
        List<String> options=setting instanceof ModeSetting?((ModeSetting)setting).getModes():setting instanceof MultiSelectSetting?((MultiSelectSetting)setting).getOptions():Collections.emptyList();
        for(String option:options)if(option.toLowerCase(Locale.ROOT).contains(query))return true;return false;
    }
    private void header(){
        BRAND.draw("Vibe",x+18,y+17,RenderUtils.alpha(accent,110));BRAND.draw("Vibe",x+18,y+16,TEXT);
        int left=x+74,right=x+w-47,available=right-left;Category[] categories=Category.values();int minimum=0;
        for(Category c:categories)minimum=Math.max(minimum,(int)Math.ceil(SMALL.width(c.getLabel()))+18);
        boolean overflow=minimum*categories.length+(categories.length-1)*5>available;
        if(overflow){left+=22;right-=22;}else tabOffset=0;
        int a=left-tabOffset;clipping(left,y+10,right-left,32);
        try(GuiClip clip=GuiClip.local(left,y+10,right-left,32)){
            for(int i=0;i<categories.length;i++){Category c=categories[i];int width;
                if(overflow)width=minimum;else{a=left+(right-left)*i/categories.length;int end=left+(right-left)*(i+1)/categories.length;width=end-a-(i==categories.length-1?0:5);}
                boolean on=category==c&&search.getText().trim().isEmpty();
                float hover=hover("category:"+c,a,y+11,width,29);material("category:"+c,a,y+11,width,29,8,hover,on);
                SMALL.centered(c.getLabel(),a,y+11,width,29,TEXT);
                add(new Hit("category:"+c,a,y+11,width,29,()->choose(c)));a+=width+5;}
        }
        tabMaximum=overflow?Math.max(0,a+tabOffset-right-5):0;tabOffset=Math.max(0,Math.min(tabMaximum,tabOffset));
        clipping(x+4,y+4,w-8,h-8);
        if(overflow){button("category-prev",left-24,y+11,20,29,"‹",false,()->tabOffset=Math.max(0,tabOffset-85));button("category-next",right+4,y+11,20,29,"›",false,()->tabOffset=Math.min(tabMaximum,tabOffset+85));}
        button("close",x+w-35,y+12,23,27,"×",false,()->mc.displayGuiScreen(null));
    }
    private void moduleList(List<Module> modules){
        clipping(x+13,listTop,side-16,bottom-listTop);
        try(GuiClip clip=GuiClip.local(clipLeft,clipTop,clipRight-clipLeft,clipBottom-clipTop)){
            int row=listTop-Math.round(listScroll);
            for(Module module:modules){
                if(row+31>=listTop&&row<bottom){
                    boolean on=selected==module;float hot=hover("module:"+module.getId(),x+15,row,side-23,31);
                    if(on||hot>.005F)material("module:"+module.getId(),x+15,row,side-23,31,8,hot,on);
                    text(on?BOLD:FONT,module.getName(),x+25,row+10,side-55,module.isEnabled()||on?TEXT:MUTED);
                    glowDot(x+side-19,row+16,2.5F,animated(module,module.isEnabled()),accent);
                    Hit hit=new Hit("module:"+module.getId(),x+15,row,side-23,31,()->{module.toggle();select(module);});hit.module=module;add(hit);
                }row+=35;
            }
        }
        clipping(x+4,y+4,w-8,h-8);
    }
    private void settingsHeader(){
        Module module=selected;int sy=bodyTop+1;
        text(BOLD,module.getName(),cx,sy+6,cw-108,TEXT);
        float enabled=animated(module,module.isEnabled());material("module-toggle",cx+cw-100,sy,100,28,14,hover("module-toggle",cx+cw-100,sy,100,28),module.isEnabled());
        glowDot(cx+cw-86,sy+14,3,enabled,accent);FONT.centered(module.isEnabled()?"Enabled":"Disabled",cx+cw-76,sy,72,28,TEXT);
        add(new Hit("module-toggle",cx+cw-100,sy,100,28,()->{module.toggle();select(module);}));
        List<String> description=SMALL.wrap(module.getDescription(),cw-4);int dy=sy+34;
        for(int i=0;i<Math.min(2,description.size());i++){SMALL.draw(description.get(i),cx,dy,MUTED);dy+=13;}
        FONT.middle("Keybind",cx,bodyTop+63,25,MUTED);
        button("binding",cx+cw-104,bodyTop+61,104,26,binding==module?"Press a key":keyName(module.getKey()),binding==module,()->{commit();binding=module;});
        line(cx,cy-8,cx+cw,cy-8,RenderUtils.alpha(accent,40));
    }
    private void settings(){
        int row=cy-Math.round(scroll);clipping(cx-1,cy,cw+2,bottom-cy);
        try(GuiClip clip=GuiClip.local(clipLeft,clipTop,clipRight-clipLeft,clipBottom-clipTop)){
            for(Setting<?> value:selected.getSettings())if(value.isVisible())row=setting(value,row);
        }
        maxScroll=Math.max(0,row+Math.round(scroll)-bottom+5);target=clamp(target,0,maxScroll);
    }
    private int setting(Setting<?> value,int row){
        if(value instanceof BooleanSetting){
            BooleanSetting flag=(BooleanSetting)value;float t=animated(flag,flag.isEnabled());text(FONT,value.getName(),cx+1,row+10,cw-60,TEXT);
            int a=cx+cw-43;glow(a-2,row+3,44,25,13,t*.7F);material("switch:"+value.getRawName(),a,row+6,40,22,11,0,flag.isEnabled());
            if(visible(a,row+6,40,22))MenuRoundedRenderer.horizontalGradient(a+2,row+8,36,18,9,RenderUtils.alpha(accent,Math.round(t*105)),RenderUtils.alpha(palette.light,Math.round(t*155)));
            int center=a+10+Math.round(t*20);glowDot(center,row+17,7,t*.5F,accent);rect(center-7,row+10,14,14,7,TEXT);
            Hit hit=new Hit("boolean:"+value.getRawName(),cx,row,cw,35,()->flag.setEnabled(!flag.isEnabled()));hit.setting=flag;add(hit);return row+39;
        }
        if(value instanceof ColorSetting){
            ColorSetting color=(ColorSetting)value;text(FONT,value.getName(),cx+1,row+10,cw-87,TEXT);
            int a=cx+cw-75;material("swatch:"+value.getRawName(),a,row+3,72,29,8,hover("swatch:"+value.getRawName(),a,row+3,72,29),open.contains(value));
            swatch(a+5,row+8,color.getArgb());BreezeIcons.draw("chevrons",a+52,row+10,13,TEXT);
            Hit hit=new Hit("color:"+value.getRawName(),a,row+3,72,29,()->{commit();if(!open.remove(value))open.add(value);hues.put(color,Color.RGBtoHSB(color.getRed(),color.getGreen(),color.getBlue(),null)[0]);});hit.setting=color;add(hit);
            row+=40;return open.contains(value)?picker(color,row):row;
        }
        if(value instanceof ModeSetting||value instanceof MultiSelectSetting){
            int width=Math.min(190,Math.max(90,Math.round(cw*.46F))),a=cx+cw-width;
            text(FONT,value.getName(),cx+1,row+10,cw-width-14,TEXT);
            String label=value instanceof ModeSetting?((ModeSetting)value).getValue():((MultiSelectSetting)value).getValue().size()+" selected";
            material("open:"+value.getRawName(),a,row+3,width,27,8,hover("open:"+value.getRawName(),a,row+3,width,27),open.contains(value));
            if(visible(a,row+3,width,27)){FONT.centered(FONT.fit(label,width-32),a+5,row+3,width-26,27,TEXT);BreezeIcons.draw("chevrons",a+width-19,row+10,13,TEXT);}
            add(new Hit("open:"+value.getRawName(),a,row+3,width,27,()->{commit();if(!open.remove(value))open.add(value);}));
            row+=32;
            if(open.contains(value)){
                List<String> options=value instanceof ModeSetting?((ModeSetting)value).getModes():((MultiSelectSetting)value).getOptions();
                material("choices:"+value.getRawName(),cx,row,cw,options.size()*29+8,8,0,false);row+=4;
                for(String option:options){
                    boolean on=value instanceof ModeSetting?((ModeSetting)value).is(option):((MultiSelectSetting)value).isSelected(option);
                    String id="option:"+value.getRawName()+":"+option;float hot=hover(id,cx+4,row,cw-8,28);
                    if(on||hot>.005F)rect(cx+4,row,cw-8,28,5,RenderUtils.alpha(accent,on?115:Math.round(30*hot)));
                    if(on){glowDot(cx+15,row+14,3,1,accent);text(BOLD,option,cx+28,row+9,cw-63,TEXT);BreezeIcons.draw("check",cx+cw-23,row+8,13,TEXT);}
                    else text(FONT,option,cx+28,row+9,cw-43,MUTED);
                    add(new Hit(id,cx+4,row,cw-8,28,()->{if(value instanceof ModeSetting){((ModeSetting)value).setValue(option);open.remove(value);}else ((MultiSelectSetting)value).toggle(option);}));row+=29;}
                row+=4;
            }
            return row+9;
        }
        text(FONT,value.getName(),cx+1,row+4,cw-4,TEXT);row+=23;
        if(value instanceof NumberSetting||value instanceof RangeSetting){
            double min=value instanceof NumberSetting?((NumberSetting)value).getMinimum():((RangeSetting)value).getMinimum(),max=value instanceof NumberSetting?((NumberSetting)value).getMaximum():((RangeSetting)value).getMaximum();
            double low=value instanceof NumberSetting?min:((RangeSetting)value).getMin(),high=value instanceof NumberSetting?((NumberSetting)value).getDouble():((RangeSetting)value).getMax();
            int box=value instanceof RangeSetting?90:57,sx=cx+4,sw=Math.max(25,cw-box-18);track(sx,row+13,sw,min,max,low,high);
            Hit hit=new Hit("slider:"+value.getRawName(),sx,row,sw,27,null);hit.setting=value;hit.min=min;hit.max=max;sliderHit(hit);
            valueBox(value,cx+cw-box,row,box,value instanceof RangeSetting?number(low)+" – "+number(high):number(high));return row+37;
        }
        if(value instanceof StringSetting){valueBox(value,cx,row,cw,((StringSetting)value).getValue());return row+37;}
        return row+5;
    }
    private int picker(ColorSetting color,int row){
        float hue=hues.computeIfAbsent(color,c->Color.RGBtoHSB(c.getRed(),c.getGreen(),c.getBlue(),null)[0]);int pw=cw-20,ph=86;
        material("picker:"+color.getRawName(),cx,row,cw,164,9,0,false);
        int sx=cx+10,sy=row+10,full=Color.HSBtoRGB(hue,1,1);gradient(sx,sy,pw,ph,0xFFFFFFFF,full,0xFFFFFFFF,full);gradient(sx,sy,pw,ph,0,0,0xFF000000,0xFF000000);
        Hit sv=new Hit("saturation:"+color.getRawName(),sx,sy,pw,ph,null);sv.setting=color;sv.channel=0;sliderHit(sv);
        int hy=sy+ph+10;for(int i=0;i<pw;i++)rect(sx+i,hy,1,10,0,Color.HSBtoRGB(i/(float)pw,1,1));Hit ht=new Hit("hue:"+color.getRawName(),sx,hy-2,pw,14,null);ht.setting=color;ht.channel=1;sliderHit(ht);
        int ay=hy+34;SMALL.draw("Opacity",sx,hy+18,MUTED);String opacity=Math.round(color.getAlpha()/255F*100)+"%";SMALL.draw(opacity,sx+pw-SMALL.width(opacity),hy+18,TEXT);
        checker(sx,ay,pw,9);int rgb=color.getArgb()&0xFFFFFF;gradient(sx,ay,pw,9,rgb,rgb|0xFF000000,rgb,rgb|0xFF000000);
        Hit at=new Hit("alpha:"+color.getRawName(),sx,ay-2,pw,13,null);at.setting=color;at.channel=2;sliderHit(at);
        float[] hsv=Color.RGBtoHSB(color.getRed(),color.getGreen(),color.getBlue(),null);int px=sx+Math.round((pw-1)*hsv[1]),py=sy+Math.round((ph-1)*(1-hsv[2]));rect(px-3,py-3,6,6,3,TEXT);rect(px-1,py-1,2,2,1,color.getArgb()|0xFF000000);
        rect(sx+Math.round((pw-1)*hue)-1,hy-2,3,14,1,TEXT);rect(sx+Math.round((pw-1)*color.getAlpha()/255F)-1,ay-2,3,13,1,TEXT);return row+174;
    }
    private void checker(int a,int b,int width,int height){
        if(!visible(a,b,width,height))return;
        for(int i=0;i<width;i+=5)for(int j=0;j<height;j+=5)rect(a+i,b+j,Math.min(5,width-i),Math.min(5,height-j),0,((i/5+j/5)&1)==0?0xFFB9C3CF:0xFF657382);
    }
    private static net.minecraft.util.ResourceLocation swatchTexture;
    private void swatch(int a,int b,int color){
        if(!visible(a,b,43,19))return;
        if(swatchTexture==null){
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(172,76,java.awt.image.BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=image.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(java.awt.Color.WHITE);g.fillRoundRect(0,0,172,76,32,32);
            g.setComposite(java.awt.AlphaComposite.SrcIn);for(int i=0;i<172;i+=20)for(int j=0;j<76;j+=20){g.setColor(new java.awt.Color(((i/20+j/20)&1)==0?0xB9C3CF:0x657382));g.fillRect(i,j,20,20);}g.dispose();
            net.minecraft.client.renderer.texture.DynamicTexture texture=new net.minecraft.client.renderer.texture.DynamicTexture(image);texture.setBlurMipmap(true,false);swatchTexture=mc.getTextureManager().getDynamicTextureLocation("vibe_color_swatch",texture);
        }
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GlStateManager.enableTexture2D();GlStateManager.disableAlpha();GlStateManager.color(1,1,1,1);mc.getTextureManager().bindTexture(swatchTexture);
        Gui.drawScaledCustomSizeModalRect(a,b,0,0,172,76,43,19,172,76);rect(a,b,43,19,4,color);
    }
    private void valueBox(Setting<?> value,int a,int b,int width,String label){
        material("value:"+value.getRawName(),a,b,width,27,7,hover("value:"+value.getRawName(),a,b,width,27),editing==value);
        if(editing==value&&editor!=null){editor.xPosition=a+7;editor.yPosition=b+2;editor.width=width-14;editor.palette(TEXT,accent);if(visible(a,b,width,27))editor.drawTextBox();}
        else text(FONT,label.isEmpty()?"Click to edit":label,a+7,b+8,width-14,TEXT);
        Hit hit=new Hit("value:"+value.getRawName(),a,b,width,27,()->edit(value,a+7,b+2,width-14));hit.setting=value;add(hit);
    }
    private void edit(Setting<?> value,int a,int b,int width){
        commit();editing=value;editor=new MenuTextField(1,mc.fontRendererObj,a,b,width,22);editor.setEnableBackgroundDrawing(false);editor.setMaxStringLength(value instanceof StringSetting?((StringSetting)value).getMaxLength():64);
        editor.setText(value instanceof NumberSetting?number(((NumberSetting)value).getDouble()):value instanceof RangeSetting?number(((RangeSetting)value).getMin())+" "+number(((RangeSetting)value).getMax()):((StringSetting)value).getValue());editor.setFocused(true);
    }
    private void commit(){
        if(editing!=null&&editor!=null)try{String value=editor.getText();if(editing instanceof NumberSetting){double n=Double.parseDouble(value);if(Double.isFinite(n))((NumberSetting)editing).setValue(n);}else if(editing instanceof RangeSetting){String[] pair=value.trim().split("[ ,;]+");if(pair.length==2){double a=Double.parseDouble(pair[0]),b=Double.parseDouble(pair[1]);if(Double.isFinite(a)&&Double.isFinite(b))((RangeSetting)editing).setRange(a,b);}}else if(editing instanceof StringSetting)((StringSetting)editing).setValue(value);}catch(NumberFormatException ignored){}
        editing=null;editor=null;
    }
    private void track(int a,int b,int width,double min,double max,double low,double high){
        if(!visible(a,b-5,width,11))return;double span=Math.max(.00001,max-min);int left=a+(int)Math.round(width*(low-min)/span),right=a+(int)Math.round(width*(high-min)/span);
        rect(a,b-2,width,4,2,0x806D8DA8);glow(left,b-3,Math.max(2,right-left),6,3,.25F);rect(left,b-2,Math.max(1,right-left),4,2,accent);glowDot(right,b,4,.7F,accent);rect(right-3,b-4,6,8,3,TEXT);if(low!=min)rect(left-3,b-4,6,8,3,TEXT);
    }
    private void sliderHit(Hit hit){hit.action=()->{commit();slider=hit;rangeDrag=hit.setting instanceof RangeSetting?((RangeSetting)hit.setting).beginDrag(value(hit,mx)):null;updateSlider(mx,my);};add(hit);}
    private static double value(Hit hit,int mx){return hit.min+(hit.max-hit.min)*clamp((mx-hit.x)/(float)Math.max(1,hit.w),0,1);}
    private void updateSlider(int mouseX,int mouseY){
        if(slider==null)return;if(!slider.setting.isVisible()){slider=null;return;}
        if(slider.setting instanceof NumberSetting)((NumberSetting)slider.setting).setValue(value(slider,mouseX));else if(rangeDrag!=null)rangeDrag.move(value(slider,mouseX));
        else if(slider.setting instanceof ColorSetting){ColorSetting c=(ColorSetting)slider.setting;float[] hsv=Color.RGBtoHSB(c.getRed(),c.getGreen(),c.getBlue(),null);float hue=hues.getOrDefault(c,hsv[0]),f=clamp((mouseX-slider.x)/(float)Math.max(1,slider.w-1),0,1);int alpha=c.getAlpha();if(slider.channel==0){hsv[1]=f;hsv[2]=1-clamp((mouseY-slider.y)/(float)Math.max(1,slider.h-1),0,1);}else if(slider.channel==1)hue=f;else alpha=Math.round(f*255);hues.put(c,hue);int rgb=Color.HSBtoRGB(hue,hsv[1],hsv[2]);c.setRgba(rgb>>16&255,rgb>>8&255,rgb&255,alpha);}
    }
    private void material(String id,int a,int b,int width,int height,int radius,float hover,boolean on){
        if(!visible(a,b,width,height))return;int fill=palette.surface,border=RenderUtils.blend(palette.surface,accent,on?.85F:.22F);
        if(!MenuGlass.control(a,b,width,height,radius,fill,border,hover,clamp((mx-a)/(float)Math.max(1,width),0,1),clamp((my-b)/(float)Math.max(1,height),0,1)))rect(a,b,width,height,radius,0x9055758A);
    }
    private void button(String id,int a,int b,int width,int height,String label,boolean on,Runnable action){
        material(id,a,b,width,height,8,hover(id,a,b,width,height),on);if(visible(a,b,width,height))FONT.centered(FONT.fit(label,width-10),a,b,width,height,TEXT);add(new Hit(id,a,b,width,height,action));
    }
    private float hover(String id,int a,int b,int width,int height){float v=hoverValues.getOrDefault(id,0F);v=ease(v,mx>=a&&mx<a+width&&my>=b&&my<b+height?1:0,16);hoverValues.put(id,v);return v;}
    private float animated(Object key,boolean on){Motion motion=motions.get(key);if(motion==null){motion=new Motion(on?1:0);motions.put(key,motion);}if(motion.frame!=frame){motion.value=ease(motion.value,on?1:0,16);motion.frame=frame;}return motion.value;}
    private float ease(float from,float to,float speed){return from+(to-from)*(1-(float)Math.exp(-speed*frameDt));}
    private void glowDot(float a,float b,float radius,float enabled,int color){
        if(!visible((int)(a-radius-8),(int)(b-radius-8),(int)(radius*2+16),(int)(radius*2+16)))return;
        for(int i=7;i>=1;i--){float r=radius+i;MenuRoundedRenderer.rect(Math.round(a-r),Math.round(b-r),Math.round(r*2),Math.round(r*2),Math.round(r),RenderUtils.alpha(color,Math.round(enabled*(8-i)*3)));}
        MenuRoundedRenderer.rect(Math.round(a-radius),Math.round(b-radius),Math.round(radius*2),Math.round(radius*2),Math.round(radius),RenderUtils.blend(0xFF5D788B,color,enabled));
    }
    private void glow(int a,int b,int width,int height,int radius,float strength){if(!visible(a-6,b-6,width+12,height+12))return;for(int i=6;i>=1;i--)rect(a-i,b-i,width+i*2,height+i*2,radius+i,RenderUtils.alpha(accent,Math.round(strength*(7-i)*2)));}
    private void resizeGrip(){
        boolean hot=mx>=x+w-19&&my>=y+h-19;int color=RenderUtils.alpha(TEXT,hot||resizing?240:170);
        try(dev.vibe.ui.effect.EffectState ignored=new dev.vibe.ui.effect.EffectState()){
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1.2F);color(color);GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(x+w-17,y+h-8);GL11.glVertex2f(x+w-8,y+h-17);GL11.glVertex2f(x+w-12,y+h-8);GL11.glVertex2f(x+w-8,y+h-12);GL11.glEnd();
        }
    }
    private void scrollbar(int a,int b,int height,float offset,int max,boolean list){
        if(max<=0||height<8)return;int thumb=Math.max(15,height*height/(height+max)),at=b+Math.round(offset/Math.max(1,max)*(height-thumb));rect(a,b,2,height,1,0x306D98B5);glow(a,at,2,thumb,1,.25F);rect(a,at,2,thumb,1,RenderUtils.alpha(accent,215));
        add(new Hit(list?"modules-scrollbar":"settings-scrollbar",a-3,b,8,height,()->{commit();scrolling=true;scrollingList=list;dragScroll(my,list);}));
    }
    private void dragScroll(int pointer,boolean list){int start=list?listTop:cy,height=bottom-start,max=list?listMax:maxScroll,thumb=Math.max(15,height*height/Math.max(1,height+max));float position=clamp((pointer-start-thumb*.5F)/Math.max(1,height-thumb)*max,0,max);if(list)listScroll=listTarget=position;else scroll=target=position;}
    private void choose(Category next){remember();commit();category=next;selected=null;binding=null;search.setText("");lastQuery="";listScroll=listTarget=moduleScroll.getOrDefault(next,0F);scroll=target=0;open.clear();}
    private void select(Module module){if(selected==module)return;remember();commit();selected=module;binding=null;slider=null;rangeDrag=null;open.clear();scroll=target=module==null?0:settingScroll.getOrDefault(module.getId(),0F);}
    private void remember(){if(search==null||search.getText().trim().isEmpty())moduleScroll.put(category,listTarget);if(selected!=null)settingScroll.put(selected.getId(),target);}
    private void clipping(int a,int b,int width,int height){clipLeft=a;clipTop=b;clipRight=a+Math.max(0,width);clipBottom=b+Math.max(0,height);}
    private boolean visible(int a,int b,int width,int height){return a<clipRight&&a+width>clipLeft&&b<clipBottom&&b+height>clipTop;}
    private void add(Hit hit){hit.left=Math.max(hit.x,clipLeft);hit.top=Math.max(hit.y,clipTop);hit.right=Math.min(hit.x+hit.w,clipRight);hit.bottom=Math.min(hit.y+hit.h,clipBottom);if(hit.right>hit.left&&hit.bottom>hit.top)hits.add(hit);}
    private void text(MenuFont font,String value,int a,int b,int width,int color){if(!visible(a,b,width,18))return;String label=font.fit(value,Math.max(0,width));font.draw(label,a,b+1,0x90000000);font.draw(label,a,b,color);}
    private void rect(int a,int b,int width,int height,int radius,int color){if(width>0&&height>0&&visible(a,b,width,height))MenuRoundedRenderer.rect(a,b,width,height,radius,color);}
    private void line(int a,int b,int c,int d,int color){Gui.drawRect(a,b,c+1,d+1,color);}

    void click(int mouseX,int mouseY,int button){
        mx=mouseX;my=mouseY;
        if(mx<x||mx>=x+w||my<y||my>=y+h)return;
        if(button==0&&mx>=x+w-19&&my>=y+h-19){commit();resizing=true;return;}
        if(search!=null){search.mouseClicked(mx,my,button);if(search.isFocused()){commit();return;}}
        if(editor!=null&&mx>=editor.xPosition-6&&mx<editor.xPosition+editor.width+6&&my>=editor.yPosition-2&&my<editor.yPosition+25){editor.mouseClicked(mx,my,button);return;}
        commit();
        for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(!hit.contains(mx,my))continue;if(button==1){if(hit.module!=null)select(hit.module);else if(hit.id.equals("binding")&&selected!=null)selected.setKey(0);}else if(button==0)hit.action.run();return;}
        if(button==0&&my<y+50){dragging=true;dragX=mx-x;dragY=my-y;}
    }
    void release(int button){if(button==0){slider=null;rangeDrag=null;dragging=resizing=scrolling=false;}}
    void wheel(int mouseX,int mouseY,int amount){
        if(amount==0||mouseX<x||mouseX>=x+w)return;
        if(mouseY>=y+10&&mouseY<y+45){tabOffset=Math.max(0,Math.min(tabMaximum,tabOffset+(amount>0?-80:80)));return;}
        if(mouseY<listTop||mouseY>=bottom)return;commit();
        if(mouseX<x+side+8)listTarget=clamp(listTarget+(amount>0?-52:52),0,listMax);else if(mouseX>=cx&&mouseY>=cy)target=clamp(target+(amount>0?-52:52),0,maxScroll);
    }
    boolean key(char character,int key){
        if(binding!=null){binding.setKey(key==Keyboard.KEY_ESCAPE||key==Keyboard.KEY_DELETE?0:key);binding=null;return true;}
        if(editor!=null){if(key==Keyboard.KEY_ESCAPE){editing=null;editor=null;return true;}if(key==Keyboard.KEY_RETURN){commit();return true;}editor.textboxKeyTyped(character,key);return true;}
        if(search!=null&&search.isFocused()&&key==Keyboard.KEY_ESCAPE){search.setText("");search.setFocused(false);return true;}
        return search!=null&&search.textboxKeyTyped(character,key);
    }
    void tick(){if(search!=null)search.updateCursorCounter();if(editor!=null)editor.updateCursorCounter();}
    void close(){remember();commit();release(0);binding=null;last=System.nanoTime();if(search!=null)search.setFocused(false);}
    private static String keyName(int key){String name=key==0?null:Keyboard.getKeyName(key);return name==null?"Unbound":name;}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private static String number(double n){return java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();}
    private static void gradient(int x,int y,int w,int h,int tl,int tr,int bl,int br){GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GL11.glShadeModel(GL11.GL_SMOOTH);GL11.glBegin(GL11.GL_QUADS);color(tl);GL11.glVertex2f(x,y);color(bl);GL11.glVertex2f(x,y+h);color(br);GL11.glVertex2f(x+w,y+h);color(tr);GL11.glVertex2f(x+w,y);GL11.glEnd();GL11.glShadeModel(GL11.GL_FLAT);GlStateManager.enableTexture2D();GlStateManager.resetColor();}
    private static void color(int c){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);}
    private static final class Motion{float value;long frame;Motion(float value){this.value=value;}}
    private static final class Hit{
        final String id;final int x,y,w,h;int left,top,right,bottom,channel=-1;double min,max;Setting<?> setting;Module module;Runnable action;
        Hit(String id,int x,int y,int w,int h,Runnable action){this.id=id;this.x=x;this.y=y;this.w=w;this.h=h;this.action=action;}
        boolean contains(int a,int b){return a>=left&&a<right&&b>=top&&b<bottom;}
    }
}
