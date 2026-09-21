"""Build the published source/assets on Linux or Windows without a local ROM tree."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,urllib.request,zipfile
import xml.etree.ElementTree as ET
from apk_layout import RESOURCE_ALIGNMENT,write_entry,verify_apk

ROOT=Path(__file__).resolve().parents[1]
WIN=os.name=='nt';EXE='.exe' if WIN else ''
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
JAVA=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if WIN else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
BT=SDK/'build-tools/35.0.0';ANDROID=SDK/'platforms/android-35/android.jar'
OUT=ROOT/'build/release';DIST=ROOT/'dist';VENDOR=ROOT/'vendor/flyme'
for folder in ['classes','dex','generated','ui-api']:
    p=(OUT/folder).resolve()
    if not p.is_relative_to((ROOT/'build').resolve()):raise ValueError(p)
    if p.exists():shutil.rmtree(p)
    p.mkdir(parents=True)
DIST.mkdir(exist_ok=True)

def run(*args,env=None):subprocess.run([str(a) for a in args],cwd=ROOT,env=env,check=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def javac_args(name,args):
    # javac argument files avoid command length limits; paths use forward slashes.
    p=OUT/(name+'.args')
    p.write_text('\n'.join(json.dumps(str(a).replace('\\','/')) for a in args),encoding='utf8')
    run(JAVA/('javac'+EXE),'@'+str(p))

manifest=ET.parse(ROOT/'app/AndroidManifest.xml').getroot()
version=manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
apk=DIST/('OpenAlive-'+version+'-arm64-v8a.apk')

# Pin the compile-only API. It is never added to the APK.
xposed=OUT/'xposed-api-82.jar'
expected='f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'
if not xposed.exists() or sha(xposed)!=expected:
    with urllib.request.urlopen('https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar',timeout=60) as r:xposed.write_bytes(r.read())
if sha(xposed)!=expected:raise ValueError('Xposed API checksum mismatch')

for script in ['check_layout.py','check_clock_scope.py']:run(sys.executable,ROOT/'tools'/script)
if WIN:
    linux='/mnt/'+ROOT.drive[0].lower()+ROOT.as_posix()[2:]
    # Local Windows builds use the existing WSL NDK; CI builds directly on Linux.
    import shlex
    run('wsl.exe','--','bash','-lc','cd '+shlex.quote(linux)+' && export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$HOME/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang" && "$HOME/.cargo/bin/cargo" test --locked && "$HOME/.cargo/bin/cargo" build --locked --release --target aarch64-linux-android')
else:
    ndk=Path(os.environ.get('ANDROID_NDK_HOME',str(SDK/'ndk/29.0.14206865')))
    env={**os.environ,'CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER':str(ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang')}
    run('cargo','test','--locked',env=env)
    run('cargo','build','--locked','--release','--target','aarch64-linux-android',env=env)

run(BT/('aapt2'+EXE),'compile','--dir',VENDOR/'res','-o',OUT/'vendor-res.zip')
run(BT/('aapt2'+EXE),'compile','--dir',ROOT/'app/res','-o',OUT/'app-res.zip')
run(BT/('aapt2'+EXE),'link','-o',OUT/'base.apk','--manifest',ROOT/'app/AndroidManifest.xml','-I',ANDROID,
    '--auto-add-overlay','--java',OUT/'generated','-A',ROOT/'app/assets',OUT/'vendor-res.zip','-R',OUT/'app-res.zip')
javac_args('api',['-encoding','UTF-8','-source','8','-target','8','-bootclasspath',ANDROID,'-d',OUT/'ui-api',*sorted((ROOT/'tools/ui-api').rglob('*.java'))])
sources=sorted((ROOT/'app/java').rglob('*.java'))+sorted((OUT/'generated').rglob('*.java'))
javac_args('app',['-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(ANDROID)+os.pathsep+str(BT/'core-lambda-stubs.jar'),
    '-classpath',str(xposed)+os.pathsep+str(OUT/'ui-api'),'-d',OUT/'classes',*sources])
run(JAVA/('java'+EXE),'-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',ANDROID,
    '--classpath',xposed,'--classpath',OUT/'ui-api','--output',OUT/'dex',*sorted((OUT/'classes').rglob('*.class')))
native=ROOT/'target/aarch64-linux-android/release/libalive_clean.so'
with zipfile.ZipFile(OUT/'base.apk') as base,zipfile.ZipFile(OUT/'unsigned.apk','w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for entry in base.infolist():
        if not entry.filename.startswith('assets/video/'):write_entry(z,entry.filename,base.read(entry))
    z.write(OUT/'dex/classes.dex','classes.dex')
    z.write(VENDOR/'editor-ui.dex','classes2.dex')
    z.write(native,'lib/arm64-v8a/libalive_clean.so')
run(BT/('zipalign'+EXE),'-f','-p',RESOURCE_ALIGNMENT,OUT/'unsigned.apk',OUT/'aligned.apk')

key=Path(os.environ.get('OPENALIVE_KEYSTORE',str(ROOT/'local/development.jks')))
alias=os.environ.get('OPENALIVE_KEY_ALIAS','development')
env={**os.environ,'OPENALIVE_STORE_PASSWORD':os.environ.get('OPENALIVE_STORE_PASSWORD','android')}
if not key.exists():
    if 'OPENALIVE_KEYSTORE' in os.environ:raise FileNotFoundError('Configured signing key is missing')
    key.parent.mkdir(parents=True,exist_ok=True)
    run(JAVA/('keytool'+EXE),'-genkeypair','-keystore',key,'-storepass:env','OPENALIVE_STORE_PASSWORD','-keypass:env','OPENALIVE_STORE_PASSWORD',
        '-alias',alias,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=OpenAlive Development',env=env)
run(JAVA/('java'+EXE),'-jar',BT/'lib/apksigner.jar','sign','--alignment-preserved','true','--ks',key,
    '--ks-pass','env:OPENALIVE_STORE_PASSWORD','--ks-key-alias',alias,'--out',apk,OUT/'aligned.apk',env=env)
run(JAVA/('java'+EXE),'-jar',BT/'lib/apksigner.jar','verify','--verbose','--print-certs',apk)
run(BT/('zipalign'+EXE),'-c','-p',RESOURCE_ALIGNMENT,apk)
report={'version':version,'apk':apk.name,'sha256':sha(apk),'bytes':apk.stat().st_size,'resource_tables':verify_apk(apk),
        'executables':{'classes.dex':sha(OUT/'dex/classes.dex'),'classes2.dex':sha(VENDOR/'editor-ui.dex'),'libalive_clean.so':sha(native)}}
with zipfile.ZipFile(apk) as z:
    assert z.read('lib/arm64-v8a/libalive_clean.so')==native.read_bytes()
    assert z.read('classes2.dex')==(VENDOR/'editor-ui.dex').read_bytes()
    assert not any(n.startswith('assets/video/') for n in z.namelist())
(DIST/'build-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
(DIST/'SHA256SUMS').write_text(report['sha256']+'  '+apk.name+'\n',encoding='ascii')
print(json.dumps(report,indent=2))
