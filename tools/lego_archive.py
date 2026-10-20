"""Read local TT PC DAT archives (-3/-4/-5 and CC4 v2) without modifying games.

Layout references: Luigi Auriemma ttgames.bms and Ac_K's MIT TTGames Explorer.
Offsets, names and decoded lengths are validated before use.
"""
import struct
import zlib
from pathlib import Path
from lego_lz2k import decode as unlz2k


def path_hash(name, bits=32):
    result, prime = (0x811c9dc5, 0x199933) if bits == 32 else (0xcbf29ce484222325, 1099511628211)
    for value in name.upper().replace('/', '\\').lstrip('\\').encode('ascii'):
        result = ((result ^ value) * prime) & ((1 << bits) - 1)
    return result


def entries(path):
    path = Path(path)
    with path.open('rb') as stream:
        offset, size = struct.unpack('<II', stream.read(8))
        if offset & 0x80000000:
            offset = ((offset ^ 0xffffffff) << 8) + 256
        if not 8 <= offset <= path.stat().st_size - size or size > 256 * 1024 * 1024:
            raise ValueError('DAT index outside archive: ' + str(path))
        stream.seek(offset)
        data = stream.read(size)
    paths = []
    records = []
    if data[4:12] == b'.CC40TAD':
        declared, magic, kind, version, count, names, text_size = struct.unpack_from('>I8si4I', data)
        if kind != -12 or version != 2 or declared + 4 != len(data):
            raise ValueError('Unsupported CC4 index')
        base, pos = 32, 36 + text_size
        folders = {0: '', 65535: ''}
        for i in range(names):
            at, parent, unused, sibling, marker = struct.unpack_from('>IHHhH', data, pos)
            pos += 12
            if at == 0xffffffff:
                continue
            name = data[base + at:data.index(b'\0', base + at, base + text_size)].decode('ascii')
            full = (folders[parent] + '/' + name).lstrip('/')
            if marker or i == names - 1:
                paths.append(full)
            else:
                folders[i] = full
        table_kind, table_count = struct.unpack_from('>iI', data, pos)
        pos += 8
        if table_kind != kind or table_count != count:
            raise ValueError('Inconsistent DAT counts')
        for i in range(count):
            at, packed, raw = struct.unpack_from('>QII', data, pos)
            records.append((at, packed, raw & 0x7fffffff))
            pos += 16
        hashes = struct.unpack_from('>%dQ' % count, data, pos)
        bits = 64
    else:
        kind, count = struct.unpack_from('<iI', data)
        if kind not in (-3, -4, -5):
            raise ValueError('Unsupported DAT kind: ' + str(kind))
        names = struct.unpack_from('<I', data, 8 + count * 16)[0]
        name_info = 12 + count * 16
        stride = 12 if kind <= -5 else 8
        base = name_info + names * stride + 4
        text_size = struct.unpack_from('<I', data, base - 4)[0]
        hashes = struct.unpack_from('<%dI' % count, data, base + text_size)
        folders, prefix = {0: '', 65535: ''}, ''
        for i in range(names):
            child, previous, at = struct.unpack_from('<hhi', data, name_info + i * stride)
            if kind == -5:
                parent = struct.unpack_from('<I', data, name_info + i * stride + 8)[0]
                prefix = folders.get(parent, '')
            elif previous:
                prefix = folders.get(previous & 65535, '')
            name = data[base + at:data.index(b'\0', base + at, base + text_size)].decode('ascii') if at >= 0 else ''
            folders[i] = prefix
            if child > 0:
                prefix += name + ('/' if name else '')
                if kind == -5:
                    folders[i] = prefix
            elif name:
                paths.append(prefix + name)
        for i in range(count):
            high, packed, raw, flags = struct.unpack_from('<4I', data, 8 + i * 16)
            records.append(((high << 8) + (flags >> 24), packed, raw))
        bits = 32
    by_hash = {key: i for i, key in enumerate(hashes)}
    result = []
    for name in paths:
        name = name.replace('\\', '/').lstrip('/')
        number = by_hash.get(path_hash(name, bits))
        if number is None:
            raise ValueError('Unresolved DAT name: ' + name)
        at, packed, raw = records[number]
        if at + (packed or raw) > path.stat().st_size or any(p in ('..', '') for p in name.split('/')):
            raise ValueError('Invalid DAT extent/path: ' + name)
        result.append((name, at, packed, raw))
    if len(result) != count:
        raise ValueError('Unresolved DAT records: %s of %s' % (len(result), count))
    return result


def rc4(data, key):
    state = list(range(256))
    j = 0
    for i in range(256):
        j = (j + state[i] + key[i % len(key)]) & 255
        state[i], state[j] = state[j], state[i]
    i = j = 0
    result = bytearray()
    for value in data:
        i = (i + 1) & 255
        j = (j + state[i]) & 255
        state[i], state[j] = state[j], state[i]
        result.append(value ^ state[(state[i] + state[j]) & 255])
    return bytes(result)


def read(stream, entry):
    name, offset, packed, raw = entry
    stream.seek(offset)
    data = stream.read(packed or raw)
    if data[:4] not in (b'DFLT', b'ZLIB', b'LZ2K', b'ZIPX'):
        if len(data) != raw:
            raise ValueError('Unsupported compressed entry: ' + name)
        return data
    output = bytearray()
    pos = 0
    while pos < len(data):
        kind, compressed, size = struct.unpack_from('<4sII', data, pos)
        if kind == b'LZ2K':
            compressed, size = size, compressed
        pos += 12
        chunk = data[pos:pos + compressed]
        if kind == b'ZIPX':
            chunk = rc4(chunk, struct.pack('<I', compressed))
        if compressed != size:
            if kind == b'LZ2K':
                chunk = unlz2k(chunk, size)
            elif kind == b'ZIPX':
                chunk = zlib.decompress(chunk, -15)
            else:
                chunk = zlib.decompress(chunk, 15 if kind == b'ZLIB' else -15)
        if len(chunk) != size:
            raise ValueError('Invalid chunk length: ' + name)
        output += chunk
        pos += compressed
    if len(output) != raw:
        raise ValueError('Invalid decoded length: ' + name)
    return bytes(output)
