#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
make seed
# Only generated scenario JSON and index participate, not transcripts/README.md.
output=$(mktemp -d "$PWD/target/parity.XXXXXX")
mkdir "$output/expected" "$output/actual"
python3 parity/full_capture.py "$output/actual"
python3 - "$output" <<'PY'
import shutil
import sys
from pathlib import Path
sys.path.insert(0, 'parity')
from common import ROOT, capture
output = Path(sys.argv[1])
passed = 0
for name in [scenario[0] for scenario in capture.SCENARIOS] + ['index']:
    expected = ROOT / 'transcripts' / (name + '.json')
    shutil.copy2(expected, output / 'expected' / expected.name)
    same = expected.read_bytes() == (output / 'actual' / expected.name).read_bytes()
    if name != 'index':
        passed += same
        print('%-36s %s' % (name, 'PASS' if same else 'FAIL'))
print('%d/%d' % (passed, len(capture.SCENARIOS)))
PY
diff -r "$output/expected" "$output/actual"
