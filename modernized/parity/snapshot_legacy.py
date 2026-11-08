#!/usr/bin/env python3
"""Record the stopped legacy database immediately before every stateful scenario."""

import os
import shutil
from common import (ROOT, SNAPSHOTS, capture, compare, database_files, ensure_stopped,
                    opener, serialized, snapshot_path, start, stop)


def main():
    os.environ["JAVA_HOME"] = "/usr/lib/jvm/java-17-openjdk-amd64"
    os.environ["PATH"] = os.environ["JAVA_HOME"] + "/bin:" + os.environ["PATH"]
    ensure_stopped(modern=False)
    capture.reset_database()
    SNAPSHOTS.mkdir(parents=True, exist_ok=True)
    passed = 0
    for index, scenario in enumerate(capture.SCENARIOS):
        snapshot = snapshot_path(index, scenario)
        snapshot.mkdir(exist_ok=True)
        for stale in snapshot.glob("northstar.*"):
            stale.unlink()
        for path in database_files(ROOT / "target/db"):
            shutil.copy2(path, snapshot / path.name)
        process = start(modern=False)
        try:
            result = capture.write_transcript(opener(login=scenario[0] != "login"), scenario)
            passed += compare(scenario, serialized(result))
        finally:
            stop(process)
    print("%d/%d equivalent" % (passed, len(capture.SCENARIOS)))
    return 0 if passed == len(capture.SCENARIOS) else 1


if __name__ == "__main__":
    raise SystemExit(main())
