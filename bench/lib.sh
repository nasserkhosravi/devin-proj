# Shared helpers for the benchmark scripts. Source it; don't run it.

BENCH_DIR="${BENCH_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
APP_ID="com.khosravi.devin.bench"
SERVER_PORT=8787
export JAVA_HOME="${JAVA_HOME_17:-/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home}"
export PATH="$HOME/Library/Android/sdk/platform-tools:$JAVA_HOME/bin:$PATH"

log() { echo "[bench] $*" >&2; }

stop_server() {
  # Free the port whoever holds it, so a stale server can't answer with the wrong variant.
  local pids
  pids="$(lsof -ti tcp:"$SERVER_PORT" 2>/dev/null || true)"
  [ -n "$pids" ] && kill $pids 2>/dev/null || true
  sleep 0.5
}

# start_server <variant>: restarts the shop backend with the given response variant.
start_server() {
  stop_server
  python3 "$BENCH_DIR/server.py" --port "$SERVER_PORT" --variant "$1" > /tmp/devin-bench-server.log 2>&1 &
  for _ in $(seq 1 50); do
    curl -s -o /dev/null "http://localhost:$SERVER_PORT/products" && return 0
    sleep 0.1
  done
  log "server did not start"; return 1
}

# run_scenario <name>: cold-starts the app on one user flow and waits for it (and Devin's batched writes) to finish.
run_scenario() {
  adb shell am force-stop "$APP_ID"
  adb shell am start -W -n "$APP_ID/.MainActivity" --es scenario "$1" > /dev/null
  sleep 5
}

clear_devin_logs() {
  adb shell "content delete --uri 'content://com.khosravi.devin.provider/log?_clientId=$APP_ID'" > /dev/null
}
