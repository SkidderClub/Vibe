# Server browser

The Multiplayer button opens Vibe's server browser. It uses the selected main-menu shader and menu theme, antialiased rounded controls, short hover/reorder animations and a compact layout at smaller GUI sizes.

- Search saved servers by name or address. Hidden-address mode also hides addresses in the browser and search.
- The list spans the window width, with compact rows and 28px server icons. The footer follows vanilla: Join Server, Direct Connect, Add Server on the first row; Edit, Delete, Refresh, Cancel below. Double-click or press Enter to join. F5 refreshes pings; Ctrl+F focuses search; arrow keys select servers.
- Drag a saved row to move it. The new order is saved to Minecraft's `servers.dat` once, on release. Dropping outside the list cancels. The list scrolls when dragging near its edges. Filtering keeps the relative order of hidden servers. LAN servers are discovered automatically and cannot be reordered.
- The account button includes the current skin face and opens the existing account manager. Every row's result belongs to the currently selected account and server address.

Join results are stored locally in `vibe/server-results.json`, separately from module profiles. No authentication tokens are stored. A green Joined badge requires entering a remote world and remaining connected for 1.5 seconds. A red Banned badge identifies ban/blacklist/suspension messages; other login/network failures receive an amber Failed badge. Untried accounts are neutral. A subsequent ban replaces a successful result; ordinary disconnects preserve it. Cancelling a connection preserves the previous result. Hover the result badge to see the account, time and last disconnect reason.

Rendering visits only visible rows and caches text, badge textures, icons and tooltips. Icon eviction removes one old entry instead of clearing the entire cache. Ambient shader rendering uses a smaller pixel budget at 10 FPS; controls and scrolling retain the client's frame rate. Native large-list checks write local timing measurements to `build/client-changes-check/server-browser-performance.txt`.

Server-list pings never count as successful joins. The results describe the last observed connection; they cannot predict a server's future decisions. Ban detection uses the server's disconnect message.

Verification: `gradlew.bat test verifyServerMenuRendering -PvibePublicBuild`. The render check uses temporary server lists and synthetic account results, without contacting external servers, and writes screenshots to `build/client-changes-check/server-browser-*.png`.
