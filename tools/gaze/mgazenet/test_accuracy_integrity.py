import copy
import hashlib
import json
import unittest
from pathlib import Path
from accuracy_metrics import evaluate
from collect_accuracy import validate


class AccuracyIntegrityTests(unittest.TestCase):
    @staticmethod
    def bind_manifests(report):
        pipeline_encoded = json.dumps(report["calibration_manifest"]["pipeline"], separators=(",", ":"))
        report["pipeline_json"] = pipeline_encoded
        report["pipeline_id"] = hashlib.sha256(pipeline_encoded.encode()).hexdigest()
        encoded = json.dumps(report["calibration_manifest"], separators=(",", ":"))
        digest = hashlib.sha256(encoded.encode()).hexdigest()
        report["calibration_manifest_json"] = encoded
        report["calibration_manifest_sha256"] = digest
        report["calibration_id"] = report["session_id"] + ":" + digest

    def manifest_fixture(self):
        # Frozen Kotlin export uses invented features/predictions, never a person's data.
        # Keep this in the source tree so integrity tests also run before a Gradle build.
        path = Path(__file__).parent / "fixtures/accuracy-harness-kotlin-synthetic.json"
        return json.loads(path.read_text(encoding="utf-8"))

    def v2_manifest_fixture(self, order="forward_then_reverse"):
        directions = (["forward", "reverse"] if order == "forward_then_reverse"
                      else ["reverse", "forward"])
        report = self.manifest_fixture()
        report.update(schema="mgazenet_accuracy_v2", protocol_id="mgazenet_stationary_viewport_v2",
                      validation_order=order, max_output_age_ms=None,
                      analysis_age_limits_ms=[50, 100, 200, 500])
        manifest = report["calibration_manifest"]
        manifest.update(protocol="mgazenet_stationary_viewport_v2",
                        validation_locations=[2, 8, 13, 15, 22, 24, 31, 33, 38, 44],
                        validation_order=order,
                        sweep_directions=directions,
                        analysis_age_limits_ms=[50, 100, 200, 500])
        viewport = manifest["viewport_screen_px"]
        lines = report["blocks"][0]["lines"]
        boxes = [line["rect_px"] for line in lines]
        locations = manifest["validation_locations"]
        blocks = []
        shown = 48582.0
        for sweep, direction in enumerate(directions, start=1):
            sequence = locations if direction == "forward" else list(reversed(locations))
            for position, index in enumerate(sequence, start=1):
                x_fraction = 50/1920 + ((index-1) % 9) * (1820/1920)/8
                y_fraction = 50/1080 + ((index-1) // 9) * (980/1080)/4
                raw = [viewport[0] + x_fraction*(viewport[2]-viewport[0]),
                       viewport[1] + y_fraction*(viewport[3]-viewport[1])]
                line = min(range(len(boxes)), key=lambda i: abs((boxes[i][1]+boxes[i][3])/2-raw[1]))
                left, top, right, bottom = boxes[line]
                point = [min(max(raw[0], left+(right-left)*.02), right-(right-left)*.02),
                         (top+bottom)/2]
                blocks.append({
                    "id": f"sweep_{sweep}_test_{index}", "location_id": f"test_{index}",
                    "grid_index": index, "sweep": sweep, "sweep_direction": direction,
                    "order_in_sweep": position, "region": f"grid_{index}", "role": "held_out",
                    "task": "stationary_point", "shown_ms": shown, "start_ms": shown+3000,
                    "end_ms": shown+5500, "target_px": point, "target_line_index": line,
                    "line_height_px": manifest["line_height_px"], "viewport_screen_px": viewport,
                    "lines": lines, "samples": [],
                })
                shown += 5751
        report["blocks"] = blocks
        report["finished_ms"] = shown
        self.bind_manifests(report)
        return report

    def test_manifest_changes_or_misbound_session_are_rejected(self):
        report = self.manifest_fixture()
        self.assertIsNone(evaluate(report)["accuracy_gate_pass"])
        for change in (
            lambda r: r["calibration_manifest"].update(training_rows=586),
            lambda r: r.update(calibration_manifest_json='{}'),
            lambda r: r.update(calibration_id="wrong_session"),
            lambda r: r.update(outcome="stopped"),
        ):
            corrupted = copy.deepcopy(report); change(corrupted)
            with self.assertRaises(ValueError):
                evaluate(corrupted)

    def test_complete_harness_export_rejects_cross_field_changes(self):
        report = self.manifest_fixture()
        self.assertEqual(8, evaluate(report)["planned_blocks"])
        for change in (
            lambda r: r.update(device_id="other"),
            lambda r: r.update(pipeline_id="0"*64),
            lambda r: r["calibration_manifest"]["pipeline"].update(model_sha256="0"*64),
            lambda r: r["fit_points"][1].update(accepted=44),
            lambda r: r.update(fit_block_ids=list(reversed(r["fit_block_ids"]))),
            lambda r: r["blocks"][0].update(id="test_3"),
            lambda r: r["blocks"][0].update(start_ms=r["blocks"][0]["start_ms"]+1),
            lambda r: r["blocks"][0].update(line_height_px=r["blocks"][0]["line_height_px"]+1),
            lambda r: r.update(features_retained=True),
        ):
            corrupted = copy.deepcopy(report); change(corrupted)
            with self.assertRaises(ValueError):
                evaluate(corrupted)

    def test_removing_manifests_cannot_bypass_harness_validation(self):
        keys = ("calibration_manifest_json", "calibration_manifest_sha256",
                "calibration_manifest", "pipeline_json")
        for kind in ("recorded", "synthetic_contract"):
            for removed in [(key,) for key in keys] + [keys]:
                report = self.manifest_fixture()
                report["evidence_kind"] = kind
                for key in removed:
                    report.pop(key)
                with self.subTest(kind=kind, removed=removed), self.assertRaises(ValueError):
                    evaluate(report)

    def test_rehashed_geometry_or_protocol_changes_are_rejected(self):
        for change in (
            lambda r: r["blocks"][0].update(target_px=[200, 191]),
            lambda r: r["blocks"][0].update(region="grid_8"),
            lambda r: r["calibration_manifest"]["targets"][1].update(point_px=[100, 100]),
            lambda r: r["calibration_manifest"].update(screen_px=[100, 100]),
            lambda r: r["fit_points"][0].update(practice=False),
            lambda r: r["blocks"][1]["lines"][0].update(rect_px=[41, 101, 1040, 161]),
            lambda r: (r.update(protocol_id="changed"), r["calibration_manifest"].update(protocol="changed")),
            lambda r: r["calibration_manifest"].update(test_settle_ms=2000),
            lambda r: r.pop("outcome"),
        ):
            report = self.manifest_fixture()
            change(report)
            self.bind_manifests(report)
            with self.assertRaises(ValueError):
                evaluate(report)

    def test_recorded_evidence_requires_the_pinned_assets(self):
        report = self.manifest_fixture()
        report["evidence_kind"] = "recorded"
        report["calibration_manifest"]["pipeline"].update(
            model_sha256="2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96",
            face_landmarker_sha256="64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
        )
        self.bind_manifests(report)
        self.assertIsNone(evaluate(report)["accuracy_gate_pass"])
        report["calibration_manifest"]["pipeline"]["model_sha256"] = "0" * 64
        self.bind_manifests(report)
        with self.assertRaises(ValueError):
            evaluate(report)

    def test_v2_freezes_repeated_locations_orders_and_four_age_reporting(self):
        report = self.v2_manifest_fixture()
        first = report["blocks"][0]
        second = next(block for block in report["blocks"] if block["location_id"] == first["location_id"] and block["sweep"] == 2)
        first["samples"] = [
            {"capture_ms": first["start_ms"]+10, "output_ms": first["start_ms"]+30,
             "point_px": [first["target_px"][0]-4, first["target_px"][1]-2]},
            {"capture_ms": first["start_ms"]+50, "output_ms": first["start_ms"]+70,
             "point_px": [first["target_px"][0]+4, first["target_px"][1]+2]},
        ]
        second["samples"] = [
            {"capture_ms": second["start_ms"]+10, "output_ms": second["start_ms"]+30,
             "point_px": [second["target_px"][0]+10, second["target_px"][1]+6]},
        ]
        result = evaluate(report)
        self.assertEqual("mgazenet_accuracy_summary_v2", result["schema"])
        self.assertEqual(20, result["planned_blocks"])
        self.assertEqual([50, 100, 200, 500], [row["max_output_age_ms"] for row in result["freshness_sensitivity"]])
        self.assertIsNone(result["primary_freshness_threshold_ms"])
        self.assertEqual(6.0, result["repeated_location_bias_change"][0]["second_minus_first_mean_bias_px"]["y"])
        self.assertEqual(2, result["blocks"][0]["within_target_dispersion_px"]["n"])
        self.assertEqual("sample_standard_deviation_n_minus_1",
                         result["blocks"][0]["within_target_dispersion_px"]["estimator"])
        self.assertEqual(18, result["blocks_without_coordinates"])
        reverse = evaluate(self.v2_manifest_fixture("reverse_then_forward"))
        self.assertEqual("reverse_then_forward", reverse["validation_order"])
        self.assertEqual("reverse", reverse["blocks"][0]["sweep_direction"])

    def test_v2_rejects_rehashed_order_location_and_age_changes(self):
        for change in (
            lambda r: r["blocks"][0].update(order_in_sweep=2),
            lambda r: r["blocks"][0].update(location_id="test_8"),
            lambda r: r["blocks"][0].update(sweep_direction="reverse"),
            lambda r: r.update(analysis_age_limits_ms=[100, 200, 500]),
            lambda r: r.update(max_output_age_ms=100),
        ):
            report = self.v2_manifest_fixture(); change(report)
            with self.assertRaises(ValueError):
                evaluate(report)

    def test_collector_retains_partial_record_but_refuses_hash_or_identity_change(self):
        report = {"schema": "mgazenet_accuracy_partial_v1", "session_id": "1_abc", "outcome": "stopped",
                  "camera_frames_retained": False}
        raw = json.dumps(report).encode()
        digest = hashlib.sha256(raw).hexdigest()
        self.assertEqual(validate(raw, digest, "1_abc")["outcome"], "stopped")
        with self.assertRaises(ValueError):
            validate(raw, "0"*64, "1_abc")
        with self.assertRaises(ValueError):
            validate(raw, digest, "2_def")
        report.update(schema="mgazenet_accuracy_partial_v2")
        raw = json.dumps(report).encode(); digest = hashlib.sha256(raw).hexdigest()
        self.assertEqual("stopped", validate(raw, digest, "1_abc")["outcome"])


if __name__ == "__main__":
    unittest.main()
