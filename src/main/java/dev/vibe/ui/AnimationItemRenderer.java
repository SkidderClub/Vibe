package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.AnimationsModule;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import org.lwjgl.opengl.GL11;

/**
 * Wraps Minecraft's existing ItemRenderer, leaving its item/controller logic
 * untouched while adding a transform immediately before vanilla draws a sword
 * block. The wrapper delegates all stateful calls to the original renderer.
 */
public final class AnimationItemRenderer extends ItemRenderer {

    private static boolean installed;
    private final ItemRenderer vanilla;

    private AnimationItemRenderer(Minecraft minecraft, ItemRenderer vanilla) {
        super(minecraft);
        this.vanilla = vanilla;
    }

    public static void install(Minecraft minecraft) {
        if (installed || minecraft == null || minecraft.entityRenderer == null) {
            return;
        }
        try {
            ItemRenderer current = minecraft.getItemRenderer();
            if (current instanceof AnimationItemRenderer) {
                installed = true;
                return;
            }
            AnimationItemRenderer wrapped = new AnimationItemRenderer(minecraft, current);
            setItemRendererField(minecraft, wrapped);
            setItemRendererField(minecraft.entityRenderer, wrapped);
            installed = true;
        } catch (Exception ignored) {
            // The client still uses the normal renderer if another environment
            // exposes different field mappings.
        }
    }

    @Override
    public void renderItemInFirstPerson(float partialTicks) {
        EntityPlayerSP visualPlayer = AuraBlockVisual.begin();
        try {
            renderWithAnimation(partialTicks);
        } finally {
            AuraBlockVisual.end(visualPlayer);
        }
    }

    private void renderWithAnimation(float partialTicks) {
        AnimationsModule module = Vibe.getInstance() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(AnimationsModule.class);
        if (module == null || !module.isEnabled() || module.getBlockingStyle().is("Vanilla") || !isSelectedUse(module)) {
            vanilla.renderItemInFirstPerson(partialTicks);
            return;
        }
        GL11.glPushMatrix();
        try {
            if (module != null && module.isEnabled() && !module.getBlockingStyle().is("Vanilla")) {
                transform(module, partialTicks);
            }
            vanilla.renderItemInFirstPerson(partialTicks);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @Override
    public void renderOverlays(float partialTicks) {
        vanilla.renderOverlays(partialTicks);
    }

    @Override
    public void updateEquippedItem() {
        vanilla.updateEquippedItem();
    }

    @Override
    public void resetEquippedProgress() {
        vanilla.resetEquippedProgress();
    }

    @Override
    public void resetEquippedProgress2() {
        vanilla.resetEquippedProgress2();
    }

    private boolean isSelectedUse(AnimationsModule module) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || !player.isUsingItem() || player.getItemInUseCount() <= 0) {
            return false;
        }
        ItemStack used = player.getItemInUse();
        if (used == null) return false;
        if (used.getItem() instanceof ItemSword) return module.getItemTypes().isSelected("Swords");
        if (used.getItem() instanceof ItemBow) return module.getItemTypes().isSelected("Bow");
        if (used.getItem() instanceof ItemFood) return module.getItemTypes().isSelected("Food") || module.getItemTypes().isSelected("Consumables");
        if (used.getItem() instanceof ItemPotion) return module.getItemTypes().isSelected("Potions") || module.getItemTypes().isSelected("Consumables");
        return module.getItemTypes().isSelected("Other");
    }

    private void transform(AnimationsModule module, float partialTicks) {
        float time = (System.currentTimeMillis() % 12000L) / 1000.0F * module.getAnimationSpeed().getFloat();
        float amount = module.getRotation().getFloat();
        GL11.glTranslatef(module.getOffsetX().getFloat(), module.getOffsetY().getFloat(), module.getOffsetZ().getFloat());
        if (module.getBlockingStyle().is("Slide")) {
            GL11.glTranslatef(-0.24F, 0.10F + (float) Math.sin(time * 4.0F) * 0.035F, -0.14F);
            GL11.glRotatef(-amount, 0.0F, 1.0F, 0.0F);
        } else if (module.getBlockingStyle().is("Reverse")) {
            GL11.glTranslatef(0.20F, -0.12F, 0.04F);
            GL11.glRotatef(amount * 1.45F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(amount * 0.50F, 0.0F, 1.0F, 0.0F);
        } else if (module.getBlockingStyle().is("Reverse Spin")) {
            GL11.glTranslatef(-0.04F, 0.04F, -0.18F);
            GL11.glRotatef((time * 190.0F) % 360.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(amount, 0.0F, 1.0F, 0.0F);
        } else if (module.getBlockingStyle().is("Spin")) {
            GL11.glTranslatef(-0.04F, 0.04F, -0.18F);
            GL11.glRotatef((-time * 190.0F) % 360.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(amount, 0.0F, 1.0F, 0.0F);
        } else if (module.getBlockingStyle().is("Orbit")) {
            // Shift the sword toward the screen centre before rotation; this
            // makes its tip orbit around the crosshair rather than its hilt.
            GL11.glTranslatef(-0.38F, 0.31F, -0.58F);
            GL11.glRotatef((time * 145.0F) % 360.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(amount * 0.65F, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(0.38F, -0.31F, 0.58F);
        }
    }

    private static void setItemRendererField(Object owner, ItemRenderer renderer) throws Exception {
        Field selected = null;
        for (Field field : owner.getClass().getDeclaredFields()) {
            if (ItemRenderer.class.isAssignableFrom(field.getType())) {
                selected = field;
                break;
            }
        }
        if (selected == null) {
            throw new NoSuchFieldException("ItemRenderer field");
        }
        selected.setAccessible(true);
        selected.set(owner, renderer);
    }
}
