#!/usr/bin/env python3
"""Validate and score the frozen MGazeNet direct-screen confirmation record."""
import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path

from accuracy_metrics import score_block, spatial_aggregate
from calibration_audit_metrics import (
    compact_distribution,
    error_summary,
    expected_point,
    number,
    point,
    target_balanced,
)


SCHEMA = "mgazenet_direct_validation_confirmation_v1"
PROTOCOL = "mgazenet_direct_validation_confirmation_v1"
METHOD = "leave_one_complete_target_group_out"
SCREEN_METHOD = "direct_five_point_vertical_candidate_v1"
GRID = (.1, .3667, .6333, .9)
SCREEN_FRACTIONS = ((.5, .5), (.25, .25), (.75, .25), (.25, .75), (.75, .75))
CONFIRMATION_LOCATIONS = (2, 8, 13, 15, 22, 24, 31, 33, 38, 44)
MIN_COORDINATES = 10
MAX_MEAN_MEDIAN_LINES = 1.0
MAX_WORST_MEDIAN_LINES = 1.2
SVR = "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def close_point(actual, expected, tolerance=1e-3):
    require(isinstance(actual, (list, tuple)) and len(actual) == 2,
            "Expected 2-D point")
    return all(abs(number(left)-right) <= tolerance for left, right in zip(actual, expected))


def equivalent(actual, expected, tolerance=1e-7):
    if isinstance(expected, dict):
        return isinstance(actual, dict) and actual.keys() == expected.keys() and all(
            equivalent(actual[key], value, tolerance) for key, value in expected.items())
    if isinstance(expected, list):
        return isinstance(actual, list) and len(actual) == len(expected) and all(
            equivalent(left, right, tolerance) for left, right in zip(actual, expected))
    if type(expected) in (int, float):
        return type(actual) in (int, float) and math.isfinite(actual) and abs(actual-expected) <= tolerance
    return actual == expected


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered)-1)*fraction
    lower = int(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower]+(ordered[upper]-ordered[lower])*(position-lower)


def _validate_identity(report):
    require(report.get("schema") == SCHEMA and report.get("outcome") == "complete",
            "Only a complete confirmation v1 record can be scored")
    require(report.get("protocol_id") == PROTOCOL and report.get("audit_method") == METHOD,
            "Unknown confirmation protocol or audit method")
    require(report.get("evidence_kind") in ("recorded", "synthetic_contract"),
            "Unknown evidence kind")
    require(report.get("coordinate_space") == "physical_screen_px" and
            report.get("clock") == "shared_monotonic_ms",
            "Changed coordinate or clock contract")
    for key in ("session_id", "device_id", "pipeline_id", "calibration_id"):
        require(isinstance(report.get(key), str) and report[key].strip(),
                "Missing provenance: " + key)
    require(report.get("confirmation_accuracy_pass") is None and
            report.get("accuracy_gate_pass") is None and
            report.get("promotion_decision") == "not_evaluated",
            "Raw confirmation record assigned an accuracy or promotion decision")
    require(report.get("screen_result_hidden_until_terminal") is True,
            "Screen result was not declared hidden until the terminal record")
    for key in ("active_tracker_access", "calibration_store_access", "camera_frames_retained",
                "features_retained", "personal_model_retained", "model_correction_applied"):
        require(report.get(key) is False, "Unexpected access, retention, or correction: " + key)


def _validate_manifest(report):
    required = ("pipeline_json", "calibration_manifest_json", "calibration_manifest_sha256",
                "calibration_manifest")
    require(all(key in report for key in required), "Complete manifest fields required")
    pipeline_json = report["pipeline_json"]
    manifest_json = report["calibration_manifest_json"]
    require(isinstance(pipeline_json, str) and isinstance(manifest_json, str),
            "Invalid encoded manifests")
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
        require(pipeline.get("model_sha256") ==
                "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96" and
                pipeline.get("face_landmarker_sha256") ==
                "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                "Unexpected model or localizer asset")
    return manifest


def _validate_geometry_and_constants(report, manifest):
    screen = manifest.get("screen_px")
    viewport = manifest.get("viewport_screen_px")
    require(isinstance(screen, list) and len(screen) == 2 and
            all(type(value) is int and value > 0 for value in screen), "Invalid screen geometry")
    require(isinstance(viewport, list) and len(viewport) == 4 and
            all(type(value) in (int, float) and math.isfinite(value) for value in viewport),
            "Invalid viewport geometry")
    left, top, right, bottom = viewport
    require(0 <= left < right <= screen[0] and 0 <= top < bottom <= screen[1],
            "Viewport outside screen")
    line_height = number(manifest.get("line_height_px"))
    require(line_height > 0, "Invalid line height")
    require(manifest.get("coordinate_space") == "physical_screen_px" and
            manifest.get("label_space") == "physical_screen_fractions" and
            manifest.get("fit_geometry_source") == "NewsMead 16-point calibration structure" and
            manifest.get("fit_grid_fractions") == list(GRID) and
            manifest.get("fit_grid_order") == "row_major_top_to_bottom_left_to_right",
            "Changed fit geometry contract")
    require(manifest.get("fit_settle_ms") == 1500 and
            manifest.get("fit_samples_per_target") == 45 and
            manifest.get("fit_wait_ms") == 500 and manifest.get("fit_timeout_ms") == 30000 and
            manifest.get("screen_settle_ms") == 3000 and
            manifest.get("screen_measure_ms") == 2500 and manifest.get("screen_drain_ms") == 250 and
            manifest.get("confirmation_settle_ms") == 3000 and
            manifest.get("confirmation_measure_ms") == 2500 and
            manifest.get("confirmation_drain_ms") == 250 and
            manifest.get("eye_area_rule") == "both_pixel_polygon_areas_strictly_above_10" and
            manifest.get("filter") == "none" and manifest.get("correction") == "none" and
            manifest.get("svr") == SVR, "Changed timing or model contract")
    require(manifest.get("audit_method") == METHOD and
            manifest.get("audit_training_groups_per_fold") == 15 and
            manifest.get("audit_rows_per_group") == 45, "Changed whole-target audit contract")
    require(manifest.get("screen_method") == SCREEN_METHOD and
            manifest.get("screen_minimum_coordinates_per_target") == MIN_COORDINATES and
            manifest.get("screen_maximum_mean_target_median_absolute_vertical_lines") ==
                MAX_MEAN_MEDIAN_LINES and
            manifest.get("screen_maximum_worst_target_median_absolute_vertical_lines") ==
                MAX_WORST_MEDIAN_LINES and
            manifest.get("screen_result_visibility") == "sealed_and_hidden_until_terminal_record" and
            manifest.get("continue_after_screen_result") is True,
            "Changed screen threshold, visibility, or continuation contract")
    training_digest = manifest.get("training_digest")
    require(manifest.get("training_rows") == 720 and isinstance(training_digest, str) and
            len(training_digest) == 64 and all(char in "0123456789abcdef" for char in training_digest),
            "Incomplete training provenance")
    return screen, viewport, line_height


def _validate_fit_and_loo(report, manifest, screen, viewport, line_height):
    fit_fractions = [(.5, .5)]+[(x, y) for y in GRID for x in GRID]
    fit_ids = ["practice"]+[f"fit_{index}" for index in range(1, 17)]
    targets = manifest.get("fit_targets")
    require(isinstance(targets, list) and len(targets) == 17, "Missing fit targets")
    for index, (item, target_id, fraction) in enumerate(zip(targets, fit_ids, fit_fractions)):
        require(item.get("id") == target_id and item.get("practice") is (index == 0) and
                item.get("fraction") == list(fraction) and
                close_point(item.get("point_px"), expected_point(viewport, fraction)),
                "Changed fit target identity or geometry")
    require(report.get("fit_block_ids") == fit_ids[1:], "Fit IDs do not match manifest")

    fit_points = report.get("fit_points")
    require(isinstance(fit_points, list) and len(fit_points) == 17 and
            [item.get("id") for item in fit_points] == fit_ids, "Changed fit-point record")
    for index, item in enumerate(fit_points):
        shown = number(item.get("shown_ms"))
        first = number(item.get("first_accepted_capture_ms"))
        completed = number(item.get("completed_output_ms"))
        require(item.get("practice") is (index == 0) and item.get("accepted") == 45 and
                first >= shown+1500 and completed >= first and
                abs(number(item.get("collection_elapsed_ms_from_window_open"))-
                    (completed-(shown+1500))) <= 1e-7,
                "Incomplete fit target timing")
        rejected = item.get("rejected")
        require(isinstance(rejected, dict) and all(isinstance(key, str) and key and
                type(value) is int and value >= 0 for key, value in rejected.items()),
                "Invalid fit rejection counts")
        ages = item.get("output_age_ms")
        require(isinstance(ages, dict) and ages.get("count") == 45 and
                0 <= number(ages.get("median")) <= number(ages.get("p95")) <= number(ages.get("max")),
                "Invalid fit output ages")
        dispersion = item.get("within_target_feature_dispersion_rms")
        require(dispersion is None if index == 0 else number(dispersion) >= 0,
                "Invalid fit feature dispersion")

    folds = report.get("loo_folds")
    require(isinstance(folds, list) and len(folds) == 16 and
            [fold.get("held_out_id") for fold in folds] == fit_ids[1:],
            "Missing or reordered LOO folds")
    fold_results = []
    previous_capture = -1
    previous_output = -1
    for index, fold in enumerate(folds, start=1):
        target = expected_point(viewport, fit_fractions[index])
        require(close_point(fold.get("target_px"), target), "LOO target geometry mismatch")
        samples = fold.get("samples")
        require(isinstance(samples, list) and len(samples) == 45,
                "Each LOO fold must contain 45 held-out rows")
        coordinates = []
        ages = []
        for sample in samples:
            capture = number(sample.get("capture_ms"))
            output = number(sample.get("output_ms"))
            require(capture > previous_capture and output > previous_output and output >= capture,
                    "Nonmonotonic LOO clocks")
            coordinates.append(point(sample.get("point_px")))
            ages.append(output-capture)
            previous_capture, previous_output = capture, output
        fit_item = fit_points[index]
        require(abs(samples[0]["capture_ms"]-fit_item["first_accepted_capture_ms"]) <= 1e-7 and
                abs(samples[-1]["output_ms"]-fit_item["completed_output_ms"]) <= 1e-7 and
                equivalent(fit_item["output_age_ms"], compact_distribution(ages)),
                "Fit timing does not match LOO rows")
        result = error_summary(coordinates, target, screen, line_height)
        result["held_out_id"] = fold["held_out_id"]
        fold_results.append(result)
    return fit_fractions, fold_results, previous_capture, previous_output


def _sample_block(block, target, screen, line_height, previous_capture, previous_output):
    samples = block.get("samples")
    require(isinstance(samples, list), "Expected retained empty or populated sample list")
    coordinates = []
    ages = []
    nulls = 0
    for sample in samples:
        capture = number(sample.get("capture_ms"))
        output = number(sample.get("output_ms"))
        require(block["start_ms"] <= capture < block["end_ms"] and output >= capture and
                capture > previous_capture and output > previous_output,
                "Invalid or nonmonotonic live sample clocks")
        previous_capture, previous_output = capture, output
        if sample.get("point_px") is None:
            nulls += 1
            require(isinstance(sample.get("reason"), str) and sample["reason"] != "coordinate",
                    "Null output requires a rejection reason")
        else:
            require(sample.get("reason") == "coordinate", "Coordinate output has wrong reason")
            coordinates.append(point(sample["point_px"]))
        ages.append(output-capture)
    result = error_summary(coordinates, target, screen, line_height) if coordinates else {
        "coordinate_samples": 0,
    }
    result.update(received_samples=len(samples), null_samples=nulls,
                  output_age_ms=(None if not ages else {
                      "count": len(ages), "median": percentile(ages, .5),
                      "p95": percentile(ages, .95), "max": max(ages),
                  }))
    return result, previous_capture, previous_output


def _screen(report, manifest, screen, viewport, line_height, previous_capture, previous_output):
    ids = [f"screen_validation_{index}" for index in range(1, 6)]
    targets = manifest.get("screen_targets")
    blocks = report.get("screen_blocks")
    require(isinstance(targets, list) and len(targets) == 5 and
            isinstance(blocks, list) and len(blocks) == 5,
            "Exactly five screen targets and blocks are required")
    results = []
    previous_end = -1
    for item, block, target_id, fraction in zip(targets, blocks, ids, SCREEN_FRACTIONS):
        expected = expected_point(viewport, fraction)
        require(item.get("id") == target_id and item.get("role") == "candidate_screen" and
                item.get("fraction") == list(fraction) and
                close_point(item.get("point_px"), expected), "Changed screen target")
        require(block.get("id") == target_id and block.get("role") == "candidate_screen" and
                close_point(block.get("target_px"), expected) and
                number(block.get("line_height_px")) == line_height,
                "Screen block does not match manifest")
        shown = number(block.get("shown_ms"))
        start = number(block.get("start_ms"))
        end = number(block.get("end_ms"))
        require(start == shown+3000 and end == start+2500 and start >= previous_end,
                "Changed or overlapping screen timing")
        block["start_ms"], block["end_ms"] = start, end
        result, previous_capture, previous_output = _sample_block(
            block, expected, screen, line_height, previous_capture, previous_output)
        result.update(id=target_id, role="candidate_screen")
        results.append(result)
        previous_end = end

    small_targets = [{
        "id": result["id"],
        "coordinate_samples": result["coordinate_samples"],
        "median_absolute_vertical_lines": result.get("absolute_vertical_lines", {}).get("median"),
        "p95_absolute_vertical_lines": result.get("absolute_vertical_lines", {}).get("p95"),
        "max_absolute_vertical_lines": result.get("absolute_vertical_lines", {}).get("max"),
    } for result in results]
    contributing = [item for item in small_targets
                    if item["median_absolute_vertical_lines"] is not None]
    medians = [item["median_absolute_vertical_lines"] for item in contributing]
    p95s = [item["p95_absolute_vertical_lines"] for item in contributing]
    minimum = min(item["coordinate_samples"] for item in small_targets)
    all_five = len(contributing) == 5
    mean_median = statistics.mean(medians) if medians else None
    mean_p95 = statistics.mean(p95s) if p95s else None
    worst = max(medians) if medians else None
    checks = {
        "all_five_targets_contribute": all_five,
        "minimum_coordinates": minimum >= MIN_COORDINATES,
        "aggregate_vertical": mean_median is not None and mean_median <= MAX_MEAN_MEDIAN_LINES,
        "regional_vertical": all_five and worst is not None and worst <= MAX_WORST_MEDIAN_LINES,
    }
    computed = {
        "method": SCREEN_METHOD,
        "sealed_ms": number(report.get("sealed_screen_result", {}).get("sealed_ms")),
        "targets": small_targets,
        "minimum_coordinate_samples": minimum,
        "mean_target_median_absolute_vertical_lines": mean_median,
        "mean_target_p95_absolute_vertical_lines": mean_p95,
        "worst_target_median_absolute_vertical_lines": worst,
        "checks": checks,
        "screen_candidate_pass": all(checks.values()),
    }
    sealed = report.get("sealed_screen_result")
    require(isinstance(sealed, dict) and equivalent(sealed, computed),
            "Sealed on-device screen result does not reproduce")
    require(sealed["sealed_ms"] >= blocks[-1]["end_ms"]+250 and
            report.get("screen_candidate_pass") is computed["screen_candidate_pass"],
            "Screen was not sealed after its drain or top-level result changed")
    return results, computed, previous_capture, previous_output


def _grid_point(viewport, index):
    column = (index-1) % 9
    row = (index-1) // 9
    fraction = (50/1920+column*(1820/1920)/8,
                50/1080+row*(980/1080)/4)
    return expected_point(viewport, fraction)


def _confirmation(report, manifest, screen_points, screen, viewport, line_height,
                  previous_capture, previous_output, sealed_ms):
    plans = {
        "forward_then_reverse": ("forward", "reverse"),
        "reverse_then_forward": ("reverse", "forward"),
    }
    order = report.get("validation_order")
    require(order in plans and manifest.get("validation_order") == order and
            manifest.get("sweep_directions") == list(plans[order]) and
            manifest.get("confirmation_locations") == list(CONFIRMATION_LOCATIONS),
            "Changed confirmation order or locations")
    expected_grids = [grid for direction in plans[order]
                      for grid in (CONFIRMATION_LOCATIONS if direction == "forward"
                                   else tuple(reversed(CONFIRMATION_LOCATIONS)))]
    expected_directions = [direction for direction in plans[order]
                           for _ in CONFIRMATION_LOCATIONS]
    blocks = report.get("confirmation_blocks")
    targets = manifest.get("confirmation_targets")
    require(isinstance(blocks, list) and len(blocks) == 20 and
            isinstance(targets, list) and len(targets) == 20,
            "Exactly twenty confirmation blocks are required")
    lines = blocks[0].get("lines")
    require(isinstance(lines, list) and len(lines) >= 5 and
            [line.get("index") for line in lines] == list(range(len(lines))),
            "Invalid confirmation line geometry")
    boxes = [tuple(number(value) for value in line["rect_px"]) for line in lines]
    scored = []
    previous_end = -1
    unique_points = {}
    for position, (block, manifest_target, grid_index, direction) in enumerate(
            zip(blocks, targets, expected_grids, expected_directions)):
        sweep = position//10+1
        within = position%10+1
        original = _grid_point(viewport, grid_index)
        line = min(range(len(boxes)), key=lambda index:
            abs((boxes[index][1]+boxes[index][3])/2-original[1]))
        left, top, right, bottom = boxes[line]
        expected = (min(max(original[0], left+(right-left)*.02), right-(right-left)*.02),
                    (top+bottom)/2)
        expected_id = f"confirmation_sweep_{sweep}_test_{grid_index}"
        expected_location = f"test_{grid_index}"
        metadata_ok = (
            block.get("id") == expected_id and block.get("role") == "independent_confirmation" and
            block.get("task") == "stationary_point" and
            block.get("location_id") == expected_location and block.get("grid_index") == grid_index and
            block.get("sweep") == sweep and block.get("sweep_direction") == direction and
            block.get("order_in_sweep") == within and block.get("region") == f"grid_{grid_index}" and
            block.get("target_line_index") == line and close_point(block.get("target_px"), expected) and
            block.get("lines") == lines and block.get("viewport_screen_px") == list(viewport) and
            number(block.get("line_height_px")) == line_height)
        require(metadata_ok, "Changed confirmation block identity, geometry, or order")
        expected_manifest = {
            "id": expected_id, "location_id": expected_location, "grid_index": grid_index,
            "sweep": sweep, "sweep_direction": direction, "order_in_sweep": within,
            "point_px": list(point(block["target_px"])), "target_line_index": line,
        }
        require(equivalent(manifest_target, expected_manifest),
                "Confirmation manifest target mismatch")
        unique_points.setdefault(expected_location, point(block["target_px"]))
        require(close_point(unique_points[expected_location], expected),
                "Repeated confirmation location changed")
        shown = number(block.get("shown_ms"))
        start = number(block.get("start_ms"))
        end = number(block.get("end_ms"))
        require(start == shown+3000 and end == start+2500 and start >= previous_end and
                (position > 0 or shown >= sealed_ms),
                "Changed, overlapping, or pre-seal confirmation timing")
        block["start_ms"], block["end_ms"] = start, end
        normalized = dict(block)
        normalized["role"] = "held_out"
        for sample in block.get("samples", []):
            capture = number(sample.get("capture_ms"))
            output = number(sample.get("output_ms"))
            require(start <= capture < end and output >= capture and
                    capture > previous_capture and output > previous_output,
                    "Invalid or nonmonotonic confirmation sample clocks")
            previous_capture, previous_output = capture, output
            require((sample.get("point_px") is None and
                     isinstance(sample.get("reason"), str) and sample["reason"] != "coordinate") or
                    (sample.get("point_px") is not None and sample.get("reason") == "coordinate"),
                    "Confirmation coordinate/reason mismatch")
        result = score_block(normalized, 500)
        result["role"] = "independent_confirmation"
        scored.append(result)
        previous_end = end

    require(len(unique_points) == 10 and len(set(unique_points.values())) == 10,
            "Confirmation locations are not ten distinct repeated points")
    require(not any(close_point(value, fit) for value in unique_points.values()
                    for fit in [expected_point(viewport, (x, y)) for y in GRID for x in GRID]) and
            not any(close_point(value, screen_point) for value in unique_points.values()
                    for screen_point in screen_points),
            "Fit/screen/confirmation coordinate overlap")

    locations = []
    bias_changes = []
    for grid_index in CONFIRMATION_LOCATIONS:
        location_id = f"test_{grid_index}"
        raw_repeated = [block for block in blocks if block["location_id"] == location_id]
        scored_repeated = [block for block in scored if block["location_id"] == location_id]
        aggregate = spatial_aggregate(raw_repeated)
        locations.append({"location_id": location_id, **aggregate})
        first, second = sorted(scored_repeated, key=lambda item: item["sweep"])
        first_bias, second_bias = first["signed_mean_bias_px"], second["signed_mean_bias_px"]
        bias_changes.append({
            "location_id": location_id,
            "first_sweep_direction": first["sweep_direction"],
            "second_sweep_direction": second["sweep_direction"],
            "contributing_sweeps": int(first_bias["x"] is not None)+int(second_bias["x"] is not None),
            "second_minus_first_mean_bias_px": {
                "x": (second_bias["x"]-first_bias["x"]
                      if first_bias["x"] is not None and second_bias["x"] is not None else None),
                "y": (second_bias["y"]-first_bias["y"]
                      if first_bias["y"] is not None and second_bias["y"] is not None else None),
            },
        })
    contributing = [location for location in locations if location["coordinate_samples"]]
    mean_median = (statistics.mean(location["absolute_vertical_lines"]["median"]
                                   for location in contributing) if contributing else None)
    worst_median = (max(location["absolute_vertical_lines"]["median"]
                        for location in contributing) if contributing else None)
    minimum_block_coordinates = min(item["coordinate_samples"] for item in scored)
    checks = {
        "all_twenty_blocks_contribute": all(item["coordinate_samples"] > 0 for item in scored),
        "all_ten_locations_contribute": len(contributing) == 10,
        "at_least_ten_coordinates_per_block": minimum_block_coordinates >= MIN_COORDINATES,
        "mean_location_median_vertical_at_most_one_line":
            mean_median is not None and mean_median <= MAX_MEAN_MEDIAN_LINES,
        "every_location_median_vertical_at_most_1_2_lines":
            len(contributing) == 10 and worst_median is not None and
            worst_median <= MAX_WORST_MEDIAN_LINES,
    }
    return {
        "blocks": scored,
        "session_spatial": spatial_aggregate(blocks),
        "sweep_spatial": [{"sweep": sweep, **spatial_aggregate(
            [block for block in blocks if block["sweep"] == sweep])} for sweep in (1, 2)],
        "location_spatial": locations,
        "target_balanced": {
            "contributing_location_count": len(contributing),
            "planned_location_count": 10,
            "mean_location_median_absolute_vertical_lines": mean_median,
            "mean_location_p95_absolute_vertical_lines":
                (statistics.mean(location["absolute_vertical_lines"]["p95"]
                                 for location in contributing) if contributing else None),
            "mean_location_max_absolute_vertical_lines":
                (statistics.mean(location["absolute_vertical_lines"]["max"]
                                 for location in contributing) if contributing else None),
            "worst_location_median_absolute_vertical_lines": worst_median,
            "mean_location_exact_line_fraction_of_coordinates":
                (statistics.mean(location["exact_line_fraction_of_coordinates"]
                                 for location in contributing) if contributing else None),
            "mean_location_within_one_line_fraction_of_coordinates":
                (statistics.mean(location["within_one_line_fraction_of_coordinates"]
                                 for location in contributing) if contributing else None),
        },
        "minimum_coordinate_samples_per_block": minimum_block_coordinates,
        "checks": checks,
        "confirmation_line_level_pass": all(checks.values()),
        "repeated_location_bias_change": bias_changes,
    }, previous_capture, previous_output


def evaluate(report):
    _validate_identity(report)
    manifest = _validate_manifest(report)
    screen, viewport, line_height = _validate_geometry_and_constants(report, manifest)
    fit_fractions, fold_results, previous_capture, previous_output = _validate_fit_and_loo(
        report, manifest, screen, viewport, line_height)
    screen_results, sealed, previous_capture, previous_output = _screen(
        report, manifest, screen, viewport, line_height, previous_capture, previous_output)
    screen_points = [expected_point(viewport, fraction) for fraction in SCREEN_FRACTIONS]
    confirmation, previous_capture, previous_output = _confirmation(
        report, manifest, screen_points, screen, viewport, line_height,
        previous_capture, previous_output, sealed["sealed_ms"])
    finished = number(report.get("finished_ms"))
    require(finished >= report["confirmation_blocks"][-1]["end_ms"]+250,
            "Confirmation record finished before the final drain")
    screen_pass = sealed["screen_candidate_pass"]
    downstream_pass = confirmation["confirmation_line_level_pass"]
    contributing_screen = [item for item in screen_results if item["coordinate_samples"]]
    return {
        "schema": "mgazenet_direct_validation_confirmation_summary_v1",
        "evidence_kind": report["evidence_kind"],
        "provenance": {key: report[key] for key in
                       ("protocol_id", "session_id", "device_id", "pipeline_id", "calibration_id")},
        "validation_order": report["validation_order"],
        "loo": {"folds": fold_results, "target_balanced": target_balanced(fold_results)},
        "screen": {
            "blocks": screen_results,
            "target_balanced": (target_balanced(contributing_screen)
                                if contributing_screen else None),
            "sealed_result": sealed,
        },
        "confirmation": confirmation,
        "screen_prediction": {
            "screen_candidate_pass": screen_pass,
            "confirmation_line_level_pass": downstream_pass,
            "classification_agrees": screen_pass is downstream_pass,
            "false_accept": screen_pass and not downstream_pass,
            "false_reject": not screen_pass and downstream_pass,
        },
        "confirmation_accuracy_pass": None,
        "accuracy_gate_pass": None,
        "promotion_decision": "not_evaluated",
        "scope": "Vertical candidate-screen confirmation at stationary instructed targets; not exact-line or natural-reading validation",
        "limitations": [
            "A per-session screen/downstream match cannot validate the screen; the frozen four-session comparison is required.",
            "Exact-line and within-one-line fractions are reported but have no pass threshold in this protocol.",
            "Stationary instructed targets do not establish fixation compliance, scrolling, word, natural-reading, or population accuracy.",
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
    print("Scored sealed screen and independent confirmation; no promotion decision")


if __name__ == "__main__":
    main()
