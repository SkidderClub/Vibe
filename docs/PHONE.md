# Phone / VibeOS

Enable **Meme → Phone** in ClickGUI (Right Shift), or type `.toggle Phone` in chat.
Bind the module to any toggle key in ClickGUI or with `.bind Phone P`.
The phone raises into your left hand in first person. It uses your player's skin
and shows the normal world and held item alongside it. **Hide sometimes** is on
by default: menus, third person, and F1 hide the phone. Turn it off to keep the
phone visible in those situations. The phone still requires a loaded world and
player. Menus retain their own keyboard input and pause the phone's game.
**Hide Thumb** is off by default. The upright, articulated thumb follows selection
and taps, with a connected grip that settles back after an interaction.

## Controls

| Key | Action |
| --- | --- |
| Numpad 8 / 2 / 4 / 6 | Up / down / left / right |
| Numpad 5 | Select, take a photo, flap, or toggle Tetris autoplay |
| Numpad 0 | Home |
| Numpad decimal | Back / cancel |
| Numpad 7 | Delete a photo with confirmation, or hard-drop in Tetris |

The keys are configurable. In **Settings**, select a control and press 5, then
press its replacement key. Esc cancels. Duplicate control keys and the module's
toggle key are rejected. **Reset controls** restores the numpad layout. A key used
by the phone is consumed so it does not also drop an item or toggle another module.

## Apps

- **Tetris:** a full 10×20 board, seven-piece shuffle, wall kicks, ghost landing,
  three next-piece previews, line-clear animation, level/combo scoring and a saved
  personal best. Numpad **4/6** move, **8** rotates, **2** soft-drops and **7**
  hard-drops (or starts a new round after game over). **5 toggles autoplay**; the
  placement player rotates and moves pieces, clears lines, and restarts after game
  over. Turning autoplay off returns the current round to manual play. These use
  the phone's rebindable Left, Right, Up, Down, Delete and Select controls. Games
  pause when another app is open or the phone is hidden. Key hints use keycaps.

- **Settings:** phone finish, wallpaper style, the top and bottom gradient colors,
  brightness, size, hints, shutter sound, Hide sometimes, Hide Thumb, and every control. Phone's ClickGUI
  settings also accept custom colors. Changes use your existing Vibe profile.
- **Camera:** live world viewfinder, focus marks, composition grid and shutter
  flash. Press Select to capture. Images are full-resolution PNGs captured before
  the HUD and phone. Files are written in the background; a notification reports
  success or failure.
- **Photos:** a thumbnail album and full image viewer. Left/right move between
  photos. Back returns to the album. Delete opens a confirmation; Select deletes
  the PNG, Back keeps it. Only VibeOS photos are listed and managed.
- **Flappy Bird:** flap through scrolling pipes, score points, restart after a
  collision, and keep a personal best across sessions. Movement uses fixed time
  steps and pauses outside the app. Pipes and the bird are clipped to the playfield,
  including when the phone is tilted or scaled.
- **Contacts:** ESound, Donald J Trump, Lucky, Richy, Bipas, Vogel, and Carl.
  All seven contacts stay visible. Up/down selects a person; left/right chooses
  Call or Message in the action bar, and Select performs that action immediately.
  The five imported portraits live under
  `assets/vibe/textures/phone/`, renamed to the contact IDs. WebP pictures are
  converted losslessly to PNG for Minecraft's image loader. Carl has an initials avatar.
  These are scripted fictional conversations; Trump's profile is explicitly a parody.
  Numbers are randomly assigned display labels in French (+33), German (+49),
  Senegalese (+221), and Indian (+91) mobile formats. They stay the same between
  sessions. Trump's label is `911`; ESound keeps his original number. Nothing is dialled.
- **Telephone:** All contacts appear in a list before calling. Up/down selects a
  person; Select calls them and plays the supplied ringing clip once. The home
  icon and Call buttons use a smooth, curved handset with rounded earpieces.
  The call ends after the final audio
  samples finish, then that contact sends a reply. Changing contacts during a call
  keeps its caller, portrait, and eventual reply attached to the original contact.
  **Call again** starts a new call. Home returns to your
  apps while the clip continues. Disabling Phone or disconnecting stops playback.
  **Call volume** and Minecraft's master volume control the clip. An unavailable
  audio device ends the attempt cleanly instead of trapping you in a call.
- **Messages:** Opens a conversation list with portraits, the latest message,
  timestamps, typing status, and unread badges. Up/down chooses a chat and Select
  opens it without sending anything. Back returns to the list; Back again goes home.
  Browsing the list does not mark messages read. Inside a chat, Select (Numpad 5
  by default) sends a random message from that contact's pool, avoiding the previous outgoing message when
  there is more than one choice. ESound retains all seven original messages.
  His Discord image message includes the supplied image attachment.
  Replies arrive after an animated typing indicator. Each contact has their own
  responses, including Lucky's French banter, Richy's German ragebait replies,
  Bipas's thanks, Carl's sales pitches, and the Trump parody's evasive paperwork
  and conspiracy jokes. Bipas only accepts the positive preset messages.
  Vogel's only preset is `I want augustus key`: sending it immediately blocks
  further messages and calls, including a call already in progress. Opening another
  app or disconnecting does not lift the block; disable and re-enable Phone to reset it.
  Incoming messages have gray
  bubbles; your messages are blue. New replies play a soft chime (controlled by
  **Message sound**) and use the HUD notification widget when it is selected.
  Those cards follow the widget's Vibe, Skeet, or LiquidGlass theme, colors,
  duration and stack settings. The home screen shows an unread Messages badge.
  Opening a chat marks only that conversation read; scrolling up preserves your place
  when another reply arrives. Up/down scroll the conversation. The last 64 messages persist locally in
  `<game directory>/vibe/phone/messages.json` for ESound, and in
  `<game directory>/vibe/phone/conversations/<contact ID>.json` for the other contacts.
  Each history is separate, and the home badge counts unread messages across contacts.
  Calls and messages are VibeOS-only.
  Existing outgoing-message files remain readable, and incoming messages and their
  read state survive relaunches. Replies continue in other apps or while the phone
  is hidden; disabling Phone cancels replies that have not arrived yet.
- **Internet:** opens [@heisthacksjp on YouTube](https://www.youtube.com/@heisthacksjp)
  in your system browser. The phone has a channel card and an Open YouTube button
  to reopen it. Playback and sign-in happen in that browser.

Photos live in `<game directory>/vibe/phone/photos/`. With `run.bat` this is
`run/client/vibe/phone/photos/`. Existing photos load again on later launches.
The four supplied gallery pictures are installed once per profile. Deleting them
in Photos keeps them deleted. The avatar, attachment and gallery assets are bundled
under `assets/vibe/`; the temporary import directory is no longer needed.
The user-supplied ringing MP3 is bundled unchanged as
`assets/vibe/phone/call-ringing.mp3`; its original loose file has been moved there.
It is decoded by the existing JLayer library on a cancellable background thread.

The bottom-left control overlay is off by default. Enable **Show controls** in the
phone's Settings app if you want it back. Every displayed key uses a bordered
keycap, including remapped keys and the cancel key during rebinding.
Apps fill the display, with larger text and home icons. There is no offset shadow
behind the phone or inset background panel around apps. ItemGlow remains a
separate, optional effect controlled by its own module.

The launch animation includes a VibeOS boot screen, and the wallpaper is generated
locally. The camera preview is copied on the GPU only while Camera is open. Photo
decoding and saving use a single background worker, with bounded image/texture
caches. Photo deletion is limited to the managed photo directory.

The phone and hand render into a transparent, supersampled surface with filtered
downscaling, so their edges stay smooth when tilted. Text uses larger glyph images
with fractional spacing, and the interface uses normal capitalization. Rendering
resources are released on disconnect; the offscreen surface is also released when
the phone closes.

## Verification

`gradlew test --tests dev.vibe.phone.* verifyPhoneRendering` checks controls,
photo storage/deletion, frame-rate-independent game physics, and renders all apps,
the skin hand, boot screen, and large and compact tilted views using real OpenGL.
The render check measures partial pixel coverage along a tilted edge, checks that
the framebuffer and viewport are restored, and verifies PNG colors/orientation and
that capture occurs before phone drawing.
Its images and isolated profile are in `build/phone-render-check/`.
