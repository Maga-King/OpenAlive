"""Verify real system previews after an injected primary-route failure; never apply wallpaper."""
from pathlib import Path
import argparse, os, re, subprocess, time, zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial', required=True)
a = p.parse_args()
sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'AppData/Local/Android/Sdk'))
java = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17')) / 'bin'
bt = sdk / 'build-tools/35.0.0'
android = sdk / 'platforms/android-35/android.jar'
out = ROOT / 'build/picker-device'
pkg = 'org.aliveclean.pickerprobe'
exe = '.exe' if os.name == 'nt' else ''
for part in ['classes', 'dex']:
    (out / part).mkdir(parents=True, exist_ok=True)

def run(*cmd, **kw):
    return subprocess.run(list(map(str, cmd)), check=True, **kw)

def adb(*cmd, **kw):
    return run(sdk / ('platform-tools/adb' + exe), '-s', a.serial, *cmd, **kw)

def read(*cmd):
    return adb(*cmd, capture_output=True, text=True, encoding='utf8').stdout

(out / 'AndroidManifest.xml').write_text(
    f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{pkg}">'
    '<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>'
    '<queries><intent><action android:name="android.service.wallpaper.CHANGE_LIVE_WALLPAPER"/></intent>'
    '<package android:name="com.android.wallpaper.livepicker"/></queries>'
    '<application android:debuggable="true" android:theme="@android:style/Theme.Material.Light.NoActionBar">'
    '<activity android:name="org.aliveclean.PickerProbeActivity" android:exported="true"/>'
    '</application></manifest>', encoding='utf8')
run(java / ('javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', android, '-d', out / 'classes', ROOT / 'app/java/org/aliveclean/WallpaperPicker.java',
    ROOT / 'tests/picker/PickerProbeActivity.java')
run(java / ('java' + exe), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28',
    '--lib', android, '--output', out / 'dex', *sorted((out / 'classes').rglob('*.class')))
run(bt / ('aapt2' + exe), 'link', '-o', out / 'base.apk', '--manifest', out / 'AndroidManifest.xml', '-I', android)
with zipfile.ZipFile(out / 'base.apk') as src, zipfile.ZipFile(out / 'unsigned.apk', 'w') as dst:
    for entry in src.infolist():
        dst.writestr(entry.filename, src.read(entry))
    dst.write(out / 'dex/classes.dex', 'classes.dex')
run(bt / ('zipalign' + exe), '-f', '4', out / 'unsigned.apk', out / 'aligned.apk')
run(java / ('java' + exe), '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', ROOT / 'local/development.jks',
    '--ks-pass', 'pass:android', '--ks-key-alias', 'development', '--out', out / 'probe.apk', out / 'aligned.apk')
before = read('shell', 'dumpsys', 'wallpaper')
adb('install', '-r', out / 'probe.apk')
try:
    for fallback in [False, True]:
        name = 'fallback' if fallback else 'primary'
        adb('shell', 'am', 'start', '-W', '-n', pkg + '/org.aliveclean.PickerProbeActivity',
            '--ez', 'fallback', str(fallback).lower())
        for _ in range(20):
            service = read('shell', 'dumpsys', 'activity', 'service', 'org.aliveclean/.CleanWallpaper')
            bindings = read('shell', 'dumpsys', 'activity', 'services', 'org.aliveclean/.CleanWallpaper')
            if 'mReportedVisible=true' in service and 'mSurfaceCreated=true' in service and 'com.android.wallpaper.livepicker' in bindings:
                break
            time.sleep(.25)
        (out / (name + '-engine.txt')).write_text(service, encoding='utf8')
        (out / (name + '-bindings.txt')).write_text(bindings, encoding='utf8')
        result = read('exec-out', 'run-as', pkg, 'cat', 'files/result.txt')
        (out / (name + '.txt')).write_text(result, encoding='utf8')
        assert 'PREVIEW_STARTED' in result and 'FAILED' not in result, result
        assert 'mReportedVisible=true' in service and 'mSurfaceCreated=true' in service and 'com.android.wallpaper.livepicker' in bindings, 'System picker did not bind a visible OpenAlive engine'
        top = read('shell', 'dumpsys', 'activity', 'activities')
        assert re.search(r'(?:mResumedActivity:|topResumedActivity=).*com.android.wallpaper.livepicker', top), 'Unexpected foreground activity'
        time.sleep(1)
        with (out / (name + '.png')).open('wb') as image:
            adb('exec-out', 'screencap', '-p', stdout=image)
        adb('shell', 'input', 'keyevent', '4')
        for _ in range(30):
            result = read('exec-out', 'run-as', pkg, 'cat', 'files/result.txt')
            if 'RETURNED result=' in result:
                break
            time.sleep(.2)
        (out / (name + '.txt')).write_text(result, encoding='utf8')
        assert 'RETURNED result=0' in result, 'Cancellation not returned to caller: ' + result
        print(name.upper() + '_PREVIEW_OK')
finally:
    adb('uninstall', pkg)
after = read('shell', 'dumpsys', 'wallpaper')
fields = r'mWallpaperId=\d+|mWallpaperComponent=[^\r\n]+'
assert re.findall(fields, before) == re.findall(fields, after), 'Installed wallpaper changed'
print('DEVICE_WALLPAPER_PICKER_OK')
