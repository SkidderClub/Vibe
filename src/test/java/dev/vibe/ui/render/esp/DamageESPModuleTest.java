package dev.vibe.ui.render.esp;

import dev.vibe.module.impl.visual.DamageESPModule;
import dev.vibe.path.PathTestFixture;
import org.junit.*;
import static org.junit.Assert.*;

public class DamageESPModuleTest {
    private PathTestFixture f; private DamageESPModule module; private HealthPlayer target;
    @Before public void setup() throws Exception {
        f = new PathTestFixture(); module = new DamageESPModule(); module.setEnabled(true);
        target = HealthPlayer.create(f, 67, 2, 64, 0, 20);
    }
    @After public void cleanup() throws Exception { module.setEnabled(false); f.close(); }
    @Test public void onlyObservedHealthDropsProduceNumbersWithCorrectAmounts() {
        module.tick(0); assertTrue(module.markers().isEmpty()); target.hurtTime = 10; module.tick(50); assertTrue(module.markers().isEmpty());
        target.hp(16.5F); module.tick(100); assertEquals(1, module.markers().size()); assertEquals(3.5F, module.markers().get(0).amount, 0);
        assertEquals("-3.5", module.text(module.markers().get(0))); module.tick(150); assertEquals(1, module.markers().size());
        module.units.setValue("Hearts"); module.decimals.setValue(2D); assertEquals("-1.75", module.text(module.markers().get(0)));
    }
    @Test public void absorptionAndHealingUseRealDeltasWithoutFalseSettingsDamage() {
        target.shield = 4; module.tick(0); target.shield = 1; module.tick(50); assertEquals(3, module.markers().get(0).amount, 0);
        module.absorption.setEnabled(false); module.tick(100); assertEquals(1, module.markers().size());
        target.hp(15); module.tick(150); target.hp(18); module.tick(200); assertEquals(2, module.markers().size());
        module.healing.setEnabled(true); target.hp(20); module.tick(250); assertEquals(3, module.markers().size());
        assertTrue(module.markers().get(2).heal); assertEquals("+2.0", module.text(module.markers().get(2)));
    }
    @Test public void unloadsRangeFiltersInvalidHealthAndWorldChangesResetBaselines() {
        module.tick(0); f.world.loadedEntityList.remove(target); module.tick(50); target.hp(2); f.world.loadedEntityList.add(target); module.tick(100);
        assertTrue(module.markers().isEmpty()); target.hp(Float.NaN); module.tick(150); target.hp(1); module.tick(200); assertTrue(module.markers().isEmpty());
        target.posX = 200; module.tick(250); target.hp(.5F); target.posX = 2; module.tick(300); assertTrue(module.markers().isEmpty());
        target.hp(0); target.isDead = true; module.tick(350); assertEquals(.5, module.markers().get(0).amount, 0);
        f.mc.theWorld = null; module.tick(400); assertTrue(module.markers().isEmpty());
    }
    @Test public void boundedMarkersExpireAndDisableClearsEverything() {
        module.limit.setValue(8D); module.tick(0);
        for (int i = 1; i <= 100; i++) { target.hp(20 - i * .1F); module.tick(i); }
        assertEquals(8, module.markers().size()); module.tick(1600); assertTrue(module.markers().isEmpty());
        target.hp(1); module.tick(1650); assertEquals(1, module.markers().size()); module.setEnabled(false); assertTrue(module.markers().isEmpty());
    }
    @Test public void upwardMotionEasesToRestAndFadesAreBounded() {
        assertEquals(0, module.rise(0), 0); assertTrue(module.rise(.5) > 0);
        assertTrue(module.rise(.1) - module.rise(0) > module.rise(.9) - module.rise(.8));
        assertEquals(0, module.opacity(0), 0); assertEquals(0, module.opacity(1), 0);
        for (String mode : module.fade.getModes()) { module.fade.setValue(mode); for (int i = 0; i <= 100; i++) assertTrue(module.opacity(i / 100D) >= 0 && module.opacity(i / 100D) <= 1); }
        module.easing.setValue("Linear"); assertEquals(module.rise(1) / 2, module.rise(.5), .0001);
        module.easing.setValue("Exponential Out"); assertTrue(module.rise(.1) > module.rise(1) - module.rise(.9));
    }
    public static class HealthPlayer extends net.minecraft.client.entity.EntityPlayerSP {
        public float shield;
        private HealthPlayer() { super(null, null, null, null); }
        public void hp(float value) { getDataWatcher().updateObject(6, value); }
        public static HealthPlayer create(PathTestFixture f, int id, double x, double y, double z, float hp) throws Exception {
            HealthPlayer player = PathTestFixture.allocate(HealthPlayer.class); player.setEntityId(id); player.height = 1.8F;
            player.posX = x; player.posY = y; player.posZ = z; player.worldObj = f.world;
            net.minecraft.entity.DataWatcher watcher = new net.minecraft.entity.DataWatcher(player); watcher.addObject(6, hp);
            PathTestFixture.set(net.minecraft.entity.Entity.class, player, "dataWatcher", watcher);
            f.world.loadedEntityList.add(player); return player;
        }
        @Override public float getAbsorptionAmount() { return shield; }
    }
}
