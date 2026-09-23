"""Verify pause/resume with the built APK's exact code/assets in a disposable test app."""
from pathlib import Path
import os,subprocess,zipfile,sys
from apk_layout import RESOURCE_ALIGNMENT,write_entry
ROOT=Path(__file__).resolve().parents[1]
SDK=Path.home()/'AppData/Local/Android/Sdk'; BT=SDK/'build-tools/35.0.0'
JAVA=Path('C:/Program Files/Java/jdk-17/bin'); ANDROID=SDK/'platforms/android-35/android.jar'
ADB=SDK/'platform-tools/adb.exe'; OUT=ROOT/'build/render-lifecycle'
for n in ['classes','dex']:(OUT/n).mkdir(parents=True,exist_ok=True)
def run(*a,**kw):return subprocess.run(list(map(str,a)),check=True,**kw)
rom=run(ADB,'shell','getprop','ro.build.version.oplusrom',capture_output=True,text=True).stdout.strip()
if rom:raise RuntimeError('Run this app-process instrumentation on the spare phone only')
(OUT/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.aliveclean.rendercheck">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/><application android:label="OpenAlive render test" android:extractNativeLibs="true"/>
<instrumentation android:name="org.aliveclean.RenderLifecycleInstrumentation" android:targetPackage="org.aliveclean.rendercheck"/>
</manifest>''',encoding='utf8')
run(BT/'aapt2.exe','link','-o',OUT/'base.apk','--manifest',OUT/'AndroidManifest.xml','-I',ANDROID)
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(ANDROID)+os.pathsep+str(BT/'core-lambda-stubs.jar'),'-cp',ROOT/'build/release/classes','-d',OUT/'classes',*list((ROOT/'tests/render').rglob('*.java')))
run(JAVA/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',ANDROID,'--output',OUT/'dex',*(OUT/'classes').rglob('*.class'))
with zipfile.ZipFile(OUT/'base.apk') as src,zipfile.ZipFile(ROOT/'dist/OpenAlive-0.4.18-preview-arm64-v8a.apk') as production,zipfile.ZipFile(OUT/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as dst:
    write_entry(dst,'AndroidManifest.xml',src.read('AndroidManifest.xml'))
    for e in production.infolist():
        if e.filename!='AndroidManifest.xml' and not e.filename.startswith('META-INF/'):
            write_entry(dst,e.filename,production.read(e))
    dst.write(OUT/'dex/classes.dex','classes3.dex')
run(BT/'zipalign.exe','-f',RESOURCE_ALIGNMENT,OUT/'unsigned.apk',OUT/'aligned.apk')
run(JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--alignment-preserved','true','--ks',ROOT/'local/development.jks','--ks-pass','pass:android','--ks-key-alias','development','--out',OUT/'test.apk',OUT/'aligned.apk')
run(ADB,'install','-r',OUT/'test.apk')
try:
    cases=[(9,False),(0,False)] if '--finite-only' in sys.argv else [(9,True),(201,True),(0,True),(9,False),(0,False)]
    for variant,continuous in cases:
        run(ADB,'shell','input','keyevent','224')
        result=run(ADB,'shell','am','instrument','-w','-e','variant',variant,'-e','continuous',str(continuous).lower(),'org.aliveclean.rendercheck/org.aliveclean.RenderLifecycleInstrumentation',capture_output=True,text=True,timeout=60)
        (OUT/f'result-{variant}-{continuous}.txt').write_text(result.stdout+result.stderr,encoding='utf8');print(result.stdout)
        assert 'RENDER_LIFECYCLE_OK' in result.stdout,result.stdout
finally:run(ADB,'uninstall','org.aliveclean.rendercheck')
