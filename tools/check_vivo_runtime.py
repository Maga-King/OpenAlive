"""Run an isolated Vivo renderer/sensor regression APK on an explicitly selected device.

The installed wallpaper, app preferences and SystemUI are not changed. Hidden-API
checks are disabled for this instrumentation process only, so the test can inspect
sensor subscriptions and inject reproducible SensorEvents into the original engine.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='ADB serial of the test device')
parser.add_argument('--coordinates', action='store_true', help='Run deterministic touch and gyro-to-pixel diagnostics')
parser.add_argument('--build-only', action='store_true', help='Compile the probe without connecting to or changing a device')
parser.add_argument('--media', action='store_true', help='Test independent media replacement and the original video controller')
args = parser.parse_args()
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'AppData/Local/Android/Sdk')))
WIN = os.name == 'nt'
EXE = '.exe' if WIN else ''
JAVA = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if WIN
                           else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'
BT = SDK / 'build-tools/35.0.0'
ANDROID = SDK / 'platforms/android-35/android.jar'
ADB = SDK / ('platform-tools/adb' + EXE)
OUT = ROOT / 'build/vivo-runtime-test'
PACKAGE = 'org.aliveclean.vivoprobe'
KEY = ROOT / 'local/development.jks'
if not KEY.is_file():
    raise SystemExit('Run tools/build_release.py once to create the local development test key.')

def run(*command, **kwargs):
    return subprocess.run(list(map(str, command)), check=True, **kwargs)

def adb(*command, **kwargs):
    return run(ADB, '-s', args.serial, *command, **kwargs)

if not args.build_only:
    adb('get-state')
for name in ['classes', 'dex', 'assets']:
    path = (OUT / name).resolve()
    assert path.is_relative_to((ROOT / 'build').resolve())
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
shutil.copytree(ROOT / 'app/assets/vivo/runtime', OUT / 'assets/vivo/runtime')
if args.media:
    shutil.copyfile(ROOT / 'build/vivo-media-fixture.mp4', OUT / 'assets/media.mp4')
(OUT / 'AndroidManifest.xml').write_text(
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
    f'package="{PACKAGE}"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>'
    '<application android:label="OpenAlive renderer test" android:debuggable="true">'
    '<activity android:name="org.aliveclean.VivoSceneProbe$ProbeActivity" android:exported="true"/></application>'
    '<instrumentation android:name="org.aliveclean.VivoSceneProbe" '
    f'android:targetPackage="{PACKAGE}"/></manifest>', encoding='utf8')
sources = [ROOT / 'tests/vivo/VivoSceneProbe.java'] + [
    ROOT / 'app/java/org/aliveclean' / (name + '.java') for name in [
        'VivoEngineRuntime', 'AssetGl', 'VivoCalls', 'VivoTextureLayer', 'PhotoViewport',
        'VivoEngineScene', 'VivoSensorGain', 'VivoOptions', 'VivoImages', 'VivoMediaSelection', 'VivoWallpaperZoom', 'VivoWallpaperDim', 'VivoDimPass', 'VivoUi', 'I18n', 'I18nTable']]
run(JAVA / ('javac' + EXE), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', ANDROID, '-classpath', BT / 'core-lambda-stubs.jar',
    '-d', OUT / 'classes', *sources)
run(JAVA / ('java' + EXE), '-cp', BT / 'lib/d8.jar', 'com.android.tools.r8.D8',
    '--min-api', '28', '--lib', ANDROID, '--output', OUT / 'dex',
    *sorted((OUT / 'classes').rglob('*.class')))
run(BT / ('aapt2' + EXE), 'link', '-o', OUT / 'base.apk', '--manifest',
    OUT / 'AndroidManifest.xml', '-I', ANDROID, '-A', OUT / 'assets')
with zipfile.ZipFile(OUT / 'base.apk') as src, zipfile.ZipFile(OUT / 'unsigned.apk', 'w') as dst:
    for entry in src.infolist():
        dst.writestr(entry.filename.replace('\\', '/'), src.read(entry),
                     compress_type=zipfile.ZIP_STORED if entry.filename.endswith('resources.arsc')
                     else zipfile.ZIP_DEFLATED)
    dst.write(OUT / 'dex/classes.dex', 'classes.dex')
run(BT / ('zipalign' + EXE), '-f', '-p', '4096', OUT / 'unsigned.apk', OUT / 'aligned.apk')
run(JAVA / ('java' + EXE), '-jar', BT / 'lib/apksigner.jar', 'sign', '--ks', KEY,
    '--ks-pass', 'pass:android', '--ks-key-alias', 'development', '--out',
    OUT / 'probe.apk', OUT / 'aligned.apk')
if args.build_only:
    print('Probe compiled only; no device operations performed.')
    raise SystemExit(0)
adb('install', '-r', OUT / 'probe.apk')
try:
    result = adb('shell', 'am', 'instrument', '--no-hidden-api-checks', '-w', '-e', 'media',str(args.media).lower(),'-e', 'coordinates', str(args.coordinates).lower(),
                 PACKAGE + '/org.aliveclean.VivoSceneProbe', capture_output=True,
                 text=True, encoding='utf8', timeout=90)
    (OUT / 'result.txt').write_text(result.stdout + result.stderr, encoding='utf8')
    print(result.stdout)
    if 'VIVO_SCENE_OK' not in result.stdout:
        raise RuntimeError('Vivo renderer regression failed; see build/vivo-runtime-test/result.txt')
    names = ['liquid-touch-25', 'liquid-touch-75', 'flash-axis-0', 'flash-axis-1'] if args.coordinates else [f'scene-{family}' for family in range(1, 4)]
    for name in names:
        with (OUT / f'{name}.png').open('wb') as output:
            adb('exec-out', 'run-as', PACKAGE, 'cat', f'files/{name}.png', stdout=output)
finally:
    adb('uninstall', PACKAGE)
