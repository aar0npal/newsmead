"""Read only a named synthetic report and its tensors from the separate app.

Does not install, launch, open a camera, or access the NewsMead package.
Binary-safe ADB collection avoids shell redirection altering float bytes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGE = "com.newsmead.mgazenetbenchmark"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb",required=True); parser.add_argument("--serial",required=True)
    parser.add_argument("--run",required=True); parser.add_argument("--output",type=Path,required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+_[0-9a-f-]+",args.run): raise ValueError("Invalid synthetic run ID")
    if args.output.exists(): raise ValueError("Use a new output directory to preserve earlier evidence")
    remote = "files/benchmarks/"+args.run+"/"
    def read(name):
        return subprocess.run([args.adb,"-s",args.serial,"exec-out","run-as",PACKAGE,"cat",remote+name],check=True,capture_output=True).stdout
    raw = read("report.json"); report = json.loads(raw)
    if report.get("mode") not in ("synthetic","synthetic_profile"):
        raise ValueError("Only synthetic reports may be collected")
    files = {"report.json":raw}
    for fixture in report.get("fixtures",[]):
        for key in ("face","left","right","rect"):
            descriptor = fixture[key]; name = descriptor["file"]
            if not re.fullmatch(r"[a-z_]+\.f32",name): raise ValueError("Unexpected tensor name")
            data = read(name)
            if len(data)!=descriptor["count"]*4 or hashlib.sha256(data).hexdigest()!=descriptor["sha256"]:
                raise ValueError("Collected tensor integrity mismatch")
            files[name] = data
    args.output.mkdir(parents=True)
    for name,data in files.items(): (args.output/name).write_bytes(data)
    print(args.output/"report.json")


if __name__ == "__main__":
    main()
