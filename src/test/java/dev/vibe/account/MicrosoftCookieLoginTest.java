package dev.vibe.account;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MicrosoftCookieLoginTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String COOKIE = ".live.com\tTRUE\t/\tTRUE\t0\tMSPAuth\tsynthetic-cookie\n";

    @Test public void cookieLoginFollowsScopedRedirectsAndCompletesPkceXboxAndMinecraft() throws Exception {
        AtomicInteger gets = new AtomicInteger();
        AtomicReference<String> state = new AtomicReference<>();
        AtomicReference<String> challenge = new AtomicReference<>();
        MicrosoftCookieLogin cookieLogin = new MicrosoftCookieLogin((uri, header) -> {
            int step = gets.getAndIncrement();
            if (step == 0) {
                assertEquals("MSPAuth=synthetic-cookie", header);
                assertEquals("login.live.com", uri.getHost());
                Map<String, String> query = query(uri.getRawQuery());
                assertEquals("00000000402b5328", query.get("client_id"));
                assertEquals("service::user.auth.xboxlive.com::MBI_SSL", query.get("scope"));
                assertEquals("none", query.get("prompt"));
                assertEquals("S256", query.get("code_challenge_method"));
                assertEquals(MicrosoftCookieLogin.REDIRECT, query.get("redirect_uri"));
                state.set(query.get("state"));
                challenge.set(query.get("code_challenge"));
                return new MicrosoftCookieLogin.Page(302, "https://login.microsoftonline.com/bridge",
                        Collections.singletonList("MSPAuth=rotated-cookie; Domain=.live.com; Path=/; Secure"));
            }
            if (step == 1) {
                assertEquals("", header);
                return page(302, "https://login.live.com/continue");
            }
            assertEquals(2, step);
            assertEquals("/continue", uri.getPath());
            assertEquals("MSPAuth=rotated-cookie", header);
            return page(302, MicrosoftCookieLogin.REDIRECT + "?code=synthetic-code&state=" + state.get());
        });
        Queue<MicrosoftLogin.Response> replies = new ArrayDeque<>();
        replies.add(response("{'access_token':'synthetic-ms','refresh_token':'synthetic-refresh'}"));
        replies.add(response("{'Token':'synthetic-xbl','DisplayClaims':{'xui':[{'uhs':'123'}]}}"));
        replies.add(response("{'Token':'synthetic-xsts','DisplayClaims':{'xui':[{'uhs':'123'}]}}"));
        replies.add(response("{'access_token':'synthetic-minecraft'}"));
        replies.add(response("{'id':'12345678123412341234123456789abc','name':'Example'}"));
        MicrosoftLogin login = new MicrosoftLogin((endpoint, type, body, bearer) -> {
            if (endpoint.endsWith("oauth20_token.srf")) {
                Map<String, String> form = query(body);
                assertEquals("00000000402b5328", form.get("client_id"));
                assertEquals("service::user.auth.xboxlive.com::MBI_SSL", form.get("scope"));
                assertEquals("authorization_code", form.get("grant_type"));
                assertEquals("synthetic-code", form.get("code"));
                assertEquals(MicrosoftCookieLogin.REDIRECT, form.get("redirect_uri"));
                assertEquals(challenge.get(), sha256(form.get("code_verifier")));
            }
            if (endpoint.endsWith("/user/authenticate")) assertTrue(body.contains("t=synthetic-ms"));
            if (endpoint.endsWith("/minecraft/profile")) assertEquals("synthetic-minecraft", bearer);
            assertFalse(body != null && body.contains("synthetic-cookie"));
            return replies.remove();
        }, cookieLogin);
        Path file = temporary.newFile("cookies.txt").toPath();
        Files.write(file, COOKIE.getBytes(StandardCharsets.UTF_8));
        MicrosoftLogin.Result result = login.fromCookies(file, ignored -> { });
        assertEquals(3, gets.get());
        assertTrue(replies.isEmpty());
        assertTrue(result.account.isMicrosoft());
        assertEquals("Example", result.account.getName());
        assertEquals("synthetic-refresh", result.account.refreshToken());
        assertEquals(MicrosoftApplication.MINECRAFT, result.account.application());
        assertEquals("synthetic-minecraft", result.accessToken());
        assertEquals(COOKIE, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        AccountStore store = new AccountStore(temporary.newFolder().toPath());
        store.save(Collections.singletonList(result.account));
        assertEquals("synthetic-refresh", store.load().get(0).refreshToken());
        assertEquals(MicrosoftApplication.MINECRAFT, store.load().get(0).application());
    }

    @Test public void hostileRedirectsAreRejectedBeforeAnotherRequest() throws Exception {
        for (String target : Arrays.asList("https://evil.invalid/?code=synthetic-secret", "http://login.live.com/",
                "https://login.live.com.evil.invalid/", "https://login.live.com:8443/", "https://user@login.live.com/",
                MicrosoftCookieLogin.REDIRECT + "#code=synthetic-secret",
                MicrosoftCookieLogin.REDIRECT.replace("login.live.com", "login.live.com.evil.invalid"),
                MicrosoftCookieLogin.REDIRECT.replace("login.live.com", "login.live.com:8443"),
                MicrosoftCookieLogin.REDIRECT.replace("login.live.com", "user@login.live.com"),
                MicrosoftCookieLogin.REDIRECT.replace("https://", "http://"),
                "https://[invalid/", "https://login.live.com/\r\nsynthetic-secret")) {
            AtomicInteger calls = new AtomicInteger();
            MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> {
                assertEquals(0, calls.getAndIncrement());
                return page(302, target);
            });
            IOException failure = assertThrows(IOException.class, () -> authorize(login));
            assertFalse(failure.getMessage().contains("synthetic-secret"));
            assertNull(failure.getCause());
            assertEquals(1, calls.get());
        }
    }

    @Test public void microsoftRedirectsWithEmptyFragmentsReachCallback() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> state = new AtomicReference<>();
        MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> {
            if (calls.getAndIncrement() == 0) {
                state.set(query(uri.getRawQuery()).get("state"));
                assertEquals("MSPAuth=synthetic-cookie", header);
                // Microsoft's real authorize endpoint includes this trailing empty fragment.
                return page(302, "https://login.live.com/oauth20_authorize.srf#");
            }
            assertEquals("login.live.com", uri.getHost());
            assertEquals("MSPAuth=synthetic-cookie", header);
            return page(302, MicrosoftCookieLogin.REDIRECT + "?code=synthetic-code&state=" + state.get() + "#");
        });
        assertEquals("synthetic-code", authorize(login).code);
        assertEquals(2, calls.get());
    }

    @Test public void mismatchedMissingAndDuplicateStateCannotRedeemACode() throws Exception {
        for (String suffix : Arrays.asList("&state=wrong", "", "&state=correct&state=correct")) {
            MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> page(302,
                    MicrosoftCookieLogin.REDIRECT + "?code=synthetic-secret" + suffix.replace("correct", query(uri.getRawQuery()).get("state"))));
            IOException failure = assertThrows(IOException.class, () -> authorize(login));
            assertFalse(failure.getMessage().contains("synthetic-secret"));
        }
    }

    @Test public void callbackPathMustMatchAndDuplicateOrMissingCodesAreRejected() throws Exception {
        for (String suffix : Arrays.asList("?code=one&code=two", "?code=", "?ignored=value")) {
            MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> page(302,
                    MicrosoftCookieLogin.REDIRECT + suffix + "&state=" + query(uri.getRawQuery()).get("state")));
            assertThrows(IOException.class, () -> authorize(login));
        }
        AtomicInteger calls = new AtomicInteger();
        MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> {
            if (calls.getAndIncrement() == 0) return page(302, MicrosoftCookieLogin.REDIRECT + "/wrong?code=synthetic-secret");
            return page(200, null);
        });
        assertThrows(IOException.class, () -> authorize(login));
        assertEquals(2, calls.get());
    }

    @Test public void interactiveAndRevokedSessionsReturnGuidanceWithoutProviderDetails() throws Exception {
        for (String error : Arrays.asList("login_required", "interaction_required", "consent_required", "access_denied", "invalid_client")) {
            MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> page(302,
                    MicrosoftCookieLogin.REDIRECT + "?error=" + error + "&state=" + query(uri.getRawQuery()).get("state")
                            + "&error_description=synthetic-private-details"));
            IOException failure = assertThrows(IOException.class, () -> authorize(login));
            if ("consent_required".equals(error)) {
                assertTrue(failure.getMessage().contains("Minecraft launcher"));
                assertFalse(failure.getMessage().contains("In-Game Account Switcher"));
            } else assertTrue(failure.getMessage().contains("Microsoft login"));
            if ("interaction_required".equals(error)) assertTrue(failure.getMessage().contains("interactive sign-in"));
            assertEquals("login_required".equals(error), failure instanceof InvalidCookiesException);
            if ("access_denied".equals(error)) assertTrue(failure.getMessage().contains("declined"));
            assertFalse(failure.getMessage().contains("synthetic-private"));
            assertNull(failure.getCause());
        }
    }

    @Test public void htmlRateLimitsAndOutagesAreReported() throws Exception {
        for (int status : new int[] {200, 429, 503, 302}) {
            MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> page(status, null));
            IOException failure = assertThrows(IOException.class, () -> authorize(login));
            if (status == 429) assertTrue(failure.getMessage().contains("rate limiting"));
            else if (status == 503) assertTrue(failure.getMessage().contains("temporarily unavailable"));
            else assertTrue(failure.getMessage().contains("Microsoft login"));
            assertFalse(failure instanceof InvalidCookiesException);
        }
    }

    @Test public void redirectLoopsAreBoundedAndTransportErrorsAreSanitized() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MicrosoftCookieLogin loop = new MicrosoftCookieLogin((uri, header) -> {
            calls.incrementAndGet();
            return page(302, "https://login.live.com/loop");
        });
        assertThrows(IOException.class, () -> authorize(loop));
        assertEquals(12, calls.get());
        MicrosoftCookieLogin failed = new MicrosoftCookieLogin((uri, header) -> { throw new IOException("synthetic-private-url"); });
        IOException failure = assertThrows(IOException.class, () -> authorize(failed));
        assertFalse(failure.getMessage().contains("synthetic-private"));
        assertNull(failure.getCause());
    }

    @Test public void cancellationStopsBeforeNextRedirectAndTokenExchange() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MicrosoftCookieLogin login = new MicrosoftCookieLogin((uri, header) -> {
            calls.incrementAndGet();
            Thread.currentThread().interrupt();
            return page(302, MicrosoftCookieLogin.REDIRECT + "?code=synthetic&state=" + query(uri.getRawQuery()).get("state"));
        });
        try { assertThrows(CancellationException.class, () -> authorize(login)); }
        finally { Thread.interrupted(); }
        assertEquals(1, calls.get());
    }

    private static MicrosoftCookieLogin.Authorization authorize(MicrosoftCookieLogin login) throws IOException {
        try (MicrosoftCookies cookies = MicrosoftCookies.parse(COOKIE, System.currentTimeMillis() / 1000)) {
            return login.authorize(cookies);
        }
    }
    private static MicrosoftCookieLogin.Page page(int status, String location) {
        return new MicrosoftCookieLogin.Page(status, location, Collections.emptyList());
    }
    private static MicrosoftLogin.Response response(String json) {
        return new MicrosoftLogin.Response(200, new JsonParser().parse(json.replace('\'', '"')).getAsJsonObject());
    }
    private static Map<String, String> query(String text) throws IOException {
        Map<String, String> result = new HashMap<>();
        for (String item : text.split("&")) {
            String[] pair = item.split("=", 2);
            result.put(URLDecoder.decode(pair[0], "UTF-8"), pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : "");
        }
        return result;
    }
    private static String sha256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
