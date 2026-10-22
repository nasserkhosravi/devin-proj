#!/usr/bin/env python3
"""Runs a command with a clean Claude Code environment for benchmark agents.

Drops the CLAUDE_CODE_* variables and the API endpoint/token of any Claude Code session this runs inside
(they force bare mode, only work for that session, or remap model aliases), so `claude` uses its own sign-in (`claude /login`)
or an exported ANTHROPIC_API_KEY.

Usage: claude-env.py <command> [args...]
"""
import os
import sys

HOST_ONLY = ("ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_BASE_URL", "ANTHROPIC_MODEL")
env = {
    k: v for k, v in os.environ.items()
    # ANTHROPIC_DEFAULT_*_MODEL remap aliases like "sonnet" to other models; the benchmark picks its own.
    if not k.startswith(("CLAUDE_CODE_", "ANTHROPIC_DEFAULT_")) and k not in HOST_ONLY
}
os.execvpe(sys.argv[1], sys.argv[1:], env)
