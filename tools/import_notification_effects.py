"""Import unchanged notification presets from the official Flyme SystemUI APK."""
from pathlib import Path
import hashlib, json, sys, zipfile

source = Path(sys.argv[1])
target = Path(__file__).resolve().parents[1] / 'app/assets/notification'
target.mkdir(parents=True, exist_ok=True)
entries = []
with zipfile.ZipFile(source) as apk:
    for name in ('circle.svga', 'light.svga'):
        data = apk.read('assets/' + name)
        (target / name).write_bytes(data)
        entries.append(dict(file=name, source='assets/' + name,
                            sha256=hashlib.sha256(data).hexdigest(), bytes=len(data)))
(target / 'source.json').write_text(json.dumps(dict(
    source='Meizu 22 Flyme 12.6.0.0A SystemUI',
    apk_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
    controller='com.flyme.aod.AODNotiAnimController', files=entries),
    ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
