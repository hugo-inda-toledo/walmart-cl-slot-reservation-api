#!/usr/bin/env python3
"""Fail when an OSV Scanner JSON report includes CVSS v3 findings >= 7.0."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path


THRESHOLD = 7.0
AV = {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.2}
AC = {"L": 0.77, "H": 0.44}
PR_UNCHANGED = {"N": 0.85, "L": 0.62, "H": 0.27}
PR_CHANGED = {"N": 0.85, "L": 0.68, "H": 0.5}
UI = {"N": 0.85, "R": 0.62}
CIA = {"H": 0.56, "L": 0.22, "N": 0.0}


def round_up(value: float) -> float:
    return math.ceil(value * 10 - 1e-9) / 10


def cvss_v3_score(vector: str) -> float:
    """Calculate the CVSS v3 base score from OSV's standard vector string."""
    components = dict(item.split(":", 1) for item in vector.split("/")[1:])
    scope = components["S"]
    impact_sub_score = 1 - (1 - CIA[components["C"]]) * (1 - CIA[components["I"]]) * (1 - CIA[components["A"]])
    if impact_sub_score <= 0:
        return 0.0
    impact = (
        6.42 * impact_sub_score
        if scope == "U"
        else 7.52 * (impact_sub_score - 0.029) - 3.25 * (impact_sub_score - 0.02) ** 15
    )
    privileges_required = (PR_UNCHANGED if scope == "U" else PR_CHANGED)[components["PR"]]
    exploitability = 8.22 * AV[components["AV"]] * AC[components["AC"]] * privileges_required * UI[components["UI"]]
    base = min(impact + exploitability, 10) if scope == "U" else min(1.08 * (impact + exploitability), 10)
    return round_up(base)


def findings(report: dict) -> list[tuple[str, str, str, float]]:
    matches = []
    for result in report.get("results", []):
        for package_result in result.get("packages", []):
            package = package_result.get("package", {})
            coordinate = f"{package.get('name', 'unknown')}@{package.get('version', 'unknown')}"
            for group in package_result.get("groups", []):
                try:
                    score = float(group.get("max_severity", ""))
                except (TypeError, ValueError):
                    continue
                if score >= THRESHOLD:
                    for vulnerability_id in group.get("ids", ["unknown"]):
                        matches.append((coordinate, vulnerability_id, "OSV group max_severity", score))
            for vulnerability in package_result.get("vulnerabilities", []):
                for severity in vulnerability.get("severity", []):
                    vector = severity.get("score", "")
                    if severity.get("type", "").startswith("CVSS_V3") and vector.startswith("CVSS:3."):
                        score = cvss_v3_score(vector)
                        if score >= THRESHOLD:
                            matches.append((coordinate, vulnerability.get("id", "unknown"), vector, score))
    return matches


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: check-osv-severity.py REPORT.json", file=sys.stderr)
        return 2
    try:
        report = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
        matches = findings(report)
    except (OSError, ValueError, KeyError) as error:
        print(f"Unable to evaluate OSV report: {error}", file=sys.stderr)
        return 2
    if not matches:
        return 0
    for coordinate, vulnerability_id, vector, score in matches:
        print(f"{coordinate}: {vulnerability_id} (CVSS {score:.1f}; {vector})", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
