package dev.vibe.features;

import dev.vibe.camera.CameraRig;
import dev.vibe.hud.NetworkSamples;
import dev.vibe.aiaura.*;
import dev.vibe.setting.StringSetting;
import net.minecraft.client.model.ModelPlayer;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CameraAndPresentationTest {
    @Test public void sitSurvivesTravelAndLookingAndItsModelOffsetIsRestoredBeforeVanilla()throws Exception{try(dev.vibe.inventory.AutomationFixture f=new dev.vibe.inventory.AutomationFixture()){
        dev.vibe.module.impl.meme.EmoteWheelModule emotes=new dev.vibe.module.impl.meme.EmoteWheelModule();f.base.modules.registerDynamic(emotes);f.player.lastTickPosX=f.player.posX;f.player.lastTickPosZ=f.player.posZ;emotes.setEnabled(true);emotes.select("Sit");emotes.tick();assertTrue(emotes.sit()>0);f.player.posX+=.1;f.player.rotationYaw+=90;UserActivity.mark();emotes.tick();assertEquals("Sit",emotes.selected());assertTrue(emotes.sit()>0);
        ModelPlayer model=new ModelPlayer(0,false);float body=model.bipedBody.rotationPointY;EmotePoses.preview="Sit";try{EmotePoses.apply(model);assertEquals(body+10.5F,model.bipedBody.rotationPointY,0);assertEquals(model.bipedBody.rotationPointY,model.bipedBodyWear.rotationPointY,0);EmotePoses.before(model);assertEquals(body,model.bipedBody.rotationPointY,0);}finally{EmotePoses.preview=null;emotes.setEnabled(false);}
    }}
    @Test public void cameraPathUsesShortestAnglesAndArcLength(){String data="[{\"x\":0,\"y\":64,\"z\":0,\"yaw\":179,\"pitch\":0,\"roll\":0,\"seconds\":2,\"world\":\"test\"},{\"x\":10,\"y\":64,\"z\":0,\"yaw\":-179,\"pitch\":20,\"roll\":0,\"seconds\":2,\"world\":\"test\"}]";CameraRig rig=new CameraRig(new StringSetting("data",data,20000,()->true));assertEquals(2,rig.poses().size());CameraRig.Frame mid=rig.sample(1,false);assertEquals(5,mid.position.xCoord,.01);assertEquals(180,mid.yaw,.01);assertEquals(10,mid.pitch,.01);assertEquals(2,rig.duration(false,1),0);assertEquals(5,rig.duration(true,2),.01);assertEquals(10,rig.sample(1000,false).position.xCoord,0);}
    @Test public void corruptedCameraDataClearsCompiledFrames(){StringSetting data=new StringSetting("data","[]",20000,()->true);CameraRig rig=new CameraRig(data);data.setValue("[{\"x\":0,\"y\":64,\"z\":0,\"yaw\":0,\"pitch\":0,\"roll\":0,\"seconds\":2,\"world\":\"test\"}]");assertFalse(rig.frames().isEmpty());data.setValue("[broken");assertTrue(rig.frames().isEmpty());assertTrue(rig.poses().isEmpty());}
    @Test public void missingNetworkMeasurementsAreNotInterpolatedOrInvented(){NetworkSamples n=new NetworkSamples();n.add(50,false);n.add(65,true);assertEquals(15,n.jitter[n.at(1)],0);n.add(Double.NaN,false);n.add(40,false);assertTrue(Double.isNaN(n.jitter[n.at(3)]));for(int i=0;i<130;i++)n.add(i,false);assertEquals(120,n.count);assertEquals(129,n.ping[n.at(119)],0);}
    @Test public void challengeCoverageIsIndependentFromPreviewPerformance(){RecorderArena arena=new RecorderArena(null,1);arena.preview=true;RecorderArena.Enemy e=new RecorderArena.Enemy();e.id=1;e.x=0;e.y=0;e.z=3;arena.enemies.add(e);arena.challenges.mode=PracticeChallenges.Mode.SPACING;for(int i=0;i<20;i++)arena.challenges.tick(arena);assertEquals(100,arena.challenges.score());assertEquals(0,arena.challenges.coverage());arena.preview=false;arena.challenges.tick(arena);assertTrue(arena.challenges.coverage()>0);}
}
