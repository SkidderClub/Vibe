# ClickGUI themes

Choose **PacketV3**, **Novoline**, or **SuizideX** in ClickGUI → Theme. Existing themes and module settings are retained.

- **PacketV3:** rounded charcoal category panels, centered labels, and continuous cyan-to-blue enabled rows. Packet Color Mode offers Fade, Static and Rainbow. Packet Color, Packet Fade Color and Packet Fade Speed customize the highlights; color pickers include opacity and hexadecimal input.
- **Novoline:** compact dark panels, category icons, crisp purple enabled text, soft panel shadows and an optional accent glow, plus a purple bottom rail. Novoline Color changes the accent.
- **SuizideX:** content-sized light/mint panels, smooth rounded red-to-pink highlights and soft panel glows. Settings expand inside the panel. SuizideX Dark Mode switches the body and inactive rows to black. The two SuizideX colors control its gradient.

Left-click a module to toggle it, right-click to expand its settings, and middle-click to bind a key. Right-click a category header to collapse it; drag its header to move it. Each panel has its own scrolling and draggable scrollbar. The mouse wheel scrolls the panel even over a slider; sliders change only through clicking or dragging. PacketV3 and Novoline mode choices expand inline, using their theme colors. SuizideX modes open a selection menu. Multi-selects expand inline, and colors open an HSV/opacity/hex picker. Escape dismisses a picker before closing the GUI.

Long module names, setting labels, and values scroll inside their own clipped text areas, with a pause at either end. Panel footers reserve space for rounded corners, including while content scrolls and when headers are collapsed.

Every theme uses Vibe's categories in their normal order: Combat, Visual, Movement, World, Meme, Client and Scripts. On smaller screens a category strip appears at the bottom to reach panels outside the viewport. Shift+wheel also pans horizontally. Panels keep their positions when the GUI is reopened during the client session.

These controls use Vibe's existing settings and config system: edits are restored through session recovery; named config files change only when explicitly saved.

Verification: `gradlew.bat test verifyDropdownThemes verifyServerMenuRendering -PvibePublicBuild`. Native screenshots are written to `build/client-changes-check/packetv3-*.png`, `novoline-*.png`, and `suizidex-*.png`.
