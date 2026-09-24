"""Keep original clock fonts and create ColorOS line-metric compatibility copies."""
from pathlib import Path
from zipfile import ZipFile
import argparse, hashlib, json
from io import BytesIO
from fontTools.ttLib import TTFont
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import RecordingPen

ROOT = Path(__file__).resolve().parents[1]
FONTS = {
    'flyme': ['assets/fonts/FlymeNumber-VF.ttf', 'assets/fonts/SixCaps-Regular.ttf',
              'assets/fonts/DMSerifDisplay-Regular.ttf'],
    'hyperos': ['assets/MiSansRCFVF.ttf', 'assets/MiSansRoundedFVF.ttf',
                'assets/MiSerifFVF.ttf', 'assets/fonts/MiClock-Light.otf'],
}

def digit_bounds(font):
    glyphs = font.getGlyphSet()
    pen = BoundsPen(glyphs)
    cmap = font.getBestCmap()
    for character in '0123456789':
        glyphs[cmap[ord(character)]].draw(pen)
    return pen.bounds

def adapt_metrics(data, reference):
    font = TTFont(BytesIO(data), recalcBBoxes=False, recalcTimestamp=False)
    source_bounds = digit_bounds(font)
    reference_bounds = digit_bounds(reference)
    em = font['head'].unitsPerEm
    ref_em = reference['head'].unitsPerEm
    ratio = em / ref_em
    # ColorOS's fixed-font margins assume these blank bands around the digits.
    # Preserve all glyph outlines, advances, variation axes and hinting.
    top = round(source_bounds[3] + (reference['head'].yMax-reference_bounds[3])*ratio)
    bottom = round(source_bounds[1] + (reference['head'].yMin-reference_bounds[1])*ratio)
    ascent = round(source_bounds[3] + (reference['hhea'].ascent-reference_bounds[3])*ratio)
    descent = round(source_bounds[1] + (reference['hhea'].descent-reference_bounds[1])*ratio)
    font['head'].yMax = max(top, font['head'].yMax)
    font['head'].yMin = min(bottom, font['head'].yMin)
    font['hhea'].ascent = ascent
    font['hhea'].descent = descent
    font['hhea'].lineGap = 0
    os2 = font['OS/2']
    os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = ascent, descent, 0
    os2.usWinAscent, os2.usWinDescent = font['head'].yMax, -font['head'].yMin
    if 'CFF ' in font:
        bbox = font['CFF '].cff.topDictIndex[0].FontBBox
        font['CFF '].cff.topDictIndex[0].FontBBox = [bbox[0], font['head'].yMin, bbox[2], font['head'].yMax]
    result = BytesIO()
    font.save(result)
    adapted = result.getvalue()
    verify_glyphs_unchanged(data, adapted)
    return adapted

def verify_glyphs_unchanged(original, adapted):
    before, after = [TTFont(BytesIO(data), recalcBBoxes=False) for data in (original, adapted)]
    assert before.getBestCmap() == after.getBestCmap(), 'Character mapping changed'
    assert before['hmtx'].metrics == after['hmtx'].metrics, 'Glyph advances changed'
    left, right = before.getGlyphSet(), after.getGlyphSet()
    assert set(left.keys()) == set(right.keys()), 'Glyph set changed'
    for name in left.keys():
        a, b = RecordingPen(), RecordingPen()
        left[name].draw(a); right[name].draw(b)
        assert a.value == b.value, 'Outline changed: '+name
    if 'gvar' in before:
        assert before['gvar'].variations.keys() == after['gvar'].variations.keys()
        for name, variations in before['gvar'].variations.items():
            other = after['gvar'].variations[name]
            assert len(variations) == len(other)
            for a, b in zip(variations, other):
                assert a.axes == b.axes and a.coordinates == b.coordinates, 'Variation changed: '+name
    if 'fvar' in before:
        assert before.getTableData('fvar') == after.getTableData('fvar'), 'Variable axes changed'

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--flyme', type=Path, required=True, help='Meizu 22 SystemUI.apk')
    parser.add_argument('--hyperos', type=Path, required=True, help='MIUIAod.apk')
    parser.add_argument('--coloros', type=Path, required=True, help='KeyguardPersonalityClocks.apk, metric reference only')
    args = parser.parse_args()
    destination = ROOT / 'app/assets/native-clock'
    records = []
    reference_path = 'assets/fonts/fixed/OPPODigit01.ttf'
    with ZipFile(args.coloros) as apk:
        reference_data = apk.read(reference_path)
    reference = TTFont(BytesIO(reference_data))
    for vendor, fonts in FONTS.items():
        package = getattr(args, vendor)
        package_hash = hashlib.sha256(package.read_bytes()).hexdigest()
        with ZipFile(package) as apk:
            for member in fonts:
                data = apk.read(member)
                relative = vendor + '/' + Path(member).name
                original = destination / 'original' / relative
                original.parent.mkdir(parents=True, exist_ok=True)
                original.write_bytes(data)
                adapted = adapt_metrics(data, reference)
                target = destination / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(adapted)
                records.append({'file': relative, 'source': vendor, 'apk_sha256': package_hash,
                                'member': member, 'original_file': 'original/'+relative,
                                'original_sha256': hashlib.sha256(data).hexdigest(),
                                'sha256': hashlib.sha256(adapted).hexdigest(),
                                'adaptation': 'hhea/OS2/head and CFF FontBBox line metrics; glyphs and advances unchanged',
                                'metric_reference': reference_path,
                                'metric_reference_sha256': hashlib.sha256(reference_data).hexdigest()})
    (destination / 'source.json').write_text(json.dumps(records, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print('Imported', len(records), 'original clock fonts and ColorOS metric copies')
