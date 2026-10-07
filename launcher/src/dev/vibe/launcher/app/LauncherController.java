package dev.vibe.launcher.app;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.game.AccountVault;
import dev.vibe.launcher.game.FailureAnalyzer;
import dev.vibe.launcher.game.GameProfile;
import dev.vibe.launcher.game.GameSession;
import dev.vibe.launcher.game.LaunchMode;
import dev.vibe.launcher.game.ModLibrary;
import dev.vibe.launcher.game.Theme;
import dev.vibe.launcher.install.ChangelogSection;
import dev.vibe.launcher.install.LauncherUpdater;
import dev.vibe.launcher.install.RuntimeManager;
import dev.vibe.launcher.install.SourceManager;
import dev.vibe.launcher.skin.SkinService;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;

/**
 * The launcher's state and every operation the interface can trigger.
 * Long-running work runs on one worker thread, so installation, updates and
 * launches never overlap; listeners are always notified on the Swing thread.
 */
public final class LauncherController {
    public enum Event { STATE, ACCOUNTS, MODS, THEME, SOURCE, LAUNCHER_UPDATE, SETTINGS, SKIN }

    public enum State { IDLE, PREPARING, BUILDING, RUNNING, STOPPING }

    public interface Listener { void changed(Event event); }

    public interface Notifier {
        void notify(Notice notice);
    }

    /** A toast: level, text, an optional action and, for errors, a link to the fix. */
    public static final class Notice {
        public enum Level { INFO, SUCCESS, WARNING, ERROR }
        public final Level level;
        public final String title, message, actionLabel;
        public final Runnable action;
        /** The error code shown in the title, or {@code null}. */
        public final ErrorCode code;
        /** Opens the error guide at {@link #code}; {@code null} when there is nothing to look up. */
        public final Runnable help;
        /** Action marker: the interface opens its console page. */
        public static final Runnable SHOW_CONSOLE = () -> { };
        public Notice(Level level, String title, String message, String actionLabel, Runnable action) {
            this(level, title, message, actionLabel, action, null, null);
        }
        public Notice(Level level, String title, String message, String actionLabel, Runnable action, ErrorCode code, Runnable help) {
            this.level = level; this.title = title; this.message = message; this.actionLabel = actionLabel; this.action = action;
            this.code = code; this.help = help;
        }
    }

    /** The last failed installation, update or launch, shown in the play bar until the next attempt. */
    public static final class Failure {
        public final ErrorCode code;
        public final String detail;
        Failure(ErrorCode code, String detail) { this.code = code; this.detail = detail; }
    }

    private static final java.util.regex.Pattern PROGRESS_LINE = java.util.regex.Pattern.compile("\\d{1,3}% \\(\\d+/\\d+\\)|[.\\d%]+");

    private final AppPaths paths;
    private final Settings settings;
    private final AppLog log;
    private final SourceManager source;
    private final RuntimeManager runtimes;
    private final LauncherUpdater updater;
    private final SkinService skins;
    private final GameProfile profile;
    private final ModLibrary mods;
    private final ConsoleBuffer console = new ConsoleBuffer();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Vibe launcher worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Notifier notifier;

    private volatile State state = State.IDLE;
    private volatile String progressTitle = "", progressDetail = "";
    private volatile double progress = -1;
    private volatile Failure lastFailure;
    private volatile boolean launchQueued;
    /** Guards the hand-off between a queued Play click and the preparing task. */
    private final Object queueLock = new Object();
    private boolean queueOpen;
    private volatile Future<?> task;
    private volatile Runnable exit = () -> System.exit(0);
    private volatile boolean installedCache;
    private volatile String vibeVersionCache = "";
    private volatile GameSession session;
    private volatile LaunchMode runningMode;
    private volatile long lastActivity;

    private volatile Theme theme;
    private volatile LaunchMode mode;
    private volatile List<AccountVault.Account> accounts = Collections.emptyList();
    private volatile String accountsError = "";
    private volatile ErrorCode accountsCode;
    private volatile List<ModLibrary.Mod> modList = Collections.emptyList();
    private volatile SourceManager.Remote remote;
    private volatile String remoteError = "";
    private volatile IOException remoteFailure;
    private volatile List<ChangelogSection> changelog = Collections.emptyList();
    private volatile LauncherUpdater.Release launcherUpdate;
    private volatile String launcherUpdateStatus = "";
    private volatile SkinService.Skin skin;

    public LauncherController(AppPaths paths, Settings settings, AppLog log) {
        this.paths = paths;
        this.settings = settings;
        this.log = log;
        this.source = new SourceManager(paths, settings, log);
        this.runtimes = new RuntimeManager(paths, settings, log);
        this.updater = new LauncherUpdater(paths, log);
        this.skins = new SkinService(paths.skinCache(), log);
        this.profile = new GameProfile(paths.profile());
        this.mods = new ModLibrary(profile.mods());
        Theme shared = profile.readTheme();
        this.theme = shared != null ? shared : Theme.parse(settings.theme());
        this.mode = LaunchMode.parse(settings.launchMode());
        log.addListener((level, message) -> {
            if (!"INFO".equals(level)) console.append("[Launcher] " + level + ": " + message);
        });
    }

    // ---- lifecycle ---------------------------------------------------------

    /** Loads local state and starts the background checks; call once the window is visible. */
    public void start() {
        reloadAccounts();
        reloadMods();
        changelog = source.changelog();
        console.append("[Launcher] " + VibeLauncher.PRODUCT + " " + VibeLauncher.VERSION + " · Java " + System.getProperty("java.version")
                + " · " + System.getProperty("os.name"));
        refreshSourceInfo();
        GameSession attached = GameSession.reattach(paths.sessionFile(), sessionListener());
        if (attached != null) {
            session = attached;
            if (attached.reachedGame()) setState(State.RUNNING, I18n.t("Vibe is running"), "", 1);
            else setState(State.BUILDING, I18n.t("Building Vibe"), "", 0.05);
            console.append("[Launcher] Reconnected to the running game.");
        }
        worker.execute(() -> {
            source.cleanLeftovers();
            updater.cleanUp();
        });
        boolean setupNeeded = !source.isInstalled() || !runtimes.isReady();
        // Never touch the checkout while a game started earlier is still using it.
        if (attached == null && (setupNeeded || settings.autoUpdate())) {
            submit(setupNeeded ? I18n.t("Installing Vibe") : I18n.t("Checking for updates"), false, false);
        } else {
            worker.execute(this::refreshRemote);
        }
        worker.execute(this::checkLauncherUpdate);
    }

    /**
     * Stops background work. Downloads are interrupted at once; a checkout swap that is
     * already moving folders is allowed to finish so the game profile is never stranded.
     */
    public void shutdown() {
        worker.shutdownNow();
        try { worker.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    /** How the launcher exits after a self-update; the window saves its bounds first. */
    public void setExit(Runnable exit) { this.exit = exit; }

    // ---- listeners -------------------------------------------------------

    public void addListener(Listener listener) { listeners.add(listener); }
    public void setNotifier(Notifier notifier) { this.notifier = notifier; }
    public ConsoleBuffer console() { return console; }

    private void fire(final Event event) {
        SwingUtilities.invokeLater(() -> { for (Listener listener : listeners) listener.changed(event); });
    }

    private void notice(Notice.Level level, String title, String message, String actionLabel, Runnable action) {
        notice(new Notice(level, title, message, actionLabel, action));
    }

    private void notice(final Notice notice) {
        SwingUtilities.invokeLater(() -> { Notifier current = notifier; if (current != null) current.notify(notice); });
    }

    // ---- failures ----------------------------------------------------------

    /**
     * Logs a failed operation with its stack trace and reports it with its error code.
     *
     * @param context what failed, e.g. "Installing Vibe failed"
     * @param blocking whether it stopped an installation, update or launch; only those
     *                 stay visible in the play bar
     */
    private void fail(String context, Throwable error, ErrorCode fallback, boolean blocking, String actionLabel, Runnable action) {
        ErrorCode code = ErrorCode.of(error, fallback);
        log.error(code.id() + " " + context, error);
        report(code, context + ": " + Text.describe(error), blocking, actionLabel, action);
    }

    /** Shows a failure as a toast with its fix and a link to the error guide, and adds the fix to the console. */
    private void report(ErrorCode code, String detail, boolean blocking, String actionLabel, Runnable action) {
        if (blocking) lastFailure = new Failure(code, detail);
        console.append("[Launcher] " + code.heading() + " \u2013 " + code.hint());
        console.append("[Launcher] " + I18n.t("Help: {0}", code.helpUrl()));
        Runnable help = code == ErrorCode.OPEN_FAILED ? null : () -> openHelp(code);
        notice(new Notice(Notice.Level.ERROR, code.heading(), detail + "\n" + code.hint(), actionLabel, action, code, help));
        if (blocking) fire(Event.STATE);
    }

    /** Opens the error guide, at the given code's section when there is one. */
    public void openHelp(ErrorCode code) { browse(code == null ? ErrorCode.HELP_URL : code.helpUrl()); }

    // ---- getters ---------------------------------------------------------

    public Settings settings() { return settings; }
    public AppPaths paths() { return paths; }
    public GameProfile profile() { return profile; }
    public AppLog log() { return log; }
    public State state() { return state; }
    public boolean busy() { return state != State.IDLE; }
    public String progressTitle() { return progressTitle; }
    public String progressDetail() { return progressDetail; }
    public double progress() { return progress; }
    /** The last failed installation, update or launch; {@code null} after a success or a new attempt. */
    public Failure lastFailure() { return lastFailure; }
    public boolean launchQueued() { return launchQueued; }
    /** Whether Play would queue a launch behind the running installation or update. */
    public boolean canQueueLaunch() { synchronized (queueLock) { return queueOpen && !launchQueued; } }
    public LaunchMode runningMode() { return runningMode; }
    public Theme theme() { return theme; }
    public LaunchMode mode() { return mode; }
    public List<AccountVault.Account> accounts() { return accounts; }
    public String accountsError() { return accountsError; }
    /** The code for {@link #accountsError()}, or {@code null}. */
    public ErrorCode accountsCode() { return accountsCode; }
    public List<ModLibrary.Mod> mods() { return modList; }
    public SourceManager.Remote remote() { return remote; }
    public String remoteError() { return remoteError; }
    public List<ChangelogSection> changelog() { return changelog; }
    public LauncherUpdater.Release launcherUpdate() { return launcherUpdate; }
    public String launcherUpdateStatus() { return launcherUpdateStatus; }
    public boolean sourceInstalled() { return installedCache; }
    public String installedRevision() { return source.installedRevision(); }
    public String vibeVersion() { return vibeVersionCache; }

    /** Cached so painting never reads build.gradle or stats the checkout. */
    private void refreshSourceInfo() {
        installedCache = source.isInstalled();
        vibeVersionCache = source.vibeVersion();
    }
    public boolean sourceUpdateAvailable() {
        SourceManager.Remote known = remote;
        return known != null && installedCache && (settings.sourceIncomplete() || !known.head.equals(settings.sourceRevision()));
    }
    public Path logFile() { return log.file(); }

    /** The account that will be used, or {@code null} for Vibe's own auto-login. */
    public AccountVault.Account selectedAccount() {
        String uuid = settings.selectedUuid();
        if (uuid.isEmpty()) return null;
        for (AccountVault.Account account : accounts) if (account.uuid.equalsIgnoreCase(uuid)) return account;
        return null;
    }

    /** The account Vibe logs into when "Automatic" is selected. */
    public AccountVault.Account autoLoginAccount() {
        for (AccountVault.Account account : accounts) if (account.autoLogin) return account;
        return null;
    }

    public String displayName() {
        AccountVault.Account account = selectedAccount();
        if (account == null) account = autoLoginAccount();
        if (account != null) return account.name;
        String gamertag = profile.gamertag();
        return gamertag.isEmpty() ? "Player" : gamertag;
    }

    public SkinService.Skin skin() {
        SkinService.Skin current = skin;
        return current != null ? current : skins.defaultSkin("");
    }

    public SkinService skins() { return skins; }

    // ---- settings --------------------------------------------------------

    public void setMode(LaunchMode next) {
        mode = next;
        settings.setLaunchMode(next.name());
        fire(Event.SETTINGS);
    }

    public void setTheme(Theme next) {
        theme = next;
        settings.setTheme(next.name());
        try { profile.writeTheme(next); } catch (IOException error) { log.warn("Could not share the theme with Vibe", error); }
        fire(Event.THEME);
    }

    public void settingsChanged() { fire(Event.SETTINGS); }

    // ---- accounts --------------------------------------------------------

    public void reloadAccounts() {
        try {
            accounts = AccountVault.read(profile.accounts());
            accountsError = "";
            accountsCode = null;
        } catch (IOException error) {
            accounts = Collections.emptyList();
            accountsCode = ErrorCode.of(error, ErrorCode.ACCOUNT_VAULT);
            accountsError = Text.describe(error);
            log.warn(accountsCode.id() + " Account vault unavailable: " + accountsError);
        }
        if (!settings.selectedUuid().isEmpty() && selectedAccount() == null && accountsError.isEmpty() && !accounts.isEmpty()) {
            // The selected account was removed inside Vibe.
            settings.setSelectedAccount("", "");
        }
        fire(Event.ACCOUNTS);
        refreshSkin();
    }

    public void selectAccount(AccountVault.Account account) {
        settings.setSelectedAccount(account == null ? "" : account.uuid, account == null ? "" : account.name);
        if (state == State.IDLE) writeBridge(LaunchMode.VIBE);
        fire(Event.ACCOUNTS);
        refreshSkin();
    }

    private void refreshSkin() {
        AccountVault.Account account = selectedAccount();
        if (account == null) account = autoLoginAccount();
        final String uuid = account == null ? "" : account.uuid;
        final String name = account == null ? profile.gamertag() : account.name;
        skin = skins.current(uuid, name);
        fire(Event.SKIN);
        if (account == null && name.isEmpty()) return;
        skins.load(uuid, name, account != null && account.microsoft, loaded -> {
            AccountVault.Account now = selectedAccount();
            if (now == null) now = autoLoginAccount();
            String currentUuid = now == null ? "" : now.uuid;
            if (currentUuid.equals(uuid)) {
                skin = loaded;
                fire(Event.SKIN);
            }
        });
    }

    public void openAltManager() { launch(LaunchMode.ACCOUNTS); }

    // ---- mods --------------------------------------------------------------

    public void reloadMods() {
        modList = mods.list();
        fire(Event.MODS);
    }

    public Path modsFolder() { return mods.folder(); }

    public void importMods(final List<File> files) {
        // Imports wait for a running download: a full reinstall moves the profile folder.
        if (state == State.PREPARING) notice(Notice.Level.INFO, I18n.t("Mods queued"), I18n.t("They are added as soon as the current step has finished."), null, null);
        worker.execute(() -> {
            try {
                ModLibrary.ImportResult result = mods.importFiles(files);
                reloadMods();
                if (!result.added.isEmpty()) {
                    notice(Notice.Level.SUCCESS, result.added.size() == 1 ? I18n.t("Mod added") : I18n.t("{0} mods added", result.added.size()),
                            join(result.added), null, null);
                    log.info("Added mods: " + join(result.added));
                }
                if (!result.rejected.isEmpty()) {
                    // The codes are in the text; the guide opens at the first one.
                    final ErrorCode first = result.rejectedCodes.get(0);
                    notice(new Notice(Notice.Level.WARNING, I18n.t("Some files were not added"), join(result.rejected), null, null, first, () -> openHelp(first)));
                }
            } catch (IOException error) {
                fail(I18n.t("Could not add mods"), error, ErrorCode.FILE_ERROR, false, null, null);
            }
        });
    }

    public void setModEnabled(ModLibrary.Mod mod, boolean enabled) {
        try { mods.setEnabled(mod, enabled); }
        catch (IOException error) { fail(I18n.t("Could not change the mod"), error, ErrorCode.FILE_ERROR, false, null, null); }
        reloadMods();
    }

    public void deleteMod(ModLibrary.Mod mod) {
        try {
            mods.delete(mod);
            log.info("Removed mod " + mod.fileName);
        } catch (IOException error) {
            fail(I18n.t("Could not remove the mod"), error, ErrorCode.FILE_ERROR, false, null, null);
        }
        reloadMods();
    }

    // ---- play ------------------------------------------------------------

    public void play() { launch(mode); }

    private void launch(LaunchMode requested) {
        synchronized (queueLock) {
            if (queueOpen) {
                launchQueued = true;
                runningMode = requested;
                fire(Event.STATE);
                return;
            }
        }
        if (state != State.IDLE) return;
        runningMode = requested;
        launchQueued = true;
        submit(I18n.t("Preparing {0}", requested.label), true, false);
    }

    /** Runs installation/update and, if a launch is queued, starts the game afterwards. */
    private void submit(final String title, final boolean forLaunch, final boolean forceUpdate) {
        lastFailure = null;
        synchronized (queueLock) { queueOpen = true; }
        setState(State.PREPARING, title, "", -1);
        task = worker.submit(() -> {
            try {
                prepare(forLaunch, forceUpdate);
                boolean start;
                synchronized (queueLock) {
                    start = launchQueued;
                    launchQueued = false;
                    queueOpen = false;
                }
                if (start) startGame();
                else setState(State.IDLE, "", "", -1);
            } catch (Exception error) {
                closeQueue();
                // A read timeout is an InterruptedIOException as well, but a failure, not a cancel.
                if (Thread.currentThread().isInterrupted() || LauncherException.cancelled(error)) {
                    setState(State.IDLE, "", "", -1);
                    console.append("[Launcher] Cancelled.");
                    return;
                }
                fail(I18n.t("{0} failed", title), error, ErrorCode.UNEXPECTED, true, I18n.t("Show console"), Notice.SHOW_CONSOLE);
                setState(State.IDLE, "", "", -1);
            }
        });
    }

    private void closeQueue() {
        synchronized (queueLock) {
            launchQueued = false;
            queueOpen = false;
        }
    }

    private void prepare(boolean forLaunch, boolean forceUpdate) throws Exception {
        Progress progress = (message, detail, fraction) -> setProgress(message, detail, fraction);
        boolean installed = source.isInstalled();
        boolean applyUpdates = forceUpdate || settings.autoUpdate();
        // A half-applied update must be repaired, so GitHub is asked again even without auto-update.
        if (!installed || applyUpdates || !forLaunch || settings.sourceIncomplete()) {
            progress.update(installed ? I18n.t("Checking for updates") : I18n.t("Installing Vibe"), "", -1);
            refreshRemote();
        }
        SourceManager.Remote known = remote;
        boolean sourceNeeded = !installed || settings.sourceIncomplete() || (applyUpdates && known != null && source.needsUpdate(known));
        if (sourceNeeded && known == null && remoteFailure != null && (!installed || settings.sourceIncomplete())) {
            // Without GitHub the source can be neither installed nor repaired: report why GitHub failed.
            IOException cause = remoteFailure;
            String what = installed ? I18n.t("The last Vibe update did not finish and GitHub is not reachable.") : I18n.t("Vibe could not be downloaded from GitHub.");
            throw new LauncherException(ErrorCode.of(cause, installed ? ErrorCode.UPDATE_INCOMPLETE : ErrorCode.VIBE_DOWNLOAD),
                    what + " " + Text.describe(cause), cause);
        }
        long needed = (!installed ? 900L : sourceNeeded ? 400L : 0L) + (runtimes.isReady() ? 0L : 700L);
        if (needed > 0) requireSpace(Collections.singletonMap(paths.root(), needed * MB));
        if (sourceNeeded) {
            boolean wasInstalled = installed;
            String before = source.installedRevision();
            source.update(known, progress.range(0, 1));
            refreshSourceInfo();
            changelog = source.changelog();
            fire(Event.SOURCE);
            if (wasInstalled && !before.equals(source.installedRevision())) {
                console.append("[Launcher] Vibe updated to " + Text.shortSha(source.installedRevision()) + ".");
                if (!forLaunch) notice(Notice.Level.SUCCESS, I18n.t("Vibe updated"), known != null && known.commits.size() > 0 ? known.commits.get(0).message : "", null, null);
            }
        }
        if (!runtimes.isReady()) runtimes.ensure(progress);
    }

    private void refreshRemote() {
        try {
            remote = source.fetchRemote();
            remoteError = "";
            remoteFailure = null;
        } catch (IOException error) {
            if (LauncherException.cancelled(error)) return;
            ErrorCode code = ErrorCode.of(error, ErrorCode.UNEXPECTED_RESPONSE);
            remoteFailure = error;
            remoteError = Text.describe(error) + " (" + code.id() + ")";
            log.warn(code.id() + " Could not check GitHub for Vibe updates: " + Text.describe(error));
        }
        fire(Event.SOURCE);
    }

    private static final long MB = 1024L * 1024L;

    /**
     * Fails with VL-203 before a download or build that would run out of disk space
     * halfway. Folders on the same drive add up; an unknown free space is not checked.
     */
    private void requireSpace(Map<Path, Long> needs) throws LauncherException {
        Map<FileStore, Long> total = new LinkedHashMap<FileStore, Long>();
        Map<FileStore, Path> example = new LinkedHashMap<FileStore, Path>();
        for (Map.Entry<Path, Long> need : needs.entrySet()) {
            try {
                Path existing = need.getKey().toAbsolutePath();
                while (existing != null && !Files.exists(existing)) existing = existing.getParent();
                if (existing == null) continue;
                FileStore store = Files.getFileStore(existing);
                Long sum = total.get(store);
                total.put(store, (sum == null ? 0L : sum) + need.getValue());
                if (!example.containsKey(store)) example.put(store, need.getKey());
            } catch (IOException | RuntimeException ignored) {
                // Unknown drive: let the operation try.
            }
        }
        for (Map.Entry<FileStore, Long> entry : total.entrySet()) {
            long free;
            try { free = entry.getKey().getUsableSpace(); } catch (IOException | RuntimeException ignored) { continue; }
            if (free > 0 && free < entry.getValue()) {
                throw new LauncherException(ErrorCode.DISK_FULL, I18n.t("Only {0} free for {1}, about {2} are needed.",
                        Text.bytes(free), example.get(entry.getKey()), Text.bytes(entry.getValue())));
            }
        }
    }

    /** Gradle's cache of Minecraft, Forge and the libraries; often on another drive than the launcher. */
    private static Path gradleUserHome() {
        String configured = System.getenv("GRADLE_USER_HOME");
        if (configured != null && !configured.trim().isEmpty()) return Paths.get(configured.trim());
        return Paths.get(System.getProperty("user.home"), ".gradle");
    }

    private void startGame() throws Exception {
        final LaunchMode launchMode = runningMode == null ? LaunchMode.VIBE : runningMode;
        RuntimeManager.Runtimes installedRuntimes = runtimes.ensure(Progress.NONE);
        // The first build downloads Minecraft, Forge and the libraries and writes Vibe's build output.
        Map<Path, Long> needs = new LinkedHashMap<Path, Long>();
        Path gradleHome = gradleUserHome();
        needs.put(gradleHome, (Files.isDirectory(gradleHome.resolve("caches").resolve("unimined")) ? 256L : 1536L) * MB);
        needs.put(source.root(), (Files.isDirectory(source.root().resolve("build")) ? 256L : 768L) * MB);
        requireSpace(needs);
        writeBridge(launchMode);
        try { profile.writeTheme(theme); } catch (IOException error) { log.warn("Could not share the theme with Vibe", error); }

        GameSession.Request request = new GameSession.Request();
        request.projectRoot = source.root();
        request.java8 = installedRuntimes.java8;
        request.jdk21 = installedRuntimes.jdk21;
        request.memoryMb = settings.memoryMb();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        request.logFile = paths.gameLogs().resolve(stamp + ".log");
        request.readyFile = paths.root().resolve("session.ready");
        request.sessionFile = paths.sessionFile();
        pruneGameLogs();

        console.append("");
        console.append("[Launcher] Starting " + launchMode.label + " · Vibe " + Text.shortSha(source.installedRevision())
                + " · " + request.memoryMb + " MB · " + (displayName()));
        setState(State.BUILDING, I18n.t("Building Vibe"), I18n.t("Gradle is preparing the build"), 0.05);
        log.info("Launching " + launchMode.name() + " with log " + request.logFile);
        session = GameSession.start(request, sessionListener());
    }

    private GameSession.Listener sessionListener() {
        return new GameSession.Listener() {
            @Override public void output(List<String> lines) {
                // Download counters ("16% (119/734)") only feed the status line, not the console.
                List<String> shown = new ArrayList<String>(lines.size());
                for (String line : lines) if (!PROGRESS_LINE.matcher(line.trim()).matches() || line.trim().startsWith("100%")) shown.add(line);
                if (!shown.isEmpty()) console.append(shown);
                if (state != State.BUILDING) return;
                // Long first builds stay on one stage for minutes; the latest line shows they move.
                for (int index = lines.size() - 1; index >= 0; index--) {
                    String activity = activity(lines.get(index));
                    if (activity == null) continue;
                    long now = System.currentTimeMillis();
                    if (now - lastActivity > 300) {
                        lastActivity = now;
                        setProgress(progressTitle, activity, progress);
                    }
                    break;
                }
            }

            @Override public void stage(GameSession.Stage stage) {
                if (state == State.STOPPING) return;
                switch (stage) {
                    case CONFIGURING: setState(State.BUILDING, I18n.t("Preparing Minecraft & Forge"), I18n.t("The first build downloads Minecraft and Forge once"), 0.15); break;
                    case COMPILING: setState(State.BUILDING, I18n.t("Compiling Vibe"), "", 0.4); break;
                    case PACKAGING: setState(State.BUILDING, I18n.t("Packaging Vibe"), "", 0.62); break;
                    case OPTIFINE: setState(State.BUILDING, I18n.t("Preparing OptiFine"), "", 0.72); break;
                    case ASSETS: setState(State.BUILDING, I18n.t("Downloading Minecraft assets"), I18n.t("Only needed once"), 0.78); break;
                    case STARTING: setState(State.BUILDING, I18n.t("Starting Minecraft"), I18n.t("Forge is loading mods"), 0.85); break;
                    case RUNNING:
                        setState(State.RUNNING, I18n.t("Vibe is running"), "", 1);
                        console.append("[Launcher] Minecraft is ready.");
                        break;
                    default: break;
                }
            }

            @Override public void exited(Integer code, boolean reachedGame, String failure) { sessionEnded(code, reachedGame, failure); }
        };
    }

    private void sessionEnded(Integer code, boolean reachedGame, String failure) {
        GameSession ended = session;
        boolean stopped = ended != null && ended.isStopping();
        // Once runClient has started, a failure is Minecraft's, not the build's.
        boolean gameStarted = reachedGame || (ended != null && ended.currentStage() != null
                && ended.currentStage().ordinal() >= GameSession.Stage.STARTING.ordinal());
        session = null;
        runningMode = null;
        console.append("[Launcher] " + (stopped ? "Stopped." : "Game process ended" + (code == null ? "." : " with exit code " + code + ".")));
        reloadAccounts();
        reloadMods();
        Theme shared = profile.readTheme();
        if (shared != null && shared != theme) {
            theme = shared;
            settings.setTheme(shared.name());
            fire(Event.THEME);
        }
        // The failure is known before the state changes, so the window can come to the front for it.
        if (!stopped && code != null && code != 0) {
            if (!gameStarted) buildFailed(ended, failure);
            else gameCrashed(ended, code);
        }
        setState(State.IDLE, "", "", -1);
    }

    /** Reports why runClient failed before Minecraft started. */
    private void buildFailed(GameSession ended, String failure) {
        FailureAnalyzer.Result result = ended == null ? null : ended.analyzer().buildFailure(failure);
        ErrorCode code = result == null ? ErrorCode.BUILD_FAILED : result.code;
        // Gradle nests causes; the first line says what failed, the last one why.
        String[] lines = failure.isEmpty() ? new String[0] : failure.split("\n");
        String cause = lines.length == 0 ? "" : lines[lines.length - 1];
        String detail = lines.length == 0 ? "" : lines.length == 1 ? cause : lines[0] + "\n" + cause;
        if (detail.isEmpty() && result != null) detail = result.evidence;
        if (detail.isEmpty()) detail = I18n.t("The build failed. The console shows what went wrong.");
        detail = Text.shorten(detail, 300);
        log.error(code.id() + " Vibe could not be started: " + detail.replace('\n', ' '), null);
        report(code, detail, true, I18n.t("Show console"), Notice.SHOW_CONSOLE);
    }

    /** Reports a Minecraft that ended with an error, using its crash report when there is a fresh one. */
    private void gameCrashed(GameSession ended, Integer exitCode) {
        final Path crashReport = profile.latestCrashReport();
        boolean fresh = crashReport != null && System.currentTimeMillis() - crashReport.toFile().lastModified() < 5 * 60 * 1000L;
        String text = fresh ? GameProfile.readCrashReport(crashReport, 256 * 1024) : "";
        FailureAnalyzer.Result result = ended == null ? null : ended.analyzer().gameFailure(text);
        ErrorCode code = result == null ? ErrorCode.GAME_CRASHED : result.code;
        boolean customMods = false;
        for (ModLibrary.Mod mod : modList) customMods |= mod.enabled;
        // Without custom mods a loader error points at Vibe itself, not at a conflict.
        if (code == ErrorCode.MOD_CONFLICT && !customMods) code = ErrorCode.GAME_CRASHED;
        String detail = I18n.t("Minecraft closed unexpectedly (exit code {0}).", exitCode);
        if (result != null && !result.evidence.isEmpty()) detail += "\n" + result.evidence;
        log.error(code.id() + " Minecraft crashed: " + detail.replace('\n', ' ') + (fresh ? " Crash report: " + crashReport : ""), null);
        report(code, detail, true, fresh ? I18n.t("Open crash report") : I18n.t("Show console"),
                fresh ? () -> openPath(crashReport) : Notice.SHOW_CONSOLE);
    }

    public void stop() {
        GameSession current = session;
        if (current == null) return;
        setState(State.STOPPING, I18n.t("Stopping Vibe"), "", -1);
        new Thread(current::stop, "Vibe stop").start();
    }

    /** Cancels installation or a queued launch. A running build is stopped like the game. */
    public void cancel() {
        closeQueue();
        Future<?> current = task;
        if (state == State.PREPARING && current != null) current.cancel(true);
        else if (state == State.BUILDING) stop();
    }

    private void writeBridge(LaunchMode launchMode) {
        try { profile.writeBridge(launchMode, settings.selectedUuid(), settings.selectedName()); }
        catch (IOException error) { log.warn("Could not write the launcher bridge", error); }
    }

    private void pruneGameLogs() {
        List<Path> logs = new ArrayList<Path>(FileUtil.children(paths.gameLogs()));
        for (int index = 0; index < logs.size() - 15; index++) {
            try { Files.deleteIfExists(logs.get(index)); } catch (IOException ignored) { /* in use */ }
        }
    }

    public Path currentGameLog() {
        GameSession current = session;
        if (current != null) return current.logFile();
        List<Path> logs = FileUtil.children(paths.gameLogs());
        return logs.isEmpty() ? null : logs.get(logs.size() - 1);
    }

    // ---- maintenance -------------------------------------------------------

    public void checkForUpdates() {
        if (state != State.IDLE) return;
        worker.execute(this::checkLauncherUpdate);
        submit(I18n.t("Checking for updates"), false, false);
    }

    /** Applies a pending Vibe update now, even when automatic updates are switched off. */
    public void updateVibeNow() {
        if (state != State.IDLE) return;
        submit(I18n.t("Updating Vibe"), false, true);
    }

    public void reinstallSource() {
        if (state != State.IDLE) return;
        source.markForReinstall();
        submit(I18n.t("Reinstalling Vibe"), false, true);
    }

    public void repairRuntimes() {
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Reinstalling Java"), "", -1);
        task = worker.submit(() -> {
            try {
                requireSpace(Collections.singletonMap(paths.runtimes(), 700L * MB));
                runtimes.reinstall((message, detail, fraction) -> setProgress(message, detail, fraction));
                notice(Notice.Level.SUCCESS, I18n.t("Java reinstalled"), I18n.t("Java 8 and Java 21 are ready."), null, null);
            } catch (Exception error) {
                if (!LauncherException.cancelled(error) && !Thread.currentThread().isInterrupted()) {
                    fail(I18n.t("Java could not be installed"), error, ErrorCode.JAVA_BROKEN, true, null, null);
                }
            }
            setState(State.IDLE, "", "", -1);
            fire(Event.SETTINGS);
        });
    }

    public void cleanBuild() {
        if (state != State.IDLE) return;
        worker.execute(() -> {
            try {
                source.cleanBuild();
                notice(Notice.Level.SUCCESS, I18n.t("Build cache cleared"), I18n.t("Vibe is compiled from scratch on the next launch."), null, null);
            } catch (IOException error) {
                fail(I18n.t("Could not clear the build cache"), error, ErrorCode.FILE_ERROR, false, null, null);
            }
        });
    }

    public void importMinecraftSettings() {
        worker.execute(() -> {
            try {
                int copied = profile.importFromMinecraft();
                notice(Notice.Level.SUCCESS, I18n.t("Import finished"), copied == 0 ? I18n.t("Everything was already there.") : I18n.t("{0} items copied from .minecraft.", copied), null, null);
            } catch (IOException error) {
                final ErrorCode code = ErrorCode.of(error, ErrorCode.FILE_ERROR);
                log.warn(code.id() + " Import from .minecraft failed", error);
                notice(new Notice(Notice.Level.WARNING, code.id() + " \u00b7 " + I18n.t("Nothing imported"), Text.describe(error) + "\n" + code.hint(),
                        null, null, code, () -> openHelp(code)));
            }
        });
    }

    private void checkLauncherUpdate() {
        try {
            launcherUpdate = updater.check();
            launcherUpdateStatus = launcherUpdate == null ? I18n.t("You have the newest launcher.") : I18n.t("Version {0} is available.", launcherUpdate.version);
            if (launcherUpdate != null) {
                final LauncherUpdater.Release release = launcherUpdate;
                notice(Notice.Level.INFO, I18n.t("Launcher update available"), I18n.t("Version {0} is ready to install.", release.version), I18n.t("Update"), this::installLauncherUpdate);
            }
        } catch (IOException error) {
            ErrorCode code = ErrorCode.of(error, ErrorCode.LAUNCHER_UPDATE);
            launcherUpdateStatus = I18n.t("Update check failed: {0}", Text.describe(error) + " (" + code.id() + ")");
            log.warn(code.id() + " Launcher update check failed: " + Text.describe(error));
        }
        fire(Event.LAUNCHER_UPDATE);
    }

    public void installLauncherUpdate() {
        final LauncherUpdater.Release release = launcherUpdate;
        if (release == null || state != State.IDLE) return;
        setState(State.PREPARING, I18n.t("Updating the launcher"), "", -1);
        task = worker.submit(() -> {
            try {
                Path staged = updater.download(release, (message, detail, fraction) -> setProgress(message, detail, fraction));
                updater.installOnExit(staged);
                SwingUtilities.invokeLater(exit);
            } catch (Exception error) {
                if (LauncherException.cancelled(error) || Thread.currentThread().isInterrupted()) {
                    setState(State.IDLE, "", "", -1);
                    return;
                }
                fail(I18n.t("Launcher update failed"), error, ErrorCode.LAUNCHER_UPDATE, true, null, null);
                setState(State.IDLE, "", "", -1);
            }
        });
    }

    public void openPath(Path path) {
        try {
            if (Files.isRegularFile(path) && java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
            else Platform.openFolder(path);
        } catch (Exception error) {
            ErrorCode code = ErrorCode.of(error, ErrorCode.OPEN_FAILED);
            log.warn(code.id() + " Could not open " + path, error);
            report(code, I18n.t("Could not open {0}", path) + ": " + Text.describe(error), false, null, null);
        }
    }

    public void browse(String url) {
        try { Platform.browse(url); }
        catch (IOException error) {
            log.warn(ErrorCode.OPEN_FAILED.id() + " Could not open the browser for " + url, error);
            report(ErrorCode.OPEN_FAILED, I18n.t("Could not open the browser") + ": " + url, false, null, null);
        }
    }

    // ---- state helpers -------------------------------------------------------

    private void setState(State next, String title, String detail, double fraction) {
        state = next;
        progressTitle = title == null ? "" : title;
        progressDetail = detail == null ? "" : detail;
        progress = fraction;
        fire(Event.STATE);
    }

    private void setProgress(String title, String detail, double fraction) {
        progressTitle = title == null ? progressTitle : title;
        progressDetail = detail == null ? "" : detail;
        progress = fraction;
        fire(Event.STATE);
    }

    /** A build output line worth showing under the progress bar, or {@code null}. */
    static String activity(String line) {
        String text = line.trim();
        if (text.isEmpty() || text.startsWith("Picked up ") || text.contains("Adding mappings") || text.startsWith("at ")) return null;
        if (text.startsWith("> Task :")) return text.substring(2);
        // "[17:31:52] [Client thread/INFO]: Setting user" -> "Setting user"
        text = text.replaceFirst("^(\\[[^\\]]{1,60}\\]:?\\s*)+", "").trim();
        if (text.length() < 4 || text.startsWith("{") || text.matches("[.\\d% ]+")) return null;
        return Text.shorten(text, 90);
    }

    private static String join(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (text.length() > 0) text.append('\n');
            text.append(value);
        }
        return text.toString();
    }
}
