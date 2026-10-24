package dev.vibe.launcher.versions;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.core.Version;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The published Vibe versions from {@code VibeUpdate/VibeReleases}.
 *
 * <p>Releases are read from GitHub's API and mirrored to a cache file, so the
 * version list stays usable when GitHub is unreachable or rate limited. Only
 * non-draft releases are listed; pre-releases are included and marked.</p>
 */
public final class ReleaseCatalog {
    /** The Vibe JAR of one release, with its checksum when GitHub publishes one. */
    public static final class Asset {
        public final String name, url, sha256, sumUrl;
        public final long size;

        Asset(String name, String url, String sha256, String sumUrl, long size) {
            this.name = name;
            this.url = url;
            this.sha256 = sha256;
            this.sumUrl = sumUrl;
            this.size = size;
        }
    }

    public static final class Release {
        public final String tag, name, body, url;
        public final long published;
        /** The JAR asset, or {@code null} when the release has no download. */
        public final Asset asset;
        public final boolean prerelease;

        Release(String tag, String name, String body, String url, long published, Asset asset, boolean prerelease) {
            this.tag = tag;
            this.name = name;
            this.body = body;
            this.url = url;
            this.published = published;
            this.asset = asset;
            this.prerelease = prerelease;
        }

        /** "v0.0.6" → "0.0.6", used for display and sorting. */
        public String version() { return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag; }

        /** A short label such as "Vibe 0.0.6"; falls back to the tag. */
        public String title() { return Text.blank(name) ? tag : name; }
    }

    /** A release list plus a warning when it came from the cache instead of GitHub. */
    public static final class Snapshot {
        public final List<Release> releases;
        public final String warning;

        Snapshot(List<Release> releases, String warning) {
            this.releases = Collections.unmodifiableList(releases);
            this.warning = warning;
        }
    }

    private static final String API = "https://api.github.com/repos/" + VibeLauncher.RELEASES_OWNER + "/" + VibeLauncher.RELEASES_REPOSITORY + "/releases";

    private final Path cache;
    private final AppLog log;

    public ReleaseCatalog(Path cache, AppLog log) {
        this.cache = cache;
        this.log = log;
    }

    /** Reads GitHub, falling back to the cached list; throws when nothing can be shown. */
    public Snapshot fetch() throws IOException {
        IOException failure;
        try {
            String json = Http.getText(API + "?per_page=100", 8 * 1024 * 1024);
            List<Release> releases = parse(json);
            if (releases.isEmpty()) {
                throw new LauncherException(ErrorCode.UNEXPECTED_RESPONSE, I18n.t("GitHub did not return any Vibe releases."));
            }
            try {
                FileUtil.writeText(cache, json);
            } catch (IOException error) {
                log.warn("Could not cache the release list", error);
            }
            return new Snapshot(releases, null);
        } catch (IOException error) {
            failure = error;
        }
        List<Release> cached = readCache();
        if (!cached.isEmpty()) {
            log.warn("Showing the cached Vibe release list: " + Text.describe(failure));
            return new Snapshot(cached, Text.describe(failure));
        }
        throw failure;
    }

    private List<Release> readCache() {
        if (!Files.isRegularFile(cache)) return Collections.emptyList();
        try {
            return parse(new String(Files.readAllBytes(cache), StandardCharsets.UTF_8));
        } catch (IOException error) {
            log.warn("The cached release list is unreadable", error);
            return Collections.emptyList();
        }
    }

    /** Parses a GitHub releases document; drafts are dropped and newest versions come first. */
    static List<Release> parse(String json) throws IOException {
        List<Release> releases = new ArrayList<Release>();
        for (Object item : Json.array(Json.parse(json))) {
            Map<String, Object> entry = Json.object(item);
            if (Json.bool(entry, "draft")) continue;
            String tag = Json.string(entry, "tag_name").trim();
            if (tag.isEmpty()) continue;
            List<Asset> assets = new ArrayList<Asset>();
            String sumName = null;
            for (Object assetItem : Json.array(entry, "assets")) {
                Map<String, Object> asset = Json.object(assetItem);
                String name = Json.string(asset, "name");
                if (name.isEmpty()) continue;
                String url = Json.string(asset, "browser_download_url");
                String digest = Json.string(asset, "digest");
                if (name.endsWith(".sha256")) { sumName = url; continue; }
                if (!isVibeJar(name)) continue;
                assets.add(new Asset(name, url, sha256Of(digest), null, Json.number(asset, "size", 0)));
            }
            Asset jar = pick(tag, assets, sumName);
            releases.add(new Release(tag, Json.string(entry, "name"), Json.string(entry, "body"),
                    Json.string(entry, "html_url"), parseTime(Json.string(entry, "published_at")),
                    jar, Json.bool(entry, "prerelease")));
        }
        Collections.sort(releases, new Comparator<Release>() {
            @Override public int compare(Release left, Release right) {
                int order = Version.compare(right.version(), left.version());
                if (order != 0) return order;
                return Long.compare(right.published, left.published);
            }
        });
        return releases;
    }

    private static boolean isVibeJar(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar") || !lower.startsWith("vibe-1.8.9-")) return false;
        return !lower.contains("sources") && !lower.contains("javadoc");
    }

    /** Prefers the JAR named after the tag, then any Vibe 1.8.9 JAR. */
    private static Asset pick(String tag, List<Asset> assets, String sumUrl) {
        if (assets.isEmpty()) return null;
        String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        String preferred = "Vibe-1.8.9-" + version + ".jar";
        Asset chosen = null;
        for (Asset asset : assets) if (asset.name.equalsIgnoreCase(preferred)) chosen = asset;
        if (chosen == null) for (Asset asset : assets) if (asset.name.equalsIgnoreCase("Vibe-1.8.9-" + tag + ".jar")) chosen = asset;
        if (chosen == null) chosen = assets.get(0);
        if (sumUrl == null || !chosen.sha256.isEmpty()) return chosen;
        return new Asset(chosen.name, chosen.url, chosen.sha256, sumUrl, chosen.size);
    }

    /** "sha256:abc…" → "abc…"; anything else is no checksum. */
    private static String sha256Of(String digest) {
        if (digest == null) return "";
        String value = digest.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("sha256:")) value = value.substring(7);
        return value.matches("[0-9a-f]{64}") ? value : "";
    }

    /** GitHub timestamps are {@code 2026-01-31T12:00:00Z}; unknown times sort oldest. */
    public static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try { return format.parse(iso).getTime(); } catch (ParseException ignored) { return 0; }
    }
}
