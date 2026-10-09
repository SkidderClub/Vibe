package dev.vibe.module.impl.combat;
import dev.vibe.Vibe;
import dev.vibe.module.impl.world.BedAuraModule;
import dev.vibe.module.impl.world.RavenBlockAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Mouse;

/** RavenBS Auto Block's Vanilla controller, hosted by Killaura's Hypixel mode. */
final class HypixelAutoblock {
   private final Minecraft mc = Minecraft.getMinecraft();
   private final KillAuraModule owner;
   private final NumberValue range, maxHurtTimeMs, maxHoldMs, cooldownMs, unblockOutOfRange;
   private final ButtonValue requireLmb, requireRmb, onlyWhenDamaged, ignoreTeammates, forceBlockAnimation, forceAttack;
   // Lag mode is not part of the requested Vanilla preset.
   private final NumberValue lagMaxDuration = new NumberValue(() -> 0);
   private final ButtonValue blockAgainImmediately = new ButtonValue(() -> false);
   private boolean isLagging;
   private int lagStartTick = -1;
   private int pendingForceAttacks;
   private boolean forceAttackUnblocked, isBlocking, manualBlock, targetWasInRange, unblockedAfterLeavingRange, allowingAlwaysInteraction;
   private int blockStartTick = -1;
   private long lastBlockEndTimeMs;
   private EntityPlayer currentTarget;
   private int lastSelfHurtTime, tickCounter;
   HypixelAutoblock(KillAuraModule owner) {
      this.owner = owner;
      range = new NumberValue(() -> owner.hypixelRange.getDouble());
      maxHurtTimeMs = new NumberValue(() -> owner.hypixelMaximumHurtTime.getDouble());
      maxHoldMs = new NumberValue(() -> owner.hypixelMaximumHoldDuration.getDouble());
      cooldownMs = new NumberValue(() -> owner.hypixelCooldown.getDouble());
      unblockOutOfRange = new NumberValue(() -> owner.hypixelUnblockOutOfRange.is("Once") ? 0 : owner.hypixelUnblockOutOfRange.is("Always") ? 1 : 2);
      requireLmb = new ButtonValue(() -> owner.hypixelRequireLeftMouse.isEnabled());
      requireRmb = new ButtonValue(() -> owner.hypixelRequireRightMouse.isEnabled());
      onlyWhenDamaged = new ButtonValue(() -> owner.hypixelDamaged.isEnabled());
      ignoreTeammates = new ButtonValue(() -> owner.hypixelIgnoreTeammates.isEnabled());
      forceAttack = new ButtonValue(() -> owner.hypixelForceAttack.isEnabled());
      forceBlockAnimation = new ButtonValue(() -> owner.hypixelForceBlockAnimation.isEnabled());
   }
   private boolean isEnabled() { return owner.isEnabled() && owner.isHypixelBlock(); }
   private boolean holdingSword() { return mc.thePlayer != null && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword; }
   private static BedAuraModule bedAura() { return Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
      : Vibe.getInstance().getModuleManager().getModule(BedAuraModule.class); }
   private static boolean mouseDown(int button) { return Mouse.isCreated() && Mouse.isButtonDown(button); }
   private static int msToTicks(double ms) { return ms <= 0.0 ? 0 : (int) Math.ceil(ms / 50.0); }
   private void releaseLag() { }
   private boolean isLagMode() { return false; }
   private boolean shouldStartLag() { return false; }
   private void startLag(int tick) { }
   void reset(boolean release) { resetState(release); }
   boolean blocksUse() { return allowingAlwaysInteraction || shouldBlockVanillaUse() || isFakeBlockOutOfRange(); }
   boolean blocksRightClick() { return shouldBlockVanillaUse(); }
   void frame() {
      if (!RavenBlockAccess.nullCheck()) syncBlockAnimation();
      else if (bedAura() != null && bedAura().isActivelyMining()) owner.setHypixelVisual(false);
      else if (mc.currentScreen == null || !isBlocking && !isLagging) syncBlockAnimation();
      else resetState(true);
   }
   public void onMouse(MouseEvent e) {
      if (RavenBlockAccess.nullCheck() && holdingSword()) {
         if (bedAura() == null || !bedAura().isActivelyMining()) {
            if (e.button == 1) {
               if (this.isAlwaysUnblockMode()) {
                  if (!e.buttonstate && this.allowingAlwaysInteraction) {
                     this.allowingAlwaysInteraction = false;
                     return;
                  }

                  if (e.buttonstate && this.canInteractWhileAlwaysUnblocked()) {
                     this.releaseLag();
                     this.stopBlocking(true);
                     this.manualBlock = false;
                     this.allowingAlwaysInteraction = true;
                     return;
                  }
               }

               e.setCanceled(true);
            }
         }
      }
   }

   public void onPrePlayerInteract() {
      this.forceAttackUnblocked = false;
      if (!RavenBlockAccess.nullCheck() || mc.thePlayer.isDead || mc.currentScreen != null) {
         this.resetState(true);
      } else if (bedAura() != null && bedAura().isActivelyMining()) {
         this.resetState(true);
      } else {
         int selfHurtTime = mc.thePlayer.hurtTime;
         boolean hurtAgain = selfHurtTime > this.lastSelfHurtTime;
         this.lastSelfHurtTime = selfHurtTime;
         if (!holdingSword()) {
            this.resetState(false);
         } else if (this.allowingAlwaysInteraction) {
            this.releaseLag();
            this.stopBlocking(true);
            this.manualBlock = false;
         } else {
            this.tickCounter++;
            int currentTick = this.tickCounter;
            if (!this.forceAttack.isToggled() || !mc.inGameHasFocus) {
               this.pendingForceAttacks = 0;
            }

            if (this.pendingForceAttacks > 0) {
               this.forceAttackUnblocked = true;
               this.releaseLag();
               this.stopBlocking(true);
               this.manualBlock = false;
            } else {
               if (!this.isLagMode() && this.isLagging) {
                  this.releaseLag();
               }

               this.currentTarget = RavenCombatAccess.findTarget(this.range.getInput() * this.range.getInput(), this.ignoreTeammates.isToggled());
               boolean killAuraAttacking = owner != null
                  && owner.isEnabled()
                  && true
                  && this.currentTarget != null;
               boolean rmbDown = mouseDown(1);
               boolean lmbDown = mouseDown(0) || killAuraAttacking;
               boolean hasTarget = this.currentTarget != null;
               boolean conditionsMet = hasTarget && this.checkConditions(lmbDown, rmbDown);
               boolean leftTargetRange = this.targetWasInRange && !hasTarget;
               this.targetWasInRange = hasTarget;
               int unblockMode = (int)this.unblockOutOfRange.getInput();
               if (unblockMode != 0 || !rmbDown || hasTarget) {
                  this.unblockedAfterLeavingRange = false;
               }

               if (this.isAlwaysUnblockMode() && !hasTarget) {
                  this.releaseLag();
                  this.stopBlocking(true);
                  this.manualBlock = false;
               } else if (unblockMode == 0 && rmbDown && leftTargetRange) {
                  if (this.isLagging) {
                     this.releaseLag();
                  }

                  this.stopBlocking(true);
                  this.manualBlock = false;
                  this.unblockedAfterLeavingRange = true;
               } else {
                  if (hurtAgain) {
                     this.releaseLag();
                     this.stopBlocking(true);
                     this.manualBlock = false;
                  }

                  if (!conditionsMet && rmbDown) {
                     if (!this.unblockedAfterLeavingRange) {
                        if (this.isLagging) {
                           this.releaseLag();
                        }

                        if (!this.isBlocking) {
                           this.startBlocking(currentTick);
                        }

                        this.manualBlock = true;
                     }
                  } else {
                     if (this.manualBlock) {
                        this.stopBlocking(true);
                        this.manualBlock = false;
                     }

                     if (this.isLagging) {
                        int lagMaxTicks = msToTicks(this.lagMaxDuration.getInput());
                        boolean lagExpired = lagMaxTicks > 0 && this.lagStartTick >= 0 && currentTick - this.lagStartTick >= lagMaxTicks;
                        if (lagExpired || !conditionsMet) {
                           this.releaseLag();
                           if (lagExpired && this.blockAgainImmediately.isToggled() && conditionsMet) {
                              this.startBlocking(currentTick);
                           }
                        }
                     }

                     if (!conditionsMet) {
                        this.stopBlocking(true);
                     } else {
                        if (!this.isBlocking && !this.isLagging && this.shouldPredictiveBlock()) {
                           this.startBlocking(currentTick);
                        }

                        if (this.isBlocking) {
                           int maxHoldTicks = msToTicks(this.maxHoldMs.getInput());
                           boolean timeExpired = maxHoldTicks > 0 && this.blockStartTick >= 0 && currentTick - this.blockStartTick >= maxHoldTicks;
                           if (timeExpired) {
                              if (this.shouldStartLag()) {
                                 this.startLag(currentTick);
                              }

                              this.stopBlocking(true);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void onForceAttack() {
      if (this.forceAttack.isToggled()
         && RavenBlockAccess.nullCheck()
         && !mc.thePlayer.isDead
         && mc.currentScreen == null
         && mc.inGameHasFocus
         && holdingSword()) {
         if (bedAura() == null || !bedAura().isActivelyMining()) {
            if (this.allowingAlwaysInteraction) {
               this.pendingForceAttacks = 0;
            } else {
               KeyBinding attackKey = mc.gameSettings.keyBindAttack;
               if (this.pendingForceAttacks > 0 && !mc.thePlayer.isUsingItem()) {
                  for (int i = 0; i < this.pendingForceAttacks; i++) {
                     KeyBinding.onTick(attackKey.getKeyCode());
                  }

                  this.pendingForceAttacks = 0;
               } else if (this.isBlocking || this.isLagging || mc.thePlayer.isBlocking() || this.pendingForceAttacks != 0) {
                  boolean attackPressed = attackKey.isPressed();
                  if (attackPressed || this.pendingForceAttacks != 0) {
                     if (mc.thePlayer.isUsingItem()) {
                        if (attackPressed) {
                           this.pendingForceAttacks++;
                        }

                        while (attackKey.isPressed()) {
                           this.pendingForceAttacks++;
                        }
                     } else {
                        KeyBinding.onTick(attackKey.getKeyCode());
                     }

                     this.forceAttackUnblocked = true;
                     this.releaseLag();
                     this.stopBlocking(true);
                     this.manualBlock = false;
                  }
               }
            }
         }
      }
   }

   private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
      return this.requireLmb.isToggled() && !lmbDown ? false : !this.requireRmb.isToggled() || rmbDown;
   }

   private boolean shouldPredictiveBlock() {
      int ourHurtTime = mc.thePlayer.hurtTime;
      int triggerTick = (int)Math.round(this.maxHurtTimeMs.getInput() / 50.0);
      triggerTick = Math.max(1, Math.min(10, triggerTick));
      return ourHurtTime == triggerTick || !this.onlyWhenDamaged.isToggled() && ourHurtTime == 0;
   }

   private boolean shouldBlockVanillaUse() {
      return this.isEnabled() && (this.isLagging || this.forceAttackUnblocked) && RavenBlockAccess.nullCheck() && holdingSword() && mc.currentScreen == null;
   }

   private void startBlocking(int currentTick) {
      if (!this.forceAttackUnblocked && holdingSword() && !this.isCooldownActive()) {
         int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
         KeyBinding.setKeyBindState(keyCode, true);
         KeyBinding.onTick(keyCode);
         this.isBlocking = true;
         this.blockStartTick = currentTick;
         this.syncBlockAnimation();
      }
   }

   private void stopBlocking(boolean forceRelease) {
      if (this.isBlocking || forceRelease) {
         boolean wasBlocking = this.isBlocking;
         int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
         KeyBinding.setKeyBindState(keyCode, false);
         this.isBlocking = false;
         this.blockStartTick = -1;
         if (wasBlocking) {
            this.lastBlockEndTimeMs = System.currentTimeMillis();
         }

         this.syncBlockAnimation();
      }
   }

   private boolean isCooldownActive() {
      double cooldown = this.cooldownMs.getInput();
      return cooldown > 0.0 && this.lastBlockEndTimeMs > 0L && System.currentTimeMillis() - this.lastBlockEndTimeMs < cooldown;
   }

   private boolean isAlwaysUnblockMode() {
      int unblockMode = (int)this.unblockOutOfRange.getInput();
      return unblockMode == 1 || unblockMode == 2;
   }

   private boolean canInteractWhileAlwaysUnblocked() {
      MovingObjectPosition hit = mc.objectMouseOver;
      if (hit == null) {
         return false;
      } else if (hit.typeOfHit == MovingObjectType.BLOCK) {
         return RavenBlockAccess.isInteractable(hit);
      } else if (hit.typeOfHit == MovingObjectType.ENTITY && hit.entityHit != null) {
         Entity entity = hit.entityHit;
         return !(entity instanceof EntityPlayer) || false;
      } else {
         return false;
      }
   }

   private boolean isFakeBlockOutOfRange() {
      return this.isEnabled()
         && (int)this.unblockOutOfRange.getInput() == 2
         && this.currentTarget == null
         && RavenBlockAccess.nullCheck()
         && !mc.thePlayer.isDead
         && mc.currentScreen == null
         && holdingSword()
         && (bedAura() == null || !bedAura().isActivelyMining());
   }

   private void resetState(boolean releaseUseKey) {
      this.pendingForceAttacks = 0;
      this.forceAttackUnblocked = false;
      boolean restorePhysicalUse = this.isBlocking && mc.gameSettings.keyBindUseItem.isKeyDown() && mouseDown(1) && mc.currentScreen == null;
      this.releaseLag();
      this.stopBlocking(releaseUseKey);
      this.manualBlock = false;
      this.targetWasInRange = false;
      this.unblockedAfterLeavingRange = false;
      this.allowingAlwaysInteraction = false;
      this.lastBlockEndTimeMs = 0L;
      this.currentTarget = null;
      this.lastSelfHurtTime = 0;
      this.syncBlockAnimation();
      if (restorePhysicalUse) {
         KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
      }
   }

   private void syncBlockAnimation() {
      boolean killAuraAttacking = owner != null
         && owner.isEnabled()
         && true
         && this.currentTarget != null;
      boolean requiredMouseButtonsDown = this.checkConditions(mouseDown(0) || killAuraAttacking, mouseDown(1));
      boolean continuousUndamagedBlock = !this.onlyWhenDamaged.isToggled() && this.currentTarget != null && !this.allowingAlwaysInteraction;
      boolean shouldAnimate = this.forceBlockAnimation.isToggled()
         && RavenBlockAccess.nullCheck()
         && mc.currentScreen == null
         && holdingSword()
         && requiredMouseButtonsDown
         && (continuousUndamagedBlock || this.isBlocking || this.isLagging);
      boolean fakeBlock = this.isFakeBlockOutOfRange()
         && mc.inGameHasFocus
         && !this.allowingAlwaysInteraction
         && RavenBlockAccess.isBindDown(mc.gameSettings.keyBindUseItem);
      owner.setHypixelVisual(isBlocking || shouldAnimate || fakeBlock);
   }
   private static final class NumberValue {
      private final java.util.function.DoubleSupplier value;
      NumberValue(java.util.function.DoubleSupplier value) { this.value = value; }
      double getInput() { return value.getAsDouble(); }
   }
   private static final class ButtonValue {
      private final java.util.function.BooleanSupplier value;
      ButtonValue(java.util.function.BooleanSupplier value) { this.value = value; }
      boolean isToggled() { return value.getAsBoolean(); }
   }
}
