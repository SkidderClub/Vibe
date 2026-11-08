package dev.vibe.hud;

import dev.vibe.inventory.AutomationFixture;
import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.visual.*;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.render.HologramPose;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import java.util.*;
import org.junit.Test;
import org.lwjgl.util.vector.Matrix4f;
import static org.junit.Assert.*;

public class VisualAdditionsTest {
    @Test public void temporalDecayIsFrameRateIndependentAndZeroReallyDisablesBlur(){
        assertEquals(0,MotionBlurModule.historyWeight(0,.016),0);
        double oneSecond=Math.pow(MotionBlurModule.historyWeight(.8,1/30D),30);
        for(int fps:new int[]{60,144,240})assertEquals(oneSecond,Math.pow(MotionBlurModule.historyWeight(.8,1D/fps),fps),1e-9);
        assertTrue(MotionBlurModule.historyWeight(.9,.016)<1);
    }
    @Test public void deathIsRequiredDeduplicatedAndScopedToRecentAttacks()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            KillEffectsModule m=new KillEffectsModule();f.base.modules.registerDynamic(m);m.setEnabled(true);
            PathTestFixture.set(Entity.class,f.player,"entityUniqueID",UUID.randomUUID());
            m.attack(f.player);assertTrue(m.deaths().isEmpty());
            m.capture(f.player,new HologramPose(new ModelPlayer(0,false),new Matrix4f(),new ResourceLocation("skin"),false,System.currentTimeMillis()));
            f.health(0);long now=System.currentTimeMillis();m.observe(f.player,now);assertEquals(1,m.deaths().size());m.observe(f.player,now+50);assertEquals(1,m.deaths().size());
            m.clear();m.observe(f.player,now);assertTrue(m.deaths().isEmpty());
            f.health(20);m.observe(f.player,now);f.health(0);m.observe(f.player,now+100);assertTrue(m.deaths().isEmpty());
            f.health(20);m.ownKills.setEnabled(false);m.observe(f.player,now+200);m.capture(f.player,new HologramPose(new ModelPlayer(0,false),new Matrix4f(),new ResourceLocation("skin"),false,now+200));f.health(0);m.observe(f.player,now+250);assertEquals(1,m.deaths().size());
            m.setEnabled(false);assertTrue(m.deaths().isEmpty());
        }
    }
    @Test public void keystrokesHasIndependentOptionalRowsAndPersistsTheLayout()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            HudModule h=new HudModule();f.modules.registerDynamic(h);assertTrue(h.getHudElements().getOptions().contains(HudManager.KEYSTROKES));assertFalse(h.getHudElements().isSelected(HudManager.KEYSTROKES));
            h.keystrokes.leftClick.setEnabled(false);assertTrue(h.keystrokes.rightClick.isEnabled());h.keystrokes.space.setEnabled(false);assertTrue(h.keystrokes.rightClick.isEnabled());
            HudManager manager=new HudManager(new java.io.File("build/keystrokes-unit"));HudManager.HudElement key=manager.getElement(HudManager.KEYSTROKES);key.setScale(1.35F);key.setTheme("Lavalamp");
            HudManager copy=new HudManager(new java.io.File("build/keystrokes-unit"));copy.fromJson(manager.toJson());assertEquals(1.35F,copy.getElement(HudManager.KEYSTROKES).getScale(),0);assertEquals("Lavalamp",copy.getElement(HudManager.KEYSTROKES).getTheme());
        }
    }
}
