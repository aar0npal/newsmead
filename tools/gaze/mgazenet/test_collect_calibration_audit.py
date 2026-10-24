"""Contract tests for hash-verified calibration-audit collection."""
import copy
import hashlib
import json
import unittest

from collect_calibration_audit import validate


RUN_ID = "1788944307040_7525fa1f-cca6-4112-afcb-ebdae2788a34"


def payload(schema="mgazenet_calibration_audit_v3"):
    return {
        "schema": schema,
        "outcome": "complete" if schema.endswith("_v3") and "partial" not in schema else "stopped",
        "protocol_id": "mgazenet_calibration_observability_v3",
        "session_id": RUN_ID,
        "active_tracker_access": False,
        "calibration_store_access": False,
        "camera_frames_retained": False,
        "features_retained": False,
        "personal_model_retained": False,
        "model_correction_applied": False,
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
    }


def encoded(report):
    return json.dumps(report, separators=(",", ":")).encode()


class CollectCalibrationAuditTests(unittest.TestCase):
    def test_complete_and_partial_records_are_accepted_without_scoring(self):
        for schema in ("mgazenet_calibration_audit_v3", "mgazenet_calibration_audit_partial_v3"):
            raw = encoded(payload(schema))
            self.assertEqual(schema, validate(raw, hashlib.sha256(raw).hexdigest(), RUN_ID)["schema"])

    def test_hash_protocol_identity_privacy_correction_and_decision_changes_are_rejected(self):
        mutations = (
            lambda report: None,
            lambda report: report.update(protocol_id="other"),
            lambda report: report.update(session_id="1788944307040_00000000-0000-0000-0000-000000000000"),
            lambda report: report.update(camera_frames_retained=True),
            lambda report: report.update(features_retained=True),
            lambda report: report.update(model_correction_applied=True),
            lambda report: report.update(accuracy_gate_pass=False),
            lambda report: report.update(promotion_decision="promote"),
        )
        for index, mutate in enumerate(mutations):
            report = copy.deepcopy(payload())
            mutate(report)
            raw = encoded(report)
            expected = "0"*64 if index == 0 else hashlib.sha256(raw).hexdigest()
            with self.assertRaises(ValueError):
                validate(raw, expected, RUN_ID)

    def test_unknown_schema_and_missing_privacy_field_are_rejected(self):
        reports = (payload("mgazenet_accuracy_v2"), payload())
        reports[1].pop("active_tracker_access")
        for report in reports:
            raw = encoded(report)
            with self.assertRaises(ValueError):
                validate(raw, hashlib.sha256(raw).hexdigest(), RUN_ID)


if __name__ == "__main__":
    unittest.main()
