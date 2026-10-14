"""Check proportional desktop motion without a device or Android runtime."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if os.name == 'nt' else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'

def executable(name):
    path = JAVA / (name + ('.exe' if os.name == 'nt' else ''))
    return str(path) if path.is_file() else shutil.which(name) or name

with tempfile.TemporaryDirectory(prefix='openalive-desktop-') as folder:
    subprocess.run([executable('javac'), '-encoding', 'UTF-8', '-d', folder,
                    str(ROOT / 'app/java/org/aliveclean/XiaomiDesktopMotion.java'),
                    str(ROOT / 'tests/desktop/org/aliveclean/XiaomiDesktopMotionTest.java')], check=True)
    subprocess.run([executable('java'), '-cp', folder, 'org.aliveclean.XiaomiDesktopMotionTest'], check=True)
