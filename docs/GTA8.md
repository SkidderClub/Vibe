# GTA 8: Los Vibes

Enable **GTA8** in the Meme category to enter Los Vibes, a standalone open city with its own
shader renderer. It is separate from GTA7 and uses none of Minecraft's world rendering.

The city covers 1.6 by 1.6 kilometres: an 11 by 11 street grid through Downtown, Midtown,
Alta, Vibe Park, Cypress Flats (industrial and harbour), Vespucci Shores (beach) and the
Mirror Heights suburbs, surrounded by hills, the VIBE sign and the ocean.

## Controls

Movement, jump, sprint, crouch, aim (use item), attack, perspective and hotbar keys follow your
Minecraft bindings.

| Key | On foot | In a vehicle |
| --- | --- | --- |
| W / A / S / D | Walk | Throttle, steer, brake and reverse |
| Mouse | Look and aim | Orbit camera |
| Sprint / crouch | Sprint (uses stamina) / crouch | Crouch toggles the siren in a police car |
| Jump | Jump | Handbrake |
| Attack / use item | Fire or punch / aim | Drive-by with the pistol or Micro SMG while aiming |
| F | Enter the nearest car (locked cars take a moment) | Get out |
| E | Shops, job board, safehouse bed | Horn |
| H | | Horn |
| R | Reload | |
| 1 to 6, mouse wheel | Select weapon | |
| Perspective (F5) | First/third person | First/third person |
| M | Map; click to set a GPS waypoint, wheel to zoom | Map |
| J | Job board | Job board |
| F1 | Hide the HUD | Hide the HUD |
| F3 (hold) | Performance overlay | Performance overlay |
| Escape | Pause menu (map, jobs, stats, settings) | Pause menu |

## Gameplay

- **Driving:** nine vehicles (Norden Stanza, Kaito Blip, Atlas Ranger, Vero Fulmine, Stallion GT,
  taxi, Norden Interceptor police car, Atlas Rancher pickup and Norden Cargo van) with realistic
  mass, power and top speed. Tyres use a Pacejka model with load transfer, gears, handbrake slides
  and reduced grip on wet roads. Crashes damage the bodywork, tyres can burst and wrecks catch fire.
- **Traffic and people:** cars follow lanes and traffic lights with an intelligent-driver model;
  pedestrians walk the sidewalks and crossings, react to gunfire and call the police.
- **Police:** crimes raise one to five stars. Witnesses phone in crimes after a delay; officers who
  see you re-centre the search circle. Stay out of sight outside the circle for 7 seconds plus
  2.5 seconds per star to escape. Officers arrest at one star and open fire from two stars. Being
  busted costs up to $1,000 and half your ammunition; the hospital bill after dying is up to $500.
- **Weapons:** fists, Pistol, Micro SMG, Carbine Rifle, Pump Shotgun and Sniper Rifle, with
  magazines, recoil, spread and head/body/limb damage. Buy weapons, ammunition and body armour
  ($500) at gun shops.
- **Jobs:** deliveries, car thefts and hits from the job board pay cash.
- **Places:** stores and gas stations (aim at the clerk to rob the register), gun shops,
  hospitals, police stations, safehouses (sleep six hours and save while not wanted) and the
  Quick Spray garage: stop inside, unseen by the police, to repair and repaint your car for $250 and clear
  your wanted level.
- **World:** a 24-hour day takes 48 real minutes. Weather cycles through clear, hazy, cloudy,
  overcast, drizzle, rain, thunderstorms and fog; rain leaves puddles and wet, reflective roads.

## Graphics

The renderer is an HDR forward renderer with cascaded sun shadows, a physically based sky with
clouds, stars and moon, height fog, PBR materials, analytic street lighting, 16 dynamic lights,
wet surfaces, water with foam, particles, bloom, ACES tone mapping and FXAA. Module settings:

| Setting | Values |
| --- | --- |
| Graphics | Low, Medium, High (default), Ultra |
| Render Scale | 50 to 200% (default 100%) |
| Traffic Density | Low, Medium (default), High, Very High |
| Bloom, FXAA, Game Audio | On by default |

## Saves

Money, weapons, ammunition, armour and statistics are saved to `vibe/gta8-progress.json` in the
game directory, encrypted with AES-GCM using the key in `vibe/.gta8-save.key`. The previous save is
kept as `gta8-progress.json.bak` and used automatically if the primary file is damaged. Keep the
key file together with the save when copying it to another computer.

## Verification

```powershell
.\gradlew test --tests "dev.vibe.game.gta8.*"
.\gradlew verifyGta8Rendering
```

`verifyGta8Rendering` renders the city, actors, HUD and a vehicle and character gallery offscreen
and writes the screenshots and frame timings to `build/gta8-render-check/`. Pass
`-Pgta8Only=<prefix>` to render only matching shots, for example `-Pgta8Only=g` for the gallery.
