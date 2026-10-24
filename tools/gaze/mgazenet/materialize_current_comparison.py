#!/usr/bin/env python3
"""Materialize the frozen current estimator with reviewed comparison controls.

The command refuses an existing destination and never changes the active Git
branch or the preserved ``tmp/current-reference-ec635fd`` directory.
"""
from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import zipfile
from pathlib import Path


REFERENCE_COMMIT = "ec635fd01905544c8251c37b6891105ebbdee923"
REFERENCE_ARCHIVE_SHA256 = (
    "5c631be6d12892768b707d433eebbdab4c7f102f18738b2f9d21d19fe2518aca"
)
PATCH_SHA256 = "e577ef8f8f27e7bd0aef688ee1ff9e6153f3f41aaf76a7cc1b2cdfe28b9b4dea"
PATCH_FILES = {
    "app/build.gradle.kts",
    "app/src/androidTest/java/com/newsmead/gaze/CurrentComparisonIntegrationTest.kt",
    "app/src/main/assets/comparison/newsmead-current-vs-mgazenet-reading-v1.json",
    "app/src/main/java/com/newsmead/activities/GazeCalibrationActivity.kt",
    "app/src/main/java/com/newsmead/activities/ReadingValidationActivity.kt",
    "app/src/main/java/com/newsmead/gaze/ComparisonProtocol.kt",
    "app/src/main/java/com/newsmead/gaze/ComparisonRuntime.kt",
    "app/src/main/java/com/newsmead/gaze/ComparisonSlotStore.kt",
    "app/src/main/java/com/newsmead/gaze/ReadingValidationProtocol.kt",
    "app/src/main/java/com/newsmead/gaze/ReadingValidationSessionLog.kt",
    "app/src/test/java/com/newsmead/gaze/ComparisonProtocolTest.kt",
    "app/src/test/java/com/newsmead/gaze/ReadingValidationProtocolTest.kt",
}
SHARED_FILES = {
    "app/src/main/assets/comparison/newsmead-current-vs-mgazenet-reading-v1.json",
    "app/src/main/java/com/newsmead/gaze/ComparisonProtocol.kt",
    "app/src/main/java/com/newsmead/gaze/ComparisonRuntime.kt",
    "app/src/main/java/com/newsmead/gaze/ComparisonSlotStore.kt",
    "app/src/main/java/com/newsmead/gaze/ReadingValidationProtocol.kt",
    "app/src/main/java/com/newsmead/gaze/ReadingValidationSessionLog.kt",
    "app/src/test/java/com/newsmead/gaze/ComparisonProtocolTest.kt",
    "app/src/test/java/com/newsmead/gaze/ReadingValidationProtocolTest.kt",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalized_text_bytes(path: Path) -> bytes:
    return path.read_bytes().replace(b"\r\n", b"\n")


def run(*arguments: str, cwd: Path) -> str:
    completed = subprocess.run(
        arguments,
        cwd=cwd,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return completed.stdout.strip()


def patch_paths(patch: Path, root: Path) -> set[str]:
    output = run("git", "apply", "--numstat", str(patch), cwd=root)
    return {line.split("\t", 2)[2].replace("\\", "/") for line in output.splitlines()}


def safe_extract(archive: Path, destination: Path) -> None:
    with zipfile.ZipFile(archive) as bundle:
        for member in bundle.infolist():
            member_path = Path(member.filename)
            if member_path.is_absolute() or ".." in member_path.parts:
                raise ValueError(f"Unsafe archive member: {member.filename}")
        bundle.extractall(destination)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[3]
    archive = root / "tmp" / "current-reference-ec635fd.zip"
    patch = root / "tools" / "gaze" / "mgazenet" / "patches" / (
        "current-reference-ec635fd-comparison-v1.patch"
    )
    destination = (args.output or root / "tmp" / (
        f"current-comparison-ec635fd-{PATCH_SHA256[:12]}"
    )).resolve()
    tmp_root = (root / "tmp").resolve()

    if destination == tmp_root or tmp_root not in destination.parents:
        raise ValueError("The output must be a new directory below this repository's tmp directory.")
    if destination.exists():
        raise FileExistsError(f"Refusing existing output: {destination}")
    if run("git", "rev-parse", "gaze-pipeline-improvements^{commit}", cwd=root) != REFERENCE_COMMIT:
        raise ValueError("The preserved reference branch moved.")
    if not archive.is_file() or digest(archive) != REFERENCE_ARCHIVE_SHA256:
        raise ValueError("The frozen current-reference archive is missing or changed.")
    if not patch.is_file() or digest(patch) != PATCH_SHA256:
        raise ValueError("The reviewed current comparison patch is missing or changed.")
    if patch_paths(patch, root) != PATCH_FILES:
        raise ValueError("The current comparison patch changed its file allowlist.")

    destination.mkdir(parents=True)
    safe_extract(archive, destination)
    relative_destination = destination.relative_to(root).as_posix()
    run(
        "git",
        "apply",
        "--check",
        f"--directory={relative_destination}",
        str(patch),
        cwd=root,
    )
    run(
        "git",
        "apply",
        f"--directory={relative_destination}",
        str(patch),
        cwd=root,
    )

    # Git for Windows may materialize a newly patched text file with CRLF even
    # though this protocol is intentionally bound to its raw bytes.
    canonical_manifest = root / "tools" / "gaze" / "mgazenet" / "manifests" / (
        "newsmead-current-vs-mgazenet-reading-v1.json"
    )
    embedded_manifest = destination / (
        "app/src/main/assets/comparison/newsmead-current-vs-mgazenet-reading-v1.json"
    )
    if digest(canonical_manifest) != (
        "41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185"
    ):
        raise ValueError("The canonical frozen protocol manifest changed.")
    shutil.copyfile(canonical_manifest, embedded_manifest)

    local_properties = root / "local.properties"
    if local_properties.is_file():
        shutil.copyfile(local_properties, destination / "local.properties")
    for relative in SHARED_FILES:
        if normalized_text_bytes(destination / relative) != normalized_text_bytes(root / relative):
            raise ValueError(f"Shared comparison control differs: {relative}")

    print(f"materialized={destination}")
    print(f"reference_commit={REFERENCE_COMMIT}")
    print(f"reference_archive_sha256={REFERENCE_ARCHIVE_SHA256}")
    print(f"comparison_controls_sha256={PATCH_SHA256}")


if __name__ == "__main__":
    main()
