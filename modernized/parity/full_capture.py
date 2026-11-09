"""Own the modern server while running capture's full sequential workflow."""

import subprocess
import sys
from common import ROOT, start, stop

process = start(modern=True)
try:
    subprocess.run([sys.executable, str(ROOT / "tools/capture/capture.py"),
                    "--base-url", "http://localhost:8081/claims", "--out-dir", sys.argv[1]], check=True)
finally:
    stop(process)
