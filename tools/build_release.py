"""Build the published source/assets on Linux or Windows without a local ROM tree."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys,urllib.request,zipfile
import xml.etree.ElementTree as ET
from apk_layout import RESOURCE_ALIGNMENT,write_entry,verify_apk

ROOT=Path(__file__).resolve().parents[1]
WIN=os.name=='nt';EXE='.exe' if WIN else ''
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
JAVA=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if WIN else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
BT=SDK/'build-tools/35.0.0';ANDROID=SDK/'platforms/android-35/android.jar'
OUT=ROOT/'build/release';DIST=ROOT/'dist';VENDOR=ROOT/'vendor/flyme'
for folder in ['classes','dex','generated','ui-api','xz-source']:
    p=(OUT/folder).resolve()
    if not p.is_relative_to((ROOT/'build').resolve()):raise ValueError(p)
    if p.exists():shutil.rmtree(p)
    p.mkdir(parents=True)
DIST.mkdir(exist_ok=True)

def run(*args,env=None):subprocess.run([str(a) for a in args],cwd=ROOT,env=env,check=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def javac_args(name,args):
    # javac argument files avoid command length limits; paths use forward slashes.
    p=OUT/(name+'.args')
    p.write_text('\n'.join(json.dumps(str(a).replace('\\','/')) for a in args),encoding='utf8')
    run(JAVA/('javac'+EXE),'@'+str(p))

manifest=ET.parse(ROOT/'app/AndroidManifest.xml').getroot()
version=manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
apk=DIST/('OpenAlive-'+version+'-arm64-v8a.apk')

# Pin the compile-only API. It is never added to the APK.
xposed=OUT/'xposed-api-82.jar'
expected='f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'
if not xposed.exists() or sha(xposed)!=expected:
    with urllib.request.urlopen('https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar',timeout=60) as r:xposed.write_bytes(r.read())
if sha(xposed)!=expected:raise ValueError('Xposed API checksum mismatch')

for script in ['check_layout.py','check_clock_scope.py','check_xiaomi_assets.py']:run(sys.executable,ROOT/'tools'/script)
if WIN:
    linux='/mnt/'+ROOT.drive[0].lower()+ROOT.as_posix()[2:]
    # Local Windows builds use the existing WSL NDK; CI builds directly on Linux.
    import shlex
    run('wsl.exe','--','bash','-lc','cd '+shlex.quote(linux)+' && export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$HOME/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang" && "$HOME/.cargo/bin/cargo" test --locked && "$HOME/.cargo/bin/cargo" build --locked --release --target aarch64-linux-android')
else:
    ndk=Path(os.environ.get('ANDROID_NDK_HOME',str(SDK/'ndk/29.0.14206865')))
    env={**os.environ,'CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER':str(ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang')}
    run('cargo','test','--locked',env=env)
    run('cargo','build','--locked','--release','--target','aarch64-linux-android',env=env)

run(BT/('aapt2'+EXE),'compile','--dir',VENDOR/'res','-o',OUT/'vendor-res.zip')
run(BT/('aapt2'+EXE),'compile','--dir',ROOT/'app/res','-o',OUT/'app-res.zip')
# Extra translations for the original UI bundles: strings-only tables that reuse
# each bundle's own resource IDs, layered over it at runtime by LocaleOverlay.
# IDs are read from the bundle by name, so an updated bundle needs no manual step;
# strings the bundle no longer has are skipped.
BUNDLES={'com.meizu.wallpapersetting':'app/assets/ui/settings-ui.apk','com.flyme.systemuieditor':'app/assets/ui/editor-ui.apk',
         'com.android.thememanager':'app/assets/xiaomi/ui.apk'}
def string_ids(apk):
    dump=subprocess.run([str(BT/('aapt2'+EXE)),'dump','resources',str(apk)],capture_output=True,text=True,encoding='utf8',errors='replace',check=True).stdout
    return {m.group(2):m.group(1) for m in re.finditer(r'resource (0x[0-9a-f]{8}) string/(\S+)',dump)}
I18N=OUT/'i18n-assets'
if I18N.exists():shutil.rmtree(I18N)
(I18N/'i18n').mkdir(parents=True)
for pack in sorted(p for p in (ROOT/'app/locale-overlays').iterdir() if p.is_dir()):
    ids=string_ids(ROOT/BUNDLES[pack.name])
    work=OUT/('i18n-'+pack.name)
    if work.exists():shutil.rmtree(work)
    shutil.copytree(pack/'res',work/'res')
    for table in (work/'res').rglob('*.xml'):
        tree=ET.parse(table);root=tree.getroot()
        for item in list(root):
            if item.get('name') not in ids:print('Skipping translation missing from '+pack.name+': '+item.get('name'));root.remove(item)
        tree.write(table,encoding='utf-8',xml_declaration=True)
    names={item.get('name') for table in (work/'res').rglob('*.xml') for item in ET.parse(table).getroot()}
    (work/'ids.txt').write_text(''.join(pack.name+':string/'+name+' = '+ids[name]+'\n' for name in sorted(names)),encoding='ascii')
    (work/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+pack.name+'"/>\n',encoding='utf8')
    run(BT/('aapt2'+EXE),'compile','--dir',work/'res','-o',work/'res.zip')
    table=I18N/'i18n'/(pack.name+'.apk')
    run(BT/('aapt2'+EXE),'link','-o',table,'--manifest',work/'AndroidManifest.xml','-I',ANDROID,'--stable-ids',work/'ids.txt','--no-auto-version','--no-resource-removal',work/'res.zip')
    if string_ids(table)!={name:ids[name] for name in names}:raise ValueError('Translation IDs differ from '+pack.name)
run(BT/('aapt2'+EXE),'link','-o',OUT/'base.apk','--manifest',ROOT/'app/AndroidManifest.xml','-I',ANDROID,
    '--auto-add-overlay','--java',OUT/'generated','-A',ROOT/'app/assets','-A',I18N,OUT/'vendor-res.zip','-R',OUT/'app-res.zip')
# Reports untranslated text (never fatal) and refreshes I18nTable.java from app/i18n/strings.json.
run(sys.executable,ROOT/'tools/i18n.py','build')
javac_args('api',['-encoding','UTF-8','-source','8','-target','8','-bootclasspath',ANDROID,'-d',OUT/'ui-api',*sorted((ROOT/'tools/ui-api').rglob('*.java'))])
# Compile the Java 8 decoder from the pinned upstream sources. No decoder APK
# download, native executable or runtime network dependency is needed.
xz_source=ROOT/'vendor/xz/xz-1.12-sources.jar'
if sha(xz_source)!='c35c682fa8b617c1f6f21270df14bad4e11df2fe46962dfe45e765b7aec0181b':raise ValueError('XZ source checksum mismatch')
with zipfile.ZipFile(xz_source) as upstream:
    for entry in upstream.infolist():
        if not entry.filename.startswith('org/tukaani/xz/') or not entry.filename.endswith('.java'):continue
        target=(OUT/'xz-source'/entry.filename).resolve()
        if not target.is_relative_to((OUT/'xz-source').resolve()):raise ValueError(entry.filename)
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(upstream.read(entry))
sources=sorted((ROOT/'app/java').rglob('*.java'))+sorted((OUT/'generated').rglob('*.java'))+sorted((OUT/'xz-source').rglob('*.java'))
javac_args('app',['-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(ANDROID)+os.pathsep+str(BT/'core-lambda-stubs.jar'),
    '-classpath',str(xposed)+os.pathsep+str(OUT/'ui-api'),'-d',OUT/'classes',*sources])
# Use the Java launcher's argument file as well: a fresh checkout in a longer
# Windows path can exceed CreateProcess's command-line limit with all class files.
d8_args=OUT/'d8.args'
d8_args.write_text('\n'.join(json.dumps(str(a).replace('\\','/')) for a in [
    '-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',ANDROID,
    '--classpath',xposed,'--classpath',OUT/'ui-api','--output',OUT/'dex',
    *sorted((OUT/'classes').rglob('*.class'))]),encoding='utf8')
run(JAVA/('java'+EXE),'@'+str(d8_args))
native=ROOT/'target/aarch64-linux-android/release/libalive_clean.so'
with zipfile.ZipFile(OUT/'base.apk') as base,zipfile.ZipFile(OUT/'unsigned.apk','w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for entry in base.infolist():
        if not entry.filename.startswith('assets/video/'):write_entry(z,entry.filename,base.read(entry))
    z.write(OUT/'dex/classes.dex','classes.dex')
    z.write(VENDOR/'editor-ui.dex','classes2.dex')
    z.write(native,'lib/arm64-v8a/libalive_clean.so')
run(BT/('zipalign'+EXE),'-f','-p',RESOURCE_ALIGNMENT,OUT/'unsigned.apk',OUT/'aligned.apk')

key=Path(os.environ.get('OPENALIVE_KEYSTORE',str(ROOT/'local/development.jks')))
alias=os.environ.get('OPENALIVE_KEY_ALIAS','development')
env={**os.environ,'OPENALIVE_STORE_PASSWORD':os.environ.get('OPENALIVE_STORE_PASSWORD','android')}
if not key.exists():
    if 'OPENALIVE_KEYSTORE' in os.environ:raise FileNotFoundError('Configured signing key is missing')
    key.parent.mkdir(parents=True,exist_ok=True)
    run(JAVA/('keytool'+EXE),'-genkeypair','-keystore',key,'-storepass:env','OPENALIVE_STORE_PASSWORD','-keypass:env','OPENALIVE_STORE_PASSWORD',
        '-alias',alias,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=OpenAlive Development',env=env)
run(JAVA/('java'+EXE),'-jar',BT/'lib/apksigner.jar','sign','--alignment-preserved','true','--ks',key,
    '--ks-pass','env:OPENALIVE_STORE_PASSWORD','--ks-key-alias',alias,'--out',apk,OUT/'aligned.apk',env=env)
run(JAVA/('java'+EXE),'-jar',BT/'lib/apksigner.jar','verify','--verbose','--print-certs',apk)
run(BT/('zipalign'+EXE),'-c','-p',RESOURCE_ALIGNMENT,apk)
report={'version':version,'apk':apk.name,'sha256':sha(apk),'bytes':apk.stat().st_size,'resource_tables':verify_apk(apk),
        'executables':{'classes.dex':sha(OUT/'dex/classes.dex'),'classes2.dex':sha(VENDOR/'editor-ui.dex'),'libalive_clean.so':sha(native)}}
with zipfile.ZipFile(apk) as z:
    assert z.read('lib/arm64-v8a/libalive_clean.so')==native.read_bytes()
    assert z.read('classes2.dex')==(VENDOR/'editor-ui.dex').read_bytes()
    assert not any(n.startswith('assets/video/') for n in z.namelist())
(DIST/'build-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
(DIST/'SHA256SUMS').write_text(report['sha256']+'  '+apk.name+'\n',encoding='ascii')
print(json.dumps(report,indent=2))
