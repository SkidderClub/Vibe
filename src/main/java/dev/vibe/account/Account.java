package dev.vibe.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Immutable account record. Credentials must only be written through AccountStore. */
public final class Account {
    private final String name;
    private final UUID uuid;
    private final String refreshToken;
    private final MicrosoftApplication application;

    public Account(String name, UUID uuid, String refreshToken) {
        this(name, uuid, refreshToken, MicrosoftApplication.IAS);
    }

    Account(String name, UUID uuid, String refreshToken, MicrosoftApplication application) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}") || uuid == null
                || refreshToken == null || refreshToken.length() > 32768 || application == null) {
            throw new IllegalArgumentException("Invalid account record.");
        }
        this.name = name;
        this.uuid = uuid;
        this.refreshToken = refreshToken;
        this.application = application;
    }

    public static Account offline(String name) {
        String value = name == null ? "" : name.trim();
        return new Account(value, UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + value).getBytes(StandardCharsets.UTF_8)), "");
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public boolean isMicrosoft() { return !refreshToken.isEmpty(); }
    String refreshToken() { return refreshToken; }
    MicrosoftApplication application() { return application; }

    public boolean sameIdentity(Account other) {
        return other != null && uuid.equals(other.uuid) && isMicrosoft() == other.isMicrosoft();
    }
}
