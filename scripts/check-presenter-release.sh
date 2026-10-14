#!/usr/bin/env bash
# Checks present-app is ready to release:
#   - versionName in present-app/build.gradle.kts has no `presenter_X.Y.Z` tag yet,
#   - versionCode is exactly the latest presenter release's versionCode + 1,
#   - present-app/CHANGELOG.md has a `## X.Y.Z` section.
#
# Run it on the release branch before opening the PR to main; release-presenter.yml runs it too.
# On CI it also exports VERSION and TAG to $GITHUB_ENV.
#
# Usage: scripts/check-presenter-release.sh
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

gradle_file=present-app/build.gradle.kts
changelog=present-app/CHANGELOG.md

read_version_code() { grep -E '^[[:space:]]*versionCode = ' | grep -oE '[0-9]+'; }

version=$(grep -E 'val versionName = ' "$gradle_file" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || true)
if [ -z "$version" ]; then
  echo "error: could not read versionName from $gradle_file" >&2
  exit 1
fi
code=$(read_version_code < "$gradle_file" || true)
if [ -z "$code" ]; then
  echo "error: could not read versionCode from $gradle_file" >&2
  exit 1
fi
tag="presenter_$version"

git fetch --tags --quiet origin

if git ls-remote --exit-code --tags origin "refs/tags/$tag" > /dev/null; then
  echo "error: tag $tag already exists. Bump versionName in $gradle_file." >&2
  exit 1
fi

last_tag=$(git tag -l 'presenter_*' --sort=-v:refname | head -1)
if [ -n "$last_tag" ]; then
  last_code=$(git show "$last_tag:$gradle_file" | read_version_code)
  expected=$((last_code + 1))
  if [ "$code" -ne "$expected" ]; then
    echo "error: versionCode is $code, but $last_tag has $last_code. Set versionCode = $expected in $gradle_file." >&2
    exit 1
  fi
fi

if ! grep -qx "## $version" "$changelog"; then
  echo "error: $changelog has no '## $version' section." >&2
  exit 1
fi

echo "OK: $tag, versionCode $code (previous: ${last_tag:-none})"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "VERSION=$version"
    echo "TAG=$tag"
  } >> "$GITHUB_ENV"
fi
