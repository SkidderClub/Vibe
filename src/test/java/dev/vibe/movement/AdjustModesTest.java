package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.CriticalsModule;
import dev.vibe.module.impl.movement.FlyModule;
import dev.vibe.module.impl.movement.PhaseModule;
import dev.vibe.network.PacketDelayService;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.ModeSetting;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.junit.Test;

import static org.junit.Assert.*;

public class AdjustModesTest {
    @Test public void criticalsUseReferenceOffsetsAndServerFallWithoutMovingThePlayer() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final CriticalsModule criticals = register(new CriticalsModule());
            criticals.mode.setValue("RewoundAdjust");
            criticals.setEnabled(true);
            fixture.player.ticksExisted = 40;
            walk(fixture);
            final C03PacketPlayer packet = lastMovement(fixture);
            assertEquals(64.0301, packet.getPositionY(), 1E-10);
            assertFalse(packet.isOnGround());
            assertEquals(64, fixture.player.posY, 0);
            assertEquals(0, fixture.player.fallDistance, 0);
            walk(fixture);
            assertEquals(64, lastMovement(fixture).getPositionY(), 0);
            walk(fixture);
            assertFalse(lastMovement(fixture).isOnGround());
            assertFalse(lastMovement(fixture).isMoving());

            fixture.position(60.9);
            fixture.player.onGround = false;
            walk(fixture);
            assertTrue("Ground before predicted server fall reaches three blocks", lastMovement(fixture).isOnGround());
            criticals.setEnabled(false);
            fixture.player.onGround = true;
            walk(fixture);
            assertTrue(lastMovement(fixture).isOnGround());
        }
    }

    @Test public void criticalsYieldToJumpMiningAndFlightAndResetAcrossPlayers() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final CriticalsModule criticals = register(new CriticalsModule());
            criticals.mode.setValue("RewoundAdjust");
            criticals.setEnabled(true);
            fixture.player.motionY = 0.42;
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(64, RewoundHooks.walkingY(64), 0);
            assertTrue(RewoundHooks.ground(fixture.player, true));
            RewoundHooks.endWalking();
            fixture.player.motionY = 0;
            PathTestFixture.set(PlayerControllerMP.class, fixture.mc.playerController, "isHittingBlock", true);
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(64, RewoundHooks.walkingY(64), 0);
            RewoundHooks.endWalking();
            PathTestFixture.set(PlayerControllerMP.class, fixture.mc.playerController, "isHittingBlock", false);

            final FlyModule fly = register(new FlyModule());
            fly.setEnabled(true);
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(64, RewoundHooks.walkingY(64), 0);
            assertTrue(RewoundHooks.ground(fixture.player, true));
            RewoundHooks.endWalking();
            fly.setEnabled(false);
            criticals.setEnabled(false);
            criticals.setEnabled(true);
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(64.0301, RewoundHooks.walkingY(64), 1E-10);
            RewoundHooks.endWalking();
            criticals.setEnabled(false);
        }
    }

    @Test public void flightNoDamagePathUsesReferenceFrictionAndRestoresTimer() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final FlyModule fly = flight();
            fly.rewoundDisabler.setValue(false);
            fly.rewoundDamage.setValue("None");
            fixture.player.ticksExisted = 40;
            fly.setEnabled(true);
            fixture.player.posZ = 0.2;
            RewoundHooks.beforeWalking(fixture.player);
            RewoundHooks.endWalking();
            fixture.player.onGround = false;
            final double expected = Math.max(Math.max(0.2 - 0.2 / 160,
                    0.2 - (0.2 - 0.2873) / 33.3), 0.2 - (0.2 - 0.2873) * 0.02) - 0.00001;
            final double[] move = RewoundHooks.move(fixture.player, 0, -0.08, 0);
            assertEquals(Math.max(expected, 0.2873), move[2], 1E-12);
            assertEquals(0, move[1], 0);
            assertFalse(fixture.player.capabilities.isFlying);
            assertEquals(2, CombatTimerAccess.getSpeed(), 0);
            fixture.player.movementInput.moveForward = 0;
            assertEquals(0, RewoundHooks.move(fixture.player, 0, 0, 1)[2], 0);
            fly.setEnabled(false);
            assertEquals(1, CombatTimerAccess.getSpeed(), 0);
            assertNull(RewoundHooks.move(fixture.player, 1, 2, 3));
        }
    }

    @Test public void hoverRejectsEarlyCorrectionsAndExpiresThreeSecondsAfterSuccess() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final FlyModule fly = flight();
            RewoundHooks.preUpdate(fixture.player);
            fixture.player.onGround = false;
            fixture.player.movementInput.moveForward = 0;
            fly.setEnabled(true);
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(64.1, fixture.player.posY, 1E-10);
            assertArrayEquals(new double[] { 0, 0, 0 }, RewoundHooks.move(fixture.player, 1, 2, 3), 0);
            RewoundHooks.correction(correction());
            assertFalse(fly.isEnabled());
            fly.setEnabled(true);
            RewoundHooks.beforeWalking(fixture.player);
            final AdjustFlight state = state(fly);
            field(AdjustFlight.class, "hoverStarted").setLong(state, System.currentTimeMillis() - 4000);
            RewoundHooks.correction(correction());
            assertTrue(fly.isEnabled());
            fixture.player.movementInput.moveForward = 1;
            assertEquals(4, RewoundHooks.move(fixture.player, 0, 0, 0)[2], 1E-10);
            final AdjustFlightTracker tracker = (AdjustFlightTracker) field(AdjustFlight.class, "tracker").get(state);
            field(AdjustFlightTracker.class, "activeUntil").setLong(tracker, System.currentTimeMillis() - 1);
            assertNull(RewoundHooks.move(fixture.player, 0, 0, 0));
            assertFalse(fly.isEnabled());
        }
    }

    @Test public void dropBlinksAndReleasesOnModeSwitchAndWorldExit() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final FlyModule fly = flight();
            fly.rewoundDisabler.setValue(false);
            fixture.player.onGround = false;
            fixture.player.fallDistance = 5;
            fly.setEnabled(true);
            assertEquals(4, RewoundHooks.move(fixture.player, 0, -1, 0)[2], 0);
            assertTrue(fly.rewoundBlinking());
            assertTrue(FlyModule.blockClickHook(false));
            mode(fly).setValue("Basic");
            fly.tick();
            assertFalse(fly.rewoundBlinking());
            mode(fly).setValue("Rewound");
            fly.tick();
            RewoundHooks.move(fixture.player, 0, 0, 0);
            fixture.mc.thePlayer = null;
            fly.maintainRewound();
            assertFalse(fly.isEnabled());
            assertFalse(fly.rewoundBlinking());
        }
    }

    @Test public void trackerReadsCopiedPayloadsAndFailsClosedOnMalformedData() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final AdjustFlightTracker tracker = new AdjustFlightTracker();
            final PacketBuffer location = new PacketBuffer(Unpooled.buffer());
            location.writeBoolean(true);
            location.writeVarIntToBuffer(1);
            location.writeString("server");
            location.writeBoolean(true);
            location.writeString("BEDWARS");
            location.writeBoolean(false);
            location.writeBoolean(true);
            location.writeString("solo");
            location.writeBoolean(false);
            final int index = location.readerIndex();
            tracker.receive(new S3FPacketCustomPayload("rewound:location", location), () -> { });
            assertEquals(index, location.readerIndex());
            location.release();
            tracker.tick();
            assertTrue(tracker.bedWars());
            assertFalse(tracker.canDamage());
            field(AdjustFlightTracker.class, "started").setLong(tracker, System.currentTimeMillis() - 6000);
            assertTrue(tracker.canDamage());
            final PacketBuffer malformed = new PacketBuffer(Unpooled.buffer());
            malformed.writeBoolean(true);
            tracker.receive(new S3FPacketCustomPayload("rewound:location", malformed), () -> { });
            malformed.release();
            tracker.tick();
            assertFalse(tracker.canDamage());
            tracker.outbound(new C03PacketPlayer());
            assertEquals(1, tracker.packetCount());
            tracker.clear();
            assertEquals(0, tracker.packetCount());
        }
    }

    @Test public void packetlessPhaseUsesReferenceStepAndOnlyRemovesBlocksAtFeetOrAbove() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final PhaseModule phase = register(new PhaseModule());
            phase.mode.setValue("Packetless");
            phase.setEnabled(true);
            phase.maintainAdjust();
            fixture.position(63.5);
            fixture.player.moveForward = 1;
            fixture.player.moveStrafing = 0;
            fixture.player.movementInput.jump = true;
            final double before = fixture.player.posZ;
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(0.39, fixture.player.posZ - before, 1E-12);
            assertEquals(0.525, fixture.player.motionY, 0);
            assertTrue(RewoundHooks.ground(fixture.player, false));
            assertTrue(fixture.player.isAirBorne);
            final AxisAlignedBB floor = new AxisAlignedBB(-1, 63, -1, 1, 64, 1);
            final AxisAlignedBB ceiling = new AxisAlignedBB(-1, 64, -1, 1, 65, 1);
            assertEquals(Collections.singletonList(floor), PhaseModule.collisions(Arrays.asList(floor, ceiling), fixture.player));
            assertFalse(fixture.player.noClip);
            phase.setEnabled(false);
            assertFalse(fixture.player.isAirBorne);
            assertSame("Other entities retain their collision list", Collections.emptyList(), PhaseModule.collisions(Collections.emptyList(), new Object()));
        }
    }

    @Test public void verticalPhaseUsesTwoTickDelayAndTheSourceEightyEightDegreeStep() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final PhaseModule phase = register(new PhaseModule());
            phase.mode.setValue("Vertical");
            phase.setEnabled(true);
            phase.maintainAdjust();
            fixture.position(63.5);
            final double before = fixture.player.posZ;
            RewoundHooks.beforeWalking(fixture.player);
            RewoundHooks.endWalking();
            assertEquals(before, fixture.player.posZ, 0);
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(0.031 * Math.sin(Math.toRadians(88)), fixture.player.posZ - before, 1E-12);
            assertTrue(RewoundHooks.ground(fixture.player, false));
            RewoundHooks.endWalking();
            phase.mode.setValue("Watchdog");
            phase.maintainAdjust();
            assertFalse(fixture.player.isAirBorne);
            phase.setEnabled(false);
        }
    }

    @Test public void blinkPhaseStartsOnlyInTheCountdownWindowAndReleasesForCommands() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final PhaseModule phase = register(new PhaseModule());
            phase.mode.setValue("Blink");
            phase.setEnabled(true);
            phase.maintainAdjust();
            final AdjustPhase state = (AdjustPhase) field(PhaseModule.class, "adjust").get(phase);
            field(AdjustPhase.class, "skyWars").setBoolean(state, true);
            field(AdjustPhase.class, "lobby").set(state, false);
            field(AdjustPhase.class, "initialFloor").set(state, 63);
            field(AdjustPhase.class, "countdownEnd").setLong(state, System.currentTimeMillis() + 3000);
            phase.preUpdate(fixture.player);
            assertFalse(phase.holdAdjust(new C03PacketPlayer()));
            field(AdjustPhase.class, "countdownEnd").setLong(state, System.currentTimeMillis() + 1000);
            phase.preUpdate(fixture.player);
            assertTrue(phase.holdAdjust(new C03PacketPlayer()));
            final AxisAlignedBB floor = new AxisAlignedBB(-1, 63, -1, 1, 64, 1);
            assertTrue(PhaseModule.collisions(Collections.singletonList(floor), fixture.player).isEmpty());
            fixture.position(62.9);
            phase.preUpdate(fixture.player);
            assertEquals(Collections.singletonList(floor), PhaseModule.collisions(Collections.singletonList(floor), fixture.player));
            assertFalse(phase.holdAdjust(new C01PacketChatMessage("/lobby")));
            assertFalse(phase.holdAdjust(new C03PacketPlayer()));
            phase.setEnabled(false);
        }
    }

    @Test public void flightDamageWaitSendsReferencePairsAndWaitsForOwnHurtStatus() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final FlyModule fly = flight();
            fly.rewoundDisabler.setValue(false);
            fly.rewoundDamage.setValue("Wait");
            fixture.player.ticksExisted = 40;
            fly.setEnabled(true);
            final AdjustFlight state = state(fly);
            final AdjustFlightTracker tracker = (AdjustFlightTracker) field(AdjustFlight.class, "tracker").get(state);
            field(AdjustFlightTracker.class, "inGame").setBoolean(tracker, true);
            field(AdjustFlightTracker.class, "started").setLong(tracker, System.currentTimeMillis() - 10000);
            fixture.player.posZ = 0.1;
            RewoundHooks.beforeWalking(fixture.player);
            assertEquals(101, fixture.queue.packets.size());
            final C03PacketPlayer first = (C03PacketPlayer) fixture.queue.packets.get(0);
            assertEquals(64.06249, first.getPositionY(), 1E-10);
            assertFalse(first.isOnGround());
            assertTrue(PacketDelayService.isUnmodified(first));
            assertTrue(((C03PacketPlayer) fixture.queue.packets.get(100)).isOnGround());
            assertEquals(0, RewoundHooks.move(fixture.player, 0, 0, 0)[2], 0);
            PathTestFixture.set(NetHandlerPlayClient.class, fixture.queue, "playerInfoMap", new HashMap<>());
            fly.observeInbound(new S19PacketEntityStatus(fixture.player, (byte) 2) {
                @Override public Entity getEntity(final World world) {
                    return fixture.player;
                }
            });
            fly.tick();
            assertEquals(1, RewoundHooks.move(fixture.player, 0, 0, 0)[2], 0);
            assertEquals(2.145, RewoundHooks.move(fixture.player, 0, 0, 0)[2], 1E-12);
            assertEquals((double) 0.42F, fixture.player.motionY, 0);
            fly.setEnabled(false);
            assertEquals(1, CombatTimerAccess.getSpeed(), 0);
        }
    }

    @Test public void blinkPhaseTracksSkyWarsLocationFloorAndCountdownPackets() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final PhaseModule phase = register(new PhaseModule());
            phase.mode.setValue("Blink");
            phase.setEnabled(true);
            phase.maintainAdjust();
            final ScoreObjective objective = new ScoreObjective(
                    new Scoreboard(), "sidebar", IScoreObjectiveCriteria.DUMMY);
            objective.setDisplayName("SKYWARS");
            phase.receive(new S3BPacketScoreboardObjective(objective, 0));
            phase.receive(team("team_1", "11/18/26 m123"));
            phase.receive(new S08PacketPlayerPosLook(10, 64, 10, 0, 0, Collections.emptySet()));
            phase.receive(team("team_6", "Starting in: §a1s"));
            phase.preUpdate(fixture.player);
            assertTrue(phase.holdAdjust(new C03PacketPlayer()));
            final AdjustPhase state = (AdjustPhase) field(PhaseModule.class, "adjust").get(phase);
            assertEquals(62, field(AdjustPhase.class, "initialFloor").get(state));
            phase.receive(team("team_6", ""));
            phase.preUpdate(fixture.player);
            assertTrue(phase.holdAdjust(new C03PacketPlayer()));
            field(AdjustPhase.class, "countdownEnd").setLong(state, System.currentTimeMillis() - 200);
            phase.preUpdate(fixture.player);
            assertFalse(phase.holdAdjust(new C03PacketPlayer()));
            phase.setEnabled(false);
            assertFalse(phase.holdAdjust(new C03PacketPlayer()));
        }
    }

    @Test public void phaseBuffersBothDirectionsAndReplaysBeforeACommand() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final PhaseModule phase = register(new PhaseModule());
            phase.mode.setValue("Blink");
            phase.setEnabled(true);
            phase.maintainAdjust();
            final AdjustPhase state = (AdjustPhase) field(PhaseModule.class, "adjust").get(phase);
            field(AdjustPhase.class, "blinking").setBoolean(state, true);
            final PacketDelayService service = PacketDelayService.getInstance();
            final Method outgoing = PacketDelayService.class.getDeclaredMethod("delayOutbound", Packet.class,
                    ChannelHandlerContext.class, ChannelPromise.class);
            final Method incoming = PacketDelayService.class.getDeclaredMethod("delayInbound", Packet.class,
                    ChannelHandlerContext.class);
            outgoing.setAccessible(true);
            incoming.setAccessible(true);
            final EmbeddedChannel channel = new EmbeddedChannel(new ChannelDuplexHandler() {
                @Override public void write(final ChannelHandlerContext context, final Object packet,
                                            final ChannelPromise promise) throws Exception {
                    if (!(boolean) outgoing.invoke(service, packet, context, promise))
                        context.write(packet, promise);
                }
                @Override public void channelRead(final ChannelHandlerContext context, final Object packet) throws Exception {
                    if (!(boolean) incoming.invoke(service, packet, context))
                        context.fireChannelRead(packet);
                }
            });
            final Object previousChannel = field(PacketDelayService.class, "installedChannel").get(service);
            final Object previousContext = field(PacketDelayService.class, "context").get(service);
            field(PacketDelayService.class, "installedChannel").set(service, channel);
            field(PacketDelayService.class, "context").set(service, channel.pipeline().firstContext());
            try {
                final C03PacketPlayer move = new C03PacketPlayer();
                final S08PacketPlayerPosLook correction = correction();
                assertFalse(channel.writeAndFlush(move).isDone());
                assertNull(channel.readOutbound());
                assertFalse(channel.writeInbound(correction));
                final C01PacketChatMessage command = new C01PacketChatMessage("/lobby");
                channel.writeOutbound(command);
                assertSame(move, channel.readOutbound());
                assertSame(command, channel.readOutbound());
                assertSame(correction, channel.readInbound());
                assertTrue(service.queuedOutbound(PacketDelayService.Owner.ADJUST_PHASE).isEmpty());
            } finally {
                phase.setEnabled(false);
                channel.finish();
                field(PacketDelayService.class, "installedChannel").set(service, previousChannel);
                field(PacketDelayService.class, "context").set(service, previousContext);
            }
        }
    }

    @Test public void pearlFlightThrowsAfterTheDownwardLookAndRestoresTheCameraAndSlot() throws Exception {
        try (RewoundModesTest.Fixture fixture = new RewoundModesTest.Fixture()) {
            final FlyModule fly = flight();
            fixture.player.inventory.mainInventory[3] = new ItemStack(new ItemEnderPearl() {
                @Override public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
                    stack.stackSize--;
                    return stack;
                }
            }, 2);
            fixture.player.rotationPitch = -12;
            fly.setEnabled(true);
            walk(fixture);
            assertEquals(-12, fixture.player.rotationPitch, 0);
            assertEquals(0, fixture.player.inventory.currentItem);
            assertEquals(1, fixture.player.inventory.mainInventory[3].stackSize);
            int look = -1, use = -1, slot = -1, restore = -1;
            for (int i = 0; i < fixture.queue.packets.size(); i++) {
                final Packet<?> packet = fixture.queue.packets.get(i);
                if (packet instanceof C03PacketPlayer && ((C03PacketPlayer) packet).getPitch() == 90)
                    look = i;
                if (packet instanceof C08PacketPlayerBlockPlacement)
                    use = i;
                if (packet instanceof C09PacketHeldItemChange) {
                    if (((C09PacketHeldItemChange) packet).getSlotId() == 3)
                        slot = i;
                    else if (((C09PacketHeldItemChange) packet).getSlotId() == 0)
                        restore = i;
                }
            }

            assertTrue(look >= 0 && slot > look && use > slot && restore > use);
            fly.setEnabled(false);
        }
    }

    private static S3EPacketTeams team(final String name, final String prefix) throws Exception {
        final PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeString(name);
            buffer.writeByte(2);
            buffer.writeString(name);
            buffer.writeString(prefix.substring(0, Math.min(16, prefix.length())));
            buffer.writeString(prefix.substring(Math.min(16, prefix.length())));
            buffer.writeByte(3);
            buffer.writeString("always");
            buffer.writeByte(15);
            final S3EPacketTeams packet = new S3EPacketTeams();
            packet.readPacketData(buffer);
            return packet;
        } finally {
            buffer.release();
        }
    }

    private static void walk(final RewoundModesTest.Fixture fixture) throws Exception {
        fixture.player.onUpdateWalkingPlayer();
        // The native-bytecode fixture shadows SP's private reporting fields.
        field(EntityPlayerSP.class, "lastReportedPosY").setDouble(fixture.player,
                field(fixture.player.getClass(), "lastReportedPosY").getDouble(fixture.player));
    }

    private static FlyModule flight() {
        final FlyModule fly = register(new FlyModule());
        mode(fly).setValue("Rewound");
        return fly;
    }

    private static ModeSetting mode(final FlyModule fly) {
        return (ModeSetting) fly.getSettings().stream().filter(setting -> setting.getName().equals("Mode")).findFirst().get();
    }

    private static <T extends Module> T register(final T module) {
        Vibe.getInstance().getModuleManager().registerDynamic(module);
        return module;
    }

    private static AdjustFlight state(final FlyModule fly) throws Exception {
        return (AdjustFlight) field(FlyModule.class, "rewound").get(fly);
    }

    private static Field field(final Class<?> type, final String name) throws Exception {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static C03PacketPlayer lastMovement(final RewoundModesTest.Fixture fixture) {
        C03PacketPlayer result = null;
        for (final Packet<?> packet : fixture.queue.packets)
            if (packet instanceof C03PacketPlayer)
                result = (C03PacketPlayer) packet;

        assertNotNull(result);
        return result;
    }

    private static S08PacketPlayerPosLook correction() {
        return new S08PacketPlayerPosLook(0, 64, 0, 0, 0, Collections.emptySet());
    }
}
