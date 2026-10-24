"""Collect one named numeric MGazeNet direct-screen confirmation record.

Does not install, launch, open a camera, score gaze values, or access NewsMead's
package. Complete and retained partial records must preserve privacy, hidden-
screen, no-correction, and no-promotion contracts.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


PACKAGE = "com.newsmead.mgazenetbenchmark"
PROTOCOL = "mgazenet_direct_validation_confirmation_v1"
SCHEMAS = {
    "mgazenet_direct_validation_confirmation_v1",
    "mgazenet_direct_validation_confirmation_partial_v1",
}


def validate(raw, expected_hash, run_id):
    digest = hashlib.sha256(raw).hexdigest()
    if digest != expected_hash.strip():
        raise ValueError("Confirmation report hash mismatch")
    report = json.loads(raw)
    if report.get("schema") not in SCHEMAS:
        raise ValueError("Not an isolated direct-screen confirmation record")
    if report.get("protocol_id") != PROTOCOL or report.get("session_id") != run_id:
        raise ValueError("Unexpected confirmation protocol or identity")
    required_false = (
        "active_tracker_access",
        "calibration_store_access",
        "camera_frames_retained",
        "features_retained",
        "personal_model_retained",
        "model_correction_applied",
    )
    if any(report.get(field) is not False for field in required_false):
        raise ValueError("Confirmation privacy or correction boundary changed")
    if report.get("screen_result_hidden_until_terminal") is not True:
        raise ValueError("Confirmation screen visibility boundary changed")
    if report.get("confirmation_accuracy_pass") is not None or \
            report.get("accuracy_gate_pass") is not None or \
            report.get("promotion_decision") != "not_evaluated":
        raise ValueError("Confirmation decision boundary changed")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--run", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+_[0-9a-f-]+", args.run):
        raise ValueError("Invalid confirmation run ID")
    if args.output.exists():
        raise ValueError("Use a new output directory")

    def read(name):
        remote = "files/confirmation/"+args.run+"/"+name
        return subprocess.run(
            [args.adb, "-s", args.serial, "exec-out", "run-as", PACKAGE, "cat", remote],
            check=True,
            capture_output=True,
        ).stdout

    raw = read("report.json")
    expected = read("report.sha256").decode("ascii")
    report = validate(raw, expected, args.run)
    args.output.mkdir(parents=True)
    (args.output/"report.json").write_bytes(raw)
    (args.output/"report.sha256").write_text(expected, encoding="ascii")
    print(f"Saved {report.get('outcome')} confirmation record without scoring: {args.output}")


if __name__ == "__main__":
    main()
