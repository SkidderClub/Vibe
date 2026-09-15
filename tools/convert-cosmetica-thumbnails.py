"""Convert Cosmetica's public WebP thumbnails to PNG for Minecraft 1.8.9.

The original WebP files remain alongside their converted PNG previews.  The
client only uses the PNG files because 1.8.9's native texture loader cannot
decode WebP image data.
"""

from __future__ import print_function

import argparse
import os
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", help="The src/main/resources/assets/vibe/cosmetica/thumbnails directory")
    parser.add_argument("--force", action="store_true", help="Regenerate existing PNG preview files")
    arguments = parser.parse_args()
    raise SystemExit(convert(arguments.directory, arguments.force))
