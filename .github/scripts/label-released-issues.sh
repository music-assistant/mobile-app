#!/usr/bin/env bash
# Labels every issue that a PR in PREV_TAG..TAG closes with the release tag,
# so a closed issue shows the release that carries its fix.
# Usage: label-released-issues.sh <prev_tag> <tag> <label_color>
# Needs GH_TOKEN (issues: write) and GH_REPO (owner/name).
set -euo pipefail

PREV_TAG="$1"
TAG="$2"
COLOR="$3"

# Without a previous tag the range is the whole history: label nothing.
[ -n "$PREV_TAG" ] || { echo "No previous tag; skipping issue labels"; exit 0; }

gh label create "$TAG" --color "$COLOR" --description "Released in $TAG" --force

# Commit -> merged PR comes from the API, not from the subject: a squash
# subject can name an issue (#N) before its own PR number.
for SHA in $(git rev-list --first-parent "$PREV_TAG..$TAG"); do
  gh api "repos/$GH_REPO/commits/$SHA/pulls" --jq '.[] | select(.merged_at != null) | .number'
done | sort -u | while read -r PR; do
  gh pr view "$PR" --json closingIssuesReferences \
    --jq ".closingIssuesReferences[].url | select(startswith(\"https://github.com/$GH_REPO/issues/\"))"
done | sort -u | while read -r ISSUE; do
  echo "Labeling $ISSUE with $TAG"
  gh issue edit "$ISSUE" --add-label "$TAG" || echo "::warning::Could not label $ISSUE"
done
