package dev.vibe.inventory;

import dev.vibe.module.Module;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Shared ownership, vanilla slot synchronization and exception-safe client logic scopes. */
public final class HotbarSwitches {
    public static final int SCAFFOLD=100, BUFF=90, MURDER=80, PROJECTILE=70, CHATTER=60, BED=50, PICKEN=40, TOOL=30, AFK=10;
    private static final List<Lease> requests = new ArrayList<>();
    private static EntityPlayerSP player;
    private static World world;
    private static Minecraft minecraft;
    private static int baseline, expected, depth, inputScope=-1;
    private static long sequence;
    private static Highlight flash;
    private static Object flashOwner;
    private static long flashUntil;
    private static int flashPriority;
    private HotbarSwitches() { }

    private static boolean context() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc==null||mc.thePlayer==null||mc.thePlayer.inventory==null||mc.theWorld==null||mc.thePlayer.worldObj!=mc.theWorld){invalidate();return false;}
        if(minecraft!=mc||player!=mc.thePlayer||world!=mc.theWorld){
            invalidate();minecraft=mc;player=mc.thePlayer;world=mc.theWorld;baseline=expected=player.inventory.currentItem;
        }
        if(player.inventory.currentItem!=expected){
            invalidateRequests();baseline=expected=player.inventory.currentItem;flash=null;
        }
        boolean removed=false;
        for(Iterator<Lease> it=requests.iterator();it.hasNext();){Lease lease=it.next();
            if(lease.closed){it.remove();removed=true;}
        }
        if(removed)applyLocal();
        return true;
    }
    private static void invalidateRequests(){for(Lease lease:requests)lease.closed=true;requests.clear();}
    private static void invalidate(){
        // A disconnect/respawn may occur inside a native action. Do not strand
        // that player's inventory on the temporary logic slot as scopes unwind.
        if(player!=null&&player.inventory!=null&&!requests.isEmpty()&&player.inventory.currentItem==expected)
            player.inventory.currentItem=baseline;
        invalidateRequests();player=null;world=null;minecraft=null;depth=0;inputScope=-1;flash=null;flashOwner=null;
    }
    private static Lease top(){Lease result=null;for(Lease lease:requests)if(!lease.closed&&(result==null||lease.priority>result.priority||lease.priority==result.priority&&lease.order>result.order))result=lease;return result;}
    private static int visible(Lease lease){return lease==null?baseline:lease.settings.silent()?lease.visible:lease.slot;}
    private static void applyLocal(){if(player==null)return;Lease lease=top();expected=depth>0&&lease!=null?lease.slot:visible(lease);player.inventory.currentItem=expected;}
    private static void sync(){if(minecraft!=null&&minecraft.playerController!=null)minecraft.playerController.updateController();}

    public static Lease acquire(Object owner,ItemSwitching settings,int slot,int priority){return acquire(owner,settings,slot,priority,true);}
    public static Lease acquire(Object owner,ItemSwitching settings,int slot,int priority,boolean syncNow){
        if(slot<0||slot>8||owner instanceof Module&&!((Module)owner).isEnabled()||!context())return null;
        for(Lease lease:requests)if(lease.owner==owner&&!lease.closed){lease.select(slot,syncNow);return lease;}
        if(requests.isEmpty())baseline=player.inventory.currentItem;
        Lease lease=new Lease(owner,settings,slot,priority,visible(top()));requests.add(lease);applyLocal();
        if(syncNow)try{sync();}catch(RuntimeException|Error failure){lease.close(false,true);throw failure;}
        return lease;
    }
    public static int selectedSlot(){if(!context())return 0;Lease lease=top();return lease==null?player.inventory.currentItem:lease.slot;}
    public static int visibleSlot(){return context()?visible(top()):0;}
    public static int serverSlotHook(int vanilla){if(!context())return vanilla;Lease lease=top();return lease==null?vanilla:lease.slot;}
    public static ItemStack stack(){return context()?player.inventory.mainInventory[selectedSlot()]:null;}
    public static ItemStack actionStack(ItemStack vanilla){return context()&&top()!=null?stack():vanilla;}
    public static Lease owned(Object owner){if(!context())return null;for(Lease lease:requests)if(lease.owner==owner&&!lease.closed)return lease;return null;}
    public static boolean busy(Object owner,int minimumPriority){if(!context())return false;Lease lease=top();return lease!=null&&lease.owner!=owner&&lease.priority>=minimumPriority;}
    public static boolean hasAction(){return busy(null,CHATTER);}
    public static void releaseOwner(Object owner){if(!context())return;for(Lease lease:new ArrayList<>(requests))if(lease.owner==owner)lease.close(false,true);if(flashOwner==owner)flash=null;}
    public static void reset(){if(!context())return;invalidateRequests();depth=0;inputScope=-1;flash=null;applyLocal();}

    /** Only native input/player logic sees the server stack; rendering sees the visible selection. */
    public static int beginActionHook(){if(!context())return -1;int token=++depth;applyLocal();return token;}
    public static void endActionHook(int token){if(token<1||!context()||token>depth)return;
        if(player.inventory.currentItem!=expected){invalidateRequests();baseline=player.inventory.currentItem;flash=null;}
        depth=token-1;applyLocal();}
    public static void beginInputHook(){if(inputScope<0)inputScope=beginActionHook();}
    public static void finishInputHook(){int token=inputScope;inputScope=-1;endActionHook(token);}
    public static int beginPlayerHook(Object entity){Minecraft mc=Minecraft.getMinecraft();return mc!=null&&entity==mc.thePlayer?beginActionHook():-1;}
    /** Pure strength calculations may inspect a candidate without changing packets or ownership. */
    public static <T> T evaluate(int slot,Supplier<T> calculation){Minecraft mc=Minecraft.getMinecraft();int old=mc.thePlayer.inventory.currentItem;
        try{mc.thePlayer.inventory.currentItem=slot;return calculation.get();}finally{mc.thePlayer.inventory.currentItem=old;}}
    /** Compatibility for APIs that explicitly request a normal visible selection. */
    public static void selectBasic(int slot){if(slot<0||slot>8||!context())return;invalidateRequests();baseline=slot;applyLocal();sync();}

    public static final class Lease implements AutoCloseable {
        private final Object owner;
        private final ItemSwitching settings;
        private final int priority,visible;
        private final long order=++sequence;
        private int slot;
        private boolean closed;
        private Lease(Object owner,ItemSwitching settings,int slot,int priority,int visible){this.owner=owner;this.settings=settings;this.slot=slot;this.priority=priority;this.visible=visible;}
        public boolean valid(){return context()&&!closed&&requests.contains(this);}
        public boolean active(){return valid()&&top()==this;}
        public int slot(){return slot;}
        public boolean silent(){return settings.silent();}
        public ItemStack stack(){return valid()?player.inventory.mainInventory[slot]:null;}
        public void select(int next){select(next,true);}
        public void select(int next,boolean syncNow){if(next<0||next>8||!valid())return;slot=next;applyLocal();if(syncNow)sync();}
        public void reselect(){if(valid())applyLocal();}
        @Override public void close(){close(true,true);}
        public void closePreservingSelection(){close(true,false);}
        public void close(boolean syncNow,boolean restore){
            if(!valid())return;boolean active=top()==this;
            if(active&&settings.highlight.isEnabled()){
                long now=System.nanoTime()/1000000L;
                if(flash==null||now>=flashUntil||priority>=flashPriority){flash=new Highlight(slot,settings.color.getArgb());flashOwner=owner;flashPriority=priority;flashUntil=now+240;}
            }
            if(!restore&&active)baseline=depth>0?visible(this):player.inventory.currentItem;
            closed=true;requests.remove(this);applyLocal();if(syncNow)sync();
        }
    }
    public static final class Highlight {
        public final int slot,color;
        private Highlight(int slot,int color){this.slot=slot;this.color=color;}
    }
    public static Highlight highlight(){
        if(!context())return null;Lease active=top();long now=System.nanoTime()/1000000L;
        if(flash!=null&&now<flashUntil&&(active==null||flashPriority>active.priority)){
            float fade=Math.min(1,(flashUntil-now)/140F);return new Highlight(flash.slot,Math.round((flash.color>>>24)*fade)<<24|flash.color&0xFFFFFF);
        }
        if(active!=null&&active.settings.highlight.isEnabled())return new Highlight(active.slot,active.settings.color.getArgb());
        return null;
    }
}
