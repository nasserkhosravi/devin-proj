#!/usr/bin/env bash
# Checks every task is well-formed: its hidden test fails with the bug planted and passes on the clean app.
# No device needed. Usage: bench/validate.sh
set -euo pipefail
BENCH_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$BENCH_DIR/lib.sh"

run_grade_test() {  # <app-dir> <task>
  local test_dir="$1/app/src/test/java/com/khosravi/devin/bench" name
  name="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["grade_test"])' "$BENCH_DIR/tasks/$2/task.json")"
  mkdir -p "$test_dir"; cp "$BENCH_DIR/tasks/$2/grade/"*.kt "$test_dir/"
  (cd "$1" && ./gradlew -q :app:testDebugUnitTest --tests "com.khosravi.devin.bench.$name" > /dev/null 2>&1)
}

status=0
for task in $(cd "$BENCH_DIR/tasks" && ls -d */ | tr -d /); do
  work="/tmp/devin-bench-validate/$task"
  for variant in clean bug; do
    rm -rf "$work/$variant"; mkdir -p "$work/$variant"
    rsync -a --exclude build --exclude .gradle "$BENCH_DIR/bench-app/" "$work/$variant/"
    [ "$variant" = bug ] && (cd "$work/$variant" && patch -s -p1 < "$BENCH_DIR/tasks/$task/bug.patch")
  done
  if run_grade_test "$work/clean" "$task"; then clean=pass; else clean=FAIL; fi
  if run_grade_test "$work/bug" "$task"; then bug=PASS; else bug=fail; fi
  verdict=ok; { [ "$clean" = pass ] && [ "$bug" = fail ]; } || { verdict=BROKEN; status=1; }
  printf '%-22s clean=%-4s bug=%-4s %s\n' "$task" "$clean" "$bug" "$verdict"
done
exit $status
