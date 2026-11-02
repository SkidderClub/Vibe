package dev.vibe.ui.menu;

/** Static release notes used by the expandable main-menu changelog. */
public final class VibeChangelog {
    private static final Entry[] ENTRIES = {
            new Entry("2 November 2026", new String[] {
                    "+ TNT ESP with predicted explosion positions, fuse timers and customisable outlines and glow",
                    "+ Crab Sprint Reset with WTap, Sneak and NoStop modes",
                    "+ Custom Chat, Hotbar, Health and Tab HUD elements with independent styling",
                    "~ Minecraft-style health and hunger, Fortnite health bars, cleaner hotbars and compact Tab layouts",
                    "~ Chat fits its messages, with a matching input field and smooth chat and Tab animations",
                    "~ Fixed black enchanted items and improved Minecraft font readability on resized HUD elements",
                    "~ All HUD theme choices are directly visible; chat width and height resize independently",
                    "~ Expanded analogue clock colours, optional digital text and transparent backgrounds",
                    "~ Lava-lamp styling for startup, the HUD designer, shader selection and Miniblox loading",
                    "+ Lightweight cached shader previews",
                    "~ Refined menu buttons, status tags, scrollbars, rounded skin faces and title readability",
                    "+ Loaded-extension count in the main menu",
                    "~ Cleaner account manager with Random offline and Cookie folder under Add account",
                    "~ Improved Animus performance, movement, sensitivity and hit registration; starts in first person",
                    "~ Fixed Augustus ClickGUI opening stalls"
            }),
            new Entry("1 November 2026", new String[] {
                    "+ Breeze ClickGUI theme",
                    "~ Redesigned main menu, multiplayer menu and account manager"
            }),
            new Entry("31 October 2026", new String[] {
                    "+ Target Area bounds in Targets, positioned with .pos1 and .pos2",
                    "+ AutoRespawn with a configurable delay",
                    "+ AutoBuff with AutoPot, AutoSoup, AutoHead, AutoGapple and automatic refilling",
                    "+ Crafter module for automatic crafting",
                    "+ .vomit and .clear commands to drop your inventory",
                    "+ New Vibe logo, Ghosts HUD element and animated startup screen",
                    "~ Fixed AIAura LockFOV",
                    "+ Experimental AIAura with trainable combat profiles"
            }),
            new Entry("30 October 2026", new String[] {
                    "+ SmoothF5 for animated perspective changes",
                    "+ Reconnect and random-account reconnect buttons in the pause menu",
                    "+ Chest-screen buttons to disable active KillAura and ChestStealer modules",
                    "+ Counter-Strike-style hitmarker holograms"
            }),
            new Entry("28 October 2026", new String[] {
                    "+ Chatter for chat-triggered messages, automatic registration, login and server actions",
                    "+ Seasonal easter eggs",
                    "~ Improved AutoWalk navigation in water"
            }),
            new Entry("27 October 2026", new String[] {
                    "~ Miniblox support expanded to more game modes",
                    "+ AntiAFK with Freecam support",
                    "+ AutoWalk with pathfinding and improved combat navigation",
                    "+ Tetris in the Phone module",
                    "+ DamageESP for floating health-change indicators",
                    "~ Chat-command completion now cycles through suggestions with repeated Tab presses"
            }),
            new Entry("26 October 2026", new String[] {
                    "+ Animated chunk loading",
                    "+ Fog colour modes",
                    "+ Miniblox.io support",
                    "+ Inner-glow modes for ItemGlow",
                    "~ Mouse-wheel scrolling no longer changes setting sliders",
                    "~ Refined PacketV3, SuizideX and Novoline ClickGUI panels"
            }),
            new Entry("25 October 2026", new String[] {
                    "+ PacketV3, SuizideX and Novoline ClickGUI themes",
                    "+ Custom server browser with ban markers",
                    "~ Updated launcher to reduce build failures and startup issues",
                    "+ More Phone contacts and interactions",
                    "+ Multiple TargetHUDs with smooth entry and exit animations",
                    "~ Sliders now support dragging with laptop touchpads",
                    "~ Fixed Out of FOV Arrows and Hypixel Murder Mystery's Silent Murder mode",
                    "~ Fixed ArrayList text glow and Chams occluded colours on armour",
                    "~ Fixed backgrounds in Direct Connect and Edit Server"
            }),
            new Entry("24 October 2026", new String[] {
                    "+ TargetHUD styles including Novoline, Rise, ZeroDay and LiquidGlass",
                    "+ Analogue HUD clock",
                    "+ Full-border outlines for the HUD ArrayList",
                    "+ Static, rainbow and fade colours for CustomScoreboard domains",
                    "+ Out of FOV Arrows in Visuals",
                    "~ Improved HUD previews and item-rendering performance",
                    "~ Fixed a Freecam crash"
            }),
            new Entry("23 October 2026", new String[] {
                    "~ Improved Hypixel Murder Mystery path lines and knife detection",
                    "~ Fixed BedAura and improved movement compatibility on Hypixel",
                    "~ Cleaner ESP text backgrounds and inventory item previews",
                    "~ Consistent client chat formatting with an animated chat tag",
                    "~ Improved CustomModels and removed broken models",
                    "~ Improved ChestStealer title filtering, Scaffold and TickBase",
                    "+ Online configs through the config editor or .onlineconfig list and .onlineconfig load",
                    "+ Initial online configs for Hypixel and GrimAC"
            }),
            new Entry("21 October 2026", new String[] {
                    "+ ItemGlow",
                    "~ Improved Phone and notification visuals",
                    "~ Fixed module category issues",
                    "+ HUD notifications for module toggles",
                    "+ Toggle sounds",
                    "+ NoHurtCam"
            }),
            new Entry("Phone update", new String[] {
                    "+ Phone module"
            }),
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
            new Entry("Earlier menu and account updates", new String[] {
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
