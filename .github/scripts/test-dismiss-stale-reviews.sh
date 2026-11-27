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
bash -n "$work/step.sh" || { echo "the step's script in $workflow does not parse"; exit 1; }
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
echo "$*" >> "$FAKE/requests"
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
  GET:repos/*/pulls/*) awk -v n="${path##*/}" '$1 == n { print "open " $2 " " $3 }' "$FAKE/heads" ;;
  GET:repos/*/git/ref/heads/*) awk -v ref="${path#*/git/ref/heads/}" '$1 == ref { print $2 }' "$FAKE/bases" ;;
  PUT:repos/*/dismissals) echo "${path%/dismissals} $message" >> "$FAKE/dismissed" ;;
  GET:repos/*/issues/*/comments)
    jq -r "$jq" "$FAKE/comments.json"
    # A push while the comments are read: the head of pull request 1 moves.
    if [[ -e "$FAKE/move-on-list" ]]; then awk '$1 == 1 { $2 = "moved" } 1' "$FAKE/heads" > "$FAKE/h" && mv "$FAKE/h" "$FAKE/heads"; rm "$FAKE/move-on-list"; fi ;;
  POST:repos/*/issues/*/comments)
    if [[ -e "$FAKE/fail-post" ]]; then rm "$FAKE/fail-post"; exit 1; fi
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
# A tail that fails to read the last byte of a file, for the check of a failed read.
mkdir -p "$work/failbin"
printf '#!/bin/bash\n[[ "$1" == -c ]] && exit 1\nexec /usr/bin/tail "$@"\n' > "$work/failbin/tail"
chmod +x "$work/failbin/tail"
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
    [[ -z "${FAIL_TAIL:-}" ]] || PATH="$work/failbin:$PATH"
    [[ -n "${FAIL:-}" ]] && eval "should_fail() { set -- \"\$@\" '' '' '' '' '' '' '' ''; $FAIL; }" && bounded() {
      if should_fail "$@"; then return 124; fi
      local left=$(( deadline - SECONDS )); (( left > 0 )) || return 124; timeout "$left" "$@"
    }
    if checked_merges "$base" "$approved" "$head"; then echo "kept: $(tr '\n' '|' <<< "$accepted" | sed 's/[0-9a-f]\{40\} //g')"; else echo "dismissed: $reason"; fi
    [[ ! -s merges.git/info/attributes ]] || echo "info/attributes left behind"
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
# One changed line each, so that each spelling is looked at.
for pair in ' 42:42' '+42:42' '1e2:100' '4.20:4.2' '0x10:16' '1.0:1'; do
  was=${pair%%:*} now=${pair#*:}
  merge_case "number-${was// /space}" f "a\n$was\nc\nd\ne\nf\nx\nh\n" "a\n$was\nc\nd\ne\nf\nPR\nh\n" "a\n$was\nc\nd\ne\nf\nBASE\nh\n" "a\n$now\nc\nd\ne\nf\nPR\nh\n" dismissed "$changed"
done
merge_case number-in-side f "$lines" 'a\n42\nc\nd\ne\nf\ng\nh\n' 'a\n43\nc\nd\ne\nf\ng\nh\n' 'a\n042\nc\nd\ne\nf\ng\nh\n' dismissed "$changed"
merge_case number-side-kept f "$lines" 'a\n42\nc\nd\ne\nf\ng\nh\n' 'a\n43\nc\nd\ne\nf\ng\nh\n' 'a\n42\nc\nd\ne\nf\ng\nh\n' kept
echo "== the end of the file"
merge_case newline-dropped f 'a\nx\n' 'a\nours\n' 'a\ntheirs\n' 'a\nours' dismissed "$changed"
merge_case newline-kept f 'a\nx\n' 'a\nours\n' 'a\ntheirs\n' 'a\nours\n' kept
merge_case side-without-newline f 'a\nx\n' 'a\nours' 'a\ntheirs\n' 'a\nours' kept
merge_case newline-added f 'a\nx\n' 'a\nours' 'a\ntheirs\n' 'a\nours\n' dismissed "$changed"
# One side deletes the last line, the other replaces it with a line without a newline.
merge_case empty-side-newline-added f 'a\nx\n' 'a\n' 'a\ny' 'a\ny\n' dismissed "$changed"
merge_case empty-side-newline-kept f 'a\nx\n' 'a\n' 'a\ny' 'a\ny' kept
merge_case empty-side-dropped f 'a\nx\n' 'a\n' 'a\ny' 'a\n' kept
# The file ends in a conflict, and a side has no newline at its end: keeping that side as it is keeps the approval.
merge_case theirs-without-newline f 'a\nx\n' 'a\nours\n' 'a\ntheirs' 'a\ntheirs' kept
merge_case theirs-without-newline-added f 'a\nx\n' 'a\nours\n' 'a\ntheirs' 'a\ntheirs\n' dismissed "$changed"
merge_case both-without-newline-ours f 'a\nx' 'a\nours' 'a\ntheirs' 'a\nours' kept
merge_case both-without-newline-theirs f 'a\nx' 'a\nours' 'a\ntheirs' 'a\ntheirs' kept
merge_case common-without-newline f 'a\nx' 'a\nours\n' 'a\ntheirs' 'a\ntheirs' kept
merge_case ours-then-theirs-without-newline f 'a\nx\n' 'a\nours\n' 'a\ntheirs' 'a\nours\ntheirs' kept
merge_case theirs-then-ours-without-newline f 'a\nx\n' 'a\nours' 'a\ntheirs\n' 'a\ntheirs\nours' kept
merge_case crlf-without-newline f 'a\r\nx\r\n' 'a\r\nours\r\n' 'a\r\ntheirs' 'a\r\ntheirs' kept
merge_case crlf-without-newline-added f 'a\r\nx\r\n' 'a\r\nours\r\n' 'a\r\ntheirs' 'a\r\ntheirs\r\n' dismissed "$changed"
merge_case crlf-without-newline-then-other f 'a\r\nx\r\n' 'a\r\nours' 'a\r\ntheirs\r\n' 'a\r\nours\r\ntheirs\r\n' kept
merge_case crlf-other-then-without-newline f 'a\r\nx\r\n' 'a\r\nours\r\n' 'a\r\ntheirs' 'a\r\nours\r\ntheirs' kept
merge_case crlf-ours-without-newline f 'a\r\nx\r\n' 'a\r\nours' 'a\r\ntheirs\r\n' 'a\r\nours' kept
merge_case crlf-ours-without-newline-added f 'a\r\nx\r\n' 'a\r\nours' 'a\r\ntheirs\r\n' 'a\r\nours\r\n' dismissed "$changed"
merge_case crlf-without-newline-bare-cr f 'a\r\nx\r\n' 'a\r\nours\r\n' 'a\r\ntheirs' 'a\r\ntheirs\r' dismissed "$changed"
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

echo "== attributes of the pull request's side"
# An ASCII file the repository merges as a binary one: git does not merge its lines, and writes the pull request's
# side; the pushed file has to be one side's.
binary_case() {
  local name=$1 pushed=$2 expected=$3 wanted=${4:-} start approved tip
  g checkout -q -B "common-$name" "$root"; echo '*.dat merge=binary' > "$src/.gitattributes"; write x.dat "$lines"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
  g checkout -q -B "base-$name" "$start"; write x.dat 'a\nb\nc\nd\ne\nf\ng\nBASE\n'; g commit -q -am base; tip=$(g rev-parse HEAD)
  g checkout -q -B "pr-$name" "$start"; write x.dat 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g commit -q -am pr; approved=$(g rev-parse HEAD)
  g merge -q --no-edit "base-$name" > /dev/null 2>&1; write x.dat "$pushed"; g add -A; g commit -q -m merge
  check "$name" "$approved" "$(g rev-parse HEAD)" "$tip" "$expected" "$wanted"
}
binary_case binary-lines-of-both 'a\nPR\nc\nd\ne\nf\ng\nBASE\n' dismissed "$changed"
binary_case binary-theirs 'a\nb\nc\nd\ne\nf\ng\nBASE\n' kept x.dat
binary_case binary-ours 'a\nPR\nc\nd\ne\nf\ng\nh\n' kept x.dat

# The pull request's side sets a marker size of its own; the conflict is still read with the size the check uses.
g checkout -q -B common-size "$root"; write s.txt "$lines"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q -B base-size "$start"; write s.txt 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; g commit -q -am base; tip=$(g rev-parse HEAD)
g checkout -q -B pr-size "$start"; echo '*.txt conflict-marker-size=10' > "$src/.gitattributes"; write s.txt 'a\nPR\nc\nd\ne\nf\ng\nPR\n'; g add -A; g commit -q -m pr; approved=$(g rev-parse HEAD)
g merge -q --no-edit base-size > /dev/null 2>&1; write s.txt 'a\nBASE\nc\nd\ne\nf\ng\nPR\n'; g add -A; g commit -q -m merge
check marker-size-of-the-pull-request "$approved" "$(g rev-parse HEAD)" "$tip" kept s.txt

# git reports a conflict for a directory the pull request split in two, into which the base branch adds a file, but
# names no conflicted file: there is nothing to check, so the merge is not taken for a clean one.
g checkout -q -B common-split "$root"; mkdir -p "$src/d"; echo a > "$src/d/a"; echo b > "$src/d/b"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q -B base-split "$start"; echo new > "$src/d/new"; g add -A; g commit -q -m base; tip=$(g rev-parse HEAD)
g checkout -q -B pr-split "$start"; mkdir -p "$src/x" "$src/y"; g mv d/a x/a; g mv d/b y/b; g commit -q -m pr; approved=$(g rev-parse HEAD)
g merge -q --no-edit base-split > /dev/null 2>&1; g add -A; g commit -q -m merge > /dev/null 2>&1
check directory-split-without-a-file "$approved" "$(g rev-parse HEAD)" "$tip" dismissed "$unchecked"

# Conflicts that tie several paths together are not checked file by file. The pull request renames old to left,
# the base branch renames it to right: keeping one name, both, or none leaves the commits unchecked.
rename_case() {
  local name=$1 keep=$2 start approved tip
  g checkout -q -B "common-$name" "$root"; seq -f 'line %g' 20 > "$src/old"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
  g checkout -q -B "base-$name" "$start"; g mv old right; g commit -q -m base; tip=$(g rev-parse HEAD)
  g checkout -q -B "pr-$name" "$start"; g mv old left; g commit -q -m pr; approved=$(g rev-parse HEAD)
  g merge -q --no-edit "base-$name" > /dev/null 2>&1
  g rm -q --cached -f old left right > /dev/null 2>&1; rm -f "$src/old" "$src/left" "$src/right"
  for f in $keep; do seq -f 'line %g' 20 > "$src/$f"; done
  g add -A; g commit -q -m merge > /dev/null 2>&1
  check "$name" "$approved" "$(g rev-parse HEAD)" "$tip" dismissed "$unchecked"
}
rename_case rename-rename-keeps-left left
rename_case rename-rename-keeps-both 'left right'
rename_case rename-rename-keeps-none ''
# A directory split in two and, in the same merge, a conflict of a file's lines resolved with one side.
g checkout -q -B common-split2 "$root"; mkdir -p "$src/d"; echo a > "$src/d/a"; echo b > "$src/d/b"; write t.txt "$lines"; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q -B base-split2 "$start"; echo new > "$src/d/new"; write t.txt 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; g add -A; g commit -q -m base; tip=$(g rev-parse HEAD)
g checkout -q -B pr-split2 "$start"; mkdir -p "$src/x" "$src/y"; g mv d/a x/a; g mv d/b y/b; write t.txt 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g add -A; g commit -q -m pr; approved=$(g rev-parse HEAD)
g merge -q --no-edit base-split2 > /dev/null 2>&1; write t.txt 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g add -A; g commit -q -m merge > /dev/null 2>&1
check directory-split-and-a-file "$approved" "$(g rev-parse HEAD)" "$tip" dismissed "$unchecked"
# A head whose history of merges ends without the approved commit, at a commit without parents.
orphan=$(g commit-tree "$(g rev-parse "$approved^{tree}")" -m orphan)
check history-without-the-approved-commit "$approved" "$orphan" "$tip" dismissed "$changed"

echo "== a submodule"
# Commits whose tree holds a submodule at sub, made from trees directly, as no checkout is needed for one.
gitlink_commit() { # parent... -- commit-of-the-submodule message
  local parents=() tree
  while [[ "$1" != -- ]]; do parents+=(-p "$1"); shift; done; shift
  tree=$(printf '160000 commit %s\tsub\n' "$1" | g mktree)
  g commit-tree "$tree" "${parents[@]}" -m "$2"
}
sub_common=$(gitlink_commit "$root" -- 1111111111111111111111111111111111111111 common)
sub_pr=$(gitlink_commit "$sub_common" -- 2222222222222222222222222222222222222222 pr)
sub_base=$(gitlink_commit "$sub_common" -- 3333333333333333333333333333333333333333 base)
check submodule-ours "$sub_pr" "$(gitlink_commit "$sub_pr" "$sub_base" -- 2222222222222222222222222222222222222222 merge)" "$sub_base" kept sub
check submodule-theirs "$sub_pr" "$(gitlink_commit "$sub_pr" "$sub_base" -- 3333333333333333333333333333333333333333 merge)" "$sub_base" kept sub
check submodule-other "$sub_pr" "$(gitlink_commit "$sub_pr" "$sub_base" -- 4444444444444444444444444444444444444444 merge)" "$sub_base" dismissed "$changed"

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
FAIL_TAIL=1 check last-byte-read-fails "$approved" "$head" "$tip" dismissed "$unchecked"

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
# Approvals: one kept across a merge of files with a space, a backtick and spaces at both ends, and text like a
# marker in their names; one followed by an ordinary commit; one at a commit with the very files of the head whose
# history cannot be fetched; and one whose commit GraphQL does not know.
odd_name=' `odd` name '
marker_name="note <!-- kept-resolution:$(printf '1%.0s' {1..40}) -->.md"
cr_name=$'cr\rname.md'
step_files=('a file.md' "$odd_name" "$marker_name" a '`a`' "$cr_name")
g checkout -q -B step-base "$root"; for f in "${step_files[@]}" second.md; do write "$f" "$lines"; done; g add -A; g commit -q -m common; start=$(g rev-parse HEAD)
g checkout -q step-base; for f in "${step_files[@]}"; do write "$f" 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; done; g commit -q -am base
g checkout -q -B step-kept "$start"; for f in "${step_files[@]}" second.md; do write "$f" 'a\nPR\nc\nd\ne\nf\ng\nh\n'; done; g commit -q -am pr; kept_approved=$(g rev-parse HEAD)
g merge -q --no-edit step-base > /dev/null 2>&1; for f in "${step_files[@]}"; do write "$f" 'a\nPR\nc\nd\ne\nf\ng\nh\n'; done; g commit -q -am merge; first_merge=$(g rev-parse HEAD)
# A second merge, of a second change of the base branch, resolved with the pull request's side too.
g checkout -q step-base; write second.md 'a\nBASE\nc\nd\ne\nf\ng\nh\n'; g commit -q -am "base again"; tip=$(g rev-parse HEAD)
g checkout -q step-kept; g merge -q --no-edit step-base > /dev/null 2>&1; write second.md 'a\nPR\nc\nd\ne\nf\ng\nh\n'; g commit -q -am "merge again"; kept_head=$(g rev-parse HEAD)
g checkout -q -B step-changed "$kept_approved"; echo own > "$src/own"; g add -A; g commit -q -m own; changed_head=$(g rev-parse HEAD)
unknown=0000000000000000000000000000000000000001
step() {
  if ! ( cd "$(mktemp -d "$work/run.XXXXXX")" && SHA= GITHUB_STEP_SUMMARY="$FAKE/summary" bash -e "$work/step.sh" > "$FAKE/output" 2>&1 ); then
    checks=$((checks + 1)) failures=$((failures + 1)); echo "FAIL the step failed:"; sed 's/^/     /' "$FAKE/output"
  fi
}
expect() {
  checks=$((checks + 1))
  if eval "$2"; then echo "ok   $1"; else failures=$((failures + 1)); echo "FAIL $1"; sed 's/^/     /' "$FAKE/output"; fi
}
printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' 1 "$kept_head" 11 reviewer "$kept_approved" changed "$tip" step-base \
  2 "$changed_head" 21 reviewer "$kept_approved" changed "$tip" step-base \
  3 "$changed_head" 31 reviewer "$unknown" same "$tip" step-base \
  4 "$changed_head" 41 reviewer - changed "$tip" step-base > "$FAKE/stale.tsv"
printf '11 APPROVED %s\n21 APPROVED %s\n31 APPROVED %s\n41 APPROVED\n' "$kept_approved" "$kept_approved" "$unknown" > "$FAKE/reviews"
printf '1 %s step-base\n2 %s step-base\n3 %s step-base\n4 %s step-base\n' "$kept_head" "$changed_head" "$changed_head" "$changed_head" > "$FAKE/heads"
printf 'step-base %s\n' "$tip" > "$FAKE/bases"
echo '[]' > "$FAKE/comments.json"; : > "$FAKE/dismissed"; : > "$FAKE/summary"; : > "$FAKE/race"; : > "$FAKE/requests"
step
expect "the kept approval is not dismissed" '! grep -q "/pulls/1/" "$FAKE/dismissed"'
expect "the summary names the file with a space" 'grep -q "resolved .*a file.md" "$FAKE/summary"'
expect "an ordinary commit dismisses the approval as a change" 'grep -q "/pulls/2/reviews/21 Dismissed automatically: $changed" "$FAKE/dismissed"'
expect "the same files whose history cannot be read are not called a change" 'grep -q "/pulls/3/reviews/31 Dismissed automatically: $unchecked" "$FAKE/dismissed"'
expect "an approval whose commit GraphQL does not know is dismissed" 'grep -q "/pulls/4/reviews/41 Dismissed automatically" "$FAKE/dismissed"'
expect "one comment is left for each kept merge when another run wrote it too" '[[ $(jq length "$FAKE/comments.json") == 2 ]]'
expect "the comment names the file with a space" 'jq -r ".[].body" "$FAKE/comments.json" | grep -qF "\`a file.md\`"'
expect "the comment names the file with a backtick and spaces at its ends as it is" 'jq -r ".[].body" "$FAKE/comments.json" | grep -qF "\`\`  \`odd\` name  \`\`"'
expect "the marker is the last line of the comment" '[[ $(jq -r ".[] | select(.body | contains(\"kept-resolution:$first_merge\")) | .body" "$FAKE/comments.json" | tail -n 1) == "<!-- kept-resolution:$first_merge -->" ]]'
expect "the comment names the files a and \`a\` both" 'jq -r ".[] | select(.body | contains(\"kept-resolution:$first_merge\")) | .body" "$FAKE/comments.json" | grep -qF ", \`a\`, " && jq -r ".[] | select(.body | contains(\"kept-resolution:$first_merge\")) | .body" "$FAKE/comments.json" | grep -qF "\`\` \`a\` \`\`"'
expect "the comment writes a carriage return in a name as an escape" 'jq -r ".[] | select(.body | contains(\"kept-resolution:$first_merge\")) | .body" "$FAKE/comments.json" | grep -qF "\`cr\\rname.md\`"'
# The step's own reading of the markers: the text like one in a file name is not taken for one.
expect "a name like a marker is not read as one" '[[ $(cd "$work" && source functions.sh && job_end=$(( SECONDS + 60 )) && comment_markers 1 | cut -d " " -f 1 | sort | tr "\n" " ") == "$(printf "%s\n" "$first_merge" "$kept_head" | sort | tr "\n" " ")" ]]'
# The approval is moved to a new head while the step checks it, and a comment left over by a stopped run repeats
# the kept merge's.
printf '11 APPROVED %s\n21 APPROVED %s\n31 APPROVED %s\n41 APPROVED\n' "$kept_approved" "$changed_head" "$unknown" > "$FAKE/reviews"; : > "$FAKE/dismissed"; : > "$FAKE/requests"
jq '. + [.[0] | .id = 9]' "$FAKE/comments.json" > "$FAKE/c" && mv "$FAKE/c" "$FAKE/comments.json"
step
expect "the second run read the review again" 'grep -q "pulls/2/reviews/21" "$FAKE/requests"'
expect "an approval moved while it was checked is not dismissed" '! grep -q "/pulls/2/" "$FAKE/dismissed"'
expect "a second run writes no second comment and takes away the one left over" '[[ $(jq length "$FAKE/comments.json") == 2 && $(jq "[.[].id] | index(9)" "$FAKE/comments.json") == null ]]'
# The base branch of the pull request is changed while the step checks it.
printf '11 APPROVED %s\n21 APPROVED %s\n31 APPROVED %s\n41 APPROVED\n' "$kept_approved" "$kept_approved" "$unknown" > "$FAKE/reviews"
printf '1 %s step-base\n2 %s other-base\n3 %s step-base\n4 %s step-base\n' "$kept_head" "$changed_head" "$changed_head" "$changed_head" > "$FAKE/heads"; : > "$FAKE/dismissed"
step
expect "an approval whose pull request was retargeted while it was checked is not dismissed" '! grep -q "/pulls/2/" "$FAKE/dismissed"'
# The base branch gets a new commit while the step checks.
printf '1 %s step-base\n2 %s step-base\n3 %s step-base\n4 %s step-base\n' "$kept_head" "$changed_head" "$changed_head" "$changed_head" > "$FAKE/heads"
printf 'step-base %s\n' "$start" > "$FAKE/bases"; : > "$FAKE/dismissed"
step
expect "an approval whose base branch moved while it was checked is not dismissed" '! grep -q "/pulls/2/" "$FAKE/dismissed"'
# The approval is moved to another commit that is not the head.
printf 'step-base %s\n' "$tip" > "$FAKE/bases"
printf '11 APPROVED %s\n21 APPROVED %s\n31 APPROVED %s\n41 APPROVED\n' "$kept_approved" "$first_merge" "$unknown" > "$FAKE/reviews"; : > "$FAKE/dismissed"
step
expect "an approval moved to another commit while it was checked is not dismissed" '! grep -q "/pulls/2/" "$FAKE/dismissed"'
# A push while the comments are read, then a comment that fails: no comment is written for a moved pull request,
# and one that fails leaves the other merge's to be written.
echo '[]' > "$FAKE/comments.json"; : > "$FAKE/move-on-list"
step
expect "no comment is written when the pull request moves while its comments are read" '[[ $(jq length "$FAKE/comments.json") == 0 ]]'
printf '1 %s step-base\n2 %s step-base\n3 %s step-base\n4 %s step-base\n' "$kept_head" "$changed_head" "$changed_head" "$changed_head" > "$FAKE/heads"; : > "$FAKE/fail-post"
step
expect "a comment that fails leaves the other merge's to be written" '[[ $(jq length "$FAKE/comments.json") == 1 ]] && grep -q "Resolution not noted" "$FAKE/output"'
# Two names whose code contains the other's, in both orders: each is named.
for order in 'a:`a`' '`a`:a'; do
  echo '[]' > "$FAKE/comments.json"
  ( cd "$work" && source functions.sh && job_end=$(( SECONDS + 60 )) && declare -A pull_heads=([1]="$kept_head") pull_bases=([1]="step-base $tip") \
    && note_resolutions 1 "$kept_head ${order%%:*}"$'\n'"$kept_head ${order#*:}" ) > "$FAKE/output" 2>&1
  expect "the names ${order/:/ and } are both named, in that order" 'jq -r ".[0].body" "$FAKE/comments.json" | grep -qF ": \`${order%%:*}\`, \`\` \`a\` \`\`" || jq -r ".[0].body" "$FAKE/comments.json" | grep -qF ": \`\` \`a\` \`\`, \`a\`"'
done
# A merge with more names than a comment holds: the comment stays within GitHub's limit and says how many more.
long_names=$(for i in $(seq 1 400); do printf "%s %s\n" "$kept_head" "$(printf "f%03d-%0190d.md" "$i" 0)"; done)
echo '[]' > "$FAKE/comments.json"
( cd "$work" && source functions.sh && job_end=$(( SECONDS + 60 )) && declare -A pull_heads=([1]="$kept_head") pull_bases=([1]="step-base $tip") && note_resolutions 1 "$long_names" ) > "$FAKE/output" 2>&1
expect "a comment with more names than fit stays within the limit and says how many more" '(( $(jq ".[0].body | length" "$FAKE/comments.json") < 65536 )) && jq -r ".[0].body" "$FAKE/comments.json" | grep -q "and [0-9]* more, named in the step summary"'

echo
echo "$((checks - failures)) of $checks checks passed"
(( failures == 0 ))
