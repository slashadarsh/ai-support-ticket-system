#!/usr/bin/env bash
# UserPromptSubmit hook: records every prompt.
#   .specstory/history/<date>_<session>.md  raw, verbatim log per session
#   docs/prompt-log.md                      readable log (harness markup removed, prompt text unchanged)
# Never blocks the prompt.
set -u
input="$(cat)"
root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
prompt="$(printf '%s' "$input" | jq -r '.prompt // empty')"
session="$(printf '%s' "$input" | jq -r '.session_id // "unknown"' | cut -c1-8)"
[ -z "$prompt" ] && exit 0

# System notifications (e.g. a background task finishing) arrive through this hook but are not prompts.
case "$prompt" in "<task-notification>"*) exit 0 ;; esac

# The history is committed, so mask anything that looks like an API key.
prompt="$(printf '%s' "$prompt" | sed -E 's/sk-[A-Za-z0-9_-]{16,}/sk-***REDACTED***/g')"

# Readable form: terminal commands as "$ cmd", pasted-text wrappers removed. Wording is not changed.
readable="$(printf '%s' "$prompt" | perl -0pe 's{<bash-input>(.*?)</bash-input>.*}{\$ $1}s; s{</?pasted_content[^>]*>}{}g; s/\A\s+|\s+\z//g')"

ts="$(date '+%Y-%m-%d %H:%M:%S')"
day="$(date '+%Y-%m-%d')"
hist="$root/docs/prompt-log.md"
sess="$root/.specstory/history/${day}_${session}.md"
mkdir -p "$(dirname "$hist")" "$(dirname "$sess")"

[ -f "$sess" ] || printf '# Session %s (%s)\n\n' "$session" "$day" > "$sess"
printf '### %s · session %s\n\n```text\n%s\n```\n\n' "$ts" "$session" "$prompt" >> "$sess"
printf '#### %s · `%s`\n\n```text\n%s\n```\n\n' "$ts" "$session" "$readable" >> "$hist"
exit 0
