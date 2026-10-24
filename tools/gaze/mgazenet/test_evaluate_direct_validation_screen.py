"""Tests for the frozen development-only direct validation screen."""
import copy
import unittest

from evaluate_direct_validation_screen import evaluate


def block(index, median=0.8, p95=1.0, maximum=1.1, coordinates=16):
    return {
        "id": f"validation_{index}",
        "role": "held_out_validation",
        "received_samples": coordinates,
        "coordinate_samples": coordinates,
        "null_samples": 0,
        "absolute_vertical_lines": {
            "median": median,
            "p95": p95,
            "max": maximum,
        },
    }


def summary(blocks=None):
    blocks = blocks or [block(index) for index in range(1, 6)]
    mean_median = sum(item["absolute_vertical_lines"]["median"] for item in blocks) / 5
    mean_p95 = sum(item["absolute_vertical_lines"]["p95"] for item in blocks) / 5
    return {
        "schema": "mgazenet_calibration_audit_summary_v3",
        "evidence_kind": "recorded",
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
        "input_sha256": "a" * 64,
        "provenance": {
            "protocol_id": "mgazenet_calibration_observability_v3",
            "session_id": "session",
            "device_id": "samsung/SM-G991B",
            "pipeline_id": "pipeline",
            "calibration_id": "calibration",
        },
        "verification": {
            "blocks": blocks,
            "blocks_without_coordinates": 0,
            "held_out_validation_target_balanced": {
                "contributing_target_count": 5,
                "mean_target_median": {"absolute_vertical_lines": mean_median},
                "mean_target_p95": {"absolute_vertical_lines": mean_p95},
            },
        },
    }


class DirectValidationScreenTests(unittest.TestCase):
    def test_inclusive_frozen_boundaries_pass_without_promotion(self):
        blocks = [block(index, median=1.0, p95=1.3, maximum=1.4)
                  for index in range(1, 5)]
        blocks.append(block(5, median=1.0, p95=3.0, maximum=3.1))
        result = evaluate(summary(blocks), "b" * 64)
        self.assertTrue(result["screen_candidate_pass"])
        self.assertTrue(result["frozen_screen"]["p95_is_reported_but_not_thresholded"])
        self.assertIsNone(result["accuracy_gate_pass"])
        self.assertEqual("not_evaluated", result["promotion_decision"])

    def test_mean_regional_and_sample_checks_fail_independently(self):
        cases = []
        cases.append([block(index, median=1.01, p95=1.1, maximum=1.2)
                      for index in range(1, 6)])

        regional = [block(index, median=0.8, p95=1.0, maximum=1.1)
                    for index in range(1, 6)]
        regional[0] = block(1, median=1.21, p95=1.21, maximum=1.3)
        cases.append(regional)

        sparse = [block(index) for index in range(1, 6)]
        sparse[2] = block(3, coordinates=9)
        cases.append(sparse)

        failed_keys = (
            "mean_target_median_vertical_at_most_one_line",
            "every_target_median_vertical_at_most_1_2_lines",
            "at_least_ten_coordinates_per_target",
        )
        for blocks, failed_key in zip(cases, failed_keys):
            result = evaluate(summary(blocks))
            self.assertFalse(result["screen_candidate_pass"])
            self.assertFalse(result["checks"][failed_key])

    def test_changed_identity_counts_aggregate_or_decision_is_rejected(self):
        mutations = (
            lambda data: data["verification"]["blocks"][0].update(id="validation_2"),
            lambda data: data["verification"]["blocks"][0].update(received_samples=17),
            lambda data: data["verification"]["held_out_validation_target_balanced"]
                ["mean_target_median"].update(absolute_vertical_lines=0.7),
            lambda data: data.update(accuracy_gate_pass=True),
        )
        for mutate in mutations:
            data = copy.deepcopy(summary())
            mutate(data)
            with self.assertRaises(ValueError):
                evaluate(data)

    def test_invalid_vertical_order_is_rejected(self):
        data = summary()
        data["verification"]["blocks"][0]["absolute_vertical_lines"]["p95"] = 0.7
        with self.assertRaises(ValueError):
            evaluate(data)


if __name__ == "__main__":
    unittest.main()
