# Devin agent skill

`devin-logs` lets an AI coding agent (Claude Code and others that support skills) understand what an Android app did at runtime: its logs, crashes and HTTP calls saved by Devin on a connected device or emulator.

## Install

Copy the `devin-logs` folder into your project's `.claude/skills/` (or `~/.claude/skills/` for all projects):

```bash
cp -R devin-logs /path/to/your-app/.claude/skills/
```

Or download `devin-logs-<version>.zip` from the `skill_<version>` GitHub Release and unzip it there.

## Requirements

- `adb` on the PATH, with one device or emulator connected (use `-s <serial>` for more).
- The Devin presenter app installed on that device, and the app writing to Devin (`io.github.nasserkhosravi.devin:write`).
- `bash` and `perl` (macOS and Linux have both).

## Try it

```bash
devin-logs/devin.sh apps
devin-logs/devin.sh -a <applicationId> timeline
```

## Measured effect

See `bench/FINDINGS.md`. On bugs whose evidence exists only at runtime, agents with the skill were about 1.7× cheaper and 2× faster, with the same success rate.
