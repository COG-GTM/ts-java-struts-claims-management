#!/usr/bin/env python3
"""Replay an area independently from its exact pre-scenario legacy state."""

import argparse
from common import (area, capture, compare, ensure_stopped, opener, restore, serialized,
                    snapshot_path, start, stop)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--area", required=True, choices=[
        "foundation", "policy", "intake", "workbench", "settlement", "payment", "reports", "all"])
    args = parser.parse_args()
    passed = total = 0
    for index, scenario in enumerate(capture.SCENARIOS):
        if args.area != "all" and area(scenario) != args.area:
            continue
        total += 1
        ensure_stopped(modern=True)
        restore(snapshot_path(index, scenario))
        process = start(modern=True)
        try:
            result = capture.write_transcript(opener(login=scenario[0] != "login"), scenario)
            passed += compare(scenario, serialized(result))
        finally:
            stop(process)
    print("%d/%d PASS" % (passed, total))
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
