package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.inventory.*;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Native slot geometry and the new compact/edge-anchored HUD layouts on the actual driver. */
public final class HudPolishRenderCheck {
    public static void verify(Minecraft mc,HudManager manager,HudModule hud,Path output)throws Exception{
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"hudManager",manager);
        net.minecraft.client.resources.Locale locale=new net.minecraft.client.resources.Locale();Map<String,String> translations=new HashMap<>();
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation("lang/en_US.lang")).getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){int split=line.indexOf('=');if(!line.startsWith("#")&&split>0)translations.put(line.substring(0,split),line.substring(split+1));}
        }
        PathTestFixture.set(net.minecraft.client.resources.Locale.class,locale,"properties",translations);PathTestFixture.set(net.minecraft.client.resources.I18n.class,null,"i18nLocale",locale);
        HudManager.HudElement effects=manager.getElement(HudManager.EFFECTS),guis=manager.getElement(HudManager.CUSTOM_GUIS),counter=manager.getElement(HudManager.BLOCK_COUNTER),hotbar=manager.getElement(HudManager.HOTBAR);
        hud.blockCounter.barMode.setValue("Circle");hud.blockCounter.percentage.setEnabled(true);hud.blockCounter.compact.setEnabled(true);
        hud.hotbar.selectionGlow.setEnabled(true);hud.hotbar.selectionStyle.setValue("Fill and Outline");
        for(String theme:new String[]{"Vibe","Skeet","LiquidGlass","Lavalamp"}){
            for(HudManager.HudElement e:new HudManager.HudElement[]{effects,guis,counter,hotbar}){e.setTheme(theme);e.setScale(1);}
            manager.drawPreview(hotbar,mc.fontRendererObj);hotbar.moveTo(270,570,new ScaledResolution(mc));
            effects.setBounds(0,0,154,98);effects.moveTo(40,40,new ScaledResolution(mc));counter.setBounds(0,0,120,36);counter.moveTo(250,50,new ScaledResolution(mc));
            frame();manager.beginEditorPreview(true);try{
                manager.drawPreview(effects,mc.fontRendererObj);manager.drawPreview(counter,mc.fontRendererObj);manager.drawPreview(hotbar,mc.fontRendererObj);manager.drawPreview(guis,mc.fontRendererObj);
                effects.moveTo(40,500,new ScaledResolution(mc));manager.drawPreview(effects,mc.fontRendererObj);
            }finally{manager.endEditorPreview();}
            save(output.resolve("hud-polish-"+theme.toLowerCase(Locale.ROOT)+".png"));
        }
        for(String selection:hud.hotbar.selectionStyle.getModes()){hud.hotbar.selectionStyle.setValue(selection);frame();manager.drawPreview(hotbar,mc.fontRendererObj);save(output.resolve("hotbar-selection-"+selection.toLowerCase(Locale.ROOT).replace(' ','-')+".png"));}
        nativePanels(mc,manager,hud,output);
        editorSelection(mc,manager,hud,output);
        if(GL11.glGetError()!=0)throw new AssertionError("HUD polish left an OpenGL error");
        System.out.println("Effects icons/bars, compact circular counter, hotbar selection styles, native panel slices/highlights and HUD editor selection passed.");
    }
    private static void nativePanels(Minecraft mc,HudManager manager,HudModule hud,Path output)throws Exception{
        boolean enabled=hud.getHudElements().isSelected(HudManager.CUSTOM_GUIS);if(!enabled)hud.getHudElements().toggle(HudManager.CUSTOM_GUIS);
        boolean wasHud=hud.isEnabled();hud.setEnabled(true);
        dev.vibe.inventory.AutomationFixture.Player player=PathTestFixture.allocate(dev.vibe.inventory.AutomationFixture.Player.class);player.inventory=new InventoryPlayer(player);
        try{
            GuiInventory inventory=PathTestFixture.allocate(GuiInventory.class);
            ContainerPlayer slots=new ContainerPlayer(player.inventory,true,player);inventory.inventorySlots=slots;
            GuiChest chest=PathTestFixture.allocate(GuiChest.class);chest.inventorySlots=new ContainerChest(player.inventory,new net.minecraft.inventory.InventoryBasic("Chest",false,27),player);
            for(GuiContainer gui:new GuiContainer[]{inventory,chest}){
                PathTestFixture.set(GuiContainer.class,gui,"xSize",176);PathTestFixture.set(GuiContainer.class,gui,"ySize",gui==inventory?166:168);
                PathTestFixture.set(GuiContainer.class,gui,"guiLeft",300);PathTestFixture.set(GuiContainer.class,gui,"guiTop",160);
                Container original=gui.inventorySlots;
                for(String theme:new String[]{"Vibe","Skeet","LiquidGlass","Lavalamp"}){
                    manager.getElement(HudManager.CUSTOM_GUIS).setTheme(theme);frame();
                    if(!ContainerThemes.backdrop(gui,300,160,0,0,176,gui==inventory?166:71))throw new AssertionError("Native panel not themed");
                    if(gui==chest&&!ContainerThemes.backdrop(gui,300,231,0,126,176,96))throw new AssertionError("Chest lower panel was not suppressed");
                    Slot slot=gui.inventorySlots.getSlot(gui==inventory?9:0);
                    dev.vibe.ui.render.InventoryHighlightRenderer.drawAt(gui,slot.xDisplayPosition+8,slot.yDisplayPosition+8,0xFF2DE2C2,600);
                    if(gui==inventory){gui.width=1280;gui.height=720;ContainerThemes.drawPotionEffects(gui,new ArrayList<>(Arrays.asList(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.moveSpeed.id,1240,1),new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.regeneration.id,240))));}
                    if(gui.inventorySlots!=original)throw new AssertionError("Container transaction object was replaced");
                    save(output.resolve("native-"+(gui==inventory?"inventory":"chest")+"-"+theme.toLowerCase(Locale.ROOT)+".png"));
                }
            }
            GuiInventory compact=PathTestFixture.allocate(GuiInventory.class);compact.width=320;compact.height=240;compact.inventorySlots=slots;
            PathTestFixture.set(GuiContainer.class,compact,"xSize",176);PathTestFixture.set(GuiContainer.class,compact,"ySize",166);PathTestFixture.set(GuiContainer.class,compact,"guiLeft",72);PathTestFixture.set(GuiContainer.class,compact,"guiTop",37);
            frame();ContainerThemes.backdrop(compact,72,37,0,0,176,166);ContainerThemes.drawPotionEffects(compact,new ArrayList<>(Arrays.asList(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.moveSpeed.id,1240,1),new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.regeneration.id,240))));save(output.resolve("native-inventory-compact-effects.png"));
        }finally{if(!enabled)hud.getHudElements().toggle(HudManager.CUSTOM_GUIS);if(!wasHud)hud.setEnabled(false);}
    }
    private static void editorSelection(Minecraft mc,HudManager manager,HudModule hud,Path output)throws Exception{
        HudEditorGui editor=new HudEditorGui(manager);editor.setWorldAndResolution(mc,1280,720);
        java.lang.reflect.Method select=HudEditorGui.class.getDeclaredMethod("select",HudManager.HudElement.class);select.setAccessible(true);
        java.lang.reflect.Field scroll=HudEditorGui.class.getDeclaredField("elementsScroll");scroll.setAccessible(true);scroll.setInt(editor,0);
        select.invoke(editor,manager.getElement(HudManager.CUSTOM_GUIS));if(scroll.getInt(editor)<=0)throw new AssertionError("Preview selection did not reveal the bottom sidebar row");
        frame();editor.drawScreen(-1,-1,0);save(output.resolve("hud-editor-custom-guis-selected.png"));
        select.invoke(editor,manager.getElement(HudManager.EFFECTS));frame();editor.drawScreen(-1,-1,0);save(output.resolve("hud-editor-effects-selected.png"));
        editor.onGuiClosed();
    }
    private static void frame(){GL11.glViewport(0,0,1280,720);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(.045F,.055F,.075F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,1280,720,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();}
    private static void save(Path file)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;image.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(image,"png",file.toFile());}
}
