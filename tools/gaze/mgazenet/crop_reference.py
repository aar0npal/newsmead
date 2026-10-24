"""Generate synthetic crop expectations by executing the pinned upstream methods.

Requires existing NumPy only. Never imports GazeFollower/MediaPipe or initializes
a camera. Hash-checks reviewed source, extracts its two numeric methods and
index constants, and supplies synthetic FaceMesh output in memory.
Upstream methods: Gancheng Zhu, GazeFollower, CC BY-NC-SA 4.0; see NOTICE.md.
"""
import ast
import hashlib
import math
from pathlib import Path
from types import SimpleNamespace
import numpy as np

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "mgazenet-benchmark/vendor/reference/face_alignment/MediaPipeFaceAlignment.py"
SOURCE_SHA = "5378917b8f53d4192a22e02fbd0fdbe42064a408db22d4a641bdca2e902426ff"
OUTPUT = ROOT / "mgazenet-benchmark/src/test/resources/upstream-crops.tsv"


def upstream():
    content = SOURCE.read_bytes()
    if hashlib.sha256(content).hexdigest() != SOURCE_SHA:
        raise ValueError("Upstream alignment source hash mismatch")
    cls = next(n for n in ast.parse(content).body if isinstance(n,ast.ClassDef))
    methods = [n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name in ("detect","calculate_polygon_area")]
    if len(methods) != 2: raise ValueError("Missing upstream numeric methods")
    cls.bases = []; cls.body = methods
    scope = {"np":np,"math":math,"FaceInfo":SimpleNamespace}
    exec(compile(ast.Module(body=[cls],type_ignores=[]),str(SOURCE),"exec"),scope)
    alignment = scope[cls.name]()  # Extracted class has no camera-initializing constructor.
    original = next(n for n in ast.parse(content).body if isinstance(n,ast.ClassDef))
    init = next(n for n in original.body if isinstance(n,ast.FunctionDef) and n.name=="__init__")
    for node in init.body:
        if isinstance(node,ast.Assign) and isinstance(node.targets[0],ast.Attribute):
            name = node.targets[0].attr
            if name.endswith("vertices_index"):
                setattr(alignment,name,ast.literal_eval(node.value))
    return alignment


def fixtures():
    base = {0:(.2,.2),1:(.8,.8),33:(.3,.4),133:(.4,.4),362:(.6,.4),263:(.7,.4)}
    for w,h in ((100,100),(480,640),(640,480)):
        for scale in (.55,.85,1.25):
            for dx in (-.3,0,.3):
                for dy in (-.3,0,.3):
                    default = (.5+dx,.5+dy)
                    edits = {i:((x-.5)*scale+.5+dx,(y-.5)*scale+.5+dy) for i,(x,y) in base.items()}
                    yield f"{w}x{h}_scale{scale}_dx{dx}_dy{dy}",w,h,default,edits
    for name,edit in (
        ("half_pixel",{33:(.305,.4)}),
        ("left_border",{33:(.04,.4)}),
        ("right_border",{263:(.96,.4)}),
        ("upper_border",{33:(.3,.08),133:(.4,.08)}),
        ("lower_border",{362:(.6,.98),263:(.7,.98)}),
        ("wide_face",{0:(.05,.35),1:(.95,.65)}),
        ("tall_face",{0:(.35,.05),1:(.65,.95)}),
        ("lips_below_frame",{i:(.5,1.0) for i in (61,91,14,178,402,324,95)}),
    ):
        yield name,100,100,(.5,.5),base | edit


def main():
    alignment = upstream()
    lines = ["# Synthetic landmarks only; upstream source SHA256 "+SOURCE_SHA,
             "# name\twidth\theight\tdefault_x\tdefault_y\tedits(index:x:y;...)\taccepted\tface,left,right(x,y,w,h)"]
    accepted = 0
    for name,w,h,default,edits in fixtures():
        # Real MediaPipe normalized coordinates are protobuf float32 values.
        default = tuple(float(np.float32(v)) for v in default)
        edits = {i:tuple(float(np.float32(v)) for v in p) for i,p in edits.items()}
        points = [SimpleNamespace(x=edits.get(i,default)[0],y=edits.get(i,default)[1],z=0.0) for i in range(478)]
        alignment.face_mesh = SimpleNamespace(process=lambda _:SimpleNamespace(multi_face_landmarks=[SimpleNamespace(landmark=points)]))
        result = alignment.detect(0,SimpleNamespace(shape=(h,w,3)))
        valid = bool(result.can_gaze_estimation)
        boxes = [int(v) for attr in ("face_rect","left_rect","right_rect") for v in getattr(result,attr)] if valid else []
        if valid and any(boxes[i+2]<=0 or boxes[i+3]<=0 for i in (0,4,8)):
            raise ValueError("Fixture outside supported valid-box domain")
        accepted += valid
        lines.append("\t".join((name,str(w),str(h),repr(default[0]),repr(default[1]),
            ";".join(f"{i}:{x!r}:{y!r}" for i,(x,y) in sorted(edits.items())),str(valid).lower(),",".join(map(str,boxes)) or "-")))
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    OUTPUT.write_text("\n".join(lines)+"\n",encoding="utf-8")
    print(f"{len(lines)-2} synthetic upstream cases: {accepted} accepted, {len(lines)-2-accepted} rejected; {OUTPUT}")


if __name__ == "__main__":
    main()
