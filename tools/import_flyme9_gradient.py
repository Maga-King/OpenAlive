"""Extract the Flyme 9 Gradient wallpapers into our JSON palette format.

The original renderer (gradient/l.java) binds 16 colours and 6 animated
polynomial splines. The colours live in the per-variant config classes
(gradient/i.java = 大漠, j.java = 极光, k.java = 远海); the splines are the same
for all three and are constants in the renderer.

Field mapping inside the renderer, colour slot name -> config field:
  a_inner <- f4074a   b_inner <- i      a_cp1 <- f4075b   b_cp1 <- j
  a_cp2   <- f4076c   b_cp2   <- k      a_cp3 <- f4077d   b_cp3 <- l
  a_cp4   <- f4078e   b_cp4   <- m      a_cp5 <- f        b_cp5 <- n
  a_cp6   <- g        b_cp6   <- o      a_outer <- h      b_outer <- p
"""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/assets/gradient'

VARIANTS = [
    ('i', 'damos', '大漠'),
    ('j', 'jiguang', '极光'),
    ('k', 'yuanhai', '远海'),
]

# Renderer colour slot -> config field name, in binding order.
SLOTS = [
    ('a_inner', 'f4074a'), ('b_inner', 'i'),
    ('a_cp1', 'f4075b'), ('b_cp1', 'j'),
    ('a_cp2', 'f4076c'), ('b_cp2', 'k'),
    ('a_cp3', 'f4077d'), ('b_cp3', 'l'),
    ('a_cp4', 'f4078e'), ('b_cp4', 'm'),
    ('a_cp5', 'f'), ('b_cp5', 'n'),
    ('a_cp6', 'g'), ('b_cp6', 'o'),
    ('a_outer', 'h'), ('b_outer', 'p'),
]

# The six splines, with the (x, y) control points the renderer constructs.
# x is fixed at 0/270/540/810/1080; y differs per spline.
SPLINES = {
    'spline1': dict(kind='b', y=[275, 367, 432, 313, 169]),
    'spline2': dict(kind='c', y=[522, 529, 682, 828, 857]),
    'spline3': dict(kind='d', y=[795, 840, 970, 1079, 1090]),
    'spline4': dict(kind='e', y=[827, 884, 1103, 1410, 1459]),
    'spline5': dict(kind='f', y=[858, 1078, 1589, 1866, 1918]),
    'spline6': dict(kind='g', y=[2023, 1931, 2048, 2143, 2091]),
}
SPLINE_X = [0, 270, 540, 810, 1080]

FIELD = re.compile(r'this\.(?:\w+)\.([JKLM])\s*=\s*([0-9.]+)f;')


def parse_colors(source):
    """/Read the 16 colours out of one decompiled config class."""
    text = source.read_text(encoding='utf-8')
    # Each -field is assigned in blocks; track the current receiver field name.
    block = re.compile(r'this\.(\S+?)\.([JKLM]) = ([0-9.]+)f;')
    by_field = {}
    for receiver, channel, value in block.findall(text):
        # The decompiler widens `this.f` to `this.<field>`, so the receiver is
        # exactly the field name we want.
        by_field.setdefault(receiver, {})[channel] = float(value)
    return by_field


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path,
                        help='Directory with the decompiled gradient/i.java, j.java, k.java')
    parser.add_argument('--apk', type=Path, required=True, help='Original sdkstage 1.8.15 APK, for shaders and thumbnails')
    args = parser.parse_args()
    source = args.source.resolve()
    DEST.mkdir(parents=True, exist_ok=True)

    catalogue = []
    for index, (cls, slug, name) in enumerate(VARIANTS, start=1):
        path = source / f'{cls}.java'
        if not path.is_file():
            raise SystemExit(f'Missing decompiled config: {path}')
        fields = parse_colors(path)
        palette = {}
        for slot, field in SLOTS:
            if field not in fields:
                raise ValueError(f'{path.name}: no assignments for field {field!r}')
            channels = fields[field]
            missing = [c for c in 'JKLM' if c not in channels]
            if missing:
                raise ValueError(f'{path.name}: field {field!r} missing {missing}')
            palette[slot] = [channels[c] for c in 'JKLM']

        document = dict(name=name, slug=slug, colors=palette,
                        splines={key: dict(kind=value['kind'],
                                           x=SPLINE_X, y=value['y'])
                                 for key, value in SPLINES.items()})
        target = DEST / f'{slug}.json'
        target.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n',
                          encoding='utf-8')
        catalogue.append(dict(id=300 + index, slug=slug, name=name,
                              palette=f'{slug}.json'))
        print(f'{name}: {len(palette)} colours -> {target.name}')

    (DEST / 'catalog.json').write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    provenance = {'source': 'Flyme 9 sdkstage 1.8.15',
                  'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                  'files': {}, 'palette_sources': {}}
    with zipfile.ZipFile(args.apk) as apk:
        assets = {f'assets/shader/gradient/gradient.{ext}': f'app/assets/shader/gradient/gradient.{ext}'
                  for ext in ['vert', 'frag']}
        assets.update({f'res/drawable-nodpi-v4/mz_wallpaper_gradient_v{i}.png':
                       f'app/assets/cosmic/gradient_{slug}.png'
                       for i, (_, slug, _) in enumerate(VARIANTS, 1)})
        for original, target in assets.items():
            data = apk.read(original)
            path = ROOT / target
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            provenance['files'][target] = {'original': original, 'sha256': hashlib.sha256(data).hexdigest()}
    for cls, slug, _ in VARIANTS:
        provenance['palette_sources'][slug] = {'class': f'com.meizu.flyme.sdkstage.wallpaper.gradient.{cls}',
            'decompiled_sha256': hashlib.sha256((source / f'{cls}.java').read_bytes()).hexdigest(),
            'palette_sha256': hashlib.sha256((DEST / f'{slug}.json').read_bytes()).hexdigest()}
    (DEST / 'source.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(f'Wrote {len(catalogue)} gradient variants')


if __name__ == '__main__':
    main()
