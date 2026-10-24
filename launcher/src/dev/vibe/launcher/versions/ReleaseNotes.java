package dev.vibe.launcher.versions;

import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.install.ChangelogSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns a GitHub release body into the same sections the changelog uses.
 *
 * <p>Release notes start with a version heading ({@code ## 0.0.6}) that is
 * skipped, then contain {@code ##}/{@code ###} sections and bullet points.
 * Text without headings becomes one "Changes" section.</p>
 */
public final class ReleaseNotes {
    private static final int MAX_SECTIONS = 12;
    private static final int MAX_ENTRY = 4000;
    private static final String FALLBACK_TITLE = "Changes";

    private ReleaseNotes() { }

    public static List<ChangelogSection> parse(String body) {
        if (Text.blank(body)) return Collections.emptyList();
        List<ChangelogSection> sections = new ArrayList<ChangelogSection>();
        String title = null;
        List<String> entries = new ArrayList<String>();
        StringBuilder paragraph = null;
        boolean firstLine = true;
        for (String raw : body.split("\r?\n")) {
            String line = raw.trim();
            if (line.startsWith("#")) {
                if (paragraph != null) { add(entries, paragraph); paragraph = null; }
                boolean heading = line.startsWith("## ") || line.startsWith("### ");
                if (!heading) continue;
                String headingText = ChangelogSection.plain(line.replaceFirst("^#{2,3}\\s*", ""));
                if (firstLine) {
                    // The release body repeats the version as its first heading.
                    firstLine = false;
                    continue;
                }
                firstLine = false;
                if (title != null && !entries.isEmpty()) sections.add(new ChangelogSection(title, entries));
                if (sections.size() >= MAX_SECTIONS) return Collections.unmodifiableList(sections);
                entries = new ArrayList<String>();
                if (!headingText.isEmpty()) title = headingText;
                continue;
            }
            firstLine = false;
            if (line.isEmpty()) {
                if (paragraph != null) { add(entries, paragraph); paragraph = null; }
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (paragraph != null) add(entries, paragraph);
                paragraph = new StringBuilder(line.substring(2).trim());
                continue;
            }
            if (paragraph == null) paragraph = new StringBuilder(line);
            else paragraph.append(' ').append(line);
        }
        if (paragraph != null) add(entries, paragraph);
        if (!entries.isEmpty()) {
            sections.add(new ChangelogSection(title == null ? FALLBACK_TITLE : title, entries));
        } else if (title != null) {
            sections.add(new ChangelogSection(title, Collections.<String>emptyList()));
        }
        return sections.size() > MAX_SECTIONS ? Collections.unmodifiableList(sections.subList(0, MAX_SECTIONS)) : sections;
    }

    private static void add(List<String> entries, StringBuilder paragraph) {
        String text = ChangelogSection.plain(paragraph.toString());
        if (text.isEmpty()) return;
        entries.add(Text.shorten(text, MAX_ENTRY));
    }
}
