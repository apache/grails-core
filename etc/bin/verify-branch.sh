#!/usr/bin/env bash
#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#
# verify-branch.sh - pre-tag release readiness check
#
# Runs all release-readiness checks that can be done LOCALLY against the
# current working tree, with no tag, no GitHub release, and no staged
# artifacts. This is the pre-tag counterpart to verify.sh.
#
# Use this BEFORE publishing the release tag (e.g. v8.0.0-M1) to gain
# confidence that the publish job in .github/workflows/release.yml will
# succeed. After staging completes, use the existing verify.sh to verify
# the staged artifacts themselves.
#
# Recommended usage on the 8.0.x branch:
#
#   etc/bin/verify-branch.sh
#
# Or inside the verification container (recommended for parity with CI):
#
#   docker build -t grails:testing -f etc/bin/Dockerfile .
#   docker run -it --rm -v "$(pwd):/home/groovy/project" grails:testing bash
#   cd /home/groovy/project
#   etc/bin/verify-branch.sh

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: verify-branch.sh [OPTIONS]

Runs the release-readiness checks that can be performed locally against the
current working tree, without requiring a tag, GitHub release, or any staged
artifacts. Mirrors the locally-runnable surface of release.yml's publish job.

Default checks (typically 15-30 minutes):
  1. dependencies.gradle does not contain SNAPSHOT versions
  2. KEYS file matches the canonical copy at dist.apache.org
  3. Apache RAT license audit (./gradlew rat)
  4. Code style (./gradlew codeStyle)
  5. grails-core assemble (./gradlew assemble)
  6. grails-forge assemble (cd grails-forge && ./gradlew assemble)
  7. grails-doc build (./gradlew grails-doc:build)

OPTIONS:
  --branch <name>             Branch name passed to -PgithubBranch.
                              Defaults to the current git branch.
  --skip-build                Skip the gradle assemble + docs steps. Runs
                              only steps 1-4 (typically 5-10 minutes).
  --include-reproducibility   Also run etc/bin/test-reproducible-builds.sh
                              after the default checks (slow, 30-60+ min).
  --include-tests             Also run the full test suite (slow).
  --include-all               Equivalent to --include-reproducibility
                              --include-tests.
  -h, --help                  Show this help.

Exit codes:
  0   All requested checks passed
  1   At least one check failed
  2   Invalid arguments
EOF
}

BRANCH=""
SKIP_BUILD=false
INCLUDE_REPRODUCIBILITY=false
INCLUDE_TESTS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch)
      if [[ $# -lt 2 ]]; then
        echo "❌ --branch requires an argument" >&2
        usage >&2
        exit 2
      fi
      BRANCH="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --include-reproducibility)
      INCLUDE_REPRODUCIBILITY=true
      shift
      ;;
    --include-tests)
      INCLUDE_TESTS=true
      shift
      ;;
    --include-all)
      INCLUDE_REPRODUCIBILITY=true
      INCLUDE_TESTS=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "❌ Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)
cd "${PROJECT_ROOT}"

if [[ -z "${BRANCH}" ]]; then
  BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  if [[ -z "${BRANCH}" || "${BRANCH}" == "HEAD" ]]; then
    echo "⚠️  Could not auto-detect a branch name. Pass --branch <name> explicitly."
    BRANCH="unknown"
  fi
fi

# Mirror release.yml's "Store common build date" so any reproducibility-
# sensitive task that runs here uses the same epoch the CI publish job will.
SOURCE_DATE_EPOCH=$(git log -1 --pretty=%ct 2>/dev/null || echo "$(date +%s)")
export SOURCE_DATE_EPOCH

GRADLE_PROPS=("-PgithubBranch=${BRANCH}")
TMP_DIR="${PROJECT_ROOT}/build/branch-verify-tmp"
NORMALIZED_SCRIPTS_DIR="${TMP_DIR}/normalized-scripts"
mkdir -p "${TMP_DIR}" "${NORMALIZED_SCRIPTS_DIR}"

# Resolve a sibling script to a CR-stripped copy under TMP_DIR. This makes
# verify-branch.sh resilient to a working tree that was checked out on
# Windows under core.autocrlf=true before .gitattributes was in place. On
# Linux/macOS checkouts the files are already LF and the sed step is a
# no-op rewrite.
sibling_script() {
  local src="${SCRIPT_DIR}/$1"
  local dst="${NORMALIZED_SCRIPTS_DIR}/$1"
  if [[ ! -f "${src}" ]]; then
    echo "❌ Required sibling script not found: ${src}" >&2
    return 1
  fi
  sed 's/\r$//' "${src}" > "${dst}"
  chmod +x "${dst}"
  echo "${dst}"
}

# Resolve a Gradle wrapper launcher under <dir>. If the on-disk gradlew has
# CRLF endings (Windows checkout under autocrlf=true before .gitattributes
# was in place) the Linux kernel cannot execute it, so we fall back to a
# CR-stripped copy adjacent to the original. The wrapper jar / properties
# next to the original gradlew are picked up unchanged.
resolve_gradlew() {
  local dir="${1}"
  local launcher="${dir}/gradlew"
  if [[ ! -f "${launcher}" ]]; then
    echo "❌ gradlew not found at ${launcher}" >&2
    return 1
  fi
  if head -c 200 "${launcher}" | grep -q $'\r'; then
    local stripped="${dir}/gradlew.lf"
    sed 's/\r$//' "${launcher}" > "${stripped}"
    chmod +x "${stripped}"
    echo "${stripped}"
  else
    echo "${launcher}"
  fi
}

GRADLEW=$(resolve_gradlew "${PROJECT_ROOT}")
if [[ -d "${PROJECT_ROOT}/grails-forge" ]]; then
  GRADLEW_FORGE=$(resolve_gradlew "${PROJECT_ROOT}/grails-forge")
else
  GRADLEW_FORGE=""
fi

step_no=0
banner() {
  step_no=$((step_no + 1))
  echo
  echo "============================================================"
  echo "▶ Step ${step_no}: $1"
  echo "============================================================"
}

cleanup() {
  echo
  echo "❌❌❌ Branch verification FAILED at step ${step_no}."
  echo "    Branch: ${BRANCH}"
  echo "    SOURCE_DATE_EPOCH: ${SOURCE_DATE_EPOCH}"
  echo "    Fix the failure above and rerun before tagging."
}
trap cleanup ERR

echo "============================================================"
echo "Pre-tag verification for branch: ${BRANCH}"
echo "Project root: ${PROJECT_ROOT}"
echo "SOURCE_DATE_EPOCH: ${SOURCE_DATE_EPOCH} ($(date -u -d "@${SOURCE_DATE_EPOCH}" 2>/dev/null || date -u -r "${SOURCE_DATE_EPOCH}" 2>/dev/null || echo unknown))"
echo "============================================================"

# Pre-flight: warn about untracked / modified files. RAT scans the entire
# working tree (not just git-tracked files), so untracked debris (AI tool
# configs, work-in-progress dirs, IDE state) would falsely fail RAT even
# though the tracked branch is clean. The release CI runs on a fresh
# checkout and will not see these files.
if command -v git >/dev/null 2>&1 && git -C "${PROJECT_ROOT}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  UNTRACKED=$(git -C "${PROJECT_ROOT}" ls-files --others --exclude-standard | head -20 || true)
  MODIFIED=$(git -C "${PROJECT_ROOT}" diff --name-only HEAD | head -20 || true)
  if [[ -n "${UNTRACKED}" || -n "${MODIFIED}" ]]; then
    echo
    echo "⚠️  Working tree is not clean. RAT scans every file regardless of"
    echo "   git tracking, so any debris below would falsely fail RAT even"
    echo "   though the tracked branch is clean. The release CI uses a"
    echo "   fresh checkout and will not see these files."
    echo
    if [[ -n "${UNTRACKED}" ]]; then
      echo "   Untracked files (first 20):"
      echo "${UNTRACKED}" | sed 's/^/      /'
    fi
    if [[ -n "${MODIFIED}" ]]; then
      echo "   Modified tracked files (first 20):"
      echo "${MODIFIED}" | sed 's/^/      /'
    fi
    echo
    echo "   To verify against the tracked branch only, either:"
    echo "     1. Stash or remove the untracked files before rerunning, OR"
    echo "     2. Clone a fresh worktree:"
    echo "          git worktree add /tmp/branch-verify ${BRANCH}"
    echo "          cd /tmp/branch-verify && etc/bin/verify-branch.sh"
    echo
  else
    echo "✅ Working tree is clean (no untracked / modified files)"
  fi
else
  echo "ℹ️  Not inside a git work tree, skipping cleanliness check"
fi

banner "Check dependencies.gradle for SNAPSHOT versions"
if [[ ! -f dependencies.gradle ]]; then
  echo "❌ dependencies.gradle not found at project root"
  exit 1
fi
# Only flag uncommented lines that declare a -SNAPSHOT dependency version.
# Tolerate the word SNAPSHOT in comments or in property keys.
if grep -nE "^[^/]*['\"][^'\"]*-SNAPSHOT['\"]" dependencies.gradle >/tmp/branch-verify-snapshots 2>/dev/null && [[ -s /tmp/branch-verify-snapshots ]]; then
  echo "❌ Found SNAPSHOT versions in dependencies.gradle. Per the Apache Release"
  echo "   Policy, all dependencies must be released versions before tagging."
  cat /tmp/branch-verify-snapshots
  rm -f /tmp/branch-verify-snapshots
  exit 1
fi
rm -f /tmp/branch-verify-snapshots
echo "✅ No SNAPSHOT dependencies declared in dependencies.gradle"

banner "Verify KEYS file matches canonical SVN copy"
# Compare content (line-ending-insensitive). The release CI builds on Linux
# where files are LF, so the staged release's KEYS will be LF regardless of
# how the contributor's local working tree was checked out. We therefore
# normalize both sides to LF before hashing.
mkdir -p "${TMP_DIR}/keys"
curl -fsSL -o "${TMP_DIR}/keys/SVN_KEYS" "https://dist.apache.org/repos/dist/release/grails/KEYS"
SVN_HASH=$(tr -d '\r' < "${TMP_DIR}/keys/SVN_KEYS" | shasum -a 512 | awk '{print $1}')
if [[ ! -f "${PROJECT_ROOT}/KEYS" ]]; then
  echo "❌ ${PROJECT_ROOT}/KEYS not found"
  exit 1
fi
LOCAL_HASH=$(tr -d '\r' < "${PROJECT_ROOT}/KEYS" | shasum -a 512 | awk '{print $1}')
if [[ "${SVN_HASH}" != "${LOCAL_HASH}" ]]; then
  echo "❌ KEYS file content drift between in-tree and canonical SVN copy"
  echo "   in-tree (LF-normalized): ${LOCAL_HASH}"
  echo "   SVN     (LF-normalized): ${SVN_HASH}"
  echo "   The in-tree KEYS file at ${PROJECT_ROOT}/KEYS must be updated"
  echo "   to match https://dist.apache.org/repos/dist/release/grails/KEYS"
  echo "   before tagging a release."
  exit 1
fi
echo "✅ KEYS content matches canonical SVN copy"

banner "Apache RAT license audit (gradle rat)"
"${GRADLEW}" rat "${GRADLE_PROPS[@]}"
echo "✅ RAT passed"

banner "Code style (gradle codeStyle)"
"${GRADLEW}" codeStyle "${GRADLE_PROPS[@]}"
echo "✅ Code style passed"

if ! $SKIP_BUILD; then
  banner "grails-core assemble (matches release.yml publish step)"
  "${GRADLEW}" assemble "${GRADLE_PROPS[@]}"
  echo "✅ grails-core assemble passed"

  if [[ -n "${GRADLEW_FORGE}" ]]; then
    banner "grails-forge assemble (matches release.yml publish step)"
    pushd grails-forge >/dev/null
    "${GRADLEW_FORGE}" assemble "${GRADLE_PROPS[@]}"
    popd >/dev/null
    echo "✅ grails-forge assemble passed"
  else
    echo "⚠️  grails-forge directory not found - skipping forge assemble"
  fi

  banner "grails-doc build (matches release.yml publish step)"
  "${GRADLEW}" grails-doc:build "${GRADLE_PROPS[@]}"
  echo "✅ grails-doc build passed"
fi

if $INCLUDE_REPRODUCIBILITY; then
  banner "Reproducibility check (etc/bin/test-reproducible-builds.sh)"
  "$(sibling_script test-reproducible-builds.sh)"
  echo "✅ Reproducibility check passed"
fi

if $INCLUDE_TESTS; then
  banner "Full test suite (gradle test)"
  "${GRADLEW}" test "${GRADLE_PROPS[@]}"
  echo "✅ Tests passed"
fi

trap - ERR

echo
echo "============================================================"
echo "✅✅✅ Pre-tag verification PASSED for branch ${BRANCH}"
echo "============================================================"
echo
echo "Next steps:"
echo "  1. Confirm the staged release version in gradle.properties is the"
echo "     intended target (e.g. 8.0.0-M1)."
echo "  2. Publish the GitHub release with the matching tag (e.g. v8.0.0-M1)."
echo "     This will kick off .github/workflows/release.yml."
echo "  3. Once the publish + source + upload jobs complete, run:"
echo "       verify.sh v<version> <download-location>"
echo "     in this container to verify the staged artifacts."
