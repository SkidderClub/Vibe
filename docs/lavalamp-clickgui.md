# Lavalamp ClickGUI and menu styles

Select ClickGUI → Theme → Lavalamp. The shared module/setting model, bindings, module sorting and profile persistence remain in use. Categories run across a compact top bar. Left-click a module to toggle it and open its settings; right-click to open settings without toggling. Keybind and enable controls stay visible above the scrolling settings. Drag the free part of the title bar to move the window, and drag the lower-right grip to resize both dimensions. The same workspace retains its size, position, category and independent scroll positions when reopened.

Controls include animated glowing boolean switches, typed numbers, two-handle ranges, wrapped mode choices and multiselects, strings, keybinds, and clickable colour swatches that open an HSV/opacity picker. Colour controls display the actual colour and transparency instead of hexadecimal codes. The mouse wheel scrolls the pane without changing sliders. The exact main-menu glass/button renderer supplies the background and controls, with no dark sheets covering the lava. Scrolling, switches and highlights ease smoothly. Font lines are rasterized at physical GUI scale and cached.

Mode and multiselect values sit beside their labels and expand their choices underneath. Category buttons stretch across the available header; compact windows retain horizontal scrolling. ClickGUI glass uses a lighter tint and a smaller blur radius with a bounded half-resolution blur target. Main-menu and HUD glass retain their existing frost settings.

Searching searches every category, including module names, setting names and mode options. Name matches precede setting matches while retaining the chosen module sorting within each group. A changed query resets the list offset, and drawing and mouse handling use the same clipped hit rectangles.

The main menu theme browser also offers Minimalistic and Skeet. These styles apply when outside a world; in-game menus retain their existing presentation. In-game Lavalamp HUD elements and the Lavalamp ClickGUI use rainbow wax colours with either preset. Shader presets still select matching palette colours. The thumbnail queue prioritizes visible cards and then one row above and below, making one 128×72 snapshot at a time; off-window snapshots are discarded.

Custom-theme editing renders the draft shader and colours live without changing the saved selection. Save applies the complete draft; Cancel restores the selected theme. Editor shader programs and framebuffers are released when done.

Validation: `MenuThemesTest`, `verifyLavalampClickGui`, `verifyMenuGlass`, `verifyFreecamProduction`.
