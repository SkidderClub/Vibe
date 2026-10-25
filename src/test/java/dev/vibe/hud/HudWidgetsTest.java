package dev.vibe.hud;

import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.client.HudModule;
import java.time.ZonedDateTime;
import net.minecraft.util.MovingObjectPosition;
import org.junit.Test;
import static org.junit.Assert.*;

public class HudWidgetsTest {
    @org.junit.Rule public org.junit.rules.TemporaryFolder temporary=new org.junit.rules.TemporaryFolder();
    @Test public void analogAndDigitalShareActualLocalTimeAndTwelveHourOptions() {
        ClockSettings settings=new ClockSettings(s->s);
        ZonedDateTime now=ZonedDateTime.parse("2026-10-24T15:07:42+02:00[Europe/Berlin]");
        assertEquals("15:07:42",ClockRenderer.time(settings,now));
        settings.seconds.setEnabled(false);assertEquals("15:07",ClockRenderer.time(settings,now));
        settings.twelveHour.setEnabled(true);assertEquals("3:07 PM",ClockRenderer.time(settings,now));
        settings.display.setValue("Analog");settings.diameter.setValue(104D);
        settings.date.setEnabled(true);assertEquals(132,ClockRenderer.height(settings));
    }
    @Test public void percentagesAndVerdictsUseTargetMaximumAndOptionalAbsorption() {
        TargetHudData sample=TargetHudData.sample();
        TargetHudData d=new TargetHudData(null,"Player",12,40,4,16,2,354,1,sample.id,sample.skin);
        assertEquals(.3F,d.fraction(),.00001F);assertEquals(16,d.total(true),0);
        assertEquals("Draw",d.verdict(true));assertEquals("Winning",d.verdict(false));
        assertEquals("Losing",new TargetHudData(null,"Player",18,20,0,16,0,0,0,sample.id,sample.skin).verdict(true));
    }
    @Test public void onlyTracksAttacksAndDoesNotRefreshTheTimerFromTheCrosshair() throws Exception {
        try(PathTestFixture f=new PathTestFixture()) {
            TargetHudSettings settings=new TargetHudSettings(s->s);settings.linger.setValue(2D);
            TargetHudTracker tracker=new TargetHudTracker();
            PathTestFixture.Player target=f.addPlayer(7,"Target",2,64,2);
            tracker.attack(target,1000);assertSame(target,tracker.resolve(f.mc,settings,1001));
            assertNull(tracker.resolve(f.mc,settings,3001));
            f.mc.objectMouseOver=new MovingObjectPosition(target);
            assertNull(tracker.resolve(f.mc,settings,4000));
            tracker.attack(target,4000);assertSame(target,tracker.resolve(f.mc,settings,4001));
            target.isDead=true;assertNull(tracker.resolve(f.mc,settings,4002));
            target.isDead=false;tracker.attack(target,5000);f.mc.theWorld=null;
            assertNull(tracker.resolve(f.mc,settings,5001));
        }
    }
    @Test public void newWidgetCanBeMovedScaledAndSavedAlongsideOldLayouts() throws Exception {
        try(PathTestFixture f=new PathTestFixture()) {
            HudModule hud=new HudModule();f.modules.registerDynamic(hud);
            assertTrue(hud.getHudElements().hasOption(HudManager.TARGET_HUD));
            HudManager manager=new HudManager(temporary.newFolder("hud-write"));
            manager.getElement(HudManager.TARGET_HUD).setScale(1.5F);
            manager.getElement(HudManager.TARGET_HUD).setTheme("LiquidGlass");
            HudManager restored=new HudManager(temporary.newFolder("hud-read"));
            restored.fromJson(manager.toJson());
            assertEquals(1.5F,restored.getElement(HudManager.TARGET_HUD).getScale(),0);
            assertNotNull(restored.getElement(HudManager.CLOCK));
            assertEquals("Digital",hud.clock.display.getValue());
            assertEquals(5,hud.targetHud.mode.getModes().size());
        }
    }
}
