package dev.vibe.features;
import dev.vibe.module.impl.visual.AspectRatioModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.movement.OrbitSteering;
import org.junit.Test;
import static org.junit.Assert.*;
public class AspectRatioTest {
    @Test public void aspectPresetsAndHandOptInRestoreNativeProjectionImmediately()throws Exception{try(PathTestFixture f=new PathTestFixture()){
        AspectRatioModule aspect=new AspectRatioModule();f.modules.registerDynamic(aspect);float nativeRatio=16F/9;assertEquals(nativeRatio,AspectRatioModule.resolve(nativeRatio,false),0);aspect.setEnabled(true);assertEquals(4F/3,AspectRatioModule.resolve(nativeRatio,false),0);assertEquals(nativeRatio,AspectRatioModule.resolve(nativeRatio,true),0);aspect.hand.setEnabled(true);assertEquals(4F/3,AspectRatioModule.resolve(nativeRatio,true),0);aspect.preset.setValue("Custom");aspect.custom.setValue(2.2);assertEquals(2.2F,AspectRatioModule.resolve(nativeRatio,false),.001);aspect.setEnabled(false);assertEquals(nativeRatio,AspectRatioModule.resolve(nativeRatio,false),0);
    }}
    @Test public void orbitClosesDistanceAndLegalKeysFollowAnySilentYaw(){double[] outside=OrbitSteering.direction(5,0,2.5,1,0),inside=OrbitSteering.direction(1,0,2.5,1,0);assertTrue(outside[0]<0);assertTrue(inside[0]>0);
        for(int yaw=-180;yaw<=180;yaw+=15){float[] keys=OrbitSteering.keys(.6,.8,yaw);assertTrue(Math.abs(keys[0])<=1&&Math.abs(keys[1])<=1);double a=Math.toRadians(yaw),len=Math.hypot(keys[0],keys[1]);double dot=((-Math.sin(a)*keys[0]+Math.cos(a)*keys[1])*.6+(Math.cos(a)*keys[0]+Math.sin(a)*keys[1])*.8)/len;assertTrue("World-space direction at "+yaw,dot>.92);}
    }
}
