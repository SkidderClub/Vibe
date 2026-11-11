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
    public final dev.vibe.inventory.ItemSwitching switching = new dev.vibe.inventory.ItemSwitching(this::addSetting,"Basic");
    private dev.vibe.inventory.HotbarSwitches.Lease slotLease;
    private ChatterEngine engine;
    private String session="";
    private boolean menuRequested;
    private GuiScreen menuParent;
    private int interactionSlot=-1;private boolean useOwned;private NetHandlerPlayClient interactionConnection;private long interactionQueued;
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
    public void clear(){if(engine!=null)engine.cancel();session="";interactionSlot=-1;interactionConnection=null;releaseUse();if(slotLease!=null){slotLease.close(true,switching.silent());slotLease=null;}}
    private void releaseUse(){if(useOwned){dev.vibe.input.VanillaClicks.discardPresses(Minecraft.getMinecraft().gameSettings.keyBindUseItem);dev.vibe.input.VanillaClicks.restore(Minecraft.getMinecraft().gameSettings.keyBindUseItem);useOwned=false;}}
    private boolean canInteract(){Minecraft mc=Minecraft.getMinecraft();return isEnabled()&&mc.thePlayer!=null&&mc.theWorld!=null&&mc.currentScreen==null&&mc.inGameHasFocus&&!mc.thePlayer.isUsingItem()
            &&!dev.vibe.module.impl.world.ScaffoldModule.isActive()&&!dev.vibe.module.impl.world.BedAuraModule.ownsMiningInput()
            &&!dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook()&&!dev.vibe.module.impl.world.AutoToolModule.hasHotbarAction()&&!dev.vibe.module.impl.combat.AutoBuffModule.ownsUseInput()&&!dev.vibe.module.impl.combat.AntiFireballModule.reserving();}
    public static void prepareInputHook(){
        if(dev.vibe.Vibe.getInstance()==null||dev.vibe.Vibe.getInstance().getModuleManager()==null)return;
        ChatterModule module=dev.vibe.Vibe.getInstance().getModuleManager().getModule(ChatterModule.class);if(module!=null)module.applyInteraction();
    }
    private void applyInteraction(){
        if(interactionSlot<0)return;Minecraft mc=Minecraft.getMinecraft();
        if(!isEnabled()||mc.getNetHandler()!=interactionConnection||now()-interactionQueued>10000){interactionSlot=-1;interactionConnection=null;return;}
        if(!canInteract())return;int slot=interactionSlot;interactionSlot=-1;interactionConnection=null;
        slotLease=dev.vibe.inventory.HotbarSwitches.acquire(this,switching,slot,dev.vibe.inventory.HotbarSwitches.CHATTER);
        if(slotLease==null||!slotLease.active()){if(slotLease!=null)slotLease.close(false,true);slotLease=null;return;}
        dev.vibe.inventory.HotbarSwitches.beginInputHook();dev.vibe.input.VanillaClicks.pulseUse(mc.gameSettings.keyBindUseItem);useOwned=true;
    }
    public static void titleHook(String title,String subtitle){
        if(dev.vibe.Vibe.getInstance()==null||dev.vibe.Vibe.getInstance().getModuleManager()==null)return;
        ChatterModule m=dev.vibe.Vibe.getInstance().getModuleManager().getModule(ChatterModule.class);if(m==null||!m.isEnabled())return;
        if(title!=null&&!title.isEmpty())m.engine().receive(title,dev.vibe.chatter.ChatterData.Source.TITLE,m.context(),now());
        if(subtitle!=null&&!subtitle.isEmpty())m.engine().receive(subtitle,dev.vibe.chatter.ChatterData.Source.SUBTITLE,m.context(),now());
    }
    public void receive(String text){if(isEnabled())engine().receive(text,context(),now());}
    public void hotkey(int key){Minecraft mc=Minecraft.getMinecraft();if(isEnabled()&&mc.currentScreen==null&&mc.inGameHasFocus)engine().key(key,context(),now());}
    public void tick(){
        releaseUse();
        Minecraft mc=Minecraft.getMinecraft();
        if(slotLease!=null&&(!slotLease.valid()||mc.thePlayer==null||!mc.thePlayer.isUsingItem())){slotLease.close(true,switching.silent());slotLease=null;}
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
            public void matched(ChatterData.Source source,ChatterData.Action action){
                if(!isEnabled()||mc.getNetHandler()!=handler)return;
                String origin=source==ChatterData.Source.TITLE?"Title":source==ChatterData.Source.SUBTITLE?"Subtitle":source==ChatterData.Source.HOTKEY?"Hotkey":"Chat";
                String queued=action==ChatterData.Action.LEAVE?"disconnect queued":action==ChatterData.Action.INTERACT?"item interaction queued":"message queued";
                dev.vibe.feedback.ModuleFeedback.message("chatter.match",getName(),origin+" matched: "+queued);
            }
            public void message(String value){if(mc.getNetHandler()==handler&&mc.thePlayer!=null)mc.thePlayer.sendChatMessage(value);}
            public boolean interact(int slot){if(mc.getNetHandler()!=handler||interactionSlot>=0||!canInteract())return false;interactionSlot=slot;interactionConnection=handler;interactionQueued=now();return true;}
            public void leave(){if(mc.getNetHandler()!=handler)return;mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));}
        };
    }
}
