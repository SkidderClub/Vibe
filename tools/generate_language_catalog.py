#!/usr/bin/env python3
"""Build Vibe's checked-in per-language UI catalogs.

This script is a maintenance tool, not a runtime dependency.  It derives every
built-in module name, module description, setting label and mode choice from
the Java sources, combines them with the curated interface table, then writes
one UTF-8 ``<code>.lang`` file per language under src/main/resources.

Each entry uses the form ``vibe.<short id>=<translated text>``.  ``en.lang`` is
the authoritative key list: it maps every short id to the English source
string, and the runtime loads it before the other languages.  A legacy
``complete.tsv`` checkout is migrated by running this script once with
``--migrate`` (or simply on a checkout where en.lang does not exist yet).
"""
from __future__ import annotations

import csv
import json
import pathlib
import re
import sys
import time
import urllib.parse
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java"
LANG_DIR = ROOT / "src/main/resources/assets/vibe/lang"
BASE = LANG_DIR / "interface.tsv"
EN_LANG = LANG_DIR / "en.lang"
COMPLETE = LANG_DIR / "complete.tsv"
OLD_ZH = LANG_DIR / "zh.lang"

# (selector name, Google Translate code, language file code)
LANGUAGES = [
    ("English", "en", "en"), ("Chinese", "zh-CN", "zh"), ("Russian", "ru", "ru"),
    ("Japanese", "ja", "ja"), ("Bavarian", "de", "bar"), ("Finnish", "fi", "fi"),
    ("Swedish", "sv", "sv"), ("Greek", "el", "el"), ("Spanish", "es", "es"),
    ("German", "de", "de"), ("French", "fr", "fr"), ("Enchantment Table", "galactic", "galactic"),
    ("Portuguese", "pt", "pt"), ("Ukrainian", "uk", "uk"), ("Hindi", "hi", "hi"),
    ("Standard Arabic", "ar", "ar"), ("Bengali", "bn", "bn"), ("Indonesian", "id", "id"),
    ("Urdu", "ur", "ur"), ("Nigerian Pidgin", "pidgin", "pcm"), ("Egyptian Arabic", "ar", "arz"),
    ("Marathi", "mr", "mr"), ("Vietnamese", "vi", "vi"), ("Telugu", "te", "te"),
    ("Swahili", "sw", "sw"), ("Hausa", "ha", "ha"), ("Turkish", "tr", "tr"),
    ("Western Punjabi", "pa", "pa"), ("Tagalog", "tl", "tl"), ("Tamil", "ta", "ta"),
    ("Iranian Persian", "fa", "fa"), ("Korean", "ko", "ko"), ("Amharic", "am", "am"),
    ("Thai", "th", "th"), ("Javanese", "jv", "jv"), ("Italian", "it", "it"),
    ("Gujarati", "gu", "gu"), ("Dutch", "nl", "nl"), ("Nepali", "ne", "ne"),
    ("Czech", "cs", "cs"), ("Polish", "pl", "pl"), ("Zulu", "zu", "zu"),
    ("Romanian", "ro", "ro"), ("Aurebesh", "aurebesh", "aurebesh"),
]
LANGUAGE_NAMES = {name for name, _, _ in LANGUAGES}
PREFIX = "vibe."
CURATED = {"Chinese", "Russian", "Japanese", "Bavarian"}
SETTING = re.compile(r"new\s+(?:[\w.]+\.)?(?:BooleanSetting|NumberSetting|ModeSetting|MultiSelectSetting|ColorSetting|RangeSetting|StringSetting)\s*\(")
SUPER = re.compile(r"\bsuper\s*\(")
DIRECT = re.compile(r'(?:LanguageManager\.(?:translate|format)|AccountScreenStyle\.(?:text|rawText|title|fit)|'
                    r'SkeetEditorStyle\.(?:button|window|panel)|drawString(?:WithShadow)?|rawText|text|title|fit|'
                    r'label|button|window|panel|center(?:ed)?|small|heading|subtitle|setText)\s*\(\s*"((?:\\.|[^"\\])*)"')
STRING = re.compile(r'"((?:\\.|[^"\\])*)"')
# Everything outside the bootstrapping and language implementation packages can
# contribute client-facing copy.  Keeping this as a deny-list makes a new GUI
# package covered automatically instead of depending on a hand-maintained list.
NON_VISIBLE_SOURCE_DIRECTORIES = {"core", "language"}


def java_string(value: str) -> str:
    def unicode(match: re.Match[str]) -> str:
        return chr(int(match.group(1), 16))
    value = re.sub(r"\\u([0-9a-fA-F]{4})", unicode, value)
    return (value.replace(r"\\", "\\").replace(r'\"', '"').replace(r"\n", " ")
            .replace(r"\r", " ").replace(r"\t", " "))


def expression(source: str, start: int) -> str:
    depth = 0
    quoted = escaped = False
    for index in range(start, len(source)):
        char = source[index]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
            continue
        if char == '"':
            quoted = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    return source[start:]


def strings_in(value: str) -> list[str]:
    return [java_string(match.group(1)) for match in STRING.finditer(value) if match.group(1)]


def is_visible_literal(value: str) -> bool:
    """Retain readable client copy while excluding paths, protocols, and syntax."""
    value = value.strip()
    if not value or len(value) > 260 or not any(char.isalpha() for char in value):
        return False
    if value.startswith(("/", "\\", "http:", "https:", "assets/", "vibe/", "minecraft:", "net.minecraft")):
        return False
    # Embedded profiles and texture data are not interface copy.  Treat a long
    # base64-looking value as data before considering the normal literal rules.
    if len(value) > 64 and re.fullmatch(r"[A-Za-z0-9+/=]+", value):
        return False
    if "\\" in value or re.search(r"\{\d|\[A-Za-z|\[0-9|\(\?[:=!<]|[{}]|^#version", value):
        return False
    # Lower-case identifiers, resource paths, and serialized keys are never
    # drawn as interface copy.  Titles such as "HUD" and "AES" remain so a
    # visible acronym can still be localized or deliberately preserved.
    if re.fullmatch(r"[a-z_][a-z0-9_./:-]*", value):
        return False
    if re.fullmatch(r"[A-Za-z0-9_.-]+", value):
        # Single title words are common in enum-backed game menus and normal
        # GuiButton constructors (for example, "Operations" or "Global").
        # Include them; low-case identifiers were filtered immediately above.
        return value[0].isupper()
    return True


def read_lang(path: pathlib.Path) -> dict[str, str]:
    """Read ``key=value`` lines, keeping the right-hand side exactly as written."""
    result: dict[str, str] = {}
    if not path.is_file():
        return result
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        equals = line.find("=")
        if equals <= 0:
            continue
        result[line[:equals]] = line[equals + 1:]
    return result


def catalog_sources() -> list[str]:
    """English sources already known from en.lang or a legacy catalog."""
    if EN_LANG.is_file():
        return [value for key, value in read_lang(EN_LANG).items()
                if key.startswith(PREFIX) and value]
    result: list[str] = []
    if COMPLETE.is_file():
        with COMPLETE.open("r", encoding="utf-8", newline="") as stream:
            for row in csv.reader(stream, delimiter="\t"):
                if row and row[0] and row[0] != "key":
                    result.append(row[0])
    result.extend(read_lang(OLD_ZH).keys())
    return result


def collect_keys(migrating: bool = False) -> tuple[list[str], dict[str, dict[str, str]]]:
    keys: set[str] = set()
    curated: dict[str, dict[str, str]] = {language: {} for language in CURATED}
    with BASE.open("r", encoding="utf-8", newline="") as stream:
        rows = list(csv.reader(stream, delimiter="\t"))
    header = rows[0]
    for row in rows[1:]:
        if not row or row[0].startswith("#"):
            continue
        keys.add(row[0])
        for index, language in enumerate(header[1:], 1):
            if language in curated and index < len(row) and row[index]:
                curated[language][row[0]] = row[index]

    # A migration converts the checked-in catalog; it must not grow the key
    # list with every literal currently present in the sources.  New keys are
    # picked up by later normal runs, which translate only those additions.
    if not migrating:
        for path in list((SOURCE / "dev/vibe/module").rglob("*.java")) + list((SOURCE / "dev/vibe/hud").rglob("*.java")):
            source = path.read_text(encoding="utf-8")
            for match in SETTING.finditer(source):
                keys.update(strings_in(expression(source, match.end() - 1)))

        for path in (SOURCE / "dev/vibe/module/impl").rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            for match in SUPER.finditer(source):
                keys.update(strings_in(expression(source, match.end() - 1))[:2])

        for path in (SOURCE / "dev/vibe").rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            for match in DIRECT.finditer(source):
                keys.add(java_string(match.group(1)))

        # UI text often reaches a renderer through a button constructor, an enum
        # choice, or an array.  Those values are not direct render arguments, so
        # collect all readable literals from user-facing client packages as well.
        for path in (SOURCE / "dev/vibe").rglob("*.java"):
            relative = path.relative_to(SOURCE / "dev/vibe")
            if relative.parts and relative.parts[0] in NON_VISIBLE_SOURCE_DIRECTORIES:
                continue
            source = path.read_text(encoding="utf-8")
            for match in STRING.finditer(source):
                value = java_string(match.group(1))
                if is_visible_literal(value):
                    keys.add(value)

    # Keys implemented by the catalog itself or passed dynamically through the
    # shared themed widgets are still ordinary user-facing UI text.
    keys.update({"Language selector", "Choose language", "Close", "Elements", "Theme", "Inspector", "HUD ELEMENTS", "INSPECTOR", "COLOR",
                 "No module is available in this category.", "Press a key (Esc to clear)", "None", "EDIT", "OFF"})
    # Keep every key that is already present in en.lang or a legacy catalog so
    # regenerating never drops a translation or renumbers a stable id.
    for source in catalog_sources():
        if source and "\t" not in source and "\n" not in source:
            keys.add(source)
    # Runtime lookup is case-insensitive.  Keep one source spelling per key so
    # a lower-case literal cannot overwrite a curated title-cased UI label in
    # the final catalog.
    canonical: dict[str, str] = {}
    for key in keys:
        if not key or "\t" in key or "\n" in key:
            continue
        normalized = key.lower()
        if normalized not in canonical or key in curated["Chinese"]:
            canonical[normalized] = key
    return sorted(canonical.values()), curated


def existing_values() -> dict[str, dict[str, str]]:
    values: dict[str, dict[str, str]] = {}
    if EN_LANG.is_file():
        english = {key[len(PREFIX):]: value for key, value in read_lang(EN_LANG).items()
                   if key.startswith(PREFIX)}
        for language, _, code in LANGUAGES:
            if language == "English":
                continue
            for key, value in read_lang(LANG_DIR / (code + ".lang")).items():
                if not key.startswith(PREFIX) or not value:
                    continue
                source = english.get(key[len(PREFIX):])
                if source is not None:
                    values.setdefault(source, {})[language] = value
        return values
    if COMPLETE.is_file():
        with COMPLETE.open("r", encoding="utf-8", newline="") as stream:
            rows = list(csv.reader(stream, delimiter="\t"))
        if rows and rows[0] and rows[0][0] == "key":
            headers = rows[0][1:]
            for row in rows[1:]:
                if not row or not row[0]:
                    continue
                values.setdefault(row[0], {})
                for index, language in enumerate(headers):
                    if language in LANGUAGE_NAMES and index + 1 < len(row) and row[index + 1]:
                        values[row[0]][language] = row[index + 1]
    # The old zh.lang loaded after complete.tsv, so its Chinese values win and
    # its extra keys are preserved during the migration.
    for source, value in read_lang(OLD_ZH).items():
        if source and value and not source.startswith(PREFIX):
            values.setdefault(source, {})["Chinese"] = value
    return values


def existing_ids() -> dict[str, str]:
    """English source (lower case) to the short id already stored in en.lang."""
    result: dict[str, str] = {}
    if EN_LANG.is_file():
        for key, value in read_lang(EN_LANG).items():
            if key.startswith(PREFIX) and value:
                result[value.lower()] = key[len(PREFIX):]
    return result


def assign_ids(keys: list[str]) -> dict[str, str]:
    known = existing_ids()
    used: set[str] = set()
    ids: dict[str, str] = {}
    for key in sorted(keys, key=str.lower):
        candidate = known.get(key.lower())
        if candidate and candidate not in used:
            ids[key] = candidate
            used.add(candidate)
    for key in sorted(keys, key=str.lower):
        if key in ids:
            continue
        base = re.sub(r"[^a-z0-9]+", "_", key.lower()).strip("_")[:64] or "key"
        candidate, number = base, 2
        while candidate in used:
            candidate = base + "_" + str(number)
            number += 1
        ids[key] = candidate
        used.add(candidate)
    return ids


def script_substitution(value: str, alphabet: dict[str, str]) -> str:
    """Transliterate text while leaving Minecraft formatting controls intact."""
    result: list[str] = []
    index = 0
    while index < len(value):
        char = value[index]
        if char == "\u00a7" and index + 1 < len(value):
            result.extend((char, value[index + 1]))
            index += 2
            continue
        result.append(alphabet.get(char.lower(), char))
        index += 1
    return "".join(result)


def galactic(value: str) -> str:
    # Standard Galactic Alphabet is the 1:1 English substitution seen on
    # Minecraft enchanting tables. The bundled Sga Regular font renders the
    # canonical glyphs in themed interfaces; these Unicode forms are the
    # compatible fallback for vanilla's bitmap renderer.
    alphabet = {
        "a": "ᔑ", "b": "ʖ", "c": "ᓵ", "d": "↸", "e": "ᒷ", "f": "⎓", "g": "⊣", "h": "⍑", "i": "╎",
        "j": "⋮", "k": "ꖌ", "l": "ꖎ", "m": "ᒲ", "n": "リ", "o": "𝙹", "p": "!¡", "q": "ᑑ", "r": "∷",
        "s": "ᓭ", "t": "ℸ", "u": "⚍", "v": "⍊", "w": "∴", "x": "·/", "y": "||", "z": "⨅",
    }
    return script_substitution(value, alphabet)


def aurebesh(value: str) -> str:
    # Aurebesh has no Unicode block. These well-supported symbols are kept as
    # a legible fallback, while themed Vibe UI uses the bundled Aurebesh font.
    alphabet = {
        "a": "ꓮ", "b": "ꓐ", "c": "Ↄ", "d": "ꓓ", "e": "ꓰ", "f": "ꓝ", "g": "ꓖ", "h": "ꓧ", "i": "ꓲ",
        "j": "ꓙ", "k": "ꓗ", "l": "ꓡ", "m": "ꓟ", "n": "ꓠ", "o": "ꓳ", "p": "ꓑ", "q": "Ϙ", "r": "ꓣ",
        "s": "ꓢ", "t": "ꓔ", "u": "ꓴ", "v": "ꓦ", "w": "ꓪ", "x": "ჯ", "y": "ꓬ", "z": "ꓜ",
    }
    return script_substitution(value, alphabet)


def pidgin(value: str) -> str:
    original = value
    replacements = [
        (r"\bOnly\b", "Na only"), (r"\bEnable(d)?\b", "E dey on"), (r"\bDisable(d)?\b", "E no dey on"),
        (r"\bSettings\b", "Setting dem"), (r"\bSelect\b", "Pick"), (r"\bChoose\b", "Pick"),
        (r"\bSave\b", "Save"), (r"\bLoad\b", "Load"), (r"\bDelete\b", "Comot"), (r"\bShow\b", "Show"),
        (r"\bHide\b", "Hide"), (r"\bColor\b", "Colour"), (r"\bwith\b", "wit"), (r"\bfrom\b", "from"),
        (r"\bthe\b", "di"), (r"\band\b", "an"), (r"\bfor\b", "for"), (r"\bto\b", "to"),
    ]
    for pattern, replacement in replacements:
        value = re.sub(pattern, replacement, value, flags=re.IGNORECASE)
    # Avoid silently presenting an English label where Pidgin has no special
    # vocabulary in the small local conversion table.  Proper names and glyphs
    # remain unchanged, but every ordinary UI label is clearly Pidgin-facing.
    if value == original and any(len(word) >= 3 for word in re.findall(r"[A-Za-z]+", value)):
        return "Na " + value
    return value


def migrated_value(key: str, language: str) -> str:
    """Offline fallback for keys merged from a legacy catalog."""
    if language == "Enchantment Table":
        return galactic(key)
    if language == "Aurebesh":
        return aurebesh(key)
    if language == "Nigerian Pidgin":
        return pidgin(key)
    return key


def google_batch(keys: list[str], target: str) -> dict[str, str]:
    result: dict[str, str] = {}
    batches: list[list[str]] = []
    current: list[str] = []
    used = 0
    for key in keys:
        if current and used + len(key) + 1 > 3600:
            batches.append(current)
            current, used = [], 0
        current.append(key)
        used += len(key) + 1
    if current:
        batches.append(current)

    for number, batch in enumerate(batches, 1):
        source = "\n".join(batch)
        query = urllib.parse.urlencode({"client": "gtx", "sl": "en", "tl": target, "dt": "t", "q": source})
        url = "https://translate.googleapis.com/translate_a/single?" + query
        for attempt in range(4):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "Vibe translation catalog builder/1.0"})
                with urllib.request.urlopen(request, timeout=35) as response:
                    payload = json.loads(response.read().decode("utf-8"))
                translated = "".join(part[0] or "" for part in payload[0])
                values = translated.split("\n")
                if len(values) != len(batch):
                    raise RuntimeError("translator changed a batch separator")
                result.update(zip(batch, (value.replace("\t", " ").replace("\r", " ") for value in values)))
                break
            except Exception as error:  # retry temporary HTTP rate limits
                if attempt == 3:
                    raise RuntimeError("%s batch %d/%d failed: %s" % (target, number, len(batches), error))
                time.sleep(1.5 * (attempt + 1))
        time.sleep(0.06)
    return result


def sanitize(value: str) -> str:
    return value.replace("\t", " ").replace("\r", " ").replace("\n", " ")


def write_catalog(keys: list[str], values: dict[str, dict[str, str]], ids: dict[str, str]) -> None:
    LANG_DIR.mkdir(parents=True, exist_ok=True)
    ordered = sorted(keys, key=lambda key: ids[key])

    def write(path: pathlib.Path, header: str, language: str) -> None:
        with path.open("w", encoding="utf-8", newline="\n") as stream:
            stream.write(header)
            for key in ordered:
                # A missing translation falls back to the English source so the
                # runtime never loses a key and every file keeps the same ids.
                value = key if language == "English" else values[key].get(language) or key
                stream.write("%s%s=%s\n" % (PREFIX, ids[key], sanitize(value)))

    write(EN_LANG, "# English source strings. This file is the key list: every id maps to its English UI text.\n", "English")
    for language, _, code in LANGUAGES:
        if language == "English":
            continue
        write(LANG_DIR / (code + ".lang"),
              "# %s catalog for Vibe. Keys match en.lang; do not rename existing ids.\n" % language,
              language)


def main() -> None:
    migrating = "--migrate" in sys.argv[1:] or not EN_LANG.is_file()
    keys, curated = collect_keys(migrating)
    existing = existing_values()
    values: dict[str, dict[str, str]] = {key: {} for key in keys}
    for key in keys:
        for language, value in existing.get(key, {}).items():
            if language in LANGUAGE_NAMES and language != "English":
                values[key][language] = value
        for language, catalog in curated.items():
            if key in catalog:
                values[key][language] = catalog[key]

    if migrating:
        # The migration must not need the network: keys only found in the old
        # zh.lang keep Chinese and use the English source elsewhere.
        for key in keys:
            for language, _, _ in LANGUAGES:
                if language != "English" and not values[key].get(language):
                    values[key][language] = migrated_value(key, language)

    for language, code, _ in LANGUAGES:
        if language == "English":
            continue
        if language == "Enchantment Table":
            for key in keys:
                values[key][language] = galactic(key)
            continue
        if language == "Aurebesh":
            for key in keys:
                values[key][language] = aurebesh(key)
            continue
        if language == "Nigerian Pidgin":
            for key in keys:
                values[key][language] = pidgin(key)
            continue
        missing = [key for key in keys if not values[key].get(language)]
        if missing:
            translated = google_batch(missing, code)
            for key, value in translated.items():
                values[key][language] = value or key

    ids = assign_ids(keys)
    write_catalog(keys, values, ids)
    print("Wrote %d keys to en.lang and %d language files under %s"
          % (len(keys), len(LANGUAGES) - 1, LANG_DIR.relative_to(ROOT)))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
