"""Import Flyme 9 Stereo V4/V5/V6 assets and the actual two-pass shaders.

Usage: python tools/import_stereo.py [decoded-apk-directory]
The originals select ai=true in com.meizu.a.a.d, hence zsstereo, not the
unused stereotexture renderer. No generated image or depth approximation.
"""
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'build/flyme9-decoded'
DEST = ROOT / 'app/assets/stereo'
DEST.mkdir(parents=True, exist_ok=True)
files = {}
for version in (4, 5, 6):
    for kind, extension in (('main', 'webp'), ('depth', 'png')):
        name = f'mz_stereo_{kind}_v{version}.{extension}'
        files[f'assets/stereo/{name}'] = name
    files[f'res/drawable-nodpi/mz_wallpaper_stereo_v{version}.png'] = f'preview_v{version}.png'
for name in ('vertex.glsl', 'vertex_split.glsl', 'fragment_mixformanual.glsl', 'fragment_blur.glsl'):
    files[f'assets/zsstereo/{name}'] = name
manifest = {'source': 'Flyme 9 MzWallpaper common 1.8.15', 'files': []}
for source, name in files.items():
    original = (SOURCE / source).read_bytes()
    data = original
    if name.endswith('.glsl'):
        # ES 1.00 strict compilers reject the C-style float suffix accepted by
        # Meizu's driver. This changes lexical spelling only, never constants.
        data = re.sub(rb'(?<=\d)f\b', b'', data)
    (DEST / name).write_bytes(data)
    manifest['files'].append({'source': source, 'file': name,
        'sourceSha256': hashlib.sha256(original).hexdigest(),
        'sha256': hashlib.sha256(data).hexdigest()})
(DEST / 'provenance.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f'Imported {len(files)} original Stereo assets to {DEST}')
