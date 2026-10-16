"""Check production unlock state and outer-hook flow with separate UI/native-worker queues.

Native body, Android scheduling and Xposed dispatch are modeled; real ROM hook
compatibility and display/transition timing still require device validation.
"""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if os.name == 'nt'
                          else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'
EXE = '.exe' if os.name == 'nt' else ''
OUT = ROOT / 'build/unlock-handoff-host'
OUT.mkdir(parents=True, exist_ok=True)
sources = [ROOT / 'app/java/org/aliveclean/ColorOsUnlockHandoff.java',
           *sorted((ROOT / 'tests/unlockhandoff').rglob('*.java'))]
subprocess.run([str(JDK / ('javac' + EXE)), '-encoding', 'UTF-8', '-d', str(OUT),
                *map(str, sources)], check=True)
subprocess.run([str(JDK / ('java' + EXE)), '-cp', str(OUT), 'org.aliveclean.UnlockHandoffTest'], check=True)
