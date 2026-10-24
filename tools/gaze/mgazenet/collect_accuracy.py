"""Collect one explicitly named numeric accuracy record from the isolated app.

Does not launch, install, calibrate, open cameras or read NewsMead's storage.
Participant collection must be authorized before using this command on a real run.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def validate(raw, expected_hash, run_id):
    digest = hashlib.sha256(raw).hexdigest()
    if digest != expected_hash.strip():
        raise ValueError("Accuracy report hash mismatch")
    report = json.loads(raw)
    if report.get("schema") not in ("mgazenet_accuracy_v1", "mgazenet_accuracy_partial_v1",
                                     "mgazenet_accuracy_v2", "mgazenet_accuracy_partial_v2"):
        raise ValueError("Not an isolated accuracy record")
    if report.get("session_id") != run_id or report.get("camera_frames_retained") is not False:
        raise ValueError("Unexpected session identity or image retention")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--run", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+_[0-9a-f-]+", args.run):
        raise ValueError("Invalid run ID")
    if args.output.exists():
        raise ValueError("Use a new output directory")
    def read(name):
        return subprocess.run([args.adb, "-s", args.serial, "exec-out", "run-as",
            "com.newsmead.mgazenetbenchmark", "cat", "files/accuracy/"+args.run+"/"+name],
            check=True, capture_output=True).stdout
    raw = read("report.json")
    expected = read("report.sha256").decode("ascii")
    report = validate(raw, expected, args.run)
    args.output.mkdir(parents=True)
    (args.output/"report.json").write_bytes(raw)
    (args.output/"report.sha256").write_text(expected, encoding="ascii")
    print(f"Saved {report.get('outcome')} record; no accuracy decision assigned: {args.output}")


if __name__ == "__main__":
    main()
