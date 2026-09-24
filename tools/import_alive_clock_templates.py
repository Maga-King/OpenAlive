"""Extract the actual Flyme ALIVE clocks, independently of ColorOS font presets."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import argparse
import ast
import hashlib
import json
import operator
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = Path('E:/MIO/AAAFLYME/system/system/app/SystemUIEditor/SystemUIEditor.apk')
TEMPLATES = ('hverticaltime', 'hhorizontaltime')
OPS = {ast.Add: operator.add, ast.Sub: operator.sub, ast.Mult: operator.mul,
       ast.Div: operator.truediv}

def number(text):
    def visit(node):
        if isinstance(node, ast.Constant) and type(node.value) in (int, float):
            return node.value
        if isinstance(node, ast.BinOp) and type(node.op) in OPS:
            return OPS[type(node.op)](visit(node.left), visit(node.right))
        raise ValueError(f'Unsupported geometry expression: {text}')
    return float(visit(ast.parse(text, mode='eval').body))

def extract(apk):
    output = ROOT/'app/assets/native-clock/templates/flyme'
    provenance = {'source': '魅族22 FlymeOS / SystemUIEditor.apk',
                  'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'templates': []}
    with ZipFile(apk) as source:
        for name in TEMPLATES:
            member = f'assets/aod/zkAod/hidden/{name}.zip'
            raw = source.read(member)
            destination = output/name
            destination.mkdir(parents=True, exist_ok=True)
            hashes = {}
            with ZipFile(BytesIO(raw)) as template:
                for entry in template.namelist():
                    # Exact originals, including the transparent cover, are retained.
                    if '/' in entry or '\\' in entry or entry in ('.', '..'):
                        raise ValueError(entry)
                    data = template.read(entry)
                    (destination/entry).write_bytes(data)
                    hashes[entry] = hashlib.sha256(data).hexdigest()
                xml = ET.fromstring(template.read('manifest.xml').decode('utf-8-sig', errors='replace'))
                config = ET.fromstring(template.read('aod.xml').decode('utf-8-sig', errors='replace'))
                texts = []
                for element in xml.iter('Text'):
                    a = element.attrib
                    hour = '#hour24/10%10,#hour24%10'
                    minute = '#minute/10%10,#minute%10'
                    fields = {hour: 'hour', minute: 'minute', hour+','+minute: 'time'}
                    if a['paras'] not in fields:
                        raise ValueError(a['paras'])
                    texts.append({'x': number(a['x']), 'y': number(a['y']), 'size': number(a['size']),
                                  'align': a.get('align', 'left'), 'alignV': a.get('alignV', 'top'),
                                  'font': a['typeface'], 'field': fields[a['paras']]})
                # These shipped templates only use a zero-offset Group.
                for group in xml.iter('Group'):
                    assert number(group.get('x', '0')) == number(group.get('y', '0')) == 0
                render = {'schema': 1, 'id': 'flyme.alive.'+name,
                          'width': int(config.findtext('screen_width')),
                          'height': int(config.findtext('screen_height')), 'texts': texts}
                (destination/'render.json').write_text(json.dumps(render, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
            provenance['templates'].append({'id': render['id'], 'member': member,
                                           'zip_sha256': hashlib.sha256(raw).hexdigest(), 'files': hashes})
    (output/'source.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    return provenance

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, default=DEFAULT)
    result = extract(parser.parse_args().apk)
    print('Imported original Flyme ALIVE clock templates:', len(result['templates']))
