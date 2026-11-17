# Bundled script fonts

Vibe bundles two small display fonts for its optional custom-language UI. They
are used only when the corresponding Language module option is selected.

| Font | Local file | Source | License |
| --- | --- | --- | --- |
| Sga Regular | `src/main/resources/assets/vibe/fonts/StandardGalactic-Regular.ttf` | [standardgalactic/alphabet](https://github.com/standardgalactic/alphabet), `core/fonts/Sga-Regular.ttf` | [CC0 1.0](STANDARD-GALACTIC-CC0-1.0.txt) |
| Aurebesh Rodian | `src/main/resources/assets/vibe/fonts/Aurebesh-Rodian.otf` | [AurekFonts/Aurebesh_Rodian](https://github.com/AurekFonts/Aurebesh_Rodian) | [MIT](AUREBESH-RODIAN-MIT.txt) |

The fonts retain their original names and are loaded locally. No font is
downloaded by the client at runtime.

## Novoline fonts

The Novoline Client arraylist option uses the supplied SF.ttf. Dropdown and notification icons use stylesicons.ttf. Novoline Vanilla uses Minecraft's bitmap font. See NOVOLINE_PORT.md for provenance and licensing information supplied with the checkout.

## Diablo fonts

The supplied sfregular.ttf, guiicons.ttf and checkmark.ttf are used by the Diablo ClickGUI and TargetHUD. See DIABLO_PORT.md for the source paths and available licensing information.
