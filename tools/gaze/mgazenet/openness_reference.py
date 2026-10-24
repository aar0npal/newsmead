"""Generate synthetic eye-area expectations using the pinned upstream alignment.

No camera or localizer inference. GazeFollower numeric methods retain their
CC BY-NC-SA 4.0 attribution; see the benchmark NOTICE.md and crop_reference.py.
"""
import math
from types import SimpleNamespace
import numpy as np
from crop_reference import ROOT, SOURCE_SHA, upstream


def main():
    alignment = upstream()
    lines = ["# Synthetic eye contours; upstream source SHA256 " + SOURCE_SHA,
             "# name\twidth\theight\tedits\tleft_area\tright_area\tboth_above_10"]
    for width, height in ((100, 100), (480, 640), (640, 480)):
        for name, left_amp, right_amp in (
            ("closed", 0, 0), ("thin", .001, .001), ("small", .003, .003),
            ("near_threshold", .0065, .0065), ("open", .025, .025),
            ("left_closed", 0, .025), ("right_closed", .025, 0),
        ):
            edits = {0: (.2, .2), 1: (.8, .8)}
            for indices, center, amplitude in (
                (alignment.left_vertices_index, .35, left_amp),
                (alignment.right_vertices_index, .65, right_amp),
            ):
                # Repeated source indices intentionally retain their final assignment.
                for i, index in enumerate(indices[:-1]):
                    angle = 2 * math.pi * i / (len(indices)-1)
                    edits[index] = (center - .05 * math.cos(angle), .4 - amplitude * math.sin(angle))
            edits = {i: tuple(float(np.float32(v)) for v in xy) for i, xy in edits.items()}
            points = [SimpleNamespace(x=edits.get(i, (.5, .5))[0],
                                      y=edits.get(i, (.5, .5))[1], z=0.) for i in range(478)]
            alignment.face_mesh = SimpleNamespace(process=lambda _: SimpleNamespace(
                multi_face_landmarks=[SimpleNamespace(landmark=points)]))
            result = alignment.detect(0, SimpleNamespace(shape=(height, width, 3)))
            if not result.can_gaze_estimation:
                raise ValueError("Unexpected invalid synthetic crop")
            left, right = result.left_eye_openness, result.right_eye_openness
            lines.append("\t".join((f"{width}x{height}_{name}", str(width), str(height),
                ";".join(f"{i}:{x!r}:{y!r}" for i, (x, y) in sorted(edits.items())),
                repr(float(left)), repr(float(right)), str(left > 10 and right > 10).lower())))
    target = ROOT / "mgazenet-benchmark/src/test/resources/upstream-openness.tsv"
    target.write_text("\n".join(lines)+"\n", encoding="utf-8")
    print(f"{len(lines)-2} synthetic source eye-area cases: {target}")


if __name__ == "__main__":
    main()
