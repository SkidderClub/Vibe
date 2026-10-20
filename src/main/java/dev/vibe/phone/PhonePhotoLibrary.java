package dev.vibe.phone;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Serial disk work; framebuffer access and texture upload stay on the render thread. */
public final class PhonePhotoLibrary {
    public static final class Photo {
        public final Path file;
        public final long taken;
        public final long bytes;
        private Photo(Path file, long taken, long bytes) { this.file = file; this.taken = taken; this.bytes = bytes; }
        public String name() { return file.getFileName().toString(); }
    }
    private final Path root;
    private final ExecutorService worker;
    private volatile List<Photo> photos = Collections.emptyList();
    private volatile boolean saving;
    private volatile String error = "";
    private volatile int revision;
    private final Map<Path, BufferedImage> decoded = new LinkedHashMap<Path, BufferedImage>(16, .75F, true);
    private final Set<Path> loading = new HashSet<Path>();
    private final Set<Path> failedImages = new HashSet<Path>();

    public PhonePhotoLibrary(Path directory) {
        root = directory.toAbsolutePath().normalize();
        worker = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "VibeOS Photos"); thread.setDaemon(true); return thread; });
    }
    public void refresh() { worker.execute(() -> { try { scan(); } catch (IOException failure) { fail(failure); } }); }
    /** Install the supplied gallery once, so deleting a starter photo is permanent. */
    public void installStarterPhotos() {
        worker.execute(() -> {
            try {
                Files.createDirectories(root);
                Path marker = root.resolve(".starter-photos-installed");
                if (Files.exists(marker)) return;
                String[] assets = {"52.png", "portrait.png", "ghostderboss.png", "img_0112.jpg"};
                for (int i = 0; i < assets.length; i++) {
                    Path target = root.resolve("VIBE_starter_" + (i + 1) + ".png");
                    if (Files.exists(target)) continue;
                    try (InputStream input = PhonePhotoLibrary.class.getResourceAsStream("/assets/vibe/phone/gallery/" + assets[i])) {
                        BufferedImage image = input == null ? null : ImageIO.read(input);
                        if (image == null) throw new IOException("Could not load starter photo");
                        Path temporary = Files.createTempFile(root, ".starter-", ".tmp");
                        try {
                            if (!ImageIO.write(image, "png", temporary.toFile())) throw new IOException("PNG encoder unavailable");
                            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                        } finally { Files.deleteIfExists(temporary); }
                    }
                }
                Files.write(marker, new byte[]{1}, StandardOpenOption.CREATE_NEW);
                scan();
            } catch (IOException failure) { fail(failure); }
        });
    }
    public boolean save(final BufferedImage image) {
        if (image == null || saving) return false;
        saving = true; error = "";
        worker.execute(() -> {
            Path temporary = null;
            try {
                Files.createDirectories(root);
                String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());
                Path target = root.resolve("VIBE_" + stamp + "_" + UUID.randomUUID().toString().substring(0, 6) + ".png");
                temporary = Files.createTempFile(root, ".capture-", ".tmp");
                if (!ImageIO.write(image, "png", temporary.toFile())) throw new IOException("PNG encoder unavailable");
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target); }
                scan();
            } catch (IOException failure) { fail(failure); }
            finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                saving = false;
            }
        });
        return true;
    }
    public void delete(Photo photo) {
        if (photo == null) return;
        worker.execute(() -> {
            try {
                Path target = photo.file.toAbsolutePath().normalize();
                if (!root.equals(target.getParent()) || !target.getFileName().toString().startsWith("VIBE_")
                        || !target.toString().endsWith(".png") || Files.isSymbolicLink(target)) throw new IOException("Invalid photo path");
                Files.deleteIfExists(target);
                synchronized (decoded) { decoded.remove(target); }
                scan();
            } catch (IOException failure) { fail(failure); }
        });
    }
    private void scan() throws IOException {
        Files.createDirectories(root);
        List<Photo> found = new ArrayList<Photo>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root, "VIBE_*.png")) {
            for (Path file : entries) if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                found.add(new Photo(file, Files.getLastModifiedTime(file).toMillis(), Files.size(file)));
        }
        found.sort((a, b) -> Long.compare(b.taken, a.taken));
        photos = Collections.unmodifiableList(found); revision++;
    }
    public BufferedImage image(Photo photo) {
        if (photo == null) return null;
        synchronized (decoded) {
            BufferedImage ready = decoded.get(photo.file);
            if (ready != null || loading.contains(photo.file) || failedImages.contains(photo.file)) return ready;
            loading.add(photo.file);
        }
        worker.execute(() -> {
            try (ImageInputStream stream = ImageIO.createImageInputStream(photo.file.toFile())) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IOException("Photo cannot be read");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > 36000000) throw new IOException("Photo is too large");
                    javax.imageio.ImageReadParam options = reader.getDefaultReadParam();
                    int sample = Math.max(1, Math.max(width, height) / 1280);
                    options.setSourceSubsampling(sample, sample, 0, 0);
                    BufferedImage original = reader.read(0, options);
                    double factor = Math.min(1, 1024D / Math.max(original.getWidth(), original.getHeight()));
                    BufferedImage thumbnail = new BufferedImage(Math.max(1, (int) (original.getWidth() * factor)),
                            Math.max(1, (int) (original.getHeight() * factor)), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = thumbnail.createGraphics();
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(original, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), null); graphics.dispose();
                    synchronized (decoded) {
                        decoded.put(photo.file, thumbnail);
                        while (decoded.size() > 10) decoded.remove(decoded.keySet().iterator().next());
                    }
                } finally { reader.dispose(); }
            } catch (IOException | RuntimeException failure) {
                error = "Could not load " + photo.name();
                synchronized (decoded) { failedImages.add(photo.file); }
            }
            finally { synchronized (decoded) { loading.remove(photo.file); } }
        });
        return null;
    }
    private void fail(Exception failure) { error = failure.getMessage() == null ? "Photo operation failed" : failure.getMessage(); }
    public List<Photo> photos() { return photos; }
    public boolean isSaving() { return saving; }
    public String error() { return error; }
    public int revision() { return revision; }
    public Path directory() { return root; }
    /** Testing/controlled shutdown: never called by the render thread. */
    public void awaitIdle() throws Exception { worker.submit(() -> { }).get(10, TimeUnit.SECONDS); }
    public void close() { worker.shutdown(); }
}
