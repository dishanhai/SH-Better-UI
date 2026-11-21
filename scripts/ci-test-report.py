#!/usr/bin/env python3
"""Print all failed Gradle/JUnit cases, including failure messages and stack traces."""
import argparse
from collections import defaultdict
from pathlib import Path
import os
import sys
import xml.etree.ElementTree as ET


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("roots", nargs="+", type=Path)
    args = cli.parse_args()
    totals = defaultdict(lambda: [0, 0, 0, 0])
    found = set()
    errors = []
    for root in args.roots:
        for path in sorted(root.glob("**/build/test-results/**/TEST-*.xml")):
            canonical = path.resolve()
            if canonical in found:
                continue
            found.add(canonical)
            try:
                suite = ET.parse(path).getroot()
                results, task = next(
                    (p, path.relative_to(p).parts[0])
                    for p in path.parents if p.name == "test-results"
                )
                key = f"{results.parent.parent}/{task}"
                for case in suite.iter("testcase"):
                    counts = totals[key]
                    counts[0] += 1
                    failures = [node for node in case if node.tag in {"failure", "error"}]
                    if failures:
                        counts[2] += 1
                        print(f"FAIL {case.get('classname', '?')} > {case.get('name', '?')} ({key})")
                        for node in failures:
                            for line in "\n".join(filter(None, (node.get("message"), node.text))).splitlines():
                                print(f"  | {line}")
                    elif case.find("skipped") is not None:
                        counts[3] += 1
                    else:
                        counts[1] += 1
            except (ET.ParseError, OSError, StopIteration, IndexError) as exc:
                errors.append(f"{path}: {exc}")

    summary = ["### JVM regression results", "",
               "| Suite | Total | Passed | Failed | Skipped |",
               "| --- | ---: | ---: | ---: | ---: |"]
    for key, counts in sorted(totals.items()):
        safe_key = " ".join(key.split()).replace("|", "\\|").replace("<", "&lt;").replace(">", "&gt;")
        summary.append(f"| {safe_key} | {' | '.join(map(str, counts))} |")
        print(f"{key}: {counts[0]} total, {counts[1]} passed, {counts[2]} failed, {counts[3]} skipped")
    if not totals:
        summary += ["", "No JUnit test cases were produced. Check the preceding test step."]
        print("No JUnit test cases were produced. Check the preceding test step.")
    for error in errors:
        print(f"Could not parse report: {error}", file=sys.stderr)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
            stream.write("\n".join(summary) + "\n")
    return int(bool(errors) or not totals)


if __name__ == "__main__":
    raise SystemExit(main())
