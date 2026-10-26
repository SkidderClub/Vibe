package dev.vibe.miniblox;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Installs the separate upstream bridge on first use. No work runs on the render thread. */
public final class MinibloxRuntime implements AutoCloseable {
    public static final String REVISION = "842d2fd9779982bb8689a536c9f91e7f428eb7b0";
    public static final String PROJECT = "https://github.com/7GrandDadPGN/MinibloxTranslationLayer";
    private final Path root;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "Vibe Miniblox installer"); t.setDaemon(true); return t;
    });
    private Attempt active;
    private Process bridge;
    private int port;
    private boolean closed;

    public MinibloxRuntime(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Path root() { return root; }
    public Path source() { return root.resolve("layer-" + REVISION.substring(0, 12)); }
    public synchronized boolean running() { return bridge != null && bridge.isAlive() && port > 0; }
    public synchronized boolean owns(int remotePort) { return running() && remotePort == port; }

    public synchronized Attempt start() {
        if (active != null) active.cancel();
        Attempt request = new Attempt();
        if (closed) { request.error = "Miniblox has been shut down."; return request; }
        active = request;
        if (running()) { request.port = port; request.message = "Ready to connect"; return request; }
        request.future = worker.submit(() -> launch(request));
        return request;
    }

    public static final class Attempt {
        public volatile String message = "Preparing Miniblox";
        public volatile String error;
        public volatile int port;
        private volatile boolean cancelled;
        private volatile Process child;
        private volatile Future<?> future;
        public boolean cancelled() { return cancelled; }
        public void cancel() {
            cancelled = true;
            Future<?> task = future; if (task != null) task.cancel(true);
            Process process = child; if (process != null) process.destroy();
        }
        private void check() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
        }
    }

    private void launch(Attempt request) {
        Process process = null;
        try {
            Files.createDirectories(root);
            Path node = node(request);
            install(node, request);
            request.check(); request.message = "Starting translation layer";
            try (InputStream wrapper = MinibloxRuntime.class.getResourceAsStream("/assets/vibe/miniblox/bridge.cjs")) {
                if (wrapper == null) throw new IOException("The Miniblox bridge launcher is missing from Vibe.");
                Files.copy(wrapper, source().resolve(".vibe-bridge.cjs"), StandardCopyOption.REPLACE_EXISTING);
            }
            String nonce = UUID.randomUUID().toString();
            ProcessBuilder builder = new ProcessBuilder(node.toString(), ".vibe-bridge.cjs");
            builder.directory(source().toFile()).redirectErrorStream(true);
            builder.environment().put("VIBE_BRIDGE_NONCE", nonce);
            process = builder.start(); request.child = process;
            CompletableFuture<Integer> ready = new CompletableFuture<>();
            Process launched = process;
            Thread output = new Thread(() -> readOutput(launched, nonce, ready), "Vibe Miniblox output");
            output.setDaemon(true); output.start();
            int listening = ready.get(45, TimeUnit.SECONDS);
            request.check();
            synchronized (this) {
                request.check();
                if (closed || active != request || !process.isAlive()) throw new IOException("The translation layer stopped during startup.");
                bridge = process; port = listening; request.child = null;
                request.message = "Ready to connect"; request.port = listening;
            }
            process = null; // Owned by this runtime until game shutdown; reused for reconnects.
        } catch (Exception error) {
            if (!request.cancelled) {
                Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
                request.error = cause instanceof TimeoutException ? "The translation layer did not become ready in time."
                        : cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                request.message = "Miniblox could not start";
            }
        } finally {
            request.child = null;
            if (process != null) process.destroyForcibly();
        }
    }

    private void readOutput(Process process, String nonce, CompletableFuture<Integer> ready) {
        String prefix = "[VibeMiniblox:" + nonce + "]:";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter log = Files.newBufferedWriter(root.resolve("bridge.log"), StandardCharsets.UTF_8)) {
            String line; long written = 0;
            while ((line = reader.readLine()) != null) {
                int result = readyPort(line, prefix);
                if (result > 0) ready.complete(result);
                if (written < 4 * 1024 * 1024) {
                    log.write(line); log.newLine(); log.flush(); written += line.length() + 1;
                }
            }
            ready.completeExceptionally(new IOException("The translation layer exited. See vibe/miniblox/bridge.log."));
        } catch (IOException error) { ready.completeExceptionally(error); }
    }

    static int readyPort(String line, String prefix) {
        if (!line.startsWith(prefix)) return -1;
        try { int value = Integer.parseInt(line.substring(prefix.length())); return value > 0 && value <= 65535 ? value : -1; }
        catch (NumberFormatException ignored) { return -1; }
    }

    private void install(Path node, Attempt request) throws Exception {
        if (Files.isRegularFile(source().resolve(".vibe-ready")) && Files.isRegularFile(source().resolve("index.js"))
                && Files.isDirectory(source().resolve("node_modules/minecraft-protocol"))) return;
        request.message = "Downloading Miniblox Translation Layer";
        Path staging = Files.createTempDirectory(root, "install-");
        try {
            Path archive = staging.resolve("source.zip");
            download("https://codeload.github.com/7GrandDadPGN/MinibloxTranslationLayer/zip/" + REVISION, archive, 25 * 1024 * 1024L, request);
            Path unpacked = staging.resolve("unpacked");
            extract(archive, unpacked, 100 * 1024 * 1024L, request);
            Path layer = unpacked.resolve("MinibloxTranslationLayer-" + REVISION);
            if (!Files.isRegularFile(layer.resolve("index.js")) || !Files.isRegularFile(layer.resolve("LICENSE.md")))
                throw new IOException("The downloaded translation layer is incomplete.");
            request.message = "Installing translation layer dependencies";
            runInstall(node, layer, request);
            request.check();
            Files.write(layer.resolve(".vibe-ready"), Collections.singletonList(REVISION), StandardCharsets.UTF_8);
            if (Files.isRegularFile(source().resolve("login.token")))
                Files.copy(source().resolve("login.token"), layer.resolve("login.token"), StandardCopyOption.REPLACE_EXISTING);
            removeOwned(source()); // Only a previous incomplete install of this same revision.
            Files.move(layer, source());
        } finally { removeOwned(staging); }
    }

    private void runInstall(Path node, Path directory, Attempt request) throws Exception {
        Path npm = npmFor(node);
        if (npm == null) throw new IOException("npm is missing from the Node.js installation.");
        List<String> command = Arrays.asList(node.toString(), npm.toString(), "install", "--omit=dev", "--ignore-scripts", "--no-audit", "--no-fund");
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(root.resolve("install.log").toFile());
        builder.environment().put("npm_config_cache", root.resolve("npm-cache").toString());
        builder.environment().put("npm_config_update_notifier", "false");
        builder.environment().put("PATH", node.getParent() + File.pathSeparator + builder.environment().getOrDefault("PATH", ""));
        Process process = builder.start(); request.child = process;
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) throw new IOException("Dependency installation timed out. Retry when your connection is available.");
            request.check();
            if (process.exitValue() != 0) throw new IOException("Could not install Miniblox dependencies. See vibe/miniblox/install.log.");
        } finally { if (process.isAlive()) process.destroyForcibly(); request.child = null; }
    }

    private Path node(Attempt request) throws Exception {
        request.message = "Checking Node.js";
        Path portable = root.resolve("node");
        if (Files.isDirectory(portable)) try (DirectoryStream<Path> dirs = Files.newDirectoryStream(portable, "node-v*")) {
            for (Path dir : dirs) { Path exe = dir.resolve("node.exe"); if (usableNode(exe, request)) return exe; }
        }
        String path = System.getenv("PATH");
        if (path != null) for (String entry : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path exe;
            try { exe = Paths.get(entry.replace("\"", "")).resolve(windows() ? "node.exe" : "node").toAbsolutePath(); }
            catch (InvalidPathException ignored) { continue; }
            if (usableNode(exe, request)) return exe;
        }
        if (!windows()) throw new IOException("Install Node.js 22 or newer with npm, then try Miniblox again.");
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String arch = architecture.equals("aarch64") || architecture.equals("arm64") ? "arm64"
                : architecture.equals("amd64") || architecture.equals("x86_64") ? "x64" : "";
        if (arch.isEmpty()) throw new IOException("Miniblox needs 64-bit Windows and Node.js 22 or newer.");
        request.message = "Downloading portable Node.js";
        Path sums = root.resolve("node-shasums.txt");
        download("https://nodejs.org/dist/latest-v24.x/SHASUMS256.txt", sums, 1024 * 1024, request);
        String filename = null, checksum = null;
        for (String line : Files.readAllLines(sums, StandardCharsets.UTF_8)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[0].matches("[a-fA-F0-9]{64}") && parts[1].matches("node-v24\\.\\d+\\.\\d+-win-" + arch + "\\.zip")) {
                checksum = parts[0]; filename = parts[1]; break;
            }
        }
        if (filename == null) throw new IOException("Node.js did not provide a compatible download.");
        Path archive = root.resolve("node-download.zip");
        Path unpacked = Files.createTempDirectory(root, "node-install-");
        try {
            download("https://nodejs.org/dist/" + filename.substring(5, filename.indexOf("-win-")) + "/" + filename, archive, 100 * 1024 * 1024L, request);
            if (!checksum.equalsIgnoreCase(sha256(archive))) throw new IOException("Node.js download checksum did not match. Please retry.");
            request.message = "Installing portable Node.js";
            extract(archive, unpacked, 400 * 1024 * 1024L, request);
            Path folder = unpacked.resolve(filename.substring(0, filename.length() - 4));
            if (!usableNode(folder.resolve("node.exe"), request)) throw new IOException("The portable Node.js installation did not start.");
            Files.createDirectories(portable);
            Path installed = portable.resolve(folder.getFileName());
            removeOwned(installed); Files.move(folder, installed);
            return installed.resolve("node.exe");
        } finally { Files.deleteIfExists(archive); removeOwned(unpacked); }
    }

    private static boolean windows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"); }
    private static Path npmFor(Path node) {
        Path folder = node.getParent();
        if (folder == null) return null;
        for (Path candidate : Arrays.asList(folder.resolve("node_modules/npm/bin/npm-cli.js"), folder.resolve("../lib/node_modules/npm/bin/npm-cli.js").normalize()))
            if (Files.isRegularFile(candidate)) return candidate;
        return null;
    }
    private static boolean usableNode(Path node, Attempt request) throws IOException {
        request.check();
        if (!Files.isRegularFile(node) || npmFor(node) == null) return false;
        Process process = null;
        try {
            process = new ProcessBuilder(node.toString(), "-p", "Number(process.versions.node.split('.')[0]) >= 22").redirectErrorStream(true).start();
            request.child = process;
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) return false;
            BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            return "true".equals(output.readLine());
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); request.check(); return false; }
        catch (IOException ignored) { request.check(); return false; }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); request.child = null; }
    }

    private static void download(String address, Path target, long max, Attempt request) throws IOException {
        request.check();
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Vibe-Miniblox/1.0");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Download failed (HTTP " + connection.getResponseCode() + "). Please retry.");
            if (connection.getContentLengthLong() > max) throw new IOException("The download is unexpectedly large.");
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[32768]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) {
                    request.check(); total += count;
                    if (total > max) throw new IOException("The download is unexpectedly large.");
                    output.write(buffer, 0, count);
                }
            }
        } finally { connection.disconnect(); }
    }

    static void extract(Path archive, Path target, long max, Attempt request) throws IOException {
        Path base = target.toAbsolutePath().normalize(); Files.createDirectories(base);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry; long total = 0; int entries = 0; byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                request.check();
                Path file = base.resolve(entry.getName().replace('\\', '/')).normalize();
                if (!file.startsWith(base) || file.equals(base) || ++entries > 30000) throw new IOException("Invalid archive entry.");
                if (entry.isDirectory()) { Files.createDirectories(file); continue; }
                Files.createDirectories(file.getParent());
                try (OutputStream output = Files.newOutputStream(file)) {
                    int count; while ((count = zip.read(buffer)) != -1) {
                        request.check(); total += count;
                        if (total > max) throw new IOException("The extracted download is unexpectedly large.");
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255)); return hex.toString();
    }

    private void removeOwned(Path directory) throws IOException {
        Path checked = directory.toAbsolutePath().normalize();
        if (!checked.startsWith(root) || checked.equals(root)) throw new IOException("Invalid Miniblox cleanup path.");
        if (!Files.exists(checked)) return;
        Files.walkFileTree(checked, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) throw failure; Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override public synchronized void close() {
        closed = true;
        if (active != null) active.cancel();
        if (bridge != null) { try { bridge.getOutputStream().close(); } catch (IOException ignored) { } bridge.destroy(); }
        bridge = null; port = 0; worker.shutdownNow();
    }
}
