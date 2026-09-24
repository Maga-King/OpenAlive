"""Isolated original font + installed ColorOS clock codec/cache test, no lockscreen writes."""
from pathlib import Path
import os, subprocess, sys, zipfile, shutil, queue, threading, time
from apk_layout import RESOURCE_ALIGNMENT, write_entry

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home()/'AppData/Local/Android/Sdk')))
BT = SDK/'build-tools/35.0.0'
JAVA = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17'))/'bin'
ANDROID = SDK/'platforms/android-35/android.jar'
OUT = ROOT/'build/native-clock-test'
for name in ['classes', 'dex']:
    target = (OUT/name).resolve()
    assert target.is_relative_to((ROOT/'build').resolve())
    if target.exists(): shutil.rmtree(target)
    target.mkdir(parents=True)
ADB = [str(SDK/'platform-tools/adb.exe'), '-s', sys.argv[1]]
def run(*args, **kwargs):
    # adb emits UTF-8, independently of the Windows console code page.
    if kwargs.get('text'):
        kwargs.setdefault('encoding', 'utf-8')
        kwargs.setdefault('errors', 'replace')
    return subprocess.run(list(map(str,args)), check=True, **kwargs)

def instrument_foreground(component, timeout=90):
    # Instrumentation restarts the test process, so opening a window before it
    # starts does not bypass HyperOS's background launch gate. Wait for the
    # test's explicit launch stage, then open this disposable activity via adb.
    process=subprocess.Popen([*ADB,'shell','am','instrument','-w',component],
            stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
    lines=queue.Queue()
    def read_output():
        for line in process.stdout: lines.put(line)
        lines.put(None)
    threading.Thread(target=read_output,daemon=True).start()
    collected=[]; deadline=time.monotonic()+timeout; launched=False
    try:
        while True:
            remaining=deadline-time.monotonic()
            if remaining<=0: raise subprocess.TimeoutExpired(process.args,timeout)
            try: line=lines.get(timeout=remaining)
            except queue.Empty: raise subprocess.TimeoutExpired(process.args,timeout)
            if line is None: break
            collected.append(line)
            if not launched and 'test activity launch' in line:
                launched=True
                run(*ADB,'shell','am','start','-W','-n',
                        'org.aliveclean.nativeclocktest/org.aliveclean.ClockTestActivity',
                        capture_output=True,text=True,timeout=min(30,max(1,deadline-time.monotonic())))
        code=process.wait(timeout=max(1,deadline-time.monotonic()))
        if code: raise subprocess.CalledProcessError(code,process.args,output=''.join(collected))
        return subprocess.CompletedProcess(process.args,code,''.join(collected),'')
    finally:
        if process.poll() is None: process.kill(); process.wait(timeout=10)
        process.stdout.close()
(OUT/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.aliveclean.nativeclocktest">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<queries><package android:name="org.aliveclean"/><package android:name="com.oplus.keyguard.personality.clocks"/><package android:name="com.oplus.keyguard.clock.base"/><package android:name="com.oplus.wallpapers"/><package android:name="com.oplus.keyguard.style.widgets"/><package android:name="com.nearme.instant.platform"/></queries>
<application android:label="OpenAlive clock test" android:debuggable="true"><activity android:name="org.aliveclean.ClockTestActivity" android:exported="true" android:hardwareAccelerated="false" android:theme="@android:style/Theme.Material.NoActionBar"/></application>
<instrumentation android:name="org.aliveclean.NativeClockInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.AliveTemplateInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.OriginalHyperOsClockInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeClockTransportInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.OriginalFlymeClockInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeClockEditorInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeClockPanelInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeClockRetentionInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeClockWidgetsInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeHyperOsTransportInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.HyperOsWrappedInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.FlymeArtworkInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeClockBootInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
<instrumentation android:name="org.aliveclean.NativeClockEditBoxInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/>
</manifest>''', encoding='utf-8')
if '--release-host' in sys.argv or '--effects' in sys.argv:
    manifest=(OUT/'AndroidManifest.xml').read_text(encoding='utf8').replace('android:debuggable="true"','android:debuggable="false"')
    manifest=manifest.replace('android:targetSdkVersion="35"','android:targetSdkVersion="37"')
    manifest=manifest.replace('</application>','<activity android:name="org.aliveclean.NativeClockReleaseProbe" android:exported="true" android:theme="@android:style/Theme.Material.NoActionBar"/></application>')
    manifest=manifest.replace('</application>','<activity android:name="org.aliveclean.NativeClockEffectsProbe" android:exported="true" android:theme="@android:style/Theme.Material.NoActionBar"/></application>')
    manifest=manifest.replace('</manifest>','<instrumentation android:name="org.aliveclean.NativeClockEffectsInstrumentation" android:targetPackage="org.aliveclean.nativeclocktest"/></manifest>')
    (OUT/'AndroidManifest.xml').write_text(manifest,encoding='utf8')
run(BT/'aapt2.exe','link','-o',OUT/'base.apk','--manifest',OUT/'AndroidManifest.xml','-I',ANDROID)
sources = [ROOT/'app/java/org/aliveclean'/name for name in ('NativeClockFonts.java', 'FlymeAliveClockTemplate.java', 'FlymeAliveAodFace.java', 'OfficialUi.java', 'OfficialClockFace.java', 'ClockUpdates.java', 'NativeClockEditSession.java', 'NativeClockStylePanel.java', 'NativeClockWidgetBridge.java', 'NativeClockGeometry.java', 'NativeClockAvailability.java', 'NativeClockFaces.java', 'NativeOriginalClockPlugin.java', 'NativeFlymeClockPlugin.java', 'NativeClockProvider.java', 'NativeClockInflater.java', 'NativeClockEditor.java', 'NativeHyperOsFace.java', 'NativeHyperOsStyles.java', 'NativeHyperOsClockPlugin.java', 'NativeClockRuntime.java', 'OfficialHyperOsUi.java', 'OfficialHyperOsClockFace.java')]
sources.append(ROOT/'app/java/org/aliveclean/NativeFlymePerspectiveFace.java')
sources.append(ROOT/'app/java/org/aliveclean/NativeClockEditBox.java')
sources.append(ROOT/'app/java/org/aliveclean/NativeClockLoadState.java')
sources.append(ROOT/'app/java/org/aliveclean/NativeClockWidgetRecovery.java')
sources.append(ROOT/'app/java/org/aliveclean/NativeClockSceneTransition.java')
sources.append(ROOT/'app/java/org/aliveclean/NativeClockMaterial.java')
sources.append(ROOT/'app/java/org/aliveclean/NativeClockMaterialBootstrap.java')
sources.append(ROOT/'app/java/miui/util/font/MultiLangHelper.java')
sources.append(ROOT/'app/java/org/aliveclean/NativeFlymeArtworkPlugin.java')
sources += [ROOT/'app/java/org/aliveclean'/name for name in ('AodClockHost.java','AodClockView.java','AodCalendar.java','AodWidgetSpace.java','AodNotificationBounds.java')]
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(ANDROID)+os.pathsep+str(BT/'core-lambda-stubs.jar'),'-d',OUT/'classes',*sources,*sorted((ROOT/'tests/nativeclock').rglob('*.java')))
run(JAVA/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',ANDROID,'--output',OUT/'dex',*sorted((OUT/'classes').rglob('*.class')))
with zipfile.ZipFile(OUT/'base.apk') as base, zipfile.ZipFile(OUT/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as dst:
    for entry in base.infolist(): write_entry(dst,entry.filename,base.read(entry))
    dst.write(OUT/'dex/classes.dex','classes.dex')
    dst.write(ROOT/'vendor/flyme/editor-ui.dex','classes2.dex')
    dst.write(ROOT/'app/assets/clock/horizontal.otf','assets/clock/horizontal.otf')
    for name in ('editor-ui.apk','editor-ui.sha256'):
        dst.write(ROOT/'app/assets/ui'/name,'assets/ui/'+name)
    for font in (ROOT/'app/assets/native-clock').rglob('*'):
        if font.is_file(): dst.write(font,'assets/'+font.relative_to(ROOT/'app/assets').as_posix())
    if '--hyperos-wrapped' in sys.argv or '--flyme-artwork' in sys.argv:
        dst.write(ROOT/'research/phone-platform/com.oplus.keyguard.personality.clocks/0-KeyguardPersonalityClocks.apk','assets/test-clock-host.apk')
run(BT/'zipalign.exe','-f',RESOURCE_ALIGNMENT,OUT/'unsigned.apk',OUT/'aligned.apk')
run(JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--alignment-preserved','true','--ks',ROOT/'local/development.jks','--ks-pass','pass:android','--ks-key-alias','development','--out',OUT/'test.apk',OUT/'aligned.apk')
run(*ADB,'install','-r',OUT/'test.apk')
try:
    if '--effects' in sys.argv:
        probe=run(*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockEffectsInstrumentation',capture_output=True,text=True,timeout=180)
        print(probe.stdout)
        deadline=time.monotonic()+180
        result=''
        while time.monotonic()<deadline:
            response=subprocess.run([*ADB,'shell','su','-c','cat /data/user/0/org.aliveclean.nativeclocktest/files/effects.txt'],capture_output=True,text=True,encoding='utf8',errors='replace')
            if response.returncode==0 and response.stdout.strip(): result=response.stdout;break
            time.sleep(1)
        (OUT/(sys.argv[1]+'-effects.txt')).write_text(result,encoding='utf8')
        print(result)
        for index in (0,3,17,'mid','soft','wallpaper-soft'):
            data=subprocess.run([*ADB,'exec-out','su','-c',f'cat /data/user/0/org.aliveclean.nativeclocktest/files/clock-effects-{index}.png'],capture_output=True)
            if data.returncode==0:(OUT/f'{sys.argv[1]}-clock-effects-{index}.png').write_bytes(data.stdout)
        assert 'NATIVE_EFFECTS_OK styles=21 transitions=42 glass=21' in result,result
        sys.exit(0)
    if '--release-host' in sys.argv:
        run(*ADB,'shell','am','start','-W','-n','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockReleaseProbe')
        deadline=time.monotonic()+120
        result=''
        while time.monotonic()<deadline:
            response=subprocess.run([*ADB,'shell','su','-c','cat /data/user/0/org.aliveclean.nativeclocktest/files/release-host.txt'],capture_output=True,text=True,encoding='utf8',errors='replace')
            if response.returncode==0 and response.stdout.strip():
                result=response.stdout;break
            time.sleep(1)
        (OUT/(sys.argv[1]+'-release-host.txt')).write_text(result,encoding='utf8')
        print(result)
        assert 'NATIVE_RELEASE_HOST_OK styles=21 scenes=84 final_write_rejected=true' in result,result
        sys.exit(0)
    if '--editbox' in sys.argv:
        result=run(*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockEditBoxInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-editbox.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
        print(result.stdout)
        assert 'NATIVE_EDITBOX_OK' in result.stdout,result.stdout
        raster=run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/native-clock-editbox.png',capture_output=True)
        (OUT/(sys.argv[1]+'-editbox.png')).write_bytes(raster.stdout)
    if '--original-only' not in sys.argv:
        result = run(*ADB,'shell','am','instrument','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-result.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
        print(result.stdout)
        image = subprocess.run([*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/fonts.png'],capture_output=True)
        if image.returncode == 0: (OUT/(sys.argv[1]+'-fonts.png')).write_bytes(image.stdout)
        assert 'NATIVE_CLOCK_FONTS_OK' in result.stdout, result.stdout
    template = run(*ADB,'shell','am','instrument','-w','org.aliveclean.nativeclocktest/org.aliveclean.AliveTemplateInstrumentation',capture_output=True,text=True,timeout=90)
    (OUT/(sys.argv[1]+'-templates.txt')).write_text(template.stdout+template.stderr,encoding='utf-8')
    print(template.stdout)
    assert 'ALIVE_TEMPLATE_OK' in template.stdout, template.stdout
    raster = run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/alive-templates.png',capture_output=True)
    (OUT/(sys.argv[1]+'-templates.png')).write_bytes(raster.stdout)
    if '--boot' in sys.argv:
        boot = run(*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockBootInstrumentation',capture_output=True,text=True,timeout=60)
        (OUT/(sys.argv[1]+'-boot.txt')).write_text(boot.stdout+boot.stderr,encoding='utf8')
        print(boot.stdout)
        assert 'NATIVE_CLOCK_BOOT_OK' in boot.stdout, boot.stdout
    if '--hyperos-wrapped' in sys.argv:
        wrapped=instrument_foreground('org.aliveclean.nativeclocktest/org.aliveclean.HyperOsWrappedInstrumentation', timeout=180)
        (OUT/(sys.argv[1]+'-wrapped.txt')).write_text(wrapped.stdout,encoding='utf8')
        print(wrapped.stdout)
        images=run(*ADB,'shell','run-as','org.aliveclean.nativeclocktest','ls','files',capture_output=True,text=True).stdout.splitlines()
        for name in images:
            if name.startswith('hyperos-') and name.endswith('.png'):
                data=run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/'+name,capture_output=True)
                (OUT/(sys.argv[1]+'-'+name)).write_bytes(data.stdout)
        assert 'HYPEROS_WRAPPED_OK' in wrapped.stdout, wrapped.stdout
    if '--flyme-artwork' in sys.argv:
        artwork=instrument_foreground('org.aliveclean.nativeclocktest/org.aliveclean.FlymeArtworkInstrumentation', timeout=120)
        (OUT/(sys.argv[1]+'-artwork.txt')).write_text(artwork.stdout,encoding='utf8')
        print(artwork.stdout)
        images=run(*ADB,'shell','run-as','org.aliveclean.nativeclocktest','ls','files',capture_output=True,text=True).stdout.splitlines()
        for name in images:
            if name.startswith('flyme-perspective-') and name.endswith('.png'):
                data=run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/'+name,capture_output=True)
                (OUT/(sys.argv[1]+'-'+name)).write_bytes(data.stdout)
        assert 'FLYME_ARTWORK_OK' in artwork.stdout, artwork.stdout
    if '--transport' in sys.argv:
        transport = run(*ADB,'shell','am','instrument','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockTransportInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-transport.txt')).write_text(transport.stdout+transport.stderr,encoding='utf-8')
        print(transport.stdout)
        assert 'NATIVE_TRANSPORT_OK' in transport.stdout, transport.stdout
    if '--flyme' in sys.argv:
        flyme = run(*ADB,'shell','am','instrument','-w','org.aliveclean.nativeclocktest/org.aliveclean.OriginalFlymeClockInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-flyme.txt')).write_text(flyme.stdout+flyme.stderr,encoding='utf-8')
        print(flyme.stdout)
        assert 'ORIGINAL_FLYME_OK' in flyme.stdout, flyme.stdout
        raster = run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/flyme-originals.png',capture_output=True)
        (OUT/(sys.argv[1]+'-flyme.png')).write_bytes(raster.stdout)
    if '--editor' in sys.argv:
        editor = run(*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockEditorInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-editor.txt')).write_text(editor.stdout+editor.stderr,encoding='utf-8')
        print(editor.stdout)
        assert 'NATIVE_EDITOR_OK' in editor.stdout, editor.stdout
    if '--panel' in sys.argv:
        panel = run(*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockPanelInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-panel.txt')).write_text(panel.stdout+panel.stderr,encoding='utf-8')
        print(panel.stdout)
        assert 'NATIVE_PANEL_OK' in panel.stdout, panel.stdout
        raster = run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/native-clock-panel.png',capture_output=True)
        (OUT/(sys.argv[1]+'-panel.png')).write_bytes(raster.stdout)
    if '--retention' in sys.argv:
        retention = run(*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockRetentionInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-retention.txt')).write_text(retention.stdout+retention.stderr,encoding='utf-8')
        print(retention.stdout)
        assert 'NATIVE_RETENTION_OK' in retention.stdout, retention.stdout
    if '--widgets' in sys.argv:
        widgets = run(*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeClockWidgetsInstrumentation',capture_output=True,text=True,timeout=90)
        (OUT/(sys.argv[1]+'-widgets.txt')).write_text(widgets.stdout+widgets.stderr,encoding='utf-8')
        print(widgets.stdout)
        assert 'NATIVE_WIDGETS_OK' in widgets.stdout, widgets.stdout
    if '--hyperos-native' in sys.argv:
        native = instrument_foreground('org.aliveclean.nativeclocktest/org.aliveclean.NativeHyperOsTransportInstrumentation')
        (OUT/(sys.argv[1]+'-hyperos-native.txt')).write_text(native.stdout+native.stderr,encoding='utf-8')
        print(native.stdout)
        assert 'NATIVE_HYPEROS_OK' in native.stdout, native.stdout
        for orientation in ['vertical','horizontal']:
            raster=run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat',f'files/hyperos-classic-{orientation}.png',capture_output=True)
            (OUT/(sys.argv[1]+f'-hyperos-classic-{orientation}.png')).write_bytes(raster.stdout)
    if '--hyperos' in sys.argv:
        original = instrument_foreground('org.aliveclean.nativeclocktest/org.aliveclean.OriginalHyperOsClockInstrumentation')
        (OUT/(sys.argv[1]+'-hyperos.txt')).write_text(original.stdout+original.stderr,encoding='utf-8')
        print(original.stdout)
        raster = subprocess.run([*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/hyperos-originals-0.png'],capture_output=True)
        if raster.returncode == 0: (OUT/(sys.argv[1]+'-hyperos.png')).write_bytes(raster.stdout)
        for scene in range(1,5):
            raster = run(*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat',f'files/hyperos-originals-{scene}.png',capture_output=True)
            (OUT/(sys.argv[1]+f'-hyperos-{scene}.png')).write_bytes(raster.stdout)
        assert 'ORIGINAL_VIEW_BLOCKED' not in original.stdout, original.stdout
        assert original.stdout.count('ORIGINAL_VIEW_DRAWN ') == 45, original.stdout
finally:
    run(*ADB,'uninstall','org.aliveclean.nativeclocktest')
