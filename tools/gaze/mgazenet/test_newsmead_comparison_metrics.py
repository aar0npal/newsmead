"""Synthetic contract tests for the frozen current-versus-MGazeNet scorer."""
from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import newsmead_comparison_metrics as scorer


ROOT = Path(__file__).resolve().parents[3]
PROTOCOL = ROOT / "tools/gaze/mgazenet/manifests/newsmead-current-vs-mgazenet-reading-v1.json"
SIGNER = "3056e94475f68ae47cd7d25babf34eeb7d201acb97999f20eaa3ab95fa200332"


def hash_text(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def target_plan(order: str) -> list[dict]:
    word_ids = scorer.WORD_A if order == "A" else scorer.WORD_B
    line_ids = scorer.LINE_A if order == "A" else scorer.LINE_B
    targets = []
    for target_id in word_ids + line_ids:
        number = int(target_id.rsplit("_", 1)[1])
        word = target_id.startswith("localization_")
        start = number * 20
        targets.append({
            "id": target_id,
            "region": (scorer.WORD_REGIONS if word else scorer.LINE_REGIONS)[target_id],
            "line_index": number + (1 if word else 20),
            "target_kind": "WORD_FIXATION" if word else "LINE_READING",
            "target_text": f"target-{target_id}",
            "target_start": start,
            "target_end": start + 10,
            "viewport_fraction": (
                scorer.WORD_FRACTIONS if word else scorer.LINE_FRACTIONS
            )[target_id],
        })
    return targets


def collection_manifest() -> dict:
    builds = {}
    for estimator, (commit, base_apk) in scorer.BASES.items():
        final = scorer.COMPARISON_BUILDS[estimator]
        builds[estimator] = {
            "estimator_id": estimator,
            "base_commit": commit,
            "base_apk_sha256": base_apk,
            "comparison_apk_sha256": final["apk_sha256"],
            "comparison_apk_size_bytes": final["apk_size_bytes"],
            "comparison_apk_file": final["apk_file"],
            "comparison_controls_sha256": final["controls_sha256"],
        }
    return {
        "schema": scorer.COLLECTION_SCHEMA,
        "protocol_id": scorer.PROTOCOL_ID,
        "protocol_manifest_sha256": scorer.PROTOCOL_SHA256,
        "status": "host_build_verified_device_checks_pending",
        "participant_collection_authorized": False,
        "application_id": "com.newsmead",
        "version_code": 1,
        "version_name": "0.2",
        "signer_sha256": SIGNER,
        "builds": builds,
        "slots": [
            {
                "id": slot_id,
                "device": device,
                "estimator": estimator,
                "order_variant": order,
                "comparison_apk_sha256": builds[estimator]["comparison_apk_sha256"],
            }
            for slot_id, (device, estimator, order) in scorer.SLOTS.items()
        ],
    }


class SlotBuilder:
    def __init__(self, slot_id: str, collection: dict):
        self.slot_id = slot_id
        self.device, self.estimator, self.order = scorer.SLOTS[slot_id]
        self.collection = collection
        self.calibration = hash_text(f"fresh-calibration-{slot_id}")
        self.device_instance = hash_text(f"device-instance-{self.device}")
        self.layout = hash_text(f"layout-{self.device}-{self.order}")
        self.passage = scorer.PASSAGE_SHA256
        self.records: list[dict] = []
        self.sequence = 0
        self.session_start_ns = 3_000_000_000
        self.now_ns = self.session_start_ns
        self.delivery_id = 0
        self.arrivals = 0
        self.binding = scorer.comparison_binding(self.common(), self.calibration)
        self.reading_binding = scorer.reading_binding(
            self.binding, self.layout, self.passage
        )

    def common(self) -> dict:
        base_commit, base_apk = scorer.BASES[self.estimator]
        return {
            "comparison_schema": scorer.SLOT_SCHEMA,
            "protocol_id": scorer.PROTOCOL_ID,
            "protocol_manifest_sha256": scorer.PROTOCOL_SHA256,
            "slot_id": self.slot_id,
            "order_variant": self.order,
            "estimator_id": self.estimator,
            "estimator_base_commit": base_commit,
            "estimator_base_apk_sha256": base_apk,
            "collection_apk_sha256": self.collection["builds"][self.estimator][
                "comparison_apk_sha256"
            ],
            "device_instance_sha256": self.device_instance,
            "device_model": self.device,
            "screen_width_px": 1080,
            "screen_height_px": 2400,
            "density_dpi": 420,
            "display_rotation": 0,
            "coordinate_space": scorer.COORDINATE_SPACE,
            "monotonic_clock": scorer.CLOCK,
        }

    def append(self, record: dict, elapsed_ns: int, reading: bool = False) -> None:
        stamped = self.common()
        stamped.update(record)
        stamped.update({
            "calibration_sha256": self.calibration,
            "comparison_binding_sha256": self.binding,
            "record_sequence": self.sequence,
            "event_elapsed_ns": elapsed_ns,
            "reading_layout_sha256": self.layout if reading else None,
            "passage_sha256": self.passage if reading else None,
            "reading_binding_sha256": self.reading_binding if reading else None,
        })
        if reading:
            stamped.update({
                "session_id": f"comparison_{self.slot_id}",
                "timestamp_ms": 1_800_000_000_000 + elapsed_ns // 1_000_000,
                "elapsed_ms": (elapsed_ns - self.session_start_ns) / 1e6,
            })
        self.records.append(stamped)
        self.sequence += 1

    def context(self, target: dict | None) -> dict:
        return {
            "expected_checkpoint": target["id"] if target else None,
            "expected_region": target["region"] if target else None,
            "expected_line": target["line_index"] if target else None,
            "expected_target_kind": target["target_kind"] if target else None,
            "expected_target_text": target["target_text"] if target else None,
            "expected_target_start": target["target_start"] if target else None,
            "expected_target_end": target["target_end"] if target else None,
        }

    def state(self, phase: str, step: str, state: str, target=None) -> None:
        record = {
            "record_type": "protocol_state",
            "phase": phase,
            "step_id": step,
            "trial_state": state,
            "instruction": "synthetic frozen protocol state",
        }
        record.update(self.context(target))
        self.append(record, self.now_ns, True)

    @staticmethod
    def text_target(prefix: str, target: tuple[int, int, int, int]) -> dict:
        line, count, start, end = target
        return {
            f"{prefix}_valid": line >= 0 and count > 0,
            f"{prefix}_line": line,
            f"{prefix}_line_count": count,
            f"{prefix}_word_start": start,
            f"{prefix}_word_end": end,
        }

    def gaze(self, target: dict, phase: str, error_lines: int, stabilizer) -> None:
        self.delivery_id += 1
        self.arrivals += 1
        delivery_ns = self.now_ns
        raw = (
            target["line_index"] + error_lines,
            100,
            target["target_start"] if target["target_kind"] == "WORD_FIXATION" else -1,
            target["target_end"] if target["target_kind"] == "WORD_FIXATION" else -1,
        )
        stable = stabilizer.update(raw, delivery_ns // 1_000_000)
        if self.estimator == "mgazenet":
            source = {
                "record_type": "mgazenet_source",
                "phase": phase,
                "step_id": target["id"],
                "trial_state": "MEASURE",
                "delivery_id": self.delivery_id,
                "capture_elapsed_ms": delivery_ns / 1e6 - 100.0,
                "delivery_elapsed_ns": delivery_ns,
                "output_age_ms": 100.0,
                "reason": "coordinate",
                "raw_screen_x": 500.0,
                "raw_screen_y": 900.0,
                "analyzer_arrivals": self.arrivals,
                "observed_busy_drops": 0,
                "operational_expiry_ms": 500,
                "filter": "none",
            }
            source.update(self.context(None))
            self.append(source, delivery_ns, True)
        sample = {
            "record_type": "gaze_sample",
            "phase": phase,
            "step_id": target["id"],
            "trial_state": "MEASURE",
            "delivery_id": self.delivery_id,
            "delivery_elapsed_ns": delivery_ns,
            "gaze_x_screen_px": 500.0,
            "gaze_y_screen_px": 900.0,
            "effective_gaze_y_screen_px": 900.0,
            "vertical_alignment_delta_px": 0.0,
            "scroll_y": 0,
            "text_top_screen_px": 100,
            "line_count": 100,
        }
        sample.update(self.context(target))
        sample.update(self.text_target("base_target", raw))
        sample.update(self.text_target("raw_target", raw))
        sample.update(self.text_target("stable_target", stable))
        self.append(sample, delivery_ns, True)

    def build(
        self,
        error_lines: int,
        undercovered_target: str | None = None,
        partial_targets: int | None = None,
    ) -> list[dict]:
        self.append({
            "record_type": "slot_start",
            "stage": "calibration",
            "started_wall_time_ms": 1_800_000_000_000,
        }, 1_000_000_000)
        self.append({
            "record_type": "calibration_start",
            "explicit_start": True,
            "fresh_calibration_required": True,
        }, 1_000_000_000)
        self.append({
            "record_type": "calibration_end",
            "outcome": "completed",
            "calibration_technically_complete": True,
            "fit_target_count": 16,
            "accepted_rows_per_target": 45 if self.estimator == "mgazenet" else None,
            "feature_count": 258 if self.estimator == "mgazenet" else 2,
            "post_fit_check_count": 6,
            "excluded_fit_target_count": 0,
            "detailed_telemetry_enabled": False,
            "correction_applied": False,
            "spatial_result_exposed": False,
        }, 2_000_000_000)

        targets = target_plan(self.order)
        self.append({
            "record_type": "session_start",
            "schema_version": 7,
            "protocol_version": 4,
            "run_label": self.slot_id,
            "order_variant": self.order,
            "vertical_alignment_mode": "off",
            "drift_correction_active": False,
            "detailed_source_telemetry_enabled": False,
            "camera_frames_retained": False,
            "result_visibility": "offline_after_four_hashes",
        }, self.now_ns, True)
        self.now_ns += 1_000_000
        self.append({
            "record_type": "layout",
            "text_length": 1382,
            "line_count": 40,
            "line_height_px": 60.0,
            "text_size_px": 44.0,
            "viewport_width_px": 1080,
            "viewport_height_px": 1800,
        }, self.now_ns, True)
        self.now_ns += 1_000_000
        self.append({
            "record_type": "protocol_plan",
            "passage_sha256": self.passage,
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
            "targets": targets,
        }, self.now_ns, True)
        self.now_ns += 1_000_000
        self.state("SETUP", "unscored_dot_preview", "PREPARE")
        self.now_ns += 1_000_000

        for index, reference in enumerate(scorer.VERTICAL):
            self.state("VERTICAL_ALIGNMENT", reference, "ACQUIRE")
            self.now_ns += 2_000_000_000
            self.state("VERTICAL_ALIGNMENT", reference, "MEASURE")
            self.now_ns += 2_500_000_000
            self.append({
                "record_type": "vertical_alignment_reference",
                "reference_id": reference,
                "target_x_screen_px": 540.0,
                "target_y_screen_px": 400.0 + index * 600.0,
                "sample_count": 10,
                "observed_median_y_screen_px": 400.0 + index * 600.0,
                "signed_error_before_px": 0.0,
            }, self.now_ns, True)
        self.append({
            "record_type": "vertical_alignment_fit",
            "mode": "off",
            "accepted": True,
            "applied": False,
            "reason": "accepted",
            "reference_count": 3,
            "gain": 1.0,
            "intercept_px": 0.0,
        }, self.now_ns, True)
        self.now_ns += 1_000_000

        limit = len(targets) if partial_targets is None else partial_targets
        for target in targets[:limit]:
            phase = (
                "LOCALIZATION" if target["target_kind"] == "WORD_FIXATION"
                else "GUIDED_LINE_READING"
            )
            acquire_ms = 3000 if phase == "LOCALIZATION" else 2500
            measure_ms = 2500 if phase == "LOCALIZATION" else 5000
            stabilizer = scorer.Stabilizer()
            stabilizer.reset()
            self.state(phase, target["id"], "ACQUIRE", target)
            self.now_ns += acquire_ms * 1_000_000
            self.state(phase, target["id"], "MEASURE", target)
            count = 0 if target["id"] == undercovered_target else 10
            spacing_ms = 200 if phase == "LOCALIZATION" else 400
            for index in range(count):
                self.now_ns += (100 if index == 0 else spacing_ms) * 1_000_000
                self.gaze(target, phase, error_lines, stabilizer)
            measure_end = self.records[-1]["event_elapsed_ns"] if count else self.now_ns
            target_end = next(
                record["event_elapsed_ns"] for record in reversed(self.records)
                if record.get("record_type") == "protocol_state" and
                record.get("step_id") == target["id"] and
                record.get("trial_state") == "MEASURE"
            ) + measure_ms * 1_000_000
            self.now_ns = max(measure_end, target_end)

        outcome = "completed" if partial_targets is None else "interrupted"
        if self.estimator == "mgazenet":
            self.delivery_id += 1
            if outcome == "completed":
                phase, step, state = "COMPLETE", "complete", "MEASURE"
            else:
                active = next(record for record in reversed(self.records)
                              if record.get("record_type") == "protocol_state")
                phase, step, state = (
                    active["phase"], active["step_id"], active["trial_state"]
                )
            terminal_source = {
                "record_type": "mgazenet_source",
                "phase": phase,
                "step_id": step,
                "trial_state": state,
                "delivery_id": self.delivery_id,
                "capture_elapsed_ms": None,
                "delivery_elapsed_ns": self.now_ns,
                "output_age_ms": None,
                "reason": "stopped",
                "raw_screen_x": None,
                "raw_screen_y": None,
                "analyzer_arrivals": self.arrivals,
                "observed_busy_drops": 0,
                "operational_expiry_ms": 500,
                "filter": "none",
            }
            terminal_source.update(self.context(None))
            self.append(terminal_source, self.now_ns, True)
        self.append({
            "record_type": "session_end",
            "outcome": outcome,
            "spatial_result_exposed": False,
        }, self.now_ns, True)
        return self.records


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_slot(path: Path, records: list[dict]) -> None:
    raw = "".join(json.dumps(record, sort_keys=True, separators=(",", ":")) + "\n"
                  for record in records).encode()
    path.write_bytes(raw)
    Path(str(path) + ".sha256").write_text(
        f"{hashlib.sha256(raw).hexdigest()}  {path.name}\n", encoding="utf-8"
    )


def dataset(
    root: Path,
    errors: dict[str, int],
    partial_slot: str | None = None,
    undercovered_slot: str | None = None,
) -> tuple[Path, dict[str, Path]]:
    collection = collection_manifest()
    collection_path = root / "collection.json"
    write_json(collection_path, collection)
    paths = {}
    for slot_id in scorer.SLOTS:
        builder = SlotBuilder(slot_id, collection)
        records = builder.build(
            errors[slot_id],
            undercovered_target="localization_1" if slot_id == undercovered_slot else None,
            partial_targets=1 if slot_id == partial_slot else None,
        )
        path = root / f"{slot_id}.jsonl"
        write_slot(path, records)
        paths[slot_id] = path
    return collection_path, paths


class ComparisonScorerTest(unittest.TestCase):
    def score(self, root: Path, errors, **options):
        collection, slots = dataset(root, errors, **options)
        return scorer.score_comparison(PROTOCOL, collection, slots)

    def test_complete_directional_classifications(self):
        cases = {
            "consistent_mgazenet_direction": {
                slot: (0 if estimator == "mgazenet" else 2)
                for slot, (_, estimator, _) in scorer.SLOTS.items()
            },
            "consistent_current_direction": {
                slot: (2 if estimator == "mgazenet" else 0)
                for slot, (_, estimator, _) in scorer.SLOTS.items()
            },
        }
        for expected, errors in cases.items():
            with self.subTest(expected=expected), tempfile.TemporaryDirectory() as directory:
                result = self.score(Path(directory), errors)
                self.assertEqual(expected, result["classification"])
                self.assertEqual("validated_before_scoring", result["integrity"])
                self.assertTrue(all(slot["spatial_complete"] for slot in result["slots"]))
                self.assertEqual([50, 100, 200, 500], [
                    item["sensitivity_ms"]
                    for item in result["slots"][0]["delivery_target_balanced"]
                ])

    def test_hash_valid_partial_slot_is_inconclusive(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = {slot: 0 for slot in scorer.SLOTS}
            result = self.score(
                Path(directory), errors, partial_slot="a56_mgazenet_A"
            )
            self.assertEqual("inconclusive", result["classification"])
            partial = next(slot for slot in result["slots"]
                           if slot["slot_id"] == "a56_mgazenet_A")
            self.assertEqual("interrupted", partial["outcome"])

    def test_empty_target_remains_null_and_incomplete(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = {slot: 0 for slot in scorer.SLOTS}
            result = self.score(
                Path(directory), errors, undercovered_slot="g991b_current_B"
            )
            self.assertEqual("inconclusive", result["classification"])
            slot = next(item for item in result["slots"]
                        if item["slot_id"] == "g991b_current_B")
            target = next(item for item in slot["targets"]
                          if item["id"] == "localization_1")
            self.assertEqual(0, target["delivered_coordinate_count"])
            self.assertFalse(target["coverage_complete"])
            self.assertIsNone(target["stabilized_absolute_line_error"]["median"])

    def test_conflicting_phone_directions_are_mixed(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = {
                "a56_current_A": 2,
                "a56_mgazenet_A": 0,
                "g991b_mgazenet_B": 2,
                "g991b_current_B": 0,
            }
            self.assertEqual(
                "mixed", self.score(Path(directory), errors)["classification"]
            )

    def test_wrong_build_and_wrong_receipt_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            errors = {slot: 0 for slot in scorer.SLOTS}
            collection_path, paths = dataset(root, errors)
            collection = json.loads(collection_path.read_text())
            collection["builds"]["mgazenet"]["base_commit"] = "0" * 40
            write_json(collection_path, collection)
            with self.assertRaisesRegex(ValueError, "wrong mgazenet"):
                scorer.score_comparison(PROTOCOL, collection_path, paths)

            collection = collection_manifest()
            write_json(collection_path, collection)
            paths["a56_current_A"].write_bytes(
                paths["a56_current_A"].read_bytes() + b"\n"
            )
            with self.assertRaisesRegex(ValueError, "SHA receipt"):
                scorer.score_comparison(PROTOCOL, collection_path, paths)

    def test_altered_protocol_and_stabilizer_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            errors = {slot: 0 for slot in scorer.SLOTS}
            collection_path, paths = dataset(root, errors)
            changed_protocol = root / "protocol.json"
            changed_protocol.write_bytes(PROTOCOL.read_bytes() + b" ")
            with self.assertRaisesRegex(ValueError, "manifest bytes changed"):
                scorer.score_comparison(changed_protocol, collection_path, paths)

            slot_path = paths["g991b_mgazenet_B"]
            records = [json.loads(line) for line in slot_path.read_text().splitlines()]
            sample = next(record for record in records if record["record_type"] == "gaze_sample")
            sample["stable_target_line"] += 1
            write_slot(slot_path, records)
            with self.assertRaisesRegex(ValueError, "stabilizer"):
                scorer.score_comparison(PROTOCOL, collection_path, paths)

    def test_strict_json_and_invalid_event_coverage(self):
        with self.assertRaisesRegex(ValueError, "Duplicate JSON key"):
            scorer.strict_loads('{"a":1,"a":2}')
        with self.assertRaisesRegex(ValueError, "Non-finite"):
            scorer.strict_loads('{"a":NaN}')
        start, end = 0, 1_000_000_000
        result = scorer.coverage(
            [(100_000_000, True), (200_000_000, False), (700_000_000, True)],
            start,
            end,
            500,
        )
        self.assertEqual(400.0, result["covered_ms"])
        self.assertEqual(500.0, result["longest_gap_ms"])

    def test_integrity_mutations_are_rejected(self):
        cases = (
            ("a56_current_A", "session_start", lambda record: record.update(
                drift_correction_active=True
            )),
            ("a56_current_A", "protocol_plan", lambda record: record.update(
                word_measure_ms=2499
            )),
            ("g991b_current_B", "gaze_sample", lambda record: record.update(
                capture_elapsed_ms=1.0
            )),
            ("a56_mgazenet_A", "mgazenet_source", lambda record: record.update(
                output_age_ms=101.0
            )),
            ("g991b_mgazenet_B", "gaze_sample", lambda record: record.update(
                vertical_alignment_delta_px=1.0
            )),
        )
        for index, (slot_id, record_type, mutate) in enumerate(cases):
            with self.subTest(record_type=record_type), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                collection_path, paths = dataset(root, {slot: 0 for slot in scorer.SLOTS})
                records = [json.loads(line) for line in paths[slot_id].read_text().splitlines()]
                mutate(next(record for record in records if record["record_type"] == record_type))
                write_slot(paths[slot_id], records)
                with self.assertRaises(ValueError, msg=f"mutation {index} was accepted"):
                    scorer.score_comparison(PROTOCOL, collection_path, paths)

    def test_terminal_mgazenet_counters_are_required_and_reported(self):
        errors = {slot: 0 for slot in scorer.SLOTS}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            collection_path, paths = dataset(root, errors)
            result = scorer.score_comparison(PROTOCOL, collection_path, paths)
            slot = next(item for item in result["slots"]
                        if item["slot_id"] == "a56_mgazenet_A")
            self.assertGreater(slot["mgazenet_analyzer_arrivals"], 0)
            self.assertEqual(0, slot["mgazenet_observed_busy_drops"])

            for mutation in ("missing", "null", "late"):
                with self.subTest(mutation=mutation):
                    collection_path, paths = dataset(root, errors)
                    path = paths["a56_mgazenet_A"]
                    records = [json.loads(line) for line in path.read_text().splitlines()]
                    stopped_index = next(index for index, record in enumerate(records)
                                         if record.get("reason") == "stopped")
                    if mutation == "missing":
                        records.pop(stopped_index)
                    elif mutation == "null":
                        records[stopped_index]["observed_busy_drops"] = None
                    else:
                        records[stopped_index], records[-1] = records[-1], records[stopped_index]
                    for index, record in enumerate(records):
                        record["record_sequence"] = index
                        if index:
                            record["event_elapsed_ns"] = max(
                                record["event_elapsed_ns"], records[index - 1]["event_elapsed_ns"]
                            )
                            if record.get("record_type") in {
                                "session_start", "layout", "protocol_plan", "protocol_state",
                                "vertical_alignment_reference", "vertical_alignment_fit",
                                "mgazenet_source", "gaze_sample", "session_end",
                            }:
                                record["elapsed_ms"] = (
                                    record["event_elapsed_ns"] - records[3]["event_elapsed_ns"]
                                ) / 1e6
                                record["timestamp_ms"] = (
                                    1_800_000_000_000 + record["event_elapsed_ns"] // 1_000_000
                                )
                    write_slot(path, records)
                    with self.assertRaises(ValueError):
                        scorer.score_comparison(PROTOCOL, collection_path, paths)

    def test_cli_refuses_to_overwrite_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            collection_path, paths = dataset(root, {slot: 0 for slot in scorer.SLOTS})
            output = root / "result.json"
            output.write_text("preserve me", encoding="utf-8")
            arguments = [
                "newsmead_comparison_metrics.py",
                "--protocol-manifest", str(PROTOCOL),
                "--collection-manifest", str(collection_path),
                "--output", str(output),
            ]
            for slot_id, path in paths.items():
                arguments.extend(("--slot", f"{slot_id}={path}"))
            with mock.patch.object(sys, "argv", arguments), self.assertRaises(FileExistsError):
                scorer.main()
            self.assertEqual("preserve me", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
