"""Package original WallpaperSetting layouts, controls and their resource namespace."""
from pathlib import Path
import argparse, hashlib, json, subprocess, zipfile
from apk_layout import RESOURCE_ALIGNMENT, inspect

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    out = ROOT / 'app/assets/ui'
    out.mkdir(parents=True, exist_ok=True)
    temp = ROOT / 'build/settings-ui-unaligned.apk'
    hashes = {}
    with zipfile.ZipFile(args.apk) as src, zipfile.ZipFile(temp, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as dst:
        for item in src.infolist():
            name = item.filename
            if name in ('AndroidManifest.xml', 'resources.arsc') or name.startswith('res/') or (name.startswith('classes') and name.endswith('.dex')) or (name.startswith('assets/') and name.endswith(('.ttf', '.otf'))):
                data = src.read(name)
                dst.writestr(name, data, compress_type=zipfile.ZIP_STORED if name == 'resources.arsc' else zipfile.ZIP_DEFLATED)
                hashes[name] = hashlib.sha256(data).hexdigest()
    target = out / 'settings-ui.apk'
    sdk = Path.home() / 'AppData/Local/Android/Sdk'
    subprocess.run([str(sdk/'build-tools/35.0.0/zipalign.exe'), '-f', str(RESOURCE_ALIGNMENT), str(temp), str(target)], check=True)
    inspect(target.read_bytes())
    with zipfile.ZipFile(target) as src:
        assert all(hashlib.sha256(src.read(name)).hexdigest() == digest for name, digest in hashes.items())
    (out/'settings-ui.sha256').write_bytes(hashlib.sha256(target.read_bytes()).hexdigest().encode('ascii'))
    (out/'settings-ui-source.json').write_bytes((json.dumps(dict(source='魅族22 FlymeOS / WallpaperSetting.apk',
        sourceSha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(), entries=hashes), ensure_ascii=False, indent=2)+'\n').encode('utf-8'))
    print('Original entries:', len(hashes), 'bundle bytes:', target.stat().st_size)

if __name__ == '__main__':
    main()
