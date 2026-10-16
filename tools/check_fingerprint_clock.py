"""Check visual suppression lifetime independently of native fingerprint authorization."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if os.name == 'nt'
                          else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'
EXE = '.exe' if os.name == 'nt' else ''
OUT = ROOT / 'build/fingerprint-clock-host'
OUT.mkdir(parents=True, exist_ok=True)
sources = [ROOT / 'app/java/org/aliveclean/FingerprintClockAttempt.java',
           *sorted((ROOT / 'tests/fingerprintclock').rglob('*.java'))]
subprocess.run([str(JDK / ('javac' + EXE)), '-encoding', 'UTF-8', '-d', str(OUT),
                *map(str, sources)], check=True)
subprocess.run([str(JDK / ('java' + EXE)), '-cp', str(OUT),
                'org.aliveclean.FingerprintClockAttemptTest'], check=True)
