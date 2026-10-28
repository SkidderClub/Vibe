package dev.vibe.module.impl.client;

import dev.vibe.chatter.*;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.ui.screen.ChatterGui;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.lwjgl.input.Keyboard;

public final class ChatterModule extends Module {
    public final BooleanSetting openMessageMenu=addSetting(new BooleanSetting("Open Message Menu",false).onEnable(()->openMenu()));
    private ChatterEngine engine;
    private String session="";
    private boolean menuRequested;
    private GuiScreen menuParent;
    public ChatterModule(){super("Chatter","React to chat with delayed messages or leaving the server",Category.CLIENT,Keyboard.KEY_NONE);}
    public ChatterEngine engine() {
        if(engine==null)engine=new ChatterEngine(new ChatterStore(new File(Minecraft.getMinecraft().mcDataDir,"vibe/chatter").toPath()));
        return engine;
    }
    private void openMenu() {
        openMessageMenu.setEnabled(false);
        if(!isConfigLoading()) {
            GuiScreen current=Minecraft.getMinecraft().currentScreen;
            menuParent=current instanceof GuiChat?null:current;
            menuRequested=true;
        }
    }
    @Override protected void onDisable(){clear();}
    public void clear(){if(engine!=null)engine.cancel();session="";}
    public void receive(String text){if(isEnabled())engine().receive(text,context(),now());}
    public void tick(){
        // Opening after chat key handling also lets the normal setting command
        // open this editor, instead of vanilla Enter immediately closing it.
        if(menuRequested){menuRequested=false;Minecraft.getMinecraft().displayGuiScreen(new ChatterGui(this,menuParent));menuParent=null;}
        if(isEnabled())engine().tick(context(),now());else if(engine!=null)engine.cancel();
    }
    private static long now(){return System.nanoTime()/1000000L;}
    private ChatterEngine.Connection context() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.thePlayer==null||mc.theWorld==null||mc.isSingleplayer()||mc.getNetHandler()==null)return null;
        String current=mc.getSession().getUsername();
        if(!session.equals(current)){if(engine!=null)engine.cancel();session=current;}
        final NetHandlerPlayClient handler=mc.getNetHandler();
        final String name=mc.thePlayer.getName(),host=mc.getCurrentServerData()==null
                ?String.valueOf(handler.getNetworkManager().getRemoteAddress()):mc.getCurrentServerData().serverIP;
        return new ChatterEngine.Connection() {
            public Object identity(){return handler;}public String name(){return name;}public String server(){return host;}
            public void message(String value){if(mc.getNetHandler()==handler&&mc.thePlayer!=null)mc.thePlayer.sendChatMessage(value);}
            public void leave(){if(mc.getNetHandler()!=handler)return;mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));}
        };
    }
}
