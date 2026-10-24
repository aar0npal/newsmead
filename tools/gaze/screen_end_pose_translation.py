"""Offline screen for one fixed calibration-to-end-pose feature translation.

The candidate uses the raw feature difference between the existing repeated
same-target drift observations. That full two-axis difference is added to every
16-point fit aggregate before fitting the unchanged deployed quadratic mapper.
The five later held-out aggregates are never changed or used for fitting.

This is a retrospective candidate screen, not a runtime calibration change.
Inputs are explicit files or exact app-private filenames read through adb. The
report contains only derived errors, summary values, filenames, and hashes.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np

from compare_vertical_mapper import (
    BASELINE,
    extract,
    point_arrays,
    predict,
    read_source,
    verify_baseline,
)


DRIFT_MATCH_TOLERANCE = 1e-6


def summarize(predicted: np.ndarray, truth: np.ndarray) -> dict:
    residual = np.asarray(predicted, dtype=float) - np.asarray(truth, dtype=float)
    vertical = np.sort(np.abs(residual[:, 1]))
    radial = np.sort(np.linalg.norm(residual, axis=1))

    def metrics(values: np.ndarray) -> dict:
        return {
            "median_px": float(np.median(values)),
            "p95_px": float(values[math.ceil(0.95 * len(values)) - 1]),
            "max_px": float(values[-1]),
        }

    return {
        "n": len(residual),
        "vertical": metrics(vertical),
        "two_dimensional": metrics(radial),
        "mean_signed_dx_px": float(np.mean(residual[:, 0])),
        "mean_signed_dy_px": float(np.mean(residual[:, 1])),
    }


def drift_vector(payload: dict, fit: list[dict]) -> np.ndarray:
    accepted_repeats = [
        point for point in payload.get("points", [])
        if point.get("kind") == "DRIFT_REPEAT" and point.get("status") == "ACCEPTED"
    ]
    if not accepted_repeats:
        raise ValueError("Missing accepted repeated drift target")
    repeat = accepted_repeats[-1]
    matching_fit = [point for point in fit if point.get("point_id") == repeat.get("point_id")]
    if len(matching_fit) != 1:
        raise ValueError("Repeated drift target does not identify one final fit point")
    first_point = matching_fit[0]
    if (first_point["screen_x"], first_point["screen_y"]) != (repeat["screen_x"], repeat["screen_y"]):
        raise ValueError("Repeated drift target screen coordinates differ from the fit target")

    check = payload.get("drift_check") or {}
    first = np.asarray(check.get("first_pass_feature"), dtype=np.float32)
    second = np.asarray(check.get("second_pass_feature"), dtype=np.float32)
    if first.shape != (2,) or second.shape != (2,) or not np.isfinite(first).all() or not np.isfinite(second).all():
        raise ValueError("Missing finite two-axis drift features")
    logged_first = np.asarray(first_point.get("aggregated_feature"), dtype=np.float32)
    logged_second = np.asarray(repeat.get("aggregated_feature"), dtype=np.float32)
    if np.max(np.abs(first - logged_first)) > DRIFT_MATCH_TOLERANCE:
        raise ValueError("Drift first-pass feature does not match the final fit point")
    if np.max(np.abs(second - logged_second)) > DRIFT_MATCH_TOLERANCE:
        raise ValueError("Drift second-pass feature does not match the repeated point")
    return second - first


def analyze_payload(payload: dict) -> dict:
    fit, held = extract(payload)
    features, targets = point_arrays(fit)
    held_features, held_targets = point_arrays(held)
    baseline = predict(features, targets, held_features, BASELINE)
    parity = verify_baseline(payload, targets, held_targets, *baseline)

    drift = drift_vector(payload, fit)
    translated_features = (features + drift).astype(np.float32)
    candidate = predict(translated_features, targets, held_features, BASELINE)

    arms = {}
    for name, predictions in (("deployed", baseline), ("end_pose_translation", candidate)):
        arms[name] = {
            "loo": summarize(predictions[0], targets),
            "held_out": summarize(predictions[1], held_targets),
        }

    points = []
    for index, truth in enumerate(held_targets):
        old = baseline[1][index] - truth
        new = candidate[1][index] - truth
        points.append({
            "id": index,
            "target_x_px": float(truth[0]),
            "target_y_px": float(truth[1]),
            "baseline_dx_px": float(old[0]),
            "baseline_dy_px": float(old[1]),
            "candidate_dx_px": float(new[0]),
            "candidate_dy_px": float(new[1]),
        })

    baseline_held = arms["deployed"]["held_out"]
    candidate_held = arms["end_pose_translation"]["held_out"]
    improves_vertical_median_and_max = (
        candidate_held["vertical"]["median_px"] < baseline_held["vertical"]["median_px"]
        and candidate_held["vertical"]["max_px"] < baseline_held["vertical"]["max_px"]
    )
    improves_all_held_metrics = improves_vertical_median_and_max and all(
        candidate_held["two_dimensional"][metric] <= baseline_held["two_dimensional"][metric]
        for metric in ("median_px", "max_px")
    )
    return {
        "label": payload.get("run_label"),
        "timestamp": payload.get("timestamp_start"),
        "feature_mode": payload.get("raw_feature_mode"),
        "telemetry_mode": payload.get("telemetry_mode"),
        "baseline_max_parity_delta_px": parity,
        "verified_coordinate_pairs": 21,
        "drift_feature_dx": float(drift[0]),
        "drift_feature_dy": float(drift[1]),
        "logged_high_drift": bool((payload.get("drift_check") or {}).get("flagged_high_drift")),
        "arms": arms,
        "held_out_points": points,
        "improves_vertical_median_and_max": improves_vertical_median_and_max,
        "improves_all_held_metrics": improves_all_held_metrics,
    }


def aggregate(reports: list[dict]) -> dict:
    result = {}
    for error_type in ("vertical", "two_dimensional"):
        result[error_type] = {}
        for metric in ("median_px", "p95_px", "max_px"):
            old = [report["arms"]["deployed"]["held_out"][error_type][metric] for report in reports]
            new = [report["arms"]["end_pose_translation"]["held_out"][error_type][metric] for report in reports]
            result[error_type][metric] = {
                "baseline_session_median": float(np.median(old)),
                "candidate_session_median": float(np.median(new)),
                "baseline_session_mean": float(np.mean(old)),
                "candidate_session_mean": float(np.mean(new)),
                "improved_sessions": sum(b < a for a, b in zip(old, new)),
                "worsened_sessions": sum(b > a for a, b in zip(old, new)),
                "tied_sessions": sum(b == a for a, b in zip(old, new)),
            }
    result["vertical_median_and_max_improved_sessions"] = sum(
        report["improves_vertical_median_and_max"] for report in reports
    )
    result["all_selected_held_metrics_improved_sessions"] = sum(
        report["improves_all_held_metrics"] for report in reports
    )
    return result


def build_report(sources: list[str], adb: str | None = None) -> dict:
    reports = []
    hashes = set()
    for source in sources:
        raw = read_source(source, adb)
        fingerprint = hashlib.sha256(raw).hexdigest()
        if fingerprint in hashes:
            raise ValueError("Duplicate source payload")
        hashes.add(fingerprint)
        report = analyze_payload(json.loads(raw))
        report.update(source=Path(source).name, bytes=len(raw), sha256=fingerprint)
        reports.append(report)
    if not reports:
        raise ValueError("No eligible sessions")
    return {
        "method": "fixed_full_drift_vector_translation_to_end_pose",
        "retrospective_only": True,
        "session_count": len(reports),
        "candidate_definition": (
            "Add the complete second-minus-first same-target raw feature vector to every fit "
            "aggregate; fit the unchanged quadratic; leave held-out observations untouched."
        ),
        "limitations": [
            "Repeated sessions are from one participant and one phone, not independent participants.",
            "The repeated target estimates a global translation, not regional or nonlinear drift.",
            "Held-out checks immediately follow calibration and are not reading-path samples.",
            "The candidate is fixed in advance; no scale, cap, axis choice, or parameter sweep is evaluated.",
            "Retrospective improvement can only justify a prospective isolated test, not deployment.",
        ],
        "aggregate": aggregate(reports),
        "sessions": reports,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", nargs="+")
    parser.add_argument("--adb", help="Absolute adb path; raw device payloads stay in memory")
    args = parser.parse_args()
    print(json.dumps(build_report(args.sources, args.adb), indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
