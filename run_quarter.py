#!/usr/bin/env python3
"""
Run a quarter of the grails-data-hibernate7-core test suite with a timeout.

Usage:
    python3 run_quarter.py <spec-list-file> [--timeout SECONDS]

Each line in <spec-list-file> is a fully-qualified spec class name.
Runs all specs via a single Gradle invocation.  Kills the process if it
exceeds --timeout (default: 540 seconds / 9 min).

Exits:
    0  – all specs passed
    1  – one or more specs failed or timed out
"""

import argparse
import asyncio
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).parent
MODULE = "grails-data-hibernate7-core"
RESULTS_DIR = REPO_ROOT / "grails-data-hibernate7" / "core" / "build" / "test-results" / "test"
GRADLE = str(REPO_ROOT / "gradlew")


def build_gradle_cmd(specs: list[str]) -> list[str]:
    cmd = [GRADLE, f":{MODULE}:test", "--rerun-tasks"]
    for spec in specs:
        cmd += ["--tests", spec]
    return cmd


def collect_results() -> dict[str, str]:
    """Parse all TEST-*.xml files; return {className: 'PASS'|'FAIL'|'ERROR'}."""
    results: dict[str, str] = {}
    if not RESULTS_DIR.exists():
        return results
    for xml_file in RESULTS_DIR.glob("TEST-*.xml"):
        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()
            cls = root.attrib.get("name", xml_file.stem.replace("TEST-", ""))
            failures = int(root.attrib.get("failures", 0))
            errors = int(root.attrib.get("errors", 0))
            results[cls] = "FAIL" if (failures + errors) > 0 else "PASS"
        except Exception:
            pass
    return results


async def run(specs: list[str], timeout_secs: int) -> bool:
    cmd = build_gradle_cmd(specs)
    print(f"\n>>> Running {len(specs)} specs  (timeout {timeout_secs}s)")
    print(f">>> {' '.join(cmd[:6])} ... [{len(specs)} --tests args]")

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        cwd=REPO_ROOT,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )

    timed_out = False

    async def read_output():
        while True:
            line = await proc.stdout.readline()
            if not line:
                break
            sys.stdout.buffer.write(line)
            sys.stdout.flush()

    output_task = asyncio.create_task(read_output())

    try:
        await asyncio.wait_for(proc.wait(), timeout=timeout_secs)
    except asyncio.TimeoutError:
        timed_out = True
        print(f"\n!!! TIMEOUT after {timeout_secs}s — killing process", flush=True)
        proc.kill()
        await proc.wait()
    finally:
        await output_task

    return timed_out


def print_report(specs: list[str], results: dict[str, str], timed_out: bool) -> bool:
    """Print summary. Returns True if any spec failed."""
    known = {s: results.get(s, "SUSPECT" if timed_out else "MISSING") for s in specs}

    passes = [s for s, r in known.items() if r == "PASS"]
    fails = [s for s, r in known.items() if r == "FAIL"]
    suspects = [s for s, r in known.items() if r in ("SUSPECT", "MISSING")]

    print(f"\n{'='*70}")
    print(f"RESULTS: {len(passes)} PASS  {len(fails)} FAIL  {len(suspects)} SUSPECT")
    print("=" * 70)

    if fails:
        print("\nFAILED:")
        for s in fails:
            print(f"  FAIL  {s}")

    if suspects:
        label = "SUSPECT (timed out)" if timed_out else "MISSING (no XML)"
        print(f"\n{label}:")
        for s in suspects:
            print(f"  ???   {s}")

    if not fails and not suspects:
        print("\nAll specs PASSED ✅")

    return bool(fails or suspects)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("spec_file", help="File with one spec class name per line")
    parser.add_argument("--timeout", type=int, default=540, help="Kill timeout in seconds (default 540)")
    args = parser.parse_args()

    spec_path = Path(args.spec_file)
    if not spec_path.exists():
        print(f"ERROR: file not found: {spec_path}", file=sys.stderr)
        sys.exit(2)

    specs = [l.strip() for l in spec_path.read_text().splitlines() if l.strip() and not l.startswith("#")]
    if not specs:
        print("No specs found in file.", file=sys.stderr)
        sys.exit(2)

    timed_out = asyncio.run(run(specs, args.timeout))
    results = collect_results()
    had_failures = print_report(specs, results, timed_out)
    sys.exit(1 if had_failures else 0)


if __name__ == "__main__":
    main()
