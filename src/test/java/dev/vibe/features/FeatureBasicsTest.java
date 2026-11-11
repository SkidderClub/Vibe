package dev.vibe.features;
import dev.vibe.inventory.AutomationFixture;
import dev.vibe.module.impl.movement.StepModule;
import dev.vibe.module.impl.visual.EnchantmentGlintModule;
import java.util.*;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FeatureBasicsTest {
    @Test public void jumpStepRespectsItsHeightLimitAndHeadroom()throws Exception{try(dev.vibe.combat.ProjectileFixture f=new dev.vibe.combat.ProjectileFixture()){
        StepModule step=new StepModule();step.mode.setValue("Jump");step.setEnabled(true);f.self.isCollidedHorizontally=true;f.block(0,64,1,net.minecraft.init.Blocks.stone.getDefaultState());MovementInput input=new MovementInput();input.moveForward=1;step.input(input);assertTrue(input.jump);
        input.jump=false;step.height.setValue(.6D);step.input(input);assertFalse(input.jump);step.height.setValue(1D);f.block(0,66,1,net.minecraft.init.Blocks.stone.getDefaultState());step.input(input);assertFalse(input.jump);step.setEnabled(false);
    }}
    @Test public void minimalCollisionMeshJoinsFlatNeighbours(){List<AxisAlignedBB> boxes=Arrays.asList(new AxisAlignedBB(0,0,0,1,1,1),new AxisAlignedBB(1,0,0,2,1,1));List<CollisionMesh.Edge> edges=CollisionMesh.build(boxes,true);assertEquals(12,edges.size());for(CollisionMesh.Edge e:edges)assertFalse("Interior seam remained",e.a.xCoord==1&&e.b.xCoord==1);assertEquals(20,CollisionMesh.build(boxes,false).size());}
    @Test public void minimalCollisionMeshKeepsRealHeightChanges(){List<CollisionMesh.Edge> edges=CollisionMesh.build(Arrays.asList(new AxisAlignedBB(0,0,0,1,1,1),new AxisAlignedBB(1,0,0,2,.5,1)),true);assertTrue(edges.size()>12);assertTrue(edges.stream().anyMatch(e->e.a.yCoord==.5&&e.b.yCoord==.5));}
    @Test public void meshIsStableForRepeatedShapes(){AxisAlignedBB box=new AxisAlignedBB(0,0,0,1,.5,1);assertEquals(12,CollisionMesh.build(Arrays.asList(box,box),true).size());assertTrue(CollisionMesh.build(Collections.emptyList(),true).isEmpty());}
    @Test public void stepRestoresOriginalHeightOnDisableAndModeChange()throws Exception{try(AutomationFixture f=new AutomationFixture()){StepModule step=new StepModule();f.base.modules.registerDynamic(step);f.player.stepHeight=.6F;step.setEnabled(true);step.height.setValue(1.5);step.input(new MovementInput());assertEquals(1.5,f.player.stepHeight,.00001);step.mode.setValue("Jump");step.input(new MovementInput());assertEquals(.6,f.player.stepHeight,.00001);step.mode.setValue("Vanilla");step.input(new MovementInput());step.setEnabled(false);assertEquals(.6,f.player.stepHeight,.00001);}}
    @Test public void glintIsNativeWhenOffAndOpacityDimsItsMaterial()throws Exception{try(AutomationFixture f=new AutomationFixture()){EnchantmentGlintModule m=new EnchantmentGlintModule();f.base.modules.registerDynamic(m);assertEquals(-8372020,EnchantmentGlintModule.tint(-8372020));m.setEnabled(true);m.style.primary.setValue(0xFFFF0000);m.opacity.setValue(1.0);assertEquals(0xFFFF0000,EnchantmentGlintModule.tint(0));m.opacity.setValue(0.0);assertEquals(0xFF000000,EnchantmentGlintModule.tint(0));m.setEnabled(false);assertEquals(1234,EnchantmentGlintModule.time(1234));}}
}
