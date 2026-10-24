package dev.vibe.launcher.ui;

/** The sidebar destinations. */
enum Page {
    HOME("Home", Icons.HOME),
    VERSIONS("Versions", Icons.DOWNLOAD),
    MODS("Mods", Icons.MODS),
    EXTENSIONS("Extensions", Icons.PLUS),
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
