package dev.vibe.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/** A pasted Minecraft Services access token (a JWT, usually starting with "eyJra"). Minecraft verifies it; locally only the expiry is read. */
public final class MinecraftToken {
    static final int MAX_LENGTH = 16384;
    private static final Pattern FORMAT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern BEARER = Pattern.compile("(?i)^bearer\\s+");
    private final String value;
    private final long expiresAt;

    private MinecraftToken(String value, long expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }

    /** Accepts the bare token, a quoted token or a copied "Bearer ..." value; returns null for anything else. */
    public static MinecraftToken parse(String input) {
        if (input == null || input.length() > MAX_LENGTH * 2) return null;
        String value = input.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1).trim();
        value = BEARER.matcher(value).replaceFirst("");
        // Tokens never contain whitespace; line breaks come from wrapped copies.
        value = value.replaceAll("\\s+", "");
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) return null;
        return new MinecraftToken(value, expiry(value));
    }

    private static long expiry(String value) {
        try {
            String payload = value.substring(value.indexOf('.') + 1, value.lastIndexOf('.'));
            JsonElement root = new JsonParser().parse(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return 0;
            JsonObject claims = root.getAsJsonObject();
            if (!claims.has("exp") || !claims.get("exp").isJsonPrimitive()) return 0;
            long seconds = claims.get("exp").getAsLong();
            return seconds > 0 && seconds < Long.MAX_VALUE / 1000 ? seconds * 1000L : 0;
        } catch (RuntimeException e) {
            // Unreadable claims do not make the token invalid; Minecraft decides.
            return 0;
        }
    }

    /** Expiry in epoch milliseconds, or 0 if the token does not state one. */
    public long expiresAt() { return expiresAt; }
    public boolean isExpired(long now) { return expiresAt > 0 && now >= expiresAt; }
    /** Shows the token header only; the claims and signature stay hidden. */
    public String preview() { return value.substring(0, Math.min(12, value.length())) + "... (" + value.length() + " characters)"; }
    String value() { return value; }
}
