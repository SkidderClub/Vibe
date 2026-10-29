package dev.vibe.event;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.world.ChestStealerModule;
import dev.vibe.language.LanguageManager;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/** Live chest safety controls, using ordinary Forge buttons and module disable paths. */
public final class ChestControlsEvents {
    static final int AURA=0x564910,STEALER=0x564911;
    private final Map<GuiScreen,List<GuiButton>> screens=new WeakHashMap<>();
    @SubscribeEvent public void init(GuiScreenEvent.InitGuiEvent.Post event){
        if(!(event.gui instanceof GuiChest))return;
        event.buttonList.removeIf(button->button.id==AURA||button.id==STEALER);
        event.buttonList.add(new GuiButton(AURA,0,0,138,18,LanguageManager.translate("Disable Killaura")));
        event.buttonList.add(new GuiButton(STEALER,0,0,138,18,LanguageManager.translate("Disable Cheststealer")));
        screens.put(event.gui,event.buttonList);sync(event.gui);
    }
    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Pre event){if(event.gui instanceof GuiChest)sync(event.gui);}
    private void sync(GuiScreen gui){
        List<GuiButton> buttons=screens.get(gui);if(buttons==null)return;int y=6,w=Math.min(138,gui.width-12);
        for(int id:new int[]{AURA,STEALER})for(GuiButton button:buttons)if(button.id==id){
            Module module=module(id);button.visible=button.enabled=module!=null&&module.isEnabled();
            button.width=w;button.xPosition=gui.width-w-6;button.yPosition=y;if(button.visible)y+=22;
        }
    }
    @SubscribeEvent public void click(GuiScreenEvent.ActionPerformedEvent.Pre event){
        if(!(event.gui instanceof GuiChest)||event.button==null||(event.button.id!=AURA&&event.button.id!=STEALER))return;
        event.setCanceled(true);disable(event.gui,event.button.id);
    }
    @SubscribeEvent public void mouse(GuiScreenEvent.MouseInputEvent.Pre event){
        if(!(event.gui instanceof GuiChest)||Mouse.getEventButton()!=0||!Mouse.getEventButtonState())return;
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        int x=Mouse.getEventX()*event.gui.width/mc.displayWidth,y=event.gui.height-Mouse.getEventY()*event.gui.height/mc.displayHeight-1;
        // GuiContainer continues handling slots after super.mouseClicked. Consume
        // the raw press so a button over a large chest cannot also move an item.
        if(press(event.gui,x,y))event.setCanceled(true);
    }
    boolean press(GuiScreen gui,int x,int y){
        sync(gui);List<GuiButton> buttons=screens.get(gui);if(buttons==null)return false;
        for(GuiButton button:buttons)if((button.id==AURA||button.id==STEALER)&&button.mousePressed(net.minecraft.client.Minecraft.getMinecraft(),x,y)){
            disable(gui,button.id);return true;
        }
        return false;
    }
    private void disable(GuiScreen gui,int id){
        Module module=module(id);if(module!=null)module.setEnabled(false);sync(gui);
        Vibe vibe=Vibe.getInstance();if(vibe!=null&&vibe.getConfig()!=null)vibe.getConfig().save(vibe.getModuleManager());
    }
    private static Module module(int id){Vibe vibe=Vibe.getInstance();if(vibe==null||vibe.getModuleManager()==null)return null;
        return id==AURA?vibe.getModuleManager().getModule(KillAuraModule.class):vibe.getModuleManager().getModule(ChestStealerModule.class);}
}
