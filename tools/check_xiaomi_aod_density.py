"""Check resolution normalization without modifying any device display setting."""
from pathlib import Path
import os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_HOME',Path.home()/'AppData/Local/Android/Sdk'))
java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if os.name=='nt' else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
ext='.exe' if os.name=='nt' else '';android=sdk/'platforms/android-35/android.jar'
with tempfile.TemporaryDirectory(prefix='openalive-aod-density-') as out:
    subprocess.run([str(java/('javac'+ext)),'-encoding','UTF-8','-cp',str(android),'-d',out,str(ROOT/'app/java/org/aliveclean/XiaomiAodDensity.java'),str(ROOT/'tests/desktop/org/aliveclean/XiaomiAodDensityTest.java')],check=True)
    subprocess.run([str(java/('java'+ext)),'-cp',out+os.pathsep+str(android),'org.aliveclean.XiaomiAodDensityTest'],check=True)
