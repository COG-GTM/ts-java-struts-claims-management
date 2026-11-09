#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUTPUT=/tmp/parity
LOGS="$ROOT/modernized/target/side-by-side"
export PYTHONDONTWRITEBYTECODE=1
mkdir -p "$LOGS" "$OUTPUT/legacy" "$OUTPUT/modern"

# Seeding must never run against a server that already owns the file database.
python3 - <<'PY'
import socket
for port in (8080, 8081):
    with socket.socket() as connection:
        if connection.connect_ex(('localhost', port)) == 0:
            raise SystemExit('Stop the existing server on port %d before running this script' % port)
PY
test -f "$ROOT/modernized/target/claims.jar"
cd "$ROOT"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 make seed > "$LOGS/legacy-seed.log" 2>&1
make -C modernized seed > "$LOGS/modern-seed.log" 2>&1

# Independent process sessions deliberately survive script completion for the QA recording.
nohup setsid env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    PATH="/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH" make run \
    > "$LOGS/legacy.log" 2>&1 < /dev/null &
echo $! > "$LOGS/legacy.pid"
nohup setsid make -C modernized run > "$LOGS/modern.log" 2>&1 < /dev/null &
echo $! > "$LOGS/modern.pid"

# Explicit base URLs wait for login readiness and never seed or spawn another server.
python3 tools/capture/capture.py --base-url http://localhost:8080/claims --out-dir /tmp/parity/legacy
python3 tools/capture/capture.py --base-url http://localhost:8081/claims --out-dir /tmp/parity/modern
python3 - "$ROOT" <<'PY'
import importlib.util
from pathlib import Path
import sys
root = Path(sys.argv[1])
spec = importlib.util.spec_from_file_location('capture', root / 'tools/capture/capture.py')
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)
passed = 0
for scenario in capture.SCENARIOS:
    name = scenario[0]
    expected = (root / 'transcripts' / (name + '.json')).read_bytes()
    legacy = (Path('/tmp/parity/legacy') / (name + '.json')).read_bytes()
    modern = (Path('/tmp/parity/modern') / (name + '.json')).read_bytes()
    match = legacy == modern == expected
    passed += match
    print('%-36s %s' % (name, 'MATCH' if match else 'DIFFER'))
print('%d/22 byte-identical' % passed)
PY
diff -r "$OUTPUT/legacy" "$OUTPUT/modern"
# The README documents the fixtures but is not generated capture output.
diff -r --exclude=README.md "$ROOT/transcripts" "$OUTPUT/legacy"
diff -r --exclude=README.md "$ROOT/transcripts" "$OUTPUT/modern"
echo 'Both applications left RUNNING: legacy :8080/claims, modern :8081/claims'
echo "Logs and launcher PIDs: $LOGS"
