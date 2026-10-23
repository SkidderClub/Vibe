package dev.vibe.config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineConfigServiceTest {
    private static final String SHA = "1234567890123456789012345678901234567890";
    private static String tree(String entries) { return "{\"sha\":\"" + SHA + "\",\"tree\":[" + entries + "],\"truncated\":false}"; }
    private static String file(String path) { return "{\"type\":\"blob\",\"path\":\"" + path + "\",\"size\":100}"; }

    @Test public void catalogueSupportsFoldersAndSpacesAndFiltersInvalidPaths() throws Exception {
        List<OnlineConfigService.Entry> entries = OnlineConfigService.parseCatalogue(tree(file("hypixel.json") + "," + file("servers/Other config.JSON")
                + "," + file("README.md") + "," + file("../escape.json") + "," + file("/root.json") + "," + file("hypixel.JSON")));
        assertEquals(2, entries.size()); assertEquals("hypixel", entries.get(0).getName());
        assertEquals("servers/Other config", entries.get(1).getName());
    }

    @Test public void fetchUsesPinnedRevisionAndRunsOffCallingThreadAndCachesCatalogue() throws Exception {
        AtomicInteger catalogues = new AtomicInteger(); String caller = Thread.currentThread().getName();
        try (OnlineConfigService service = new OnlineConfigService(url -> {
            assertNotEquals(caller, Thread.currentThread().getName());
            if (url.contains("api.github.com")) { catalogues.incrementAndGet(); return tree(file("folder/space name.json")); }
            assertEquals("https://raw.githubusercontent.com/SkidderClub/VibeConfig/" + SHA + "/folder/space%20name.json", url);
            return "{\"modules\":[{\"id\":\"speed\",\"settings\":{\"Mode\":\"Custom\"}}]}";
        })) {
            assertEquals(1, service.refresh(false).get(5, TimeUnit.SECONDS).size());
            assertTrue(service.fetch("FOLDER/SPACE NAME.json").get(5, TimeUnit.SECONDS).has("modules"));
            assertEquals(1, catalogues.get());
            service.refresh(true).get(5, TimeUnit.SECONDS); assertEquals(2, catalogues.get());
            try { service.fetch("missing").get(5, TimeUnit.SECONDS); fail(); }
            catch (java.util.concurrent.ExecutionException expected) { assertTrue(OnlineConfigService.errorMessage(expected).contains("not found")); }
        }
    }

    @Test public void failedRefreshRetainsLastCatalogueAndCanRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (OnlineConfigService service = new OnlineConfigService(url -> {
            if (attempts.incrementAndGet() == 2) throw new IOException("Offline");
            return tree(file("hypixel.json"));
        })) {
            service.refresh(true).get(5, TimeUnit.SECONDS);
            try { service.refresh(true).get(5, TimeUnit.SECONDS); fail(); }
            catch (java.util.concurrent.ExecutionException expected) { assertEquals("Offline", OnlineConfigService.errorMessage(expected)); }
            assertEquals("hypixel", service.getEntries().get(0).getName());
            assertEquals(1, service.refresh(true).get(5, TimeUnit.SECONDS).size());
        }
    }

    @Test public void rejectsMalformedOrIncompleteCataloguesAndNonConfigJson() throws Exception {
        for (String text : new String[] {"[]", "{}", "not json", tree(file("hypixel.json")).replace("false", "true")}) {
            try { OnlineConfigService.parseCatalogue(text); fail(text); } catch (IOException expected) { }
        }
        for (String text : new String[] {"[]", "{}", "not json", "{\"modules\":[]}", "{\"modules\":[{}]}", "{\"modules\":[{\"id\":\"speed\",\"settings\":[]}]}", "{\"modules\":[{\"id\":1}]}"}) {
            try { OnlineConfigService.parseProfile(text); fail(text); } catch (IOException expected) { }
        }
    }
}
