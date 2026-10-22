#!/usr/bin/env python3
"""Summarizes a results directory into report.md: one row per task × condition, plus totals per condition.

Usage: report.py <results-dir>
"""
import glob
import json
import os
import statistics
import sys


def mean(values):
    values = [v for v in values if v is not None]
    return statistics.mean(values) if values else None


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def fmt(value, pattern="{:.2f}"):
    return "-" if value is None else pattern.format(value)


def row(name, runs):
    n = len(runs)
    success = sum(r["success"] for r in runs)
    return "| {} | {} | {}/{} | {}/{} | {} | {} | {} | {} | {} |".format(
        name,
        n,
        success, n,
        sum(r["test_pass"] for r in runs), n,
        fmt(mean([r["cost_usd"] for r in runs]), "${:.2f}"),
        fmt(median([r["num_turns"] for r in runs]), "{:.0f}"),
        fmt(median([r["tool_calls"] for r in runs]), "{:.0f}"),
        fmt(mean([r["duration_s"] for r in runs]), "{:.0f}s"),
        sum(r["devin_calls"] > 0 for r in runs),
    )


def main():
    out = sys.argv[1]
    runs = []
    for path in sorted(glob.glob(os.path.join(out, "*", "result.json"))):
        result = json.load(open(path))
        if "error" not in result:
            runs.append(result)
    if not runs:
        print("no results in", out)
        return

    header = (
        "| Task / condition | Runs | Success | Hidden test | Mean cost | Median turns | Median tool calls | Mean time | Used Devin |\n"
        "|---|---|---|---|---|---|---|---|---|"
    )
    lines = ["# Devin agent benchmark", "", "Results: `{}`, model: `{}` ({})".format(os.path.basename(out.rstrip("/")), runs[0]["model"], ", ".join(sorted({m for r in runs for m in r.get("models_used", [])}))), ""]
    lines += ["## Per task", "", header]
    for task in sorted({r["task"] for r in runs}):
        for condition in sorted({r["condition"] for r in runs}):
            subset = [r for r in runs if r["task"] == task and r["condition"] == condition]
            if subset:
                lines.append(row("{} / {}".format(task, condition), subset))
    lines += ["", "## Totals", "", header]
    for condition in sorted({r["condition"] for r in runs}):
        lines.append(row(condition, [r for r in runs if r["condition"] == condition]))

    flagged = [r for r in runs if r["peeked"] or r["timed_out"] or r.get("non_claude_model")]
    if flagged:
        lines += ["", "## Flags", ""]
        for r in flagged:
            lines.append("- {} / {} / #{}: {}".format(
                r["task"], r["condition"], r["repeat"],
                ", ".join(k for k in ("peeked", "timed_out", "non_claude_model") if r.get(k))))

    lines += ["", "Success = the root-cause line matches the task's patterns and the hidden test passes. "
              "Used Devin = runs that read Devin data at least once (its provider, or present-app's database directly)."]
    report = "\n".join(lines) + "\n"
    open(os.path.join(out, "report.md"), "w").write(report)
    print(report)


if __name__ == "__main__":
    main()
