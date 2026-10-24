"""Replay schema-1 natural-reading logs. Standard library only; no trained RII.

Example: python tools/gaze/analyze_reading_measurement.py recording.jsonl --output analysis.json
All thresholds are provisional engineering parameters, retained in the output.
Analysis version 6 removes the retired pattern classifier and its output schema;
raw measurement, dispersion candidates, sequence events and coverage remain.
"""
import argparse
from collections import Counter
import json
import math
from pathlib import Path
import statistics

def finite_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def percentile95(values):
    return sorted(values)[math.ceil(0.95 * len(values)) - 1] if values else None


def dispersion(samples):
    return (max(s['raw_x'] for s in samples) - min(s['raw_x'] for s in samples)
            + max(s['raw_y'] for s in samples) - min(s['raw_y'] for s in samples))


class FixationDetector:
    """I-DT: expand a compact minimum-duration window until dispersion exceeds its limit."""
    def __init__(self, min_ms=100.0, min_samples=3, max_gap_ms=125.0, agreement=0.8):
        self.min_ms = min_ms
        self.min_samples = min_samples
        self.max_gap_ms = max_gap_ms
        self.agreement = agreement
        self.pending = []
        self.fixations = []
        self.segment_boundaries = []
        self.segment = 0
        self.left_censored = True
        self.last_time = None
        self.latest_capture_time = None
        self.accepted_samples = 0
        self.rejected_nonmonotonic_samples = 0

    def note_capture_time(self, timestamp):
        """Keep camera-time order across boundaries, including invalid observations."""
        if finite_number(timestamp):
            self.latest_capture_time = (timestamp if self.latest_capture_time is None else
                                        max(self.latest_capture_time, timestamp))

    def established(self):
        return (len(self.pending) >= self.min_samples and
                self.pending[-1]['capture_ms'] - self.pending[0]['capture_ms'] >= self.min_ms)

    def emit(self, right_censored, evidence_available_ms=None, evidence_time_basis=None):
        if not self.established():
            return
        samples = self.pending
        counts = Counter((s['word_start'], s['word_end']) for s in samples)
        word, count = counts.most_common(1)[0]
        assigned = word[0] >= 0 and word[1] > word[0] and count / len(samples) >= self.agreement
        line_counts = Counter(s.get('line') for s in samples)
        line, line_count = line_counts.most_common(1)[0]
        line_assigned = (isinstance(line, int) and line >= 0 and
                         line_count / len(samples) >= self.agreement)
        available = (evidence_available_ms if finite_number(evidence_available_ms)
                     and evidence_available_ms >= samples[-1]['capture_ms'] else None)
        self.fixations.append(dict(
            segment=self.segment, start_ms=samples[0]['capture_ms'], end_ms=samples[-1]['capture_ms'],
            duration_ms=samples[-1]['capture_ms'] - samples[0]['capture_ms'],
            sample_count=len(samples), x=statistics.mean(s['raw_x'] for s in samples),
            y=statistics.mean(s['raw_y'] for s in samples), dispersion_px=dispersion(samples),
            word_start=word[0] if assigned else None, word_end=word[1] if assigned else None,
            word_sample_agreement=count / len(samples), left_censored=self.left_censored,
            line=line if line_assigned else None,
            line_sample_agreement=line_count / len(samples),
            right_censored=right_censored,
            geometry_id=samples[0].get('geometry_id'),
            geometry_snapshot_id=samples[0].get('geometry_snapshot_id'),
            evidence_available_ms=available,
            evidence_time_basis=evidence_time_basis if available is not None else 'unavailable',
        ))

    @staticmethod
    def availability(sample):
        capture, delivery = sample.get('capture_ms'), sample.get('delivery_ms')
        if finite_number(delivery) and finite_number(capture) and delivery >= capture:
            return delivery, 'trigger_observation_delivery'
        if finite_number(capture):
            return capture, 'trigger_capture_lower_bound'
        return None, 'unavailable'

    def boundary(self, reason='unspecified', at_ms=None, detail=None,
                 evidence_available_ms=None, evidence_time_basis=None):
        if evidence_time_basis is None:
            evidence_available_ms = at_ms if finite_number(at_ms) else None
            evidence_time_basis = 'boundary_record_time' if evidence_available_ms is not None else 'unavailable'
        self.emit(right_censored=True, evidence_available_ms=evidence_available_ms,
                  evidence_time_basis=evidence_time_basis)
        self.segment_boundaries.append(dict(from_segment=self.segment,
                                            to_segment=self.segment + 1,
                                            reason=reason, at_ms=at_ms, detail=detail,
                                            evidence_available_ms=evidence_available_ms,
                                            evidence_time_basis=evidence_time_basis))
        self.pending = []
        self.last_time = None
        self.segment += 1
        self.left_censored = True

    def add(self, sample, threshold_px):
        timestamp = sample['capture_ms']
        available, time_basis = self.availability(sample)
        if self.latest_capture_time is not None and timestamp <= self.latest_capture_time:
            self.rejected_nonmonotonic_samples += 1
            self.boundary('non_monotonic_capture', timestamp,
                          dict(latest_capture_ms=self.latest_capture_time), available, time_basis)
            return False
        self.note_capture_time(timestamp)
        if self.last_time is not None:
            if timestamp - self.last_time > self.max_gap_ms:
                self.boundary('sample_gap', timestamp,
                              dict(gap_ms=timestamp - self.last_time), available, time_basis)
        self.last_time = timestamp
        if self.pending and dispersion(self.pending + [sample]) > threshold_px:
            if self.established():
                self.emit(right_censored=False, evidence_available_ms=available,
                          evidence_time_basis=time_basis)
                self.pending = []
                self.left_censored = False
            else:
                while self.pending and dispersion(self.pending + [sample]) > threshold_px:
                    self.pending.pop(0)
                    self.left_censored = False
        self.pending.append(sample)
        self.accepted_samples += 1
        return True


def summarize(fixations, word_ranges, max_transition_gap_ms=125.0):
    """Only contiguous, unambiguous word sequences contribute transition/visit measures."""
    ordinals = {tuple(word): i for i, word in enumerate(word_ranges)}
    visits = []
    previous_segment = None
    previous_end = None
    sequence_gap_breaks = 0
    group = 0
    for fixation in fixations:
        word = ordinals.get((fixation['word_start'], fixation['word_end']))
        if fixation['segment'] != previous_segment:
            group += 1
        elif previous_end is not None and fixation['start_ms'] - previous_end > max_transition_gap_ms:
            # Continuous camera samples do not guarantee continuous usable reading evidence.
            # Discarded short/noisy candidates can hide intermediate fixations or visits.
            group += 1
            sequence_gap_breaks += 1
        previous_segment = fixation['segment']
        previous_end = fixation['end_ms']
        if word is None:
            group += 1
            continue
        if visits and visits[-1]['group'] == group and visits[-1]['word'] == word:
            visit = visits[-1]
            visit['duration_ms'] += fixation['duration_ms']
            visit['end_ms'] = fixation['end_ms']
            visit['right_censored'] = fixation['right_censored']
        else:
            if visits and visits[-1]['group'] != group:
                visits[-1]['right_censored'] = True
            visits.append(dict(group=group, word=word, duration_ms=fixation['duration_ms'],
                               start_ms=fixation['start_ms'], end_ms=fixation['end_ms'],
                               left_censored=fixation['left_censored'] or not visits or visits[-1]['group'] != group,
                               right_censored=fixation['right_censored']))

    if visits:
        visits[-1]['right_censored'] = True

    transitions = regressions = skipped = eligible = 0
    reread_ms = total_ms = 0.0
    first_pass = []
    seen = set()
    frontier = previous_word = None
    previous_group = None
    for visit in visits:
        if visit['group'] != previous_group:
            seen = set()
            frontier = previous_word = None
        previous_group = visit['group']
        word = visit['word']
        visit['revisit_within_segment'] = word in seen
        visit['first_pass_eligible'] = (previous_word is not None and word > frontier and
                                        not visit['left_censored'] and not visit['right_censored'])
        if visit['first_pass_eligible']:
            first_pass.append(visit['duration_ms'])
        if previous_word is not None:
            transitions += 1
            regressions += word < previous_word
            # Count initial forward coverage only from the known frontier, not after gaps
            # or forward movements from a rereading region. The landing word is included.
            if word > frontier and previous_word == frontier:
                skipped += word - previous_word - 1
                eligible += word - previous_word
        total_ms += visit['duration_ms']
        if word in seen:
            reread_ms += visit['duration_ms']
        seen.add(word)
        frontier = word if frontier is None else max(frontier, word)
        previous_word = word

    durations = [f['duration_ms'] for f in fixations if not f['left_censored'] and not f['right_censored']]
    ratio = lambda a, b: a / b if b else None
    return dict(
        detected_fixations=len(fixations), complete_fixations=len(durations),
        unclassified_sequence_gap_breaks=sequence_gap_breaks,
        ambiguous_word_fixations=sum(f['word_start'] is None for f in fixations),
        mean_fixation_duration_ms=statistics.mean(durations) if durations else None,
        median_fixation_duration_ms=statistics.median(durations) if durations else None,
        first_pass_eligible_visits=len(first_pass),
        mean_observed_first_pass_gaze_duration_ms=statistics.mean(first_pass) if first_pass else None,
        observed_word_fixation_time_ms=total_ms, observed_revisit_fixation_time_ms=reread_ms,
        observed_rereading_proportion=ratio(reread_ms, total_ms),
        eligible_word_transitions=transitions, regressions=regressions,
        regression_probability=ratio(regressions, transitions),
        first_pass_skipped_words=skipped, first_pass_eligible_words=eligible,
        first_pass_skipping_rate=ratio(skipped, eligible),
    ), visits


def replay_paths(fixations, word_ranges, segment_boundaries=(), max_transition_gap_ms=125.0):
    """Descriptive fixation and visit order; never infer unobserved intervening text."""
    ordinals = {tuple(word): i for i, word in enumerate(word_ranges)}
    word_visits = []
    line_visits = []
    word_transitions = []
    word_visit_transitions = []
    line_transitions = []
    sequence_breaks = []
    seen_words = set()
    boundary_by_segment = {b['from_segment']: b for b in segment_boundaries}
    previous = None
    word_group = line_group = 0
    assigned_word = assigned_line = 0
    eligible_word_pairs = eligible_line_pairs = 0

    for index, fixation in enumerate(fixations):
        word = ordinals.get((fixation['word_start'], fixation['word_end']))
        line = fixation.get('line')
        if not isinstance(line, int) or line < 0:
            line = None
        assigned_word += word is not None
        assigned_line += line is not None
        reasons = []
        if previous is not None:
            if fixation['segment'] != previous['segment']:
                reasons.extend(b['reason'] for segment in range(previous['segment'], fixation['segment'])
                               if (b := boundary_by_segment.get(segment)) is not None)
                if not reasons:
                    reasons.append('segment_change_unknown')
            if fixation['start_ms'] - previous['end_ms'] > max_transition_gap_ms:
                reasons.append('unclassified_fixation_gap')
            word_reasons = reasons + (['unassigned_word'] if previous_word is None or word is None else [])
            line_reasons = reasons + (['unassigned_line'] if previous_line is None or line is None else [])
            if word_reasons:
                word_group += 1
                seen_words = set()
                if word_visits:
                    word_visits[-1]['right_censored'] = True
            else:
                eligible_word_pairs += 1
                word_transitions.append(dict(from_fixation_index=index - 1, to_fixation_index=index,
                                             from_word=previous_word, to_word=word,
                                             delta_words=word - previous_word))
            if line_reasons:
                line_group += 1
                if line_visits:
                    line_visits[-1]['right_censored'] = True
            else:
                eligible_line_pairs += 1
            if word_reasons or line_reasons:
                sequence_breaks.append(dict(from_fixation_index=index - 1, to_fixation_index=index,
                                            gap_ms=fixation['start_ms'] - previous['end_ms'],
                                            word_reasons=word_reasons, line_reasons=line_reasons))
        if word is not None:
            if word_visits and word_visits[-1]['group'] == word_group and word_visits[-1]['word'] == word:
                visit = word_visits[-1]
                visit['fixation_end_index'] = index
                visit['end_ms'] = fixation['end_ms']
                visit['duration_ms'] += fixation['duration_ms']
                visit['right_censored'] = fixation['right_censored']
                if word_visit_transitions and word_visit_transitions[-1]['to_visit_index'] == len(word_visits) - 1:
                    word_visit_transitions[-1]['to_end_ms'] = fixation['end_ms']
            else:
                if word_visits and word_visits[-1]['group'] == word_group:
                    prior = word_visits[-1]
                    word_visit_transitions.append(dict(from_visit_index=len(word_visits) - 1,
                                                       to_visit_index=len(word_visits),
                                                       from_word=prior['word'], to_word=word,
                                                       delta_words=word - prior['word'],
                                                       from_start_ms=prior['start_ms'],
                                                       from_end_ms=prior['end_ms'],
                                                       to_start_ms=fixation['start_ms'],
                                                       to_end_ms=fixation['end_ms']))
                word_visits.append(dict(group=word_group, word=word, fixation_start_index=index,
                                        fixation_end_index=index, start_ms=fixation['start_ms'],
                                        end_ms=fixation['end_ms'], duration_ms=fixation['duration_ms'],
                                        left_censored=fixation['left_censored'] or not word_visits or
                                        word_visits[-1]['group'] != word_group,
                                        right_censored=fixation['right_censored'],
                                        revisit_within_segment=word in seen_words))
            seen_words.add(word)
        if line is not None:
            if line_visits and line_visits[-1]['group'] == line_group and line_visits[-1]['line'] == line:
                visit = line_visits[-1]
                visit['fixation_end_index'] = index
                visit['end_ms'] = fixation['end_ms']
                visit['duration_ms'] += fixation['duration_ms']
                visit['right_censored'] = fixation['right_censored']
                if line_transitions and line_transitions[-1]['to_visit_index'] == len(line_visits) - 1:
                    line_transitions[-1]['to_end_ms'] = fixation['end_ms']
            else:
                if line_visits and line_visits[-1]['group'] == line_group:
                    prior = line_visits[-1]
                    delta = line - prior['line']
                    line_transitions.append(dict(from_visit_index=len(line_visits) - 1,
                                                 to_visit_index=len(line_visits),
                                                 from_line=prior['line'], to_line=line,
                                                 delta_lines=delta,
                                                 direction='forward' if delta > 0 else 'backward',
                                                 from_start_ms=prior['start_ms'],
                                                 from_end_ms=prior['end_ms'],
                                                 to_start_ms=fixation['start_ms'],
                                                 to_end_ms=fixation['end_ms']))
                line_visits.append(dict(group=line_group, line=line, fixation_start_index=index,
                                        fixation_end_index=index, start_ms=fixation['start_ms'],
                                        end_ms=fixation['end_ms'], duration_ms=fixation['duration_ms'],
                                        left_censored=fixation['left_censored'] or not line_visits or
                                        line_visits[-1]['group'] != line_group,
                                        right_censored=fixation['right_censored']))
        previous = fixation
        previous_word = word
        previous_line = line

    count = len(fixations)
    pair_count = max(0, count - 1)
    ratio = lambda numerator, denominator: numerator / denominator if denominator else None
    censor_coverage = {}
    for name, visits in (('word', word_visits), ('line', line_visits)):
        if visits:
            visits[-1]['right_censored'] = True
        censored = sum(v['left_censored'] or v['right_censored'] for v in visits)
        censor_coverage.update({
            f'{name}_complete_visits': len(visits) - censored,
            f'{name}_left_censored_visits': sum(v['left_censored'] for v in visits),
            f'{name}_right_censored_visits': sum(v['right_censored'] for v in visits),
            f'{name}_censored_visit_fraction': ratio(censored, len(visits)),
        })
    word_break_reasons = Counter(reason for item in sequence_breaks
                                 for reason in set(item['word_reasons']))
    line_break_reasons = Counter(reason for item in sequence_breaks
                                 for reason in set(item['line_reasons']))
    return dict(word_fixation_transitions=word_transitions, ordered_word_visits=word_visits,
                word_visit_transitions=word_visit_transitions,
                line_visits=line_visits, line_transitions=line_transitions,
                sequence_breaks=sequence_breaks,
                coverage=dict(fixation_denominator=count, adjacent_fixation_pair_denominator=pair_count,
                              word_assigned_fixations=assigned_word,
                              ambiguous_word_fixations=count - assigned_word,
                              word_assignment_fraction=ratio(assigned_word, count),
                              line_assigned_fixations=assigned_line,
                              ambiguous_line_fixations=count - assigned_line,
                              line_assignment_fraction=ratio(assigned_line, count),
                              eligible_word_fixation_pairs=eligible_word_pairs,
                              eligible_word_pair_fraction=ratio(eligible_word_pairs, pair_count),
                              eligible_line_fixation_pairs=eligible_line_pairs,
                              eligible_line_pair_fraction=ratio(eligible_line_pairs, pair_count),
                              ordered_word_visits=len(word_visits), line_visits=len(line_visits),
                              word_fixation_transitions=len(word_transitions),
                              word_visit_transitions=len(word_visit_transitions),
                              word_sequence_breaks=sum(bool(item['word_reasons']) for item in sequence_breaks),
                              line_sequence_breaks=sum(bool(item['line_reasons']) for item in sequence_breaks),
                              word_sequence_breaks_by_reason=dict(word_break_reasons),
                              line_sequence_breaks_by_reason=dict(line_break_reasons),
                              line_transitions=len(line_transitions), **censor_coverage))


def analyze(records, dispersion_lines=0.5, min_ms=100.0, min_samples=3,
            max_gap_ms=125.0, agreement=0.8, allow_incomplete=False):
    if not (all(finite_number(v) for v in (dispersion_lines, min_ms, max_gap_ms, agreement))
            and dispersion_lines > 0 and min_ms > 0 and max_gap_ms > 0
            and min_samples >= 2 and 0.5 < agreement <= 1):
        raise ValueError('Invalid detector parameters')
    if not records or records[0].get('type') != 'session_start' or records[0].get('schema_version') != 1:
        raise ValueError('Expected a schema-1 reading measurement session')
    metadata = records[0]
    if sum(r.get('type') == 'session_start' for r in records) != 1:
        raise ValueError('Analyze one session at a time')
    sample_count = sum(r.get('type') == 'sample' for r in records)
    end = records[-1]
    complete = (end.get('type') == 'session_end' and end.get('complete') is True
                and end.get('dropped_records') == 0 and end.get('sample_records') == sample_count)
    if not complete and not allow_incomplete:
        raise ValueError('Missing/incomplete footer or record loss; use --allow-incomplete for diagnostics only')
    detector = FixationDetector(min_ms, min_samples, max_gap_ms, agreement)
    geometry = None
    geometry_snapshot_id = None
    geometry_snapshots = {}
    coordinate_eligible_count = 0
    reasons = Counter()
    exclusions = Counter()
    intervals = []
    last_capture = None
    first_capture = None
    coordinate_ages = []
    coordinate_age_missing = coordinate_age_negative = coordinate_age_denominator = 0
    for row in records[1:]:
        if row['type'] == 'geometry':
            detector.boundary('geometry_change', row.get('elapsed_ms'),
                              dict(geometry_id=row.get('geometry_id')))
            geometry = row
            geometry_snapshot_id = f'geometry:{len(geometry_snapshots) + 1}'
            geometry_snapshots[geometry_snapshot_id] = dict(row)
        elif row['type'] == 'sample':
            reasons[row['reason']] += 1
            if row.get('measurement_exclusion'):
                exclusions[row['measurement_exclusion']] += 1
            capture = row.get('capture_ms')
            if finite_number(capture):
                first_capture = capture if first_capture is None else min(first_capture, capture)
                if last_capture is not None and capture > last_capture:
                    intervals.append(capture - last_capture)
                last_capture = capture if last_capture is None else max(capture, last_capture)
            if row['reason'] == 'coordinate':
                coordinate_age_denominator += 1
                delivery = row.get('delivery_ms')
                if not finite_number(capture) or not finite_number(delivery):
                    coordinate_age_missing += 1
                elif delivery < capture:
                    coordinate_age_negative += 1
                else:
                    coordinate_ages.append(delivery - capture)
            finite = all(finite_number(row.get(k)) for k in ('capture_ms', 'raw_x', 'raw_y'))
            if (not row.get('measurement_valid') or not finite or geometry is None
                    or row.get('geometry_id') != geometry['geometry_id']
                    or geometry['line_height_px'] <= 0):
                invalid_reasons = []
                if not row.get('measurement_valid'):
                    invalid_reasons.append('measurement_invalid')
                if not finite:
                    invalid_reasons.append('nonfinite_or_missing_coordinates')
                if geometry is None or row.get('geometry_id') != geometry['geometry_id']:
                    invalid_reasons.append('geometry_mismatch_or_missing')
                if geometry is not None and geometry['line_height_px'] <= 0:
                    invalid_reasons.append('invalid_line_height')
                available, time_basis = detector.availability(row)
                detector.boundary('invalid_sample', capture,
                                  dict(causes=invalid_reasons,
                                       measurement_exclusion=row.get('measurement_exclusion')),
                                  available, time_basis)
                detector.note_capture_time(capture)
                continue
            coordinate_eligible_count += 1
            detector.add(dict(row, geometry_snapshot_id=geometry_snapshot_id),
                         dispersion_lines * geometry['line_height_px'])
        elif row['type'] == 'session_end':
            detector.boundary('session_end', row.get('elapsed_ms'))
    if end.get('type') != 'session_end':
        detector.boundary('stream_end')
    metrics, visits = summarize(detector.fixations, metadata['word_ranges'], max_gap_ms)
    paths = replay_paths(detector.fixations, metadata['word_ranges'],
                         detector.segment_boundaries, max_gap_ms)
    observation_span = last_capture - first_capture if first_capture is not None else None
    span_fraction = lambda span: span / observation_span if observation_span and observation_span > 0 else None
    fixation_span = sum(f['duration_ms'] for f in detector.fixations)
    paths['coverage'].update(
        sample_denominator=sample_count,
        coordinate_geometry_eligible_samples=coordinate_eligible_count,
        accepted_monotonic_samples=detector.accepted_samples,
        accepted_sample_fraction=detector.accepted_samples / sample_count if sample_count else None,
        observation_span_ms=observation_span,
        observed_fixation_span_ms=fixation_span,
        observed_fixation_span_fraction=span_fraction(fixation_span),
        observed_word_fixation_span_ms=sum(v['duration_ms'] for v in paths['ordered_word_visits']),
        observed_line_fixation_span_ms=sum(v['duration_ms'] for v in paths['line_visits']),
        complete_fixations=metrics['complete_fixations'],
        censored_fixation_fraction=(1 - metrics['complete_fixations'] / len(detector.fixations)
                                    if detector.fixations else None),
    )
    return dict(
        analysis_version=6, session_id=metadata['session_id'], source_complete=complete,
        source_metadata=metadata,
        parameters=dict(dispersion_lines=dispersion_lines, min_ms=min_ms, min_samples=min_samples,
                        max_gap_ms=max_gap_ms, word_sample_agreement=agreement,
                        line_sample_agreement=agreement),
        sample_records=sample_count, valid_sample_records=detector.accepted_samples,
        coordinate_geometry_eligible_sample_records=coordinate_eligible_count,
        rejected_nonmonotonic_sample_records=detector.rejected_nonmonotonic_samples,
        reasons=dict(reasons),
        measurement_exclusions=dict(exclusions),
        median_capture_interval_ms=statistics.median(intervals) if intervals else None,
        p95_capture_interval_ms=percentile95(intervals),
        coordinate_output_age=dict(sample_denominator=coordinate_age_denominator,
                                   measured_samples=len(coordinate_ages),
                                   missing_time_samples=coordinate_age_missing,
                                   negative_age_samples=coordinate_age_negative,
                                   median_ms=statistics.median(coordinate_ages) if coordinate_ages else None,
                                   p95_ms=percentile95(coordinate_ages)),
        metrics=metrics, fixations=detector.fixations, visits=visits,
        geometry_snapshots=geometry_snapshots,
        segment_boundaries=detector.segment_boundaries, **paths,
        segment_boundary_event_counts=dict(Counter(b['reason'] for b in detector.segment_boundaries)),
        interpretation='Descriptive raw measurement and dispersion candidates; '
                       'no difficulty probability, RII, attention diagnosis, or independent eye labels.',
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('recording', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--dispersion-lines', type=float, default=0.5)
    parser.add_argument('--min-ms', type=float, default=100.0)
    parser.add_argument('--min-samples', type=int, default=3)
    parser.add_argument('--max-gap-ms', type=float, default=125.0)
    parser.add_argument('--agreement', type=float, default=0.8)
    parser.add_argument('--allow-incomplete', action='store_true')
    args = parser.parse_args()
    with args.recording.open(encoding='utf-8') as source:
        records = [json.loads(line) for line in source if line.strip()]
    result = analyze(records, args.dispersion_lines, args.min_ms, args.min_samples,
                     args.max_gap_ms, args.agreement, args.allow_incomplete)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    print(json.dumps(result['metrics'], indent=2))


if __name__ == '__main__':
    main()
