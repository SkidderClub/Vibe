package dev.vibe.ui.clickgui;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import dev.vibe.ui.ClientChangesRenderCheck;
import dev.vibe.ui.menu.MenuTextField;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Native controls, clipping, resize, reopening and live shader/theme drafts. */
public final class LavalampRenderCheck {
    private static Minecraft mc;private static Path output;
    public static void verify(Minecraft client,ModuleManager modules,Path directory)throws Exception{
        mc=client;output=directory;Path profile=Files.createTempDirectory(output,"lavalamp-profile-");PathTestFixture.set(Minecraft.class,mc,"mcDataDir",profile.toFile());
        ClickGuiModule gui=new ClickGuiModule();modules.registerDynamic(gui);gui.getTheme().setValue("Lavalamp");
        Fixture example=new Fixture("Lavalamp settings",Category.COMBAT);modules.registerDynamic(example);for(Category c:Category.values())for(int i=0;i<18;i++)modules.registerDynamic(new Fixture(c.getLabel()+" module "+i,c));
        LavalampWorkspace ws=new LavalampWorkspace();PathTestFixture.set(LavalampWorkspace.class,ws,"selected",example);
        render(ws,960,540,"lavalamp-clickgui.png");int cx=integer(ws,"cx"),cy=integer(ws,"cy");
        Object firstCategory=find(ws,"category:COMBAT"),lastCategory=find(ws,"category:SCRIPTS");
        check(integer(firstCategory,"x")==integer(ws,"x")+74&&integer(lastCategory,"x")+integer(lastCategory,"w")==integer(ws,"x")+integer(ws,"w")-47,"Categories do not fill the available top row");
        Object mode=find(ws,"open:Style");check(integer(mode,"x")>cx&&integer(mode,"w")<integer(ws,"cw"),"Mode selector is not beside its label");
        click(ws,"open:Style",0);render(ws,960,540,"lavalamp-mode-options.png");click(ws,"option:Style:Sharp",0);check(example.style.is("Sharp"),"Expanded mode selection failed");render(ws,960,540,null);
        click(ws,"module:"+example.getId(),0);check(example.isEnabled()&&get(ws,"selected")==example,"Left-click must toggle and open settings");render(ws,960,540,null);
        Object second=find(ws,"module:combatmodule0");ws.click(integer(second,"x")+12,integer(second,"y")+12,1);check(get(ws,"selected")!=example&&!((Module)get(ws,"selected")).isEnabled(),"Right-click toggled instead of opening settings");
        click(ws,"module:"+example.getId(),1);render(ws,960,540,null);
        boolean before=example.flag.isEnabled();click(ws,"boolean:Enabled feature",0);PathTestFixture.set(LavalampWorkspace.class,ws,"last",System.nanoTime()-16000000L);render(ws,960,540,null);check(example.flag.isEnabled()!=before,"Boolean control did not toggle");
        Object motion=((Map<?,?>)get(ws,"motions")).get(example.flag);float progress=(Float)get(motion,"value");check(progress>0&&progress<1,"Boolean knob snapped instead of animating");
        click(ws,"binding",0);ws.key('k',Keyboard.KEY_K);check(example.getKey()==Keyboard.KEY_K,"Keybind editor failed");
        click(ws,"color:Effect colour",0);render(ws,960,540,"lavalamp-colorpicker.png");
        Object hue=slider(ws,example.color,1);int hx=integer(hue,"x"),hy=integer(hue,"y"),hw=integer(hue,"w");int color=example.color.getArgb();ws.click(hx+hw*3/4,hy+5,0);ws.release(0);check(example.color.getArgb()!=color,"HSV hue picker did not change colour");
        ws.wheel(cx+20,cy+80,-120);float target=(Float)get(ws,"target");check(target>0,"Settings did not scroll");check(example.number.getDouble()==3,"Wheel changed a slider value");ws.close();check((Float)get(ws,"target")==target,"Closing discarded scroll position");render(ws,960,540,null);
        MenuTextField search=(MenuTextField)get(ws,"search");ws.wheel(integer(ws,"x")+20,integer(ws,"listTop")+20,-120);search.setText("Effect colour");render(ws,960,540,"lavalamp-global-search.png");check((Float)get(ws,"listScroll")==0,"Search retained the previous list offset");check(get(ws,"category")==Category.COMBAT,"Global search changed the category");
        int count=0;for(Object hit:(List<?>)get(ws,"hits"))if(get(hit,"module")!=null)count++;check(count>0,"Settings search has no module hits");
        check(LavalampWorkspace.visibleModules(modules.getModules(),Category.COMBAT,"Effect colour").stream().anyMatch(m->m.getCategory()==Category.MEME),"Search omitted other categories");search.setText("");render(ws,960,540,null);
        int x=integer(ws,"x"),y=integer(ws,"y"),w=integer(ws,"w"),h=integer(ws,"h");ws.click(x+w-5,y+h-5,0);renderAt(ws,960,540,x+w-90,y+h-65,null);ws.release(0);check(integer(ws,"w")<w&&integer(ws,"h")<h,"Resize grip did not resize both axes");
        for(int[] size:new int[][]{{480,300},{320,240}}){render(ws,size[0],size[1],"lavalamp-clickgui-"+size[0]+".png");check(integer(ws,"x")>=0&&integer(ws,"x")+integer(ws,"w")<=size[0],"Window escaped small-screen bounds");ws.wheel(integer(ws,"x")+90,integer(ws,"y")+25,-120);if(integer(ws,"tabMaximum")>0)check(integer(ws,"tabOffset")>0,"Compact category strip did not scroll");}
        ws.close();clearBackdrop(example);dev.vibe.ui.menu.MenuStylesRenderCheck.verify(mc,output);
        System.out.println("Lavalamp ClickGUI native toggle, keybind, HSV picker, scroll without slider edits, resize, clipping and compact bounds passed.");
    }
    private static void render(LavalampWorkspace ws,int w,int h,String file)throws Exception{renderAt(ws,w,h,-100,-100,file);}
    private static void clearBackdrop(Fixture selected)throws Exception{
        net.minecraft.client.multiplayer.WorldClient previous=mc.theWorld;
        try{mc.theWorld=PathTestFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);mc.displayWidth=960;mc.displayHeight=540;mc.gameSettings.guiScale=1;ClientChangesRenderCheck.frame(960,540);
            for(int y=0;y<540;y+=24)for(int x=0;x<960;x+=24)net.minecraft.client.gui.Gui.drawRect(x,y,x+24,y+24,((x/24+y/24)&1)==0?0xFF365B4B:0xFF6E5141);
            LavalampWorkspace live=new LavalampWorkspace();PathTestFixture.set(LavalampWorkspace.class,live,"selected",selected);live.draw(960,540,-1,-1,0);check(GL11.glGetError()==0,"Clear workspace backdrop GL error");save("lavalamp-clear-backdrop.png",960,540);live.close();
        }finally{mc.theWorld=previous;}
    }
    private static void renderAt(LavalampWorkspace ws,int w,int h,int mx,int my,String file)throws Exception{mc.displayWidth=w;mc.displayHeight=h;mc.gameSettings.guiScale=1;ClientChangesRenderCheck.frame(w,h);ws.draw(w,h,mx,my,0);check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Clipping leaked");check(GL11.glGetError()==0,"Lavalamp GL error");if(file!=null)save(file,w,h);}
    private static Object slider(Object ws,Setting<?> value,int channel)throws Exception{for(Object hit:(List<?>)get(ws,"hits"))if(get(hit,"setting")==value&&integer(hit,"channel")==channel)return hit;throw new AssertionError("Missing color slider");}
    private static Object find(Object ws,String id)throws Exception{for(Object hit:(List<?>)get(ws,"hits"))if(id.equals(get(hit,"id")))return hit;throw new AssertionError("Missing hit "+id);}
    private static void click(LavalampWorkspace ws,String id,int button)throws Exception{Object hit=find(ws,id);int left=integer(hit,"left"),top=integer(hit,"top"),right=integer(hit,"right"),bottom=integer(hit,"bottom");ws.click((left+right)/2,(top+bottom)/2,button);}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static int integer(Object o,String field)throws Exception{return (Integer)get(o,field);}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private static void save(String file,int w,int h)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-1-y)*w+x)*4;image.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(image,"png",output.resolve(file).toFile());}
    private static final class Fixture extends Module{
        final BooleanSetting flag=addSetting(new BooleanSetting("Enabled feature",true));
        final ColorSetting color=addSetting(new ColorSetting("Effect colour",0xFF9D6AFF));
        final NumberSetting number=addSetting(new NumberSetting("Strength",3,0,10,.1));
        final ModeSetting style;
        Fixture(String name,Category category){super(name,"Adjust your module in one place.",category,0);addSetting(new RangeSetting("Delay",200,400,0,1000,10));style=addSetting(new ModeSetting("Style","Soft","Soft","Sharp","Smooth"));addSetting(new MultiSelectSetting("Parts",Arrays.asList("Fill","Outline","Glow"),Arrays.asList("Fill","Outline")));addSetting(new StringSetting("Label","Vibe"));}
    }
}
