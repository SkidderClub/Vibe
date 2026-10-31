package dev.vibe.core.startup;

import java.util.*;
import net.minecraftforge.fml.common.ProgressManager;
import org.lwjgl.opengl.*;

/** Uses Forge's current GL owner; never acquires, shares or releases the display context. */
public final class GhostStartup {
    private static volatile boolean active;
    private static Thread mainThread;
    private static GhostLoadingRenderer renderer;
    private static long lastFrame;
    private static boolean failed;
    private GhostStartup() { }

    public static void begin() { mainThread = Thread.currentThread(); active = true; failed = false; }
    public static void finish() { active = false; release(); }

    /** Called inside Forge's splash loop, before its own semaphore/display update. */
    public static boolean renderForge(int clearMask) {
        if (failed) { GL11.glClear(clearMask); return false; }
        try { render(); return true; }
        catch (Exception | LinkageError e) { fail(e); GL11.glClear(clearMask); return false; }
    }

    /** Main-thread fallback for configurations where Forge's threaded splash is disabled. */
    public static boolean drawVanilla() {
        if (!active || failed || !current()) return false;
        try { render(); Display.update(); return true; }
        catch (Exception | LinkageError e) { fail(e); return false; }
    }

    public static void progressChanged() {
        if (!active || failed || Thread.currentThread() != mainThread || !current()) return;
        long now = System.nanoTime();
        if (now - lastFrame < 33000000L) return;
        lastFrame = now; drawVanilla();
    }

    private static boolean current() {
        try { return Display.isCreated() && Display.getDrawable().isCurrent(); }
        catch (Exception e) { return false; }
    }
    private static void render() {
        if (renderer == null) renderer = new GhostLoadingRenderer();
        // Vanilla loading may leave its temporary FBO bound; present on the window itself.
        boolean arb = GLContext.getCapabilities().GL_ARB_framebuffer_object;
        boolean ext = !arb && GLContext.getCapabilities().GL_EXT_framebuffer_object;
        int target = arb ? GL11.glGetInteger(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER_BINDING)
                : ext ? GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) : 0;
        try {
            if (arb) ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER, 0);
            else if (ext) EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
            renderer.draw(Display.getWidth(), Display.getHeight(), stages(), System.nanoTime() / 1000000L);
        } finally {
            if (arb) ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER, target);
            else if (ext) EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, target);
        }
    }
    private static void fail(Throwable error) {
        if (!failed) System.err.println("[Vibe] Custom splash unavailable; using Forge's screen: " + error);
        failed = true; release();
    }
    /** Invoked by Forge immediately before it relinquishes its current context. */
    public static void release() {
        if (renderer == null) return;
        try { if (current()) { renderer.close(); renderer = null; } }
        catch (Exception ignored) { renderer = null; }
    }
    public static List<Stage> stages() {
        List<Stage> result = new ArrayList<Stage>();
        Iterator<ProgressManager.ProgressBar> bars = ProgressManager.barIterator();
        while (bars.hasNext()) {
            ProgressManager.ProgressBar bar = bars.next();
            result.add(new Stage(bar.getTitle(), bar.getMessage(), bar.getStep(), bar.getSteps()));
        }
        return result;
    }
    public static final class Stage {
        public final String title, message;
        public final int step, steps;
        public Stage(String title, String message, int step, int steps) {
            this.title = clean(title); this.message = clean(message);
            this.steps = Math.max(0, steps); this.step = Math.max(0, Math.min(step, this.steps));
        }
        public float ratio() { return steps == 0 ? 0 : step / (float)steps; }
        private static String clean(String s) { return s == null ? "" : s.replaceAll("\u00a7.", "").replace('\n', ' '); }
    }
}
