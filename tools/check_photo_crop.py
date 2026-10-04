"""Build/run the production display-aspect crop in an isolated test package."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
args = parser.parse_args()
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'AppData/Local/Android/Sdk')))
WIN = os.name == 'nt'
EXE = '.exe' if WIN else ''
JAVA = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if WIN else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'
BT = SDK / 'build-tools/35.0.0'
ANDROID = SDK / 'platforms/android-35/android.jar'
ADB = SDK / ('platform-tools/adb' + EXE)
OUT = ROOT / 'build/photo-crop-test'
PACKAGE = 'org.aliveclean.photocroptest'

def run(*command, **kwargs):
    return subprocess.run(list(map(str, command)), check=True, **kwargs)

def adb(*command, **kwargs):
    return run(ADB, '-s', args.serial, *command, **kwargs)

adb('get-state')
for name in ['classes', 'dex']:
    path = (OUT / name).resolve()
    assert path.is_relative_to((ROOT / 'build').resolve())
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
(OUT / 'AndroidManifest.xml').write_text(
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
    f'package="{PACKAGE}"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>'
    '<application android:label="OpenAlive crop test" android:debuggable="true">'
    '<activity android:name="org.aliveclean.PhotoCropActivity" android:exported="true" '
    'android:theme="@android:style/Theme.Material.NoActionBar"/>'
    '<activity android:name="org.aliveclean.PhotoCropInstrumentation$Host" android:exported="true"/></application>'
    '<instrumentation android:name="org.aliveclean.PhotoCropInstrumentation" '
    f'android:targetPackage="{PACKAGE}"/></manifest>', encoding='utf8')
sources = [ROOT / 'tests/crop/java/org/aliveclean/PhotoCropInstrumentation.java'] + [
    ROOT / 'app/java/org/aliveclean' / (name + '.java') for name in ['PhotoCropActivity', 'PhotoViewport', 'I18n', 'I18nTable']]
run(JAVA / ('javac' + EXE), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', ANDROID, '-classpath', BT / 'core-lambda-stubs.jar', '-d', OUT / 'classes', *sources)
run(JAVA / ('java' + EXE), '-cp', BT / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28', '--lib', ANDROID,
    '--output', OUT / 'dex', *sorted((OUT / 'classes').rglob('*.class')))
run(BT / ('aapt2' + EXE), 'link', '-o', OUT / 'base.apk', '--manifest', OUT / 'AndroidManifest.xml', '-I', ANDROID)
with zipfile.ZipFile(OUT / 'base.apk') as src, zipfile.ZipFile(OUT / 'unsigned.apk', 'w') as dst:
    for entry in src.infolist():
        dst.writestr(entry.filename.replace('\\', '/'), src.read(entry), compress_type=zipfile.ZIP_STORED)
    dst.write(OUT / 'dex/classes.dex', 'classes.dex')
run(BT / ('zipalign' + EXE), '-f', '-p', '4096', OUT / 'unsigned.apk', OUT / 'aligned.apk')
run(JAVA / ('java' + EXE), '-jar', BT / 'lib/apksigner.jar', 'sign', '--ks', ROOT / 'local/development.jks',
    '--ks-pass', 'pass:android', '--ks-key-alias', 'development', '--out', OUT / 'probe.apk', OUT / 'aligned.apk')
adb('install', '-r', OUT / 'probe.apk')
try:
    result = adb('shell', 'am', 'instrument', '-w', PACKAGE + '/org.aliveclean.PhotoCropInstrumentation',
                 capture_output=True, text=True, encoding='utf8', timeout=60)
    (OUT / 'result.txt').write_text(result.stdout + result.stderr, encoding='utf8')
    print(result.stdout)
    if 'PHOTO_CROP_OK' not in result.stdout:
        raise RuntimeError('Crop check failed; see build/photo-crop-test/result.txt')
    with (OUT / 'crop-ui.png').open('wb') as output:
        adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/crop-ui.png', stdout=output)
finally:
    adb('uninstall', PACKAGE)
