"""Build/run isolated GPU + asynchronous codec tests for six Flyme 9 additions.

Compile without device access:
  python tools/check_flyme9_extra.py --compile-only
Build, install, test, collect screenshots and uninstall the disposable package:
  python tools/check_flyme9_extra.py --serial DEVICE_SERIAL
Production release classes must exist for the factory's unused other families.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.aliveclean.flymeextracheck'
ENTRY = PACKAGE + '/org.aliveclean.FlymeExtraInstrumentation'


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('--compile-only', action='store_true', help='Build test.apk without any ADB calls')
    selection.add_argument('--serial', help='Only this explicitly selected device is used')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'AppData/Local/Android/Sdk'))
    java = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17')) / 'bin'
    exe = '.exe' if os.name == 'nt' else ''
    bt = sdk / 'build-tools/35.0.0'
    android = sdk / 'platforms/android-35/android.jar'
    release = ROOT / 'build/release/classes'
    if not release.is_dir():
        raise RuntimeError('Build production release classes before this isolated test')
    out = ROOT / 'build/flyme-extra'
    for name in ('classes', 'dex', 'assets'):
        directory = out / name
        if directory.resolve().parent != out.resolve() or out.resolve().parent != (ROOT / 'build').resolve():
            raise RuntimeError('Unexpected test output directory')
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True)
    for family in ('stereo', 'flyme-video'):
        shutil.copytree(ROOT / 'app/assets' / family, out / 'assets' / family, dirs_exist_ok=True)
    manifest = out / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<application android:label="OpenAlive Flyme extra test" android:debuggable="true"/>
<instrumentation android:name="org.aliveclean.FlymeExtraInstrumentation" android:targetPackage="{PACKAGE}"/>
</manifest>''', encoding='utf8')
    run(bt / ('aapt2' + exe), 'link', '-o', out / 'base.apk', '--manifest', manifest,
        '-I', android, '-A', out / 'assets', '-0', 'mp4')
    app = ROOT / 'app/java/org/aliveclean'
    # Actual current production sources, not a mock renderer/decoder. Existing
    # release classes only satisfy other unexecuted WallpaperRenderer branches.
    sources = [app / (name + '.java') for name in (
        'AssetGl', 'WallpaperMotion', 'WallpaperRenderer', 'StereoRenderer', 'StereoMotion',
        'FlymeVideoRenderer', 'FlymeVideoMotion', 'FlymeVideoDecoder')]
    sources.append(ROOT / 'tests/flymeextra/org/aliveclean/FlymeExtraInstrumentation.java')
    run(java / ('javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-bootclasspath', str(android) + os.pathsep + str(bt / 'core-lambda-stubs.jar'),
        '-classpath', release, '-d', out / 'classes', *sources)
    run(java / ('java' + exe), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28',
        '--lib', android, '--classpath', release, '--output', out / 'dex',
        *sorted((out / 'classes').rglob('*.class')))
    with zipfile.ZipFile(out / 'base.apk') as base, zipfile.ZipFile(out / 'unsigned.apk', 'w') as archive:
        for entry in base.infolist():
            # Retain aapt2's uncompressed MP4 storage for AssetManager.openFd.
            archive.writestr(entry, base.read(entry))
        archive.write(out / 'dex/classes.dex', 'classes.dex')
    run(bt / ('zipalign' + exe), '-f', '4', out / 'unsigned.apk', out / 'aligned.apk')
    key = out / 'test.jks'
    if not key.exists():
        run(java / ('keytool' + exe), '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
            '-alias', 'test', '-dname', 'CN=OpenAlive Flyme extra test', '-keyalg', 'RSA', '-validity', '10000')
    apk = out / 'test.apk'
    run(java / ('java' + exe), '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-pass', 'pass:android', '--ks-key-alias', 'test', '--out', apk, out / 'aligned.apk')
    print(f'Test APK: {apk}')
    if args.compile_only:
        print(f'Run: adb -s SERIAL shell am instrument -w {ENTRY}')
        return
    adb = [sdk / ('platform-tools/adb' + exe), '-s', args.serial]
    run(*adb, 'install', '-r', apk)
    try:
        result = run(*adb, 'shell', 'am', 'instrument', '-w', ENTRY,
                     capture_output=True, text=True, timeout=180)
        (out / 'result.txt').write_text(result.stdout + result.stderr, encoding='utf8')
        print(result.stdout)
        listing = run(*adb, 'shell', 'run-as', PACKAGE, 'ls', 'files', capture_output=True, text=True)
        for name in listing.stdout.split():
            if name.startswith('flymeextra-') and name.endswith('.png'):
                with (out / name).open('wb') as image:
                    run(*adb, 'exec-out', 'run-as', PACKAGE, 'cat', 'files/' + name, stdout=image)
        if 'FLYME_EXTRA_OK' not in result.stdout:
            raise RuntimeError('GPU/codec test failed; see build/flyme-extra/result.txt')
    finally:
        run(*adb, 'uninstall', PACKAGE)


if __name__ == '__main__':
    main()
