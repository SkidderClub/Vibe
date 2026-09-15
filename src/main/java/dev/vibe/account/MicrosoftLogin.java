package dev.vibe.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Java 8 Microsoft device and Minecraft cookie authorization. */
public final class MicrosoftLogin {
    // Public application identifier, not a client secret. Consent identifies In-Game Account Switcher.
    static final String CLIENT_ID = MicrosoftApplication.IAS.clientId;
    static final String MS_BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    static final String SCOPE = MicrosoftApplication.IAS.scope;
    private final Transport transport;
    private final MicrosoftCookieLogin cookieLogin;

    public MicrosoftLogin() { this(MicrosoftLogin::request); }
    MicrosoftLogin(Transport transport) { this(transport, new MicrosoftCookieLogin()); }
    MicrosoftLogin(Transport transport, MicrosoftCookieLogin cookieLogin) {
        this.transport = transport;
        this.cookieLogin = cookieLogin;
    }

    public Result fromCookies(Path source, Consumer<String> progress) throws IOException {
        checkCancelled();
        progress.accept("Reading Microsoft session cookies...");
        try (MicrosoftCookies cookies = MicrosoftCookies.read(source)) {
            return fromCookies(cookies, progress);
        }
    }

    Result fromCookies(MicrosoftCookies cookies, Consumer<String> progress) throws IOException {
        checkCancelled();
        progress.accept("Signing in with Microsoft session cookies...");
        MicrosoftCookieLogin.Authorization authorization = cookieLogin.authorize(cookies);
        checkCancelled();
        progress.accept("Completing Microsoft sign-in...");
        MicrosoftApplication application = MicrosoftApplication.MINECRAFT;
        JsonObject tokens = checked(transport.send(application.tokenEndpoint, "application/x-www-form-urlencoded",
                form("client_id", application.clientId, "grant_type", "authorization_code", "code", authorization.code,
                        "redirect_uri", MicrosoftCookieLogin.REDIRECT, "scope", application.scope,
                        "code_verifier", authorization.verifier), null), "Microsoft");
        return minecraft(string(tokens, "access_token"), string(tokens, "refresh_token"), application, progress);
    }

    public DeviceCode begin() throws IOException {
        JsonObject response = checked(transport.send(MS_BASE + "devicecode", "application/x-www-form-urlencoded",
                form("client_id", CLIENT_ID, "scope", SCOPE), null), "Microsoft");
        try {
            URI uri = URI.create(string(response, "verification_uri"));
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || !("microsoft.com".equals(uri.getHost()) || "www.microsoft.com".equals(uri.getHost())
                    || "login.microsoftonline.com".equals(uri.getHost()))) {
                throw new IOException("Microsoft returned an invalid sign-in address.");
            }
            long seconds = Math.max(1, Math.min(1800, response.get("expires_in").getAsLong()));
            int interval = response.has("interval") ? response.get("interval").getAsInt() : 5;
            return new DeviceCode(string(response, "device_code"), string(response, "user_code"), uri,
                    System.currentTimeMillis() + seconds * 1000L, Math.max(1, Math.min(60, interval)));
        } catch (RuntimeException e) {
            throw new IOException("Microsoft returned an invalid sign-in response.");
        }
    }

    public Result await(DeviceCode code, Consumer<String> progress) throws IOException, InterruptedException {
        int interval = code.interval;
        while (System.currentTimeMillis() < code.expiresAt) {
            Thread.sleep(interval * 1000L);
            checkCancelled();
            if (System.currentTimeMillis() >= code.expiresAt) break;
            Response response = transport.send(MS_BASE + "token", "application/x-www-form-urlencoded",
                    form("client_id", CLIENT_ID, "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                            "device_code", code.deviceCode), null);
            String error = optional(response.body, "error");
            if (response.status == 400 && "authorization_pending".equals(error)) continue;
            if ((response.status == 400 || response.status == 429) && "slow_down".equals(error)) {
                interval = Math.min(120, interval + 5);
                continue;
            }
            JsonObject tokens = checked(response, "Microsoft");
            return minecraft(string(tokens, "access_token"), string(tokens, "refresh_token"), MicrosoftApplication.IAS, progress);
        }
        throw new IOException("Sign-in code expired. Start Microsoft sign-in again.");
    }

    public Result refresh(Account account, Consumer<Account> rotated, Consumer<String> progress) throws IOException {
        checkCancelled();
        progress.accept("Refreshing Microsoft sign-in...");
        MicrosoftApplication application = account.application();
        JsonObject tokens = checked(transport.send(application.tokenEndpoint, "application/x-www-form-urlencoded",
                form("client_id", application.clientId, "grant_type", "refresh_token", "refresh_token", account.refreshToken(),
                        "scope", application.scope), null), "Microsoft");
        String refresh = optional(tokens, "refresh_token");
        if (refresh.isEmpty()) refresh = account.refreshToken();
        // Persist rotation before the Xbox/Minecraft requests, which may fail independently.
        rotated.accept(new Account(account.getName(), account.getUuid(), refresh, application));
        Result result = minecraft(string(tokens, "access_token"), refresh, application, progress);
        if (!result.account.getUuid().equals(account.getUuid())) {
            throw new IOException("The signed-in Minecraft account does not match the saved account.");
        }
        return result;
    }

    private Result minecraft(String msToken, String refresh, MicrosoftApplication application, Consumer<String> progress) throws IOException {
        checkCancelled();
        progress.accept("Signing in to Xbox Live...");
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", application.rpsPrefix + msToken);
        JsonObject xbl = checked(transport.send("https://user.auth.xboxlive.com/user/authenticate", "application/json",
                xboxRequest(properties, "http://auth.xboxlive.com").toString(), null), "Xbox Live");
        String hash = userHash(xbl);
        checkCancelled();
        JsonArray tokens = new JsonArray();
        tokens.add(new JsonPrimitive(string(xbl, "Token")));
        properties = new JsonObject();
        properties.add("UserTokens", tokens);
        properties.addProperty("SandboxId", "RETAIL");
        JsonObject xsts = checked(transport.send("https://xsts.auth.xboxlive.com/xsts/authorize", "application/json",
                xboxRequest(properties, "rp://api.minecraftservices.com/").toString(), null), "Xbox authorization");
        if (!hash.equals(userHash(xsts))) throw new IOException("Xbox returned mismatching account identities.");
        checkCancelled();
        progress.accept("Checking Minecraft Java profile...");
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", "XBL3.0 x=" + hash + ";" + string(xsts, "Token"));
        JsonObject minecraft = checked(transport.send("https://api.minecraftservices.com/authentication/login_with_xbox",
                "application/json", payload.toString(), null), "Minecraft");
        String access = string(minecraft, "access_token");
        checkCancelled();
        JsonObject profile = checked(transport.send("https://api.minecraftservices.com/minecraft/profile", null, null, access),
                "Minecraft profile");
        String id = string(profile, "id");
        if (!id.matches("[a-fA-F0-9]{32}")) throw new IOException("Minecraft returned an invalid profile ID.");
        UUID uuid = UUID.fromString(id.replaceFirst("(........)(....)(....)(....)(............)", "$1-$2-$3-$4-$5"));
        return new Result(new Account(string(profile, "name"), uuid, refresh, application), access);
    }

    private static JsonObject xboxRequest(JsonObject properties, String relyingParty) {
        JsonObject request = new JsonObject();
        request.add("Properties", properties);
        request.addProperty("RelyingParty", relyingParty);
        request.addProperty("TokenType", "JWT");
        return request;
    }

    private static String userHash(JsonObject response) throws IOException {
        try {
            return string(response.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(), "uhs");
        } catch (RuntimeException e) {
            throw new IOException("Xbox returned an invalid account identity.");
        }
    }

    static JsonObject checked(Response response, String service) throws IOException {
        if (response.status >= 200 && response.status < 300) return response.body;
        String error = optional(response.body, "error");
        if ("authorization_declined".equals(error) || "access_denied".equals(error))
            throw new IOException("Microsoft sign-in was declined.");
        if ("expired_token".equals(error)) throw new IOException("Sign-in code expired. Please try again.");
        if ("invalid_grant".equals(error)) throw new IOException("Saved sign-in expired or was revoked. Add this Microsoft account again.");
        String xerr = optional(response.body, "XErr");
        if ("2148916233".equals(xerr)) throw new IOException("Create an Xbox profile at xbox.com, then try again.");
        if ("2148916235".equals(xerr)) throw new IOException("Xbox Live is unavailable for this account's region.");
        if ("2148916236".equals(xerr) || "2148916237".equals(xerr) || "2148916238".equals(xerr))
            throw new IOException("Complete the age/family settings for this account at xbox.com.");
        if (response.status == 404 && "Minecraft profile".equals(service))
            throw new IOException("No Minecraft Java profile found. Check ownership and create a Java profile.");
        if (response.status == 429) throw new IOException(service + " is rate limiting sign-ins. Try again later.");
        if (response.status == 403 && "Minecraft".equals(service))
            throw new IOException("Minecraft rejected the sign-in application or account (HTTP 403).");
        throw new IOException(service + " sign-in failed (HTTP " + response.status + "). Please try again.");
    }

    private static String string(JsonObject object, String name) throws IOException {
        String value = optional(object, name);
        if (value.isEmpty() || value.length() > 32768) throw new IOException("Incomplete sign-in response.");
        return value;
    }

    private static String optional(JsonObject object, String name) {
        return object != null && object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : "";
    }

    static String form(String... parts) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i += 2) {
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(parts[i], "UTF-8")).append('=')
                    .append(URLEncoder.encode(parts[i + 1], "UTF-8"));
        }
        return result.toString();
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static Response request(String endpoint, String contentType, String body, String bearer) throws IOException {
        checkCancelled();
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Vibe-Account-Manager/1.0");
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", contentType);
                connection.setDoOutput(true);
                byte[] data = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(data); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) return new Response(status, new JsonObject());
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    checkCancelled();
                    if (out.size() + count > 1024 * 1024) throw new IOException("Sign-in response is too large.");
                    out.write(buffer, 0, count);
                }
                try {
                    return new Response(status, new JsonParser().parse(new String(out.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (RuntimeException e) {
                    // Providers sometimes serve HTML during an outage. Never expose the body.
                    if (status < 200 || status >= 300) return new Response(status, new JsonObject());
                    throw new IOException("Sign-in service returned an unreadable response.");
                }
            }
        } catch (javax.net.ssl.SSLException e) {
            throw new IOException("Secure sign-in connection failed. Use an up-to-date Java 8 runtime.");
        } catch (java.net.SocketTimeoutException e) {
            throw new IOException("Sign-in connection timed out. Check your connection and try again.");
        } finally {
            connection.disconnect();
        }
    }

    interface Transport {
        Response send(String endpoint, String contentType, String body, String bearer) throws IOException;
    }

    static final class Response {
        final int status;
        final JsonObject body;
        Response(int status, JsonObject body) { this.status = status; this.body = body; }
    }

    public static final class DeviceCode {
        private final String deviceCode;
        public final String userCode;
        public final URI verificationUri;
        public final long expiresAt;
        private final int interval;
        DeviceCode(String deviceCode, String userCode, URI verificationUri, long expiresAt, int interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresAt = expiresAt;
            this.interval = interval;
        }
    }

    public static final class Result {
        public final Account account;
        private final String accessToken;
        Result(Account account, String accessToken) { this.account = account; this.accessToken = accessToken; }
        String accessToken() { return accessToken; }
    }
}
