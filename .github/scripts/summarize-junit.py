#!/usr/bin/env python3
"""Write a GitHub Actions job summary from JUnit XML test results."""

from __future__ import annotations

import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field


@dataclass
class Case:
    classname: str
    name: str
    time: float
    message: str | None = None


@dataclass
class SuiteStats:
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    cases: list[Case] = field(default_factory=list)


def parse_file(path: str) -> SuiteStats:
    stats = SuiteStats()
    root = ET.parse(path).getroot()
    suites = root.findall("testsuite") if root.tag == "testsuites" else [root]

    for suite in suites:
        stats.tests += int(suite.attrib.get("tests", 0))
        stats.failures += int(suite.attrib.get("failures", 0))
        stats.errors += int(suite.attrib.get("errors", 0))
        stats.skipped += int(suite.attrib.get("skipped", 0))

        for testcase in suite.findall("testcase"):
            failure = testcase.find("failure")
            error = testcase.find("error")
            skipped = testcase.find("skipped")
            detail = failure or error or skipped
            message = None
            if detail is not None:
                message = (detail.attrib.get("message") or detail.text or "").strip()

            stats.cases.append(
                Case(
                    classname=testcase.attrib.get("classname", ""),
                    name=testcase.attrib.get("name", ""),
                    time=float(testcase.attrib.get("time", 0) or 0),
                    message=message,
                )
            )

    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pattern", help="Glob for JUnit XML files")
    parser.add_argument("--title", default="Test results", help="Summary heading")
    args = parser.parse_args()

    files = sorted(glob.glob(args.pattern, recursive=True))
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    output = open(summary_path, "a", encoding="utf-8") if summary_path else sys.stdout

    with output:
        output.write(f"## {args.title}\n\n")

        if not files:
            output.write("_No JUnit XML files found._\n")
            return 0

        total = SuiteStats()
        for path in files:
            suite = parse_file(path)
            total.tests += suite.tests
            total.failures += suite.failures
            total.errors += suite.errors
            total.skipped += suite.skipped
            total.cases.extend(suite.cases)

        passed = total.tests - total.failures - total.errors - total.skipped
        output.write("| Total | Passed | Failed | Errors | Skipped |\n")
        output.write("|------:|-------:|-------:|-------:|--------:|\n")
        output.write(
            f"| {total.tests} | {passed} | {total.failures} | {total.errors} | {total.skipped} |\n\n"
        )

        failed_cases = [
            case
            for case in total.cases
            if case.message is not None and case.name
        ]
        if failed_cases:
            output.write("### Failures\n\n")
            for case in failed_cases:
                label = f"{case.classname}.{case.name}" if case.classname else case.name
                output.write(f"- **{label}**")
                if case.message:
                    first_line = case.message.splitlines()[0]
                    output.write(f" — {first_line}")
                output.write("\n")
            output.write("\n")

        output.write("<details><summary>All tests</summary>\n\n")
        for case in total.cases:
            label = f"{case.classname}.{case.name}" if case.classname else case.name
            if case.message:
                output.write(f"- ❌ {label}\n")
            else:
                output.write(f"- ✅ {label}\n")
        output.write("\n</details>\n")

    return 1 if total.failures or total.errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
