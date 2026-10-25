package dev.vibe.hud;

import dev.vibe.core.HudItemsTransformer;
import dev.vibe.module.impl.visual.OofArrowsModule;
import dev.vibe.ui.render.HudItemGeometryRenderer;
import dev.vibe.ui.render.OofArrowsRenderer;
import java.io.*;
import java.util.*;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class HudVisualsTest {
    @Test public void closedArrayPerimeterBridgesRowGapsWithoutInternalSeams() {
        List<ArrayListOutline.Box> rows=Arrays.asList(new ArrayListOutline.Box(10,10,100,22),new ArrayListOutline.Box(30,26,100,38),new ArrayListOutline.Box(20,42,100,54));
        List<ArrayListOutline.Point> p=ArrayListOutline.path(rows,false);
        assertTrue(p.size()>=8);
        assertEquals(10,p.get(0).x,0);assertEquals(10,p.get(0).y,0);
        boolean connector=false;
        for(int i=0;i<p.size();i++) {
            ArrayListOutline.Point a=p.get(i),b=p.get((i+1)%p.size());
            assertTrue(a.x==b.x||a.y==b.y);
            if(a.y==24&&b.y==24&&a.x!=b.x)connector=true;
        }
        assertTrue("Step joins must run across the middle of each row gap",connector);
        for(ArrayListOutline.Point point:ArrayListOutline.path(rows,true))assertTrue(point.x==10||point.x==100);
    }
    @Test public void arrowCircleOvalAndCardinalDirectionsMatchTheCamera() {
        float[] front=OofArrowsRenderer.position(960,540,90,11,false,0);
        float[] right=OofArrowsRenderer.position(960,540,90,11,false,Math.PI/2);
        float[] rear=OofArrowsRenderer.position(960,540,90,11,false,Math.PI);
        assertEquals(480,front[0],.01);assertEquals(180,front[1],.01);
        assertEquals(570,right[0],.01);assertEquals(270,right[1],.01);assertEquals(360,rear[1],.01);
        float[] oval=OofArrowsRenderer.position(960,540,90,11,true,Math.PI/2);
        assertEquals(90*960/540F,oval[0]-480,.01);
        for(int angle=0;angle<360;angle+=10) {
            float[] point=OofArrowsRenderer.position(320,180,300,36,true,Math.toRadians(angle));
            assertTrue(point[0]>=36&&point[0]<=284&&point[1]>=36&&point[1]<=144);
        }
    }
    @Test public void arrowBearingsUseActualCameraAxesIncludingDetachedTranslation() {
        // Minecraft yaw zero looks south: world +X is on the LEFT of the camera.
        float[] south={-1,0,0,0,0,1,0,0,0,0,-1,0,0,0,0,1};
        assertTrue(arrowPoint(south,10,0)[0]<480);assertTrue(arrowPoint(south,-10,0)[0]>480);
        assertTrue(arrowPoint(south,0,10)[1]<270);assertTrue(arrowPoint(south,0,-10)[1]>270);
        float[] west={0,0,1,0,0,1,0,0,-1,0,0,0,0,0,0,1};
        assertTrue(arrowPoint(west,0,10)[0]<480);assertTrue(arrowPoint(west,0,-10)[0]>480);
        south[12]=5;
        assertTrue(arrowPoint(south,8,0)[0]<480);assertTrue(arrowPoint(south,2,0)[0]>480);
    }
    private static float[] arrowPoint(float[] view,double x,double z) {
        return OofArrowsRenderer.position(960,540,90,11,false,OofArrowsRenderer.bearing(view,x,0,z));
    }
    @Test public void projectionHidesVisibleEntitiesAndKeepsOffscreenOnes() {
        float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        assertTrue(OofArrowsRenderer.visible(new AxisAlignedBB(-.1,-.1,-.1,.1,.1,.1),identity,identity,960,540));
        assertFalse(OofArrowsRenderer.visible(new AxisAlignedBB(3,0,0,4,1,1),identity,identity,960,540));
    }
    @Test public void arrowAndDomainColorsAnimateWithoutChangingStaticColors() {
        ScoreboardDomainSettings domain=new ScoreboardDomainSettings(s->s,()->true);
        domain.color.setValue(0xFF55CCAA);assertEquals(domain.colorAt(0,0),domain.colorAt(1000,0));
        domain.mode.setValue("Fade");assertNotEquals(domain.colorAt(0,0),domain.colorAt(1000,0));
        domain.mode.setValue("Rainbow");assertNotEquals(domain.colorAt(0,0),domain.colorAt(1000,0));
        OofArrowsModule arrows=new OofArrowsModule();
        int near=OofArrowsRenderer.color(arrows,0,0,0),far=OofArrowsRenderer.color(arrows,0,0,128);
        assertEquals(near&0xFFFFFF,far&0xFFFFFF);assertTrue((near>>>24)>(far>>>24));
        arrows.colorMode.setValue("Rainbow");assertNotEquals(OofArrowsRenderer.color(arrows,0,0,10),OofArrowsRenderer.color(arrows,1200,0,10));
        arrows.distanceFade.setEnabled(false);arrows.visibleOpacity.setValue(.25);arrows.occludedOpacity.setValue(.75);
        assertEquals(64,OofArrowsRenderer.color(arrows,0,0,10,true)>>>24);
        assertEquals(191,OofArrowsRenderer.color(arrows,0,0,10,false)>>>24);
    }
    @Test public void geometryHookIsInertOutsideHudAndPatchesActualNativeModelWriter() throws Exception {
        assertFalse(HudItemGeometryRenderer.draw(new Object(),-1,null));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=RenderItem.class.getResourceAsStream("RenderItem.class")) {
            byte[] bytes=new byte[8192];for(int n;(n=in.read(bytes))>=0;)out.write(bytes,0,n);
        }
        byte[] raw=out.toByteArray(),patched=new HudItemsTransformer().transform("bjh","net.minecraft.client.renderer.entity.RenderItem",raw);
        ClassNode type=new ClassNode();new ClassReader(patched).accept(type,0);int calls=0,clears=0;
        for(Object entry:type.methods) {
            MethodNode method=(MethodNode)entry;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.owner.equals("dev/vibe/ui/render/HudItemGeometryRenderer")) {
                    if(call.name.equals("draw"))calls++;else if(call.name.equals("clear"))clears++;
                    new Analyzer(new BasicVerifier()).analyze(type.name,method);
                }
            }
        }
        assertEquals(1,calls);
        assertEquals(1,clears);
        assertSame(raw,new HudItemsTransformer().transform("other.Class","other.Class",raw));
    }
}
