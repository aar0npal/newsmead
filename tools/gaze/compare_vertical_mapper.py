"""Fixed offline screen: deployed six-term y versus [1, zy, zy^2].

Both use training-fold population standardization (std floor 1e-6), ridge 1
excluding the intercept, float32 inputs/outputs, and boundary-gradient tanh
extension with scale 2.5. The x map is always the deployed six-term map.
No parameter search, reading-answer fitting, or runtime changes are performed.
Only accepted, complete eye-local 16-point calibrations are eligible. Every
baseline LOO residual and held-out prediction must reproduce the saved log.
Device inputs are explicit filenames read into memory; output is derived
pointwise errors/aggregate metrics and source hashes, never raw features.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess

import numpy as np


FEATURE_MODE = "eye_local_width_average_v1"
BASELINE = "quadratic_xy"
CANDIDATE = "quadratic_y_only"
PARITY_TOLERANCE_PX = 0.001


def design(z: np.ndarray, mode: str) -> np.ndarray:
    x, y = z
    if mode == BASELINE:
        return np.array([1.0, x, y, x*x, y*y, x*y])
    if mode == CANDIDATE:
        return np.array([1.0, y, y*y])
    raise ValueError(f"Unknown model: {mode}")


class Mapper:
    """Scalar y fit; identical baseline x fit retained for both arms."""

    def __init__(self, features: np.ndarray, targets: np.ndarray, mode: str):
        features = np.asarray(features, dtype=np.float32).astype(float)
        targets = np.asarray(targets, dtype=np.float32).astype(float)
        if features.shape != targets.shape or features.ndim != 2 or features.shape[1] != 2:
            raise ValueError("Expected aligned Nx2 feature/target arrays")
        if len(features) < 6 or not np.isfinite(features).all() or not np.isfinite(targets).all():
            raise ValueError("Need at least six finite points")
        self.mode = mode
        self.mean = features.mean(axis=0)
        self.std = np.maximum(features.std(axis=0), 1e-6)
        z = (features - self.mean) / self.std
        self.lo, self.hi = z.min(axis=0), z.max(axis=0)
        self.beta_x = self.fit(np.array([design(row, BASELINE) for row in z]), targets[:, 0])
        self.beta_y = self.fit(np.array([design(row, mode) for row in z]), targets[:, 1])

    @staticmethod
    def fit(matrix: np.ndarray, targets: np.ndarray) -> np.ndarray:
        normal = matrix.T @ matrix
        normal[1:, 1:] += np.eye(matrix.shape[1] - 1)
        return np.linalg.solve(normal, matrix.T @ targets)

    @staticmethod
    def evaluate(beta: np.ndarray, z: np.ndarray, excess: np.ndarray, mode: str) -> float:
        x, y = z
        if mode == BASELINE:
            gx = beta[1] + 2*beta[3]*x + beta[5]*y
            gy = beta[2] + 2*beta[4]*y + beta[5]*x
        else:
            gx = 0.0
            gy = beta[1] + 2*beta[2]*y
        return float(design(z, mode) @ beta + gx*excess[0] + gy*excess[1])

    def map(self, features: np.ndarray) -> np.ndarray:
        raw = (np.asarray(features, dtype=np.float32).astype(float) - self.mean) / self.std
        z = np.clip(raw, self.lo, self.hi)
        excess = 2.5 * np.tanh((raw - z) / 2.5)
        return np.array([
            self.evaluate(self.beta_x, z, excess, BASELINE),
            self.evaluate(self.beta_y, z, excess, self.mode),
        ], dtype=np.float32)


def predict(features: np.ndarray, targets: np.ndarray, held: np.ndarray, mode: str) -> tuple[np.ndarray, np.ndarray]:
    """Each LOO fold refits normalization and coefficients on only 15 points."""
    loo = []
    for index, feature in enumerate(features):
        keep = np.arange(len(features)) != index
        loo.append(Mapper(features[keep], targets[keep], mode).map(feature))
    fitted = Mapper(features, targets, mode)
    return np.array(loo), np.array([fitted.map(row) for row in held])


def point_arrays(points: list[dict]) -> tuple[np.ndarray, np.ndarray]:
    features = np.array([p["aggregated_feature"] for p in points], dtype=np.float32)
    targets = np.array([[p["screen_x"], p["screen_y"]] for p in points], dtype=np.float32)
    if features.shape != (len(points), 2) or not np.isfinite(features).all() or not np.isfinite(targets).all():
        raise ValueError("Missing/malformed finite point aggregates")
    return features, targets


def extract(payload: dict) -> tuple[list[dict], list[dict]]:
    if payload.get("raw_feature_mode") != FEATURE_MODE:
        raise ValueError("Not the retained eye-local feature mode")
    if payload.get("outcome") != "accepted":
        raise ValueError("Not an accepted calibration")
    if payload.get("fit", {}).get("active_mapping_mode") != "quadratic_average":
        raise ValueError("Logged baseline is not quadratic_average")
    latest = {}
    for point in payload.get("points", []):
        if point.get("kind") == "FIT":
            latest[point["point_id"]] = point
    if set(latest) != set(range(16)) or payload["fit"].get("points_used") != 16:
        raise ValueError("Need complete 16-point fit")
    fit = [latest[i] for i in range(16)]
    if any(p.get("status") != "ACCEPTED" for p in fit):
        raise ValueError("Latest fit attempt is not accepted")
    # The root validation array is reset on refit. Recover the final complete
    # validation pass; parity below fails closed if its fit/round is ambiguous.
    held = [p for p in payload["points"] if p.get("kind") == "VALIDATION"][-5:]
    if len(held) != 5 or len(payload.get("validation", [])) != 5 or any(p.get("status") != "ACCEPTED" for p in held):
        raise ValueError("Need five accepted final validation points")
    if len({(p["screen_x"], p["screen_y"]) for p in held}) != 5:
        raise ValueError("Duplicate validation targets")
    if {(p["screen_x"], p["screen_y"]) for p in fit} & {(p["screen_x"], p["screen_y"]) for p in held}:
        raise ValueError("Validation targets overlap fit grid")
    if len({(p["screen_x"], p["screen_y"]) for p in fit}) != 16:
        raise ValueError("Duplicate fit targets")
    return fit, held


def verify_baseline(payload: dict, fit_targets: np.ndarray, held_targets: np.ndarray,
                    loo: np.ndarray, held: np.ndarray) -> float:
    logged = payload["fit"].get("leave_one_out_points", [])
    if len(logged) != 16 or {p["point_id"] for p in logged} != set(range(16)):
        raise ValueError("Incomplete logged LOO evidence")
    logged = sorted(logged, key=lambda p: p["point_id"])
    residual = loo - fit_targets  # Android Float subtraction
    expected = np.array([[p["dx_px"], p["dy_px"]] for p in logged])
    validation = payload["validation"]
    if not np.array_equal(held_targets, np.array([[p["screen_x"], p["screen_y"]] for p in validation], dtype=np.float32)):
        raise ValueError("Validation target/order mismatch")
    mapped = np.array([[p["mapped_x"], p["mapped_y"]] for p in validation])
    if not np.isfinite(expected).all() or not np.isfinite(mapped).all():
        raise ValueError("Non-finite logged baseline evidence")
    delta = max(float(np.max(np.abs(residual - expected))), float(np.max(np.abs(held - mapped))))
    if delta > PARITY_TOLERANCE_PX:
        raise ValueError(f"Baseline does not reproduce logged predictions: {delta:.6f} px")
    return delta


def summarize(residual: np.ndarray) -> dict:
    absolute = np.sort(np.abs(np.asarray(residual, dtype=float)))
    return {
        "n": len(absolute), "median_px": float(np.median(absolute)),
        "p95_px": float(absolute[math.ceil(.95*len(absolute)) - 1]),
        "max_px": float(absolute[-1]), "mean_signed_px": float(np.mean(residual)),
    }


def analyze_payload(payload: dict) -> dict:
    fit, held = extract(payload)
    features, targets = point_arrays(fit)
    held_features, held_targets = point_arrays(held)
    baseline = predict(features, targets, held_features, BASELINE)
    parity = verify_baseline(payload, targets, held_targets, *baseline)
    candidate = predict(features, targets, held_features, CANDIDATE)
    if any(not np.array_equal(a[:, 0], b[:, 0]) for a, b in zip(baseline, candidate)):
        raise ValueError("Candidate unexpectedly changes x")
    arms = {}
    for mode, predictions in ((BASELINE, baseline), (CANDIDATE, candidate)):
        arms[mode] = {}
        for stage, predicted, truth in zip(("loo", "held_out"), predictions, (targets, held_targets)):
            arms[mode][stage] = summarize((predicted - truth)[:, 1])
    points = {}
    for stage, a, b, truth, observations in zip(("loo", "held_out"), baseline, candidate, (targets, held_targets), (fit, held)):
        rows = []
        for index in range(len(truth)):
            old, new = float((a[index] - truth[index])[1]), float((b[index] - truth[index])[1])
            rows.append({
                "point_id": observations[index]["point_id"] if stage == "loo" else index,
                "target_x_px": float(truth[index, 0]), "target_y_px": float(truth[index, 1]),
                "baseline_dy_px": old, "candidate_dy_px": new,
                "absolute_error_change_px": abs(new) - abs(old),
            })
        points[stage] = rows
    return {
        "label": payload.get("run_label"), "timestamp": payload.get("timestamp_start"),
        "feature_mode": payload["raw_feature_mode"], "telemetry_mode": payload.get("telemetry_mode"),
        "baseline_max_parity_delta_px": parity, "verified_coordinate_pairs": 21,
        "candidate_x_max_delta_px": 0.0, "variants": arms, "points": points,
    }


def aggregate(reports: list[dict]) -> dict:
    if not reports:
        raise ValueError("No sessions")
    result = {}
    for stage in ("loo", "held_out"):
        result[stage] = {}
        for metric in ("median_px", "p95_px", "max_px"):
            a = [r["variants"][BASELINE][stage][metric] for r in reports]
            b = [r["variants"][CANDIDATE][stage][metric] for r in reports]
            result[stage][metric] = {
                "baseline_session_median": float(np.median(a)),
                "candidate_session_median": float(np.median(b)),
                "baseline_session_mean": float(np.mean(a)),
                "candidate_session_mean": float(np.mean(b)),
                "improved_sessions": sum(y < x for x, y in zip(a, b)),
                "worsened_sessions": sum(y > x for x, y in zip(a, b)),
                "tied_sessions": sum(y == x for x, y in zip(a, b)),
            }
    result["both_tail_improved_sessions"] = sum(
        all(r["variants"][CANDIDATE][s]["max_px"] < r["variants"][BASELINE][s]["max_px"] for s in ("loo", "held_out"))
        for r in reports
    )
    return result


def read_source(source: str, adb: str | None) -> bytes:
    path = Path(source)
    if path.is_file():
        return path.read_bytes()
    if adb and re.fullmatch(r"calibration_session_\d{8}_\d{6}\.json", source):
        return subprocess.check_output([adb, "exec-out", "run-as", "com.newsmead", "cat", "files/" + source], timeout=30)
    raise ValueError(f"No local source or allowed explicit device filename: {source}")


def build_report(sources: list[str], adb: str | None = None) -> dict:
    reports, hashes = [], set()
    for source in sources:
        raw = read_source(source, adb)
        fingerprint = hashlib.sha256(raw).hexdigest()
        if fingerprint in hashes:
            raise ValueError("Duplicate source payload")
        hashes.add(fingerprint)
        report = analyze_payload(json.loads(raw))
        report.update(source=Path(source).name, bytes=len(raw), sha256=fingerprint)
        reports.append(report)
    return {
        "method": "fixed_vertical_only_quadratic_ridge_1_vs_deployed_quadratic",
        "retrospective_only": True, "session_count": len(reports),
        "limitations": [
            "One participant/device; repeated sessions are not independent participants.",
            "Calibration aggregates, not replayed live filters or reading samples.",
            "Five held-out points omit outer corners; LOO covers grid edges separately.",
            "P95 nearest-rank equals maximum for 16 LOO and five held-out targets.",
            "Errors in pixels, not exact-line accuracy or reading-layout line counts.",
            "One fixed candidate; no ridge search, target-specific corrections, or tuned gate.",
        ],
        "aggregate": aggregate(reports), "sessions": reports,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", nargs="+")
    parser.add_argument("--adb", help="Absolute adb path; raw device payloads stay in memory")
    args = parser.parse_args()
    print(json.dumps(build_report(args.sources, args.adb), indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
