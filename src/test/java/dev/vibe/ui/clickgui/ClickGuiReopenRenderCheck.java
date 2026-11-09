package dev.vibe.ui.clickgui;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.NumberSetting;
import dev.vibe.ui.ClientChangesRenderCheck;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.Session;
import org.lwjgl.opengl.GL11;

/** Close/reopen through Vibe's real entry point, retaining scroll and discarding in-flight input. */
public final class ClickGuiReopenRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path output)throws Exception{
        mc.displayWidth=520;mc.displayHeight=420;mc.gameSettings.guiScale=1;
        PathTestFixture.set(Minecraft.class,mc,"session",new Session("Alex","11111111111111111111111111111111","","legacy"));
        Path profile=Files.createTempDirectory(output,"clickgui-reopen-");
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"config",new VibeConfig(profile.toFile()));
        ClickGuiModule theme=new ClickGuiModule();modules.registerDynamic(theme);
        for(int i=0;i<36;i++)modules.registerDynamic(new Fixture("Combat test "+i,i==0?24:3));
        VibeClickGui cached=null;
        for(String name:new String[]{"Novoline","PacketV3","SuizideX","NeverLose","Xanax","Skeet","Futuristic","Breeze","Lavalamp"}){
            theme.getTheme().setValue(name);Vibe.getInstance().openClickGui();
            VibeClickGui gui=(VibeClickGui)mc.currentScreen;
            if(cached==null)cached=gui;else check(cached==gui,"Opening "+name+" discarded the shared screen");
            render(gui);render(gui);
            if(name.equals("Novoline")||name.equals("PacketV3")||name.equals("SuizideX")){
                Object workspace=get(gui,"dropdown"),panel=((List<?>)get(workspace,"panels")).get(0);
                int x=(Integer)get(panel,"x")-Math.round((Float)get(workspace,"horizontal"))+12,y=(Integer)get(panel,"y")+55;
                for(int i=0;i<4;i++)gui.wheel(x,y,-120);
                gui.wheel(500,408,-120);
            }else if(name.equals("NeverLose")){
                Object workspace=get(gui,"neverLose");for(int i=0;i<4;i++)gui.wheel((Integer)get(workspace,"contentLeft")+12,(Integer)get(workspace,"contentTop")+20,-120);
            }else if(name.equals("Xanax")){
                Object workspace=get(gui,"xanax"),pane=get(workspace,"modulesPane"),settings=get(workspace,"settingsPane");
                for(int i=0;i<4;i++){gui.wheel((Integer)get(pane,"x")+10,(Integer)get(pane,"y")+40,-120);gui.wheel((Integer)get(settings,"x")+15,(Integer)get(settings,"y")+50,-120);}
            }else if(name.equals("Skeet")){
                for(int i=0;i<4;i++)gui.wheel((Integer)get(gui,"skeetX")+110,(Integer)get(gui,"skeetY")+100,-120);
            }else if(name.equals("Futuristic")){
                Object panel=((Map<?,?>)get(gui,"panels")).values().iterator().next();gui.wheel((Integer)get(panel,"x")+12,(Integer)get(panel,"y")+35,-120);
            }else if(name.equals("Lavalamp")){
                Object workspace=get(gui,"lavalamp");for(int i=0;i<4;i++){gui.wheel((Integer)get(workspace,"x")+25,(Integer)get(workspace,"cy")+60,-120);gui.wheel((Integer)get(workspace,"cx")+30,(Integer)get(workspace,"cy")+70,-120);}
            }else{
                Object workspace=get(gui,"breeze");for(int i=0;i<4;i++)gui.wheel((Integer)get(workspace,"contentX")+20,(Integer)get(workspace,"contentY")+30,-120);
            }
            for(int i=0;i<5;i++){Thread.sleep(18);render(gui);}
            Map<String,Double> before=snapshot(gui,name);boolean moved=false;for(double value:before.values())if(value!=0)moved=true;
            check(moved,name+" fixture did not scroll");
            PathTestFixture.set(VibeClickGui.class,gui,"draggingSkeet",true);
            mc.displayGuiScreen(null);
            check(!(Boolean)get(gui,"draggingSkeet"),"A held drag survived closing");
            Vibe.getInstance().openClickGui();check(mc.currentScreen==gui,name+" created a fresh workspace");render(gui);
            compare(before,snapshot(gui,name),.001,name+" changed position after reopening");
            mc.displayGuiScreen(null);
            if(name.equals("Novoline")||name.equals("PacketV3")||name.equals("SuizideX")){
                // The dropdown fallback also restores offsets when a fresh screen is explicitly created.
                VibeClickGui fresh=new VibeClickGui();mc.displayGuiScreen(fresh);render(fresh);
                compare(before,snapshot(fresh,name),.51,name+" omitted saved panel/horizontal scroll");mc.displayGuiScreen(null);
            }
        }
        // Augustus already owns a persistent native ImGui context; use the same screen on this route too.
        theme.getTheme().setValue("Augustus");Vibe.getInstance().openClickGui();check(mc.currentScreen==cached,"Augustus discarded the shared screen");mc.displayGuiScreen(null);
        check(GL11.glGetError()==0,"ClickGUI reopen left an OpenGL error");
        System.out.println("ClickGUI reopen passed: all nine theme entry points, eight rendered scroll layouts, independent dropdown horizontal/vertical offsets, Xanax lists/settings and cleared drags.");
    }
    private static void render(VibeClickGui gui)throws Exception{ClientChangesRenderCheck.frame(520,420);gui.drawScreen(-1,-1,0);}
    private static Map<String,Double> snapshot(VibeClickGui gui,String name)throws Exception{
        Map<String,Double> values=new LinkedHashMap<>();
        if(name.equals("Novoline")||name.equals("PacketV3")||name.equals("SuizideX")){
            Object workspace=get(gui,"dropdown");values.put("horizontal",((Number)get(workspace,"horizontal")).doubleValue());
            for(Object panel:(List<?>)get(workspace,"panels"))values.put((String)get(panel,"name"),((Number)get(panel,"scroll")).doubleValue());
        }else if(name.equals("NeverLose"))values.put("scroll",((Number)get(get(gui,"neverLose"),"scroll")).doubleValue());
        else if(name.equals("Xanax"))for(String pane:new String[]{"modulesPane","settingsPane"})values.put(pane,((Number)get(get(get(gui,"xanax"),pane),"scroll")).doubleValue());
        else if(name.equals("Skeet"))values.put("scroll",((Number)get(gui,"skeetScroll")).doubleValue());
        else if(name.equals("Breeze"))values.put("scroll",((Number)get(get(gui,"breeze"),"scroll")).doubleValue());
        else if(name.equals("Lavalamp")){Object ws=get(gui,"lavalamp");for(String key:new String[]{"listTarget","target","x","y","w","h"})values.put(key,((Number)get(ws,key)).doubleValue());}
        else for(Object panel:((Map<?,?>)get(gui,"panels")).values())values.put(get(panel,"category").toString(),((Number)get(panel,"y")).doubleValue());
        return values;
    }
    private static void compare(Map<String,Double> a,Map<String,Double> b,double tolerance,String message){check(a.keySet().equals(b.keySet()),message);for(String key:a.keySet())check(Math.abs(a.get(key)-b.get(key))<=tolerance,message+" ("+key+": "+a.get(key)+" -> "+b.get(key)+")");}
    private static Object get(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Fixture extends Module {
        Fixture(String name,int settings){super(name,"Scroll preservation fixture",Category.COMBAT,0);for(int i=0;i<settings;i++)addSetting(new NumberSetting("Setting "+i,1,0,10,.1));}
    }
    public static final class ReopenMinecraft extends Minecraft {
        private ReopenMinecraft(){super(null);}
        @Override public void displayGuiScreen(GuiScreen screen){
            if(currentScreen!=null)currentScreen.onGuiClosed();currentScreen=screen;
            if(screen!=null)screen.setWorldAndResolution(this,new ScaledResolution(this).getScaledWidth(),new ScaledResolution(this).getScaledHeight());
        }
    }
}
