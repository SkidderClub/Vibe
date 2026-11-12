package dev.vibe.features;

import dev.vibe.module.impl.movement.ParkourModule;
import dev.vibe.module.impl.visual.CollisionPreviewModule;
import dev.vibe.module.impl.meme.*;
import dev.vibe.path.PathTestFixture;
import net.minecraft.init.Blocks;
import net.minecraft.block.Block;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.util.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.input.Keyboard;
import static org.junit.Assert.*;

public class PresentationFixesTest {
    @Test public void parkourJumpsFromChestBedAndFullBlockButNotFromTheirCentres()throws Exception{
        for(Block block:new Block[]{Blocks.chest,Blocks.bed,Blocks.stone})try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
            f.world.blocks.clear();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)f.world.blocks.put(new BlockPos(x,62,z),Blocks.stone.getDefaultState());
            f.world.blocks.put(new BlockPos(0,63,0),block.getDefaultState());
            double height=block==Blocks.chest?63.875:block==Blocks.bed?63.5625:64;
            f.base.base.mc.inGameHasFocus=true;f.base.self.onGround=true;f.base.self.rotationYaw=0;
            ParkourModule p=new ParkourModule();MovementInput input=new MovementInput();input.moveForward=1;
            f.base.self.setPosition(.5,height,.5);p.input(input);assertFalse(block+" centre",input.jump);
            f.base.self.motionZ=.25;f.base.self.setPosition(.5,height,.99);p.input(input);assertTrue(block+" edge",input.jump);
            input.jump=false;input.sneak=true;p.input(input);assertFalse(input.jump);
            input.sneak=false;f.world.blocks.put(new BlockPos(0,65,0),Blocks.stone.getDefaultState());p.input(input);assertFalse("Do not jump into a ceiling",input.jump);
        }
    }
    @Test public void collisionScanKeepsItsLastCompleteMeshAndFinishesWhileMoving()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        CollisionPreviewModule c=new CollisionPreviewModule();c.setEnabled(true);for(int i=0;i<3;i++)c.tick();List<?> old=(List<?>)field(c,"mesh");assertFalse(old.isEmpty());
        for(int i=0;i<2;i++){f.base.self.setPosition(f.base.self.posX+2,64,f.base.self.posZ);c.tick();assertSame("Keep committed geometry during scan",old,field(c,"mesh"));}
        f.base.self.setPosition(f.base.self.posX+2,64,f.base.self.posZ);c.tick();assertNotSame("A moving scan must finish",old,field(c,"mesh"));assertFalse(((List<?>)field(c,"mesh")).isEmpty());
        c.setEnabled(false);assertTrue(((List<?>)field(c,"mesh")).isEmpty());
    }}
    @Test public void sitOnlyStopsOnPhysicalMovementKeysAndOtherEmotesContinue()throws Exception{try(dev.vibe.inventory.AutomationFixture f=new dev.vibe.inventory.AutomationFixture()){
        EmoteWheelModule e=new EmoteWheelModule();f.base.modules.registerDynamic(e);e.setEnabled(true);
        boolean old=Keyboard.isCreated();Field keys=Keyboard.class.getDeclaredField("keyDownBuffer");keys.setAccessible(true);ByteBuffer buffer=(ByteBuffer)keys.get(null);byte previous=buffer.get(f.mc.gameSettings.keyBindForward.getKeyCode());
        try{PathTestFixture.set(Keyboard.class,null,"created",true);e.select("Sit");UserActivity.mark();e.tick();assertEquals("Sit",e.selected());buffer.put(f.mc.gameSettings.keyBindForward.getKeyCode(),(byte)1);e.tick();assertEquals("Stop",e.selected());
            for(String name:new String[]{"Dab","Wave","Cheer","Dance","Bow","Facepalm"}){e.select(name);f.player.rotationYaw+=15;f.player.posX+=.2;e.tick();assertEquals(name,e.selected());}
        }finally{buffer.put(f.mc.gameSettings.keyBindForward.getKeyCode(),previous);PathTestFixture.set(Keyboard.class,null,"created",old);e.setEnabled(false);}
    }}
    @Test public void disablingBobbleheadRestoresHeadAndSkinLayerWithoutAccumulating()throws Exception{try(dev.vibe.inventory.AutomationFixture f=new dev.vibe.inventory.AutomationFixture()){
        BobbleheadModule b=new BobbleheadModule();f.base.modules.registerDynamic(b);b.setEnabled(true);PathTestFixture.set(BobbleheadModule.class,b,"roll",.3F);
        ModelPlayer model=new ModelPlayer(0,false);model.bipedHead.rotateAngleZ=.04F;
        for(int i=0;i<8;i++){EmotePoses.apply(model);assertEquals(.04F+b.roll(),model.bipedHead.rotateAngleZ,.0001F);EmotePoses.before(model);assertEquals(.04F,model.bipedHead.rotateAngleZ,.0001F);}
        EmotePoses.apply(model);b.setEnabled(false);EmotePoses.before(model);assertEquals(.04F,model.bipedHead.rotateAngleZ,.0001F);assertEquals(.04F,model.bipedHeadwear.rotateAngleZ,.0001F);
    }}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
}
