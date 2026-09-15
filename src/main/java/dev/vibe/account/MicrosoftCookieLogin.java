package dev.vibe.account;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/** Silent OAuth with a user-selected cookie export and a private, short-lived cookie jar. */
final class MicrosoftCookieLogin {
    // Minecraft's desktop callback; consume the code without requesting the callback page.
    static final String REDIRECT = "https://login.live.com/oauth20_desktop.srf";
    private final Transport transport;

    MicrosoftCookieLogin() { this(MicrosoftCookieLogin::request); }
    MicrosoftCookieLogin(Transport transport) { this.transport = transport; }

    Authorization authorize(MicrosoftCookies cookies) throws IOException {
        String state = random();
        String verifier = random();
        String challenge;
        try {
            challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) { throw new IOException("Secure Microsoft sign-in is unavailable."); }
        MicrosoftApplication application = MicrosoftApplication.MINECRAFT;
        URI uri = URI.create("https://login.live.com/oauth20_authorize.srf?" + MicrosoftLogin.form(
                "client_id", application.clientId, "response_type", "code", "redirect_uri", REDIRECT,
                "scope", application.scope, "response_mode", "query", "prompt", "none", "state", state,
                "code_challenge", challenge, "code_challenge_method", "S256"));
        try {
            for (int redirects = 0; redirects < 12; redirects++) {
                checkCancelled();
                if (isCallback(uri)) return callback(uri, state, verifier);
                if (!MicrosoftCookies.trusted(uri)) throw new IOException("Unsupported Microsoft sign-in redirect. Use Microsoft login.");
                Page page;
                try { page = transport.get(uri, cookies.header(uri)); }
                catch (java.net.SocketTimeoutException e) { throw new IOException("Cookie sign-in timed out. Check your connection and try again."); }
                catch (IOException e) { throw new IOException("Cannot connect to Microsoft for cookie sign-in. Check your connection and Java runtime."); }
                checkCancelled();
                cookies.receive(uri, page.cookies);
                if (page.status == 429) throw new IOException("Microsoft is rate limiting sign-ins. Try again later.");
                if (page.status >= 500) throw new IOException("Microsoft sign-in is temporarily unavailable. Try again later.");
                if (!(page.status == 301 || page.status == 302 || page.status == 303 || page.status == 307 || page.status == 308)
                        || page.location == null) {
                    throw new IOException("Cookies expired or Microsoft needs confirmation. Export again or use Microsoft login.");
                }
                if (page.location.length() > 65536) throw new IOException("Microsoft returned an invalid sign-in redirect.");
                uri = uri.resolve(page.location);
            }
            throw new IOException("Too many Microsoft sign-in redirects. Use Microsoft login.");
        } catch (IllegalArgumentException e) {
            // URI/parser exceptions can include codes or cookie-bearing URLs.
            throw new IOException("Microsoft returned an invalid sign-in redirect.");
        }
    }

    private static boolean isCallback(URI uri) {
        URI callback = URI.create(REDIRECT);
        return callback.getScheme().equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && callback.getHost().equalsIgnoreCase(uri.getHost()) && callback.getPort() == uri.getPort()
                && callback.getRawPath().equals(uri.getRawPath())
                && (uri.getRawFragment() == null || uri.getRawFragment().isEmpty());
    }

    private static Authorization callback(URI uri, String state, String verifier) throws IOException {
        Map<String, String> query = new HashMap<>();
        if (uri.getRawQuery() != null) for (String item : uri.getRawQuery().split("&")) {
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], "UTF-8");
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : "";
            if (query.put(key, value) != null) throw new IOException("Microsoft returned an invalid sign-in response.");
        }
        if (!state.equals(query.get("state"))) throw new IOException("Microsoft sign-in response did not match this login. Please try again.");
        String error = query.get("error");
        if (error != null) {
            if ("login_required".equals(error)) throw new InvalidCookiesException("Microsoft session cookies expired or were revoked. Export again or use Microsoft login.");
            if ("consent_required".equals(error))
                throw new IOException("Microsoft needs consent for Minecraft. Complete sign-in in the Minecraft launcher and export again.");
            if ("interaction_required".equals(error))
                throw new IOException("Microsoft requires an interactive sign-in. Use Microsoft login.");
            if ("access_denied".equals(error))
                throw new IOException("Microsoft sign-in was declined. Use Microsoft login to try again.");
            throw new IOException("Microsoft declined cookie sign-in. Use Microsoft login.");
        }
        String code = query.get("code");
        if (code == null || code.isEmpty() || code.length() > 32768) throw new IOException("Microsoft returned an incomplete sign-in response.");
        return new Authorization(code, verifier);
    }

    private static String random() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void checkCancelled() { if (Thread.currentThread().isInterrupted()) throw new CancellationException(); }

    private static Page request(URI uri, String cookies) throws IOException {
        checkCancelled();
        if (!MicrosoftCookies.trusted(uri)) throw new IOException("Unsupported Microsoft sign-in address.");
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", "text/html");
        if (!cookies.isEmpty()) request.setHeader("Cookie", cookies);
        // Minecraft already bundles Apache HttpClient. Unlike URLConnection, this client
        // does not read or update a JVM-global CookieHandler installed by another mod.
        try (CloseableHttpClient client = HttpClients.custom().disableCookieManagement().disableRedirectHandling()
                .disableAutomaticRetries().setUserAgent("Vibe-Account-Manager/1.0")
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(15000)
                        .setSocketTimeout(15000).setConnectionRequestTimeout(15000).build()).build();
             CloseableHttpResponse response = client.execute(request)) {
            List<String> received = new ArrayList<>();
            for (Header header : response.getHeaders("Set-Cookie")) received.add(header.getValue());
            Header location = response.getFirstHeader("Location");
            // HTML/JavaScript pages require interactive login. Do not scrape or submit their forms.
            return new Page(response.getStatusLine().getStatusCode(), location == null ? null : location.getValue(), received);
        } finally { request.abort(); }
    }

    interface Transport { Page get(URI uri, String cookieHeader) throws IOException; }
    static final class Page {
        final int status;
        final String location;
        final List<String> cookies;
        Page(int status, String location, List<String> cookies) {
            this.status = status; this.location = location; this.cookies = cookies;
        }
    }
    static final class Authorization {
        final String code, verifier;
        Authorization(String code, String verifier) { this.code = code; this.verifier = verifier; }
    }
}
