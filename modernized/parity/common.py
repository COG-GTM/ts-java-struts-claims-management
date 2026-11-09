"""Use the unmodified capture extraction and serialization contract everywhere."""

import difflib
import http.cookiejar
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import urllib.request

MODERN = Path(__file__).resolve().parents[1]
ROOT = MODERN.parent
SNAPSHOTS = MODERN / "parity" / "snapshots"
spec = importlib.util.spec_from_file_location("capture", ROOT / "tools/capture/capture.py")
capture = importlib.util.module_from_spec(spec)
sys.dont_write_bytecode = True
spec.loader.exec_module(capture)


def serialized(value):
    return json.dumps(value, indent=2, sort_keys=True) + "\n"


def opener(login=False):
    client = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    capture.request(client, "GET", "/login.do")
    if login:
        capture.request(client, "POST", "/login.do",
                        {"username": "supervisor", "password": "supervisor"})
    return client


def wait_ready(process):
    deadline = time.time() + 180
    while time.time() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Server exited; inspect parity/*.log")
        try:
            code, _, body = capture.request(urllib.request.build_opener(), "GET", "/login.do")
            if code == 200 and "Claims Login" in body:
                return
        except Exception:
            pass
        time.sleep(0.5)
    raise RuntimeError("Server readiness timed out at " + capture.BASE)


def ensure_stopped(modern):
    capture.BASE = "http://localhost:%d/claims" % (8081 if modern else 8080)
    # Never accidentally drive an already-running server against a different database.
    try:
        capture.request(urllib.request.build_opener(), "GET", "/login.do")
    except Exception:
        pass
    else:
        raise RuntimeError("Stop the existing server at " + capture.BASE + " first")


def start(modern):
    ensure_stopped(modern)
    env = os.environ.copy()
    env["JAVA_HOME"] = "/usr/lib/jvm/java-%s-openjdk-amd64" % (21 if modern else 17)
    env["PATH"] = env["JAVA_HOME"] + "/bin:" + env["PATH"]
    command = [env["JAVA_HOME"] + "/bin/java", "-jar", "target/claims.jar"] if modern else [
        "mvn", *os.environ.get("MAVEN_CAPTURE_ARGS", "").split(), "-q", "jetty:run"]
    with (MODERN / "parity" / ("modern.log" if modern else "legacy.log")).open("w") as log:
        process = subprocess.Popen(command, cwd=MODERN if modern else ROOT,
                                   env=env, stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True)
    try:
        wait_ready(process)
    except Exception:
        stop(process)
        raise
    return process


def stop(process):
    if process is None or process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait()


def snapshot_path(index, scenario):
    return SNAPSHOTS / ("%02d_%s" % (index + 1, scenario[0]))


def database_files(directory):
    return [path for path in directory.glob("northstar.*")
            if path.is_file() and path.suffix != ".lck"]


def restore(snapshot):
    target = MODERN / "target/db"
    target.mkdir(parents=True, exist_ok=True)
    files = database_files(snapshot)
    if not files:
        raise RuntimeError("Missing snapshot: " + str(snapshot))
    for path in target.glob("northstar.*"):
        if path.is_dir():
            shutil.rmtree(path)
        else:
            path.unlink()
    for path in files:
        shutil.copy2(path, target / path.name)


def compare(scenario, actual):
    name = scenario[0]
    expected = (ROOT / "transcripts" / (name + ".json")).read_text()
    if actual == expected:
        print("%-36s PASS" % name, flush=True)
        return True
    print("%-36s FAIL" % name, flush=True)
    print("".join(difflib.unified_diff(expected.splitlines(True), actual.splitlines(True),
                                      fromfile="transcripts/" + name + ".json",
                                      tofile="modern/" + name + ".json")))
    return False


def area(scenario):
    name = scenario[0]
    if name == "login":
        return "foundation"
    prefix = name.split("_")[0]
    return {"fnol": "intake", "report": "reports"}.get(prefix, prefix)
