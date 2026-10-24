"""Tests for the frozen four-session calibration-audit comparison."""
import copy
import unittest

from compare_calibration_audits import ORDER, compare


DEVICES = {
    "g991b_v3_r1": "samsung/SM-G991B",
    "g991b_v3_r2": "samsung/SM-G991B",
    "a56_v3_r1": "samsung/SM-A566B",
    "a56_v3_r2": "samsung/SM-A566B",
}


def balanced(median):
    return {
        "contributing_target_count": 16,
        "mean_target_median": {"absolute_vertical_lines": median, "euclidean_px": median*60},
        "mean_target_p95": {"absolute_vertical_lines": median+1},
        "mean_target_max": {"absolute_vertical_lines": median+2},
        "mean_target_signed_bias_px": {"x": 0, "y": median*10},
    }


def summary(label, loo, held_out):
    held = balanced(held_out)
    held["contributing_target_count"] = 5
    return {
        "schema": "mgazenet_calibration_audit_summary_v3",
        "evidence_kind": "recorded",
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
        "input_sha256": "a"*64,
        "provenance": {
            "protocol_id": "mgazenet_calibration_observability_v3",
            "session_id": label+"_session",
            "device_id": DEVICES[label],
            "pipeline_id": "pipeline",
            "calibration_id": label+"_calibration",
        },
        "loo": {"target_balanced": balanced(loo)},
        "verification": {
            "blocks_without_coordinates": 0,
            "held_out_validation_target_balanced": held,
        },
    }


def fixtures():
    return {
        "g991b_v3_r1": summary("g991b_v3_r1", 1, 1),
        "a56_v3_r1": summary("a56_v3_r1", 2, 2),
        "a56_v3_r2": summary("a56_v3_r2", 3, 3),
        "g991b_v3_r2": summary("g991b_v3_r2", 4, 4),
    }


class CompareCalibrationAuditsTests(unittest.TestCase):
    def test_frozen_primary_deltas_and_rank_association(self):
        result = compare(fixtures())
        self.assertEqual(list(ORDER), result["session_order"])
        self.assertEqual(1, result["four_session_spearman_primary"])
        self.assertTrue(result["within_phone"]["SM-G991B"]["directional_agreement"])
        self.assertTrue(result["within_phone"]["SM-A566B"]["directional_agreement"])
        self.assertIsNone(result["accuracy_gate_pass"])
        self.assertEqual("not_evaluated", result["promotion_decision"])

    def test_opposite_within_phone_change_is_retained(self):
        data = fixtures()
        data["g991b_v3_r2"] = summary("g991b_v3_r2", 4, .5)
        result = compare(data)
        self.assertFalse(result["within_phone"]["SM-G991B"]["directional_agreement"])

    def test_missing_heldout_target_makes_primary_comparison_unavailable(self):
        data = fixtures()
        data["a56_v3_r2"]["verification"]["held_out_validation_target_balanced"][
            "contributing_target_count"] = 4
        result = compare(data)
        self.assertFalse(result["all_sessions_have_five_held_out_targets"])
        self.assertIsNone(result["four_session_spearman_primary"])
        self.assertIsNone(result["within_phone"]["SM-A566B"]["directional_agreement"])

    def test_changed_schema_device_decision_or_session_identity_is_rejected(self):
        mutations = (
            lambda data: data["g991b_v3_r1"].update(schema="other"),
            lambda data: data["g991b_v3_r1"]["provenance"].update(device_id="samsung/SM-A566B"),
            lambda data: data["g991b_v3_r1"].update(accuracy_gate_pass=True),
            lambda data: data["g991b_v3_r1"]["provenance"].update(
                session_id=data["g991b_v3_r2"]["provenance"]["session_id"]),
        )
        for mutate in mutations:
            data = copy.deepcopy(fixtures())
            mutate(data)
            with self.assertRaises(ValueError):
                compare(data)


if __name__ == "__main__":
    unittest.main()
