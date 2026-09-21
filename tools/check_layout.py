"""Offline display-coordinate checks; does not contact a device."""
from pathlib import Path
import subprocess,os
root=Path(__file__).resolve().parents[1]
jdk=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if os.name=='nt' else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
exe='.exe' if os.name=='nt' else ''
out=root/'build/layout-check';out.mkdir(parents=True,exist_ok=True)
sources=[root/'app/java/org/aliveclean'/f'{name}.java' for name in ['AodRegion','SceneState','AodMotionWindow','AodTransitionWindow']]+list((root/'tests/host').rglob('*.java'))
subprocess.run([str(jdk/('javac'+exe)),'-encoding','UTF-8','-d',str(out),*map(str,sources)],check=True)
for name in ['AodRegionTest','SceneStateTest','AodMotionWindowTest','AodTransitionWindowTest']:
    subprocess.run([str(jdk/('java'+exe)),'-cp',str(out),'org.aliveclean.'+name],check=True)
