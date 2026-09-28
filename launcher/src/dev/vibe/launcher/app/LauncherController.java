package dev.vibe.launcher.app;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.game.AccountVault;
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
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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

    /** A toast: level, text and an optional action. */
    public static final class Notice {
        public enum Level { INFO, SUCCESS, WARNING, ERROR }
        public final Level level;
        public final String title, message, actionLabel;
        public final Runnable action;
        /** Action marker: the interface opens its console page. */
        public static final Runnable SHOW_CONSOLE = () -> { };
        public Notice(Level level, String title, String message, String actionLabel, Runnable action) {
            this.level = level; this.title = title; this.message = message; this.actionLabel = actionLabel; this.action = action;
        }
    }

    private static final java.util.regex.Pattern DOWNLOAD_FAILURE = java.util.regex.Pattern.compile(
            "Could not (resolve|download|GET|HEAD)|status code (4\\d\\d|5\\d\\d)|timed out|UnknownHost|Connection reset|Network is unreachable");

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
    private volatile String lastError = "";
    private volatile boolean launchQueued;
    private volatile Future<?> task;
    private volatile GameSession session;
    private volatile LaunchMode runningMode;
    private volatile long lastActivity;

    private volatile Theme theme;
    private volatile LaunchMode mode;
    private volatile List<AccountVault.Account> accounts = Collections.emptyList();
    private volatile String accountsError = "";
    private volatile List<ModLibrary.Mod> modList = Collections.emptyList();
    private volatile SourceManager.Remote remote;
    private volatile String remoteError = "";
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
        GameSession attached = GameSession.reattach(paths.sessionFile(), sessionListener());
        if (attached != null) {
            session = attached;
            setState(State.RUNNING, I18n.t("Vibe is running"), "", 1);
            console.append("[Launcher] Reconnected to the running game.");
        }
        worker.execute(() -> source.cleanLeftovers());
        boolean setupNeeded = !source.isInstalled() || !runtimes.isReady();
        // Never touch the checkout while a game started earlier is still using it.
        if (attached == null && (setupNeeded || settings.autoUpdate())) {
            submit(setupNeeded ? I18n.t("Installing Vibe") : I18n.t("Checking for updates"), false, false);
        } else {
            worker.execute(this::refreshRemote);
        }
        worker.execute(this::checkLauncherUpdate);
    }

    public void shutdown() { worker.shutdownNow(); }

    // ---- listeners -------------------------------------------------------

    public void addListener(Listener listener) { listeners.add(listener); }
    public void setNotifier(Notifier notifier) { this.notifier = notifier; }
    public ConsoleBuffer console() { return console; }

    private void fire(final Event event) {
        SwingUtilities.invokeLater(() -> { for (Listener listener : listeners) listener.changed(event); });
    }

    private void notice(Notice.Level level, String title, String message, String actionLabel, Runnable action) {
        final Notice notice = new Notice(level, title, message, actionLabel, action);
        SwingUtilities.invokeLater(() -> { Notifier current = notifier; if (current != null) current.notify(notice); });
    }

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
    public String lastError() { return lastError; }
    public boolean launchQueued() { return launchQueued; }
    public LaunchMode runningMode() { return runningMode; }
    public Theme theme() { return theme; }
    public LaunchMode mode() { return mode; }
    public List<AccountVault.Account> accounts() { return accounts; }
    public String accountsError() { return accountsError; }
    public List<ModLibrary.Mod> mods() { return modList; }
    public SourceManager.Remote remote() { return remote; }
    public String remoteError() { return remoteError; }
    public List<ChangelogSection> changelog() { return changelog; }
    public LauncherUpdater.Release launcherUpdate() { return launcherUpdate; }
    public String launcherUpdateStatus() { return launcherUpdateStatus; }
    public boolean sourceInstalled() { return source.isInstalled(); }
    public String installedRevision() { return source.installedRevision(); }
    public String vibeVersion() { return source.vibeVersion(); }
    public boolean sourceUpdateAvailable() { SourceManager.Remote known = remote; return known != null && source.isInstalled() && source.needsUpdate(known); }
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
        } catch (IOException error) {
            accounts = Collections.emptyList();
            accountsError = error.getMessage();
            log.warn("Account vault unavailable: " + error.getMessage());
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
                    notice(Notice.Level.WARNING, I18n.t("Some files were not added"), join(result.rejected), null, null);
                }
            } catch (IOException error) {
                log.error("Could not add mods", error);
                notice(Notice.Level.ERROR, I18n.t("Could not add mods"), Text.describe(error), null, null);
            }
        });
    }

    public void setModEnabled(ModLibrary.Mod mod, boolean enabled) {
        try { mods.setEnabled(mod, enabled); }
        catch (IOException error) { notice(Notice.Level.ERROR, I18n.t("Could not change the mod"), Text.describe(error), null, null); }
        reloadMods();
    }

    public void deleteMod(ModLibrary.Mod mod) {
        try {
            mods.delete(mod);
            log.info("Removed mod " + mod.fileName);
        } catch (IOException error) {
            notice(Notice.Level.ERROR, I18n.t("Could not remove the mod"), Text.describe(error), null, null);
        }
        reloadMods();
    }

    // ---- play ------------------------------------------------------------

    public void play() { launch(mode); }

    private void launch(LaunchMode requested) {
        if (state == State.PREPARING && task != null && !task.isDone()) {
            launchQueued = true;
            runningMode = requested;
            fire(Event.STATE);
            return;
        }
        if (state != State.IDLE) return;
        runningMode = requested;
        launchQueued = true;
        submit(I18n.t("Preparing {0}", requested.label), true, false);
    }

    /** Runs installation/update and, if a launch is queued, starts the game afterwards. */
    private void submit(final String title, final boolean forLaunch, final boolean forceUpdate) {
        lastError = "";
        setState(State.PREPARING, title, "", -1);
        task = worker.submit(() -> {
            try {
                prepare(forLaunch, forceUpdate);
                if (launchQueued) startGame();
                else setState(State.IDLE, "", "", -1);
            } catch (InterruptedIOException | InterruptedException cancelled) {
                launchQueued = false;
                setState(State.IDLE, "", "", -1);
                console.append("[Launcher] Cancelled.");
            } catch (Exception error) {
                launchQueued = false;
                if (Thread.currentThread().isInterrupted()) {
                    setState(State.IDLE, "", "", -1);
                    return;
                }
                log.error(title + " failed", error);
                lastError = Text.describe(error);
                setState(State.IDLE, "", "", -1);
                notice(Notice.Level.ERROR, I18n.t("{0} failed", title), lastError, I18n.t("Show console"), Notice.SHOW_CONSOLE);
            }
        });
    }

    private void prepare(boolean forLaunch, boolean forceUpdate) throws Exception {
        Progress progress = (message, detail, fraction) -> setProgress(message, detail, fraction);
        boolean installed = source.isInstalled();
        boolean applyUpdates = forceUpdate || settings.autoUpdate();
        if (!installed || applyUpdates || !forLaunch) {
            progress.update(installed ? I18n.t("Checking for updates") : I18n.t("Installing Vibe"), "", -1);
            refreshRemote();
        }
        SourceManager.Remote known = remote;
        if (!installed || settings.sourceIncomplete() || (applyUpdates && known != null && source.needsUpdate(known))) {
            boolean wasInstalled = installed;
            String before = source.installedRevision();
            source.update(known, progress.range(0, 1));
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
        } catch (IOException error) {
            remoteError = Text.describe(error);
            log.warn("Could not check GitHub for Vibe updates: " + remoteError);
        }
        fire(Event.SOURCE);
    }

    private void startGame() throws Exception {
        final LaunchMode launchMode = runningMode == null ? LaunchMode.VIBE : runningMode;
        launchQueued = false;
        RuntimeManager.Runtimes installedRuntimes = runtimes.ensure(Progress.NONE);
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
        session = null;
        runningMode = null;
        console.append("[Launcher] " + (stopped ? "Stopped." : "Game process ended" + (code == null ? "." : " with exit code " + code + ".")));
        setState(State.IDLE, "", "", -1);
        reloadAccounts();
        reloadMods();
        Theme shared = profile.readTheme();
        if (shared != null && shared != theme) {
            theme = shared;
            settings.setTheme(shared.name());
            fire(Event.THEME);
        }
        if (stopped || code == null || code == 0) return;
        if (!reachedGame) {
            // Gradle nests causes; the first line says what failed, the last one why.
            String[] lines = failure.isEmpty() ? new String[0] : failure.split("\n");
            String cause = lines.length == 0 ? "" : lines[lines.length - 1];
            String message = lines.length == 0 ? "" : lines.length == 1 ? cause : lines[0] + "\n" + cause;
            if (DOWNLOAD_FAILURE.matcher(failure).find()) {
                lastError = I18n.t("A download failed while building Vibe. Check your connection and try again in a few minutes.");
            } else {
                lastError = cause.isEmpty() ? I18n.t("The build failed. The console shows what went wrong.") : Text.shorten(cause, 240);
            }
            notice(Notice.Level.ERROR, I18n.t("Vibe could not be started"), message.isEmpty() ? lastError : Text.shorten(message, 400),
                    I18n.t("Show console"), Notice.SHOW_CONSOLE);
        } else {
            final Path report = profile.latestCrashReport();
            boolean fresh = report != null && System.currentTimeMillis() - report.toFile().lastModified() < 5 * 60 * 1000L;
            lastError = I18n.t("Minecraft closed unexpectedly (exit code {0}).", code);
            notice(Notice.Level.ERROR, I18n.t("Minecraft crashed"), lastError,
                    fresh ? I18n.t("Open crash report") : I18n.t("Show console"),
                    fresh ? () -> openPath(report) : Notice.SHOW_CONSOLE);
        }
        fire(Event.STATE);
    }

    public void stop() {
        GameSession current = session;
        if (current == null) return;
        setState(State.STOPPING, I18n.t("Stopping Vibe"), "", -1);
        new Thread(current::stop, "Vibe stop").start();
    }

    /** Cancels installation or a queued launch. A running build is stopped like the game. */
    public void cancel() {
        launchQueued = false;
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
        lastError = "";
        setState(State.PREPARING, I18n.t("Reinstalling Java"), "", -1);
        task = worker.submit(() -> {
            try {
                runtimes.reinstall((message, detail, fraction) -> setProgress(message, detail, fraction));
                notice(Notice.Level.SUCCESS, I18n.t("Java reinstalled"), I18n.t("Java 8 and Java 21 are ready."), null, null);
            } catch (Exception error) {
                if (!(error instanceof InterruptedIOException)) {
                    log.error("Java reinstall failed", error);
                    lastError = Text.describe(error);
                    notice(Notice.Level.ERROR, I18n.t("Java could not be installed"), lastError, null, null);
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
                notice(Notice.Level.ERROR, I18n.t("Could not clear the build cache"), Text.describe(error), null, null);
            }
        });
    }

    public void importMinecraftSettings() {
        worker.execute(() -> {
            try {
                int copied = profile.importFromMinecraft();
                notice(Notice.Level.SUCCESS, I18n.t("Import finished"), copied == 0 ? I18n.t("Everything was already there.") : I18n.t("{0} items copied from .minecraft.", copied), null, null);
            } catch (IOException error) {
                notice(Notice.Level.WARNING, I18n.t("Nothing imported"), Text.describe(error), null, null);
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
            launcherUpdateStatus = I18n.t("Update check failed: {0}", Text.describe(error));
            log.warn("Launcher update check failed: " + Text.describe(error));
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
                SwingUtilities.invokeLater(() -> System.exit(0));
            } catch (Exception error) {
                log.error("Launcher update failed", error);
                lastError = Text.describe(error);
                setState(State.IDLE, "", "", -1);
                notice(Notice.Level.ERROR, I18n.t("Launcher update failed"), lastError, null, null);
            }
        });
    }

    public void openPath(Path path) {
        try {
            if (Files.isRegularFile(path) && java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
            else Platform.openFolder(path);
        } catch (Exception error) {
            notice(Notice.Level.ERROR, I18n.t("Could not open {0}", path.getFileName()), Text.describe(error), null, null);
        }
    }

    public void browse(String url) {
        try { Platform.browse(url); }
        catch (IOException error) { notice(Notice.Level.ERROR, I18n.t("Could not open the browser"), url, null, null); }
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
        if (text.startsWith("[") && text.indexOf("] ") > 0 && text.indexOf("] ") < 60) text = text.substring(text.indexOf("] ") + 2).trim();
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
