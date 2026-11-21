package dev.vibe.movement;

import dev.vibe.chat.ClientChat;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.inventory.HotbarSwitches;
import dev.vibe.module.impl.movement.FlyModule;
import dev.vibe.network.PacketDelayService;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Adjust RewoundFlightMode, adapted to native move and walking phases. */
public final class AdjustFlight {
    private enum State { DAMAGE, DROP, PEARL, FIREBALL, HOVER, DISABLER }
    private final Minecraft mc = Minecraft.getMinecraft();
    private final FlyModule module;
    private final AdjustFlightTracker tracker = new AdjustFlightTracker();
    private State state;
    private EntityPlayerSP owner, trackedPlayer;
    private World world;
    private int stage, groundTicks, projectileSlot, projectileStage;
    private double lastDistance, lastX, lastZ, lastGroundY, moveSpeed;
    private long started, hoverStarted, boostUntil;
    private boolean waitingForDamage, timerOwned, rotated;
    private float originalPitch;
    private float previousTimer, lastTimer;
    private volatile boolean blinking;

    public AdjustFlight(final FlyModule module) {
        this.module = module;
    }

    public void outbound(final Packet<?> packet) {
        tracker.outbound(packet);
    }

    public void receive(final Packet<?> packet) {
        tracker.receive(packet, () -> {
            if (state == State.DAMAGE && stage == 0 && waitingForDamage) {
                waitingForDamage = false;
                stage = 1;
            }
        });
    }

    public void tick() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (owner != null)
                module.setEnabled(false);
            tracker.clear();
            trackedPlayer = null;
            return;
        }
        tracker.tick();
        if (owner != null && (owner != mc.thePlayer || world != mc.theWorld || owner.isDead)) {
            module.setEnabled(false);
            tracker.clear();
        }
    }

    public void observeGround() {
        if (trackedPlayer != mc.thePlayer) {
            trackedPlayer = mc.thePlayer;
            lastGroundY = Double.NEGATIVE_INFINITY;
        }

        if (mc.thePlayer.onGround)
            lastGroundY = mc.thePlayer.posY;
    }

    public void enable() {
        reset();
        owner = mc.thePlayer;
        world = mc.theWorld;
        if (owner == null) return;
        lastX = mc.thePlayer.posX;
        lastZ = mc.thePlayer.posZ;
        observeGround();
        started = System.currentTimeMillis();
        if (module.rewoundDisabler.isEnabled()) {
            if (tracker.active()) {
                state = State.DISABLER;
                return;
            }

            if (!mc.thePlayer.onGround && module.rewoundPacket.isEnabled() && !AacMovementSupport.moving(mc.thePlayer)
                    && (mc.thePlayer.fallDistance < 4 || !module.rewoundDrop.isEnabled())) {
                state = State.HOVER;
                return;
            }

            for (int i = 0; i < 9; i++) {
                final int slot = (mc.thePlayer.inventory.currentItem + i) % 9;
                final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (stack != null && module.rewoundPearl.isEnabled() && stack.getItem() instanceof ItemEnderPearl) {
                    state = State.PEARL;
                    projectileSlot = slot;
                    return;
                }
            }

            if (module.rewoundFireball.isEnabled() && tracker.bedWars())
                for (int i = 0; i < 9; i++) {
                    final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                    if (stack != null && stack.getItem() instanceof ItemFireball) {
                        state = State.FIREBALL;
                        projectileSlot = i;
                        return;
                    }
                }
        }

        if (mc.thePlayer.onGround)
            state = State.DAMAGE;
        else if (module.rewoundDrop.isEnabled() && mc.thePlayer.fallDistance > 4)
            state = State.DROP;
    }

    public void disable() {
        if (owner == mc.thePlayer && owner != null) {
            final double baseSpeed = 0.2873 * (1 + 0.2 * boost(Potion.moveSpeed));
            if (Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ) > baseSpeed) {
                mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;
                if (state == State.DISABLER)
                    mc.thePlayer.motionY = 0;
            }
        }

        reset();
    }

    public void endWalking() {
        if (rotated && owner != null)
            owner.rotationPitch = originalPitch;
        rotated = false;
    }

    public void reset() {
        endWalking();
        releaseTimer();
        blink(false);
        state = null;
        owner = null;
        world = null;
        stage = groundTicks = projectileStage = 0;
        waitingForDamage = false;
        hoverStarted = boostUntil = 0;
        moveSpeed = lastDistance = 0;
    }

    public boolean blinking() {
        return blinking;
    }

    private void blink(final boolean hold) {
        if (blinking == hold) return;
        blinking = hold;
        if (!hold)
            PacketDelayService.getInstance().flush(PacketDelayService.Owner.REWOUND_FLIGHT);
    }

    private void setTimer(final float speed) {
        if (!timerOwned)
            previousTimer = CombatTimerAccess.getBaseSpeed();

        CombatTimerAccess.setSpeed(speed);
        lastTimer = speed;
        timerOwned = true;
    }

    private void releaseTimer() {
        if (timerOwned && CombatTimerAccess.getBaseSpeed() == lastTimer)
            CombatTimerAccess.setSpeed(previousTimer);

        timerOwned = false;
    }

    public void beforeWalking() {
        if (!ready()) return;
        lastDistance = Math.hypot(mc.thePlayer.posX - lastX, mc.thePlayer.posZ - lastZ);
        lastX = mc.thePlayer.posX;
        lastZ = mc.thePlayer.posZ;
        if (mc.thePlayer.onGround)
            lastGroundY = mc.thePlayer.posY;

        if (state == State.DAMAGE) {
            if (stage == 0 && mc.thePlayer.onGround && lastDistance > 0 && !waitingForDamage) {
                if (!module.rewoundDamage.is("None") && tracker.canDamage() && damage()) {
                    waitingForDamage = module.rewoundDamage.is("Wait");
                    if (!waitingForDamage)
                        stage = 1;
                } else {
                    moveSpeed = 0;
                    stage = 4;
                }

                if (module.rewoundTimer.isEnabled())
                    boostUntil = System.currentTimeMillis() + module.rewoundTimerTime.getInt();
            } else if (stage != 0 && (groundTicks++ & 1) == 0)
                mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 1E-9 - Math.random() * 9.9999E-10, mc.thePlayer.posZ);
        } else if (state == State.HOVER && hoverStarted == 0) {
            if (mc.thePlayer.posY - lastGroundY < 0.1) {
                sendPosition(mc.thePlayer.posY + 0.2, false);
                sendPosition(mc.thePlayer.posY + 0.1, false);
                mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.1, mc.thePlayer.posZ);
            }

            hoverStarted = System.currentTimeMillis();
            ClientChat.send("Fly: Getting ready to fly.");
        } else if ((state == State.PEARL || state == State.FIREBALL) && projectileStage < 2) {
            originalPitch = mc.thePlayer.rotationPitch;
            mc.thePlayer.rotationPitch = 90;
            rotated = true;
            projectileStage = 1;
        }

        if (module.rewoundBobbing.is("Always") || state == State.DAMAGE && module.rewoundBobbing.is("Normal only"))
            mc.thePlayer.cameraYaw = 0.1F;
    }

    public void afterWalking() {
        if (!rotated) return;
        try {
            if (owner != mc.thePlayer || world != mc.theWorld || !module.isEnabled()) return;
            final HotbarSwitches.Lease lease = HotbarSwitches.acquire(module, module.rewoundSwitching, projectileSlot, HotbarSwitches.PROJECTILE + 5);
            if (lease == null) return;
            try {
                if (!lease.active()) return;
                final int scope = HotbarSwitches.beginActionHook();
                try {
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, HotbarSwitches.stack());
                    projectileStage = 2;
                } finally {
                    HotbarSwitches.endActionHook(scope);
                }
            } finally {
                lease.close();
            }
        } finally {
            if (owner != null)
                owner.rotationPitch = originalPitch;
            rotated = false;
        }
    }

    private boolean damage() {
        final int count = (int) Math.ceil((3.1 + boost(Potion.jump)) / 0.06249);
        if (tracker.packetCount() >= 795 - (count * 2 + 1)) return false;
        for (int i = 0; i < count; i++) {
            sendPosition(mc.thePlayer.posY + 0.06249, false);
            sendPosition(mc.thePlayer.posY, false);
        }

        sendPosition(mc.thePlayer.posY, true);
        final float slowdown = (count * 2 + 1) * 0.001F;
        setTimer(slowdown > 0.2F ? 0.8F : 1 - slowdown);
        try {
            final Field ticks = ReflectionHelper.findField(EntityPlayerSP.class, "positionUpdateTicks", "field_175168_bP");
            ticks.setAccessible(true);
            ticks.setInt(mc.thePlayer, 0);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Flight position update ticks", failure);
        }

        return true;
    }

    public double[] move() {
        if (!ready()) return null;
        if (tracker.active() && module.rewoundDisabler.isEnabled())
            state = State.DISABLER;

        final double baseSpeed = 0.2873 * (1 + 0.2 * boost(Potion.moveSpeed));
        if (state == State.DAMAGE) {
            if (stage == 0)
                moveSpeed = waitingForDamage ? 0 : baseSpeed;
            else if (stage == 1) {
                mc.thePlayer.motionY = 0;
                moveSpeed = module.rewoundSpeed.getDouble();
            } else if (stage == 2) {
                mc.thePlayer.motionY = (double) 0.42F + (double) (0.1F * boost(Potion.jump));
                moveSpeed *= 2.145;
            } else if (stage == 3) {
                mc.thePlayer.motionY = 0;
                moveSpeed = lastDistance - (lastDistance - baseSpeed) * 0.66;
            } else {
                moveSpeed = Math.max(Math.max(lastDistance - lastDistance / 160,
                        lastDistance - (lastDistance - baseSpeed) / 33.3), lastDistance - (lastDistance - baseSpeed) * 0.02) - 0.00001;
                mc.thePlayer.motionY = 0;
                if (mc.thePlayer.onGround && moveSpeed > baseSpeed) {
                    module.setEnabled(false);
                    return null;
                }
            }

            if (mc.thePlayer.ticksExisted < 10)
                moveSpeed = waitingForDamage ? 0 : baseSpeed;
            else if (!waitingForDamage)
                moveSpeed = Math.max(moveSpeed, baseSpeed);

            strafe(moveSpeed);
            if (stage >= 1) {
                if (System.currentTimeMillis() < boostUntil)
                    setTimer(module.rewoundTimerSpeed.getFloat());
                else
                    releaseTimer();

                stage++;
            }
        } else if (state == State.DISABLER) {
            if (!tracker.active()) {
                module.setEnabled(false);
                return null;
            }

            mc.thePlayer.motionY = (mc.gameSettings.keyBindJump.isKeyDown() ? module.rewoundVertical.getDouble() : 0)
                    - (mc.gameSettings.keyBindSneak.isKeyDown() ? module.rewoundVertical.getDouble() : 0);
            strafe(module.rewoundHorizontal.getDouble());
        } else if (state == State.DROP) {
            if (mc.thePlayer.onGround || System.currentTimeMillis() - started >= 3000) {
                module.setEnabled(false);
                return null;
            }

            if (mc.thePlayer.getHealth() < 6) {
                module.setEnabled(false);
                ClientChat.send("Fly: Automatically disabled due to low health.");
                return null;
            }

            strafe(module.rewoundDropSpeed.getDouble());
            mc.thePlayer.motionY = 0;
            blink(mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                    mc.thePlayer.getEntityBoundingBox().offset(mc.thePlayer.motionX, 0, mc.thePlayer.motionZ)).isEmpty());
        } else if (state != State.PEARL || projectileStage != 2) {
            mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;
            if (state == State.HOVER)
                mc.thePlayer.motionY = 0;
        }

        return new double[] { mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ };
    }

    public void correction() {
        tracker.tick();
        if (!ready()) return;
        if (state == State.HOVER && hoverStarted != 0) {
            if (System.currentTimeMillis() - hoverStarted < 3920) {
                module.setEnabled(false);
                ClientChat.send("Fly: Failed to disable movement checks.");
            } else {
                tracker.activate();
                state = State.DISABLER;
                ClientChat.send("Fly: You can fly for 3 seconds.");
            }
        } else if (state == State.DAMAGE)
            module.setEnabled(false);
    }

    public void renderProgress(final int width, final int height) {
        if (!module.isEnabled() || owner != mc.thePlayer) return;
        final float progress;
        if (state == State.HOVER && hoverStarted != 0)
            progress = Math.min(1, (System.currentTimeMillis() - hoverStarted) / 4250.0F);
        else if (state == State.DISABLER && tracker.active() && module.rewoundProgress.isEnabled())
            progress = tracker.progress();
        else return;
        Gui.drawRect(width / 2 - 50, height / 2 + 20, width / 2 + 50, height / 2 + 24, 0x99000000);
        Gui.drawRect(width / 2 - 50, height / 2 + 20, width / 2 - 50 + (int) (100 * progress), height / 2 + 24, 0xFF77BBFF);
    }

    private boolean ready() {
        if (owner == mc.thePlayer && world == mc.theWorld && owner != null && world != null && !owner.isDead && state != null) return true;
        module.setEnabled(false);
        return false;
    }

    private void strafe(final double speed) {
        double[] orbit = dev.vibe.module.impl.movement.TargetStrafeModule.speedDirection();
        if (orbit != null) {
            mc.thePlayer.motionX = orbit[0] * speed;
            mc.thePlayer.motionZ = orbit[1] * speed;
            return;
        }
        if (!AacMovementSupport.moving(mc.thePlayer)) {
            mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;
            return;
        }

        AacMovementSupport.strafe(mc.thePlayer, speed);
    }

    private int boost(final Potion potion) {
        return mc.thePlayer.isPotionActive(potion) ? mc.thePlayer.getActivePotionEffect(potion).getAmplifier() + 1 : 0;
    }

    private void sendPosition(final double y, final boolean ground) {
        send(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, y, mc.thePlayer.posZ, ground));
    }

    private void send(final Packet<?> packet) {
        tracker.outbound(packet);
        PacketDelayService.sendUnmodified(mc.getNetHandler(), packet);
    }
}
