#!/usr/bin/env bash
# UserPromptSubmit hook: records every prompt to docs/prompt-history.md and
# .specstory/history/<date>-<session>.md. Never blocks the prompt.
set -u
input="$(cat)"
root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
prompt="$(printf '%s' "$input" | jq -r '.prompt // empty')"
session="$(printf '%s' "$input" | jq -r '.session_id // "unknown"' | cut -c1-8)"
[ -z "$prompt" ] && exit 0
# The history is committed, so mask anything that looks like an API key.
prompt="$(printf '%s' "$prompt" | sed -E 's/sk-[A-Za-z0-9_-]{16,}/sk-***REDACTED***/g')"

ts="$(date '+%Y-%m-%d %H:%M:%S')"
day="$(date '+%Y-%m-%d')"
hist="$root/docs/prompt-history.md"
sess="$root/.specstory/history/${day}_${session}.md"
mkdir -p "$(dirname "$hist")" "$(dirname "$sess")"

entry="$(printf '### %s · session %s\n\n```text\n%s\n```\n\n' "$ts" "$session" "$prompt")"
printf '%s\n\n' "$entry" >> "$hist"
[ -f "$sess" ] || printf '# Session %s (%s)\n\n' "$session" "$day" > "$sess"
printf '%s\n\n' "$entry" >> "$sess"
exit 0
