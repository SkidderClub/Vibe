package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.input.VanillaClicks;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.combat.AutoClickerModule;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.world.BedAuraModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.Setting;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executes Minecraft's real click/held-dig methods, controller and SP swing/walking packet writers.
 * Only the transport, particles/audio and deterministic block hardness are fixtures. */
public class BedAuraPacketsTest {
    @Test public void initialHoldContinuationAndFinishMatchVanillaPacketsByteForByte() throws Exception {
        List<String> manual = capture(false, 5, false), automatic = capture(true, 5, false);
        assertEquals(manual, automatic);
        assertTrue(automatic.stream().anyMatch(s -> s.contains("STOP_DESTROY_BLOCK")));
        Path output = Paths.get("build/bedaura-input/packet-traces.txt"); Files.createDirectories(output.getParent());
        Files.write(output, ("Vanilla held attack:\n" + String.join("\n", manual)
                + "\n\nBedAura through vanilla input:\n" + String.join("\n", automatic) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test public void releaseUsesVanillasAbortAndDoesNotSendAnythingFromDisable() throws Exception {
        assertEquals(captureRelease(false), captureRelease(true));
    }

    @Test public void retargetingAHeldDigMatchesVanillaWithoutAnotherPress() throws Exception {
        List<String> manual = captureRetarget(false), automatic = captureRetarget(true);
        assertEquals(manual, automatic);
        assertEquals(3, automatic.stream().filter(s -> s.startsWith("C0A")).count());
        assertTrue(automatic.stream().anyMatch(s -> s.contains("ABORT_DESTROY_BLOCK")));
    }

    @Test public void vanillaMissCooldownDoesNotCauseAnAutomaticSwing() throws Exception {
        assertEquals(capture(false, 8, true), capture(true, 8, true));
        try (Fixture f = new Fixture()) {
            set(Minecraft.class,f.mc,"leftClickCounter",5);
            f.step(true,false); f.step(true,false);
            assertFalse(f.queue.packets.stream().anyMatch(p -> p instanceof C0APacketAnimation || p instanceof C07PacketPlayerDigging));
        }
    }

    @Test public void miningUsesOnePreMovementRotationAndAddsNoPostMovementPackets() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.rotationYaw = 90;
            f.bedBlock.damage = .05F;
            for (int i=0;i<6;i++) f.step(true,true);
            assertTrue(f.queue.packets.stream().anyMatch(p -> p instanceof C0APacketAnimation));
            assertTrue(f.controller.getIsHittingBlock());
            assertFalse(f.mc.gameSettings.keyBindAttack.isKeyDown());
            assertFalse(f.mc.gameSettings.keyBindAttack.isPressed());
        }
    }

    @Test public void toolsSynchronizeBeforeTheDigAndRestoreOnTheNextInputTick() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.inventory.mainInventory[3] = new ItemStack(Items.iron_axe);
            f.step(true,false);
            int slot=-1, start=-1;
            for(int i=0;i<f.queue.packets.size();i++) {
                Packet<?> p=f.queue.packets.get(i);
                if(p instanceof C09PacketHeldItemChange && ((C09PacketHeldItemChange)p).getSlotId()==3)slot=i;
                if(p instanceof C07PacketPlayerDigging && ((C07PacketPlayerDigging)p).getStatus()==C07PacketPlayerDigging.Action.START_DESTROY_BLOCK)start=i;
            }
            assertTrue("The selected tool must reach the server before START",slot>=0&&start>slot);
            int afterMovement=f.queue.packets.size(); f.aura.setEnabled(false);
            assertEquals("Disable must not emit post-movement C09/C07/C0A",afterMovement,f.queue.packets.size());
            assertEquals(0,f.player.inventory.currentItem);
            f.step(true,false);
            assertTrue(f.queue.packets.subList(afterMovement,f.queue.packets.size()).stream()
                    .anyMatch(p -> p instanceof C09PacketHeldItemChange && ((C09PacketHeldItemChange)p).getSlotId()==0));
        }
    }

    @Test public void guiUseItemAndRotationTakeoverDoNotInjectAnAttack() throws Exception {
        for(int state=0;state<3;state++)try(Fixture f=new Fixture()) {
            if(state==0)f.mc.currentScreen=new net.minecraft.client.gui.GuiChat();
            if(state==1)f.player.using=true;
            f.start(true);
            if(state==2)f.fix.setFakeRotation("other",45,0);
            f.input(true); f.walk(false);
            assertFalse(f.queue.packets.stream().anyMatch(p -> p instanceof C0APacketAnimation || p instanceof C07PacketPlayerDigging));
        }
    }

    @Test public void otherClickProducersCannotAddAttackEdgesToAnOwnedDig() throws Exception {
        try(Fixture f=new Fixture()) {
            AutoClickerModule clicks=new AutoClickerModule(); f.modules.registerDynamic(clicks);
            for(Setting<?> setting:clicks.getSettings())if(setting.getRawName().equals("Hold To Click"))((BooleanSetting)setting).setEnabled(false);
            set(Module.class,clicks,"enabled",true);
            KillAuraModule aura=new KillAuraModule(); f.modules.registerDynamic(aura); set(Module.class,aura,"enabled",true);
            f.step(true,false); f.step(true,false);
            assertEquals("One press swing plus two held-damage swings",3,
                    f.queue.packets.stream().filter(p -> p instanceof C0APacketAnimation).count());
        }
    }

    private static List<String> capture(boolean automated,int ticks,boolean cooldown)throws Exception {
        try(Fixture f=new Fixture()) {
            if(cooldown)set(Minecraft.class,f.mc,"leftClickCounter",5);
            for(int i=0;i<ticks;i++)f.step(automated,false);
            return f.queue.trace(false);
        }
    }
    private static List<String> captureRelease(boolean automated)throws Exception {
        try(Fixture f=new Fixture()) {
            f.step(automated,false);
            int before=f.queue.packets.size();
            if(automated)f.aura.setEnabled(false);else KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindAttack.getKeyCode(),false);
            assertEquals(before,f.queue.packets.size());
            f.manualReleased=true;f.step(automated,false);
            return f.queue.trace(true);
        }
    }
    private static List<String> captureRetarget(boolean automated)throws Exception {
        try(Fixture f=new Fixture()) {
            f.step(automated,false);
            f.world.blocks.remove(f.bed);f.bed=new BlockPos(1,0,3);f.world.blocks.put(f.bed,f.bedBlock.getDefaultState());
            set(BedAuraModule.class,f.aura,"nextScan",0L);
            f.step(automated,false);
            return f.queue.trace(true);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Minecraft previousMc=Minecraft.getMinecraft(); final Vibe previousVibe=Vibe.getInstance();
        final Minecraft mc; final Player player; final TestWorld world; final FixedBed bedBlock=new FixedBed();
        final Queue queue; final PlayerControllerMP controller; final ModuleManager modules;
        final BedAuraModule aura; final MoveFixModule fix;
        BlockPos bed=new BlockPos(0,0,3);
        boolean firstManual=true,manualReleased;
        Fixture()throws Exception {
            dev.vibe.camera.DroneCollisionsTest.registerBlocks();
            mc=allocate(Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
            mc.gameSettings=new GameSettings();mc.inGameHasFocus=true;
            queue=allocate(Queue.class);queue.packets=new ArrayList<Packet<?>>();queue.network=allocate(NoopNetwork.class);
            world=allocate(TestWorld.class);world.blocks=new HashMap<BlockPos,IBlockState>();world.border=new WorldBorder();
            world.blocks.put(bed,bedBlock.getDefaultState());mc.theWorld=world;
            player=allocate(Player.class);mc.thePlayer=player;player.worldObj=world;
            player.inventory=new InventoryPlayer(player);player.capabilities=new PlayerCapabilities();player.onGround=true;
            player.movementInput=new MovementInput();player.movementInput.moveForward=1;
            player.setEntityBoundingBox(new AxisAlignedBB(.2,0,.2,.8,1.8,.8));player.posX=player.posZ=.5;
            set(EntityLivingBase.class,player,"activePotionsMap",new HashMap<Integer,PotionEffect>());
            set(EntityPlayerSP.class,player,"sendQueue",queue);set(EntityPlayerSP.class,player,"mc",mc);
            set(Minecraft.class,mc,"renderViewEntity",player);
            controller=new PlayerControllerMP(mc,queue);mc.playerController=controller;
            mc.effectRenderer=allocate(NoopEffects.class);set(Minecraft.class,mc,"mcSoundHandler",allocate(NoopSound.class));
            Vibe vibe=new Vibe();set(Vibe.class,null,"instance",vibe);modules=allocate(ModuleManager.class);
            set(ModuleManager.class,modules,"modules",new ArrayList<Module>());set(Vibe.class,vibe,"moduleManager",modules);
            aura=new BedAuraModule();fix=new MoveFixModule();modules.registerDynamic(aura);modules.registerDynamic(fix);
            set(Module.class,aura,"enabled",true);fix.getCorrectMovement().setValue("Silent");
            for(Setting<?> setting:aura.getSettings())if(setting.getRawName().equals("FOV"))((dev.vibe.setting.NumberSetting)setting).setValue(360D);
            aimManual();
        }
        void start(boolean automatic)throws Exception {
            int cooldown=(Integer)get(Minecraft.class,mc,"leftClickCounter");if(cooldown>0)set(Minecraft.class,mc,"leftClickCounter",cooldown-1);
            fix.beginRotationTick();fix.tick();fix.installInputHook();
            if(automatic)aura.tick();
            // Minecraft.runTick synchronizes its hotbar before dispatching attack input.
            controller.updateController();
            if(!automatic) {
                aimManual();
                KeyBinding attack=mc.gameSettings.keyBindAttack;
                KeyBinding.setKeyBindState(attack.getKeyCode(),!manualReleased);
                if(firstManual&&!manualReleased)KeyBinding.onTick(attack.getKeyCode());
                firstManual=false;
            }
        }
        void input(boolean automatic)throws Exception {
            if(automatic){KillAuraModule.prepareInputHook();BedAuraModule.prepareInputHook();}
            // Invoke the original private Minecraft handlers, not copies of their packet logic.
            Method click=Minecraft.class.getDeclaredMethod("clickMouse");click.setAccessible(true);
            while(mc.gameSettings.keyBindAttack.isPressed())click.invoke(mc);
            Method held=Minecraft.class.getDeclaredMethod("sendClickBlockToController",boolean.class);held.setAccessible(true);
            held.invoke(mc,mc.currentScreen==null&&mc.inGameHasFocus&&mc.gameSettings.keyBindAttack.isKeyDown());
            if(automatic)BedAuraModule.finishInputHook();
        }
        void walk(boolean moving)throws Exception {
            float yaw=MoveFixModule.movementYawHook(player,player.rotationYaw);
            if(moving){player.movementInput.updatePlayerMoveState();player.posX+=.12;}
            MoveFixModule.beginPacketRotationHook(player);
            if(moving)assertEquals("The yaw used by physics must reach C03 unchanged",yaw,player.rotationYaw,0);
            Method walking=EntityPlayerSP.class.getDeclaredMethod("onUpdateWalkingPlayer");walking.setAccessible(true);walking.invoke(player);
            int packets=queue.packets.size();assertTrue(queue.packets.get(packets-1)instanceof C03PacketPlayer);
            MoveFixModule.endPacketRotationHook(player);
            assertEquals("No C0A/C07/C09 may be emitted after movement",packets,queue.packets.size());
        }
        void step(boolean automatic,boolean moving)throws Exception { start(automatic);input(automatic);walk(moving); }
        void aimManual() {
            AxisAlignedBB box=bedBlock.getSelectedBoundingBox(world,bed);
            Vec3 center=new Vec3((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
            MovingObjectPosition hit=world.rayTraceBlocks(player.getPositionEyes(1),center,false,true,false);
            if(hit==null) {
                mc.objectMouseOver=new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS,center,EnumFacing.UP,bed);
                return;
            }
            mc.objectMouseOver=hit;
            Vec3 delta=hit.hitVec.subtract(player.getPositionEyes(1));
            player.rotationYaw=(float)(Math.toDegrees(Math.atan2(delta.zCoord,delta.xCoord))-90);
            player.rotationPitch=(float)-Math.toDegrees(Math.atan2(delta.yCoord,Math.hypot(delta.xCoord,delta.zCoord)));
        }
        @Override public void close()throws Exception {
            VanillaClicks.discardPresses(mc.gameSettings.keyBindAttack);KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(),false);
            set(Minecraft.class,null,"theMinecraft",previousMc);set(Vibe.class,null,"instance",previousVibe);
        }
    }

    public static final class Player extends EntityPlayerSP {
        boolean using;
        private Player(){super(null,null,null,null);}
        @Override public Vec3 getPositionEyes(float partial){return new Vec3(posX,posY+1.62,posZ);}
        @Override public boolean isUsingItem(){return using;}
        @Override public boolean isSprinting(){return false;}
        @Override public boolean isSneaking(){return false;}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isEntityAlive(){return true;}
    }
    public static final class FixedBed extends BlockBed {
        float damage=.25F;
        @Override public Material getMaterial(){return Material.wood;}
        @Override public float getPlayerRelativeBlockHardness(EntityPlayer player,World world,BlockPos pos){return damage;}
        @Override public void onBlockClicked(World world,BlockPos pos,EntityPlayer player){ }
        @Override public boolean removedByPlayer(World world,BlockPos pos,EntityPlayer player,boolean harvest){return world.setBlockToAir(pos);}
        @Override public void onBlockDestroyedByPlayer(World world,BlockPos pos,IBlockState state){ }
    }
    public static final class TestWorld extends WorldClient {
        Map<BlockPos,IBlockState> blocks;WorldBorder border;
        private TestWorld(){super(null,null,0,null,null);}
        @Override public IBlockState getBlockState(BlockPos pos){return blocks.getOrDefault(pos,Blocks.air.getDefaultState());}
        @Override public boolean isAirBlock(BlockPos pos){return getBlockState(pos).getBlock()==Blocks.air;}
        @Override public WorldBorder getWorldBorder(){return border;}
        @Override public void sendBlockBreakProgress(int id,BlockPos pos,int progress){ }
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags){blocks.put(pos,state);return true;}
        @Override public void playAuxSFX(int type,BlockPos pos,int data){ }
    }
    public static final class Queue extends NetHandlerPlayClient {
        List<Packet<?>> packets;NetworkManager network;
        private Queue(){super(null,null,null,null);}
        @Override public void addToSendQueue(Packet packet){packets.add(packet);}
        @Override public NetworkManager getNetworkManager(){return network;}
        List<String> trace(boolean actionsOnly)throws Exception {
            List<String> result=new ArrayList<String>();
            for(Packet<?> packet:packets) {
                if(actionsOnly&&packet instanceof C03PacketPlayer)continue;
                PacketBuffer buffer=new PacketBuffer(Unpooled.buffer());packet.writePacketData(buffer);
                StringBuilder hex=new StringBuilder();while(buffer.isReadable())hex.append(String.format(Locale.ROOT,"%02x",buffer.readUnsignedByte()));buffer.release();
                String label=packet.getClass().getSimpleName();
                if(packet instanceof C07PacketPlayerDigging)label+=" "+((C07PacketPlayerDigging)packet).getStatus();
                result.add(label+" "+hex);
            }
            return result;
        }
    }
    public static final class NoopNetwork extends NetworkManager {
        private NoopNetwork(){super(EnumPacketDirection.CLIENTBOUND);}
        @Override public boolean isChannelOpen(){return true;}
        @Override public void processReceivedPackets(){ }
    }
    public static final class NoopEffects extends EffectRenderer {
        private NoopEffects(){super(null,null);}
        @Override public void addBlockHitEffects(BlockPos pos,MovingObjectPosition hit){ }
    }
    public static final class NoopSound extends SoundHandler {
        private NoopSound(){super(null,null);}
        @Override public void playSound(ISound sound){ }
    }
    private static Object get(Class<?> type,Object object,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void set(Class<?> type,Object object,String name,Object value)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static <T>T allocate(Class<T> type)throws Exception{Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);return type.cast(unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type));}
}
