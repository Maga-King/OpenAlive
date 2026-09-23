"""Import declared Phoenix wallpapers from the same decoded Meizu 22 APK."""
from pathlib import Path
import argparse,hashlib,json,re,struct,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);source=parser.parse_args().source
    manifest=ET.parse(source/'AndroidManifest.xml').getroot()
    if manifest.get('package')!='com.flyme.alivewallpaper':raise ValueError('Wrong package')
    dest=ROOT/'app/assets';hashes={}
    def copy(origin,target):
        data=(source/origin).read_bytes();out=dest/target;out.parent.mkdir(parents=True,exist_ok=True)
        out.write_bytes(data);hashes[origin]=hashlib.sha256(data).hexdigest()
    labels={}
    for directory in ['values','values-zh-rCN']:
        for file in (source/'res'/directory).glob('*.xml'):
            for item in ET.parse(file).getroot().findall('string'):labels[item.get('name')]=''.join(item.itertext())
    catalog=json.loads((dest/'cosmic/catalog.json').read_text(encoding='utf8'))
    catalog=[row for row in catalog if not 101<=row['id']<=105]
    for service in manifest.find('application').findall('service'):
        match=re.fullmatch(r'.*\.CosmicPhoenixWallpaperV(\d+)',service.get(A+'name',''))
        if not match:continue
        variant=int(match[1]);key=100+variant
        for suffix in ['','_dark']:
            copy(f'assets/config/phoenix/cosmic_phoenix_v{variant}{suffix}.json',f'cosmic/v{key}{suffix}.json')
        meta=next(m for m in service.findall('meta-data') if m.get(A+'name')=='android.service.wallpaper')
        xml=source/'res'/(meta.get(A+'resource').removeprefix('@')+'.xml')
        thumbnail=ET.parse(xml).getroot().get(A+'thumbnail').split('/')[-1]
        file=next((source/'res').glob(f'drawable*/{thumbnail}.*'))
        copy(file.relative_to(source).as_posix(),f'cosmic/v{key}{file.suffix}')
        catalog.append(dict(id=key,name=labels[service.get(A+'label').removeprefix('@string/')],thumbnail=f'v{key}{file.suffix}',family='phoenix'))
    visited=set()
    def shader(path):
        if path in visited:return
        visited.add(path);copy('assets/'+path,path)
        for dep in re.findall(r'#include\s+<([^>]+)>',(source/'assets'/path).read_text(encoding='utf8')):shader(dep)
    for name in ['cosmicPhoenix_vertex.glsl','cosmicPhoenix_frag.glsl']:shader('shader/phoenix/'+name)
    copy('assets/shader/phoenix/Noise6.png','shader/phoenix/Noise6.png')
    mesh=source/'assets/model/new_9w_02.obj';positions=[];normals=[];uv=[];vertices=[];indices=[];unique={}
    for line in mesh.read_text().splitlines():
        f=line.split()
        if not f:continue
        if f[0]=='v':positions.append(tuple(map(float,f[1:4])))
        elif f[0]=='vn':normals.append(tuple(map(float,f[1:4])))
        elif f[0]=='vt':uv.append(tuple(map(float,f[1:3])))
        elif f[0]=='f':
            face=[tuple(int(x)-1 for x in v.split('/')) for v in f[1:]]
            for i in range(1,len(face)-1):
                for p,t,n in [face[0],face[i],face[i+1]]:
                    key=(p,t,n)
                    if key not in unique:unique[key]=len(vertices)//8;vertices.extend(positions[p]+normals[n]+uv[t])
                    indices.append(unique[key])
    if not vertices or len(indices)%3:raise ValueError('Invalid triangle mesh')
    (dest/'cosmic/phoenix.bin').write_bytes(struct.pack('<II',len(vertices)//8,len(indices))+struct.pack(f'<{len(vertices)}f',*vertices)+struct.pack(f'<{len(indices)}I',*indices))
    hashes['assets/model/new_9w_02.obj']=hashlib.sha256(mesh.read_bytes()).hexdigest()
    (dest/'cosmic/phoenix-source.json').write_text(json.dumps(dict(source='魅族22 FlymeOS / AliveWallPaper.apk',assets=hashes),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (dest/'cosmic/catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print('Phoenix variants:',len(catalog)-13,'mesh vertices:',len(vertices)//8)
if __name__=='__main__':main()
