# Devin agent benchmark: findings (run `full-1`, 2026-10-22)

**Question (issue #134):** does giving an AI coding agent access to Devin's logs make it debug Android bugs better or cheaper?

**Short answer:** the skill gives the **same success rate** (8/8 both ways). On bugs that need runtime evidence, it **costs 42% less and takes half the time**. On bugs visible in the code, it adds **about 18% overhead**. Two runs per cell is enough to see a direction, not to prove it.

## Setup

- 4 seeded bugs in `bench-app` (see `docs/superpowers/specs/2026-10-22-devin-agent-benchmark-design.md`), 2 conditions, 2 repeats = 16 runs.
- **baseline:** the app writes to Devin and present-app is installed, but the agent gets no Devin instructions. **skill:** the same, plus the first version of the skill (`bench/skill/devin-logs/SKILL.md` at commit 9ae03d1, hand-written queries). The current skill lives in `devin-skill/` and hasn't been benchmarked yet.
- Every run: Claude Code 2.1.118 headless, **Claude Sonnet 5.5** (`claude-sonnet-5-5`; Haiku 5.5 for Claude Code's small internal calls), Pixel_9a emulator, same prompt and tool allowlist, fresh copy of the app.
- Graded automatically: the root-cause line must match the task's patterns, and a hidden unit test must pass.
- Total spend: $1.42 ($0.81 baseline, $0.62 skill).

## Results

| Task group | Condition | Success | Mean cost | Mean turns | Mean tool calls | Mean time |
|---|---|---|---|---|---|---|
| Runtime evidence (t1 error inside a 200, t2 post-mortem crash) | baseline | 4/4 | $0.141 | 15.5 | 14.5 | 77 s |
| | **skill** | 4/4 | **$0.082** | **10.2** | **8.2** | **36 s** |
| Visible in code (t3 wrong sort value, t4 plain crash) | baseline | 4/4 | $0.061 | 6.5 | 5.5 | 29 s |
| | skill | 4/4 | $0.072 | 9.8 | 8.2 | 37 s |
| All | baseline | 8/8 | $0.101 | 11.0 | 10.0 | 53 s |
| | skill | 8/8 | $0.077 | 10.0 | 8.2 | 37 s |

Per-run detail: `results/full-1/report.md` and `results/full-1/*/result.json`.

## What the transcripts show

- **Skill runs go straight to the evidence.** One Devin query returned the HTTP response body (t1) or the saved stack trace (t2), and the agent named the cause next. Nothing went wrong, so the gain is in effort, not in answers.
- **Baselines rebuild the evidence instead.** t1 baseline #2 suspected the parser from the code, tried `curl` (not allowed), then added a log line printing the response body, rebuilt, and reran. That's the "add logging, rebuild, reproduce" loop Devin saves, and it's where the extra turns and cost come from.
- **Baselines still found runtime evidence in 2 of the 4 runtime-evidence runs**, by routes I didn't plan for:
  - t2 baseline #1 read the crash from Android's own crash record (`adb shell dumpsys dropbox`). It survives `logcat -c`, so the "logcat has rolled over" setup didn't remove all evidence.
  - t2 baseline #2 found Devin's presenter and read its SQLite database directly with `adb shell run-as com.khosravi.devin.present` (possible because present-app is a debug build). Without the skill, the agent spent 7 commands on that route.
- **On bugs visible in the code, the skill is overhead.** The agent loads the skill and runs a Devin query it doesn't need, adding about 3 turns and $0.01 per run.

## Validity limits

- **Small sample:** 2 runs per cell. The direction is consistent across both repeats, but the size of the effect is rough.
- **Small app:** every bug in a 10-file app can be found by reading code, so the baseline always succeeds eventually. In a large app, reading all the code isn't an option, and runtime evidence should matter more. This benchmark probably **understates** Devin's value.
- **Grading changes after seeing results, applied to every run in both conditions:**
  - The t1 root-cause pattern was too strict. It required the word "status", and two correct answers said "error body". Fixed and regraded; it changed only those 2 runs, one in each condition.
  - The t2 hidden test was tightened before the full run to reject "price becomes 0".
- **Model change during the run:** the `sonnet` alias moved from Sonnet 4.6 to Sonnet 5.5 after the first trial. That trial (Sonnet 4.6, $0.70) is kept in `results/full-1-excluded/` and was rerun on Sonnet 5.5. Future runs pin a full model ID.
- **Baseline isn't "no Devin at all":** the app source mentions `DevinTool`, and present-app is installed, so a baseline agent can find Devin on its own. That's the realistic situation today, but it narrows the gap.

## Conclusions

1. **The thesis holds, with a caveat.** A bridge to Devin makes agents cheaper and faster on bugs whose evidence is only visible at runtime, about 40% less cost and half the time here. It doesn't make them more correct on this app, because the app is small enough to debug by reading code.
2. **The skill already captures the gain.** Agents need one or two queries per bug, and the skill's recipes were enough. An MCP server isn't justified by this data yet. Build it when the skill's limits show up: huge HAR bodies, redaction, or tools that only support MCP.
3. **Reduce the skill's overhead.** Its description makes agents query Devin even for crashes in plain sight. Narrowing "when to use" (start from logcat or code, use Devin when evidence is missing, past or network-related) should remove most of the overhead on code-visible bugs.
4. **Security finding:** an agent with adb read all Devin data on the device, through the open provider or through `run-as` on a debug present-app. This supports locking down the provider in Part 1.

## Next benchmark round

- **Harder tasks:** more files, bugs not inferable from code (backend data that varies, ordering and timing), and a noisy-log task.
- 3 or more repeats, model pinned (`--model claude-sonnet-5-5`).
- A variant where the post-mortem setup also clears the dropbox crash record (`adb shell dumpsys dropbox --clear`, if allowed), or reports that it can't.
- Re-measure after narrowing the skill's description.
