"""Exercise the installed production Vivo editor, restoring its drafts afterwards."""
from pathlib import Path
import argparse,json,os,subprocess,zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);args=p.parse_args()
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
JAVA=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17'))/'bin'
BT=SDK/'build-tools/35.0.0';ANDROID=SDK/'platforms/android-35/android.jar'
OUT=ROOT/'build/vivo-editor-flow';OUT.mkdir(exist_ok=True)
for name in ['classes','dex']:(OUT/name).mkdir(exist_ok=True)
PACKAGE='org.aliveclean.editorflow'
def run(*cmd,**kw):return subprocess.run(list(map(str,cmd)),check=True,**kw)
def adb(*cmd,**kw):return run(SDK/'platform-tools/adb.exe','-s',args.serial,*cmd,**kw)
(OUT/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+PACKAGE+'"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/><application/><instrumentation android:name="org.aliveclean.VivoEditorFlowProbe" android:targetPackage="org.aliveclean"/></manifest>',encoding='utf8')
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',ANDROID,'-classpath',str(ROOT/'build/release/classes')+os.pathsep+str(BT/'core-lambda-stubs.jar'),'-d',OUT/'classes',ROOT/'tests/vivo/VivoEditorFlowProbe.java')
run(JAVA/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',ANDROID,'--classpath',ROOT/'build/release/classes','--output',OUT/'dex',*sorted((OUT/'classes').rglob('*.class')))
run(BT/'aapt2.exe','link','-o',OUT/'base.apk','--manifest',OUT/'AndroidManifest.xml','-I',ANDROID)
with zipfile.ZipFile(OUT/'base.apk') as src,zipfile.ZipFile(OUT/'unsigned.apk','w') as dst:
    for entry in src.infolist():dst.writestr(entry.filename,src.read(entry))
    dst.write(OUT/'dex/classes.dex','classes.dex')
run(BT/'zipalign.exe','-f','-p','4096',OUT/'unsigned.apk',OUT/'aligned.apk')
signing=json.loads((Path.home()/'.openalive-signing/signing.json').read_text(encoding='utf8'))
env={**os.environ,'OPENALIVE_PROBE_PASSWORD':signing['password']}
run(JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',signing['keystore'],'--ks-pass','env:OPENALIVE_PROBE_PASSWORD','--ks-key-alias',signing['alias'],'--out',OUT/'probe.apk',OUT/'aligned.apk',env=env)
adb('install','-r','--no-incremental',OUT/'probe.apk')
try:
    r=adb('shell','am','instrument','--no-hidden-api-checks','-w',PACKAGE+'/org.aliveclean.VivoEditorFlowProbe',capture_output=True,text=True,encoding='utf8',timeout=100)
    (OUT/'result.txt').write_text(r.stdout+r.stderr,encoding='utf8');print(r.stdout)
    if 'VIVO_EDITOR_FLOW_OK' not in r.stdout:raise RuntimeError('Editor flow failed; see build/vivo-editor-flow/result.txt')
finally:adb('uninstall',PACKAGE)
