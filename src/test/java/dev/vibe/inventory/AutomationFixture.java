package dev.vibe.inventory;

import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.Packet;
import net.minecraft.util.*;

public final class AutomationFixture implements AutoCloseable {
    public final PathTestFixture base = new PathTestFixture();
    public final Minecraft mc;
    public final Player player;
    public final Queue queue;
    public AutomationFixture() throws Exception {
        mc = PathTestFixture.allocate(Client.class); mc.gameSettings = base.mc.gameSettings; mc.theWorld = base.world;
        PathTestFixture.set(Minecraft.class,null,"theMinecraft",mc);
        queue = PathTestFixture.allocate(Queue.class); queue.packets = new ArrayList<Packet<?>>();
        player = PathTestFixture.allocate(Player.class); player.worldObj = base.world;
        player.messages = new ArrayList<IChatComponent>(); player.droppedStacks = new ArrayList<net.minecraft.item.ItemStack>();
        player.inventory = new InventoryPlayer(player); player.capabilities = new PlayerCapabilities();
        player.posX = .5; player.posY = 64; player.posZ = .5; player.onGround = true;
        player.setEntityBoundingBox(new AxisAlignedBB(.2,64,.2,.8,65.8,.8));
        PathTestFixture.set(EntityPlayerSP.class, player, "mc", mc);
        PathTestFixture.set(EntityPlayerSP.class, player, "sendQueue", queue);
        DataWatcher watcher = new DataWatcher(player); watcher.addObject(0,(byte)0); watcher.addObject(6,20F);
        PathTestFixture.set(Entity.class,player,"dataWatcher",watcher);
        net.minecraft.entity.ai.attributes.ServersideAttributeMap attributes = new net.minecraft.entity.ai.attributes.ServersideAttributeMap();
        attributes.registerAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20);
        PathTestFixture.set(EntityLivingBase.class,player,"attributeMap",attributes);
        PathTestFixture.set(EntityLivingBase.class,player,"activePotionsMap",new HashMap<Integer,net.minecraft.potion.PotionEffect>());
        PathTestFixture.set(EntityPlayer.class,player,"foodStats",new FoodStats());
        player.inventoryContainer = new ContainerPlayer(player.inventory,true,player);
        player.openContainer = player.inventoryContainer; mc.thePlayer = player; mc.inGameHasFocus = true;
        mc.playerController = new PlayerControllerMP(mc,queue);
    }
    public void health(float hp) { player.getDataWatcher().updateObject(6,hp); player.isDead = hp <= 0; }
    @Override public void close() throws Exception { base.close(); }
    public static class Player extends EntityPlayerSP {
        public int respawns, closes;
        public List<IChatComponent> messages;
        public List<net.minecraft.item.ItemStack> droppedStacks;
        private Player(){super(null,null,null,null);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean isSpectator(){return false;}
        @Override public void respawnPlayer(){respawns++;}
        @Override public void closeScreen(){closes++; openContainer=inventoryContainer; Minecraft.getMinecraft().currentScreen=null;}
        @Override public void addStat(net.minecraft.stats.StatBase stat,int amount){ }
        @Override public void addChatMessage(IChatComponent message){ messages.add(message); }
        @Override public net.minecraft.entity.item.EntityItem dropPlayerItemWithRandomChoice(net.minecraft.item.ItemStack stack,boolean random){droppedStacks.add(stack.copy());return null;}
    }
    public static class Queue extends NetHandlerPlayClient {
        public List<Packet<?>> packets;
        private Queue(){super(null,null,null,null);}
        @Override public void addToSendQueue(Packet packet){packets.add(packet);}
    }
    public static class Client extends Minecraft {
        private Client(){super(null);}
        @Override public void displayGuiScreen(net.minecraft.client.gui.GuiScreen screen){currentScreen=screen;}
        @Override public boolean isGamePaused(){return false;}
    }
}
