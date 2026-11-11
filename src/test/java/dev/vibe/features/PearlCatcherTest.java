package dev.vibe.features;

import dev.vibe.combat.ProjectileFixture;
import dev.vibe.module.impl.combat.PearlCatcherModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.path.PathTestFixture;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

public class PearlCatcherTest {
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    @Test public void foreignPearlReservesMoveFixAndKeepsItsLandingAfterDespawn()throws Exception{try(ProjectileFixture f=new ProjectileFixture()){MoveFixModule fix=new MoveFixModule();f.base.modules.registerDynamic(fix);PearlCatcherModule catcher=new PearlCatcherModule();f.base.modules.registerDynamic(catcher);catcher.delay.setValue(0D);catcher.rotationSpeed.setValue(180D);catcher.setEnabled(true);f.self.inventory.mainInventory[8]=new ItemStack(Items.ender_pearl,3);ProjectileFixture.Player other=f.player("Opponent",2.5,64,2.5);EntityEnderPearl pearl=new EntityEnderPearl(f.world,other);pearl.setEntityId(97);pearl.setPosition(2.5,65.5,3);pearl.motionX=0;pearl.motionY=.2;pearl.motionZ=.8;pearl.ticksExisted=1;f.world.loadedEntityList.add(pearl);fix.beginRotationTick();catcher.tick();Vec3 landing=(Vec3)field(catcher,"landing");assertNotNull(landing);assertNotNull(field(catcher,"shot"));catcher.applyRotation();assertTrue(fix.ownsRotation(catcher.getId()));assertTrue(PearlCatcherModule.reserving());pearl.isDead=true;fix.beginRotationTick();catcher.tick();assertSame(landing,field(catcher,"landing"));assertNotNull(field(catcher,"shot"));catcher.setEnabled(false);assertFalse(PearlCatcherModule.reserving());assertFalse(fix.ownsRotation(catcher.getId()));}}
    @Test public void ownPearlsAndUnloadedDestinationsDoNotPlanAnAction()throws Exception{try(ProjectileFixture f=new ProjectileFixture()){PearlCatcherModule catcher=new PearlCatcherModule();f.base.modules.registerDynamic(catcher);catcher.setEnabled(true);f.self.inventory.mainInventory[8]=new ItemStack(Items.ender_pearl);EntityEnderPearl own=new EntityEnderPearl(f.world,f.self);own.setEntityId(91);own.motionZ=.8;f.world.loadedEntityList.add(own);catcher.tick();assertNull(field(catcher,"landing"));assertFalse(PearlCatcherModule.reserving());EntityEnderPearl other=new EntityEnderPearl(f.world,f.player("Other",3,64,2));other.setEntityId(92);other.motionZ=.8;f.world.loadedEntityList.add(other);f.world.loaded=false;catcher.tick();assertFalse(PearlCatcherModule.reserving());catcher.setEnabled(false);}}
}
