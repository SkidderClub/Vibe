package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.*;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import dev.vibe.combat.CombatTimerAccess;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.*;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdditionalRewoundTest {
    @Test public void speedKeepsItsBoostWhileWalkingWithScaffold()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            dev.vibe.module.impl.world.ScaffoldModule scaffold=new dev.vibe.module.impl.world.ScaffoldModule();register(scaffold);scaffold.setEnabled(true);
            f.player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            ModeSetting mode=(ModeSetting)f.speed.getSettings().stream().filter(s->s.getRawName().equals("Mode")).findFirst().get();
            for(String name:mode.getModes()){
                mode.setValue(name);f.speed.setEnabled(true);f.player.motionX=0;f.player.motionY=0;f.player.motionZ=.1;f.player.onGround=true;int jumps=f.player.jumps;
                f.speed.tick();assertEquals(name,name.equals("Rewound"),f.speed.rewoundControlsJump());
                if(name.equals("Rewound")){
                    f.player.moveEntity(0,0,.1);assertEquals(name,.407,f.player.lastMove[1],1E-9);
                }else assertTrue(name+" must retain its jump/acceleration",f.player.jumps>jumps||f.player.motionY>0||Math.hypot(f.player.motionX,f.player.motionZ)>.1);
                f.speed.setEnabled(false);
            }
            scaffold.setEnabled(false);
        }
    }
    @Test public void scaffoldSpeedUsesCorrectedMovementRatherThanCameraYaw()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            dev.vibe.module.impl.world.ScaffoldModule scaffold=new dev.vibe.module.impl.world.ScaffoldModule();register(scaffold);scaffold.setEnabled(true);
            MoveFixModule fix=new MoveFixModule();register(fix);fix.setEnabled(true);
            f.player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            ModeSetting mode=(ModeSetting)f.speed.getSettings().stream().filter(s->s.getRawName().equals("Mode")).findFirst().get();
            ((BooleanSetting)f.speed.getSettings().stream().filter(s->s.getRawName().equals("Not On Void")).findFirst().get()).setEnabled(false);
            for(String name:new String[]{"Custom","GroundStrafe","AACYSpoof","AACHop3.3.13","Rewound"})
                for(float turn:new float[]{180,90,-90,137})for(float[] keys:new float[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1}}){
                    mode.setValue(name);f.speed.setEnabled(true);f.player.rotationYaw=37;f.player.onGround=f.player.isCollidedVertically=true;f.player.fallDistance=0;
                    fix.setFakeRotation(scaffold.getId(),37+turn,80,"Silent");
                    MovementInput input=new MovementInput();input.moveForward=keys[0];input.moveStrafe=keys[1];fix.correctInjectedInput(input);f.player.movementInput=input;
                    // These native fields can still contain the previous tick's input.
                    f.player.moveForward=f.player.moveStrafing=0;
                    double yaw=Math.toRadians(37),dx=keys[1]*Math.cos(yaw)-keys[0]*Math.sin(yaw),dz=keys[0]*Math.cos(yaw)+keys[1]*Math.sin(yaw),length=Math.hypot(dx,dz);dx/=length;dz/=length;
                    f.player.motionX=dx*.1;f.player.motionY=0;f.player.motionZ=dz*.1;
                    f.speed.tick();double x=f.player.motionX,z=f.player.motionZ;
                    if(name.equals("Rewound")){
                        Field setback=RewoundSpeed.class.getDeclaredField("lastSetback");setback.setAccessible(true);Field state=SpeedModule.class.getDeclaredField("rewound");state.setAccessible(true);setback.setLong(state.get(f.speed),System.nanoTime()/1000000L-1000);
                        f.player.moveEntity(x,0,z);x=f.player.lastMove[0];z=f.player.lastMove[2];
                    }
                    assertTrue(name+" must boost while Scaffold owns rotations",Math.hypot(x,z)>.1);
                    assertTrue(name+" steered away at turn "+turn,(x*dx+z*dz)/Math.hypot(x,z)>.92);
                    assertEquals("Silent rotations must preserve the camera",37,f.player.rotationYaw,0);
                    f.speed.setEnabled(false);
                }
            scaffold.setEnabled(false);fix.setEnabled(false);
        }
    }
    private static void register(Module module){Vibe.getInstance().getModuleManager().registerDynamic(module);}
    @Test public void nativeSprintJumpBoostFollowsScaffoldTravelAndLeavesOrdinaryJumpsAlone()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture(true,true)){
            dev.vibe.module.impl.world.ScaffoldModule scaffold=new dev.vibe.module.impl.world.ScaffoldModule();register(scaffold);
            MoveFixModule fix=new MoveFixModule();register(fix);fix.setEnabled(true);f.player.sprinting=true;
            for(boolean bridging:new boolean[]{false,true}){
                scaffold.setEnabled(bridging);
                for(float turn:new float[]{180,90,-90,137})for(float[] keys:new float[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1}}){
                    f.player.rotationYaw=37;fix.setFakeRotation("Scaffold",37+turn,80,"Silent");
                    MovementInput input=new MovementInput();input.moveForward=keys[0];input.moveStrafe=keys[1];fix.correctInjectedInput(input);f.player.movementInput=input;
                    f.player.motionX=.08;f.player.motionY=0;f.player.motionZ=.12;
                    AacMovementSupport.jump(f.player);
                    double dx=f.player.motionX-.08,dz=f.player.motionZ-.12;
                    float wanted=bridging?AacMovementSupport.directionYaw(f.player):MoveFixModule.movementYawHook(f.player,f.player.rotationYaw);
                    float radians=wanted*.017453292F;
                    assertEquals("Native sprint impulse X",-MathHelper.sin(radians)*.2F,dx,3E-8);
                    assertEquals("Native sprint impulse Z",MathHelper.cos(radians)*.2F,dz,3E-8);
                    assertEquals("Native jump height is unchanged",.41999998688697815,f.player.motionY,1E-8);
                    assertEquals("Silent camera is unchanged",37,f.player.rotationYaw,0);
                }
            }
            scaffold.setEnabled(false);fix.setEnabled(false);
        }
    }
    @Test public void jesusUsesTheSourceVectorAndYImpulseAndLetsJumpSneakAndGroundThrough()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            JesusModule jesus=new JesusModule();register(jesus);jesus.setEnabled(true);
            assertNull(RewoundHooks.move(f.player,.2,-.08,.3));
            PathTestFixture.set(Entity.class,f.player,"inWater",true);f.player.onGround=false;
            double[] vector=RewoundHooks.move(f.player,.2,-.08,.3);assertEquals((double)1E-5F,vector[1],0);assertEquals(.12,vector[2],1E-8);assertEquals(vector[1],f.player.motionY,0);
            f.player.effects.put(Potion.moveSpeed.id,new PotionEffect(Potion.moveSpeed.id,100,1));
            f.player.effects.put(Potion.moveSlowdown.id,new PotionEffect(Potion.moveSlowdown.id,100,0));
            assertEquals(.14,RewoundHooks.move(f.player,0,0,0)[2],1E-8);
            KeyBinding jump=f.mc.gameSettings.keyBindJump,sneak=f.mc.gameSettings.keyBindSneak;
            try{KeyBinding.setKeyBindState(jump.getKeyCode(),true);assertNull(RewoundHooks.move(f.player,0,0,0));KeyBinding.setKeyBindState(jump.getKeyCode(),false);
                KeyBinding.setKeyBindState(sneak.getKeyCode(),true);assertNull(RewoundHooks.move(f.player,0,0,0));
            }finally{KeyBinding.setKeyBindState(jump.getKeyCode(),false);KeyBinding.setKeyBindState(sneak.getKeyCode(),false);}
            f.player.onGround=true;assertNull(RewoundHooks.move(f.player,0,0,0));jesus.setEnabled(false);
        }
    }
    @Test public void everySpeedModeWaitsFourCompleteTicksAfterLeavingWater()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            f.player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            ModeSetting mode=(ModeSetting)f.speed.getSettings().stream().filter(s->s.getRawName().equals("Mode")).findFirst().get();
            for(String name:mode.getModes()){
                mode.setValue(name);PathTestFixture.set(Entity.class,f.player,"inWater",true);f.player.ticksExisted+=20;
                f.speed.setEnabled(true);f.speed.maintainRewound();PathTestFixture.set(Entity.class,f.player,"inWater",false);
                f.player.onGround=true;int jumps=f.player.jumps;float factor=f.player.jumpMovementFactor;CombatTimerAccess.setSpeed(1.375F);
                for(int tick=0;tick<4;tick++){
                    f.speed.maintainRewound();f.speed.tick();assertFalse(name,f.speed.rewoundControlsJump());assertNull(name,RewoundHooks.move(f.player,.17,.04,.23));
                    assertEquals(name,jumps,f.player.jumps);assertEquals(name,1.375F,CombatTimerAccess.getSpeed(),0);assertEquals(name,factor,f.player.jumpMovementFactor,0);f.player.ticksExisted++;
                }
                f.speed.maintainRewound();assertEquals(name.equals("Rewound"),f.speed.rewoundControlsJump());
                f.speed.tick();if(name.equals("Legit"))assertEquals(jumps+1,f.player.jumps);f.speed.setEnabled(false);
            }
        }
    }
    @Test public void disablerQueuesOnlyConfirmationsAndReleasesFifoBeforeMovementIncludingDisable()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            f.player.ticksExisted=10;DisablerModule module=new DisablerModule();register(module);module.setEnabled(true);
            EmbeddedChannel channel=new EmbeddedChannel(new ChannelDuplexHandler(){@Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise)throws Exception{
                if(!(message instanceof Packet)||!module.outbound((Packet<?>)message,ctx,promise))ctx.write(message,promise);
            }});
            C0FPacketConfirmTransaction a=new C0FPacketConfirmTransaction(),b=new C0FPacketConfirmTransaction();C03PacketPlayer move=new C03PacketPlayer();
            assertFalse(channel.writeOutbound(a,b));assertEquals(2,module.pendingTransactions());assertNull(channel.readOutbound());
            channel.writeOutbound(move);assertSame(a,channel.readOutbound());assertSame(b,channel.readOutbound());assertSame(move,channel.readOutbound());assertEquals(0,module.pendingTransactions());
            C00PacketKeepAlive keepalive=new C00PacketKeepAlive();channel.writeOutbound(keepalive);assertSame(keepalive,channel.readOutbound());
            channel.writeOutbound(a);module.setEnabled(false);channel.runPendingTasks();assertSame(a,channel.readOutbound());assertEquals(0,module.pendingTransactions());
            module.setEnabled(true);channel.writeOutbound(a);f.player.ticksExisted=0;module.tick();assertEquals(0,module.pendingTransactions());assertNull(channel.readOutbound());
            module.setEnabled(false);channel.finish();
        }
    }
    @Test public void flagsSuspendMovementExactlyForTicksWithoutChangingSavedEnableState()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            FlagDetectorModule flags=new FlagDetectorModule();Counter movement=new Counter(Category.MOVEMENT),client=new Counter(Category.CLIENT);register(flags);register(movement);register(client);
            flags.setEnabled(true);flags.stopMovement.setEnabled(true);flags.ticks.setValue(4D);movement.setEnabled(true);client.setEnabled(true);
            for(int i=0;i<22;i++)flags.tick();f.player.ticksExisted=100;
            flags.observe(new S08PacketPlayerPosLook(f.player.posX,f.player.posY,f.player.posZ,0,0,Collections.emptySet()));
            assertFalse(movement.isEnabled());assertTrue(movement.isConfiguredEnabled());assertEquals(1,movement.stopped);assertTrue(client.isEnabled());
            for(int tick=100;tick<104;tick++){f.player.ticksExisted=tick;flags.maintainPause();assertFalse(movement.isEnabled());}
            f.player.ticksExisted=104;flags.maintainPause();assertTrue(movement.isEnabled());assertEquals(2,movement.started);
            flags.observe(new S08PacketPlayerPosLook(f.player.posX,f.player.posY,f.player.posZ,0,0,Collections.emptySet()));movement.setEnabled(false);
            f.player.ticksExisted=108;flags.maintainPause();assertFalse(movement.isEnabled());assertFalse(movement.isConfiguredEnabled());
            flags.setEnabled(false);client.setEnabled(false);
        }
    }
    @Test public void multiAuraSendsOneNativeBurstWithDistinctTargetsAndNoFacingRequirement()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            register(new MoveFixModule());
            List<Entity> entities=new ArrayList<>();entities.add(f.player);PathTestFixture.set(World.class,f.world,"loadedEntityList",entities);
            for(int i=0;i<24;i++){RewoundModesTest.Player target=PathTestFixture.allocate(RewoundModesTest.Player.class);target.effects=new HashMap<>();target.worldObj=f.world;target.width=.6F;target.height=1.8F;target.setEntityId(i+2);
                double angle=i*Math.PI/12;target.setPosition(Math.sin(angle)*2,64,Math.cos(angle)*2);
                DataWatcher watcher=new DataWatcher(target);watcher.addObject(0,(byte)0);watcher.addObject(6,20F);PathTestFixture.set(Entity.class,target,"dataWatcher",watcher);entities.add(target);
            }
            f.aura.setEnabled(true);f.aura.targetAmount.setValue(20D);f.aura.tickStart();assertNotNull(f.aura.getTarget());
            Method ray=KillAuraModule.class.getDeclaredMethod("attackHit",double.class);ray.setAccessible(true);
            assertSame(f.aura.getTarget(),((MovingObjectPosition)ray.invoke(f.aura,3D)).entityHit);
            f.queue.packets.clear();f.mc.playerController.attackEntity(f.player,f.aura.getTarget());
            Set<Integer> hit=new HashSet<>();int count=0;for(Packet<?> packet:f.queue.packets)if(packet instanceof C02PacketUseEntity){PacketBuffer buffer=new PacketBuffer(Unpooled.buffer());try{packet.writePacketData(buffer);hit.add(buffer.readVarIntFromBuffer());count++;}finally{buffer.release();}}
            assertEquals(20,count);assertEquals(20,hit.size());
            f.aura.targetAmount.setValue(1D);f.queue.packets.clear();f.mc.playerController.attackEntity(f.player,f.aura.getTarget());
            assertEquals(1,f.queue.packets.stream().filter(p->p instanceof C02PacketUseEntity).count());
        }
    }
    private static final class Counter extends Module {
        int started,stopped;Counter(Category category){super("Pause fixture "+category,"",category,0);}
        @Override protected void onEnable(){started++;}@Override protected void onDisable(){stopped++;}
    }
}
