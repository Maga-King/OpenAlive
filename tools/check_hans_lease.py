"""Test lease IPC lifecycle on a non-ColorOS device with a test-only Binder fixture."""
from pathlib import Path
from apk_layout import RESOURCE_ALIGNMENT, write_entry
import os, subprocess, zipfile
ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
JDK=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17'))/'bin'
BT=SDK/'build-tools/35.0.0'; ANDROID=SDK/'platforms/android-35/android.jar'
ADB=SDK/'platform-tools/adb.exe'; OUT=ROOT/'build/hans-test'
for name in ('classes','dex'):(OUT/name).mkdir(parents=True,exist_ok=True)
def run(*args,**kw):return subprocess.run([str(a) for a in args],check=True,**kw)
# Boot-classpath ColorOS APIs would override the test fixture, so never run there.
rom=run(ADB,'shell','getprop','ro.build.version.oplusrom',capture_output=True,text=True).stdout.strip()
if rom:raise RuntimeError('This Binder fixture must run on the spare non-ColorOS phone')
manifest=OUT/'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.aliveclean.leasetest">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<application android:label="OpenAlive lease test"/>
<instrumentation android:name="org.aliveclean.HansLeaseInstrumentation" android:targetPackage="org.aliveclean.leasetest"/>
</manifest>''',encoding='utf8')
run(BT/'aapt2.exe','link','-o',OUT/'base.apk','--manifest',manifest,'-I',ANDROID)
sources=[ROOT/'app/java/org/aliveclean/ColorOsAnimationLease.java',*list((ROOT/'tests/hans').rglob('*.java')),*list((ROOT/'tests/fixtures/hans').rglob('*.java'))]
run(JDK/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',ANDROID,'-d',OUT/'classes',*sources)
run(JDK/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',ANDROID,'--output',OUT/'dex',*(OUT/'classes').rglob('*.class'))
with zipfile.ZipFile(OUT/'base.apk') as base,zipfile.ZipFile(OUT/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as z:
    for entry in base.infolist():write_entry(z,entry.filename,base.read(entry))
    z.write(OUT/'dex/classes.dex','classes.dex')
run(BT/'zipalign.exe','-f',RESOURCE_ALIGNMENT,OUT/'unsigned.apk',OUT/'aligned.apk')
run(JDK/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--alignment-preserved','true','--ks',ROOT/'local/development.jks','--ks-pass','pass:android','--ks-key-alias','development','--out',OUT/'test.apk',OUT/'aligned.apk')
run(ADB,'install','-r',OUT/'test.apk')
try:
    result=run(ADB,'shell','am','instrument','-w','org.aliveclean.leasetest/org.aliveclean.HansLeaseInstrumentation',capture_output=True,text=True,timeout=45)
    (OUT/'result.txt').write_text(result.stdout+result.stderr,encoding='utf8');print(result.stdout)
    assert 'HANS_LEASE_OK' in result.stdout,result.stderr
finally:run(ADB,'uninstall','org.aliveclean.leasetest')
