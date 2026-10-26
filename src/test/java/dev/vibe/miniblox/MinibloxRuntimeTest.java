package dev.vibe.miniblox;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MinibloxRuntimeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path zip(String name, byte[] bytes) throws IOException {
        Path path = temporary.newFile().toPath();
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry(name)); output.write(bytes); output.closeEntry();
        }
        return path;
    }
    @Test public void extractsNestedArchive() throws Exception {
        Path dest = temporary.getRoot().toPath().resolve("unpacked");
        MinibloxRuntime.extract(zip("layer/base/index.js", new byte[]{1,2,3}), dest, 3, new MinibloxRuntime.Attempt());
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(dest.resolve("layer/base/index.js")));
    }
    @Test public void rejectsTraversalInBothPathStyles() throws Exception {
        for (String name : new String[]{"../escape", "..\\escape", "/absolute", "layer/../../escape"}) {
            try { MinibloxRuntime.extract(zip(name, new byte[]{1}), temporary.getRoot().toPath().resolve("safe"), 5, new MinibloxRuntime.Attempt()); fail(name); }
            catch (IOException expected) { assertEquals("Invalid archive entry.", expected.getMessage()); }
        }
        assertFalse(Files.exists(temporary.getRoot().toPath().resolve("escape")));
    }
    @Test public void rejectsExpandedSizeAndCancellation() throws Exception {
        Path archive = zip("large", new byte[1024]); Path target = temporary.getRoot().toPath().resolve("size");
        try { MinibloxRuntime.extract(archive, target, 100, new MinibloxRuntime.Attempt()); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("large")); }
        MinibloxRuntime.Attempt cancelled = new MinibloxRuntime.Attempt(); cancelled.cancel();
        try { MinibloxRuntime.extract(archive, target, 2000, cancelled); fail(); }
        catch (InterruptedIOException expected) { assertEquals("Cancelled", expected.getMessage()); }
    }
    @Test public void validatesReadinessSignalAndPort() {
        assertEquals(25565, MinibloxRuntime.readyPort("[VibeMiniblox:nonce]:25565", "[VibeMiniblox:nonce]:"));
        for (String suffix : new String[]{"0", "65536", "-1", "hello", "42 extra"})
            assertEquals(-1, MinibloxRuntime.readyPort("ready:" + suffix, "ready:"));
        assertEquals(-1, MinibloxRuntime.readyPort("[VibeMiniblox:other]:1234", "[VibeMiniblox:nonce]:"));
    }
    @Test public void computesOfficialArchiveHashFormat() throws Exception {
        Path file = temporary.newFile().toPath(); Files.write(file, "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", MinibloxRuntime.sha256(file));
    }
    @Test public void closingWithoutStartingDoesNotDownloadAnything() throws Exception {
        Path folder = temporary.getRoot().toPath().resolve("runtime");
        MinibloxRuntime runtime = new MinibloxRuntime(folder); runtime.close();
        assertFalse(runtime.running()); assertFalse(runtime.owns(25565)); assertFalse(Files.exists(folder));
        assertNotNull(runtime.start().error);
    }
}
