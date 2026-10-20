"""Bundle locally extracted TCS assets used by the LEGO modules.

Run import-lego-assets.py first, then this script. All models use their declared
high-detail layers; portraits, HUD sheets and audio come from the same local game.
"""
import importlib
import json
import re
import shutil
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw

Nu = importlib.import_module('convert-lego-models').Nu
root = Path('build/lego-extracted')
out = Path('extensions/lego/src/main/resources/assets/vibe/lego')
for folder in ('models', 'portraits', 'sounds', 'textures'):
    (out / folder).mkdir(parents=True, exist_ok=True)

roster = [
    ('obiwan', 'OBIWANKENOBI/OBIWANKENOBI', 'OBIWAN_PADAWAN'),
    ('quigon', 'QUIGONJINN/QUIGONJINN', 'QUIGON'),
    ('jarjar', 'JARJARBINKS/JARJARBINKS', 'JARJAR'),
    ('amidala', 'AMIDALA/AMIDALA', 'AMIDALA'),
    ('panaka', 'CAPTAINPANAKA/CAPTAINPANAKA', 'PANAKA'),
    ('anakinboy', 'ANAKIN_BOY/ANAKIN_BOY', 'ANAKIN_BOY'),
    ('maul', 'DARTHMAUL/DARTHMAUL', 'DARTH_MAUL'),
    ('anakin', 'ANAKIN/ANAKIN_PADAWAN', 'ANAKIN_PADAWAN'),
    ('padme', 'PADME/PADME', 'PADME'),
    ('clone', 'CLONE/CLONE', 'CLONE_TROOPER'),
    ('jango', 'JANGOFETT/JANGOFETT', 'JANGO_FETT'),
    ('dooku', 'COUNTDOOKU/COUNTDOOKU', 'COUNT_DOOKU'),
    ('obiwan3', 'OBIWANKENOBI/OBIWANKENOBI_EP3', 'OBIWAN_E3'),
    ('mace', 'MACEWINDU/MACEWINDU', 'MACE_WINDU'),
    ('grievous', 'GRIEVOUS/GRIEVOUS', 'GRIEVOUS'),
    ('anakin3', 'ANAKIN/ANAKIN_JEDI', 'ANAKIN_JEDI'),
    ('yoda', 'YODA/YODA', 'YODA'),
    ('luke', 'LUKESKYWALKER/LUKESKYWALKER_TATOOINE', 'LUKE_TATOOINE'),
    ('leia', 'PRINCESSLEIA/PRINCESSLEIA', 'PRINCESS_LEIA'),
    ('han', 'HANSOLO/HANSOLO', 'HAN_SOLO'),
    ('chewbacca', 'CHEWBACCA/CHEWBACCA', 'CHEWBACCA'),
    ('ben', 'BENKENOBI/BENKENOBI', 'OBIWAN'),
    ('hothhan', 'HANSOLO/HANSOLO_HOTH', 'HAN_SOLO_HOTH'),
    ('lando', 'LANDOCALRISSIAN/LANDOCALRISSIAN', 'LANDO_CALRISSIAN'),
    ('boba', 'BOBAFETT/BOBAFETT', 'BOBA_FETT'),
    ('bespinluke', 'LUKESKYWALKER_JEDI/LUKESKYWALKER_BESPIN', 'LUKE_BESPIN'),
    ('vader', 'DARTHVADER/DARTHVADER', 'DARTH_VADER'),
    ('wicket', 'EWOK/WICKET', 'WICKET'),
    ('ackbar', 'ADMIRALACKBAR/ADMIRALACKBAR', 'ADMIRAL_ACKBAR'),
    ('endorleia', 'PRINCESSLEIA/PRINCESSLEIA_ENDOR', 'LEIA_ENDOR'),
    ('lukesjedi', 'LUKESKYWALKER_JEDI/LUKESKYWALKER_JEDI', 'LUKE_JEDI'),
    ('emperor', 'THEEMPEROR/THEEMPEROR', 'THE_EMPEROR'),
]
provenance = []
for id, model, icon in roster:
    src = root / 'CHARS' / (model + '_PC.GHG')
    config = root / 'CHARS' / (model + '.TXT')
    if not config.exists():
        config = next(src.parent.glob('*.TXT'))
    text = config.read_text(errors='replace')
    match = re.search(r'layers_high\s*=\s*([\d, ]+)', text)
    layers = [int(v.strip()) for v in match[1].split(',')] if match else [0, 2, 5]
    dest = Path('build/lego-converted') / id
    Nu(src).convert(dest, layers)
    with zipfile.ZipFile(out / 'models' / (id + '.zip'), 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for file in sorted(dest.iterdir()):
            archive.write(file, file.name)
    portrait = root / 'STUFF/ICONS' / (icon + '_ICON_PC.GSC')
    Nu(portrait).images()[0].save(out / 'portraits' / (id + '.png'))
    provenance.append({'id': id, 'model': str(src.relative_to(root)), 'layers': layers, 'portrait': str(portrait.relative_to(root))})

Nu(root / 'STUFF/ICONS/QUESTION_ICON_PC.GSC').images()[0].save(out / 'portraits/question.png')
sheet = Image.new('RGB', (960, 1080), '#202430')
draw = ImageDraw.Draw(sheet)
i = 0
for src in ('THINGS_PC.GSC', 'PC_BITS_PC.GSC', 'STARFIELD_PC.GSC'):
    for j, image in enumerate(Nu(root / 'STUFF' / src).images()):
        name = Path(src).stem.lower() + str(j)
        image.save(out / 'textures' / (name + '.png'))
        preview = image.copy(); preview.thumbnail((140, 140))
        x, y = (i % 6) * 160, (i // 6) * 180
        sheet.paste(preview, (x, y), preview)
        draw.text((x, y + 145), name, fill='white')
        i += 1
sheet.save('build/lego-converted/texture-contact.png')
sounds = {'silver': 'PICKUP/COIN1', 'gold': 'PICKUP/COIN2', 'blue': 'PICKUP/COINBLUE',
          'drop': 'PICKNEW/COINDROPS', 'burst': 'PICKNEW/COINDROPG', 'unlock': 'PICKNEW/VICTORY1',
          'quest': 'PICKNEW/HIDDENITEMREVEAL', 'select': 'UI/MENUSELECT',
          'locked': 'UI/MENUNOENTRY', 'move': 'UI/MENUMOVE', 'back': 'UI/MENUBACK'}
for name, source in sounds.items():
    shutil.copyfile(root / 'AUDIO/_SOUNDFX' / (source + '.WAV'), out / 'sounds' / (name + '.wav'))
(out / 'provenance.json').write_text(json.dumps({'game': 'LEGO Star Wars: The Complete Saga (user-supplied PC installation)',
                                               'characters': provenance, 'sounds': sounds}, indent=2))
print('Bundled', len(roster), 'original characters and portraits;', i, 'HUD texture sheets.')
