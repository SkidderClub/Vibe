"""Render a transparent portrait directly from the imported character geometry."""
import io
import json
import zipfile
import numpy as np
from PIL import Image


def render(bundle, size=128, head=True):
    with zipfile.ZipFile(bundle) as archive:
        gltf = json.loads(archive.read('model.gltf'))
        binary = archive.read('model.bin')
        textures = [np.asarray(Image.open(io.BytesIO(archive.read(i['uri']))).convert('RGBA')) for i in gltf.get('images', [])]
    def data(index):
        a = gltf['accessors'][index]; v = gltf['bufferViews'][a['bufferView']]
        width = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}[a['type']]
        dtype = {5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']]
        return np.frombuffer(binary, dtype=dtype, count=a['count'] * width, offset=v.get('byteOffset', 0) + a.get('byteOffset', 0)).reshape((-1, width))
    primitives = gltf['meshes'][0]['primitives']
    positions = np.concatenate([data(p['attributes']['POSITION']) for p in primitives])
    lo, hi = positions.min(axis=0), positions.max(axis=0)
    height = hi[1] - lo[1]
    bottom = lo[1] + height * (.60 if head else 0)
    top = hi[1] + height * .04
    extent = max(top - bottom, (hi[0] - lo[0]) * (.55 if head else 1.05))
    center = (lo[0] + hi[0]) / 2
    canvas = np.zeros((size, size, 4), dtype=np.uint8)
    depth = np.full((size, size), np.inf)
    for primitive in primitives:
        xyz = data(primitive['attributes']['POSITION'])
        uv = data(primitive['attributes']['TEXCOORD_0'])
        projected = np.c_[(xyz[:, 0] - center) / extent * size + size / 2, (top - xyz[:, 1]) / extent * size]
        material = gltf['materials'][primitive.get('material', 0)]['pbrMetallicRoughness']
        colour = np.array(material.get('baseColorFactor', [1, 1, 1, 1]), dtype=float)
        tex = material.get('baseColorTexture')
        image = textures[gltf['textures'][tex['index']]['source']] if tex else None
        for indices in data(primitive['indices']).reshape((-1, 3)):
            pts = projected[indices]
            minimum = np.maximum(np.floor(pts.min(axis=0)).astype(int), 0)
            maximum = np.minimum(np.ceil(pts.max(axis=0)).astype(int), size - 1)
            if np.any(minimum > maximum): continue
            a, b, c = pts
            denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(denominator) < 1e-7: continue
            yy, xx = np.mgrid[minimum[1]:maximum[1] + 1, minimum[0]:maximum[0] + 1]
            w0 = ((b[1] - c[1]) * (xx + .5 - c[0]) + (c[0] - b[0]) * (yy + .5 - c[1])) / denominator
            w1 = ((c[1] - a[1]) * (xx + .5 - c[0]) + (a[0] - c[0]) * (yy + .5 - c[1])) / denominator
            w = np.stack((w0, w1, 1 - w0 - w1), axis=-1)
            z = w @ xyz[indices, 2]
            valid = (w >= -1e-6).all(axis=-1) & (z < depth[yy, xx])
            if not valid.any(): continue
            rgba = np.broadcast_to(colour * 255, w.shape[:2] + (4,)).copy()
            if 'COLOR_0' in primitive['attributes']:
                rgba *= w @ data(primitive['attributes']['COLOR_0'])[indices]
            if image is not None:
                coords = (w @ uv[indices]) % 1
                tx = np.minimum((coords[..., 0] * image.shape[1]).astype(int), image.shape[1] - 1)
                ty = np.minimum((coords[..., 1] * image.shape[0]).astype(int), image.shape[0] - 1)
                rgba *= image[ty, tx] / 255
            valid &= rgba[..., 3] >= 64
            normal = np.cross(xyz[indices[1]] - xyz[indices[0]], xyz[indices[2]] - xyz[indices[0]])
            length = np.linalg.norm(normal)
            lighting = .64 + .36 * abs(np.dot(normal / length, [-.25, .45, -.857])) if length else 1
            rgba[..., :3] *= lighting
            canvas[yy[valid], xx[valid]] = np.clip(rgba[valid], 0, 255).astype(np.uint8)
            depth[yy[valid], xx[valid]] = z[valid]
    return Image.fromarray(canvas)
