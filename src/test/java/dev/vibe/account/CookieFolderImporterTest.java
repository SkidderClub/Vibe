package dev.vibe.account;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CookieFolderImporterTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String COOKIE = ".live.com\tTRUE\t/\tTRUE\t0\tMSPAuth\tsynthetic-cookie\n";
    private static final Account ACCOUNT = new Account("Example", UUID.fromString("12345678-1234-1234-1234-123456789abc"), "synthetic-refresh");
    private static final URI LIVE = URI.create("https://login.live.com/");

    @Test public void createsInboxAndImportsOnlyStableDirectTextExports() throws Exception {
        Path folder = temporary.getRoot().toPath().resolve("cookies");
        List<Account> saved = new ArrayList<>();
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> {
            assertEquals("MSPAuth=synthetic-cookie", cookies.header(LIVE));
            return ACCOUNT;
        }, account -> saved.add(account));
        importer.scan();
        assertTrue(Files.isDirectory(folder.resolve("valid")));
        write(folder.resolve("export.TXT"), "partially copied");
        write(folder.resolve("ignored.json"), COOKIE);
        write(folder.resolve("valid/already.txt"), COOKIE);
        importer.scan();
        assertTrue(saved.isEmpty());
        write(folder.resolve("export.TXT"), COOKIE);
        importer.scan();
        assertTrue(saved.isEmpty());
        importer.scan();
        assertEquals(1, saved.size());
        assertFalse(Files.exists(folder.resolve("export.TXT")));
        assertEquals(COOKIE, read(folder.resolve("valid/export.TXT")));
        String note = read(folder.resolve("valid/export.TXT.result"));
        assertTrue(note.contains("Added Example"));
        assertFalse(note.contains("synthetic"));
        assertEquals("1 valid / 0 invalid / 0 errors / 0 pending", importer.getSummary());
        importer.scan();
        new CookieFolderImporter(folder, cookies -> { fail("Archived exports must not be rechecked after restart"); return ACCOUNT; }, account -> false).scan();
        assertEquals(1, saved.size());
    }

    @Test public void mixedBatchSeparatesInvalidCookiesFromConnectionAndRuntimeErrors() throws Exception {
        Path folder = inbox();
        write(folder.resolve("a-malformed.txt"), "not Netscape");
        write(folder.resolve("b-expired.txt"), COOKIE.replace("\t0\t", "\t1\t"));
        write(folder.resolve("c-network.txt"), COOKIE.replace("synthetic-cookie", "network"));
        write(folder.resolve("d-runtime.txt"), COOKIE.replace("synthetic-cookie", "runtime"));
        write(folder.resolve("e-good.txt"), COOKIE);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> {
            calls.incrementAndGet();
            if (cookies.header(LIVE).contains("network")) throw new IOException("synthetic-private-provider-response");
            if (cookies.header(LIVE).contains("runtime")) throw new IllegalArgumentException("synthetic-private-parser-response");
            return ACCOUNT;
        }, account -> { saved.incrementAndGet(); return true; });
        for (int i = 0; i < 6; i++) importer.scan();
        assertEquals(3, calls.get());
        assertEquals(1, saved.get());
        assertTrue(Files.exists(folder.resolve("invalid/a-malformed.txt")));
        assertTrue(Files.exists(folder.resolve("invalid/b-expired.txt")));
        assertTrue(Files.exists(folder.resolve("error/c-network.txt")));
        assertTrue(Files.exists(folder.resolve("error/d-runtime.txt")));
        assertFalse(read(folder.resolve("error/c-network.txt.result")).contains("synthetic-private"));
        assertFalse(read(folder.resolve("error/d-runtime.txt.result")).contains("synthetic-private"));
        assertEquals("1 valid / 2 invalid / 2 errors / 0 pending", importer.getSummary());
    }

    @Test public void rejectedSessionIsInvalidAndSaveFailureIsAnError() throws Exception {
        Path folder = inbox();
        write(folder.resolve("a-revoked.txt"), COOKIE.replace("synthetic-cookie", "revoked"));
        write(folder.resolve("b-unsaved.txt"), COOKIE);
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> {
            if (cookies.header(LIVE).contains("revoked")) throw new InvalidCookiesException("Session revoked.");
            return ACCOUNT;
        }, account -> { throw new IOException("synthetic-private-vault-path"); });
        for (int i = 0; i < 3; i++) importer.scan();
        assertTrue(Files.exists(folder.resolve("invalid/a-revoked.txt")));
        String note = read(folder.resolve("error/b-unsaved.txt.result"));
        assertTrue(note.contains("could not be saved"));
        assertFalse(note.contains("synthetic-private"));
        assertEquals("0 valid / 1 invalid / 1 errors / 0 pending", importer.getSummary());
    }

    @Test public void duplicateAndArchiveCollisionKeepEarlierExport() throws Exception {
        Path folder = inbox();
        AtomicInteger saves = new AtomicInteger();
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> ACCOUNT,
                account -> saves.getAndIncrement() == 0);
        write(folder.resolve("same.txt"), COOKIE);
        importer.scan();
        importer.scan();
        write(folder.resolve("same.txt"), COOKIE + "# new export\n");
        importer.scan();
        importer.scan();
        assertEquals(2, saves.get());
        assertEquals(COOKIE, read(folder.resolve("valid/same.txt")));
        try (Stream<Path> files = Files.list(folder.resolve("valid"))) {
            List<Path> notes = new ArrayList<>();
            files.filter(path -> path.toString().endsWith(".result")).forEach(notes::add);
            assertEquals(2, notes.size());
            assertTrue(read(notes.get(0)).contains("Updated Example") || read(notes.get(1)).contains("Updated Example"));
        }
    }

    @Test public void pauseAndMovingErrorBackAllowExplicitRetry() throws Exception {
        Path folder = inbox();
        write(folder.resolve("retry.txt"), COOKIE);
        AtomicInteger calls = new AtomicInteger();
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> {
            if (calls.getAndIncrement() == 0) throw new IOException("temporary failure");
            return ACCOUNT;
        }, account -> true);
        importer.setPaused(true);
        importer.scan();
        importer.scan();
        assertEquals(0, calls.get());
        importer.setPaused(false);
        importer.scan();
        importer.scan();
        assertTrue(Files.exists(folder.resolve("error/retry.txt")));
        importer.scan();
        assertEquals(1, calls.get());
        Files.move(folder.resolve("error/retry.txt"), folder.resolve("retry.txt"));
        importer.scan();
        importer.scan();
        assertEquals(2, calls.get());
        assertTrue(Files.exists(folder.resolve("valid/retry.txt")));
    }

    @Test public void changedContentsDuringLoginAreNotArchivedAsTheCheckedExport() throws Exception {
        Path folder = inbox();
        Path source = folder.resolve("changing.txt");
        write(source, COOKIE);
        FileTime stamp = Files.getLastModifiedTime(source);
        AtomicInteger calls = new AtomicInteger();
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> {
            if (calls.getAndIncrement() == 0) {
                // Same size and timestamp: the digest must still detect this change.
                write(source, COOKIE.replace("synthetic-cookie", "synthetic-change"));
                Files.setLastModifiedTime(source, stamp);
            }
            return ACCOUNT;
        }, account -> true);
        importer.scan();
        importer.scan();
        assertTrue(Files.exists(source));
        assertFalse(Files.exists(folder.resolve("valid/changing.txt")));
        importer.scan();
        importer.scan();
        assertEquals(2, calls.get());
        assertEquals(COOKIE.replace("synthetic-cookie", "synthetic-change"), read(folder.resolve("valid/changing.txt")));
    }

    @Test public void archiveFailureRetriesWithoutAuthenticatingOrSavingAgain() throws Exception {
        Path folder = inbox();
        write(folder.resolve("retry.txt"), COOKIE);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> {
            calls.incrementAndGet();
            Files.delete(folder.resolve("valid")); // Empty test directory only.
            write(folder.resolve("valid"), "blocks archiving");
            return ACCOUNT;
        }, account -> { saves.incrementAndGet(); return true; });
        importer.scan();
        importer.scan();
        assertTrue(Files.exists(folder.resolve("retry.txt")));
        Files.delete(folder.resolve("valid"));
        importer.scan();
        assertTrue(Files.exists(folder.resolve("valid/retry.txt")));
        assertEquals(1, calls.get());
        assertEquals(1, saves.get());
    }

    @Test public void cancellationLeavesSourceUntouchedAndUnsaved() throws Exception {
        Path folder = inbox();
        write(folder.resolve("cancel.txt"), COOKIE);
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> { throw new CancellationException(); },
                account -> { fail("Cancelled login cannot be saved"); return false; });
        importer.scan();
        assertThrows(CancellationException.class, importer::scan);
        assertTrue(Files.exists(folder.resolve("cancel.txt")));
        assertFalse(Files.exists(folder.resolve("error/cancel.txt")));
    }

    @Test public void oversizedFileGoesToErrorWithoutAuthentication() throws Exception {
        Path folder = inbox();
        Files.write(folder.resolve("huge.txt"), new byte[1024 * 1024 + 1]);
        CookieFolderImporter importer = new CookieFolderImporter(folder, cookies -> { fail("Oversized exports must not authenticate"); return ACCOUNT; },
                account -> false);
        importer.scan();
        importer.scan();
        assertTrue(Files.exists(folder.resolve("error/huge.txt")));
    }

    private Path inbox() throws IOException { return temporary.newFolder("cookies").toPath(); }
    private static void write(Path path, String value) throws IOException { Files.write(path, value.getBytes(StandardCharsets.UTF_8)); }
    private static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
}
