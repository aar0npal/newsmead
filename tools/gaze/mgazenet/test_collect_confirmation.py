"""Contract tests for hash-verified direct-screen confirmation collection."""
import copy
import hashlib
import json
import unittest

from collect_confirmation import validate


RUN_ID = "1789000000000_7525fa1f-cca6-4112-afcb-ebdae2788a34"


def payload(schema="mgazenet_direct_validation_confirmation_v1"):
    return {
        "schema": schema,
        "outcome": "complete" if "partial" not in schema else "stopped",
        "protocol_id": "mgazenet_direct_validation_confirmation_v1",
        "session_id": RUN_ID,
        "active_tracker_access": False,
        "calibration_store_access": False,
        "camera_frames_retained": False,
        "features_retained": False,
        "personal_model_retained": False,
        "model_correction_applied": False,
        "screen_result_hidden_until_terminal": True,
        "confirmation_accuracy_pass": None,
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
    }


def encoded(report):
    return json.dumps(report, separators=(",", ":")).encode()


class CollectConfirmationTests(unittest.TestCase):
    def test_complete_and_partial_records_are_accepted_without_scoring(self):
        for schema in ("mgazenet_direct_validation_confirmation_v1",
                       "mgazenet_direct_validation_confirmation_partial_v1"):
            raw = encoded(payload(schema))
            self.assertEqual(schema,validate(raw,hashlib.sha256(raw).hexdigest(),RUN_ID)["schema"])

    def test_hash_identity_privacy_visibility_correction_and_decisions_are_rejected(self):
        mutations = (
            lambda report: None,
            lambda report: report.update(protocol_id="other"),
            lambda report: report.update(session_id="1789000000000_00000000-0000-0000-0000-000000000000"),
            lambda report: report.update(camera_frames_retained=True),
            lambda report: report.update(features_retained=True),
            lambda report: report.update(model_correction_applied=True),
            lambda report: report.update(screen_result_hidden_until_terminal=False),
            lambda report: report.update(confirmation_accuracy_pass=False),
            lambda report: report.update(accuracy_gate_pass=False),
            lambda report: report.update(promotion_decision="promote"),
        )
        for index, mutate in enumerate(mutations):
            report = copy.deepcopy(payload())
            mutate(report)
            raw = encoded(report)
            expected = "0"*64 if index == 0 else hashlib.sha256(raw).hexdigest()
            with self.assertRaises(ValueError):
                validate(raw,expected,RUN_ID)

    def test_unknown_schema_and_missing_boundary_field_are_rejected(self):
        reports = (payload("mgazenet_accuracy_v2"),payload())
        reports[1].pop("active_tracker_access")
        for report in reports:
            raw = encoded(report)
            with self.assertRaises(ValueError):
                validate(raw,hashlib.sha256(raw).hexdigest(),RUN_ID)


if __name__ == "__main__":
    unittest.main()
