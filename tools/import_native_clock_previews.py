"""Import thumbnails only after the matching original-control rendering test passed."""
from pathlib import Path
import argparse, hashlib, json, re, shutil

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('device')
    parser.add_argument('--flyme-artwork', action='store_true')
    args = parser.parse_args()
    source = (ROOT/'app/java/org/aliveclean/NativeHyperOsStyles.java').read_text(encoding='utf8')
    presets = re.findall(r'new Style\("([^"]+)","([^"]+)",(\d+),(\d+),(\d+),(true|false),"([^"]+)"\)', source)
    evidence = ROOT/'build/native-clock-test'/f'{args.device}-wrapped.txt'
    result = evidence.read_text(encoding='utf8')
    assert len({preset[0] for preset in presets}) == len(presets)
    assert f'HYPEROS_WRAPPED_OK scenes={len(presets)*4}' in result
    dest = ROOT/'app/assets/native-clock/previews'
    dest.mkdir(parents=True, exist_ok=True)
    metadata = dest/'source.json'
    provenance = json.loads(metadata.read_text(encoding='utf8')) if metadata.exists() else {}
    provenance = {name: entry for name, entry in provenance.items() if not name.startswith('hyperos-')}
    for suffix, template, variant, font, weight, vertical, title in presets:
        style_id = 'org.aliveclean.clock.hyperos.'+suffix
        for scene in range(4):
            assert f'WRAPPED_OK {style_id} scene={scene} ' in result
        name = 'hyperos-'+suffix.replace('.', '-')+'.png'
        thumbnail = evidence.parent/f'{args.device}-{name}'
        data = thumbnail.read_bytes()
        assert data.startswith(b'\x89PNG\r\n\x1a\n')
        shutil.copyfile(thumbnail, dest/name)
        provenance[name] = dict(sha256=hashlib.sha256(data).hexdigest(),
            source='Unmodified HyperOS controls rendered by HyperOsWrappedInstrumentation',
            runtime_sha256=(ROOT/'app/assets/native-clock/runtime/hyperos/runtime.sha256').read_text().strip(),
            template=template, variant=int(variant), font=int(font), weight=int(weight),
            size=[540,900], validation_sha256=hashlib.sha256(evidence.read_bytes()).hexdigest())
    if args.flyme_artwork:
        proof = evidence.parent/f'{args.device}-artwork.txt'
        output = proof.read_text(encoding='utf8')
        assert 'FLYME_ARTWORK_OK checks=24' in output and 'ARTWORK_FACTORY_OK transitions=12 restore=true release=true' in output
        image = evidence.parent/f'{args.device}-flyme-perspective-0.png'
        shutil.copyfile(image, dest/'flyme-perspective.png')
        provenance['flyme-perspective.png'] = dict(
            sha256=hashlib.sha256(image.read_bytes()).hexdigest(),
            source='Unmodified Flyme PerspectiveDigitClockView and layout_distinctive_self',
            resource_sha256=(ROOT/'app/assets/ui/editor-ui.sha256').read_text().strip(),
            dex_sha256=hashlib.sha256((ROOT/'vendor/flyme/editor-ui.dex').read_bytes()).hexdigest(),
            size=[540,900], validation_sha256=hashlib.sha256(proof.read_bytes()).hexdigest())
    metadata.write_text(json.dumps(provenance, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(f'Imported {len(presets)} validated original previews')

if __name__ == '__main__':
    main()
