"""Import the Flyme 9 Cosmic wallpapers that are missing from our library.

The Flyme 9 public-component APK ships four Cosmic variants (园蓝 / 梅洛 / 金杏 /
青提) whose shader configs are structurally identical to the ones we already
render, but whose palettes are different resources. They must not overwrite the
existing variants that happen to share the same numbers, so they are registered
under fresh catalogue IDs.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/assets/cosmic'

# The four Flyme 9 entries, in the order they appear in the original APK, with
# the fresh IDs we assign them. Existing plain variants occupy 1,3,4,6..15.
ENTRIES = [
    dict(source=1, id=16, name='园蓝', thumbnail='mz_wallpaper_cosmic.webp'),
    dict(source=2, id=17, name='梅洛', thumbnail='mz_wallpaper_cosmic_v2.webp'),
    dict(source=3, id=18, name='金杏', thumbnail='mz_wallpaper_cosmic_v3.webp'),
    dict(source=4, id=19, name='青提', thumbnail='mz_wallpaper_cosmic_v4.webp'),
]
SCENES = ('aod', 'keyguard', 'unlock')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='Decoded Flyme 9 系统公共组件 APK directory')
    args = parser.parse_args()
    source = args.source.resolve()

    if source.name != 'flyme9-decoded' and not (source / 'assets/shader/cosmic').is_dir():
        raise SystemExit(f'Not a decoded Flyme 9 APK: {source}')

    catalogue = json.loads((DEST / 'catalog.json').read_text(encoding='utf-8'))
    by_id = {entry['id']: entry for entry in catalogue}
    hashes = {}

    for entry in ENTRIES:
        variant = entry['source']
        for suffix in ('', '_dark'):
            rel = f'assets/shader/cosmic/cosmic_v{variant}{suffix}.json'
            data = (source / rel).read_bytes()
            config = json.loads(data)
            missing = [scene for scene in SCENES if scene not in config]
            if missing:
                raise ValueError(f'{rel} is missing scenes: {missing}')
            target_id = entry['id'] if not suffix else entry['id']
            name = f'v{target_id}{suffix}.json'
            (DEST / name).write_bytes(data)
            hashes[rel] = hashlib.sha256(data).hexdigest()

        # Thumbnails are WebP in the Flyme 9 package; store them as PNG so the
        # existing BitmapFactory path needs no new format handling.
        thumb_source = source / 'res/drawable-nodpi' / entry['thumbnail']
        image = Image.open(thumb_source).convert('RGB')
        thumb_name = f'v{entry["id"]}.png'
        image.save(DEST / thumb_name, 'PNG', optimize=True)
        hashes[f'res/drawable-nodpi/{entry["thumbnail"]}'] = hashlib.sha256(
            thumb_source.read_bytes()).hexdigest()

        if entry['id'] in by_id:
            raise ValueError(f'Refusing to overwrite existing variant {entry["id"]}')
        catalogue.append(dict(id=entry['id'], name=entry['name'], thumbnail=thumb_name))
        print(f'{entry["name"]}: source v{variant} -> catalogue id {entry["id"]}')

    (DEST / 'catalog.json').write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (DEST / 'flyme9-source.json').write_text(json.dumps(dict(
        source='Flyme 9 系统公共组件 1.8.15 / com.meizu.flyme.sdkstage',
        note='Cosmic variants 园蓝/梅洛/金杏/青提, registered as fresh catalogue IDs',
        assets=hashes), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Imported {len(ENTRIES)} Cosmic variants; catalogue now {len(catalogue)} entries')


if __name__ == '__main__':
    main()
