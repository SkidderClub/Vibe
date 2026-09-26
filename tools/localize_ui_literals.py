#!/usr/bin/env python3
"""Wrap static Vibe UI labels in the shared language manager.

Only literal first arguments to text-rendering helpers are rewritten.  Dynamic
player, server, and configuration values deliberately remain untouched.
"""
from __future__ import annotations

import pathlib
import re


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/dev/vibe"
CALL = re.compile(
    r'(?P<prefix>(?:drawString(?:WithShadow)?|rawText|text|title|fit|label|button|'
    r'window|panel|center(?:ed)?|small|heading|subtitle|setText)\s*\(\s*)'
    r'(?P<literal>"(?:\\.|[^"\\])*")'
)


def localize(match: re.Match[str]) -> str:
    return match.group("prefix") + "dev.vibe.language.LanguageManager.translate(" + match.group("literal") + ")"


def main() -> None:
    changed = 0
    for path in SOURCE.rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        rewritten = CALL.sub(localize, source)
        if rewritten == source:
            continue
        path.write_text(rewritten, encoding="utf-8", newline="")
        changed += 1
    print("Localized literals in %d source files" % changed)


if __name__ == "__main__":
    main()
