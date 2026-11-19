package dev.vibe.inventory;
import dev.vibe.features.FeatureModule;
import dev.vibe.module.impl.client.QolModule;
import dev.vibe.module.impl.world.InventoryManagerModule;
import dev.vibe.feedback.ModuleFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
/** Adjust's two-second, same-slot confirmation, before vanilla mutates the inventory. */
public final class DropConfirmation {
    private static Object player,world,container;private static int pending=-1;private static long expires;private static ItemStack expected;
    private DropConfirmation(){}
    public static int hotbarSlot(){
        Minecraft mc=Minecraft.getMinecraft();long now=System.nanoTime()/1000000;
        if(!enabled()||mc.thePlayer!=player||mc.theWorld!=world||now>=expires||expected==null)return -1;
        int slot=container==mc.thePlayer.inventory?pending:container==mc.thePlayer.inventoryContainer&&pending>=36&&pending<=44?pending-36:-1;
        if(slot<0||slot>8)return -1;ItemStack stack=mc.thePlayer.inventory.mainInventory[slot];
        return ItemStack.areItemsEqual(expected,stack)&&ItemStack.areItemStackTagsEqual(expected,stack)?slot:-1;
    }
    /** Adjust's two-second slot cue, shared by vanilla and the replacement hotbar. */
    public static void drawSlot(int x,int y,int size){
        long now=System.nanoTime()/1000000;float remaining=Math.max(0,Math.min(1,(expires-now)/2000F)),intro=Math.max(0,Math.min(1,(now-(expires-2000))/110F));
        dev.vibe.module.impl.client.HudModule hud=FeatureModule.module(dev.vibe.module.impl.client.HudModule.class);int color=hud==null?0xFF435AD1:hud.getAccent().getArgb();
        try(dev.vibe.ui.effect.EffectState ignored=new dev.vibe.ui.effect.EffectState()){
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            net.minecraft.client.gui.Gui.drawRect(x,y+Math.round(size*(1-remaining)),x+size,y+size,Math.round(120*intro)<<24|color&0xFFFFFF);
        }
    }
    public static boolean enabled(){QolModule q=FeatureModule.module(QolModule.class);return q!=null&&q.isEnabled()&&q.dropConfirm.isEnabled();}
    public static boolean protects(ItemStack stack){return enabled()&&InventoryManagerModule.shouldKeep(stack);}
    public static boolean allowDrop(Object entity,boolean all){
        Minecraft mc=Minecraft.getMinecraft();if(entity!=mc.thePlayer||mc.thePlayer==null)return true;
        return allow(mc.thePlayer,mc.thePlayer.inventory,mc.thePlayer.inventory.currentItem,mc.thePlayer.getHeldItem(),all);
    }
    public static boolean allowClick(int window,int slot,int button,int mode,Object entity){
        if(mode!=4||!(entity instanceof EntityPlayer)||entity!=Minecraft.getMinecraft().thePlayer)return true;
        EntityPlayer p=(EntityPlayer)entity;
        if(p.openContainer==null||p.openContainer.windowId!=window||slot<0||slot>=p.openContainer.inventorySlots.size())return true;
        return allow(p,p.openContainer,slot,p.openContainer.getSlot(slot).getStack(),button==1);
    }
    private static boolean allow(EntityPlayer p,Object inventory,int slot,ItemStack stack,boolean all){
        long now=System.nanoTime()/1000000;
        if(player!=p||world!=p.worldObj||now>=expires||!enabled()){pending=-1;expected=null;}
        player=p;world=p.worldObj;
        if(!protects(stack))return true;
        boolean confirmed=container==inventory&&pending==slot&&expected!=null&&ItemStack.areItemsEqual(expected,stack)&&ItemStack.areItemStackTagsEqual(expected,stack);
        if(confirmed&&(all||stack.stackSize<2)){pending=-1;expected=null;return true;}
        pending=slot;container=inventory;expected=stack.copy();expires=now+2000;
        if(!confirmed&&dev.vibe.Vibe.getInstance()!=null&&dev.vibe.Vibe.getInstance().getFeedback()!=null)ModuleFeedback.message("drop-confirm","Drop confirm","Press drop again within 2 seconds to drop "+stack.getDisplayName()+".");
        return confirmed;
    }
}
