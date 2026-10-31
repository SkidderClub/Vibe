package dev.vibe.aiaura;

import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.Setting;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AIAuraIntegrationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void settingsHaveRequestedOrderDefaultsAndMutualExclusion()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            f.mc.thePlayer=null;PathTestFixture.set(Minecraft.class,f.mc,"mcDataDir",temp.newFolder());KillAuraModule ordinary=new KillAuraModule();AIAuraModule ai=new AIAuraModule();f.modules.registerDynamic(ordinary);f.modules.registerDynamic(ai);
            List<String> names=new ArrayList<>();for(Setting<?> setting:ai.getSettings())names.add(setting.getRawName());assertEquals(Arrays.asList("Open Recorder","Reach","Booster Clicking (x)","Booster Aiming (x)","LockFOV","Reset"),names);assertEquals(3,ai.reach.getDouble(),0);assertEquals(1,ai.boosterAiming.getDouble(),0);assertEquals(1,ai.boosterClicking.getDouble(),0);assertFalse(ai.lockFov.isEnabled());
            ordinary.setEnabled(true);ai.setEnabled(true);assertFalse(ordinary.isEnabled());assertSame(ai,KillAuraModule.activeController());ordinary.setEnabled(true);assertFalse(ai.isEnabled());assertSame(ordinary,KillAuraModule.activeController());
            ai.openRecorder.setEnabled(true);assertFalse(ai.openRecorder.isEnabled());assertEquals(1,get(AIAuraModule.class,ai,"requested"));ai.reset.setEnabled(true);assertFalse(ai.reset.isEnabled());assertEquals(2,get(AIAuraModule.class,ai,"requested"));
        }
    }
    @Test public void nativeClickHandlerProducesSwingThenAttackBeforeMovement()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            PathTestFixture.set(Minecraft.class,f.mc,"mcDataDir",temp.newFolder());TestWorld world=PathTestFixture.allocate(TestWorld.class);PathTestFixture.set(net.minecraft.world.World.class,world,"loadedEntityList",new ArrayList<Entity>());f.mc.theWorld=world;
            Queue queue=PathTestFixture.allocate(Queue.class);queue.packets=new ArrayList<>();Player self=player(world,1,.5,64,.5),enemy=player(world,2,.5,64,3);self.inventory=new InventoryPlayer(self);self.inventory.currentItem=0;self.movementInput=new MovementInput();self.onGround=true;PathTestFixture.set(EntityPlayerSP.class,self,"sendQueue",queue);PathTestFixture.set(EntityPlayerSP.class,self,"mc",f.mc);f.mc.thePlayer=self;f.mc.inGameHasFocus=true;f.mc.displayWidth=960;f.mc.displayHeight=600;f.mc.gameSettings.mouseSensitivity=.5F;f.mc.gameSettings.fovSetting=70;
            PathTestFixture.set(Minecraft.class,f.mc,"renderViewEntity",self);f.mc.playerController=new PlayerControllerMP(f.mc,queue);f.filters.getIgnoreTeammates().setEnabled(false);
            KillAuraModule ordinary=new KillAuraModule();AIAuraModule ai=new AIAuraModule();MoveFixModule fix=new MoveFixModule();f.modules.registerDynamic(ordinary);f.modules.registerDynamic(ai);f.modules.registerDynamic(fix);
            AuraLearning learning=new AuraLearning(temp.newFolder().toPath());TrainedAura model=AuraFixtures.policy();AuraFixtures.bias(model,0,0);AuraFixtures.bias(model,1,0);model.minRotationDelay=model.maxRotationDelay=model.minClickDelay=model.maxClickDelay=0;PathTestFixture.set(AuraLearning.class,learning,"model",model);PathTestFixture.set(AIAuraModule.class,ai,"learning",learning);ai.setEnabled(true);
            fix.beginRotationTick();ai.tickStart();assertSame(enemy,ai.getTarget());fix.tick();assertEquals("aiaura",get(MoveFixModule.class,fix,"rotationOwner"));
            queue.packets.clear();KillAuraModule.prepareInputHook();assertTrue(queue.packets.isEmpty());assertEquals(MovingObjectPosition.MovingObjectType.ENTITY,f.mc.objectMouseOver.typeOfHit);assertSame(enemy,f.mc.objectMouseOver.entityHit);
            Method click=Minecraft.class.getDeclaredMethod("clickMouse");click.setAccessible(true);while(f.mc.gameSettings.keyBindAttack.isPressed())click.invoke(f.mc);
            assertEquals(2,queue.packets.size());assertTrue(queue.packets.get(0) instanceof C0APacketAnimation);assertTrue(queue.packets.get(1) instanceof C02PacketUseEntity);
            MoveFixModule.beginPacketRotationHook(self);Method walk=EntityPlayerSP.class.getDeclaredMethod("onUpdateWalkingPlayer");walk.setAccessible(true);walk.invoke(self);MoveFixModule.endPacketRotationHook(self);assertTrue(queue.packets.get(queue.packets.size()-1) instanceof C03PacketPlayer);
            int sent=queue.packets.size();ai.tickEnd();assertEquals(sent,queue.packets.size());assertNull(f.mc.objectMouseOver);
            AuraFixtures.bias(model,0,.05F);ai.lockFov.setEnabled(true);enemy.posX=enemy.lastTickPosX=-.5;enemy.setEntityBoundingBox(new AxisAlignedBB(-.8,64,2.7,-.2,65.8,3.3));
            fix.beginRotationTick();ai.tickStart();fix.tick();assertTrue(self.rotationYaw>0);assertEquals(fix.getRotationYaw(),self.rotationYaw,0);ai.frameTick(0);float start=AIAuraModule.cameraYawHook(self,-999);ai.frameTick(.5F);float halfway=AIAuraModule.cameraYawHook(self,-999);ai.frameTick(1);assertEquals(self.rotationYaw,AIAuraModule.cameraYawHook(self,-999),1e-5);assertTrue(halfway>=start&&halfway<=self.rotationYaw);assertEquals(self.rotationYaw,dev.vibe.module.impl.movement.FreecamModule.yawHook(self,-999),1e-5);
            ai.lockFov.setEnabled(false);assertEquals(17,AIAuraModule.cameraYawHook(self,17),0);
            // A silent rotation returns monotonically after the last target disappears; no neural noise runs there.
            world.loadedEntityList.remove(enemy);self.rotationYaw=-30;float previous=fix.getRotationYaw();for(int i=0;i<80;i++){fix.beginRotationTick();ai.tickStart();fix.tick();float current=fix.getRotationYaw();assertTrue(Math.abs(current+30)<=Math.abs(previous+30)+.001);previous=current;}assertNull(ai.getTarget());assertNull(get(MoveFixModule.class,fix,"fakeRotation"));
            ai.setEnabled(false);assertNull(get(MoveFixModule.class,fix,"fakeRotation"));
        }
    }
    @Test public void untrainedModuleNeverQueuesAttacksOrRotations()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            PathTestFixture.set(Minecraft.class,f.mc,"mcDataDir",temp.newFolder());AIAuraModule ai=new AIAuraModule();MoveFixModule fix=new MoveFixModule();f.modules.registerDynamic(ai);f.modules.registerDynamic(fix);PathTestFixture.set(Module.class,ai,"enabled",true);ai.tickStart();KillAuraModule.prepareInputHook();assertNull(ai.getTarget());assertNull(get(MoveFixModule.class,fix,"fakeRotation"));assertFalse(f.mc.gameSettings.keyBindAttack.isPressed());
        }
    }
    private static Player player(TestWorld world,int id,double x,double y,double z)throws Exception{Player p=PathTestFixture.allocate(Player.class);p.setEntityId(id);p.worldObj=world;p.posX=p.lastTickPosX=x;p.posY=p.lastTickPosY=y;p.posZ=p.lastTickPosZ=z;p.width=.6F;p.height=1.8F;p.setEntityBoundingBox(new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+1.8,z+.3));
        DataWatcher watcher=new DataWatcher(p);watcher.addObject(6,20F);PathTestFixture.set(Entity.class,p,"dataWatcher",watcher);net.minecraft.entity.ai.attributes.ServersideAttributeMap attributes=new net.minecraft.entity.ai.attributes.ServersideAttributeMap();attributes.registerAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20);PathTestFixture.set(EntityLivingBase.class,p,"attributeMap",attributes);world.loadedEntityList.add(p);return p;}
    private static Object get(Class<?> type,Object object,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    public static class Player extends EntityPlayerSP {
        private Player(){super(null,null,null,null);}
        @Override public String getName(){return "Test"+getEntityId();}
        @Override public boolean isEntityAlive(){return true;}@Override public boolean isSpectator(){return false;}@Override public boolean isInvisible(){return false;}@Override public boolean canBeCollidedWith(){return true;}
        @Override public boolean isSprinting(){return false;}@Override public boolean isSneaking(){return false;}@Override public boolean isUsingItem(){return false;}
        @Override public void swingItem(){sendQueue.addToSendQueue(new C0APacketAnimation());}
        @Override public Vec3 getPositionEyes(float partial){return new Vec3(posX,posY+1.62,posZ);}
        @Override public void attackTargetEntityWithCurrentItem(Entity target){}
    }
    public static class TestWorld extends WorldClient {
        private TestWorld(){super(null,null,0,null,null);}
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 a,Vec3 b,boolean liquid,boolean ignore,boolean last){return null;}
        @Override public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity self,AxisAlignedBB box){List<Entity> list=new ArrayList<>();for(Object value:loadedEntityList){Entity e=(Entity)value;if(e!=self&&e.getEntityBoundingBox().intersectsWith(box))list.add(e);}return list;}
    }
    public static class Queue extends NetHandlerPlayClient {List<Packet<?>> packets;private Queue(){super(null,null,null,null);}@Override public void addToSendQueue(Packet packet){packets.add(packet);}}
}
