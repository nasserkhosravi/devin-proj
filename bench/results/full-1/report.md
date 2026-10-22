# Devin agent benchmark

Results: `full-1`, model: `claude-sonnet-5-5` (claude-haiku-5-5, claude-sonnet-5-5)

## Per task

| Task / condition | Runs | Success | Hidden test | Mean cost | Median turns | Median tool calls | Mean time | Used Devin |
|---|---|---|---|---|---|---|---|---|
| t1-error-in-200 / baseline | 2 | 2/2 | 2/2 | $0.13 | 17 | 16 | 86s | 0 |
| t1-error-in-200 / skill | 2 | 2/2 | 2/2 | $0.08 | 10 | 8 | 42s | 2 |
| t2-postmortem-crash / baseline | 2 | 2/2 | 2/2 | $0.15 | 14 | 13 | 68s | 1 |
| t2-postmortem-crash / skill | 2 | 2/2 | 2/2 | $0.08 | 11 | 9 | 29s | 2 |
| t3-wrong-query / baseline | 2 | 2/2 | 2/2 | $0.06 | 7 | 6 | 33s | 0 |
| t3-wrong-query / skill | 2 | 2/2 | 2/2 | $0.07 | 9 | 8 | 29s | 1 |
| t4-plain-crash / baseline | 2 | 2/2 | 2/2 | $0.06 | 6 | 5 | 25s | 0 |
| t4-plain-crash / skill | 2 | 2/2 | 2/2 | $0.07 | 10 | 9 | 46s | 1 |

## Totals

| Task / condition | Runs | Success | Hidden test | Mean cost | Median turns | Median tool calls | Mean time | Used Devin |
|---|---|---|---|---|---|---|---|---|
| baseline | 8 | 8/8 | 8/8 | $0.10 | 8 | 7 | 53s | 1 |
| skill | 8 | 8/8 | 8/8 | $0.08 | 10 | 8 | 37s | 6 |

Success = the root-cause line matches the task's patterns and the hidden test passes. Used Devin = runs that read Devin data at least once (its provider, or present-app's database directly).
