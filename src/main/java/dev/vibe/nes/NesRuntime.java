package dev.vibe.nes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Runs the supplied ES5 NES core in the bundled Nashorn engine. The emulator
 * thread never touches OpenGL; it publishes completed RGB frames for the GUI
 * thread to upload into Minecraft's DynamicTexture safely.
 */
public final class NesRuntime {

    public static final int WIDTH = 256;
    public static final int HEIGHT = 240;
    private static final String[] SCRIPTS = {"utils.js", "tile.js", "mappers.js", "rom.js", "controller.js", "cpu.js", "ppu.js", "papu.js", "nes.js"};

    private final Object engineLock = new Object();
    private final FrameSink frameSink = new FrameSink();
    // UI input must never contend with the interpreter. The GUI simply
    // publishes this bitset; the emulation worker applies it before its next
    // frame while it already owns the JS engine.
    private final AtomicInteger requestedButtons = new AtomicInteger();
    private volatile boolean running;
    private volatile String status = "Choose a .nes ROM to start.";
    private volatile Throwable failure;
    private ScriptEngine engine;
    private Invocable invocable;
    private long lastUploadNanos;
    private long uploadIntervalNanos = 16_000_000L;
    /**
     * Forge's LaunchClassLoader can deliberately hide third-party packages
     * added inside a mod JAR. Keep a private loader fallback for the bundled
     * Nashorn/ASM classes in that environment.
     */
    private URLClassLoader engineLoader;
    private Thread worker;

    public synchronized boolean start(File rom, boolean pal, int presentationFps) {
        stop();
        if (rom == null || !rom.isFile()) {
            status = "ROM file is missing.";
            return false;
        }
        try {
            engine = createEngine();
            if (engine == null) throw new IllegalStateException("Nashorn runtime is unavailable");
            if (!(engine instanceof Invocable)) throw new IllegalStateException("Bundled JavaScript runtime cannot invoke functions");
            invocable = (Invocable) engine;
            synchronized (engineLock) {
                engine.put("frameSink", frameSink);
                engine.eval("function load(path) { }\n");
                for (String script : SCRIPTS) engine.eval(readResource("/assets/vibe/nes/emu/" + script));
                boolean nashorn = engine.getFactory().getEngineName().toLowerCase(java.util.Locale.ROOT).contains("nashorn");
                // Copying 61k JavaScript pixels is the expensive boundary
                // crossing. The core still emulates at 60 Hz; a 30 Hz
                // presentation simply transfers alternate completed frames.
                String every = Integer.toString(presentationFps <= 30 ? 2 : 1);
                String frameCallback = nashorn
                        ? "onFrame:function(frame){ if((__vibePresent++ % " + every + ")===0) frameSink.accept(Java.to(frame, 'int[]')); }"
                        : "onFrame:function(frame){ if((__vibePresent++ % " + every + ")===0) frameSink.accept(frame); }";
                engine.eval("var __vibePresent=0; var __vibeNes = new NES({emulateSound:false, preferredFrameRate:60, " + frameCallback + "});"
                        // Parse these calls once. Re-evaluating a source
                        // string for every frame was expensive, especially
                        // with Rhino as the compatible fallback engine.
                        + "function __vibeFrame(){ __vibeNes.frame(); }"
                        + "function __vibeButton(button, down){ if(down) __vibeNes.buttonDown(1, button); else __vibeNes.buttonUp(1, button); }");
                engine.put("vibeRomData", new String(readFile(rom), StandardCharsets.ISO_8859_1));
                engine.eval("__vibeNes.loadROM(vibeRomData);");
            }
            running = true;
            failure = null;
            lastUploadNanos = 0L;
            uploadIntervalNanos = presentationFps <= 30 ? 33_000_000L : 16_000_000L;
            // Emulation itself must run at the source frame-rate; presenting
            // every other completed frame controls GUI upload cost without
            // making gameplay progress at half speed.
            final long frameNanos = pal ? 20_000_000L : 16_666_667L;
            worker = new Thread(new Runnable() {
                @Override public void run() { runFrames(frameNanos); }
            }, "Vibe-NES");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY + 1);
            worker.start();
            status = "Running " + rom.getName() + (pal ? " • PAL" : " • NTSC");
            return true;
        } catch (Throwable error) {
            failure = error;
            status = "Could not load ROM: " + friendly(error);
            engine = null;
            return false;
        }
    }

    /** Compatibility entry point used by old callers and tests. */
    public synchronized boolean start(File rom, boolean pal) { return start(rom, pal, 60); }

    public synchronized void stop() {
        running = false;
        if (worker != null && worker != Thread.currentThread()) {
            try { worker.join(120L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        worker = null;
        engine = null;
        invocable = null;
        lastUploadNanos = 0L;
        if (engineLoader != null) {
            try { engineLoader.close(); } catch (Exception ignored) { }
            engineLoader = null;
        }
        frameSink.clear();
        requestedButtons.set(0);
    }

    public boolean isRunning() { return running; }
    public String getStatus() { return status; }
    public void showStatus(String message) { status = message == null ? "" : message; }
    public Throwable getFailure() { return failure; }

    public void setButton(int controllerButton, boolean down) {
        if (!running || controllerButton < 0 || controllerButton > 30) return;
        int bit = 1 << controllerButton;
        while (true) {
            int current = requestedButtons.get();
            int replacement = down ? current | bit : current & ~bit;
            if (requestedButtons.compareAndSet(current, replacement)) return;
        }
    }

    /** Called by the GUI thread immediately before drawing the NES texture. */
    public void upload(DynamicTexture texture) {
        // Uploading is throttled independently from emulation so the worker
        // always advances gameplay at the original console frame rate.
        long now = System.nanoTime();
        if (now - lastUploadNanos < uploadIntervalNanos || texture == null) return;
        int[] target = texture.getTextureData();
        if (!frameSink.copyTo(target)) return;
        for (int index = 0; index < WIDTH * HEIGHT; index++) target[index] = 0xFF000000 | (target[index] & 0x00FFFFFF);
        texture.updateDynamicTexture();
        lastUploadNanos = now;
    }

    private void runFrames(long frameNanos) {
        int appliedButtons = -1;
        long next = System.nanoTime();
        while (running) {
            try {
                synchronized (engineLock) {
                    if (invocable == null) throw new IllegalStateException("JavaScript runtime stopped");
                    int wantedButtons = requestedButtons.get();
                    if (wantedButtons != appliedButtons) {
                        int changed = wantedButtons ^ appliedButtons;
                        // On the first frame appliedButtons is -1, which
                        // explicitly releases every controller key before
                        // normal game input begins.
                        for (int button = 0; button < 8; button++) {
                            int bit = 1 << button;
                            if ((changed & bit) != 0) {
                                invocable.invokeFunction("__vibeButton", Integer.valueOf(button),
                                        Boolean.valueOf((wantedButtons & bit) != 0));
                            }
                        }
                        appliedButtons = wantedButtons;
                    }
                    invocable.invokeFunction("__vibeFrame");
                }
            } catch (Throwable error) {
                failure = error;
                status = "Emulation stopped: " + friendly(error);
                running = false;
                break;
            }
            next += frameNanos;
            long wait = next - System.nanoTime();
            if (wait > 0L) java.util.concurrent.locks.LockSupport.parkNanos(wait);
            else if (wait < -frameNanos * 3L) next = System.nanoTime();
            if (Thread.interrupted()) { Thread.currentThread().interrupt(); break; }
        }
    }

    private ScriptEngine createEngine() throws Exception {
        // In a normal development or Forge classpath this is all that is
        // needed. Supplying the loader explicitly matters on JDK 21 because
        // ScriptEngineManager otherwise consults only the application loader.
        ClassLoader modLoader = NesRuntime.class.getClassLoader();
        ScriptEngine created = createEngine(modLoader);
        if (created != null) return created;

        // Forge's LaunchClassLoader may decline classes which live in the
        // mod archive but are outside a mod namespace (such as Nashorn and
        // Rhino).  Give the bundled archive a normal JDK parent instead of
        // LaunchClassLoader: the engine only needs JDK APIs and can still
        // invoke the public FrameSink object supplied through script bindings.
        URL archive = NesRuntime.class.getProtectionDomain().getCodeSource() == null ? null
                : NesRuntime.class.getProtectionDomain().getCodeSource().getLocation();
        if (archive != null) {
            URLClassLoader fallback = new URLClassLoader(new URL[] {archive}, ClassLoader.getSystemClassLoader());
            created = createEngine(fallback);
            if (created != null) {
                engineLoader = fallback;
                return created;
            }
            try { fallback.close(); } catch (Exception ignored) { }
        }
        return null;
    }

    private ScriptEngine createEngine(ClassLoader loader) {
        String[] factories = {
                "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory",
                "org.mozilla.javascript.engine.RhinoScriptEngineFactory"
        };
        for (String name : factories) {
            try {
                Class<?> type = Class.forName(name, true, loader);
                Object factory = type.newInstance();
                Object created = type.getMethod("getScriptEngine").invoke(factory);
                if (created instanceof ScriptEngine) return (ScriptEngine) created;
            } catch (Throwable ignored) { }
        }
        try {
            ScriptEngineManager manager = new ScriptEngineManager(loader);
            ScriptEngine engine = manager.getEngineByName("nashorn");
            return engine != null ? engine : manager.getEngineByName("rhino");
        } catch (Throwable ignored) { return null; }
    }

    private String readResource(String path) throws Exception {
        InputStream stream = NesRuntime.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("Missing packaged NES core: " + path);
        try { return new String(readAll(stream), StandardCharsets.UTF_8); } finally { stream.close(); }
    }

    private byte[] readFile(File file) throws Exception {
        InputStream stream = new FileInputStream(file);
        try { return readAll(stream); } finally { stream.close(); }
    }

    private byte[] readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private String friendly(Throwable error) {
        String text = error.getMessage();
        return text == null || text.trim().isEmpty() ? error.getClass().getSimpleName() : text;
    }

    /** Public so Nashorn can call it without reflective accessibility issues. */
    public static final class FrameSink {
        private final int[] pending = new int[WIDTH * HEIGHT];
        private boolean ready;

        /** Nashorn's Java.to() selects this fast primitive overload. */
        public synchronized void accept(int[] pixels) {
            if (pixels == null || pixels.length < WIDTH * HEIGHT) return;
            System.arraycopy(pixels, 0, pending, 0, pending.length);
            ready = true;
        }

        public void accept(Object pixels) {
            if (pixels instanceof int[]) {
                accept((int[]) pixels);
                return;
            }
            // Nashorn can still expose a ScriptObjectMirror when a runtime
            // restricts Java.to. Its getSlot contract is the same indexed
            // array shape as Rhino's Scriptable array below.
            try {
                java.lang.reflect.Method slot = pixels == null ? null : pixels.getClass().getMethod("getSlot", Integer.TYPE);
                if (slot != null) {
                    int[] data = new int[WIDTH * HEIGHT];
                    for (int index = 0; index < data.length; index++) {
                        Object value = slot.invoke(pixels, Integer.valueOf(index));
                        data[index] = value instanceof Number ? ((Number) value).intValue() : 0;
                    }
                    accept(data);
                    return;
                }
            } catch (Throwable ignored) { }
            // Rhino supplies a Scriptable array rather than a primitive Java
            // int[] when invoking a public method through JSR-223. Use
            // reflection here: when Forge filters third-party packages on the
            // parent loader, the engine's private child loader still owns the
            // Rhino classes and this callback remains loadable.
            try {
                ClassLoader loader = pixels == null ? null : pixels.getClass().getClassLoader();
                if (loader == null) return;
                Class<?> scriptable = Class.forName("org.mozilla.javascript.Scriptable", true, loader);
                if (!scriptable.isInstance(pixels)) return;
                Class<?> scriptableObject = Class.forName("org.mozilla.javascript.ScriptableObject", true, loader);
                java.lang.reflect.Method named = scriptableObject.getMethod("getProperty", scriptable, String.class);
                java.lang.reflect.Method indexed = scriptableObject.getMethod("getProperty", scriptable, Integer.TYPE);
                Object lengthValue = named.invoke(null, pixels, "length");
                int length = lengthValue instanceof Number ? ((Number) lengthValue).intValue() : 0;
                if (length < WIDTH * HEIGHT) return;
                int[] data = new int[WIDTH * HEIGHT];
                for (int index = 0; index < data.length; index++) {
                    Object value = indexed.invoke(null, pixels, Integer.valueOf(index));
                    data[index] = value instanceof Number ? ((Number) value).intValue() : 0;
                }
                accept(data);
            } catch (Throwable ignored) {
                // A malformed frame must not terminate the GUI thread.
            }
        }
        private synchronized boolean copyTo(int[] target) {
            if (!ready || target == null || target.length < pending.length) return false;
            System.arraycopy(pending, 0, target, 0, pending.length);
            ready = false;
            return true;
        }

        private synchronized void clear() { ready = false; }
    }
}
