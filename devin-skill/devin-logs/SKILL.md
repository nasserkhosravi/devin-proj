---
name: devin-logs
description: Understand what an Android app actually did at runtime, from the logs, crashes and full HTTP traffic that Devin saved on a connected device. Use it whenever an answer depends on runtime behavior rather than code alone, while developing, debugging, or answering questions about the app. For example "did I log in?", "why isn't this card shown?", "what caused the crash?", "what did the backend send?", "did my change run?".
---

# Devin: what happened at runtime

Devin keeps each app's logs, crashes (with stack traces) and HTTP calls (with bodies), across restarts and after logcat has rolled over. Code tells you what the app *can* do; Devin tells you what it *did*. Use both.

Read Devin with `devin.sh` from this skill's folder. It prints one JSON object per line and masks secrets.

```
devin.sh -a <applicationId> <command>     # or: export DEVIN_APP=<applicationId>
  apps                  apps that log to Devin
  timeline [n]          latest session, in order
  tags                  each tag: count and last message
  search <word> [n]     logs and request URLs containing <word>
  http [path] [n]       HTTP calls, newest first
  call <id>             one call: request, status, response
  field <id> <path>     one response field, e.g. '$.data.cards[0].visible'
  headers <id>          header names sent and received
  crash [n]             latest crash + the n entries before it
  errors [n]            warnings, errors, logged exceptions
  clear                 delete the app's logs (only right before reproducing)
```
Several devices connected: add `-s <serial>`.

## How to work

1. **Orient:** `apps`, then `timeline` or `tags` for the latest session. Older sessions can mislead.
2. **Find the evidence** for the question: the logs, calls or crash around the event (`search`, `http`, `call`, `field`, `crash`, `errors`).
3. **Join runtime and code:** search the source for the tag, message, endpoint or field you found, and read what the code does with it. For "why is X shown/hidden/wrong", find the condition in the code, then the actual value Devin recorded for it.
4. **To watch something happen** (a change, a flow): `clear`, run it, then `timeline`.
5. **Answer with proof:** what happened, when, and the log id or value that shows it. If Devin has no evidence, say so, and suggest a Devin log where the app is silent.
6. **Never repeat secrets:** no tokens, passwords or personal data in your answer.
