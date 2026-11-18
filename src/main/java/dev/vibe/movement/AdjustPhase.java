package dev.vibe.movement;

import dev.vibe.module.impl.movement.PhaseModule;
import dev.vibe.network.PacketDelayService;
import dev.vibe.pit.PitStatus;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;

/** Adjust's selectable Phase modes. Existing Watchdog/NCP state stays in PhaseModule. */
public final class AdjustPhase {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final PhaseModule module;
    private final ConcurrentLinkedQueue<Packet<?>> incoming = new ConcurrentLinkedQueue<>();
    private final Pattern countdown = Pattern.compile("(\\d{1,6}):(\\d{1,2})");
    private EntityPlayerSP owner;
    private Object world;
    private String mode;
    private boolean skyWars, largeGame, countdownActive, aboveFloor, reportedGround, oldAirBorne, airBorneOwned;
    private volatile boolean blinking;
    private Integer initialFloor, floor;
    private Boolean lobby;
    private long countdownEnd, gameStarted, sinkAt;
    private int ticks;

    public AdjustPhase(final PhaseModule module) {
        this.module = module;
    }

    public boolean selected() {
        return module.mode.is("Blink") || module.mode.is("Vertical") || module.mode.is("Packetless");
    }

    public void receive(final Packet<?> packet) {
        if (packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn || packet instanceof S08PacketPlayerPosLook
                || packet instanceof S3BPacketScoreboardObjective || packet instanceof S3EPacketTeams
                || packet instanceof S13PacketDestroyEntities)
            incoming.add(packet);
    }

    public void maintain() {
        if (owner != mc.thePlayer || world != mc.theWorld) {
            reset();
            skyWars = largeGame = countdownActive = false;
            initialFloor = null;
            lobby = null;
            gameStarted = countdownEnd = 0;
            owner = mc.thePlayer;
            world = mc.theWorld;
        }

        if (!module.isEnabled() || !selected() || !module.mode.getValue().equals(mode)) {
            reset();
            mode = module.isEnabled() ? module.mode.getValue() : null;
        }

        Packet<?> packet;
        while ((packet = incoming.poll()) != null)
            observe(packet);
    }

    public void reset() {
        release();
        if (airBorneOwned && owner != null)
            owner.isAirBorne = oldAirBorne;

        airBorneOwned = reportedGround = false;
        ticks = 0;
        sinkAt = System.currentTimeMillis();
    }

    public void release() {
        final boolean held = blinking;
        blinking = false;
        floor = null;
        aboveFloor = false;
        if (held)
            PacketDelayService.getInstance().flush(PacketDelayService.Owner.ADJUST_PHASE);
    }

    public boolean hold(final Packet<?> packet) {
        if (packet instanceof C01PacketChatMessage && ((C01PacketChatMessage) packet).getMessage().startsWith("/")) {
            if (blinking) {
                blinking = false;
                incoming.add(packet);
                PacketDelayService.getInstance().flush(PacketDelayService.Owner.ADJUST_PHASE);
            }

            return false;
        }

        return module.isEnabled() && module.mode.is("Blink") && blinking;
    }

    public void preUpdate() {
        maintain();
        if (!module.isEnabled() || !module.mode.is("Blink") || owner == null || !skyWars || !Boolean.FALSE.equals(lobby) || initialFloor == null) return;
        final long now = System.currentTimeMillis();
        if ((gameStarted != 0) != largeGame || gameStarted != 0 && now - gameStarted >= 12000) {
            release();
            return;
        }

        if (countdownEnd != 0) {
            final long remaining = countdownEnd - now;
            if (remaining < 500) {
                if (remaining < -100)
                    release();
            } else if (floor == null && module.blinkPhaseAt.getDouble() * 1000 >= remaining) {
                floor = initialFloor;
                blinking = true;
            }
        }

        if (!aboveFloor && floor != null && floor > mc.thePlayer.posY)
            aboveFloor = true;
    }

    public void beforeWalking(final boolean inside) {
        reportedGround = false;
        if (!module.isEnabled() || !selected() || owner != mc.thePlayer || world != mc.theWorld || owner == null) return;
        if (module.mode.is("Blink")) return;
        if (!airBorneOwned) {
            oldAirBorne = mc.thePlayer.isAirBorne;
            airBorneOwned = true;
        }

        if (module.mode.is("Packetless")) {
            mc.thePlayer.isAirBorne = true;
            if (!inside) return;
            final double radians = Math.toRadians(mc.thePlayer.rotationYaw + 90);
            final double forward = mc.thePlayer.moveForward * 0.39, strafe = mc.thePlayer.moveStrafing * 0.39;
            final double speed = Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
            if (AacMovementSupport.moving(mc.thePlayer))
                AacMovementSupport.strafe(mc.thePlayer, speed);
            else
                mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;

            mc.thePlayer.motionY = mc.thePlayer.movementInput.jump ? 0.525 : mc.thePlayer.movementInput.sneak ? -0.525 : 0;
            mc.thePlayer.setPosition(mc.thePlayer.posX + forward * Math.cos(radians) + strafe * Math.sin(radians),
                    mc.thePlayer.posY, mc.thePlayer.posZ + forward * Math.sin(radians) - strafe * Math.cos(radians));
            reportedGround = true;
            return;
        }

        if (!inside) return;
        reportedGround = true;
        mc.thePlayer.isAirBorne = true;
        if (mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer.posY == (int) mc.thePlayer.posY)
            mc.thePlayer.jump();

        ticks += 2;
        if (mc.thePlayer.movementInput.sneak && !AacMovementSupport.moving(mc.thePlayer)
                && System.currentTimeMillis() - sinkAt >= 100 && mc.thePlayer.posY == (int) mc.thePlayer.posY)
            mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY - 0.8, mc.thePlayer.posZ);

        if (ticks < 3) return;
        double forward = mc.thePlayer.movementInput.moveForward, strafe = mc.thePlayer.movementInput.moveStrafe;
        float yaw = mc.thePlayer.rotationYaw;
        if (forward != 0) {
            if (strafe > 0)
                yaw += forward <= 0 ? 45 : -45;
            else if (strafe < 0)
                yaw += forward <= 0 ? -45 : 45;
            forward = Math.signum(forward);
            strafe = 0;
        }

        final double radians = (yaw + 88.0F) * 0.0174532925199433;
        mc.thePlayer.setPosition(mc.thePlayer.posX + forward * 0.031 * Math.cos(radians) + strafe * 0.031 * Math.sin(radians),
                mc.thePlayer.posY, mc.thePlayer.posZ + forward * 0.031 * Math.sin(radians) - strafe * 0.031 * Math.cos(radians));
    }

    public boolean ground(final boolean vanilla) {
        return reportedGround || vanilla;
    }

    public void collisions(final List<AxisAlignedBB> boxes) {
        if (module.mode.is("Blink")) {
            if (!aboveFloor && floor != null)
                boxes.removeIf(box -> floor <= Math.floor(box.minY));
        } else if (module.mode.is("Vertical") || module.mode.is("Packetless"))
            boxes.removeIf(box -> Math.floor(box.minY) >= mc.thePlayer.getEntityBoundingBox().minY);
    }

    private void observe(final Packet<?> packet) {
        if (packet instanceof C01PacketChatMessage) {
            release();
            return;
        }

        if (packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn) {
            reset();
            skyWars = largeGame = countdownActive = false;
            initialFloor = null;
            lobby = null;
            gameStarted = countdownEnd = 0;
        } else if (packet instanceof S3BPacketScoreboardObjective) {
            final String title = PitStatus.plain(((S3BPacketScoreboardObjective) packet).func_149337_d());
            if (title.equalsIgnoreCase("SKYWARS"))
                skyWars = true;
        } else if (packet instanceof S08PacketPlayerPosLook) {
            final S08PacketPlayerPosLook position = (S08PacketPlayerPosLook) packet;
            if (skyWars && Boolean.FALSE.equals(lobby) && initialFloor == null && (gameStarted != 0) == largeGame
                    && position.getX() * position.getX() + position.getZ() * position.getZ() > 32)
                initialFloor = (int) position.getY() - 2;
        } else if (packet instanceof S13PacketDestroyEntities) {
            if (skyWars && gameStarted == 0)
                for (final int id : ((S13PacketDestroyEntities) packet).getEntityIDs())
                    if (id == -1234)
                        gameStarted = System.currentTimeMillis();
        } else if (packet instanceof S3EPacketTeams && skyWars) {
            final S3EPacketTeams team = (S3EPacketTeams) packet;
            if (!team.getName().startsWith("team_")) return;
            final String text = team.getPrefix() + team.getSuffix(), plain = PitStatus.plain(text);
            if (lobby == null && team.getColor() == 15 && team.getFriendlyFlags() == 3
                    && (team.getPlayers() == null || team.getPlayers().isEmpty())) {
                final String prefix = team.getPrefix().trim();
                if ((prefix.contains("/24 ") || prefix.contains("/25 ") || prefix.contains("/26 ") || prefix.contains("/27 "))
                        && prefix.chars().filter(character -> character == '/').count() >= 2) {
                    final String server = PitStatus.plain(prefix.substring(prefix.lastIndexOf(' ') + 1));
                    if (!server.isEmpty())
                        lobby = server.charAt(0) == 'L';
                }
            }

            if (!Boolean.FALSE.equals(lobby)) return;
            if ((team.getPlayers() == null || team.getPlayers().isEmpty()) && team.getPrefix().isEmpty()) {
                countdownActive = false;
                return;
            }

            final Matcher time = countdown.matcher(plain);
            if ((team.getPlayers() == null || team.getPlayers().isEmpty()) && time.find()) {
                if (!countdownActive)
                    countdownEnd = System.currentTimeMillis() + 1000L * (Long.parseLong(time.group(1)) * 60 + Long.parseLong(time.group(2))) + 1000;

                countdownActive = true;
                return;
            }

            if (gameStarted != 0) return;
            if (team.getName().equals("team_6")) {
                if (!text.matches(".*\u00a7a\\d{1,2}.*")) {
                    countdownEnd = 0;
                    return;
                }

                final Matcher numbers = Pattern.compile("\\d{1,6}").matcher(plain);
                long previous = 0, last = 0;
                while (numbers.find()) {
                    previous = last;
                    last = Long.parseLong(numbers.group());
                }

                countdownEnd = System.currentTimeMillis() + 1000 * (last + 60 * previous);
            } else if (text.contains(": §a") && text.contains("/")) {
                final String maximum = plain.substring(plain.indexOf('/') + 1).trim();
                if (maximum.matches("\\d{1,3}") && Integer.parseInt(maximum) >= 13)
                    largeGame = true;
            }
        }
    }
}
