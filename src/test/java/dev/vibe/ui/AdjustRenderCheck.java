package dev.vibe.ui;
import dev.vibe.Vibe;
import dev.vibe.hud.*;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.*;
import dev.vibe.module.impl.movement.*;
import dev.vibe.module.impl.visual.AnimationsModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import dev.vibe.ui.clickgui.VibeClickGui;
import dev.vibe.ui.render.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Real fonts, item geometry, clipping, input targets and native block poses. */
public final class AdjustRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path out)throws Exception{
        mc.displayWidth=1280;mc.displayHeight=720;
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"config",new dev.vibe.config.VibeConfig(out.resolve("adjust-config").toFile()));
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();dev.vibe.ui.render.esp.EspRenderCheck.installItemRenderer(mc);
        ClickGuiModule click=register(modules,new ClickGuiModule());click.getTheme().setValue("Adjust");
        HudModule hud=register(modules,new HudModule());Controls controls=register(modules,new Controls());register(modules,new StepModule());register(modules,new JesusModule());register(modules,new NoFallModule());register(modules,new AntiAimModule());register(modules,new QolModule());register(modules,new JoinClaimModule());AnimationsModule animations=register(modules,new AnimationsModule());hud.synchronizeArrayListModules();
        VibeClickGui gui=new VibeClickGui();gui.setWorldAndResolution(mc,1280,720);draw(gui,-1,-1);save(out.resolve("adjust-columns.png"));
        Object workspace=read(gui,"adjust"),row=find(workspace,"MODULE",controls,null,-1);click(gui,row,1);draw(gui,center(row,"x","w"),center(row,"y","h"));
        boolean before=hud.getArrayListModules().isSelected(controls.getRawName());click(gui,find(workspace,"EYE",controls,null,-1),0);if(hud.getArrayListModules().isSelected(controls.getRawName())==before)throw new AssertionError("Eye did not update Vibe's arraylist selector");
        draw(gui,-1,-1);click(gui,find(workspace,"SETTING",null,controls.color,-1),0);draw(gui,-1,-1);
        click(gui,find(workspace,"COLOR",null,controls.color,1),0);click(gui,find(workspace,"COLOR",null,controls.color,0),0);click(gui,find(workspace,"COLOR",null,controls.color,2),0);if(Math.abs(controls.color.getAlpha()-128)>4)throw new AssertionError("Inline alpha picker ignored its hit rectangle");
        double old=controls.number.getDouble();Object slider=find(workspace,"SLIDER",null,controls.number,-1);Method wheel=workspace.getClass().getDeclaredMethod("wheel",int.class,int.class,int.class);wheel.setAccessible(true);wheel.invoke(workspace,center(slider,"x","w"),center(slider,"y","h"),-120);if(controls.number.getDouble()!=old)throw new AssertionError("Scrolling changed a slider");
        click(gui,slider,0);if(Math.abs(controls.number.getDouble()-5)>.2)throw new AssertionError("Slider click not mapped to its track");draw(gui,-1,-1);save(out.resolve("adjust-picker.png"));
        gui.onGuiClosed();VibeClickGui reopened=new VibeClickGui();reopened.setWorldAndResolution(mc,1280,720);draw(reopened,-1,-1);if(hud.getArrayListModules().isSelected(controls.getRawName())==before)throw new AssertionError("Eye selection lost on reopen");reopened.onGuiClosed();
        hud.targetHud.mode.setValue("Adjust");ClientChangesRenderCheck.frame(1280,720);TargetHudRenderer renderer=new TargetHudRenderer();TargetHudData sample=TargetHudData.sample();int width=renderer.width(hud.targetHud,sample,mc.fontRendererObj);if(renderer.height(hud.targetHud)!=24||width!=80)throw new AssertionError("Adjust target card is not source-sized");
        GL11.glPushMatrix();GL11.glTranslatef(80,80,0);GL11.glScalef(5,5,1);renderer.draw(hud.targetHud,sample,mc.fontRendererObj,0,0,width,()->{},true,System.currentTimeMillis());GL11.glPopMatrix();save(out.resolve("adjust-target-hud.png"));renderer.close();
        chatActions(mc,hud,out);
        mc.thePlayer=PathTestFixture.allocate(NovolineRenderCheck.PosePlayer.class);
        try{int count=0;for(String mode:animations.getBlockingStyle().getModes())if(mode.startsWith("Adjust ")){animations.getBlockingStyle().setValue(mode);GL11.glPushMatrix();GL11.glLoadIdentity();try{NovolineAnimations.begin(animations,.5F);if(!NovolineAnimations.transform(.3F,0))throw new AssertionError("Missing "+mode);FloatBuffer matrix=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);for(int n=0;n<16;n++)if(!Float.isFinite(matrix.get(n)))throw new AssertionError("Nonfinite "+mode);}finally{NovolineAnimations.end();GL11.glPopMatrix();}count++;}if(count!=13)throw new AssertionError("Expected 13 sword styles, got "+count);}finally{mc.thePlayer=null;}
        for(int[] size:new int[][]{{854,480},{480,300}}){mc.displayWidth=size[0];mc.displayHeight=size[1];VibeClickGui small=new VibeClickGui();small.setWorldAndResolution(mc,size[0],size[1]);ClientChangesRenderCheck.frame(size[0],size[1]);small.drawScreen(50,50,1);if(GL11.glGetError()!=0)throw new AssertionError("GL error at "+size[0]);small.onGuiClosed();}
        System.out.println("Adjust columns, eye/slider/HSBA controls, scroll input, compact TargetHUD and all 13 source poses passed.");
    }
    private static void chatActions(Minecraft mc,HudModule hud,Path out)throws Exception{
        HudManager manager=new HudManager(out.resolve("adjust-chat-layout").toFile());PathTestFixture.set(Vibe.class,Vibe.getInstance(),"hudManager",manager);
        mc.thePlayer=PathTestFixture.allocate(NovolineRenderCheck.PosePlayer.class);mc.theWorld=PathTestFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);mc.ingameGUI=PathTestFixture.allocate(GuiIngame.class);
        GuiNewChat chat=new GuiNewChat(mc);PathTestFixture.set(GuiIngame.class,mc.ingameGUI,"persistantChatGUI",chat);hud.setEnabled(true);
        dev.vibe.command.VibeChatGui screen=new dev.vibe.command.VibeChatGui();
        for(int scale:new int[]{1,2})for(boolean custom:new boolean[]{false,true}){
            mc.gameSettings.guiScale=scale;ScaledResolution res=new ScaledResolution(mc);screen.setWorldAndResolution(mc,res.getScaledWidth(),res.getScaledHeight());mc.currentScreen=screen;
            if(hud.getHudElements().isSelected(HudManager.CHAT)!=custom)hud.getHudElements().toggle(HudManager.CHAT);
            net.minecraft.util.IChatComponent root=new net.minecraft.util.ChatComponentText("A message with clickable metadata");ChatLine line=new ChatLine(0,root,0);ChatActions.track(line,root);
            List<ChatLine> drawn=new ArrayList<>(Collections.singletonList(line));PathTestFixture.set(GuiNewChat.class,chat,"drawnChatLines",drawn);PathTestFixture.set(GuiNewChat.class,chat,"chatLines",new ArrayList<>(drawn));
            ClientChangesRenderCheck.frame(1280,720);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,res.getScaledWidth(),res.getScaledHeight(),0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
            float x,y,s=chat.getChatScale();
            if(custom){HudManager.HudElement element=manager.getElement(HudManager.CHAT);element.setTheme("Skeet");element.setScale(1.35F);element.moveTo(30,80,res);PathTestFixture.set(HudChat.class,null,"wrappedChat",chat);PathTestFixture.set(HudChat.class,null,"wrappedWidth",hud.chat.width.getInt()-12);manager.drawChat();Thread.sleep(210);manager.drawChat();
                Field rows=ChatActions.class.getDeclaredField("CUSTOM");rows.setAccessible(true);List<?> values=(List<?>)rows.get(null);if(values.isEmpty())throw new AssertionError("Scaled custom chat did not publish action geometry");Object row=values.get(0);Method action=row.getClass().getDeclaredMethod("actionsX");action.setAccessible(true);x=(Float)action.invoke(row);y=((Float)read(row,"top")+(Float)read(row,"bottom"))/2;s=(Float)read(row,"scale");
            }else{x=chat.getChatWidth()+5*s+2;y=res.getScaledHeight()-28-9*s/2;}
            if(ChatActions.draw((int)(x+s),(int)y).isEmpty())throw new AssertionError("Copy button not reachable: custom="+custom+", GUI scale="+scale);
            GuiInput.mouseClicked(screen,(int)(x+11*s),(int)y,0);if(!drawn.isEmpty())throw new AssertionError("Remove click did not consume native/custom chat action");
        }
        mc.gameSettings.guiScale=1;mc.currentScreen=null;mc.theWorld=null;mc.thePlayer=null;
    }
    private static void draw(GuiScreen gui,int x,int y)throws Exception{ClientChangesRenderCheck.frame(1280,720);gui.drawScreen(x,y,1);if(GL11.glGetError()!=0)throw new AssertionError("Adjust GL state error");}
    private static Object find(Object workspace,String kind,Module module,Setting<?> setting,int part)throws Exception{for(Object hit:(List<?>)read(workspace,"hits"))if(read(hit,"kind").toString().equals(kind)&&(module==null||read(hit,"module")==module)&&(setting==null||read(hit,"setting")==setting)&&(part<0||(Integer)read(hit,"part")==part))return hit;throw new AssertionError("Missing Adjust "+kind+" control");}
    private static int center(Object h,String a,String b)throws Exception{return (Integer)read(h,a)+(Integer)read(h,b)/2;}
    private static void click(GuiScreen gui,Object h,int button)throws Exception{int x=center(h,"x","w"),y=center(h,"y","h");GuiInput.mouseClicked(gui,x,y,button);GuiInput.mouseReleased(gui,x,y,button);}
    private static Object read(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static <T extends Module>T register(ModuleManager modules,T module){modules.registerDynamic(module);return module;}
    private static void save(Path path)throws Exception{ByteBuffer b=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=(y*1280+x)*4;image.setRGB(x,719-y,0xFF000000|(b.get(i)&255)<<16|(b.get(i+1)&255)<<8|(b.get(i+2)&255));}ImageIO.write(image,"PNG",path.toFile());}
    private static final class Controls extends Module{
        final NumberSetting number=addSetting(new NumberSetting("Speed",2,0,10,.1));final RangeSetting range=addSetting(new RangeSetting("Delay",2,6,0,10,.1));final ModeSetting mode=addSetting(new ModeSetting("Mode","First","First","Second"));final MultiSelectSetting multi=addSetting(new MultiSelectSetting("Options",Arrays.asList("One","Two"),Collections.singletonList("One")));final ColorSetting color=addSetting(new ColorSetting("Color",0xFF2DE2C2));final StringSetting text=addSetting(new StringSetting("Text","Vibe",100,null));Controls(){super("Control preview","Rendering fixture",Category.COMBAT,0);}
    }
}
