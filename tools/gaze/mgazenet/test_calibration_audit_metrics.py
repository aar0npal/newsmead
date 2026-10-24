"""Synthetic contract tests for calibration-audit validation and scoring, not gaze accuracy."""
import copy
import hashlib
import json
import math
import unittest

from calibration_audit_metrics import evaluate


GRID = [.1, .3667, .6333, .9]
VALIDATION = [[.5, .5], [.25, .25], [.75, .25], [.25, .75], [.75, .75]]


def encoded(value):
    return json.dumps(value, separators=(",", ":"))


def point(viewport, fraction):
    left, top, right, bottom = viewport
    return [left+fraction[0]*(right-left), top+fraction[1]*(bottom-top)]


def compact(values):
    values = sorted(values)
    def percentile(fraction):
        position = (len(values)-1)*fraction
        lower = int(position)
        upper = math.ceil(position)
        return values[lower] if lower == upper else values[lower]+(values[upper]-values[lower])*(position-lower)
    return {"count": len(values), "median": percentile(.5), "p95": percentile(.95), "max": max(values)}


def fixture():
    screen = [1080, 2340]
    viewport = [40.0, 101.0, 1040.0, 1901.0]
    fit_fractions = [[.5, .5]]+[[x, y] for y in GRID for x in GRID]
    fit_ids = ["practice"]+[f"fit_{index}" for index in range(1, 17)]
    pipeline = {"source": "invented_features_and_predictions"}
    shown = 100.0
    fit_points = []
    folds = []
    for index, (target_id, fraction) in enumerate(zip(fit_ids, fit_fractions)):
        samples = []
        ages = []
        for row in range(45):
            capture = shown+1500+row*33
            output = capture+10
            ages.append(output-capture)
            if index:
                samples.append({"capture_ms": capture, "output_ms": output,
                                "point_px": point(viewport, fraction)})
        fit_points.append({
            "id": target_id, "practice": index == 0, "shown_ms": shown, "accepted": 45,
            "rejected": {}, "first_accepted_capture_ms": shown+1500,
            "completed_output_ms": shown+1500+44*33+10,
            "collection_elapsed_ms_from_window_open": 44*33+10,
            "output_age_ms": compact(ages),
            "within_target_feature_dispersion_rms": None if index == 0 else .1,
        })
        if index:
            folds.append({"held_out_id": target_id, "target_px": point(viewport, fraction),
                          "samples": samples})
        shown += 1500+44*33+10+500+1
    verification_fractions = [fit_fractions[6]]+VALIDATION
    verification_ids = ["drift_repeat_fit_6"]+[f"validation_{index}" for index in range(1, 6)]
    verification_roles = ["drift_repeat"]+["held_out_validation"]*5
    blocks = []
    for index, (target_id, role, fraction) in enumerate(zip(
            verification_ids, verification_roles, verification_fractions)):
        start = shown+3000
        target = point(viewport, fraction)
        blocks.append({
            "id": target_id, "role": role, "shown_ms": shown, "start_ms": start,
            "end_ms": start+2500, "target_px": target,
            "samples": [
                {"capture_ms": start+100, "output_ms": start+110, "point_px": target, "reason": "coordinate"},
                {"capture_ms": start+200, "output_ms": start+210, "point_px": None, "reason": "no_face"},
            ],
        })
        shown += 5751
    fit_targets = [{"id": target_id, "practice": index == 0, "fraction": fraction,
                    "point_px": point(viewport, fraction)}
                   for index, (target_id, fraction) in enumerate(zip(fit_ids, fit_fractions))]
    verification_targets = [{"id": target_id, "role": role, "fraction": fraction,
                             "point_px": point(viewport, fraction)}
                            for target_id, role, fraction in zip(
                                verification_ids, verification_roles, verification_fractions)]
    manifest = {
        "protocol": "mgazenet_calibration_observability_v3", "pipeline": pipeline,
        "device": "no_phone", "screen_px": screen, "viewport_screen_px": viewport,
        "line_height_px": 60.0, "coordinate_space": "physical_screen_px",
        "label_space": "physical_screen_fractions",
        "fit_geometry_source": "NewsMead 16-point calibration structure",
        "fit_grid_fractions": GRID, "fit_grid_order": "row_major_top_to_bottom_left_to_right",
        "fit_settle_ms": 1500.0, "fit_samples_per_target": 45, "fit_wait_ms": 500.0,
        "fit_timeout_ms": 30000.0, "verification_settle_ms": 3000.0,
        "verification_measure_ms": 2500.0, "verification_drain_ms": 250.0,
        "eye_area_rule": "both_pixel_polygon_areas_strictly_above_10",
        "filter": "none", "correction": "none",
        "audit_method": "leave_one_complete_target_group_out",
        "audit_training_groups_per_fold": 15, "audit_rows_per_group": 45,
        "training_rows": 720, "training_digest": "a"*64,
        "svr": "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001",
        "fit_targets": fit_targets, "drift_repeat_fit_id": "fit_6",
        "verification_targets": verification_targets,
    }
    pipeline_json = encoded(pipeline)
    manifest_json = encoded(manifest)
    digest = hashlib.sha256(manifest_json.encode()).hexdigest()
    return {
        "schema": "mgazenet_calibration_audit_v3", "outcome": "complete", "failure": None,
        "evidence_kind": "synthetic_contract", "protocol_id": "mgazenet_calibration_observability_v3",
        "session_id": "synthetic_audit", "run_label": "synthetic", "device_id": "no_phone",
        "pipeline_id": hashlib.sha256(pipeline_json.encode()).hexdigest(), "pipeline_json": pipeline_json,
        "calibration_id": "synthetic_audit:"+digest, "calibration_manifest_sha256": digest,
        "calibration_manifest": manifest, "calibration_manifest_json": manifest_json,
        "coordinate_space": "physical_screen_px", "clock": "shared_monotonic_ms",
        "audit_method": "leave_one_complete_target_group_out", "fit_block_ids": fit_ids[1:],
        "fit_points": fit_points, "loo_folds": folds, "verification_blocks": blocks,
        "started_ms": 100.0, "finished_ms": shown, "discarded_outside_sampling_windows": 0,
        "active_tracker_access": False, "calibration_store_access": False,
        "camera_frames_retained": False, "features_retained": False,
        "personal_model_retained": False, "model_correction_applied": False,
        "accuracy_gate_pass": None, "promotion_decision": "not_evaluated",
        "scope": "calibration observability and instructed stationary verification only",
    }


def rebind(report):
    report["pipeline_json"] = encoded(report["calibration_manifest"]["pipeline"])
    report["pipeline_id"] = hashlib.sha256(report["pipeline_json"].encode()).hexdigest()
    report["calibration_manifest_json"] = encoded(report["calibration_manifest"])
    digest = hashlib.sha256(report["calibration_manifest_json"].encode()).hexdigest()
    report["calibration_manifest_sha256"] = digest
    report["calibration_id"] = report["session_id"]+":"+digest


class CalibrationAuditMetricsTests(unittest.TestCase):
    def test_well_separated_targets_have_zero_loo_and_verification_error(self):
        result = evaluate(fixture())
        self.assertEqual(16, len(result["loo"]["folds"]))
        self.assertEqual(0, result["loo"]["target_balanced"]["mean_target_median"]["euclidean_px"])
        self.assertEqual(0, result["verification"]["target_balanced"]["mean_target_p95"]["absolute_vertical_lines"])
        self.assertIsNone(result["accuracy_gate_pass"])
        self.assertEqual("not_evaluated", result["promotion_decision"])

    def test_one_corrupted_target_remains_visible_in_target_balanced_audit(self):
        report = fixture()
        for sample in report["loo_folds"][7]["samples"]:
            sample["point_px"][1] += 180
        result = evaluate(report)
        self.assertEqual(3, result["loo"]["folds"][7]["absolute_vertical_lines"]["median"])
        self.assertEqual(3/16, result["loo"]["target_balanced"]["mean_target_median"]["absolute_vertical_lines"])

    def test_global_feature_collapse_shape_cannot_hide_spatial_errors(self):
        report = fixture()
        centre = point(report["calibration_manifest"]["viewport_screen_px"], [.5, .5])
        for fold in report["loo_folds"]:
            for sample in fold["samples"]:
                sample["point_px"] = centre.copy()
        result = evaluate(report)
        self.assertGreater(result["loo"]["target_balanced"]["mean_target_median"]["euclidean_px"], 300)
        self.assertEqual(16, result["loo"]["target_balanced"]["contributing_target_count"])

    def test_successful_fit_metadata_does_not_mask_poor_heldout_predictions(self):
        report = fixture()
        for sample in report["loo_folds"][0]["samples"]:
            sample["point_px"] = [1079, 2339]
        result = evaluate(report)
        self.assertGreater(result["loo"]["folds"][0]["euclidean_px"]["median"], 1000)
        self.assertIsNone(result["accuracy_gate_pass"])

    def test_missing_duplicate_nonfinite_or_row_count_changes_are_rejected(self):
        mutations = (
            lambda report: report["loo_folds"].pop(),
            lambda report: report["loo_folds"][1].update(held_out_id="fit_1"),
            lambda report: report["loo_folds"][0]["samples"].pop(),
            lambda report: report["loo_folds"][0]["samples"][0]["point_px"].__setitem__(0, math.nan),
            lambda report: report["loo_folds"][0]["samples"][1].update(
                capture_ms=report["loo_folds"][0]["samples"][0]["capture_ms"]),
        )
        for mutate in mutations:
            report = fixture(); mutate(report)
            with self.assertRaises(ValueError):
                evaluate(report)

    def test_row_level_split_claim_anchor_change_and_decision_are_rejected(self):
        mutations = (
            lambda report: report["calibration_manifest"].update(audit_method="random_row_split"),
            lambda report: report["calibration_manifest"].update(audit_training_groups_per_fold=16),
            lambda report: report["calibration_manifest"]["verification_targets"][1].update(fraction=[.5, .4]),
            lambda report: report["calibration_manifest"].update(drift_repeat_fit_id="fit_11"),
            lambda report: report.update(accuracy_gate_pass=True),
        )
        for mutate in mutations:
            report = fixture(); mutate(report)
            if "calibration_manifest" in report:
                rebind(report)
            with self.assertRaises(ValueError):
                evaluate(report)

    def test_manifest_removal_and_identity_changes_are_rejected(self):
        mutations = (
            lambda report: report.pop("calibration_manifest_json"),
            lambda report: report.update(calibration_manifest_sha256="0"*64),
            lambda report: report.update(calibration_id="other"),
            lambda report: report.update(pipeline_id="0"*64),
            lambda report: report["calibration_manifest"].pop("fit_targets"),
        )
        for mutate in mutations:
            report = fixture(); mutate(report)
            with self.assertRaises((ValueError, KeyError)):
                evaluate(report)

    def test_verification_nulls_and_output_age_are_retained_separately(self):
        result = evaluate(fixture())["verification"]
        self.assertEqual(1, result["blocks"][0]["coordinate_samples"])
        self.assertEqual(1, result["blocks"][0]["null_samples"])
        self.assertEqual(2, result["blocks"][0]["output_age_ms"]["count"])
        self.assertEqual(0, result["blocks_without_coordinates"])

    def test_heldout_primary_excludes_drift_repeat(self):
        report = fixture()
        drift = report["verification_blocks"][0]
        drift_target = drift["target_px"]
        drift["samples"][0]["point_px"] = [drift_target[0], drift_target[1]+120]
        result = evaluate(report)["verification"]
        self.assertEqual(1/3,
                         result["target_balanced"]["mean_target_median"]["absolute_vertical_lines"])
        held_out = result["held_out_validation_target_balanced"]
        self.assertEqual(5, held_out["contributing_target_count"])
        self.assertEqual(0, held_out["mean_target_median"]["absolute_vertical_lines"])


if __name__ == "__main__":
    unittest.main()
