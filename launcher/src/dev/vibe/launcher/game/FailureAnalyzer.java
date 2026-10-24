package dev.vibe.launcher.game;

import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.Text;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises known problems in Minecraft's output and crash reports, so a failed
 * start is reported with a specific {@link ErrorCode} ("game libraries could not
 * be loaded") instead of a generic one.
 *
 * <p>Only the last lines of the game are kept, so harmless errors earlier in a
 * long session do not decide the diagnosis.</p>
 */
public final class FailureAnalyzer {
    /** A diagnosis: the code and the output line that led to it (empty for the fallback). */
    public static final class Result {
        public final ErrorCode code;
        public final String evidence;
        Result(ErrorCode code, String evidence) { this.code = code; this.evidence = evidence; }
    }

    private static final class Rule {
        final ErrorCode code;
        final Pattern pattern;
        Rule(ErrorCode code, String regex) { this.code = code; this.pattern = Pattern.compile(regex); }
    }

    /** The first rule that matches decides, so specific causes come before general ones. */
    private static final Rule[] RULES = {
        new Rule(ErrorCode.ROSETTA, "Bad CPU type in executable"),
        new Rule(ErrorCode.DISK_FULL, "No space left on device|not enough space on the disk|There is not enough space"),
        new Rule(ErrorCode.JAVA_START, "Cannot run program|A problem occurred starting process|Error: Could not find or load main class"),
        new Rule(ErrorCode.GAME_MEMORY_SETTING, "Could not reserve enough space for .*object heap|Invalid maximum heap size"
                + "|Error occurred during initialization of VM|Could not create the Java Virtual Machine"
                + "|Initial heap size set to a larger value|insufficient memory for the Java Runtime"),
        new Rule(ErrorCode.GAME_OUT_OF_MEMORY, "java\\.lang\\.OutOfMemoryError|GC overhead limit exceeded"),
        new Rule(ErrorCode.NATIVES, "UnsatisfiedLinkError|no lwjgl(64)? in java\\.library\\.path|Can't load (IA 32|AMD 64|ARM)-bit"
                + "|Failed to load (the )?(LWJGL|native)"),
        new Rule(ErrorCode.GRAPHICS, "Pixel format not accelerated|No OpenGL context found|Could not create context|Unable to create OpenGL"
                + "|org\\.lwjgl\\.LWJGLException|GLXBadFBConfig|Could not choose GLX13 config|LinuxDisplay|XRandR"
                + "|OpenGL [0-9.]+ (is )?not supported|Display\\.create"),
        new Rule(ErrorCode.NATIVE_CRASH, "A fatal error has been detected by the Java Runtime Environment|EXCEPTION_ACCESS_VIOLATION|SIGSEGV|SIGBUS|hs_err_pid"),
        new Rule(ErrorCode.MOD_CONFLICT, "MissingModsException|Missing Mods|DuplicateModsFoundException|Duplicate Mods|WrongMinecraftVersionException"
                + "|LoaderExceptionModCrash|MixinApplyError|MixinTransformerError|UnsupportedClassVersionError|compiled by a more recent version"),
    };

    private static final int TAIL = 200;

    private final ArrayDeque<String> tail = new ArrayDeque<String>();

    /** Feeds one output line while Minecraft runs. */
    public synchronized void accept(String line) {
        String text = line == null ? "" : line.trim();
        if (text.length() < 6) return;
        tail.addLast(text);
        if (tail.size() > TAIL) tail.removeFirst();
    }

    /** Why Minecraft ended with an error: from its crash report, or the last lines it printed. */
    public synchronized Result gameFailure(String crashReport) {
        Result fromReport = match(lines(crashReport));
        if (fromReport != null) return fromReport;
        Result fromTail = match(tail.toArray(new String[0]));
        return fromTail != null ? fromTail : new Result(ErrorCode.GAME_CRASHED, "");
    }

    private static Result match(String[] lines) {
        for (Rule rule : RULES) {
            for (String line : lines) {
                if (!rule.pattern.matcher(line).find()) continue;
                // Forge blames the mod whose code failed; when that is Vibe itself it is no mod conflict.
                if (rule.code == ErrorCode.MOD_CONFLICT && line.toLowerCase(Locale.ROOT).contains("vibe")) continue;
                return new Result(rule.code, Text.shorten(line.trim(), 200));
            }
        }
        return null;
    }

    private static String[] lines(String text) {
        return text == null || text.isEmpty() ? new String[0] : text.split("\r?\n");
    }
}
