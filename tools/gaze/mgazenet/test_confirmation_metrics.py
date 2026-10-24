"""Contract tests for the frozen direct-screen confirmation scorer."""
import copy
import hashlib
import json
import unittest

from confirmation_metrics import (
    CONFIRMATION_LOCATIONS,
    GRID,
    MAX_MEAN_MEDIAN_LINES,
    MAX_WORST_MEDIAN_LINES,
    METHOD,
    MIN_COORDINATES,
    PROTOCOL,
    SCREEN_FRACTIONS,
    SCREEN_METHOD,
    SVR,
    evaluate,
)


SCREEN = [1080, 2340]
VIEWPORT = [40.0, 100.0, 1040.0, 1900.0]
LINE_HEIGHT = 60.0
LINES = [{"index": index, "rect_px": [40.0, 100.0+index*60, 1040.0, 160.0+index*60]}
         for index in range(30)]


def viewport_point(fraction):
    x, y = fraction
    return [VIEWPORT[0]+x*(VIEWPORT[2]-VIEWPORT[0]),
            VIEWPORT[1]+y*(VIEWPORT[3]-VIEWPORT[1])]


def confirmation_point(grid_index):
    column = (grid_index-1) % 9
    row = (grid_index-1) // 9
    original = viewport_point((50/1920+column*(1820/1920)/8,
                               50/1080+row*(980/1080)/4))
    line = min(range(len(LINES)), key=lambda index:
        abs((LINES[index]["rect_px"][1]+LINES[index]["rect_px"][3])/2-original[1]))
    left, top, right, bottom = LINES[line]["rect_px"]
    point = [min(max(original[0], left+(right-left)*.02), right-(right-left)*.02),
             (top+bottom)/2]
    return point, line


def encoded(value):
    return json.dumps(value, separators=(",", ":"))


def rehash(report):
    report["calibration_manifest_json"] = encoded(report["calibration_manifest"])
    digest = hashlib.sha256(report["calibration_manifest_json"].encode()).hexdigest()
    report["calibration_manifest_sha256"] = digest
    report["calibration_id"] = report["session_id"]+":"+digest


def fixture(screen_error=0.0, confirmation_error=0.0,
            order="forward_then_reverse"):
    pipeline = {"source": "invented_features_and_predictions"}
    directions = (["forward", "reverse"] if order == "forward_then_reverse"
                  else ["reverse", "forward"])
    fit_fractions = [(.5, .5)]+[(x, y) for y in GRID for x in GRID]
    fit_targets = []
    fit_points = []
    loo_folds = []
    now = 100.0
    for index, fraction in enumerate(fit_fractions):
        target_id = "practice" if index == 0 else f"fit_{index}"
        target = viewport_point(fraction)
        captures = [now+1500+row*20 for row in range(45)]
        outputs = [capture+10 for capture in captures]
        fit_targets.append({"id": target_id, "practice": index == 0,
                            "fraction": list(fraction), "point_px": target})
        fit_points.append({
            "id": target_id, "practice": index == 0, "shown_ms": now,
            "accepted": 45, "rejected": {}, "first_accepted_capture_ms": captures[0],
            "completed_output_ms": outputs[-1],
            "collection_elapsed_ms_from_window_open": outputs[-1]-(now+1500),
            "output_age_ms": {"count": 45, "median": 10.0, "p95": 10.0, "max": 10.0},
            "within_target_feature_dispersion_rms": None if index == 0 else 0.1,
        })
        if index:
            loo_folds.append({
                "held_out_id": target_id, "target_px": target,
                "samples": [{"capture_ms": capture, "output_ms": output,
                             "point_px": target.copy()}
                            for capture, output in zip(captures, outputs)],
            })
        now = outputs[-1]+501

    screen_targets = []
    screen_blocks = []
    screen_small = []
    for index, fraction in enumerate(SCREEN_FRACTIONS, start=1):
        target_id = f"screen_validation_{index}"
        target = viewport_point(fraction)
        shown = now
        start = shown+3000
        end = start+2500
        observed = [target[0], target[1]+screen_error*LINE_HEIGHT]
        samples = [{"capture_ms": start+10+sample*20,
                    "output_ms": start+20+sample*20,
                    "point_px": observed.copy(), "reason": "coordinate"}
                   for sample in range(10)]
        screen_targets.append({"id": target_id, "role": "candidate_screen",
                               "fraction": list(fraction), "point_px": target})
        screen_blocks.append({"id": target_id, "role": "candidate_screen",
                              "shown_ms": shown, "start_ms": start, "end_ms": end,
                              "target_px": target, "line_height_px": LINE_HEIGHT,
                              "samples": samples})
        screen_small.append({"id": target_id, "coordinate_samples": 10,
                             "median_absolute_vertical_lines": screen_error,
                             "p95_absolute_vertical_lines": screen_error,
                             "max_absolute_vertical_lines": screen_error})
        now = end+251
    sealed_ms = screen_blocks[-1]["end_ms"]+250
    screen_checks = {
        "all_five_targets_contribute": True,
        "minimum_coordinates": True,
        "aggregate_vertical": screen_error <= MAX_MEAN_MEDIAN_LINES,
        "regional_vertical": screen_error <= MAX_WORST_MEDIAN_LINES,
    }
    sealed = {
        "method": SCREEN_METHOD, "sealed_ms": sealed_ms, "targets": screen_small,
        "minimum_coordinate_samples": 10,
        "mean_target_median_absolute_vertical_lines": screen_error,
        "mean_target_p95_absolute_vertical_lines": screen_error,
        "worst_target_median_absolute_vertical_lines": screen_error,
        "checks": screen_checks, "screen_candidate_pass": all(screen_checks.values()),
    }

    confirmation_targets = []
    confirmation_blocks = []
    for sweep_index, direction in enumerate(directions, start=1):
        grids = CONFIRMATION_LOCATIONS if direction == "forward" else tuple(reversed(CONFIRMATION_LOCATIONS))
        for order_index, grid_index in enumerate(grids, start=1):
            target, line = confirmation_point(grid_index)
            target_id = f"confirmation_sweep_{sweep_index}_test_{grid_index}"
            shown = now
            start = shown+3000
            end = start+2500
            observed = [target[0], target[1]+confirmation_error*LINE_HEIGHT]
            metadata = {
                "id": target_id, "location_id": f"test_{grid_index}",
                "grid_index": grid_index, "sweep": sweep_index,
                "sweep_direction": direction, "order_in_sweep": order_index,
                "point_px": target, "target_line_index": line,
            }
            confirmation_targets.append(metadata)
            confirmation_blocks.append({
                **metadata, "region": f"grid_{grid_index}",
                "role": "independent_confirmation", "task": "stationary_point",
                "shown_ms": shown, "start_ms": start, "end_ms": end,
                "line_height_px": LINE_HEIGHT, "viewport_screen_px": VIEWPORT,
                "lines": LINES,
                "samples": [{"capture_ms": start+10+sample*20,
                             "output_ms": start+20+sample*20,
                             "point_px": observed.copy(), "reason": "coordinate"}
                            for sample in range(10)],
            })
            confirmation_blocks[-1]["target_px"] = target.copy()
            now = end+251

    manifest = {
        "protocol": PROTOCOL, "pipeline": pipeline, "device": "no_phone",
        "screen_px": SCREEN, "viewport_screen_px": VIEWPORT, "line_height_px": LINE_HEIGHT,
        "coordinate_space": "physical_screen_px", "label_space": "physical_screen_fractions",
        "fit_geometry_source": "NewsMead 16-point calibration structure",
        "fit_grid_fractions": list(GRID), "fit_grid_order": "row_major_top_to_bottom_left_to_right",
        "fit_settle_ms": 1500.0, "fit_samples_per_target": 45,
        "fit_wait_ms": 500.0, "fit_timeout_ms": 30000.0,
        "screen_settle_ms": 3000.0, "screen_measure_ms": 2500.0, "screen_drain_ms": 250.0,
        "confirmation_settle_ms": 3000.0, "confirmation_measure_ms": 2500.0,
        "confirmation_drain_ms": 250.0,
        "eye_area_rule": "both_pixel_polygon_areas_strictly_above_10",
        "filter": "none", "correction": "none", "audit_method": METHOD,
        "audit_training_groups_per_fold": 15, "audit_rows_per_group": 45,
        "training_rows": 720, "training_digest": "a"*64, "svr": SVR,
        "screen_method": SCREEN_METHOD,
        "screen_minimum_coordinates_per_target": MIN_COORDINATES,
        "screen_maximum_mean_target_median_absolute_vertical_lines": MAX_MEAN_MEDIAN_LINES,
        "screen_maximum_worst_target_median_absolute_vertical_lines": MAX_WORST_MEDIAN_LINES,
        "screen_result_visibility": "sealed_and_hidden_until_terminal_record",
        "continue_after_screen_result": True,
        "confirmation_locations": list(CONFIRMATION_LOCATIONS),
        "validation_order": order, "sweep_directions": directions,
        "fit_targets": fit_targets, "screen_targets": screen_targets,
        "confirmation_targets": confirmation_targets,
    }
    pipeline_json = encoded(pipeline)
    report = {
        "schema": PROTOCOL, "outcome": "complete", "failure": None,
        "evidence_kind": "synthetic_contract", "protocol_id": PROTOCOL,
        "session_id": "synthetic", "run_label": "synthetic", "device_id": "no_phone",
        "pipeline_id": hashlib.sha256(pipeline_json.encode()).hexdigest(),
        "pipeline_json": pipeline_json, "calibration_manifest": manifest,
        "coordinate_space": "physical_screen_px", "clock": "shared_monotonic_ms",
        "audit_method": METHOD, "validation_order": order,
        "fit_block_ids": [f"fit_{index}" for index in range(1, 17)],
        "fit_points": fit_points, "loo_folds": loo_folds,
        "screen_blocks": screen_blocks, "sealed_screen_result": sealed,
        "screen_candidate_pass": sealed["screen_candidate_pass"],
        "confirmation_blocks": confirmation_blocks,
        "started_ms": fit_points[0]["shown_ms"],
        "finished_ms": confirmation_blocks[-1]["end_ms"]+250,
        "discarded_outside_sampling_windows": 0,
        "active_tracker_access": False, "calibration_store_access": False,
        "camera_frames_retained": False, "features_retained": False,
        "personal_model_retained": False, "model_correction_applied": False,
        "screen_result_hidden_until_terminal": True,
        "confirmation_accuracy_pass": None, "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
    }
    rehash(report)
    return report


class ConfirmationMetricsTests(unittest.TestCase):
    def test_matching_pass_reconstructs_without_promotion(self):
        result = evaluate(fixture(.2, .3))
        self.assertTrue(result["screen_prediction"]["screen_candidate_pass"])
        self.assertTrue(result["confirmation"]["confirmation_line_level_pass"])
        self.assertTrue(result["screen_prediction"]["classification_agrees"])
        self.assertEqual(20, len(result["confirmation"]["blocks"]))
        self.assertEqual(10, len(result["confirmation"]["location_spatial"]))
        self.assertIsNone(result["accuracy_gate_pass"])
        self.assertEqual("not_evaluated", result["promotion_decision"])

    def test_screen_failure_still_contains_and_scores_all_downstream_blocks(self):
        result = evaluate(fixture(2.0, .2, "reverse_then_forward"))
        self.assertFalse(result["screen_prediction"]["screen_candidate_pass"])
        self.assertTrue(result["confirmation"]["confirmation_line_level_pass"])
        self.assertTrue(result["screen_prediction"]["false_reject"])
        self.assertTrue(all(block["coordinate_samples"] == 10
                            for block in result["confirmation"]["blocks"]))

    def test_false_accept_is_exposed(self):
        result = evaluate(fixture(.2, 2.0))
        self.assertTrue(result["screen_prediction"]["screen_candidate_pass"])
        self.assertFalse(result["confirmation"]["confirmation_line_level_pass"])
        self.assertTrue(result["screen_prediction"]["false_accept"])

    def test_rehashed_threshold_continuation_and_geometry_changes_are_rejected(self):
        mutations = (
            lambda report: report["calibration_manifest"].update(
                screen_maximum_mean_target_median_absolute_vertical_lines=1.1),
            lambda report: report["calibration_manifest"].update(continue_after_screen_result=False),
            lambda report: report["calibration_manifest"]["screen_targets"][0].update(
                point_px=report["calibration_manifest"]["fit_targets"][1]["point_px"]),
        )
        for mutate in mutations:
            report = fixture()
            mutate(report)
            rehash(report)
            with self.assertRaises(ValueError):
                evaluate(report)

    def test_screen_result_order_decision_and_privacy_tampering_are_rejected(self):
        mutations = (
            lambda report: report["sealed_screen_result"].update(screen_candidate_pass=False),
            lambda report: report["confirmation_blocks"][0].update(order_in_sweep=2),
            lambda report: report.update(accuracy_gate_pass=True),
            lambda report: report.update(model_correction_applied=True),
        )
        for mutate in mutations:
            report = fixture()
            mutate(report)
            with self.assertRaises(ValueError):
                evaluate(report)


if __name__ == "__main__":
    unittest.main()
