"""Compile production gradient sources/shaders into a disposable GPU test APK."""
from pathlib import Path
import argparse, os, shutil, subprocess, zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.aliveclean.gradientcheck'


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Only this explicitly selected device is used')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'AppData/Local/Android/Sdk'))
    java = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17')) / 'bin'
    exe = '.exe' if os.name == 'nt' else ''
    bt = sdk / 'build-tools/35.0.0'
    android = sdk / 'platforms/android-35/android.jar'
    out = ROOT / 'build/gradient-render'
    for name in ['classes', 'dex', 'assets']:
        (out / name).mkdir(parents=True, exist_ok=True)
    for family in ['gradient', 'shader/gradient', 'shader/base']:
        shutil.copytree(ROOT / 'app/assets' / family, out / 'assets' / family, dirs_exist_ok=True)
    manifest = out / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<application android:label="OpenAlive gradient test" android:debuggable="true"/>
<instrumentation android:name="org.aliveclean.GradientSurfaceInstrumentation" android:targetPackage="{PACKAGE}"/>
</manifest>''', encoding='utf8')
    run(bt / ('aapt2' + exe), 'link', '-o', out / 'base.apk', '--manifest', manifest, '-I', android, '-A', out / 'assets')
    app = ROOT / 'app/java/org/aliveclean'
    sources = [app / (name + '.java') for name in
               ['AssetGl', 'WallpaperMotion', 'WallpaperRenderer', 'GradientMotion', 'GradientSpline', 'GradientRenderer']]
    sources.append(ROOT / 'tests/gradient/org/aliveclean/GradientSurfaceInstrumentation.java')
    # The renderer interface's unused static factory also names the other families.
    # Compile against existing release classes; the test instantiates GradientRenderer directly.
    run(java / ('javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-bootclasspath', str(android) + os.pathsep + str(bt / 'core-lambda-stubs.jar'),
        '-classpath', ROOT / 'build/release/classes', '-d', out / 'classes', *sources)
    run(java / ('java' + exe), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28',
        '--lib', android, '--classpath', ROOT / 'build/release/classes', '--output', out / 'dex',
        *sorted((out / 'classes').rglob('*.class')))
    with zipfile.ZipFile(out / 'base.apk') as base, zipfile.ZipFile(out / 'unsigned.apk', 'w') as archive:
        for entry in base.infolist():
            archive.writestr(entry.filename, base.read(entry))
        archive.write(out / 'dex/classes.dex', 'classes.dex')
    run(bt / ('zipalign' + exe), '-f', '4', out / 'unsigned.apk', out / 'aligned.apk')
    key = out / 'test.jks'
    if not key.exists():
        run(java / ('keytool' + exe), '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
            '-alias', 'test', '-dname', 'CN=OpenAlive gradient test', '-keyalg', 'RSA', '-validity', '10000')
    apk = out / 'test.apk'
    run(java / ('java' + exe), '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-pass', 'pass:android', '--ks-key-alias', 'test', '--out', apk, out / 'aligned.apk')
    adb = [sdk / ('platform-tools/adb' + exe), '-s', args.serial]
    run(*adb, 'install', '-r', apk)
    try:
        result = run(*adb, 'shell', 'am', 'instrument', '-w', PACKAGE + '/org.aliveclean.GradientSurfaceInstrumentation',
                     capture_output=True, text=True, timeout=120)
        (out / 'result.txt').write_text(result.stdout + result.stderr, encoding='utf8')
        print(result.stdout)
        listing = run(*adb, 'shell', 'run-as', PACKAGE, 'ls', 'files', capture_output=True, text=True)
        for name in listing.stdout.split():
            if name.startswith('gradient-') and name.endswith('.png'):
                with (out / name).open('wb') as image:
                    run(*adb, 'exec-out', 'run-as', PACKAGE, 'cat', 'files/' + name, stdout=image)
        if 'GRADIENT_RENDER_OK' not in result.stdout:
            raise RuntimeError('GPU test failed; see build/gradient-render/result.txt')
    finally:
        run(*adb, 'uninstall', PACKAGE)


if __name__ == '__main__':
    main()
