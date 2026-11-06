package dev.vibe.ui.clickgui;

import com.google.gson.*;
import dev.vibe.Vibe;
import dev.vibe.config.OnlineConfigService;
import dev.vibe.friend.FriendManager.Friend;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.setting.*;
import dev.vibe.ui.*;
import dev.vibe.ui.menu.MenuFont;
import dev.vibe.ui.menu.MenuTextField;
import java.awt.Desktop;
import java.net.URI;
import java.util.*;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Breeze's list/sidebar presentation over Vibe's existing module, config and friend models. */
final class BreezeWorkspace {
    enum Page { MODULES, CONFIGS, SOCIAL, THEMES }
    private static final MenuFont NAME=new MenuFont(java.awt.Font.PLAIN,14),BRAND=new MenuFont(java.awt.Font.BOLD,16),BODY=new MenuFont(java.awt.Font.PLAIN,12),SMALL=new MenuFont(java.awt.Font.PLAIN,10);
    private static final ResourceLocation GHOST=new ResourceLocation("vibe","branding/ghost-static.png");
    private final Minecraft mc=Minecraft.getMinecraft();
    private final List<Hit> hits=new ArrayList<Hit>();
    private final Set<Setting<?>> open=Collections.newSetFromMap(new IdentityHashMap<Setting<?>,Boolean>());
    private final Map<String,JsonObject> onlinePreviews=new HashMap<String,JsonObject>();
    private Page page=Page.MODULES;
    private Category category;
    private boolean favorites,online,fetching,closed,dragging,scrollDragging,withKeys=true,withVisuals=true,inContent,expanded,bookmarkedThemes;
    private int x=Integer.MIN_VALUE,y,w,h,side,contentX,contentY,contentW,bottom,maxScroll,tabScroll,maxTabScroll,dragX,dragY,generation,pointerX,pointerY;
    private float scroll,targetScroll;
    private long last=System.nanoTime();
    private MenuTextField search,input,alias,editor;
    private Setting<?> editing;
    private Hit slider;
    private RangeSetting.Drag rangeDrag;
    private Module selected,binding;
    private String chosenConfig,status="";
    private Friend friend;
    private JsonObject preview;
    private List<String> localConfigs=Collections.emptyList();
    private final Map<String,String> configCreators=new HashMap<String,String>();
    private List<OnlineConfigService.Entry> onlineConfigs=Collections.emptyList();
    private BreezePalette palette=BreezePalette.DEFAULT;
    private ClickGuiModule settings(){return Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);}
    private void rect(int a,int b,int width,int height,int radius,int color){if(inContent&&(b+height<contentY||b>=bottom))return;MenuRoundedRenderer.rect(a,b,width,height,radius,color);}
    private void text(MenuFont font,String label,int a,int b,int width,int color){if(inContent&&(b+20<contentY||b>=bottom))return;font.draw(font.fit(label,Math.max(0,width)),a,b,color);}
    private void hit(String id,int a,int b,int width,int height,IntConsumer action){
        if(b<bottom&&b+height>contentY)hits.add(new Hit(id,a,Math.max(b,contentY),width,Math.min(b+height,bottom)-Math.max(b,contentY),action));
    }
    private void control(String id,int a,int b,int width,int height,String label,boolean active,Runnable action){
        rect(a,b,width,height,5,active?palette.accent:palette.raised);BODY.centered(label,a,b,width,height,palette.text);
        IntConsumer click=button->{if(button==0)action.run();};if(inContent)hit(id,a,b,width,height,click);else hits.add(new Hit(id,a,b,width,height,click));
    }
    private void select(Page next){commit();generation++;fetching=false;page=next;selected=null;friend=null;chosenConfig=null;preview=null;input=null;alias=null;targetScroll=scroll=0;search.setText("");search.setFocused(false);status="";if(next==Page.CONFIGS)refreshConfigs();}
    void draw(int screenW,int screenH,int mx,int my,float partialTicks){
        pointerX=mx;pointerY=my;
        if(closed&&fetching&&page==Page.CONFIGS){
            fetching=false;float previousScroll=scroll;
            if(chosenConfig!=null&&preview==null)chooseConfig(chosenConfig);else if(online)refreshOnline();else refreshConfigs();
            scroll=targetScroll=previousScroll;
        }
        closed=false;palette=BreezePalette.from(settings().getBreezePalette().getValue());
        w=expanded?screenW-20:Math.min(720,screenW-20);h=expanded?screenH-20:Math.min(440,screenH-20);side=Math.max(94,Math.min(164,w*22/100));
        if(x==Integer.MIN_VALUE){x=(screenW-w)/2;y=(screenH-h)/2;}
        if(dragging){x=mx-dragX;y=my-dragY;}
        x=Math.max(0,Math.min(screenW-w,x));y=Math.max(0,Math.min(screenH-h,y));
        contentX=x+side+10;contentW=w-side-20;contentY=y+(page==Page.MODULES&&selected!=null?110:83);bottom=y+h-12-(status.isEmpty()?0:28);
        if(search==null){search=new MenuTextField(0,mc.fontRendererObj,0,0,100,16);search.setMaxStringLength(64);search.setEnableBackgroundDrawing(false);}
        search.xPosition=contentX+Math.max(14,(contentW-Math.min(204,contentW-14))/2)+20;search.yPosition=y+18;search.width=Math.min(174,contentW-42);search.setTextColor(palette.text);
        if(page==Page.MODULES&&selected!=null&&contentW<300){search.xPosition=contentX+52;search.width=Math.max(40,contentW-66);}
        search.palette(palette.text,palette.accent);
        long now=System.nanoTime();float dt=Math.min(.1F,(now-last)/1e9F);last=now;
        scroll+=(targetScroll-scroll)*(1-(float)Math.exp(-18*dt));
        if(slider!=null)updateSlider(mx);if(scrollDragging)dragScroll(my);
        hits.clear();GuiRenderState.prepare(false);
        dev.vibe.module.impl.client.BlurModule blur=Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.client.BlurModule.class);
        if(blur!=null&&blur.isEnabled()&&blur.getElements().isSelected(dev.vibe.module.impl.client.BlurModule.CLICK_GUI))KawaseBlur.drawBackdrop(screenW,screenH,blur.getStrength().getInt(),partialTicks);
        Gui.drawRect(0,0,screenW,screenH,mc.theWorld==null?0xFF29272C:0x28000000);
        for(int i=8;i>0;i--)rect(x-i,y-i+2,w+2*i,h+2*i,18+i,(palette==BreezePalette.DEFAULT?0x06000000:RenderUtils.alpha(palette.accent,5)));
        rect(x-1,y-1,w+2,h+2,23,palette.border);rect(x,y,w,h,22,RenderUtils.alpha(palette.background,mc.theWorld==null?255:226));
        BreezeArtwork.draw(palette,x,y,w,h);
        Gui.drawRect(x+side,y+1,x+side+1,y+h-1,RenderUtils.alpha(palette.muted,32));
        Gui.drawRect(x+side,y+49,x+w-1,y+50,RenderUtils.alpha(palette.muted,26));
        sidebar();
        rect(search.xPosition-24,y+8,search.width+34,34,10,palette.raised);rect(search.xPosition-22,y+10,search.width+30,30,8,RenderUtils.alpha(palette.background,230));
        BreezeIcons.draw("search",search.xPosition-19,y+16,17,palette.muted);
        search.drawTextBox();if(search.getText().isEmpty()&&!search.isFocused())SMALL.draw("Search...",search.xPosition,search.yPosition+2,palette.muted);
        if(page==Page.MODULES){if(selected==null)tabs();else moduleHeader();}else{
            NAME.draw(page==Page.CONFIGS?"Configs":page==Page.SOCIAL?"Social":"Themes",contentX+4,y+59,palette.text);
            if(page==Page.CONFIGS){int a=contentX+Math.min(90,contentW/3);control("local",a,y+55,50,22,"Local",!online,()->{online=false;chosenConfig=null;preview=null;scroll=targetScroll=0;refreshConfigs();});control("online",a+54,y+55,55,22,"Online",online,()->{online=true;chosenConfig=null;preview=null;scroll=targetScroll=0;refreshOnline();});}
            if(page==Page.SOCIAL&&friend!=null)control("friend-new",contentX+contentW-82,y+55,82,22,"New friend",false,()->{friend=null;input.setText("");input.setEnabled(true);alias.setText("");});
            if(page==Page.THEMES)control("theme-bookmarks",contentX+contentW-92,y+55,92,22,"Bookmarked",bookmarkedThemes,()->{bookmarkedThemes=!bookmarkedThemes;scroll=targetScroll=0;});
        }
        int end;
        inContent=true;
        try(GuiClip clip=new GuiClip(contentX,contentY,contentW,bottom-contentY)){
            if(page==Page.MODULES)end=selected==null?modules(mx,my):moduleSettings();
            else if(page==Page.CONFIGS)end=configs();else if(page==Page.SOCIAL)end=social();else end=themes();
        }
        inContent=false;
        maxScroll=Math.max(0,end+Math.round(scroll)-bottom+5);targetScroll=Math.max(0,Math.min(maxScroll,targetScroll));
        if(maxScroll>0){int track=bottom-contentY,thumb=Math.max(22,track*track/(track+maxScroll)),top=contentY+Math.round(scroll/maxScroll*(track-thumb));rect(x+w-5,contentY,2,track,1,palette.row);rect(x+w-5,top,2,thumb,1,palette.muted);hits.add(new Hit("scrollbar",x+w-9,contentY,9,track,b->{if(b==0){scrollDragging=true;dragScroll(my);}}));}
        if(!status.isEmpty()){int statusY=y+h-36;rect(contentX,statusY,contentW,24,5,palette.background);text(SMALL,status,contentX+7,statusY+7,contentW-14,palette.muted);}
    }
    private void sidebar(){
        GlStateManager.color(1,1,1,1);mc.getTextureManager().bindTexture(GHOST);Gui.drawScaledCustomSizeModalRect(x+14,y+13,0,0,256,256,30,30,256,256);
        text(BRAND,"Vibe",x+50,y+12,side-57,palette.text);text(SMALL,"Breeze",x+50,y+32,side-57,palette.muted);
        boolean compact=h<300;int navHeight=compact?22:24,navStep=compact?25:31;int row=y+(compact?56:64);
        for(Page item:Page.values()){
            boolean on=page==item,hot=pointerX>=x+10&&pointerX<x+side-10&&pointerY>=row&&pointerY<row+navHeight;if(on||hot)rect(x+10,row,side-20,navHeight,8,on?palette.accent:RenderUtils.alpha(palette.raised,180));
            BreezeIcons.draw(item.name().toLowerCase(Locale.ROOT),x+17,row+4,17,on?0xFFFFFFFF:hot?palette.text:palette.muted);
            text(BODY,item==Page.MODULES?"Modules":item==Page.CONFIGS?"Configs":item==Page.SOCIAL?"Social":"Themes",x+36,row+5,side-45,on?0xFFFFFFFF:hot?palette.text:palette.muted);
            hits.add(new Hit("page:"+item,x+10,row,side-20,navHeight,b->{if(b==0)select(item);}));row+=navStep;
        }
        int cardY=y+h-(compact?60:80),footer=compact?35:46;rect(x+9,cardY,side-18,compact?49:69,12,palette.row);rect(x+9,cardY+footer,side-18,compact?14:23,7,palette.raised);
        BreezeIcons.draw("profile",x+15,cardY+7,30,palette.muted);
        String username=Vibe.getInstance().getIdentity()!=null?Vibe.getInstance().getIdentity().getGamertag():mc.getSession().getUsername();
        text(SMALL,username,x+48,cardY+12,side-61,palette.text);text(SMALL,"Vibe user",x+48,cardY+27,side-61,palette.muted);
        int slot=(side-18)/4,iconSize=compact?11:14;
        for(int i=0;i<4;i++){final int action=i;String name=new String[]{"discord","help","expand","close"}[i];int a=x+9+i*slot;boolean hot=pointerX>=a&&pointerX<a+slot&&pointerY>=cardY+footer&&pointerY<cardY+footer+(compact?14:23);BreezeIcons.draw(name,a+(slot-iconSize)/2,cardY+footer+((compact?14:23)-iconSize)/2,iconSize,hot?palette.text:palette.muted);hits.add(new Hit(name,a,cardY+footer,slot,compact?14:23,b->{if(b==0){if(action==0)browse("https://discord.gg/ehv2UKbSvW");else if(action==1)browse("https://skidderclub.xyz");else if(action==2){expanded=!expanded;x=Integer.MIN_VALUE;}else mc.displayGuiScreen(null);}}));}
    }
    private void tabs(){
        int at=contentX-tabScroll;
        try(GuiClip clip=new GuiClip(contentX,y+52,contentW,27)){
            List<String> labels=new ArrayList<String>();for(Category value:Category.values())labels.add(value.getLabel());labels.add("Favorite");labels.add("All");
            for(int i=0;i<labels.size();i++){final int index=i;String label=labels.get(i);int width=(int)Math.ceil(SMALL.width(label))+14;boolean on=i==Category.values().length+1?category==null&&!favorites:i==Category.values().length?favorites:!favorites&&category==Category.values()[i];
                rect(at,y+56,width,22,5,on?palette.accent:palette.row);SMALL.centered(label,at,y+56,width,22,on?0xFFFFFFFF:palette.muted);
                int left=Math.max(contentX,at),right=Math.min(contentX+contentW,at+width);
                if(right>left)hits.add(new Hit("category:"+label,left,y+56,right-left,22,b->{if(b==0){category=index<Category.values().length?Category.values()[index]:null;favorites=index==Category.values().length;selected=null;scroll=targetScroll=0;}}));at+=width+5;
            }
        }
        maxTabScroll=Math.max(0,at+tabScroll-contentX-contentW);tabScroll=Math.max(0,Math.min(maxTabScroll,tabScroll));
    }
    List<Module> visibleModules(){
        List<Module> result=new ArrayList<Module>();Set<String> starred=stars();String query=search==null?"":search.getText().toLowerCase(Locale.ROOT);
        for(Module m:Vibe.getInstance().getModuleManager().getModules())if((category==null||m.getCategory()==category)&&(!favorites||starred.contains(m.getId()))&&(m.getName()+" "+m.getDescription()).toLowerCase(Locale.ROOT).contains(query))result.add(m);
        result.sort(Comparator.comparing(Module::getName,String.CASE_INSENSITIVE_ORDER));return ClickGuiModule.sortedModules(result);
    }
    private int modules(int mx,int my){
        int row=contentY-Math.round(scroll),height=contentW<280?46:50;
        for(Module module:visibleModules()){
            if(row+height<contentY||row>=bottom){row+=height+6;continue;}
            final int ry=row;boolean over=mx>=contentX&&mx<contentX+contentW&&my>=row&&my<row+height;
            rect(contentX,row,contentW,height,15,over?palette.raised:palette.row);
            try(GuiClip clip=new GuiClip(contentX,row,42,height)){rect(contentX,row,contentW,height,15,module.isEnabled()?palette.accent:palette.raised);}
            if(module.isEnabled())BreezeIcons.draw("check-circle",contentX+7,row+(height-29)/2,29,0xFFF5F5F5);else circle(contentX+21,row+height/2,2,palette.muted,true);
            text(NAME,module.getName(),contentX+49,row+8,contentW-75,module.isEnabled()?palette.text:RenderUtils.blend(palette.text,palette.muted,.25F));
            text(SMALL,module.getDescription(),contentX+49,row+28,contentW-78,palette.muted);
            try(GuiClip clip=new GuiClip(contentX+contentW-24,row,24,height)){rect(contentX,row,contentW,height,15,palette.raised);}BreezeIcons.draw("dots",contentX+contentW-20,row+(height-20)/2,20,palette.text);
            hit("module:"+module.getId(),contentX,row,contentW-25,height,b->{if(b==0)module.toggle();else if(b==1){selected=module;scroll=targetScroll=0;}else if(b==2)binding=module;});
            hit("settings:"+module.getId(),contentX+contentW-25,row,25,height,b->{selected=module;scroll=targetScroll=0;});row+=height+6;
        }
        if(visibleModules().isEmpty())text(BODY,"No modules found",contentX+12,row+24,contentW-24,palette.muted);
        return row;
    }
    private void moduleHeader(){
        final Module module=selected;int a=x+side+1,b=y+50,width=w-side-2,height=54;
        rect(a,b,width,height,0,palette.row);rect(a,b,45,height,0,module.isEnabled()?palette.accent:palette.raised);
        if(module.isEnabled())BreezeIcons.draw("check-circle",a+7,b+11,31,palette.text);else circle(a+22,b+27,3,palette.text,true);
        text(NAME,module.getName(),a+53,b+12,width-114,palette.text);text(SMALL,module.getDescription(),a+53,b+33,width-115,palette.muted);
        BreezeIcons.draw(stars().contains(module.getId())?"star-filled":"star",a+width-48,b+18,15,palette.muted);BreezeIcons.draw("dots",a+width-24,b+16,21,palette.text);
        hits.add(new Hit("header-toggle",a,b,width-60,height,button->{if(button==0)module.toggle();}));
        hits.add(new Hit("favorite",a+width-55,b,28,height,button->{if(button==0)toggleStar(module);}));
        hits.add(new Hit("header-collapse",a+width-27,b,27,height,button->{if(button==0){commit();selected=null;scroll=targetScroll=0;}}));
        BreezeIcons.draw("back",contentX,y+16,19,palette.muted);hits.add(new Hit("back",contentX-2,y+10,25,30,button->{if(button==0){commit();selected=null;scroll=targetScroll=0;}}));
    }
    private int moduleSettings(){
        int row=contentY-Math.round(scroll);final Module module=selected;
        text(NAME,"Bind",contentX+10,row+8,contentW/3-15,palette.text);
        int bindX=contentX+Math.max(70,contentW/3),bindW=Math.min(110,contentW/2);
        control("binding",bindX,row+2,bindW,26,binding==module?"Press key…":Keyboard.getKeyName(module.getKey()),false,()->binding=module);
        if(contentW>350){rect(bindX+bindW+18,row+2,100,26,12,palette.accent);BODY.centered("Toggle",bindX+bindW+18,row+2,100,26,palette.text);}
        row+=40;
        for(Setting<?> setting:module.getSettings())if(setting.isVisible())row=setting(setting,row);
        return row;
    }
    private int setting(Setting<?> value,int row){
        int height=36,labelWidth=Math.max(68,Math.min(180,contentW*31/100)),controlX=contentX+labelWidth,controlW=contentW-labelWidth-8;
        boolean compact=contentW<270;boolean numeric=value instanceof NumberSetting||value instanceof RangeSetting;
        if(numeric&&compact)height=55;
        text(NAME,value.getName(),contentX+10,row+9,(numeric&&compact?contentW:labelWidth)-19,palette.text);
        if(value instanceof BooleanSetting){BooleanSetting flag=(BooleanSetting)value;int bx=contentX+contentW-45;rect(bx,row+7,34,21,11,flag.isEnabled()?palette.accent:palette.raised);circle(bx+(flag.isEnabled()?23:11),row+17,7,palette.text,true);hit("boolean:"+value.getRawName(),contentX,row,contentW,height,b->{if(b==0)flag.setEnabled(!flag.isEnabled());});}
        else if(numeric){
            double min=value instanceof NumberSetting?((NumberSetting)value).getMinimum():((RangeSetting)value).getMinimum(),max=value instanceof NumberSetting?((NumberSetting)value).getMaximum():((RangeSetting)value).getMaximum();
            double low=value instanceof NumberSetting?min:((RangeSetting)value).getMin(),high=value instanceof NumberSetting?((NumberSetting)value).getDouble():((RangeSetting)value).getMax();
            int boxW=value instanceof RangeSetting?75:52,boxX=contentX+contentW-boxW-1,sliderX=compact?contentX+12:controlX,sliderY=row+(compact?38:18),sliderW=boxX-sliderX-13;
            valueBox(value,boxX,row+(compact?25:5),boxW,value instanceof NumberSetting?number(high):number(low)+" – "+number(high));
            drawSlider(sliderX,sliderY,sliderW,min,max,low,high);Hit target=new Hit("slider:"+value.getRawName(),sliderX,sliderY-10,sliderW,22,null);target.setting=value;target.min=min;target.max=max;hitSlider(target);
        }else if(value instanceof StringSetting){
            rect(controlX,row+4,controlW,27,8,palette.row);if(editing==value&&editor!=null)drawEditor(controlX+7,row+9,controlW-14);else text(BODY,((StringSetting)value).getValue().isEmpty()?"Click to edit":((StringSetting)value).getValue(),controlX+7,row+10,controlW-14,palette.muted);
            final int editY=row+9;hit("string:"+value.getRawName(),controlX,row+4,controlW,27,b->{if(b==0)edit(value,controlX+7,editY,controlW-14);});
        }else if(value instanceof ModeSetting||value instanceof MultiSelectSetting||value instanceof ColorSetting){
            int boxW=Math.min(154,controlW),boxX=contentX+contentW-boxW-1;String label=value instanceof ModeSetting?((ModeSetting)value).getValue():value instanceof MultiSelectSetting?((MultiSelectSetting)value).getValue().size()+" selected":((ColorSetting)value).getHex();
            rect(boxX,row+4,boxW,26,8,palette.row);text(BODY,label,boxX+7,row+10,boxW-31,palette.muted);rect(boxX+boxW-20,row+5,17,24,5,palette.accent);BreezeIcons.draw("chevrons",boxX+boxW-19,row+8,15,palette.text);
            hit("open:"+value.getRawName(),boxX,row+3,boxW,29,b->{if(b==0){if(!open.remove(value))open.add(value);}else if(b==1&&value instanceof ModeSetting)((ModeSetting)value).cycle(true);});
            if(open.contains(value)){
                row+=height;
                if(value instanceof ColorSetting){ColorSetting color=(ColorSetting)value;rect(contentX+4,row,contentW-8,127,12,palette.row);for(int c=0;c<4;c++){int channel=c,channelValue=c==0?color.getRed():c==1?color.getGreen():c==2?color.getBlue():color.getAlpha();text(SMALL,new String[]{"Red","Green","Blue","Alpha"}[c]+": "+channelValue,contentX+15,row+10,78,palette.muted);drawSlider(contentX+94,row+16,contentW-115,0,255,0,channelValue);Hit target=new Hit("color:"+c,contentX+94,row+4,contentW-115,24,null);target.setting=color;target.min=0;target.max=255;target.channel=channel;hitSlider(target);row+=31;}}
                else{List<String> options=value instanceof ModeSetting?((ModeSetting)value).getModes():((MultiSelectSetting)value).getOptions();rect(boxX,row,boxW,options.size()*26+4,8,palette.row);for(String option:options){boolean on=value instanceof ModeSetting?((ModeSetting)value).is(option):((MultiSelectSetting)value).isSelected(option);if(on)rect(boxX+3,row+2,boxW-6,23,5,RenderUtils.blend(palette.row,palette.accent,.25F));text(BODY,option,boxX+9,row+8,boxW-31,on?palette.text:palette.muted);if(on)BreezeIcons.draw("check",boxX+boxW-22,row+7,14,palette.text);hit("option:"+option,boxX,row,boxW,26,b->{if(b==0){if(value instanceof ModeSetting){((ModeSetting)value).setValue(option);open.remove(value);}else ((MultiSelectSetting)value).toggle(option);}});row+=26;}}
                return row+8;
            }
        }
        return row+height+4;
    }
    private void valueBox(Setting<?> value,int a,int b,int width,String label){
        rect(a,b,width,26,10,palette.raised);rect(a+2,b+2,width-4,22,8,RenderUtils.alpha(palette.background,220));
        if(editing==value&&editor!=null)drawEditor(a+5,b+5,width-10);else BODY.centered(BODY.fit(label,width-8),a,b,width,26,palette.muted);
        hit("value:"+value.getRawName(),a,b,width,26,button->{if(button==0)edit(value,a+5,b+5,width-10);});
    }
    private void drawEditor(int a,int b,int width){editor.xPosition=a;editor.yPosition=b;editor.width=width;editor.palette(palette.text,palette.accent);editor.drawTextBox();}
    private void hitSlider(Hit target){
        if(target.y<bottom&&target.y+target.h>contentY){target.action=b->{if(b==0){slider=target;rangeDrag=null;if(target.setting instanceof RangeSetting)rangeDrag=((RangeSetting)target.setting).beginDrag(sliderValue(target,lastMouseX));updateSlider(lastMouseX);}};hits.add(target);}
    }
    private int lastMouseX;
    private void drawSlider(int a,int b,int width,double min,double max,double low,double high){
        double span=Math.max(.000001,max-min);int start=a+(int)Math.round(width*(low-min)/span),end=a+(int)Math.round(width*(high-min)/span);
        rect(a,b-3,width,6,3,palette.row);rect(start,b-3,Math.max(1,end-start),6,3,palette.accent);circle(end,b,6,palette.text,true);if(low!=min)circle(start,b,6,palette.text,true);
    }
    private double sliderValue(Hit target,int mouseX){return target.min+(target.max-target.min)*Math.max(0,Math.min(1,(mouseX-target.x)/(double)Math.max(1,target.w)));}
    private void updateSlider(int mx){
        if(slider==null||!slider.setting.isVisible()){slider=null;rangeDrag=null;return;}
        double n=sliderValue(slider,mx);if(slider.setting instanceof NumberSetting)((NumberSetting)slider.setting).setValue(n);else if(rangeDrag!=null)rangeDrag.move(n);else if(slider.setting instanceof ColorSetting){ColorSetting color=(ColorSetting)slider.setting;int[] channels={color.getRed(),color.getGreen(),color.getBlue(),color.getAlpha()};channels[slider.channel]=(int)Math.round(n);color.setRgba(channels[0],channels[1],channels[2],channels[3]);}
    }
    private void edit(Setting<?> value,int a,int b,int width){
        commit();editing=value;editor=new MenuTextField(3,mc.fontRendererObj,a,b,width,16);editor.setEnableBackgroundDrawing(false);editor.setMaxStringLength(value instanceof StringSetting?256:40);
        editor.setText(value instanceof StringSetting?((StringSetting)value).getValue():value instanceof NumberSetting?number(((NumberSetting)value).getDouble()):value instanceof RangeSetting?number(((RangeSetting)value).getMin())+" "+number(((RangeSetting)value).getMax()):"");
        editor.setTextColor(palette.text);editor.setFocused(true);if(!(value instanceof StringSetting))editor.setSelectionPos(0);
    }
    private void commit(){
        if(editing!=null&&editor!=null){try{String entered=editor.getText().trim();if(editing instanceof StringSetting)((StringSetting)editing).setValue(editor.getText());else if(editing instanceof NumberSetting){double n=Double.parseDouble(entered);if(!Double.isFinite(n))throw new NumberFormatException();((NumberSetting)editing).setValue(n);}else if(editing instanceof RangeSetting){String[] pair=entered.split("[ ,;–]+");if(pair.length!=2)throw new NumberFormatException();double min=Double.parseDouble(pair[0]),max=Double.parseDouble(pair[1]);if(!Double.isFinite(min)||!Double.isFinite(max))throw new NumberFormatException();((RangeSetting)editing).setRange(min,max);}}catch(NumberFormatException invalid){status="Enter a valid number";}}
        editing=null;editor=null;
    }
    private boolean matches(String name){return search==null||name.toLowerCase(Locale.ROOT).contains(search.getText().toLowerCase(Locale.ROOT));}
    private void refreshConfigs(){if(Vibe.getInstance().getConfig()!=null){localConfigs=Vibe.getInstance().getConfig().list();configCreators.clear();for(String name:localConfigs)configCreators.put(name,Vibe.getInstance().getConfig().getInfo(name).getCreator());}onlineConfigs=Vibe.getInstance().getOnlineConfigs().getEntries();}
    private void refreshOnline(){int token=++generation;fetching=true;status="Fetching online configs...";Vibe.getInstance().getOnlineConfigs().refresh(true).whenComplete((entries,error)->mc.addScheduledTask(()->{if(closed||token!=generation)return;fetching=false;status=error==null?"":OnlineConfigService.errorMessage(error);refreshConfigs();}));}
    private int configs(){
        int row=contentY-Math.round(scroll);
        if(chosenConfig!=null)return configDetails(row);
        int controls=Math.min(90,contentW/3);control("refresh",contentX,row,controls,25,"Refresh",false,()->{if(online)refreshOnline();else refreshConfigs();});
        if(!online){input=field(input,contentX+controls+6,row+5,Math.max(45,contentW-controls-65),"Config name",48);control("save-config",contentX+contentW-54,row,54,25,"Save",false,()->{if(!input.getText().trim().isEmpty()){boolean ok=Vibe.getInstance().getConfig().save(input.getText(),Vibe.getInstance().getModuleManager());status=ok?"Saved "+input.getText():"Could not save config";refreshConfigs();}});}
        row+=34;
        List<String> names=new ArrayList<String>();if(online)for(OnlineConfigService.Entry entry:onlineConfigs)names.add(entry.getName());else names.addAll(localConfigs);
        for(String name:names)if(matches(name)){rect(contentX,row,contentW,48,11,palette.row);text(NAME,name,contentX+12,row+7,contentW-80,palette.text);String detail=online?"Online config":configCreators.getOrDefault(name,"");text(SMALL,detail,contentX+12,row+29,contentW-75,palette.muted);text(SMALL,"Preview  ›",contentX+contentW-68,row+20,62,palette.muted);hit("config:"+name,contentX,row,contentW,48,b->{if(b==0)chooseConfig(name);});row+=54;}
        if(names.isEmpty())text(BODY,fetching?"Fetching configs...":"No configs yet",contentX+12,row+15,contentW-24,palette.muted);
        return row;
    }
    private void chooseConfig(String name){chosenConfig=name;preview=null;scroll=targetScroll=0;status="";if(!online){preview=Vibe.getInstance().getConfig().preview(name);return;}JsonObject cached=onlinePreviews.get(name);if(cached!=null){preview=cached;return;}int token=++generation;fetching=true;Vibe.getInstance().getOnlineConfigs().fetch(name).whenComplete((root,error)->mc.addScheduledTask(()->{if(closed||token!=generation)return;fetching=false;if(error!=null){status=OnlineConfigService.errorMessage(error);return;}onlinePreviews.put(name,root);preview=root;}));}
    private int configDetails(int row){
        control("config-back",contentX,row,48,24,"Back",false,()->{generation++;fetching=false;chosenConfig=null;preview=null;scroll=targetScroll=0;status="";});text(NAME,chosenConfig,contentX+58,row+5,contentW-60,palette.text);row+=32;
        control("load-config",contentX,row,Math.min(100,contentW/2),26,"Load config",preview!=null,()->{if(preview==null)return;String name=chosenConfig;boolean ok=online?Vibe.getInstance().getConfig().loadOnline(name,preview,Vibe.getInstance().getModuleManager(),withKeys,withVisuals):Vibe.getInstance().getConfig().load(name,Vibe.getInstance().getModuleManager(),withKeys,withVisuals);status=ok?"Loaded "+name:"Could not load config";});row+=34;
        control("load-keys",contentX,row,contentW/2-3,23,(withKeys?"✓ ":"")+"Keybinds",false,()->withKeys=!withKeys);control("load-visuals",contentX+contentW/2+3,row,contentW/2-3,23,(withVisuals?"✓ ":"")+"Visuals",false,()->withVisuals=!withVisuals);row+=33;
        if(preview==null){text(BODY,fetching?"Loading preview...":"Preview unavailable",contentX+10,row+14,contentW-20,palette.muted);return row+40;}
        for(String key:new String[]{"creator","createdAt","savedAt"})if(preview.has(key)&&preview.get(key).isJsonPrimitive()){text(SMALL,(key.equals("creator")?"Creator":key.equals("createdAt")?"Created":"Saved")+": "+preview.get(key).getAsString(),contentX+8,row,contentW-16,palette.muted);row+=19;}
        JsonArray modules=preview.has("modules")&&preview.get("modules").isJsonArray()?preview.getAsJsonArray("modules"):new JsonArray();
        for(JsonElement element:modules)if(element.isJsonObject()){
            JsonObject module=element.getAsJsonObject();if(!module.has("id")||!module.get("id").isJsonPrimitive())continue;String id=module.get("id").getAsString();boolean enabled=module.has("enabled")&&module.get("enabled").isJsonPrimitive()&&"true".equalsIgnoreCase(module.get("enabled").getAsString());
            for(Module known:Vibe.getInstance().getModuleManager().getModules())if(known.getId().equals(id)){id=known.getName();break;}
            rect(contentX,row,contentW,27,6,palette.row);text(BODY,id+(enabled?"  ·  Enabled":"  ·  Disabled"),contentX+10,row+7,contentW-20,enabled?palette.text:palette.muted);row+=31;
            if(module.has("settings")&&module.get("settings").isJsonObject())for(Map.Entry<String,JsonElement> setting:module.getAsJsonObject("settings").entrySet()){text(SMALL,setting.getKey()+": "+setting.getValue().toString(),contentX+18,row+3,contentW-28,palette.muted);row+=19;}
        }
        return row;
    }
    private MenuTextField field(MenuTextField current,int a,int b,int width,String placeholder,int max){
        if(current==null){current=new MenuTextField(4,mc.fontRendererObj,a,b,width,16);current.setEnableBackgroundDrawing(false);current.setMaxStringLength(max);}
        current.xPosition=a;current.yPosition=b;current.width=width;current.setTextColor(palette.text);current.palette(palette.text,palette.accent);rect(a-3,b-4,width+6,24,5,palette.row);current.drawTextBox();if(current.getText().isEmpty()&&!current.isFocused())text(SMALL,placeholder,a,b+2,width,palette.muted);return current;
    }
    private int social(){
        int row=contentY-Math.round(scroll),fieldW=Math.max(45,(contentW-72)/2);
        input=field(input,contentX+4,row+5,fieldW,"Username",16);alias=field(alias,contentX+fieldW+12,row+5,fieldW,"Alias",16);
        input.setEnabled(friend==null);input.palette(friend==null?palette.text:palette.muted,palette.accent);
        control("friend-add",contentX+contentW-54,row,54,25,friend==null?"Add":"Save",false,()->{if(Vibe.getInstance().getFriendManager()==null)return;boolean ok=friend==null?Vibe.getInstance().getFriendManager().add(input.getText(),alias.getText()):Vibe.getInstance().getFriendManager().rename(friend.getName(),alias.getText());status=ok?"Friend saved":"Names and aliases: 1–16 letters, digits or underscores";if(ok){friend=null;input.setText("");alias.setText("");}});row+=35;
        if(Vibe.getInstance().getFriendManager()==null)return row;
        for(Friend entry:Vibe.getInstance().getFriendManager().getFriends())if(matches(entry.getName()+" "+entry.getAlias())){
            rect(contentX,row,contentW,49,12,palette.row);circle(contentX+23,row+25,13,palette.raised,true);BODY.centered(entry.getName().substring(0,1),contentX+10,row+12,26,26,palette.text);
            text(NAME,entry.getName(),contentX+46,row+7,contentW-100,palette.text);text(SMALL,entry.getAlias().isEmpty()?"Added "+entry.getAddedDate():entry.getAlias(),contentX+46,row+29,contentW-100,palette.muted);
            final Friend item=entry;hit("friend:"+entry.getName(),contentX,row,contentW-42,49,b->{if(b==0){friend=item;input.setText(item.getName());alias.setText(item.getAlias());}});
            final int at=row;control("friend-remove:"+entry.getName(),contentX+contentW-35,at+12,26,25,"",false,()->{Vibe.getInstance().getFriendManager().remove(item.getName());if(friend==item){friend=null;input.setText("");alias.setText("");}});BreezeIcons.draw("trash",contentX+contentW-29,at+17,15,palette.muted);row+=55;
        }
        if(friend!=null){text(SMALL,"Added "+friend.getAddedDate(),contentX+8,row+8,contentW-16,palette.muted);row+=30;}
        return row;
    }
    private int themes(){
        int row=contentY-Math.round(scroll),columns=contentW>=500?3:contentW>=330?2:1,gap=10,cardW=(contentW-(columns-1)*gap)/columns,index=0;
        for(BreezePalette theme:BreezePalette.values()){
            boolean bookmarked=stars().contains("theme:"+theme.label);if(bookmarkedThemes&&!bookmarked)continue;
            int a=contentX+(index%columns)*(cardW+gap),b=row+(index/columns)*168;
            rect(a,b,cardW,156,8,theme==palette?palette.accent:palette.row);rect(a+1,b+1,cardW-2,154,7,palette.row);
            text(BODY,theme.label,a+9,b+9,cardW-38,palette.text);BreezeIcons.draw("bookmark",a+cardW-23,b+7,16,bookmarked?palette.accent:palette.muted);
            rect(a+7,b+31,cardW-14,88,4,theme.background);BreezeArtwork.draw(theme,a+7,b+31,cardW-14,88);
            int left=a+13,top=b+40,leftW=(cardW-38)*38/100,right=left+leftW+8,rightW=cardW-29-leftW;
            rect(left,top,leftW,68,6,theme.raised);rect(right,top,rightW,68,6,theme.row);
            for(int r=0;r<3;r++){rect(left+6,top+7+r*10,Math.max(8,leftW-18-r*3),5,2,theme.text);rect(right+6,top+8+r*16,Math.max(10,rightW-24),4,2,theme.muted);rect(right+6,top+8+r*16,Math.max(5,(rightW-24)/2),4,2,theme.accent);rect(right+6+(rightW-24)/2,top+6+r*16,7,7,4,theme.text);}
            rect(left+6,top+48,leftW-12,13,5,theme.accent);rect(right+rightW-24,top+53,17,8,4,theme.accent);rect(right+rightW-15,top+54,6,6,3,theme.text);
            text(SMALL,theme==palette?"Selected":"Use theme",a+10,b+134,cardW-34,palette.muted);if(theme==palette)BreezeIcons.draw("check",a+cardW-24,b+130,16,palette.text);
            hit("theme:"+theme.label,a,b,cardW,156,button->{if(button==0)settings().getBreezePalette().setValue(theme.label);});
            hit("bookmark:"+theme.label,a+cardW-30,b+3,28,25,button->{if(button==0){Set<String> set=stars();set.remove("");if(!set.remove("theme:"+theme.label))set.add("theme:"+theme.label);settings().getBreezeFavorites().setValue(String.join(",",set));}});index++;
        }
        if(index==0)text(BODY,"No bookmarked themes",contentX+10,row+22,contentW-20,palette.muted);
        return row+((index+columns-1)/columns)*168;
    }
    void click(int mx,int my,int button){
        lastMouseX=mx;
        if(editor!=null){if(mx>=editor.xPosition&&mx<editor.xPosition+editor.width&&my>=Math.max(contentY,editor.yPosition-3)&&my<Math.min(bottom,editor.yPosition+18)){editor.mouseClicked(mx,my,button);return;}commit();}
        search.mouseClicked(mx,my,button);
        if(my>=contentY&&my<bottom){if((page==Page.SOCIAL||page==Page.CONFIGS&&!online&&chosenConfig==null)&&input!=null)input.mouseClicked(mx,my,button);if(page==Page.SOCIAL&&alias!=null)alias.mouseClicked(mx,my,button);}
        for(int i=hits.size()-1;i>=0;i--){Hit target=hits.get(i);if(target.id.startsWith("slider:")||target.id.startsWith("color:")){if(my<contentY||my>=bottom)continue;}if(target.contains(mx,my)){target.action.accept(button);return;}}
        if(button==0&&mx>=x&&mx<x+side&&my>=y&&my<y+52){dragging=true;dragX=mx-x;dragY=my-y;}
    }
    void release(int button){if(button==0){slider=null;rangeDrag=null;dragging=false;scrollDragging=false;}}
    void wheel(int mx,int my,int amount){
        if(mx<contentX||mx>contentX+contentW||my<y+50||my>bottom)return;
        if(page==Page.MODULES&&my<contentY){tabScroll=Math.max(0,Math.min(maxTabScroll,tabScroll+(amount>0?-70:70)));return;}
        commit();targetScroll=Math.max(0,Math.min(maxScroll,targetScroll+(amount>0?-56:56)));
    }
    private void dragScroll(int my){int track=bottom-contentY,thumb=Math.max(22,track*track/(track+maxScroll));targetScroll=scroll=Math.max(0,Math.min(maxScroll,(my-contentY-thumb/2F)/Math.max(1,track-thumb)*maxScroll));}
    boolean key(char character,int key){
        if(binding!=null){binding.setKey(key==Keyboard.KEY_ESCAPE?Keyboard.KEY_NONE:key);binding=null;return true;}
        if(editor!=null){if(key==Keyboard.KEY_ESCAPE){editing=null;editor=null;return true;}if(key==Keyboard.KEY_RETURN){commit();return true;}editor.textboxKeyTyped(character,key);if(editing instanceof StringSetting)((StringSetting)editing).setValue(editor.getText());return true;}
        if(key==Keyboard.KEY_ESCAPE){if(selected!=null){selected=null;scroll=targetScroll=0;return true;}if(chosenConfig!=null){chosenConfig=null;preview=null;generation++;fetching=false;scroll=targetScroll=0;return true;}if(search.isFocused()){search.setFocused(false);return true;}return false;}
        if(search.textboxKeyTyped(character,key)){targetScroll=scroll=0;return true;}
        if((page==Page.SOCIAL||page==Page.CONFIGS)&&input!=null&&input.textboxKeyTyped(character,key))return true;
        return page==Page.SOCIAL&&alias!=null&&alias.textboxKeyTyped(character,key);
    }
    void close(){closed=true;generation++;commit();release(0);binding=null;targetScroll=scroll;}
    void tick(){if(search!=null)search.updateCursorCounter();if(input!=null)input.updateCursorCounter();if(alias!=null)alias.updateCursorCounter();if(editor!=null)editor.updateCursorCounter();}
    private Set<String> stars(){return new LinkedHashSet<String>(Arrays.asList(settings().getBreezeFavorites().getValue().split(",")));}
    private void toggleStar(Module module){Set<String> set=stars();set.remove("");if(!set.remove(module.getId()))set.add(module.getId());settings().getBreezeFavorites().setValue(String.join(",",set));}
    private void browse(String url){try{Desktop.getDesktop().browse(new URI(url));}catch(Exception error){status="Could not open browser";}}
    private static String number(double n){return java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();}
    private void circle(int cx,int cy,int radius,int color,boolean fill){
        if(fill){rect(cx-radius,cy-radius,radius*2,radius*2,radius,color);return;}
        GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.disableAlpha();GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1.5F);GlStateManager.color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);
        GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<32;i++){double angle=i*Math.PI/16;GL11.glVertex2f(cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius);}GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1);GlStateManager.enableTexture2D();GlStateManager.resetColor();
    }
    private static final class Hit {
        final String id;final int x,y,w,h;IntConsumer action;Setting<?> setting;double min,max;int channel;
        Hit(String id,int x,int y,int w,int h,IntConsumer action){this.id=id;this.x=x;this.y=y;this.w=w;this.h=h;this.action=action;}
        boolean contains(int mx,int my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
}
