"""Read-only ARM64 ELF/package audit using only Python's standard library.

Checks packaged MNN hashes, JNI exports, ELF segment alignment, and whether
OpenCV/MNN C++ imports exist in the selected libc++. This is not a loader test.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

PINS = {
    "libMNN.so":"c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60",
    "libmnncore.so":"075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9",
    "libc++_shared.so":"f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970",
}


def elf(data):
    if data[:6] != b"\x7fELF\x02\x01": raise ValueError("Expected little-endian ELF64")
    header = struct.unpack_from("<HHIQQQIHHHHHH",data,16)
    if header[1] != 183: raise ValueError("Expected AArch64")
    phoff, shoff, phsize, phnum, shsize, shnum = header[4],header[5],header[8],header[9],header[10],header[11]
    segments = [struct.unpack_from("<IIQQQQQQ",data,phoff+i*phsize) for i in range(phnum)]
    sections = [struct.unpack_from("<IIQQQQIIQQ",data,shoff+i*shsize) for i in range(shnum)]
    def string(section, offset):
        start = section[4]+offset
        return data[start:data.index(b"\0",start)].decode()
    exports, imports, needed = set(),set(),[]
    for section in sections:
        if section[1] == 11: # SHT_DYNSYM
            strings = sections[section[6]]
            for offset in range(section[4],section[4]+section[5],section[9]):
                name,info,other,index,value,size = struct.unpack_from("<IBBHQQ",data,offset)
                name = string(strings,name)
                if not name: continue
                if index and info>>4 in (1,2) and other&3 in (0,3): exports.add(name)
                elif not index and info>>4 == 1: imports.add(name)
        elif section[1] == 6: # SHT_DYNAMIC
            strings = sections[section[6]]
            for offset in range(section[4],section[4]+section[5],16):
                tag,value = struct.unpack_from("<qQ",data,offset)
                if tag == 1: needed.append(string(strings,value))
    loads = [p for p in segments if p[0]==1]
    return {"exports":exports,"imports":imports,"needed":needed,
            "load_alignments":[p[7] for p in loads],
            "elf_16kb_compatible":bool(loads) and all(p[7]>=16384 and (p[3]-p[2])%16384==0 for p in loads)}


def audit(apk):
    with zipfile.ZipFile(apk) as archive:
        names = [n for n in archive.namelist() if n.startswith("lib/") and n.endswith(".so")]
        if len(names)!=len(set(names)) or any(not n.startswith("lib/arm64-v8a/") for n in names):
            raise ValueError("Duplicate or unexpected native ABI")
        blobs = {Path(n).name:archive.read(n) for n in names}
        parsed = {name:elf(blob) for name,blob in blobs.items()}
        for name,digest in PINS.items():
            if hashlib.sha256(blobs[name]).hexdigest()!=digest: raise ValueError("Packaged runtime hash mismatch: "+name)
        result = {"apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),
                  "native_runtime_executed":False,"libraries":{}}
        for name,e in parsed.items():
            # Android libc supplies these ABI entrypoints; remaining C++ symbols must resolve in bundle.
            cxx = {s for s in e["imports"] if s.startswith(("_Z","__cxa_"))} - {"__cxa_atexit","__cxa_finalize"}
            available = set().union(*(p["exports"] for p in parsed.values()))
            missing = sorted(cxx-available)
            result["libraries"][name] = {"sha256":hashlib.sha256(blobs[name]).hexdigest(),
                "needed":e["needed"],"load_alignments":e["load_alignments"],
                "elf_16kb_compatible":e["elf_16kb_compatible"],"missing_strong_cpp_symbols":missing,
                "compressed_in_apk":archive.getinfo("lib/arm64-v8a/"+name).compress_type!=zipfile.ZIP_STORED}
        methods = ("nativeCreateNetFromBuffer","nativeReleaseNet","nativeCreateSession","nativeRunSession",
                   "nativeGetSessionInput","nativeGetSessionOutput","nativeTensorGetDimensions","nativeSetInputFloatData","nativeTensorGetData")
        result["required_jni_exports_present"] = all("Java_com_taobao_android_mnn_MNNNetNative_"+m in parsed["libmnncore.so"]["exports"] for m in methods)
        result["strong_cpp_symbols_resolve"] = not any(v["missing_strong_cpp_symbols"] for v in result["libraries"].values())
        result["all_native_elf_16kb_compatible"] = all(e["elf_16kb_compatible"] for e in parsed.values())
        return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk",type=Path); parser.add_argument("--output",type=Path,required=True)
    args = parser.parse_args(); result = audit(args.apk)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps({k:v for k,v in result.items() if k!="libraries"}))
    raise SystemExit(0 if result["required_jni_exports_present"] and result["strong_cpp_symbols_resolve"] else 1)
