package dev.vibe.ui;

import com.google.gson.*;
import dev.vibe.Vibe;
import dev.vibe.config.*;
import dev.vibe.friend.FriendManager;
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
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Real Vibe host callbacks, isolated profiles and native screenshots for Breeze. */
public final class BreezeRenderCheck {
    private static Minecraft mc;private static Path output;private static VibeClickGui gui;private static Object workspace;
    public static void verify(Minecraft client,ModuleManager manager,Path directory)throws Exception{
        mc=client;output=directory;Path profile=Files.createTempDirectory(directory,"breeze-profile-");
        PathTestFixture.set(Minecraft.class,mc,"mcDataDir",profile.toFile());PathTestFixture.set(Minecraft.class,mc,"session",new Session("VibeTester","22222222222232228222222222222222","","legacy"));
        VibeConfig config=new VibeConfig(profile.toFile());PathTestFixture.set(Vibe.class,Vibe.getInstance(),"config",config);FriendManager friends=new FriendManager(profile.toFile());PathTestFixture.set(Vibe.class,Vibe.getInstance(),"friendManager",friends);
        ClickGuiModule click=new ClickGuiModule();manager.registerDynamic(click);click.getTheme().setValue("Breeze");
        Fixture fixture=new Fixture("Breeze Test",Category.COMBAT);manager.registerDynamic(fixture);
        for(Category category:Category.values())for(int i=0;i<4;i++)manager.registerDynamic(new Fixture(category.getLabel()+" Example "+i,category));
        config.restoreSession(manager);
        gui=new VibeClickGui();setup(960,540,1);workspace=get(gui,"breeze");render("breeze-modules.png");
        Method visible=workspace.getClass().getDeclaredMethod("visibleModules");visible.setAccessible(true);
        check(((List<?>)visible.invoke(workspace)).size()==manager.getModules().size(),"Breeze lost or recategorized modules");
        for(Object hit:(List<?>)get(workspace,"hits")){String id=(String)get(hit,"id");check(!id.equals("page:SETTINGS")&&!id.equals("page:SCRIPTING"),"Unexpected sidebar page");}
        GuiTextField search=(GuiTextField)get(workspace,"search");search.setText("Breeze Test");render(null);check(((List<?>)visible.invoke(workspace)).size()==1,"Search does not filter modules");
        click("module:"+fixture.getId(),0);check(fixture.isEnabled(),"Module toggle failed");render(null);click("settings:"+fixture.getId(),0);render("breeze-settings.png");
        Object header=find("header-toggle");int pinnedY=integer(header,"y");wheel(integer(header,"x")+20,integer(workspace,"contentY")+30,-120);PathTestFixture.set(workspace.getClass(),workspace,"scroll",(Float)get(workspace,"targetScroll"));render(null);check(integer(find("header-toggle"),"y")==pinnedY,"Module header scrolls out of view");PathTestFixture.set(workspace.getClass(),workspace,"scroll",0F);PathTestFixture.set(workspace.getClass(),workspace,"targetScroll",0F);
        click("favorite",0);check(click.getBreezeFavorites().getValue().contains(fixture.getId()),"Favorites were not saved");
        click("binding",0);GuiInput.keyTyped(gui,'k',Keyboard.KEY_K);check(fixture.getKey()==Keyboard.KEY_K,"Breeze keybind failed");
        click("boolean:Smart",0);check(!fixture.flag.isEnabled(),"Boolean control failed");render(null);check(!has("open:Mode"),"Hidden setting remained interactive");click("boolean:Smart",0);
        click("slider:Reach",0);GuiInput.mouseReleased(gui,0,0,0);double reach=fixture.range.getDouble();render(null);Object number=find("slider:Reach");int oldScroll=Math.round((Float)get(workspace,"targetScroll"));wheel(integer(number,"x")+5,integer(number,"y")+5,-120);check(fixture.range.getDouble()==reach,"Wheel changed a slider value");check((Float)get(workspace,"targetScroll")>oldScroll,"A hovered slider blocked scrolling");
        click("value:Reach",0);GuiInput.keyTyped(gui,'4',Keyboard.KEY_4);GuiInput.keyTyped(gui,'\0',Keyboard.KEY_RETURN);check(fixture.range.getDouble()==4,"Inline numeric value entry failed");click("value:Reach",0);GuiInput.keyTyped(gui,'5',Keyboard.KEY_5);GuiInput.keyTyped(gui,'\0',Keyboard.KEY_ESCAPE);check(fixture.range.getDouble()==4,"Escape did not cancel numeric entry");
        click("open:Mode",0);render(null);click("option:Packet",0);check(fixture.mode.is("Packet"),"Mode selection failed");
        Object pair=find("slider:Click speed");GuiInput.mouseClicked(gui,integer(pair,"x")+integer(pair,"w")-1,integer(pair,"y")+5,0);GuiInput.mouseReleased(gui,0,0,0);check(fixture.cps.getMax()>18&&fixture.cps.getMin()<=fixture.cps.getMax(),"Range slider failed to move its upper handle");
        click("open:Targets",0);render(null);click("option:Players",0);check(!fixture.targets.isSelected("Players"),"Multiselect did not toggle");
        click("open:Color",0);render(null);Object red=find("color:0");GuiInput.mouseClicked(gui,integer(red,"x")+integer(red,"w")-1,integer(red,"y")+4,0);GuiInput.mouseReleased(gui,0,0,0);check(fixture.color.getRed()>245,"Color editor did not update RGBA");
        click("string:Note",0);GuiInput.keyTyped(gui,'!',Keyboard.KEY_1);GuiInput.keyTyped(gui,'\0',Keyboard.KEY_RETURN);check(fixture.note.getValue().equals("Default!"),"Text setting edit failed");
        click("value:Precision",0);GuiInput.keyTyped(gui,'\0',Keyboard.KEY_RETURN);check(fixture.precision.getDouble()==.001,"Opening a numeric editor rounded away a precise setting value");
        click("back",0);search.setText("");click("category:Favorite",0);render(null);check(((List<?>)visible.invoke(workspace)).size()==1,"Favorites filter failed");click("category:All",0);
        config.save("Example",manager);click("page:CONFIGS",0);render("breeze-configs.png");
        fixture.flag.setEnabled(false);click("config:Example",0);render("breeze-config-preview.png");check(!fixture.flag.isEnabled(),"Preview applied a config without Load");click("load-config",0);check(fixture.flag.isEnabled(),"Local config load failed");
        click("config-back",0);render(null);
        // Online preview uses a cached fixture; no external requests or user profiles.
        JsonObject community=config.preview("Example");community.getAsJsonArray("modules").get(1).getAsJsonObject().addProperty("enabled",false);
        Map<String,JsonObject> previews=(Map<String,JsonObject>)get(workspace,"onlinePreviews");previews.put("Community",community);
        Class<?> entry=Class.forName("dev.vibe.config.OnlineConfigService$Entry");Constructor<?> ctor=entry.getDeclaredConstructors()[0];ctor.setAccessible(true);Object online=ctor.newInstance("Community","https://example.invalid/fixture");
        PathTestFixture.set(OnlineConfigService.class,Vibe.getInstance().getOnlineConfigs(),"entries",Collections.singletonList(online));
        PathTestFixture.set(workspace.getClass(),workspace,"onlineConfigs",Collections.singletonList(online));PathTestFixture.set(workspace.getClass(),workspace,"online",true);render(null);
        boolean beforePreview=fixture.isEnabled();click("config:Community",0);render("breeze-online-preview.png");check(fixture.isEnabled()==beforePreview,"Online preview applied module state");click("load-config",0);check(!fixture.isEnabled(),"Cached online config did not load through Vibe's loader");
        click("page:SOCIAL",0);render(null);((GuiTextField)get(workspace,"input")).setText("TestFriend");((GuiTextField)get(workspace,"alias")).setText("Buddy");click("friend-add",0);check(friends.isFriend("TestFriend"),"Friend Add failed");render("breeze-social.png");
        click("friend:TestFriend",0);render(null);((GuiTextField)get(workspace,"alias")).setText("NewAlias");click("friend-add",0);check(friends.find("TestFriend").getAlias().equals("NewAlias"),"Friend edit failed");render(null);click("friend-remove:TestFriend",0);check(!friends.isFriend("TestFriend"),"Friend Remove failed");
        click("page:THEMES",0);render("breeze-themes.png");
        click("bookmark:Fancy",0);click("theme-bookmarks",0);render(null);check(has("theme:Fancy")&&!has("theme:Default"),"Theme bookmark filter failed");click("theme-bookmarks",0);
        for(String name:click.getBreezePalette().getModes()){click("theme:"+name,0);render(null);click("page:MODULES",0);search.setText("");render("breeze-"+name.toLowerCase(Locale.ROOT).replace(' ','-')+".png");click("page:THEMES",0);}
        click.getBreezePalette().setValue("Default");click("page:MODULES",0);search.setText("");
        for(int[] size:new int[][]{{480,300,1},{320,240,1},{480,270,2},{320,240,4}}){setup(size[0],size[1],size[2]);render("breeze-"+size[0]+"-scale"+size[2]+".png");check(integer(workspace,"x")>=0&&integer(workspace,"y")>=0,"Window outside screen");click("page:SOCIAL",0);render(null);click("page:THEMES",0);render(null);click("page:MODULES",0);}
        setup(960,540,1);render(null);int regularWidth=integer(workspace,"w");click("expand",0);render(null);check(integer(workspace,"w")>regularWidth,"Expand footer button failed");click("expand",0);render(null);int x=integer(workspace,"x"),y=integer(workspace,"y");GuiInput.mouseClicked(gui,x+30,y+20,0);gui.drawScreen(x+60,y+35,0);GuiInput.mouseReleased(gui,0,0,0);check(integer(workspace,"x")!=x,"Window drag failed");
        gui.onGuiClosed();check(get(workspace,"slider")==null&&!(Boolean)get(workspace,"dragging"),"Close left an active drag");check(config.preview("Example").toString().contains("Breeze Favorites"),"Breeze preferences missing from config");check(config.saveSession(manager),"Breeze settings could not be checkpointed for restart");
        Class<?> icons=Class.forName("dev.vibe.ui.clickgui.BreezeIcons");Method rasterize=icons.getDeclaredMethod("rasterize",String.class,int.class);rasterize.setAccessible(true);BufferedImage discord=(BufferedImage)rasterize.invoke(null,"discord",96);check((discord.getRGB(33,51)>>>24)==0&&(discord.getRGB(48,37)>>>24)>200,"SVG Discord mask lost its eye cutouts");
        System.out.println("Breeze passed: real module/category data, all setting types, keybinds, favorites, local/online read-only previews and loads, friend CRUD, eight palettes, compact/scaled layouts and GL clipping.");
    }
    private static void setup(int w,int h,int scale){mc.displayWidth=w*scale;mc.displayHeight=h*scale;mc.gameSettings.guiScale=scale;gui.setWorldAndResolution(mc,w,h);mc.currentScreen=gui;}
    private static void render(String name)throws Exception{
        ClientChangesRenderCheck.frame(mc.displayWidth,mc.displayHeight);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,gui.width,gui.height,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);gui.drawScreen(-1,-1,0);
        check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Breeze leaked its scissor");check(GL11.glGetError()==GL11.GL_NO_ERROR,"Breeze produced a GL error");if(name!=null)save(name);
    }
    private static Object find(String id)throws Exception{
        for(int attempt=0;attempt<15;attempt++){render(null);for(Object hit:(List<?>)get(workspace,"hits"))if(get(hit,"id").equals(id)&&integer(hit,"h")>=3)return hit;
            int maximum=integer(workspace,"maxScroll");float offset=(Float)get(workspace,"targetScroll");if(offset>=maximum){PathTestFixture.set(workspace.getClass(),workspace,"scroll",0F);PathTestFixture.set(workspace.getClass(),workspace,"targetScroll",0F);}else{offset=Math.min(maximum,offset+100);PathTestFixture.set(workspace.getClass(),workspace,"scroll",offset);PathTestFixture.set(workspace.getClass(),workspace,"targetScroll",offset);}}
        throw new AssertionError("Missing Breeze control: "+id);
    }
    private static boolean has(String id)throws Exception{for(Object hit:(List<?>)get(workspace,"hits"))if(get(hit,"id").equals(id))return true;return false;}
    private static void click(String id,int button)throws Exception{Object target=find(id);GuiInput.mouseClicked(gui,integer(target,"x")+Math.max(1,integer(target,"w")/2),integer(target,"y")+Math.max(1,integer(target,"h")/2),button);GuiInput.mouseReleased(gui,0,0,button);}
    private static void wheel(int x,int y,int delta)throws Exception{Method method=VibeClickGui.class.getDeclaredMethod("wheel",int.class,int.class,int.class);method.setAccessible(true);method.invoke(gui,x,y,delta);}
    private static Object get(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static int integer(Object target,String name)throws Exception{return(Integer)get(target,name);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void save(String name)throws Exception{int w=mc.displayWidth,h=mc.displayHeight;ByteBuffer buffer=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int at=((h-y-1)*w+x)*4;image.setRGB(x,y,((buffer.get(at)&255)<<16)|((buffer.get(at+1)&255)<<8)|(buffer.get(at+2)&255));}ImageIO.write(image,"png",output.resolve(name).toFile());}
    private static final class Fixture extends Module {
        final BooleanSetting flag=addSetting(new BooleanSetting("Smart",true));final NumberSetting range=addSetting(new NumberSetting("Reach",3,1,6,.1));
        final ModeSetting mode=addSetting(new ModeSetting("Mode","Normal",()->flag.isEnabled(),"Normal","Packet"));final RangeSetting cps=addSetting(new RangeSetting("Click speed",8,12,1,20,1));
        final ColorSetting color=addSetting(new ColorSetting("Color",0xFF26B8DD));final MultiSelectSetting targets=addSetting(new MultiSelectSetting("Targets",Arrays.asList("Players","Mobs"),Arrays.asList("Players")));final StringSetting note=addSetting(new StringSetting("Note","Default"));
        final NumberSetting precision=addSetting(new NumberSetting("Precision",.001,0,.1,.001));
        Fixture(String name,Category category){super(name,"Real Vibe module data and settings",category,0);}
    }
}
