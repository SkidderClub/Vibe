package dev.vibe.ui;

import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.render.InventoryHighlightRenderer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryHighlightTest {
    @Test public void followsVanillaPotionLayoutAtEveryGuiSize() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            Map<Integer, PotionEffect> potions = new HashMap<Integer, PotionEffect>();
            PathTestFixture.set(EntityLivingBase.class, f.player, "activePotionsMap", potions);
            PotionInventory gui = new PotionInventory();
            for (int[] size : new int[][] {{960,540}, {480,270}, {320,240}}) {
                gui.layout(f.mc, size[0], size[1]);
                int normal = InventoryHighlightRenderer.left(gui);
                assertEquals((size[0]-176)/2, normal);
                assertEquals((size[1]-166)/2, InventoryHighlightRenderer.top(gui));
                potions.put(Potion.moveSpeed.id, new PotionEffect(Potion.moveSpeed.id, 200));
                gui.refresh();
                assertEquals(normal + 60, InventoryHighlightRenderer.left(gui));
                potions.clear(); gui.refresh();
                assertEquals(normal, InventoryHighlightRenderer.left(gui));
            }
        }
    }

    public static final class PotionInventory extends InventoryEffectRenderer {
        public PotionInventory() {
            super(new Container() { @Override public boolean canInteractWith(EntityPlayer player) { return true; } });
        }
        public void layout(Minecraft client, int w, int h) {
            mc = client; width=w; height=h; xSize=176; ySize=166; guiTop=(h-ySize)/2;
            updateActivePotionEffects();
        }
        public void refresh() { updateActivePotionEffects(); }
        public void position(int left, int top) { guiLeft=left; guiTop=top; }
        @Override protected void drawGuiContainerBackgroundLayer(float ticks, int x, int y) { }
    }
}
