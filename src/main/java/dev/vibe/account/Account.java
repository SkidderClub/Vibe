package dev.vibe.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Immutable account record. Credentials must only be written through AccountStore. */
public final class Account {
    private final String name;
    private final UUID uuid;
    private final String refreshToken;
    private final MicrosoftApplication application;
    private final MinecraftToken token;

    public Account(String name, UUID uuid, String refreshToken) {
        this(name, uuid, refreshToken, MicrosoftApplication.IAS);
    }

    Account(String name, UUID uuid, String refreshToken, MicrosoftApplication application) {
        this(name, uuid, refreshToken, application, null);
    }

    private Account(String name, UUID uuid, String refreshToken, MicrosoftApplication application, MinecraftToken token) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}") || uuid == null
                || refreshToken == null || refreshToken.length() > 32768 || application == null
                || (token != null && !refreshToken.isEmpty())) {
            throw new IllegalArgumentException("Invalid account record.");
        }
        this.name = name;
        this.uuid = uuid;
        this.refreshToken = refreshToken;
        this.application = application;
        this.token = token;
    }

    /** A pasted Minecraft access token: usable until it expires, as there is nothing to refresh it with. */
    static Account token(String name, UUID uuid, String accessToken) {
        MinecraftToken token = MinecraftToken.parse(accessToken);
        if (token == null) throw new IllegalArgumentException("Invalid account record.");
        return new Account(name, uuid, "", MicrosoftApplication.IAS, token);
    }

    public static Account offline(String name) {
        String value = name == null ? "" : name.trim();
        return new Account(value, UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + value).getBytes(StandardCharsets.UTF_8)), "");
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public boolean isMicrosoft() { return !refreshToken.isEmpty(); }
    public boolean isToken() { return token != null; }
    /** Microsoft and access-token accounts can join online-mode servers. */
    public boolean isOnline() { return isMicrosoft() || isToken(); }
    /** Access-token expiry in epoch milliseconds, or 0 if unknown or not a token account. */
    public long getTokenExpiry() { return token == null ? 0 : token.expiresAt(); }
    String refreshToken() { return refreshToken; }
    MicrosoftApplication application() { return application; }
    MinecraftToken token() { return token; }

    public boolean sameIdentity(Account other) {
        return other != null && uuid.equals(other.uuid) && isMicrosoft() == other.isMicrosoft() && isToken() == other.isToken();
    }
}
