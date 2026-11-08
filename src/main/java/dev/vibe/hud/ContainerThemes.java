package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.RenderUtils;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.renderer.*;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Reskins native panels, never their slots, container IDs, input handlers or transactions. */
public final class ContainerThemes {
    private static final Field WIDTH=ReflectionHelper.findField(GuiContainer.class,"xSize","field_146999_f"),HEIGHT=ReflectionHelper.findField(GuiContainer.class,"ySize","field_147000_g"),LEFT=ReflectionHelper.findField(GuiContainer.class,"guiLeft","field_147003_i");
    private static final ResourceLocation EFFECT_ICONS=new ResourceLocation("textures/gui/container/inventory.png");
    private static Method foreground;
    private static final ThreadLocal<Boolean> DRAWING_LABELS=new ThreadLocal<>();
    private ContainerThemes(){}
    private static HudModule hud(){return Vibe.getInstance()==null||Vibe.getInstance().getModuleManager()==null?null:Vibe.getInstance().getModuleManager().getModule(HudModule.class);}
    public static String screen(Object gui){
        if(gui instanceof GuiInventory)return "Inventory";if(gui instanceof GuiChest)return "Chests";if(gui instanceof GuiCrafting)return "Crafting";if(gui instanceof GuiFurnace)return "Furnaces";
        if(gui instanceof GuiDispenser)return "Dispensers";if(gui instanceof GuiHopper)return "Hoppers";if(gui instanceof GuiBrewingStand)return "Brewing";
        if(gui instanceof GuiRepair)return "Anvils";if(gui instanceof GuiEnchantment)return "Enchanting";if(gui instanceof GuiMerchant)return "Trading";if(gui instanceof GuiBeacon)return "Beacons";return null;
    }
    public static boolean enabled(Object gui){HudModule hud=hud();String kind=screen(gui);return hud!=null&&hud.isEnabled()&&hud.getHudElements().isSelectedIgnoreCase(HudManager.CUSTOM_GUIS)&&kind!=null&&hud.containers.screens.isSelected(kind)&&Vibe.getInstance().getHudManager()!=null;}
    /** Run after vanilla's effect layout so slots, highlights and the player model share one origin. */
    public static void updatePotionLayout(Object object){
        if(!(object instanceof GuiInventory)||!enabled(object)||!hud().containers.centerInventory.isEnabled())return;
        GuiContainer gui=(GuiContainer)object;
        try{LEFT.setInt(gui,(gui.width-dimension(WIDTH,gui))/2);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    /** Returning false leaves the complete vanilla/Forge effect renderer in control. */
    public static boolean potionEffects(Object object){
        if(!(object instanceof GuiInventory)||!enabled(object))return false;
        Minecraft mc=Minecraft.getMinecraft();if(mc.thePlayer==null)return true;
        drawPotionEffects((GuiContainer)object,new ArrayList<>(mc.thePlayer.getActivePotionEffects()));return true;
    }
    static void drawPotionEffects(GuiContainer gui,List<PotionEffect> effects){
        Minecraft mc=Minecraft.getMinecraft();HudModule hud=hud();HudManager manager=Vibe.getInstance().getHudManager();OverlaySettings.Containers s=hud.containers;
        effects.removeIf(e->Potion.potionTypes[e.getPotionID()]==null||!Potion.potionTypes[e.getPotionID()].shouldRender(e));
        if(effects.isEmpty())return;
        int left=dev.vibe.ui.render.InventoryHighlightRenderer.left(gui),right=left+dimension(WIDTH,gui),top=dev.vibe.ui.render.InventoryHighlightRenderer.top(gui);
        int leftSpace=left-12,rightSpace=gui.width-right-12,w=Math.min(120,Math.max(32,Math.max(leftSpace,rightSpace)));
        int x=leftSpace>=rightSpace?left-w-8:right+8;
        int step=effects.size()<6?34:Math.max(1,Math.min(34,(gui.height-top-36)/Math.max(1,effects.size()-1)));
        int y=Math.max(4,Math.min(top,gui.height-4-(effects.size()-1)*step-32));
        effects.sort(Comparator.comparingInt(PotionEffect::getPotionID));
        GlStateManager.disableLighting();
        for(PotionEffect effect:effects){
            Potion potion=Potion.potionTypes[effect.getPotionID()];
            HudOverlayRenderer.panel(manager,hud,manager.getElement(HudManager.CUSTOM_GUIS),s,x,y,w,32);
            if(potion.hasStatusIcon()){
                int icon=potion.getStatusIconIndex();mc.getTextureManager().bindTexture(EFFECT_ICONS);GlStateManager.color(1,1,1,1);
                Gui.drawModalRectWithCustomSizedTexture(x+6,y+7,icon%8*18,198+icon/8*18,18,18,256,256);
            }
            potion.renderInventoryEffect(x,y,effect,mc);
            if(potion.shouldRenderInvText(effect)){
                String name=I18n.format(potion.getName());int level=effect.getAmplifier()+1;
                if(level>1)name+=" "+(level<=10?new String[]{"","I","II","III","IV","V","VI","VII","VIII","IX","X"}[level]:level);
                int textX=x+29,space=Math.max(0,w-35);
                mc.fontRendererObj.drawString(mc.fontRendererObj.trimStringToWidth(name,space),textX,y+6,s.text.getArgb(),s.shadow.isEnabled());
                mc.fontRendererObj.drawString(mc.fontRendererObj.trimStringToWidth(Potion.getDurationString(effect),space),textX,y+17,RenderUtils.blend(s.text.getArgb(),s.accent.getArgb(),.35F),s.shadow.isEnabled());
            }
            y+=step;
        }
        GlStateManager.color(1,1,1,1);GlStateManager.enableTexture2D();
    }
    /** True only for the native full panel, including the two full-width chest panel slices. */
    public static boolean panelSlice(int x,int y,int width,int height,int left,int top,int panelWidth,int panelHeight){return x==left&&width==panelWidth&&height>18&&y>=top&&y+height<=top+panelHeight+1;}
    public static boolean backdrop(Object object,int x,int y,int u,int v,int width,int height){
        if(!(object instanceof GuiContainer)||!enabled(object))return false;GuiContainer gui=(GuiContainer)object;
        int left=dev.vibe.ui.render.InventoryHighlightRenderer.left(gui),top=dev.vibe.ui.render.InventoryHighlightRenderer.top(gui);
        int w=dimension(WIDTH,gui),h=dimension(HEIGHT,gui);
        if(!panelSlice(x,y,width,height,left,top,w,h))return false;
        if(y==top){HudModule hud=hud();HudManager manager=Vibe.getInstance().getHudManager();HudManager.HudElement element=manager.getElement(HudManager.CUSTOM_GUIS);
            HudOverlayRenderer.panel(manager,hud,element,hud.containers,left,top,w,h);
            for(Object value:gui.inventorySlots.inventorySlots){Slot slot=(Slot)value;slot(hud.containers,left+slot.xDisplayPosition,top+slot.yDisplayPosition);}
            if(gui instanceof GuiCrafting||gui instanceof GuiFurnace){int ax=left+(gui instanceof GuiFurnace?79:89),ay=top+35;
                int c=hud.containers.accent.getArgb();HudShapes.line(ax,ay+8,ax+21,ay+8,1.5F,c);HudShapes.line(ax+15,ay+2,ax+21,ay+8,1.5F,c);HudShapes.line(ax+21,ay+8,ax+15,ay+14,1.5F,c);}
        }
        GlStateManager.color(-1,-1,-1,-1);GlStateManager.color(1,1,1,1);GlStateManager.disableTexture2D();GlStateManager.enableTexture2D();
        return true;
    }
    private static void slot(OverlaySettings.Containers s,int x,int y){HudShapes.round(x-1,y-1,18,18,2,s.slots.getArgb());if(s.slotOutline.isEnabled())RenderUtils.roundedOutline(x-1,y-1,x+17,y+17,2,.6F,s.slotBorder.getArgb());}
    private static int dimension(Field field,Object gui){try{return field.getInt(gui);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    public static void foreground(Object gui,int x,int y){
        boolean themed=enabled(gui),previous=Boolean.TRUE.equals(DRAWING_LABELS.get());
        if(themed)DRAWING_LABELS.set(true);
        try{
            if(foreground==null){for(String name:new String[]{"drawGuiContainerForegroundLayer","func_146979_b","b"})try{foreground=GuiContainer.class.getDeclaredMethod(name,int.class,int.class);foreground.setAccessible(true);break;}catch(NoSuchMethodException ignored){}
                if(foreground==null)throw new IllegalStateException("Cannot resolve native container labels");}
            foreground.invoke(gui,x,y);
        }catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException(cause);}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
        finally{if(previous)DRAWING_LABELS.set(true);else DRAWING_LABELS.remove();}
    }
    public static int fontColor(int vanilla){
        if(!Boolean.TRUE.equals(DRAWING_LABELS.get()))return vanilla;int rgb=vanilla&0xFFFFFF;
        HudModule hud=hud();if(hud==null)return vanilla;
        int text=rgb==0x808080?RenderUtils.blend(hud.containers.background.getArgb()|0xFF000000,hud.containers.text.getArgb(),.55F):hud.containers.text.getArgb();
        return rgb==0x404040||rgb==0x808080?((vanilla&0xFC000000)==0?0xFF000000:vanilla&0xFF000000)|(text&0xFFFFFF):vanilla;
    }
    static void preview(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res){
        Minecraft mc=Minecraft.getMinecraft();int w=176,h=166,x=(res.getScaledWidth()-w)/2,y=(res.getScaledHeight()-h)/2;e.setBounds(x,y,w,h);
        HudOverlayRenderer.panel(manager,hud,e,hud.containers,x,y,w,h);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(hud.containers,x+8+col*18,y+84+row*18);
        for(int col=0;col<9;col++)slot(hud.containers,x+8+col*18,y+142);
        for(int row=0;row<4;row++)slot(hud.containers,x+8,y+8+row*18);
        for(int row=0;row<2;row++)for(int col=0;col<2;col++)slot(hud.containers,x+88+col*18,y+26+row*18);
        slot(hud.containers,x+144,y+36);mc.fontRendererObj.drawString("Crafting",x+86,y+10,hud.containers.text.getArgb());
        HudShapes.line(x+131,y+42,x+140,y+42,1.5F,hud.containers.accent.getArgb());
        RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();try(dev.vibe.ui.render.HudItemGeometryRenderer.Scope items=dev.vibe.ui.render.HudItemGeometryRenderer.begin()){
            for(int col=0;col<5;col++){ItemStack stack=mc.thePlayer==null||mc.thePlayer.inventory==null?null:mc.thePlayer.inventory.mainInventory[col];if(stack==null)stack=new ItemStack(col==0?Items.diamond_sword:col==1?Items.bow:Items.golden_apple);mc.getRenderItem().renderItemAndEffectIntoGUI(stack,x+8+col*18,y+142);}
        }finally{RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.color(1,1,1,1);}
    }
}
