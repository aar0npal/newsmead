"""Collect one named numeric input-check record from the isolated benchmark app.

Does not install, launch, open a camera or access NewsMead's package. It accepts
complete and retained partial outcomes and refuses identity, hash or retention
contract changes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGE = "com.newsmead.mgazenetbenchmark"


def validate(raw, expected_hash, run_id):
    digest = hashlib.sha256(raw).hexdigest()
    if digest != expected_hash.strip():
        raise ValueError("Input-check report hash mismatch")
    report = json.loads(raw)
    if report.get("schema") not in (
        "mgazenet_input_check_v1",
        "mgazenet_input_check_partial_v1",
    ):
        raise ValueError("Not an isolated input-check record")
    if report.get("protocol_id") != "mgazenet_input_check_v1":
        raise ValueError("Unexpected input-check protocol")
    if report.get("session_id") != run_id:
        raise ValueError("Unexpected input-check identity")
    retained = ("camera_frames_retained", "crop_images_retained",
                "landmarks_retained", "features_retained", "personal_model_retained")
    if any(report.get(field) is not False for field in retained):
        raise ValueError("Input-check retention contract changed")
    if report.get("svr_fit") is not False or report.get("calibration_grid_presented") is not False:
        raise ValueError("Input-check calibration boundary changed")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--run", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+_[0-9a-f-]+", args.run):
        raise ValueError("Invalid input-check run ID")
    if args.output.exists():
        raise ValueError("Use a new output directory")

    def read(name):
        remote = "files/input-check/" + args.run + "/" + name
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
    print(f"Saved {report.get('outcome')} input-check record: {args.output}")


if __name__ == "__main__":
    main()
