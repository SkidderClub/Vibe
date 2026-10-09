package dev.vibe.module.impl.world;

import java.util.Arrays;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import org.lwjgl.input.Mouse;

/**
 * RavenBS Scaffold's placement, prediction and rotation implementation.
 * Only Long Telly and Telly B are exposed; Vibe owns the excluded UI,
 * inventory, sneak, sprint and swing features.
 */
final class HypixelScaffold {
   private final Minecraft mc = Minecraft.getMinecraft();
   private final ScaffoldModule owner;
   private static final double ROTATION_INCREMENT = 0.0096F;
   private static final float TAKEOFF_YAW_MIN = 90.0F;
   private static final float TAKEOFF_YAW_MAX = 95.0F;
   private static final float AIRBORNE_YAW_MIN = 30.0F;
   private static final float AIRBORNE_YAW_MAX = 35.0F;
   private static final float LONG_TELLY_SMOOTH_TICKS = 9.0F;
   private static final int LOOK_DIAGONAL_BUCKETS = 1;
   private static final int GROUND_BUCKET_SEARCH_RADIUS = 1;
   private static final float GROUND_YAW_HOLD_ANGLE = 112.5F;
   private static final int GROUND_DIAGONAL_BUCKETS = 1;
   private static final int SPRINT_JUMP_HOLD_TICKS = 1;
   private static final float SPRINT_JUMP_ALIGNMENT = 12.0F;
   private static final int LOOK_LOCK_MISS_LIMIT = 4;
   private static final float YAW_BUCKET_STEP = 1.0F;
   private static final double[] FACE_SAMPLE_OFFSETS = new double[]{
      0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
   };
   private static final double[] FACE_MIN_OFFSET = new double[]{0.0};
   private static final double[] FACE_MAX_OFFSET = new double[]{1.0};
   private static final double[] TOP_FACE_SAMPLES = new double[]{0.5, 0.25, 0.75};
   private static final double[] SIDE_FACE_SAMPLES = new double[]{0.5, 0.8, 0.2};
   private static final float ACTIVATION_SMOOTH_TICKS = 3.0F;
   private static final float ACTIVATION_YAW_LAG = 0.72F;
   private static final EnumFacing[] HORIZONTAL_FACES = new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
   private static final EnumFacing[] PLACEMENT_FACES = new EnumFacing[]{EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
   private static final float SPRINT_MAX_YAW_OFFSET = 50.0F;
   private static final float MOUSE_ROTATION_INCREMENT = 0.0234375F;
   private static final float MIN_YAW_JITTER = 0.11F;
   private static final float MAX_YAW_JITTER = 0.46F;

   private final SliderValue tellyMode, keepMode, multiplace, iceThreshold, clickSpeed;
   private final ButtonValue tellyOnJump, keepYOnRightClick, disableOnJumpPotion, vibeSilentSwing;
   private int heldBlockCount = -1;
   private int keepYStage;
   private int keepYStartHeight = 256;
   private boolean useCurrentHeight;
   private boolean eagleSneaking;
   private int eagleReleaseTicks = -1;
   private boolean sprintSuppressed;
   private boolean sprintWasPressed;
   private boolean sprintStopRequested;
   private int sprintToggleCooldown;
   private final Vec3[] predictedPositions = new Vec3[5];
   private boolean hasPrediction;
   private EntityPlayerSP predictionInputPlayer;
   private float predictionInputYaw = Float.NaN;
   private final HypixelScaffold.Placement[] candidates = new HypixelScaffold.Placement[16];
   private BlockPos neededBlock;
   private int longTellyMissTicks;
   private BlockPos verificationBlock;
   private int verificationDelayTicks;
   private int revertedPlacementCount;
   private int longTellyYawBucket;
   private boolean longTellySprintJump = true;
   private boolean longTellyWasAirborne;
   private boolean longTellyNeedsSideUpdate = true;
   private boolean longTellyWasEngaged;
   private double previousVerticalMotion;
   private int lastGroundX;
   private int lastGroundZ;
   private int ticksSinceGrounded = 99;
   private int longTellyRowY;
   private int longTellySide = 1;
   private boolean hasLongTellyOrigin;
   private int longTellyOriginX;
   private int longTellyOriginZ;
   private int longTellyForwardX;
   private int longTellyForwardZ;
   private int longTellyLateralX;
   private int longTellyLateralZ;
   private final BlockPos[] queuedBlocks = new BlockPos[8];
   private final int[] queueRetryTicks = new int[8];
   private int queuedBlockCount;
   private int pendingQueueIndex = -1;
   private int queueCursor;
   private int strafeTapCooldown;
   private int queueStallTicks;
   private int skippedQueueMask;
   private float longTellyAimYaw;
   private float longTellyAimPitch;
   private HypixelScaffold.Placement pendingPlacement;
   private boolean pendingPlacementReady;
   private int placementDelayTicks;
   private float yaw = -180.0F;
   private float pitch;
   private boolean canRotate;
   private boolean tellyJumping;
   private boolean takeoffLatched;
   private boolean placedThisTick;
   private int placementAttemptsThisTick;
   private float lastSentYaw;
   private float lastSentPitch;
   private float previousSentPitch;
   private float takeoffPitch;
   private int activationTicks;
   private int jumpHoldTicks;
   private int groundPlacementSide = 1;
   private boolean movingCardinally;
   private int lookSide = 1;
   private boolean lookingStraightBack;
   private float activationYawAccumulator;
   private boolean rotationSentThisTick;
   private boolean rotationSentLastTick;
   private boolean movementFixActive;
   private float rotationEaseRate = 1.0F;
   private int cardinalSide = 1;
   private float continuousCameraYaw;
   private boolean hasCameraYaw;
   private float yawGridBase;
   private int groundMissTicks;
   private boolean rotationEaseStarted;
   private float rotationEaseSlowdown;
   private int lastYawBucket;
   private boolean diagonalMovement;
   private float targetYaw;
   private float targetPitch;
   private float pendingYawJitter;
   private float lastBaseYaw;
   private float lastBasePitch;
   private float yawJitterAmplitude = 0.28F;
   private boolean sendingPlacement;
   private long nextClickAt;
   private int clickCount;
   private long clickWindowStart;
   private float measuredClickCps;
   private char clickState = 'D';
   private boolean sendingAutoClick;
   private Vec3 lastPlacementHit;
   private long lastPlacementTime;


   HypixelScaffold(ScaffoldModule owner) {
      this.owner = owner;
      tellyMode = new SliderValue(() -> owner.hypixelTelly.is("Disabled") ? -1 : 1, () -> owner.hypixelTelly.getValue());
      keepMode = new SliderValue(() -> owner.hypixelKeepMode.is("Disabled") ? -1 : 1, () -> owner.hypixelKeepMode.getValue());
      multiplace = new SliderValue(() -> owner.hypixelMultiPlace.is("Disabled") ? -1 : Double.parseDouble(owner.hypixelMultiPlace.getValue()), () -> "");
      iceThreshold = new SliderValue(() -> owner.hypixelIceThreshold.getDouble(), () -> "");
      clickSpeed = new SliderValue(() -> owner.hypixelClickSpeed.getDouble(), () -> "");
      tellyOnJump = new ButtonValue(() -> owner.hypixelTellyOnJump.isEnabled());
      keepYOnRightClick = new ButtonValue(() -> owner.hypixelKeepRmb.isEnabled());
      disableOnJumpPotion = new ButtonValue(() -> owner.hypixelDisableJumpPotion.isEnabled());
      vibeSilentSwing = new ButtonValue(() -> !owner.hypixelSwing());
      reset();
   }
   void reset() {
      heldBlockCount = -1;
      placementDelayTicks = 3;
      yaw = -180.0F;
      pitch = 0.0F;
      canRotate = rotationSentThisTick = rotationSentLastTick = movementFixActive = false;
      resetMovementState();
      cardinalSide = longTellySide = 1;
      neededBlock = null;
      keepYStage = 0;
      keepYStartHeight = 256;
      useCurrentHeight = placedThisTick = false;
      placementAttemptsThisTick = 0;
   }
   private boolean selectPlacementStack(EntityPlayerSP player) {
      heldBlockCount = getBlockCount(player.getHeldItem());
      return heldBlockCount > 0;
   }
   int preferredIceSlot(EntityPlayerSP player) {
      return isIceSelectionEnabled() && isKeepYActive()
         && (tellyJumping || isTellyTakeoff() || keepYStage > 0 || isMoving()) ? findPreferredIceSlot(player) : -1;
   }
   boolean accepts(ItemStack stack) { return isValidBlock(stack); }
   boolean permitsPacket(Packet<?> packet) { return allowPacket(packet); }
   boolean permitsMouse(int button) { return allowMouse(button); }
   private static float clampPitch(float pitch) { return MathHelper.clamp_float(pitch, -90.0F, 90.0F); }
   void beforeInput(MovementInput input) {
      if (!RavenBlockAccess.nullCheck()) return;
      boolean airTap = isLongTellyEnabled() && tellyJumping && !mc.thePlayer.onGround;
      if (airTap && input.moveStrafe == 0.0F && strafeTapCooldown <= 0 && owner.hypixelMovementFix()) {
         double cameraYawRadians = Math.toRadians(getPlayerYaw());
         double lateralMotion = mc.thePlayer.motionX * -Math.cos(cameraYawRadians) + mc.thePlayer.motionZ * -Math.sin(cameraYawRadians);
         if (Math.abs(lateralMotion) > 0.02) {
            input.moveStrafe = lateralMotion > 0.0 ? 1.0F : -1.0F;
            strafeTapCooldown = 2 + (randomFloat(0.0F, 1.0F) < 0.4F ? 1 : 0);
         }
      }
      if (mc.thePlayer.onGround && isKeepYActive() && keepYStage > 0 && isMoving()) input.jump = true;
      if (!mc.thePlayer.onGround) jumpHoldTicks = 0;
      else if (input.jump && isLongTellyEnabled() && longTellySprintJump && jumpHoldTicks < 1
         && Math.abs(MathHelper.wrapAngleTo180_float(lastSentYaw - getPlayerYaw())) > 12.0F) {
         input.jump = false;
         jumpHoldTicks++;
      }
   }
   void afterInput() {
      if (!RavenBlockAccess.nullCheck()) predictionInputPlayer = null;
      else {
         Float serverYaw = owner.hypixelServerYaw();
         predictionInputYaw = owner.hypixelMovementFix() && serverYaw != null && !serverYaw.isNaN() && !serverYaw.isInfinite()
            ? serverYaw : mc.thePlayer.rotationYaw;
         predictionInputPlayer = mc.thePlayer;
      }
   }
   void update() {
      if (RavenBlockAccess.nullCheck()) {
         Vec3 position = mc.thePlayer.getPositionVector();
         this.beginTick();
         
         this.updateKeepYState(mc.thePlayer, position);
         if (!this.selectPlacementStack(mc.thePlayer)) return;
         boolean longTellyEngaged = this.updateLongTellyState(mc.thePlayer, position);
         float cameraYaw = this.updateCameraYaw(mc.thePlayer.rotationYaw);
         float movementYaw = this.getMovementYaw(cameraYaw, this.getForwardInput(), this.getStrafeInput());
         float referenceServerYaw = this.getServerYaw();
         float backwardYaw = this.unwrapYaw(movementYaw - 180.0F, referenceServerYaw);
         float diagonalYaw = this.isDiagonalMovement(movementYaw)
            ? backwardYaw
            : this.unwrapYaw(movementYaw - 135.0F * this.updateCardinalSide(movementYaw), referenceServerYaw);
         if (!this.canRotate) {
            if (this.yaw == -180.0F && this.pitch == 0.0F) {
               this.pitch = this.quantizeRotation(85.0F);
            }

            this.yaw = this.quantizeRotation(diagonalYaw);
         }

         this.yawGridBase = cameraYaw;
         boolean flatGround = mc.thePlayer.onGround && !this.tellyJumping && !this.isTellyTakeoff();
         if (flatGround) {
            this.yaw = this.quantizeRotation(diagonalYaw);
            this.canRotate = true;
         }

         boolean shortDiagonal = this.isShortDiagonalJump();
         HypixelScaffold.Placement placement = flatGround ? null : this.findPlacement();
         this.aimAtPlacement(placement, referenceServerYaw, mc.thePlayer, position);
         boolean tellyRotating = this.tellyJumping || this.isTellyTakeoff();
         Vec3 motion = new Vec3(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ);
         float previousYaw = this.rotationSentLastTick ? this.lastSentYaw : cameraYaw;
         float previousPitch = this.rotationSentLastTick ? this.lastSentPitch : mc.thePlayer.rotationPitch;
         boolean urgent = !this.rotationSentLastTick
            || this.isUnsupportedAt(position.xCoord, position.zCoord)
            || this.isUnsupportedAt(position.xCoord + motion.xCoord * 2.0, position.zCoord + motion.zCoord * 2.0)
            || this.isUnsupportedAt(position.xCoord + motion.xCoord * 3.5, position.zCoord + motion.zCoord * 3.5)
            || this.isUnsupportedAt(position.xCoord + motion.xCoord * 5.0, position.zCoord + motion.zCoord * 5.0)
            || this.groundMissTicks >= 3;
         this.updateTargetRotations(position, motion, previousYaw, previousPitch, urgent, shortDiagonal);
         int lookBucket = this.updateLookBucket(position, cameraYaw);
         boolean cameraAligned = this.alignTakeoffRotations(tellyRotating, cameraYaw);
         this.snapGroundYaw(tellyRotating, position, motion, cameraYaw);
         this.aimYawWithJitter(this.targetYaw, this.nextYawJitter());
         this.aimPitch(clampPitch(this.targetPitch));
         int groundCandidateIndex = flatGround ? this.aimGroundPlacement(mc.thePlayer, position, urgent, previousPitch) : -1;
         boolean placementAllowed = this.isPlacementAllowed(placement);
         Vec3 verifiedHit = placement != null && placementAllowed
            ? this.verifyPlacementAim(placement, tellyRotating, shortDiagonal, lookBucket, cameraAligned)
            : null;
         if (longTellyEngaged
            && verifiedHit == null
            && groundCandidateIndex < 0
            && this.queuedBlockCount > 0
            && (!mc.thePlayer.onGround || !this.longTellySprintJump)
            && this.findLongTellyPlacement()) {
            this.aimLongTellyPlacement(previousYaw, previousPitch, lookBucket, cameraAligned);
         }

         if (verifiedHit == null && groundCandidateIndex < 0 && !this.pendingPlacementReady && !this.placedThisTick) {
            this.easeActivationRotation(mc.thePlayer, previousYaw, previousPitch, lookBucket, cameraAligned);
         }

         
         float serverYaw = this.getServerYaw();
         this.targetYaw = serverYaw + MathHelper.wrapAngleTo180_float(this.targetYaw - serverYaw);
         this.targetYaw = this.quantizeMouseRotation(this.targetYaw, this.lastSentYaw);
         this.targetPitch = clampPitch(this.quantizeMouseRotation(this.targetPitch, this.lastSentPitch));
         if (shortDiagonal && placement != null && placementAllowed && this.placementDelayTicks <= 0) {
            verifiedHit = this.raycastFace(placement, this.targetYaw, this.targetPitch);
            if (verifiedHit == null) {
               verifiedHit = this.aimShortDiagonalFace(placement);
            }
         }

         this.sendRotations();
         this.placeSelectedBlock(groundCandidateIndex, verifiedHit, placement);
         if (this.neededBlock != null && !this.placedThisTick && mc.thePlayer.onGround && !this.tellyJumping) {
            this.groundMissTicks++;
         } else {
            this.groundMissTicks = 0;
         }

         this.updateKeepYPlacement(mc.thePlayer, position, motion);
         this.autoClick(mc.thePlayer);
      }
   }
   private int getBlockCount(ItemStack stack) {
      return this.isValidBlock(stack) ? stack.stackSize : 0;
   }

   private void resetMovementState() {
      this.predictionInputPlayer = null;
      this.predictionInputYaw = Float.NaN;
      this.activationTicks = 0;
      this.groundPlacementSide = 1;
      this.movingCardinally = false;
      this.lookSide = 1;
      this.lookingStraightBack = false;
      this.jumpHoldTicks = 0;
      this.activationYawAccumulator = 0.0F;
      this.rotationEaseRate = 1.0F;
      this.rotationEaseStarted = false;
      this.groundMissTicks = 0;
      this.hasCameraYaw = false;
      this.rotationEaseSlowdown = 0.0F;
      this.tellyJumping = false;
      this.takeoffLatched = false;
      this.sprintStopRequested = false;
      this.sprintToggleCooldown = 0;
      this.lastYawBucket = 0;
      this.diagonalMovement = false;
      this.eagleSneaking = false;
      this.eagleReleaseTicks = -1;
      this.longTellySprintJump = true;
      this.longTellyWasAirborne = false;
      this.longTellyNeedsSideUpdate = true;
      this.longTellyWasEngaged = false;
      this.previousVerticalMotion = 0.0;
      this.ticksSinceGrounded = 99;
      this.hasLongTellyOrigin = false;
      this.resetLongTellyQueue();
      this.strafeTapCooldown = 0;
      this.pendingPlacementReady = false;
      this.verificationDelayTicks = 0;
   }

   private void resetLongTellyQueue() {
      this.queuedBlockCount = 0;
      this.pendingQueueIndex = -1;
      this.skippedQueueMask = 0;
      Arrays.fill(this.queueRetryTicks, 0);
      this.queueCursor = 0;
      this.queueStallTicks = 0;
   }

   private void autoClick(EntityPlayerSP player) {
      if (player != null && this.isValidBlock(player.getHeldItem())) {
         float configuredCps = (float)this.clickSpeed.getInput();
         if (configuredCps < 1.0F) {
            this.clickState = 'D';
         } else if (mc.currentScreen != null) {
            this.clickState = 'S';
         } else if (this.placementAttemptsThisTick > 0) {
            this.clickState = 'P';
         } else {
            long now = System.currentTimeMillis();
            if (now < this.nextClickAt) {
               this.clickState = 'w';
            } else {
               if (this.nextClickAt < now - 500L) {
                  this.nextClickAt = now;
               }

               this.clickState = 'C';
               this.sendingAutoClick = true;

               try {
                  mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, player.getHeldItem(), 0.0F, 0.0F, 0.0F));
               } catch (Throwable var11) {
                  this.clickState = 'E';
               } finally {
                  this.sendingAutoClick = false;
               }

               float cps = configuredCps * this.randomFloat(0.88F, 1.12F);
               if (cps < 1.0F) {
                  cps = 1.0F;
               }

               float clickInterval = 1000.0F / cps;
               if (this.randomFloat(0.0F, 1.0F) < 0.1F) {
                  clickInterval *= this.randomFloat(1.5F, 2.4F);
               }

               this.nextClickAt += (long)clickInterval;
               if (this.nextClickAt < now) {
                  this.nextClickAt = now + (long)clickInterval;
               }

               this.clickCount++;
               if (now - this.clickWindowStart >= 1000L) {
                  long elapsedMillis = now - this.clickWindowStart;
                  this.measuredClickCps = this.clickCount * 1000.0F / (float)Math.max(1L, elapsedMillis);
                  this.clickCount = 0;
                  this.clickWindowStart = now;
               }
            }
         }
      }
   }

   private float updateCameraYaw(float rawCameraYaw) {
      if (!this.hasCameraYaw) {
         this.continuousCameraYaw = rawCameraYaw;
         this.hasCameraYaw = true;
      } else {
         this.continuousCameraYaw = this.continuousCameraYaw + MathHelper.wrapAngleTo180_float(rawCameraYaw - this.continuousCameraYaw);
      }

      return this.continuousCameraYaw;
   }

   private void beginTick() {
      this.placedThisTick = false;
      this.placementAttemptsThisTick = 0;
      this.hasPrediction = false;
      this.updateYawJitter();
      this.rotationEaseRate = MathHelper.clamp_float(this.rotationEaseRate + this.randomFloat(-0.01F, 0.01F), 0.97F, 1.03F);
      if (this.activationTicks < 1000) {
         this.activationTicks++;
      }

      this.pendingPlacementReady = false;
      this.skippedQueueMask = 0;

      for (int q = 0; q < this.queuedBlockCount; q++) {
         if (this.queueRetryTicks[q] > 0 && --this.queueRetryTicks[q] > 0) {
            this.skippedQueueMask |= 1 << q;
         }
      }

      this.rotationSentLastTick = this.rotationSentThisTick;
      this.rotationSentThisTick = false;
      if (this.placementDelayTicks > 0) {
         this.placementDelayTicks--;
      }
   }

   private void updateKeepYState(EntityPlayerSP player, Vec3 position) {
      if (!this.isKeepYActive()) {
         this.keepYStage = 0;
         this.useCurrentHeight = false;
         this.keepYStartHeight = MathHelper.floor_double(position.yCoord);
      }

      if (player.onGround) {
         if (this.keepYStage != 0) {
            this.keepYStage = this.keepYStage + (this.keepYStage > 0 ? -1 : 1);
         }

         if (this.keepYStage == 0 && this.isKeepYActive() && (!this.disableOnJumpPotion.isToggled() || !this.hasJumpPotion()) && !this.isJumpPressed()) {
            this.keepYStage = 1;
         }

         this.keepYStartHeight = this.useCurrentHeight ? this.keepYStartHeight : MathHelper.floor_double(position.yCoord);
         this.useCurrentHeight = false;
         this.tellyJumping = false;
      }
   }

   private boolean updateLongTellyState(EntityPlayerSP player, Vec3 position) {
      if (this.tellyOnJump.isToggled() && !this.isJumpPressed() && (!this.isKeepYActive() || this.keepYStage <= 0)) {
         this.tellyJumping = false;
         this.takeoffLatched = false;
      }

      if (this.isLongTellyEnabled() && !player.onGround && (this.longTellyWasEngaged || this.tellyJumping)) {
         this.tellyJumping = true;
      }

      boolean longTellyEngaged = this.isLongTellyEnabled() && (this.tellyJumping || this.isManualTellyTakeoff());
      if (longTellyEngaged && !player.onGround) {
         this.longTellyMissTicks++;
      } else {
         this.longTellyMissTicks = 0;
      }

      if (this.verificationDelayTicks > 0 && --this.verificationDelayTicks == 0 && this.isReplaceableAt(this.verificationBlock)) {
         this.revertedPlacementCount++;
         this.skippedQueueMask = 0;
         Arrays.fill(this.queueRetryTicks, 0);
         this.queueStallTicks = 0;

      }

      if (player.onGround) {
         this.lastGroundX = MathHelper.floor_double(position.xCoord);
         this.lastGroundZ = MathHelper.floor_double(position.zCoord);
         this.ticksSinceGrounded = 0;
      } else if (this.ticksSinceGrounded < 99) {
         this.ticksSinceGrounded++;
      }

      if (longTellyEngaged && !this.longTellyWasEngaged) {
         this.longTellySprintJump = true;
         this.longTellyWasAirborne = false;
         this.longTellyNeedsSideUpdate = true;
         this.hasLongTellyOrigin = false;
         this.queuedBlockCount = 0;
         this.pendingQueueIndex = -1;
      }

      this.longTellyWasEngaged = longTellyEngaged;
      if (this.strafeTapCooldown > 0) {
         this.strafeTapCooldown--;
      }

      Vec3 motion = new Vec3(player.motionX, player.motionY, player.motionZ);
      if (longTellyEngaged) {
         if (player.onGround && this.longTellyWasAirborne) {
            this.longTellyWasAirborne = false;
            this.longTellySprintJump = !this.longTellySprintJump;
            this.longTellyNeedsSideUpdate = this.longTellySprintJump;
            this.buildLongTellyQueue(motion);
            if (!this.longTellySprintJump) {
               this.placementDelayTicks = 0;
            }
         }

         if (!player.onGround) {
            this.longTellyWasAirborne = true;
         }

         if (motion.yCoord > 0.3 && this.previousVerticalMotion <= 0.05) {
            this.buildLongTellyQueue(motion);

         }
      } else {
         this.queuedBlockCount = 0;
         this.pendingQueueIndex = -1;
      }

      this.previousVerticalMotion = motion.yCoord;
      return longTellyEngaged;
   }

   private void aimAtPlacement(HypixelScaffold.Placement placement, float referenceServerYaw, EntityPlayerSP player, Vec3 position) {
      if (placement != null) {
         BlockPos blockPosition = placement.support;
         EnumFacing face = placement.face;
         double[] xOffsets = this.getFaceOffsets(face.getFrontOffsetX());
         double[] yOffsets = this.getFaceOffsets(face.getFrontOffsetY());
         double[] zOffsets = this.getFaceOffsets(face.getFrontOffsetZ());
         float[] bestRotation = null;
         float bestRotationDifference = 0.0F;
         float baseYaw = this.unwrapYaw(this.yaw, referenceServerYaw);
         float referenceYaw = this.rotationSentLastTick ? this.lastSentYaw : baseYaw;
         float referencePitch = this.rotationSentLastTick ? this.lastSentPitch : this.pitch;

         for (double dx : xOffsets) {
            for (double dy : yOffsets) {
               for (double dz : zOffsets) {
                  double relX = blockPosition.getX() + dx - position.xCoord;
                  double relY = blockPosition.getY() + dy - position.yCoord - player.getEyeHeight();
                  double relZ = blockPosition.getZ() + dz - position.zCoord;
                  float[] rotation = this.getRotationsToOffset(relX, relY, relZ, baseYaw, this.pitch);
                  float rotationDifference = Math.abs(MathHelper.wrapAngleTo180_float(rotation[0] - referenceYaw)) + Math.abs(rotation[1] - referencePitch);
                  if (bestRotation == null || !(rotationDifference >= bestRotationDifference)) {
                     Vec3 hit = this.raycastFace(placement, rotation[0], rotation[1]);
                     if (hit != null) {
                        bestRotation = rotation;
                        bestRotationDifference = rotationDifference;
                     }
                  }
               }
            }
         }

         if (bestRotation != null) {
            this.yaw = bestRotation[0];
            this.pitch = bestRotation[1];
            this.canRotate = true;
         }
      }
   }

   private double[] getFaceOffsets(int direction) {
      return direction < 0 ? FACE_MIN_OFFSET : (direction > 0 ? FACE_MAX_OFFSET : FACE_SAMPLE_OFFSETS);
   }

   private void aimYaw(float value) {
      this.targetYaw = value;
      this.pendingYawJitter = 0.0F;
   }

   private void aimYawWithJitter(float base, float yawJitter) {
      this.targetYaw = base + yawJitter;
      this.pendingYawJitter = yawJitter;
   }

   private void aimPitch(float value) {
      this.targetPitch = value;
   }

   private void updateTargetRotations(Vec3 position, Vec3 motion, float previousYaw, float previousPitch, boolean urgent, boolean shortDiagonal) {
      if (!urgent) {
         float[] eased = this.easeRotations(previousYaw, previousPitch, this.yaw, this.pitch);
         this.aimYaw(eased[0]);
         this.aimPitch(eased[1]);
      } else {
         this.aimYaw(this.yaw);
         this.aimPitch(this.pitch);
         this.rotationEaseStarted = false;
         this.rotationEaseSlowdown = 0.0F;
      }

      if (this.tellyJumping && (motion.yCoord > 0.0 || position.yCoord > this.keepYStartHeight + 1)) {
         float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - previousYaw);
         float rotationLimit = this.placementDelayTicks >= 2 ? this.randomFloat(90.0F, 95.0F) : this.randomFloat(30.0F, 35.0F);
         if (Math.abs(yawDiff) > rotationLimit) {
            this.aimYaw(previousYaw + this.clampRotationDelta(yawDiff, rotationLimit));
            if (!shortDiagonal && (!this.isLongTellyEnabled() || this.queuedBlockCount <= 0)) {
               this.placementDelayTicks = Math.max(this.placementDelayTicks, 1);
            }
         }
      }
   }

   private int updateLookBucket(Vec3 position, float cameraYaw) {
      int backBucket = Math.round(MathHelper.wrapAngleTo180_float(cameraYaw + 180.0F - cameraYaw) / 45.0F);
      int currentBucket = Math.round(MathHelper.wrapAngleTo180_float(this.lastSentYaw - cameraYaw) / 45.0F);

      while (backBucket - currentBucket > 4) {
         backBucket -= 8;
      }

      while (backBucket - currentBucket < -4) {
         backBucket += 8;
      }

      double blockCenterX = Math.floor(position.xCoord) + 0.5;
      double blockCenterZ = Math.floor(position.zCoord) + 0.5;
      double lateralOffset = (position.xCoord - blockCenterX) * -this.longTellyForwardZ
         + (position.zCoord - blockCenterZ) * this.longTellyForwardX;
      if (lateralOffset > 0.12) {
         this.lookSide = 1;
      } else if (lateralOffset < -0.12) {
         this.lookSide = -1;
      }

      if (currentBucket == backBucket) {
         this.lookingStraightBack = true;
      }

      return this.lookingStraightBack ? backBucket : backBucket + 1 * this.lookSide;
   }

   private boolean alignTakeoffRotations(boolean tellyRotating, float cameraYaw) {
      boolean cameraAligned = false;
      if (tellyRotating && this.isTellyTakeoff() && (!this.isLongTellyEnabled() || this.longTellySprintJump)) {
         cameraAligned = true;
         this.lookingStraightBack = false;
         this.aimYawWithJitter(cameraYaw, this.jitter(0.12F));
         if (this.takeoffPitch < 30.0F || this.takeoffPitch > 89.5F) {
            this.takeoffPitch = clampPitch(this.lastSentPitch);
         }

         this.takeoffPitch = this.takeoffPitch + this.randomFloat(-0.5F, 0.5F);
         this.takeoffPitch = MathHelper.clamp_float(this.takeoffPitch, 30.0F, 89.0F);
         this.takeoffPitch = this.takeoffPitch + (clampPitch(this.lastSentPitch) - this.takeoffPitch) * 0.25F;
         this.aimPitch(this.takeoffPitch);
         if (this.isManualTellyTakeoff()) {
            this.placementDelayTicks = 2;
            this.takeoffLatched = true;
         } else if (!this.takeoffLatched) {
            this.placementDelayTicks = Math.max(this.placementDelayTicks, 2);
            this.takeoffLatched = true;
         }

         this.tellyJumping = true;
      }

      if (!cameraAligned) {
         this.takeoffLatched = false;
      }

      return cameraAligned;
   }

   private void snapGroundYaw(boolean tellyRotating, Vec3 position, Vec3 motion, float cameraYaw) {
      if (!tellyRotating) {
         float snapBase = this.targetYaw;
         double horizontalSpeed = Math.sqrt(motion.xCoord * motion.xCoord + motion.zCoord * motion.zCoord);
         if (horizontalSpeed >= 0.02) {
            float travelYaw = (float)Math.toDegrees(Math.atan2(-motion.xCoord, motion.zCoord));
            float cardinalOffset = Math.abs(MathHelper.wrapAngleTo180_float(travelYaw - Math.round(travelYaw / 90.0F) * 90.0F));
            if (this.movingCardinally) {
               if (cardinalOffset > 27.0F) {
                  this.movingCardinally = false;
               }
            } else if (cardinalOffset < 18.0F) {
               this.movingCardinally = true;
            }
         } else {
            this.movingCardinally = false;
         }

         if (horizontalSpeed >= 0.02 && this.movingCardinally) {
            double blockCenterX = Math.floor(position.xCoord) + 0.5;
            double blockCenterZ = Math.floor(position.zCoord) + 0.5;
            double lateralOffset = (
                  (position.xCoord - blockCenterX) * -motion.zCoord + (position.zCoord - blockCenterZ) * motion.xCoord
               )
               / horizontalSpeed;
            if (lateralOffset > 0.12) {
               this.groundPlacementSide = 1;
            } else if (lateralOffset < -0.12) {
               this.groundPlacementSide = -1;
            }

            snapBase = (float)Math.toDegrees(Math.atan2(motion.xCoord, -motion.zCoord)) + 1 * this.groundPlacementSide * 45.0F;
         } else if (horizontalSpeed < 0.02) {
            snapBase = cameraYaw + this.lastYawBucket * 45.0F;
         }

         float holdAngle = 112.5F;
         if (!this.movingCardinally) {
            holdAngle = 29.5F;
         } else if (horizontalSpeed >= 0.02 && this.groundMissTicks >= 2) {
            holdAngle = 22.5F;
         }

         float snapDelta = MathHelper.wrapAngleTo180_float(snapBase - cameraYaw);
         int bucket = Math.round(snapDelta / 45.0F);
         if (bucket != this.lastYawBucket && Math.abs(MathHelper.wrapAngleTo180_float(snapDelta - this.lastYawBucket * 45.0F)) <= holdAngle) {
            bucket = this.lastYawBucket;
         }

         this.lastYawBucket = bucket;
         this.aimYaw(cameraYaw + bucket * 45.0F);
      } else {
         this.lastYawBucket = Math.round(MathHelper.wrapAngleTo180_float(this.targetYaw - cameraYaw) / 45.0F);
      }
   }

   private int aimGroundPlacement(EntityPlayerSP player, Vec3 position, boolean urgent, float previousPitch) {
      float cameraYaw = this.yawGridBase;
      int groundY = MathHelper.floor_double(position.yCoord) - 1;
      this.findNeededBlock(groundY);
      int candidateCount = this.buildCandidates(groundY);
      if (this.neededBlock != null) {
         if (this.eagleSneaking) {
            urgent = true;
         }

         for (int corner = 0; corner < 4 && !urgent; corner++) {
            double cornerX = position.xCoord + ((corner & 1) == 0 ? -0.3 : 0.3);
            double cornerZ = position.zCoord + ((corner & 2) == 0 ? -0.3 : 0.3);
            if (MathHelper.floor_double(cornerX) == this.neededBlock.getX() && MathHelper.floor_double(cornerZ) == this.neededBlock.getZ()) {
               urgent = true;
            }
         }
      }

      if (candidateCount == 0) {
         return -1;
      } else {
         float bestPitch = Float.NaN;
         float bestScanPitch = 0.0F;
         int bestScore = -1;
         boolean holdingPitch = false;
         if (this.rotationSentLastTick) {
            int previousCandidateIndex = this.matchCandidate(candidateCount, this.lastBaseYaw, this.lastBasePitch);
            if (previousCandidateIndex >= 0
               && this.getCandidateScore(previousCandidateIndex) >= 2
               && this.matchCandidate(candidateCount, this.lastBaseYaw + 0.5F, this.lastBasePitch) >= 0
               && this.matchCandidate(candidateCount, this.lastBaseYaw - 0.5F, this.lastBasePitch) >= 0
               && this.matchCandidate(candidateCount, this.lastBaseYaw, clampPitch(this.lastBasePitch - 0.35F)) >= 0
               && this.matchCandidate(candidateCount, this.lastBaseYaw, clampPitch(this.lastBasePitch + 0.35F)) >= 0) {
               this.aimYawWithJitter(this.lastBaseYaw, this.nextYawJitter());
               holdingPitch = true;
               bestPitch = this.lastBasePitch;
               bestScore = 2;
               bestScanPitch = 0.0F;
            }
         }

         int heldCandidateIndex = holdingPitch ? -1 : this.matchCandidate(candidateCount, this.targetYaw, previousPitch);
         if (heldCandidateIndex >= 0
            && this.getCandidateScore(heldCandidateIndex) >= 2
            && this.matchCandidate(candidateCount, this.targetYaw, clampPitch(previousPitch - 0.35F)) >= 0
            && this.matchCandidate(candidateCount, this.targetYaw, clampPitch(previousPitch + 0.35F)) >= 0) {
            holdingPitch = true;
            bestPitch = previousPitch;
            bestScore = 2;
            bestScanPitch = 0.0F;
         }

         if (this.neededBlock != null && !holdingPitch) {
            int[] yawBuckets = this.getGroundYawBuckets(player, position, cameraYaw);

            label164:
            for (int pass = 2; pass >= 1; pass--) {
               for (int bucketIndex = 0; bucketIndex < yawBuckets.length; bucketIndex++) {
                  float yawJitter = bucketIndex == 0 ? this.pendingYawJitter : this.nextYawJitter();
                  float candidateYaw = bucketIndex == 0 ? this.targetYaw : cameraYaw + yawBuckets[bucketIndex] * 45.0F + yawJitter;

                  for (int i = 0; i < candidateCount; i++) {
                     if (this.getCandidateScore(i) == pass) {
                        float candidatePitch = this.findFacePitch(this.candidates[i], candidateYaw);
                        if (!Float.isNaN(candidatePitch)) {
                           bestPitch = candidatePitch;
                           bestScore = pass;
                           if (bucketIndex > 0) {
                              this.aimYawWithJitter(candidateYaw - yawJitter, yawJitter);
                              this.lastYawBucket = yawBuckets[bucketIndex];
                           }

                           break label164;
                        }
                     }
                  }
               }
            }
         }

         int maxScore = this.neededBlock != null ? 2 : 0;
         float scanPitch = 60.0F;

         while (Float.isNaN(bestPitch) || bestScore < maxScore) {
            float candidatePitch = Math.min(scanPitch, 90.0F);
            int matchedIndex = this.matchCandidate(candidateCount, this.targetYaw, this.quantizeRotation(candidatePitch));
            if (matchedIndex >= 0) {
               int score = this.getCandidateScore(matchedIndex);
               if (score > bestScore || score == bestScore && candidatePitch < bestScanPitch) {
                  bestPitch = candidatePitch;
                  bestScanPitch = candidatePitch;
                  bestScore = score;
               }
            }

            if (scanPitch >= 90.0F) {
               break;
            }

            float pitchStep = scanPitch >= 84.0F ? 0.3F + this.randomFloat(0.0F, 0.08F) : 1.0F + this.randomFloat(-0.38F, 0.38F);
            scanPitch += pitchStep;
         }

         if (holdingPitch) {
            this.pitch = previousPitch;
            this.aimPitch(clampPitch(previousPitch));
         } else if (!Float.isNaN(bestPitch)) {
            this.pitch = this.quantizeRotation(bestPitch);
            if (!urgent) {
               this.aimPitch(this.easeRotations(this.targetYaw, previousPitch, this.targetYaw, this.pitch)[1]);
            } else {
               this.aimPitch(this.pitch);
            }

            this.aimPitch(clampPitch(this.targetPitch));
         }

         int groundCandidateIndex = this.matchCandidate(candidateCount, this.targetYaw, this.targetPitch);
         if (groundCandidateIndex < 0 && !Float.isNaN(bestPitch)) {
            this.aimPitch(clampPitch(bestPitch));
            groundCandidateIndex = this.matchCandidate(candidateCount, this.targetYaw, this.targetPitch);
         }

         return groundCandidateIndex;
      }
   }

   private int[] getGroundYawBuckets(EntityPlayerSP player, Vec3 position, float cameraYaw) {
      int[] allBuckets = new int[]{
         this.lastYawBucket,
         this.lastYawBucket - 1,
         this.lastYawBucket + 1,
         this.lastYawBucket - 2,
         this.lastYawBucket + 2,
         this.lastYawBucket - 3,
         this.lastYawBucket + 3,
         this.lastYawBucket + 4
      };
      Vec3 motion = new Vec3(player.motionX, player.motionY, player.motionZ);
      double horizontalSpeed = RavenBlockAccess.getHorizontalSpeed(player);
      int preferredBucket = this.lastYawBucket;
      if (horizontalSpeed >= 0.02) {
         double blockCenterX = Math.floor(position.xCoord) + 0.5;
         double blockCenterZ = Math.floor(position.zCoord) + 0.5;
         double lateralOffset = (
               (position.xCoord - blockCenterX) * -motion.zCoord + (position.zCoord - blockCenterZ) * motion.xCoord
            )
            / horizontalSpeed;
         if (lateralOffset > 0.12) {
            this.groundPlacementSide = 1;
         } else if (lateralOffset < -0.12) {
            this.groundPlacementSide = -1;
         }

         float backwardYaw = (float)Math.toDegrees(Math.atan2(motion.xCoord, -motion.zCoord));
         float preferredYaw = backwardYaw + 1 * this.groundPlacementSide * 45.0F;
         preferredBucket = Math.round(MathHelper.wrapAngleTo180_float(preferredYaw - cameraYaw) / 45.0F);
      }

      int searchRadius = 1;
      if (!this.movingCardinally || this.groundMissTicks >= 4) {
         searchRadius = 9;
      } else if (this.groundMissTicks >= 2) {
         searchRadius = 2;
      }

      int bucketCount = 1;

      for (int index = 1; index < allBuckets.length; index++) {
         if (Math.abs(allBuckets[index] - this.lastYawBucket) <= searchRadius) {
            allBuckets[bucketCount++] = allBuckets[index];
         }
      }

      int[] yawBuckets = Arrays.copyOf(allBuckets, bucketCount);

      for (int first = 0; first < yawBuckets.length; first++) {
         for (int second = first + 1; second < yawBuckets.length; second++) {
            if (Math.abs(yawBuckets[second] - preferredBucket) < Math.abs(yawBuckets[first] - preferredBucket)) {
               int previousBucket = yawBuckets[first];
               yawBuckets[first] = yawBuckets[second];
               yawBuckets[second] = previousBucket;
            }
         }
      }

      return yawBuckets;
   }

   private boolean isPlacementAllowed(HypixelScaffold.Placement placement) {
      BlockPos target = placement == null ? null : placement.getTarget();
      int placementRow = target == null ? 0 : target.getY();
      if (this.queuedBlockCount > 0 && placementRow > this.longTellyRowY + (this.longTellySprintJump ? 0 : 1)) {
         return false;
      } else if (target != null && this.isAbovePredictedPath(target.getX(), target.getY(), target.getZ())) {
         return false;
      } else {
         return true;
      }
   }

   private Vec3 verifyPlacementAim(HypixelScaffold.Placement placement, boolean tellyRotating, boolean shortDiagonal, int lookBucket, boolean cameraAligned) {
      float cameraYaw = this.yawGridBase;
      if (this.rotationSentLastTick && !tellyRotating && this.hasStableFaceAim(placement)) {
         this.aimYawWithJitter(this.lastBaseYaw, this.nextYawJitter());
         this.aimPitch(this.lastBasePitch);
      }

      Vec3 verifiedHit = this.raycastFace(placement, this.targetYaw, this.targetPitch);
      if (verifiedHit == null && tellyRotating) {
         float fallbackPitch = clampPitch(Math.max(30.0F, this.pitch));
         Vec3 fallbackHit = this.raycastFace(placement, this.targetYaw, fallbackPitch);
         if (fallbackHit != null) {
            this.aimPitch(fallbackPitch);
            verifiedHit = fallbackHit;
         }
      }

      if (verifiedHit == null && (!tellyRotating || shortDiagonal && this.placementDelayTicks <= 0)) {
         int baseBucket = Math.round(MathHelper.wrapAngleTo180_float(this.yaw - cameraYaw) / 45.0F);
         int[] yawBuckets = new int[]{
            baseBucket, baseBucket - 1, baseBucket + 1, baseBucket - 2, baseBucket + 2, baseBucket - 3, baseBucket + 3, baseBucket + 4
         };
         if (this.isLookLocked() && this.isLongTellyEnabled() && this.queuedBlockCount > 0 && !cameraAligned && baseBucket != lookBucket) {
            yawBuckets = new int[]{
               lookBucket, baseBucket, baseBucket - 1, baseBucket + 1, baseBucket - 2, baseBucket + 2, baseBucket - 3, baseBucket + 3, baseBucket + 4
            };
         }

         for (int i = 0; i < yawBuckets.length && verifiedHit == null; i++) {
            float yawJitter = this.jitter(0.2F);
            float candidateYaw = cameraYaw + yawBuckets[i] * 45.0F + yawJitter;
            float candidatePitch = this.findFacePitch(placement, candidateYaw);
            if (!Float.isNaN(candidatePitch)) {
               Vec3 candidateHit = this.raycastFace(placement, candidateYaw, candidatePitch);
               if (candidateHit != null) {
                  this.aimYawWithJitter(candidateYaw - yawJitter, yawJitter);
                  this.aimPitch(candidatePitch);
                  verifiedHit = candidateHit;
                  this.lastYawBucket = yawBuckets[i];
               }
            }
         }
      }

      return verifiedHit;
   }

   private void aimLongTellyPlacement(float previousYaw, float previousPitch, int lookBucket, boolean cameraAligned) {
      float cameraYaw = this.yawGridBase;
      float rotationLimit = this.longTellySprintJump ? this.randomFloat(30.0F, 35.0F) : this.randomFloat(90.0F, 95.0F);
      float yawDelta = MathHelper.wrapAngleTo180_float(this.longTellyAimYaw - previousYaw);
      float pitchDelta = this.longTellyAimPitch - previousPitch;
      float easeFraction = 1.0F - (float)Math.pow(0.05, 0.1111111111111111);
      easeFraction = Math.min(1.0F, easeFraction * this.rotationEaseScale(Math.max(Math.abs(yawDelta), Math.abs(pitchDelta))));
      this.aimYawWithJitter(previousYaw + this.clampRotationDelta(yawDelta * easeFraction, rotationLimit), this.jitter(0.1F));
      this.aimPitch(clampPitch(previousPitch + this.clampRotationDelta(pitchDelta * easeFraction, rotationLimit)));
      this.lastYawBucket = Math.round(MathHelper.wrapAngleTo180_float(this.targetYaw - cameraYaw) / 45.0F);
      Vec3 verifiedHit = null;
      if (this.rotationSentLastTick && !cameraAligned && this.hasStableFaceAim(this.pendingPlacement)) {
         this.aimYawWithJitter(this.lastBaseYaw, this.jitter(0.1F));
         this.aimPitch(clampPitch(this.lastBasePitch));
         this.lastYawBucket = Math.round(MathHelper.wrapAngleTo180_float(this.targetYaw - cameraYaw) / 45.0F);
         verifiedHit = this.raycastFace(this.pendingPlacement, this.targetYaw, this.targetPitch);
      }

      float nearestBucket = Math.round(MathHelper.wrapAngleTo180_float(this.targetYaw - cameraYaw) / 45.0F);
      float[] yawBuckets = this.isLookLocked() && this.isLongTellyEnabled() && this.queuedBlockCount > 0 && !cameraAligned && nearestBucket != lookBucket
         ? new float[]{lookBucket, nearestBucket}
         : new float[]{nearestBucket};

      for (int bucketIndex = 0; bucketIndex < yawBuckets.length && verifiedHit == null; bucketIndex++) {
         float bucket = yawBuckets[bucketIndex];
         float bucketTolerance = bucket == lookBucket ? Math.max(rotationLimit, 46.0F) : rotationLimit;
         float yawJitter = this.jitter(0.1F);
         float candidateYaw = cameraYaw + bucket * 45.0F + yawJitter;
         if (Math.abs(MathHelper.wrapAngleTo180_float(candidateYaw - this.targetYaw)) <= bucketTolerance) {
            float facePitch = this.findNearestFacePitch(this.pendingPlacement, candidateYaw, previousPitch);
            if (!Float.isNaN(facePitch)) {
               float candidatePitch = clampPitch(facePitch);
               Vec3 candidateHit = this.raycastFace(this.pendingPlacement, candidateYaw, this.targetPitch);
               float selectedPitch = this.targetPitch;
               if (candidateHit == null) {
                  selectedPitch = candidatePitch;
                  candidateHit = this.raycastFace(this.pendingPlacement, candidateYaw, candidatePitch);
               }

               if (candidateHit != null) {
                  this.aimYawWithJitter(candidateYaw - yawJitter, yawJitter);
                  this.aimPitch(selectedPitch);
                  this.lastYawBucket = Math.round(bucket);
                  verifiedHit = candidateHit;
               }
            }
         }
      }

      if (verifiedHit == null) {
         verifiedHit = this.raycastFace(this.pendingPlacement, this.targetYaw, this.targetPitch);
      }

      if (verifiedHit == null && Math.abs(yawDelta) <= rotationLimit && Math.abs(pitchDelta) <= rotationLimit) {
         float adjustedPitch = this.findNearestFacePitch(this.pendingPlacement, this.targetYaw, this.targetPitch);
         if (!Float.isNaN(adjustedPitch)) {
            this.aimPitch(clampPitch(adjustedPitch));
            verifiedHit = this.raycastFace(this.pendingPlacement, this.targetYaw, this.targetPitch);
         }
      }

      if (verifiedHit != null) {
         this.pendingPlacementReady = true;
         this.queueStallTicks = 0;
      } else {
         if (this.pendingQueueIndex >= 0 && ++this.queueStallTicks >= 3) {
            this.queueRetryTicks[this.pendingQueueIndex] = 2;
            this.skippedQueueMask = this.skippedQueueMask | 1 << this.pendingQueueIndex;
            this.queueStallTicks = 0;
            this.pendingQueueIndex = -1;
         }
      }
   }

   private void easeActivationRotation(EntityPlayerSP player, float previousYaw, float previousPitch, int lookBucket, boolean cameraAligned) {
      float smoothTicks = 3.0F;
      boolean ramping = smoothTicks >= 1.0F && this.activationTicks <= smoothTicks;
      float easeFraction = ramping ? 1.0F - (float)Math.pow(0.05, 1.0 / smoothTicks) : 1.0F;
      if (ramping) {
         float distance = Math.max(Math.abs(MathHelper.wrapAngleTo180_float(this.targetYaw - previousYaw)), Math.abs(this.targetPitch - previousPitch));
         easeFraction = Math.min(1.0F, easeFraction * this.rotationEaseScale(distance));
      }

      float targetBucket = Math.round(MathHelper.wrapAngleTo180_float(this.targetYaw - this.yawGridBase) / 45.0F);
      if (this.isLookLocked() && this.isLongTellyEnabled() && this.queuedBlockCount > 0 && !cameraAligned) {
         targetBucket = lookBucket;
      }

      float currentBucket = Math.round(MathHelper.wrapAngleTo180_float(previousYaw - this.yawGridBase) / 45.0F);

      while (targetBucket - currentBucket > 4.0F) {
         targetBucket -= 8.0F;
      }

      while (targetBucket - currentBucket < -4.0F) {
         targetBucket += 8.0F;
      }

      float nextBucket = targetBucket;
      float maxStep = 1.0F;
      boolean stepping = !player.onGround;
      if (ramping) {
         this.activationYawAccumulator = this.activationYawAccumulator + Math.abs(targetBucket - currentBucket) * easeFraction * 0.72F;
         maxStep = (float)Math.floor(this.activationYawAccumulator);
         this.activationYawAccumulator -= maxStep;
         stepping = true;
      } else {
         this.activationYawAccumulator = 0.0F;
      }

      if (stepping && Math.abs(targetBucket - currentBucket) > maxStep) {
         nextBucket = currentBucket + Math.signum(targetBucket - currentBucket) * maxStep;
      }

      this.aimYawWithJitter(this.yawGridBase + nextBucket * 45.0F, this.jitter(0.18F));
      if (ramping) {
         float pitchDelta = this.targetPitch - previousPitch;
         if (Math.abs(pitchDelta) > 0.5F) {
            this.aimPitch(clampPitch(previousPitch + pitchDelta * easeFraction));
         }
      }
   }

   private void sendRotations() {
      owner.sendHypixelRotations(this.targetYaw, this.targetPitch);
      this.previousSentPitch = this.lastSentPitch;
      this.lastSentYaw = this.targetYaw;
      this.lastSentPitch = this.targetPitch;
      this.lastBaseYaw = this.targetYaw - this.pendingYawJitter;
      this.lastBasePitch = this.targetPitch;
      this.rotationSentThisTick = true;
      
      this.movementFixActive = owner.hypixelMovementFix();
   }

   private void placeSelectedBlock(int groundCandidateIndex, Vec3 verifiedHit, HypixelScaffold.Placement placement) {
      if (this.pendingPlacementReady && this.placementDelayTicks > 0 && !this.isLongTellyEnabled()) {
      }

      if (groundCandidateIndex >= 0 && this.placementDelayTicks <= 0) {
         this.placeBlock(this.candidates[groundCandidateIndex]);
      } else if (verifiedHit != null && this.placementDelayTicks <= 0) {
         this.placeBlock(placement);
      } else if (this.pendingPlacementReady && (this.isLongTellyEnabled() || this.placementDelayTicks <= 0) && this.placeBlock(this.pendingPlacement)) {
         this.verificationBlock = this.pendingPlacement.getTarget();
         this.verificationDelayTicks = 4;
      }
   }

   private void updateKeepYPlacement(EntityPlayerSP player, Vec3 position, Vec3 motion) {
      if (this.getActiveKeepYMode().equals("Telly B") && this.keepYStage > 0 && !player.onGround) {
         int nextBlockY = MathHelper.floor_double(position.yCoord + motion.yCoord);
         if (nextBlockY <= this.keepYStartHeight && position.yCoord > this.keepYStartHeight + 1) {
            this.useCurrentHeight = true;
            HypixelScaffold.Placement placement = this.findPlacement();
            if (placement != null && this.placementDelayTicks <= 0 && !this.placedThisTick) {
               Vec3 hit = this.raycastFace(placement, this.lastSentYaw, this.lastSentPitch);
               if (hit != null) {
                  this.placeBlock(placement);
               }
            }
         }
      }

      if (this.isKeepYActive() && this.keepYStage > 0 && this.placementDelayTicks <= 0) {
         int placements = this.placementAttemptsThisTick;

         while (placements < this.getPlacementLimit() && this.placeKeepYFromRay()) {
            placements++;
         }
      }
   }

   private boolean allowPacket(Packet<?> packet) {
      if (packet instanceof C08PacketPlayerBlockPlacement) {
         return !this.sendingPlacement && !this.sendingAutoClick ? !this.rotationSentThisTick && !this.rotationSentLastTick : true;
      } else if (!(packet instanceof C07PacketPlayerDigging)) {
         return true;
      } else {
         C07PacketPlayerDigging dig = (C07PacketPlayerDigging)packet;
         if (dig.getStatus() != Action.START_DESTROY_BLOCK
            && dig.getStatus() != Action.ABORT_DESTROY_BLOCK
            && dig.getStatus() != Action.STOP_DESTROY_BLOCK) {
            return true;
         } else {
            EntityPlayerSP player = mc.thePlayer;
            return player != null && player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemBlock
               ? !this.rotationSentThisTick && !this.rotationSentLastTick
               : true;
         }
      }
   }

   private boolean allowMouse(int button) {
      return mc.currentScreen != null ? true : button > 1;
   }

   private int buildCandidates(int groundY) {
      EntityPlayerSP player = mc.thePlayer;
      Vec3 position = player.getPositionVector();
      int minX = MathHelper.floor_double(position.xCoord - 0.3);
      int maxX = MathHelper.floor_double(position.xCoord + 0.3);
      int minZ = MathHelper.floor_double(position.zCoord - 0.3);
      int maxZ = MathHelper.floor_double(position.zCoord + 0.3);
      double motionX = player.motionX;
      double motionZ = player.motionZ;
      double horizontalSpeed = RavenBlockAccess.getHorizontalSpeed(player);
      boolean forwardOnly = horizontalSpeed >= 0.02;
      int count = 0;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (this.isSolidSupport(x, groundY, z) && !this.isInteractableAt(x, groundY, z)) {
               for (int f = 0; f < HORIZONTAL_FACES.length && count < this.candidates.length; f++) {
                  EnumFacing face = HORIZONTAL_FACES[f];
                  int targetX = x + face.getFrontOffsetX();
                  int targetZ = z + face.getFrontOffsetZ();
                  if ((
                        !forwardOnly
                           || this.neededBlock != null && targetX == this.neededBlock.getX() && targetZ == this.neededBlock.getZ()
                           || !(face.getFrontOffsetX() * motionX + face.getFrontOffsetZ() * motionZ < 0.15 * horizontalSpeed)
                     )
                     && this.isReplaceableAt(targetX, groundY, targetZ)) {
                     this.candidates[count] = new HypixelScaffold.Placement(new BlockPos(x, groundY, z), face);
                     count++;
                  }
               }
            }
         }
      }

      return count;
   }

   private int matchCandidate(int candidateCount, float candidateYaw, float candidatePitch) {
      MovingObjectPosition hit = RavenBlockAccess.rayCastBlock(4.5, candidateYaw, candidatePitch);
      if (hit == null) {
         return -1;
      } else {
         for (int i = 0; i < candidateCount; i++) {
            if (this.candidates[i].matches(hit)) {
               return i;
            }
         }

         return -1;
      }
   }

   private void buildPrediction() {
      this.hasPrediction = true;
      EntityPlayerSP player = mc.thePlayer;
      Vec3 position = player.getPositionVector();
      Vec3 motion = new Vec3(player.motionX, player.motionY, player.motionZ);
      Arrays.fill(this.predictedPositions, position);

      try {
         RavenSimulatedPlayer simulatedPlayer = this.createSimulation();
         if (this.isShortDiagonalJump()) {
            simulatedPlayer.rotationYaw = player.rotationYaw;
            simulatedPlayer.movementInput.moveForward = this.getForwardInput();
            simulatedPlayer.movementInput.moveStrafe = this.getStrafeInput();
            simulatedPlayer.movementInput.jump = false;
         }

         this.predictedPositions[0] = new Vec3(position.xCoord, simulatedPlayer.getPos().yCoord, position.zCoord);

         for (int t = 1; t < this.predictedPositions.length; t++) {
            simulatedPlayer.tick();
            this.predictedPositions[t] = simulatedPlayer.getPos();
         }
      } catch (Throwable var10) {
         double verticalMotion = motion.yCoord;
         double y = position.yCoord;

         for (int t = 1; t < this.predictedPositions.length; t++) {
            y += verticalMotion;
            verticalMotion = (verticalMotion - 0.08) * 0.98;
            this.predictedPositions[t] = new Vec3(position.xCoord + motion.xCoord * t, y, position.zCoord + motion.zCoord * t);
         }
      }
   }

   private boolean isAbovePredictedPath(int blockX, int blockY, int blockZ) {
      EntityPlayerSP player = mc.thePlayer;
      Vec3 position = player.getPositionVector();
      if (blockY + 1.0 <= position.yCoord + 0.001) {
         return false;
      } else {
         if (!this.hasPrediction) {
            this.buildPrediction();
         }

         double dx = blockX + 0.5 - position.xCoord;
         double dz = blockZ + 0.5 - position.zCoord;
         double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
         float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
         float quadrantAngle = Math.abs(targetYaw % 90.0F);
         boolean diagonal = quadrantAngle > 22.5F && quadrantAngle < 67.5F;
         int predictionIndex = (int)(horizontalDistance * (diagonal ? 1.5 : 1.0) + 0.7);
         predictionIndex = MathHelper.clamp_int(predictionIndex, 0, this.predictedPositions.length - 1);
         return blockY > this.predictedPositions[predictionIndex].yCoord;
      }
   }


   private double getSupportOverhang(double playerX, double playerZ) {
      EntityPlayerSP player = mc.thePlayer;
      int groundY = MathHelper.floor_double(player.getPositionVector().yCoord) - 1;
      int centerX = MathHelper.floor_double(playerX);
      int centerZ = MathHelper.floor_double(playerZ);
      double best = 999.0;

      for (int x = centerX - 1; x <= centerX + 1; x++) {
         for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            if (this.isSolidSupport(x, groundY, z)) {
               double nearestX = MathHelper.clamp_double(playerX, x, x + 1.0);
               double nearestZ = MathHelper.clamp_double(playerZ, z, z + 1.0);
               double distance = Math.max(Math.abs(playerX - nearestX), Math.abs(playerZ - nearestZ));
               if (distance < best) {
                  best = distance;
               }
            }
         }
      }

      return best;
   }


   private void findNeededBlock(int groundY) {
      this.neededBlock = null;
      EntityPlayerSP player = mc.thePlayer;
      Vec3 position = player.getPositionVector();

      try {
         RavenSimulatedPlayer simulatedPlayer = this.createSimulation();

         for (int t = 0; t < 5 && this.neededBlock == null; t++) {
            simulatedPlayer.tick();
            Vec3 predictedPosition = simulatedPlayer.getPos();
            this.checkUnsupportedCorners(predictedPosition.xCoord, predictedPosition.zCoord, groundY);
         }
      } catch (Throwable var7) {
         Vec3 motion = new Vec3(player.motionX, player.motionY, player.motionZ);

         for (int k = 1; k <= 5 && this.neededBlock == null; k++) {
            this.checkUnsupportedCorners(position.xCoord + motion.xCoord * k, position.zCoord + motion.zCoord * k, groundY);
         }
      }
   }

   private RavenSimulatedPlayer createSimulation() {
      RavenSimulatedPlayer simulatedPlayer = RavenSimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
      if (this.predictionInputPlayer == mc.thePlayer && !Float.isNaN(this.predictionInputYaw)) {
         simulatedPlayer.rotationYaw = this.predictionInputYaw;
      }

      simulatedPlayer.movementInput.sneak = false;
      return simulatedPlayer;
   }

   private void checkUnsupportedCorners(double playerX, double playerZ, int groundY) {
      Vec3 motion = new Vec3(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ);
      double bestScore = -1.0E9;

      for (int corner = 0; corner < 4; corner++) {
         double offsetX = (corner & 1) == 0 ? -0.3 : 0.3;
         double offsetZ = (corner & 2) == 0 ? -0.3 : 0.3;
         int blockX = MathHelper.floor_double(playerX + offsetX);
         int blockZ = MathHelper.floor_double(playerZ + offsetZ);
         if (this.isReplaceableAt(blockX, groundY, blockZ)) {
            double score = offsetX * motion.xCoord + offsetZ * motion.zCoord;
            double centerOffsetX = blockX + 0.5 - playerX;
            double centerOffsetZ = blockZ + 0.5 - playerZ;
            float cellYaw = (float)Math.toDegrees(Math.atan2(-centerOffsetX, centerOffsetZ));
            float yawOffset = Math.abs(MathHelper.wrapAngleTo180_float(cellYaw - this.lastSentYaw));
            score += 0.01 * (180.0F - yawOffset) / 180.0;
            if (score > bestScore) {
               bestScore = score;
               this.neededBlock = new BlockPos(blockX, groundY, blockZ);
            }
         }
      }
   }

   private HypixelScaffold.FaceGeometry getFaceGeometry(HypixelScaffold.Placement placement, float candidateYaw) {
      int blockX = placement.support.getX();
      int blockY = placement.support.getY();
      int blockZ = placement.support.getZ();
      EnumFacing face = placement.face;
      EntityPlayerSP player = mc.thePlayer;
      Vec3 position = player.getPositionVector();
      double eyeX = position.xCoord;
      double eyeY = position.yCoord + player.getEyeHeight();
      double eyeZ = position.zCoord;
      double directionX = -Math.sin(Math.toRadians(candidateYaw));
      double directionZ = Math.cos(Math.toRadians(candidateYaw));
      if (face == EnumFacing.UP) {
         double topY = blockY + 1.0;
         if (eyeY <= topY + 0.05) {
            return null;
         } else {
            double nearDistance = 0.001;
            double farDistance = 4.0;
            if (Math.abs(directionX) < 1.0E-6) {
               if (eyeX < blockX + 0.05 || eyeX > blockX + 0.95) {
                  return null;
               }
            } else {
               double minEdgeIntersection = (blockX + 0.05 - eyeX) / directionX;
               double maxEdgeIntersection = (blockX + 0.95 - eyeX) / directionX;
               nearDistance = Math.max(nearDistance, Math.min(minEdgeIntersection, maxEdgeIntersection));
               farDistance = Math.min(farDistance, Math.max(minEdgeIntersection, maxEdgeIntersection));
            }

            if (Math.abs(directionZ) < 1.0E-6) {
               if (eyeZ < blockZ + 0.05 || eyeZ > blockZ + 0.95) {
                  return null;
               }
            } else {
               double minEdgeIntersection = (blockZ + 0.05 - eyeZ) / directionZ;
               double maxEdgeIntersection = (blockZ + 0.95 - eyeZ) / directionZ;
               nearDistance = Math.max(nearDistance, Math.min(minEdgeIntersection, maxEdgeIntersection));
               farDistance = Math.min(farDistance, Math.max(minEdgeIntersection, maxEdgeIntersection));
            }

            return nearDistance >= farDistance ? null : new HypixelScaffold.FaceGeometry(true, nearDistance, farDistance, eyeY, topY);
         }
      } else if (face.getFrontOffsetY() != 0) {
         return null;
      } else {
         double distance;
         double hitCoordinate;
         double blockEdge;
         if (face.getFrontOffsetX() != 0) {
            double facePlane = blockX + (face.getFrontOffsetX() > 0 ? 1.0 : 0.0);
            if ((eyeX - facePlane) * face.getFrontOffsetX() <= 0.0 || Math.abs(directionX) < 1.0E-6) {
               return null;
            }

            distance = (facePlane - eyeX) / directionX;
            hitCoordinate = eyeZ + directionZ * distance;
            blockEdge = blockZ;
         } else {
            double facePlane = blockZ + (face.getFrontOffsetZ() > 0 ? 1.0 : 0.0);
            if ((eyeZ - facePlane) * face.getFrontOffsetZ() <= 0.0 || Math.abs(directionZ) < 1.0E-6) {
               return null;
            }

            distance = (facePlane - eyeZ) / directionZ;
            hitCoordinate = eyeX + directionX * distance;
            blockEdge = blockX;
         }

         if (distance <= 0.0 || distance > 4.0) {
            return null;
         } else {
            return !(hitCoordinate < blockEdge + 0.05) && !(hitCoordinate > blockEdge + 0.95)
               ? new HypixelScaffold.FaceGeometry(false, distance, distance, eyeY, blockY)
               : null;
         }
      }
   }

   private float findFacePitch(HypixelScaffold.Placement placement, float candidateYaw) {
      HypixelScaffold.FaceGeometry geometry = this.getFaceGeometry(placement, candidateYaw);
      if (geometry == null) {
         return Float.NaN;
      } else {
         double[] samples = geometry.topFace ? TOP_FACE_SAMPLES : SIDE_FACE_SAMPLES;

         for (double sample : samples) {
            float candidatePitch;
            if (geometry.topFace) {
               double sampleDistance = geometry.nearDistance + (geometry.farDistance - geometry.nearDistance) * sample;
               candidatePitch = (float)Math.toDegrees(Math.atan2(geometry.eyeY - geometry.surfaceY, sampleDistance));
            } else {
               candidatePitch = (float)Math.toDegrees(Math.atan2(geometry.eyeY - (geometry.surfaceY + sample), geometry.nearDistance));
            }

            if (candidatePitch > 90.0F) {
               candidatePitch = 90.0F;
            }

            candidatePitch = this.quantizeRotation(candidatePitch);
            if (this.raycastFace(placement, candidateYaw, candidatePitch) != null) {
               return candidatePitch;
            }
         }

         return Float.NaN;
      }
   }

   private float findNearestFacePitch(HypixelScaffold.Placement placement, float candidateYaw, float currentPitch) {
      HypixelScaffold.FaceGeometry geometry = this.getFaceGeometry(placement, candidateYaw);
      if (geometry == null) {
         return Float.NaN;
      } else {
         double minimumPitch;
         double maximumPitch;
         if (geometry.topFace) {
            minimumPitch = Math.toDegrees(Math.atan2(geometry.eyeY - geometry.surfaceY, geometry.farDistance));
            maximumPitch = Math.toDegrees(Math.atan2(geometry.eyeY - geometry.surfaceY, geometry.nearDistance));
         } else {
            minimumPitch = Math.toDegrees(Math.atan2(geometry.eyeY - (geometry.surfaceY + 0.95), geometry.nearDistance));
            maximumPitch = Math.toDegrees(Math.atan2(geometry.eyeY - (geometry.surfaceY + 0.05), geometry.nearDistance));
         }

         double margin = Math.min(0.35, (maximumPitch - minimumPitch) * 0.25);
         double innerMinimum = minimumPitch + margin;
         double innerMaximum = maximumPitch - margin;
         if (innerMinimum > innerMaximum) {
            innerMinimum = (minimumPitch + maximumPitch) * 0.5;
            innerMaximum = innerMinimum;
         }

         if (currentPitch >= innerMinimum && currentPitch <= innerMaximum && this.raycastFace(placement, candidateYaw, currentPitch) != null) {
            return currentPitch;
         } else {
            double desiredPitch = currentPitch;
            double span = innerMaximum - innerMinimum;
            double step = Math.min(0.45, span * 0.3);
            if (desiredPitch < innerMinimum) {
               desiredPitch = innerMinimum + this.randomFloat(0.0F, (float)step);
            } else if (desiredPitch > innerMaximum) {
               desiredPitch = innerMaximum - this.randomFloat(0.0F, (float)step);
            }

            float candidatePitch = this.quantizeRotation(clampPitch((float)desiredPitch));
            if (this.raycastFace(placement, candidateYaw, candidatePitch) != null) {
               return candidatePitch;
            } else {
               double[] samples = new double[]{
                  0.5 + this.randomFloat(-0.06F, 0.06F), 0.3 + this.randomFloat(-0.05F, 0.05F), 0.7 + this.randomFloat(-0.05F, 0.05F)
               };

               for (double sample : samples) {
                  float samplePitch = this.quantizeRotation(clampPitch((float)(minimumPitch + (maximumPitch - minimumPitch) * sample)));
                  if (this.raycastFace(placement, candidateYaw, samplePitch) != null) {
                     return samplePitch;
                  }
               }

               return Float.NaN;
            }
         }
      }
   }

   private int getCandidateScore(int index) {
      if (this.neededBlock == null) {
         return 0;
      } else {
         HypixelScaffold.Placement candidate = this.candidates[index];
         int targetX = candidate.support.getX() + candidate.face.getFrontOffsetX();
         int targetZ = candidate.support.getZ() + candidate.face.getFrontOffsetZ();
         if (targetX == this.neededBlock.getX() && targetZ == this.neededBlock.getZ()) {
            return 2;
         } else {
            int distance = Math.abs(targetX - this.neededBlock.getX()) + Math.abs(targetZ - this.neededBlock.getZ());
            return distance == 1 ? 1 : 0;
         }
      }
   }

   private float getTravelYaw() {
      EntityPlayerSP player = mc.thePlayer;
      Vec3 motion = new Vec3(player.motionX, player.motionY, player.motionZ);
      return motion.xCoord * motion.xCoord + motion.zCoord * motion.zCoord > 0.0016
         ? (float)(Math.atan2(-motion.xCoord, motion.zCoord) * 180.0 / Math.PI)
         : this.getInputYaw();
   }

   private String getTellyMode() {
      return this.tellyMode.getInput() != -1.0 && (!this.tellyOnJump.isToggled() || this.isJumpPressed()) ? this.tellyMode.getSelectedOption() : "Disabled";
   }

   private String getKeepYMode() {
      return this.keepMode.getInput() == -1.0 ? "Disabled" : this.keepMode.getSelectedOption();
   }

   private boolean isIceSelectionEnabled() {
      return this.iceThreshold.getInput() > -0.595;
   }

   private boolean isKeepYActive() {
      return !this.getKeepYMode().equals("Disabled") && (!this.keepYOnRightClick.isToggled() || Mouse.isButtonDown(1));
   }

   private String getActiveKeepYMode() {
      return this.isKeepYActive() ? this.getKeepYMode() : "Disabled";
   }

   private float randomFloat(float minimum, float maximum) {
      return (float)(minimum + Math.random() * (maximum - minimum));
   }

   private float getPlayerYaw() {
      return mc.thePlayer.rotationYaw;
   }

   private float getServerYaw() {
      Float serverYaw = owner.hypixelServerYaw();
      return serverYaw == null ? this.getPlayerYaw() : serverYaw;
   }

   private float quantizeMouseRotation(float value, float reference) {
      float delta = value - reference;
      float snapped = Math.round(delta / 0.0234375F) * 0.0234375F;
      return reference + snapped;
   }

   private float unwrapYaw(float angle, float target) {
      return target + MathHelper.wrapAngleTo180_float(angle - target);
   }

   private float clampRotationDelta(float delta, float limit) {
      limit = MathHelper.clamp_float(limit, 0.0F, 180.0F);
      return MathHelper.clamp_float(delta, -limit, limit);
   }

   private float quantizeRotation(float angle) {
      return (float)(angle - angle % 0.0096F);
   }

   private float jitter(float maximumDegrees) {
      float offset = this.quantizeRotation(this.randomFloat(-maximumDegrees, maximumDegrees));
      if (offset == 0.0F) {
         offset = this.randomFloat(0.0F, 1.0F) < 0.5F ? 0.0096F : -0.0096F;
      }

      return offset;
   }

   private void updateYawJitter() {
      this.yawJitterAmplitude = this.yawJitterAmplitude + this.randomFloat(-0.055F, 0.055F);
      this.yawJitterAmplitude = MathHelper.clamp_float(this.yawJitterAmplitude, 0.11F, 0.46F);
   }

   private float nextYawJitter() {
      return this.jitter(this.yawJitterAmplitude);
   }

   private float rotationEaseScale(float distance) {
      float influence = MathHelper.clamp_float((distance - 1.0F) / 5.0F, 0.0F, 1.0F);
      return 1.0F + (this.rotationEaseRate - 1.0F) * influence;
   }

   private float[] easeRotations(float fromYaw, float fromPitch, float toYaw, float toPitch) {
      float yawDelta = MathHelper.wrapAngleTo180_float(toYaw - fromYaw);
      float pitchDelta = clampPitch(toPitch) - fromPitch;
      float distance = (float)Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
      if (distance <= 1.0F) {
         this.rotationEaseSlowdown = 0.0F;
         return new float[]{toYaw, clampPitch(toPitch)};
      } else {
         float speedLimit;
         if (!this.rotationEaseStarted) {
            speedLimit = 73.0F;
            this.rotationEaseStarted = true;
            this.rotationEaseSlowdown = 0.0F;
         } else {
            speedLimit = 43.0F - this.rotationEaseSlowdown;
            if (this.rotationEaseSlowdown < 10.0F) {
               this.rotationEaseSlowdown = this.rotationEaseSlowdown + this.randomFloat(3.0F, 4.0F);
            }
         }

         speedLimit -= this.randomFloat(0.1F, 1.0F);
         speedLimit *= this.rotationEaseScale(distance);
         float step = Math.min(speedLimit, distance);
         float scale = step / distance;
         return new float[]{fromYaw + yawDelta * scale, clampPitch(fromPitch + pitchDelta * scale)};
      }
   }

   private float smooth(float angle, float smoothingFactor) {
      float randomizedFactor = MathHelper.clamp_float(smoothingFactor + this.randomFloat(-0.1F, 0.1F), 0.0F, 1.0F);
      return angle * (0.5F + 0.5F * (1.0F - randomizedFactor));
   }

   private float[] getRotationsToOffset(double offsetX, double offsetY, double offsetZ, float currentYaw, float currentPitch) {
      double horizontalDistance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
      float yawDelta = MathHelper.wrapAngleTo180_float((float)(Math.atan2(offsetZ, offsetX) * 180.0 / Math.PI) - 90.0F - currentYaw);
      float pitchDelta = MathHelper.wrapAngleTo180_float((float)(-Math.atan2(offsetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
      yawDelta = Math.abs(yawDelta) <= 1.0F ? 0.0F : this.smooth(this.clampRotationDelta(yawDelta, 180.0F), 0.0F);
      pitchDelta = Math.abs(pitchDelta) <= 1.0F ? 0.0F : this.smooth(this.clampRotationDelta(pitchDelta, 180.0F), 0.0F);
      return new float[]{this.quantizeRotation(currentYaw + yawDelta), this.quantizeRotation(currentPitch + pitchDelta)};
   }

   private float getMovementYaw(float movementYaw, float forward, float strafe) {
      if (forward < 0.0F) {
         movementYaw += 180.0F;
      }

      if (strafe != 0.0F) {
         float strafeScale = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
         movementYaw += -90.0F * strafeScale * Math.signum(strafe);
      }

      return movementYaw;
   }

   private int getForwardInput() {
      int forward = 0;
      if (RavenBlockAccess.isBindDown(mc.gameSettings.keyBindForward)) {
         forward++;
      }

      if (RavenBlockAccess.isBindDown(mc.gameSettings.keyBindBack)) {
         forward--;
      }

      return forward;
   }

   private int getStrafeInput() {
      int strafe = 0;
      if (RavenBlockAccess.isBindDown(mc.gameSettings.keyBindLeft)) {
         strafe++;
      }

      if (RavenBlockAccess.isBindDown(mc.gameSettings.keyBindRight)) {
         strafe--;
      }

      return strafe;
   }

   private boolean isMoving() {
      boolean forward = RavenBlockAccess.isBindDown(mc.gameSettings.keyBindForward);
      boolean backward = RavenBlockAccess.isBindDown(mc.gameSettings.keyBindBack);
      boolean left = RavenBlockAccess.isBindDown(mc.gameSettings.keyBindLeft);
      boolean right = RavenBlockAccess.isBindDown(mc.gameSettings.keyBindRight);
      return forward != backward || left != right;
   }

   private boolean isJumpPressed() {
      return RavenBlockAccess.isBindDown(mc.gameSettings.keyBindJump);
   }

   private int updateCardinalSide(float yaw) {
      float quadrantAngle = ((yaw + 180.0F) % 90.0F + 90.0F) % 90.0F;
      if (quadrantAngle > 8.0F && quadrantAngle < 37.0F) {
         this.cardinalSide = 1;
      } else if (quadrantAngle > 53.0F && quadrantAngle < 82.0F) {
         this.cardinalSide = -1;
      }

      return this.cardinalSide;
   }

   private boolean isDiagonalMovement(float yaw) {
      float quadrantAngle = Math.abs(yaw % 90.0F);
      if (this.diagonalMovement) {
         if (quadrantAngle < 16.0F || quadrantAngle > 74.0F) {
            this.diagonalMovement = false;
         }
      } else if (quadrantAngle > 24.0F && quadrantAngle < 66.0F) {
         this.diagonalMovement = true;
      }

      return this.diagonalMovement;
   }

   private float getInputYaw() {
      return MathHelper.wrapAngleTo180_float(this.getMovementYaw(this.getPlayerYaw(), this.getForwardInput(), this.getStrafeInput()));
   }

   private boolean isTellyTakeoff() {
      if (mc.thePlayer.onGround && this.isMoving() && !this.hasCeilingAbove()) {
         String keepYMode = this.getActiveKeepYMode();
         boolean keepYEnabled = keepYMode.equals("Telly A") || keepYMode.equals("Telly B");
         boolean tellyEnabled = this.getTellyMode().equals("Short") || this.isLongTellyEnabled();
         return keepYEnabled && this.keepYStage > 0 || tellyEnabled && this.isJumpPressed();
      } else {
         return false;
      }
   }

   private boolean isManualTellyTakeoff() {
      return mc.thePlayer.onGround && this.isMoving() && !this.hasCeilingAbove() && this.isJumpPressed()
         ? this.getTellyMode().equals("Short") || this.isLongTellyEnabled()
         : false;
   }

   private boolean isLongTellyEnabled() {
      return this.getTellyMode().equals("Long");
   }

   private boolean isShortDiagonalJump() { return false; }

   private boolean isLookLocked() {
      return this.longTellyMissTicks < 4;
   }

   private EnumFacing faceFromDelta(int dx, int dz) {
      if (dx > 0) {
         return EnumFacing.EAST;
      } else if (dx < 0) {
         return EnumFacing.WEST;
      } else {
         return dz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
      }
   }

   private boolean intersectsPlayer(Vec3 position, int cx, int cy, int cz) {
      return cx + 1.0 > position.xCoord - 0.3
         && cx < position.xCoord + 0.3
         && cy + 1.0 > position.yCoord
         && cy < position.yCoord + 1.8
         && cz + 1.0 > position.zCoord - 0.3
         && cz < position.zCoord + 0.3;
   }

   private HypixelScaffold.Placement findQueueSupport(int supportIndex, boolean upFirst, int cx, int cy, int cz) {
      int directionIndex = upFirst ? (supportIndex == 0 ? 4 : supportIndex - 1) : supportIndex;
      int supportY = cy;
      int supportX;
      int supportZ;
      EnumFacing supportFace;
      if (directionIndex == 0) {
         supportX = cx - this.longTellyForwardX;
         supportZ = cz - this.longTellyForwardZ;
         supportFace = this.faceFromDelta(this.longTellyForwardX, this.longTellyForwardZ);
      } else if (directionIndex == 1) {
         supportX = cx - this.longTellyLateralX;
         supportZ = cz - this.longTellyLateralZ;
         supportFace = this.faceFromDelta(this.longTellyLateralX, this.longTellyLateralZ);
      } else if (directionIndex == 2) {
         supportX = cx + this.longTellyLateralX;
         supportZ = cz + this.longTellyLateralZ;
         supportFace = this.faceFromDelta(-this.longTellyLateralX, -this.longTellyLateralZ);
      } else if (directionIndex == 3) {
         supportX = cx + this.longTellyForwardX;
         supportZ = cz + this.longTellyForwardZ;
         supportFace = this.faceFromDelta(-this.longTellyForwardX, -this.longTellyForwardZ);
      } else {
         supportX = cx;
         supportY = cy - 1;
         supportZ = cz;
         supportFace = EnumFacing.UP;
      }

      return this.isSolidSupport(supportX, supportY, supportZ) && !this.isInteractableAt(supportX, supportY, supportZ)
         ? new HypixelScaffold.Placement(new BlockPos(supportX, supportY, supportZ), supportFace)
         : null;
   }

   private void buildLongTellyQueue(Vec3 motion) {
      this.resetLongTellyQueue();
      if (this.ticksSinceGrounded <= 3) {
         Vec3 position = mc.thePlayer.getPositionVector();
         this.longTellyRowY = MathHelper.floor_double(position.yCoord) - 1;
         double speedX = Math.abs(motion.xCoord);
         double speedZ = Math.abs(motion.zCoord);
         if (!(speedX * speedX + speedZ * speedZ < 0.01)) {
            int directionX = 0;
            int directionZ = 0;
            if (speedX >= speedZ * 2.4) {
               directionX = motion.xCoord > 0.0 ? 1 : -1;
            } else {
               if (!(speedZ >= speedX * 2.4)) {
                  return;
               }

               directionZ = motion.zCoord > 0.0 ? 1 : -1;
            }

            if (this.longTellySprintJump && this.longTellyNeedsSideUpdate) {
               this.longTellyNeedsSideUpdate = false;
               if (this.hasLongTellyOrigin) {
                  int travelledX = this.lastGroundX - this.longTellyOriginX;
                  int travelledZ = this.lastGroundZ - this.longTellyOriginZ;
                  if (travelledX * this.longTellyLateralX + travelledZ * this.longTellyLateralZ >= 1) {
                     this.longTellySide = -this.longTellySide;
                  }
               } else {
                  double lean = motion.xCoord * -directionZ + motion.zCoord * directionX;
                  if (lean > 0.02) {
                     this.longTellySide = 1;
                  } else if (lean < -0.02) {
                     this.longTellySide = -1;
                  }
               }

               this.longTellyOriginX = this.lastGroundX;
               this.longTellyOriginZ = this.lastGroundZ;
               this.hasLongTellyOrigin = true;
            }

            this.longTellyForwardX = directionX;
            this.longTellyForwardZ = directionZ;
            this.longTellyLateralX = -directionZ * this.longTellySide;
            this.longTellyLateralZ = directionX * this.longTellySide;

            for (int i = 0; i < 3; i++) {
               int forwardOffset = this.longTellySprintJump ? i + 1 : i;
               this.queuedBlocks[i] = new BlockPos(
                  this.lastGroundX + forwardOffset * directionX,
                  this.longTellyRowY + (this.longTellySprintJump ? 0 : 1),
                  this.lastGroundZ + forwardOffset * directionZ
               );
            }

            this.queuedBlockCount = 3;
         }
      }
   }

   private boolean findLongTellyPlacement() {
      EntityPlayerSP player = mc.thePlayer;
      Vec3 position = player.getPositionVector();
      float cameraYaw = this.yawGridBase;
      this.queueCursor = 0;

      while (this.queueCursor < this.queuedBlockCount && !this.isReplaceableAt(this.queuedBlocks[this.queueCursor])) {
         this.queueCursor++;
      }

      if (this.pendingQueueIndex >= 0 && (this.pendingQueueIndex < this.queueCursor || this.pendingQueueIndex >= this.queuedBlockCount)) {
         this.pendingQueueIndex = -1;
         this.queueStallTicks = 0;
      }

      for (int queueIndex = this.queueCursor; queueIndex < this.queuedBlockCount; queueIndex++) {
         if ((this.skippedQueueMask & 1 << queueIndex) == 0) {
            BlockPos queuedBlock = this.queuedBlocks[queueIndex];
            int cx = queuedBlock.getX();
            int cy = queuedBlock.getY();
            int cz = queuedBlock.getZ();
            if (!this.isReplaceableAt(queuedBlock)) {
               if (queueIndex == this.pendingQueueIndex) {
                  this.pendingQueueIndex = -1;
               }
            } else {
               double distanceX = cx + 0.5 - position.xCoord;
               double distanceZ = cz + 0.5 - position.zCoord;
               if (!(distanceX * distanceX + distanceZ * distanceZ > 18.0) && !this.intersectsPlayer(position, cx, cy, cz)) {
                  int firstPass = queueIndex == this.pendingQueueIndex ? 0 : 1;
                  boolean upFirst = cy > this.longTellyRowY || !player.onGround && player.motionY > 0.0;

                  for (int pass = firstPass; pass < 2; pass++) {
                     for (int supportIndex = 0; supportIndex < 5; supportIndex++) {
                        HypixelScaffold.Placement support = this.findQueueSupport(supportIndex, upFirst, cx, cy, cz);
                        if (support != null) {
                           float candidateYaw;
                           float candidatePitch;
                           if (pass == 0) {
                              candidateYaw = this.longTellyAimYaw;
                              candidatePitch = this.findNearestFacePitch(support, candidateYaw, this.lastSentPitch);
                           } else {
                              float[] candidateYaws = this.getLongTellyCandidateYaws(support, position, cameraYaw);
                              candidateYaw = Float.NaN;
                              candidatePitch = Float.NaN;

                              for (float baseYaw : candidateYaws) {
                                 float jitteredYaw = baseYaw + this.nextYawJitter();
                                 float facePitch = this.findNearestFacePitch(support, jitteredYaw, this.lastSentPitch);
                                 if (!Float.isNaN(facePitch)) {
                                    candidateYaw = jitteredYaw;
                                    candidatePitch = facePitch;
                                    break;
                                 }
                              }
                           }

                           if (!Float.isNaN(candidatePitch)) {
                              if (queueIndex != this.pendingQueueIndex) {
                                 this.queueStallTicks = 0;
                              }

                              this.pendingQueueIndex = queueIndex;
                              this.pendingPlacement = support;
                              this.longTellyAimYaw = candidateYaw;
                              this.longTellyAimPitch = candidatePitch;
                              this.longTellyYawBucket = Math.round(MathHelper.wrapAngleTo180_float(candidateYaw - cameraYaw) / 45.0F);
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private float[] getLongTellyCandidateYaws(HypixelScaffold.Placement placement, Vec3 position, float cameraYaw) {
      double faceCenterX = placement.support.getX() + 0.5 + placement.face.getFrontOffsetX() * 0.5;
      double faceCenterZ = placement.support.getZ() + 0.5 + placement.face.getFrontOffsetZ() * 0.5;
      float faceYaw = (float)Math.toDegrees(Math.atan2(-(faceCenterX - position.xCoord), faceCenterZ - position.zCoord));
      int faceBucket = Math.round(MathHelper.wrapAngleTo180_float(faceYaw - cameraYaw) / 45.0F);
      float heldYaw = cameraYaw + this.longTellyYawBucket * 45.0F;
      float[] candidateYaws = new float[]{
         heldYaw, cameraYaw + faceBucket * 45.0F, cameraYaw + (faceBucket - 1) * 45.0F, cameraYaw + (faceBucket + 1) * 45.0F, faceYaw
      };
      float[] yawScores = new float[candidateYaws.length];
      float backYaw = (float)Math.toDegrees(Math.atan2(this.longTellyForwardX, -this.longTellyForwardZ))
         + (this.lookingStraightBack ? 0.0F : 1 * this.lookSide * 45.0F);

      for (int first = 0; first < candidateYaws.length; first++) {
         yawScores[first] = this.isLookLocked()
            ? Math.abs(MathHelper.wrapAngleTo180_float(candidateYaws[first] - backYaw)) + 0.05F * Math.abs(MathHelper.wrapAngleTo180_float(candidateYaws[first] - heldYaw))
            : Math.abs(MathHelper.wrapAngleTo180_float(candidateYaws[first] - heldYaw));
      }

      for (int first = 0; first < candidateYaws.length; first++) {
         for (int second = first + 1; second < candidateYaws.length; second++) {
            if (yawScores[second] < yawScores[first]) {
               float previousYaw = candidateYaws[first];
               candidateYaws[first] = candidateYaws[second];
               candidateYaws[second] = previousYaw;
               float previousScore = yawScores[first];
               yawScores[first] = yawScores[second];
               yawScores[second] = previousScore;
            }
         }
      }

      return candidateYaws;
   }

   private boolean hasCeilingAbove() {
      Vec3 position = mc.thePlayer.getPositionVector();
      double minY = position.yCoord + 1.0;
      double maxY = position.yCoord + 1.0 + 1.8;
      int y0 = MathHelper.floor_double(minY);
      int y1 = MathHelper.floor_double(maxY - 1.0E-6);

      for (int y = y0; y <= y1; y++) {
         if (this.hasCornerSupport(position.xCoord, position.zCoord, y)) {
            return true;
         }
      }

      return false;
   }

   private boolean hasJumpPotion() {
      return mc.thePlayer.isPotionActive(Potion.jump);
   }

   private boolean isReplaceable(IBlockState state) {
      Block block = state.getBlock();
      return block == Blocks.snow_layer
         ? (Integer)state.getValue(BlockSnow.LAYERS) == 1
         : block == Blocks.air
            || block == Blocks.water
            || block == Blocks.flowing_water
            || block == Blocks.lava
            || block == Blocks.flowing_lava
            || block == Blocks.fire
            || block == Blocks.tallgrass
            || block == Blocks.deadbush
            || block == Blocks.double_plant
            || block == Blocks.vine;
   }

   private boolean isReplaceableAt(int x, int y, int z) {
      return this.isReplaceableAt(new BlockPos(x, y, z));
   }
   private boolean isReplaceableAt(BlockPos position) {
      return this.isReplaceable(RavenBlockAccess.getBlockState(position));
   }

   private boolean isSolidSupport(int x, int y, int z) {
      return !this.isReplaceableAt(x, y, z);
   }

   private boolean isInteractableAt(int x, int y, int z) {
      return RavenBlockAccess.isInteractable(RavenBlockAccess.getBlock(x, y, z));
   }

   private HypixelScaffold.Placement findPlacement() {
      EntityPlayerSP player = mc.thePlayer;
      Vec3 position = player.getPositionVector();
      boolean shortDiagonal = this.isShortDiagonalJump();
      BlockPos target = this.findPlacementTarget(player, position, shortDiagonal);
      return target == null ? null : this.findClosestPlacement(target, position, shortDiagonal);
   }

   private BlockPos findPlacementTarget(EntityPlayerSP player, Vec3 position, boolean shortDiagonal) {
      int playerBlockX = MathHelper.floor_double(position.xCoord);
      int playerBlockY = MathHelper.floor_double(position.yCoord);
      int playerBlockZ = MathHelper.floor_double(position.zCoord);
      int targetY = (this.keepYStage != 0 && !this.useCurrentHeight ? Math.min(playerBlockY, this.keepYStartHeight) : playerBlockY) - 1;
      int targetX = playerBlockX;
      int targetZ = playerBlockZ;
      if (shortDiagonal) {
         if (!this.hasPrediction) {
            this.buildPrediction();
         }

         int ahead = 1;

         while (ahead < 3 && this.predictedPositions[ahead].yCoord > targetY + 1.0) {
            ahead++;
         }

         if (this.hasCornerSupport(this.predictedPositions[ahead].xCoord, this.predictedPositions[ahead].zCoord, targetY)) {
            return null;
         }

         targetX = MathHelper.floor_double(this.predictedPositions[ahead].xCoord);
         targetZ = MathHelper.floor_double(this.predictedPositions[ahead].zCoord);
      } else if (!this.isReplaceableAt(playerBlockX, targetY, playerBlockZ)) {
         if (!player.onGround && (!this.isKeepYActive() || this.keepYStage <= 0)) {
            return null;
         }

         Vec3 motion = new Vec3(player.motionX, player.motionY, player.motionZ);
         double horizontalSpeed = RavenBlockAccess.getHorizontalSpeed(player);
         if (horizontalSpeed < 0.01) {
            return null;
         }

         double directionX = motion.xCoord / horizontalSpeed;
         double directionZ = motion.zCoord / horizontalSpeed;
         int previousX = playerBlockX;
         int previousZ = playerBlockZ;
         boolean foundGap = false;

         for (double distance = 0.25; distance <= 2.0 && !foundGap; distance += 0.25) {
            int nextX = MathHelper.floor_double(position.xCoord + directionX * distance);
            int nextZ = MathHelper.floor_double(position.zCoord + directionZ * distance);
            if (nextX != previousX || nextZ != previousZ) {
               nextX = previousX + MathHelper.clamp_int(nextX - previousX, -1, 1);
               nextZ = previousZ + MathHelper.clamp_int(nextZ - previousZ, -1, 1);
               if (nextX != previousX
                  && nextZ != previousZ
                  && !this.isSolidSupport(previousX, targetY, nextZ)
                  && !this.isSolidSupport(nextX, targetY, previousZ)) {
                  if (this.isReplaceableAt(previousX, targetY, nextZ)) {
                     targetX = previousX;
                     targetZ = nextZ;
                  } else {
                     if (!this.isReplaceableAt(nextX, targetY, previousZ)) {
                        return null;
                     }

                     targetX = nextX;
                     targetZ = previousZ;
                  }

                  foundGap = true;
               } else if (this.isReplaceableAt(nextX, targetY, nextZ)) {
                  targetX = nextX;
                  targetZ = nextZ;
                  foundGap = true;
               } else {
                  if (!this.isSolidSupport(nextX, targetY, nextZ)) {
                     return null;
                  }

                  previousX = nextX;
                  previousZ = nextZ;
               }
            }
         }

         if (!foundGap) {
            return null;
         }
      }

      return new BlockPos(targetX, targetY, targetZ);
   }

   private HypixelScaffold.Placement findClosestPlacement(BlockPos target, Vec3 position, boolean shortDiagonal) {
      int targetX = target.getX();
      int targetY = target.getY();
      int targetZ = target.getZ();
      double reachSquared = 20.25;
      HypixelScaffold.Placement bestPlacement = null;
      double bestDistance = 0.0;

      for (int dx = -4; dx <= 4; dx++) {
         for (int dy = -4; dy <= 0; dy++) {
            for (int dz = -4; dz <= 4; dz++) {
               int supportX = targetX + dx;
               int supportY = targetY + dy;
               int supportZ = targetZ + dz;
               if (this.keepYStage == 0 || this.useCurrentHeight || supportY < this.keepYStartHeight) {
                  double playerOffsetX = supportX + 0.5 - position.xCoord;
                  double playerOffsetY = supportY + 0.5 - position.yCoord;
                  double playerOffsetZ = supportZ + 0.5 - position.zCoord;
                  if (!(playerOffsetX * playerOffsetX + playerOffsetY * playerOffsetY + playerOffsetZ * playerOffsetZ > reachSquared)) {
                     double targetOffsetX = (double)supportX - targetX;
                     double targetOffsetY = (double)supportY - targetY;
                     double targetOffsetZ = (double)supportZ - targetZ;
                     double targetDistanceSquared = targetOffsetX * targetOffsetX + targetOffsetY * targetOffsetY + targetOffsetZ * targetOffsetZ;
                     if (shortDiagonal || bestPlacement == null || !(targetDistanceSquared >= bestDistance)) {
                        BlockPos support = new BlockPos(supportX, supportY, supportZ);
                        IBlockState supportState = RavenBlockAccess.getBlockState(support);
                        if (!this.isReplaceable(supportState) && !RavenBlockAccess.isInteractable(supportState.getBlock())) {
                           EnumFacing side = this.findBestPlacementFace(supportX, supportY, supportZ, targetX, targetY, targetZ);
                           if (side != null) {
                              if (shortDiagonal) {
                                 if (this.intersectsPlayer(
                                    position, supportX + side.getFrontOffsetX(), supportY + side.getFrontOffsetY(), supportZ + side.getFrontOffsetZ()
                                 )) {
                                    continue;
                                 }

                                 targetOffsetX += side.getFrontOffsetX();
                                 targetOffsetY += side.getFrontOffsetY();
                                 targetOffsetZ += side.getFrontOffsetZ();
                                 targetDistanceSquared = targetOffsetX * targetOffsetX + targetOffsetY * targetOffsetY + targetOffsetZ * targetOffsetZ;
                              }

                              if (bestPlacement == null || targetDistanceSquared < bestDistance) {
                                 bestDistance = targetDistanceSquared;
                                 bestPlacement = new HypixelScaffold.Placement(support, side);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (bestPlacement == null) {
         return null;
      } else {
         return bestPlacement;
      }
   }

   private EnumFacing findBestPlacementFace(int supportX, int supportY, int supportZ, int targetX, int targetY, int targetZ) {
      EnumFacing bestFace = null;
      double bestDistance = 0.0;

      for (EnumFacing face : PLACEMENT_FACES) {
         int blockX = supportX + face.getFrontOffsetX();
         int blockY = supportY + face.getFrontOffsetY();
         int blockZ = supportZ + face.getFrontOffsetZ();
         if (blockY <= targetY && this.isReplaceableAt(blockX, blockY, blockZ) && (!this.isKeepYActive() || this.keepYStage <= 0 || blockY == targetY)) {
            double offsetX = (double)blockX - targetX;
            double offsetY = (double)blockY - targetY;
            double offsetZ = (double)blockZ - targetZ;
            double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
            if (bestFace == null || distanceSquared < bestDistance || distanceSquared == bestDistance && face == EnumFacing.UP) {
               bestDistance = distanceSquared;
               bestFace = face;
            }
         }
      }

      return bestFace;
   }

   private Vec3 raycastFace(HypixelScaffold.Placement placement, float candidateYaw, float candidatePitch) {
      MovingObjectPosition hit = RavenBlockAccess.rayCastBlock(4.5, candidateYaw, candidatePitch);
      return placement.matches(hit) ? hit.hitVec : null;
   }

   private boolean hasStableFaceAim(HypixelScaffold.Placement placement) {
      return this.raycastFace(placement, this.lastBaseYaw, this.lastBasePitch) != null
         && this.raycastFace(placement, this.lastBaseYaw + 0.5F, this.lastBasePitch) != null
         && this.raycastFace(placement, this.lastBaseYaw - 0.5F, this.lastBasePitch) != null
         && this.raycastFace(placement, this.lastBaseYaw, clampPitch(this.lastBasePitch - 0.35F)) != null
         && this.raycastFace(placement, this.lastBaseYaw, clampPitch(this.lastBasePitch + 0.35F)) != null;
   }

   private Vec3 aimShortDiagonalFace(HypixelScaffold.Placement placement) {
      EntityPlayerSP player = mc.thePlayer;
      double dx = placement.support.getX() + 0.5 + placement.face.getFrontOffsetX() * 0.5 - player.posX;
      double dz = placement.support.getZ() + 0.5 + placement.face.getFrontOffsetZ() * 0.5 - player.posZ;
      float centerYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float[] yaws = new float[]{this.targetYaw, this.lastSentYaw, centerYaw};

      for (float desiredYaw : yaws) {
         float candidateYaw = this.quantizeMouseRotation(this.lastSentYaw + MathHelper.wrapAngleTo180_float(desiredYaw - this.lastSentYaw), this.lastSentYaw);

         for (int pass = 0; pass < 2; pass++) {
            float candidatePitch = pass == 0
               ? this.findNearestFacePitch(placement, candidateYaw, this.targetPitch)
               : this.findFacePitch(placement, candidateYaw);
            if (!Float.isNaN(candidatePitch)) {
               candidatePitch = clampPitch(this.quantizeMouseRotation(candidatePitch, this.lastSentPitch));
               Vec3 hit = this.raycastFace(placement, candidateYaw, candidatePitch);
               if (hit != null) {
                  this.aimYaw(candidateYaw);
                  this.aimPitch(candidatePitch);
                  this.lastYawBucket = Math.round(MathHelper.wrapAngleTo180_float(candidateYaw - this.yawGridBase) / 45.0F);
                  return hit;
               }
            }
         }
      }

      return null;
   }

   private boolean placeKeepYFromRay() {
      MovingObjectPosition hit = RavenBlockAccess.rayCastBlock(4.5, this.lastSentYaw, this.lastSentPitch);
      if (hit != null && hit.sideHit != EnumFacing.DOWN) {
         EntityPlayerSP player = mc.thePlayer;
         BlockPos support = hit.getBlockPos();
         BlockPos target = RavenBlockAccess.offsetPos(hit);
         int floorY = MathHelper.floor_double(player.posY);
         int placeY = (this.useCurrentHeight ? floorY : Math.min(floorY, this.keepYStartHeight)) - 1;
         if (target.getY() == placeY
            && this.isSolidSupport(support.getX(), support.getY(), support.getZ())
            && !this.isInteractableAt(support.getX(), support.getY(), support.getZ())
            && this.isReplaceableAt(target.getX(), target.getY(), target.getZ())) {
            double dx = target.getX() + 0.5 - player.posX;
            double dz = target.getZ() + 0.5 - player.posZ;
            double leadX = player.motionX * 2.0;
            double leadZ = player.motionZ * 2.0;
            double leadSq = leadX * leadX + leadZ * leadZ;
            double along = leadSq > 1.0E-4 ? MathHelper.clamp_double((dx * leadX + dz * leadZ) / leadSq, 0.0, 1.0) : 0.0;
            double offsetX = dx - leadX * along;
            double offsetZ = dz - leadZ * along;
            return offsetX * offsetX + offsetZ * offsetZ > 1.0
               ? false
               : this.placeBlock(new HypixelScaffold.Placement(support, hit.sideHit))
                  && !this.isReplaceableAt(target.getX(), target.getY(), target.getZ());
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int getPlacementLimit() {
      return MathHelper.clamp_int((int)this.multiplace.getInput(), 1, 3);
   }

   private boolean placeBlock(HypixelScaffold.Placement placement) {
      if (this.placementAttemptsThisTick >= this.getPlacementLimit()) {
         return false;
      } else {
         EnumFacing side = placement.face;
         EntityPlayerSP player = mc.thePlayer;
         if (RavenBlockAccess.nullCheck() && side != null) {
            BlockPos support = placement.support;
            BlockPos target = placement.getTarget();
            IBlockState supportState = RavenBlockAccess.getBlockState(support);
            if (!this.isReplaceable(supportState)
               && !supportState.getBlock().isReplaceable(mc.theWorld, support)
               && !RavenBlockAccess.isInteractable(supportState.getBlock())
               && this.isReplaceable(RavenBlockAccess.getBlockState(target))) {
               Vec3 exactHit = this.raycastFace(placement, this.lastSentYaw, this.lastSentPitch);
               if (exactHit != null && this.selectPlacementStack(player)) {
                  ItemStack heldStack = player.getHeldItem();
                  ItemBlock item = (ItemBlock)heldStack.getItem();
                  if (!RavenBlockAccess.canPlaceBlockOnSide(heldStack, support, side)) {
                     return false;
                  } else {
                     this.placementAttemptsThisTick++;
                     this.sendingPlacement = true;

                     boolean placed;
                     try {
                        placed = mc.playerController.onPlayerRightClick(player, mc.theWorld, heldStack, support, side, exactHit);
                     } finally {
                        this.sendingPlacement = false;
                     }

                     placed = placed && RavenBlockAccess.check(target, item.getBlock());
                     this.heldBlockCount = this.getBlockCount(player.getHeldItem());
                     if (placed) {
                        this.placedThisTick = true;
                        this.lastPlacementHit = exactHit;
                        this.lastPlacementTime = System.currentTimeMillis();
                        this.longTellyMissTicks = 0;




                        if (!this.vibeSilentSwing.isToggled()) {
                           mc.thePlayer.swingItem();
                        } else {
                           mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
                        }
                     }

                     return placed;
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private boolean isUnsupportedAt(double px, double pz) {
      EntityPlayerSP player = mc.thePlayer;
      int y = MathHelper.floor_double(player.getPositionVector().yCoord) - 1;
      return !this.hasCornerSupport(px, pz, y);
   }

   private boolean hasCornerSupport(double x, double z, int y) {
      return this.isSolidSupport(MathHelper.floor_double(x - 0.3), y, MathHelper.floor_double(z - 0.3))
         || this.isSolidSupport(MathHelper.floor_double(x + 0.3), y, MathHelper.floor_double(z - 0.3))
         || this.isSolidSupport(MathHelper.floor_double(x - 0.3), y, MathHelper.floor_double(z + 0.3))
         || this.isSolidSupport(MathHelper.floor_double(x + 0.3), y, MathHelper.floor_double(z + 0.3));
   }

   private boolean isValidBlock(ItemStack stack) {
      if (stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock) {
         ItemBlock item = (ItemBlock)stack.getItem();
         return RavenBlockAccess.canBePlaced(item) && !RavenBlockAccess.notFull(item.getBlock());
      } else {
         return false;
      }
   }

   private int findPreferredIceSlot(EntityPlayerSP player) {
      int iceSlot = -1;
      int normalSlot = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = player.inventory.getStackInSlot(i);
         if (this.isValidBlock(stack)) {
            Block block = ((ItemBlock)stack.getItem()).getBlock();
            if (block != Blocks.packed_ice && block != Blocks.ice) {
               if (normalSlot < 0) {
                  normalSlot = i;
               }
            } else if (iceSlot < 0) {
               iceSlot = i;
            }
         }
      }

      if (iceSlot < 0) {
         return -1;
      } else {
         boolean wantIce = player.motionY < this.iceThreshold.getInput() || player.onGround;
         return !wantIce && normalSlot >= 0 ? normalSlot : iceSlot;
      }
   }
   private static final class FaceGeometry {
      private final boolean topFace;
      private final double nearDistance;
      private final double farDistance;
      private final double eyeY;
      private final double surfaceY;

      private FaceGeometry(boolean topFace, double nearDistance, double farDistance, double eyeY, double surfaceY) {
         this.topFace = topFace;
         this.nearDistance = nearDistance;
         this.farDistance = farDistance;
         this.eyeY = eyeY;
         this.surfaceY = surfaceY;
      }
   }
   private static final class Placement {
      private final BlockPos support;
      private final EnumFacing face;

      private Placement(BlockPos support, EnumFacing face) {
         this.support = support;
         this.face = face;
      }

      private BlockPos getTarget() {
         return this.support.offset(this.face);
      }

      private boolean matches(MovingObjectPosition hit) {
         return hit != null && this.support.equals(hit.getBlockPos()) && this.face == hit.sideHit;
      }
   }
   private static final class SliderValue {
      private final java.util.function.DoubleSupplier input;
      private final java.util.function.Supplier<String> option;
      SliderValue(java.util.function.DoubleSupplier input, java.util.function.Supplier<String> option) { this.input = input; this.option = option; }
      double getInput() { return input.getAsDouble(); }
      String getSelectedOption() { return option.get(); }
   }
   private static final class ButtonValue {
      private final java.util.function.BooleanSupplier input;
      ButtonValue(java.util.function.BooleanSupplier input) { this.input = input; }
      boolean isToggled() { return input.getAsBoolean(); }
   }
}
