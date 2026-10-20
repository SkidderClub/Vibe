package dev.vibe.launcher.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A "## heading" of Vibe's changelog with its bullet points as plain text. */
public final class ChangelogSection {
    public final String title;
    public final List<String> entries;

    ChangelogSection(String title, List<String> entries) {
        this.title = title;
        this.entries = Collections.unmodifiableList(entries);
    }

    static List<ChangelogSection> parse(String markdown, int maxSections) {
        List<ChangelogSection> sections = new ArrayList<ChangelogSection>();
        String title = null;
        List<String> entries = new ArrayList<String>();
        StringBuilder current = null;
        for (String raw : markdown.split("\r?\n")) {
            String line = raw.trim();
            if (line.startsWith("## ")) {
                if (current != null) entries.add(plain(current.toString()));
                current = null;
                if (title != null && !entries.isEmpty()) sections.add(new ChangelogSection(title, entries));
                if (sections.size() >= maxSections) return sections;
                title = line.substring(3).trim();
                entries = new ArrayList<String>();
            } else if (title != null && (line.startsWith("- ") || line.startsWith("* "))) {
                if (current != null) entries.add(plain(current.toString()));
                current = new StringBuilder(line.substring(2).trim());
            } else if (title != null && current != null && !line.isEmpty() && !line.startsWith("#")) {
                current.append(' ').append(line);
            }
        }
        if (current != null) entries.add(plain(current.toString()));
        if (title != null && !entries.isEmpty() && sections.size() < maxSections) sections.add(new ChangelogSection(title, entries));
        return sections;
    }

    /** Strips the Markdown the changelog uses: links, code spans and emphasis. */
    static String plain(String text) {
        return text.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replace("`", "")
                .replace("**", "")
                .replaceAll("(?<![A-Za-z0-9])_([^_]+)_(?![A-Za-z0-9])", "$1")
                .trim();
    }
}
