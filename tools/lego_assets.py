"""Case-insensitive, cached access to user-supplied LEGO game archives."""
import json
from pathlib import Path
from lego_archive import read, entries

GAMES = {
    'tcs': ('LEGO.Star.Wars.Complete.Saga', 'The Complete Saga'),
    'cw': ('LEGO Star Wars - The Clone Wars', 'The Clone Wars'),
    'hp1': ('LEGO.Harry.Potter.1-4', 'Harry Potter: Years 1–4'),
    'hp2': ('LEGO.Harry.Potter.5-7', 'Harry Potter: Years 5–7'),
    'hobbit': ('LEGO.The.Hobbit', 'The Hobbit'),
    'dc': ('LEGO.DC.Super.Villains.v1.0.0.15083.Incl.ALL.DLC', 'DC Super-Villains'),
}


class Game:
    def __init__(self, key):
        self.key = key
        self.name = GAMES[key][1]
        self.cache = Path('build/lego-sources') / key
        inventory = Path('build/lego-catalog') / GAMES[key][0] / 'all.json'
        if not inventory.exists():
            root = Path('context/lego') / GAMES[key][0]
            archives = sorted(root.rglob('*.DAT'), key=lambda p: str(p).lower())
            if not archives:
                raise FileNotFoundError('No supplied game archives under ' + str(root))
            rows = [dict(archive=str(path), entry=entry)
                    for path in archives for entry in entries(path)]
            inventory.parent.mkdir(parents=True, exist_ok=True)
            inventory.write_text(json.dumps(rows), encoding='utf8')
        rows = json.loads(inventory.read_text(encoding='utf8'))
        self.index = {r['entry'][0].lower(): r for r in rows}

    def get(self, name):
        name = name.replace('\\', '/').lstrip('/').lower()
        dest = self.cache / name
        row = self.index[name]
        if not dest.exists() or dest.stat().st_size != row['entry'][3]:
            with open(row['archive'], 'rb') as stream:
                data = read(stream, row['entry'])
            dest.parent.mkdir(parents=True, exist_ok=True)
            temporary = dest.with_name(dest.name + '.part')
            temporary.write_bytes(data)
            temporary.replace(dest)
        return dest

    def text(self, name):
        return self.get(name).read_text(encoding='utf8', errors='replace')

    def find(self, suffix):
        suffix = suffix.lower()
        return [p for p in self.index if p.endswith(suffix)]
