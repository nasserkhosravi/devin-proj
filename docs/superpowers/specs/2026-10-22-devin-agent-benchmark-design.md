# Devin agent benchmark: design

Issue #134, part 2. Before we build a Devin skill or MCP server, measure whether giving an AI coding agent access to Devin's logs makes it debug better or cheaper.

## Question

When an agent debugs an Android app, does access to Devin logs (through a skill now, an MCP server later) change:
- **success:** does it find the right root cause and land a fix that passes a hidden test?
- **cost:** tokens/$, tool calls, turns, and wall time.

And on which kinds of bugs does it change them?

## Conditions

All conditions use the same model, prompt, app source, emulator, `adb`, Gradle and tool allowlist. The only difference is Devin access.

| Id | Name | Devin access |
|---|---|---|
| A | baseline | present-app is installed and the app writes to Devin, but the agent gets no Devin instructions. This is today's situation. We also record whether the agent finds the Devin provider on its own. |
| B | skill | Same as A, plus `.claude/skills/devin-logs/SKILL.md` in the task directory. |
| C | mcp | (Later) Same as A, plus the Devin MCP server. |

## Benchmark app (`bench/bench-app`)

A small "shop" app, built as its own Gradle project against the **published** Devin artifacts (`write` 4.5.0, `write-okhttp` 1.0.2), like a real client app.

- **Fair logging.** Every app log goes through `AppLog`, which writes the same line to logcat and to Devin. HTTP traffic is logged to logcat by OkHttp's `HttpLoggingInterceptor` at `BASIC` (method, URL, status, time). Devin's network interceptor captures full HAR. This matches a common debug setup: request lines in logcat, bodies only in Devin.
- **Real HTTP.** A local Python server (`bench/server.py`) on the Mac serves the app's API. The emulator reaches it at `http://10.0.2.2:8787`. Each task can start it with a response variant.
- **Scenarios instead of UI driving.** `MainActivity` runs a user flow named by an intent extra, e.g. `adb shell am start -n <pkg>/.MainActivity --es scenario checkout`. Every task gets `repro.sh`, which does exactly this.
- **Testable logic.** Bugs live in plain Kotlin classes (parsers, mappers, repositories), so a hidden JVM unit test can check the fix.

## Tasks

Each task is a folder `bench/tasks/<id>/` with:
- `bug.patch`: plants the bug in a clean copy of bench-app
- `prompt.md`: what the agent is told, phrased like a bug report
- `setup.sh`: brings the device to the starting state (e.g. reproduces a past crash, then restarts the app and clears logcat)
- `grade/`: a hidden unit test copied in only when grading, plus `rootcause.txt` with regexes the answer must match
- `task.json`: kind, expected winner, server variant

| Id | Kind | Bug | Expected |
|---|---|---|---|
| `t1-error-in-200` | network body | Checkout returns HTTP 200 with `{"status":"error",...}`; the parser treats every 200 as success | Devin wins (only the HAR has the body) |
| `t2-postmortem-crash` | post-mortem | Yesterday the backend returned a product with `price: null` and the mapper crashed; the backend is fine now and logcat has rolled | Devin wins (persisted crash and the logs before it) |
| `t3-wrong-query` | network URL | Search sends `sort=price_dsc` (typo), so the server ignores it | Tie (logcat BASIC shows the URL too) |
| `t4-plain-crash` | live crash | NPE in the cart total, reproducible with `repro.sh`, clear stack trace | Tie |

Kinds where Devin should not help (t3, t4) are there on purpose, to measure overhead and to keep the result honest.

## Runner (`bench/run.sh`)

For each (task, condition, repeat):
1. Copy bench-app to a fresh directory outside the repo, `/tmp/devin-bench/<run-id>/`, so the repo's CLAUDE.md and AGENTS.md don't leak in. Apply `bug.patch`. For condition B, add the skill.
2. Build and install the app. Clear Devin logs for its client id and run `setup.sh`.
3. Run `claude -p` with `--output-format json`, `--model <fixed>`, `--setting-sources project`, `--strict-mcp-config`, `--permission-mode dontAsk`, an allowlist (Read, Edit, Write, Grep, Glob, `Bash(adb *)`, `Bash(./gradlew *)`, `Bash(./repro.sh*)` and a few read-only shell commands), plus `--max-budget-usd` and a turn cap.
4. The agent must write `ANSWER.md` with a `Root cause:` line and fix the code.
5. Grade: match the answer against `rootcause.txt`, copy in the hidden test and run `./gradlew testDebugUnitTest`. Save the cost, turns, duration and tool-call counts from the JSON result to `results/<run-id>.json`, along with whether the agent touched the Devin provider (`content query` in the transcript).

`bench/report.py` turns `results/` into `bench/report.md`: success rate and mean/median cost per task and condition.

## Size and cost

Default: 4 tasks × 2 conditions × 3 repeats = 24 runs. Each run has a budget cap (default $2), so the worst case is known before starting.

## Not in scope

- The MCP server (condition C) is added later on the same tasks.
- Real bugs from other apps (private, and hard to reproduce on demand).
- A UI automation framework.
