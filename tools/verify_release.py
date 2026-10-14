"""Fail closed unless all published files equal the reviewed local build."""
from pathlib import Path
import hashlib,json

ROOT=Path(__file__).resolve().parents[1]
expected=json.loads((ROOT/'release/expected.json').read_text(encoding='utf8'))
for name,digest in expected['files'].items():
    path=ROOT/'dist'/name
    if path.name!=name:raise ValueError('Invalid artifact name')
    actual=hashlib.sha256(path.read_bytes()).hexdigest()
    if actual!=digest:raise SystemExit(f'Artifact differs from reviewed local build: {name}\nexpected {digest}\nactual   {actual}')
    print(f'EXACT_MATCH {digest}  {name}')
