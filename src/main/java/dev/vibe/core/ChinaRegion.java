package dev.vibe.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;

/**
 * Best-effort mainland China detection, used by VibeOS to pick a mainland
 * ringtone. A country-code IP lookup runs once on a daemon thread; when it is
 * unreachable the system locale and time zone act as a local hint so a
 * Chinese player still hears the mainland clip offline.
 */
public final class ChinaRegion {

    private static final String IP9_ENDPOINT = "https://ip9.com.cn/get";
    private static final String IPINFO_ENDPOINT = "https://ipinfo.io/country";
    /** Province-level names that only exist in mainland China. */
    private static final List<String> MAINLAND_PROVINCES = Arrays.asList(
            "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江", "上海", "江苏",
            "浙江", "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "广西",
            "海南", "重庆", "四川", "贵州", "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆");
    private static final long RETRY_AFTER_MS = 10 * 60 * 1000L;
    private static final AtomicBoolean LOOKING_UP = new AtomicBoolean();
    private static volatile Boolean mainland;
    private static volatile long checkedAt;

    private ChinaRegion() {
    }

    /** Warms the lookup at startup so a phone call never waits on the network. */
    public static void warmUp() {
        if (override() != null || mainland != null) return;
        lookup();
    }

    public static boolean mainlandChina() {
        Boolean forced = override();
        if (forced != null) return forced;
        Boolean known = mainland;
        if (known != null) return known;
        lookup();
        return localHint();
    }

    private static void lookup() {
        if (mainland != null || System.currentTimeMillis() - checkedAt < RETRY_AFTER_MS
                || !LOOKING_UP.compareAndSet(false, true)) return;
        Thread worker = new Thread(ChinaRegion::resolve, "Vibe-Region");
        worker.setDaemon(true);
        worker.start();
    }

    private static void resolve() {
        try {
            // ip9.com.cn reports the Chinese province directly, which also
            // excludes Hong Kong, Macau and Taiwan; ipinfo.io is the backup.
            Boolean detected = query(IP9_ENDPOINT, ChinaRegion::isMainlandProvince);
            if (detected == null) detected = query(IPINFO_ENDPOINT, ChinaRegion::isChinaCountryCode);
            if (detected == null) {
                LogManager.getLogger("Vibe").info("Region lookup unavailable; using locale/time zone hints.");
            } else {
                mainland = detected;
                LogManager.getLogger("Vibe").info("Region lookup: {}", detected ? "mainland China" : "other");
            }
        } finally {
            checkedAt = System.currentTimeMillis();
            LOOKING_UP.set(false);
        }
    }

    /** Returns {@code null} when the endpoint could not be reached or parsed. */
    private static Boolean query(String address, Predicate<String> parser) {
        try {
            return parser.test(download(address));
        } catch (IOException | RuntimeException unreachable) {
            return null;
        }
    }

    private static String download(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(4_000);
        connection.setReadTimeout(4_000);
        connection.setRequestProperty("User-Agent", "Vibe-Region");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Region request failed (HTTP " + status + ").");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[512];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    if (output.size() > 4096) break;
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    static boolean isMainlandProvince(String response) {
        JsonObject root = new JsonParser().parse(response).getAsJsonObject();
        if (!root.has("data") || !root.get("data").isJsonObject()) return false;
        JsonObject data = root.getAsJsonObject("data");
        String province = text(data, "prov");
        for (String name : MAINLAND_PROVINCES) if (province.contains(name)) return true;
        return false;
    }

    static boolean isChinaCountryCode(String response) {
        return response != null && response.trim().equalsIgnoreCase("CN");
    }

    private static String text(JsonObject object, String member) {
        return object.has(member) && object.get(member).isJsonPrimitive()
                ? object.get(member).getAsString().trim() : "";
    }

    private static boolean localHint() {
        TimeZone zone = TimeZone.getDefault();
        String id = zone == null ? "" : zone.getID().toLowerCase(Locale.ROOT);
        if (id.equals("asia/shanghai") || id.equals("asia/chongqing") || id.equals("asia/harbin")
                || id.equals("asia/urumqi") || id.equals("prc")) return true;
        return "CN".equalsIgnoreCase(Locale.getDefault().getCountry());
    }

    /** {@code -Dvibe.region=cn|other} overrides detection for testing. */
    private static Boolean override() {
        String value = System.getProperty("vibe.region", "").trim().toLowerCase(Locale.ROOT);
        if (value.equals("cn") || value.equals("china") || value.equals("mainland")) return Boolean.TRUE;
        if (value.equals("other") || value.equals("global")) return Boolean.FALSE;
        return null;
    }
}
