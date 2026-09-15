package dev.vibe.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class MicrosoftLoginTest {
    private static final UUID PROFILE = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test public void fullDeviceFlowWaitsForConsentAndResolvesMinecraftProfile() throws Exception {
        Queue<MicrosoftLogin.Response> responses = new ArrayDeque<>();
        responses.add(response(400, "{'error':'authorization_pending'}"));
        responses.add(response(200, "{'access_token':'synthetic-ms','refresh_token':'synthetic-refresh'}"));
        responses.add(response(200, "{'Token':'synthetic-xbl','DisplayClaims':{'xui':[{'uhs':'123'}]}}"));
        responses.add(response(200, "{'Token':'synthetic-xsts','DisplayClaims':{'xui':[{'uhs':'123'}]}}"));
        responses.add(response(200, "{'access_token':'synthetic-minecraft'}"));
        responses.add(response(200, "{'id':'12345678123412341234123456789abc','name':'Example'}"));
        MicrosoftLogin login = new MicrosoftLogin((url, contentType, body, bearer) -> {
            assertTrue(url.startsWith("https://"));
            if (url.endsWith("/user/authenticate")) assertTrue(body.contains("d=synthetic-ms"));
            if (url.endsWith("/login_with_xbox")) assertTrue(body.contains("XBL3.0 x=123;synthetic-xsts"));
            if (url.endsWith("/minecraft/profile")) {
                assertNull(body);
                assertEquals("synthetic-minecraft", bearer);
            } else assertNull(bearer);
            return responses.remove();
        });
        MicrosoftLogin.Result result = login.await(new MicrosoftLogin.DeviceCode("synthetic-device", "TEST-CODE",
                URI.create("https://www.microsoft.com/link"), System.currentTimeMillis() + 10000, 0), ignored -> { });
        assertTrue(responses.isEmpty());
        assertEquals(PROFILE, result.account.getUuid());
        assertEquals("synthetic-refresh", result.account.refreshToken());
        assertEquals(MicrosoftApplication.IAS, result.account.application());
        assertEquals("synthetic-minecraft", result.accessToken());
    }

    @Test public void refreshRotationIsPreservedEvenWhenXboxFails() throws Exception {
        AtomicReference<Account> rotated = new AtomicReference<>();
        MicrosoftLogin login = new MicrosoftLogin((url, type, body, bearer) -> {
            if (url.endsWith("/token")) {
                assertTrue(body.contains("grant_type=refresh_token"));
                return response(200, "{'access_token':'synthetic-ms','refresh_token':'new-refresh'}");
            }
            assertNotNull(rotated.get());
            return response(503, "{'error':'synthetic-private-error'}");
        });
        IOException failure = assertThrows(IOException.class, () -> login.refresh(
                new Account("Example", PROFILE, "old-refresh"), rotated::set, ignored -> { }));
        assertEquals("new-refresh", rotated.get().refreshToken());
        assertFalse(failure.getMessage().contains("synthetic-private-error"));
        assertNull(failure.getCause());
    }

    @Test public void mismatchedXboxHashesStopBeforeMinecraftLogin() throws Exception {
        Queue<MicrosoftLogin.Response> responses = new ArrayDeque<>();
        responses.add(response(200, "{'access_token':'synthetic','refresh_token':'synthetic'}"));
        responses.add(response(200, "{'Token':'synthetic','DisplayClaims':{'xui':[{'uhs':'one'}]}}"));
        responses.add(response(200, "{'Token':'synthetic','DisplayClaims':{'xui':[{'uhs':'two'}]}}"));
        MicrosoftLogin login = new MicrosoftLogin((url, type, body, bearer) -> responses.remove());
        assertThrows(IOException.class, () -> login.refresh(new Account("Example", PROFILE, "synthetic"),
                ignored -> { }, ignored -> { }));
        assertTrue(responses.isEmpty());
    }

    @Test public void refreshUsesTheSavedApplicationForTokensAndXboxTickets() throws Exception {
        for (MicrosoftApplication application : MicrosoftApplication.values()) {
            Queue<MicrosoftLogin.Response> responses = new ArrayDeque<>();
            responses.add(response(200, "{'access_token':'synthetic-ms','refresh_token':'rotated-refresh'}"));
            responses.add(response(200, "{'Token':'synthetic-xbl','DisplayClaims':{'xui':[{'uhs':'123'}]}}"));
            responses.add(response(200, "{'Token':'synthetic-xsts','DisplayClaims':{'xui':[{'uhs':'123'}]}}"));
            responses.add(response(200, "{'access_token':'synthetic-minecraft'}"));
            responses.add(response(200, "{'id':'12345678123412341234123456789abc','name':'Example'}"));
            AtomicReference<Account> rotated = new AtomicReference<>();
            MicrosoftLogin login = new MicrosoftLogin((url, type, body, bearer) -> {
                if (responses.size() == 5) {
                    assertEquals(application.tokenEndpoint, url);
                    assertTrue(body.contains("client_id=" + application.clientId));
                    assertTrue(body.contains("grant_type=refresh_token"));
                    assertTrue(body.contains("refresh_token=old-refresh"));
                }
                if (url.endsWith("/user/authenticate")) {
                    assertTrue(body.contains((application == MicrosoftApplication.MINECRAFT ? "t=" : "d=") + "synthetic-ms"));
                    assertEquals(application, rotated.get().application());
                }
                return responses.remove();
            });
            MicrosoftLogin.Result result = login.refresh(new Account("Example", PROFILE, "old-refresh", application),
                    rotated::set, ignored -> { });
            assertTrue(responses.isEmpty());
            assertEquals(application, result.account.application());
            assertEquals("rotated-refresh", result.account.refreshToken());
            assertEquals(PROFILE, result.account.getUuid());
        }
    }

    @Test public void minecraftRefreshRotationRetainsItsApplicationOnXboxFailure() throws Exception {
        AtomicReference<Account> rotated = new AtomicReference<>();
        MicrosoftLogin login = new MicrosoftLogin((url, type, body, bearer) -> {
            if (url.equals("https://login.live.com/oauth20_token.srf"))
                return response(200, "{'access_token':'synthetic-ms','refresh_token':'new-refresh'}");
            return response(503, "{}");
        });
        assertThrows(IOException.class, () -> login.refresh(
                new Account("Example", PROFILE, "old-refresh", MicrosoftApplication.MINECRAFT), rotated::set, ignored -> { }));
        assertEquals(MicrosoftApplication.MINECRAFT, rotated.get().application());
        assertEquals("new-refresh", rotated.get().refreshToken());
    }

    @Test public void declinedRevokedMissingProfileAndFamilyErrorsAreActionable() {
        assertError(400, "{'error':'authorization_declined'}", "Microsoft", "declined");
        assertError(400, "{'error':'invalid_grant'}", "Microsoft", "revoked");
        assertError(404, "{}", "Minecraft profile", "No Minecraft Java profile");
        assertError(401, "{'XErr':2148916233}", "Xbox", "Create an Xbox profile");
        assertError(401, "{'XErr':2148916238}", "Xbox", "age/family");
        assertError(429, "{}", "Microsoft", "rate limiting");
    }

    @Test public void deviceCodeRejectsUntrustedBrowserDestination() {
        MicrosoftLogin login = new MicrosoftLogin((url, type, body, bearer) -> response(200,
                "{'device_code':'synthetic','user_code':'TEST','expires_in':900,'verification_uri':'https://example.invalid/'}"));
        assertThrows(IOException.class, login::begin);
    }

    @Test public void deviceCodeAcceptsMicrosoftPageAndDefaultsPollingInterval() throws Exception {
        MicrosoftLogin login = new MicrosoftLogin((url, type, body, bearer) -> response(200,
                "{'device_code':'synthetic','user_code':'TEST','expires_in':900,'verification_uri':'https://www.microsoft.com/link'}"));
        assertEquals("TEST", login.begin().userCode);
    }

    @Test public void expiredAndInterruptedLoginDoesNotSendRequests() throws Exception {
        MicrosoftLogin login = new MicrosoftLogin((url, type, body, bearer) -> { throw new AssertionError("Unexpected request"); });
        assertThrows(IOException.class, () -> login.await(new MicrosoftLogin.DeviceCode("synthetic", "TEST",
                URI.create("https://www.microsoft.com/link"), 0, 0), ignored -> { }));
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> login.refresh(new Account("Example", PROFILE, "synthetic"),
                    ignored -> { }, ignored -> { }));
        } finally { Thread.interrupted(); }
    }

    private static void assertError(int status, String json, String service, String expected) {
        IOException failure = assertThrows(IOException.class, () -> MicrosoftLogin.checked(response(status, json), service));
        assertTrue(failure.getMessage(), failure.getMessage().contains(expected));
    }

    private static MicrosoftLogin.Response response(int status, String json) {
        JsonObject body = new JsonParser().parse(json.replace('\'', '"')).getAsJsonObject();
        return new MicrosoftLogin.Response(status, body);
    }
}
