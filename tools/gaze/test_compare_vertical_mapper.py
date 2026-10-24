"""Synthetic tests; no participant files or connected phone required."""

import copy
from pathlib import Path
import unittest
from unittest.mock import patch

import numpy as np

from compare_mapper_candidates import CANDIDATES, Observation, RidgeMapper
from compare_vertical_mapper import (
    BASELINE, CANDIDATE, FEATURE_MODE, Mapper, aggregate, analyze_payload,
    build_report, extract, predict, read_source, summarize,
)


def fixture():
    features = np.array([[x, y] for y in (.1, .35, .65, .9) for x in (.1, .35, .65, .9)], dtype=np.float32)
    targets = np.array([[100 + 800*x, 150 + 1800*y + 70*x*y] for x, y in features], dtype=np.float32)
    held_features = np.array([[.5, .5], [.25, .25], [.75, .25], [.25, .75], [.75, .75]], dtype=np.float32)
    held_targets = np.array([[100 + 800*x, 150 + 1800*y + 70*x*y] for x, y in held_features], dtype=np.float32)
    loo, held = predict(features, targets, held_features, BASELINE)
    points = []
    for kind, fs, ts in (("FIT", features, targets), ("VALIDATION", held_features, held_targets)):
        for i, (f, t) in enumerate(zip(fs, ts)):
            points.append({"kind": kind, "point_id": i if kind == "FIT" else -1,
                           "status": "ACCEPTED", "aggregated_feature": f.tolist(),
                           "screen_x": float(t[0]), "screen_y": float(t[1])})
    return {
        "raw_feature_mode": FEATURE_MODE, "outcome": "accepted", "points": points,
        "fit": {"active_mapping_mode": "quadratic_average", "points_used": 16,
                "leave_one_out_points": [{"point_id": i, "dx_px": float(p[0]), "dy_px": float(p[1])}
                                         for i, p in enumerate(loo - targets)]},
        "validation": [{"screen_x": float(t[0]), "screen_y": float(t[1]),
                        "mapped_x": float(p[0]), "mapped_y": float(p[1])} for p, t in zip(held, held_targets)],
    }, features, targets, held_features


class VerticalMapperTest(unittest.TestCase):
    def test_baseline_matches_existing_independent_mapper(self):
        _, features, targets, held = fixture()
        obs = tuple(Observation(t.astype(float), np.array([f[0], f[1], 0, 0, 0, 0, 0, 0], dtype=float))
                    for f, t in zip(features, targets))
        previous = RidgeMapper(obs, next(c for c in CANDIDATES if c.name == "quadratic_avg"), 1.0)
        current = Mapper(features, targets, BASELINE)
        for f in np.concatenate((features, held, [[-3, 4], [7, -2]])):
            f = np.asarray(f, dtype=np.float32)
            item = Observation(np.zeros(2), np.array([*f, 0, 0, 0, 0, 0, 0]))
            np.testing.assert_allclose(current.map(f), previous.map(item), atol=.001, rtol=0)

    def test_candidate_y_ignores_horizontal_input_x_map_unchanged(self):
        _, features, targets, _ = fixture()
        original = Mapper(features, targets, BASELINE)
        candidate = Mapper(features, targets, CANDIDATE)
        ys = []
        for x in (-200, -.1, .5, 1, 300):
            f = [x, .64]
            self.assertEqual(original.map(f)[0], candidate.map(f)[0])
            ys.append(candidate.map(f)[1])
        self.assertEqual(len(set(ys)), 1)
        self.assertEqual(len(candidate.beta_y), 3)
        self.assertEqual(len(original.beta_y), 6)

    def test_candidate_ridge_excludes_intercept(self):
        _, features, targets, _ = fixture()
        targets[:, 1] = 777
        mapper = Mapper(features, targets, CANDIDATE)
        self.assertAlmostEqual(mapper.beta_y[0], 777)
        np.testing.assert_allclose(mapper.beta_y[1:], 0, atol=1e-10)
        self.assertEqual(mapper.map([.5, .5])[1], 777)

    def test_soft_extension_uses_boundary_gradient(self):
        _, features, targets, _ = fixture()
        mapper = Mapper(features, targets, CANDIDATE)
        mapper.beta_y = np.array([100., 50., 10.])
        for raw_y in (-5., 8.):
            f = np.array([.5, raw_y], dtype=np.float32)
            raw = (f.astype(float) - mapper.mean) / mapper.std
            zy = np.clip(raw[1], mapper.lo[1], mapper.hi[1])
            excess = 2.5*np.tanh((raw[1]-zy)/2.5)
            expected = np.float32(100 + 50*zy + 10*zy*zy + (50+20*zy)*excess)
            self.assertEqual(mapper.map(f)[1], expected)

    def test_std_floor_keeps_degenerate_axis_finite(self):
        _, features, targets, _ = fixture()
        features[:, 1] = .5
        for mode in (BASELINE, CANDIDATE):
            mapper = Mapper(features, targets, mode)
            self.assertEqual(mapper.std[1], 1e-6)
            self.assertTrue(np.isfinite(mapper.map([.5, .9])).all())

    def test_loo_refits_without_held_feature_or_answer(self):
        _, features, targets, held = fixture()
        features[0] = [-2, -4]
        for mode in (BASELINE, CANDIDATE):
            loo, _ = predict(features, targets, held, mode)
            expected = Mapper(features[1:], targets[1:], mode).map(features[0])
            np.testing.assert_array_equal(loo[0], expected)
            changed = targets.copy()
            changed[0] += 5000
            new_loo, _ = predict(features, changed, held, mode)
            np.testing.assert_array_equal(new_loo[0], loo[0])

    def test_valid_payload_nonmutation_and_parity(self):
        payload, *_ = fixture()
        saved = copy.deepcopy(payload)
        report = analyze_payload(payload)
        self.assertEqual(payload, saved)
        self.assertEqual(report["baseline_max_parity_delta_px"], 0)
        self.assertEqual(report["verified_coordinate_pairs"], 21)
        self.assertEqual(report["candidate_x_max_delta_px"], 0)

    def test_mismatched_saved_baseline_fails(self):
        for field in ("loo", "held"):
            payload, *_ = fixture()
            if field == "loo":
                payload["fit"]["leave_one_out_points"][0]["dy_px"] += 10
            else:
                payload["validation"][0]["mapped_y"] += 10
            with self.assertRaisesRegex(ValueError, "does not reproduce"):
                analyze_payload(payload)

    def test_malformed_or_ineligible_sessions_fail(self):
        edits = [
            lambda p: p.update(raw_feature_mode="eyelid_fraction_average"),
            lambda p: p.update(outcome="aborted"),
            lambda p: p["fit"].update(points_used=15),
            lambda p: p["points"][0].update(status="EXCLUDED"),
            lambda p: p["points"][0].update(aggregated_feature=[float("nan"), .5]),
            lambda p: p["validation"].pop(),
            lambda p: p["validation"].reverse(),
            lambda p: p["fit"]["leave_one_out_points"].pop(),
        ]
        for edit in edits:
            with self.subTest(edit=edit):
                payload, *_ = fixture()
                edit(payload)
                with self.assertRaises(ValueError):
                    analyze_payload(payload)

    def test_final_fit_attempt_and_validation_pass(self):
        payload, *_ = fixture()
        obsolete = copy.deepcopy(payload["points"])
        obsolete[0]["aggregated_feature"] = [99, 99]
        payload["points"] = obsolete + payload["points"]
        self.assertEqual(analyze_payload(payload)["baseline_max_parity_delta_px"], 0)
        payload["points"].append({**payload["points"][0], "status": "EXCLUDED"})
        with self.assertRaises(ValueError):
            extract(payload)

    def test_nearest_rank_p95_and_equal_session_weight(self):
        self.assertEqual(summarize(np.arange(16))["p95_px"], 15)
        self.assertEqual(summarize(np.arange(5))["p95_px"], 4)
        payload, *_ = fixture()
        report = analyze_payload(payload)
        both = aggregate([report, report])
        self.assertEqual(both["loo"]["median_px"]["baseline_session_median"], report["variants"][BASELINE]["loo"]["median_px"])

    def test_device_filename_guard_and_duplicate_sources(self):
        with patch.object(Path, "is_file", return_value=False), patch("compare_vertical_mapper.subprocess.check_output") as call:
            for source in ("../calibration.json", "calibration_session_20260903_011422.json;echo nope", "files/calibration_session_20260903_011422.json"):
                with self.assertRaises(ValueError):
                    read_source(source, "adb")
            call.assert_not_called()
        payload, *_ = fixture()
        import json
        with patch("compare_vertical_mapper.read_source", return_value=json.dumps(payload).encode()):
            with self.assertRaisesRegex(ValueError, "Duplicate source"):
                build_report(["first", "second"])


if __name__ == "__main__":
    unittest.main()
