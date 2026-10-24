"""Collect one named numeric calibration-audit record from the isolated app.

Does not install, launch, open a camera, score gaze values or access NewsMead's
package. It accepts complete and retained partial outcomes and refuses identity,
hash, privacy, correction or decision-contract changes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGE = "com.newsmead.mgazenetbenchmark"
PROTOCOL = "mgazenet_calibration_observability_v3"
SCHEMAS = {
    "mgazenet_calibration_audit_v3",
    "mgazenet_calibration_audit_partial_v3",
}


def validate(raw, expected_hash, run_id):
    digest = hashlib.sha256(raw).hexdigest()
    if digest != expected_hash.strip():
        raise ValueError("Calibration-audit report hash mismatch")
    report = json.loads(raw)
    if report.get("schema") not in SCHEMAS:
        raise ValueError("Not an isolated calibration-audit v3 record")
    if report.get("protocol_id") != PROTOCOL or report.get("session_id") != run_id:
        raise ValueError("Unexpected calibration-audit protocol or identity")
    required_false = (
        "active_tracker_access",
        "calibration_store_access",
        "camera_frames_retained",
        "features_retained",
        "personal_model_retained",
    )
    if any(report.get(field) is not False for field in required_false):
        raise ValueError("Calibration-audit privacy boundary changed")
    if report.get("model_correction_applied", False) is not False:
        raise ValueError("Calibration-audit correction boundary changed")
    if report.get("accuracy_gate_pass") is not None or report.get("promotion_decision") != "not_evaluated":
        raise ValueError("Calibration-audit decision boundary changed")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--run", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+_[0-9a-f-]+", args.run):
        raise ValueError("Invalid calibration-audit run ID")
    if args.output.exists():
        raise ValueError("Use a new output directory")

    def read(name):
        remote = "files/calibration-audit/" + args.run + "/" + name
        return subprocess.run(
            [args.adb, "-s", args.serial, "exec-out", "run-as", PACKAGE, "cat", remote],
            check=True,
            capture_output=True,
        ).stdout

    raw = read("report.json")
    expected = read("report.sha256").decode("ascii")
    report = validate(raw, expected, args.run)
    args.output.mkdir(parents=True)
    (args.output / "report.json").write_bytes(raw)
    (args.output / "report.sha256").write_text(expected, encoding="ascii")
    print(f"Saved {report.get('outcome')} calibration-audit record without scoring: {args.output}")


if __name__ == "__main__":
    main()
