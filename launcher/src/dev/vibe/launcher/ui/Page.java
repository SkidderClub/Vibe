package dev.vibe.launcher.ui;

/** The sidebar destinations. */
enum Page {
    HOME("Home", Icons.HOME),
    MODS("Mods", Icons.MODS),
    ACCOUNTS("Accounts", Icons.ACCOUNTS),
    APPEARANCE("Appearance", Icons.APPEARANCE),
    SETTINGS("Settings", Icons.SETTINGS),
    CONSOLE("Console", Icons.CONSOLE);

    final String label;
    final Icons icon;

    Page(String label, Icons icon) {
        this.label = label;
        this.icon = icon;
    }
}
