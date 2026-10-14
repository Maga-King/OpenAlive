"""Run the original Earth renderer in an isolated test package, without changing wallpaper."""
from pathlib import Path
import argparse,hashlib,json,lzma,os,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);a=p.parse_args()
sdk=Path(os.environ.get('ANDROID_HOME',Path.home()/'AppData/Local/Android/Sdk'));java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17'))/'bin'
bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar';out=ROOT/'build/xiaomi-startup';pkg='org.aliveclean.xiaomistartup'
for part in ['classes','dex','assets']:(out/part).mkdir(parents=True,exist_ok=True)
def run(*cmd,**kw):return subprocess.run(list(map(str,cmd)),check=True,**kw)
def adb(*cmd,**kw):return run(sdk/'platform-tools/adb.exe','-s',a.serial,*cmd,**kw)
assets=ROOT/'app/assets/xiaomi';recipe=json.loads((assets/'packs.json').read_text())['packs']['earth']
apk=out/'assets/earth.apk'
if not apk.exists():apk.write_bytes(b''.join(lzma.decompress((assets/'packs'/(k+'.xz')).read_bytes()) for k,n in recipe['chunks']))
assert hashlib.sha256(apk.read_bytes()).hexdigest()==recipe['sha256']
(out/'AndroidManifest.xml').write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{pkg}"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/><application android:debuggable="true"><activity android:name="org.aliveclean.XiaomiProbeActivity" android:exported="false"/></application><instrumentation android:name="org.aliveclean.XiaomiStartupProbe" android:targetPackage="{pkg}"/></manifest>',encoding='utf8')
run(java/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(android)+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-d',out/'classes',ROOT/'app/java/org/aliveclean/XiaomiPlayer.java',*sorted((ROOT/'tests/xiaomi/org/aliveclean').glob('*.java')))
run(java/'java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android,'--output',out/'dex',*sorted((out/'classes').rglob('*.class')))
run(bt/'aapt2.exe','link','-o',out/'base.apk','--manifest',out/'AndroidManifest.xml','-I',android,'-A',out/'assets')
with zipfile.ZipFile(out/'base.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w') as dst:
    for entry in src.infolist():dst.writestr(entry.filename,src.read(entry))
    dst.write(out/'dex/classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk')
key=out/'test.jks'
if not key.exists():run(java/'keytool.exe','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','test','-dname','CN=OpenAlive Earth test','-keyalg','RSA','-validity','10000')
run(java/'java.exe','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--ks-key-alias','test','--out',out/'test.apk',out/'aligned.apk')
adb('install','-r',out/'test.apk')
try:
    with (out/'result.txt').open('w',encoding='utf8') as log:
        adb('shell','am','instrument','--no-hidden-api-checks','-w',pkg+'/org.aliveclean.XiaomiStartupProbe',stdout=log,stderr=subprocess.STDOUT,timeout=90)
    result=(out/'result.txt').read_text(encoding='utf8');print(result)
    if 'XIAOMI_STARTUP_OK' not in result:raise RuntimeError('Startup probe failed')
finally:adb('uninstall',pkg)
