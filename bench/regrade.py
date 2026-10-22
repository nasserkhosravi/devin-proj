#!/usr/bin/env python3
"""Re-applies the current root-cause patterns to saved answers, e.g. after fixing a too-strict pattern.

The hidden-test result is kept as recorded. Usage: regrade.py <results-dir>
"""
import glob
import json
import os
import re
import sys

BENCH_DIR = os.path.dirname(os.path.abspath(__file__))
for path in sorted(glob.glob(os.path.join(sys.argv[1], "*", "result.json"))):
    r = json.load(open(path))
    if "task" not in r:
        continue
    patterns = [l.strip() for l in open(os.path.join(BENCH_DIR, "tasks", r["task"], "grade", "rootcause.txt")) if l.strip()]
    rootcause_pass = bool(r["root_cause"]) and all(re.search(p, r["root_cause"], re.I) for p in patterns)
    if rootcause_pass != r["rootcause_pass"]:
        print("{} / {} / #{}: rootcause {} -> {}".format(r["task"], r["condition"], r["repeat"], r["rootcause_pass"], rootcause_pass))
    transcript = os.path.join(os.path.dirname(path), "transcript.jsonl")
    if os.path.exists(transcript):
        commands = []
        for line in open(transcript):
            try:
                e = json.loads(line)
            except ValueError:
                continue
            if e.get("type") == "assistant":
                for b in e.get("message", {}).get("content", []):
                    if b.get("type") == "tool_use" and b.get("name") == "Bash":
                        commands.append(b.get("input", {}).get("command", ""))
        r["devin_calls"] = sum(bool(re.search(r"com\.khosravi\.devin\.(provider|present)|db_devin", c)) for c in commands)
        r["dropbox_calls"] = sum("dropbox" in c for c in commands)
        r["logcat_calls"] = sum("logcat" in c for c in commands)
    r["rootcause_pass"] = rootcause_pass
    r["success"] = rootcause_pass and r["test_pass"]
    json.dump(r, open(path, "w"), indent=2)
