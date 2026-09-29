package dev.vibe.account;

import java.util.regex.Pattern;

/** A pasted Microsoft account refresh token (consumer tokens start with "M.C5..."). Microsoft verifies it; locally only the format is checked. */
public final class MicrosoftRefreshToken {
    static final int MAX_LENGTH = 32768;
    private static final Pattern FORMAT = Pattern.compile("M\\.[A-Za-z0-9._!*$~+/=%-]{16,}");
    private static final Pattern PREFIX = Pattern.compile("(?i)^(refresh[_ ]?token\\s*[:=]\\s*)");
    private final String value;

    private MicrosoftRefreshToken(String value) { this.value = value; }

    /** Accepts the bare token or a quoted one, also with line breaks from wrapped copies; returns null for anything else. */
    public static MicrosoftRefreshToken parse(String input) {
        if (input == null || input.length() > MAX_LENGTH * 2) return null;
        String value = input.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1).trim();
        value = PREFIX.matcher(value).replaceFirst("");
        value = value.replaceAll("\\s+", "");
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) return null;
        return new MicrosoftRefreshToken(value);
    }

    /** Shows the start only, e.g. "M.C508_BAY.0.U... (1234 characters)". */
    public String preview() { return value.substring(0, Math.min(15, value.length())) + "... (" + value.length() + " characters)"; }
    String value() { return value; }
}
