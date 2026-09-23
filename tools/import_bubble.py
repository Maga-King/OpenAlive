"""Extract Bubble's numeric presets and original shaders from the official APK decode.

JADX is used only to read literal configuration assignments, never to execute code.
"""
from pathlib import Path
import argparse, hashlib, json, re, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'

def presets(file):
    root={}; aliases={'this':root}
    for line in file.read_text(encoding='utf8').splitlines():
        declaration=re.fullmatch(r'\s*(?:b|g|w1\.b|x1\.j|e0\.d) (\w+) = (\w+)\.(\w+);',line)
        literal=re.fullmatch(r'\s*(\w+)\.(\w+) = (-?[\d.]+(?:E[+-]?\d+)?)[fF];',line)
        if declaration:
            name,parent,key=declaration.groups();aliases[name]=aliases[parent].setdefault(key,{})
        elif literal:
            name,key,value=literal.groups();aliases[name][key]=float(value)
        elif '=' in line:raise ValueError('Unrecognized preset expression: '+line)
    def vector(obj,key,fields,default):return [obj.get(key,{}).get(f,d) for f,d in zip(fields,default)]
    def color(obj,key):return vector(obj,key,['f3454a','f3455b','f3456c','f3457d'],[0,0,0,0])
    def point(obj,key):return vector(obj,key,['f3556a','f3557e','f3558f'],[0,0,0])
    states=[]
    for scene in range(3):
        spheres=[]
        for layer in range(3):
            key=['f1404a','f1405b','f1406c','f1407d','f1408e','f1409f','f1410g','f1411h','f1412i'][layer*3+scene]
            obj=root[key]
            data=point(obj,'f1378a')+[obj.get('f1379b',0),obj.get('f1381d',1)]+color(obj,'f1380c')
            for c,p,s,r,default in [('f1382e','f1383f','f1384g','f1385h',1500),('f1386i','f1387j','f1388k','f1389l',1000),('f1390m','f1391n','f1392o','f1393p',1000)]:
                data+=color(obj,c)+point(obj,p)+[obj.get(s,1),obj.get(r,default)]
            spheres.append(data)
        bg=root[['f1416m','f1417n','f1418o'][scene]]
        fields=['f1527b','f1528c','f1529d','f1530e','f1531f','f1532g','f1533h','f1534i','f1535j','f1536k','f1537l','f1538m','f1539n']
        states.append(dict(spheres=spheres,background=sum([color(bg,f) for f in fields],[])))
    return states

def main():
    p=argparse.ArgumentParser();p.add_argument('decoded',type=Path);p.add_argument('jadx',type=Path);args=p.parse_args()
    dest=ROOT/'app/assets';out=dest/'bubble';out.mkdir(exist_ok=True)
    hashes={}
    labels={}
    for directory in ['values','values-zh-rCN']:
        for file in (args.decoded/'res'/directory).glob('*.xml'):
            for item in ET.parse(file).getroot().findall('string'):labels[item.get('name')]=''.join(item.itertext())
    manifest=ET.parse(args.decoded/'AndroidManifest.xml').getroot()
    catalog=json.loads((dest/'cosmic/catalog.json').read_text(encoding='utf8'))
    catalog=[r for r in catalog if not 201<=r['id']<=205]
    for service in manifest.find('application').findall('service'):
        match=re.fullmatch(r'.*\.BubbleWallpaper(?:V(\d+))?',service.get(A+'name',''))
        if not match:continue
        variant=int(match[1] or 1);key=200+variant
        # Keep the selected named palette in both system themes. Mapping every
        # night preset to j makes all five catalog entries look like black coffee.
        for suffix,letter in [('',chr(ord('i')+variant-1)),('_dark',chr(ord('i')+variant-1))]:
            file=args.jadx/'d0'/f'{letter}.java'
            (out/f'v{key}{suffix}.json').write_text(json.dumps(presets(file),separators=(',',':'))+'\n',encoding='utf8',newline='\n')
            hashes[f'configuration/{letter}']=hashlib.sha256(file.read_bytes()).hexdigest()
        meta=next(m for m in service.findall('meta-data') if m.get(A+'name')=='android.service.wallpaper')
        xml=args.decoded/'res'/(meta.get(A+'resource').removeprefix('@')+'.xml')
        thumbnail=ET.parse(xml).getroot().get(A+'thumbnail').split('/')[-1]
        file=next((args.decoded/'res').glob(f'drawable*/{thumbnail}.*'))
        (dest/'cosmic'/f'v{key}{file.suffix}').write_bytes(file.read_bytes())
        hashes[file.relative_to(args.decoded).as_posix()]=hashlib.sha256(file.read_bytes()).hexdigest()
        catalog.append(dict(id=key,name=labels[service.get(A+'label').removeprefix('@string/')],thumbnail=f'v{key}{file.suffix}',family='bubble'))
    for name in ['shader/bubble/bubble_vertex.glsl','shader/bubble/bubble_frag.glsl','soundviz/disk.vert','soundviz/disk.frag']:
        data=(args.decoded/'assets'/name).read_bytes();target=dest/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        hashes['assets/'+name]=hashlib.sha256(data).hexdigest()
    (out/'source.json').write_text(json.dumps(dict(source='魅族22 FlymeOS / AliveWallPaper.apk',assets=hashes),ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    (dest/'cosmic/catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    print('Imported five Bubble presets and original shaders')
if __name__=='__main__':main()
