package dev.vibe.combat;

import com.mojang.authlib.GameProfile;
import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.combat.PickenSwitchModule;
import dev.vibe.module.impl.combat.PickenSwitchTest;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.stats.StatBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Actual transformed controller packets feed vanilla's real attack calculation. */
public class PickenSwitchPacketsTest {
    private Minecraft previous,mc;
    private Vibe oldVibe;
    private PickenSwitchModule module;
    private ClientPlayer client;
    private ServerPlayer attacker,victim;
    private Connection connection;
    private PickenSwitchHooksTest.Victim clientTarget;
    @BeforeClass public static void bootstrap()throws Exception{PickenSwitchTest.bootstrap();}
    @Before public void setup()throws Exception{
        previous=Minecraft.getMinecraft();oldVibe=Vibe.getInstance();mc=PathTestFixture.allocate(Minecraft.class);PathTestFixture.set(Minecraft.class,null,"theMinecraft",mc);
        mc.theWorld=PathTestFixture.allocate(WorldClient.class);
        client=PathTestFixture.allocate(ClientPlayer.class);client.worldObj=mc.theWorld;client.inventory=new InventoryPlayer(client);mc.thePlayer=client;
        ItemStack weapon=new ItemStack(net.minecraft.init.Items.diamond_sword);weapon.addEnchantment(Enchantment.sharpness,5);
        ItemStack utility=new ItemStack(new Item());utility.addEnchantment(Enchantment.knockback,2);utility.addEnchantment(Enchantment.fireAspect,2);utility.addEnchantment(Enchantment.sharpness,2);
        client.inventory.mainInventory[0]=weapon;client.inventory.mainInventory[4]=utility;
        ServerWorld world=new ServerWorld();attacker=new ServerPlayer(world);victim=new ServerPlayer(world);
        attacker.inventory.mainInventory[0]=weapon.copy();attacker.inventory.mainInventory[4]=utility.copy();
        attacker.getAttributeMap().applyAttributeModifiers(weapon.getAttributeModifiers());
        connection=PathTestFixture.allocate(Connection.class);connection.trace=new ArrayList<>();connection.attacker=attacker;connection.victim=victim;
        mc.playerController=SilentMurderPacketsTest.nativeController(mc,connection);
        clientTarget=PathTestFixture.allocate(PickenSwitchHooksTest.Victim.class);clientTarget.worldObj=mc.theWorld;clientTarget.setEntityId(42);
        module=new PickenSwitchModule();PathTestFixture.set(Module.class,module,"enabled",true);
        ModuleManager modules=PathTestFixture.allocate(ModuleManager.class);PathTestFixture.set(ModuleManager.class,modules,"modules",new ArrayList<Module>(Collections.singletonList(module)));
        Vibe vibe=new Vibe();PathTestFixture.set(Vibe.class,vibe,"moduleManager",modules);PathTestFixture.set(Vibe.class,null,"instance",vibe);
        module.tick();module.tick();module.tick();
    }
    @After public void cleanup()throws Exception{PathTestFixture.set(Minecraft.class,null,"theMinecraft",previous);PathTestFixture.set(Vibe.class,null,"instance",oldVibe);}
    @Test public void realPacketsCombineBaseDamageWithUtilityEnchantmentsAndReturnImmediately(){
        for(String mode:new String[]{"Basic","Silent"}){
            for(Setting<?> setting:module.getSettings())if(setting.getRawName().equals("Item Switching"))((ModeSetting)setting).setValue(mode);
            connection.trace.clear();victim.damage=0;victim.motionZ=0;victim.fireSeconds=0;
            mc.playerController.attackEntity(client,clientTarget);
            assertEquals(Arrays.asList("C09:4","C02:4","C09:0"),connection.trace);
            assertEquals(10.5F,victim.damage,0);assertEquals(8,victim.fireSeconds);assertEquals(1,victim.motionZ,.00001);
            assertEquals(0,attacker.inventory.currentItem);assertEquals(0,client.inventory.currentItem);assertFalse(PickenSwitchModule.ownsSlot());
            assertEquals(8,attacker.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue(),0);
            assertSame(client.inventory.mainInventory[4],client.predictionItem);
        }
        // After the transaction the real weapon's Sharpness, not the utility's, applies normally.
        module.setEnabled(false);victim.damage=0;attacker.attackTargetEntityWithCurrentItem(victim);assertEquals(14.25F,victim.damage,0);
    }
    @Test public void localPredictionFailureStillClosesTheServerSlotTransaction(){
        client.fail=true;
        try{mc.playerController.attackEntity(client,clientTarget);fail("Expected local prediction failure");}catch(IllegalStateException expected){assertEquals("prediction",expected.getMessage());}
        assertEquals(Arrays.asList("C09:4","C02:4","C09:0"),connection.trace);
        assertEquals(10.5F,victim.damage,0);assertEquals(0,attacker.inventory.currentItem);assertEquals(0,client.inventory.currentItem);assertFalse(PickenSwitchModule.ownsSlot());
    }
    public static final class ClientPlayer extends EntityPlayerSP {
        boolean fail;ItemStack predictionItem;
        private ClientPlayer(){super(null,null,null,null);}
        @Override public void attackTargetEntityWithCurrentItem(Entity target){predictionItem=getHeldItem();if(fail)throw new IllegalStateException("prediction");}
    }
    public static final class Connection extends NetHandlerPlayClient {
        ServerPlayer attacker,victim;List<String> trace;
        private Connection(){super(null,null,null,null);}
        @Override public void addToSendQueue(Packet packet){
            if(packet instanceof C09PacketHeldItemChange){attacker.inventory.currentItem=((C09PacketHeldItemChange)packet).getSlotId();trace.add("C09:"+attacker.inventory.currentItem);}
            else if(packet instanceof C02PacketUseEntity&&((C02PacketUseEntity)packet).getAction()==C02PacketUseEntity.Action.ATTACK){trace.add("C02:"+attacker.inventory.currentItem);attacker.attackTargetEntityWithCurrentItem(victim);}
        }
    }
    private static final class ServerPlayer extends EntityPlayer {
        float damage;int fireSeconds;
        ServerPlayer(World world){super(world,new GameProfile(UUID.randomUUID(),"Fixture"));}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean attackEntityFrom(DamageSource source,float damage){this.damage=damage;return true;}
        @Override public void setFire(int seconds){fireSeconds=seconds;}
        @Override public void addStat(StatBase stat,int amount){}
    }
    private static final class ServerWorld extends World {
        ServerWorld(){super(new SaveHandlerMP(),new WorldInfo(new WorldSettings(0,WorldSettings.GameType.SURVIVAL,false,false,WorldType.DEFAULT),"Picken vanilla hit"),new WorldProviderSurface(),new net.minecraft.profiler.Profiler(),true);provider.registerWorld(this);chunkProvider=createChunkProvider();}
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){return new net.minecraft.client.multiplayer.ChunkProviderClient(this);}
        @Override protected int getRenderDistanceChunks(){return 0;}
    }
}
