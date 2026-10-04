"""UI translations / 界面翻译.

Chinese text in app/java is the source. Translations live in app/i18n/strings.json,
one entry per Chinese text:

    "完成": {"en": "Done", "ru": "Готово"},

    python tools/i18n.py          check (default): report what needs attention
    python tools/i18n.py update   wrap new Chinese text in I18n.t(), add empty entries
                                  to strings.json and regenerate I18nTable.java
    python tools/i18n.py build    check, then refresh I18nTable.java if strings.json
                                  changed (run by tools/build_release.py)

I18nTable.java is generated from strings.json and kept in the repository, so any
script that compiles app/java directly still works. Do not edit it by hand.

A missing or empty translation shows the Chinese text (Russian falls back to English
first), so nothing breaks while a translation is pending. Only real errors fail the
build: an invalid strings.json, or a translation whose %s/%d placeholders differ from
the Chinese text (String.format would throw).

Not treated as UI text: lines containing "i18n:ignore", log messages (Log.x(...)),
annotations, and the files and literals in SKIP_FILES / SKIP_LITERALS below. Text
translated later, where it is shown, is marked with I18n.mark("...").
"""
from pathlib import Path
import json, re, sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/java'
TABLE = ROOT/'app/i18n/strings.json'
GENERATED = JAVA/'org/aliveclean/I18nTable.java'
LANGUAGES = ('en', 'ru')
# Names shown from JSON catalogs; translated with I18n.t() where they are read.
CATALOGS = [('app/assets/cosmic/catalog.json', 'name'), ('app/assets/xiaomi/catalog.json', 'title')]
# Chinese that must stay Chinese: the lunar calendar and Chinese-only date formats.
SKIP_FILES = {'AodCalendar.java'}
SKIP_LITERALS = {'M月d日', 'M月d日 E'}
CHINESE = re.compile(r'[\u3400-\u9fff\uff00-\uffef\u3000-\u303f]')
LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
WRAPPED = re.compile(r'I18n\.(t|mark)\(\s*$')
NOT_UI = re.compile(r'\bLog\.[a-z]+\(')
PLACEHOLDER = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]')
ESCAPES = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', "'": "'", '\\': '\\', 'b': '\b', 'f': '\f'}


def unescape(text):
    out, i = [], 0
    while i < len(text):
        if text[i] == '\\':
            if text[i+1] == 'u':
                out.append(chr(int(text[i+2:i+6], 16))); i += 6; continue
            out.append(ESCAPES[text[i+1]]); i += 2; continue
        out.append(text[i]); i += 1
    return ''.join(out)


def escape(text):
    return '"'+text.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\t', '\\t').replace('\r', '\\r')+'"'


def write(path, text):
    with open(path, 'w', encoding='utf8', newline='\n') as out:
        out.write(text)


def where(path, number=None):
    return path.relative_to(ROOT).as_posix()+('' if number is None else ':%d' % number)


def in_log_call(before):
    """True when the text ends inside the parentheses of a Log.x( call."""
    calls = list(NOT_UI.finditer(before))
    if not calls:
        return False
    depth, rest = 1, LITERAL.sub('""', before[calls[-1].end():])
    for char in rest:
        depth += {'(': 1, ')': -1}.get(char, 0)
        if depth == 0:
            return False
    return True


def ui_literals(path):
    """(line number, line, match) for Chinese string literals that may be UI text."""
    for number, line in enumerate(path.read_text(encoding='utf8').split('\n'), 1):
        code = line.lstrip()
        if code.startswith(('//', '*', '/*', '@')):
            continue
        for match in LITERAL.finditer(line):
            raw, before = match.group(1), line[:match.start()]
            # Switch labels must stay compile-time constants; they are matched, not shown.
            if CHINESE.search(raw) and raw not in SKIP_LITERALS and not in_log_call(before) and not re.search(r'\bcase\s*$', before):
                yield number, line, match


def scan():
    """Source texts (wrapped, marked or from catalogs) and unwrapped places (path, line, text)."""
    sources, unwrapped = {}, []
    for path in sorted(JAVA.rglob('*.java')):
        if path.name in SKIP_FILES or path == GENERATED:
            continue
        for number, line, match in ui_literals(path):
            if WRAPPED.search(line[:match.start()]):
                sources.setdefault(unescape(match.group(1)), where(path, number))
            elif 'i18n:ignore' not in line:
                unwrapped.append((path, number, match.group(1)))
    for name, key in CATALOGS:
        for item in json.loads((ROOT/name).read_text(encoding='utf8')):
            sources.setdefault(item[key], name)
    return sources, unwrapped


def load():
    """strings.json as an ordered dict, plus the errors found in it."""
    errors, duplicates = [], []
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                duplicates.append(key)
            result[key] = value
        return result
    try:
        table = json.loads(TABLE.read_text(encoding='utf8'), object_pairs_hook=pairs)
    except ValueError as error:
        return {}, ['%s is not valid JSON: %s' % (where(TABLE), error)]
    errors += ['%s appears more than once' % json.dumps(key, ensure_ascii=False) for key in duplicates]
    for source, values in table.items():
        if not isinstance(values, dict):
            errors.append('%s: expected {"en": "...", "ru": "..."}' % json.dumps(source, ensure_ascii=False)); continue
        for language, text in values.items():
            if language not in LANGUAGES or not isinstance(text, str):
                errors.append('%s: unknown language %r or a value that is not text' % (json.dumps(source, ensure_ascii=False), language)); continue
            if text and sorted(PLACEHOLDER.findall(text)) != sorted(PLACEHOLDER.findall(source)):
                errors.append('%s: the "%s" translation %s must keep the placeholders %s' % (
                    json.dumps(source, ensure_ascii=False), language, json.dumps(text, ensure_ascii=False), ' '.join(PLACEHOLDER.findall(source)) or '(none)'))
    return table, errors


def save(table):
    items = list(table.items())
    lines = ['  '+json.dumps(k, ensure_ascii=False)+': '+json.dumps(v, ensure_ascii=False)+(',' if i < len(items)-1 else '')
             for i, (k, v) in enumerate(items)]
    write(TABLE, '{\n'+'\n'.join(lines)+'\n}\n')


def generated(table):
    rows = ['        {%s,%s,%s},' % (escape(source), *(escape(values[language]) if values.get(language) else 'null' for language in LANGUAGES))
            for source, values in table.items()]
    return '''package org.aliveclean;

import java.util.HashMap;

/**
 * Generated by tools/i18n.py from app/i18n/strings.json; do not edit.
 * Rows: {Chinese source, English, Russian}.
 */
final class I18nTable {
    private static final String[][] ROWS={
%s
    };
    private static final HashMap<String,String[]> MAP=new HashMap<>();
    static{for(String[] row:ROWS)MAP.put(row[0],new String[]{row[1],row[2]});}
    private I18nTable(){}
    static String[] get(String source){return MAP.get(source);}
}
''' % '\n'.join(rows)


def stale(table):
    return not GENERATED.exists() or GENERATED.read_text(encoding='utf8') != generated(table)


def hardcoded_resources():
    """Chinese written directly into layouts or the manifest instead of strings.xml."""
    found = []
    for path in sorted((ROOT/'app/res').glob('layout*/*.xml'))+[ROOT/'app/AndroidManifest.xml']:
        for number, line in enumerate(path.read_text(encoding='utf8').split('\n'), 1):
            for value in re.findall(r'android:\w+="([^"@?][^"]*)"', line):
                if CHINESE.search(value):
                    found.append((path, number, value))
    return found


def resource_gaps():
    """Strings in app/res/values missing from values-en / values-ru."""
    def names(folder):
        path = ROOT/'app/res'/folder/'strings.xml'
        return set(re.findall(r'<string name="([^"]+)"', path.read_text(encoding='utf8'))) if path.exists() else set()
    base = names('values')
    return {folder: sorted(base-names(folder)) for folder in ('values-en', 'values-ru') if base-names(folder)}


def check(quiet_stale=False):
    """Print the report; return the number of errors (only errors fail the build)."""
    sources, unwrapped = scan()
    table, errors = load()
    missing = [s for s in sources if s not in table]
    pending = {language: [s for s in sources if s in table and not table[s].get(language)] for language in LANGUAGES}
    unused = [s for s in table if s not in sources]
    hardcoded, gaps = hardcoded_resources(), resource_gaps()
    out_of_date = not errors and not quiet_stale and stale(table)
    if unwrapped:
        print('i18n: %d Chinese text(s) not wrapped in I18n.t() / 未用 I18n.t() 包裹 -> run: python tools/i18n.py update' % len(unwrapped))
        for path, number, raw in unwrapped:
            print('  %s  "%s"' % (where(path, number), raw))
    if missing:
        print('i18n: %d text(s) missing from app/i18n/strings.json / 缺少条目 -> run: python tools/i18n.py update' % len(missing))
        for source in missing:
            print('  %s  %s' % (sources[source], json.dumps(source, ensure_ascii=False)))
    for language, items in pending.items():
        if items:
            shown = 'Chinese' if language == 'en' else 'English, or Chinese without English'
            print('i18n: %d text(s) without a "%s" translation, %s is shown / 尚未翻译 (%s):' % (len(items), language, shown, language))
            for source in items:
                print('  '+json.dumps(source, ensure_ascii=False))
    if unused:
        print('i18n: %d entr(ies) in strings.json are no longer used and can be deleted / 未使用的条目:' % len(unused))
        for source in unused:
            print('  '+json.dumps(source, ensure_ascii=False))
    if hardcoded:
        print('i18n: %d Chinese text(s) written directly in layouts or the manifest; move them to app/res/values/strings.xml / 请移至 strings.xml:' % len(hardcoded))
        for path, number, value in hardcoded:
            print('  %s  "%s"' % (where(path, number), value))
    for folder, names in gaps.items():
        print('i18n: app/res/%s/strings.xml lacks %s (the default text is shown)' % (folder, ', '.join(names)))
    if out_of_date:
        print('i18n: %s is out of date -> run: python tools/i18n.py update (the build refreshes it too)' % where(GENERATED))
    for error in errors:
        print('i18n ERROR: '+error)
    if not (unwrapped or missing or any(pending.values()) or unused or hardcoded or gaps or out_of_date or errors):
        print('i18n: all %d texts translated / 全部已翻译' % len(sources))
    return len(errors)


def update():
    _, unwrapped = scan()
    lines_by_file = {}
    for path, number, _ in unwrapped:
        lines_by_file.setdefault(path, set()).add(number)
    wrapped = 0
    for path, numbers in lines_by_file.items():
        # Wrap exactly the literals that scan() reported, right to left within a line.
        spans = {}
        for number, line, match in ui_literals(path):
            if number in numbers and not WRAPPED.search(line[:match.start()]):
                spans.setdefault(number, []).append(match.span())
        lines = path.read_text(encoding='utf8').split('\n')
        for number, places in spans.items():
            line = lines[number-1]
            for start, end in reversed(places):
                line = line[:start]+'I18n.t('+line[start:end]+')'+line[end:]
                wrapped += 1
            lines[number-1] = line
        write(path, '\n'.join(lines))
    sources, _ = scan()
    table, errors = load()
    if errors:
        for error in errors:
            print('i18n ERROR: '+error)
        print('i18n: fix app/i18n/strings.json first; nothing else was changed in it')
        return 1
    added = [s for s in sources if s not in table]
    for source in added:
        table[source] = {language: '' for language in LANGUAGES}
    if added:
        save(table)
    refreshed = stale(table)
    if refreshed:
        write(GENERATED, generated(table))
    print('i18n: wrapped %d text(s) in I18n.t(), added %d empty entr(ies) to app/i18n/strings.json%s' % (
        wrapped, len(added), ', regenerated '+where(GENERATED) if refreshed else ''))
    for source in added:
        print('  '+json.dumps(source, ensure_ascii=False))
    if added:
        print('i18n: fill in "en" and "ru" for them when you can; until then the Chinese text is shown')
    return 0


def build():
    if check(quiet_stale=True):
        return 1
    table, _ = load()
    if stale(table):
        write(GENERATED, generated(table))
        print('i18n: regenerated %s from app/i18n/strings.json; commit it with strings.json' % where(GENERATED))
    return 0


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    commands = {'check': lambda: 1 if check() else 0, 'update': update, 'build': build}
    command = sys.argv[1] if len(sys.argv) > 1 else 'check'
    if command not in commands or len(sys.argv) > 2:
        sys.exit(__doc__)
    sys.exit(commands[command]())
