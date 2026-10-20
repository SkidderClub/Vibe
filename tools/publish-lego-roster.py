"""Validate complete local imports before publishing the runtime character catalog.

Run after import-lego-roster.py. A failed/missing source entry prevents publication;
no partial roster or replacement character is silently accepted.
"""
import json
import importlib
import zipfile
from collections import Counter
from pathlib import Path
from PIL import Image
from lego_assets import Game, GAMES
from lego_catalog import discover

DEST = Path('extensions/lego/src/main/resources/assets/vibe/lego')
REVISION = 11


def verify_bundle(character):
    path = DEST / 'models' / (character['id'] + '.zip')
    with zipfile.ZipFile(path) as bundle:
        if bundle.testzip() is not None:
            raise ValueError('Corrupt model bundle: ' + str(path))
        doc = json.loads(bundle.read('model.gltf'))
        if doc['asset']['version'] != '2.0' or not doc['meshes'][0]['primitives']:
            raise ValueError('Empty or unsupported character: ' + str(path))
        for buffer in doc['buffers']:
            if len(bundle.read(buffer['uri'])) != buffer['byteLength']:
                raise ValueError('Incomplete geometry: ' + str(path))
        for image in doc.get('images', []):
            import io
            with Image.open(io.BytesIO(bundle.read(image['uri']))) as decoded:
                decoded.verify()
        for mesh in doc['meshes']:
            for primitive in mesh['primitives']:
                positions = doc['accessors'][primitive['attributes']['POSITION']]
                indices = doc['accessors'][primitive['indices']]
                if positions['count'] < 3 or indices['count'] < 3 or indices['count'] % 3:
                    raise ValueError('Invalid primitive: ' + str(path))
    with Image.open(DEST / 'portraits' / (character['id'] + '.png')) as portrait:
        portrait.verify()


def main():
    report = json.loads(Path('build/lego-catalog/import.json').read_text(encoding='utf8'))
    if report['failed']:
        raise ValueError('Import still has %d failures; catalog not published' % len(report['failed']))
    imported = {r['id']: r for r in report['characters']}
    if len(imported) != len(report['characters']):
        raise ValueError('Duplicate character IDs')
    legacy = json.loads(Path('tools/lego-legacy-roster.json').read_text(encoding='utf8'))
    seed = {r['id']: r for r in legacy}
    by_source = {(r['game'], r['character'].lower()): r for r in imported.values()}
    declared, excluded, games = [], [], []
    for key in GAMES:
        game = Game(key)
        rows, rejected, _, _ = discover(game)
        labels = importlib.import_module('import-lego-roster').localized(game)
        unresolved = [r['character'] for r in rejected if r['reason'] == 'No character definition in supplied archives']
        if unresolved:
            raise ValueError('Unresolved collection definitions: %s / %s' % (key, unresolved))
        for source in rows:
            row = by_source.get((key, source['character'].lower()))
            if row is None:
                raise ValueError('Missing character: %s / %s' % (key, source['character']))
            row['name'] = seed[row['id']]['name'] if row['id'] in seed else labels.get(row['character'].lower(), row['name'])
            revision = 11 if key == 'hobbit' else 10
            if key != 'tcs' and row['import'].get('revision') != revision:
                raise ValueError('Outdated bundle: ' + row['id'])
            declared.append(row)
        excluded.extend(dict(game=key, **r) for r in rejected)
        games.append(dict(id=key, name=GAMES[key][1], declared=len(rows), converted=len(rows)))
    if len(declared) != len(imported):
        raise ValueError('Stale/non-collection characters in import report')
    ordered = [imported[r['id']] for r in legacy]
    ordered.extend(r for r in declared if r['id'] not in seed)
    characters, provenance, warnings = [], [], []
    for row in ordered:
        verify_bundle(row)
        tier = seed[row['id']]['tier'] if row['id'] in seed else -1
        characters.append(dict(id=row['id'], name=row['name'], game=row['game'], tier=tier,
                               cost=0 if tier >= 0 else 6000))
        provenance.append(dict(id=row['id'], game=row['game'], character=row['character'],
                               collection=row['collection'], config=row['config'], **row['import']))
        if row['import'].get('warnings'):
            warnings.append(dict(id=row['id'], resources=row['import']['warnings']))
    catalog = dict(revision=REVISION, characters=characters)
    coverage = dict(revision=REVISION, declared=len(declared), converted=len(ordered), games=games,
                    characters=provenance, excluded=excluded, warnings=warnings, failed=[])
    for name, data in [('catalog.json', catalog), ('coverage.json', coverage)]:
        target = DEST / name
        temporary = target.with_suffix('.json.part')
        temporary.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding='utf8')
        temporary.replace(target)
    # Preserve obsolete generated entries outside packaged resources. This also
    # removes vehicle/customizer experiments from earlier partial imports.
    ids = set(imported)
    for kind, extension in [('models', '*.zip'), ('portraits', '*.png')]:
        for asset in (DEST / kind).glob(extension):
            if asset.stem in ids or asset.stem == 'question':
                continue
            saved = Path('build/lego-excluded-resources') / kind / asset.name
            saved.parent.mkdir(parents=True, exist_ok=True)
            asset.replace(saved)
    print('Published', len(characters), 'characters:', dict(Counter(r['game'] for r in characters)))
    print('Characters with explicitly recorded missing source cosmetics:', len(warnings))


if __name__ == '__main__':
    main()
