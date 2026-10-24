#!/usr/bin/env python3
"""Compare protocol-v4 reading validation OFF/ON logs.

The report separates the logged base mapper output from the effective raw output
and the effective stabilized output.  It never treats invalid samples as zero
error; accuracy denominators include every measured sample, matching the app.
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


MEASURED_PHASES = {"LOCALIZATION", "GUIDED_LINE_READING"}
TARGET_PREFIXES = ("base_target", "raw_target", "stable_target")


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    rank = fraction * (len(ordered) - 1)
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return ordered[low]
    return ordered[low] + (ordered[high] - ordered[low]) * (rank - low)


def load_records(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if line.strip():
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError as exc:
                    raise ValueError(f"{path}:{line_number}: {exc}") from exc
    return records


def measured_samples(records: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        record
        for record in records
        if record.get("record_type") == "gaze_sample"
        and record.get("phase") in MEASURED_PHASES
        and record.get("trial_state") == "MEASURE"
        and record.get("expected_checkpoint")
    ]


def target_summary(
    samples: list[dict[str, Any]], prefix: str
) -> dict[str, int | float | None]:
    valid = [sample for sample in samples if sample.get(f"{prefix}_valid")]
    signed_errors = [
        int(sample[f"{prefix}_line"]) - int(sample["expected_line"])
        for sample in valid
    ]
    absolute_errors = [abs(error) for error in signed_errors]
    word_samples = [
        sample for sample in samples if sample.get("expected_target_kind") == "WORD_FIXATION"
    ]
    guided_samples = [
        sample for sample in samples if sample.get("expected_target_kind") == "LINE_READING"
    ]

    def exact_line(subset: list[dict[str, Any]]) -> int:
        return sum(
            bool(sample.get(f"{prefix}_valid"))
            and sample.get(f"{prefix}_line") == sample.get("expected_line")
            for sample in subset
        )

    def within_one(subset: list[dict[str, Any]]) -> int:
        return sum(
            bool(sample.get(f"{prefix}_valid"))
            and abs(int(sample[f"{prefix}_line"]) - int(sample["expected_line"])) <= 1
            for sample in subset
        )

    exact_word = sum(
        bool(sample.get(f"{prefix}_valid"))
        and sample.get(f"{prefix}_word_start") == sample.get("expected_target_start")
        and sample.get(f"{prefix}_word_end") == sample.get("expected_target_end")
        for sample in word_samples
    )

    def ratio(numerator: int, denominator: int) -> float | None:
        return numerator / denominator if denominator else None

    return {
        "samples": len(samples),
        "valid": len(valid),
        "valid_fraction": ratio(len(valid), len(samples)),
        "exact_line": ratio(exact_line(samples), len(samples)),
        "within_one_line": ratio(within_one(samples), len(samples)),
        "median_signed_line_error": percentile([float(v) for v in signed_errors], 0.50),
        "median_absolute_line_error": percentile([float(v) for v in absolute_errors], 0.50),
        "p95_absolute_line_error": percentile([float(v) for v in absolute_errors], 0.95),
        "word_samples": len(word_samples),
        "word_exact_line": ratio(exact_line(word_samples), len(word_samples)),
        "word_within_one_line": ratio(within_one(word_samples), len(word_samples)),
        "word_exact_word": ratio(exact_word, len(word_samples)),
        "guided_samples": len(guided_samples),
        "guided_exact_line": ratio(exact_line(guided_samples), len(guided_samples)),
        "guided_within_one_line": ratio(within_one(guided_samples), len(guided_samples)),
    }


def analyze(path: Path) -> dict[str, Any]:
    records = load_records(path)
    start = next(record for record in records if record.get("record_type") == "session_start")
    end = next(record for record in reversed(records) if record.get("record_type") == "session_end")
    fit = next(
        (record for record in records if record.get("record_type") == "vertical_alignment_fit"),
        None,
    )
    references = [
        record
        for record in records
        if record.get("record_type") == "vertical_alignment_reference"
    ]
    samples = measured_samples(records)
    checkpoints: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for sample in samples:
        checkpoints[str(sample["expected_checkpoint"])].append(sample)

    fps_values = [
        float(record["fps"])
        for record in records
        if record.get("record_type") == "fps"
        and record.get("phase") in MEASURED_PHASES
        and record.get("trial_state") == "MEASURE"
        and isinstance(record.get("fps"), (int, float))
        and float(record["fps"]) > 0
    ]
    correction_deltas = [
        float(sample.get("vertical_alignment_delta_px", 0.0)) for sample in samples
    ]

    checkpoint_report: dict[str, Any] = {}
    for checkpoint_id, checkpoint_samples in checkpoints.items():
        first = checkpoint_samples[0]
        checkpoint_report[checkpoint_id] = {
            "region": first.get("expected_region"),
            "kind": first.get("expected_target_kind"),
            "expected_line": first.get("expected_line"),
            **{
                prefix: target_summary(checkpoint_samples, prefix)
                for prefix in TARGET_PREFIXES
            },
        }

    return {
        "path": str(path),
        "session": {
            key: start.get(key)
            for key in (
                "session_id",
                "run_label",
                "order_variant",
                "protocol_version",
                "vertical_alignment_mode",
                "detailed_source_telemetry_enabled",
                "drift_correction_active",
            )
        },
        "outcome": end.get("outcome"),
        "references": references,
        "fit": fit,
        "fps": {
            "count": len(fps_values),
            "median": percentile(fps_values, 0.50),
            "p05": percentile(fps_values, 0.05),
        },
        "correction_delta_px": {
            "median": percentile(correction_deltas, 0.50),
            "min": min(correction_deltas) if correction_deltas else None,
            "max": max(correction_deltas) if correction_deltas else None,
        },
        "targets": {prefix: target_summary(samples, prefix) for prefix in TARGET_PREFIXES},
        "checkpoints": checkpoint_report,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("off_log", type=Path)
    parser.add_argument("on_log", type=Path)
    args = parser.parse_args()
    report = {"off": analyze(args.off_log), "on": analyze(args.on_log)}
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
