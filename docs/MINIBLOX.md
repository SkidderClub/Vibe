# Miniblox

The multiplayer browser automatically adds **Miniblox** to `servers.dat`. Drag it to
reorder it, or edit its name/resource-pack preference. Its reserved address remains
`miniblox.io`; Delete and the Delete key are disabled for this entry. Filtering,
account-specific join history, double-click, Enter, Join and Reconnect work normally.

Joining opens a cancellable installation screen. The first connection downloads
[7GrandDad's Miniblox Translation Layer](https://github.com/7GrandDadPGN/MinibloxTranslationLayer)
at revision `842d2fd9779982bb8689a536c9f91e7f428eb7b0` and installs its production npm
dependencies with lifecycle scripts disabled. Later connections reuse that installation.
Windows automatically downloads portable Node.js 24 when Node 22+ with npm is absent,
verifying its archive against Node's official SHA-256 manifest. Other operating systems
can use an existing Node 22+ installation with npm on PATH.

Everything lives under `<game directory>/vibe/miniblox/`, including `install.log`,
`bridge.log`, npm's cache and the upstream license/source. This directory is ignored by
Git. The upstream `/login <token>` command stores `login.token` in the installed layer's
directory. Treat that file as private.

The separate bridge listens only on `127.0.0.1`, on an OS-assigned port. Vibe waits
for its ready signal before connecting and retains `miniblox.io` as the saved identity.
It reuses the process across reconnects, closes it at game exit, and the bridge also
exits when its parent input pipe closes after a game crash. Installation/downloads run
on a background worker; errors offer Retry and preserve diagnostic logs.

## Playing other game modes

The installed bridge defaults to `kitpvp` (FFA). It also handles `/play <mode>` and
`/queue <mode>` locally to reconnect to another Miniblox game. For example:

```text
/play skywars
/play eggwars
/play survival
/play creative
```

Type `/play ` or `/queue ` and press Tab to list/cycle the available identifiers.
The pinned handler offers `skywars`, `eggwars`, `spleef`, `survival`, `creative`,
`duels_bridge`, `blockhunt`, `parkour`, `oitq`, `kitpvp`, `blitzbuild`, `murder` and
`pvp`. These are the bridge's supported queue identifiers; actual game availability
is determined by Miniblox's matchmaking service.

EggWars defaults to the `quads` configuration. The handler also accepts a compact
JSON configuration, for example `/play eggwars {"type":"quads"}`. `/join <server ID>`
joins a specific server and `/next` requests another match. The upstream README
mentions `/q`, but the installed revision's local handler only registers `/play`
and `/queue`; use those full commands.

Verified against the installed, unmodified revision's
[chat handler](https://github.com/7GrandDadPGN/MinibloxTranslationLayer/blob/842d2fd9779982bb8689a536c9f91e7f428eb7b0/miniblox/handlers/impl/misc.js)
and [queue request](https://github.com/7GrandDadPGN/MinibloxTranslationLayer/blob/842d2fd9779982bb8689a536c9f91e7f428eb7b0/index.js).

## Movement compatibility

Only connections to Vibe's own managed bridge install the Miniblox packet adapter.
It sends `miniblox:movepacket` before each outgoing C03 movement packet once a player
exists. The runtime-owned endpoint identifies the bridge, since Forge can finish its
vanilla handshake after upstream has already sent `layer:player`. The layout follows upstream `MovePacket.java` and
`miniblox/handlers/impl/entity.js`: three doubles, yaw/pitch and forward/sideways floats,
jump/sneak/on-ground/sprint booleans, and the current optional using-item boolean.

The snapshot is captured at the walking packet boundary after MoveFix applies its
rotation scope. Corrected keyboard inputs, reported positions, outgoing packet rotations
and NoFall's final ground flag are preserved. The adapter is downstream of Vibe's packet
delay handler, so cancelled movement produces no payload and released movement keeps
its payload adjacent. Normal Minecraft servers and singleplayer do not use this adapter.

The upstream translation layer is distributed separately under **AGPL-3.0-or-later**;
its unmodified source and `LICENSE.md` remain in the installation. Vibe's launcher wrapper
only configures its local listener and process lifetime. Miniblox's online availability
and protocol compatibility still depend on Miniblox and the upstream project.
