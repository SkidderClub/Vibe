"""Convert extracted TCS PC NU20 models into glTF for Vibe's CustomModelRenderer.

Reads the game's layer table, bone transforms, materials, triangle strips and DDS.
File layout research: Sluicer, https://rockraidersunited.com/topic/6759-noesis-script-for-ghg-files/
No game files are downloaded by this tool. Requires Pillow and numpy.
"""
import io
import json
import struct
import sys
from pathlib import Path
import numpy as np
from PIL import Image


class Nu:
    def __init__(self, path):
        self.b = path.read_bytes()
        self.nu = self.i(0) + 4
        assert self.b[self.nu:self.nu + 4] == b'NU20'
        self.g = self.ptr(self.nu + 28) - 8
        self.ms = self.b.find(b'MS00', self.nu)

    def i(self, p):
        return struct.unpack_from('<i', self.b, p)[0]

    def ptr(self, p):
        v = self.i(p)
        return p + v if v else 0

    def images(self):
        pos = 6
        result = []
        for _ in range(struct.unpack_from('<H', self.b, 4)[0]):
            size = self.i(pos + 20)
            if self.b[pos + 24:pos + 28] != b'DDS ':
                # Some headers count externally referenced textures too; the vertex
                # buffer begins immediately after the last embedded DDS image.
                break
            result.append(Image.open(io.BytesIO(self.b[pos + 24:pos + 24 + size])).convert('RGBA'))
            pos += 24 + size
        self.buffers = pos
        return result

    def convert(self, out, layers):
        out.mkdir(parents=True, exist_ok=True)
        images = self.images()
        for i, image in enumerate(images):
            image.save(out / ('texture%d.png' % i))
        b = self.b
        g = self.g
        count = self.i(g + 368)
        bonebase = self.ptr(g + 372)
        localbase = self.ptr(g + 376)
        bones, names, parents, locals_ = [], [], [], []
        for i in range(count):
            p = bonebase + i * 96
            at = self.ptr(p + 76)
            names.append(b[at:b.index(b'\0', at)].decode('latin1'))
            parent = struct.unpack_from('<b', b, p + 80)[0]
            local = np.array(struct.unpack_from('<16f', b, localbase + i * 64)).reshape(4, 4)
            bones.append(local @ bones[parent] if parent >= 0 else local)
            parents.append(parent)
            locals_.append(local)
        pos = self.buffers
        buffers = []
        for _ in range(2):
            n = struct.unpack_from('<H', b, pos)[0]
            pos += 2
            items = []
            for j in range(n):
                size = self.i(pos)
                items.append(b[pos + 4:pos + 4 + size])
                pos += 4 + size
            buffers.append(items)
        vertices, indices = buffers
        meta = self.ptr(g + 60)
        nparts = self.i(meta + 20)
        partmeta = [self.ptr(meta + 32 + i * 4) for i in range(nparts)]
        # Texture IDs include empty external-reference slots; embedded DDS blocks do not.
        texture_ids = {}
        image_meta = self.ptr(g + 16)
        for i in range(self.i(g + 12)):
            width, height = struct.unpack_from('<2i', b, self.ptr(image_meta + i * 4))
            if width and height:
                texture_ids[i] = len(texture_ids)
        assert len(texture_ids) == len(images)
        materials = []
        for i in range(self.i(self.ms + 8)):
            p = self.ms + 16 + i * 708
            color = list(struct.unpack_from('<4f', b, p + 84))
            # TCS stores an unused fourth diffuse component, not glTF opacity.
            color[3] = 1.0
            if not all(np.isfinite(v) and 0 <= v <= 1 for v in color):
                raise ValueError('Invalid material color at ' + str(p))
            tex = texture_ids.get(struct.unpack_from('<h', b, p + 116)[0], -1)
            mat = {'name': 'material%d' % i, 'doubleSided': True, 'pbrMetallicRoughness':
                   {'baseColorFactor': color, 'metallicFactor': 0, 'roughnessFactor': .65}}
            if 0 <= tex < len(images):
                mat['pbrMetallicRoughness']['baseColorTexture'] = {'index': tex}
                mat['alphaMode'] = 'MASK'
                mat['alphaCutoff'] = .25
            materials.append(mat)
        binary = bytearray()
        views, accessors, primitives = [], [], []

        def accessor(data, component, kind, bounds=False):
            data = np.asarray(data)
            while len(binary) % 4:
                binary.append(0)
            off = len(binary)
            binary.extend(data.tobytes())
            vi = len(views)
            views.append({'buffer': 0, 'byteOffset': off, 'byteLength': len(binary) - off})
            item = {'bufferView': vi, 'componentType': component, 'count': len(data), 'type': kind}
            if bounds:
                item.update(min=data.min(axis=0).tolist(), max=data.max(axis=0).tolist())
            accessors.append(item)
            return len(accessors) - 1

        part_index = 0
        layerbase = self.ptr(g + 412)
        for layer in range(self.i(g + 408)):
            for slot in range(4):
                pointer = self.ptr(layerbase + layer * 20 + 4 + slot * 4)
                if not pointer:
                    continue
                pairs = [(j, self.ptr(pointer + j * 4)) for j in range(count)] if slot in (0, 2) else [(-1, pointer)]
                for bone, obj in pairs:
                    if not obj:
                        continue
                    matrix = self.ptr(obj + 8)
                    geom = self.ptr(matrix + 176)
                    num = self.i(geom)
                    mats = self.ptr(geom + 4)
                    matids = [self.i(mats + j * 4) for j in reversed(range(num))]
                    for matid in matids:
                        p = partmeta[part_index]
                        part_index += 1
                        if layer not in layers:
                            continue
                        numidx = self.i(p + 4) + 2
                        stride = struct.unpack_from('<H', b, p + 8)[0]
                        vstart, nv, istart, ib, vb = struct.unpack_from('<5i', b, p + 20)
                        vdata = vertices[vb]
                        xyz = np.array([struct.unpack_from('<3f', vdata, (vstart + j) * stride) for j in range(nv)])
                        joints = np.zeros((nv, 4), dtype='<u2')
                        weights = np.zeros((nv, 4), dtype='<f4')
                        if bone >= 0:
                            xyz = (np.c_[xyz, np.ones(nv)] @ bones[bone])[:, :3]
                            joints[:, 0], weights[:, 0] = bone, 1
                        elif stride in (32, 36, 40, 44):
                            # Shader vertices index the eight-bone palette in the part header.
                            palette = list(b[p + 10:p + 18])
                            for j in range(nv):
                                w = np.array(struct.unpack_from('<4B', vdata, (vstart + j) * stride + stride - 8)) / 255.0
                                ids = struct.unpack_from('<4B', vdata, (vstart + j) * stride + stride - 4)
                                for k in range(4):
                                    if w[k] > 0 and ids[k] < 8 and palette[ids[k]] < count:
                                        joints[j, k], weights[j, k] = palette[ids[k]], w[k]
                                total = weights[j].sum()
                                if total: weights[j] /= total
                                else: weights[j, 0] = 1
                        else:
                            raise ValueError('Unsupported skinned vertex layout: ' + str(stride))
                        uvpos = ({32: 16, 36: 20, 40: 24, 44: 28} if bone < 0 else {28: 20, 32: 24, 36: 28, 40: 24, 44: 28}).get(stride)
                        uv = np.array([struct.unpack_from('<2f', vdata, (vstart + j) * stride + uvpos) for j in range(nv)], dtype='<f4') if uvpos else np.zeros((nv, 2), dtype='<f4')
                        strip = struct.unpack_from('<%dH' % numidx, indices[ib], istart * 2)
                        faces = []
                        for j in range(numidx - 2):
                            tri = strip[j:j + 3] if j % 2 == 0 else strip[j:j + 3][::-1]
                            if len(set(tri)) == 3 and max(tri) < nv:
                                faces.append(tri)
                        if not faces:
                            continue
                        # GHG texture coordinates use the same top-origin convention as glTF.
                        primitives.append({'attributes': {'POSITION': accessor(xyz.astype('<f4'), 5126, 'VEC3', True),
                                                         'TEXCOORD_0': accessor(uv, 5126, 'VEC2'),
                                                         'JOINTS_0': accessor(joints, 5123, 'VEC4'),
                                                         'WEIGHTS_0': accessor(weights, 5126, 'VEC4')},
                                           'indices': accessor(np.array(faces, dtype='<u4').flatten(), 5125, 'SCALAR'),
                                           'material': matid})
        assert part_index == nparts, (part_index, nparts)
        nodes = [{'mesh': 0, 'skin': 0}]
        for i, name in enumerate(names):
            # TCS's arm labels are mirrored relative to glTF's forward convention.
            # Matching their anatomical sides also makes the existing fitter face -Z correctly.
            name = name.replace('left', '@RIGHT@').replace('right', 'left').replace('@RIGHT@', 'right')
            name = name.replace('Shoulder', 'UpperArm')
            node = {'name': name, 'matrix': locals_[i].flatten().tolist()}
            children = [j + 1 for j, parent in enumerate(parents) if parent == i]
            if children: node['children'] = children
            nodes.append(node)
        ibm = accessor(np.array([np.linalg.inv(bone).flatten() for bone in bones], dtype='<f4'), 5126, 'MAT4')
        gltf = {'asset': {'version': '2.0', 'generator': 'Vibe local TCS converter'},
                'buffers': [{'uri': 'model.bin', 'byteLength': len(binary)}], 'bufferViews': views, 'accessors': accessors,
                'images': [{'uri': 'texture%d.png' % i} for i in range(len(images))],
                'textures': [{'source': i} for i in range(len(images))], 'materials': materials,
                'meshes': [{'primitives': primitives}], 'nodes': nodes,
                'skins': [{'joints': list(range(1, count + 1)), 'inverseBindMatrices': ibm}],
                'scenes': [{'nodes': [0] + [i + 1 for i, parent in enumerate(parents) if parent < 0]}], 'scene': 0}
        (out / 'model.gltf').write_text(json.dumps(gltf))
        (out / 'model.bin').write_bytes(binary)
        print(out.name, len(primitives), 'meshes;', sum(accessors[p['indices']]['count'] // 3 for p in primitives), 'triangles', flush=True)


if __name__ == '__main__':
    Nu(Path(sys.argv[1])).convert(Path(sys.argv[2]), [int(v) for v in sys.argv[3].split(',')])
