"""Offline scoring of independent, stationary point-target blocks; never fits gaze.

No camera/device access, interpolation, snapping, parameter selection or promotion.
See docs/gaze-mgazenet-accuracy-protocol.md for the versioned input contract.
"""
import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path

HARNESS_PROTOCOL_V1 = "mgazenet_stationary_viewport_v1"
HARNESS_PROTOCOL_V2 = "mgazenet_stationary_viewport_v2"
HARNESS_PROTOCOLS = (HARNESS_PROTOCOL_V1, HARNESS_PROTOCOL_V2)
FIT_GRID = (1, 5, 9, 12, 16, 19, 27, 30, 34, 37, 41, 45, 23)
TEST_GRID_V1 = (2, 8, 13, 15, 31, 33, 38, 44)
TEST_GRID_V2 = (2, 8, 13, 15, 22, 24, 31, 33, 38, 44)
ANALYSIS_AGES_V2 = (50, 100, 200, 500)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def number(value):
    require(type(value) in (int, float) and math.isfinite(value), "Expected finite number")
    return value


def pair(value):
    require(isinstance(value, list) and len(value) == 2, "Expected coordinate pair")
    return [number(v) for v in value]


def rectangle(value):
    require(isinstance(value, list) and len(value) == 4, "Expected rectangle")
    left, top, right, bottom = map(number, value)
    require(left < right and top < bottom, "Empty or inverted rectangle")
    return left, top, right, bottom


def inside(point, rect):
    x, y = point
    left, top, right, bottom = rect
    return left <= x < right and top <= y < bottom


def distribution(values):
    if not values:
        return {"n": 0, "median": None, "p95": None, "max": None}
    ordered = sorted(values)
    return {"n": len(values), "median": statistics.median(ordered),
            "p95": ordered[math.ceil(.95 * len(ordered)) - 1], "max": ordered[-1]}


def signed_distribution(values):
    if not values:
        return {"n": 0, "min": None, "p05": None, "median": None, "p95": None, "max": None}
    ordered = sorted(values)
    return {"n": len(values), "min": ordered[0],
            "p05": ordered[max(0, math.ceil(.05 * len(ordered)) - 1)],
            "median": statistics.median(ordered),
            "p95": ordered[math.ceil(.95 * len(ordered)) - 1], "max": ordered[-1]}


def validate_target_geometry(report, manifest, protocol):
    """Reconstruct the declared targets independently of the predictions."""
    viewport = rectangle(manifest.get("viewport_screen_px"))
    screen = manifest.get("screen_px")
    require(isinstance(screen, list) and len(screen) == 2 and
            all(type(v) is int and v > 0 for v in screen), "Invalid manifest screen")
    require(0 <= viewport[0] < viewport[2] <= screen[0] and
            0 <= viewport[1] < viewport[3] <= screen[1], "Viewport outside physical screen")

    def grid(index):
        x = 50/1920 + ((index-1) % 9) * (1820/1920)/8
        y = 50/1080 + ((index-1) // 9) * (980/1080)/4
        return [viewport[0] + x*(viewport[2]-viewport[0]),
                viewport[1] + y*(viewport[3]-viewport[1])]

    def matches(actual, expected):
        # Cross-language double arithmetic allowance, far below a physical pixel.
        return all(math.isclose(a, b, rel_tol=0, abs_tol=1e-7)
                   for a, b in zip(pair(actual), expected))

    targets = manifest["targets"]
    expected_ids = ["practice"] + [f"fit_{i}" for i in FIT_GRID]
    require([t["id"] for t in targets] == expected_ids, "Changed calibration target order")
    for target, index in zip(targets, (23,) + FIT_GRID):
        require(matches(target.get("point_px"), grid(index)), "Changed calibration target position")
    require(all(p.get("practice") is t["practice"]
                for p, t in zip(report["fit_points"], targets)), "Fit-point practice flag mismatch")
    blocks = report["blocks"]
    if protocol == HARNESS_PROTOCOL_V1:
        expected_grids = TEST_GRID_V1
        require([b["id"] for b in blocks] == [f"test_{i}" for i in expected_grids],
                "Changed held-out target contract")
    else:
        require(manifest.get("validation_locations") == list(TEST_GRID_V2) and
                report.get("analysis_age_limits_ms") == list(ANALYSIS_AGES_V2) and
                manifest.get("analysis_age_limits_ms") == list(ANALYSIS_AGES_V2) and
                report.get("max_output_age_ms") is None,
                "Changed v2 location or fixed-age analysis contract")
        plans = {
            "forward_then_reverse": ("forward", "reverse"),
            "reverse_then_forward": ("reverse", "forward"),
        }
        order = report.get("validation_order")
        require(order in plans and manifest.get("validation_order") == order and
                manifest.get("sweep_directions") == list(plans[order]),
                "Missing or changed counterbalanced order plan")
        expected_grids = tuple(
            grid for direction in plans[order]
            for grid in (TEST_GRID_V2 if direction == "forward" else tuple(reversed(TEST_GRID_V2)))
        )
        expected_directions = tuple(direction for direction in plans[order] for _ in TEST_GRID_V2)
        for position, (block, index, direction) in enumerate(zip(blocks, expected_grids, expected_directions)):
            sweep = position // len(TEST_GRID_V2) + 1
            within = position % len(TEST_GRID_V2) + 1
            require(block.get("id") == f"sweep_{sweep}_test_{index}" and
                    block.get("location_id") == f"test_{index}" and
                    block.get("grid_index") == index and block.get("sweep") == sweep and
                    block.get("sweep_direction") == direction and
                    block.get("order_in_sweep") == within,
                    "Changed v2 sweep, location, or order metadata")
        require(len(blocks) == 2 * len(TEST_GRID_V2), "Changed v2 held-out block count")
    lines = blocks[0]["lines"]
    require(isinstance(lines, list) and len(lines) >= 5, "Insufficient harness line geometry")
    require([line["index"] for line in lines] == list(range(len(lines))), "Changed harness line indices")
    boxes = [rectangle(line["rect_px"]) for line in lines]
    for block, index in zip(blocks, expected_grids):
        require(block["lines"] == lines, "Line geometry changed within stationary session")
        point = grid(index)
        line = min(range(len(boxes)), key=lambda i: abs((boxes[i][1]+boxes[i][3])/2-point[1]))
        left, top, right, bottom = boxes[line]
        expected = [min(max(point[0], left+(right-left)*.02), right-(right-left)*.02),
                    (top+bottom)/2]
        require(block["region"] == f"grid_{index}" and block["target_line_index"] == line and
                matches(block["target_px"], expected), "Held-out target does not match protocol geometry")
    points = [pair(b["target_px"]) for b in blocks]
    if protocol == HARNESS_PROTOCOL_V1:
        require(len({tuple(p) for p in points}) == len(points), "Duplicate held-out target positions")
        location_points = points
    else:
        by_location = {}
        for block, point in zip(blocks, points):
            by_location.setdefault(block["location_id"], []).append(point)
        require(len(by_location) == len(TEST_GRID_V2) and
                all(len(repeated) == 2 and matches(repeated[0], repeated[1])
                    for repeated in by_location.values()),
                "Each v2 held-out location must occur exactly once in each sweep")
        location_points = [repeated[0] for repeated in by_location.values()]
        require(len({tuple(p) for p in location_points}) == len(TEST_GRID_V2),
                "V2 held-out locations are not distinct after text clipping")
    require(not any(matches(p, t["point_px"]) for p in location_points for t in targets[1:]),
            "Fit/test coordinate leakage")


def score_block(block, max_age):
    require(block["role"] == "held_out" and block["task"] == "stationary_point",
            "Only independent stationary held-out blocks are supported")
    start, end = number(block["start_ms"]), number(block["end_ms"])
    require(0 <= start < end, "Invalid measurement window")
    target = pair(block["target_px"])
    height = number(block["line_height_px"])
    require(height > 0, "Invalid line height")
    viewport = rectangle(block["viewport_screen_px"])
    require(inside(target, viewport), "Target is outside viewport")
    lines = block["lines"]
    require(isinstance(lines, list) and lines, "Missing rendered line geometry")
    indices = [line["index"] for line in lines]
    require(all(type(i) is int and i >= 0 for i in indices), "Invalid line index")
    require(indices == sorted(set(indices)), "Duplicate or unordered line indices")
    boxes = [rectangle(line["rect_px"]) for line in lines]
    for i, box in enumerate(boxes):
        require(viewport[0] <= box[0] < box[2] <= viewport[2] and
                viewport[1] <= box[1] < box[3] <= viewport[3], "Line outside clipped viewport")
        if i:
            require(boxes[i-1][3] <= box[1], "Overlapping or unordered line bands")
    def line_at(point):
        return next((index for index, box in zip(indices, boxes) if inside(point, box)), None)
    target_line = block["target_line_index"]
    require(type(target_line) is int and line_at(target) == target_line,
            "Target coordinate and target line disagree")

    samples = block["samples"]
    require(isinstance(samples, list), "Expected sample list; empty blocks must remain")
    events, coordinates, dxs, dys, errors, vertical, ages = [], [], [], [], [], [], []
    valid = exact = adjacent = off_text = late = 0
    previous_capture = previous_output = -1
    for sample in samples:
        capture, output = number(sample["capture_ms"]), number(sample["output_ms"])
        require(start <= capture < end and output >= capture, "Timestamp outside capture window or reversed")
        require(capture > previous_capture and output > previous_output,
                "Duplicate or out-of-order samples; resolve explicitly before scoring")
        previous_capture, previous_output = capture, output
        point = sample["point_px"]
        age = output - capture
        ages.append(age)
        late += output >= end
        assigned = None
        if point is not None:
            point = pair(point)
            coordinates.append(point)
            dx, dy = point[0] - target[0], point[1] - target[1]
            require(math.isfinite(dx) and math.isfinite(dy) and math.isfinite(math.hypot(dx, dy)),
                    "Coordinate error overflow")
            dxs.append(dx); dys.append(dy); errors.append(math.hypot(dx, dy))
            vertical.append(abs(dy) / height)
            require(math.isfinite(vertical[-1]), "Line error overflow")
            assigned = line_at(point)
            valid += 1
            exact += assigned == target_line
            adjacent += assigned is not None and abs(assigned - target_line) <= 1
            off_text += assigned is None
        events.append((output, capture + max_age, point is not None, assigned))

    # Availability is measured on result time. Never fill the initial gap,
    # carry across blocks, or keep a result beyond its capture-age limit.
    fresh_intervals = []
    fresh_ms = exact_ms = adjacent_ms = 0.0
    for i, (output, expiry, present, assigned) in enumerate(events):
        stop = min(end, expiry, events[i+1][0] if i+1 < len(events) else end)
        begin = max(start, output)
        if not present or stop <= begin:
            continue
        duration = stop - begin
        fresh_intervals.append((begin, stop))
        fresh_ms += duration
        exact_ms += duration if assigned == target_line else 0
        adjacent_ms += duration if assigned is not None and abs(assigned-target_line) <= 1 else 0
    cursor = start
    gaps = []
    for begin, stop in fresh_intervals:
        if begin > cursor:
            gaps.append(begin-cursor)
        cursor = stop
    if cursor < end:
        gaps.append(end-cursor)
    duration = end-start
    total = len(samples)
    result = {
        "id": block["id"], "region": block["region"], "planned_duration_ms": duration,
        "received_samples": total, "coordinate_samples": valid,
        "explicit_invalid_samples": total-valid, "off_text_coordinate_samples": off_text,
        "outputs_after_window": late,
        "exact_line_samples": exact, "within_one_line_samples": adjacent,
        "coordinate_fraction_of_received": valid/total if total else None,
        "exact_line_fraction_of_received": exact/total if total else None,
        "within_one_line_fraction_of_received": adjacent/total if total else None,
        "exact_line_fraction_of_coordinates": exact/valid if valid else None,
        "fresh_coordinate_ms": fresh_ms, "fresh_exact_line_ms": exact_ms,
        "fresh_within_one_line_ms": adjacent_ms,
        "fresh_coordinate_time_fraction": fresh_ms/duration,
        "fresh_exact_line_time_fraction": exact_ms/duration,
        "fresh_within_one_line_time_fraction": adjacent_ms/duration,
        "unavailable_ms": duration-fresh_ms, "longest_unavailable_ms": max(gaps, default=0),
        "signed_dx_px": signed_distribution(dxs), "signed_dy_px": signed_distribution(dys),
        "euclidean_px": distribution(errors), "absolute_vertical_lines": distribution(vertical),
        "output_age_ms": distribution(ages),
    }
    if "location_id" in block:
        result.update({
            "within_one_line_fraction_of_coordinates": adjacent/valid if valid else None,
            "coordinate_mean_px": ([statistics.mean(p[0] for p in coordinates),
                                    statistics.mean(p[1] for p in coordinates)] if coordinates else None),
            "signed_mean_bias_px": {"x": statistics.mean(dxs) if dxs else None,
                                    "y": statistics.mean(dys) if dys else None},
            "within_target_dispersion_px": {
                "estimator": "sample_standard_deviation_n_minus_1", "n": len(coordinates),
                "x_sd": statistics.stdev(p[0] for p in coordinates) if len(coordinates) >= 2 else None,
                "y_sd": statistics.stdev(p[1] for p in coordinates) if len(coordinates) >= 2 else None,
            },
        })
    for key in ("location_id", "grid_index", "sweep", "sweep_direction", "order_in_sweep"):
        if key in block:
            result[key] = block[key]
    return result


AVAILABILITY_KEYS = (
    "fresh_coordinate_ms", "fresh_exact_line_ms", "fresh_within_one_line_ms",
    "fresh_coordinate_time_fraction", "fresh_exact_line_time_fraction",
    "fresh_within_one_line_time_fraction", "unavailable_ms", "longest_unavailable_ms",
)


def spatial_aggregate(blocks):
    dxs, dys, errors, vertical, ages = [], [], [], [], []
    exact = adjacent = coordinates = received = invalid = off_text = 0
    for block in blocks:
        target = pair(block["target_px"])
        height = number(block["line_height_px"])
        lines = [(line["index"], rectangle(line["rect_px"])) for line in block["lines"]]
        target_line = block["target_line_index"]
        received += len(block["samples"])
        for sample in block["samples"]:
            ages.append(number(sample["output_ms"])-number(sample["capture_ms"]))
            point = sample["point_px"]
            if point is None:
                invalid += 1
                continue
            point = pair(point)
            dx, dy = point[0]-target[0], point[1]-target[1]
            assigned = next((index for index, box in lines if inside(point, box)), None)
            dxs.append(dx); dys.append(dy); errors.append(math.hypot(dx, dy)); vertical.append(abs(dy)/height)
            coordinates += 1
            exact += assigned == target_line
            adjacent += assigned is not None and abs(assigned-target_line) <= 1
            off_text += assigned is None
    signed_x = signed_distribution(dxs); signed_x["mean"] = statistics.mean(dxs) if dxs else None
    signed_y = signed_distribution(dys); signed_y["mean"] = statistics.mean(dys) if dys else None
    return {
        "contributing_blocks": sum(any(s["point_px"] is not None for s in b["samples"]) for b in blocks),
        "received_samples": received, "coordinate_samples": coordinates,
        "explicit_invalid_samples": invalid, "off_text_coordinate_samples": off_text,
        "signed_dx_px": signed_x, "signed_dy_px": signed_y,
        "euclidean_px": distribution(errors), "absolute_vertical_lines": distribution(vertical),
        "output_age_ms": distribution(ages),
        "exact_line_fraction_of_received": exact/received if received else None,
        "within_one_line_fraction_of_received": adjacent/received if received else None,
        "exact_line_fraction_of_coordinates": exact/coordinates if coordinates else None,
        "within_one_line_fraction_of_coordinates": adjacent/coordinates if coordinates else None,
    }


def v2_summary(report, scored_at_largest_age):
    blocks = report["blocks"]
    spatial_blocks = []
    for scored in scored_at_largest_age:
        spatial_blocks.append({key: value for key, value in scored.items() if key not in AVAILABILITY_KEYS})
    sensitivity = []
    for age in ANALYSIS_AGES_V2:
        scored = [score_block(block, age) for block in blocks]
        sensitivity.append({
            "max_output_age_ms": age,
            "planned_blocks": len(scored),
            "blocks_without_coordinates": sum(b["coordinate_samples"] == 0 for b in scored),
            "target_balanced": {key: statistics.mean(b[key] for b in scored) for key in
                                ("fresh_coordinate_time_fraction", "fresh_exact_line_time_fraction",
                                 "fresh_within_one_line_time_fraction")},
            "blocks": [{"id": b["id"], **{key: b[key] for key in AVAILABILITY_KEYS}} for b in scored],
        })
    by_sweep = [{"sweep": sweep, **spatial_aggregate([b for b in blocks if b["sweep"] == sweep])}
                for sweep in (1, 2)]
    by_location = []
    bias_changes = []
    for location in (f"test_{index}" for index in TEST_GRID_V2):
        repeated = [b for b in blocks if b["location_id"] == location]
        by_location.append({"location_id": location, **spatial_aggregate(repeated)})
        first, second = sorted(repeated, key=lambda b: b["sweep"])
        first_scored = next(b for b in spatial_blocks if b["id"] == first["id"])
        second_scored = next(b for b in spatial_blocks if b["id"] == second["id"])
        first_bias, second_bias = first_scored["signed_mean_bias_px"], second_scored["signed_mean_bias_px"]
        bias_changes.append({
            "location_id": location,
            "first_sweep_direction": first["sweep_direction"],
            "second_sweep_direction": second["sweep_direction"],
            "contributing_sweeps": int(first_bias["x"] is not None) + int(second_bias["x"] is not None),
            "second_minus_first_mean_bias_px": {
                "x": second_bias["x"]-first_bias["x"] if first_bias["x"] is not None and second_bias["x"] is not None else None,
                "y": second_bias["y"]-first_bias["y"] if first_bias["y"] is not None and second_bias["y"] is not None else None,
            },
        })
    contributing = [location for location in by_location if location["coordinate_samples"]]
    return {
        "schema": "mgazenet_accuracy_summary_v2", "evidence_kind": report["evidence_kind"],
        "accuracy_gate_pass": None, "promotion_decision": "not_evaluated",
        "scope": "Stationary instructed-point scoring; no verified fixation or natural-reading ground truth",
        "provenance": {key: report[key] for key in
                       ("protocol_id", "session_id", "device_id", "pipeline_id", "calibration_id")},
        "validation_order": report["validation_order"],
        "analysis_age_limits_ms": list(ANALYSIS_AGES_V2), "primary_freshness_threshold_ms": None,
        "planned_blocks": len(spatial_blocks),
        "planned_locations": len(TEST_GRID_V2),
        "blocks_without_coordinates": sum(b["coordinate_samples"] == 0 for b in spatial_blocks),
        "blocks": spatial_blocks,
        "session_spatial": spatial_aggregate(blocks),
        "sweep_spatial": by_sweep,
        "location_spatial": by_location,
        "target_balanced_spatial": {
            "contributing_location_count": len(contributing),
            "planned_location_count": len(TEST_GRID_V2),
            "mean_location_median_euclidean_px": statistics.mean(
                location["euclidean_px"]["median"] for location in contributing) if contributing else None,
            "mean_location_p95_euclidean_px": statistics.mean(
                location["euclidean_px"]["p95"] for location in contributing) if contributing else None,
            "mean_location_max_euclidean_px": statistics.mean(
                location["euclidean_px"]["max"] for location in contributing) if contributing else None,
            "mean_location_median_absolute_vertical_lines": statistics.mean(
                location["absolute_vertical_lines"]["median"] for location in contributing) if contributing else None,
            "mean_location_p95_absolute_vertical_lines": statistics.mean(
                location["absolute_vertical_lines"]["p95"] for location in contributing) if contributing else None,
            "mean_location_max_absolute_vertical_lines": statistics.mean(
                location["absolute_vertical_lines"]["max"] for location in contributing) if contributing else None,
            "mean_location_signed_x_bias_px": statistics.mean(
                location["signed_dx_px"]["mean"] for location in contributing) if contributing else None,
            "mean_location_signed_y_bias_px": statistics.mean(
                location["signed_dy_px"]["mean"] for location in contributing) if contributing else None,
            "mean_location_exact_line_fraction_of_coordinates": statistics.mean(
                location["exact_line_fraction_of_coordinates"] for location in contributing) if contributing else None,
            "mean_location_within_one_line_fraction_of_coordinates": statistics.mean(
                location["within_one_line_fraction_of_coordinates"] for location in contributing) if contributing else None,
        },
        "repeated_location_bias_change": bias_changes,
        "freshness_sensitivity": sensitivity,
        "limitations": ["Declared fit/test IDs do not independently prove provenance or participant compliance.",
                        "The four freshness ages are fixed sensitivity columns, not accuracy thresholds.",
                        "Time fractions describe bounded availability of the latest result, not eye position between samples.",
                        "Within-target dispersion uses sample SD (n-1) and is undefined for fewer than two coordinates.",
                        "No word, fixation, dwell, regression or population-validity claim is computed."],
    }


def evaluate(report):
    require(report["schema"] in ("mgazenet_accuracy_v1", "mgazenet_accuracy_v2"), "Unknown accuracy schema")
    is_v2 = report["schema"] == "mgazenet_accuracy_v2"
    require(report.get("outcome", "complete") == "complete", "Incomplete session cannot be scored as completed")
    require(report["evidence_kind"] in ("synthetic_contract", "recorded"), "Unknown evidence kind")
    manifest_fields = {"calibration_manifest_json", "calibration_manifest_sha256",
                       "calibration_manifest", "pipeline_json"}
    strict_harness = (is_v2 or report["evidence_kind"] == "recorded" or
                      report.get("protocol_id") in HARNESS_PROTOCOLS or
                      bool(manifest_fields.intersection(report)))
    if strict_harness:
        require(manifest_fields.issubset(report), "Complete harness manifests required")
        require(report.get("protocol_id") == (HARNESS_PROTOCOL_V2 if is_v2 else HARNESS_PROTOCOL_V1),
                "Unsupported recorded/harness protocol")
        require(report.get("outcome") == "complete", "Harness completion must be explicit")
    if "calibration_manifest_json" in report:
        encoded = report["calibration_manifest_json"]
        require(isinstance(encoded, str), "Invalid calibration manifest encoding")
        digest = hashlib.sha256(encoded.encode("utf-8")).hexdigest()
        require(digest == report["calibration_manifest_sha256"] and
                json.loads(encoded) == report["calibration_manifest"] and
                report["calibration_id"] == report["session_id"] + ":" + digest,
                "Calibration manifest integrity mismatch")
        manifest = report["calibration_manifest"]
        require(manifest.get("protocol") == report["protocol_id"] and
                manifest.get("device") == report["device_id"] and
                manifest.get("coordinate_space") == report["coordinate_space"],
                "Manifest provenance mismatch")
        require(manifest.get("label_space") == "physical_screen_fractions" and
                manifest.get("filter") == "none" and manifest.get("correction") == "none",
                "Unsupported calibration transformation")
        pipeline_encoded = report.get("pipeline_json")
        require(isinstance(pipeline_encoded, str) and
                hashlib.sha256(pipeline_encoded.encode("utf-8")).hexdigest() == report["pipeline_id"] and
                json.loads(pipeline_encoded) == manifest.get("pipeline"),
                "Pipeline manifest integrity mismatch")
        pipeline = manifest["pipeline"]
        if report["evidence_kind"] == "recorded":
            require(pipeline.get("model_sha256") == "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96" and
                    pipeline.get("face_landmarker_sha256") == "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                    "Unexpected model or localizer asset")
        require(manifest.get("eye_area_rule") == "both_pixel_polygon_areas_strictly_above_10" and
                manifest.get("fit_settle_ms") == 1500 and
                manifest.get("fit_samples_per_target") == 45 and
                manifest.get("fit_wait_ms") == 500 and
                manifest.get("fit_timeout_ms") == 30000 and
                manifest.get("test_settle_ms") == 3000 and
                manifest.get("test_measure_ms") == 2500 and
                manifest.get("test_drain_ms") == 250 and
                manifest.get("svr") == "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001",
                "Changed calibration or measurement protocol")
        require(manifest.get("training_rows") == 585 and
                isinstance(manifest.get("training_digest"), str) and
                len(manifest["training_digest"]) == 64 and
                all(c in "0123456789abcdef" for c in manifest["training_digest"]),
                "Incomplete calibration training provenance")
        targets = manifest.get("targets")
        require(isinstance(targets, list) and len(targets) == 14 and
                targets[0].get("id") == "practice" and targets[0].get("practice") is True and
                all(t.get("practice") is False for t in targets[1:]),
                "Changed fit/practice target contract")
        require(report["fit_block_ids"] == [t["id"] for t in targets[1:]],
                "Fit IDs do not match calibration manifest")
        fit_points = report.get("fit_points")
        require(isinstance(fit_points, list) and len(fit_points) == 14 and
                [p.get("id") for p in fit_points] == [t["id"] for t in targets] and
                all(p.get("accepted") == 45 for p in fit_points),
                "Incomplete fit-point record")
        require(report.get("active_tracker_access") is False and
                report.get("calibration_store_access") is False and
                report.get("camera_frames_retained") is False and
                report.get("features_retained") is False and
                report.get("personal_model_retained") is False,
                "Unexpected data or active-tracker access")
    require(report["coordinate_space"] == "physical_screen_px" and
            report["clock"] == "shared_monotonic_ms", "Incompatible coordinate or clock contract")
    for key in ("protocol_id", "session_id", "device_id", "pipeline_id", "calibration_id"):
        require(isinstance(report[key], str) and bool(report[key].strip()), "Missing provenance: " + key)
    if is_v2:
        require(report.get("max_output_age_ms") is None and
                report.get("analysis_age_limits_ms") == list(ANALYSIS_AGES_V2),
                "V2 requires the four fixed sensitivity ages and no primary threshold")
        max_age = ANALYSIS_AGES_V2[-1]
    else:
        max_age = number(report["max_output_age_ms"])
        require(max_age > 0, "Explicit positive freshness limit required; no default is selected")
    fit_ids = report["fit_block_ids"]
    require(isinstance(fit_ids, list) and fit_ids and
            all(isinstance(i, str) and i for i in fit_ids) and len(fit_ids) == len(set(fit_ids)),
            "Missing or duplicate fit block IDs")
    blocks = report["blocks"]
    require(isinstance(blocks, list) and blocks, "No planned held-out blocks")
    ids = [block["id"] for block in blocks]
    require(all(isinstance(i, str) and i for i in ids) and len(ids) == len(set(ids)),
            "Invalid or duplicated held-out block IDs")
    require(not set(ids).intersection(fit_ids), "Fit/test block leakage")
    if "calibration_manifest_json" in report:
        validate_target_geometry(report, manifest, report["protocol_id"])
        screen = manifest.get("screen_px")
        require(isinstance(screen, list) and len(screen) == 2 and
                all(type(v) is int and v > 0 for v in screen), "Invalid manifest screen")
        manifest_viewport = list(rectangle(manifest.get("viewport_screen_px")))
        manifest_height = number(manifest.get("line_height_px"))
        require(manifest_height > 0, "Invalid manifest line height")
        settle_ms = number(manifest.get("test_settle_ms"))
        measure_ms = number(manifest.get("test_measure_ms"))
        for block in blocks:
            shown = number(block.get("shown_ms"))
            require(number(block["start_ms"]) == shown + settle_ms and
                    number(block["end_ms"]) == block["start_ms"] + measure_ms,
                    "Test timing does not match manifest")
            require(block["viewport_screen_px"] == manifest_viewport and
                    block["line_height_px"] == manifest_height,
                    "Test geometry does not match calibration manifest")
    previous_end = -1
    results = []
    for block in blocks:
        require(isinstance(block["region"], str) and block["region"], "Missing target region")
        require(number(block["start_ms"]) >= previous_end, "Overlapping or unordered blocks")
        results.append(score_block(block, max_age))
        previous_end = block["end_ms"]
    if is_v2:
        return v2_summary(report, results)
    return {
        "schema": "mgazenet_accuracy_summary_v1", "evidence_kind": report["evidence_kind"],
        "accuracy_gate_pass": None, "promotion_decision": "not_evaluated",
        "scope": "Stationary instructed-point scoring; no verified fixation or natural-reading ground truth",
        "provenance": {key: report[key] for key in
                       ("protocol_id", "session_id", "device_id", "pipeline_id", "calibration_id")},
        "max_output_age_ms": max_age, "planned_blocks": len(results),
        "blocks_without_coordinates": sum(b["coordinate_samples"] == 0 for b in results),
        "blocks": results,
        "target_balanced": {key: statistics.mean(b[key] for b in results) for key in
                            ("fresh_coordinate_time_fraction", "fresh_exact_line_time_fraction",
                             "fresh_within_one_line_time_fraction")},
        "worst_block_vertical_lines": max((b["absolute_vertical_lines"]["max"] for b in results
                                           if b["coordinate_samples"]), default=None),
        "limitations": ["Declared fit/test IDs do not independently prove provenance or participant compliance.",
                        "Freshness is a declared analysis parameter, not a validated study threshold.",
                        "Time fractions describe bounded availability of the latest result, not eye position between samples.",
                        "No word, fixation, dwell, regression or population-validity claim is computed."],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = evaluate(json.loads(args.input.read_text(encoding="utf-8")))
    result["input_sha256"] = hashlib.sha256(args.input.read_bytes()).hexdigest()
    # Refuse accidental overwriting of an earlier result or the input evidence.
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print(f"Scored {result['planned_blocks']} planned blocks; no accuracy gate or promotion decision")


if __name__ == "__main__":
    main()
