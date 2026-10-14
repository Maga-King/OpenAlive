"""Build an isolated, offscreen motion test APK; all device calls require --serial."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.aliveclean.motioncheck'


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='Run instrumentation only on this explicit adb serial')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'AppData/Local/Android/Sdk'))
    java = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17')) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    bt = sdk / 'build-tools/35.0.0'
    android = sdk / 'platforms/android-35/android.jar'
    out = ROOT / 'build/wallpaper-motion'
    for name in ['classes', 'dex', 'assets/cosmic', 'assets/bubble']:
        (out / name).mkdir(parents=True, exist_ok=True)
    # Exact production palettes, without GPU/model assets or the installed app's data.
    for family in ['cosmic', 'bubble']:
        for source in (ROOT / 'app/assets' / family).glob('*.json'):
            shutil.copyfile(source, out / 'assets' / family / source.name)
    manifest = out / 'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.aliveclean.motioncheck">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<application android:label="OpenAlive motion test" android:hasCode="true"/>
<instrumentation android:name="org.aliveclean.WallpaperMotionInstrumentation" android:targetPackage="org.aliveclean.motioncheck"/>
</manifest>''', encoding='utf8')
    run(bt / ('aapt2' + suffix), 'link', '-o', out / 'base.apk', '--manifest', manifest,
        '-I', android, '-A', out / 'assets')
    app = ROOT / 'app/java/org/aliveclean'
    tests = ROOT / 'tests/android/org/aliveclean'
    sources = [app / (name + '.java') for name in
               ['AssetGl', 'WallpaperMotion', 'WallpaperTransitionTime', 'CosmicMotion', 'BubbleMotion']]
    sources += [tests / (name + '.java') for name in ['WallpaperMotionLifecycleTest', 'WallpaperMotionInstrumentation']]
    run(java / ('javac' + suffix), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-bootclasspath', str(android) + os.pathsep + str(bt / 'core-lambda-stubs.jar'),
        '-d', out / 'classes', *sources)
    run(java / ('java' + suffix), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--min-api', '28', '--lib', android, '--output', out / 'dex',
        *sorted((out / 'classes').rglob('*.class')))
    with zipfile.ZipFile(out / 'base.apk') as base, zipfile.ZipFile(out / 'unsigned.apk', 'w') as archive:
        for entry in base.infolist():
            archive.writestr(entry.filename, base.read(entry))
        archive.write(out / 'dex/classes.dex', 'classes.dex')
    run(bt / ('zipalign' + suffix), '-f', '4', out / 'unsigned.apk', out / 'aligned.apk')
    key = out / 'test.jks'
    if not key.exists():
        run(java / ('keytool' + suffix), '-genkeypair', '-keystore', key, '-storepass', 'android',
            '-keypass', 'android', '-alias', 'test', '-dname', 'CN=OpenAlive motion test',
            '-keyalg', 'RSA', '-validity', '10000')
    apk = out / 'test.apk'
    run(java / ('java' + suffix), '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-pass', 'pass:android', '--ks-key-alias', 'test', '--out', apk, out / 'aligned.apk')
    print(apk)
    if args.serial:
        adb = [sdk / ('platform-tools/adb' + suffix), '-s', args.serial]
        run(*adb, 'install', '-r', apk)
        try:
            result = run(*adb, 'shell', 'am', 'instrument', '-w',
                         PACKAGE + '/org.aliveclean.WallpaperMotionInstrumentation',
                         capture_output=True, text=True, timeout=90)
            (out / 'result.txt').write_text(result.stdout + result.stderr, encoding='utf8')
            print(result.stdout)
            if 'WALLPAPER_MOTION_OK' not in result.stdout:
                raise RuntimeError('Motion instrumentation failed; see build/wallpaper-motion/result.txt')
        finally:
            run(*adb, 'uninstall', PACKAGE)


if __name__ == '__main__':
    main()
