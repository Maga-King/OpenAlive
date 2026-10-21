"""Compare installed and candidate Xiaomi AOD rendering on an explicit device."""
from pathlib import Path
import argparse,json,os,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True)
p.add_argument('--foreground',action='store_true',help='Show a disposable test window to prevent OEM freezing; closes when checks finish.')
p.add_argument('--compare-native-paths',action='store_true',help='Also compare all battery pixels against the original system path parser in the disposable test process.')
a=p.parse_args()
sdk=Path(os.environ.get('ANDROID_HOME',Path.home()/'AppData/Local/Android/Sdk'))
java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if os.name=='nt' else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
ext='.exe' if os.name=='nt' else '';bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
out=ROOT/'build/xiaomi-aod-probe';pkg='org.aliveclean.xiaomiaodprobe'
for folder in ['classes','dex']:(out/folder).mkdir(parents=True,exist_ok=True)
def run(*args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def adb(*args,**kw):return run(sdk/'platform-tools'/('adb'+ext),'-s',a.serial,*args,**kw)
(out/'AndroidManifest.xml').write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{pkg}"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/><queries><package android:name="org.aliveclean"/></queries><application android:debuggable="true"><activity android:name="org.aliveclean.probe.XiaomiAodProbeActivity" android:exported="true" android:screenOrientation="portrait"/></application><instrumentation android:name="org.aliveclean.probe.XiaomiAodProbe" android:targetPackage="{pkg}"/></manifest>',encoding='utf8')
run(java/('javac'+ext),'-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(android)+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-cp',ROOT/'build/release/classes','-d',out/'classes',*[ROOT/'app/java/org/aliveclean'/n for n in ['XiaomiAodDensity.java','XiaomiAodPreview.java','XiaomiAodBattery.java']],*sorted((ROOT/'tests/xiaomiaod').rglob('*.java')))
run(java/('java'+ext),'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android,'--output',out/'dex',*sorted((out/'classes').rglob('*.class')))
run(bt/('aapt2'+ext),'link','-o',out/'base.apk','--manifest',out/'AndroidManifest.xml','-I',android)
with zipfile.ZipFile(out/'base.apk') as src,zipfile.ZipFile(out/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as dst:
    for entry in src.infolist():dst.writestr(entry.filename,src.read(entry))
    dst.write(out/'dex/classes.dex','classes.dex')
run(bt/('zipalign'+ext),'-f','4',out/'unsigned.apk',out/'aligned.apk')
key=out/'test.jks'
if not key.exists():run(java/('keytool'+ext),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','test','-dname','CN=OpenAlive AOD Test','-keyalg','RSA','-validity','10000')
run(java/('java'+ext),'-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--ks-key-alias','test','--out',out/'test.apk',out/'aligned.apk')
def capture(destination,native_paths=False):
    destination.mkdir(parents=True,exist_ok=True)
    with (destination/'result.txt').open('w',encoding='utf8') as log:
        command=[str(sdk/'platform-tools'/('adb'+ext)),'-s',a.serial,'shell','am','instrument','-w',pkg+'/org.aliveclean.probe.XiaomiAodProbe']
        if native_paths:command.insert(-2,'--no-hidden-api-checks')
        process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
        try:
            if a.foreground:
                deadline=time.monotonic()+10
                while time.monotonic()<deadline:
                    current=subprocess.run(command[:3]+['shell','pidof',pkg],capture_output=True,text=True)
                    if current.stdout.strip():
                        adb('shell','am','start','-W','-n',pkg+'/org.aliveclean.probe.XiaomiAodProbeActivity');break
                    time.sleep(.2)
            if process.wait(timeout=100):raise RuntimeError('Instrumentation command failed')
        finally:
            if process.poll() is None:process.kill();process.wait(timeout=5)
    result=(destination/'result.txt').read_text(encoding='utf8');print(result)
    if 'XIAOMI_AOD_RENDER_OK' not in result:raise RuntimeError('Clock probe failed; see '+str(destination/'result.txt'))
    names=['installed','candidate']+['candidate-'+p['id'] for p in json.loads((ROOT/'app/assets/xiaomi/catalog.json').read_text(encoding='utf8')) if p['id']!='snowmountain']
    names+=['battery-'+str(level)+'-'+str(charging).lower() for level in [0,1,19,20,50,99,100] for charging in [False,True]]
    for name in names:
        with (destination/(name+'.png')).open('wb') as image:adb('exec-out','run-as',pkg,'cat','files/'+name+'.png',stdout=image)
adb('install','-r',out/'test.apk')
try:
    capture(out)
    if a.compare_native_paths:
        from PIL import Image
        adb('shell','am','force-stop',pkg)
        reference=out/'native-path-reference';capture(reference,True)
        for level in [0,1,19,20,50,99,100]:
            for charging in [False,True]:
                name='battery-'+str(level)+'-'+str(charging).lower()+'.png'
                with Image.open(out/name) as portable_file,Image.open(reference/name) as native_file:
                    portable=portable_file.convert('RGBA');native=native_file.convert('RGBA')
                    if portable.size!=native.size or portable.tobytes()!=native.tobytes():
                        raise AssertionError('Original battery pixels differ: '+name)
                print('NATIVE_PATH_PIXELS_MATCH',name)
finally:adb('uninstall',pkg)
