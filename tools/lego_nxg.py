"""Bounded PC NXG/DX11 geometry, display and skeleton reader.

Based on the serialized layouts in the supplied files, cross-checked against
Sluicer's ExtractNxgMESH / ExtractDx11MESH and JaanDev's NXG format notes.
Exports the original geometry; game shader effects and facial animation are
outside the custom player renderer's capabilities.
"""
import struct
import numpy as np

WIDTH = {2: 8, 3: 12, 4: 16, 5: 4, 6: 8, 7: 4, 8: 4, 9: 4}


class Cursor:
    def __init__(self, data, pos=0):
        self.data, self.pos = data, pos

    def take(self, size):
        if size < 0 or self.pos + size > len(self.data):
            raise ValueError('Model extent outside file at ' + hex(self.pos))
        pos = self.pos
        self.pos += size
        return self.data[pos:self.pos]

    def get(self, fmt='I', order='>'):
        values = struct.unpack(order + fmt, self.take(struct.calcsize(order + fmt)))
        return values[0] if len(values) == 1 else values

    def count(self, maximum=100000):
        value = self.get()
        if value > maximum:
            raise ValueError('Invalid model count at ' + hex(self.pos - 4))
        return value

    def label(self):
        return self.take(self.get('H')).rstrip(b'\0').decode('ascii')


class Model:
    def __init__(self, path, skeleton=True):
        self.data = path.read_bytes()
        self.path = path
        nt = self.data.find(b'LBTN')
        self.names = self.data[nt + 12:nt + 12 + struct.unpack_from('>I', self.data, nt + 8)[0]] if nt >= 0 else b''
        at = self.data.index(b'HSEM')
        self.version = struct.unpack_from('>I', self.data, at + 4)[0]
        if self.version not in (4, 5, 46, 47, 48, 161, 169, 170, 175, 200):
            raise ValueError('Unsupported MESH version ' + str(self.version))
        self.dx = self.version in (175, 200)
        self.buffers, self.serial, self.base = {}, 0, None
        self.c = Cursor(self.data, at + 8)
        self.parts = self.mesh()
        self.skeleton = self.rig() if skeleton else None
        self.specials = self.display()

    def name(self, offset):
        if offset >= len(self.names):
            raise ValueError('Invalid model name offset')
        return self.names[offset:self.names.index(b'\0', offset)].decode('ascii')

    def buffer(self, vertex):
        c = self.c
        token = c.get()
        if token & 0xffff0000 == 0xc0000000:
            absolute = token & 65535
            possibilities = {absolute - i for i, b in self.buffers.items() if b['vertex'] == vertex and i <= absolute}
            self.base = possibilities if self.base is None else possibilities & self.base
            if not self.base:
                raise ValueError('Unresolved mesh buffer reference')
            if c.get() != 1:
                raise ValueError('Invalid mesh buffer reference flag')
            return ('absolute', absolute)
        if token != 1:
            raise ValueError('Invalid mesh buffer token ' + hex(token) + ' at ' + hex(c.pos - 4))
        flags, count = c.get('2I')
        if count > 20000000:
            raise ValueError('Mesh buffer too large')
        if vertex:
            if self.version <= 5:
                raw = c.take(32)
                descriptors = []
                offset = 0
                # The old fixed descriptor has reserved slots 6, 8 and 14–16.
                for slot, variable in ((0, 0), (1, 1), (2, 2), (3, 3), (4, 20), (5, 5),
                                       (7, 6), (9, 9), (10, 10), (11, 12), (12, 13)):
                    typ = raw[slot * 2]
                    if typ:
                        descriptors.append((variable, typ, offset))
                        offset += WIDTH[typ]
            else:
                if self.version >= 161:
                    if c.take(4) != b'DXTV':
                        raise ValueError('Missing vertex descriptor marker')
                    c.get()
                descriptors = [c.get('3B') for _ in range(c.count(32))]
            stride = max(off + WIDTH[typ] for _, typ, off in descriptors)
            if self.version >= 161:
                c.take(6)
            values = dict(vertex=True, count=count, descriptors=descriptors, stride=stride,
                          data=c.take(stride * count))
        else:
            stride = c.get()
            if stride not in (2, 4):
                raise ValueError('Unsupported index width')
            values = dict(vertex=False, count=count, stride=stride, data=c.take(count * stride))
        key = self.serial
        self.buffers[key] = values
        self.serial += 1
        return ('relative', key)

    def stream(self):
        return self.buffer(True), self.c.get()

    def morphs(self, palette):
        c, targets = self.c, 0
        while c.get():
            c.get()
            targets += 1
            if targets > 4096:
                raise ValueError('Too many morph targets')
        if not targets:
            return
        if self.dx:
            c.take(4)
        for _ in range(targets):
            count = c.count(1000000)
            if count:
                c.take(count * 12)
                if self.version <= 5:
                    if palette and palette[-1] == 255:
                        c.take(5)
                    else:
                        c.take(2)
                        serialized = c.get()
                        n = c.count()
                        c.take(4 * n)
                        self.serial += serialized == 1
                else:
                    trailer = c.take(20 if self.version == 200 else 21)
                    if self.version == 200 and not any(trailer):
                        self.serial -= 1  # No serialized companion for this dense vector.
            else:
                c.take(4 if self.dx else 13)
                c.take(c.count(64000000))
                if self.dx and self.version != 200:
                    c.take(4)
                n = c.count()
                c.take(n * 4)
                if self.version == 200:
                    c.take(c.count() * 4)
                    c.take(4)
                    self.serial += bool(n)
                elif self.dx:
                    c.take(4)
                elif n:
                    self.serial += 1
            self.serial += 2

    def mesh(self):
        c = self.c
        if self.version in (48, 161, 169, 170):
            c.take(4)
        parts = []
        for _ in range(c.count()):
            if self.version == 200:
                c.take(12)  # Counted RNMS record and its version.
            elif self.dx:
                c.take(4)
            streams = [self.stream() for _ in range(c.count(16))]
            extra = c.count(16)
            for j in range(extra):
                self.stream()
            if extra:
                self.serial += 1
            indices = self.buffer(False)
            first_index, index_count, first_vertex = c.get('3I')
            c.take(2)
            vertex_count = c.count(1000000)
            if self.version <= 46:
                self.serial += 1
            c.take(4)
            palette = list(c.take(c.count(256)))
            self.serial += bool(palette)
            before = self.serial
            self.morphs(palette)
            had_morphs = self.serial != before
            if self.version >= 46:
                if not self.dx or not had_morphs:
                    c.take(4)
                c.take(36)
            if self.version >= 47 and not self.dx:
                secondary = c.get() & 255
                if secondary:
                    self.stream()
                    c.get()
                    self.buffer(False)
                    c.take(12)
                else:
                    c.take(28)
                self.serial += 1
            if self.dx:
                self.serial += 2
            parts.append(dict(streams=streams, indices=indices, first_index=first_index,
                              index_count=index_count, first_vertex=first_vertex,
                              vertex_count=vertex_count, palette=palette))
        if self.base is not None and len(self.base) != 1:
            raise ValueError('Ambiguous mesh reference base')
        base = next(iter(self.base)) if self.base else 0
        def resolve(ref):
            kind, key = ref
            return self.buffers[key - base if kind == 'absolute' else key]
        for part in parts:
            attrs = {}
            for ref, skip in part['streams']:
                buf = resolve(ref)
                for variable, typ, offset in buf['descriptors']:
                    width = {2: 2, 3: 3, 4: 4, 5: 2, 6: 4}.get(typ, 4)
                    dtype = ('<' if self.dx else '>') + ('f4' if typ < 5 else 'f2') if typ <= 6 else 'u1'
                    at = skip + part['first_vertex'] * buf['stride'] + offset
                    attrs[variable] = np.ndarray((part['vertex_count'], width), dtype=dtype,
                                                 buffer=buf['data'], offset=at,
                                                 strides=(buf['stride'], np.dtype(dtype).itemsize)).astype(float)
            if 0 not in attrs or not np.isfinite(attrs[0]).all():
                raise ValueError('Invalid vertex positions')
            buf = resolve(part['indices'])
            dtype = ('<' if self.dx else '>') + ('u2' if buf['stride'] == 2 else 'u4')
            faces = np.frombuffer(buf['data'], dtype=dtype, count=part['index_count'],
                                  offset=part['first_index'] * buf['stride']).astype(np.uint32)
            if len(faces) % 3 or (len(faces) and faces.max() >= part['vertex_count']):
                raise ValueError('Invalid triangle indices')
            part.update(attributes=attrs, triangles=faces.reshape((-1, 3)))
        return parts

    def rig(self):
        at = self.data.find(b'LOGH')
        if at < 0:
            return None
        c = Cursor(self.data, at + 4)
        version = c.get()
        typed = version >= 12
        def count():
            if version >= 12:
                c.take(4)  # Array reference / ROTV marker.
            return c.count()
        def label():
            return c.label() if version >= 15 else self.name(c.get())
        joints = []
        for i in range(count()):
            name = label()
            orient, locator, parent, flags = c.get('16f'), c.get('3f'), c.get('B'), c.get('B')
            joints.append(dict(name=name, parent=-1 if parent == 255 else parent, orient=orient, locator=locator))
        for field in ('local', 'inverse'):
            if count() != len(joints):
                raise ValueError('Invalid bind matrix count')
            for joint in joints:
                joint[field] = np.array(c.get('16f')).reshape(4, 4)
        for i, joint in enumerate(joints):
            parent = joint['parent']
            if parent >= i:
                raise ValueError('Invalid skeleton hierarchy')
            joint['world'] = joint['local'] @ joints[parent]['world'] if parent >= 0 else joint['local']
        pre = list(c.take(count()))
        points = []
        for i in range(count()):
            name = c.label() if version >= 12 else self.name(c.get())
            points.append(dict(name=name, matrix=np.array(c.get('16f')).reshape(4, 4), joint=c.get('B')))
        remap = list(c.take(count()))
        metadata, layers = [], []
        if version <= 5:
            for layer in range(c.count(64)):
                offset, first = c.get(), len(metadata)
                name = self.name(offset) if offset < len(self.names) else ''
                for _ in range(2):
                    for joint in range(c.count(255)):
                        special = c.get()
                        if special != 0xffffffff:
                            metadata.append((0, joint, special, layer))
                    special = c.get()
                    if special != 0xffffffff:
                        metadata.append((1, 255, special, layer))
                layers.append((name, (first, len(metadata) - first, 0)))
        else:
            if version >= 10:
                c.take(c.count(64000000))
            metadata = [c.get('BBHB') for _ in range(count())]
            layers = [(label(), c.get('3H')) for _ in range(count())]
        return dict(version=version, joints=joints, points=points, remap=remap, metadata=metadata, layers=layers)

    def display(self):
        at = self.data.index(b'PSID')
        c = Cursor(self.data, at + 4)
        version = c.get()
        dx = version >= 26
        def array(width=0):
            if c.data[c.pos:c.pos + 4] == b'ROTV' or version >= 34:
                c.take(4)
            n = c.count(1000000)
            if width:
                c.take(n * width)
            return n
        if not dx:
            c.take(c.count(65536))
        clips = []
        commands = []
        if version >= 34:
            for i in range(array()):
                bindings = []
                for _ in range(c.get('H')):
                    material, command_ref, duplicate, part = c.get('HHIH')
                    c.take(6 if version >= 35 else 5)
                    if command_ref != duplicate or part >= len(self.parts):
                        raise ValueError('Invalid compact DC draw binding')
                    bindings.append((material, len(commands)))
                    commands.append((0xb3, 0, part))
                clips.append(bindings)
        else:
            commands = [c.get('BBI') for _ in range(array())]
            for i in range(array()):
                if dx:
                    pairs = [c.get('2I') for _ in range(c.get('H'))]
                    clips.append([(mat, cmd) for cmd, mat in pairs])
                else:
                    c.take(2)
                    mats = [c.get() for _ in range(c.count())]
                    refs = [c.get() for _ in range(c.count())]
                    clips.append(list(zip(mats, refs)))
        if not dx and version <= 10:
            # Static GSCs and skinned GHGs serialize different auxiliary maps.
            # Bound the authored special table by its count, names, transforms,
            # LOD arrays and clip references rather than assuming a map stride.
            candidates = []
            boundaries = [self.data.find(marker, c.pos) for marker in (b'LOGH', b'LBTN')]
            limit = min([p for p in boundaries if p >= 0] or [len(self.data)])
            counts = list(dict.fromkeys([len(clips)] + list(range(1, min(6, len(clips)) + 1))))
            starts = []
            for count in counts:
                pattern = struct.pack('>I', count)
                at = self.data.find(pattern, c.pos, limit)
                while at >= 0:
                    starts.append(at)
                    at = self.data.find(pattern, at + 1, limit)
            for at in starts:
                cursor = at + 4
                try:
                    count = struct.unpack_from('>I', self.data, at)[0]
                    if not 1 <= count <= len(clips): raise ValueError('Invalid special count')
                    for _ in range(count):
                        name = self.name(struct.unpack_from('>I', self.data, cursor)[0])
                        values = np.array(struct.unpack_from('>16f', self.data, cursor + 4))
                        clip, flags, n = struct.unpack_from('>3I', self.data, cursor + 180)
                        if not name or not np.isfinite(values).all() or not (values[-1] == 1 or not np.any(values)):
                            raise ValueError('Invalid special matrix')
                        if clip >= len(clips) and clip != 0xffffffff or not flags or flags & 65535 > 255 or n > 32:
                            raise ValueError('Invalid special binding')
                        cursor += (205 if version == 3 else 204) + 4 * n
                        if cursor > limit: raise ValueError('Special table exceeds model')
                    candidates.append(at)
                except (ValueError, struct.error, IndexError):
                    pass
            if len(candidates) != 1:
                raise ValueError('Ambiguous display special table: ' + str(candidates))
            c.pos = candidates[0]
        elif not dx:
            array(2)
            if version == 4:
                for _ in range(array()):
                    c.take(4)
                    c.take(c.get('H') * 4)
            else:
                array(4)
            if version <= 16:
                for _ in range(2 if version == 5 else 3):
                    array(4)
            if version <= 10:
                array(8)
                array(12 if version <= 5 else 16)
        specials = []
        for i in range(array()):
            start = c.pos
            if dx and version != 26:
                name = c.label()
                body = c.pos
                matrix = np.array(c.get('16f')).reshape(4, 4)
                c.pos = body + 112
                clip, flags = c.get('2I')
                if version >= 34: c.take(4)
                n = c.get()
                if n > 100000:
                    raise ValueError('Display header: %s %s %s %s %s' % (version, i, hex(start), clip, n))
                ranges = [c.get('f') for _ in range(n)]
                if version >= 34:
                    c.pos = body + 140 + 4 * n
                else:
                    c.pos = body + 132 + 4 * n
                    extra = c.count()
                    c.take(extra * 4)
            else:
                name = self.name(c.get())
                matrix = np.array(c.get('16f')).reshape(4, 4)
                c.pos = start + 180
                clip, flags, n = c.get('3I')
                if n > 100000:
                    raise ValueError('Display header: %s %s %s %s %s' % (version, i, hex(start), clip, n))
                ranges = [c.get('f') for _ in range(n)]
                c.pos = start + (205 if version == 3 else 204) + 4 * n
            if ranges and ranges[-1] == 0 and clip + len(ranges) <= len(clips):
                clip += len(ranges) - 1
            bindings = []
            if clip != 0xffffffff:
                for mat, cmd in clips[clip]:
                    opcode, flag, part = commands[cmd]
                    if opcode in (0x80, 0xb3):
                        if part >= len(self.parts):
                            raise ValueError('Display references absent part')
                        bindings.append((part, mat))
            specials.append(dict(name=name, matrix=matrix, bindings=bindings, flags=flags))
        return specials
