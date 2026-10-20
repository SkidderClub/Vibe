"""Assemble native character layers and attachments into renderer-ready glTF."""
import json
import re
import struct
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image
from lego_catalog import normalize
from lego_assets import Game, GAMES
from lego_definition import definition
from lego_material import materials, dds_images
from lego_nxg import Model


class Exporter:
    # These exact references are absent from every archive in the supplied
    # installations. Keep the native character/material instead of substituting
    # another character or failing the entire playable entry.
    MISSING_ATTACHMENTS = {('hobbit', 'face_hawkeye'), ('dc', 'rigidhair_cw_vandalsavage'),
                           ('dc', 'face_hulk_2017'), ('dc', 'chars/super_character/hat/hat_sportshelmet')}
    MISSING_PRINTS = {('hp2', 'minifigs/super_characters/leg/leg_ fleurbluejacket'),
                      ('hp2', 'minifigs/super_characters/hips/hips_ fleurbluejacket'),
                      ('dc', 'chars/super_character_texture/arm/arm_malcolmmerlyn_right_diff'),
                      ('dc', 'chars/super_character_texture/head/head_polkadotmanback_mask_diff'),
                      ('dc', 'chars/super_character_texture/arm/arm_psychopirate_left_diff'),
                      ('dc', 'chars/super_character_texture/arm/arm_psychopirate_right_diff'),
                      ('dc', 'chars/super_character_texture/leg/leg_wondergirl_diff'),
                      ('dc', 'chars/super_character_texture/body/body_securityguardnoirvariant_front_diff'),
                      ('dc', 'chars/super_character_texture/body/body_securityguardnoirvariant_back_diff')}
    def __init__(self, game, hgos, configs):
        self.game, self.hgos, self.configs = game, hgos, configs

    @lru_cache(maxsize=2048)
    def resource_definition(self, resource):
        key = normalize(resource)
        config = self.configs.get(Path(key).stem)
        if config and '.cd' in config:
            return definition(self.game.get(config['.cd']).read_bytes())['objects']
        return []

    def resolve(self, resource, kind='model', chain=()):
        if resource in chain or len(chain) > 8:
            raise ValueError('Recursive character model reference: ' + resource)
        key = normalize(resource)
        if key in self.hgos:
            key = self.hgos[key]
        if key.startswith('chars/additionalcontent/'):
            key = key[len('chars/'):]
        key = re.sub(r'\.(hgo|ghg|gsc|tex)$', '', key)
        suffixes = ('_dx11.ghg', '_nxg.ghg', '_dx11.gsc', '_nxg.gsc') if kind == 'model' else ('_dx11.tex', '_nxg.tex')
        for stem in (key, 'chars/' + key):
            for suffix in suffixes:
                if stem + suffix in self.game.index:
                    return stem + suffix
        matches = [p for p in self.game.index if any(p.endswith('/' + key + s) for s in suffixes)]
        if len(matches) == 1:
            return matches[0]
        if kind == 'model':
            objects = self.resource_definition(resource)
            fields = next((o['fields'] for o in objects if o['class'] == 'Character Definition'), {})
            reference = fields.get('Override Model File') or fields.get('Model File') or fields.get('Scene File') or self.hgos.get(str(fields.get('Skeleton Name', '')).lower())
            if reference and normalize(reference) != key:
                return self.resolve(reference, kind, chain + (resource,))
        raise ValueError('Unresolved %s resource: %s' % (kind, resource))

    @lru_cache(maxsize=12)
    def model(self, resource):
        path = self.resolve(resource)
        try:
            return Model(self.game.get(path), skeleton=path.endswith('.ghg'))
        except ValueError as error:
            raise ValueError(path + ': ' + str(error)) from error

    @lru_cache(maxsize=512)
    def texture(self, resource):
        try:
            path = self.resolve(resource, 'texture')
            images = dds_images(self.game.get(path).read_bytes())
        except ValueError:
            # Some shipped CDs retain an obsolete directory/spelling for a
            # uniform LEGO palette swatch. Resolve the same authored colour
            # from the supplied games, verifying that it is a uniform image.
            basename = normalize(resource).rsplit('/', 1)[-1].replace('litegrey', 'lightgrey')
            if not basename.startswith('lego_'): raise
            images = []
            for game in [self.game] + [Game(k) for k in GAMES if k != self.game.key]:
                paths = [p for p in game.index if p.rsplit('/', 1)[-1] in (basename + '_nxg.tex', basename + '_dx11.tex')]
                for p in paths:
                    found = dds_images(game.get(p).read_bytes())
                    if found and all(lo == hi for lo, hi in found[0].getextrema()):
                        images = found; break
                if images: break
            if not images: raise ValueError('Missing uniform LEGO colour: ' + resource)
        if not images:
            raise ValueError('No diffuse DDS in ' + path)
        return images[0]

    @lru_cache(maxsize=12)
    def surfaces(self, path):
        model = self.model(path)
        data = model.data
        for suffix in ('.nxg_textures', '.dx11_textures'):
            source = re.sub(r'\.(ghg|gsc)$', suffix, normalize(str(model.path.relative_to(self.game.cache))))
            if source in self.game.index:
                data = self.game.get(source).read_bytes()
                break
        try:
            return materials(model), dds_images(data)
        except ValueError as error:
            raise ValueError(str(model.path) + ': ' + str(error)) from error

    def convert(self, row, out):
        self.warnings = []
        self.baked_prints = {}
        config = row['config']
        cd = definition(self.game.get(config['.cd']).read_bytes()) if '.cd' in config else {'objects': []}
        objects = cd['objects']
        fields = next((o['fields'] for o in objects if o['class'] == 'Character Definition'), {})
        model_path = fields.get('Override Model File') or fields.get('Model File') or row['model'] or fields.get('Skeleton Name')
        if not model_path:
            raise ValueError('No model declared by ' + row['character'])
        model = self.model(model_path)
        rig = model.skeleton
        if not rig or not rig['joints']:
            raise ValueError('Character has no bind skeleton')
        mask = fields.get('Default Layers')
        text = self.game.text(config['.txt']) if '.txt' in config else ''
        if mask is None:
            match = re.search(r'layers_(?:special|high)\s*=\s*([0-9, ]+)', text, re.I)
            mask = sum(1 << int(v) for v in match[1].split(',') if v.strip()) if match else 0x3f
        specials = {}
        for obj in objects:
            if obj['class'] == 'Character Layer Special':
                f = obj['fields']; layer = f.get('Layer Id', f.get('Layer'))
                specials[layer] = f.get('Layer Special') or f.get('Layer Specials', 1)
        overrides, tints = {}, {}
        for obj in objects:
            f = obj['fields']; role = f.get('Material Name', f.get('Material'))
            if obj['class'] in ('Character Texture', 'Character Material Mapping'):
                texture = f.get('Texture File') or f.get('Diffuse Texture File 1')
                slot = f.get('Texture Slot', 0)
                if texture and (self.game.key != 'dc' or slot in (0, 1)):
                    overrides[role, slot] = texture
            if obj['class'] == 'Character Material':
                tints[role] = f.get('Layer 1 Tint Colour', [1, 1, 1])
                tints[role, 2] = f.get('Layer 2 Tint Colour', [1, 1, 1])
        builder = Gltf(rig)
        self.append(builder, model_path, mask, specials, overrides, tints)
        for obj in objects:
            if obj['class'] != 'Character Attachment':
                continue
            f = obj['fields']; layer = f.get('Layer', -1)
            if layer >= 0 and not (mask & (1 << layer)):
                continue
            resource = f.get('Model File') or f.get('Resource File')
            if not resource:
                continue
            if (self.game.key, normalize(resource)) in self.MISSING_ATTACHMENTS:
                self.warnings.append(dict(resource=resource, reason='Cosmetic attachment absent from supplied game archives'))
                continue
            logical = f.get('Locator', 0)
            if logical >= len(rig['remap']) or rig['remap'][logical] >= len(rig['points']):
                raise ValueError('Attachment locator outside declared table')
            point = rig['points'][rig['remap'][logical]]
            joint = point['joint']
            offset = np.array(f.get('Object Offset', f.get('Offset Matrix', np.eye(4).flatten()))).reshape(4, 4)
            transform = offset @ point['matrix'] @ rig['joints'][joint]['world']
            self.append(builder, resource, None, {}, overrides, {},
                        transform, joint, f.get('Tint Colour', [1, 1, 1]), f.get('Object Name', ''))
        builder.write(out)
        return dict(model=self.resolve(model_path), layers=mask, primitives=len(builder.primitives),
                    triangles=builder.triangles, attachments=sum(o['class'] == 'Character Attachment' for o in objects), warnings=self.warnings)

    def append(self, builder, path, mask, choices, overrides, tints, transform=None, parent=0, tint=(1, 1, 1), object_name=''):
        if transform is not None:
            objects = self.resource_definition(path)
            fields = next((o['fields'] for o in objects if o['class'] == 'Character Definition'), {})
            mask = fields.get('Default Layers', mask)
            if fields.get('Scene Object'):
                object_name = fields['Scene Object']
            tint = [a * b for a, b in zip(tint, fields.get('Default Tint Colour', (1, 1, 1)))]
            overrides, tints = dict(overrides), dict(tints)
            for obj in objects:
                f = obj['fields']; role = f.get('Material Name', f.get('Material'))
                if obj['class'] in ('Character Texture', 'Character Material Mapping'):
                    texture = f.get('Texture File') or f.get('Diffuse Texture File 1')
                    slot = f.get('Texture Slot', 0)
                    if texture and slot in (0, 1): overrides[role, slot] = texture
                if obj['class'] == 'Character Material':
                    tints[role] = f.get('Layer 1 Tint Colour', tint)
                    tints[role, 2] = f.get('Layer 2 Tint Colour', [1, 1, 1])
                if obj['class'] == 'Character Layer Special':
                    f = obj['fields']; choices[f.get('Layer Id', f.get('Layer'))] = f.get('Layer Special') or f.get('Layer Specials', 1)
        model = self.model(path)
        native_materials, images = self.surfaces(path)
        chosen = []
        if mask is not None and model.skeleton:
            for layer, (_, span) in enumerate(model.skeleton['layers']):
                if not (mask & (1 << layer)):
                    continue
                first, rigid, skinned = span
                items = model.skeleton['metadata'][first:first + rigid + skinned]
                # Multi-piece body layers (body, hair, studs, depth head) must
                # keep every piece. Two-entry modern minifig layers instead
                # contain alternate body/skeleton variants.
                default = 'All' if transform is not None or self.game.key == 'hp1' or len(items) > 2 else 1
                choice = choices.get(layer, default)
                if choice == 'All':
                    chosen.extend(items)
                elif isinstance(choice, str):
                    chosen.extend(i for i in items if model.specials[i[2]]['name'].lower() == choice.lower())
                else:
                    chosen.extend(i for n, i in enumerate(items) if choice & (1 << n))
        elif model.skeleton and model.skeleton['layers']:
            # Attached hair/capes have authored active layers, generally TT0.
            first, rigid, skinned = model.skeleton['layers'][0][1]
            chosen = model.skeleton['metadata'][first:first + rigid + skinned]
        else:
            chosen = [(0, 0, i, 0) for i, s in enumerate(model.specials)
                      if not object_name or s['name'].lower() == object_name.lower()]
            if not chosen and object_name:
                drawable = [(0, 0, i, 0) for i, s in enumerate(model.specials) if s['bindings']]
                if len(drawable) != 1:
                    raise ValueError('Ambiguous scene object %s in %s' % (object_name, path))
                chosen = drawable
        if self.game.key == 'hp1' and transform is not None and '/faces/' in self.resolve(path):
            # HP1 supplies an original printed static face alongside its animated
            # blend-shape face. Use that authored surface in this static renderer;
            # the animated base alone has no neutral eye/mouth deformation.
            printed = [i for i, s in enumerate(model.specials) if s['bindings'] and all(
                5 in model.parts[part]['attributes'] and 0 <= native_materials[mat]['texture'] < len(images)
                for part, mat in s['bindings'])]
            if printed:
                chosen = [(1, 255, printed[-1], 0)]
        face_mask = None
        if transform is not None:
            masks = []
            for kind, joint, special_id, layer in chosen:
                for part_id, mat_id in model.specials[special_id]['bindings']:
                    if 'OCCLUDE' in native_materials[mat_id]['name'].upper():
                        part = model.parts[part_id]; positions = part['attributes'][0][:, :3]
                        positions = (np.c_[positions, np.ones(len(positions))] @ transform)[:, :3]
                        masks.append((positions, part['triangles']))
            if masks:
                from lego_face import FaceMask
                face_mask = FaceMask(masks)
        for kind, joint, special_id, layer in chosen:
            special = model.specials[special_id]
            matrix = np.eye(4) if transform is not None else special['matrix']
            if not np.any(matrix) or abs(np.linalg.det(matrix)) < 1e-10:
                matrix = np.eye(4)
            if kind == 0 and model.skeleton:
                matrix = matrix @ model.skeleton['joints'][joint]['world']
            if transform is not None:
                matrix = matrix @ transform
            for part_id, mat_id in special['bindings']:
                mat = native_materials[mat_id]
                if mat['name'].upper().split(':')[0] in ('ZHEAD', 'Z_HEAD', 'SHADOW_IMPOSTERS') or 'OCCLUDE' in mat['name'].upper():
                    continue
                role = mat['role']
                image = images[mat['texture']] if 0 <= mat['texture'] < len(images) else None
                override = overrides.get((role, 0)) or overrides.get((role, 1))
                colour = list(tints.get(role, [a * b for a, b in zip(mat.get('colour', (1, 1, 1)), tint)])) + [1]
                if override:
                    if (self.game.key, normalize(override)) in self.MISSING_PRINTS:
                        self.warnings.append(dict(resource=override, reason='Print absent from supplied archives; native material retained'))
                        override = None
                    else: image = self.texture(override)
                    # Older games author their colour in the texture itself.
                    if override and self.game.key != 'dc': colour = [1, 1, 1, 1]
                if self.game.key == 'dc' and role is not None and role in tints:
                    # DCSV prints are alpha layers over solid coloured plastic.
                    # Baking the layers prevents the unprinted torso/limbs from
                    # disappearing when the custom renderer applies glTF alpha.
                    from PIL import ImageChops
                    base = tuple(round(max(0, min(1, v)) * 255) for v in tints[role]) + (255,)
                    print_file = overrides.get((role, 1))
                    if print_file and (self.game.key, normalize(print_file)) in self.MISSING_PRINTS: print_file = None
                    if print_file:
                        cache_key = (role, base, print_file, tuple(tints.get((role, 2), [1, 1, 1])))
                        image = self.baked_prints.get(cache_key)
                        if image is None:
                            overlay = self.texture(print_file).copy()
                            if 'mask_skin' in print_file.lower():
                                alpha = ImageChops.multiply(overlay.getchannel('A'), ImageChops.invert(overlay.getchannel('R')))
                                skin = tuple(round(max(0, min(1, v)) * 255) for v in tints.get((role, 2), [1, 1, 1])) + (255,)
                                overlay = Image.new('RGBA', overlay.size, skin); overlay.putalpha(alpha)
                            image = Image.alpha_composite(Image.new('RGBA', overlay.size, base), overlay)
                            self.baked_prints[cache_key] = image
                        colour = [1, 1, 1, 1]
                    else:
                        image = None
                part = model.parts[part_id]
                attrs = part['attributes']
                xyz = (np.c_[attrs[0][:, :3], np.ones(len(attrs[0]))] @ matrix)[:, :3]
                uv_sets = []
                for variable in (5, 6, 7):
                    if variable in attrs:
                        uv_sets.extend(attrs[variable][:, i:i + 2] for i in range(0, attrs[variable].shape[1], 2))
                uv_index = 1 if override and self.game.key in ('hp1', 'hp2', 'hobbit', 'dc') else mat['uv']
                uv = uv_sets[min(uv_index, len(uv_sets) - 1)] if uv_sets else np.zeros((len(xyz), 2))
                vertex_colour = attrs.get(2)
                if vertex_colour is not None:
                    vertex_colour = np.clip(vertex_colour / (255 if model.dx else 127), 0, 1)
                    if model.dx: vertex_colour = vertex_colour[:, [2, 1, 0, 3]]
                    # TT stores shader masks in COLOR.a (CW bodies use zero).
                    # Opacity comes from diffuse alpha, not that auxiliary mask.
                    vertex_colour[:, 3] = 1
                joints = np.zeros((len(xyz), 4), dtype='<u2')
                weights = np.zeros((len(xyz), 4), dtype='<f4')
                if kind != 0 and 9 in attrs and 10 in attrs and model.skeleton:
                    source = attrs[9].astype(int)
                    weight = attrs[10] / 255 if attrs[10].max() > 1 else attrs[10]
                    for channel in range(min(4, source.shape[1])):
                        for pi, bone in enumerate(part['palette']):
                            indices = source[:, channel] == pi
                            if bone < len(model.skeleton['joints']):
                                name = model.skeleton['joints'][bone]['name']
                                target = builder.bone_names.get(name.lower(), parent)
                                joints[indices, channel], weights[indices, channel] = target, weight[indices, channel]
                else:
                    joints[:, 0] = parent if transform is not None else joint
                    weights[:, 0] = 1
                total = weights.sum(axis=1)
                weights[total == 0, 0] = 1
                total[total == 0] = 1
                weights /= total[:, None]
                faces = part['triangles']
                if face_mask is not None:
                    xyz, uv, joints, weights, faces, vertex_colour = face_mask.clip(xyz, uv, joints, weights, faces, vertex_colour)
                builder.primitive(xyz, uv, joints, weights, faces, image, colour, mat['name'], vertex_colour)


class Gltf:
    def __init__(self, rig):
        self.rig = rig
        self.bone_names = {j['name'].lower(): i for i, j in enumerate(rig['joints'])}
        self.binary, self.views, self.accessors, self.primitives = bytearray(), [], [], []
        self.images, self.materials, self.image_ids = [], [], {}
        self.triangles = 0

    def accessor(self, data, component, kind, bounds=False):
        data = np.asarray(data)
        self.binary.extend(bytes((-len(self.binary)) % 4))
        offset = len(self.binary)
        self.binary.extend(data.tobytes())
        index = len(self.views)
        self.views.append(dict(buffer=0, byteOffset=offset, byteLength=len(self.binary) - offset))
        result = dict(bufferView=index, componentType=component, count=len(data), type=kind)
        if bounds: result.update(min=data.min(axis=0).tolist(), max=data.max(axis=0).tolist())
        self.accessors.append(result)
        return len(self.accessors) - 1

    def primitive(self, xyz, uv, joints, weights, faces, image, colour, name, vertex_colour=None):
        if not len(faces): return
        if not np.isfinite(xyz).all() or np.abs(xyz).max() > 10000:
            raise ValueError('Invalid assembled geometry')
        if vertex_colour is not None:
            # The fixed-function model pipeline uses a vertex colour array.
            # Bake the material factor into it so that the array cannot replace
            # skin/hair tints with white when OpenGL draws the display list.
            vertex_colour = vertex_colour * np.asarray(colour)
            colour = [1, 1, 1, 1]
        material = dict(name=name, doubleSided=True, pbrMetallicRoughness=dict(
            baseColorFactor=colour, metallicFactor=0, roughnessFactor=.65))
        if image is not None:
            key = id(image)
            if key not in self.image_ids:
                self.image_ids[key] = len(self.images)
                self.images.append(image)
            material['pbrMetallicRoughness']['baseColorTexture'] = dict(index=self.image_ids[key])
            material.update(alphaMode='MASK', alphaCutoff=.25)
        self.materials.append(material)
        attributes = {key: self.accessor(data, component, kind, key == 'POSITION') for key, data, component, kind in (
            ('POSITION', xyz.astype('<f4'), 5126, 'VEC3'), ('TEXCOORD_0', uv.astype('<f4'), 5126, 'VEC2'),
            ('JOINTS_0', joints, 5123, 'VEC4'), ('WEIGHTS_0', weights, 5126, 'VEC4'))}
        if vertex_colour is not None:
            attributes['COLOR_0'] = self.accessor(vertex_colour.astype('<f4'), 5126, 'VEC4')
        self.primitives.append(dict(attributes=attributes, indices=self.accessor(faces.astype('<u4').flatten(), 5125, 'SCALAR'), material=len(self.materials) - 1))
        self.triangles += len(faces)

    def write(self, out):
        if not self.primitives:
            raise ValueError('Character has no assembled geometry')
        out.mkdir(parents=True, exist_ok=True)
        nodes = [dict(mesh=0, skin=0)]
        bones = self.rig['joints']
        for i, joint in enumerate(bones):
            name = re.sub('Left|Right', lambda m: 'Right' if m[0] == 'Left' else 'Left', joint['name'])
            name = name.replace('Shoulder', 'UpperArm')
            node = dict(name=name, matrix=joint['local'].flatten().tolist())
            children = [n + 1 for n, j in enumerate(bones) if j['parent'] == i]
            if children: node['children'] = children
            nodes.append(node)
        ibm = self.accessor(np.array([np.linalg.inv(j['world']).flatten() for j in bones], dtype='<f4'), 5126, 'MAT4')
        result = dict(asset=dict(version='2.0', generator='Vibe local LEGO character assembler'),
            buffers=[dict(uri='model.bin', byteLength=len(self.binary))], bufferViews=self.views, accessors=self.accessors,
            nodes=nodes, meshes=[dict(primitives=self.primitives)], materials=self.materials,
            images=[dict(uri='texture%d.png' % i) for i in range(len(self.images))],
            textures=[dict(source=i) for i in range(len(self.images))],
            skins=[dict(joints=list(range(1, len(bones) + 1)), inverseBindMatrices=ibm)],
            scenes=[dict(nodes=[0] + [i + 1 for i, j in enumerate(bones) if j['parent'] < 0])], scene=0)
        (out / 'model.gltf').write_text(json.dumps(result), encoding='utf8')
        (out / 'model.bin').write_bytes(self.binary)
        for i, image in enumerate(self.images):
            image = image.copy(); image.thumbnail((512, 512))
            image.save(out / ('texture%d.png' % i))
