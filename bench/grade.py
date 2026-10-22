#!/usr/bin/env python3
"""Grades one benchmark run from its transcript and answer; writes result.json next to them."""
import argparse
import json
import os
import re


def read_transcript(path):
    events = []
    if not os.path.exists(path):
        return events
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    events.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
    return events


def tool_uses(events):
    for event in events:
        if event.get("type") != "assistant":
            continue
        for block in event.get("message", {}).get("content", []):
            if block.get("type") == "tool_use":
                yield block


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--task", required=True)
    p.add_argument("--condition", required=True)
    p.add_argument("--repeat", type=int, required=True)
    p.add_argument("--model", required=True)
    p.add_argument("--rootcause", required=True)
    p.add_argument("--test-pass", required=True)
    p.add_argument("--out", required=True)
    a = p.parse_args()

    events = read_transcript(os.path.join(a.out, "transcript.jsonl"))
    result_event = next((e for e in reversed(events) if e.get("type") == "result"), {})
    init_event = next((e for e in events if e.get("type") == "system" and e.get("subtype") == "init"), {})
    uses = list(tool_uses(events))
    commands = [u.get("input", {}).get("command", "") for u in uses if u.get("name") == "Bash"]
    raw = open(os.path.join(a.out, "transcript.jsonl")).read() if events else ""

    answer_path = os.path.join(a.out, "ANSWER.md")
    answer = open(answer_path).read() if os.path.exists(answer_path) else ""
    root_line = next((l for l in answer.splitlines() if l.lower().startswith("root cause:")), "")
    patterns = [l.strip() for l in open(a.rootcause) if l.strip()]
    rootcause_pass = bool(root_line) and all(re.search(pat, root_line, re.I) for pat in patterns)
    test_pass = a.test_pass == "true"

    usage = result_event.get("usage", {})
    result = {
        "task": a.task,
        "condition": a.condition,
        "repeat": a.repeat,
        "model": a.model,
        "models_used": sorted((result_event.get("modelUsage") or {}).keys()),
        # Agents must run on Claude models only; anything else (e.g. a gateway alias) invalidates the run.
        "non_claude_model": any(not m.startswith("claude-") for m in (result_event.get("modelUsage") or {})),
        "success": rootcause_pass and test_pass,
        "rootcause_pass": rootcause_pass,
        "test_pass": test_pass,
        "root_cause": root_line,
        "cost_usd": result_event.get("total_cost_usd"),
        "num_turns": result_event.get("num_turns"),
        "duration_s": round(result_event.get("duration_ms", 0) / 1000, 1),
        "input_tokens": usage.get("input_tokens"),
        "cache_read_tokens": usage.get("cache_read_input_tokens"),
        "cache_creation_tokens": usage.get("cache_creation_input_tokens"),
        "output_tokens": usage.get("output_tokens"),
        "tool_calls": len(uses),
        "bash_calls": len(commands),
        "logcat_calls": sum("logcat" in c for c in commands),
        # Any route into Devin's data: the provider, or the presenter's database read directly (run-as).
        "devin_calls": sum(bool(re.search(r"com\.khosravi\.devin\.(provider|present)|db_devin", c)) for c in commands),
        "dropbox_calls": sum("dropbox" in c for c in commands),
        "skill_loaded": "devin-logs" in json.dumps(init_event.get("skills", init_event.get("slash_commands", []))),
        # Integrity flags: the agent should never look at the benchmark itself.
        "peeked": bool(re.search(r"bench/tasks|GradeTest|server\.py", raw)),
        "stop_reason": result_event.get("subtype"),
        "timed_out": os.path.exists(os.path.join(a.out, "timeout")),
    }
    with open(os.path.join(a.out, "result.json"), "w") as f:
        json.dump(result, f, indent=2)
    print(json.dumps({k: result[k] for k in ("task", "condition", "repeat", "success", "cost_usd", "num_turns", "devin_calls")}))


if __name__ == "__main__":
    main()
