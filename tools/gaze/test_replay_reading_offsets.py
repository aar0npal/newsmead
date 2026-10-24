"""Pure synthetic tests; no phone, Android build, or participant logs needed."""

import copy
import math
import unittest

from replay_reading_offsets import Geometry, analyze_records, float32, infer_geometry, infer_pitch, reference_offset, summarize


def fixture():
    geometry = Geometry(133, 12, 48, 6340, 259, 2205, 45)
    records = [
        {"record_type": "session_start", "protocol_version": 4, "density_dpi": 450, "run_label": "synthetic", "session_id": "synthetic", "vertical_alignment_mode": "off", "order_variant": "A", "detailed_source_telemetry_enabled": False, "drift_correction_active": False},
        {"record_type": "layout", "line_count": 48, "line_height_px": 137},
    ]
    for name, y in [("top", 648.2), ("middle", 1232), ("bottom", 1815.8)]:
        records.append({"record_type": "vertical_alignment_reference", "reference_id": name, "sample_count": 30, "target_y_screen_px": y, "observed_median_y_screen_px": y})
    records.append({"record_type": "vertical_alignment_fit", "accepted": True, "applied": False, "gain": 1., "intercept_px": 0., "reason": "accepted"})

    def sample(local, scroll, state, cp, expected_line):
        text_top = 304 - scroll
        y = text_top + local
        line = geometry.line_at(y, text_top)
        return {"record_type": "gaze_sample", "phase": "LOCALIZATION" if cp.startswith("localization") else "GUIDED_LINE_READING", "step_id": cp, "trial_state": state, "expected_checkpoint": cp, "expected_line": expected_line, "expected_region": "synthetic", "gaze_y_screen_px": y, "effective_gaze_y_screen_px": y, "vertical_alignment_delta_px": 0., "text_top_screen_px": text_top, "scroll_y": scroll, "base_target_line": line, "base_target_valid": line >= 0, "raw_target_line": line, "raw_target_valid": line >= 0}

    # Observations straddle every interior boundary; geometry uses ACQUIRE only.
    for line in range(1, 48):
        boundary = geometry.line_top(line)
        for local in (boundary - .25, boundary + .25):
            scroll = min(4484, max(0, int(local) - 500))
            records.append(sample(local, scroll, "ACQUIRE", "unscored", None))
    for idx, line in enumerate([5, 11, 17, 23, 29, 35, 41, 44]):
        fraction = [.22, .50, .76, .22, .76, .50, .22, .76][idx]
        scroll = min(4484, max(0, 45 + int(geometry.line_center(line)) - int(1946 * fraction)))
        records.append(sample(geometry.line_center(line), scroll, "MEASURE", f"localization_{idx + 1}", line))
    for idx, line in enumerate([9, 15, 22, 30, 36, 43]):
        scroll = min(4484, max(0, 45 + int(geometry.line_center(line)) - int(1946 * [.25, .5, .75][idx % 3])))
        records.append(sample(geometry.line_center(line), scroll, "MEASURE", f"line_reading_{idx + 1}", line))
    records.append({"record_type": "session_end", "outcome": "completed", "measured_sample_count": 14})
    return records


class OffsetReplayTest(unittest.TestCase):
    def test_reference_median_only(self):
        refs = [{"reference_id": str(i), "sample_count": 20, "target_y_screen_px": 1000., "observed_median_y_screen_px": 1000. - error} for i, error in enumerate([100, 120, 900])]
        self.assertEqual(reference_offset(refs), 120.)
        refs[2]["observed_median_y_screen_px"] = -99999.
        self.assertEqual(reference_offset(refs), 120.)

    def test_bad_references_rejected(self):
        refs = [{"reference_id": str(i), "sample_count": 20, "target_y_screen_px": 1000., "observed_median_y_screen_px": 900.} for i in range(3)]
        with self.assertRaises(ValueError):
            reference_offset(refs[:2])
        refs[0]["sample_count"] = 14
        with self.assertRaises(ValueError):
            reference_offset(refs)
        refs[0]["sample_count"] = 20
        refs[0]["observed_median_y_screen_px"] = math.nan
        with self.assertRaises(ValueError):
            reference_offset(refs)

    def test_interior_pitch_not_nominal_height(self):
        observations = [(133 * k + 11, k - 1) for k in range(1, 48)] + [(133 * k + 12, k) for k in range(1, 48)]
        self.assertEqual(infer_pitch(observations, 48, 137), (133, 12))

    def test_ambiguous_geometry_fails(self):
        with self.assertRaises(ValueError):
            infer_pitch([(200, 1), (400, 2)], 48, 137)

    def test_complete_replay_geometry(self):
        report = analyze_records(fixture())
        self.assertEqual(report["geometry"]["content_height_px"], 6340)
        self.assertEqual(report["reproduced_measured_base_and_effective_assignments"], 28)
        for variant in report["variants"].values():
            self.assertEqual(variant["all"]["exact_line_pct"], 100.)
        self.assertEqual(report["variants"]["base"], report["variants"]["offset_only"])

    def test_mismatch_refuses_scoring(self):
        records = fixture()
        row = next(r for r in records if r.get("trial_state") == "MEASURE")
        row["base_target_line"] += 1
        with self.assertRaisesRegex(ValueError, "Geometry mismatch"):
            analyze_records(records)

    def test_missing_scroll_witness_fails(self):
        records = [r for r in fixture() if r.get("expected_checkpoint") != "localization_7"]
        with self.assertRaisesRegex(ValueError, "No bottom-scroll witness"):
            infer_geometry(records)

    def test_actual_content_bottom_clips_last_line(self):
        g = Geometry(133, 12, 48, 6340, 259, 2205, 45)
        self.assertEqual(g.line_at(2159.99, -4180), 47)
        self.assertEqual(g.line_at(2160., -4180), -1)
        self.assertEqual(g.line_at(258.9, -56), -1)
        self.assertEqual(g.line_at(2205., -1000), -1)

    def test_invalid_denominator_and_uncensored_tail(self):
        g = Geometry(133, 12, 48, 6340, 259, 2205, 45)
        samples = [{"expected_line": 5, "text_top_screen_px": -296}] * 2
        result = summarize(samples, [g.line_center(5) - 296, 5000.], g)
        self.assertEqual(result["valid_pct"], 50.)
        self.assertEqual(result["exact_line_pct"], 50.)
        self.assertEqual(result["valid_only_p95_absolute_line_error"], 0.)
        self.assertGreater(result["all_sample_center_error_p95_lines"], 10.)

    def test_analysis_does_not_mutate_records(self):
        records = fixture()
        before = copy.deepcopy(records)
        analyze_records(records)
        self.assertEqual(records, before)

    def test_float32_matches_android_conversion(self):
        self.assertEqual(float32(1000.00001), 1000.)


if __name__ == "__main__":
    unittest.main()
