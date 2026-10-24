"""Analytic fixtures test measurement behavior, not MGazeNet gaze accuracy."""
import copy
import math
import unittest
from accuracy_metrics import evaluate


def fixture():
    block = {
        "id": "test_left", "region": "top_left", "role": "held_out", "task": "stationary_point",
        "start_ms": 0, "end_ms": 1000, "target_px": [50, 50], "target_line_index": 0,
        "line_height_px": 100, "viewport_screen_px": [0, 0, 100, 300],
        "lines": [{"index": i, "rect_px": [0, i*100, 100, (i+1)*100]} for i in range(3)],
        "samples": [
            {"capture_ms": 100, "output_ms": 120, "point_px": [50, 50]},
            {"capture_ms": 400, "output_ms": 420, "point_px": None},
            {"capture_ms": 800, "output_ms": 820, "point_px": [50, 50]},
        ],
    }
    empty = copy.deepcopy(block)
    empty.update(id="test_empty", region="bottom_left", start_ms=1000, end_ms=2000, samples=[])
    return {
        "schema": "mgazenet_accuracy_v1", "evidence_kind": "synthetic_contract",
        "coordinate_space": "physical_screen_px", "clock": "shared_monotonic_ms",
        "protocol_id": "analytic_fixture_only", "session_id": "invented_session",
        "device_id": "no_phone", "pipeline_id": "invented_coordinates", "calibration_id": "no_person",
        "fit_block_ids": ["fit_center"], "max_output_age_ms": 100, "blocks": [block, empty],
    }


class AccuracyMetricsTests(unittest.TestCase):
    def test_missing_intervals_and_empty_targets_remain_in_denominator(self):
        result = evaluate(fixture())
        first, empty = result["blocks"]
        self.assertAlmostEqual(first["exact_line_fraction_of_received"], 2/3)
        self.assertEqual(first["fresh_exact_line_ms"], 160)
        self.assertEqual(first["longest_unavailable_ms"], 620)
        self.assertEqual(empty["unavailable_ms"], 1000)
        self.assertIsNone(empty["absolute_vertical_lines"]["median"])
        self.assertEqual(result["blocks_without_coordinates"], 1)
        self.assertEqual(result["target_balanced"]["fresh_exact_line_time_fraction"], .08)
        self.assertIsNone(result["accuracy_gate_pass"])

    def test_zero_vertical_error_does_not_make_off_text_coordinate_correct(self):
        report = fixture()
        report["blocks"][0]["samples"][0]["point_px"] = [101, 50]
        result = evaluate(report)["blocks"][0]
        self.assertEqual(result["absolute_vertical_lines"]["max"], 0)
        self.assertEqual(result["off_text_coordinate_samples"], 1)
        self.assertEqual(result["exact_line_samples"], 1)
        self.assertEqual(result["fresh_coordinate_ms"], 160)
        self.assertEqual(result["fresh_exact_line_ms"], 80)

    def test_line_geometry_is_half_open_and_adjacent_is_separate(self):
        report = fixture()
        report["blocks"][0]["samples"][0]["point_px"] = [50, 100]
        result = evaluate(report)["blocks"][0]
        self.assertEqual(result["exact_line_samples"], 1)
        self.assertEqual(result["within_one_line_samples"], 2)
        self.assertEqual(result["signed_dy_px"]["max"], 50)

    def test_invalid_result_ends_cached_coordinate_availability(self):
        report = fixture()
        report["max_output_age_ms"] = 1000
        result = evaluate(report)["blocks"][0]
        # First result 120..420; explicit invalid 420..820; final result 820..1000.
        self.assertEqual(result["fresh_coordinate_ms"], 480)
        self.assertEqual(result["longest_unavailable_ms"], 400)

    def test_late_and_stale_results_do_not_backfill_time(self):
        report = fixture()
        report["blocks"][0]["samples"] = [
            {"capture_ms": 100, "output_ms": 300, "point_px": [50, 50]},
            {"capture_ms": 900, "output_ms": 1010, "point_px": [50, 50]},
        ]
        result = evaluate(report)["blocks"][0]
        self.assertEqual(result["exact_line_samples"], 2)
        self.assertEqual(result["fresh_coordinate_ms"], 0)
        self.assertEqual(result["outputs_after_window"], 1)

    def test_no_good_median_can_hide_a_large_regional_tail(self):
        report = fixture()
        report["blocks"][0]["samples"][2]["point_px"] = [50, 550]
        result = evaluate(report)
        self.assertEqual(result["worst_block_vertical_lines"], 5)
        self.assertEqual(result["blocks"][0]["absolute_vertical_lines"]["p95"], 5)

    def test_signed_error_keeps_both_negative_and_positive_tails(self):
        report = fixture()
        report["blocks"][0]["samples"][0]["point_px"] = [10, 10]
        report["blocks"][0]["samples"][2]["point_px"] = [90, 90]
        signed = evaluate(report)["blocks"][0]["signed_dy_px"]
        self.assertEqual(-40, signed["min"])
        self.assertEqual(-40, signed["p05"])
        self.assertEqual(40, signed["p95"])
        self.assertEqual(40, signed["max"])

    def test_coordinate_origin_changes_preserve_errors_and_assignment(self):
        report = fixture()
        before = evaluate(report)
        for b in report["blocks"]:
            b["target_px"][1] += 101
            b["viewport_screen_px"][1] += 101
            b["viewport_screen_px"][3] += 101
            for line in b["lines"]:
                line["rect_px"][1] += 101; line["rect_px"][3] += 101
            for sample in b["samples"]:
                if sample["point_px"] is not None:
                    sample["point_px"][1] += 101
        self.assertEqual(evaluate(report), before)

    def test_leakage_unknown_task_and_bad_clocks_are_rejected(self):
        for mutate in (
            lambda r: r.update(fit_block_ids=["test_left"]),
            lambda r: r["blocks"][0].update(role="fit"),
            lambda r: r["blocks"][0].update(task="natural_reading"),
            lambda r: r.update(clock="wall_clock_ms"),
            lambda r: r.update(max_output_age_ms=0),
            lambda r: r["blocks"][1].update(id="test_left"),
            lambda r: r["blocks"][1].update(start_ms=500),
            lambda r: r["blocks"][0].update(target_line_index=2),
        ):
            report = fixture(); mutate(report)
            with self.assertRaises(ValueError):
                evaluate(report)

    def test_corrupt_coordinates_timestamps_and_geometry_are_rejected(self):
        for mutate in (
            lambda r: r["blocks"][0]["samples"][0].update(point_px=[math.nan, 50]),
            lambda r: r["blocks"][0]["samples"][0].update(point_px=[math.inf, 50]),
            lambda r: r["blocks"][0]["samples"][0].update(output_ms=99),
            lambda r: r["blocks"][0]["samples"][1].update(capture_ms=100),
            lambda r: r["blocks"][0]["samples"][1].update(output_ms=120),
            lambda r: r["blocks"][0]["lines"][1].update(rect_px=[0, 99, 100, 200]),
            lambda r: r["blocks"][0].update(line_height_px=0),
        ):
            report = fixture(); mutate(report)
            with self.assertRaises(ValueError):
                evaluate(report)


if __name__ == "__main__":
    unittest.main()
