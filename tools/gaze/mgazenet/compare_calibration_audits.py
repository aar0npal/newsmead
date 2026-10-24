#!/usr/bin/env python3
"""Compare the four frozen MGazeNet calibration-observability v3 summaries."""
import argparse
import hashlib
import json
import math
from pathlib import Path

SCHEMA = "mgazenet_calibration_audit_summary_v3"
PROTOCOL = "mgazenet_calibration_observability_v3"
ORDER = ("g991b_v3_r1", "a56_v3_r1", "a56_v3_r2", "g991b_v3_r2")
DEVICES = {
    "g991b_v3_r1": "samsung/SM-G991B",
    "g991b_v3_r2": "samsung/SM-G991B",
    "a56_v3_r1": "samsung/SM-A566B",
    "a56_v3_r2": "samsung/SM-A566B",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def finite(value, message):
    require(type(value) in (int, float) and math.isfinite(value), message)
    return value


def metric(group, aggregate, field):
    return finite(group[aggregate][field], "Missing or non-finite metric")


def session_result(label, summary, summary_sha256=None):
    require(label in DEVICES, "Unknown frozen session label")
    require(summary.get("schema") == SCHEMA and summary.get("evidence_kind") == "recorded",
            "Unexpected summary schema or evidence kind")
    require(summary.get("accuracy_gate_pass") is None and
            summary.get("promotion_decision") == "not_evaluated",
            "Summary assigned an accuracy decision")
    provenance = summary.get("provenance")
    require(isinstance(provenance, dict) and provenance.get("protocol_id") == PROTOCOL and
            provenance.get("device_id") == DEVICES[label], "Unexpected protocol or device")
    for key in ("session_id", "pipeline_id", "calibration_id"):
        require(isinstance(provenance.get(key), str) and provenance[key], "Missing provenance")
    input_sha = summary.get("input_sha256")
    require(isinstance(input_sha, str) and len(input_sha) == 64 and
            all(char in "0123456789abcdef" for char in input_sha), "Missing raw-report hash")

    loo = summary.get("loo", {}).get("target_balanced")
    require(isinstance(loo, dict) and loo.get("contributing_target_count") == 16,
            "Primary LOO diagnostic requires all 16 targets")
    verification = summary.get("verification", {})
    held_out = verification.get("held_out_validation_target_balanced")
    held_out_complete = isinstance(held_out, dict) and held_out.get("contributing_target_count") == 5

    result = {
        "label": label,
        "provenance": provenance,
        "raw_report_sha256": input_sha,
        "summary_sha256": summary_sha256,
        "primary": {
            "loo_mean_target_median_absolute_vertical_lines":
                metric(loo, "mean_target_median", "absolute_vertical_lines"),
            "held_out_mean_target_median_absolute_vertical_lines":
                metric(held_out, "mean_target_median", "absolute_vertical_lines")
                if held_out_complete else None,
            "held_out_contributing_target_count":
                held_out.get("contributing_target_count", 0) if isinstance(held_out, dict) else 0,
        },
        "secondary": {
            "loo_mean_target_p95_absolute_vertical_lines":
                metric(loo, "mean_target_p95", "absolute_vertical_lines"),
            "loo_mean_target_max_absolute_vertical_lines":
                metric(loo, "mean_target_max", "absolute_vertical_lines"),
            "loo_mean_target_median_euclidean_px":
                metric(loo, "mean_target_median", "euclidean_px"),
            "loo_mean_target_signed_y_bias_px":
                finite(loo["mean_target_signed_bias_px"]["y"], "Missing LOO Y bias"),
            "held_out_mean_target_p95_absolute_vertical_lines":
                metric(held_out, "mean_target_p95", "absolute_vertical_lines")
                if held_out_complete else None,
            "held_out_mean_target_max_absolute_vertical_lines":
                metric(held_out, "mean_target_max", "absolute_vertical_lines")
                if held_out_complete else None,
            "held_out_mean_target_median_euclidean_px":
                metric(held_out, "mean_target_median", "euclidean_px")
                if held_out_complete else None,
            "held_out_mean_target_signed_y_bias_px":
                finite(held_out["mean_target_signed_bias_px"]["y"], "Missing held-out Y bias")
                if held_out_complete else None,
            "verification_blocks_without_coordinates": verification.get("blocks_without_coordinates"),
        },
    }
    return result


def ranks(values):
    ordered = sorted(enumerate(values), key=lambda item: item[1])
    output = [0.0] * len(values)
    index = 0
    while index < len(ordered):
        end = index + 1
        while end < len(ordered) and ordered[end][1] == ordered[index][1]:
            end += 1
        average = ((index + 1) + end) / 2
        for position in range(index, end):
            output[ordered[position][0]] = average
        index = end
    return output


def spearman(left, right):
    require(len(left) == len(right) and len(left) >= 2, "Invalid rank inputs")
    left_rank = ranks(left)
    right_rank = ranks(right)
    left_mean = sum(left_rank) / len(left_rank)
    right_mean = sum(right_rank) / len(right_rank)
    numerator = sum((x-left_mean)*(y-right_mean) for x, y in zip(left_rank, right_rank))
    left_ss = sum((x-left_mean)**2 for x in left_rank)
    right_ss = sum((y-right_mean)**2 for y in right_rank)
    return numerator / math.sqrt(left_ss*right_ss) if left_ss and right_ss else None


def direction(value):
    return 1 if value > 0 else -1 if value < 0 else 0


def phone_change(first, second):
    first_loo = first["primary"]["loo_mean_target_median_absolute_vertical_lines"]
    second_loo = second["primary"]["loo_mean_target_median_absolute_vertical_lines"]
    first_held = first["primary"]["held_out_mean_target_median_absolute_vertical_lines"]
    second_held = second["primary"]["held_out_mean_target_median_absolute_vertical_lines"]
    delta_loo = second_loo-first_loo
    delta_held = second_held-first_held if first_held is not None and second_held is not None else None
    agreement = None if delta_held is None or direction(delta_loo) == 0 or direction(delta_held) == 0 \
        else direction(delta_loo) == direction(delta_held)
    return {
        "round_2_minus_round_1": {
            "loo_primary_absolute_vertical_lines": delta_loo,
            "held_out_primary_absolute_vertical_lines": delta_held,
        },
        "directional_agreement": agreement,
    }


def compare(summaries, summary_hashes=None):
    require(set(summaries) == set(ORDER), "Exactly the four frozen sessions are required")
    summary_hashes = summary_hashes or {}
    sessions = [session_result(label, summaries[label], summary_hashes.get(label)) for label in ORDER]
    require(len({item["provenance"]["session_id"] for item in sessions}) == 4,
            "Session identities must be distinct")
    by_label = {item["label"]: item for item in sessions}
    complete = all(item["primary"]["held_out_contributing_target_count"] == 5 for item in sessions)
    association = None
    if complete:
        association = spearman(
            [item["primary"]["loo_mean_target_median_absolute_vertical_lines"] for item in sessions],
            [item["primary"]["held_out_mean_target_median_absolute_vertical_lines"] for item in sessions],
        )
    return {
        "schema": "mgazenet_calibration_audit_comparison_v3",
        "protocol_id": PROTOCOL,
        "session_order": list(ORDER),
        "sessions": sessions,
        "within_phone": {
            "SM-G991B": phone_change(by_label["g991b_v3_r1"], by_label["g991b_v3_r2"]),
            "SM-A566B": phone_change(by_label["a56_v3_r1"], by_label["a56_v3_r2"]),
        },
        "four_session_spearman_primary": association,
        "all_sessions_have_five_held_out_targets": complete,
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
        "limitations": [
            "Four sessions from one participant cannot establish a stable correlation or population claim.",
            "Directional agreement is descriptive and does not assign a calibration-quality threshold.",
            "Stationary instructed targets do not establish natural-reading accuracy.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for label in ORDER:
        parser.add_argument("--" + label.replace("_", "-"), required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    paths = {label: getattr(args, label) for label in ORDER}
    summaries = {}
    hashes = {}
    for label, path in paths.items():
        raw = path.read_bytes()
        summaries[label] = json.loads(raw)
        hashes[label] = hashlib.sha256(raw).hexdigest()
    result = compare(summaries, hashes)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print("Compared four frozen calibration-audit sessions; no gate or promotion decision")


if __name__ == "__main__":
    main()
