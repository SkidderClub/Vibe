package dev.vibe.script;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.MovementInput;
import keystrokesmod.script.model.PlayerState;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.script.packet.clientbound.SPacket;
import keystrokesmod.script.packet.serverbound.CPacket;
import keystrokesmod.script.packet.serverbound.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;

/**
 * Vibe's local Java runtime with Raven BS compatible source names. Scripts
 * stay normal .java files and are compiled only on explicit reload/create,
 * never on the render thread.
 */
public final class ScriptRuntime {
    private static final char[] INVALID_NAME = {'\\', '/', ':', '*', '?', '"', '<', '>', '|'};
    /**
     * Generated Raven-compatible classes are initialised before their first
     * callback.  Keep their settings owner here rather than making that class
     * initialisation depend on Vibe's Forge singleton being fully available.
     */
    private static final Map<String, ScriptModule> REGISTERED_MODULES = new ConcurrentHashMap<String, ScriptModule>();
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final File directory;
    private final File legacyDirectory;
    private final File compiledDirectory;
    private final Map<String, Program> programs = new LinkedHashMap<String, Program>();
    private final AtomicInteger classCounter = new AtomicInteger();
    private final List<String> diagnostics = new ArrayList<String>();

    public ScriptRuntime(File minecraftDirectory) {
        File base = new File(minecraftDirectory, "vibe");
        directory = new File(base, "scripts");
        // Raven BS itself uses this location.  Scanning it read-only means an
        // existing Raven installation can be moved to Vibe without copying
        // or rewriting every source file first.
        legacyDirectory = new File(new File(minecraftDirectory, "keystrokes"), "scripts");
        compiledDirectory = new File(directory, ".compiled");
        if (!directory.exists()) directory.mkdirs();
    }

    public File getDirectory() { return directory; }
    public synchronized List<ScriptInfo> getScripts() {
        List<ScriptInfo> out = new ArrayList<ScriptInfo>();
        for (Program program : programs.values()) out.add(program.info());
        return out;
    }
    public synchronized List<String> getDiagnostics() { return new ArrayList<String>(diagnostics); }
    public synchronized ScriptModule getModule(String name) {
        Program program = programs.get(normalize(name));
        return program == null ? null : program.module;
    }

    /** Internal bridge used by ScriptDefaults.modules while a script loads. */
    public static ScriptModule getRegisteredModule(String name) {
        return REGISTERED_MODULES.get(normalizeScriptName(name));
    }

    public synchronized void reload() {
        for (Program program : programs.values()) {
            if (program.module.isEnabled()) program.module.setEnabled(false);
            Vibe.getInstance().getModuleManager().unregisterDynamic(program.module);
            REGISTERED_MODULES.remove(normalize(program.name));
            close(program.loader);
        }
        programs.clear();
        diagnostics.clear();
        if (!directory.exists() && !directory.mkdirs()) {
            diagnostics.add("Could not create " + directory.getAbsolutePath());
            return;
        }
        clearCompiled();
        // Vibe's directory takes priority when both folders contain the same
        // file name. This gives users a safe migration path from Raven.
        loadDirectory(legacyDirectory);
        loadDirectory(directory);
    }

    private void loadDirectory(File sourceDirectory) {
        if (sourceDirectory == null || !sourceDirectory.isDirectory()) return;
        File[] files = sourceDirectory.listFiles();
        if (files == null) return;
        Arrays.sort(files, new Comparator<File>() { @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); } });
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".java") || file.getName().startsWith("_")) continue;
            String key = normalize(file.getName().substring(0, file.getName().length() - 5));
            if (programs.containsKey(key)) {
                if (sourceDirectory.equals(directory)) {
                    Program old = programs.remove(key);
                    if (old != null) {
                        if (old.module.isEnabled()) old.module.setEnabled(false);
                        Vibe.getInstance().getModuleManager().unregisterDynamic(old.module);
                        REGISTERED_MODULES.remove(key);
                        close(old.loader);
                    }
                } else {
                    diagnostic("Duplicate legacy script ignored: " + file.getName());
                    continue;
                }
            }
            load(file);
        }
    }

    public synchronized String create(String requestedName) {
        String name = validate(requestedName, null);
        if (name == null) return null;
        File file = new File(directory, name + ".java");
        try {
            Files.write(file.toPath(), defaultTemplate().getBytes(StandardCharsets.UTF_8));
            reload();
            return name;
        } catch (IOException exception) {
            diagnostic("Could not create " + name + ": " + exception.getMessage());
            return null;
        }
    }

    public synchronized boolean rename(String oldName, String requestedName) {
        Program program = programs.get(normalize(oldName));
        if (program == null) return false;
        String name = validate(requestedName, program.name);
        if (name == null) return false;
        try {
            Files.move(program.file.toPath(), new File(directory, name + ".java").toPath(), StandardCopyOption.REPLACE_EXISTING);
            reload();
            return true;
        } catch (IOException exception) { diagnostic("Could not rename script: " + exception.getMessage()); return false; }
    }

    public synchronized boolean delete(String name) {
        Program program = programs.get(normalize(name));
        File file = program == null ? new File(directory, name + ".java") : program.file;
        if (!file.isFile() || !file.delete()) return false;
        reload();
        return true;
    }

    public boolean openDirectory() {
        try {
            if (!directory.exists()) directory.mkdirs();
            if (Desktop.isDesktopSupported()) { Desktop.getDesktop().open(directory); return true; }
        } catch (Exception ignored) { }
        return false;
    }

    private void load(File file) {
        String name = file.getName().substring(0, file.getName().length() - 5);
        String key = normalize(name);
        if (programs.containsKey(key)) { diagnostic("Duplicate script name: " + name); return; }
        ScriptModule module = new ScriptModule(this, name);
        Program program = new Program(name, file, module);
        // Bind this before loading the generated class.  Its static `modules`
        // field can then safely register settings during onLoad.
        programs.put(key, program);
        REGISTERED_MODULES.put(key, module);
        Vibe.getInstance().getModuleManager().registerDynamic(module);
        try {
            String source = resolveLoads(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), file);
            compile(program, source);
            if (program.instance == null) throw new IllegalStateException("No script class was created");
            invoke(name, "onLoad");
        } catch (Exception exception) {
            programs.remove(key);
            REGISTERED_MODULES.remove(key);
            Vibe.getInstance().getModuleManager().unregisterDynamic(module);
            close(program.loader);
            diagnostic(describeFailure(program, "load", exception));
        }
    }

    private void compile(Program program, String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (!compiledDirectory.exists() && !compiledDirectory.mkdirs()) throw new IOException("Could not create compiler cache");
        program.className = "vibe_script_" + cleanClassName(program.name) + "_" + classCounter.incrementAndGet();
        String code = header(program.className, program.name) + source + "\n}";
        if (compiler == null) {
            compileWithJanino(program, code);
            return;
        }
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager manager = compiler.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8);
        try {
            List<String> options = Arrays.asList("-d", compiledDirectory.getAbsolutePath(), "-classpath", classPath(), "-source", "8", "-target", "8", "-Xlint:-options");
            Boolean result = compiler.getTask(null, manager, collector, options, null,
                    Collections.<JavaFileObject>singletonList(new JavaSource(program.className, code))).call();
            if (!Boolean.TRUE.equals(result)) {
                StringBuilder message = new StringBuilder("Compilation failed");
                for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                    message.append(" [").append(diagnostic.getLineNumber() - 5).append(": ").append(diagnostic.getMessage(Locale.ROOT)).append(']');
                }
                throw new IllegalStateException(message.toString());
            }
        } finally { manager.close(); }
        URLClassLoader loader = new URLClassLoader(new URL[]{compiledDirectory.toURI().toURL()}, Vibe.class.getClassLoader());
        program.loader = loader;
        program.type = Class.forName(program.className, true, loader);
        program.instance = program.type.newInstance();
    }

    private void compileWithJanino(Program program, String source) throws Exception {
        org.codehaus.janino.SimpleCompiler compiler = new org.codehaus.janino.SimpleCompiler();
        // Forge's LaunchClassLoader throws an NPE for a few optional classes
        // while Janino walks method signatures.  Raven used a tolerant parent
        // loader; retain that behaviour so one absent optional class cannot
        // abort the whole script load.
        compiler.setParentClassLoader(new TolerantClassLoader(Vibe.class.getClassLoader()));
        try {
            compiler.cook(source);
        } catch (Exception firstFailure) {
            // Raven's production build uses javac. Janino's 1.8 resolver can
            // erase a generic Map/List expression in otherwise valid Raven
            // scripts (notably inventory scripts), so retry with casts only at
            // those erased call sites. No API names or script logic change.
            org.codehaus.janino.SimpleCompiler retry = new org.codehaus.janino.SimpleCompiler();
            retry.setParentClassLoader(new TolerantClassLoader(Vibe.class.getClassLoader()));
            retry.cook(janinoCompatibilitySource(source));
            compiler = retry;
        }
        program.loader = compiler.getClassLoader();
        program.type = Class.forName(program.className, true, program.loader);
        program.instance = program.type.newInstance();
    }

    private static String janinoCompatibilitySource(String source) {
        String result = source;
        result = result.replaceAll("(?m)(ItemStack\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*)([A-Za-z_][A-Za-z0-9_]*)\\.get\\(([^;]+)\\);", "$1(ItemStack) $2.get($3);");
        result = result.replaceAll("(?m)(((String|Integer|Long|Double|Float|Boolean|Vec3|ItemStack|Map(?:<[^;=]+>)?|List(?:<[^;=]+>)?|Set(?:<[^;=]+>)?|Object\\[\\]|String\\[\\]))\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*)([A-Za-z_][A-Za-z0-9_]*)\\.(get|getOrDefault)\\(([^;]+)\\);", "$1($2) $4.$5($6);");
        result = result.replaceAll("(?m)(Map<String,\\s*Object>\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*)([A-Za-z_][A-Za-z0-9_]*)\\.(get|remove)\\(([^;]+)\\);", "$1(Map<String,Object>) $2.$3($4);");
        result = result.replaceAll("([A-Za-z_][A-Za-z0-9_]*\\.get\\([^;\\n]+\\))\\.get\\(", "((Map)$1).get(");
        // Janino 3.x also erases generic enhanced-for variables when the
        // script is compiled against the remapped 1.8.9 classes.  Raven's
        // javac compiler keeps the declared Map type, so preserve that
        // source-level contract with an explicit cast in the retry only.
        result = result.replaceAll("for\\s*\\(\\s*Map<String,\\s*Object>\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s*\\{", "for (Object __ravenEntry : $2) { Map<String,Object> $1 = (Map<String,Object>) __ravenEntry;");
        result = result.replace("for (Map<String, Object> entry : packets) {", "for (Object __ravenEntry : packets) { Map<String,Object> entry = (Map<String,Object>) __ravenEntry;");
        return result;
    }

    /**
     * Raven supports top-level `load - "https://..."` lines. Preserve that
     * opt-in behavior during a manual script reload; a timeout and size cap
     * prevent one unavailable URL from freezing the client.
     */
    private String resolveLoads(String source, File origin) throws IOException {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^\\s*load\\s*-\\s*\\\"([^\\\"]+)\\\"\\s*$")
                .matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String target = matcher.group(1).trim();
            String replacement;
            try {
                replacement = readLoadTarget(target, origin);
            } catch (IOException exception) {
                throw new IOException("Could not load " + target + ": " + exception.getMessage(), exception);
            }
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String readLoadTarget(String target, File origin) throws IOException {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            java.net.URLConnection connection = new URL(target).openConnection();
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(3500);
            StringBuilder text = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            try {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (text.length() + count > 1024 * 1024) throw new IOException("loaded source exceeds 1 MiB");
                    text.append(buffer, 0, count);
                }
            } finally { reader.close(); }
            return text.toString();
        }
        File local = new File(target);
        if (!local.isAbsolute()) local = new File(origin.getParentFile(), target);
        if (!local.isFile()) throw new IOException("local source not found");
        if (local.length() > 1024L * 1024L) throw new IOException("loaded source exceeds 1 MiB");
        return new String(Files.readAllBytes(local.toPath()), StandardCharsets.UTF_8);
    }

    private String header(String className, String name) {
        return "import java.awt.Color;\nimport java.util.*;\nimport java.util.concurrent.*;\n"
                + "import java.util.concurrent.atomic.*;\nimport java.util.regex.*;\n"
                + "import keystrokesmod.script.model.*;\n"
                + "import keystrokesmod.script.packet.clientbound.*;\n"
                + "import keystrokesmod.script.packet.serverbound.*;\n"
                + "public class " + className + " extends keystrokesmod.script.ScriptDefaults {\n"
                + "public static final keystrokesmod.script.ScriptDefaults.modules modules = new keystrokesmod.script.ScriptDefaults.modules(\"" + escape(name) + "\");\n"
                + "public static final String scriptName = \"" + escape(name) + "\";\n";
    }

    public synchronized void invoke(String name, String method, Object... args) { Program program = programs.get(normalize(name)); if (program != null) invoke(program, method, args); }
    public synchronized void invokeAll(String method, Object... args) { for (Program program : new ArrayList<Program>(programs.values())) if (program.module.isEnabled()) invoke(program, method, args); }
    public synchronized boolean invokeBooleanAll(String method, Object... args) {
        for (Program program : new ArrayList<Program>(programs.values())) if (program.module.isEnabled()) {
            Object value = invoke(program, method, args);
            if (value instanceof Boolean && !((Boolean) value).booleanValue()) return false;
        }
        return true;
    }
    public synchronized Float[] rotations() {
        for (Program program : new ArrayList<Program>(programs.values())) if (program.module.isEnabled()) {
            Object value = invoke(program, "getRotations");
            if (value instanceof Float[]) return (Float[]) value;
        }
        return null;
    }

    private Object invoke(Program program, String methodName, Object... args) {
        if (program.instance == null) return null;
        for (Method method : program.type.getDeclaredMethods()) {
            if (!method.getName().equalsIgnoreCase(methodName) || method.getParameterTypes().length != args.length) continue;
            try { method.setAccessible(true); return method.invoke(program.instance, args); }
            catch (InvocationTargetException exception) { diagnostic(describeFailure(program, methodName, exception)); return null; }
            catch (Exception exception) { diagnostic(describeFailure(program, methodName, exception)); return null; }
        }
        return null;
    }

    public void tickStart() {
        invokeAll("onPreUpdate");
        if (minecraft.thePlayer == null) return;
        MovementInput input = new MovementInput(minecraft.thePlayer.movementInput.moveForward, minecraft.thePlayer.movementInput.moveStrafe,
                minecraft.thePlayer.movementInput.jump, minecraft.thePlayer.movementInput.sneak);
        invokeAll("onPrePlayerInput", input);
        minecraft.thePlayer.movementInput.moveForward = input.forward;
        minecraft.thePlayer.movementInput.moveStrafe = input.strafe;
        minecraft.thePlayer.movementInput.jump = input.jump;
        minecraft.thePlayer.movementInput.sneak = input.sneak;
        PlayerState state = PlayerState.capture(minecraft.thePlayer);
        invokeAll("onPreMotion", state);
        state.apply(minecraft.thePlayer);
    }
    public void tickEnd() {
        invokeAll("onPostPlayerInput");
        invokeAll("onPostMotion");
        if (minecraft.thePlayer != null) invokeAll("onPlayerMove",
                new Vec3(minecraft.thePlayer.motionX, minecraft.thePlayer.motionY, minecraft.thePlayer.motionZ));
        invokeAll("onPostUpdate");
    }
    public void renderTick(float partial) { invokeAll("onRenderTick", partial); }
    public void renderWorld(float partial) { invokeAll("onRenderWorld", partial); }
    public boolean key(char character, int key) { return invokeBooleanAll("onKeyPress", character, key); }
    public boolean chat(String text) { return invokeBooleanAll("onChat", text); }
    public boolean mouse(int button, boolean state) { return invokeBooleanAll("onMouse", button, state); }
    public boolean attack(Entity target, Entity attacker) { return invokeBooleanAll("onAttackEntity", target, attacker) && invokeBooleanAll("onPreAttack"); }
    public void gui(String name, boolean opened) { invokeAll("onGuiUpdate", name, opened); }
    public void worldJoin(Entity entity) { invokeAll("onWorldJoin", entity); }
    public void disconnect() { invokeAll("onDisconnect"); }
    public void playerInteract() { invokeAll("onPrePlayerInteract"); }
    public void antiCheatFlag(String flag, Entity entity) { invokeAll("onAntiCheatFlag", flag, entity); }
    public boolean outbound(Packet<?> packet) { CPacket wrapped = PacketHandler.convertServerBound(packet); return wrapped == null || invokeBooleanAll("onPacketSent", wrapped); }
    public void dispatched(Packet<?> packet) { CPacket wrapped = PacketHandler.convertServerBound(packet); if (wrapped != null) invokeAll("onDispatchPacket", wrapped); }
    public boolean inbound(Packet<?> packet) { SPacket wrapped = PacketHandler.convertClientBound(packet); return wrapped == null || invokeBooleanAll("onPacketReceived", wrapped); }

    private String validate(String requested, String existing) {
        String name = requested == null ? "" : requested.trim();
        if (name.isEmpty() || name.endsWith(".") || name.endsWith(" ")) { diagnostic("Script name is invalid."); return null; }
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) < 32 || contains(INVALID_NAME, name.charAt(i))) { diagnostic("Script name contains invalid characters."); return null; }
        File file = new File(directory, name + ".java");
        if ((existing == null || !existing.equalsIgnoreCase(name)) && file.exists()) { diagnostic("A script named " + name + " already exists."); return null; }
        return name;
    }
    private void clearCompiled() { if (!compiledDirectory.exists()) return; File[] files = compiledDirectory.listFiles(); if (files != null) for (File file : files) deleteTree(file); }
    private void deleteTree(File file) { if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); } file.delete(); }
    private static void close(ClassLoader loader) { if (loader instanceof Closeable) try { ((Closeable) loader).close(); } catch (IOException ignored) { } }
    private static boolean contains(char[] chars, char value) { for (char c : chars) if (c == value) return true; return false; }
    private static String normalize(String name) { return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static String cleanClassName(String name) { String value = normalize(name); return value.isEmpty() ? "script" : value; }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String rootMessage(Throwable exception) { Throwable current = exception; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage(); }
    private String classPath() {
        StringBuilder path = new StringBuilder(System.getProperty("java.class.path", ""));
        try {
            URL codeSource = Vibe.class.getProtectionDomain().getCodeSource().getLocation();
            appendPath(path, new File(codeSource.toURI()).getAbsolutePath());
        } catch (Exception ignored) { }
        // Forge normally supplies a LaunchClassLoader rather than a plain
        // URLClassLoader. Include URL entries from either loader shape so the
        // compiler can resolve Minecraft/Forge classes when a script imports
        // them directly.
        ClassLoader loader = Vibe.class.getClassLoader();
        while (loader != null) {
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) {
                    try { appendPath(path, new File(url.toURI()).getAbsolutePath()); } catch (Exception ignored) { }
                }
            } else {
                try {
                    Method method = loader.getClass().getMethod("getURLs");
                    Object value = method.invoke(loader);
                    if (value instanceof URL[]) for (URL url : (URL[]) value) {
                        try { appendPath(path, new File(url.toURI()).getAbsolutePath()); } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }
            }
            loader = loader.getParent();
        }
        return path.toString();
    }
    private static void appendPath(StringBuilder path, String value) {
        if (value == null || value.isEmpty()) return;
        if (path.length() > 0) path.append(File.pathSeparator);
        path.append(value);
    }
    private String describeFailure(Program program, String phase, Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        StringBuilder text = new StringBuilder(program.name).append(' ').append(phase).append(": ")
                .append(root.getClass().getSimpleName());
        if (message != null && !message.trim().isEmpty()) text.append(" ( ").append(message.trim()).append(" )");
        for (StackTraceElement element : root.getStackTrace()) {
            if (program.className != null && program.className.equals(element.getClassName()) && element.getLineNumber() > 0) {
                // header() adds ten generated lines before the user's source.
                text.append(" at source line ").append(Math.max(1, element.getLineNumber() - 10));
                break;
            }
        }
        root.printStackTrace();
        return text.toString();
    }
    private void diagnostic(String text) { diagnostics.add(text); if (diagnostics.size() > 30) diagnostics.remove(0); System.err.println("[Vibe Scripts] " + text); }
    private static String defaultTemplate() { return "// Raven BS-compatible Vibe script\nvoid onLoad() {\n    modules.registerButton(\"Enabled setting\", true);\n}\n\nvoid onEnable() {\n    client.print(\"Script enabled\");\n}\n\nvoid onDisable() {\n}\n\nvoid onPreUpdate() {\n}\n"; }

    private static final class Program {
        private final String name; private final File file; private final ScriptModule module;
        private String className; private Class<?> type; private Object instance; private ClassLoader loader;
        private Program(String name, File file, ScriptModule module) { this.name = name; this.file = file; this.module = module; }
        private ScriptInfo info() { return new ScriptInfo(name, file, module.isEnabled(), instance != null); }
    }
    public static final class ScriptInfo {
        private final String name; private final File file; private final boolean enabled; private final boolean loaded;
        private ScriptInfo(String name, File file, boolean enabled, boolean loaded) { this.name = name; this.file = file; this.enabled = enabled; this.loaded = loaded; }
        public String getName() { return name; } public File getFile() { return file; } public boolean isEnabled() { return enabled; } public boolean isLoaded() { return loaded; }
    }
    private static String normalizeScriptName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static final class TolerantClassLoader extends ClassLoader {
        private final ClassLoader primary;
        private TolerantClassLoader(ClassLoader primary) { super(ClassLoader.getSystemClassLoader()); this.primary = primary; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Never ask LaunchClassLoader to resolve platform/compiler classes;
            // its 1.8.9 implementation can dereference an absent package entry
            // and throw NPE instead of ClassNotFoundException.
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")
                    || name.startsWith("org.codehaus.janino.") || name.startsWith("org.codehaus.commons.compiler.")) {
                return super.loadClass(name, resolve);
            }
            try {
                return primary.loadClass(name);
            } catch (ClassNotFoundException ignored) {
                return super.loadClass(name, resolve);
            } catch (LinkageError ignored) {
                return super.loadClass(name, resolve);
            } catch (RuntimeException ignored) {
                // LaunchClassLoader 1.8.9 can surface missing optional entries
                // as NullPointerException instead of ClassNotFoundException.
                return super.loadClass(name, resolve);
            }
        }
    }

    private static final class JavaSource extends javax.tools.SimpleJavaFileObject {
        private final String source;
        private JavaSource(String name, String source) { super(java.net.URI.create("string:///" + name + ".java"), javax.tools.JavaFileObject.Kind.SOURCE); this.source = source; }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
    }
}
