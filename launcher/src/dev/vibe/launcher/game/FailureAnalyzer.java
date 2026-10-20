package dev.vibe.launcher.game;

import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.Text;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises known problems in the output of a {@code runClient} run and in
 * Minecraft's crash reports, so a failed start is reported with a specific
 * {@link ErrorCode} ("OptiFine could not be downloaded") instead of "the build failed".
 *
 * <p>Build output is only consulted when the build fails, and for a crash only the
 * crash report and the last lines before the exit count, so harmless errors earlier
 * in a long session do not decide the diagnosis.</p>
 */
public final class FailureAnalyzer {
    /** When a rule applies: before Minecraft starts, while it runs, or both. */
    private enum Phase { BUILD, GAME, ANY }

    /** A diagnosis: the code and the output line that led to it (empty for the fallback). */
    public static final class Result {
        public final ErrorCode code;
        public final String evidence;
        Result(ErrorCode code, String evidence) { this.code = code; this.evidence = evidence; }
    }

    private static final class Rule {
        final ErrorCode code;
        final Phase phase;
        final Pattern pattern;
        Rule(ErrorCode code, Phase phase, String regex) { this.code = code; this.phase = phase; this.pattern = Pattern.compile(regex); }
        boolean applies(boolean game) { return phase == Phase.ANY || (phase == Phase.GAME) == game; }
    }

    /**
     * The first rule that matches decides, so specific causes come before general
     * ones: a download that fails for lack of internet also prints "Could not resolve".
     */
    private static final Rule[] RULES = {
        new Rule(ErrorCode.ROSETTA, Phase.ANY, "Bad CPU type in executable"),
        new Rule(ErrorCode.DISK_FULL, Phase.ANY, "No space left on device|not enough space on the disk|There is not enough space"),
        new Rule(ErrorCode.SOURCE_DAMAGED, Phase.BUILD, "Could not find or load main class org\\.gradle\\.wrapper|gradle-wrapper\\.properties \\(No such file|wrapper properties file .* does not exist"),
        new Rule(ErrorCode.GRADLE_LOCKED, Phase.BUILD, "Timeout waiting to lock|currently in use by another Gradle instance|Could not obtain (an )?exclusive lock"),
        new Rule(ErrorCode.GRADLE_DOWNLOAD, Phase.BUILD, "Could not install Gradle distribution|Exception in thread \"main\" java\\.(net|io|util\\.zip)\\.|Downloading https://services\\.gradle\\.org.*(failed|error)"),
        new Rule(ErrorCode.WRONG_JAVA, Phase.BUILD, "No matching toolchains found|No locally installed toolchains match|Cannot find a Java installation on your machine"
                + "|does not provide the required capabilities|Unsupported class file major version|Gradle requires JVM \\d+ or later"),
        new Rule(ErrorCode.GRADLE_MEMORY, Phase.BUILD, "OutOfMemoryError|Java heap space|GC overhead limit exceeded|Could not reserve enough space|Unable to start the daemon process"
                + "|Could not create the Java Virtual Machine|Error occurred during initialization of VM|insufficient memory for the Java Runtime"),
        new Rule(ErrorCode.OPTIFINE_DOWNLOAD, Phase.BUILD, "OptiFine did not provide a download link|not a valid OptiFine JAR|Execution failed for task ':prepareOptifine'"),
        new Rule(ErrorCode.MINECRAFT_FILES, Phase.BUILD, "Unimined did not prepare the Minecraft"),
        new Rule(ErrorCode.SECURE_CONNECTION, Phase.BUILD, "PKIX path building failed|unable to find valid certification path|SSLHandshakeException|Remote host terminated the handshake|SSLException"),
        new Rule(ErrorCode.NO_INTERNET, Phase.BUILD, "UnknownHostException|Name or service not known|No such host is known|nodename nor servname provided"
                + "|Temporary failure in name resolution|Network is unreachable|No route to host"),
        new Rule(ErrorCode.GRADLE_CACHE, Phase.BUILD, "Could not read workspace metadata|zip END header not found|error in opening zip file|invalid LOC header"
                + "|Unexpected end of ZLIB input stream|Corrupted .*cache|cache .* is corrupt"),
        new Rule(ErrorCode.BUILD_DOWNLOAD, Phase.BUILD, "Could not (resolve|download|GET|HEAD|get resource)|status code [45]\\d\\d|Received status code|Read timed out|[Cc]onnect timed out"
                + "|Connection reset|Connection refused|No cached version of"),
        new Rule(ErrorCode.COMPILE_ERROR, Phase.BUILD, "Compilation failed|Execution failed for task ':compile(Java|Kotlin|Groovy)'|\\.java:\\d+: error: "),
        new Rule(ErrorCode.ACCESS_DENIED, Phase.BUILD, "AccessDeniedException|Access is denied|Permission denied|Operation not permitted|Could not create Vibe client directory"),
        new Rule(ErrorCode.FILE_IN_USE, Phase.BUILD, "being used by another process|Unable to delete (file|directory)"),
        new Rule(ErrorCode.JAVA_START, Phase.ANY, "A problem occurred starting process|Cannot run program"),
        new Rule(ErrorCode.GAME_MEMORY_SETTING, Phase.GAME, "Could not reserve enough space for .*object heap|Invalid maximum heap size|Error occurred during initialization of VM"
                + "|Could not create the Java Virtual Machine|Initial heap size set to a larger value|insufficient memory for the Java Runtime"),
        new Rule(ErrorCode.GAME_OUT_OF_MEMORY, Phase.GAME, "java\\.lang\\.OutOfMemoryError|GC overhead limit exceeded"),
        new Rule(ErrorCode.NATIVES, Phase.GAME, "UnsatisfiedLinkError|no lwjgl(64)? in java\\.library\\.path|Can't load (IA 32|AMD 64|ARM)-bit|Failed to load (the )?(LWJGL|native)"),
        new Rule(ErrorCode.GRAPHICS, Phase.GAME, "Pixel format not accelerated|No OpenGL context found|Could not create context|Unable to create OpenGL|org\\.lwjgl\\.LWJGLException"
                + "|GLXBadFBConfig|Could not choose GLX13 config|LinuxDisplay|XRandR|OpenGL [0-9.]+ (is )?not supported|Display\\.create"),
        new Rule(ErrorCode.NATIVE_CRASH, Phase.GAME, "A fatal error has been detected by the Java Runtime Environment|EXCEPTION_ACCESS_VIOLATION|SIGSEGV|SIGBUS|hs_err_pid"),
        new Rule(ErrorCode.MOD_CONFLICT, Phase.GAME, "MissingModsException|Missing Mods|DuplicateModsFoundException|Duplicate Mods|WrongMinecraftVersionException"
                + "|LoaderExceptionModCrash|MixinApplyError|MixinTransformerError|UnsupportedClassVersionError|compiled by a more recent version"),
    };

    private static final int TAIL = 200;

    private final String[] buildEvidence = new String[RULES.length];
    private final ArrayDeque<String> gameTail = new ArrayDeque<String>();

    /**
     * Feeds one output line.
     *
     * @param game whether {@code runClient} has begun to start Minecraft
     */
    public synchronized void accept(String line, boolean game) {
        String text = line == null ? "" : line.trim();
        if (text.length() < 6) return;
        if (game) {
            gameTail.addLast(text);
            if (gameTail.size() > TAIL) gameTail.removeFirst();
            return;
        }
        for (int index = 0; index < RULES.length; index++) {
            if (buildEvidence[index] == null && RULES[index].applies(false) && RULES[index].pattern.matcher(text).find()) buildEvidence[index] = text;
        }
    }

    /**
     * Why the build failed. Gradle's "What went wrong" summary decides first, then
     * anything else seen while building.
     */
    public synchronized Result buildFailure(String summary) {
        Result fromSummary = match(lines(summary), false);
        if (fromSummary != null) return fromSummary;
        for (int index = 0; index < RULES.length; index++) {
            if (buildEvidence[index] != null) return new Result(RULES[index].code, Text.shorten(buildEvidence[index], 200));
        }
        return new Result(ErrorCode.BUILD_FAILED, "");
    }

    /** Why Minecraft ended with an error: from its crash report, or the last lines it printed. */
    public synchronized Result gameFailure(String crashReport) {
        Result fromReport = match(lines(crashReport), true);
        if (fromReport != null) return fromReport;
        Result fromTail = match(gameTail.toArray(new String[0]), true);
        return fromTail != null ? fromTail : new Result(ErrorCode.GAME_CRASHED, "");
    }

    private static Result match(String[] lines, boolean game) {
        for (Rule rule : RULES) {
            if (!rule.applies(game)) continue;
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
