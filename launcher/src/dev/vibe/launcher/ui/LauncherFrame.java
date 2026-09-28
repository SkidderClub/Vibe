package dev.vibe.launcher.ui;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.app.LauncherController.State;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Settings;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/** The launcher window: custom chrome, sidebar, pages, play bar and notifications. */
public final class LauncherFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int EDGE = 5;
    private final LauncherController controller;
    private final Settings settings;
    private final TitleBar titleBar;
    private final Sidebar sidebar;
    private final PlayBar playBar;
    private final CardLayout cards = new CardLayout();
    private final JPanel pageHost = new JPanel(cards);
    private final Map<Page, JComponent> pages = new EnumMap<Page, JComponent>(Page.class);
    private final Toasts toasts;
    private final DropOverlay dropOverlay = new DropOverlay();
    private boolean maximized;
    private Rectangle normalBounds;
    private State lastState = State.IDLE;
    private boolean minimizedForGame;
    private int resizeEdges;
    private Point resizeStart;
    private Rectangle resizeBounds;

    public LauncherFrame(LauncherController controller) {
        super(VibeLauncher.PRODUCT);
        this.controller = controller;
        this.settings = controller.settings();
        Style.setTheme(controller.theme());
        Anim.enabled = settings.animations();
        installDefaults();

        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImages(LogoMark.icons());
        setMinimumSize(new Dimension(940, 600));

        JPanel root = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(Style.background());
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            @Override protected void paintChildren(Graphics graphics) {
                super.paintChildren(graphics);
                if (!maximized) {
                    graphics.setColor(Style.strongBorder());
                    graphics.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setContentPane(root);

        titleBar = new TitleBar(this);
        sidebar = new Sidebar(controller, this::show);
        playBar = new PlayBar(controller, () -> show(Page.ACCOUNTS));
        pages.put(Page.HOME, new HomePage(controller));
        pages.put(Page.MODS, new ModsPage(controller));
        pages.put(Page.ACCOUNTS, new AccountsPage(controller));
        pages.put(Page.APPEARANCE, new AppearancePage(controller));
        pages.put(Page.SETTINGS, new SettingsPage(controller));
        pages.put(Page.CONSOLE, new ConsolePage(controller));
        pageHost.setOpaque(false);
        for (Map.Entry<Page, JComponent> entry : pages.entrySet()) pageHost.add(entry.getValue(), entry.getKey().name());

        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        main.add(pageHost, BorderLayout.CENTER);
        main.add(playBar, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(sidebar, BorderLayout.WEST);
        body.add(main, BorderLayout.CENTER);
        root.add(titleBar, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);

        toasts = new Toasts(getLayeredPane(), () -> show(Page.CONSOLE));
        controller.setNotifier(toasts::show);
        controller.setExit(this::exit);
        getLayeredPane().add(dropOverlay, JLayeredPane.DRAG_LAYER);
        dropOverlay.setVisible(false);
        installDrop(root);
        installShortcuts(root);
        installResizing();

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { responsive(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { requestClose(); }
            @Override public void windowIconified(WindowEvent event) { ((HomePage) pages.get(Page.HOME)).setWindowVisible(false); }
            @Override public void windowDeiconified(WindowEvent event) { ((HomePage) pages.get(Page.HOME)).setWindowVisible(true); }
        });
        controller.addListener(event -> {
            if (event == LauncherController.Event.THEME) {
                Style.setTheme(controller.theme());
                installDefaults();
                setIconImages(LogoMark.icons());
                getRootPane().repaint();
            } else if (event == LauncherController.Event.STATE) {
                stateChanged();
            } else if (event == LauncherController.Event.SETTINGS) {
                Anim.enabled = settings.animations();
            }
        });

        restoreBounds();
        show(Page.HOME);
        SwingUtilities.invokeLater(this::responsive);
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            private boolean first = true;
            @Override public void windowGainedFocus(WindowEvent event) {
                if (first) playBar.focusPlay();
                first = false;
            }
        });
    }

    // ---- navigation ------------------------------------------------------

    void show(Page page) {
        cards.show(pageHost, page.name());
        sidebar.select(page);
        if (page == Page.CONSOLE) ((ConsolePage) pages.get(page)).shown();
        if (page == Page.ACCOUNTS) controller.reloadAccounts();
        if (page == Page.MODS) controller.reloadMods();
        sidebar.repaint();
    }

    private void installShortcuts(JComponent root) {
        int modifier = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        Page[] all = Page.values();
        for (int index = 0; index < all.length; index++) {
            final Page page = all[index];
            String key = "page" + index;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + index, modifier), key);
            root.getActionMap().put(key, new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent event) { show(page); }
            });
        }
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifier), "play");
        root.getActionMap().put("play", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent event) {
                // Same as the play button: during installation this queues the launch.
                if (controller.state() == State.IDLE || controller.canQueueLaunch()) controller.play();
            }
        });
        // Focus rings appear after keyboard navigation and disappear on the next click.
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == KeyEvent.KEY_PRESSED && ((KeyEvent) event).getKeyCode() == KeyEvent.VK_TAB) {
                FlatButton.FocusStyle.keyboard = true;
                getRootPane().repaint();
            } else if (event.getID() == MouseEvent.MOUSE_PRESSED && FlatButton.FocusStyle.keyboard) {
                FlatButton.FocusStyle.keyboard = false;
                getRootPane().repaint();
            }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    // ---- state -----------------------------------------------------------

    private void stateChanged() {
        State state = controller.state();
        if (state == State.RUNNING && lastState != State.RUNNING && lastState != State.IDLE) {
            switch (settings.afterLaunch()) {
                case MINIMIZE:
                    minimizedForGame = true;
                    setExtendedState(getExtendedState() | ICONIFIED);
                    break;
                case CLOSE:
                    exit();
                    return;
                default:
                    break;
            }
        }
        if (state == State.IDLE && (lastState == State.RUNNING || lastState == State.STOPPING || lastState == State.BUILDING)) {
            if (minimizedForGame || !controller.lastError().isEmpty()) {
                setExtendedState(getExtendedState() & ~ICONIFIED);
                toFront();
            }
            minimizedForGame = false;
        }
        lastState = state;
    }

    void requestClose() {
        State state = controller.state();
        if (state == State.PREPARING && !Dialogs.confirm(this, I18n.t("Close the launcher?"),
                I18n.t("Vibe is still being downloaded or updated. The download stops and continues next time."), I18n.t("Close"), true)) {
            return;
        }
        exit();
    }

    private void exit() {
        saveBounds();
        setVisible(false);
        controller.shutdown();
        dispose();
        System.exit(0);
    }

    /** Restarts the launcher, e.g. to apply another language. */
    void restart() {
        if (controller.state() == State.PREPARING) {
            toasts.show(new LauncherController.Notice(LauncherController.Notice.Level.WARNING, I18n.t("Please wait"),
                    I18n.t("Restart once the current download has finished."), null, null));
            return;
        }
        saveBounds();
        setVisible(false);
        controller.shutdown();
        try {
            dev.vibe.launcher.install.LauncherUpdater.restart();
        } catch (java.io.IOException error) {
            controller.log().warn("Restart failed", error);
        }
        dispose();
        System.exit(0);
    }

    // ---- responsive layout -----------------------------------------------

    private void responsive() {
        int width = getWidth();
        sidebar.setCompact(width < 1100);
        // Account 250 + mode 236 + stop ~145 + play 212 + gaps and padding leave the status too little below this.
        int main = width - (width < 1100 ? Sidebar.COMPACT : Sidebar.WIDE);
        playBar.setCompact(main < 1100);
        ((HomePage) pages.get(Page.HOME)).setCompact(width < 1180);
        int content = width - (width < 1100 ? Sidebar.COMPACT : Sidebar.WIDE) - Pages.PADDING * 2;
        ((AppearancePage) pages.get(Page.APPEARANCE)).setColumns(content < 720 ? 2 : 3);
        ((SettingsPage) pages.get(Page.SETTINGS)).setColumns(content < 700 ? 2 : 3);
        toasts.setBottomOffset(PlayBar.BAR_HEIGHT);
        dropOverlay.setBounds(0, 0, getLayeredPane().getWidth(), getLayeredPane().getHeight());
        getRootPane().revalidate();
    }

    // ---- window chrome ------------------------------------------------------

    void minimize() { setExtendedState(getExtendedState() | ICONIFIED); }

    boolean isMaximizedCustom() { return maximized; }

    void toggleMaximized() {
        if (maximized) restoreFromMaximized();
        else maximize();
    }

    private void maximize() {
        normalBounds = getBounds();
        maximized = true;
        setBounds(usableBounds(getGraphicsConfiguration()));
        titleBar.updateMaximized(true);
        getRootPane().repaint();
    }

    void restoreFromMaximized() {
        if (!maximized) return;
        maximized = false;
        Rectangle target = normalBounds != null ? normalBounds : new Rectangle(getX() + 60, getY() + 60, 1180, 740);
        setSize(target.width, target.height);
        titleBar.updateMaximized(false);
        getRootPane().repaint();
    }

    /** Dropping the title bar at the top of the screen maximises the window, like Windows Aero Snap. */
    void snapIfAtTop(Point screen) {
        if (maximized) return;
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        if (screen.y <= configuration.getBounds().y + 2) {
            normalBounds = new Rectangle(getX(), Math.max(getY(), configuration.getBounds().y + 20), getWidth(), getHeight());
            maximized = true;
            setBounds(usableBounds(configuration));
            titleBar.updateMaximized(true);
        }
    }

    boolean isResizing() { return resizeEdges != 0; }

    private static Rectangle usableBounds(GraphicsConfiguration configuration) {
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    }

    /** Resizing an undecorated window: watch mouse events near the window edge. */
    private void installResizing() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent) || maximized) return;
            MouseEvent mouse = (MouseEvent) event;
            if (SwingUtilities.getWindowAncestor(mouse.getComponent()) != this && mouse.getComponent() != this) return;
            Point screen = mouse.getLocationOnScreen();
            switch (mouse.getID()) {
                case MouseEvent.MOUSE_MOVED: {
                    int edges = edgesAt(screen);
                    getRootPane().setCursor(Cursor.getPredefinedCursor(cursorFor(edges)));
                    break;
                }
                case MouseEvent.MOUSE_PRESSED: {
                    int edges = edgesAt(screen);
                    if (edges != 0 && mouse.getButton() == MouseEvent.BUTTON1) {
                        resizeEdges = edges;
                        resizeStart = screen;
                        resizeBounds = getBounds();
                        mouse.consume();
                    }
                    break;
                }
                case MouseEvent.MOUSE_DRAGGED:
                    if (resizeEdges != 0) {
                        resize(screen);
                        mouse.consume();
                    }
                    break;
                case MouseEvent.MOUSE_RELEASED:
                    if (resizeEdges != 0) {
                        resizeEdges = 0;
                        getRootPane().setCursor(Cursor.getDefaultCursor());
                    }
                    break;
                case MouseEvent.MOUSE_EXITED:
                    if (resizeEdges == 0 && mouse.getComponent() == getRootPane()) getRootPane().setCursor(Cursor.getDefaultCursor());
                    break;
                default:
                    break;
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private int edgesAt(Point screen) {
        Rectangle bounds = getBounds();
        int x = screen.x - bounds.x, y = screen.y - bounds.y;
        if (x < 0 || y < 0 || x >= bounds.width || y >= bounds.height) return 0;
        int edges = 0;
        if (x < EDGE) edges |= 1;
        if (x >= bounds.width - EDGE) edges |= 2;
        if (y < EDGE) edges |= 4;
        if (y >= bounds.height - EDGE) edges |= 8;
        return edges;
    }

    private static int cursorFor(int edges) {
        switch (edges) {
            case 1: return Cursor.W_RESIZE_CURSOR;
            case 2: return Cursor.E_RESIZE_CURSOR;
            case 4: return Cursor.N_RESIZE_CURSOR;
            case 8: return Cursor.S_RESIZE_CURSOR;
            case 5: return Cursor.NW_RESIZE_CURSOR;
            case 6: return Cursor.NE_RESIZE_CURSOR;
            case 9: return Cursor.SW_RESIZE_CURSOR;
            case 10: return Cursor.SE_RESIZE_CURSOR;
            default: return Cursor.DEFAULT_CURSOR;
        }
    }

    private void resize(Point screen) {
        int dx = screen.x - resizeStart.x, dy = screen.y - resizeStart.y;
        Rectangle next = new Rectangle(resizeBounds);
        Dimension minimum = getMinimumSize();
        if ((resizeEdges & 1) != 0) { next.x += dx; next.width -= dx; }
        if ((resizeEdges & 2) != 0) next.width += dx;
        if ((resizeEdges & 4) != 0) { next.y += dy; next.height -= dy; }
        if ((resizeEdges & 8) != 0) next.height += dy;
        if (next.width < minimum.width) {
            if ((resizeEdges & 1) != 0) next.x -= minimum.width - next.width;
            next.width = minimum.width;
        }
        if (next.height < minimum.height) {
            if ((resizeEdges & 4) != 0) next.y -= minimum.height - next.height;
            next.height = minimum.height;
        }
        setBounds(next);
        validate();
    }

    private void restoreBounds() {
        Rectangle saved = settings.windowBounds();
        boolean visible = false;
        if (saved != null) {
            for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                if (device.getDefaultConfiguration().getBounds().intersects(saved.x + 40, saved.y + 10, Math.max(1, saved.width - 80), 30)) visible = true;
            }
        }
        if (saved != null && visible && saved.width >= 940 && saved.height >= 600) {
            setBounds(saved);
        } else {
            Rectangle screen = usableBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
            int width = Math.min(1240, screen.width - 60), height = Math.min(780, screen.height - 60);
            setBounds(screen.x + (screen.width - width) / 2, screen.y + (screen.height - height) / 2, width, height);
        }
        if (settings.windowMaximized()) SwingUtilities.invokeLater(this::maximize);
    }

    private void saveBounds() {
        settings.setWindowMaximized(maximized);
        settings.setWindowBounds(maximized && normalBounds != null ? normalBounds : getBounds());
    }

    // ---- drag and drop ----------------------------------------------------

    private void installDrop(JComponent root) {
        new DropTarget(root, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override public void dragEnter(DropTargetDragEvent event) {
                if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { event.rejectDrag(); return; }
                event.acceptDrag(DnDConstants.ACTION_COPY);
                setDropping(true);
            }
            @Override public void dragExit(DropTargetEvent event) { setDropping(false); }
            @Override @SuppressWarnings("unchecked") public void drop(DropTargetDropEvent event) {
                setDropping(false);
                if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { event.rejectDrop(); return; }
                event.acceptDrop(DnDConstants.ACTION_COPY);
                try {
                    List<File> files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    controller.importMods(files);
                    show(Page.MODS);
                    event.dropComplete(true);
                } catch (Exception error) {
                    controller.log().warn("Drop failed", error);
                    event.dropComplete(false);
                }
            }
        }, true);
    }

    private void setDropping(boolean dropping) {
        dropOverlay.setBounds(0, 0, getLayeredPane().getWidth(), getLayeredPane().getHeight());
        dropOverlay.setVisible(dropping);
        ((ModsPage) pages.get(Page.MODS)).setDragActive(dropping);
    }

    private static void installDefaults() {
        UIManager.put("ToolTip.background", new ColorUIResource(Style.mix(Style.surface(), Style.background(), 0.2)));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Style.text()));
        UIManager.put("ToolTip.font", new FontUIResource(Style.small()));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Style.strongBorder()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        UIManager.put("PopupMenu.border", BorderFactory.createEmptyBorder());
        UIManager.put("PopupMenu.background", new ColorUIResource(Style.surface()));
    }

    /** Full-window hint while files are dragged over the launcher. */
    private static final class DropOverlay extends JComponent {
        private static final long serialVersionUID = 1L;
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                g.setColor(Style.alpha(Style.background(), 200));
                g.fillRect(0, 0, w, h);
                g.setColor(Style.accent());
                g.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND, 10f, new float[] { 10f, 8f }, 0f));
                g.draw(new java.awt.geom.RoundRectangle2D.Double(24, 24, w - 48, h - 48, 28, 28));
                Icons.MODS.paint(g, w / 2.0 - 24, h / 2.0 - 58, 48, Style.accent());
                Style.textCentered(g, I18n.t("Drop to add mods"), w / 2.0, h / 2.0, 30, Style.font(java.awt.Font.BOLD, 22f), Style.text());
                Style.textCentered(g, I18n.t("Forge 1.8.9 .jar files are copied into Vibe's mods folder"), w / 2.0, h / 2.0 + 30, 22, Style.body(), Style.muted());
            } finally {
                g.dispose();
            }
        }
    }
}
