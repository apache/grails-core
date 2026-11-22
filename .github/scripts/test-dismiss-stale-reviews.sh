#!/bin/bash

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

# Tests the step of .github/workflows/dismiss-stale-reviews.yml. The step's script is taken from the workflow file as
# it is, and run against small repositories built here: its functions for the checks of the merges, and the whole
# step, with a stand-in for gh that answers from files, for what it dismisses, keeps and writes. Needs bash, git
# 2.38 or later, jq, GNU coreutils, and ruby or python3 with PyYAML to read the workflow file.
set -u
here=$(cd "$(dirname "$0")/../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
workflow="$here/.github/workflows/dismiss-stale-reviews.yml"
if command -v ruby > /dev/null; then
  ruby -ryaml -e 'puts YAML.load_file(ARGV[0])["jobs"]["dismiss"]["steps"][0]["run"]' "$workflow" > "$work/step.sh"
else
  python3 -c 'import sys, yaml; print(yaml.safe_load(open(sys.argv[1]))["jobs"]["dismiss"]["steps"][0]["run"])' "$workflow" > "$work/step.sh"
fi
# The functions of the step: everything before it creates its repository.
sed '/^git init --quiet --bare merges.git$/,$d' "$work/step.sh" > "$work/functions.sh"
grep -q '^only_merges_base()' "$work/functions.sh" || { echo "the step's functions were not found in $workflow"; exit 1; }

# The stand-in for gh. It answers the requests the step makes from files in $FAKE (the stale approvals, the reviews,
# the heads and the comments of the pull requests) and writes the dismissals it is asked for to a log.
mkdir -p "$work/bin"
cat > "$work/bin/gh" << 'EOF'
#!/bin/bash
shift
method=GET path= jq= body= paginate=
while (($#)); do
  case $1 in
    -X) method=$2; shift 2 ;;
    --paginate) paginate=1; shift ;;
    --jq) jq=$2; shift 2 ;;
    -f) case $2 in body=*) body=${2#body=}; method=POST ;; message=*) message=${2#message=} ;; esac; shift 2 ;;
    -F) shift 2 ;;
    *) path=$1; shift ;;
  esac
done
case $method:$path in
  GET:graphql) cat "$FAKE/stale.tsv" ;;
  GET:repos/*/pulls/*/reviews/*) awk -v id="${path##*/}" '$1 == id { print $2 " " $3 }' "$FAKE/reviews" ;;
  GET:repos/*/pulls/*) awk -v n="${path##*/}" '$1 == n { print $2 }' "$FAKE/heads" ;;
  PUT:repos/*/dismissals) echo "${path%/dismissals} $message" >> "$FAKE/dismissed" ;;
  GET:repos/*/issues/*/comments) jq -r "$jq" "$FAKE/comments.json" ;;
  POST:repos/*/issues/*/comments)
    # Another run that writes the same comment at the same time: its comment comes first.
    if [[ -e "$FAKE/race" ]]; then
      jq --arg b "$body" '. + [{id: ((map(.id) | max // 0) + 1), user: {login: "github-actions[bot]"}, body: $b}]' "$FAKE/comments.json" > "$FAKE/c" && mv "$FAKE/c" "$FAKE/comments.json"
      rm "$FAKE/race"
    fi
    jq --arg b "$body" '. + [{id: ((map(.id) | max // 0) + 1), user: {login: "github-actions[bot]"}, body: $b}]' "$FAKE/comments.json" > "$FAKE/c" && mv "$FAKE/c" "$FAKE/comments.json" ;;
  DELETE:repos/*/issues/comments/*)
    jq --argjson id "${path##*/}" 'map(select(.id != $id))' "$FAKE/comments.json" > "$FAKE/c" && mv "$FAKE/c" "$FAKE/comments.json" ;;
  *) echo "unexpected request: $method $path" >&2; exit 1 ;;
esac
EOF
chmod +x "$work/bin/gh"
export PATH="$work/bin:$PATH" FAKE="$work/fake" REPO=x/y DRY_RUN=false GH_TOKEN=token GITHUB_SERVER_URL="file://$work"
mkdir -p "$FAKE"

# The repository the step fetches from, at $GITHUB_SERVER_URL/$REPO.
src="$work/x/y"
git init -q -b main "$src"
git -C "$src" config user.email test@example.com
git -C "$src" config user.name test
git -C "$src" config uploadpack.allowFilter true
git -C "$src" config uploadpack.allowAnySHA1InWant true
git -C "$src" commit -q --allow-empty -m root
root=$(git -C "$src" rev-parse HEAD)
g() { git -C "$src" "$@"; }
write() { printf "$2" > "$src/$1"; }

failures=0 checks=0
# Runs the check of the merges from approved to head, in a fresh repository, and compares its answer.
check() {
  local name=$1 approved=$2 head=$3 base=$4 expected=$5 wanted_reason=${6:-} answer
  answer=$(
    cd "$(mktemp -d "$work/run.XXXXXX")" || exit 1
    source "$work/functions.sh"
    git init --quiet --bare merges.git && git -C merges.git config gc.auto 0 && git -C merges.git remote add origin "file://$src"
    [[ -n "${FAIL:-}" ]] && eval "should_fail() { set -- \"\$@\" '' '' '' '' '' '' '' ''; $FAIL; }" && bounded() {
      if should_fail "$@"; then return 124; fi
      local left=$(( deadline - SECONDS )); (( left > 0 )) || return 124; timeout "$left" "$@"
    }
    if checked_merges "$base" "$approved" "$head"; then echo kept; else echo "dismissed: $reason"; fi
    [[ ! -e marker.attributes ]] || echo "marker.attributes left behind"
  )
  checks=$((checks + 1))
  if [[ "$answer" != "$expected"* || ( -n "$wanted_reason" && "$answer" != *"$wanted_reason"* ) || "$answer" == *"left behind"* ]]; then
    failures=$((failures + 1)); echo "FAIL $name: $answer (expected $expected${wanted_reason:+: $wanted_reason})"
  else
    echo "ok   $name: $answer"
  fi
}
changed="the pull request changed after this approval"
unchecked="the commits after this approval could not be checked"

# A pull request and its base branch from a common file, the pull request approved, and the base branch merged into
# it with the file as given (a printf format), or left as git merges it with "-".
merge_case() {
  local name=$1 file=$2 common=$3 pr=$4 base=$5 pushed=$6 expected=$7 wanted_reason=${8:-} start approved tip
  g checkout -q -B "common-$name" "$root"; write "$file" "$common"; g add -A; g commit -q -m "common $name"; start=$(g rev-parse HEAD)
  g checkout -q -B "base-$name" "$start"; write "$file" "$base"; g commit -q -am "base $name"; tip=$(g rev-parse HEAD)
  g checkout -q -B "pr-$name" "$start"; write "$file" "$pr"; g commit -q -am "pr $name"; approved=$(g rev-parse HEAD)
  g merge -q --no-edit "base-$name" > /dev/null 2>&1
  [[ "$pushed" == - ]] || write "$file" "$pushed"
  g add -A; g commit -q -m "Merge base-$name"
  check "$name" "$approved" "$(g rev-parse HEAD)" "$tip" "$expected" "$wanted_reason"
}
lines='a\nb\nc\nd\ne\nf\ng\nh\n'
echo "== resolutions"
merge_case ours f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\nG\nh\n' 'a\nPR\nc\nd\ne\nf\nG\nh\n' kept
merge_case theirs f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\nG\nh\n' 'a\nBASE\nc\nd\ne\nf\nG\nh\n' kept
merge_case both f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nPR\nBASE\nc\nd\ne\nf\ng\nh\n' kept
merge_case both-reversed f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nPR\nc\nd\ne\nf\ng\nh\n' kept
merge_case by-hand f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nboth\nc\nd\ne\nf\ng\nh\n' dismissed "$changed"
merge_case line-before f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nextra\nPR\nc\nd\ne\nf\ng\nh\n' dismissed "$changed"
merge_case line-repeated f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nPR\nPR\nc\nd\ne\nf\ng\nh\n' dismissed "$changed"
merge_case side-reordered f "$lines" 'a\nP1\nP2\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nP2\nP1\nc\nd\ne\nf\ng\nh\n' dismissed "$changed"
merge_case side-in-part f "$lines" 'a\nP1\nP2\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nP1\nc\nd\ne\nf\ng\nh\n' dismissed "$changed"
merge_case edit-outside f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nPR\nc\nd\ne\nF\ng\nh\n' dismissed "$changed"
merge_case markers-left f "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' - dismissed "$changed"
merge_case two-conflicts f "$lines" 'a\nPR\nc\nd\ne\nf\nPR\nh\n' 'a\nBASE\nc\nd\ne\nf\nBASE\nh\n' 'a\nPR\nc\nd\ne\nf\nBASE\nh\n' kept
merge_case two-conflicts-edit f "$lines" 'a\nPR\nc\nd\ne\nf\nPR\nh\n' 'a\nBASE\nc\nd\ne\nf\nBASE\nh\n' 'a\nPR\nc\nD\ne\nf\nBASE\nh\n' dismissed "$changed"
echo "== lines that look like numbers"
numbers='a\n42\nc\nd\ne\nf\ng\nh\n'
merge_case number-outside f "$numbers" 'a\n42\nc\nd\ne\nf\nPR\nh\n' 'a\n42\nc\nd\ne\nf\nBASE\nh\n' 'a\n042\nc\nd\ne\nf\nPR\nh\n' dismissed "$changed"
merge_case number-forms f 'a\n 42\n+42\n1e2\n4.20\n0x10\nx\nh\n' 'a\n 42\n+42\n1e2\n4.20\n0x10\nPR\nh\n' 'a\n 42\n+42\n1e2\n4.20\n0x10\nBASE\nh\n' 'a\n42\n42\n100\n4.2\n16\nPR\nh\n' dismissed "$changed"
merge_case number-in-side f "$lines" 'a\n42\nc\nd\ne\nf\ng\nh\n' 'a\n43\nc\nd\ne\nf\ng\nh\n' 'a\n042\nc\nd\ne\nf\ng\nh\n' dismissed "$changed"
merge_case number-side-kept f "$lines" 'a\n42\nc\nd\ne\nf\ng\nh\n' 'a\n43\nc\nd\ne\nf\ng\nh\n' 'a\n42\nc\nd\ne\nf\ng\nh\n' kept
echo "== the end of the file"
merge_case newline-dropped f 'a\nx\n' 'a\nours\n' 'a\ntheirs\n' 'a\nours' dismissed "$changed"
merge_case newline-kept f 'a\nx\n' 'a\nours\n' 'a\ntheirs\n' 'a\nours\n' kept
merge_case side-without-newline f 'a\nx\n' 'a\nours' 'a\ntheirs\n' 'a\nours' kept
merge_case newline-added f 'a\nx\n' 'a\nours' 'a\ntheirs\n' 'a\nours\n' dismissed "$changed"
merge_case crlf f 'a\r\nx\r\nb\r\n' 'a\r\nours\r\nb\r\n' 'a\r\ntheirs\r\nb\r\n' 'a\r\ntheirs\r\nb\r\n' kept
merge_case crlf-to-lf f 'a\r\nx\r\nb\r\n' 'a\r\nours\r\nb\r\n' 'a\r\ntheirs\r\nb\r\n' 'a\r\ntheirs\nb\r\n' dismissed "$changed"
echo "== lines like conflict markers"
merge_case heading-ours doc.adoc 'i\nOld\n---\nbody\n' 'i\nNew\n=======\nbody\n' 'i\nOther\n-----\nbody\n' 'i\nNew\n=======\nbody\n' kept
merge_case heading-theirs doc.adoc 'i\nOld\n---\nbody\n' 'i\nNew\n=======\nbody\n' 'i\nOther\n-----\nbody\n' 'i\nOther\n-----\nbody\n' kept
merge_case heading-in-part doc.adoc 'i\nOld\n---\nbody\n' 'i\nNew\n=======\nmore\nbody\n' 'i\nOther\n-----\nbody\n' 'i\nNew\nbody\n' dismissed "$changed"
merge_case nine-and-seven doc.adoc 'i\nOld\n---\nbody\n' 'i\nNew\n=========\n=======\nbody\n' 'i\nOther\n-----\nbody\n' 'i\nNew\n=========\n=======\nbody\n' kept
merge_case less-than doc.adoc 'i\nOld\nbody\n' 'i\n<<<<<<< not a marker\nNew\nbody\n' 'i\nOther\nbody\n' 'i\n<<<<<<< not a marker\nNew\nbody\n' kept
merge_case huge-run doc.adoc 'i\nOld\nbody\n' "i\nNew\n$(printf '%*s' 200000 '' | tr ' ' '=')\nbody\n" 'i\nOther\nbody\n' "i\nNew\n$(printf '%*s' 200000 '' | tr ' ' '=')\nbody\n" kept
echo "== file names"
merge_case unicode-name 'café.md' "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' kept
merge_case space-name 'a file.md' "$lines" 'a\nPR\nc\nd\ne\nf\ng\nh\n' 'a\nBASE\nc\nd\ne\nf\ng\nh\n' 'a\nPR\nc\nd\ne\nf\ng\nh\n' kept

echo "== a renamed file"
g checkout -q -B common-rename "$root"; { echo title; seq -f 'body %g' 20; } > "$src/old.adoc"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q -B base-rename "$start"; sed -i 's/^title$/Base title\n=======/' "$src/old.adoc"; g commit -q -am base; tip=$(g rev-parse HEAD); base_file=$(cat "$src/old.adoc")
g checkout -q -B pr-rename "$start"; g mv old.adoc new.adoc; sed -i 's/^title$/PR title/' "$src/new.adoc"; g commit -q -am pr; approved=$(g rev-parse HEAD)
g merge -q --no-edit base-rename > /dev/null 2>&1; printf '%s\n' "$base_file" > "$src/new.adoc"; g add -A; g commit -q -m merge
check rename-with-heading "$approved" "$(g rev-parse HEAD)" "$tip" kept

echo "== more merges, and other commits"
g checkout -q -B base-chain "$root"; write chain "$lines"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q -B pr-chain "$start"; write chain 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g commit -q -am pr; approved=$(g rev-parse HEAD)
g checkout -q base-chain; write chain 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; g commit -q -am base
g checkout -q pr-chain; g merge -q --no-edit base-chain > /dev/null 2>&1; write chain 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; g commit -q -am merge
for i in $(seq 1 25); do g checkout -q base-chain; echo "$i" > "$src/n$i"; g add -A; g commit -q -m "n$i"; g checkout -q pr-chain; g merge -q --no-edit base-chain > /dev/null; done
check 26-merges "$approved" "$(g rev-parse pr-chain)" "$(g rev-parse base-chain)" kept
g checkout -q pr-chain; echo own > "$src/own"; g add -A; g commit -q -m "an ordinary commit"
g checkout -q base-chain; echo more > "$src/more"; g add -A; g commit -q -m more; g checkout -q pr-chain; g merge -q --no-edit base-chain > /dev/null
check ordinary-commit-then-merge "$approved" "$(g rev-parse pr-chain)" "$(g rev-parse base-chain)" dismissed "$changed"
g checkout -q -B other "$root"; echo other > "$src/other"; g add -A; g commit -q -m other
g checkout -q -B pr-other "$approved"; g merge -q --no-edit other > /dev/null
check merge-of-another-branch "$approved" "$(g rev-parse pr-other)" "$(g rev-parse base-chain)" dismissed "$changed"

echo "== failures"
g checkout -q -B fail-base "$root"; write fail "$lines"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q fail-base; write fail 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; g commit -q -am base; tip=$(g rev-parse HEAD)
g checkout -q -B fail-pr "$start"; write fail 'a\nPR\nc\nd\ne\nf\ng\nh\n=======\n'; g commit -q -am pr; approved=$(g rev-parse HEAD)
g merge -q --no-edit fail-base > /dev/null 2>&1; write fail 'a\nBASE\nc\nd\ne\nf\ng\nh\n=======\n'; g commit -q -am merge; head=$(g rev-parse HEAD)
check failures-control "$approved" "$head" "$tip" kept
FAIL='[[ "$4" == fetch && ! -e fetched ]] && : > fetched' check fetch-fails-once "$approved" "$head" "$tip" kept
FAIL='[[ "$4" == fetch ]]' check fetch-fails-twice "$approved" "$head" "$tip" dismissed "$unchecked"
FAIL='[[ "$4" == merge-base ]]' check merge-base-times-out "$approved" "$head" "$tip" dismissed "$unchecked"
FAIL='[[ " $* " == *" merge-tree "* ]]' check merge-tree-times-out "$approved" "$head" "$tip" dismissed "$unchecked"
FAIL='[[ " $* " == *" merge-tree "* ]] || return 1; [[ -e first ]] && return 0; : > first; return 1' check widened-merge-times-out "$approved" "$head" "$tip" dismissed "$unchecked"
FAIL='[[ "$4" == cat-file && "$6" != *:* ]]' check side-read-times-out "$approved" "$head" "$tip" dismissed "$unchecked"
FAIL='[[ "$4" == cat-file && "$6" == *:* ]]' check conflicted-file-read-times-out "$approved" "$head" "$tip" dismissed "$unchecked"
FAIL='[[ "$4" == ls-tree ]]' check ls-tree-times-out "$approved" "$head" "$tip" dismissed "$unchecked"
FAIL='[[ "$1" == awk ]]' check awk-times-out "$approved" "$head" "$tip" dismissed "$unchecked"

echo "== two merge bases"
# The pull request and the base branch merged each other; the pull request's cross merge reverts the base's line 30.
g checkout -q -B cross-root "$root"; seq -f 'line %g' 40 > "$src/cross"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q -B cross-pr "$start"; sed -i 's/^line 5$/pr 5/' "$src/cross"; g commit -q -am pr1; p1=$(g rev-parse HEAD)
g checkout -q -B cross-base "$start"; sed -i 's/^line 30$/base 30/' "$src/cross"; g commit -q -am base1; b1=$(g rev-parse HEAD)
g checkout -q cross-pr; g merge -q --no-edit "$b1" > /dev/null; sed -i 's/^base 30$/line 30/' "$src/cross"; g commit -q --amend -am "merge base1, reverting line 30"
g checkout -q cross-base; g merge -q --no-edit "$p1" > /dev/null
g checkout -q cross-pr; sed -i 's/^line 10$/pr 10/' "$src/cross"; g commit -q -am pr2; approved=$(g rev-parse HEAD)
g checkout -q cross-base; sed -i 's/^line 10$/base 10/' "$src/cross"; g commit -q -am base2; tip=$(g rev-parse HEAD)
for how in kept undone; do
  g checkout -q -B "cross-$how" "$approved"; g merge -q --no-edit cross-base > /dev/null 2>&1
  awk '/^<<<<<<< /{s=1; next} /^=======$/{s=2; next} /^>>>>>>> /{s=0; next} s!=2' "$src/cross" > "$src/cross.tmp" && mv "$src/cross.tmp" "$src/cross"
  [[ $how == kept ]] || sed -i 's/^line 30$/base 30/' "$src/cross"
  g commit -q -am "merge cross-base"
done
check two-bases-whole-side "$approved" "$(g rev-parse cross-kept)" "$tip" kept
check two-bases-edit-of-one-base "$approved" "$(g rev-parse cross-undone)" "$tip" dismissed "$changed"

echo "== the same files as the approved commit"
# Run as the step decides for an approval whose commit has the tree of the head.
same() {
  local name=$1 approved=$2 head=$3 base=$4 expected=$5 answer
  answer=$(
    cd "$(mktemp -d "$work/run.XXXXXX")" || exit 1
    source "$work/functions.sh"
    git init --quiet --bare merges.git && git -C merges.git config gc.auto 0 && git -C merges.git remote add origin "file://$src"
    deadline=$(( SECONDS + 60 ))
    fork=0; same_fork "$base" "$approved" "$head" || fork=$?
    if (( fork == 0 )); then echo "kept"; elif checked_merges "$base" "$approved" "$head"; then echo kept; else echo "dismissed $fork"; fi
  )
  checks=$((checks + 1))
  if [[ "$answer" == "$expected"* ]]; then echo "ok   $name: $answer"; else failures=$((failures + 1)); echo "FAIL $name: $answer (expected $expected)"; fi
}
g checkout -q -B same-base "$root"; write same "$lines"; echo y > "$src/y"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q -B same-pr "$start"; write same 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g commit -q -am pr; approved=$(g rev-parse HEAD)
g checkout -q same-base; write same 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; echo changed > "$src/y"; g commit -q -am base; tip=$(g rev-parse HEAD)
g checkout -q -B same-reworded "$approved"; g commit -q --amend -m "pr, reworded"
same reworded "$approved" "$(g rev-parse HEAD)" "$tip" kept
g checkout -q -B same-undone "$approved"; g merge -q -s ours --no-edit same-base
same undoing-merge "$approved" "$(g rev-parse HEAD)" "$tip" "dismissed 1"
g checkout -q -B same-rebased "$tip"; g checkout -q "$approved" -- same y; g commit -q -m "pr, rebased with its old files"
same rebase-with-old-files "$approved" "$(g rev-parse HEAD)" "$tip" "dismissed 1"

echo "== the whole step"
# Three approvals: one kept across a merge of a file with a space in its name, one followed by an ordinary commit,
# and one at a commit with the very files of the head but whose history cannot be fetched.
g checkout -q -B step-base "$root"; write 'a file.md' "$lines"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q step-base; write 'a file.md' 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; g commit -q -am base; tip=$(g rev-parse HEAD)
g checkout -q -B step-kept "$start"; write 'a file.md' 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g commit -q -am pr; kept_approved=$(g rev-parse HEAD)
g merge -q --no-edit step-base > /dev/null 2>&1; write 'a file.md' 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g commit -q -am merge; kept_head=$(g rev-parse HEAD)
g checkout -q -B step-changed "$kept_approved"; echo own > "$src/own"; g add -A; g commit -q -m own; changed_head=$(g rev-parse HEAD)
unknown=0000000000000000000000000000000000000001
step() {
  ( cd "$(mktemp -d "$work/run.XXXXXX")" && SHA= GITHUB_STEP_SUMMARY="$FAKE/summary" bash -e "$work/step.sh" > "$FAKE/output" 2>&1 ) || echo "the step failed: $(cat "$FAKE/output")"
}
expect() {
  checks=$((checks + 1))
  if eval "$2"; then echo "ok   $1"; else failures=$((failures + 1)); echo "FAIL $1"; sed 's/^/     /' "$FAKE/output"; fi
}
printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' 1 "$kept_head" 11 reviewer "$kept_approved" changed "$tip" \
  2 "$changed_head" 21 reviewer "$kept_approved" changed "$tip" \
  3 "$changed_head" 31 reviewer "$unknown" same "$tip" > "$FAKE/stale.tsv"
printf '11 APPROVED %s\n21 APPROVED %s\n31 APPROVED %s\n' "$kept_approved" "$kept_approved" "$unknown" > "$FAKE/reviews"
printf '1 %s\n2 %s\n3 %s\n' "$kept_head" "$changed_head" "$changed_head" > "$FAKE/heads"
echo '[]' > "$FAKE/comments.json"; : > "$FAKE/dismissed"; : > "$FAKE/summary"; : > "$FAKE/race"
step
expect "the kept approval is not dismissed" '! grep -q "/pulls/1/" "$FAKE/dismissed"'
expect "the summary names the file with a space" 'grep -q "resolved a file.md" "$FAKE/summary"'
expect "an ordinary commit dismisses the approval as a change" 'grep -q "/pulls/2/reviews/21 Dismissed automatically: $changed" "$FAKE/dismissed"'
expect "the same files whose history cannot be read are not called a change" 'grep -q "/pulls/3/reviews/31 Dismissed automatically: $unchecked" "$FAKE/dismissed"'
expect "one comment is left for the kept merge when another run wrote it too" '[[ $(jq "[.[] | select(.body | contains(\"kept-resolution:$kept_head\"))] | length" "$FAKE/comments.json") == 1 ]]'
expect "the comment names the file with a space" 'jq -r ".[].body" "$FAKE/comments.json" | grep -q "\`a file.md\`"'
# The approval is moved to a new head while the step checks it: it is left to the next run.
printf '21 APPROVED %s\n31 APPROVED %s\n' "$changed_head" "$unknown" > "$FAKE/reviews"; : > "$FAKE/dismissed"
step
expect "an approval moved while it was checked is not dismissed" '! grep -q "/pulls/2/" "$FAKE/dismissed"'
expect "a second run writes no second comment" '[[ $(jq length "$FAKE/comments.json") == 1 ]]'

echo
echo "$((checks - failures)) of $checks checks passed"
(( failures == 0 ))
