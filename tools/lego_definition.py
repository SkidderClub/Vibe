"""Read self-describing TT character definitions from their embedded schemas.

The schema is part of each supplied game file; no character-specific byte offsets
or hand-written costume lists are needed. Versions 20–38 have three schema sizes.
"""
import re
import struct


def definition(data, schemas_only=False):
    def u(fmt, at):
        value = struct.unpack_from('<' + fmt, data, at)
        return value[0] if len(value) == 1 else value

    def text(at):
        size = u('I', at)
        if not 1 <= size <= 65536 or at + 4 + size > len(data) or data[at + 3 + size]:
            raise ValueError('Invalid character definition string at ' + hex(at))
        return data[at + 4:at + 3 + size].decode('ascii'), at + 4 + size

    info = data.index(b'StreamInfo\0')
    version = u('I', info + 11)
    start = data.index(b'ClassList\0', info)
    end = start - 8 + u('I', start - 8)
    classes = []
    for match in re.finditer(b'\x06\0\0\0Class\0', data[start:end]):
        at = start + match.start() + 4
        limit = at - 8 + u('I', at - 8)
        name, pos = text(at + 6)
        schema = data.index(b'Types\0', pos, limit)
        count, pos = u('I', schema + 6), schema + 10
        fields = []
        for _ in range(count):
            typ = u('I', pos)
            field, pos = text(pos + 4)
            stride = 3 if version <= 21 else 4 if version < 27 else 5
            details = u(str(stride) + 'I', pos)
            pos += stride * 4
            fields.append((field, typ, details))
        if pos != schema - 8 + u('I', schema - 8):
            raise ValueError('Character schema extent mismatch: ' + name)
        classes.append((name, fields))

    if schemas_only:
        return {'version': version, 'schemas': classes}

    objects = []
    formats = {0: 'B', 1: 'h', 2: 'i', 3: 'q', 4: 'f', 6: '3f', 8: '3f',
               9: '4f', 10: '16f', 17: '3e', 18: 'B'}

    def fields(index, chain=()):
        if index in chain:
            raise ValueError('Recursive character schema')
        for field, typ, details in classes[index][1]:
            flags = details[2]
            if flags & 0xc0000000 == 0xc0000000 or (version <= 21 and flags & 0xe0000000 == 0xa0000000):
                # Both inherited bases (C...) and embedded value structs (A...)
                # are serialized inline. Terrain Params precedes layer masks in
                # HP1; stopping there loses the costume's hair/hat layers.
                yield from fields(typ, chain + (index,))
            else:
                yield field, typ, flags

    for match in re.finditer(b'\x05\0\0\0OLST\0', data[end:]):
        at = end + match.start() + 4
        limit = at - 8 + u('I', at - 8)
        if version >= 35:
            count = u('I', at + 5)
            if not count:
                continue
            cls = u('H', at + 9)
            pos = at + 11
        else:
            cls = u('H', at + 5)
            count = u('H' if version < 24 else 'I', at + 7)
            pos = at + (9 if version < 24 else 11)
        if cls >= len(classes) or count > 65536:
            raise ValueError('Invalid character object list')
        for _ in range(count):
            if data[pos + 8:pos + 13] != b'MOBJ\0':
                raise ValueError('Invalid character object boundary at ' + hex(pos))
            stop = pos + u('I', pos)
            cursor = pos + (17 if version >= 28 else 16 if version >= 24 else 18)
            values = {}
            for field, typ, flags in fields(cls):
                if cursor >= stop or flags & 0x80000000:
                    break
                if typ == 5:
                    value, cursor = text(cursor)
                elif typ in formats:
                    fmt = formats[typ]
                    value = u(fmt, cursor)
                    cursor += struct.calcsize('<' + fmt)
                else:
                    break
                if cursor > stop:
                    raise ValueError('Character field exceeds object')
                values[field] = value
            objects.append({'class': classes[cls][0], 'fields': values,
                            'offset': pos, 'decoded_end': cursor, 'end': stop})
            pos = stop
            if version >= 35 and _ + 1 < count:
                cls = u('H', pos)
                pos += 2
            if pos > limit:
                raise ValueError('Character object exceeds list')
    return {'version': version, 'objects': objects, 'schemas': classes}
