# Phone / VibeOS

Enable **Fun → Phone** in ClickGUI (Right Shift), or type `.toggle Phone` in chat.
Bind the module to any toggle key in ClickGUI or with `.bind Phone P`.
The phone raises into your left hand in first person. It uses your player's skin
and shows the normal world and held item alongside it. Menus and third person hide
the phone; a game in progress pauses while you use another app or menu.

## Controls

| Key | Action |
| --- | --- |
| Numpad 8 / 2 / 4 / 6 | Up / down / left / right |
| Numpad 5 | Open an app, select, take a photo, flap, or restart |
| Numpad 0 | Home |
| Numpad decimal | Back / cancel |
| Numpad 7 | Delete the selected photo, with confirmation |

The keys are configurable. In **Settings**, select a control and press 5, then
press its replacement key. Esc cancels. Duplicate control keys and the module's
toggle key are rejected. **Reset controls** restores the numpad layout. A key used
by the phone is consumed so it does not also drop an item or toggle another module.

## Apps

- **Settings:** phone finish, wallpaper style, the top and bottom gradient colors,
  brightness, size, hints, shutter sound, and every control. Phone's ClickGUI
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
- **Contacts:** ESound is the only contact, at `+67 1874/2069`. Select the contact,
  then choose Call or Message with left/right and Select.
- **Telephone:** Select calls ESound. The call stays on the calling screen forever;
  it never connects. Home returns to your apps; reopening Telephone returns to the
  call. Disabling Phone ends the call.
- **Messages:** Select (Numpad 5 by default) sends one of seven preset messages to
  ESound. The Discord image message includes the supplied image attachment.
  Up/down scroll the conversation. The last 64 messages persist locally in
  `<game directory>/vibe/phone/messages.json`. Calls and messages are VibeOS-only.
- **Internet:** opens [@heisthacksjp on YouTube](https://www.youtube.com/@heisthacksjp)
  in your system browser. The phone has a channel card and an Open YouTube button
  to reopen it. Playback and sign-in happen in that browser.

Photos live in `<game directory>/vibe/phone/photos/`. With `run.bat` this is
`run/client/vibe/phone/photos/`. Existing photos load again on later launches.
The four supplied gallery pictures are installed once per profile. Deleting them
in Photos keeps them deleted. The avatar, attachment and gallery assets are bundled
under `assets/vibe/`; the temporary import directory is no longer needed.

The bottom-left control overlay is off by default. Enable **Show controls** in the
phone's Settings app if you want it back. In-app button labels remain visible.

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
