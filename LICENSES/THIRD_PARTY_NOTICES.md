# Third-party notices

This file records third-party libraries, assets and implementation references used
by Vibe. Each component retains its own license and attribution; Vibe's
[GPLv3 license](GPL-3.0.txt) does not replace those terms.

## License scope

This is the central license and attribution guide. The project README covers
installation and use; the following files have distinct legal/documentation roles:

| Location | Purpose |
| --- | --- |
| [GPL-3.0.txt](GPL-3.0.txt) | Complete, unmodified GPLv3 text for Vibe's GPL-covered code. |
| [AGPL-3.0.txt](AGPL-3.0.txt) | Complete AGPLv3 text for the Schizoid-derived components, distributed here under AGPL-3.0-only. |
| [LGPL-3.0.txt](LGPL-3.0.txt) | LGPLv3 reference text for IAS and MinecraftAuth; incorporates GPL-3.0.txt. These libraries are not bundled. |
| [webgl-noise-MIT.txt](webgl-noise-MIT.txt) | Complete MIT permission/warranty text with the Ashima Arts and Stefan Gustavson copyright notices for the noise implementation. |
| [OFL-1.1.txt](OFL-1.1.txt) | SIL Open Font License for the bundled Noto Sans CJK font. |
| [SCHIZOID.md](SCHIZOID.md) | File-by-file Schizoid provenance, adaptations and source-distribution instructions. |

The license texts differ in length and structure because they are different
licenses. They are retained verbatim; explanatory Markdown belongs in this guide.
All license texts and supporting documents are collected in `LICENSES/`.
The component licenses supplement Vibe's GPL where applicable. They are not an
alternative choice of license for the whole JAR, and this directory is not a
complete license-text inventory for every dependency and asset listed below.

Known gaps and the steps needed to address them are recorded in
[Open distribution issues](#open-distribution-issues) below. This document records
available provenance; it does not establish a complete legal clearance of a release.

In the client, open **Licenses & credits** from the main menu or pause menu for
component credits, offline license texts, full notices and source instructions.
These documents are bundled under `META-INF/vibe/LICENSES/` and included on the development
classpath as well. Build and release source archives retain their repository paths.

## Open distribution issues

| Area | Current evidence or gap | Required follow-up |
| --- | --- | --- |
| Bundled libraries | Some published JARs omit license texts. The generated dependency index records the exact bundled artifacts and the notice files found inside each one. | Obtain missing texts and copyright notices from the matching upstream release, including native components; retain any required source and relinking materials. |
| Copied source | Schizoid has a pinned revision and file mapping. The exact JSNES snapshot and provenance of Raven compatibility helpers are not recorded. | Establish the original revisions, preserve notices and document adaptations. |
| Assets | Cosmetica creator assets, some shaders, UI images, Waifu images and Girlfriend audio lack complete license records; several shaders carry noncommercial terms. | Record the source, author, license and modifications per asset; obtain permission or replace/remove assets where redistribution cannot be established. |
| Minecraft combination | The GPL/AGPL combination clauses do not themselves grant an exception for linking with proprietary Minecraft code. | Establish the permissions needed for the distributed combination with the relevant rights holders. |
| OAuth registrations | Vibe uses third-party public application IDs; no separate permission is documented here. | Establish permitted use with the application owners and service requirements, or use a permitted Vibe registration. |

These gaps are not resolved by adding credits or a license URL. A successful build
checks documentation and packaging consistency, not permission to distribute.

## Maintaining these notices

When adding or updating a dependency, copied source file or asset:

1. Record its exact version/revision or original download URL, author, local path,
   license and Vibe modifications in the relevant section below. Preserve upstream
   copyright and attribution notices; do not infer an asset's license from its host.
2. Keep required license texts in `LICENSES/`. Documents found in bundled JARs are
   additionally preserved verbatim in a separate directory for each artifact.
   Check `META-INF/vibe/dependencies/INDEX.md` inside the built JAR for the resolved
   filenames, SHA-256 hashes, copied notice paths and artifacts missing notices.
   The source ZIP contains the same index and files under `LICENSES/dependencies/`.
3. Run `./gradlew verifyLicenseDocuments` (Windows: `.\gradlew.bat verifyLicenseDocuments`)
   to check local documentation links and the client's offline document paths.
   The same check runs during `check` and before packaging resources or sources.
4. Build with `./gradlew build` and distribute the matching `-sources.zip` alongside
   the JAR. Resolve the open issues above for the contents of that release, including
   any dependency source obligations the Vibe source archive does not cover.

## Schizoid fog, torus and media display ports

Vibe combines GPLv3 code with AGPLv3 components from
[SchizoidDevelopment/schizoid](https://github.com/SchizoidDevelopment/schizoid),
revision `ea2a0acc78cf1e6868accc30e089d65bc918f140`. Upstream copyright:
Schizoid, 2023 (shader headers) and 2024 (module headers). Vibe distributes the
derived components under AGPL-3.0-only; the separate webgl-noise implementation
is MIT. Vibe's top-level GPL notice does not relicense these components.

GPLv3 and AGPLv3 section 13 permit their combination: the respective parts keep
their licenses, and AGPLv3's network-interaction requirements apply to the
combination when their conditions are met. A Kotlin-to-Java or GLSL port remains
an adaptation; rewriting the syntax does not remove the upstream obligations.

The file-by-file mapping, original paths, changes dated 2026-09-09, and source
distribution instructions are in [SCHIZOID.md](SCHIZOID.md).
The complete [AGPLv3 text](AGPL-3.0.txt) and provenance document are also
included under `META-INF/vibe/LICENSES/` in the JAR. The noise include credits Ian McEwan
and Stefan Gustavson and retains its separate [MIT license](webgl-noise-MIT.txt).

The new Windows SMTC/WASAPI helpers and radio player are Vibe implementations.
The existing JLayer dependency decodes radio MP3; no additional media library,
upstream fonts, album covers or radio audio are bundled by this port.

## Bundled libraries

The following libraries are included in the mod JAR by [build.gradle](../build.gradle).
Versions below follow the declared dependencies; Gradle resolves transitive versions.
The generated `META-INF/vibe/dependencies/INDEX.md` records the actual bundled
versions. Each artifact's embedded legal files are copied under
`META-INF/vibe/dependencies/<original-jar-name>/`, preserving their internal paths.
This prevents equally named notices from different JARs from overwriting each other.
Extraction does not supply legal files omitted by upstream; those gaps remain
explicit in the index.

| Component | Version | Use in Vibe | License source |
| --- | --- | --- | --- |
| Janino and Commons Compiler | 3.1.12 | Compile local Java scripts | [BSD-3-Clause](https://github.com/janino-compiler/janino/blob/master/LICENSE) |
| WebP ImageIO (Sejda/Luciad) | 0.1.6 | Read WebP images, including native decoding | [Apache-2.0](https://github.com/sejda-pdf/webp-imageio/blob/master/LICENSE) |
| Nashorn Core (OpenJDK) | 15.4 | JavaScript runtime for the NES emulator | [GPL-2.0 with Classpath Exception](https://github.com/openjdk/nashorn/blob/main/LICENSE) |
| Rhino and Rhino Engine (Mozilla) | 1.7.15 | Alternative JavaScript runtime | [MPL-2.0](https://github.com/mozilla/rhino/blob/Rhino1_7_15_Release/LICENSE.txt) |
| ASM (OW2) | Resolved by Gradle | Bytecode support for bundled runtimes and Forge | [BSD-3-Clause](https://asm.ow2.io/license.html) |
| imgui-java bindings and Windows/Linux/macOS natives (SpaiR) | 1.86.11 | Augustus interface | [Apache-2.0](https://github.com/SpaiR/imgui-java/blob/v1.86.11/LICENSE) |
| JLayer (JavaZoom) | 1.0.1 | MP3 playback | [LGPL, as declared in the published POM](https://repo.maven.apache.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1.pom) |

Native libraries have additional upstream components: imgui-java includes
[Dear ImGui](https://github.com/ocornut/imgui/blob/v1.86/LICENSE.txt) (MIT), and WebP ImageIO uses
[libwebp](https://github.com/webmproject/libwebp/blob/main/COPYING) (BSD-3-Clause). Their upstream
notices also apply.

## Augustus ClickGUI design inspiration

Vibe's Augustus ClickGUI theme is inspired by the Augustus Client. Credit for
the original interface design goes to Augustus.

- Website: [Augustus Client](https://electriclauncher.de/)
- Community: [Augustus Discord](https://discord.electriclauncher.de)

## Bundled source and assets

### NES emulator

The adapted ES5 emulator in
[`src/main/resources/assets/vibe/nes/emu/`](../src/main/resources/assets/vibe/nes/emu)
is based on [JSNES](https://github.com/bfirsh/jsnes), by Ben Firshman and
contributors. Upstream is [Apache-2.0](https://github.com/bfirsh/jsnes/blob/v1.2.1/LICENSE).
The imported snapshot's exact revision is not recorded in this repository.

### Noto Sans CJK

[`NotoSansCJKsc-Regular.otf`](../src/main/resources/assets/vibe/fonts/NotoSansCJKsc-Regular.otf)
is the unmodified Noto Sans CJK SC Regular font from
[Noto CJK](https://github.com/notofonts/noto-cjk). The font remains bundled;
Augustus now uses Minecraft's bitmap font resources. The [SIL Open Font License 1.1](OFL-1.1.txt)
is stored in `LICENSES/OFL-1.1.txt` and included in the JAR.

### Cosmetica

[`src/main/resources/assets/vibe/cosmetica/`](../src/main/resources/assets/vibe/cosmetica) contains an offline catalogue from `api.cloaks.gg`
and `cdn.cosmetica.cc`. Accessory IDs and creator metadata are retained in
[`catalog.json`](../src/main/resources/assets/vibe/cosmetica/catalog.json). Models, textures, thumbnails and skins
remain the work of their respective creators; the snapshot does not record a
license for each asset.

The client/UI assets under [`upstream-ui/`](../src/main/resources/assets/vibe/cosmetica/upstream-ui) come from
[Cosmetica-2](https://github.com/Cosmetica-cc/Cosmetica-2), whose
[Apache-2.0 license](https://github.com/Cosmetica-cc/Cosmetica-2/blob/1.16.5/LICENSE)
and [NOTICE](https://github.com/Cosmetica-cc/Cosmetica-2/blob/1.16.5/NOTICE) cover that
upstream project. Its attachment semantics also informed Vibe's independently
implemented Forge renderer. The client project's license does not establish the
terms for creator-uploaded catalogue assets.

### Shaders, images and audio

Shader sources in [`src/main/resources/assets/vibe/shader/`](../src/main/resources/assets/vibe/shader) retain their embedded author and
source comments. Explicit whole-shader notices include:

| File | Credit recorded in the source | License recorded in the source |
| --- | --- | --- |
| [`minecraft.frag`](../src/main/resources/assets/vibe/shader/minecraft.frag) | Reinder Nijhoff; comments also credit Markus Persson and Necip | CC-BY-NC-SA-4.0 |
| [`rings.frag`](../src/main/resources/assets/vibe/shader/rings.frag) | SHAU | CC-BY-NC-SA-3.0 |
| [`purplewater.frag`](../src/main/resources/assets/vibe/shader/purplewater.frag) | Alexander Alekseev (TDM), "Seascape", 2014 | CC-BY-NC-SA-3.0 |
| [`panorama.frag`](../src/main/resources/assets/vibe/shader/panorama.frag) | Antoine Clappier, "Toon Cloud", March 2015 | CC-BY-NC-SA-4.0 |
| [`sandstorm.frag`](../src/main/resources/assets/vibe/shader/sandstorm.frag) | Jan Mróz (jaszunio15) | CC-BY-3.0 |

[`rickandmortylike.frag`](../src/main/resources/assets/vibe/shader/rickandmortylike.frag) also contains Hazel
Quantock's 2018 humanoid SDF notice under CC-BY-NC-SA-4.0; that notice alone does
not establish the license of the entire shader. Noncommercial restrictions are
not permissions to distribute these works under GPL/AGPL. Whether an asset is an
independent aggregate or part of a combined work, its attribution, modification
and sharing terms must be checked before release, including a free release.

Other shaders contain mixed snippet licenses or incomplete provenance. The image
presets in [`src/main/resources/assets/vibe/waifu/`](../src/main/resources/assets/vibe/waifu), audio in [`src/main/resources/assets/vibe/girlfriend/`](../src/main/resources/assets/vibe/girlfriend)
and UI images in [`assets/minecraft/client/`](../src/main/resources/assets/minecraft/client)
also lack a complete attribution and license inventory. Their inclusion here does
not assign them Vibe's GPL license; those records remain incomplete.

### LiquidBounce provenance clue in shader assets

[`snowstar.frag`](../src/main/resources/assets/vibe/shader/snowstar.frag) contains a LiquidBounce-specific TODO
and a link to LiquidBounce-Issues issue 3932. It has been present since Vibe commit
`6b0feb9`. This is evidence of a possible source path through LiquidBounce, not
proof of the original author or license of the complete shader. The exact
upstream file, revision, author notices and any original ShaderToy terms still
need to be established. LiquidBounce's repository GPL notice cannot by itself
clear third-party shaders obtained through that project.

## Authentication references and OAuth applications

| Reference | Authors | Upstream license | Relationship to Vibe |
| --- | --- | --- | --- |
| [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher) | The_Fireplace, VidTu and contributors | LGPL-3.0-or-later | Account switching and Microsoft device authorization reference; public OAuth application used for device login. |
| [MinecraftAuth](https://github.com/RaphiMC/MinecraftAuth) | RK_01/RaphiMC and contributors | LGPL-3.0-or-later | Minecraft Java application settings and Xbox authentication protocol reference. |
| [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) | CCBlueX and contributors | GPL-3.0-or-later | Minecraft Java application configuration reference for cookie login. |

The Gradle dependencies contain none of these three projects. Vibe's
authentication implementation is in `dev.vibe.account`. The historical reference
revisions were not recorded, so this inventory does not establish independent
authorship or a revision-by-revision comparison. The shader provenance issue is
documented separately above.

The upstream LGPLv3 text for IAS and MinecraftAuth is retained in
[LICENSES/LGPL-3.0.txt](LGPL-3.0.txt) as reference documentation, together
with the GPLv3 text it incorporates in [GPL-3.0.txt](GPL-3.0.txt). Including these texts
does not mean either library is bundled or relicense Vibe's account code. If
copyrighted implementation code is copied or a library is bundled later, preserve
its notices and meet the applicable source and LGPL combined-work requirements.

Device login uses the public application ID `54fd49e4-2103-4044-9603-2b028c814ec3`;
Microsoft's consent screen identifies it as **In-Game Account Switcher**. Cookie
login uses Minecraft Java application ID `00000000402b5328`. These are public
identifiers, not client secrets. Neither the LGPL/GPL software licenses nor
successful login establish permission for Vibe to use those registrations or
their service access. No separate permission is documented in this repository.
Before release, establish permitted use with the application owners and applicable
Microsoft/Minecraft requirements, or use an appropriately registered and permitted
Vibe application. A notice alone does not resolve this issue. See Microsoft's
[device authorization](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code)
and [authorization code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)
documentation for the protocols.

The [`keystrokesmod`](../src/main/java/keystrokesmod) package exposes compatibility
types for the [Raven scripting API](https://blowsy.gitbook.io/raven). The exact
upstream revision and provenance of individual compatibility helpers are not
recorded; the API name alone is not a license statement.

## External runtime and build tools

Minecraft, Forge and the game's runtime libraries are obtained separately by the
launcher/build tooling. [OptiFine](https://optifine.net) is an optional separate
download used by `run.bat`, cached in `build/optifine/`; it is not embedded in Vibe's
JAR. Gradle, Unimined, JUnit and the optional Pillow asset-conversion tool are build
or development tools rather than bundled Vibe features.

Minecraft and Microsoft are their respective owners' products. Vibe is an
independent client mod.

## Lichess chess pieces

Vibe bundles Colin M. L. Burnett's cburnett pieces from Lichess under GPL-2.0-or-later;
see [source and conversion details](LICHESS-PIECES.md).
