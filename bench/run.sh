#!/usr/bin/env bash
# Runs the Devin agent benchmark: every task × condition × repeat, then grades each run.
#
# Rerunning with the same --out resumes: finished trials are skipped.
#
# Usage: bench/run.sh [--tasks t1-error-in-200,t2-...] [--conditions baseline,skill] [--repeats 3]
#                     [--model sonnet] [--budget 2] [--out bench/results/<name>]
#
# Needs: one emulator on adb with present-app installed, python3, the `claude` CLI, JDK 17.
set -euo pipefail

BENCH_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$BENCH_DIR/lib.sh"
trap 'log "stopped: line $LINENO: $BASH_COMMAND"' ERR
# Ctrl-C: stop the backend and this script's children (agent, watchdog) instead of leaving them running.
trap 'log "interrupted"; stop_server; pkill -P $$ 2>/dev/null; exit 130' INT TERM
trap 'stop_server' EXIT

TASKS="$(cd "$BENCH_DIR/tasks" && ls -d */ | tr -d / | paste -sd, -)"
CONDITIONS="baseline,skill"
REPEATS=3
MODEL="sonnet"
BUDGET=2
OUT="$BENCH_DIR/results/$(date +%Y%m%d-%H%M%S)"
TRIAL_TIMEOUT_S=1200

while [ $# -gt 0 ]; do
  case "$1" in
    --tasks) TASKS="$2"; shift 2 ;;
    --conditions) CONDITIONS="$2"; shift 2 ;;
    --repeats) REPEATS="$2"; shift 2 ;;
    --model) MODEL="$2"; shift 2 ;;
    --budget) BUDGET="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

ALLOWED_TOOLS=(
  "Read" "Edit" "Write" "Grep" "Glob"
  "Bash(adb *)" "Bash(./gradlew *)" "Bash(./repro.sh *)"
  "Bash(ls *)" "Bash(cat *)" "Bash(grep *)" "Bash(head *)" "Bash(tail *)" "Bash(find *)" "Bash(sleep *)"
)

# prepare <task> <condition> <dir>: a fresh copy of bench-app with the bug planted, installed and set up on the device.
prepare() {
  local task="$1" condition="$2" dir="$3"
  rm -rf "$dir"; mkdir -p "$dir"
  rsync -a --exclude build --exclude .gradle "$BENCH_DIR/bench-app/" "$dir/"
  (cd "$dir" && patch -s -p1 < "$BENCH_DIR/tasks/$task/bug.patch")
  cp "$BENCH_DIR/repro.sh" "$dir/repro.sh"
  if [ "$condition" = "skill" ]; then
    mkdir -p "$dir/.claude/skills"
    cp -R "$BENCH_DIR/skill/devin-logs" "$dir/.claude/skills/devin-logs"
  fi
  # A baseline commit, so the agent's changes can be diffed afterwards.
  (cd "$dir" && git init -q && git add -A && git -c user.name=bench -c user.email=bench@local commit -qm start)

  (cd "$dir" && ./gradlew -q :app:installDebug > "$dir.build.log" 2>&1) || { log "build failed: $dir.build.log"; return 1; }
  adb shell am force-stop "$APP_ID"
  clear_devin_logs
  adb logcat -c
  # shellcheck disable=SC1090
  source "$BENCH_DIR/tasks/$task/setup.sh"
}

# agent <task> <dir> <out>: runs Claude Code headless in <dir>; the transcript goes to <out>/transcript.jsonl.
agent() {
  local task="$1" dir="$2" out="$3"
  local report prompt
  report="$(cat "$BENCH_DIR/tasks/$task/prompt.md")"
  prompt="$(python3 -c 'import sys; print(open(sys.argv[1]).read().replace("{{BUG_REPORT}}", sys.argv[2]))' "$BENCH_DIR/agent-prompt.md" "$report")"

  (
    cd "$dir"
    # claude-env.py drops the variables of any Claude Code session this script runs inside, so the agent
    # uses the CLI's own sign-in (`claude /login`) or ANTHROPIC_API_KEY.
    "$BENCH_DIR/claude-env.py" claude -p "$prompt" \
      --output-format stream-json --verbose \
      --model "$MODEL" \
      --setting-sources project \
      --strict-mcp-config --mcp-config '{"mcpServers":{}}' \
      --permission-mode dontAsk \
      --allowedTools "${ALLOWED_TOOLS[@]}" \
      --max-budget-usd "$BUDGET" \
      --no-session-persistence \
      < /dev/null > "$out/transcript.jsonl" 2> "$out/agent.stderr" &
    local pid=$!
    # Watchdog: kills the agent after TRIAL_TIMEOUT_S, and exits within seconds once the agent is done,
    # so nothing is left holding this script's output open.
    (
      waited=0
      while kill -0 "$pid" 2>/dev/null; do
        sleep 5; waited=$((waited + 5))
        if [ "$waited" -ge "$TRIAL_TIMEOUT_S" ]; then kill "$pid"; echo "timeout" > "$out/timeout"; break; fi
      done
    ) > /dev/null 2>&1 &
    wait "$pid" || true
  )
}

# grade <task> <dir> <out>: checks the root-cause line and runs the hidden test; writes <out>/result.json.
grade() {
  local task="$1" dir="$2" out="$3"
  local grade_test test_dir
  grade_test="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["grade_test"])' "$BENCH_DIR/tasks/$task/task.json")"

  (cd "$dir" && git diff > "$out/agent.diff"; git status --porcelain > "$out/agent.status")
  [ -f "$dir/ANSWER.md" ] && cp "$dir/ANSWER.md" "$out/ANSWER.md"

  test_dir="$dir/app/src/test/java/com/khosravi/devin/bench"
  mkdir -p "$test_dir"
  cp "$BENCH_DIR/tasks/$task/grade/"*.kt "$test_dir/"
  local test_pass=false
  if (cd "$dir" && ./gradlew -q :app:testDebugUnitTest --tests "com.khosravi.devin.bench.$grade_test" > "$out/grade-test.log" 2>&1); then
    test_pass=true
  fi

  python3 "$BENCH_DIR/grade.py" \
    --task "$task" --condition "$CONDITION" --repeat "$REPEAT" --model "$MODEL" \
    --rootcause "$BENCH_DIR/tasks/$task/grade/rootcause.txt" \
    --test-pass "$test_pass" --out "$out"
}

mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"  # absolute: agent() and grade() run from the trial directory
log "results: $OUT"
IFS=, read -r -a task_list <<< "$TASKS"
IFS=, read -r -a condition_list <<< "$CONDITIONS"

for REPEAT in $(seq 1 "$REPEATS"); do
  for task in "${task_list[@]}"; do
    for CONDITION in "${condition_list[@]}"; do
      id="${task}__${CONDITION}__${REPEAT}"
      dir="/tmp/devin-bench/$id"
      out="$OUT/$id"
      # Resumable: rerunning with the same --out skips trials that already finished.
      if [ -f "$out/result.json" ] && ! grep -q '"error"' "$out/result.json"; then
        log "$id: done already, skipping"; continue
      fi
      rm -rf "$out"; mkdir -p "$out"
      if ! adb get-state > /dev/null 2>&1; then
        log "no device on adb; start the emulator, then rerun with --out $OUT to resume"; exit 1
      fi
      log "$id: prepare"
      if ! prepare "$task" "$CONDITION" "$dir"; then
        echo '{"error":"prepare failed"}' > "$out/result.json"; continue
      fi
      log "$id: agent ($MODEL)"
      agent "$task" "$dir" "$out"
      log "$id: grade"
      grade "$task" "$dir" "$out"
    done
  done
done

stop_server
python3 "$BENCH_DIR/report.py" "$OUT"
log "report: $OUT/report.md"
