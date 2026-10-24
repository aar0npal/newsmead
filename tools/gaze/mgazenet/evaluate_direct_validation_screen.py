#!/usr/bin/env python3
"""Apply the frozen development-only MGazeNet five-point validation screen."""
import argparse
import hashlib
import json
import math
from pathlib import Path


INPUT_SCHEMA = "mgazenet_calibration_audit_summary_v3"
INPUT_PROTOCOL = "mgazenet_calibration_observability_v3"
OUTPUT_SCHEMA = "mgazenet_direct_validation_screen_candidate_v1"
HELD_OUT_IDS = tuple(f"validation_{index}" for index in range(1, 6))
MIN_COORDINATES_PER_TARGET = 10
MAX_MEAN_TARGET_MEDIAN_VERTICAL_LINES = 1.0
MAX_WORST_TARGET_MEDIAN_VERTICAL_LINES = 1.2
ABS_TOLERANCE = 1e-12


def require(condition, message):
    if not condition:
        raise ValueError(message)


def finite(value, message):
    require(type(value) in (int, float) and math.isfinite(value), message)
    return float(value)


def _held_out_blocks(summary):
    verification = summary.get("verification")
    require(isinstance(verification, dict), "Missing verification result")
    blocks = verification.get("blocks")
    require(isinstance(blocks, list), "Missing verification blocks")

    held_out = [block for block in blocks
                if isinstance(block, dict) and block.get("role") == "held_out_validation"]
    require(len(held_out) == len(HELD_OUT_IDS),
            "Exactly five held-out validation blocks are required")
    by_id = {block.get("id"): block for block in held_out}
    require(len(by_id) == len(held_out) and set(by_id) == set(HELD_OUT_IDS),
            "Changed or duplicate held-out validation block identity")
    return verification, [by_id[block_id] for block_id in HELD_OUT_IDS]


def evaluate(summary, summary_sha256=None):
    """Return a reproducible retrospective screen result for one v3 summary.

    This is deliberately not an accuracy gate or a promotion decision. It
    classifies only development evidence using thresholds frozen after the v3
    collection and before a separate confirmation data set.
    """
    require(summary.get("schema") == INPUT_SCHEMA and
            summary.get("evidence_kind") == "recorded",
            "Unexpected summary schema or evidence kind")
    require(summary.get("accuracy_gate_pass") is None and
            summary.get("promotion_decision") == "not_evaluated",
            "Input summary already assigned an accuracy decision")

    provenance = summary.get("provenance")
    require(isinstance(provenance, dict) and
            provenance.get("protocol_id") == INPUT_PROTOCOL,
            "Unexpected calibration-audit protocol")
    for key in ("session_id", "device_id", "pipeline_id", "calibration_id"):
        require(isinstance(provenance.get(key), str) and provenance[key],
                f"Missing provenance field: {key}")

    input_sha256 = summary.get("input_sha256")
    require(isinstance(input_sha256, str) and len(input_sha256) == 64 and
            all(char in "0123456789abcdef" for char in input_sha256),
            "Missing raw-report hash")
    if summary_sha256 is not None:
        require(isinstance(summary_sha256, str) and len(summary_sha256) == 64 and
                all(char in "0123456789abcdef" for char in summary_sha256),
                "Invalid summary hash")

    verification, held_out = _held_out_blocks(summary)
    require(verification.get("blocks_without_coordinates") == 0,
            "A verification block has no coordinates")
    aggregate = verification.get("held_out_validation_target_balanced")
    require(isinstance(aggregate, dict) and
            aggregate.get("contributing_target_count") == len(HELD_OUT_IDS),
            "All five held-out targets must contribute")

    target_results = []
    for block in held_out:
        coordinates = block.get("coordinate_samples")
        received = block.get("received_samples")
        nulls = block.get("null_samples")
        require(type(coordinates) is int and coordinates >= 0 and
                type(received) is int and received >= coordinates and
                type(nulls) is int and nulls >= 0 and received == coordinates + nulls,
                "Invalid held-out sample counts")
        vertical = block.get("absolute_vertical_lines")
        require(isinstance(vertical, dict), "Missing held-out vertical result")
        median = finite(vertical.get("median"), "Missing target vertical median")
        p95 = finite(vertical.get("p95"), "Missing target vertical P95")
        maximum = finite(vertical.get("max"), "Missing target vertical maximum")
        require(0 <= median <= p95 <= maximum,
                "Invalid held-out vertical distribution ordering")
        target_results.append({
            "id": block["id"],
            "coordinate_samples": coordinates,
            "median_absolute_vertical_lines": median,
            "p95_absolute_vertical_lines": p95,
            "max_absolute_vertical_lines": maximum,
        })

    mean_target_median = sum(item["median_absolute_vertical_lines"]
                             for item in target_results) / len(target_results)
    mean_target_p95 = sum(item["p95_absolute_vertical_lines"]
                          for item in target_results) / len(target_results)
    worst_target_median = max(item["median_absolute_vertical_lines"]
                              for item in target_results)
    minimum_coordinates = min(item["coordinate_samples"] for item in target_results)

    recorded_mean_median = finite(
        aggregate.get("mean_target_median", {}).get("absolute_vertical_lines"),
        "Missing target-balanced vertical median")
    recorded_mean_p95 = finite(
        aggregate.get("mean_target_p95", {}).get("absolute_vertical_lines"),
        "Missing target-balanced vertical P95")
    require(math.isclose(mean_target_median, recorded_mean_median,
                         rel_tol=0.0, abs_tol=ABS_TOLERANCE) and
            math.isclose(mean_target_p95, recorded_mean_p95,
                         rel_tol=0.0, abs_tol=ABS_TOLERANCE),
            "Target-balanced aggregate does not reproduce from held-out blocks")

    checks = {
        "five_complete_held_out_targets": True,
        "at_least_ten_coordinates_per_target":
            minimum_coordinates >= MIN_COORDINATES_PER_TARGET,
        "mean_target_median_vertical_at_most_one_line":
            mean_target_median <= MAX_MEAN_TARGET_MEDIAN_VERTICAL_LINES,
        "every_target_median_vertical_at_most_1_2_lines":
            worst_target_median <= MAX_WORST_TARGET_MEDIAN_VERTICAL_LINES,
    }
    screen_pass = all(checks.values())

    return {
        "schema": OUTPUT_SCHEMA,
        "evidence_role": "retrospective_development_classification",
        "input": {
            "summary_sha256": summary_sha256,
            "raw_report_sha256": input_sha256,
            "provenance": provenance,
        },
        "frozen_screen": {
            "held_out_target_ids": list(HELD_OUT_IDS),
            "minimum_coordinates_per_target": MIN_COORDINATES_PER_TARGET,
            "maximum_mean_target_median_absolute_vertical_lines":
                MAX_MEAN_TARGET_MEDIAN_VERTICAL_LINES,
            "maximum_worst_target_median_absolute_vertical_lines":
                MAX_WORST_TARGET_MEDIAN_VERTICAL_LINES,
            "p95_is_reported_but_not_thresholded": True,
        },
        "observed": {
            "mean_target_median_absolute_vertical_lines": mean_target_median,
            "mean_target_p95_absolute_vertical_lines": mean_target_p95,
            "worst_target_median_absolute_vertical_lines": worst_target_median,
            "minimum_coordinate_samples_per_target": minimum_coordinates,
            "targets": target_results,
        },
        "checks": checks,
        "screen_candidate_pass": screen_pass,
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
        "limitations": [
            "This classification is retrospective development evidence, not confirmatory validation.",
            "The screen is vertical and line-level; it does not establish exact-line, word, or natural-reading accuracy.",
            "Five held-out targets and short blocks provide only a coarse tail estimate, so P95 is reported but not thresholded.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("summary", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    raw = args.summary.read_bytes()
    summary = json.loads(raw)
    result = evaluate(summary, hashlib.sha256(raw).hexdigest())
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    state = "pass" if result["screen_candidate_pass"] else "fail"
    print(f"Development-only direct validation screen: {state}; no promotion decision")


if __name__ == "__main__":
    main()
