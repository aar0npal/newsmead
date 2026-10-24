"""Host-only tests of the evidence gate. No model/runtime or device required."""
import argparse
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import reference as gate


class EvidenceGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.a = {"mode":"synthetic", "outcome":"completed_pending_external_parity",
                  "model_sha256":gate.MODEL_SHA, "opencv":"4.11.0", "mnn_version":"3.6.1",
                  "mnn_backend":"CPU", "mnn_precision":"normal_default", "mnn_threads":4,
                  "fixtures":[], "synthetic_svr_predictions":[[.2,.3]]*7}
        self.b = dict(self.a, mode="synthetic_reference", outcome="completed", fixtures=[])
        for name in gate.NAMES:
            f = {"name":name}
            for key, count in {"face":150528,"left":37632,"right":37632,"rect":12}.items():
                payload = bytes(count*4); filename = name+"_"+key+".f32"
                (self.folder/filename).write_bytes(payload)
                f[key] = {"file":filename, "count":count, "sha256":hashlib.sha256(payload).hexdigest()}
            self.a["fixtures"].append(dict(f,output=[.1]*258))
            self.b["fixtures"].append(dict(f,output_normal=[.1]*258,output_low=[.1]*258))
        self.args = argparse.Namespace(android=self.folder/"android.json",reference=self.folder/"reference.json",output=self.folder/"result.json")
    def compare(self):
        self.args.android.write_text(json.dumps(self.a)); self.args.reference.write_text(json.dumps(self.b))
        with contextlib.redirect_stdout(io.StringIO()):
            return gate.compare(self.args)
    def test_matching_synthetic_evidence_does_not_certify_accuracy(self):
        self.assertEqual(0,self.compare())
        result = json.loads(self.args.output.read_text())
        self.assertFalse(result["accuracy_established"]); self.assertFalse(result["localizer_parity_established"])
        self.assertFalse(result["android_low_precision_executed"])
        self.assertEqual(0.0,result["reference_normal_vs_low_max_abs"])
    def test_normal_pass_does_not_hide_low_precision_difference(self):
        self.b["fixtures"][0]["output_low"][0] = .5
        self.assertEqual(0,self.compare())
        self.assertFalse(json.loads(self.args.output.read_text())["upstream_low_precision_parity_pass"])
    def test_nonfinite_output_fails(self):
        self.a["fixtures"][0]["output"][1] = float("nan")
        self.assertEqual(1,self.compare())
    def test_nonfinite_low_reference_cannot_claim_precision_parity(self):
        self.b["fixtures"][0]["output_low"][0] = float("nan")
        self.assertEqual(0,self.compare())  # Normal reference remains valid.
        result = json.loads(self.args.output.read_text())
        self.assertFalse(result["upstream_low_precision_parity_pass"])
        self.assertIsNone(result["reference_normal_vs_low_max_abs"])
    def test_model_drift_fails(self):
        self.a["model_sha256"] = "wrong"
        with self.assertRaises(ValueError): self.compare()
    def test_runtime_drift_fails(self):
        self.a["mnn_precision"] = "low"
        with self.assertRaises(ValueError): self.compare()
    def test_missing_fixture_fails(self):
        self.a["fixtures"].pop()
        with self.assertRaises(ValueError): self.compare()
    def test_duplicate_fixture_fails(self):
        self.a["fixtures"].append(self.a["fixtures"][0])
        with self.assertRaises(ValueError): self.compare()
    def test_self_consistent_wrong_shape_fails(self):
        self.a["fixtures"][0]["face"]["count"] = 1
        with self.assertRaises(ValueError): self.compare()
    def test_corrupt_tensor_fails(self):
        (self.folder/"ramp_face.f32").write_bytes(b"bad")
        with self.assertRaises(ValueError): self.compare()
    def test_parent_path_is_rejected(self):
        with self.assertRaises(ValueError): gate.floats(self.folder,{"file":"../outside.f32"})
    def test_partial_run_is_rejected(self):
        self.a["outcome"] = "failed_or_cancelled"
        with self.assertRaises(ValueError): self.compare()
    def test_svr_difference_fails(self):
        self.a["synthetic_svr_predictions"] = [[.8,.9]]*7
        self.assertEqual(1,self.compare())


if __name__ == "__main__":
    unittest.main()
