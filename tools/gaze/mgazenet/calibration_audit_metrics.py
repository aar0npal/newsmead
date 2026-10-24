#!/usr/bin/env python3
"""Validate and summarize numeric-only MGazeNet calibration-observability v3 evidence."""
import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path

SCHEMA = "mgazenet_calibration_audit_v3"
PROTOCOL = "mgazenet_calibration_observability_v3"
METHOD = "leave_one_complete_target_group_out"
GRID = (.1, .3667, .6333, .9)
VALIDATION = ((.5, .5), (.25, .25), (.75, .25), (.25, .75), (.75, .75))
SVR = "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def number(value):
    require(type(value) in (int, float) and math.isfinite(value), "Expected finite number")
    return value


def point(value):
    require(isinstance(value, list) and len(value) == 2, "Expected 2-D point")
    return tuple(number(item) for item in value)


def close_point(actual, expected, tolerance=1e-3):
    return all(abs(a-b) <= tolerance for a, b in zip(point(actual), expected))


def percentile(values, fraction):
    ordered = sorted(values)
    require(ordered, "Cannot summarize empty values")
    position = (len(ordered)-1)*fraction
    lower = int(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower]+(ordered[upper]-ordered[lower])*(position-lower)


def distribution(values):
    if not values:
        return {"count": 0, "min": None, "p05": None, "median": None,
                "mean": None, "p95": None, "max": None}
    return {"count": len(values), "min": min(values), "p05": percentile(values, .05),
            "median": percentile(values, .5), "mean": statistics.mean(values),
            "p95": percentile(values, .95), "max": max(values)}


def compact_distribution(values):
    if not values:
        return {"count": 0, "median": None, "p95": None, "max": None}
    return {"count": len(values), "median": percentile(values, .5),
            "p95": percentile(values, .95), "max": max(values)}


def expected_point(viewport, fraction):
    left, top, right, bottom = viewport
    x, y = fraction
    return left+x*(right-left), top+y*(bottom-top)


def same_numbers(actual, expected, tolerance=1e-7):
    return actual.keys() == expected.keys() and all(
        actual[key] == expected[key] if expected[key] is None
        else abs(number(actual[key])-expected[key]) <= tolerance
        for key in expected)


def error_summary(samples, target, screen, line_height):
    dx = [sample[0]-target[0] for sample in samples]
    dy = [sample[1]-target[1] for sample in samples]
    return {
        "coordinate_samples": len(samples),
        "signed_dx_px": distribution(dx),
        "signed_dy_px": distribution(dy),
        "absolute_x_px": distribution([abs(value) for value in dx]),
        "absolute_y_px": distribution([abs(value) for value in dy]),
        "euclidean_px": distribution([math.hypot(x, y) for x, y in zip(dx, dy)]),
        "absolute_vertical_lines": distribution([abs(value)/line_height for value in dy]),
        "absolute_x_normalized": distribution([abs(value)/screen[0] for value in dx]),
        "absolute_y_normalized": distribution([abs(value)/screen[1] for value in dy]),
    }


def target_balanced(items):
    fields = ("absolute_x_px", "absolute_y_px", "euclidean_px", "absolute_vertical_lines",
              "absolute_x_normalized", "absolute_y_normalized")
    return {
        "contributing_target_count": len(items),
        "mean_target_median": {field: statistics.mean(item[field]["median"] for item in items) for field in fields},
        "mean_target_p95": {field: statistics.mean(item[field]["p95"] for item in items) for field in fields},
        "mean_target_max": {field: statistics.mean(item[field]["max"] for item in items) for field in fields},
        "mean_target_signed_bias_px": {
            "x": statistics.mean(item["signed_dx_px"]["mean"] for item in items),
            "y": statistics.mean(item["signed_dy_px"]["mean"] for item in items),
        },
    }


def evaluate(report):
    require(report.get("schema") == SCHEMA and report.get("outcome") == "complete",
            "Only complete calibration-audit v3 evidence can be scored")
    require(report.get("protocol_id") == PROTOCOL and report.get("audit_method") == METHOD,
            "Unknown protocol or audit method")
    require(report.get("evidence_kind") in ("recorded", "synthetic_contract"), "Unknown evidence kind")
    require(report.get("coordinate_space") == "physical_screen_px" and
            report.get("clock") == "shared_monotonic_ms", "Changed coordinate or clock contract")
    for key in ("session_id", "device_id", "pipeline_id", "calibration_id"):
        require(isinstance(report.get(key), str) and report[key].strip(), "Missing provenance: " + key)
    require(report.get("accuracy_gate_pass") is None and
            report.get("promotion_decision") == "not_evaluated", "Audit must not assign a decision")
    for key in ("active_tracker_access", "calibration_store_access", "camera_frames_retained",
                "features_retained", "personal_model_retained", "model_correction_applied"):
        require(report.get(key) is False, "Unexpected access, retention, or correction: " + key)

    required = ("pipeline_json", "calibration_manifest_json", "calibration_manifest_sha256",
                "calibration_manifest")
    require(all(key in report for key in required), "Complete manifest fields required")
    pipeline_json = report["pipeline_json"]
    manifest_json = report["calibration_manifest_json"]
    require(isinstance(pipeline_json, str) and isinstance(manifest_json, str), "Invalid encoded manifests")
    require(hashlib.sha256(pipeline_json.encode()).hexdigest() == report["pipeline_id"],
            "Pipeline identity mismatch")
    digest = hashlib.sha256(manifest_json.encode()).hexdigest()
    require(digest == report["calibration_manifest_sha256"] and
            report["calibration_id"] == report["session_id"]+":"+digest,
            "Calibration identity mismatch")
    pipeline = json.loads(pipeline_json)
    manifest = json.loads(manifest_json)
    require(manifest == report["calibration_manifest"] and manifest.get("pipeline") == pipeline,
            "Manifest encoding mismatch")
    require(manifest.get("protocol") == PROTOCOL and manifest.get("device") == report["device_id"],
            "Manifest provenance mismatch")
    if report["evidence_kind"] == "recorded":
        require(pipeline.get("model_sha256") == "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96" and
                pipeline.get("face_landmarker_sha256") == "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                "Unexpected model or localizer asset")

    screen = manifest.get("screen_px")
    viewport = manifest.get("viewport_screen_px")
    require(isinstance(screen, list) and len(screen) == 2 and all(type(value) is int and value > 0 for value in screen),
            "Invalid screen geometry")
    require(isinstance(viewport, list) and len(viewport) == 4 and all(type(value) in (int, float) and math.isfinite(value) for value in viewport),
            "Invalid viewport geometry")
    left, top, right, bottom = viewport
    require(0 <= left < right <= screen[0] and 0 <= top < bottom <= screen[1], "Viewport outside screen")
    line_height = number(manifest.get("line_height_px"))
    require(line_height > 0, "Invalid line height")
    require(manifest.get("coordinate_space") == "physical_screen_px" and
            manifest.get("label_space") == "physical_screen_fractions" and
            manifest.get("fit_geometry_source") == "NewsMead 16-point calibration structure" and
            manifest.get("fit_grid_fractions") == list(GRID) and
            manifest.get("fit_grid_order") == "row_major_top_to_bottom_left_to_right",
            "Changed 16-point geometry contract")
    require(manifest.get("fit_settle_ms") == 1500 and
            manifest.get("fit_samples_per_target") == 45 and
            manifest.get("fit_wait_ms") == 500 and manifest.get("fit_timeout_ms") == 30000 and
            manifest.get("verification_settle_ms") == 3000 and
            manifest.get("verification_measure_ms") == 2500 and
            manifest.get("verification_drain_ms") == 250 and
            manifest.get("eye_area_rule") == "both_pixel_polygon_areas_strictly_above_10" and
            manifest.get("filter") == "none" and manifest.get("correction") == "none" and
            manifest.get("svr") == SVR, "Changed collection or model contract")
    require(manifest.get("audit_method") == METHOD and
            manifest.get("audit_training_groups_per_fold") == 15 and
            manifest.get("audit_rows_per_group") == 45, "Changed target-group isolation contract")
    training_digest = manifest.get("training_digest")
    require(manifest.get("training_rows") == 720 and isinstance(training_digest, str) and
            len(training_digest) == 64 and all(char in "0123456789abcdef" for char in training_digest),
            "Incomplete training provenance")

    fit_fractions = [(.5, .5)]+[(x, y) for y in GRID for x in GRID]
    fit_ids = ["practice"]+[f"fit_{index}" for index in range(1, 17)]
    fit_targets = manifest.get("fit_targets")
    require(isinstance(fit_targets, list) and len(fit_targets) == 17, "Missing fit targets")
    for index, (item, target_id, fraction) in enumerate(zip(fit_targets, fit_ids, fit_fractions)):
        require(item.get("id") == target_id and item.get("practice") is (index == 0) and
                item.get("fraction") == list(fraction) and
                close_point(item.get("point_px"), expected_point(viewport, fraction)),
                "Changed fit target identity or geometry")
    require(report.get("fit_block_ids") == fit_ids[1:], "Fit IDs do not match manifest")
    require(manifest.get("drift_repeat_fit_id") == "fit_6", "Changed near-centre repeat target")

    verification_fractions = [fit_fractions[6]]+list(VALIDATION)
    verification_ids = ["drift_repeat_fit_6"]+[f"validation_{index}" for index in range(1, 6)]
    verification_roles = ["drift_repeat"]+["held_out_validation"]*5
    verification_targets = manifest.get("verification_targets")
    require(isinstance(verification_targets, list) and len(verification_targets) == 6,
            "Missing verification targets")
    for item, target_id, role, fraction in zip(verification_targets, verification_ids,
                                               verification_roles, verification_fractions):
        require(item.get("id") == target_id and item.get("role") == role and
                item.get("fraction") == list(fraction) and
                close_point(item.get("point_px"), expected_point(viewport, fraction)),
                "Changed verification target identity or geometry")
    require(all(fraction not in fit_fractions[1:] for fraction in VALIDATION),
            "Held-out validation overlaps the fit grid")

    fit_points = report.get("fit_points")
    require(isinstance(fit_points, list) and len(fit_points) == 17 and
            [item.get("id") for item in fit_points] == fit_ids, "Changed fit-point record")
    for index, item in enumerate(fit_points):
        shown = number(item.get("shown_ms"))
        first = number(item.get("first_accepted_capture_ms"))
        completed = number(item.get("completed_output_ms"))
        require(item.get("practice") is (index == 0) and item.get("accepted") == 45 and
                first >= shown+1500 and completed >= first and
                abs(number(item.get("collection_elapsed_ms_from_window_open"))-(completed-(shown+1500))) <= 1e-7,
                "Incomplete fit target timing")
        rejected = item.get("rejected")
        require(isinstance(rejected, dict) and all(isinstance(key, str) and key and
                type(value) is int and value >= 0 for key, value in rejected.items()), "Invalid rejection counts")
        ages = item.get("output_age_ms")
        require(isinstance(ages, dict) and ages.get("count") == 45 and
                0 <= number(ages.get("median")) <= number(ages.get("p95")) <= number(ages.get("max")),
                "Invalid accepted-sample output ages")
        dispersion_value = item.get("within_target_feature_dispersion_rms")
        require(dispersion_value is None if index == 0 else number(dispersion_value) >= 0,
                "Invalid feature-dispersion scalar")

    folds = report.get("loo_folds")
    require(isinstance(folds, list) and len(folds) == 16 and
            [fold.get("held_out_id") for fold in folds] == fit_ids[1:], "Missing or reordered LOO folds")
    fold_results = []
    previous_capture = -1
    previous_output = -1
    for index, fold in enumerate(folds, start=1):
        target = expected_point(viewport, fit_fractions[index])
        require(close_point(fold.get("target_px"), target), "LOO target geometry mismatch")
        samples = fold.get("samples")
        require(isinstance(samples, list) and len(samples) == 45, "Each LOO fold must contain 45 held-out rows")
        coordinates = []
        ages = []
        for sample in samples:
            capture = number(sample.get("capture_ms"))
            output = number(sample.get("output_ms"))
            require(capture > previous_capture and output > previous_output and output >= capture,
                    "Nonmonotonic LOO sample clocks")
            coordinate = point(sample.get("point_px"))
            previous_capture, previous_output = capture, output
            coordinates.append(coordinate)
            ages.append(output-capture)
        fit_item = fit_points[index]
        require(abs(samples[0]["capture_ms"]-fit_item["first_accepted_capture_ms"]) <= 1e-7 and
                abs(samples[-1]["output_ms"]-fit_item["completed_output_ms"]) <= 1e-7 and
                same_numbers(fit_item["output_age_ms"], compact_distribution(ages)),
                "Fit timing summary does not match LOO rows")
        summary = error_summary(coordinates, target, screen, line_height)
        summary["held_out_id"] = fold["held_out_id"]
        fold_results.append(summary)

    blocks = report.get("verification_blocks")
    require(isinstance(blocks, list) and len(blocks) == 6 and
            [block.get("id") for block in blocks] == verification_ids, "Changed verification block order")
    verification_results = []
    previous_end = -1
    for index, block in enumerate(blocks):
        target = expected_point(viewport, verification_fractions[index])
        shown = number(block.get("shown_ms"))
        start = number(block.get("start_ms"))
        end = number(block.get("end_ms"))
        require(block.get("role") == verification_roles[index] and close_point(block.get("target_px"), target),
                "Verification block does not match manifest")
        require(start == shown+3000 and end == start+2500 and start >= previous_end,
                "Changed or overlapping verification timing")
        samples = block.get("samples")
        require(isinstance(samples, list), "Invalid verification samples")
        coordinates = []
        ages = []
        null_samples = 0
        for sample in samples:
            capture = number(sample.get("capture_ms"))
            output = number(sample.get("output_ms"))
            require(start <= capture < end and output >= capture and
                    capture > previous_capture and output > previous_output,
                    "Invalid verification sample clocks")
            previous_capture, previous_output = capture, output
            if sample.get("point_px") is None:
                null_samples += 1
                require(isinstance(sample.get("reason"), str) and sample["reason"] != "coordinate",
                        "Null verification result needs a rejection reason")
            else:
                require(sample.get("reason") == "coordinate", "Coordinate result has wrong reason")
                coordinates.append(point(sample["point_px"]))
            ages.append(output-capture)
        result = error_summary(coordinates, target, screen, line_height) if coordinates else {
            "coordinate_samples": 0
        }
        result.update(id=block["id"], role=block["role"], received_samples=len(samples),
                      null_samples=null_samples, output_age_ms=distribution(ages))
        verification_results.append(result)
        previous_end = end

    contributing = [item for item in verification_results if item["coordinate_samples"]]
    held_out = [item for item in verification_results[1:] if item["coordinate_samples"]]
    return {
        "schema": "mgazenet_calibration_audit_summary_v3",
        "evidence_kind": report["evidence_kind"],
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
        "scope": "Calibration observability and instructed stationary verification; not natural-reading accuracy",
        "provenance": {key: report[key] for key in
                       ("protocol_id", "session_id", "device_id", "pipeline_id", "calibration_id")},
        "loo": {"folds": fold_results, "target_balanced": target_balanced(fold_results)},
        "verification": {
            "blocks": verification_results,
            "blocks_without_coordinates": sum(item["coordinate_samples"] == 0 for item in verification_results),
            "target_balanced": target_balanced(contributing) if contributing else None,
            "held_out_validation_target_balanced": target_balanced(held_out) if held_out else None,
        },
        "limitations": [
            "LOO rows are adjacent frames from one instructed target, but every fold omits the complete target group.",
            "Feature dispersion is descriptive model-input variation, not gaze error.",
            "Declared targets do not independently verify fixation or participant compliance.",
            "No correction, pass threshold, promotion decision, reading claim, or population claim is computed.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = json.loads(args.input.read_text(encoding="utf-8"))
    result = evaluate(report)
    result["input_sha256"] = hashlib.sha256(args.input.read_bytes()).hexdigest()
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print("Scored 16 target-isolated folds and 6 verification blocks; no gate or promotion decision")


if __name__ == "__main__":
    main()
