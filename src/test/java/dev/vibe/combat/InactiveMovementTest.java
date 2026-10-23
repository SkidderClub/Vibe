package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.combat.VelocityModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.movement.NoSlowModule;
import dev.vibe.module.impl.world.ScaffoldModule;
import io.netty.buffer.Unpooled;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native keyboard polling, moveFlying and walking packet serialization with all producers disabled. */
public class InactiveMovementTest {
    @Test public void disabledRavenAndMovementHooksProduceExactlyVanillaPacketsAndPhysics() throws Exception {
        List<String> vanilla = capture(false), hooked = capture(true);
        assertEquals(vanilla.size(),hooked.size());
        for(int i=0;i<vanilla.size();i++)assertEquals("Native versus disabled hooks at step "+i,vanilla.get(i),hooked.get(i));
        Path output=Paths.get("build/movement-inactive-audit/packet-comparison.txt"); Files.createDirectories(output.getParent());
        Files.write(output,("Native and hooked traces match byte for byte across all four movement policies.\n"
                + "Keyboard combinations, sneak, sprint, jump, repeated turns and outgoing packet hooks.\n"
                + String.join("\n",hooked.subList(0,Math.min(20,hooked.size())))+"\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test public void packetScopeIsCompletelyInactiveWithoutARotationEvenAfterLargeTurns() throws Exception {
        try(Fixture f=new Fixture()) {
            for(float yaw:new float[]{0,270,315,-720.1F,.01337F,36000.125F,-36000.125F}) {
                f.player.rotationYaw=yaw; f.player.rotationPitch=-12.345F;
                f.fix.beginRotationTick(); f.fix.tick();
                MoveFixModule.beginPacketRotationHook(f.player);
                assertEquals("Inactive transport changed camera yaw "+yaw,Float.floatToRawIntBits(yaw),Float.floatToRawIntBits(f.player.rotationYaw));
                MoveFixModule.endPacketRotationHook(f.player);
            }
        }
    }

    @Test public void installingWrappersPreservesThePreviousInputUsedByVanillaSprintAndJumpChecks() throws Exception {
        try(Fixture f=new Fixture()) {
            f.player.movementInput.moveForward=.2F; f.player.movementInput.moveStrafe=-.3F;
            f.player.movementInput.jump=true; f.player.movementInput.sneak=true;
            f.fix.installInputHook();
            assertInput(f.player.movementInput);
            f.noSlow.installInputHook(); assertInput(f.player.movementInput);
        }
    }

    @Test public void disablingRavenProducersImmediatelyEndsTheirPendingRotateBack() throws Exception {
        try(Fixture f=new Fixture()) {
            for(Module producer:new Module[]{f.scaffold,f.aura}) {
                producer.setEnabled(true);
                f.fix.setFakeRotation(producer.getId(),120,60,"Silent");
                f.fix.clearFakeRotation(producer.getId());
                assertEquals("Silent",f.fix.getEffectiveCorrection());
                producer.setEnabled(false);
                assertEquals(0F,f.fix.getRotationYaw(),0);assertEquals(0F,f.fix.getRotationPitch(),0);
                assertEquals("Off",f.fix.getEffectiveCorrection());
                MoveFixModule.beginPacketRotationHook(f.player);
                assertEquals(0F,f.player.rotationYaw,0);MoveFixModule.endPacketRotationHook(f.player);
            }
            f.scaffold.setEnabled(true);f.fix.setFakeRotation("other",45,20,"Direct");f.scaffold.setEnabled(false);
            assertEquals("Disabling an unrelated module must preserve the actual owner",45F,f.fix.getRotationYaw(),0);
        }
    }

    @Test public void equivalentYawMustNotReplaceVanillaBecauseItsFloatTrigonometryCanDiffer() throws Exception {
        for(int i=-18000;i<18000;i++) {
            float raw=i/100F, equivalent=raw+360F;
            float rawCos=MathHelper.cos(raw*.017453292F), equivalentCos=MathHelper.cos(equivalent*.017453292F);
            float rawSin=MathHelper.sin(raw*.017453292F), equivalentSin=MathHelper.sin(equivalent*.017453292F);
            if(Float.floatToRawIntBits(rawCos)!=Float.floatToRawIntBits(equivalentCos)
                    || Float.floatToRawIntBits(rawSin)!=Float.floatToRawIntBits(equivalentSin)) {
                Path output=Paths.get("build/movement-inactive-audit/yaw-rounding.txt"); Files.createDirectories(output.getParent());
                Files.write(output,Arrays.asList("Native MathHelper float lookup example:",raw+" degrees: sin="+rawSin+", cos="+rawCos,
                        equivalent+" degrees: sin="+equivalentSin+", cos="+equivalentCos),StandardCharsets.UTF_8);
                return;
            }
        }
        fail("Expected a float lookup difference after adding an equivalent full turn");
    }

    @Test public void disablingAuraDoesNotReleaseManualUseInputThatAutoblockNeverOwned() throws Exception {
        try(Fixture f=new Fixture()) {
            f.aura.setEnabled(true);
            KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindUseItem.getKeyCode(),true);
            f.aura.setEnabled(false);
            assertTrue("Raven reset must not clear an unowned physical use key",f.mc.gameSettings.keyBindUseItem.isKeyDown());
        }
    }

    private static void assertInput(MovementInput input) {
        assertEquals(.2F,input.moveForward,0); assertEquals(-.3F,input.moveStrafe,0);
        assertTrue(input.jump); assertTrue(input.sneak);
    }

    private List<String> capture(boolean hooks) throws Exception {
        try(Fixture f=new Fixture()) {
            f.queue.hooks=hooks;
            List<String> trace=new ArrayList<String>();
            for(String policy:f.fix.getCorrectMovement().getModes()) {
                f.fix.getCorrectMovement().setValue(policy);
                for(int tick=0;tick<256;tick++) {
                    f.player.rotationYaw=(tick%7==0?-1:1)*(tick*123.01337F+270);
                    f.player.rotationPitch=(tick%120)-60.125F;
                    f.player.sprinting=(tick&1)==0;
                    KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindForward.getKeyCode(),(tick&1)!=0);
                    KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindBack.getKeyCode(),(tick&2)!=0);
                    KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindLeft.getKeyCode(),(tick&4)!=0);
                    KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindRight.getKeyCode(),(tick&8)!=0);
                    KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindSneak.getKeyCode(),(tick&16)!=0);
                    KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindJump.getKeyCode(),(tick&32)!=0);
                    if(hooks) {
                        f.fix.beginRotationTick(); f.scaffold.tickStart(); f.aura.tickStart(); f.fix.tick();
                        f.fix.installInputHook(); f.noSlow.installInputHook();
                        ScaffoldModule.hypixelUpdateHook(); assertFalse(VelocityModule.freezeUpdateHook(f.player));
                        assertFalse(KillAuraModule.hypixelUseHook()); assertFalse(KillAuraModule.hypixelRightClickHook());
                    }
                    f.player.movementInput.updatePlayerMoveState();
                    MovementInput input=f.player.movementInput;
                    if(hooks) {
                        assertEquals(Float.floatToRawIntBits(f.player.rotationYaw),Float.floatToRawIntBits(MoveFixModule.movementYawHook(f.player,f.player.rotationYaw)));
                        assertEquals(.42F,ScaffoldModule.jumpMotionHook(.42F,f.player),0);
                        f.scaffold.beforeWalkingUpdate(f.player);
                    }
                    f.player.motionX=f.player.motionZ=0;
                    f.player.moveFlying(input.moveStrafe*.98F,input.moveForward*.98F,.02F);
                    f.player.posX+=f.player.motionX; f.player.posZ+=f.player.motionZ;
                    f.player.setEntityBoundingBox(new AxisAlignedBB(f.player.posX-.3,64,f.player.posZ-.3,f.player.posX+.3,65.8,f.player.posZ+.3));
                    if(hooks)MoveFixModule.beginPacketRotationHook(f.player);
                    f.walk.invoke(f.player);
                    if(hooks) {MoveFixModule.endPacketRotationHook(f.player);ScaffoldModule.finishInputHook();f.scaffold.tickEnd();}
                    trace.add(policy+":"+tick+":"+Long.toHexString(Double.doubleToRawLongBits(f.player.posX))+":"+Long.toHexString(Double.doubleToRawLongBits(f.player.posZ))
                            +":"+input.moveForward+":"+input.moveStrafe+":"+input.jump+":"+input.sneak+":"+String.join(",",f.queue.packets));
                    f.queue.packets.clear();
                }
            }
            return trace;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Minecraft previousMc=Minecraft.getMinecraft(); final Vibe previousVibe=Vibe.getInstance();
        final Minecraft mc; final Player player; final Queue queue; final MoveFixModule fix;
        final ScaffoldModule scaffold; final KillAuraModule aura; final NoSlowModule noSlow; final Method walk;
        Fixture()throws Exception {
            dev.vibe.camera.DroneCollisionsTest.registerBlocks();
            mc=allocate(Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);mc.gameSettings=new GameSettings();
            mc.theWorld=allocate(WorldClient.class);player=allocate(Player.class);mc.thePlayer=player;
            player.worldObj=mc.theWorld;player.inventory=new InventoryPlayer(player);player.capabilities=new PlayerCapabilities();player.setEntityId(1);
            player.posY=64;player.onGround=true;player.movementInput=new MovementInputFromOptions(mc.gameSettings);
            player.setEntityBoundingBox(new AxisAlignedBB(-.3,64,-.3,.3,65.8,.3));
            queue=allocate(Queue.class);queue.packets=new ArrayList<String>();
            set(EntityPlayerSP.class,player,"mc",mc);set(EntityPlayerSP.class,player,"sendQueue",queue);set(Minecraft.class,mc,"renderViewEntity",player);
            Vibe vibe=new Vibe();set(Vibe.class,null,"instance",vibe);ModuleManager manager=allocate(ModuleManager.class);
            set(ModuleManager.class,manager,"modules",new ArrayList<Module>());set(Vibe.class,vibe,"moduleManager",manager);
            fix=new MoveFixModule();scaffold=new ScaffoldModule();aura=new KillAuraModule();noSlow=new NoSlowModule();
            manager.registerDynamic(fix);manager.registerDynamic(scaffold);manager.registerDynamic(aura);manager.registerDynamic(noSlow);manager.registerDynamic(new VelocityModule());
            walk=EntityPlayerSP.class.getDeclaredMethod("onUpdateWalkingPlayer");walk.setAccessible(true);
        }
        @Override public void close()throws Exception {
            KeyBinding.unPressAllKeys();set(Minecraft.class,null,"theMinecraft",previousMc);set(Vibe.class,null,"instance",previousVibe);
        }
    }
    public static final class Player extends EntityPlayerSP {
        boolean sprinting;
        private Player(){super(null,null,null,null);}
        @Override public boolean isSprinting(){return sprinting;}
        @Override public void setSprinting(boolean value){sprinting=value;}
        @Override public boolean isSneaking(){return movementInput.sneak;}
    }
    public static final class Queue extends NetHandlerPlayClient {
        List<String> packets; boolean hooks;
        private Queue(){super(null,null,null,null);}
        @Override public void addToSendQueue(Packet packet) {
            if(hooks) {
                assertTrue(ScaffoldModule.hypixelPacketHook(packet));
                Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class).applyToOutgoing(packet);
            }
            PacketBuffer buffer=new PacketBuffer(Unpooled.buffer());
            try {
                packet.writePacketData(buffer);StringBuilder bytes=new StringBuilder();
                while(buffer.isReadable())bytes.append(String.format(Locale.ROOT,"%02x",buffer.readUnsignedByte()));
                packets.add(packet.getClass().getSimpleName()+":"+bytes);
            }catch(Exception e){throw new AssertionError(e);}finally{buffer.release();}
        }
    }
    private static <T>T allocate(Class<T> type)throws Exception {return dev.vibe.path.PathTestFixture.allocate(type);}
    private static void set(Class<?> type,Object object,String name,Object value)throws Exception {dev.vibe.path.PathTestFixture.set(type,object,name,value);}
}
