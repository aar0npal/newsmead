import hashlib
import json
import unittest

from collect_input_check import validate


class InputCheckCollectionTests(unittest.TestCase):
    RUN = "1788821883292_b3157a0d-343e-4ce2-9be9-4119fcbc0495"

    def report(self):
        return {
            "schema": "mgazenet_input_check_partial_v1",
            "protocol_id": "mgazenet_input_check_v1",
            "session_id": self.RUN,
            "camera_frames_retained": False,
            "crop_images_retained": False,
            "landmarks_retained": False,
            "features_retained": False,
            "personal_model_retained": False,
            "svr_fit": False,
            "calibration_grid_presented": False,
        }

    def encoded(self, report):
        raw = json.dumps(report, separators=(",", ":")).encode()
        return raw, hashlib.sha256(raw).hexdigest()

    def test_complete_or_partial_numeric_record_is_accepted(self):
        report = self.report()
        raw, digest = self.encoded(report)
        self.assertEqual(self.RUN, validate(raw, digest, self.RUN)["session_id"])
        report["schema"] = "mgazenet_input_check_v1"
        raw, digest = self.encoded(report)
        self.assertEqual("mgazenet_input_check_v1", validate(raw, digest, self.RUN)["schema"])

    def test_hash_identity_retention_and_calibration_changes_are_rejected(self):
        report = self.report()
        raw, digest = self.encoded(report)
        with self.assertRaises(ValueError):
            validate(raw, "0" * 64, self.RUN)
        with self.assertRaises(ValueError):
            validate(raw, digest, "1_other")
        for field in ("camera_frames_retained", "crop_images_retained", "landmarks_retained",
                      "features_retained", "personal_model_retained", "svr_fit",
                      "calibration_grid_presented"):
            changed = self.report()
            changed[field] = True
            changed_raw, changed_digest = self.encoded(changed)
            with self.assertRaises(ValueError, msg=field):
                validate(changed_raw, changed_digest, self.RUN)


if __name__ == "__main__":
    unittest.main()
