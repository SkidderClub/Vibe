package dev.vibe.account;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MicrosoftCookiesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final URI LIVE = URI.create("https://login.live.com/oauth20_authorize.srf");
    private static final String COOKIE = ".live.com\tTRUE\t/\tTRUE\t0\tMSPAuth\tsynthetic-cookie\n";

    @Test public void headerlessExportAndBomHttpOnlyCommentsAreAccepted() throws Exception {
        try (MicrosoftCookies cookies = parse("\uFEFF# Netscape HTTP Cookie File\r\n\r\n#HttpOnly_" + COOKIE)) {
            assertEquals("MSPAuth=synthetic-cookie", cookies.header(LIVE));
            assertEquals("", cookies.header(URI.create("https://login.microsoftonline.com/")));
        }
        try (MicrosoftCookies cookies = parse(COOKIE)) { assertEquals("MSPAuth=synthetic-cookie", cookies.header(LIVE)); }
    }

    @Test public void respectsDomainFlagsExpiryAndPathBoundaries() throws Exception {
        String text = COOKIE
                + "live.com\tFALSE\t/\tTRUE\t0\thostOnly\tignore\n"
                + ".live.com\tTRUE\t/\tTRUE\t1\texpired\tignore\n"
                + "login.live.com\tFALSE\t/oauth\tTRUE\t0\tpath\tbounded\n"
                + "login.live.com\tFALSE\t/oauth/child\tTRUE\t0\tdeep\tfirst\n"
                + ".example.invalid\tTRUE\t/\tTRUE\t0\tunrelated\tignore\n";
        try (MicrosoftCookies cookies = parse(text)) {
            assertEquals("MSPAuth=synthetic-cookie", cookies.header(LIVE));
            assertEquals("path=bounded; MSPAuth=synthetic-cookie", cookies.header(URI.create("https://login.live.com/oauth")));
            assertEquals("deep=first; path=bounded; MSPAuth=synthetic-cookie",
                    cookies.header(URI.create("https://login.live.com/oauth/child/page")));
        }
    }

    @Test public void cookieHeadersNeverGoToUntrustedOrigins() throws Exception {
        try (MicrosoftCookies cookies = parse(COOKIE)) {
            for (String address : Arrays.asList("http://login.live.com/", "https://login.live.com.evil.invalid/",
                    "https://example.invalid/", "https://login.live.com:8443/", "https://user@login.live.com/",
                    "https://login.live.com/#fragment")) {
                assertThrows(IOException.class, () -> cookies.header(URI.create(address)));
            }
        }
    }

    @Test public void providerRotationDeletionAndCookieScopeAreRespected() throws Exception {
        try (MicrosoftCookies cookies = parse(COOKIE + COOKIE.replace("synthetic-cookie", "replacement"))) {
            assertEquals("MSPAuth=replacement", cookies.header(LIVE));
            cookies.receive(LIVE, Arrays.asList("MSPAuth=rotated; Domain=.live.com; Path=/; Secure; HttpOnly",
                    "bad=ignore; Domain=.microsoftonline.com; Path=/", "host=value; Secure"));
            assertEquals("MSPAuth=rotated; host=value", cookies.header(LIVE));
            assertEquals("", cookies.header(URI.create("https://login.microsoftonline.com/")));
            cookies.receive(LIVE, Collections.singletonList("MSPAuth=; Domain=.live.com; Path=/; Max-Age=0"));
            assertEquals("host=value", cookies.header(LIVE));
            cookies.close();
            assertEquals("", cookies.header(LIVE));
        }
    }

    @Test public void malformedExportsCannotInjectHeadersOrLeakValuesInErrors() {
        for (String text : Arrays.asList("[]", "synthetic-private-text", COOKIE.replace("TRUE", "maybe"),
                COOKIE.replace("\t0\t", "\t-1\t"), COOKIE.replace("\t0\t", "\t999999999999999999999\t"),
                COOKIE.replace("synthetic-cookie", "synthetic-private; injected=value"),
                COOKIE.replace("synthetic-cookie", "synthetic-private\rInjected: value"),
                COOKIE.replace("synthetic-cookie", "synthetic-private\textra-field"),
                COOKIE.replace("MSPAuth", "bad name"))) {
            IOException failure = assertThrows(IOException.class, () -> parse(text));
            assertFalse(failure.getMessage().contains("synthetic-private"));
            assertNull(failure.getCause());
        }
    }

    @Test public void emptyExpiredAndUnrelatedExportsHaveActionableErrors() {
        for (String text : Arrays.asList("", "# empty\n", COOKIE.replace("\t0\t", "\t1\t"),
                COOKIE.replace(".live.com", ".example.invalid"))) {
            IOException failure = assertThrows(IOException.class, () -> parse(text));
            assertTrue(failure.getMessage().contains("No unexpired Microsoft"));
        }
    }

    @Test public void readsSelectedFileWithoutChangingItAndRejectsOversizedFiles() throws Exception {
        Path file = temporary.newFile("cookies.txt").toPath();
        byte[] original = COOKIE.getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        try (MicrosoftCookies cookies = MicrosoftCookies.read(file)) { assertEquals("MSPAuth=synthetic-cookie", cookies.header(LIVE)); }
        assertArrayEquals(original, Files.readAllBytes(file));
        Files.write(file, new byte[1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> MicrosoftCookies.read(file));
    }

    @Test public void cookieCountIsBoundedAndCancellationStopsParsing() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 513; i++) many.append(COOKIE);
        assertThrows(IOException.class, () -> parse(many.toString()));
        Thread.currentThread().interrupt();
        try { assertThrows(CancellationException.class, () -> parse(COOKIE)); }
        finally { Thread.interrupted(); }
    }

    private static MicrosoftCookies parse(String text) throws IOException {
        return MicrosoftCookies.parse(text, System.currentTimeMillis() / 1000);
    }
}
