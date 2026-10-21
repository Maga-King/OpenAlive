"""Exercise real BLAST surface replacement without changing the user's wallpaper.
Build native code first. --baseline compares HEAD's adapter in the same probe.
"""
from pathlib import Path
import argparse,hashlib,json,lzma,os,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True)
p.add_argument('--family',choices=['snowmountain','earth'],default='snowmountain')
p.add_argument('--baseline',action='store_true')
a=p.parse_args()
sdk=Path(os.environ.get('ANDROID_HOME',Path.home()/'AppData/Local/Android/Sdk'))
java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if os.name=='nt' else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
ext='.exe' if os.name=='nt' else ''
bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
out=ROOT/'build/xiaomi-surface'/f'{a.family}-{"baseline" if a.baseline else "fixed"}'
pkg='org.aliveclean.xiaomisurface'
for folder in ['classes','dex','assets']: (out/folder).mkdir(parents=True,exist_ok=True)
def run(*args,**kw): return subprocess.run(list(map(str,args)),check=True,**kw)
def adb(*args,**kw): return run(sdk/'platform-tools'/('adb'+ext),'-s',a.serial,*args,**kw)
assets=ROOT/'app/assets/xiaomi';recipe=json.loads((assets/'packs.json').read_text())['packs'][a.family]
original=out/'assets'/(a.family+'.apk')
if not original.exists():original.write_bytes(b''.join(lzma.decompress((assets/'packs'/(k+'.xz')).read_bytes()) for k,n in recipe['chunks']))
assert hashlib.sha256(original.read_bytes()).hexdigest()==recipe['sha256']
adapter=ROOT/'app/java/org/aliveclean/XiaomiPlayer.java'
if a.baseline:
    adapter=out/'XiaomiPlayer.java'
    adapter.write_bytes(subprocess.check_output(['git','show','HEAD:app/java/org/aliveclean/XiaomiPlayer.java'],cwd=ROOT))
(out/'AndroidManifest.xml').write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{pkg}"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/><application android:debuggable="true"><activity android:name="org.aliveclean.XiaomiSurfaceActivity" android:screenOrientation="portrait" android:exported="false"/></application><instrumentation android:name="org.aliveclean.XiaomiSurfaceProbe" android:targetPackage="{pkg}"/></manifest>',encoding='utf8')
run(java/('javac'+ext),'-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(android)+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-d',out/'classes',adapter,ROOT/'app/java/org/aliveclean/XiaomiSurfaceLease.java',*sorted((ROOT/'tests/xiaomisurface').rglob('*.java')))
run(java/('java'+ext),'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android,'--output',out/'dex',*sorted((out/'classes').rglob('*.class')))
run(bt/('aapt2'+ext),'link','-o',out/'base.apk','--manifest',out/'AndroidManifest.xml','-I',android,'-A',out/'assets')
with zipfile.ZipFile(out/'base.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as dst:
    for entry in src.infolist():dst.writestr(entry.filename,src.read(entry))
    dst.write(out/'dex/classes.dex','classes.dex')
    dst.write(ROOT/'target/aarch64-linux-android/release/libalive_clean.so','lib/arm64-v8a/libalive_clean.so')
run(bt/('zipalign'+ext),'-f','4',out/'unsigned.apk',out/'aligned.apk')
key=out/'test.jks'
if not key.exists():run(java/('keytool'+ext),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','test','-dname','CN=OpenAlive Surface Test','-keyalg','RSA','-validity','10000')
run(java/('java'+ext),'-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--ks-key-alias','test','--out',out/'test.apk',out/'aligned.apk')
adb('install','-r',out/'test.apk')
try:
    with (out/'result.txt').open('w',encoding='utf8') as log:
        adb('shell','am','instrument','--no-hidden-api-checks','-w','-e','family',a.family,'-e','digest',recipe['sha256'],pkg+'/org.aliveclean.XiaomiSurfaceProbe',stdout=log,stderr=subprocess.STDOUT,timeout=100)
    result=(out/'result.txt').read_text(encoding='utf8');print(result)
    if 'XIAOMI_SURFACE_OK' not in result:raise RuntimeError('Surface handoff probe failed; see '+str(out/'result.txt'))
finally:adb('uninstall',pkg)
