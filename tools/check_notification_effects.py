"""Run official notification assets and cutout/ownership tests in an isolated app."""
from pathlib import Path
import os, subprocess, sys, zipfile
from apk_layout import RESOURCE_ALIGNMENT, write_entry

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'AppData/Local/Android/Sdk')))
BT = SDK / 'build-tools/35.0.0'
JAVA = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17')) / 'bin'
ANDROID = SDK / 'platforms/android-35/android.jar'
OUT = ROOT / 'build/notification-test'
for name in ['classes', 'dex']:
    (OUT / name).mkdir(parents=True, exist_ok=True)
ADB = [str(SDK / 'platform-tools/adb.exe'), '-s', sys.argv[1]]
def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)

(OUT / 'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.aliveclean.notificationtest">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<application android:label="OpenAlive notification test" android:debuggable="true" android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen">
<activity android:name="org.aliveclean.NotificationTestActivity" android:exported="false"/></application>
<instrumentation android:name="org.aliveclean.NotificationEffectsInstrumentation" android:targetPackage="org.aliveclean.notificationtest"/>
</manifest>''', encoding='utf-8')
run(BT/'aapt2.exe', 'link', '-o', OUT/'base.apk', '--manifest', OUT/'AndroidManifest.xml', '-I', ANDROID)
run(JAVA/'javac.exe', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', str(ANDROID)+os.pathsep+str(BT/'core-lambda-stubs.jar'), '-cp', ROOT/'build/release/classes', '-d', OUT/'classes', *(ROOT/'tests/notification').rglob('*.java'))
run(JAVA/'java.exe', '-cp', BT/'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28', '--lib', ANDROID, '--output', OUT/'dex', *(OUT/'classes').rglob('*.class'))
with zipfile.ZipFile(OUT/'base.apk') as base, zipfile.ZipFile(ROOT/'dist/OpenAlive-0.4.18-preview-arm64-v8a.apk') as production, zipfile.ZipFile(OUT/'unsigned.apk', 'w', zipfile.ZIP_DEFLATED) as dst:
    write_entry(dst, 'AndroidManifest.xml', base.read('AndroidManifest.xml'))
    for e in production.infolist():
        if e.filename != 'AndroidManifest.xml' and not e.filename.startswith('META-INF/'):
            write_entry(dst, e.filename, production.read(e))
    dst.write(OUT/'dex/classes.dex', 'classes3.dex')
run(BT/'zipalign.exe', '-f', RESOURCE_ALIGNMENT, OUT/'unsigned.apk', OUT/'aligned.apk')
run(JAVA/'java.exe', '-jar', BT/'lib/apksigner.jar', 'sign', '--alignment-preserved', 'true', '--ks', ROOT/'local/development.jks', '--ks-pass', 'pass:android', '--ks-key-alias', 'development', '--out', OUT/'test.apk', OUT/'aligned.apk')
run(*ADB, 'install', '-r', OUT/'test.apk')
try:
    result = run(*ADB, 'shell', 'am', 'instrument', '-w', 'org.aliveclean.notificationtest/org.aliveclean.NotificationEffectsInstrumentation', capture_output=True, text=True, timeout=90)
    (OUT/'result.txt').write_text(result.stdout+result.stderr, encoding='utf-8')
    print(result.stdout)
    for name in ['ring.png', 'edge.png', 'ring-blue.png']:
        image = subprocess.run([*ADB, 'exec-out', 'run-as', 'org.aliveclean.notificationtest', 'cat', 'files/'+name], capture_output=True)
        if image.returncode == 0:
            (OUT/name).write_bytes(image.stdout)
    assert 'NOTIFICATION_EFFECTS_OK' in result.stdout, result.stdout
finally:
    run(*ADB, 'uninstall', 'org.aliveclean.notificationtest')
