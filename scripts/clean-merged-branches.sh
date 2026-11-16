#!/usr/bin/env bash
# Cleans up work that is already merged into main:
#   1. linked worktrees whose checked-out commit is merged,
#   2. local branches that are merged.
# It lists both, asks once, then removes everything listed.
#
# Never touched: main, main, the current branch, the main worktree, the worktree you
# run it from, locked worktrees, and worktrees with uncommitted changes to tracked files.
# Worktrees that only hold untracked files (e.g. .idea/) are removed, files included.
#
# "Merged" means an ancestor of the local main branch or of any remote's copy of it
# (gitlab/main, origin/main).
# Squash-merged branches are not detected; delete those by hand.
#
# Usage: scripts/clean-merged-branches.sh   (or: bash scripts/clean-merged-branches.sh)

# Needs bash (process substitution below); re-run under bash if started with `sh script`.
if [ -z "${BASH_VERSION:-}" ] || shopt -oq posix; then
  exec bash "$0" "$@"
fi

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

# --- Find the base branch --------------------------------------------------------------

base="main"
if ! git show-ref --verify --quiet "refs/heads/$base"; then
  echo "error: no local '$base' branch found." >&2
  exit 1
fi

# Branches that are never deleted, even when merged.
protected_branches=("$base" main)

bases=("$base")
for remote in $(git remote); do
  if git show-ref --verify --quiet "refs/remotes/$remote/$base"; then
    bases+=("$remote/$base")
  fi
done

# True if the given commit is merged into any of the base refs.
is_merged() {
  local ref
  for ref in "${bases[@]}"; do
    if git merge-base --is-ancestor "$1" "$ref"; then
      return 0
    fi
  done
  return 1
}

current_branch=$(git symbolic-ref --short -q HEAD || true)
this_worktree=$(pwd -P)
main_worktree=$(git worktree list --porcelain | sed -n '1s/^worktree //p')

# --- Collect merged worktrees ----------------------------------------------------------

worktree_paths=()
worktree_labels=()
worktree_branches=""   # newline-separated branch names that will be freed

# Looks at one worktree entry (path, head, branch, locked) and records it if removable.
consider_worktree() {
  local path="$1" head="$2" branch="$3" locked="$4"

  [ -z "$path" ] && return 0
  [ "$path" = "$main_worktree" ] && return 0
  [ "$path" = "$this_worktree" ] && return 0
  [ -n "$locked" ] && return 0
  is_merged "$head" || return 0

  # Uncommitted changes to tracked files: leave the worktree alone.
  if [ -n "$(git -C "$path" status --porcelain --untracked-files=no)" ]; then
    echo "Skipping $path: it has uncommitted changes." >&2
    return 0
  fi

  local label="${branch:-detached HEAD}"
  if [ -n "$(git -C "$path" status --porcelain)" ]; then
    label="$label, has untracked files that will be deleted"
  fi

  worktree_paths+=("$path")
  worktree_labels+=("$label")
  if [ -n "$branch" ]; then
    worktree_branches+="$branch"$'\n'
  fi
}

# `git worktree list --porcelain` prints one block per worktree, separated by blank lines.
wt_path="" wt_head="" wt_branch="" wt_locked=""
while IFS= read -r line; do
  case "$line" in
    "worktree "*) wt_path="${line#worktree }" ;;
    "HEAD "*)     wt_head="${line#HEAD }" ;;
    "branch "*)   wt_branch="${line#branch refs/heads/}" ;;
    "locked"*)    wt_locked="yes" ;;
    "")
      consider_worktree "$wt_path" "$wt_head" "$wt_branch" "$wt_locked"
      wt_path="" wt_head="" wt_branch="" wt_locked=""
      ;;
  esac
done < <(git worktree list --porcelain)
consider_worktree "$wt_path" "$wt_head" "$wt_branch" "$wt_locked"

# --- Collect merged branches -----------------------------------------------------------

# Branches checked out in a worktree can't be deleted, unless that worktree is being removed.
checked_out=$(git worktree list --porcelain | sed -n 's|^branch refs/heads/||p')

branches=()
while IFS= read -r branch; do
  [ -z "$branch" ] && continue
  printf '%s\n' "${protected_branches[@]}" | grep -qxF -- "$branch" && continue
  [ "$branch" = "$current_branch" ] && continue

  if printf '%s\n' "$checked_out" | grep -qxF -- "$branch" \
    && ! printf '%s' "$worktree_branches" | grep -qxF -- "$branch"; then
    continue
  fi

  if is_merged "refs/heads/$branch"; then
    branches+=("$branch")
  fi
done < <(git for-each-ref --format='%(refname:short)' refs/heads)

# --- Ask, then clean up ----------------------------------------------------------------

if [ "${#worktree_paths[@]}" -eq 0 ] && [ "${#branches[@]}" -eq 0 ]; then
  echo "Nothing merged into ${bases[*]} to clean up."
  exit 0
fi

echo "Merged into ${bases[*]}:"
if [ "${#worktree_paths[@]}" -gt 0 ]; then
  echo
  echo "Worktrees to remove:"
  for i in "${!worktree_paths[@]}"; do
    echo "  ${worktree_paths[$i]}  (${worktree_labels[$i]})"
  done
fi
if [ "${#branches[@]}" -gt 0 ]; then
  echo
  echo "Local branches to delete:"
  for branch in "${branches[@]}"; do
    echo "  $branch"
  done
fi
echo

read -r -p "Remove ${#worktree_paths[@]} worktrees and delete ${#branches[@]} branches? [y/N] " answer
case "$answer" in
  y|Y|yes|YES) ;;
  *) echo "Aborted, nothing removed."; exit 0 ;;
esac

# Worktrees first, so the branches they hold can be deleted afterwards.
# --force is needed only for untracked files; tracked changes were ruled out above.
if [ "${#worktree_paths[@]}" -gt 0 ]; then
  for path in "${worktree_paths[@]}"; do
    git worktree remove --force "$path"
    echo "Removed worktree $path"
  done
fi

# -D is safe here: the merge into the base was verified above. Plain -d would check against
# the upstream or the current branch instead, and refuse branches whose upstream is gone.
if [ "${#branches[@]}" -gt 0 ]; then
  git branch -D "${branches[@]}"
fi
