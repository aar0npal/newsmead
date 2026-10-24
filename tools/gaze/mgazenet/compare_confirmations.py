#!/usr/bin/env python3
"""Compare the four frozen MGazeNet direct-screen confirmation summaries."""
import argparse
import hashlib
import json
from pathlib import Path


SCHEMA = "mgazenet_direct_validation_confirmation_summary_v1"
PROTOCOL = "mgazenet_direct_validation_confirmation_v1"
ORDER = (
    "a56_confirmation_1",
    "g991b_confirmation_1",
    "g991b_confirmation_2",
    "a56_confirmation_2",
)
EXPECTED = {
    "a56_confirmation_1": ("samsung/SM-A566B", "forward_then_reverse"),
    "g991b_confirmation_1": ("samsung/SM-G991B", "reverse_then_forward"),
    "g991b_confirmation_2": ("samsung/SM-G991B", "forward_then_reverse"),
    "a56_confirmation_2": ("samsung/SM-A566B", "reverse_then_forward"),
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def session_result(label, summary, summary_sha256=None):
    require(label in EXPECTED, "Unknown frozen confirmation label")
    device, order = EXPECTED[label]
    require(summary.get("schema") == SCHEMA and
            summary.get("evidence_kind") == "recorded",
            "Unexpected confirmation summary schema or evidence kind")
    require(summary.get("accuracy_gate_pass") is None and
            summary.get("confirmation_accuracy_pass") is None and
            summary.get("promotion_decision") == "not_evaluated",
            "Confirmation summary assigned an accuracy or promotion decision")
    provenance = summary.get("provenance")
    require(isinstance(provenance, dict) and
            provenance.get("protocol_id") == PROTOCOL and
            provenance.get("device_id") == device,
            "Unexpected protocol or device")
    for key in ("session_id", "pipeline_id", "calibration_id"):
        require(isinstance(provenance.get(key), str) and provenance[key],
                "Missing confirmation provenance")
    require(summary.get("validation_order") == order,
            "Confirmation order differs from the frozen sequence")
    prediction = summary.get("screen_prediction")
    require(isinstance(prediction, dict) and
            type(prediction.get("screen_candidate_pass")) is bool and
            type(prediction.get("confirmation_line_level_pass")) is bool and
            type(prediction.get("false_accept")) is bool and
            type(prediction.get("false_reject")) is bool,
            "Missing screen/downstream classification")
    screen_pass = prediction["screen_candidate_pass"]
    downstream_pass = prediction["confirmation_line_level_pass"]
    require(prediction.get("classification_agrees") is (screen_pass is downstream_pass) and
            prediction["false_accept"] is (screen_pass and not downstream_pass) and
            prediction["false_reject"] is (not screen_pass and downstream_pass),
            "Changed confirmation classification")
    confirmation = summary.get("confirmation")
    require(isinstance(confirmation, dict) and
            len(confirmation.get("blocks", [])) == 20 and
            len(confirmation.get("location_spatial", [])) == 10,
            "Incomplete confirmation summary")
    input_sha256 = summary.get("input_sha256")
    require(isinstance(input_sha256, str) and len(input_sha256) == 64 and
            all(char in "0123456789abcdef" for char in input_sha256),
            "Missing raw confirmation hash")
    return {
        "label": label,
        "provenance": provenance,
        "validation_order": order,
        "raw_report_sha256": input_sha256,
        "summary_sha256": summary_sha256,
        **prediction,
        "screen_primary_vertical_lines":
            summary["screen"]["sealed_result"]["mean_target_median_absolute_vertical_lines"],
        "confirmation_primary_vertical_lines":
            confirmation["target_balanced"]["mean_location_median_absolute_vertical_lines"],
        "confirmation_worst_location_median_vertical_lines":
            confirmation["target_balanced"]["worst_location_median_absolute_vertical_lines"],
    }


def compare(summaries, summary_hashes=None):
    require(set(summaries) == set(ORDER), "Exactly the four frozen confirmation sessions are required")
    summary_hashes = summary_hashes or {}
    sessions = [session_result(label,summaries[label],summary_hashes.get(label)) for label in ORDER]
    require(len({item["provenance"]["session_id"] for item in sessions}) == 4 and
            len({item["provenance"]["calibration_id"] for item in sessions}) == 4,
            "Confirmation session and calibration identities must be distinct")
    counts = {
        "true_accept": sum(item["screen_candidate_pass"] and
                           item["confirmation_line_level_pass"] for item in sessions),
        "true_reject": sum(not item["screen_candidate_pass"] and
                           not item["confirmation_line_level_pass"] for item in sessions),
        "false_accept": sum(item["false_accept"] for item in sessions),
        "false_reject": sum(item["false_reject"] for item in sessions),
    }
    if counts["false_accept"]:
        status = "reject"
    elif counts["true_accept"] >= 1 and counts["true_reject"] >= 1:
        status = "supports_next_proposal"
    else:
        status = "inconclusive"
    return {
        "schema": "mgazenet_direct_validation_confirmation_comparison_v1",
        "protocol_id": PROTOCOL,
        "session_order": list(ORDER),
        "sessions": sessions,
        "classification_counts": counts,
        "screen_confirmation_status": status,
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
        "limitations": [
            "Supports-next-proposal is not model promotion or exact-line validation.",
            "Four sessions from one participant cannot establish production or population reliability.",
            "Confirmation data may reject this screen but cannot be used to tune and retest it.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for label in ORDER:
        parser.add_argument("--"+label.replace("_", "-"), required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    paths = {label: getattr(args,label) for label in ORDER}
    summaries = {}
    hashes = {}
    for label,path in paths.items():
        raw = path.read_bytes()
        summaries[label] = json.loads(raw)
        hashes[label] = hashlib.sha256(raw).hexdigest()
    result = compare(summaries,hashes)
    with args.output.open("x",encoding="utf-8") as stream:
        json.dump(result,stream,indent=2,allow_nan=False)
        stream.write("\n")
    print(f"Compared four frozen confirmation sessions: {result['screen_confirmation_status']}")


if __name__ == "__main__":
    main()
