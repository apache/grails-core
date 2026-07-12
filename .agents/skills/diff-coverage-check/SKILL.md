<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
---
name: diff-coverage-check
description: Computes real diff coverage (coverage of only the lines you actually changed, not whole-file coverage) entirely locally, without CI or Codecov — by running each affected module's own tests and cross-referencing its JaCoCo XML against git diff. Use before committing, or when asked "is my change covered" / "check coverage on the files I touched" / "diff coverage". A change often spans several modules; run this per module.
license: Apache-2.0
compatibility: opencode, claude, grok, gemini, copilot, cursor, windsurf
metadata:
  audience: maintainers
  frameworks: grails
---

## What I Do

- Compute diff coverage — coverage of the specific lines changed in a diff, not whole-file/whole-class coverage — using only local tooling already wired into this repo's build. No new Gradle plugin, no Codecov token, no network.
- Run the actual test suite for each module touched by the change (not the whole repo), so this stays fast and scoped instead of a full aggregate build.
- Cross-reference each module's standard JaCoCo XML report (`<module>/build/reports/jacoco/test/jacocoTestReport.xml`, produced automatically whenever `:module:test` runs — see `GrailsJacocoPlugin`, `finalizedBy('jacocoTestReport')`) against the exact line ranges `git diff` reports as changed.

## Why This Exists

Codecov (`codecov.yml`) computes diff coverage in CI, but that's a cloud round-trip. The underlying data — JaCoCo line-level hit/miss counters — is already produced locally by an ordinary `:module:test` run; the only missing piece was cross-referencing it against `git diff`. That's what this skill does, without adding any build dependency (a Gradle plugin for this, `form-com/diff-coverage-gradle`, exists and was considered, but wiring in a new external plugin/dependency is a real build change with its own review burden — this skill gets the same signal without touching `build.gradle` at all).

## When to Use Me

- Before committing, to check whether the lines you just wrote/changed are actually exercised by tests — not just "does the module's overall coverage look okay."
- When asked to check coverage on specific touched files, or "diff coverage" generally.
- A single change frequently spans multiple modules in this monorepo — expect to loop the procedure below once per affected module, not just once.

This skill assumes the module's tests already pass. If `:module:test` fails, that's a different problem — use the `test-fixer` skill first; a failing test run won't produce a trustworthy JaCoCo report either.

## Procedure

### 1. Find which files changed, and group them by Gradle module

```bash
git diff --name-only <base-ref> -- '*.java' '*.groovy'
```

For each changed file, find its owning Gradle module by walking up from the file to the **nearest ancestor directory containing a `build.gradle`** — do not assume module = the first path segment. This repo has modules nested two levels deep (e.g. `grails-gsp/grails-taglib` and `grails-gsp/core` are separate modules, both under `grails-gsp/`, which itself has no `build.gradle`). The Gradle project path is that directory's path relative to repo root with `/` replaced by `:`, prefixed with `:` (e.g. `grails-gsp/grails-taglib` → `:grails-gsp:grails-taglib`).

### 2. For each affected module, run its tests

```bash
./gradlew :grails-gsp:grails-taglib:test
```

This auto-triggers `jacocoTestReport` afterward (wired via `finalizedBy` in `GrailsJacocoPlugin` — you don't need to call it separately). **Always run this fresh, even if you think a report already exists.** A stale report will not error — it will silently cross-reference the wrong code. (Verified directly: editing a file shifted its line numbers, and the stale XML matched the edited line number against unrelated old-code coverage data until the report was regenerated. This fails silently, not loudly — always regenerate.)

### 3. Locate the package/sourcefile for each changed file

Strip the file's path down to whatever comes after `src/main/java/` or `src/main/groovy/` (check both — this repo puts some `.java` files under `src/main/groovy/`, e.g. `grails-i18n/src/main/groovy/org/grails/plugins/i18n/AvailableLocaleResolver.java`). The directory portion (with `/`, not `.`) is the JaCoCo `package` name; the filename is the `sourcefile` name.

### 4. Cross-reference changed lines against the JaCoCo XML

```python
#!/usr/bin/env python3
import re, subprocess, sys
import xml.etree.ElementTree as ET

def changed_lines(filepath, base_ref):
    diff = subprocess.run(
        ['git', 'diff', '--unified=0', base_ref, '--', filepath],
        capture_output=True, text=True, check=True
    ).stdout
    lines = set()
    for m in re.finditer(r'^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@', diff, re.MULTILINE):
        start = int(m.group(1))
        count = int(m.group(2)) if m.group(2) is not None else 1
        if count == 0:
            continue  # pure deletion, nothing added in the new file
        lines.update(range(start, start + count))
    return lines

def jacoco_line_coverage(xml_path, package, sourcefile):
    root = ET.parse(xml_path).getroot()
    for pkg in root.iter('package'):
        if pkg.get('name') != package:
            continue
        for sf in pkg.iter('sourcefile'):
            if sf.get('name') != sourcefile:
                continue
            return {int(l.get('nr')): (int(l.get('mi')), int(l.get('ci'))) for l in sf.iter('line')}
    return {}

def report(filepath, xml_path, package, sourcefile, base_ref):
    changed = changed_lines(filepath, base_ref)
    cov = jacoco_line_coverage(xml_path, package, sourcefile)
    instrumented = {ln: cov[ln] for ln in changed if ln in cov}
    covered = {ln for ln, (mi, ci) in instrumented.items() if ci > 0}
    missed = sorted(ln for ln, (mi, ci) in instrumented.items() if ci == 0 and mi > 0)
    if not instrumented:
        print(f"{filepath}: no instrumented lines in this diff (comments/blank lines/braces only, or file not found in report)")
        return
    pct = 100 * len(covered) / len(instrumented)
    print(f"{filepath}: {len(covered)}/{len(instrumented)} changed+instrumented lines covered ({pct:.1f}%)")
    if missed:
        print(f"  Uncovered changed lines: {missed}")

if __name__ == '__main__':
    # filepath, xml_path, package, sourcefile, base_ref
    report(*sys.argv[1:6])
```

Example invocation, for a change to `grails-i18n/src/main/groovy/org/grails/plugins/i18n/AvailableLocaleResolver.java` against branch `8.0.x`:

```bash
python3 diff_coverage.py \
  grails-i18n/src/main/groovy/org/grails/plugins/i18n/AvailableLocaleResolver.java \
  grails-i18n/build/reports/jacoco/test/jacocoTestReport.xml \
  org/grails/plugins/i18n \
  AvailableLocaleResolver.java \
  8.0.x
```

### 5. Repeat per module, then summarize

Loop steps 1–4 for every module the diff touches, then report a combined summary. Don't stop after the first module — "it might take several modules to get it done" is the normal case for this monorepo, not the exception.

## Interpreting Results

- **Uncovered changed lines are exactly what to add tests for** — that's the actionable output, more precise than a whole-class coverage percentage.
- **"No instrumented lines"** for a changed file usually means the diff only touched comments, imports, blank lines, or braces — not a problem, just nothing for JaCoCo to measure.
- A completely new, never-executed class still appears in the JaCoCo XML (with every line `ci=0`) rather than being silently absent — JaCoCo reports on all compiled classes in the module's `classDirectories`, not just ones a specific test run happened to exercise. So a brand-new untested class will correctly show as 0% covered, not "not found."

## Source of Truth

This skill's script and procedure were verified against a real JaCoCo report and a real git diff in this repository (`grails-i18n`), not assumed from JaCoCo's documented schema alone. If `GrailsJacocoPlugin`'s report locations or the JaCoCo XML schema version change, re-verify before trusting this skill's output.
