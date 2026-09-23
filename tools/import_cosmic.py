"""Import the Cosmic family and provenance from a decoded Meizu ROM APK."""
import argparse
import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path, help='Decoded official AliveWallPaper APK directory')
    args = parser.parse_args()
    source = args.source.resolve()
    manifest = ET.parse(source / 'AndroidManifest.xml').getroot()
    if manifest.get('package') != 'com.flyme.alivewallpaper':
        raise ValueError('Wrong official package')
    dest = ROOT / 'app/assets/cosmic'
    dest.mkdir(parents=True, exist_ok=True)
    strings = {}
    for folder in ['values', 'values-zh-rCN']:
        for xml in (source / 'res' / folder).glob('*.xml'):
            for item in ET.parse(xml).getroot().findall('string'):
                strings[item.get('name')] = ''.join(item.itertext())
    def label(value):
        return strings.get(value.removeprefix('@string/'), value)
    inventory, catalogue, hashes = [], [], {}
    def copy(relative, target):
        data = (source / relative).read_bytes()
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        hashes[relative] = hashlib.sha256(data).hexdigest()
    for service in manifest.find('application').findall('service'):
        if service.get(A+'permission') != 'android.permission.BIND_WALLPAPER':
            continue
        name = service.get(A+'name')
        metadata = {m.get(A+'name'): m.get(A+'value', m.get(A+'resource')) for m in service.findall('meta-data')}
        entry = dict(service=name, name=label(service.get(A+'label', name)), metadata=metadata)
        inventory.append(entry)
        match = re.fullmatch(r'CosmicWallpaper(?:V(\d+))?', name.rsplit('.', 1)[-1])
        if not match:
            continue
        variant = int(match[1] or 1)
        for suffix in ['', '_dark']:
            path = f'assets/config/cosmic/cosmic_v{variant}{suffix}.json'
            if variant <= 4:
                path = f'assets/shader/cosmic/cosmic_v{variant}{suffix}.json'
            config = json.loads((source / path).read_text())
            assert all(scene in config for scene in ('aod', 'keyguard', 'unlock'))
            copy(path, dest / f'v{variant}{suffix}.json')
        xml = source / 'res' / (metadata['android.service.wallpaper'].removeprefix('@')+'.xml')
        thumb = ET.parse(xml).getroot().get(A+'thumbnail').split('/')[-1]
        files = sorted((source / 'res').glob(f'drawable*/{thumb}.*'))
        if not files:
            raise ValueError(f'Missing thumbnail: {thumb}')
        thumb_dest = f'v{variant}{files[0].suffix}'
        copy(files[0].relative_to(source).as_posix(), dest / thumb_dest)
        catalogue.append(dict(id=variant, name=entry['name'], thumbnail=thumb_dest,
                              officialVisible=metadata.get('meizu.wallpaper.show') == 'true'))
    # The sphere shader calculates its own displaced normals. Preserve the
    # original OBJ triangles/positions; do not substitute a lower-poly sphere.
    vertices, indices = [], []
    mesh = source / 'assets/model/sphere.obj'
    for line in mesh.read_text().splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == 'v':
            vertices.extend(map(float, fields[1:4]))
        elif fields[0] == 'f':
            face = [int(part.split('/')[0])-1 for part in fields[1:]]
            for i in range(1, len(face)-1):
                indices.extend((face[0], face[i], face[i+1]))
    assert indices and max(indices) < 65536 and min(indices) >= 0
    data = struct.pack('<II', len(vertices)//3, len(indices))
    data += struct.pack(f'<{len(vertices)}f', *vertices) + struct.pack(f'<{len(indices)}H', *indices)
    (dest / 'sphere.bin').write_bytes(data)
    hashes['assets/model/sphere.obj'] = hashlib.sha256(mesh.read_bytes()).hexdigest()
    shaders = ['cosmic_vertex.glsl', 'cosmic_frag.glsl', 'gradient_background_vertex.glsl', 'gradient_background_frag.glsl']
    visited = set()
    def shader(path):
        if path in visited:
            return
        visited.add(path)
        copy('assets/'+path, ROOT / 'app/assets' / path)
        for dependency in re.findall(r'#include\s+<([^>]+)>', (source / 'assets' / path).read_text(encoding='utf-8')):
            shader(dependency)
    for name in shaders:
        shader('shader/cosmic/'+name)
    (dest / 'catalog.json').write_bytes((json.dumps(catalogue, ensure_ascii=False, indent=2)+'\n').encode('utf-8'))
    (dest / 'source.json').write_bytes((json.dumps(dict(source='魅族22 FlymeOS / AliveWallPaper.apk',
        package=manifest.get('package'), assets=hashes), ensure_ascii=False, indent=2)+'\n').encode('utf-8'))
    audit = ROOT / 'build/official-alive-inventory.json'
    audit.write_text(json.dumps(inventory, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(f'{len(inventory)} official wallpaper services; imported {len(catalogue)} Cosmic variants, {len(vertices)//3} vertices, {len(indices)//3} triangles')

if __name__ == '__main__':
    main()
