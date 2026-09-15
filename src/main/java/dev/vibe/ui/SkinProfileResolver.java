package dev.vibe.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Public profile lookup independent of Minecraft 1.8's cached launcher properties. */
final class SkinProfileResolver {
    interface Fetcher { byte[] get(String url) throws IOException; }
    private final Fetcher fetcher;

    SkinProfileResolver() { this(SkinProfileResolver::download); }
    SkinProfileResolver(Fetcher fetcher) { this.fetcher = fetcher; }

    String resolve(UUID id, String name) throws IOException {
        try {
            // Offline sessions use name-derived UUIDs which the session server
            // cannot resolve. Look up their public Java profile by name first.
            String profileId;
            if (id == null || id.version() != 4) {
                if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return null;
                JsonObject named = json(fetcher.get("https://api.mojang.com/users/profiles/minecraft/" + name));
                if (named == null) return null;
                profileId = named.get("id").getAsString();
            } else profileId = id.toString().replace("-", "");
            if (!profileId.matches("[a-fA-F0-9]{32}")) throw new IOException("Invalid public profile ID");
            JsonObject profile = json(fetcher.get("https://sessionserver.mojang.com/session/minecraft/profile/" + profileId));
            if (profile == null) return null;
            if (!profileId.equalsIgnoreCase(profile.get("id").getAsString())) throw new IOException("Profile ID mismatch");
            if (!profile.has("properties")) return null;
            for (JsonElement value : profile.getAsJsonArray("properties")) {
                JsonObject property = value.getAsJsonObject();
                if (!"textures".equals(property.get("name").getAsString())) continue;
                JsonObject textures = json(Base64.getDecoder().decode(property.get("value").getAsString())).getAsJsonObject("textures");
                if (textures == null || !textures.has("SKIN")) return null;
                String address = textures.getAsJsonObject("SKIN").get("url").getAsString();
                // The official profile payload still advertises HTTP textures.
                // Upgrade the known texture host to HTTPS for the actual fetch.
                if (!address.matches("https?://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,64}"))
                    throw new IOException("Invalid public skin address");
                return address.replace("http://", "https://");
            }
            return null;
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid public skin response", invalid);
        }
    }

    private static JsonObject json(byte[] bytes) {
        return bytes == null ? null : new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static byte[] download(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "Vibe-SkinPreview/1.0");
        try {
            int status = connection.getResponseCode();
            if (status == 204 || status == 404) return null;
            if (status != 200) throw new IOException("Public skin request failed: " + status);
            if (connection.getContentLengthLong() > 1024 * 1024) throw new IOException("Skin response too large");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) throw new IOException("Skin response too large");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally { connection.disconnect(); }
    }
}
