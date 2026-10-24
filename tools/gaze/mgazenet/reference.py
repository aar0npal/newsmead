"""Independent reference and strict comparator for SYNTHETIC fixtures only.

reference: requires an already provisioned MNN 3.6.1 / OpenCV 4.11.0 / NumPy
environment; it does not install dependencies. compare: standard library only.
Never imports GazeFollower (whose defaults can initialize a camera), uses ADB,
reads participant frames, or fits participant calibration.
"""
import argparse
import array
import hashlib
import importlib.metadata
import json
import math
from pathlib import Path
import platform
import sys

MODEL_SHA = "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96"
NAMES = ("ramp", "clipped", "channels")
TENSOR_ATOL = 1e-7
INFERENCE_ATOL = 1e-3
INFERENCE_RTOL = 1e-4
SVR_ATOL = 1e-5


def write_tensor(folder, name, values):
    data = values.astype("<f4").tobytes()
    path = folder / (name + ".f32")
    path.write_bytes(data)
    return {"file": path.name, "count": len(data) // 4, "sha256": hashlib.sha256(data).hexdigest()}


def reference(args):
    import cv2
    import numpy as np
    import MNN
    mnn_version = importlib.metadata.version("MNN")
    if mnn_version != "3.6.1" or cv2.__version__ != "4.11.0":
        raise ValueError(f"Expected MNN 3.6.1 / OpenCV 4.11.0; found {mnn_version} / {cv2.__version__}")
    if hashlib.sha256(args.model.read_bytes()).hexdigest() != MODEL_SHA:
        raise ValueError("Model hash mismatch")
    if args.output.exists():
        raise ValueError("Use a new reference output directory to preserve earlier evidence")
    args.output.mkdir(parents=True)
    result = {"schema_version": 1, "mode": "synthetic_reference", "model_sha256": MODEL_SHA,
              "mnn_version": mnn_version, "opencv": cv2.__version__, "numpy": np.__version__,
              "reference_host": {"platform": platform.platform(), "architecture": platform.machine(), "python": platform.python_version()},
              "low_precision_scope": "Requested on reference host; effective arithmetic is backend/hardware dependent",
              "fixtures": [], "outcome": "completed", "tolerances": {
                  "tensor_atol": TENSOR_ATOL, "inference_atol": INFERENCE_ATOL,
                  "inference_rtol": INFERENCE_RTOL, "svr_atol": SVR_ATOL}}
    modules = {}
    runtimes = {}
    for precision in ("normal", "low"):
        runtimes[precision] = MNN.nn.create_runtime_manager(({'precision': precision, 'backend': 0, 'numThread': 4},))
        modules[precision] = MNN.nn.load_module_from_file(str(args.model),
            ["face", "left", "right", "rect"], ["output_0"], runtime_manager=runtimes[precision])
    rectangles = [
        [(1,2,13,9), (2,3,5,3), (9,4,6,4)],
        [(3,2,20,20), (1,1,8,4), (12,8,9,8)],
        [(0,0,17,13), (1,1,6,4), (8,2,7,5)],
    ]
    for index, name in enumerate(NAMES):
        image = np.empty((13,17,3), dtype=np.uint8)
        for y in range(13):
            for x in range(17):
                for c in range(3):
                    image[y,x,c] = (255 if x % 3 == c else 0) if name == "channels" else (x*17+y*29+c*71) % 256
        patches = []
        rect = []
        for eye, ((x,y,w,h), edge) in enumerate(zip(rectangles[index], (224,112,112))):
            patch = cv2.resize(image[y:min(y+h,13), x:min(x+w,17)].copy(), (edge,edge))
            patch = patch.astype(np.float32) / np.float32(255)
            if eye == 2:
                patch = cv2.flip(patch, 1)
            patches.append(patch)
            rect.extend((w,h,x,y))
        rect = np.array(rect, np.float32) / np.array([17,13]*6, np.float32)
        item = {"name": name}
        variables = []
        for key, patch in zip(("face","left","right"), patches):
            item[key] = write_tensor(args.output, name+"_"+key, patch.transpose(2,0,1).copy())
            var = MNN.expr.placeholder((1,*patch.shape), MNN.expr.NHWC)
            var.write(patch)
            variables.append(var)
        item["rect"] = write_tensor(args.output, name+"_rect", rect)
        var = MNN.expr.placeholder((1,12)); var.write(rect.reshape(1,12)); variables.append(var)
        for precision, module in modules.items():
            output = module.onForward(variables)[0].read().copy().reshape(-1).astype(np.float32)
            if output.size != 258 or not np.isfinite(output).all():
                raise ValueError("Invalid reference model output")
            item["output_"+precision] = output.tolist()
        result["fixtures"].append(item)
    features = np.array([[(i//45*17+i%45*3+j*13)%101 for j in range(258)] for i in range(585)], np.float32) / np.float32(100)
    labels = np.array([[i//45,(i//45*5)%13] for i in range(585)], np.float32) / np.float32(12)
    queries = np.array([[(i*11+j*7)%101 for j in range(258)] for i in range(7)], np.float32) / np.float32(100)
    svrs = []
    for axis in range(2):
        svr = cv2.ml.SVM_create()
        svr.setType(cv2.ml.SVM_EPS_SVR); svr.setKernel(cv2.ml.SVM_RBF)
        svr.setC(1.0); svr.setGamma(.005); svr.setP(.001)
        svr.setTermCriteria((cv2.TERM_CRITERIA_MAX_ITER, 10000, 1e-4))
        if not svr.train(features, cv2.ml.ROW_SAMPLE, labels[:,axis].copy()):
            raise ValueError("Synthetic reference SVR failed")
        svrs.append(svr)
    result["synthetic_svr_predictions"] = np.column_stack([svr.predict(queries)[1].flatten() for svr in svrs]).tolist()
    result["synthetic_support_vector_counts"] = [len(svr.getSupportVectors()) for svr in svrs]
    (args.output / "report.json").write_text(json.dumps(result, indent=2)+"\n")
    print(args.output / "report.json")


def floats(folder, descriptor):
    path = (folder / descriptor["file"]).resolve()
    if path.parent != folder.resolve():
        raise ValueError("Tensor file must stay beside its report")
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != descriptor["sha256"] or len(data) != descriptor["count"]*4:
        raise ValueError("Tensor hash/size mismatch")
    values = array.array("f"); values.frombytes(data)
    if sys.byteorder != "little": values.byteswap()
    return values


def difference(actual, expected, atol, rtol=0.0):
    if len(actual) != len(expected) or not actual:
        return {"pass": False, "reason": "shape mismatch or empty"}
    if not all(math.isfinite(x) for x in list(actual)+list(expected)):
        return {"pass": False, "reason": "non-finite values"}
    errors = [abs(a-b) for a,b in zip(actual, expected)]
    return {"pass": all(e <= atol+rtol*abs(b) for e,b in zip(errors,expected)),
            "count": len(errors), "max_abs": max(errors), "rmse": math.sqrt(sum(e*e for e in errors)/len(errors)),
            "atol": atol, "rtol": rtol}


def compare(args):
    observed = json.loads(args.android.read_text())
    expected = json.loads(args.reference.read_text())
    if (observed.get("mode") != "synthetic" or expected.get("mode") != "synthetic_reference"
        or observed.get("outcome") != "completed_pending_external_parity" or expected.get("outcome") != "completed"):
        raise ValueError("Only completed synthetic reports can be compared")
    if any(d.get("model_sha256") != MODEL_SHA or d.get("opencv") != "4.11.0" for d in (observed,expected)):
        raise ValueError("Model or OpenCV version mismatch")
    if expected.get("mnn_version") != "3.6.1": raise ValueError("Reference MNN version mismatch")
    if (observed.get("mnn_version") != "3.6.1" or observed.get("mnn_backend") != "CPU"
        or observed.get("mnn_precision") != "normal_default" or observed.get("mnn_threads") != 4):
        raise ValueError("Android runtime configuration mismatch")
    checks = {}
    obs = {f["name"]: f for f in observed["fixtures"]}
    exp = {f["name"]: f for f in expected["fixtures"]}
    if (set(obs) != set(NAMES) or set(exp) != set(NAMES)
        or len(observed["fixtures"]) != 3 or len(expected["fixtures"]) != 3):
        raise ValueError("Incomplete or duplicated fixture set")
    for name in NAMES:
        for key in ("face", "left", "right", "rect"):
            count = {"face":150528, "left":37632, "right":37632, "rect":12}[key]
            if obs[name][key]["count"] != count or exp[name][key]["count"] != count:
                raise ValueError("Invalid input tensor dimensions")
            checks[name+"/"+key] = difference(floats(args.android.parent, obs[name][key]),
                floats(args.reference.parent, exp[name][key]), TENSOR_ATOL)
        if any(len(v) != 258 for v in (obs[name]["output"],exp[name]["output_normal"],exp[name]["output_low"])):
            raise ValueError("Output must have 258 features")
        checks[name+"/session_vs_module_normal"] = difference(obs[name]["output"], exp[name]["output_normal"], INFERENCE_ATOL, INFERENCE_RTOL)
        # Separate result: precision differences must not be hidden by a normal-precision pass.
        checks[name+"/upstream_low_precision"] = difference(obs[name]["output"], exp[name]["output_low"], INFERENCE_ATOL, INFERENCE_RTOL)
    a = observed["synthetic_svr_predictions"]; b = expected["synthetic_svr_predictions"]
    if len(a)!=7 or len(b)!=7 or any(len(v)!=2 for v in a+b): raise ValueError("Incomplete synthetic SVR predictions")
    checks["svr"] = difference([v for row in a for v in row], [v for row in b for v in row], SVR_ATOL)
    normal_pass = all(v["pass"] for k,v in checks.items() if not k.endswith("upstream_low_precision"))
    reference_precision_difference = difference(
        [v for f in exp.values() for v in f["output_normal"]],
        [v for f in exp.values() for v in f["output_low"]], 0.0)
    result = {"normal_precision_parity_pass": normal_pass,
              "upstream_low_precision_parity_pass": all(v["pass"] for v in checks.values()),
              "android_low_precision_executed": False,
              "low_precision_scope": "Compares Android default precision against requested low on reference host only",
              "reference_host": expected.get("reference_host", "not_recorded"),
              "reference_normal_vs_low_max_abs": reference_precision_difference.get("max_abs"),
              "android_report_sha256": hashlib.sha256(args.android.read_bytes()).hexdigest(),
              "reference_report_sha256": hashlib.sha256(args.reference.read_bytes()).hexdigest(),
              "accuracy_established": False, "localizer_parity_established": False, "checks": checks}
    args.output.write_text(json.dumps(result, indent=2)+"\n")
    print(json.dumps({k:v for k,v in result.items() if k!="checks"}))
    return 0 if normal_pass else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    ref = sub.add_parser("reference")
    ref.add_argument("--model", type=Path, required=True); ref.add_argument("--output", type=Path, required=True)
    cmp = sub.add_parser("compare")
    cmp.add_argument("--android", type=Path, required=True); cmp.add_argument("--reference", type=Path, required=True)
    cmp.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "reference": reference(args); return 0
    return compare(args)


if __name__ == "__main__":
    raise SystemExit(main())
