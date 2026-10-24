#!/usr/bin/env python3
"""Strict offline validator/scorer for the frozen NewsMead estimator comparison.

The module validates all four hash-preserved slot artifacts before returning any
spatial result. It never fits, corrects, interpolates, snaps, or replaces gaze.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path


PROTOCOL_ID = "newsmead_current_vs_mgazenet_reading_v1"
PROTOCOL_SHA256 = "41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185"
PROTOCOL_SCHEMA = "newsmead_estimator_comparison_protocol_v1"
COLLECTION_SCHEMA = "newsmead_estimator_comparison_collection_v1"
SLOT_SCHEMA = "newsmead_comparison_slot_v1"
PASSAGE_SHA256 = "6127a0ce824aa6f1139c6361aa83e5f461bd5434ac2aebf1d47ce199038da06c"
COORDINATE_SPACE = "screen_px_v1"
CLOCK = "elapsedRealtimeNanos"
MIN_COORDINATES = 10
SENSITIVITY_MS = (50, 100, 200, 500)
SLOTS = {
    "a56_current_A": ("SM-A566B", "current", "A"),
    "a56_mgazenet_A": ("SM-A566B", "mgazenet", "A"),
    "g991b_mgazenet_B": ("SM-G991B", "mgazenet", "B"),
    "g991b_current_B": ("SM-G991B", "current", "B"),
}
BASES = {
    "current": (
        "ec635fd01905544c8251c37b6891105ebbdee923",
        "3c4019e9ae0390e1f9af05f262ba50ab4c71332ac9258aa4c9cc172e67c78e01",
    ),
    "mgazenet": (
        "bea2dacbfa8d95f6519ccd4fad1cb0dfd6028b22",
        "131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9",
    ),
}
COMPARISON_BUILDS = {
    "current": {
        "apk_sha256": "2147ff1b1e3e1761e144d95499001c58dad0aa4b932d0c737611062ffa93eb87",
        "apk_size_bytes": 84746470,
        "apk_file": "current-newsmead-comparison-v1.apk",
        "controls_sha256": "e577ef8f8f27e7bd0aef688ee1ff9e6153f3f41aaf76a7cc1b2cdfe28b9b4dea",
    },
    "mgazenet": {
        "apk_sha256": "8146df9bbab6facf17fab4687c2cee4fb79589d4a3ce0201bf8a3bf434dc50ea",
        "apk_size_bytes": 47861232,
        "apk_file": "mgazenet-primary-comparison-v1.apk",
        "controls_sha256": "fc5e1219506731e622d236813aa731606a640e1a7ce3005f1863d4bf4658ab7f",
    },
}
WORD_A = tuple(f"localization_{index}" for index in range(1, 9))
WORD_B = tuple(WORD_A[index] for index in (1, 3, 0, 2, 5, 7, 4, 6))
LINE_A = tuple(f"line_reading_{index}" for index in range(1, 7))
LINE_B = tuple(LINE_A[index] for index in (1, 2, 0, 4, 5, 3))
VERTICAL = ("vertical_top", "vertical_middle", "vertical_bottom")
WORD_REGIONS = {
    f"localization_{index + 1}": region for index, region in enumerate((
        "top_left", "middle_center", "bottom_right", "top_right",
        "bottom_left", "middle_right", "top_center", "bottom_center",
    ))
}
WORD_FRACTIONS = {
    f"localization_{index + 1}": fraction for index, fraction in enumerate(
        (.22, .50, .76, .22, .76, .50, .22, .76)
    )
}
LINE_REGIONS = {
    f"line_reading_{index + 1}": ("top_line", "middle_line", "bottom_line")[index % 3]
    for index in range(6)
}
LINE_FRACTIONS = {
    f"line_reading_{index + 1}": (.25, .50, .75, .25, .50, .75)[index]
    for index in range(6)
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def is_sha256(value) -> bool:
    return isinstance(value, str) and len(value) == 64 and all(
        char in "0123456789abcdef" for char in value
    )


def number(value, label="number") -> float:
    require(type(value) in (int, float) and math.isfinite(value), f"Invalid finite {label}")
    return value


def integer(value, label="integer") -> int:
    require(type(value) is int, f"Invalid {label}")
    return value


def _object_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def _reject_constant(value):
    raise ValueError(f"Non-finite JSON constant: {value}")


def strict_loads(text: str):
    return json.loads(
        text,
        object_pairs_hook=_object_no_duplicates,
        parse_constant=_reject_constant,
    )


def read_json(path: Path):
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeError as failure:
        raise ValueError(f"Invalid UTF-8 in {path.name}") from failure
    value = strict_loads(text)
    require(isinstance(value, dict), f"Expected JSON object in {path.name}")
    return value


def validate_protocol(path: Path):
    raw = path.read_bytes()
    require(sha256(raw) == PROTOCOL_SHA256, "Frozen protocol manifest bytes changed")
    manifest = strict_loads(raw.decode("utf-8"))
    require(manifest.get("schema") == PROTOCOL_SCHEMA, "Wrong protocol schema")
    require(manifest.get("protocol_id") == PROTOCOL_ID, "Wrong protocol ID")
    require(manifest.get("participant_collection_authorized") is False,
            "Frozen software-only authorization flag changed")
    expected_slots = [
        {"id": slot, "device": values[0], "estimator": values[1], "order_variant": values[2]}
        for slot, values in SLOTS.items()
    ]
    require(manifest.get("slots") == expected_slots, "Frozen slot plan changed")
    reading = manifest.get("reading", {})
    require(reading == {
        "protocol_version": 4,
        "word_targets": 8,
        "line_targets": 6,
        "vertical_reference_acquire_ms": 2000,
        "vertical_reference_measure_ms": 2500,
        "word_acquire_ms": 3000,
        "word_measure_ms": 2500,
        "line_acquire_ms": 2500,
        "line_measure_ms": 5000,
        "vertical_alignment": "off",
    }, "Frozen reading timing or target counts changed")
    coverage = manifest.get("coverage", {})
    require(coverage.get("minimum_delivered_valid_coordinates_per_scored_target") == MIN_COORDINATES and
            coverage.get("delivery_sensitivity_ms") == list(SENSITIVITY_MS) and
            coverage.get("primary_freshness_limit_ms") is None,
            "Frozen coverage contract changed")
    for estimator, (commit, apk) in BASES.items():
        actual = manifest.get("base_artifacts", {}).get(estimator, {})
        require(actual.get("commit") == commit and actual.get("apk_sha256") == apk,
                f"Frozen {estimator} baseline changed")
    return manifest, sha256(raw)


def validate_collection(path: Path, protocol):
    collection = read_json(path)
    require(collection.get("schema") == COLLECTION_SCHEMA, "Wrong collection manifest schema")
    require(collection.get("protocol_id") == PROTOCOL_ID and
            collection.get("protocol_manifest_sha256") == PROTOCOL_SHA256,
            "Collection manifest is not bound to the frozen protocol")
    require(collection.get("status") == "host_build_verified_device_checks_pending" and
            collection.get("participant_collection_authorized") is False,
            "Collection manifest crossed the software-only boundary")
    require(collection.get("application_id") == protocol.get("application_id") and
            collection.get("version_code") == protocol.get("version_code") and
            collection.get("version_name") == "0.2" and
            collection.get("signer_sha256") == protocol.get("signer_sha256"),
            "Collection application identity changed")
    builds = collection.get("builds")
    require(isinstance(builds, dict) and set(builds) == set(BASES),
            "Exactly current and MGazeNet collection builds are required")
    for estimator, (commit, base_apk) in BASES.items():
        build = builds[estimator]
        final = COMPARISON_BUILDS[estimator]
        require(build.get("estimator_id") == estimator and
                build.get("base_commit") == commit and
                build.get("base_apk_sha256") == base_apk and
                build.get("comparison_apk_sha256") == final["apk_sha256"] and
                build.get("comparison_apk_size_bytes") == final["apk_size_bytes"] and
                build.get("comparison_apk_file") == final["apk_file"] and
                build.get("comparison_controls_sha256") == final["controls_sha256"],
                f"Incomplete or wrong {estimator} collection build identity")
    slots = collection.get("slots")
    require(isinstance(slots, list) and len(slots) == 4, "Exactly four collection slots are required")
    by_id = {}
    for item in slots:
        require(isinstance(item, dict) and item.get("id") not in by_id, "Duplicate collection slot")
        by_id[item.get("id")] = item
    require(set(by_id) == set(SLOTS), "Missing or unknown collection slot")
    for slot_id, (device, estimator, order) in SLOTS.items():
        item = by_id[slot_id]
        require(item.get("device") == device and item.get("estimator") == estimator and
                item.get("order_variant") == order and
                item.get("comparison_apk_sha256") == builds[estimator]["comparison_apk_sha256"],
                f"Changed collection binding for {slot_id}")
    return collection, sha256(path.read_bytes())


def _sidecar_for(path: Path) -> Path:
    return Path(str(path) + ".sha256")


def load_slot(path: Path, expected_slot: str):
    sidecar = _sidecar_for(path)
    require(path.is_file() and sidecar.is_file(), f"Missing artifact or SHA receipt for {expected_slot}")
    raw = path.read_bytes()
    digest = sha256(raw)
    parts = sidecar.read_text(encoding="utf-8").strip().split()
    require(parts == [digest, path.name], f"Wrong device-written SHA receipt for {expected_slot}")
    records = []
    try:
        text = raw.decode("utf-8")
    except UnicodeError as failure:
        raise ValueError(f"Invalid UTF-8 slot artifact: {expected_slot}") from failure
    for line_number, line in enumerate(text.splitlines(), start=1):
        require(line.strip(), f"Blank JSONL record in {expected_slot}:{line_number}")
        record = strict_loads(line)
        require(isinstance(record, dict), f"Non-object JSONL record in {expected_slot}:{line_number}")
        records.append(record)
    require(records, f"Empty slot artifact: {expected_slot}")
    return records, digest


def comparison_binding(record, calibration_sha):
    values = (
        record["protocol_id"], record["protocol_manifest_sha256"], record["slot_id"],
        record["order_variant"], record["estimator_id"], record["estimator_base_commit"],
        record["estimator_base_apk_sha256"], record["collection_apk_sha256"],
        calibration_sha or "unavailable", record["device_instance_sha256"], record["device_model"],
        f'{record["screen_width_px"]}x{record["screen_height_px"]}', record["density_dpi"],
        record["display_rotation"], COORDINATE_SPACE, CLOCK,
    )
    return sha256(("\n".join(map(str, values))).encode())


def reading_binding(binding, layout, passage):
    return sha256(f"{binding}\n{layout}\n{passage}\n4".encode())


def _validate_common(record, slot_id, collection, calibration_sha, first):
    device, estimator, order = SLOTS[slot_id]
    build = collection["builds"][estimator]
    require(record.get("comparison_schema") == SLOT_SCHEMA and
            record.get("protocol_id") == PROTOCOL_ID and
            record.get("protocol_manifest_sha256") == PROTOCOL_SHA256 and
            record.get("slot_id") == slot_id and record.get("order_variant") == order and
            record.get("estimator_id") == estimator and
            record.get("estimator_base_commit") == BASES[estimator][0] and
            record.get("estimator_base_apk_sha256") == BASES[estimator][1] and
            record.get("collection_apk_sha256") == build["comparison_apk_sha256"] and
            record.get("device_model") == device and
            record.get("coordinate_space") == COORDINATE_SPACE and
            record.get("monotonic_clock") == CLOCK,
            f"Changed record identity in {slot_id}")
    require(is_sha256(record.get("device_instance_sha256")) and
            integer(record.get("screen_width_px"), "screen width") > 0 and
            integer(record.get("screen_height_px"), "screen height") > 0 and
            integer(record.get("density_dpi"), "density") > 0 and
            integer(record.get("display_rotation"), "rotation") in range(4),
            f"Invalid device/layout identity in {slot_id}")
    require(record.get("calibration_sha256") == calibration_sha,
            f"Changing calibration fingerprint in {slot_id}")
    expected_binding = comparison_binding(first, calibration_sha)
    require(record.get("comparison_binding_sha256") == expected_binding,
            f"Wrong comparison binding in {slot_id}")
    return expected_binding


def _validate_target_plan(plan, order):
    require(plan.get("passage_sha256") == PASSAGE_SHA256,
            "Changed validation passage")
    constants = {
        "unscored_dot_preview_count": 1,
        "vertical_reference_count": 3,
        "word_target_count": 8,
        "line_target_count": 6,
        "countdown_ms": 3000,
        "vertical_reference_acquire_ms": 2000,
        "vertical_reference_measure_ms": 2500,
        "word_acquire_ms": 3000,
        "word_measure_ms": 2500,
        "line_acquire_ms": 2500,
        "line_measure_ms": 5000,
        "minimum_coordinates_per_scored_target": 10,
    }
    require(all(plan.get(key) == value for key, value in constants.items()),
            "Changed target count, timing, or coverage plan")
    targets = plan.get("targets")
    require(isinstance(targets, list) and len(targets) == 14, "Exactly fourteen scored targets are required")
    expected_ids = (WORD_A if order == "A" else WORD_B) + (LINE_A if order == "A" else LINE_B)
    require(tuple(item.get("id") for item in targets) == expected_ids, "Changed scored target order")
    by_id = {}
    for item in targets:
        target_id = item["id"]
        require(target_id not in by_id, "Duplicate scored target")
        kind = "WORD_FIXATION" if target_id.startswith("localization_") else "LINE_READING"
        region = WORD_REGIONS.get(target_id, LINE_REGIONS.get(target_id))
        fraction = WORD_FRACTIONS.get(target_id, LINE_FRACTIONS.get(target_id))
        require(item.get("target_kind") == kind and item.get("region") == region and
                math.isclose(number(item.get("viewport_fraction")), fraction, abs_tol=1e-6) and
                integer(item.get("line_index"), "target line") >= 0 and
                isinstance(item.get("target_text"), str) and item["target_text"] and
                integer(item.get("target_start"), "target start") >= 0 and
                integer(item.get("target_end"), "target end") > item["target_start"],
                f"Changed target definition: {target_id}")
        by_id[target_id] = item
    return by_id, constants


def _target_context(record):
    return {
        "id": record.get("expected_checkpoint"),
        "region": record.get("expected_region"),
        "line_index": record.get("expected_line"),
        "target_kind": record.get("expected_target_kind"),
        "target_text": record.get("expected_target_text"),
        "target_start": record.get("expected_target_start"),
        "target_end": record.get("expected_target_end"),
    }


def _require_context_matches(record, target):
    require(_target_context(record) == {key: target[key] for key in _target_context(record)},
            f"Target context changed for {target['id']}")


def _text_target(record, prefix):
    valid = record.get(f"{prefix}_valid")
    require(type(valid) is bool, f"Invalid {prefix} validity")
    line = integer(record.get(f"{prefix}_line"), f"{prefix} line")
    count = integer(record.get(f"{prefix}_line_count"), f"{prefix} line count")
    start = integer(record.get(f"{prefix}_word_start"), f"{prefix} word start")
    end = integer(record.get(f"{prefix}_word_end"), f"{prefix} word end")
    require(valid == (line >= 0 and count > 0), f"Inconsistent {prefix} validity")
    require((start == -1 and end == -1) or (start >= 0 and end > start), f"Invalid {prefix} word")
    return (line, count, start, end)


class Stabilizer:
    def __init__(self):
        self.reset()

    def reset(self):
        self.stable = (-1, 0, -1, -1)
        self.candidate = (-1, 0, -1, -1)
        self.candidate_since = 0
        self.off_since = None
        self.word_candidate = (-1, -1)
        self.word_since = 0

    @staticmethod
    def valid(target):
        return target[0] >= 0 and target[1] > 0

    @staticmethod
    def without_word(target):
        return (target[0], target[1], -1, -1)

    def clear_word(self):
        self.word_candidate = (-1, -1)
        self.word_since = 0

    def word(self, raw, now):
        if raw[2] < 0 or raw[3] <= raw[2]:
            self.stable = self.without_word(self.stable)
            self.clear_word()
        elif self.stable[2:] != raw[2:]:
            if self.word_candidate != raw[2:]:
                self.word_candidate = raw[2:]
                self.word_since = now
            elif now - self.word_since >= 300:
                self.stable = raw
                self.clear_word()

    def update(self, raw, now):
        if not self.valid(raw):
            if self.off_since is None:
                self.off_since = now
            if now - self.off_since >= 200:
                self.stable = (-1, 0, -1, -1)
                self.candidate = (-1, 0, -1, -1)
                self.clear_word()
            return self.stable
        self.off_since = None
        if not self.valid(self.stable):
            self.stable = self.without_word(raw)
            self.candidate = self.stable
            self.word(raw, now)
            return self.stable
        if raw[0] != self.stable[0]:
            if self.candidate[0] != raw[0]:
                self.candidate = self.without_word(raw)
                self.candidate_since = now
            elif now - self.candidate_since >= 120:
                self.stable = self.without_word(raw)
                self.clear_word()
            return self.stable
        self.candidate = self.without_word(raw)
        self.word(raw, now)
        return self.stable


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    low, high = int(position), math.ceil(position)
    if low == high:
        return ordered[low]
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def distribution(values):
    if not values:
        return {"n": 0, "median": None, "p95": None, "max": None}
    return {"n": len(values), "median": percentile(values, .5),
            "p95": percentile(values, .95), "max": max(values)}


def signed_distribution(values):
    if not values:
        return {"n": 0, "median": None, "p05": None, "p95": None, "min": None, "max": None}
    return {"n": len(values), "median": percentile(values, .5),
            "p05": percentile(values, .05), "p95": percentile(values, .95),
            "min": min(values), "max": max(values)}


def coverage(events, start_ns, end_ns, sensitivity_ms):
    """Availability from valid deliveries, terminated by the next invalid event."""
    ordered = sorted(
        (integer(event[0], "availability clock"), event[1])
        for event in events if start_ns <= event[0] < end_ns
    )
    require(all(type(valid) is bool for _, valid in ordered), "Invalid availability event")
    points = [ns for ns, valid in ordered if valid]
    intervals = []
    limit = int(sensitivity_ms * 1_000_000)
    for index, (value, valid) in enumerate(ordered):
        if not valid:
            continue
        next_event = ordered[index + 1][0] if index + 1 < len(ordered) else end_ns
        begin, end = value, min(value + limit, next_event, end_ns)
        if intervals and begin <= intervals[-1][1]:
            intervals[-1][1] = max(intervals[-1][1], end)
        else:
            intervals.append([begin, end])
    covered = sum(end - begin for begin, end in intervals)
    gaps = []
    cursor = start_ns
    for begin, end in intervals:
        if begin > cursor:
            gaps.append(begin - cursor)
        cursor = max(cursor, end)
    if cursor < end_ns:
        gaps.append(end_ns - cursor)
    initial = (points[0] - start_ns) if points else (end_ns - start_ns)
    planned = end_ns - start_ns
    return {
        "sensitivity_ms": sensitivity_ms,
        "covered_ms": covered / 1e6,
        "planned_ms": planned / 1e6,
        "coverage_fraction": covered / planned,
        "initial_gap_ms": initial / 1e6,
        "longest_gap_ms": (max(gaps) if gaps else 0) / 1e6,
    }


def _score_target(target, samples, availability_events, measure_ns, duration_ms, estimator):
    end_ns = measure_ns + int(duration_ms * 1_000_000)
    used = [sample for sample in samples if measure_ns <= sample["event_elapsed_ns"] < end_ns]
    raw_signed, stable_signed = [], []
    raw_abs, stable_abs = [], []
    raw_exact = raw_adjacent = stable_exact = stable_adjacent = exact_word = off_text = 0
    for sample in used:
        raw = _text_target(sample, "raw_target")
        stable = _text_target(sample, "stable_target")
        expected_line = target["line_index"]
        if Stabilizer.valid(raw):
            error = raw[0] - expected_line
            raw_signed.append(error)
            raw_abs.append(abs(error))
            raw_exact += error == 0
            raw_adjacent += abs(error) <= 1
        else:
            off_text += 1
        if Stabilizer.valid(stable):
            error = stable[0] - expected_line
            stable_signed.append(error)
            stable_abs.append(abs(error))
            stable_exact += error == 0
            stable_adjacent += abs(error) <= 1
            if target["target_kind"] == "WORD_FIXATION":
                exact_word += stable[2] == target["target_start"] and stable[3] == target["target_end"]
    count = len(used)
    result = {
        "id": target["id"],
        "kind": target["target_kind"],
        "region": target["region"],
        "expected_line": target["line_index"],
        "delivered_coordinate_count": count,
        "invalid_event_count": (
            sum(1 for ns, valid in availability_events if measure_ns <= ns < end_ns and not valid)
            if estimator == "mgazenet" else None
        ),
        "coverage_complete": count >= MIN_COORDINATES,
        "off_text_fraction": off_text / count if count else None,
        "raw_signed_line_error": signed_distribution(raw_signed),
        "raw_absolute_line_error": distribution(raw_abs),
        "stabilized_signed_line_error": signed_distribution(stable_signed),
        "stabilized_absolute_line_error": distribution(stable_abs),
        "raw_exact_line_fraction": raw_exact / count if count else None,
        "raw_within_one_line_fraction": raw_adjacent / count if count else None,
        "stabilized_exact_line_fraction": stable_exact / count if count else None,
        "stabilized_within_one_line_fraction": stable_adjacent / count if count else None,
        "exact_word_fraction": (exact_word / count if count else None)
            if target["target_kind"] == "WORD_FIXATION" else None,
        "delivery": [coverage(availability_events, measure_ns, end_ns, age)
                     for age in SENSITIVITY_MS],
    }
    return result


def validate_and_score_slot(slot_id, records, collection):
    require([integer(record.get("record_sequence"), "record sequence") for record in records] ==
            list(range(len(records))), f"Non-contiguous record sequence in {slot_id}")
    clocks = [integer(record.get("event_elapsed_ns"), "event clock") for record in records]
    require(all(left <= right for left, right in zip(clocks, clocks[1:])),
            f"Nonmonotonic event clock in {slot_id}")
    require(len(records) >= 3 and [item.get("record_type") for item in records[:3]] ==
            ["slot_start", "calibration_start", "calibration_end"],
            f"Missing calibration prefix in {slot_id}")
    calibration_end = records[2]
    calibration_sha = calibration_end.get("calibration_sha256")
    estimator = SLOTS[slot_id][1]
    if calibration_end.get("outcome") == "completed":
        require(is_sha256(calibration_sha) and calibration_end.get("calibration_technically_complete") is True and
                calibration_end.get("fit_target_count") == 16 and
                calibration_end.get("post_fit_check_count") == 6 and
                calibration_end.get("excluded_fit_target_count") == 0 and
                calibration_end.get("detailed_telemetry_enabled") is False and
                calibration_end.get("correction_applied") is False and
                calibration_end.get("spatial_result_exposed") is False,
                f"Invalid completed calibration in {slot_id}")
        if estimator == "mgazenet":
            require(calibration_end.get("accepted_rows_per_target") == 45 and
                    calibration_end.get("feature_count") == 258,
                    f"Changed MGazeNet calibration contract in {slot_id}")
        else:
            require(calibration_end.get("accepted_rows_per_target") is None and
                    calibration_end.get("feature_count") == 2,
                    f"Changed current calibration contract in {slot_id}")
    else:
        require(calibration_end.get("outcome") in ("failed", "interrupted") and
                calibration_sha is None and calibration_end.get("calibration_technically_complete") is False and
                calibration_end.get("spatial_result_exposed") is False and len(records) == 3,
                f"Invalid retained calibration failure in {slot_id}")
    first = records[0]
    for record in records:
        binding = _validate_common(record, slot_id, collection, calibration_sha, first)
    if calibration_end.get("outcome") != "completed":
        return {"slot_id": slot_id, "outcome": calibration_end["outcome"],
                "spatial_complete": False, "targets": []}

    require(len(records) > 5 and [item.get("record_type") for item in records[3:6]] ==
            ["session_start", "layout", "protocol_plan"],
            f"Missing reading session in {slot_id}")
    reading_records = records[3:]
    layout = first_reading = reading_records[0].get("reading_layout_sha256")
    passage = reading_records[0].get("passage_sha256")
    require(is_sha256(layout) and is_sha256(passage), f"Missing reading layout binding in {slot_id}")
    expected_reading_binding = reading_binding(binding, layout, passage)
    for record in reading_records:
        require(record.get("reading_layout_sha256") == layout and record.get("passage_sha256") == passage and
                record.get("reading_binding_sha256") == expected_reading_binding,
                f"Changing reading layout identity in {slot_id}")
    start = reading_records[0]
    session_id = f"comparison_{slot_id}"
    require(all(record.get("session_id") == session_id for record in reading_records),
            f"Changing or missing reading session ID in {slot_id}")
    for record in reading_records:
        require(integer(record.get("timestamp_ms"), "wall-clock timestamp") > 0 and
                number(record.get("elapsed_ms"), "session elapsed time") >= 0 and
                record["event_elapsed_ns"] >= start["event_elapsed_ns"] and
                math.isclose(
                    record["elapsed_ms"],
                    (record["event_elapsed_ns"] - start["event_elapsed_ns"]) / 1e6,
                    abs_tol=1e-5,
                ), f"Invalid reading event envelope in {slot_id}")
    require(start.get("schema_version") == 7 and start.get("protocol_version") == 4 and
            start.get("run_label") == slot_id and start.get("vertical_alignment_mode") == "off" and
            start.get("drift_correction_active") is False and
            start.get("detailed_source_telemetry_enabled") is False and
            start.get("camera_frames_retained") is False and
            start.get("result_visibility") == "offline_after_four_hashes",
            f"Unsafe comparison session start in {slot_id}")
    plans = [item for item in reading_records if item.get("record_type") == "protocol_plan"]
    require(len(plans) == 1, f"Exactly one protocol plan required in {slot_id}")
    plan = plans[0]
    require(plan.get("passage_sha256") == passage == PASSAGE_SHA256,
            f"Passage binding mismatch in {slot_id}")
    targets, constants = _validate_target_plan(plan, SLOTS[slot_id][2])
    layouts = [item for item in reading_records if item.get("record_type") == "layout"]
    require(len(layouts) == 1 and integer(layouts[0].get("line_count"), "line count") > 0 and
            number(layouts[0].get("line_height_px"), "line height") > 0 and
            number(layouts[0].get("text_size_px"), "text size") > 0,
            f"Invalid reading layout record in {slot_id}")

    terminal = records[-1]
    require(terminal.get("record_type") == "session_end" and
            terminal.get("outcome") in ("completed", "interrupted", "mgazenet_failed", "estimator_failed",
                                         "reading_surface_not_visible") and
            terminal.get("spatial_result_exposed") is False,
            f"Missing or invalid terminal record in {slot_id}")
    completed = terminal.get("outcome") == "completed"

    active = None
    gaze_by_target = {target_id: [] for target_id in targets}
    availability_by_target = {target_id: [] for target_id in targets}
    mgaze_sources = []
    stabilizers = {target_id: Stabilizer() for target_id in targets}
    state_records = []
    for record in reading_records:
        kind = record.get("record_type")
        if kind == "protocol_state":
            active = (record.get("phase"), record.get("step_id"), record.get("trial_state"))
            state_records.append(record)
            if record.get("step_id") in targets and record.get("trial_state") == "ACQUIRE":
                stabilizers[record["step_id"]].reset()
        elif kind in ("gaze_sample", "mgazenet_source"):
            event_context = (record.get("phase"), record.get("step_id"), record.get("trial_state"))
            if kind == "mgazenet_source" and record.get("reason") == "stopped" and completed:
                require(event_context == ("COMPLETE", "complete", "MEASURE"),
                        f"Wrong completed MGaze terminal context in {slot_id}")
            else:
                require(active is not None and event_context == active,
                        f"Impossible event context order in {slot_id}")
        if kind == "gaze_sample":
            target_id = record.get("step_id")
            if target_id in targets:
                _require_context_matches(record, targets[target_id])
                gaze_by_target[target_id].append(record)
                raw = _text_target(record, "raw_target")
                require(_text_target(record, "base_target") == raw,
                        f"Active reading correction changed AOI in {slot_id}")
                require(number(record.get("gaze_y_screen_px")) ==
                        number(record.get("effective_gaze_y_screen_px")) and
                        number(record.get("vertical_alignment_delta_px")) == 0,
                        f"Active vertical correction in {slot_id}")
                delivery_ns = integer(record.get("delivery_elapsed_ns"), "delivery clock")
                require(delivery_ns == record["event_elapsed_ns"], "Mismatched gaze delivery clock")
                if estimator == "current":
                    availability_by_target[target_id].append((delivery_ns, True))
                expected_stable = stabilizers[target_id].update(raw, delivery_ns // 1_000_000)
                require(_text_target(record, "stable_target") == expected_stable,
                        f"Changed target stabilizer output in {slot_id}")
            number(record.get("gaze_x_screen_px"), "gaze x")
            number(record.get("gaze_y_screen_px"), "gaze y")
        elif kind == "mgazenet_source":
            mgaze_sources.append(record)
            target_id = record.get("step_id")
            if target_id in targets:
                availability_by_target[target_id].append(
                    (integer(record.get("delivery_elapsed_ns"), "MGaze delivery clock"),
                     record.get("reason") == "coordinate")
                )

    require(state_records and
            (state_records[0].get("phase"), state_records[0].get("step_id"),
             state_records[0].get("trial_state")) ==
            ("SETUP", "unscored_dot_preview", "PREPARE"),
            f"Missing unscored preview state in {slot_id}")
    vertical_states = [item for item in state_records if item.get("step_id") in VERTICAL]
    expected_vertical = [(target, state) for target in VERTICAL for state in ("ACQUIRE", "MEASURE")]
    vertical_observations = [item for item in reading_records
                             if item.get("record_type") == "vertical_alignment_reference"]
    observed_vertical_ids = tuple(item.get("reference_id") for item in vertical_observations)
    require(observed_vertical_ids == VERTICAL[:len(observed_vertical_ids)] and
            (not completed or observed_vertical_ids == VERTICAL) and
            all(integer(item.get("sample_count"), "vertical-reference sample count") >= 0 and
                math.isfinite(number(item.get("target_x_screen_px"), "vertical target x")) and
                math.isfinite(number(item.get("target_y_screen_px"), "vertical target y"))
                for item in vertical_observations),
            f"Changed or missing vertical-reference observations in {slot_id}")
    fits = [item for item in reading_records if item.get("record_type") == "vertical_alignment_fit"]
    require(len(fits) <= 1 and all(
        item.get("mode") == "off" and item.get("applied") is False for item in fits
    ), f"Vertical alignment was not locked off in {slot_id}")
    target_states = [item for item in state_records if item.get("step_id") in targets]
    expected_ids = list(targets)
    expected_state_sequence = expected_vertical + [
        (target, state) for target in expected_ids for state in ("ACQUIRE", "MEASURE")
    ]
    scored_states = [item for item in state_records
                     if item.get("step_id") in VERTICAL or item.get("step_id") in targets]
    actual_state_sequence = [
        (item.get("step_id"), item.get("trial_state")) for item in scored_states
    ]
    if completed:
        require(actual_state_sequence == expected_state_sequence and len(fits) == 1,
                f"Changed or incomplete trial sequence in {slot_id}")
    else:
        require(actual_state_sequence == expected_state_sequence[:len(actual_state_sequence)],
                f"Interrupted trial is not a coherent prefix in {slot_id}")
    for item in target_states:
        _require_context_matches(item, targets[item["step_id"]])
        expected_phase = ("LOCALIZATION" if item["step_id"].startswith("localization_")
                          else "GUIDED_LINE_READING")
        require(item.get("phase") == expected_phase,
                f"Wrong phase for {item['step_id']} in {slot_id}")
    require(all(item.get("phase") == "VERTICAL_ALIGNMENT" for item in vertical_states),
            f"Wrong vertical-reference phase in {slot_id}")
    if fits:
        require(fits[0]["record_sequence"] > vertical_states[-1]["record_sequence"] and
                (not target_states or fits[0]["record_sequence"] < target_states[0]["record_sequence"]),
                f"Vertical-alignment fit is out of order in {slot_id}")

    by_step_state = {(item["step_id"], item["trial_state"]): item for item in vertical_states + target_states}
    sequence = list(VERTICAL) + expected_ids
    for index, target_id in enumerate(sequence):
        acquire_record = by_step_state.get((target_id, "ACQUIRE"))
        measure_record = by_step_state.get((target_id, "MEASURE"))
        if acquire_record is None:
            break
        if measure_record is None:
            require(not completed, f"Missing measurement state for {target_id}")
            break
        acquire = acquire_record["event_elapsed_ns"]
        measure = measure_record["event_elapsed_ns"]
        if target_id in VERTICAL:
            acquire_ms, measure_ms = constants["vertical_reference_acquire_ms"], constants["vertical_reference_measure_ms"]
        elif target_id.startswith("localization_"):
            acquire_ms, measure_ms = constants["word_acquire_ms"], constants["word_measure_ms"]
        else:
            acquire_ms, measure_ms = constants["line_acquire_ms"], constants["line_measure_ms"]
        require(measure - acquire >= acquire_ms * 1_000_000,
                f"Short acquisition interval for {target_id}")
        next_record = by_step_state.get((sequence[index + 1], "ACQUIRE")) if index + 1 < len(sequence) else terminal
        if next_record is not None and (completed or next_record is not terminal):
            require(next_record["event_elapsed_ns"] - measure >= measure_ms * 1_000_000,
                    f"Short measurement interval for {target_id}")

    delivery_ids = [integer(item.get("delivery_id"), "delivery ID")
                    for item in reading_records if item.get("record_type") == "gaze_sample"]
    require(len(delivery_ids) == len(set(delivery_ids)), f"Duplicate delivered coordinate ID in {slot_id}")
    if estimator == "current":
        require(not mgaze_sources and not any(
            "capture_elapsed_ms" in item for item in reading_records
        ), "Current arm invented capture timing")
    else:
        source_ids = {}
        previous_arrivals = previous_drops = -1
        for source in mgaze_sources:
            delivery_id = integer(source.get("delivery_id"), "MGaze delivery ID")
            require(delivery_id not in source_ids, "Duplicate MGaze source delivery ID")
            source_ids[delivery_id] = source
            delivery_ns = integer(source.get("delivery_elapsed_ns"), "MGaze delivery clock")
            require(delivery_ns == source["event_elapsed_ns"], "Mismatched MGaze delivery clock")
            capture = source.get("capture_elapsed_ms")
            if capture is not None:
                capture = number(capture, "capture clock")
                require(capture <= delivery_ns / 1e6 and
                        math.isclose(number(source.get("output_age_ms")), delivery_ns / 1e6 - capture,
                                     abs_tol=1e-5), "Invalid MGaze capture age")
            arrivals, drops = source.get("analyzer_arrivals"), source.get("observed_busy_drops")
            if arrivals is not None:
                require(integer(arrivals, "arrival count") >= previous_arrivals, "Decreasing MGaze arrivals")
                previous_arrivals = arrivals
            if drops is not None:
                require(integer(drops, "busy-drop count") >= previous_drops, "Decreasing MGaze busy drops")
                previous_drops = drops
            require(source.get("filter") == "none" and source.get("operational_expiry_ms") == 500,
                    "Changed MGaze output policy")
            require(source.get("reason") in {
                "coordinate", "stale", "no_face", "invalid_crops", "eye_area",
                "invalid_prediction", "invalid_clock", "stopped",
            }, "Unknown MGaze source outcome")
            if source.get("reason") == "coordinate":
                require(capture is not None and source.get("raw_screen_x") is not None and
                        source.get("raw_screen_y") is not None,
                        "Incomplete MGaze coordinate source")
        for sample in (item for item in reading_records if item.get("record_type") == "gaze_sample"):
            source = source_ids.get(sample["delivery_id"])
            require(source is not None and source.get("reason") == "coordinate" and
                    number(source.get("raw_screen_x")) == number(sample.get("gaze_x_screen_px")) and
                    number(source.get("raw_screen_y")) == number(sample.get("gaze_y_screen_px")),
                    "Unlinked MGaze delivered coordinate")
        require(all(source.get("reason") == "coordinate" or source["delivery_id"] not in delivery_ids
                    for source in mgaze_sources), "Rejected MGaze result produced a coordinate")
        stopped_sources = [source for source in mgaze_sources if source.get("reason") == "stopped"]
        source_operated = any(source.get("reason") != "stopped" for source in mgaze_sources)
        if completed or terminal.get("outcome") == "interrupted" or source_operated:
            require(len(stopped_sources) == 1, f"Missing or duplicate terminal MGaze counters in {slot_id}")
        else:
            require(len(stopped_sources) <= 1, f"Duplicate terminal MGaze counters in {slot_id}")
        if stopped_sources:
            stopped = stopped_sources[0]
            require(stopped is mgaze_sources[-1] and
                    stopped["record_sequence"] + 1 == terminal["record_sequence"],
                    f"Late or nonterminal MGaze counters in {slot_id}")
            require(stopped.get("capture_elapsed_ms") is None and
                    stopped.get("output_age_ms") is None and
                    stopped.get("raw_screen_x") is None and
                    stopped.get("raw_screen_y") is None and
                    integer(stopped.get("analyzer_arrivals"), "terminal arrival count") >= 0 and
                    integer(stopped.get("observed_busy_drops"), "terminal busy-drop count") >= 0,
                    f"Incomplete terminal MGaze counters in {slot_id}")

    if not completed:
        partial = {
            "slot_id": slot_id,
            "outcome": terminal["outcome"],
            "spatial_complete": False,
            "calibration_sha256": calibration_sha,
            "reading_layout_sha256": layout,
            "passage_sha256": passage,
            "device_instance_sha256": first["device_instance_sha256"],
            "session_id": start.get("session_id"),
            "targets": [],
        }
        if estimator == "mgazenet":
            terminal_source = stopped_sources[0] if stopped_sources else None
            partial["mgazenet_analyzer_arrivals"] = (
                terminal_source["analyzer_arrivals"] if terminal_source else None
            )
            partial["mgazenet_observed_busy_drops"] = (
                terminal_source["observed_busy_drops"] if terminal_source else None
            )
        return partial

    results = []
    for target_id in expected_ids:
        measure_ns = by_step_state[(target_id, "MEASURE")]["event_elapsed_ns"]
        duration = constants["word_measure_ms"] if target_id.startswith("localization_") else constants["line_measure_ms"]
        results.append(_score_target(
            targets[target_id],
            gaze_by_target[target_id],
            availability_by_target[target_id],
            measure_ns,
            duration,
            estimator,
        ))
    spatial_complete = all(
        item["coverage_complete"] and item["stabilized_absolute_line_error"]["median"] is not None
        for item in results
    )
    words = [item for item in results if item["kind"] == "WORD_FIXATION"]
    guided = [item for item in results if item["kind"] == "LINE_READING"]
    def balanced(items, key):
        values = [item[key] for item in items]
        return statistics.mean(values) if values and all(value is not None for value in values) else None
    primary = balanced([{
        "value": item["stabilized_absolute_line_error"]["median"]
    } for item in guided], "value")
    slot_result = {
        "slot_id": slot_id,
        "outcome": terminal["outcome"],
        "spatial_complete": spatial_complete,
        "calibration_sha256": calibration_sha,
        "reading_layout_sha256": layout,
        "passage_sha256": passage,
        "device_instance_sha256": first["device_instance_sha256"],
        "session_id": start.get("session_id"),
        "targets": results,
        "primary_guided_target_balanced_mean_median_absolute_stabilized_line_error": primary,
        "guided_target_balanced_exact_line_fraction": balanced(guided, "stabilized_exact_line_fraction"),
        "guided_target_balanced_within_one_line_fraction": balanced(guided, "stabilized_within_one_line_fraction"),
        "word_target_balanced_median_absolute_stabilized_line_error": balanced([
            {"value": item["stabilized_absolute_line_error"]["median"]} for item in words
        ], "value"),
        "word_target_balanced_exact_line_fraction": balanced(words, "stabilized_exact_line_fraction"),
        "word_target_balanced_within_one_line_fraction": balanced(words, "stabilized_within_one_line_fraction"),
        "word_target_balanced_exact_word_fraction": balanced(words, "exact_word_fraction"),
        "delivery_target_balanced": [{
            "sensitivity_ms": age,
            "coverage_fraction": balanced([
                {"value": item["delivery"][index]["coverage_fraction"]} for item in results
            ], "value"),
        } for index, age in enumerate(SENSITIVITY_MS)],
    }
    if estimator == "mgazenet":
        ages = [number(item["output_age_ms"]) for item in mgaze_sources
                if item.get("reason") == "coordinate" and item.get("output_age_ms") is not None]
        slot_result["mgazenet_capture_age_ms"] = distribution(ages)
        terminal_source = stopped_sources[0]
        slot_result["mgazenet_analyzer_arrivals"] = terminal_source["analyzer_arrivals"]
        slot_result["mgazenet_observed_busy_drops"] = terminal_source["observed_busy_drops"]
    else:
        slot_result["mgazenet_capture_age_ms"] = None
        slot_result["mgazenet_analyzer_arrivals"] = None
        slot_result["mgazenet_observed_busy_drops"] = None
    return slot_result


def score_comparison(protocol_path: Path, collection_path: Path, slot_paths: dict[str, Path]):
    protocol, protocol_hash = validate_protocol(protocol_path)
    collection, collection_hash = validate_collection(collection_path, protocol)
    require(set(slot_paths) == set(SLOTS), "Exactly one explicitly named artifact per frozen slot is required")
    require(len({path.resolve() for path in slot_paths.values()}) == 4,
            "Each frozen slot requires a distinct artifact path")
    loaded, input_hashes = {}, {}
    for slot_id in SLOTS:
        records, digest = load_slot(slot_paths[slot_id], slot_id)
        loaded[slot_id] = records
        input_hashes[slot_id] = digest
    slots = {slot_id: validate_and_score_slot(slot_id, loaded[slot_id], collection) for slot_id in SLOTS}
    completed_calibrations = [item.get("calibration_sha256") for item in slots.values()
                              if item.get("calibration_sha256") is not None]
    require(len(completed_calibrations) == len(set(completed_calibrations)),
            "A calibration fingerprint was reused across slots")
    reading_sessions = [item.get("session_id") for item in slots.values()
                        if item.get("session_id") is not None]
    require(len(reading_sessions) == len(set(reading_sessions)),
            "A reading session identity was reused across slots")

    complete = all(item["spatial_complete"] for item in slots.values())
    paired = []
    for device, current_id, mgazenet_id in (
        ("SM-A566B", "a56_current_A", "a56_mgazenet_A"),
        ("SM-G991B", "g991b_current_B", "g991b_mgazenet_B"),
    ):
        current, mgazenet = slots[current_id], slots[mgazenet_id]
        if current.get("reading_layout_sha256") is not None and mgazenet.get("reading_layout_sha256") is not None:
            require(current["device_instance_sha256"] == mgazenet["device_instance_sha256"] and
                    current["reading_layout_sha256"] == mgazenet["reading_layout_sha256"] and
                    current["passage_sha256"] == mgazenet["passage_sha256"],
                    f"The two {device} arms do not share one device/layout plan")
            current_plan = next(item for item in loaded[current_id] if item.get("record_type") == "protocol_plan")["targets"]
            mgaze_plan = next(item for item in loaded[mgazenet_id] if item.get("record_type") == "protocol_plan")["targets"]
            require(current_plan == mgaze_plan, f"The two {device} arms changed targets")
        def difference(key):
            left, right = mgazenet.get(key), current.get(key)
            return left - right if left is not None and right is not None else None
        current_delivery = {
            item["sensitivity_ms"]: item["coverage_fraction"]
            for item in current.get("delivery_target_balanced", [])
        }
        mgazenet_delivery = {
            item["sensitivity_ms"]: item["coverage_fraction"]
            for item in mgazenet.get("delivery_target_balanced", [])
        }
        paired.append({
            "device_model": device,
            "mgazenet_minus_current": {
                "primary_error_lines": difference(
                    "primary_guided_target_balanced_mean_median_absolute_stabilized_line_error"
                ),
                "guided_within_one_line_fraction": difference(
                    "guided_target_balanced_within_one_line_fraction"
                ),
                "guided_exact_line_fraction": difference(
                    "guided_target_balanced_exact_line_fraction"
                ),
                "word_exact_line_fraction": difference(
                    "word_target_balanced_exact_line_fraction"
                ),
                "word_exact_word_fraction": difference(
                    "word_target_balanced_exact_word_fraction"
                ),
                "delivery_coverage_fraction": {
                    str(age): (
                        mgazenet_delivery[age] - current_delivery[age]
                        if age in mgazenet_delivery and age in current_delivery else None
                    ) for age in SENSITIVITY_MS
                },
            },
        })
    a56_instance = slots["a56_current_A"].get("device_instance_sha256") or slots["a56_mgazenet_A"].get(
        "device_instance_sha256"
    )
    g991b_instance = slots["g991b_current_B"].get("device_instance_sha256") or slots[
        "g991b_mgazenet_B"
    ].get("device_instance_sha256")
    if a56_instance is not None and g991b_instance is not None:
        require(a56_instance != g991b_instance,
                "The two frozen phone models share an impossible device identity")

    if not complete:
        classification = "inconclusive"
    else:
        errors = [item["mgazenet_minus_current"]["primary_error_lines"] for item in paired]
        within = [item["mgazenet_minus_current"]["guided_within_one_line_fraction"] for item in paired]
        if all(value < 0 for value in errors) and all(value >= 0 for value in within):
            classification = "consistent_mgazenet_direction"
        elif all(value > 0 for value in errors) and all(value <= 0 for value in within):
            classification = "consistent_current_direction"
        else:
            classification = "mixed"
    return {
        "schema": "newsmead_estimator_comparison_result_v1",
        "protocol_id": PROTOCOL_ID,
        "protocol_manifest_sha256": protocol_hash,
        "collection_manifest_sha256": collection_hash,
        "input_slot_sha256": input_hashes,
        "integrity": "validated_before_scoring",
        "classification": classification,
        "slots": [slots[slot_id] for slot_id in SLOTS],
        "paired_differences": paired,
        "claim_scope": "single participant on the frozen A56 and G991B known-target task",
    }


def parse_slot(value):
    slot_id, separator, path = value.partition("=")
    require(separator and slot_id in SLOTS and path, "Use --slot SLOT_ID=PATH")
    return slot_id, Path(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol-manifest", type=Path, required=True)
    parser.add_argument("--collection-manifest", type=Path, required=True)
    parser.add_argument("--slot", action="append", required=True, metavar="SLOT_ID=JSONL")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    entries = [parse_slot(value) for value in args.slot]
    require(len(entries) == 4 and len(dict(entries)) == 4, "Exactly four unique --slot values are required")
    result = score_comparison(
        args.protocol_manifest,
        args.collection_manifest,
        dict(entries),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as output:
        json.dump(result, output, indent=2, sort_keys=True, allow_nan=False)
        output.write("\n")


if __name__ == "__main__":
    main()
