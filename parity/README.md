# Java 21 migration parity harness

`parity.py` replays the application's HTTP surface against a freshly seeded
HSQLDB and stores one normalized JSON record per request (status, content
type, redirect location, body). It reuses `tools/capture/capture.py` for the
seed, the Jetty start-up and the 22 transcript workflow scenarios, and adds
every other action in `struts-config*.xml` (52 requests in total, including
the anonymous redirect, logout and the `/admin` module).

Normalization strips `jsessionid`, masks clock times and today's date, and
drops blank lines; nothing else is masked.

```text
# baseline (legacy build, Java 7 target, JDK 11 runtime)
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 python3 parity/parity.py capture parity/baseline
# candidate (current build)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 python3 parity/parity.py capture target/parity-candidate
python3 parity/parity.py compare parity/baseline target/parity-candidate
```

`parity/baseline/` was captured from the unmodified `main` build on JDK 11.
Aging reports compute day counts from the current date, so capture the
baseline and the candidate on the same day.
