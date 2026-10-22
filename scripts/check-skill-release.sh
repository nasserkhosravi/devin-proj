#!/usr/bin/env bash
# Checks the Devin agent skill is ready to release:
#   - devin-skill/VERSION holds X.Y.Z and has no `skill_X.Y.Z` tag yet,
#   - devin-skill/CHANGELOG.md has a `## X.Y.Z` section,
#   - devin-skill/devin-logs/devin.sh passes `bash -n`.
#
# Run it on the release branch before opening the PR to main; release-skill.yml runs it too.
# On CI it also exports VERSION and TAG to $GITHUB_ENV.
#
# Usage: scripts/check-skill-release.sh
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

version=$(tr -d '[:space:]' < devin-skill/VERSION)
if ! printf '%s' "$version" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "error: devin-skill/VERSION must hold X.Y.Z, found '$version'." >&2
  exit 1
fi
tag="skill_$version"

git fetch --tags --quiet origin
if git ls-remote --exit-code --tags origin "refs/tags/$tag" > /dev/null; then
  echo "error: tag $tag already exists. Bump devin-skill/VERSION." >&2
  exit 1
fi

if ! grep -qx "## $version" devin-skill/CHANGELOG.md; then
  echo "error: devin-skill/CHANGELOG.md has no '## $version' section." >&2
  exit 1
fi

bash -n devin-skill/devin-logs/devin.sh

echo "OK: $tag"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "VERSION=$version"
    echo "TAG=$tag"
  } >> "$GITHUB_ENV"
fi
