import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from analyze_reading_measurement import FixationDetector, analyze, replay_paths, summarize


def sample(t, x=10, y=10, word=0, line=0, **extra):
    return dict(type='sample', capture_ms=t, raw_x=x, raw_y=y, word_start=word * 5,
                word_end=word * 5 + 4, line=line, measurement_valid=True, geometry_id=1,
                reason='coordinate', **extra)


def fixation(word, start, duration=120, segment=0, line=0, **extra):
    return dict(word_start=word * 5 if word is not None else None,
                word_end=word * 5 + 4 if word is not None else None,
                start_ms=start, end_ms=start + duration, duration_ms=duration,
                segment=segment, line=line, left_censored=False, right_censored=False, **extra)


WORDS = [[i * 5, i * 5 + 4] for i in range(20)]


class DetectorTests(unittest.TestCase):
    def test_same_line_multiple_fixations_not_one_line_visit(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120):
            detector.add(sample(t), 20)
        for t in (160, 200, 240, 280):
            detector.add(sample(t, x=100, word=1), 20)
        detector.boundary()
        self.assertEqual([120, 120], [f['duration_ms'] for f in detector.fixations])
        self.assertTrue(detector.fixations[0]['left_censored'])
        self.assertFalse(detector.fixations[0]['right_censored'])
        self.assertTrue(detector.fixations[1]['right_censored'])

    def test_no_bridging_missing_gaze(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120, 1000, 1040, 1080, 1120):
            detector.add(sample(t), 20)
        detector.boundary()
        self.assertEqual([120, 120], [f['duration_ms'] for f in detector.fixations])
        self.assertNotEqual(detector.fixations[0]['segment'], detector.fixations[1]['segment'])
        self.assertEqual('sample_gap', detector.segment_boundaries[0]['reason'])
        self.assertEqual(880, detector.segment_boundaries[0]['detail']['gap_ms'])

    def test_noise_is_not_a_fixation(self):
        detector = FixationDetector()
        for t in range(0, 500, 40):
            detector.add(sample(t, x=t), 20)
        detector.boundary()
        self.assertEqual([], detector.fixations)

    def test_word_ambiguity_retained(self):
        detector = FixationDetector()
        for t, word in zip((0, 40, 80, 120), (0, 0, 1, 1)):
            detector.add(sample(t, word=word), 20)
        detector.boundary()
        self.assertIsNone(detector.fixations[0]['word_start'])
        self.assertEqual(0, detector.fixations[0]['line'])
        self.assertEqual(1.0, detector.fixations[0]['line_sample_agreement'])

    def test_line_assignment_independent_and_disagreement_retained(self):
        detector = FixationDetector()
        for t, line in zip((0, 40, 80, 120), (2, 2, 3, 3)):
            detector.add(sample(t, line=line), 20)
        detector.boundary()
        self.assertEqual(0.5, detector.fixations[0]['line_sample_agreement'])
        self.assertIsNone(detector.fixations[0]['line'])

    def test_duplicate_time_cannot_extend_fixation(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 80, 120):
            detector.add(sample(t), 20)
        detector.boundary()
        self.assertEqual([], detector.fixations)

    def test_capture_order_is_preserved_across_boundaries(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120, 80, 40, 80, 120, 160):
            detector.add(sample(t), 20)
        detector.boundary()
        self.assertEqual([(0, 120)], [(f['start_ms'], f['end_ms']) for f in detector.fixations])
        self.assertEqual(5, detector.accepted_samples)
        self.assertEqual(4, detector.rejected_nonmonotonic_samples)


    def test_fixation_retains_geometry_and_trigger_delivery_availability(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120):
            detector.add(sample(t, delivery_ms=t + 30, geometry_snapshot_id='geometry:1'), 20)
        detector.add(sample(160, x=100, word=1, delivery_ms=210,
                            geometry_snapshot_id='geometry:1'), 20)
        event = detector.fixations[0]
        self.assertEqual(120, event['end_ms'])
        self.assertEqual(210, event['evidence_available_ms'])
        self.assertEqual('trigger_observation_delivery', event['evidence_time_basis'])
        self.assertEqual(1, event['geometry_id'])
        self.assertEqual('geometry:1', event['geometry_snapshot_id'])

    def test_absent_boundary_time_is_not_invented_from_fixation_end(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120):
            detector.add(sample(t), 20)
        detector.boundary()
        self.assertIsNone(detector.fixations[0]['evidence_available_ms'])
        self.assertEqual('unavailable', detector.fixations[0]['evidence_time_basis'])

    def test_missing_delivery_is_marked_as_capture_lower_bound(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120):
            detector.add(sample(t), 20)
        detector.add(sample(160, x=100, word=1), 20)
        self.assertEqual(160, detector.fixations[0]['evidence_available_ms'])
        self.assertEqual('trigger_capture_lower_bound', detector.fixations[0]['evidence_time_basis'])


class MetricTests(unittest.TestCase):
    def test_line_path_survives_word_ambiguity_and_preserves_order(self):
        events = [fixation(0, 0, line=2), fixation(None, 160, line=3),
                  fixation(2, 320, line=5), fixation(1, 480, line=3)]
        paths = replay_paths(events, WORDS)
        self.assertEqual([2, 3, 5, 3], [v['line'] for v in paths['line_visits']])
        self.assertEqual([1, 2, -2], [t['delta_lines'] for t in paths['line_transitions']])
        self.assertEqual(['forward', 'forward', 'backward'],
                         [t['direction'] for t in paths['line_transitions']])
        self.assertEqual([-1], [t['delta_words'] for t in paths['word_fixation_transitions']])
        self.assertEqual(3, paths['coverage']['word_assigned_fixations'])
        self.assertEqual(4, paths['coverage']['line_assigned_fixations'])
        self.assertEqual(3, paths['coverage']['eligible_line_fixation_pairs'])
        self.assertEqual(['unassigned_word'], paths['sequence_breaks'][0]['word_reasons'])
        self.assertEqual([], paths['sequence_breaks'][0]['line_reasons'])

    def test_fixation_word_transitions_include_same_word(self):
        events = [fixation(1, 0), fixation(1, 160), fixation(3, 320)]
        paths = replay_paths(events, WORDS)
        self.assertEqual([0, 2], [t['delta_words'] for t in paths['word_fixation_transitions']])
        self.assertEqual([1, 3], [v['word'] for v in paths['ordered_word_visits']])
        self.assertEqual([2], [t['delta_words'] for t in paths['word_visit_transitions']])
        self.assertEqual((0, 280, 320, 440),
                         tuple(paths['word_visit_transitions'][0][key] for key in
                               ('from_start_ms', 'from_end_ms', 'to_start_ms', 'to_end_ms')))
        self.assertEqual([0], [v['line'] for v in paths['line_visits']])
        metrics, _ = summarize(events, WORDS)
        self.assertEqual(2, paths['coverage']['eligible_word_fixation_pairs'])
        self.assertEqual(1, metrics['eligible_word_transitions'])

    def test_word_visit_edges_record_revisits_and_final_visit_bounds(self):
        events = [fixation(1, 0), fixation(2, 160), fixation(1, 320), fixation(1, 480)]
        paths = replay_paths(events, WORDS)
        self.assertEqual([1, -1], [t['delta_words'] for t in paths['word_visit_transitions']])
        self.assertEqual([False, False, True],
                         [v['revisit_within_segment'] for v in paths['ordered_word_visits']])
        self.assertEqual(600, paths['word_visit_transitions'][-1]['to_end_ms'])
        self.assertEqual(3, len(paths['word_fixation_transitions']))

    def test_line_visit_edge_timestamps_include_all_destination_fixations(self):
        events = [fixation(0, 0, line=0), fixation(1, 160, line=2),
                  fixation(2, 320, line=2)]
        paths = replay_paths(events, WORDS)
        edge = paths['line_transitions'][0]
        self.assertEqual(2, edge['delta_lines'])
        self.assertEqual((0, 120, 160, 440),
                         tuple(edge[key] for key in
                               ('from_start_ms', 'from_end_ms', 'to_start_ms', 'to_end_ms')))

    def test_unclassified_gap_breaks_both_paths(self):
        events = [fixation(0, 0, line=0), fixation(1, 1000, line=2)]
        paths = replay_paths(events, WORDS)
        self.assertEqual([], paths['line_transitions'])
        self.assertEqual([], paths['word_fixation_transitions'])
        self.assertEqual(['unclassified_fixation_gap'], paths['sequence_breaks'][0]['line_reasons'])
        self.assertEqual(2, len(paths['line_visits']))

    def test_segment_break_provenance_and_unassigned_line(self):
        events = [fixation(0, 0, line=0), fixation(1, 160, segment=2, line=None),
                  fixation(2, 320, segment=2, line=1)]
        boundaries = [dict(from_segment=0, reason='geometry_change'),
                      dict(from_segment=1, reason='invalid_sample')]
        paths = replay_paths(events, WORDS, boundaries)
        self.assertEqual(['geometry_change', 'invalid_sample', 'unassigned_line'],
                         paths['sequence_breaks'][0]['line_reasons'])
        self.assertEqual(['unassigned_line'], paths['sequence_breaks'][1]['line_reasons'])
        self.assertEqual([], paths['line_transitions'])
        self.assertEqual(1, paths['coverage']['ambiguous_line_fixations'])
        self.assertEqual({'geometry_change': 1, 'invalid_sample': 1, 'unassigned_line': 2},
                         paths['coverage']['line_sequence_breaks_by_reason'])
        self.assertEqual(2, paths['coverage']['line_sequence_breaks'])
        self.assertIsNone(replay_paths([], WORDS)['coverage']['line_assignment_fraction'])

    def test_unclassified_interval_cannot_create_skipping_or_regression(self):
        for words in [(0, 5), (5, 0)]:
            metrics, _ = summarize([fixation(words[0], 0), fixation(words[1], 1000)], WORDS)
            self.assertEqual(1, metrics['unclassified_sequence_gap_breaks'])
            self.assertEqual(0, metrics['eligible_word_transitions'])
            self.assertIsNone(metrics['first_pass_skipping_rate'])

    def test_unclassified_interval_cannot_merge_same_word_visits(self):
        metrics, visits = summarize([fixation(0, 0), fixation(0, 1000)], WORDS)
        self.assertEqual(2, len(visits))
        self.assertNotEqual(visits[0]['group'], visits[1]['group'])
        self.assertEqual(0, metrics['first_pass_eligible_visits'])

    def test_multiple_fixations_on_word_are_one_visit(self):
        events = [fixation(0, 0), fixation(1, 160, 120), fixation(1, 320, 160), fixation(2, 520)]
        metrics, visits = summarize(events, WORDS)
        self.assertEqual(280, metrics['mean_observed_first_pass_gaze_duration_ms'])
        self.assertEqual(3, len(visits))
        self.assertEqual(2, metrics['eligible_word_transitions'])

    def test_regressions_use_document_order_including_line_wrap(self):
        # A line wrap has smaller screen x but a larger document word ordinal.
        metrics, _ = summarize([fixation(3, 0), fixation(4, 160), fixation(2, 320)], WORDS)
        self.assertEqual(1, metrics['regressions'])
        self.assertEqual(0.5, metrics['regression_probability'])

    def test_revisits_and_skipping_have_explicit_denominators(self):
        metrics, _ = summarize([fixation(0, 0), fixation(3, 160), fixation(0, 320)], WORDS)
        self.assertEqual(2, metrics['first_pass_skipped_words'])
        self.assertEqual(3, metrics['first_pass_eligible_words'])
        self.assertAlmostEqual(1 / 3, metrics['observed_rereading_proportion'])

    def test_gaps_and_ambiguous_fixations_break_transitions(self):
        for events in ([fixation(5, 0), fixation(1, 160, segment=1)],
                       [fixation(5, 0), fixation(None, 160), fixation(1, 320)]):
            metrics, _ = summarize(events, WORDS)
            self.assertEqual(0, metrics['eligible_word_transitions'])
            self.assertIsNone(metrics['regression_probability'])

    def test_final_visit_excluded_from_first_pass_duration(self):
        metrics, visits = summarize([fixation(0, 0), fixation(1, 160)], WORDS)
        self.assertIsNone(metrics['mean_observed_first_pass_gaze_duration_ms'])
        self.assertTrue(visits[-1]['right_censored'])

    def test_visit_censoring_includes_path_edges_and_assignment_breaks(self):
        paths = replay_paths([fixation(0, 0, line=0), fixation(1, 160, line=1),
                              fixation(2, 320, line=2)], WORDS)
        for name, key in (('word', 'ordered_word_visits'), ('line', 'line_visits')):
            self.assertEqual([(True, False), (False, False), (False, True)],
                             [(v['left_censored'], v['right_censored']) for v in paths[key]])
            self.assertEqual(1, paths['coverage'][name + '_complete_visits'])
            self.assertAlmostEqual(2 / 3, paths['coverage'][name + '_censored_visit_fraction'])
        paths = replay_paths([fixation(0, 0, line=0), fixation(None, 160, line=None),
                              fixation(1, 320, line=1)], WORDS)
        for key in ('ordered_word_visits', 'line_visits'):
            self.assertTrue(all(v['left_censored'] and v['right_censored'] for v in paths[key]))


class RecordingTests(unittest.TestCase):
    def records(self):
        rows = [dict(type='session_start', schema_version=1, session_id='synthetic', word_ranges=WORDS),
                dict(type='geometry', geometry_id=1, line_height_px=40)]
        rows += [sample(t) for t in (0, 40, 80, 120)]
        rows += [dict(type='session_end', complete=True, dropped_records=0, sample_records=4)]
        return rows

    def test_cli_writes_raw_measurement_without_retired_classifier_output(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent) as folder:
            root = Path(folder)
            source, output = root / 'input.jsonl', root / 'analysis.json'
            source.write_text('\n'.join(json.dumps(row) for row in self.records()) + '\n',
                              encoding='utf-8')
            process = subprocess.run([sys.executable, str(Path(__file__).with_name('analyze_reading_measurement.py')),
                                      str(source), '--output', str(output)],
                                     capture_output=True, text=True, check=False)
            self.assertEqual(0, process.returncode, process.stderr)
            result = json.loads(output.read_text(encoding='utf-8'))
            self.assertEqual(6, result['analysis_version'])
            self.assertNotIn('patterns', result)
            self.assertEqual(1, result['metrics']['detected_fixations'])
            self.assertEqual(120, result['coverage']['observed_fixation_span_ms'])

    def test_raw_stream_used_not_display_coordinates(self):
        rows = self.records()
        for i, row in enumerate(rows):
            if row['type'] == 'sample':
                row.update(display_x=i * 500, display_y=i * 500)
        result = analyze(rows)
        self.assertEqual(1, result['metrics']['detected_fixations'])
        self.assertEqual(120, result['fixations'][0]['duration_ms'])
        self.assertEqual(0, result['metrics']['complete_fixations'])

    def test_incomplete_or_dropped_recording_rejected(self):
        with self.assertRaises(ValueError):
            analyze(self.records()[:-1])
        rows = self.records()
        rows[-1]['dropped_records'] = 1
        with self.assertRaises(ValueError):
            analyze(rows)
        self.assertFalse(analyze(rows, allow_incomplete=True)['source_complete'])

    def test_scroll_geometry_change_splits_candidate(self):
        rows = self.records()
        rows.insert(4, dict(type='geometry', geometry_id=2, line_height_px=40))
        self.assertEqual(0, analyze(rows)['metrics']['detected_fixations'])

    def test_nonfinite_sample_breaks_candidate(self):
        rows = self.records()
        rows[4]['raw_x'] = float('nan')
        self.assertEqual(0, analyze(rows)['metrics']['detected_fixations'])

    def test_geometry_and_invalid_sample_provenance(self):
        rows = self.records()[:2]
        rows += [sample(t, line=0) for t in (0, 40, 80, 120)]
        rows += [dict(type='geometry', geometry_id=2, line_height_px=40)]
        rows += [dict(sample(t, word=1, line=1), geometry_id=2) for t in (160, 200, 240, 280)]
        rows += [dict(sample(300), measurement_valid=False)]
        rows += [dict(sample(t, word=2, line=2), geometry_id=2) for t in (340, 380, 420, 460)]
        rows += [dict(type='session_end', elapsed_ms=500, complete=True,
                     dropped_records=0, sample_records=13)]
        result = analyze(rows)
        self.assertEqual(6, result['analysis_version'])
        self.assertEqual([0, 1, 2], [v['line'] for v in result['line_visits']])
        self.assertEqual([], result['line_transitions'])
        self.assertEqual(['geometry_change'], result['sequence_breaks'][0]['line_reasons'])
        self.assertEqual(['invalid_sample'], result['sequence_breaks'][1]['line_reasons'])
        self.assertEqual(3, result['coverage']['line_assigned_fixations'])
        self.assertEqual(0, result['metrics']['eligible_word_transitions'])
        self.assertEqual(500, result['segment_boundaries'][-1]['at_ms'])

    def test_geometry_snapshot_provenance_survives_reused_logger_id(self):
        rows = self.records()[:2]
        rows[1].update(text_origin_x=10, text_origin_y=20, lines=[[0, 100, 0, 200, 0, 40]])
        rows += [sample(t) for t in (0, 40, 80, 120)]
        rows += [dict(type='geometry', geometry_id=1, elapsed_ms=150,
                      line_height_px=40, text_origin_x=100, text_origin_y=20,
                      lines=[[0, 100, 0, 100, 0, 40]])]
        rows += [sample(t, x=110, word=1) for t in (160, 200, 240, 280)]
        rows += [dict(type='session_end', elapsed_ms=300, complete=True,
                      dropped_records=0, sample_records=8)]
        result = analyze(rows)
        self.assertEqual(['geometry:1', 'geometry:2'],
                         [f['geometry_snapshot_id'] for f in result['fixations']])
        self.assertEqual([1, 1], [f['geometry_id'] for f in result['fixations']])
        self.assertEqual(10, result['geometry_snapshots']['geometry:1']['text_origin_x'])
        self.assertEqual(100, result['geometry_snapshots']['geometry:2']['text_origin_x'])
        self.assertEqual([], result['line_transitions'])
        self.assertNotIn('patterns', result)

    def test_usable_sample_count_excludes_nonmonotonic_samples(self):
        rows = self.records()[:2]
        times = (0, 40, 80, 120, 80, 40, 80, 120, 160)
        rows += [sample(t) for t in times]
        rows += [dict(type='session_end', elapsed_ms=200, complete=True,
                      dropped_records=0, sample_records=len(times))]
        result = analyze(rows)
        self.assertEqual(9, result['coordinate_geometry_eligible_sample_records'])
        self.assertEqual(5, result['valid_sample_records'])
        self.assertEqual(4, result['rejected_nonmonotonic_sample_records'])
        self.assertAlmostEqual(5 / 9, result['coverage']['accepted_sample_fraction'])
        self.assertEqual(120, result['coverage']['observed_fixation_span_ms'])
        self.assertEqual(160, result['coverage']['observation_span_ms'])
        self.assertEqual(0.75, result['coverage']['observed_fixation_span_fraction'])

    def test_invalid_observation_preserves_capture_order(self):
        rows = self.records()[:-1]
        rows += [dict(sample(280), measurement_valid=False)]
        rows += [sample(t) for t in (160, 200, 240, 320, 360, 400, 440)]
        rows += [dict(type='session_end', elapsed_ms=480, complete=True,
                      dropped_records=0, sample_records=12)]
        result = analyze(rows)
        self.assertEqual([(0, 120), (320, 440)],
                         [(f['start_ms'], f['end_ms']) for f in result['fixations']])
        self.assertEqual(8, result['valid_sample_records'])
        self.assertEqual(3, result['rejected_nonmonotonic_sample_records'])

    def test_terminal_boundary_counts_are_separate_from_path_link_breaks(self):
        rows = self.records()[:-1]
        rows += [dict(sample(160), measurement_valid=False)]
        rows += [dict(type='session_end', elapsed_ms=200, complete=True,
                      dropped_records=0, sample_records=5)]
        result = analyze(rows)
        self.assertEqual(1, result['segment_boundary_event_counts']['invalid_sample'])
        self.assertEqual(0, result['coverage']['word_sequence_breaks'])
        self.assertEqual(1, result['coverage']['word_right_censored_visits'])

    def test_coordinate_output_age_retains_missing_and_negative_times(self):
        rows = self.records()
        rows[2]['delivery_ms'] = 50
        rows[3]['delivery_ms'] = 70
        rows[5]['delivery_ms'] = 100
        age = analyze(rows)['coordinate_output_age']
        self.assertEqual(4, age['sample_denominator'])
        self.assertEqual(2, age['measured_samples'])
        self.assertEqual(1, age['missing_time_samples'])
        self.assertEqual(1, age['negative_age_samples'])
        self.assertEqual(40, age['median_ms'])
        self.assertEqual(50, age['p95_ms'])

    def test_nonfinite_detector_parameters_rejected(self):
        for key in ('dispersion_lines', 'min_ms', 'max_gap_ms', 'agreement'):
            with self.assertRaises(ValueError):
                analyze(self.records(), **{key: float('inf')})


if __name__ == '__main__':
    unittest.main()
