"""Bake depth-only native face masks for the static custom player renderer.

The original facial shader hides detail behind invisible masks. Exporting those
masks as white surfaces or dropping them leaves the mouth/eyes exposed. Rasterize
their front depth and clip only the covered detail, subdividing boundary triangles.
"""
import numpy as np


class FaceMask:
    def __init__(self, surfaces, resolution=768):
        points = np.concatenate([xyz for xyz, _ in surfaces])
        self.minimum = points[:, :2].min(axis=0)
        self.scale = (resolution - 3) / np.maximum(points[:, :2].max(axis=0) - self.minimum, 1e-6)
        self.depth = np.full((resolution, resolution), np.inf)
        for xyz, faces in surfaces:
            projected = (xyz[:, :2] - self.minimum) * self.scale + 1
            for face in faces:
                a, b, c = projected[face]
                lo = np.maximum(np.floor(np.minimum(np.minimum(a, b), c)).astype(int), 0)
                hi = np.minimum(np.ceil(np.maximum(np.maximum(a, b), c)).astype(int), resolution - 1)
                if np.any(lo > hi): continue
                denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(denominator) < 1e-10: continue
                yy, xx = np.mgrid[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
                u = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / denominator
                v = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / denominator
                inside = (u >= 0) & (v >= 0) & (u + v <= 1)
                z = u * xyz[face[0], 2] + v * xyz[face[1], 2] + (1 - u - v) * xyz[face[2], 2]
                patch = self.depth[yy, xx]
                self.depth[yy, xx] = np.where(inside, np.minimum(patch, z), patch)

    def hidden(self, points):
        xy = np.rint((points[..., :2] - self.minimum) * self.scale + 1).astype(int)
        outside = (xy < 0).any(axis=-1) | (xy >= self.depth.shape[0]).any(axis=-1)
        xy = np.clip(xy, 0, self.depth.shape[0] - 1)
        return ~outside & (points[..., 2] > self.depth[xy[..., 1], xy[..., 0]] + 1e-6)

    def clip(self, xyz, uv, joints, weights, faces, colours):
        fields = [xyz, uv, weights] + ([colours] if colours is not None else [])
        triangles = np.concatenate(fields, axis=1)[faces]
        ids = joints[faces[:, 0]]
        kept, kept_ids = [], []
        for level in range(3):
            p = triangles[:, :, :3]
            hidden = self.hidden(np.concatenate([p, p.mean(axis=1)[:, None]], axis=1))
            clear, covered = ~hidden.any(axis=1), hidden.all(axis=1)
            if level == 2: clear = ~hidden[:, 3]
            kept.append(triangles[clear]); kept_ids.append(ids[clear])
            mixed = ~(clear | covered)
            t, ids = triangles[mixed], ids[mixed]
            if level == 2 or not len(t): break
            a, b, c = t[:, 0], t[:, 1], t[:, 2]
            ab, bc, ca = (a + b) / 2, (b + c) / 2, (c + a) / 2
            triangles = np.concatenate([np.stack(v, axis=1) for v in ((a, ab, ca), (ab, b, bc), (ca, bc, c), (ab, bc, ca))])
            ids = np.tile(ids, (4, 1))
        data = np.concatenate(kept).reshape((-1, triangles.shape[-1]))
        joints = np.repeat(np.concatenate(kept_ids), 3, axis=0).astype('<u2')
        return (data[:, :3], data[:, 3:5], joints, data[:, 5:9].astype('<f4'),
                np.arange(len(data), dtype=np.uint32).reshape((-1, 3)), data[:, 9:13] if colours is not None else None)
