package dev.vibe.combat;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.*;
import dev.vibe.module.impl.visual.*;
import dev.vibe.module.impl.world.AutoToolModule;
import dev.vibe.path.PathTestFixture;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NewModuleBehaviorTest {
    @Test public void freeLookNeverChangesPlayerOrSilentRotationsAndHoldReleaseRestoresView()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            FreeLookModule look=new FreeLookModule();MoveFixModule fix=new MoveFixModule();f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(look);
            f.self.rotationYaw=35;f.self.rotationPitch=12;fix.setFakeRotation("killaura",190,28);look.keyChanged(true,true);
            FreecamModule.mouseLookHook(f.self,100,40);
            assertEquals(35,f.self.rotationYaw,0);assertEquals(12,f.self.rotationPitch,0);assertEquals(50,FreecamModule.yawHook(f.self,190),.001);assertEquals(6,FreecamModule.pitchHook(f.self,28),.001);
            assertEquals(190,fix.getRotationYaw(),0);assertEquals(28,fix.getRotationPitch(),0);assertEquals(1,FreecamModule.thirdPersonHook(0));
            look.keyChanged(false,true);assertFalse(look.isEnabled());assertEquals(0,FreecamModule.thirdPersonHook(0));
            look.hold.setEnabled(false);look.setEnabled(true);look.keyChanged(false,true);assertTrue(look.isEnabled());f.base.mc.inGameHasFocus=false;look.tick();assertFalse(look.isEnabled());
        }
    }
    @Test public void guiMoveHonoursScreenSelectionAndNeverMovesWhileTyping()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            GuiMoveModule m=new GuiMoveModule();assertTrue(m.allows(PathTestFixture.allocate(GuiInventory.class)));assertFalse(m.allows(PathTestFixture.allocate(GuiChest.class)));
            m.screens.toggle("Chests");assertTrue(m.allows(PathTestFixture.allocate(GuiChest.class)));assertFalse(m.allows(new GuiChat()));
            m.screens.toggle("Other Vanilla GUIs");assertTrue(m.allows(new GuiIngameMenu()));assertFalse(m.allows(PathTestFixture.allocate(GuiEditSign.class)));assertFalse(m.allows(PathTestFixture.allocate(GuiScreenBook.class)));
        }
    }
    @Test public void toolReserveDefaultsToOriginalBehaviourAndProtectsDamagedTools()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            AutoToolModule m=new AutoToolModule();ItemStack tool=new ItemStack(Items.diamond_pickaxe);tool.setItemDamage(tool.getMaxDamage()-5);
            assertTrue(m.usable(tool));assertEquals(0,m.durabilityReserve.getInt());m.durabilityReserve.setValue(5D);assertFalse(m.usable(tool));m.durabilityReserve.setValue(4D);assertTrue(m.usable(tool));assertTrue(m.usable(new ItemStack(Items.stick)));
        }
    }
    @Test public void fireballDefenceUsesSilentRotationAndOnlyQueuesTheNativeClickAtInputPhase()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            MoveFixModule fix=new MoveFixModule();AntiFireballModule m=new AntiFireballModule();f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(m);m.setEnabled(true);
            EntityLargeFireball fire=PathTestFixture.allocate(EntityLargeFireball.class);fire.worldObj=f.world;fire.width=fire.height=1;fire.setPosition(2.5,65.62,.5);fire.motionX=-.3;f.world.loadedEntityList.add(fire);
            MovingObjectPosition old=new MovingObjectPosition(new Vec3(0,0,0),EnumFacing.UP,BlockPos.ORIGIN);f.base.mc.objectMouseOver=old;
            m.prepare();m.apply();assertTrue(AntiFireballModule.reserving());assertSame(old,f.base.mc.objectMouseOver);assertEquals(0,f.self.rotationYaw,0);assertEquals(-90,fix.getRotationYaw(),1);
            AntiFireballModule.prepareInputHook();assertSame(fire,f.base.mc.objectMouseOver.entityHit);assertTrue(f.base.mc.gameSettings.keyBindAttack.isPressed());
            m.restore();assertSame(old,f.base.mc.objectMouseOver);assertFalse(f.base.mc.gameSettings.keyBindAttack.isPressed());fire.motionX=.3;m.prepare();assertFalse(AntiFireballModule.reserving());
        }
    }
    @Test public void bowFindsAnExposedHeadAndRejectsACompletelyCoveredBody()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            MoveFixModule fix=new MoveFixModule();BowAimbotModule bow=new BowAimbotModule();f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(bow);bow.setEnabled(true);
            f.self.inventory.mainInventory[0]=new ItemStack(Items.bow);f.self.drawing=true;ProjectileFixture.Player target=f.player("Enemy",.5,64,12.5);
            assertTrue("Bow input was not active",BowAimbotModule.drawing());assertTrue("Target filters rejected the fixture",f.base.filters.canTarget(target));
            Vec3 start=f.self.getPositionEyes(1).addVector(-.16,-.10000000149,0),point=new Vec3(.5,64.9,12.5);Vec3[] forecast=ArrowAimSolver.forecast(f.world,target,true);
            ArrowAimSolver.Shot direct=ArrowAimSolver.solve(start,point,forecast,3);assertNotNull("Native body forecast made an open shot unreachable: "+forecast[4],direct);
            assertTrue("Open trajectory failed terrain test: "+start+" -> "+point+"; box "+target.getEntityBoundingBox()+"; offset "+forecast[4],ArrowAimSolver.hits(f.world,target.getEntityBoundingBox(),forecast,direct));
            bow.tick();assertNotNull("Open target had no arrow solution",bow.getSolution());assertEquals("Fully drawn bow must use native launch speed",3,bow.getSolution().velocity.lengthVector(),.00001);
            for(int x=-1;x<=1;x++){f.block(x,64,10,Blocks.stone.getDefaultState());f.block(x,65,10,Blocks.stone_slab.getDefaultState());}
            bow.tick();assertNotNull("Partial cover had no arrow solution",bow.getSolution());assertSame(target,bow.getTarget());assertTrue("Bow ignored the exposed upper body",bow.getSolution().point.yCoord>65.4);assertEquals(f.self.rotationYaw,fix.getRotationYaw(),.001);
            for(int x=-1;x<=1;x++){f.block(x,65,10,Blocks.stone.getDefaultState());f.block(x,66,10,Blocks.stone.getDefaultState());}
            bow.tick();assertNull(bow.getSolution());
        }
    }
    @Test public void arrowSolverMatchesNativeAirFlightAndLeadsLateralMovement()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            Vec3 origin=new Vec3(.5,70,.5),goal=new Vec3(.5,70,30);Vec3[] movement=new Vec3[121];for(int i=0;i<movement.length;i++)movement[i]=new Vec3(.25*i,0,0);
            ArrowAimSolver.Shot shot=ArrowAimSolver.solve(origin,goal,movement,3);assertNotNull(shot);assertTrue(shot.velocity.xCoord>0);assertTrue(shot.velocity.yCoord>0);
            EntityArrow arrow=new EntityArrow(f.world,origin.xCoord,origin.yCoord,origin.zCoord);arrow.setThrowableHeading(shot.velocity.xCoord,shot.velocity.yCoord,shot.velocity.zCoord,3,0);
            int ticks=(int)shot.ticks;for(int i=0;i<ticks;i++)arrow.onUpdate();double part=shot.ticks-ticks;Vec3 actual=new Vec3(arrow.posX+arrow.motionX*part,arrow.posY+arrow.motionY*part,arrow.posZ+arrow.motionZ*part),expected=goal.add(ArrowAimSolver.at(movement,shot.ticks));
            assertEquals(expected.xCoord,actual.xCoord,.02);assertEquals(expected.yCoord,actual.yCoord,.02);assertEquals(expected.zCoord,actual.zCoord,.02);
            assertNull(ArrowAimSolver.solve(origin,new Vec3(.5,70,90),movement,.3));
        }
    }
    @Test public void blockSearchUsesTheBudgetAndDoesNotReadUnloadedChunks()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            BlockESPModule m=new BlockESPModule();f.base.modules.registerDynamic(m);BlockESPModule.Style style=new BlockESPModule.Style(0xFF45B3CD);style.enabled=true;m.put(Blocks.diamond_ore,style);m.range.setValue(8D);m.verticalRange.setValue(8D);m.setEnabled(true);f.block(2,65,2,Blocks.diamond_ore.getDefaultState());
            for(int i=0;i<80&&!m.matches().containsKey(new BlockPos(2,65,2));i++){int before=f.world.reads;m.tick();assertTrue(f.world.reads-before<=m.scanBudget.getInt());}
            assertTrue(m.matches().containsKey(new BlockPos(2,65,2)));assertSame(m.openEditor,m.getSettings().get(0));
            m.clear();f.world.loaded=false;int before=f.world.reads;m.tick();assertEquals(before,f.world.reads);
            style.enabled=false;m.put(Blocks.diamond_ore,style);assertTrue(m.matches().isEmpty());
        }
    }
}
