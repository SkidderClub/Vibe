"""Import the complete selectable roster from locally supplied games.

Usage: python tools/import-lego-roster.py [tcs cw hp1 hp2 hobbit dc]
Conversion is resumable. Failures remain explicit in build/lego-catalog/import.json.
Only successfully converted characters are published; no placeholder models.
"""
import csv
import importlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path
from PIL import Image
from lego_assets import Game, GAMES
from lego_catalog import discover, normalize
from lego_export import Exporter
from lego_material import dds_images

Nu = importlib.import_module('convert-lego-models').Nu
DEST = Path('extensions/lego/src/main/resources/assets/vibe/lego')


def tcs_config(game, path, configs, seen=()):
    if path in seen: raise ValueError('Recursive TCS costume')
    text = game.text(path)
    base = re.search(r'\btxt_file\s*=\s*"([^"]+)"', text, re.I)
    if base:
        parent = normalize(str(Path(path).parent / (base[1] + '.txt')))
        if parent not in game.index: parent = configs[base[1].lower()]['.txt']
        return tcs_config(game, parent, configs, seen + (path,)) + '\n' + text
    return text


def tcs_convert(game, row, configs, out):
    source = row['config']['.txt']; text = tcs_config(game, source, configs)
    model = source[:-4]
    matches = re.findall(r'\buse_model\s*=\s*"?([A-Za-z0-9_\\/]+)', text, re.I)
    if matches:
        model = normalize(str(Path(source).parent / matches[-1]))
        if model + '_pc.ghg' not in game.index: model = configs[matches[-1].lower()]['.txt'][:-4]
    while model + '_pc.ghg' not in game.index:
        own = game.text(model + '.txt')
        base = re.search(r'\btxt_file\s*=\s*"([^"]+)"', own, re.I)
        if not base: raise ValueError('No authored TCS model for ' + source)
        model = normalize(str(Path(model).parent / base[1]))
    match = list(re.finditer(r'layers_high\s*=\s*([0-9, ]+)', text, re.I))
    layers = [int(v) for v in match[-1][1].split(',')] if match else [0, 2, 5]
    Nu(game.get(model + '_pc.ghg')).convert(out, layers)
    icons = re.findall(r'\bicon\s*=\s*"([^"]+)"', text, re.I)
    if icons:
        icon = 'stuff/icons/' + icons[-1].lower() + '_pc.gsc'
        if icon in game.index: Nu(game.get(icon)).images()[0].save(DEST / 'portraits' / (row['id'] + '.png'))
    return dict(model=model + '_pc.ghg', layers=layers)


def localized(game):
    result = {}
    for p in game.index:
        if p.endswith('/text/text.csv'):
            content = game.get(p).read_bytes().decode('utf8', errors='replace')
            for row in csv.DictReader(io.StringIO(content)):
                label = row.get('LABEL', row.get('\ufeffLABEL', ''))
                name = row.get('ENGLISH', '')
                if label and name: result[label.lower()] = name
    if 'stuff/text/english.txt' in game.index:
        for number, name in re.findall(r'^\s*(\d+)\s+"([^"\n]+)"', game.text('stuff/text/english.txt'), re.M): result[number] = name
    return result


def main(keys):
    report_path = Path('build/lego-catalog/import.json')
    report = json.loads(report_path.read_text(encoding='utf8')) if report_path.exists() else dict(characters=[], failed=[], excluded=[], games=[])
    previous = {r['id']: r for r in report['characters']}
    legacy = json.loads((DEST / 'provenance.json').read_text())['characters']
    old_rows = [(r['id'], r['name'], r['tier']) for r in json.loads(Path('tools/lego-legacy-roster.json').read_text())]
    old = {normalize('chars/' + r['model']).replace('chars/chars/', 'chars/'): r['id'] for r in legacy}
    old_meta = {id: (name, int(tier)) for id, name, tier in old_rows}
    for key in keys:
        revision = 11 if key == 'hobbit' else 10
        game = Game(key); rows, excluded, hgos, configs = discover(game)
        declared = {r['character'].lower() for r in rows}
        previous = {id: r for id, r in previous.items() if r['game'] != key or r['character'].lower() in declared}
        names = localized(game)
        exporter = Exporter(game, hgos, configs)
        report['excluded'] = [e for e in report['excluded'] if e['game'] != key] + [dict(game=key, **e) for e in excluded]
        report['failed'] = [r for r in report['failed'] if r['game'] != key]
        for number, row in enumerate(rows):
            source = row['config'].get('.txt', '')
            if key == 'tcs':
                model = source[:-4] + '_pc.ghg'
                if model in old:
                    row['id'] = old[model]
            row['tier'] = old_meta.get(row['id'], ('', -1))[1]
            if row['id'] in old_meta: row['name'] = old_meta[row['id']][0]
            if source:
                text = game.text(source)
                label = re.search(r'^\s*(?:name_id|name)\s*=?\s*(?:"([^"]+)"|([A-Za-z0-9_]+))', text, re.I | re.M)
                if label:
                    name = names.get((label[1] or label[2]).lower())
                    if name: row['name'] = name
            if row['id'] not in old_meta and row['character'].lower() in names:
                row['name'] = names[row['character'].lower()]
            out = Path('build/lego-expanded') / row['id']
            bundle = DEST / 'models' / (row['id'] + '.zip')
            try:
                converted = False
                if row['id'] in old_meta and bundle.exists():
                    info = dict(model=source[:-4] + '_pc.ghg', legacy=True)
                elif row['id'] in previous and bundle.exists() and (key == 'tcs' or previous[row['id']]['import'].get('revision') == revision):
                    info = previous[row['id']]['import']
                else:
                    info = tcs_convert(game, row, configs, out) if key == 'tcs' else exporter.convert(row, out)
                    converted = True
                    info['revision'] = revision
                    temporary = bundle.with_name(bundle.name + '.part')
                    gltf = json.loads((out / 'model.gltf').read_text())
                    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
                        for name in ['model.gltf', 'model.bin'] + [image['uri'] for image in gltf.get('images', [])]: archive.write(out / name, name)
                    temporary.replace(bundle)
                portrait = DEST / 'portraits' / (row['id'] + '.png')
                if not portrait.exists() or key != 'tcs':
                    # Prefer an original standalone character icon when available.
                    icon_names = [row['character'].lower()]
                    if source:
                        icon_names += [s.lower() for s in re.findall(r'^\s*icon\s*=?\s*"([^"]+)"', game.text(source), re.I | re.M)]
                    icon = next((p for p in game.index if any(p.endswith('/' + name + s) for name in icon_names for s in ('_nxg.tex', '_dx11.tex')) and ('/icons/' in p or p.startswith('icons/'))), None)
                    if icon:
                        images = dds_images(game.get(icon).read_bytes())
                        if images: images[0].save(portrait)
                    if not icon and (converted or not portrait.exists()):
                        from lego_portrait import render
                        render(bundle).save(portrait)
                row['import'] = info
                previous[row['id']] = row
                print('%s %s/%s %s OK' % (key, number + 1, len(rows), row['id']), flush=True)
            except Exception as error:
                report['failed'].append(dict(game=key, character=row['character'], id=row['id'], error=str(error)))
                print('%s %s/%s %s FAILED %s' % (key, number + 1, len(rows), row['id'], error), flush=True)
            report['characters'] = list(previous.values())
            report_path.write_text(json.dumps(report, indent=2), encoding='utf8')
        report['games'] = [g for g in report['games'] if g['id'] != key] + [dict(id=key, name=game.name, declared=len(rows))]
        report_path.write_text(json.dumps(report, indent=2), encoding='utf8')
    print('Imported', len(report['characters']), 'Failed', len(report['failed']), flush=True)


if __name__ == '__main__':
    main(sys.argv[1:] or list(GAMES))
