package dev.vibe.launcher.core;

import dev.vibe.launcher.VibeLauncher;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.util.Locale;

/**
 * Every problem the launcher reports, with a stable code such as {@code VL-101}.
 * The hundreds group the cause: 1xx internet, 2xx files, 3xx Java, 4xx Vibe
 * download, 5xx build, 6xx Minecraft, 7xx launcher, 8xx mods, 9xx unexpected.
 * {@code docs/LAUNCHER_ERRORS.md} explains each code and how to fix it; codes are
 * never renumbered, so old screenshots and Discord posts stay searchable.
 */
public enum ErrorCode {
    // ---- 1xx: internet and servers ------------------------------------------
    NO_INTERNET(101, "No internet connection", "Check your Wi-Fi or network cable and try again."),
    CONNECTION_BLOCKED(102, "Connection blocked", "A firewall, proxy, VPN or network filter blocks the connection. Allow Java through the firewall or try another network."),
    TIMEOUT(103, "Connection timed out", "The connection is too slow or unstable. Try again, ideally on a faster or wired connection."),
    SECURE_CONNECTION(104, "Secure connection failed", "Check that your PC's date and time are correct and turn off HTTPS scanning in your antivirus, then try again."),
    GITHUB_RATE_LIMIT(105, "GitHub request limit reached", "GitHub allows about 60 requests per hour per network. Wait until the time in the message and try again."),
    SERVER_ERROR(106, "Server error", "The server has a problem right now. Try again in a few minutes."),
    DOWNLOAD_INTERRUPTED(107, "Download interrupted", "The connection dropped during a download. Try again on a stable connection."),
    UNEXPECTED_RESPONSE(108, "Unexpected answer from the server", "A Wi-Fi login page, proxy or filter answered instead of the server. Sign in to the network in your browser or use another network."),
    NOT_FOUND(109, "File not found on the server", "The download no longer exists. Update the launcher; if that does not help, report the error."),

    // ---- 2xx: files and folders ---------------------------------------------
    ACCESS_DENIED(201, "No permission to write files", "Your antivirus, a sync tool such as OneDrive or missing rights block the launcher's folder. Allow it and try again."),
    FILE_IN_USE(202, "Files are in use", "Close Minecraft and programs that have Vibe's files open, then try again. A restart helps if nothing else does."),
    DISK_FULL(203, "Not enough disk space", "Free up disk space and try again. The first start needs about 3 GB."),
    FILE_ERROR(204, "File system error", "A file could not be read or written. Try again; if it repeats, check the drive for errors."),
    MINECRAFT_FOLDER_MISSING(205, "No Minecraft folder found", "Start the normal Minecraft launcher once, or copy options.txt into Vibe's game folder by hand."),
    DATA_FOLDER(206, "Launcher data folder unusable", "The launcher cannot write to its data folder. Check its permissions or choose another folder (see help)."),

    // ---- 3xx: Java ----------------------------------------------------------
    JAVA_UNAVAILABLE(301, "No Java for this system", "Adoptium offers no Java for this system. Vibe needs 64-bit Windows, macOS or Linux."),
    JAVA_DAMAGED(302, "Java download damaged", "The Java download was damaged on the way. Try again; an antivirus or proxy may be altering downloads."),
    JAVA_BROKEN(303, "Java installation unusable", "Settings → Java → Reinstall downloads both runtimes again."),
    JAVA_START(304, "Java could not be started", "An antivirus may have blocked Java. Allow the launcher's runtime folder, then use Settings → Java → Reinstall."),
    WRONG_JAVA(305, "Wrong Java version", "The build did not find the Java version it needs. Settings → Java → Reinstall sets up Java 8 and Java 21 again."),

    // ---- 4xx: Vibe download and updates -------------------------------------
    VIBE_DOWNLOAD(401, "Vibe could not be downloaded", "Check your connection to GitHub and try again."),
    UPDATE_INCOMPLETE(402, "Last update did not finish", "Connect to the internet and press Play: the launcher repairs the update."),
    SOURCE_INVALID(403, "Vibe download is invalid", "Try again. If it keeps failing, use Settings → Troubleshooting → Reinstall Vibe."),
    PROFILE_CARRY_OVER(404, "Game profile could not be moved", "Close Minecraft and programs that use the game folder, then try again. Your worlds are kept."),
    SOURCE_DAMAGED(405, "Vibe files are missing", "Settings → Troubleshooting → Reinstall Vibe downloads the source again; your profile is kept."),

    // ---- 5xx: building Vibe -------------------------------------------------
    BUILD_FAILED(500, "Build failed", "The console shows why. Settings → Troubleshooting → Clear build cache often helps."),
    BUILD_DOWNLOAD(501, "Download during the build failed", "Gradle could not download Minecraft, Forge or a library. Check your connection and try again in a few minutes."),
    COMPILE_ERROR(502, "Vibe does not compile", "Clear the build cache and reinstall Vibe. If it remains, this Vibe version is broken: wait for a fix or report it."),
    GRADLE_DOWNLOAD(503, "Gradle could not be set up", "Gradle's own download failed or is damaged. Check your connection and try again (see help)."),
    OPTIFINE_DOWNLOAD(504, "OptiFine could not be downloaded", "optifine.net did not deliver the file. Try again later or save the OptiFine JAR by hand (see help)."),
    GRADLE_MEMORY(505, "Not enough memory for the build", "Close other programs and try again. The build needs about 3 GB of free RAM."),
    GRADLE_CACHE(506, "Gradle cache damaged", "Clear the build cache. If that does not help, delete Gradle's cache folder (see help)."),
    GRADLE_LOCKED(507, "Build files are locked", "Another Gradle build is running, for example in an IDE. Close it or restart the PC, then try again."),
    MINECRAFT_FILES(508, "Minecraft files are missing", "Minecraft 1.8.9 was not prepared. Delete the Unimined cache (see help) and start again with internet."),

    // ---- 6xx: Minecraft -----------------------------------------------------
    GAME_CRASHED(600, "Minecraft crashed", "The crash report or the console shows the cause. Disable your own mods to test whether one of them is responsible."),
    GAME_MEMORY_SETTING(601, "Minecraft could not reserve its memory", "Lower Settings → Memory and close other programs, then try again."),
    GAME_OUT_OF_MEMORY(602, "Minecraft ran out of memory", "Raise Settings → Memory to 3–4 GB and use fewer or smaller resource packs and mods."),
    GRAPHICS(603, "Graphics driver problem", "Update your graphics driver. On laptops, let Java use the dedicated graphics card."),
    MOD_CONFLICT(604, "A mod prevents the start", "Disable your own mods on the Mods page and start again, then enable them one by one to find the culprit."),
    NATIVES(605, "Game libraries could not be loaded", "Clear the build cache so the LWJGL libraries are unpacked again, and allow them in your antivirus."),
    NATIVE_CRASH(606, "Java crashed", "Usually the graphics driver or an overlay (Discord, RivaTuner, …). Update the driver and turn overlays off."),
    ROSETTA(607, "Rosetta 2 is missing", "Minecraft 1.8.9 needs Rosetta 2 on Apple Silicon: run softwareupdate --install-rosetta in Terminal."),

    // ---- 7xx: the launcher itself -------------------------------------------
    ALREADY_RUNNING(701, "Launcher is already open", "Switch to the open window. If none is visible, end the Java process in the task manager and start again."),
    NO_DESKTOP(702, "No desktop available", "Start the launcher in a desktop session, not over SSH or in a headless environment."),
    LAUNCHER_UPDATE(703, "Launcher update failed", "Try again later or download VibeLauncher.jar from the GitHub releases by hand."),
    LAUNCHER_CHECKSUM(704, "Launcher update damaged", "The download did not match its checksum and was discarded. Try again later."),
    NOT_A_JAR(705, "Launcher does not run from VibeLauncher.jar", "Start the launcher with java -jar VibeLauncher.jar to use updates and restarts."),
    ACCOUNT_VAULT(706, "Account vault unreadable", "Restore accounts.vault together with its matching accounts.key, or rename accounts.vault to start with an empty vault (see help)."),
    ACCOUNT_KEY(707, "Account key missing", "Restore accounts.key from a backup. Without it the saved accounts are lost: rename accounts.vault and sign in again."),
    OPEN_FAILED(708, "Could not open", "Open the file, folder or link by hand; the message names it."),
    STARTUP_FAILED(709, "Launcher could not start", "Start it again. If it keeps failing, report it with the launcher log."),

    // ---- 8xx: custom mods ---------------------------------------------------
    MOD_NOT_JAR(801, "Not a mod file", "Only Forge mods as .jar or .zip files can be added."),
    MOD_TOO_LARGE(802, "Mod file too large", "Files over 300 MB are refused. Check that it really is a mod."),
    MOD_INVALID(803, "Damaged mod file", "The file is not a valid archive. Download the mod again."),
    MOD_OPTIFINE(804, "OptiFine is already included", "Vibe always starts with OptiFine; no extra copy is needed."),
    MOD_VIBE(805, "Vibe is loaded automatically", "Vibe itself must not be added as a mod."),
    MOD_FABRIC(806, "Fabric mod", "Fabric mods do not work with Forge 1.8.9. Look for a Forge 1.8.9 version."),
    MOD_NEWER_FORGE(807, "Mod for a newer Minecraft", "This mod is for Forge 1.13 or newer. Look for a 1.8.9 version."),
    MOD_DUPLICATE(808, "Mod already installed", "A file with this name is already in the mods folder."),
    MOD_NAME_TAKEN(809, "File name already taken", "Enabled and disabled copies of this mod exist. Remove one of them in the mods folder."),
    MOD_WRONG_VERSION(810, "Mod for another Minecraft version", "The mod says it was made for another Minecraft version and may not load."),

    // ---- 9xx -------------------------------------------------------------------
    UNEXPECTED(900, "Unexpected error", "Try again. If it repeats, report it on Discord with the launcher log.");

    /** The error guide in the repository; each code has a section anchored at {@code #vl-<number>}. */
    public static final String HELP_URL = VibeLauncher.REPOSITORY_URL + "/blob/" + VibeLauncher.BRANCH + "/docs/LAUNCHER_ERRORS.md";

    public final int number;
    private final String title, hint;

    ErrorCode(int number, String title, String hint) {
        this.number = number;
        this.title = title;
        this.hint = hint;
    }

    /** "VL-101". */
    public String id() { return "VL-" + number; }
    /** Short name of the problem in the interface language. */
    public String title() { return I18n.t(title); }
    /** English name, for the launcher log. */
    public String englishTitle() { return title; }
    /** One sentence on how to fix it, in the interface language. */
    public String hint() { return I18n.t(hint); }
    public String helpUrl() { return HELP_URL + "#vl-" + number; }
    /** "VL-101 · No internet connection". */
    public String heading() { return id() + " · " + title(); }

    public static ErrorCode byNumber(int number) {
        for (ErrorCode code : values()) if (code.number == number) return code;
        return null;
    }

    /**
     * The code for a failure: the one attached to a {@link LauncherException} anywhere in
     * the cause chain, otherwise one derived from the exception types and messages.
     */
    public static ErrorCode of(Throwable error) {
        int depth = 0;
        for (Throwable cause = error; cause != null && depth < 12; cause = cause.getCause(), depth++) {
            if (cause instanceof LauncherException) return ((LauncherException) cause).code;
        }
        depth = 0;
        for (Throwable cause = error; cause != null && depth < 12; cause = cause.getCause(), depth++) {
            ErrorCode code = classify(cause);
            if (code != null) return code;
        }
        return UNEXPECTED;
    }

    /** Like {@link #of(Throwable)}, with the code of the step that failed instead of {@link #UNEXPECTED}. */
    public static ErrorCode of(Throwable error, ErrorCode fallback) {
        ErrorCode code = of(error);
        return code == UNEXPECTED && fallback != null ? fallback : code;
    }

    /** Whether {@link #of} only falls back to {@link #UNEXPECTED} for this failure. */
    public static boolean unknown(Throwable error) { return of(error) == UNEXPECTED; }

    private static ErrorCode classify(Throwable error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (error instanceof Http.StatusException) {
            Http.StatusException status = (Http.StatusException) error;
            if (status.rateLimited) return GITHUB_RATE_LIMIT;
            if (status.status == 404 || status.status == 410) return NOT_FOUND;
            if (status.status == 401 || status.status == 403 || status.status == 407 || status.status == 451) return CONNECTION_BLOCKED;
            if (status.status >= 300 && status.status < 400) return UNEXPECTED_RESPONSE;
            return SERVER_ERROR;
        }
        if (error instanceof Json.SyntaxException) return UNEXPECTED_RESPONSE;
        if (error instanceof UnknownHostException || error instanceof NoRouteToHostException) return NO_INTERNET;
        if (error instanceof SocketTimeoutException) return TIMEOUT;
        if (error instanceof ConnectException) {
            return lower.contains("unreachable") ? NO_INTERNET : lower.contains("timed out") ? TIMEOUT : CONNECTION_BLOCKED;
        }
        boolean dropped = lower.contains("connection reset") || lower.contains("ended early") || lower.contains("premature eof")
                || lower.contains("broken pipe") || lower.contains("socket closed");
        if ((error instanceof javax.net.ssl.SSLException && !dropped) || error instanceof java.security.cert.CertificateException
                || lower.contains("pkix path") || lower.contains("certification path")) {
            return SECURE_CONNECTION;
        }
        if (error instanceof SocketException && lower.contains("unreachable")) return NO_INTERNET;
        if (error instanceof SocketException || error instanceof EOFException || dropped) return DOWNLOAD_INTERRUPTED;
        if (lower.contains("non-https") || lower.contains("unexpectedly large") || lower.contains("safety limit")) return UNEXPECTED_RESPONSE;
        if (lower.contains("no space left") || lower.contains("not enough space") || lower.contains("disk full") || lower.contains("disk quota")) return DISK_FULL;
        if (lower.contains("used by another process") || lower.contains("locked a portion of the file") || lower.contains("user-mapped section open")) return FILE_IN_USE;
        if (error instanceof AccessDeniedException || lower.contains("access is denied") || lower.contains("permission denied")
                || lower.contains("operation not permitted") || lower.contains("read-only file system")) {
            return ACCESS_DENIED;
        }
        if (error instanceof FileSystemException) return FILE_ERROR;
        return null;
    }
}
