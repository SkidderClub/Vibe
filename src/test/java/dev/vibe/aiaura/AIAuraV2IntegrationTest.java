package dev.vibe.aiaura;

import dev.vibe.combat.AuraRotation;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.Setting;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.*;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AIAuraV2IntegrationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Before @After public void forget() {
        AimModel.detach();
        AimModel.clear();
    }

    @Test public void settingsAndMutualExclusionWithBothOtherAuras() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            f.mc.thePlayer = null;
            PathTestFixture.set(Minecraft.class, f.mc, "mcDataDir", temp.newFolder());
            KillAuraModule ordinary = new KillAuraModule();
            AIAuraModule ai = new AIAuraModule();
            AIAuraV2Module v2 = new AIAuraV2Module();
            f.modules.registerDynamic(ordinary);
            f.modules.registerDynamic(ai);
            f.modules.registerDynamic(v2);
            List<String> names = new ArrayList<>();
            for (Setting<?> setting : v2.getSettings()) names.add(setting.getRawName());
            assertEquals(Arrays.asList("Attack Range", "Rotation Range", "Pre-Swing", "Aim", "Rotation", "Clicking", "CPS", "Block Hit", "Hit Timing (%)", "Learn",
                    "Learn From Recordings", "Reaction (ms)",
                    "Tracking Delay (ms)", "Miss Rate (%)", "Field Of View", "Record Fights", "Reset Learned"), names);
            v2.setEnabled(true);
            assertSame(v2, KillAuraModule.activeController());
            ordinary.setEnabled(true);
            assertFalse(v2.isEnabled());
            v2.setEnabled(true);
            assertFalse(ordinary.isEnabled());
            ai.setEnabled(true);
            assertFalse(v2.isEnabled());
            assertSame(ai, KillAuraModule.activeController());
            v2.setEnabled(true);
            assertFalse(ai.isEnabled());
            assertEquals("aiaurav2", v2.getId());
        }
    }

    @Test public void turnsOntoThemAndHitsThemWhileClicking() throws Exception {
        fightUntilTheFirstHit();
    }

    @Test public void withYourHitsPaceAndClickingLearnedItStillTurnsOntoThemAndHits() throws Exception {
        Random r = new Random(7);
        for (int i = 0; i < AimModel.ENOUGH; i++) AimModel.add(r.nextFloat() - .5F, r.nextFloat() - .5F);
        for (int i = 0; i < AimModel.PACED + 50; i++) {
            float off = (float) Math.expm1(r.nextFloat() * 4.5F);
            AimModel.pace(off, off * .5F, off * .05F);
        }
        for (int i = 0; i < AimModel.CLICKS + 50; i++) AimModel.click(60F + r.nextInt(50));
        assertTrue(AimModel.ready() && AimModel.paced() && AimModel.rhythm());
        fightUntilTheFirstHit();
    }

    /** Ticks of 20 ms, real time: the clicking is on wall-clock deadlines. */
    private void fightUntilTheFirstHit() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            Fixture w = fixture(f);
            AIAuraV2Module v2 = new AIAuraV2Module();
            MoveFixModule fix = new MoveFixModule();
            f.modules.registerDynamic(v2);
            f.modules.registerDynamic(fix);
            ((NumberSetting) get(AIAuraV2Module.class, v2, "reaction")).setValue(0.0D);
            PathTestFixture.set(Module.class, v2, "enabled", true);
            w.self.rotationYaw = 60F;                    // they are 60 degrees to the right of the view
            Method click = Minecraft.class.getDeclaredMethod("clickMouse");
            click.setAccessible(true);
            Field missDelay = Minecraft.class.getDeclaredField("leftClickCounter");
            missDelay.setAccessible(true);
            boolean hit = false;
            for (int tick = 0; tick < 150 && !hit; tick++) {
                fix.beginRotationTick();
                v2.tickStart();
                fix.tick();
                w.queue.packets.clear();
                KillAuraModule.prepareInputHook();
                while (f.mc.gameSettings.keyBindAttack.isPressed()) click.invoke(f.mc);
                vanillaMissDelay(f.mc, missDelay);
                for (Packet<?> packet : w.queue.packets) if (packet instanceof C02PacketUseEntity) hit = true;
                v2.tickEnd();
                Thread.sleep(20L);
            }
            assertSame(w.enemy, v2.getTarget());
            assertTrue("it hits once the aim is on them", hit);
            assertTrue("the aim turned onto them", Math.abs(MathHelper.wrapAngleTo180_float(fix.getRotationYaw())) < 20F);
            assertTrue("silent: MoveFix sends the aim", fix.ownsRotation(v2.getId()));
            assertEquals("and the camera is left free", 60F, w.self.rotationYaw, 0F);
            float turned = 0F, furthest = 0F;
            for (int tick = 0; tick < 60; tick++) {
                float yaw = fix.getRotationYaw();
                fix.beginRotationTick();
                v2.tickStart();
                fix.tick();
                KillAuraModule.prepareInputHook();
                while (f.mc.gameSettings.keyBindAttack.isPressed()) click.invoke(f.mc);
                vanillaMissDelay(f.mc, missDelay);
                v2.tickEnd();
                turned += Math.abs(MathHelper.wrapAngleTo180_float(fix.getRotationYaw() - yaw));
                furthest = Math.max(furthest, Math.abs(MathHelper.wrapAngleTo180_float(fix.getRotationYaw())));
                Thread.sleep(20L);
            }
            // they are 2.2 blocks off, half of them 10 degrees wide
            assertTrue("on them, and they stand still, the hand only drifts - degrees a tick: " + turned / 60F, turned / 60F < 1.5F);
            assertTrue("and stays about them: " + furthest, furthest < 15F);
            // Rotation: Camera - the aim is the camera, and all of your own mouse stays in it
            ((ModeSetting) get(AIAuraV2Module.class, v2, "look")).setValue("Camera");
            for (int tick = 0; tick < 40; tick++) {      // taken up from where the camera was, 60 to the right, and back onto them
                fix.beginRotationTick();
                v2.tickStart();
                v2.tickEnd();
                Thread.sleep(20L);
            }
            assertFalse("no silent rotation", fix.ownsRotation(v2.getId()));
            assertTrue("the camera is on them: " + w.self.rotationYaw, Math.abs(MathHelper.wrapAngleTo180_float(w.self.rotationYaw)) < 20F);
            float before = w.self.rotationYaw;
            w.self.rotationYaw += 20F;                   // your mouse, between two ticks
            fix.beginRotationTick();
            v2.tickStart();
            v2.tickEnd();
            assertTrue("all of your own mouse stays in the aim: " + (w.self.rotationYaw - before), w.self.rotationYaw - before > 10F);
            assertEquals("vanilla's ten ticks after a swing at the air are kept while it fights", 10, AutoClickerModule.missCooldownHook(10));
        }
    }

    /**
     * Vanilla 1.8.9: clickMouse sets the miss delay after a swing at the air, runTick counts it down once a tick,
     * and sendClickBlockToController puts it back to 0 at the end of every tick the button is not held.
     */
    private static void vanillaMissDelay(Minecraft mc, Field missDelay) throws Exception {
        int left = missDelay.getInt(mc);
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) missDelay.setInt(mc, 0);
        else if (left > 0) missDelay.setInt(mc, left - 1);
    }

    @Test public void clicksKeepTheirCpsAcrossTicksAndTwoCanFallInOne() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            Fixture w = fixture(f);
            AIAuraV2Module v2 = new AIAuraV2Module();
            f.modules.registerDynamic(v2);
            for (int i = 0; i < AimModel.CLICKS; i++) AimModel.click(i % 2 == 0 ? 20F : 135F);   // 12.9 a second, unevenly; 155 ms a pair, so the pairs drift across the ticks
            set(KillAuraModule.class, v2, "target", w.enemy);
            Method due = KillAuraModule.class.getDeclaredMethod("clickDue", long.class), poll = KillAuraModule.class.getDeclaredMethod("pollClicks", long.class);
            due.setAccessible(true);
            poll.setAccessible(true);
            int total = 0, most = 0;
            for (long now = 1000L; now <= 4000L; now += 50L) {
                int clicks = (Boolean) due.invoke(v2, now) ? (Integer) poll.invoke(v2, now) : 0;
                total += clicks;
                most = Math.max(most, clicks);
            }
            // three seconds at 12.9 a second; each gap rounded up to whole ticks it would be 30
            assertTrue("clicks over three seconds: " + total, total >= 36 && total <= 44);
            assertTrue("a 20 ms gap puts two clicks in one tick", most >= 2);
        }
    }

    @Test public void withYourSwingsLearnedItSwingsOnYourTicks() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            Fixture w = fixture(f);
            AIAuraV2Module v2 = new AIAuraV2Module();
            f.modules.registerDynamic(v2);
            // yours: 2, 2, 4 ticks apart, and a double click in one tick - never 3 apart
            for (int i = 0; AimModel.swings() < AimModel.SWINGS; i++) AimModel.swing(new int[]{2, 2, 4, 2, 0}[i % 5]);
            assertTrue(AimModel.swingRhythm());
            set(KillAuraModule.class, v2, "target", w.enemy);
            Method due = KillAuraModule.class.getDeclaredMethod("clickDue", long.class), poll = KillAuraModule.class.getDeclaredMethod("pollClicks", long.class);
            due.setAccessible(true);
            poll.setAccessible(true);
            int[] apart = new int[8];
            int last = -1, doubles = 0;
            for (int tick = 0; tick < 400; tick++) {
                int clicks = (Boolean) due.invoke(v2, 1000L + tick * 50L) ? (Integer) poll.invoke(v2, 1000L + tick * 50L) : 0;
                if (clicks == 0) continue;
                if (last >= 0) apart[Math.min(7, tick - last)]++;
                if (clicks > 1) doubles++;
                last = tick;
            }
            assertEquals("never 3 ticks apart, as you never were", 0, apart[3]);
            assertTrue("2 and 4 apart as you were: " + Arrays.toString(apart), apart[2] > apart[4] && apart[4] > 0);
            assertTrue("and your two in a tick: " + doubles, doubles > 10);
        }
    }

    @Test public void blockHitTapsAfterAHitAndClicksLingerAndASwitchWaits() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            Fixture w = fixture(f);
            AIAuraV2Module v2 = new AIAuraV2Module();
            f.modules.registerDynamic(v2);
            set(KillAuraModule.class, v2, "target", w.enemy);
            ((AuraRotation) get(KillAuraModule.class, v2, "rotation")).reset(0F, 0F);   // straight at them, 2.2 blocks off
            Method block = KillAuraModule.class.getDeclaredMethod("ownBlock", long.class), due = KillAuraModule.class.getDeclaredMethod("clickDue", long.class);
            block.setAccessible(true);
            due.setAccessible(true);
            long now = 1000L;
            dev.vibe.setting.RangeSetting timing = (dev.vibe.setting.RangeSetting) get(AIAuraV2Module.class, v2, "hitTiming");
            set(AIAuraV2Module.class, v2, "lastAsked", now);
            set(AIAuraV2Module.class, v2, "nextClick", now + 30.0D);
            set(AIAuraV2Module.class, v2, "reopened", true);
            timing.setMin(0.0D);
            timing.setMax(0.0D);
            assertFalse("Hit Timing 0%: a click 30 ms off waits for its time, even the tick they can be hit again", (Boolean) due.invoke(v2, now));
            timing.setMax(100.0D);
            timing.setMin(100.0D);
            assertTrue("100%: it is made then", (Boolean) due.invoke(v2, now));
            set(AIAuraV2Module.class, v2, "nextClick", now + 80.0D);
            assertFalse("a click not coming before the next tick stays where it is", (Boolean) due.invoke(v2, now));
            set(AIAuraV2Module.class, v2, "reopened", false);
            set(AIAuraV2Module.class, v2, "nextClick", now + 30.0D);
            assertFalse("and any other tick nothing is moved", (Boolean) due.invoke(v2, now));
            timing.setMin(0.0D);
            int early = 0;
            for (int i = 0; i < 2000; i++) {             // 0 to 100%: drawn afresh each time, half of them on average
                set(AIAuraV2Module.class, v2, "nextClick", now + 30.0D);
                set(AIAuraV2Module.class, v2, "reopened", true);
                if ((Boolean) due.invoke(v2, now)) early++;
            }
            assertTrue("0-100%: " + early + " of 2000", early > 850 && early < 1150);

            assertNull("Block Hit off: blocking is left alone", block.invoke(v2, now));
            ((dev.vibe.setting.BooleanSetting) get(AIAuraV2Module.class, v2, "blockHit")).setEnabled(true);
            assertEquals("no fresh hit, no tap", Boolean.FALSE, block.invoke(v2, now));
            boolean tapped = false;
            for (int i = 0; i < 100 && !tapped; i++) {
                set(AIAuraV2Module.class, v2, "struck", true);
                tapped = (Boolean) block.invoke(v2, now);
            }
            assertTrue("a fresh hit on them is followed by a tap", tapped);
            assertEquals("and the tap is over within 150 ms", Boolean.FALSE, block.invoke(v2, now + 150L));

            Method range = KillAuraModule.class.getDeclaredMethod("clickRange"), changed = KillAuraModule.class.getDeclaredMethod("targetChanged", EntityLivingBase.class, EntityLivingBase.class);
            range.setAccessible(true);
            changed.setAccessible(true);
            assertEquals("in reach just now, the hand clicks on out to the rotation range", 6.0D, (Double) range.invoke(v2), 1e-9);
            set(AIAuraV2Module.class, v2, "engaged", true);
            changed.invoke(v2, w.enemy, w.enemy);
            assertTrue("a switch waits most of a reaction before the aim moves", (Long) get(AIAuraV2Module.class, v2, "holdUntil") >= System.nanoTime() / 1000000L + 50L);
        }
    }

    @Test public void yourOwnHitAndClickingAreLearned() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            Fixture w = fixture(f);
            AIAuraV2Module v2 = new AIAuraV2Module();
            f.modules.registerDynamic(v2);
            AIAuraV2Module.learnTick();                  // someone is near your crosshair: a fight
            // eye (.5, 65.62, .5); their box, with its 0.1 border, nearest point (.5, 65.62, 2.685); your crosshair a little right and low
            f.mc.objectMouseOver = new MovingObjectPosition(w.enemy, new Vec3(.65, 65.0, 2.6));
            AIAuraV2Module.learnAttack(w.enemy);
            assertEquals(1, AimModel.size());
            float[] spot = AimModel.pick();
            assertEquals("across, in shares of their half width", -0.5F, spot[0], 1e-4);
            assertEquals("up, in shares of their half height", -0.62F / 0.9F, spot[1], 1e-4);
            long t = 5_000_000_000L;
            AIAuraV2Module.learnClick(t);
            AIAuraV2Module.learnClick(t + 80_000_000L);
            AIAuraV2Module.learnClick(t + 80_000_000L + 2_000_000_000L);    // two seconds later: a new burst
            assertEquals(1, AimModel.clicks());
            assertEquals(12.5F, AimModel.cps(), 1e-3);
            PathTestFixture.set(Module.class, v2, "enabled", true);
            AIAuraV2Module.learnAttack(w.enemy);
            AIAuraV2Module.learnClick(t + 3_000_000_000L);
            AIAuraV2Module.learnClick(t + 3_090_000_000L);
            assertEquals("nothing is learned while an aura has the aim", 1, AimModel.size());
            assertEquals(1, AimModel.clicks());
        }
    }

    private static final class Fixture {
        AIAuraIntegrationTest.Player self, enemy;
        AIAuraIntegrationTest.Queue queue;
    }

    private Fixture fixture(PathTestFixture f) throws Exception {
        PathTestFixture.set(Minecraft.class, f.mc, "mcDataDir", temp.newFolder());
        AIAuraIntegrationTest.TestWorld world = PathTestFixture.allocate(AIAuraIntegrationTest.TestWorld.class);
        PathTestFixture.set(net.minecraft.world.World.class, world, "loadedEntityList", new ArrayList<Entity>());
        f.mc.theWorld = world;
        Fixture w = new Fixture();
        w.queue = PathTestFixture.allocate(AIAuraIntegrationTest.Queue.class);
        w.queue.packets = new ArrayList<Packet<?>>();
        w.self = player(world, 1, .5, 64, .5);
        w.enemy = player(world, 2, .5, 64, 3);
        w.self.inventory = new InventoryPlayer(w.self);
        w.self.inventory.currentItem = 0;
        w.self.movementInput = new MovementInput();
        w.self.onGround = true;
        PathTestFixture.set(EntityPlayerSP.class, w.self, "sendQueue", w.queue);
        PathTestFixture.set(EntityPlayerSP.class, w.self, "mc", f.mc);
        f.mc.thePlayer = w.self;
        f.mc.inGameHasFocus = true;
        f.mc.displayWidth = 960;
        f.mc.displayHeight = 600;
        f.mc.gameSettings.mouseSensitivity = .5F;
        f.mc.gameSettings.fovSetting = 70;
        PathTestFixture.set(Minecraft.class, f.mc, "renderViewEntity", w.self);
        f.mc.playerController = new PlayerControllerMP(f.mc, w.queue);
        f.filters.getIgnoreTeammates().setEnabled(false);
        return w;
    }

    private static AIAuraIntegrationTest.Player player(AIAuraIntegrationTest.TestWorld world, int id, double x, double y, double z) throws Exception {
        AIAuraIntegrationTest.Player p = PathTestFixture.allocate(AIAuraIntegrationTest.Player.class);
        p.setEntityId(id);
        p.worldObj = world;
        p.posX = p.lastTickPosX = p.prevPosX = x;
        p.posY = p.lastTickPosY = p.prevPosY = y;
        p.posZ = p.lastTickPosZ = p.prevPosZ = z;
        p.width = .6F;
        p.height = 1.8F;
        p.setEntityBoundingBox(new AxisAlignedBB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3));
        DataWatcher watcher = new DataWatcher(p);
        watcher.addObject(6, 20F);
        PathTestFixture.set(Entity.class, p, "dataWatcher", watcher);
        net.minecraft.entity.ai.attributes.ServersideAttributeMap attributes = new net.minecraft.entity.ai.attributes.ServersideAttributeMap();
        attributes.registerAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20);
        PathTestFixture.set(EntityLivingBase.class, p, "attributeMap", attributes);
        world.loadedEntityList.add(p);
        return p;
    }

    private static Object get(Class<?> type, Object object, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
