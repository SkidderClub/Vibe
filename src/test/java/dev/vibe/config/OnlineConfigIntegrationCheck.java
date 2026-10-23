package dev.vibe.config;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Opt-in real Java 8 HTTPS check against the public config repository. */
public final class OnlineConfigIntegrationCheck {
    public static void main(String[] args) throws Exception {
        try (OnlineConfigService online = new OnlineConfigService()) {
            List<OnlineConfigService.Entry> entries = online.refresh(true).get(30, TimeUnit.SECONDS);
            if (entries.isEmpty()) throw new AssertionError("No published configs found");
            JsonObject root = online.fetch("hypixel").get(30, TimeUnit.SECONDS);
            if (!OnlineConfigService.isProfile(root)) throw new AssertionError("Published profile is invalid");
            System.out.println("GitHub catalogue OK: " + entries.size() + " config(s); hypixel fetched with " + root.getAsJsonArray("modules").size() + " module records.");
        }
    }
}
