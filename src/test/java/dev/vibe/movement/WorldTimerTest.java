package dev.vibe.movement;

import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.Category;
import dev.vibe.module.impl.world.TimerModule;
import dev.vibe.setting.NumberSetting;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldTimerTest {
    @Test public void sliderUpdatesLiveAndLeavingTheWorldRestoresTheTimer() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            TimerModule module = new TimerModule();
            NumberSetting speed = (NumberSetting) module.getSettings().get(0);
            assertEquals(Category.WORLD, module.getCategory());
            assertEquals(1.2, speed.getDouble(), .0001);
            assertEquals(.1, speed.getMinimum(), 0);
            assertEquals(10, speed.getMaximum(), 0);
            module.setEnabled(true);
            try {
                assertEquals(1.2F, CombatTimerAccess.getSpeed(), 0);
                assertEquals(1, CombatTimerAccess.getBaseSpeed(), 0);
                speed.setValue(.1);
                module.tick();
                assertEquals(.1F, CombatTimerAccess.getSpeed(), 0);
                speed.setValue(10.0);
                module.tick();
                assertEquals(10, CombatTimerAccess.getSpeed(), 0);
                f.mc.theWorld = null;
                module.tick();
                assertEquals(1, CombatTimerAccess.getSpeed(), 0);
                f.mc.theWorld = f.world;
                CombatTimerAccess.setSpeed(1.5F);
                module.tick();
                assertEquals(10, CombatTimerAccess.getSpeed(), 0);
            } finally {
                module.setEnabled(false);
            }
            assertEquals(1.5F, CombatTimerAccess.getSpeed(), 0);
        }
    }
}
