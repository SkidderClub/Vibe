package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import dev.vibe.ui.clickgui.VibeClickGui;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Exercises the real host callbacks and setting model, with native antialiased GL captures. */
public final class DropdownThemesRenderCheck {
    private static Minecraft mc;private static Path output;
    public static void verify(Minecraft client,ModuleManager manager,Path directory)throws Exception{
        mc=client;output=directory;Path profile=Files.createTempDirectory(directory,"dropdown-profile-");
        VibeConfig config=new VibeConfig(profile.toFile());PathTestFixture.set(Vibe.class,Vibe.getInstance(),"config",config);
        ClickGuiModule theme=new ClickGuiModule();manager.registerDynamic(theme);
        Fixture main=new Fixture("Target Strafe",Category.COMBAT);manager.registerDynamic(main);
        Fixture longModule=new Fixture("A deliberately long module name that needs to scroll completely",Category.SCRIPTS);manager.registerDynamic(longModule);
        theme.getPacketAccent().setValue("Static");
        String[][] names={{"Auto Clicker","Aim Assist","Hit Boxes","Auto Pot","Velocity","Anti Bot","Teams","Regen","Reach","Aura"},
                {"Item Physics","Camera Clip","Session Info","Health Alert","Motion Blur","Animations","Name Tags","Target Info","Glint Color","Hit Effects","No Render","Hurt Color","Ambience","Array List","Zoom"},
                {"Inventory Move","Long Jump","Safe Walk","Fast Stop","No Slow","Spider","Speed","Sprint","Jesus","Step","Fly"},
                {"InventoryManager","AutoArmor","ChestStealer","AutoTool","Anti Void","No Fall","Scaffold","Fast Use","Timer"},
                {"Device Spoofer","Toggle Sounds","Auto Queue","Phone","Auto Dodge","Auto Snipe"},
                {"Notifications","Target HUD","Watermark","Array List","Crosshair"}};
        Category[] categories={Category.COMBAT,Category.VISUAL,Category.MOVEMENT,Category.WORLD,Category.MEME,Category.CLIENT};int n=0;
        for(int i=0;i<names.length;i++)for(String name:names[i]){Fixture m=new Fixture(name,categories[i]);manager.registerDynamic(m);PathTestFixture.set(Module.class,m,"enabled",(++n%3)!=0);}
        mc.theWorld=PathTestFixture.allocate(WorldClient.class);
        for(String name:new String[]{"PacketV3","Novoline","SuizideX"}){
            theme.getTheme().setValue(name);main.flag.setValue(true);main.range.setValue(4D);main.color.setValue(0xFF26B8DD);main.note.setValue("Default");main.targets.setValue(new LinkedHashSet<String>(Arrays.asList("Players")));main.cps.setRange(8,12);
            VibeClickGui gui=new VibeClickGui();setup(gui,960,540,1);render(gui,name.toLowerCase(Locale.ROOT)+"-overview.png");
            Object initialWorkspace=get(gui,"dropdown");Set<Module> mapped=Collections.newSetFromMap(new IdentityHashMap<Module,Boolean>());
            List<?> categoryPanels=(List<?>)get(initialWorkspace,"panels");check(categoryPanels.size()==Category.values().length,"The theme did not use Vibe's categories");
            for(Object panel:categoryPanels){
                Category category=(Category)get(panel,"category");
                for(Object module:(List<?>)get(panel,"modules")){check(mapped.add((Module)module),"Module appears in two panels");check(((Module)module).getCategory()==category,"A module was moved to a screenshot category");}
                if(name.equals("SuizideX")&&(Integer)get(panel,"max")==0)check((Integer)get(panel,"h")<=(Integer)get(panel,"content")+36,"An empty SuizideX panel did not shrink");
            }
            check(mapped.size()==manager.getModules().size(),"A module was omitted from "+name);
            verifyFooter(gui,longModule,name);
            Object row=hit(gui,"MODULE",main);click(gui,row,0);check(main.isEnabled(),name+" did not toggle a module");
            click(gui,row,1);render(gui,name.toLowerCase(Locale.ROOT)+"-settings.png");check(main.isEnabled(),"Right click toggled module");
            row=hit(gui,"MODULE",main);click(gui,row,2);GuiInput.keyTyped(gui,'k',Keyboard.KEY_K);check(main.getKey()==Keyboard.KEY_K,"Middle-click bind failed");
            Object flag=hit(gui,"BOOLEAN",main.flag);click(gui,flag,0);render(gui,null);check(!main.flag.isEnabled()&&!hasHit(gui,"MODE",main.mode),"Conditional settings remained interactive");
            click(gui,hit(gui,"BOOLEAN",main.flag),0);render(gui,null);
            Object slider=hit(gui,"NUMBER",main.range);click(gui,slider,1);check(get(gui,"draggingSlider")==null,"Right click left a slider stuck dragging");int sx=num(slider,"right")-1,sy=(num(slider,"top")+num(slider,"bottom"))/2;
            GuiInput.mouseClicked(gui,sx,sy,0);renderAt(gui,960,sy);GuiInput.mouseReleased(gui,960,sy,0);check(main.range.getDouble()==8,"Slider failed to clamp while dragging outside");
            render(gui,null);slider=hit(gui,"NUMBER",main.range);
            Object wheelPanel=((Map<?,?>)get(get(gui,"dropdown"),"categories")).get(Category.COMBAT);float beforeScroll=(Float)get(wheelPanel,"target");
            wheel(gui,num(slider,"left")+10,num(slider,"top")+2,-120);check(main.range.getDouble()==8,"Mouse wheel changed a dropdown slider");
            if((Integer)get(wheelPanel,"max")>0)check((Float)get(wheelPanel,"target")>beforeScroll,"Hovering a slider blocked panel scrolling");
            PathTestFixture.set(wheelPanel.getClass(),wheelPanel,"target",beforeScroll);PathTestFixture.set(wheelPanel.getClass(),wheelPanel,"scroll",beforeScroll);
            render(gui,null);Object pair=hit(gui,"RANGE",main.cps);click(gui,pair,0);renderAt(gui,0,num(pair,"top"));GuiInput.mouseReleased(gui,0,num(pair,"top"),0);check(main.cps.getMin()<=main.cps.getMax(),"Range crossed bounds");
            render(gui,null);click(gui,hit(gui,"MODE",main.mode),0);render(gui,name.toLowerCase(Locale.ROOT)+"-mode.png");Object workspace=get(gui,"dropdown");
            if(name.equals("SuizideX")){int mx=(Integer)get(workspace,"modeX"),my=(Integer)get(workspace,"modeY");GuiInput.mouseClicked(gui,mx+12,my+27,0);}
            else{Object option=null;for(Object item:(List<?>)get(gui,"targets"))if(get(item,"type").toString().equals("MODE_OPTION")&&get(item,"setting")==main.mode&&num(item,"channel")==1)option=item;
                check(option!=null,"Mode selection did not expand inside its panel");click(gui,option,0);}
            check(main.mode.is("Packet"),"Mode menu selection failed");
            render(gui,null);click(gui,hit(gui,"MULTI_TOGGLE",main.targets),0);render(gui,null);click(gui,hit(gui,"MULTI_OPTION",main.targets),0);check(!main.targets.isSelected("Players"),"Multi-select failed");
            render(gui,null);click(gui,hit(gui,"MULTI_TOGGLE",main.targets),0);render(gui,null);
            click(gui,hit(gui,"COLOR_TOGGLE",main.color),0);render(gui,name.toLowerCase(Locale.ROOT)+"-color.png");Object popup=get(gui,"colorPopup");
            GuiInput.mouseClicked(gui,(Integer)get(popup,"left")+40,(Integer)get(popup,"top")+40,1);GuiInput.mouseReleased(gui,0,0,1);check(get(gui,"draggingColor")==null,"Right click left the HSV picker stuck dragging");
            GuiInput.mouseClicked(gui,(Integer)get(popup,"left")+60,(Integer)get(popup,"top")+60,0);GuiInput.mouseReleased(gui,0,0,0);check(main.color.getArgb()!=0xFF26B8DD,"HSV picker did not change color");GuiInput.keyTyped(gui,'\0',Keyboard.KEY_ESCAPE);
            render(gui,null);click(gui,hit(gui,"TEXT",main.note),0);GuiInput.keyTyped(gui,'!',Keyboard.KEY_1);GuiInput.keyTyped(gui,'\0',Keyboard.KEY_RETURN);check(main.note.getValue().equals("Default!"),"String edit failed");
            render(gui,null);List<?> panels=(List<?>)get(workspace,"panels");Object first=panels.get(0);int px=(Integer)get(first,"x"),py=(Integer)get(first,"y");
            GuiInput.mouseClicked(gui,px+20,py+8,0);renderAt(gui,px+42,py+21);GuiInput.mouseReleased(gui,px+42,py+21,0);check((Integer)get(first,"x")!=px,"Panel drag failed");
            render(gui,null);px=(Integer)get(first,"x");py=(Integer)get(first,"y");GuiInput.mouseClicked(gui,px+20,py+8,1);render(gui,null);check((Boolean)get(first,"collapsed"),"Header did not collapse");
            GuiInput.mouseClicked(gui,px+20,py+8,1);render(gui,null);
            main.setEnabled(false);
            if(name.equals("SuizideX")){theme.getSuizideDarkMode().setValue(true);render(gui,"suizidex-dark.png");theme.getSuizideDarkMode().setValue(false);}
            setup(gui,427,240,1);render(gui,name.toLowerCase(Locale.ROOT)+"-compact.png");
            Object combat=null;for(Object panel:(List<?>)get(workspace,"panels"))if(get(panel,"name").equals("Combat"))combat=panel;
            int cx=(Integer)get(combat,"x"),cy=(Integer)get(combat,"y"),pw=(Integer)get(workspace,"panelWidth");
            wheel(gui,cx+14,cy+(name.equals("Novoline")?23:25)+7,-120);check((Float)get(combat,"target")>0,"Panel wheel failed");render(gui,null);
            GuiInput.mouseClicked(gui,cx+pw-2,cy+32,0);renderAt(gui,cx+pw-2,220);GuiInput.mouseReleased(gui,cx+pw-2,220,0);
            check((Float)get(combat,"target")>0,"Scrollbar drag failed");
            check((Integer)get(workspace,"horizontalMax")>0,"Small screen lost category navigation");GuiInput.mouseClicked(gui,410,225,0);render(gui,null);check((Float)get(workspace,"targetHorizontal")>0,"Category navigation did not move to hidden panels");
            setup(gui,480,270,2);render(gui,name.toLowerCase(Locale.ROOT)+"-scale2.png");
            // Close clears all drags; expanded modules are intentionally shared by all themes.
            gui.onGuiClosed();((Set<?>)staticField(VibeClickGui.class,"SAVED_EXPANDED")).clear();
        }
        manager.registerDynamic(new Fixture("Example script",Category.SCRIPTS));
        VibeClickGui scripts=new VibeClickGui();setup(scripts,960,540,1);render(scripts,null);boolean found=false;
        for(Object panel:(List<?>)get(get(scripts,"dropdown"),"panels"))if(get(panel,"name").equals("Scripts"))found=true;
        check(found,"Script extensions were omitted");scripts.onGuiClosed();
        VibeClickGui reopened=new VibeClickGui();setup(reopened,320,240,1);render(reopened,null);
        for(Object panel:(List<?>)get(get(reopened,"dropdown"),"panels"))check((Integer)get(panel,"y")>=4&&(Integer)get(panel,"y")<=186,"A saved header was unreachable after reopening at a smaller GUI size");
        reopened.onGuiClosed();
        checkGlowAndGradientPixels(); verifyScrollingTextPixels();
        mc.theWorld=null;System.out.println("PacketV3/Novoline/SuizideX: native frames, dark mode, all modules/scripts, settings, mode/HSV popups, wheel/drag/range, binds, scrollbar, clipping and compact/scaled navigation passed.");
    }
    private static void setup(VibeClickGui gui,int w,int h,int scale)throws Exception{mc.displayWidth=w*scale;mc.displayHeight=h*scale;mc.gameSettings.guiScale=scale;frame(gui,w,h);gui.setWorldAndResolution(mc,w,h);mc.currentScreen=gui;}
    private static void frame(VibeClickGui gui,int w,int h)throws Exception{
        ClientChangesRenderCheck.frame(mc.displayWidth,mc.displayHeight);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
        // Neutral world-like background makes transparent surfaces and light/dark contrast measurable.
        for(int y=0;y<h;y+=20)for(int x=0;x<w;x+=20)net.minecraft.client.gui.Gui.drawRect(x,y,x+20,y+20,((x/20+y/20)&1)==0?0xFF435352:0xFF4D5C5B);
    }
    private static void render(VibeClickGui gui,String file)throws Exception{for(int i=0;i<6;i++){renderAt(gui,-1,-1);Thread.sleep(20);}if(file!=null)save(file);}
    private static void renderAt(VibeClickGui gui,int mx,int my)throws Exception{frame(gui,gui.width,gui.height);gui.drawScreen(mx,my,0);check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Leaked scissor");check(GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM)==0,"Leaked shader");check(GL11.glGetError()==0,"GL error");}
    private static void save(String name)throws Exception{int w=mc.displayWidth,h=mc.displayHeight;ByteBuffer p=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage im=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-y-1)*w+x)*4;im.setRGB(x,y,0xFF000000|(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(im,"png",output.resolve(name).toFile());}
    private static void click(VibeClickGui gui,Object hit,int button)throws Exception{int x=(num(hit,"left")+num(hit,"right"))/2,y=(num(hit,"top")+num(hit,"bottom"))/2;GuiInput.mouseClicked(gui,x,y,button);GuiInput.mouseReleased(gui,x,y,button);}
    private static Object hit(VibeClickGui gui,String kind,Object value)throws Exception{for(Object h:(List<?>)get(gui,"targets"))if(get(h,"type").toString().equals(kind)&&(get(h,"module")==value||get(h,"setting")==value))return h;throw new AssertionError("Missing "+kind+" for "+value);}
    private static boolean hasHit(VibeClickGui gui,String kind,Object value)throws Exception{try{hit(gui,kind,value);return true;}catch(AssertionError e){return false;}}
    private static void wheel(VibeClickGui gui,int x,int y,int delta)throws Exception{Method method=VibeClickGui.class.getDeclaredMethod("wheel",int.class,int.class,int.class);method.setAccessible(true);method.invoke(gui,x,y,delta);}
    private static int num(Object o,String key)throws Exception{return (Integer)get(o,key);}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object staticField(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void checkGlowAndGradientPixels()throws Exception{
        mc.displayWidth=640;mc.displayHeight=360;mc.gameSettings.guiScale=1;ClientChangesRenderCheck.frame(640,360);
        net.minecraft.client.gui.Gui.drawRect(0,0,640,360,0xFF202020);
        SoftPanelGlow.draw(40,40,120,45,9,12,0x668846D7);MenuRoundedRenderer.rect(40,40,120,45,9,0xFF202020);
        MenuRoundedRenderer.horizontalGradient(220,40,160,32,7,0xFFFF5038,0xFFE91476);
        check((pixel(34,62)&255)>35,"The soft panel halo did not extend outside its body");
        check((pixel(25,62)&0xFFFFFF)==0x202020,"The glow painted a rectangular shadow beyond its falloff");
        check((pixel(220,40)&0xFFFFFF)==0x202020,"The rounded gradient painted a sharp corner");
        int previous=pixel(230,55);for(int x=231;x<370;x++){int color=pixel(x,55);for(int shift:new int[]{0,8,16})check(Math.abs((color>>shift&255)-(previous>>shift&255))<=3,"The highlighted gradient has a visible color seam");previous=color;}
        check(GL11.glGetError()==0,"The gradient/glow check leaked GL errors");save("theme-glow-gradient-pixels.png");
    }
    private static int pixel(int x,int y){ByteBuffer b=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,mc.displayHeight-y-1,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);return (b.get(0)&255)<<16|(b.get(1)&255)<<8|b.get(2)&255;}
    private static void verifyFooter(VibeClickGui gui,Module module,String theme)throws Exception {
        Object workspace=get(gui,"dropdown"),panel=((Map<?,?>)get(workspace,"categories")).get(Category.SCRIPTS);
        PathTestFixture.set(workspace.getClass(),workspace,"opened",System.nanoTime()-1000000000L);
        PathTestFixture.set(Module.class,module,"enabled",false);render(gui,null);
        int x=num(panel,"x"),bottom=num(panel,"y")+num(panel,"h"),w=num(workspace,"panelWidth"),footer=theme.equals("Novoline")?4:theme.equals("PacketV3")?12:11;
        int[] baseline=region(x,bottom-footer,w,footer);
        PathTestFixture.set(Module.class,module,"enabled",true);render(gui,null);
        check(Arrays.equals(baseline,region(x,bottom-footer,w,footer)),"Module background painted over the "+theme+" footer/corners");
        click(gui,hit(gui,"MODULE",module),1);render(gui,theme.toLowerCase(Locale.ROOT)+"-long-labels.png");
        // Expanded controls must be clipped before the bottom arc, including at maximum scroll.
        PathTestFixture.set(panel.getClass(),panel,"target",((Integer)get(panel,"max")).floatValue());
        PathTestFixture.set(panel.getClass(),panel,"scroll",((Integer)get(panel,"max")).floatValue());
        render(gui,theme.toLowerCase(Locale.ROOT)+"-panel-bottom.png");
        click(gui,hit(gui,"MODULE",module),1);render(gui,null);
        PathTestFixture.set(panel.getClass(),panel,"scroll",10000F);PathTestFixture.set(panel.getClass(),panel,"target",10000F);
        render(gui,null);check((Float)get(panel,"scroll")<=(Integer)get(panel,"max"),"Collapsing settings left the scroll thumb outside the panel");
        GuiInput.mouseClicked(gui,x+8,num(panel,"y")+5,1);render(gui,null);
        int corner=pixel(x,num(panel,"y")+(theme.equals("Novoline")?23:25)-1),outside=pixel(x-1,num(panel,"y")+(theme.equals("Novoline")?23:25)-1);
        if(!theme.equals("Novoline"))check(rgbDifference(corner,outside)<12,"Collapsed header painted a square bottom corner");
        GuiInput.mouseClicked(gui,x+8,num(panel,"y")+5,1);render(gui,null);
    }
    private static void verifyScrollingTextPixels()throws Exception {
        String text="A long setting name with a fully visible ending";
        int[] first=null;float width=NeverLoseFont.REGULAR.width(text),available=86;
        long end=(long)(1000+(width-available)/24*1000);
        for(int phase=0;phase<2;phase++) {
            ClientChangesRenderCheck.frame(640,360);net.minecraft.client.gui.Gui.drawRect(0,0,640,360,0xFF202020);
            try(GuiClip outer=new GuiClip(37,40,95,26)) {
                ScrollingText.draw(NeverLoseFont.REGULAR,text,40,44,available,0xFFFFFFFF,ScrollingText.Alignment.LEFT,phase==0?0:end);
            }
            int[] row=region(0,52,140,1);
            for(int x=0;x<40;x++)check(row[x]==0x202020,"Scrolling text escaped its left clip");
            for(int x=127;x<140;x++)check(row[x]==0x202020,"Scrolling text escaped its right clip");
            if(first==null)first=row;else check(!Arrays.equals(first,row),"Overflowing text never advanced");
            check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Scrolling text lost the parent's clip state");
        }
    }
    private static int[] region(int x,int y,int w,int h) {
        ByteBuffer data=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(x,mc.displayHeight-y-h,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        int[] colors=new int[w*h];
        for(int row=0;row<h;row++)for(int col=0;col<w;col++) {
            int at=((h-row-1)*w+col)*4;colors[row*w+col]=(data.get(at)&255)<<16|(data.get(at+1)&255)<<8|data.get(at+2)&255;
        }
        return colors;
    }
    private static int rgbDifference(int a,int b){return Math.max(Math.abs((a>>16&255)-(b>>16&255)),Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a&255)-(b&255))));}
    private static final class Fixture extends Module {
        final BooleanSetting flag=addSetting(new BooleanSetting("Smart",true));
        final NumberSetting range=addSetting(new NumberSetting("Range",4,1,8,.5));
        final ModeSetting mode=addSetting(new ModeSetting("Mode","Normal",()->flag.isEnabled(),"Normal","Packet","Silent"));
        final RangeSetting cps=addSetting(new RangeSetting("Click speed",8,12,1,20,1));
        final ColorSetting color=addSetting(new ColorSetting("Color",0xFF26B8DD));
        final MultiSelectSetting targets=addSetting(new MultiSelectSetting("Targets",Arrays.asList("Players","Mobs"),Arrays.asList("Players")));
        final StringSetting note=addSetting(new StringSetting("A long setting name that must reveal its entire text","Default"));
        Fixture(String name,Category category){super(name,"A reference module with real Vibe settings",category,0);}
    }
}
