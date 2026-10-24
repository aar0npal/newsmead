#!/usr/bin/env python3
"""Retrospective v4 line-only replay; no app mutation and no raw device archive.

Fixed candidate: y' = y + median(reference target_y - observed_median_y).
Reference values alone fit it. Expected reading targets are evaluation-only.
No offset/gain sweep, clipping, shrinkage, or result-dependent gates are used.

v4 does not log actual Layout line tops or glyph boxes. Recover its constant
interior line pitch from UNSCORED coordinate-to-AOI observations, not expected
targets, and recover content height from a bottom-clamped scroll. Refuse to
score unless identity and the recorded effective path exactly reproduce all
measured base/raw valid flags and line indices. Never invent exact-word or
stabilized counterfactual metrics. This is retrospective screening, not an
independent prospective validation of offset-only.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import statistics
import struct
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path

from analyze_reading_vertical_pair import measured_samples, percentile


def float32(value: float) -> float:
    return struct.unpack("f", struct.pack("f", value))[0]


def reference_offset(references: list[dict]) -> float:
    if len(references) != 3 or len({r["reference_id"] for r in references}) != 3:
        raise ValueError("Need three distinct references")
    if any(r["sample_count"] < 15 for r in references):
        raise ValueError("Insufficient reference coverage")
    residuals = [r["target_y_screen_px"] - r["observed_median_y_screen_px"] for r in references]
    if not all(math.isfinite(v) for v in residuals):
        raise ValueError("Nonfinite reference")
    return statistics.median(residuals)


def infer_pitch(observations: list[tuple[int, int]], line_count: int, nominal_height: float) -> tuple[int, int]:
    """Find integer interior boundaries b[k]=intercept+pitch*k for k>=1.

    Interior Android line tops are integer pixel offsets. The first and last
    line heights may differ because of font padding and final-line spacing.
    """
    if not observations:
        raise ValueError("No unscored layout observations")
    solutions = []
    for pitch in range(1, int(math.ceil(2 * nominal_height)) + 1):
        lower = max(v - pitch * (k + 1) + 1 for v, k in observations if k < line_count - 1)
        upper = min(v - pitch * k for v, k in observations if k > 0)
        if lower <= upper:
            if lower != upper:
                raise ValueError("Ambiguous line intercept; no exact replay possible")
            solutions.append((pitch, lower))
    if len(solutions) != 1:
        raise ValueError(f"Need one line-grid solution, got {solutions}")
    return solutions[0]


@dataclass(frozen=True)
class Geometry:
    pitch_px: int
    interior_intercept_px: int
    line_count: int
    content_height_px: int
    viewport_top_px: int
    viewport_bottom_px: int
    margin_px: int

    def line_top(self, line: int) -> int:
        return 0 if line == 0 else self.interior_intercept_px + self.pitch_px * line

    def line_bottom(self, line: int) -> int:
        return self.content_height_px if line == self.line_count - 1 else self.line_top(line + 1)

    def line_center(self, line: int) -> float:
        return (self.line_top(line) + self.line_bottom(line)) / 2

    def line_at(self, y: float, text_top: int) -> int:
        local = y - text_top
        if not (self.viewport_top_px <= y < self.viewport_bottom_px and 0 <= local < self.content_height_px):
            return -1
        return min(self.line_count - 1, max(0, (int(local) - self.interior_intercept_px) // self.pitch_px))


def infer_geometry(records: list[dict]) -> Geometry:
    start = records[0]
    layout = next(r for r in records if r["record_type"] == "layout")
    refs = sorted((r for r in records if r["record_type"] == "vertical_alignment_reference"), key=lambda r: r["target_y_screen_px"])
    if len(refs) != 3:
        raise ValueError("Need protocol-v4 reference geometry")
    height = (refs[2]["target_y_screen_px"] - refs[0]["target_y_screen_px"]) / .60
    top = refs[0]["target_y_screen_px"] - .20 * height
    if abs(top - round(top)) > .001 or abs(height - round(height)) > .001:
        raise ValueError("Reference geometry is not the expected integer viewport")
    if abs(refs[1]["target_y_screen_px"] - (top + .50 * height)) > .001:
        raise ValueError("Unexpected middle-reference position")
    top, height = round(top), round(height)
    gaze = [r for r in records if r["record_type"] == "gaze_sample"]
    unscored = [r for r in gaze if r.get("trial_state") != "MEASURE"]
    observations = [
        (int(r[y] - r["text_top_screen_px"]), r[p + "_line"])
        for r in unscored
        for p, y in [("base_target", "gaze_y_screen_px"), ("raw_target", "effective_gaze_y_screen_px")]
        if r[p + "_valid"]
    ]
    pitch, intercept = infer_pitch(observations, layout["line_count"], layout["line_height_px"])
    origins = {r["text_top_screen_px"] + r["scroll_y"] for r in gaze}
    if len(origins) != 1:
        raise ValueError("Changing text origin; static layout replay invalid")
    margin = origins.pop() - top
    expected_margin = math.floor(16 * start["density_dpi"] / 160 + .5)
    if margin != expected_margin:
        raise ValueError("Layout margin does not match v4 XML")
    samples = measured_samples(records)
    max_scroll = max(r["scroll_y"] for r in samples)
    # localization_7 requests a 22%-viewport placement, but near document end
    # cannot reach it. Require a measured witness of this bottom-clamped scroll.
    witnesses = [r for r in samples if r["expected_checkpoint"] == "localization_7" and r["scroll_y"] == max_scroll]
    if not witnesses:
        raise ValueError("No bottom-scroll witness")
    k = witnesses[0]["expected_line"]
    if not 0 < k < layout["line_count"] - 1:
        raise ValueError("Cannot reconstruct witness line center")
    requested = margin + int(intercept + pitch * (k + .5)) - int(height * .22)
    if requested <= max_scroll:
        raise ValueError("Scroll was not clamped; content height unknown")
    content_height = max_scroll + height - 2 * margin
    geometry = Geometry(pitch, intercept, layout["line_count"], content_height, top, top + height, margin)
    if not geometry.line_top(geometry.line_count - 1) < content_height:
        raise ValueError("Invalid final line")
    for r in samples:
        for p, y in [("base_target", "gaze_y_screen_px"), ("raw_target", "effective_gaze_y_screen_px")]:
            predicted = geometry.line_at(r[y], r["text_top_screen_px"])
            expected = r[p + "_line"] if r[p + "_valid"] else -1
            if predicted != expected:
                raise ValueError(f"Geometry mismatch {r['step_id']} {p}: {predicted} != {expected}")
    return geometry


def summarize(samples: list[dict], ys: list[float], geometry: Geometry) -> dict:
    indices = [geometry.line_at(y, r["text_top_screen_px"]) for r, y in zip(samples, ys)]
    signed = [index - r["expected_line"] for r, index in zip(samples, indices) if index >= 0]
    absolute = [abs(v) for v in signed]
    # Unlike assignment-error tails, this includes off-text samples as well.
    center_errors = [abs(y - r["text_top_screen_px"] - geometry.line_center(r["expected_line"])) / geometry.pitch_px for r, y in zip(samples, ys)]
    count = len(samples)
    return {
        "n": count,
        "valid": len(signed),
        "valid_pct": 100 * len(signed) / count,
        "exact_line_pct": 100 * sum(v == 0 for v in signed) / count,
        "within_one_line_pct": 100 * sum(abs(v) <= 1 for v in signed) / count,
        "valid_only_median_absolute_line_error": percentile(absolute, .50),
        "valid_only_p95_absolute_line_error": percentile(absolute, .95),
        "valid_only_max_absolute_line_error": max(absolute) if absolute else None,
        "all_sample_center_error_median_lines": percentile(center_errors, .50),
        "all_sample_center_error_p95_lines": percentile(center_errors, .95),
        "all_sample_center_error_max_lines": max(center_errors),
    }


def analyze_records(records: list[dict]) -> dict:
    start = records[0]
    end = records[-1]
    if start.get("protocol_version") != 4 or end.get("outcome") != "completed":
        raise ValueError("Require a completed protocol-v4 run; rejected/interrupted runs must be reported separately")
    refs = [r for r in records if r["record_type"] == "vertical_alignment_reference"]
    fit = next(r for r in records if r["record_type"] == "vertical_alignment_fit")
    offset = reference_offset(refs)
    geometry = infer_geometry(records)
    samples = measured_samples(records)
    if len(samples) != end["measured_sample_count"] or len({r["expected_checkpoint"] for r in samples}) != 14:
        raise ValueError("Measured count/checkpoint mismatch")
    if start.get("detailed_source_telemetry_enabled") or start.get("drift_correction_active"):
        raise ValueError("Unexpected control condition")
    base = [r["gaze_y_screen_px"] for r in samples]
    original = [float32(fit["intercept_px"] + fit["gain"] * y) for y in base] if fit["accepted"] else base
    if fit["applied"]:
        if max(abs(y - r["effective_gaze_y_screen_px"]) for r, y in zip(samples, original)) > .001:
            raise ValueError("Logged original correction formula mismatch")
    elif any(r["vertical_alignment_delta_px"] != 0 for r in samples):
        raise ValueError("Unexpected OFF correction")
    variants = {"base": base, "offset_only": [float32(y + offset) for y in base], "original_guarded_gain_bias": original}
    report = {
        "label": start["run_label"], "mode": start["vertical_alignment_mode"],
        "order": start["order_variant"], "session_id": start["session_id"],
        "offset_px": offset, "offset_in_actual_line_pitches": offset / geometry.pitch_px,
        "original_fit_accepted": fit["accepted"], "original_fit_applied_live": fit["applied"],
        "original_gain": fit["gain"], "original_intercept_px": fit["intercept_px"],
        "original_gate_reason": fit["reason"],
        "geometry": asdict(geometry), "nominal_line_height_px": next(r["line_height_px"] for r in records if r["record_type"] == "layout"),
        "reproduced_measured_base_and_effective_assignments": 2 * len(samples),
        "exact_word_replay": "not_available_glyph_geometry_not_logged",
        "stabilized_replay": "not_performed_compare_raw_line_assignment_only",
        "variants": {},
    }
    for name, ys in variants.items():
        arm = {"all": summarize(samples, ys, geometry), "phases": {}, "checkpoints": {}}
        for phase in ("LOCALIZATION", "GUIDED_LINE_READING"):
            selected = [i for i, r in enumerate(samples) if r["phase"] == phase]
            arm["phases"][phase] = summarize([samples[i] for i in selected], [ys[i] for i in selected], geometry)
        for cp in dict.fromkeys(r["expected_checkpoint"] for r in samples):
            selected = [i for i, r in enumerate(samples) if r["expected_checkpoint"] == cp]
            result = summarize([samples[i] for i in selected], [ys[i] for i in selected], geometry)
            result["nominal_region"] = samples[selected[0]]["expected_region"]
            result["actual_target_center_screen_px"] = samples[selected[0]]["text_top_screen_px"] + geometry.line_center(samples[selected[0]]["expected_line"])
            arm["checkpoints"][cp] = result
        arm["equal_checkpoint_weight_exact_line_pct"] = statistics.mean(r["exact_line_pct"] for r in arm["checkpoints"].values())
        arm["equal_checkpoint_weight_within_one_line_pct"] = statistics.mean(r["within_one_line_pct"] for r in arm["checkpoints"].values())
        arm["zero_valid_checkpoints"] = [cp for cp, r in arm["checkpoints"].items() if r["valid"] == 0]
        report["variants"][name] = arm
    report["offset_checkpoint_deltas"] = []
    for cp, baseline in report["variants"]["base"]["checkpoints"].items():
        candidate = report["variants"]["offset_only"]["checkpoints"][cp]
        report["offset_checkpoint_deltas"].append({
            "checkpoint": cp,
            "actual_target_center_screen_px": baseline["actual_target_center_screen_px"],
            "exact_line_change_pp": candidate["exact_line_pct"] - baseline["exact_line_pct"],
            "within_one_line_change_pp": candidate["within_one_line_pct"] - baseline["within_one_line_pct"],
            "all_sample_center_p95_change_lines": candidate["all_sample_center_error_p95_lines"] - baseline["all_sample_center_error_p95_lines"],
            "valid_count_change": candidate["valid"] - baseline["valid"],
        })
    return report


def read_source(source: str, adb: str | None) -> bytes:
    path = Path(source)
    if path.is_file():
        return path.read_bytes()
    if adb and re.fullmatch(r"reading_validation_session_\d{8}_\d{6}_\d{3}\.jsonl", source):
        return subprocess.check_output([adb, "exec-out", "run-as", "com.newsmead", "cat", "files/" + source], timeout=30)
    raise ValueError(f"No local source or allowed explicit device filename: {source}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", nargs="+")
    parser.add_argument("--adb", help="Optional absolute adb path; device payloads stay in memory")
    parser.add_argument("--compact", action="store_true")
    args = parser.parse_args()
    reports = []
    for source in args.sources:
        payload = read_source(source, args.adb)
        report = analyze_records([json.loads(line) for line in payload.splitlines() if line.strip()])
        report.update(source=source, bytes=len(payload), sha256=hashlib.sha256(payload).hexdigest())
        if args.compact:
            for arm in report["variants"].values():
                arm.pop("checkpoints")
        reports.append(report)
    print(json.dumps({"method": "fixed_three_reference_median_offset_no_clipping", "retrospective_only": True, "runs": reports}, indent=2))


if __name__ == "__main__":
    main()
