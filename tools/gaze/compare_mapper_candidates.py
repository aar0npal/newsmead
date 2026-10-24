"""Compare small gaze mappings against detailed calibration-session JSON.

The input JSON remains local. This tool prints aggregate errors only; it does
not copy images or participant artifacts. New telemetry-OFF logs are compatible
because they retain only the per-point per-eye aggregates, not per-frame data.

Candidates deliberately stay small for a 16-point calibration:

* affine_avg: intercept + averaged horizontal/vertical iris features
* quadratic_avg: the current six-term mapper design
* quadratic_plus_eye_y_delta: current design + one per-eye vertical difference
* quadratic_plus_eye_deltas: current design + horizontal and vertical differences
* per_eye_linear: intercept + four separate per-eye features

Each primitive input is standardized within the training fold. Ridge never
penalizes the intercept. Selection evidence is leave-one-fit-point-out plus the
five held-out validation points, with vertical tails reported explicitly.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable

import numpy as np


LEAD_IN_MS = 120.0
MAD_K = 2.5
MAD_EPSILON = 1e-6
MIN_RETAINED = 10
RIDGE_VALUES = (0.01, 0.1, 1.0, 10.0, 100.0)


@dataclass(frozen=True)
class Observation:
    screen: np.ndarray
    primitive: np.ndarray  # avg_x, avg_y, eye1_x-eye2_x, eye1_y-eye2_y, e1x, e1y, e2x, e2y


@dataclass(frozen=True)
class Session:
    path: Path
    label: str
    fit: tuple[Observation, ...]
    validation: tuple[Observation, ...]
    aggregate_replay_max_delta: float


@dataclass(frozen=True)
class Candidate:
    name: str
    primitive_indices: tuple[int, ...]
    design: Callable[[np.ndarray], np.ndarray]
    soft_bound_average: bool = False


def affine(z: np.ndarray) -> np.ndarray:
    return np.concatenate(([1.0], z))


def quadratic(z: np.ndarray) -> np.ndarray:
    x, y = z[:2]
    return np.concatenate(([1.0, x, y, x * x, y * y, x * y], z[2:]))


CANDIDATES = (
    Candidate("affine_avg", (0, 1), affine),
    Candidate("quadratic_avg", (0, 1), quadratic, True),
    Candidate("quadratic_plus_eye_y_delta", (0, 1, 3), quadratic, True),
    Candidate("quadratic_plus_eye_deltas", (0, 1, 2, 3), quadratic, True),
    Candidate("per_eye_linear", (4, 5, 6, 7), affine),
)


def finite(value: object) -> bool:
    try:
        return math.isfinite(float(value))
    except (TypeError, ValueError):
        return False


def median(values: np.ndarray) -> np.ndarray:
    return np.median(values, axis=0)


def retained_events(point: dict) -> list[dict]:
    events = [
        event
        for event in point.get("source_events", [])
        if event.get("outcome") == "EMITTED"
        and all(
            finite(event.get(key))
            for key in ("gaze_x", "gaze_y", "eye_1_x", "eye_1_y", "eye_2_x", "eye_2_y")
        )
    ]
    if not events:
        return []
    events.sort(key=lambda event: int(event["result_elapsed_ns"]))
    start_ns = int(events[0]["result_elapsed_ns"])
    trimmed = [
        event
        for event in events
        if (int(event["result_elapsed_ns"]) - start_ns) / 1_000_000.0 >= LEAD_IN_MS
    ]
    if len(trimmed) < MIN_RETAINED:
        return []

    gaze = np.asarray([[event["gaze_x"], event["gaze_y"]] for event in trimmed], dtype=float)
    center = median(gaze)
    mad = median(np.abs(gaze - center))
    keep = np.ones(len(trimmed), dtype=bool)
    for axis in range(2):
        if mad[axis] >= MAD_EPSILON:
            keep &= np.abs(gaze[:, axis] - center[axis]) <= MAD_K * mad[axis]
    return [event for event, retained in zip(trimmed, keep) if retained]


def observation(point: dict) -> tuple[Observation, float] | None:
    events = retained_events(point)
    if len(events) >= MIN_RETAINED:
        values = np.asarray(
            [
                [
                    event["gaze_x"],
                    event["gaze_y"],
                    event["eye_1_x"],
                    event["eye_1_y"],
                    event["eye_2_x"],
                    event["eye_2_y"],
                ]
                for event in events
            ],
            dtype=float,
        )
        aggregate = median(values)
        stored = np.asarray(point.get("aggregated_feature", [math.nan, math.nan]), dtype=float)
        replay_delta = float(np.max(np.abs(aggregate[:2] - stored)))
    else:
        average = point.get("aggregated_feature", [])
        per_eye = point.get("aggregated_per_eye_feature", [])
        if len(average) != 2 or len(per_eye) != 4 or not all(finite(value) for value in average + per_eye):
            return None
        aggregate = np.asarray(average + per_eye, dtype=float)
        replay_delta = 0.0
    avg_x, avg_y, eye1_x, eye1_y, eye2_x, eye2_y = aggregate
    primitive = np.asarray(
        [
            avg_x,
            avg_y,
            eye1_x - eye2_x,
            eye1_y - eye2_y,
            eye1_x,
            eye1_y,
            eye2_x,
            eye2_y,
        ],
        dtype=float,
    )
    return (
        Observation(
            screen=np.asarray([point["screen_x"], point["screen_y"]], dtype=float),
            primitive=primitive,
        ),
        replay_delta,
    )


def session_from_payload(payload: dict, path: Path) -> Session | None:
    """Build a comparison session from either detailed or aggregate-only JSON."""
    latest_fit: dict[int, dict] = {}
    validation_points: list[dict] = []
    for point in payload.get("points", []):
        if point.get("status") != "ACCEPTED":
            continue
        if point.get("kind") == "FIT":
            latest_fit[int(point["point_id"])] = point
        elif point.get("kind") == "VALIDATION":
            validation_points.append(point)
    # A redo-all appends a second five-point validation pass to the same log.
    # The final five correspond to the final FIT values retained above.
    validation_points = validation_points[-5:]
    if len(latest_fit) < 16 or len(validation_points) < 3:
        return None

    fit: list[Observation] = []
    validation: list[Observation] = []
    deltas: list[float] = []
    for point in (latest_fit[index] for index in sorted(latest_fit)):
        result = observation(point)
        if result is None:
            return None
        item, delta = result
        fit.append(item)
        deltas.append(delta)
    for point in validation_points:
        result = observation(point)
        if result is None:
            continue
        item, delta = result
        validation.append(item)
        deltas.append(delta)
    if len(validation) < 3:
        return None
    return Session(
        path=path,
        label=str(payload.get("run_label") or payload.get("session_id") or path.stem),
        fit=tuple(fit),
        validation=tuple(validation),
        aggregate_replay_max_delta=max(deltas, default=math.nan),
    )


def load_session(path: Path) -> Session | None:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return session_from_payload(payload, path)


class RidgeMapper:
    def __init__(self, training: Iterable[Observation], candidate: Candidate, ridge: float):
        items = tuple(training)
        primitive = np.asarray(
            [[item.primitive[index] for index in candidate.primitive_indices] for item in items],
            dtype=float,
        )
        self.mean = primitive.mean(axis=0)
        self.std = primitive.std(axis=0)
        self.std[self.std < 1e-8] = 1.0
        self.candidate = candidate
        standardized = (primitive - self.mean) / self.std
        self.z_min = standardized.min(axis=0)
        self.z_max = standardized.max(axis=0)
        design = np.asarray([candidate.design(row) for row in standardized])
        target = np.asarray([item.screen for item in items], dtype=float)
        normal = design.T @ design
        normal[1:, 1:] += np.eye(design.shape[1] - 1) * ridge
        self.beta = np.linalg.solve(normal, design.T @ target)

    def map(self, item: Observation) -> np.ndarray:
        primitive = np.asarray(
            [item.primitive[index] for index in self.candidate.primitive_indices], dtype=float
        )
        z = (primitive - self.mean) / self.std
        if not self.candidate.soft_bound_average:
            return self.candidate.design(z) @ self.beta

        # Reproduce GazeMapper's deployed edge behavior for the averaged gaze
        # axes. Extra per-eye residual terms remain ordinary standardized linear
        # inputs; only the current quadratic x/y surface receives this extension.
        bounded = z.copy()
        bounded[:2] = np.clip(bounded[:2], self.z_min[:2], self.z_max[:2])
        output = self.candidate.design(bounded) @ self.beta
        excess = 2.5 * np.tanh((z[:2] - bounded[:2]) / 2.5)
        x, y = bounded[:2]
        gradient_x = self.beta[1] + 2.0 * x * self.beta[3] + y * self.beta[5]
        gradient_y = self.beta[2] + 2.0 * y * self.beta[4] + x * self.beta[5]
        return output + gradient_x * excess[0] + gradient_y * excess[1]


def vertical_errors(training: tuple[Observation, ...], evaluation: Iterable[Observation], candidate: Candidate, ridge: float) -> list[float]:
    mapper = RidgeMapper(training, candidate, ridge)
    return [abs(float(mapper.map(item)[1] - item.screen[1])) for item in evaluation]


def leave_one_out(items: tuple[Observation, ...], candidate: Candidate, ridge: float) -> list[float]:
    errors: list[float] = []
    for held_index, held in enumerate(items):
        training = tuple(item for index, item in enumerate(items) if index != held_index)
        mapper = RidgeMapper(training, candidate, ridge)
        errors.append(abs(float(mapper.map(held)[1] - held.screen[1])))
    return errors


def percentile95(values: list[float]) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(0.95 * len(ordered)) - 1)]


def summarize(values: list[float]) -> tuple[float, float, float]:
    return float(np.median(values)), percentile95(values), max(values)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "paths",
        nargs="*",
        help="Calibration JSON files/directories, or - for one JSON document on stdin",
    )
    args = parser.parse_args()
    raw_roots = args.paths or ["diagnostics-local"]
    stdin_requested = "-" in raw_roots
    if stdin_requested and len(raw_roots) != 1:
        parser.error("- must be the only input path")
    if stdin_requested:
        try:
            payload = json.load(sys.stdin)
        except json.JSONDecodeError as error:
            parser.error(f"invalid calibration JSON on stdin: {error}")
        session = session_from_payload(payload, Path("<stdin>"))
        sessions = [session] if session else []
    else:
        roots = [Path(value) for value in raw_roots]
        paths: list[Path] = []
        for root in roots:
            if root.is_dir():
                paths.extend(root.rglob("calibration_session_*.json"))
            elif root.is_file():
                paths.append(root)
        sessions = [session for path in sorted(set(paths)) if (session := load_session(path))]
    if not sessions:
        parser.error("no compatible calibration sessions found")

    print(f"compatible_sessions={len(sessions)}")
    for session in sessions:
        print(
            f"  {session.path}: {session.label!r}; validation={len(session.validation)}; "
            f"aggregate_replay_max_delta={session.aggregate_replay_max_delta:.8f}"
        )
    print()
    print("Vertical error in pixels; session-balanced summaries (median of session medians/P95/max).")
    print(
        f"{'candidate':34s} {'ridge':>7s}  "
        f"{'LOO med':>8s} {'LOO p95':>8s} {'LOO max':>8s}  "
        f"{'held med':>8s} {'held p95':>8s} {'held max':>8s}"
    )

    rows = []
    baseline_by_session: list[tuple[float, float]] | None = None
    for candidate in CANDIDATES:
        for ridge in RIDGE_VALUES:
            loo_session = [summarize(leave_one_out(session.fit, candidate, ridge)) for session in sessions]
            held_session = [
                summarize(vertical_errors(session.fit, session.validation, candidate, ridge))
                for session in sessions
            ]
            loo = tuple(float(np.median([row[index] for row in loo_session])) for index in range(3))
            held = tuple(float(np.median([row[index] for row in held_session])) for index in range(3))
            session_tails = [(loo_row[2], held_row[2]) for loo_row, held_row in zip(loo_session, held_session)]
            if candidate.name == "quadratic_avg" and ridge == 1.0:
                baseline_by_session = session_tails
            rows.append((held[2] + loo[2], candidate.name, ridge, loo, held, session_tails))
            print(
                f"{candidate.name:34s} {ridge:7.2f}  "
                f"{loo[0]:8.1f} {loo[1]:8.1f} {loo[2]:8.1f}  "
                f"{held[0]:8.1f} {held[1]:8.1f} {held[2]:8.1f}"
            )

    print()
    print("Lowest descriptive combined tail scores (LOO max + held-out max; not a deployment decision):")
    for score, name, ridge, loo, held, _ in sorted(rows)[:5]:
        print(
            f"  {name} ridge={ridge:g}: score={score:.1f}, "
            f"LOO max={loo[2]:.1f}, held max={held[2]:.1f}"
        )
    if baseline_by_session is not None:
        print()
        print("Top candidates versus deployed quadratic_avg ridge=1 (session win counts):")
        for score, name, ridge, loo, held, session_tails in sorted(rows)[:8]:
            loo_wins = sum(new[0] < old[0] for new, old in zip(session_tails, baseline_by_session))
            held_wins = sum(new[1] < old[1] for new, old in zip(session_tails, baseline_by_session))
            both_wins = sum(
                new[0] < old[0] and new[1] < old[1]
                for new, old in zip(session_tails, baseline_by_session)
            )
            print(
                f"  {name} ridge={ridge:g}: LOO {loo_wins}/{len(sessions)}, "
                f"held {held_wins}/{len(sessions)}, both {both_wins}/{len(sessions)}"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
