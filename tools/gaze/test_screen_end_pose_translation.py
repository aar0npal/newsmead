import copy
import unittest

import numpy as np

from compare_vertical_mapper import BASELINE, predict
from screen_end_pose_translation import analyze_payload, drift_vector


def fixture():
    fractions = (0.1, 0.3667, 0.6333, 0.9)
    features = np.array([(x, y) for y in fractions for x in fractions], dtype=np.float32)
    targets = np.array([[100 + 900*x, 150 + 1900*y] for x, y in features], dtype=np.float32)
    drift = np.array([0.02, -0.025], dtype=np.float32)
    held_base = np.array(((0.5, 0.5), (0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)), dtype=np.float32)
    held_features = held_base + drift
    held_targets = np.array([[100 + 900*x, 150 + 1900*y] for x, y in held_base], dtype=np.float32)
    baseline = predict(features, targets, held_features, BASELINE)
    drift_id = 5
    points = []
    for index, (feature, target) in enumerate(zip(features, targets)):
        points.append({
            "point_id": index, "kind": "FIT", "status": "ACCEPTED",
            "presentation_index": index + 1,
            "aggregated_feature": feature.tolist(),
            "screen_x": float(target[0]), "screen_y": float(target[1]),
        })
    points.append({
        "point_id": drift_id, "kind": "DRIFT_REPEAT", "status": "ACCEPTED",
        "presentation_index": 17,
        "aggregated_feature": (features[drift_id] + drift).tolist(),
        "screen_x": float(targets[drift_id, 0]), "screen_y": float(targets[drift_id, 1]),
    })
    for index, (feature, target) in enumerate(zip(held_features, held_targets)):
        points.append({
            "point_id": -1, "kind": "VALIDATION", "status": "ACCEPTED",
            "presentation_index": 18 + index,
            "aggregated_feature": feature.tolist(),
            "screen_x": float(target[0]), "screen_y": float(target[1]),
        })
    loo_residual = baseline[0] - targets
    validation = []
    for mapped, target in zip(baseline[1], held_targets):
        validation.append({
            "screen_x": float(target[0]), "screen_y": float(target[1]),
            "mapped_x": float(mapped[0]), "mapped_y": float(mapped[1]),
        })
    return {
        "raw_feature_mode": "eye_local_width_average_v1",
        "outcome": "accepted",
        "telemetry_mode": "detailed_off",
        "fit": {
            "active_mapping_mode": "quadratic_average", "points_used": 16,
            "leave_one_out_points": [
                {"point_id": index, "dx_px": float(row[0]), "dy_px": float(row[1])}
                for index, row in enumerate(loo_residual)
            ],
        },
        "drift_check": {
            "first_pass_feature": features[drift_id].tolist(),
            "second_pass_feature": (features[drift_id] + drift).tolist(),
            "flagged_high_drift": True,
        },
        "points": points,
        "validation": validation,
    }, drift


class EndPoseTranslationTest(unittest.TestCase):
    def test_recovers_known_global_feature_translation(self):
        payload, expected_drift = fixture()
        report = analyze_payload(payload)
        self.assertAlmostEqual(expected_drift[0], report["drift_feature_dx"], places=6)
        self.assertAlmostEqual(expected_drift[1], report["drift_feature_dy"], places=6)
        self.assertLess(
            report["arms"]["end_pose_translation"]["held_out"]["vertical"]["max_px"],
            report["arms"]["deployed"]["held_out"]["vertical"]["max_px"],
        )
        self.assertTrue(report["improves_all_held_metrics"])

    def test_rejects_drift_feature_that_does_not_match_logged_points(self):
        payload, _ = fixture()
        payload["drift_check"]["first_pass_feature"][0] += 0.01
        with self.assertRaisesRegex(ValueError, "first-pass"):
            analyze_payload(payload)

    def test_analysis_does_not_mutate_payload(self):
        payload, _ = fixture()
        original = copy.deepcopy(payload)
        analyze_payload(payload)
        self.assertEqual(original, payload)

    def test_missing_repeat_is_rejected(self):
        payload, _ = fixture()
        fit = [point for point in payload["points"] if point["kind"] == "FIT"]
        payload["points"] = fit
        with self.assertRaisesRegex(ValueError, "repeated drift"):
            drift_vector(payload, fit)


if __name__ == "__main__":
    unittest.main()
