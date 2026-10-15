"""Build/run isolated production RenderLoop lifecycle instrumentation.

Build without a device: --build-only. To install/run, select --serial explicitly.
All production inputs are read-only; build/stereo-lifecycle owns every output.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile
from apk_layout import RESOURCE_ALIGNMENT, write_entry

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.aliveclean.stereolifecycle'
COMPONENT = PACKAGE + '/org.aliveclean.StereoLifecycleInstrumentation'


def run(*command, **kwargs):
    if kwargs.get('text'):
        kwargs.setdefault('encoding', 'utf-8')
        kwargs.setdefault('errors', 'replace')
    return subprocess.run(list(map(str, command)), check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='Required for every device operation')
    parser.add_argument('--build-only', action='store_true', help='Compile/package only; never call adb')
    parser.add_argument('--video', action='store_true', help='Also exercise production video pause/resume')
    parser.add_argument('--ordinary', action='store_true', help='Also exercise ordinary gradient animation pause/resume')
    parser.add_argument('--keep-installed', action='store_true')
    args = parser.parse_args()
    if not args.build_only and not args.serial:
        parser.error('--serial is required unless --build-only is selected')
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'AppData/Local/Android/Sdk'))
    exe = '.exe' if os.name == 'nt' else ''
    java = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if os.name == 'nt' else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'
    bt = sdk / 'build-tools/35.0.0'
    android = sdk / 'platforms/android-35/android.jar'
    release = ROOT / 'build/release/classes'
    out = ROOT / 'build/stereo-lifecycle'
    if not (release / 'org/aliveclean/RenderLoop.class').is_file():
        raise SystemExit('Read-only build/release/classes is required; finish the production Java compile first.')
    for name in ('classes', 'dex', 'assets', 'sources'):
        path = (out / name).resolve()
        if not path.is_relative_to((ROOT / 'build/stereo-lifecycle').resolve()):
            raise ValueError(path)
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True)
    # Copy compiled production dependencies once. Recompile the actual touched
    # production entry points below; never modify or clean build/release.
    shutil.copytree(release, out / 'classes', dirs_exist_ok=True)
    app = ROOT / 'app/java/org/aliveclean'
    production = ['RenderLoop', 'SceneOptions', 'WallpaperRenderer', 'WallpaperMotion',
                  'StereoRenderer', 'StereoMotion', 'StereoSensor', 'AssetGl',
                  'FlymeVideoRenderer', 'FlymeVideoDecoder', 'FlymeVideoMotion']
    originals = [app / (name + '.java') for name in production]
    originals.append(ROOT / 'tests/stereo/org/aliveclean/StereoLifecycleInstrumentation.java')
    sources = []
    source_hashes = {}
    for original in originals:
        data = original.read_bytes()
        target = out / 'sources' / original.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        sources.append(target)
        source_hashes[original.relative_to(ROOT).as_posix()] = hashlib.sha256(data).hexdigest()
    javac = ['-encoding', 'UTF-8', '-source', '8', '-target', '8',
             '-bootclasspath', str(android) + os.pathsep + str(bt / 'core-lambda-stubs.jar'),
             '-classpath', out / 'classes', '-d', out / 'classes', *sources]
    (out / 'javac.args').write_text('\n'.join(json.dumps(str(a).replace('\\', '/')) for a in javac), encoding='utf-8')
    run(java / ('javac' + exe), '@' + str(out / 'javac.args'))
    # Existing release dependencies include classes from XZ and reference the
    # optional runtime/UI APIs; these are classpath inputs, not test behavior.
    dependencies = [ROOT / 'build/release/ui-api']
    dependencies += sorted((ROOT / 'vendor').glob('**/*xposed*.jar'))
    d8 = ['-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28', '--lib', android]
    for dependency in dependencies:
        if dependency.exists():
            d8 += ['--classpath', dependency]
    d8 += ['--output', out / 'dex', ROOT / 'vendor/onnxruntime/classes.jar', *sorted((out / 'classes').rglob('*.class'))]
    (out / 'd8.args').write_text('\n'.join(json.dumps(str(a).replace('\\', '/')) for a in d8), encoding='utf-8')
    run(java / ('java' + exe), '@' + str(out / 'd8.args'))
    families = ['stereo', 'shader/base']
    if args.video:
        families.append('flyme-video')
    if args.ordinary:
        families += ['gradient', 'shader/gradient']
    for family in families:
        shutil.copytree(ROOT / 'app/assets' / family, out / 'assets' / family, dirs_exist_ok=True)
    decorator = Path('shader/photo/aod/lensPhoto/lens_decorator.png')
    (out / 'assets' / decorator).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / 'app/assets' / decorator, out / 'assets' / decorator)
    manifest = out / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<uses-feature android:glEsVersion="0x00030000" android:required="true"/>
<application android:label="OpenAlive Stereo lifecycle test" android:debuggable="true"/>
<instrumentation android:name="org.aliveclean.StereoLifecycleInstrumentation" android:targetPackage="{PACKAGE}"/>
</manifest>''', encoding='utf-8')
    run(bt / ('aapt2' + exe), 'link', '-o', out / 'base.apk', '--manifest', manifest, '-I', android)
    with zipfile.ZipFile(out / 'base.apk') as base, zipfile.ZipFile(out / 'unsigned.apk', 'w') as archive:
        for entry in base.infolist():
            write_entry(archive, entry.filename, base.read(entry))
        for dex in sorted((out / 'dex').glob('*.dex')):
            write_entry(archive, dex.name, dex.read_bytes())
        for asset in sorted((out / 'assets').rglob('*')):
            if asset.is_file():
                write_entry(archive, 'assets/' + asset.relative_to(out / 'assets').as_posix(), asset.read_bytes())
    run(bt / ('zipalign' + exe), '-f', '-p', RESOURCE_ALIGNMENT, out / 'unsigned.apk', out / 'aligned.apk')
    key = out / 'test.jks'
    if not key.exists():
        run(java / ('keytool' + exe), '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
            '-alias', 'test', '-dname', 'CN=OpenAlive Stereo lifecycle test', '-keyalg', 'RSA', '-validity', '10000')
    apk = out / 'test.apk'
    run(java / ('java' + exe), '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-pass', 'pass:android', '--ks-key-alias', 'test', '--out', apk, out / 'aligned.apk')
    run(java / ('java' + exe), '-jar', bt / 'lib/apksigner.jar', 'verify', apk)
    report = {'apk': str(apk), 'package': PACKAGE, 'component': COMPONENT,
              'video': args.video, 'ordinary': args.ordinary,
              'compiled_sources': source_hashes}
    (out / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'Built {apk}')
    if args.build_only:
        print('Build only: no ADB commands executed.')
        return
    adb_command = [sdk / ('platform-tools/adb' + exe), '-s', args.serial]
    def adb(*command, **kwargs):
        return run(*adb_command, *command, **kwargs)
    adb('install', '-r', '--no-incremental', apk)
    try:
        try:
            result = adb('shell', 'am', 'instrument', '--no-hidden-api-checks', '-w',
                         '-e', 'video', str(args.video).lower(), '-e', 'ordinary', str(args.ordinary).lower(), COMPONENT,
                         capture_output=True, text=True, timeout=180)
        except subprocess.TimeoutExpired as error:
            def decoded(value):
                return value.decode('utf-8', errors='replace') if isinstance(value, bytes) else value or ''
            partial = decoded(error.stdout) + decoded(error.stderr)
            (out / 'result.txt').write_text(partial + '\nHOST_TIMEOUT after 180 seconds\n', encoding='utf-8')
            print(partial)
            # Killing the adb client leaves instrumentation alive on Android.
            # Stop only this disposable, explicitly selected test package.
            adb('shell', 'am', 'force-stop', PACKAGE, timeout=15)
            raise RuntimeError('Stereo lifecycle timed out; test package stopped, partial progress in build/stereo-lifecycle/result.txt') from error
        (out / 'result.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
        print(result.stdout)
        with (out / 'stereo-lifecycle.txt').open('wb') as trace:
            adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/stereo-lifecycle.txt', stdout=trace)
        if 'STEREO_LIFECYCLE_OK' not in result.stdout:
            raise RuntimeError('Stereo lifecycle test failed; see build/stereo-lifecycle/result.txt')
    finally:
        if not args.keep_installed:
            adb('uninstall', PACKAGE)


if __name__ == '__main__':
    main()
