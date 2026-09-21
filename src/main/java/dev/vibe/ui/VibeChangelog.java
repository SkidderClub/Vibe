package dev.vibe.ui;

/** Static release notes used by the expandable main-menu changelog. */
public final class VibeChangelog {
    private static final Entry[] ENTRIES = {
            new Entry("v0.0.6", new String[] {
                    "+ ClickGUI now opens from the main menu; games and editors remain usable outside a world",
                    "+ Reconnect and generated cracked-account actions on disconnect screens, with local encrypted statistics",
                    "+ Rebuilt HUD ArrayList, efficient masked blur, stable outline/text glow and LiquidGlass controls",
                    "+ Custom cosmetics: smooth China Hat, trail, jump circle and KillAura circle with per-effect options",
                    "+ Music media panel, system-video thumbnail artwork and visualizer improvements",
                    "+ Faster NES runtime, ROM library selection and RetroArch launch support",
                    "+ Picken Switch timing, 3D hitmarker, Chams depth handling and block/stat tracking fixes",
                    "+ Statistics dashboard with account/global views, graphs and encrypted local records",
                    "+ Name Protect, Flag Detector, ChestStealer title filtering and AimAssist Boost Aim controls",
                    "+ New Hypixel helpers: Murder Mystery detection, BlockParty movement and PitBot pathing",
                    "+ Gothaj-style LagRange, TickBase and TimerRange combat modules; FakeLag removed",
                    "~ Startup logging, first-run profile migration, themes, scoreboard and blur rendering refined"
            }),
            new Entry("Unreleased", new String[] {
                    "+ Licenses & credits in the main and pause menus, with offline texts and Schizoid attribution",
                    "+ run-fresh.bat starts with a separate empty profile every time",
                    "+ Expandable, scrollable changelog opened by a small menu button",
                    "+ Six saved colour themes for menus and account screens",
                    "+ Minecraft skin faces in the active profile and account list",
                    "+ Vibe Accounts: Microsoft login, account switching and offline profiles",
                    "+ Encrypted account storage and Vibe backup file/folder import",
                    "+ Direct cookie login with Microsoft Netscape cookie exports",
                    "+ Automatic cookie folder import with valid/invalid/error results",
                    "~ Correct profile skins after account switching, with square skin heads",
                    "~ No vanilla-menu flash when returning from another screen",
                    "~ Centred main menu with smooth rounded corners and subtle buttons",
                    "~ Alt Manager replaces Realms; Discord and shaders now sit inside the menu",
                    "~ Prestige shader enabled by default on the first launch",
                    "~ Cleaner account screens, active-profile badges and a separate More menu"
            }),
            new Entry("v0.0.5", new String[] {
                    "+ Cosmetica cosmetics, preset editor and friend-specific looks",
                    "+ 629 locally bundled accessory models, textures and catalogue previews",
                    "~ Cosmetic rendering, skin previews and catalogue scrolling rebuilt for 1.8.9"
            }),
            new Entry("v0.0.4", new String[] {
                    "+ BlockOverlay with animated fill and per-edge colour paths",
                    "+ Language selector and localized Vibe UI labels",
                    "~ W-Tab timing recode",
                    "~ Main-menu shader performance and kick-screen backdrop fixes"
            }),
            new Entry("v0.0.3", new String[] {
                    "+ W-Tab, Debug, CPS, CPS Graph, BedESP and BlockChangeESP",
                    "+ FakeLag and Backtrack modules",
                    "~ NES runtime, HUD defaults and packet-order handling"
            })
    };

    private VibeChangelog() { }
    public static Entry[] entries() { return ENTRIES.clone(); }

    public static final class Entry {
        public final String version;
        public final String[] lines;
        private Entry(String version, String[] lines) { this.version = version; this.lines = lines; }
    }
}
