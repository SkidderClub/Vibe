"""Extract selected TCS PC assets from the user's installation (never modifies it).

DAT layout reference: Luigi Auriemma's ttgames.bms (0.9.11a).
GHG layout reference: sluicer's ExtractPcGHG and the NU20 file itself.
Usage: python tools/import-lego-assets.py "path/to/LEGO Star Wars" --extract-only
"""
import argparse
import io
import json
import struct
import zlib
from lego_lz2k import decode as unlz2k
from pathlib import Path


def dat_entries(path):
    with path.open('rb') as f:
        off, size = struct.unpack('<II', f.read(8))
        if off & 0x80000000:
            off = ((off ^ 0xffffffff) << 8) + 0x100
        f.seek(off)
        b = f.read(size)
    kind, count = struct.unpack_from('<iI', b)
    if kind != -3:
        raise ValueError('Expected TCS PC DAT version -3: ' + str(path))
    names = struct.unpack_from('<I', b, 8 + count * 16)[0]
    name_info = 12 + count * 16
    name_base = name_info + names * 8 + 4
    crc_base = name_base + struct.unpack_from('<I', b, name_base - 4)[0]
    crcs = {struct.unpack_from('<I', b, crc_base + i * 4)[0]: i for i in range(count)}
    folders = {0: ''}
    entries = []
    parent = ''
    for i in range(names):
        nxt, prev, at = struct.unpack_from('<hhi', b, name_info + i * 8)
        name = b[name_base + at:b.find(b'\0', name_base + at)].decode('latin1') if at >= 0 else ''
        if prev != 0:
            parent = folders.get(prev, '')
        folders[i] = parent
        full = parent + name
        if nxt > 0:
            parent = full + ('\\' if name else '')
        if nxt > 0 or not name:
            continue
        crc = 0x811c9dc5
        for char in full.upper().lstrip('\\').encode('latin1'):
            crc = ((crc ^ char) * 0x199933) & 0xffffffff
        if crc not in crcs:
            continue
        pos = 8 + crcs[crc] * 16
        offset, packed, unpacked, flags = struct.unpack_from('<IIII', b, pos)
        offset = (offset << 8) + (flags >> 24)
        entries.append((full.replace('\\', '/'), offset, packed, unpacked))
    return entries


def read_entry(f, entry):
    name, offset, packed, size = entry
    f.seek(offset)
    data = f.read(packed or size)
    if data[:4] not in (b'DFLT', b'ZLIB', b'LZ2K'):
        return data[:size]
    out = bytearray()
    pos = 0
    while pos < len(data):
        sig, compressed, raw = struct.unpack_from('<4sII', data, pos)
        pos += 12
        if sig == b'LZ2K':
            compressed, raw = raw, compressed
        chunk = data[pos:pos + compressed]
        out += chunk if compressed == raw else unlz2k(chunk, raw) if sig == b'LZ2K' else zlib.decompress(chunk, -15 if sig == b'DFLT' else 15)
        pos += compressed
    if len(out) != size:
        raise ValueError('Invalid extracted size: ' + name)
    return bytes(out)


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('game', type=Path)
    parser.add_argument('--output', type=Path, default=Path('build/lego-extracted'))
    parser.add_argument('--extract-only', action='store_true')
    parser.add_argument('--filter', default='', help='Extract only names containing this text')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    manifest = []
    for archive in sorted(p for p in args.game.glob('*.DAT') if p.stem.upper() == 'GAME' or p.stem.upper().startswith('EPISODE_')):
        entries = dat_entries(archive)
        print(archive.name, len(entries), 'indexed', flush=True)
        for entry in entries:
            name = entry[0]
            manifest.append(dict(archive=archive.name, name=name, offset=entry[1], size=entry[3]))
        with archive.open('rb') as f:
            for entry in entries:
                name = entry[0]
                if args.filter.upper() not in name.upper():
                    continue
                if not (name.upper().startswith(('CHARS/', 'PC/', 'STUFF/')) or
                        (name.upper().startswith('AUDIO/') and name.upper().endswith(('.WAV', '.CFG')))):
                    continue
                target = (args.output / name).resolve()
                if not target.is_relative_to(args.output.resolve()):
                    raise ValueError('Unsafe archive path')
                if target.exists():
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(read_entry(f, entry))
    (args.output / 'archive-index.json').write_text(json.dumps(manifest, indent=2))
    print('Extracted to', args.output, flush=True)


if __name__ == '__main__':
    main()
