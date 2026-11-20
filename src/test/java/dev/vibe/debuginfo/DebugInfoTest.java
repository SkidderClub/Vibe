package dev.vibe.debuginfo;

import dev.vibe.module.Category;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.DebugInfoModule;
import dev.vibe.setting.BooleanSetting;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.Assert.*;

public class DebugInfoTest {

    @After
    public void cleanup() {
        PacketTrafficTracker.reset();
    }

    @Test
    public void packetTrafficTrackerCountsOneSecondWindowAndResetsOnConnectionChange() {
        Object serverA = new Object();
        Object serverB = new Object();

        PacketTrafficTracker.observeInbound(serverA, 1000L);
        PacketTrafficTracker.observeInbound(serverA, 1200L);
        PacketTrafficTracker.observeOutbound(serverA, 1500L);
        assertEquals(2, PacketTrafficTracker.inboundPerSecond(serverA, 1800L));
        assertEquals(1, PacketTrafficTracker.outboundPerSecond(serverA, 1800L));
        assertEquals("Foreign channel must report zero", 0, PacketTrafficTracker.inboundPerSecond(serverB, 1800L));

        // After 1000ms the oldest inbound packet (at 1000ms) expires.
        assertEquals(1, PacketTrafficTracker.inboundPerSecond(serverA, 2100L));
        assertEquals(1, PacketTrafficTracker.outboundPerSecond(serverA, 2100L));

        // Switching to a new connection resets previous counts.
        PacketTrafficTracker.observeOutbound(serverB, 2200L);
        assertEquals(0, PacketTrafficTracker.inboundPerSecond(serverB, 2200L));
        assertEquals(1, PacketTrafficTracker.outboundPerSecond(serverB, 2200L));
        assertEquals(0, PacketTrafficTracker.outboundPerSecond(serverA, 2200L));
    }

    @Test
    public void horizontalBpsComputesTickVelocityAndRespectsTimerSpeed() {
        assertEquals(0.0D, DebugInfoSampler.computeHorizontalBps(10.0D, -5.0D, 10.0D, -5.0D, 1.0F), 1e-6);
        assertEquals(5.6D, DebugInfoSampler.computeHorizontalBps(0.0D, 0.0D, 0.28D, 0.0D, 1.0F), 1e-6);
        // 3-4-5 diagonal at 0.3 / 0.4 per tick = 0.5 blocks/tick = 10 b/s at 1.0x, 15 b/s at 1.5x.
        assertEquals(10.0D, DebugInfoSampler.computeHorizontalBps(1.0D, 2.0D, 1.3D, 2.4D, 1.0F), 1e-6);
        assertEquals(15.0D, DebugInfoSampler.computeHorizontalBps(1.0D, 2.0D, 1.3D, 2.4D, 1.5F), 1e-6);
        // Non-positive or NaN timer falls back to 1.0x safely.
        assertEquals(10.0D, DebugInfoSampler.computeHorizontalBps(1.0D, 2.0D, 1.3D, 2.4D, Float.NaN), 1e-6);
    }

    @Test
    public void everyMetricToggleIndividuallyControlsItsRow() {
        DebugInfoModule module = new DebugInfoModule();
        DebugInfoSnapshot snapshot = sampleMultiplayerSnapshot();

        List<BooleanSetting> metricToggles = Arrays.asList(
                module.getShowPing(),
                module.getShowTps(),
                module.getShowCps(),
                module.getShowBps(),
                module.getShowFps(),
                module.getShowCoords(),
                module.getShowFacing(),
                module.getShowTimer(),
                module.getShowPackets(),
                module.getShowMemory(),
                module.getShowServer(),
                module.getShowBiome(),
                module.getShowEntities(),
                module.getShowPlayerState(),
                module.getShowChunk(),
                module.getShowModules());

        // Turn all off -> zero rows.
        for (BooleanSetting toggle : metricToggles) {
            toggle.setEnabled(false);
        }
        assertTrue(snapshot.buildEntries(module).isEmpty());

        // Turn each on individually -> exactly 1 row per toggle.
        for (BooleanSetting toggle : metricToggles) {
            toggle.setEnabled(true);
            List<DebugInfoEntry> entries = snapshot.buildEntries(module);
            assertEquals("Toggle '" + toggle.getRawName() + "' should emit exactly one row", 1, entries.size());
            assertFalse(entries.get(0).label.isEmpty());
            assertFalse(entries.get(0).value.isEmpty());
            toggle.setEnabled(false);
        }

        // Turn all on -> 16 distinct rows.
        for (BooleanSetting toggle : metricToggles) {
            toggle.setEnabled(true);
        }
        List<DebugInfoEntry> all = snapshot.buildEntries(module);
        assertEquals(16, all.size());

        Map<String, String> byLabel = new HashMap<String, String>();
        for (DebugInfoEntry entry : all) {
            byLabel.put(entry.label, entry.value);
        }
        assertEquals("42 ms (±4)", byLabel.get("PING"));
        assertEquals("19.8", byLabel.get("TPS"));
        assertEquals("12 | 7", byLabel.get("CPS"));
        assertEquals("5.61 b/s", byLabel.get("BPS"));
        assertEquals("144 fps", byLabel.get("FPS"));
        assertEquals("128.5, 64.0, -300.2", byLabel.get("XYZ"));
        assertEquals("W (-X) (90.0 / -12.5)", byLabel.get("FACING"));
        assertEquals("1.00x", byLabel.get("TIMER"));
        assertEquals("22 TX / 64 RX", byLabel.get("PACKETS"));
        assertEquals("512 / 2048 MB (25%)", byLabel.get("RAM"));
        assertEquals("mc.hypixel.net", byLabel.get("SERVER"));
        assertEquals("Plains", byLabel.get("BIOME"));
        assertEquals("48 (6 players)", byLabel.get("ENTITIES"));
        assertEquals("Air | Y -0.15 | Hurt 4", byLabel.get("STATE"));
        assertEquals("8, -19 | Light 15/12", byLabel.get("CHUNK"));
        assertEquals("7 / 95", byLabel.get("MODULES"));
    }

    @Test
    public void singleplayerAndMissingTelemetryFormatGracefully() {
        DebugInfoModule module = new DebugInfoModule();
        module.getShowServer().setEnabled(true);
        module.getShowBiome().setEnabled(true);

        DebugInfoSnapshot local = new DebugInfoSnapshot(
                true, 0, 0, Double.NaN, 0, 0, 0.0D, 60,
                0, 64, 0, 0, 0, 1.0F, 0, 0, 256, 1024,
                "Singleplayer", "", 1, 1, true, 0.0D, 0, 0, 0, 0, 15, 1, 10);

        Map<String, String> byLabel = new HashMap<String, String>();
        for (DebugInfoEntry entry : local.buildEntries(module)) {
            byLabel.put(entry.label, entry.value);
        }
        assertEquals("Local", byLabel.get("PING"));
        assertEquals("--", byLabel.get("TPS"));
        assertEquals("Singleplayer", byLabel.get("SERVER"));
        assertEquals("--", byLabel.get("BIOME"));
    }

    @Test
    public void colorCodingThresholdsAndNeutralToggleWorkAsExpected() {
        assertEquals(DebugInfoSnapshot.COLOR_GOOD, DebugInfoSnapshot.pingColor(false, 45));
        assertEquals(DebugInfoSnapshot.COLOR_WARN, DebugInfoSnapshot.pingColor(false, 120));
        assertEquals(DebugInfoSnapshot.COLOR_BAD, DebugInfoSnapshot.pingColor(false, 250));
        assertEquals(DebugInfoSnapshot.COLOR_MUTED, DebugInfoSnapshot.pingColor(false, -1));

        assertEquals(DebugInfoSnapshot.COLOR_GOOD, DebugInfoSnapshot.tpsColor(19.5D));
        assertEquals(DebugInfoSnapshot.COLOR_WARN, DebugInfoSnapshot.tpsColor(16.0D));
        assertEquals(DebugInfoSnapshot.COLOR_BAD, DebugInfoSnapshot.tpsColor(11.0D));
        assertEquals(DebugInfoSnapshot.COLOR_MUTED, DebugInfoSnapshot.tpsColor(Double.NaN));

        assertEquals(DebugInfoSnapshot.COLOR_GOOD, DebugInfoSnapshot.fpsColor(120));
        assertEquals(DebugInfoSnapshot.COLOR_WARN, DebugInfoSnapshot.fpsColor(45));
        assertEquals(DebugInfoSnapshot.COLOR_BAD, DebugInfoSnapshot.fpsColor(20));

        assertEquals(DebugInfoSnapshot.COLOR_GOOD, DebugInfoSnapshot.memoryColor(50));
        assertEquals(DebugInfoSnapshot.COLOR_WARN, DebugInfoSnapshot.memoryColor(78));
        assertEquals(DebugInfoSnapshot.COLOR_BAD, DebugInfoSnapshot.memoryColor(92));

        assertEquals(DebugInfoSnapshot.COLOR_NEUTRAL, DebugInfoSnapshot.timerColor(1.0F));
        assertEquals(DebugInfoSnapshot.COLOR_ACCENT, DebugInfoSnapshot.timerColor(1.25F));
        assertEquals(DebugInfoSnapshot.COLOR_WARN, DebugInfoSnapshot.timerColor(0.75F));

        DebugInfoModule module = new DebugInfoModule();
        module.getColorCoded().setEnabled(false);
        for (DebugInfoEntry entry : sampleMultiplayerSnapshot().buildEntries(module)) {
            assertEquals("Disabling Color Values must keep all values neutral",
                    DebugInfoSnapshot.COLOR_NEUTRAL, entry.valueColor);
        }
    }

    @Test
    public void cardinalDirectionsWrapAnglesAccurately() {
        assertEquals("S (+Z)", DebugInfoSnapshot.cardinalDirection(0.0F));
        assertEquals("W (-X)", DebugInfoSnapshot.cardinalDirection(90.0F));
        assertEquals("N (-Z)", DebugInfoSnapshot.cardinalDirection(180.0F));
        assertEquals("E (+X)", DebugInfoSnapshot.cardinalDirection(-90.0F));
        assertEquals("S (+Z)", DebugInfoSnapshot.cardinalDirection(360.0F));
        assertEquals(-90.0F, DebugInfoSnapshot.wrapDegrees(270.0F), 1e-4F);
    }

    @Test
    public void moduleIsRegisteredInClientCategoryWithSensibleDefaults() throws Exception {
        assertTrue("ModuleManager constructor must register DebugInfoModule",
                moduleManagerConstructs("dev/vibe/module/impl/client/DebugInfoModule"));

        DebugInfoModule module = new DebugInfoModule();
        assertEquals("DebugInfo", module.getRawName());
        assertEquals("debuginfo", module.getId());
        assertEquals(Category.CLIENT, module.getCategory());
        assertEquals(DebugInfoModule.POS_TOP_LEFT, module.getPosition().getValue());
        assertTrue(module.getShowPing().isEnabled());
        assertTrue(module.getShowTps().isEnabled());
        assertTrue(module.getShowCps().isEnabled());
        assertTrue(module.getShowBps().isEnabled());
        assertTrue(module.getShowFps().isEnabled());
        assertTrue(module.getHideInF3().isEnabled());
        assertFalse(module.getShowServer().isEnabled());
    }

    private static DebugInfoSnapshot sampleMultiplayerSnapshot() {
        return new DebugInfoSnapshot(
                false, 42, 4, 19.8D, 12, 7, 5.61D, 144,
                128.5D, 64.0D, -300.2D, 450.0F, -12.5F, 1.0F,
                22, 64, 512L, 2048L, "mc.hypixel.net", "Plains",
                48, 6, false, -0.15D, 4, 8, -19, 12, 15, 7, 95);
    }

    private static boolean moduleManagerConstructs(String internalClassName) throws Exception {
        try (InputStream stream = ModuleManager.class.getResourceAsStream("/dev/vibe/module/ModuleManager.class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            for (Object item : node.methods) {
                MethodNode method = (MethodNode) item;
                if (!"<init>".equals(method.name)) {
                    continue;
                }
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.NEW && insn instanceof TypeInsnNode
                            && internalClassName.equals(((TypeInsnNode) insn).desc)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
