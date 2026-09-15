package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.ParticlesModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Lightweight force-field particles for Vibe GUI overlays. */
public final class ParticlesRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<Particle>();
    private long lastFrame;

    public void draw(GuiScreen screen) {
        ParticlesModule module = Vibe.getInstance().getModuleManager().getModule(ParticlesModule.class);
        if (module == null || !module.isEnabled() || screen == null || !shouldRender(module, screen)) {
            particles.clear();
            lastFrame = 0L;
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        int wanted = module.getCount().getInt();
        while (particles.size() < wanted) particles.add(newParticle(width, height, module));
        while (particles.size() > wanted) particles.remove(particles.size() - 1);
        long now = System.currentTimeMillis();
        float delta = lastFrame == 0L ? 1.0F : Math.min(2.0F, (now - lastFrame) / 16.666F);
        lastFrame = now;
        float mouseX = Mouse.getX() * width / (float) minecraft.displayWidth;
        float mouseY = height - Mouse.getY() * height / (float) minecraft.displayHeight - 1.0F;
        boolean attract = Mouse.isButtonDown(0);
        boolean repel = Mouse.isButtonDown(1);
        float scale = module.getSpeed().getFloat() * delta;
        GuiRenderState.prepare(false);
        try {
        for (int index = 0; index < particles.size(); index++) {
            Particle particle = particles.get(index);
            if (!module.getModes().isSelected(particle.mode)) {
                particle.mode = selectMode(module);
            }
            float dx = mouseX - particle.x;
            float dy = mouseY - particle.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if ((attract || repel) && distance > 2.0F && distance < 185.0F) {
                float force = (1.0F - distance / 185.0F) * module.getInteraction().getFloat() * 0.34F * delta;
                float direction = attract ? 1.0F : -1.0F;
                particle.vx += dx / distance * force * direction;
                particle.vy += dy / distance * force * direction;
            }
            for (int other = index + 1; other < particles.size(); other++) {
                Particle neighbour = particles.get(other);
                float ox = particle.x - neighbour.x;
                float oy = particle.y - neighbour.y;
                float separation = ox * ox + oy * oy;
                if (separation > 0.01F && separation < 900.0F) {
                    float force = (1.0F - separation / 900.0F) * 0.035F * delta;
                    float inverse = (float) (1.0D / Math.sqrt(separation));
                    particle.vx += ox * inverse * force;
                    particle.vy += oy * inverse * force;
                    neighbour.vx -= ox * inverse * force;
                    neighbour.vy -= oy * inverse * force;
                }
            }
            particle.x += particle.vx * scale;
            particle.y += particle.vy * scale;
            particle.vx *= 0.985F;
            particle.vy *= 0.985F;
            float phase = (particle.seed + (now % 7000L) / 7000.0F) % 1.0F;
            int color = RenderUtils.blend(module.getPrimary().getArgb(), module.getSecondary().getArgb(), phase);
            int radius = 5;
            // Keep the entire particle visible.  Reflection is applied after
            // integration, so mouse force cannot slowly leak particles out of
            // the frame at high GUI scales.
            if (particle.x < radius) {
                particle.x = radius;
                particle.vx = Math.abs(particle.vx);
            } else if (particle.x > width - radius - 1) {
                particle.x = width - radius - 1;
                particle.vx = -Math.abs(particle.vx);
            }
            if (particle.y < radius) {
                particle.y = radius;
                particle.vy = Math.abs(particle.vy);
            } else if (particle.y > height - radius - 1) {
                particle.y = height - radius - 1;
                particle.vy = -Math.abs(particle.vy);
            }
            drawParticle(Math.round(particle.x), Math.round(particle.y), particle.mode, color);
        }
        } finally {
            GuiRenderState.prepare(false);
        }
    }

    private Particle newParticle(int width, int height, ParticlesModule module) {
        float angle = random.nextFloat() * (float) Math.PI * 2.0F;
        float velocity = 0.25F + random.nextFloat() * 0.55F;
        return new Particle(random.nextFloat() * width, random.nextFloat() * height,
                (float) Math.cos(angle) * velocity, (float) Math.sin(angle) * velocity, random.nextFloat(), selectMode(module));
    }

    /** Assign one selected look once per particle; mixed modes stay stable while moving. */
    private String selectMode(ParticlesModule module) {
        List<String> selected = new ArrayList<String>();
        for (String mode : module.getModes().getOptions()) if (module.getModes().isSelected(mode)) selected.add(mode);
        return selected.isEmpty() ? "Dots" : selected.get(random.nextInt(selected.size()));
    }

    private void drawParticle(int x, int y, String mode, int color) {
        if ("Dots".equals(mode)) {
            // A dot is intentionally a single physical GUI pixel. This keeps
            // the high-count ambient mode inexpensive and visually crisp.
            Gui.drawRect(x, y, x + 1, y + 1, color);
            return;
        }
        // Nine-pixel glyphs keep every selection at a common visual scale,
        // but give the curves and diagonals enough detail to read as actual
        // stars, hearts and flakes rather than blocky symbols. '.' is a soft
        // edge, '+' a mid-tone, '#' the bright core.
        String[] mask;
        if ("Stars".equals(mode)) mask = new String[] {"    .    ", " .  +  . ", "  .+#+.  ", "+++###+++", " .#####. ", "+++###+++", "  .+#+.  ", " .  +  . ", "    .    "};
        else if ("Balls".equals(mode)) mask = new String[] {"  .....  ", " .+++++. ", ".++###++.", ".+#####.+", ".+#####.+", ".+#####.+", ".++###++.", " .+++++. ", "  .....  "};
        else if ("Hearts".equals(mode)) mask = new String[] {" .+. .+. ", ".+##+##+.", "+#######+", "+#######+", " .#####. ", "  .###.  ", "   .#.   ", "    .    ", "         "};
        else if ("Cross".equals(mode)) mask = new String[] {"   .#.   ", "   +#+   ", "   +#+   ", ".++###++.", "+++###+++", ".++###++.", "   +#+   ", "   +#+   ", "   .#.   "};
        else if ("Snowflake".equals(mode)) mask = new String[] {".   #   .", " .  #  . ", "  . # .  ", "+++###+++", " .+###+. ", "+++###+++", "  . # .  ", " .  #  . ", ".   #   ."};
        else mask = new String[] {"#"};
        for (int row = 0; row < mask.length; row++) {
            String line = mask[row];
            for (int column = 0; column < line.length(); column++) {
                char pixel = line.charAt(column);
                if (pixel == ' ') continue;
                int alpha = pixel == '#' ? 255 : pixel == '+' ? 190 : 86;
                int sourceAlpha = (color >>> 24) & 255;
                int composedAlpha = Math.round(alpha * sourceAlpha / 255.0F);
                Gui.drawRect(x - 4 + column, y - 4 + row, x - 3 + column, y - 3 + row, RenderUtils.alpha(color, composedAlpha));
            }
        }
    }

    private boolean shouldRender(ParticlesModule module, GuiScreen screen) {
        if (screen instanceof dev.vibe.ui.VibeClickGui) return module.getGuiTargets().isSelected("ClickGUI");
        if (screen instanceof net.minecraft.client.gui.inventory.GuiInventory
                || screen instanceof net.minecraft.client.gui.inventory.GuiContainerCreative) return module.getGuiTargets().isSelected("Inventory");
        if (screen instanceof InventoryEditorGui) return module.getGuiTargets().isSelected("Inventory Editor");
        if (screen instanceof FriendEditorGui) return module.getGuiTargets().isSelected("Friend Editor");
        if (screen instanceof ConfigEditorGui) return module.getGuiTargets().isSelected("Config Editor");
        if (screen instanceof KeybindEditorGui) return module.getGuiTargets().isSelected("Keybind Editor");
        if (screen instanceof EspEditorGui) return module.getGuiTargets().isSelected("ESP Editor");
        if (screen instanceof CosmeticsEditorGui || screen instanceof CosmeticPresetEditGui) return module.getGuiTargets().isSelected("Cosmetics Editor");
        if (screen instanceof NesEmulatorGui) return module.getGuiTargets().isSelected("NES Emulator");
        if (screen instanceof Gta7Gui) return module.getGuiTargets().isSelected("GTA7");
        if (screen instanceof MemeGameGui) return module.getGuiTargets().isSelected("Meme Games");
        if (screen instanceof SlotsGui) return module.getGuiTargets().isSelected("Meme Games");
        if (screen instanceof net.minecraft.client.gui.inventory.GuiChest) return module.getGuiTargets().isSelected("Chest");
        if (screen instanceof net.minecraft.client.gui.GuiIngameMenu) return module.getGuiTargets().isSelected("Escape");
        String simple = screen.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (simple.contains("repair") || simple.contains("anvil")) return module.getGuiTargets().isSelected("Anvil");
        if (screen instanceof net.minecraft.client.gui.inventory.GuiDispenser) {
            return module.getGuiTargets().isSelected("Dropper") || module.getGuiTargets().isSelected("Thrower");
        }
        return screen instanceof net.minecraft.client.gui.inventory.GuiContainer && module.getGuiTargets().isSelected("Other");
    }

    private static final class Particle {
        private float x;
        private float y;
        private float vx;
        private float vy;
        private final float seed;
        private String mode;
        private Particle(float x, float y, float vx, float vy, float seed, String mode) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.seed = seed; this.mode = mode;
        }
    }
}
