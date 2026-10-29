package dev.vibe.combat;

import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.visual.HitmarkerModule;
import dev.vibe.ui.render.HologramPose;
import java.nio.FloatBuffer;
import net.minecraft.client.model.*;
import net.minecraft.util.*;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import static org.junit.Assert.*;

public class HologramPoseTest {
    @Test public void capturesAllNativePivotsOffsetsAnglesAndVisibilityWithoutReanimatingTheTarget(){
        ModelPlayer source=new ModelPlayer(0,true);ModelRenderer[] parts=HologramPose.parts(source);
        for(int i=0;i<parts.length;i++){parts[i].rotateAngleX=i*.13F;parts[i].rotateAngleY=-i*.07F;parts[i].rotateAngleZ=i*.03F;parts[i].offsetX=.05F*i;parts[i].rotationPointY=i;parts[i].showModel=i%2==0;}
        Matrix4f root=new Matrix4f();root.m30=1.25F;HologramPose snapshot=new HologramPose(source,root,new ResourceLocation("test","skin"),true,42,30000000.125,64,40000000.25);
        for(ModelRenderer part:parts){part.rotateAngleX=9;part.offsetX=8;part.rotationPointY=7;part.showModel=true;}root.m30=100;
        ModelPlayer copy=new ModelPlayer(0,true);snapshot.apply(copy);ModelRenderer[] frozen=HologramPose.parts(copy);
        for(int i=0;i<frozen.length;i++){assertEquals(i*.13F,frozen[i].rotateAngleX,0);assertEquals(i,frozen[i].rotationPointY,0);assertEquals(i%2==0,frozen[i].showModel);}
        FloatBuffer data=BufferUtils.createFloatBuffer(16);snapshot.matrix(data);assertEquals(1.25F,data.get(12),0);assertEquals(30000000.125,snapshot.originX,0);assertEquals(40000000.25,snapshot.originZ,0);
        HologramPose later=snapshot.at(100);assertEquals(100,later.created);assertEquals(42,snapshot.created);assertEquals(snapshot.skin,later.skin);
    }
    @Test public void multipleHitsFreezeSeparatePosesAndTransparencyAndFadeRespectLifetime()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            HitmarkerModule module=new HitmarkerModule();f.modules.registerDynamic(module);module.getModes().toggle("Hologram");module.setEnabled(true);module.hologramDelay.setValue(0D);
            f.player.setEntityBoundingBox(new AxisAlignedBB(0,64,0,1,66,1));long now=System.currentTimeMillis();ModelPlayer model=new ModelPlayer(0,false);
            HologramPose first=new HologramPose(model,new Matrix4f(),new ResourceLocation("test","skin"),false,now);module.captureHologram(f.player,first);module.mark(f.player);
            model.bipedRightArm.rotateAngleX=1;module.captureHologram(f.player,new HologramPose(model,new Matrix4f(),first.skin,false,System.currentTimeMillis()));module.mark(f.player);assertEquals(2,module.getHolograms().size());
            module.hologramTransparency.setValue(100D);assertEquals(0,module.hologramOpacity(first),0);module.hologramTransparency.setValue(0D);module.hologramFade.setValue(false);assertEquals(1,module.hologramOpacity(first),0);
            module.hologramFade.setValue(true);assertEquals(0,module.hologramOpacity(first.at(now-module.hologramLifetime.getInt()-1)),0);module.setEnabled(false);assertTrue(module.getHolograms().isEmpty());
            module.hologramColorFade.setValue(true);assertEquals(module.hologramEndColor.getArgb(),module.hologramTint(first.at(now-module.hologramLifetime.getInt()-1)));
        }
    }
}
