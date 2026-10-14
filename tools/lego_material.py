"""Diffuse material and DDS decoding for the supplied PC model families.

The player renderer uses diffuse colour/alpha; native BRDF and game shader
programs are deliberately not exported as glTF shader programs.
"""
import io
import re
import struct
from PIL import Image

ROLES = {'HEAD_FRONT': 1, 'HEAD_BACK': 2, 'BODY_FRONT': 3, 'BODY_BACK': 4,
         'ARMS': 5, 'RIGHTARM': 5, 'HANDS': 6, 'RIGHTHAND': 6, 'HIPS': 7,
         'LEGS': 8, 'LEFTLEG': 25, 'RIGHTLEG': 8, 'LEFTARM': 24, 'LEFTHAND': 26,
         'GLASSES_LEFT': 9, 'GLASSES_RIGHT': 10, 'CAPE': 10}
OLD_ROLES = {1: 'Body', 2: 'Arms', 3: 'Hands', 4: 'Head', 5: 'Pants', 6: 'Legs',
             7: 'Back', 8: 'Hat', 9: 'Beard', 10: 'Cape', 11: 'Hair',
             12: 'Right Glasses', 13: 'Left Glasses', 14: 'Head Back'}


def dds_images(data):
    result = []
    for match in re.finditer(re.escape(b'DDS \x7c\0\0\0'), data):
        at = match.start()
        if at + 128 > len(data):
            continue
        try:
            image = Image.open(io.BytesIO(data[at:])).convert('RGBA')
            result.append(image)
        except (ValueError, OSError, NotImplementedError):
            continue
    return result


def materials(model):
    data = model.data
    at = data.index(b'LTMU')
    version, count = struct.unpack_from('>2I', data, at + 4)
    start = at + 12 + (4 if version < 190 else 0)
    result = []
    if version in (19, 20):
        stride = 738 if version == 19 else 714
        shift = 24 if version == 19 else 0
        for i in range(count):
            pos = start + i * stride
            if struct.unpack_from('>I', data, pos)[0] != 2:
                raise ValueError('Invalid old material record')
            result.append(dict(name='material%d' % i, role=OLD_ROLES.get(data[pos + 685 + shift]),
                               texture=struct.unpack_from('>h', data, pos + (97 if version == 19 else 101))[0], uv=0,
                               colour=list(struct.unpack_from('>3f', data, pos + 644 + shift))))
        return result
    # Name and diffuse-ID offsets are relative to the serialized shader prefix.
    layouts = {47: (0x33f, 'I', 0x14e), 49: (0x330, 'I', 0x13f),
               50: (0x331, 'I', 0x140), 52: (0x332, 'I', 0x141),
               108: (0x344, 'I', 0x155), 109: (0x344, 'I', 0x155), 110: (0x345, 'I', 0x156),
               177: (0x3ce, 'H', 0x19a), 185: (0x455, 'H', 0x1b9),
               186: (0x459, 'H', 0x1b9), 187: (0x459, 'H', 0x1b9),
               191: (0x459, 'H', 0x1b9), 270: (0x4d0, 'H', 0x249),
               275: (0x4cc, 'H', 0x245), 280: (0x453, 'H', 0x1b8),
               281: (0x454, 'H', 0x1b9), 282: (0x454, 'H', 0x1b9),
               283: (0x458, 'H', 0x1bd)}
    if version not in layouts:
        raise ValueError('Unsupported diffuse material layout ' + str(version))
    name_offset, length_type, texture_offset = layouts[version]
    if version >= 270:
        # The later sampler vector starts with diffuse, then BRDF. The old
        # offsets landed on BRDF, making un-overridden native bodies grey.
        texture_offset -= 4
    limit = data.index(b'TDML', start)
    # Shader family (0/1/2), blend family and lighting family form the
    # serialized header. Looking for just a single integer also matches
    # sampler payloads, which can accidentally look like short names.
    prefix = (b'\0\0\0[\0\1\2]\0\0\0[\0\1\2\3]\0\0\0[\0\1\7\10]'
              if version >= 270 else b'\0\0\0\2')
    for match in re.finditer(b'(?=' + prefix + b')', data[start:limit]):
        pos = start + match.start()
        if version >= 270 and struct.unpack_from('>I', data, pos + 20)[0] not in (1, 5):
            continue
        field = pos + name_offset
        if field + 4 >= limit:
            continue
        size = struct.unpack_from('>' + length_type, data, field)[0]
        begin = field + struct.calcsize(length_type)
        raw = data[begin:begin + size]
        if not 1 <= size <= 255 or len(raw) != size:
            continue
        raw = raw.rstrip(b'\0')
        if not raw or not all(32 <= b <= 126 for b in raw):
            continue
        if version >= 270 and (len(raw) < 3 or b'/' in raw or b'\\' in raw):
            continue
        name = raw.decode('ascii')
        simple = name.upper().split(':')[0].replace('DX11_', '').replace('_GAME', '').replace('OLD', '').strip()
        simple = simple.replace('LEFT_', 'LEFT').replace('RIGHT_', 'RIGHT')
        role = ROLES.get(simple)
        if version in (108, 109, 110) and simple in ('GLASSES_LEFT', 'GLASSES_RIGHT'):
            role = 15 if simple == 'GLASSES_LEFT' else 14
        texture = struct.unpack_from('>i', data, pos + texture_offset)[0]
        if not -1 <= texture <= 65535:
            continue
        result.append(dict(name=name, role=role, texture=texture, uv=1 if role and version >= 191 else 0))
        if len(result) == count and version < 270:
            break
    if version >= 270:
        # New shaders serialize automatic variants inside their owning material,
        # then reference them from the end of the public material vector. Draw
        # bindings address that vector, not the order of inline shader records.
        runs = []
        for tail in range(16, 33):
            cursor, refs = limit - tail, []
            while cursor - 8 >= start:
                token, flag = struct.unpack_from('>2I', data, cursor - 8)
                if token >> 16 != 0xc000 or flag != 1: break
                refs.append(token & 65535); cursor -= 8
            if refs: runs.append(list(reversed(refs)))
        refs = max(runs, key=len) if runs else []
        if refs:
            variants = [m for m in result if ':VARIANT_' in m['name'].upper()]
            base = [m for m in result if ':VARIANT_' not in m['name'].upper()]
            unique = sorted(set(refs))
            if len(variants) == len(unique) and len(base) + len(refs) == count:
                lookup = dict(zip(unique, variants))
                result = base + [lookup[r] for r in refs]
    if len(result) != count:
        raise ValueError('Material names disagree with declared count: %s %s/%s' % (version, len(result), count))
    return result
