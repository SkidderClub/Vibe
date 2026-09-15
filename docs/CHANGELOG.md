# Vibe changelog

## Unreleased

- Moved the changelog into `docs/` and documented the project layout; Windows build/run launchers remain in the project root.
- Added Licenses & credits in the main and pause menus, with Schizoid attribution, offline license texts, scrolling and source links. Consolidated license texts, THIRD_PARTY_NOTICES.md and review evidence in LICENSES/.
- Added Schizoid-derived Fog with depth-based Kawase/Gaussian blur, custom colors, animated rainbow and sky controls; Fog and CustomCrosshair also render in GTA7.
- Added the expanding reflective Torus option to Hitmarker, with the complete ported mesh, normals and noise/reflection shaders.
- Added Music with a draggable cover-art HUD, Windows media titles/timeline, MP3 internet radio and configurable audio-reactive waves, bars and lines from radio PCM or Windows output loopback.
- Added run-fresh.bat for a separate empty first-start profile on every launch; run.bat continues to use its existing settings and accounts.
- Added a small Changelog toggle that expands the scrollable release history alongside the menu, or over it on small windows, with Escape/outside-click dismissal.
- Added six persistent menu/account colour presets: Lavender, Ocean, Mint, Rose, Amber and Graphite.
- Added automatic Netscape cookie folder import at `vibe/cookies`, with saved-account deduplication, valid/invalid/error folders, per-file result notes and folder/pause controls under More.
- Added Vibe Accounts in the main menu and multiplayer server list, with Microsoft browser sign-in, saved account switching, offline profiles and launcher-session restoration.
- Added encrypted account storage, automatic Microsoft token refresh, cancellation and readable authentication errors.
- Added encrypted Vibe backup file/folder import with duplicate handling and backup export.
- Added Cookie login for exported Microsoft session cookies in Netscape .txt format, with expiry/domain/path validation, OAuth PKCE, encrypted refresh-token storage and browser-login guidance when confirmation is required.
- Documented the pinned Schizoid sources and AGPLv3/MIT terms, bundled their license texts, and added matching source archives plus the `.source` command.
- Fixed skin heads using stale launcher properties after account switching; public profiles now resolve independently, with name lookup for offline profiles and retries after temporary failures. Skin faces render square without a rounded frame.
- Removed the vanilla first frame when returning to the main menu and forced newly resized shader buffers to render immediately.
- Centred the main-menu controls and matched the account manager with smooth, antialiased rounded panels, subtle buttons and an active-profile card that also fits small windows.
- Replaced Realms with Alt Manager, removed the language globe and grouped Discord/shader controls inside the menu.
- Replaced profile initials with asynchronously loaded Minecraft skin faces, including the hat layer and standard-skin fallback.
- Enabled the bundled prestige.frag shader by default on first launch; saved shader choices remain respected.
- Redesigned account screens with rounded panels, profile initials, active-account badges, responsive layouts and clearer status messages. Moved offline, launcher and backup tools into More, and matched the Microsoft code, offline-profile and removal screens to the new style.
- Fixed cookie login rejecting Microsoft's normal redirects with a trailing empty `#`, which incorrectly reported that browser confirmation was required.
- Cookie login now uses Minecraft Java authentication directly, removing the extra IAS consent step for valid Microsoft sessions.
- Saved accounts retain their issuing OAuth application for correct token refresh; existing version 1 vaults and backups remain readable.
- Cookie login now distinguishes missing application consent, interactive sign-in requirements and declined access.

## v0.0.5

- Added catalogue paging and search for an offline snapshot of every published catalogue accessory, including 1.8.9-safe local PNG previews; the per-installation asset cache remains a fallback for future entries.
- Added a 1.8.9 JSON model renderer for authored element rotation and rotated face UVs, attached to the matching head, body, arm or leg pose.
- Reworked Cosmetics around editable, persistent presets for the local player and individual friends.
- Replaced all generated stand-in cosmetics with the public Cosmetica catalogue: presets now equip the original authored model JSON and texture from the catalogue.

## v0.0.4

- Added BlockOverlay with independent outline/fill colour modes, directional fades/rainbows and three block-break animations.
- Added the Language module and first-run flag selection for English, Chinese, Russian, Japanese and Bavarian interface labels.
- Reworked Silent and Strict MoveFix around shared effective rotation plus walking-packet transport; the camera is no longer reassigned while silent correction is active.
- Reworked W-Tab timing around chance, release delay, re-press delay and vulnerable-hit selection; added Debug messages for backtracked hits and active FakeLag queueing.
- Reworked the main menu into a Skeet surface with an in-menu changelog, capped-resolution/throttled shader rendering, and a safe vanilla kick/disconnect backdrop.

## v0.0.3

- Added W-Tab, optional local debug messages, CPS and CPS Graph HUD elements.
- Added BedESP, BlockChangeESP, FakeLag and Backtrack modules.
- Added a Skeet-style main menu while retaining third-party menu buttons.
- Improved particle glyphs, FastPlace CPS accounting, HUD defaults and Inventory Editor copy.
- Optimized the NES frame/input path and bundled runtime loading.
- Tightened delayed-packet ordering: inventory, interaction, transaction, correction and keep-alive traffic now bypass delay queues safely.
