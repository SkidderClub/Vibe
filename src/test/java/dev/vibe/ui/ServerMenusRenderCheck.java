package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.event.MainMenuEvents;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.QolModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.render.QolRenderer;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Real vanilla server forms must replace an old world/editor image with an opaque menu. */
public final class ServerMenusRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path output) throws Exception {
        QolModule settings=new QolModule();modules.registerDynamic(settings);PathTestFixture.set(Module.class,settings,"enabled",true);
        QolRenderer qol=new QolRenderer();MainMenuEvents menus=new MainMenuEvents();
        try {
            String[] names={"edit-server","direct-connect"};
            for(int i=0;i<2;i++) {
                mc.displayWidth=960;mc.displayHeight=540;mc.gameSettings.guiScale=1;
                mc.theWorld=PathTestFixture.allocate(WorldClient.class);
                ClientChangesRenderCheck.frame(960,540);Gui.drawRect(0,0,960,540,0xFFFF00FF);qol.capture();
                java.lang.reflect.Field captured=QolRenderer.class.getDeclaredField("captured");captured.setAccessible(true);
                if(!captured.getBoolean(qol))throw new AssertionError("Could not create the stale world capture fixture");
                mc.theWorld=null;
                GuiScreen parent=new GuiMultiplayer(new GuiMainMenu());
                ServerData server=new ServerData("Example server","localhost",false);
                // Only this server-side translated label needs the complete Forge
                // language bootstrap. Keep the client form/controls native.
                Object resourceMode=server.getResourceMode();
                for(java.lang.reflect.Field field:resourceMode.getClass().getDeclaredFields())
                    if(net.minecraft.util.IChatComponent.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);field.set(resourceMode,new net.minecraft.util.ChatComponentText("Prompt"));
                    }
                qol.redrawBehind(i==0?new GuiScreenAddServer(parent,server):new GuiScreenServerList(parent,server));
                if(captured.getBoolean(qol))throw new AssertionError("A server form retained an old QOL world capture");
                // The standalone GL harness has no FML LaunchClassLoader. Dispatch
                // Forge's background callback at its native point before form text.
                GuiScreen screen=i==0?new GuiScreenAddServer(parent,server){
                    @Override public void drawDefaultBackground(){super.drawDefaultBackground();background(this,menus,qol);}
                }:new GuiScreenServerList(parent,server){
                    @Override public void drawDefaultBackground(){super.drawDefaultBackground();background(this,menus,qol);}
                };
                screen.setWorldAndResolution(mc,960,540);mc.currentScreen=screen;
                ClientChangesRenderCheck.frame(960,540);Gui.drawRect(0,0,960,540,0xFFFF00FF);
                screen.drawScreen(-1,-1,0);
                ByteBuffer pixels=BufferUtils.createByteBuffer(960*540*4);GL11.glReadPixels(0,0,960,540,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                BufferedImage image=new BufferedImage(960,540,BufferedImage.TYPE_INT_ARGB);int stale=0;
                for(int y=0;y<540;y++)for(int x=0;x<960;x++) {
                    int p=((539-y)*960+x)*4,r=pixels.get(p)&255,g=pixels.get(p+1)&255,b=pixels.get(p+2)&255;
                    if(r==255&&g==0&&b==255)stale++;
                    image.setRGB(x,y,0xFF000000|r<<16|g<<8|b);
                }
                if(stale>100)throw new AssertionError(names[i]+" replayed an old world/preview background: "+stale+" pixels");
                ImageIO.write(image,"png",output.resolve(names[i]+"-background.png").toFile());screen.onGuiClosed();
                if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Server form leaked GL state");
            }
        } finally {mc.currentScreen=null;mc.theWorld=null;}
        System.out.println("Native edit-server/direct-connect menus have opaque backgrounds and reject stale QOL world captures.");
    }
    private static void background(GuiScreen screen,MainMenuEvents menus,QolRenderer qol) {
        menus.onServerFormBackground(new GuiScreenEvent.BackgroundDrawnEvent(screen));qol.redrawBehind(screen);
    }
}
