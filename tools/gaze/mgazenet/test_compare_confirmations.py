"""Tests for the frozen four-session direct-screen confirmation comparison."""
import copy
import unittest

from compare_confirmations import EXPECTED, ORDER, compare


def summary(label, screen_pass, downstream_pass):
    device, order = EXPECTED[label]
    return {
        "schema": "mgazenet_direct_validation_confirmation_summary_v1",
        "evidence_kind": "recorded",
        "input_sha256": "a"*64,
        "accuracy_gate_pass": None,
        "confirmation_accuracy_pass": None,
        "promotion_decision": "not_evaluated",
        "validation_order": order,
        "provenance": {
            "protocol_id": "mgazenet_direct_validation_confirmation_v1",
            "session_id": label+"_session",
            "device_id": device,
            "pipeline_id": "pipeline",
            "calibration_id": label+"_calibration",
        },
        "screen": {"sealed_result": {
            "mean_target_median_absolute_vertical_lines": .5,
        }},
        "confirmation": {
            "blocks": [{} for _ in range(20)],
            "location_spatial": [{} for _ in range(10)],
            "target_balanced": {
                "mean_location_median_absolute_vertical_lines": .6,
                "worst_location_median_absolute_vertical_lines": .8,
            },
        },
        "screen_prediction": {
            "screen_candidate_pass": screen_pass,
            "confirmation_line_level_pass": downstream_pass,
            "classification_agrees": screen_pass is downstream_pass,
            "false_accept": screen_pass and not downstream_pass,
            "false_reject": not screen_pass and downstream_pass,
        },
    }


def fixtures(states):
    return {label: summary(label,*state) for label,state in zip(ORDER,states)}


class CompareConfirmationsTests(unittest.TestCase):
    def test_true_accept_and_true_reject_without_false_accept_supports_next_proposal(self):
        result = compare(fixtures(((True,True),(False,False),(False,True),(True,True))))
        self.assertEqual("supports_next_proposal",result["screen_confirmation_status"])
        self.assertEqual(0,result["classification_counts"]["false_accept"])
        self.assertEqual(1,result["classification_counts"]["false_reject"])
        self.assertIsNone(result["accuracy_gate_pass"])
        self.assertEqual("not_evaluated",result["promotion_decision"])

    def test_any_false_accept_rejects_screen(self):
        result = compare(fixtures(((True,False),(False,False),(True,True),(False,True))))
        self.assertEqual("reject",result["screen_confirmation_status"])
        self.assertEqual(1,result["classification_counts"]["false_accept"])

    def test_no_true_reject_or_no_true_accept_is_inconclusive(self):
        for states in (
            ((True,True),(True,True),(False,True),(True,True)),
            ((False,False),(False,False),(False,False),(False,False)),
        ):
            self.assertEqual("inconclusive",compare(fixtures(states))["screen_confirmation_status"])

    def test_changed_order_device_identity_or_decision_is_rejected(self):
        mutations = (
            lambda data: data[ORDER[0]].update(validation_order="reverse_then_forward"),
            lambda data: data[ORDER[0]]["provenance"].update(device_id="samsung/SM-G991B"),
            lambda data: data[ORDER[0]]["provenance"].update(
                session_id=data[ORDER[1]]["provenance"]["session_id"]),
            lambda data: data[ORDER[0]].update(accuracy_gate_pass=True),
        )
        for mutate in mutations:
            data = copy.deepcopy(fixtures(((True,True),(False,False),(True,True),(False,False))))
            mutate(data)
            with self.assertRaises(ValueError):
                compare(data)

    def test_changed_derived_classification_is_rejected(self):
        data = fixtures(((True,True),(False,False),(True,True),(False,False)))
        data[ORDER[0]]["screen_prediction"]["false_accept"] = True
        with self.assertRaises(ValueError):
            compare(data)


if __name__ == "__main__":
    unittest.main()
