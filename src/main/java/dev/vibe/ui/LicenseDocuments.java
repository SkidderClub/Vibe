package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads the same legal documents that accompany the distributed JAR. */
public final class LicenseDocuments {
    public static final String REPOSITORY = "https://codeberg.org/SkidderClub/Vibe";

    public enum Page {
        CREDITS("Credits", null), NOTICES("Notices", "LICENSES/THIRD_PARTY_NOTICES.md"),
        GPL("GPLv3", "LICENSES/GPL-3.0.txt"), AGPL("AGPLv3", "LICENSES/AGPL-3.0.txt"),
        LGPL("LGPLv3", "LICENSES/LGPL-3.0.txt"), MIT("MIT", "LICENSES/webgl-noise-MIT.txt"),
        SCHIZOID("Schizoid", "LICENSES/SCHIZOID.md"), SOURCE("Source", null);

        public final String label;
        public final String path;
        Page(String label, String path) { this.label = label; this.path = path; }
    }

    private LicenseDocuments() { }

    public static String sourceArchive() { return "Vibe-1.8.9-" + Vibe.VERSION + "-sources.zip"; }

    public static String read(Page page) throws IOException {
        if (page == Page.CREDITS) return credits();
        if (page == Page.SOURCE) return source();
        InputStream stream = LicenseDocuments.class.getResourceAsStream("/META-INF/vibe/" + page.path);
        if (stream == null) throw new IOException("Missing bundled legal document: " + page.path);
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        }
        return text.toString();
    }

    private static String credits() {
        return "Vibe / GPLv3\n" + tr("Vibe combines GPLv3 code with AGPLv3 components. Each component keeps its license.")
                + "\n\nSchizoid / AGPLv3\n" + tr("Fog, Torus hit effects and the media HUD are adapted from Schizoid.")
                + "\nCopyright (c) 2023, 2024 Schizoid\nhttps://github.com/SchizoidDevelopment/schizoid"
                + "\n\nwebgl-noise / MIT\nIan McEwan, Stefan Gustavson / Ashima Arts\n" + tr("Simplex noise used by the visual effects.")
                + "\n\nIn-Game Account Switcher / LGPLv3+\nThe_Fireplace, VidTu and contributors\n" + tr("Device-login reference and OAuth application; no library bundled.")
                + "\n\nMinecraftAuth / LGPLv3+\nRK_01/RaphiMC and contributors\n" + tr("Authentication protocol and application configuration reference; no library bundled.")
                + "\n\nLiquidBounce / GPLv3+\nCCBlueX and contributors\n" + tr("Login configuration reference. Shader provenance details are in Notices.")
                + "\n\nAugustus Client\n" + tr("Vibe's Augustus ClickGUI theme is inspired by the Augustus Client.")
                + "\nhttps://electriclauncher.de/\nhttps://discord.electriclauncher.de"
                + "\n\n" + tr("Other libraries and assets keep their own terms. See Notices for the full inventory.")
                + "\n\n" + tr("You may use, modify and share covered code under its license terms. No warranty is provided; see the full licenses.");
    }

    private static String source() {
        return tr("Matching source for this build") + "\n\n" + sourceArchive()
                + "\n\n" + tr("Obtain this archive alongside the JAR from its distributor. A repository checkout may differ from your build.")
                + "\n\n" + tr("Project repository") + "\n" + REPOSITORY
                + "\n\n" + tr("When sharing a build, provide its matching source and preserve the license and copyright notices.")
                + "\n\n" + tr("GPLv3 and AGPLv3 permit this combination. AGPL network-source duties apply when their conditions are met.")
                + "\n\n" + tr("The license texts and credits are available here without an internet connection.");
    }

    private static String tr(String text) { return LanguageManager.translate(text); }
}
