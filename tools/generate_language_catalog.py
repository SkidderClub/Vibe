#!/usr/bin/env python3
"""Build Vibe's checked-in complete UI language catalog.

This script is a maintenance tool, not a runtime dependency.  It derives every
built-in module name, module description, setting label and mode choice from
the Java sources, combines them with the curated interface table, then writes
a static UTF-8 catalog under src/main/resources.  Existing generated values
are reused so adding one key only asks the translator for that key.
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
BASE = ROOT / "src/main/resources/assets/vibe/lang/interface.tsv"
OUTPUT = ROOT / "src/main/resources/assets/vibe/lang/complete.tsv"

LANGUAGES = [
    ("Chinese", "zh-CN"), ("Russian", "ru"), ("Japanese", "ja"), ("Bavarian", "de"),
    ("Finnish", "fi"), ("Swedish", "sv"), ("Greek", "el"), ("Spanish", "es"), ("German", "de"),
    ("French", "fr"), ("Enchantment Table", "galactic"), ("Portuguese", "pt"), ("Ukrainian", "uk"),
    ("Hindi", "hi"), ("Standard Arabic", "ar"), ("Bengali", "bn"), ("Indonesian", "id"), ("Urdu", "ur"),
    ("Nigerian Pidgin", "pidgin"), ("Egyptian Arabic", "ar"), ("Marathi", "mr"), ("Vietnamese", "vi"),
    ("Telugu", "te"), ("Swahili", "sw"), ("Hausa", "ha"), ("Turkish", "tr"), ("Western Punjabi", "pa"),
    ("Tagalog", "tl"), ("Tamil", "ta"), ("Iranian Persian", "fa"), ("Korean", "ko"), ("Amharic", "am"),
    ("Thai", "th"), ("Javanese", "jv"), ("Italian", "it"), ("Gujarati", "gu"), ("Dutch", "nl"),
    ("Nepali", "ne"), ("Czech", "cs"), ("Polish", "pl"), ("Zulu", "zu"), ("Romanian", "ro"),
    ("Aurebesh", "aurebesh"),
]
CURATED = {"Chinese", "Russian", "Japanese", "Bavarian"}
SETTING = re.compile(r"new\s+(?:[\w.]+\.)?(?:BooleanSetting|NumberSetting|ModeSetting|MultiSelectSetting|ColorSetting|RangeSetting|StringSetting)\s*\(")
SUPER = re.compile(r"\bsuper\s*\(")
DIRECT = re.compile(r'(?:LanguageManager\.(?:translate|format)|AccountScreenStyle\.(?:text|rawText|title|fit)|'
                    r'SkeetEditorStyle\.(?:button|window|panel)|drawString(?:WithShadow)?|rawText|text|title|fit|'
                    r'label|button|window|panel|center(?:ed)?|small|heading|subtitle|setText)\s*\(\s*"((?:\\.|[^"\\])*)"')
STRING = re.compile(r'"((?:\\.|[^"\\])*)"')


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
        elif char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    return source[start:]


def strings_in(value: str) -> list[str]:
    return [java_string(match.group(1)) for match in STRING.finditer(value) if match.group(1)]


def collect_keys() -> tuple[list[str], dict[str, dict[str, str]]]:
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

    for path in list((SOURCE / "dev/vibe/module").rglob("*.java")) + list((SOURCE / "dev/vibe/hud").rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        for match in SETTING.finditer(source):
            keys.update(strings_in(expression(source, match.end() - 1)))

    for path in (SOURCE / "dev/vibe/module/impl").glob("*.java"):
        source = path.read_text(encoding="utf-8")
        for match in SUPER.finditer(source):
            keys.update(strings_in(expression(source, match.end() - 1))[:2])

    for path in (SOURCE / "dev/vibe").rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        for match in DIRECT.finditer(source):
            keys.add(java_string(match.group(1)))

    # Keys implemented by the catalog itself or passed dynamically through the
    # shared themed widgets are still ordinary user-facing UI text.
    keys.update({"Language selector", "Choose language", "Close", "Elements", "Theme", "Inspector", "HUD ELEMENTS", "INSPECTOR", "COLOR",
                 "No module is available in this category.", "Press a key (Esc to clear)", "None", "EDIT", "OFF"})
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
    if not OUTPUT.is_file():
        return {}
    with OUTPUT.open("r", encoding="utf-8", newline="") as stream:
        rows = list(csv.reader(stream, delimiter="\t"))
    if not rows or rows[0][0] != "key":
        return {}
    headers = rows[0][1:]
    result: dict[str, dict[str, str]] = {}
    for row in rows[1:]:
        if not row:
            continue
        result[row[0]] = {headers[index]: row[index + 1] for index in range(min(len(headers), len(row) - 1)) if row[index + 1]}
    return result


def galactic(value: str) -> str:
    alphabet = {
        "a": "ᔑ", "b": "ʖ", "c": "ᓵ", "d": "↸", "e": "ᒷ", "f": "⎓", "g": "⊣", "h": "⍑", "i": "╎",
        "j": "⋮", "k": "ꖌ", "l": "ꖎ", "m": "ᒲ", "n": "リ", "o": "𝙹", "p": "!¡", "q": "ᑑ", "r": "∷",
        "s": "ᓭ", "t": "ℸ", "u": "⚍", "v": "⍊", "w": "∴", "x": "·/", "y": "||", "z": "⨅",
    }
    return "".join(alphabet.get(char.lower(), char) for char in value)


def aurebesh(value: str) -> str:
    alphabet = {
        "a": "ꓮ", "b": "ꓐ", "c": "Ↄ", "d": "ꓓ", "e": "ꓰ", "f": "ꓝ", "g": "ꓖ", "h": "ꓧ", "i": "ꓲ",
        "j": "ꓙ", "k": "ꓗ", "l": "ꓡ", "m": "ꓟ", "n": "ꓠ", "o": "ꓳ", "p": "ꓑ", "q": "Ϙ", "r": "ꓣ",
        "s": "ꓢ", "t": "ꓔ", "u": "ꓴ", "v": "ꓦ", "w": "ꓪ", "x": "ჯ", "y": "ꓬ", "z": "ꓜ",
    }
    return "".join(alphabet.get(char.lower(), char) for char in value)


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


def write_catalog(keys: list[str], values: dict[str, dict[str, str]]) -> None:
    def cell(value: str) -> str:
        return value.replace("\t", " ").replace("\r", " ").replace("\n", " ")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8", newline="") as stream:
        stream.write("\t".join(["key"] + [language for language, _ in LANGUAGES]) + "\n")
        for key in keys:
            stream.write("\t".join([cell(key)] + [cell(values[key].get(language, "")) for language, _ in LANGUAGES]) + "\n")


def main() -> None:
    keys, curated = collect_keys()
    existing = existing_values()
    values: dict[str, dict[str, str]] = {key: {} for key in keys}
    for key in keys:
        for language, value in existing.get(key, {}).items():
            values[key][language] = value
        for language, catalog in curated.items():
            if key in catalog:
                values[key][language] = catalog[key]

    for language, code in LANGUAGES:
        if code == "galactic":
            for key in keys:
                values[key][language] = galactic(key)
            write_catalog(keys, values)
            continue
        if code == "aurebesh":
            for key in keys:
                values[key][language] = aurebesh(key)
            write_catalog(keys, values)
            continue
        if code == "pidgin":
            for key in keys:
                values[key][language] = pidgin(key)
            write_catalog(keys, values)
            continue
        missing = [key for key in keys if not values[key].get(language)]
        if missing:
            translated = google_batch(missing, code)
            for key, value in translated.items():
                values[key][language] = value or key
        write_catalog(keys, values)
    print("Wrote %d keys × %d languages to %s" % (len(keys), len(LANGUAGES), OUTPUT.relative_to(ROOT)))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
