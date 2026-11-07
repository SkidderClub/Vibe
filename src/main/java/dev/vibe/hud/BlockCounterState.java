package dev.vibe.hud;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.*;

/** Counts only the hotbar; the depletion baseline belongs to this visible session. */
final class BlockCounterState {
    private int baseline;
    private boolean shown;
    private String mode="";
    static int count(InventoryPlayer inventory,int selected,boolean total){
        if(inventory==null)return 0;int count=0;
        for(int slot=0;slot<9;slot++)if(total||slot==selected){ItemStack stack=inventory.mainInventory[slot];if(block(stack))count+=stack.stackSize;}
        return count;
    }
    static boolean block(ItemStack stack){return stack!=null&&stack.stackSize>0&&stack.getItem() instanceof ItemBlock;}
    float fraction(int count,boolean visible,String mode){
        if(!visible){shown=false;return 0;}
        if(!shown||!this.mode.equals(mode))baseline=Math.max(1,count);
        baseline=Math.max(baseline,count);shown=true;this.mode=mode;
        return Math.max(0,Math.min(1,count/(float)baseline));
    }
    void reset(){shown=false;baseline=0;}
    static boolean allowed(boolean whileScaffold,boolean whileEagle,boolean scaffold,boolean eagle){return !whileScaffold&&!whileEagle||whileScaffold&&scaffold||whileEagle&&eagle;}
}
