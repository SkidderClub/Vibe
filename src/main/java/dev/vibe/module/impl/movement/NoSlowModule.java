package dev.vibe.module.impl.movement;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

/** Restores the local movement input lost while using selected vanilla items. */
public final class NoSlowModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Vanilla", "Vanilla", "Rewound"));
    private final MultiSelectSetting items = addSetting(new MultiSelectSetting("Items",
            Arrays.asList("Swords", "Bow", "Consumables", "Food", "Potions", "Other"),
            Arrays.asList("Swords", "Bow", "Consumables", "Food", "Potions")));
    private EntityPlayerSP hookedPlayer;
    private EntityPlayerSP releasedPlayer;
    private ItemStack releasedStack;
    private int releasedSlot;

    public NoSlowModule() {
        super("NoSlow", "Preserve movement while using selected items", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    public boolean applies() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.thePlayer.isRiding() || !minecraft.thePlayer.isUsingItem()
                || minecraft.thePlayer.getItemInUse() == null) {
            return false;
        }
        Item item = minecraft.thePlayer.getItemInUse().getItem();
        if (item instanceof ItemSword) return items.isSelected("Swords");
        if (item instanceof ItemBow) return items.isSelected("Bow");
        if (item instanceof ItemFood) return items.isSelected("Food") || items.isSelected("Consumables");
        if (item instanceof ItemPotion) return (!mode.is("Rewound") || !ItemPotion.isSplash(minecraft.thePlayer.getItemInUse().getMetadata()))
                && (items.isSelected("Potions") || items.isSelected("Consumables"));
        return items.isSelected("Other");
    }

    /** Exire's NCP release/move/reblock sequence; consuming items only change slowdown. */
    public void beforeWalking(Object entity) {
        clearRelease();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (entity != p || !isEnabled() || !mode.is("Rewound") || p == null || p.sendQueue == null
                || !items.isSelected("Swords") || p.getHeldItem() == null || !(p.getHeldItem().getItem() instanceof ItemSword)) return;
        KillAuraModule aura = KillAuraModule.activeController();
        if (!p.isBlocking() && (aura == null || !aura.isRewoundBlocking())) return;
        releasedPlayer = p;
        releasedStack = p.getHeldItem();
        releasedSlot = p.inventory.currentItem;
        p.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN, EnumFacing.DOWN));
    }

    public void afterWalking(Object entity) {
        EntityPlayerSP p = releasedPlayer;
        ItemStack stack = releasedStack;
        int slot = releasedSlot;
        clearRelease();
        if (p != null && entity == p && p == Minecraft.getMinecraft().thePlayer && isEnabled() && mode.is("Rewound")
                && p.sendQueue != null && p.inventory.currentItem == slot && p.getHeldItem() == stack) {
            p.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, stack, 0, 0, 0));
        }
    }

    private void clearRelease() { releasedPlayer = null; releasedStack = null; }
    public void endWalking() { clearRelease(); }
    @Override protected void onDisable() { clearRelease(); }

    /**
     * Forge 1.8.9 has no InputUpdateEvent.  Wrapping the player's normal
     * MovementInput lets us adjust the values at the exact point Minecraft
     * reads the keyboard, immediately before vanilla applies its 0.2 use-item
     * multiplier.  The original MovementInput remains the sole owner of key
     * state, so this does not synthesize movement or packets.
     */
    public void installInputHook() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || player.movementInput == null || player == hookedPlayer || player.movementInput instanceof VibeMovementInput) {
            return;
        }
        player.movementInput = new VibeMovementInput(player.movementInput, this);
        hookedPlayer = player;
    }

    private static final class VibeMovementInput extends MovementInput {

        private final MovementInput vanillaInput;
        private final NoSlowModule module;

        private VibeMovementInput(MovementInput vanillaInput, NoSlowModule module) {
            this.vanillaInput = vanillaInput;
            this.module = module;
            moveStrafe = vanillaInput.moveStrafe;
            moveForward = vanillaInput.moveForward;
            jump = vanillaInput.jump;
            sneak = vanillaInput.sneak;
        }

        @Override
        public void updatePlayerMoveState() {
            vanillaInput.updatePlayerMoveState();
            moveStrafe = vanillaInput.moveStrafe;
            moveForward = vanillaInput.moveForward;
            jump = vanillaInput.jump;
            sneak = vanillaInput.sneak;
            if (module.applies()) {
                // EntityPlayerSP multiplies these by 0.2 later in this same
                // update. Five times the legitimate key input preserves the
                // normal walk vector without touching packet ordering.
                moveForward *= 5.0F;
                moveStrafe *= 5.0F;
            }
            // Exire Speed cancels the physical jump action before vanilla
            // processes it; its move event owns the hop instead.
            dev.vibe.movement.RewoundHooks.input(this);
        }
    }

    public ModeSetting getMode() { return mode; }
    public MultiSelectSetting getItems() { return items; }
}
