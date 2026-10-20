"""Discover selectable characters from the supplied games, including DLC collections.

Vehicle entries and blank customizer slots are recorded separately, never silently
substituted for a character. IDs include the source game to preserve costumes.
"""
import json
import re
from pathlib import Path
from lego_assets import Game, GAMES


def normalize(value):
    return value.replace('\\', '/').lower().strip('/')


def nice(value):
    value = re.sub(r'([a-z])([A-Z])', r'\1 \2', value.replace('_', ' '))
    return ' '.join(w.capitalize() if w.isupper() else w for w in value.split())


def discover(game):
    hgos, configs = {}, {}
    if 'chars/hgopathnames.txt' in game.index:
        for line in game.text('chars/hgopathnames.txt').splitlines():
            parts = line.strip().split(None, 1)
            if len(parts) == 2 and parts[1].lower().endswith('.hgo'):
                path = normalize(parts[1][:-4])
                hgos[parts[0].lower()] = path if path.startswith('chars/') else 'chars/' + path
    for ext in ('.txt', '.cd'):
        for path in game.find(ext):
            if path.startswith('chars/') or '/chars/' in path:
                configs.setdefault(Path(path).stem.lower(), {})[ext] = path
    # CHARS.TXT is authoritative when several render parts have identical names.
    if 'chars/chars.txt' in game.index:
        for block in re.findall(r'char_start(.*?)char_end', game.text('chars/chars.txt'), re.I | re.S):
            directory = re.search(r'\bdir\s+"([^"]+)"', block, re.I)
            file = re.search(r'\bfile\s+"([^"]+)"', block, re.I)
            if directory and file:
                stem = normalize('chars/' + directory[1] + '/' + file[1])
                configs[file[1].lower()] = {ext: stem + ext for ext in ('.txt', '.cd') if stem + ext in game.index}
    rows, excluded, seen = [], [], set()
    collections = [p for p in game.index if re.search(r'(^|/)(?:dlc)?collection[^/]*\.txt$', p)]
    for source in sorted(collections, key=lambda p: (p != 'chars/collection.txt', p)):
        for line in game.text(source).splitlines():
            active = line.split('//', 1)[0].strip()
            m = re.match(r'collect\s+"([^"]+)"(.*)', active, re.I)
            if not m:
                continue
            original, options = m.groups()
            key = original.lower()
            if key in seen:
                continue
            seen.add(key)
            cfg = configs.get(key, {})
            model = hgos.get(key, '')
            reason = None
            if key in ('weirdo1', 'weirdo2') or key.startswith(('custom', 'playercustom', 'customchar')) or key.endswith('_custom'):
                reason = 'Blank or partial customizer template'
            elif (game.key == 'cw' and key == 'pirate_tank_gun') or key.startswith('mini_') or any(p in ('/' + model + '/') for p in ('/vehicles/', '/vehicle/', '/minikits/')) or any('/vehicles/' in ('/' + p) for p in cfg.values()) or 'vehicle' in options.lower():
                reason = 'Vehicle'
            elif 'do_not_show' in options.lower() and 'linkedtochar' not in options.lower():
                reason = 'Hidden customizer or non-playable entry'
            if reason:
                excluded.append(dict(source=source, character=original, reason=reason))
                continue
            if not cfg:
                excluded.append(dict(source=source, character=original, reason='No character definition in supplied archives'))
                continue
            if game.key in ('tcs', 'cw'):
                txt = game.text(cfg['.txt']) if '.txt' in cfg else ''
                if re.search(r'^\s*(?:vehicle|is_vehicle|flying_vehicle|freeplay_toggle_vehicle)\b', txt, re.I | re.M):
                    excluded.append(dict(source=source, character=original, reason='Vehicle definition'))
                    continue
            rows.append(dict(id=game.key + '_' + re.sub('[^a-z0-9_]', '_', key), game=game.key,
                             name=nice(original), character=original, collection=source,
                             config=cfg, model=model, options=options.strip()))
    return rows, excluded, hgos, configs


if __name__ == '__main__':
    result = dict(characters=[], excluded=[], games=[])
    for key in GAMES:
        rows, excluded, _, _ = discover(Game(key))
        result['characters'].extend(rows)
        result['excluded'].extend(dict(game=key, **e) for e in excluded)
        result['games'].append(dict(id=key, name=GAMES[key][1], characters=len(rows)))
        print(key, len(rows), 'characters;', len(excluded), 'excluded', flush=True)
    Path('build/lego-catalog/discovered.json').write_text(json.dumps(result, indent=2), encoding='utf8')
