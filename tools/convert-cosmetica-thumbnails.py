"""Convert Cosmetica's public WebP thumbnails to PNG for Minecraft 1.8.9.

The original WebP files remain alongside their converted PNG previews.  The
client only uses the PNG files because 1.8.9's native texture loader cannot
decode WebP image data.
"""

from __future__ import print_function

import argparse
import json
import os
from pathlib import Path
import sys

try:
    from PIL import Image
except ImportError:
    print("Pillow is required. Install it with: pip install Pillow", file=sys.stderr)
    raise SystemExit(2)


def convert(directory, force=False):
    converted = 0
    skipped = 0
    failed = 0
    for name in sorted(os.listdir(directory)):
        if not name.lower().endswith(".webp"):
            continue
        source = os.path.join(directory, name)
        target = os.path.join(directory, os.path.splitext(name)[0] + ".png")
        if not force and os.path.isfile(target) and os.path.getsize(target) > 0:
            skipped += 1
            continue
        try:
            with Image.open(source) as image:
                # Cosmetica's thumbnail endpoint is a vertical eight-frame
                # sheet (512x4096), even for static cosmetics.  Rendering the
                # entire sheet into a small square created the horizontal
                # striped cards shown in the editor. Use its first square
                # frame, exactly as a normal static preview does.
                source_image = image.convert("RGBA")
                frame_size = source_image.width
                frame = source_image.crop((0, 0, frame_size, min(frame_size, source_image.height)))
                if frame.height != frame_size:
                    square = Image.new("RGBA", (frame_size, frame_size), (0, 0, 0, 0))
                    square.alpha_composite(frame, (0, 0))
                    frame = square
                frame.save(target, "PNG", optimize=True)
            converted += 1
        except Exception as error:
            failed += 1
            print("Failed: {} ({})".format(source, error), file=sys.stderr)
    print("Cosmetica thumbnails: {} converted, {} already present, {} failed.".format(converted, skipped, failed))
    return 1 if failed else 0


def prepare_render_textures(root):
    """Keep authored sheets and make bounded GPU copies of oversized ones."""
    catalog = json.loads((root / "catalog.json").read_text(encoding="utf-8-sig"))
    count = 0
    for accessory in catalog["accessories"]:
        asset_id = accessory["id"]
        if not asset_id.isalnum():
            raise ValueError(f"Invalid cosmetic ID {asset_id!r}")
        target = root / "render-textures" / (asset_id + ".png")
        png = root / "textures" / (asset_id + ".png")
        source = png if png.is_file() else png.with_suffix(".webp")
        with Image.open(source) as image:
            frames = max(1, accessory.get("frames", 1))
            if image.height % frames != 0:
                raise ValueError(f"{asset_id}: texture height is not divisible by its frame count")
            limit = 4096
            if source == png and max(image.size) <= limit:
                target.unlink(missing_ok=True)
                continue
            if target.is_file() and target.stat().st_mtime >= source.stat().st_mtime:
                continue
            frame_height = image.height // frames
            scale = min(1.0, limit / max(image.size))
            width = max(1, int(image.width * scale))
            height = max(1, int(frame_height * scale))
            sheet = Image.new("RGBA", (width, height * frames))
            for index in range(frames):
                frame = image.crop((0, frame_height * index, image.width, frame_height * (index + 1)))
                sheet.paste(frame.resize((width, height), Image.Resampling.NEAREST), (0, height * index))
            target.parent.mkdir(parents=True, exist_ok=True)
            sheet.save(target, optimize=True)
            count += 1
    print(f"Cosmetica render textures: {count} prepared.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", help="The extensions/cosmetica/src/main/resources/assets/vibe/cosmetica/thumbnails directory")
    parser.add_argument("--force", action="store_true", help="Regenerate existing PNG preview files")
    parser.add_argument("--render-textures", action="store_true", help="Prepare oversized model and cape textures")
    arguments = parser.parse_args()
    if arguments.render_textures:
        prepare_render_textures(Path(arguments.directory).parent)
    else:
        raise SystemExit(convert(arguments.directory, arguments.force))
