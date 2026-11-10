package dev.vibe.module.impl.meme;

import dev.vibe.path.PathTestFixture;
import dev.vibe.module.Category;
import dev.vibe.statistics.StatisticsService;
import net.minecraft.client.model.ModelPlayer;
import org.junit.Test;
import static org.junit.Assert.*;

public class DabModuleTest {
    @Test public void killStartsExactlyFortyTicksWithoutSettings()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            DabModule dab=new DabModule();f.modules.registerDynamic(dab);dab.setEnabled(true);assertEquals(Category.MEME,dab.getCategory());assertTrue(dab.getSettings().isEmpty());
            dab.onActivity(StatisticsService.Activity.JUMP,null,f.player);assertEquals(0,dab.amount(0),0);
            dab.onActivity(StatisticsService.Activity.KILL,null,f.player);for(int i=0;i<8;i++)dab.tick();assertEquals(1,dab.amount(0),0);
            for(int i=8;i<39;i++)dab.tick();assertTrue(dab.amount(0)>0);dab.tick();assertEquals(0,dab.amount(0),0);
            dab.onActivity(StatisticsService.Activity.KILL,null,f.player);dab.tick();assertTrue(dab.amount(0)>0);dab.setEnabled(false);assertEquals(0,dab.amount(0),0);
        }
    }
    @Test public void onlyLocalThirdPersonWorldModelsArePosed()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            DabModule dab=new DabModule();f.modules.registerDynamic(dab);dab.setEnabled(true);dab.onActivity(StatisticsService.Activity.KILL,null,f.player);for(int i=0;i<10;i++)dab.tick();
            ModelPlayer model=new ModelPlayer(0,false);PathTestFixture.set(dev.vibe.ui.render.esp.ChamsRenderer.class,null,"worldDepth",1);
            try{f.mc.gameSettings.thirdPersonView=0;DabModule.poseHook(model,f.player,0);assertEquals(0,model.bipedLeftArm.rotateAngleZ,0);
                f.mc.gameSettings.thirdPersonView=1;DabModule.poseHook(model,f.addPlayer(4,"Other",2,64,2),0);assertEquals(0,model.bipedLeftArm.rotateAngleZ,0);
                DabModule.poseHook(model,f.player,0);assertEquals(-2.25F,model.bipedLeftArm.rotateAngleZ,.001);assertEquals(model.bipedLeftArm.rotateAngleZ,model.bipedLeftArmwear.rotateAngleZ,0);assertEquals(model.bipedHead.rotateAngleX,model.bipedHeadwear.rotateAngleX,0);
            }finally{PathTestFixture.set(dev.vibe.ui.render.esp.ChamsRenderer.class,null,"worldDepth",0);}
        }
    }
    @Test public void easingIsContinuousAtBothEnds(){assertEquals(0,DabModule.envelope(-1),0);assertEquals(0,DabModule.envelope(0),0);assertEquals(1,DabModule.envelope(7),0);assertEquals(1,DabModule.envelope(28),0);assertEquals(0,DabModule.envelope(40),0);assertTrue(DabModule.envelope(3)<DabModule.envelope(5));assertTrue(DabModule.envelope(34)>DabModule.envelope(38));}
}
