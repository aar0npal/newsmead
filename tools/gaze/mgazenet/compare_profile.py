"""Check every synthetic profiling condition against the fixed desktop reference."""
import argparse
import hashlib
import json
import math
from pathlib import Path
from reference import difference, MODEL_SHA, NAMES, INFERENCE_ATOL, INFERENCE_RTOL


def compare(profile, reference):
    if profile.get("mode")!="synthetic_profile" or profile.get("outcome")!="completed_pending_external_parity":
        raise ValueError("Incomplete synthetic profile")
    if reference.get("mode")!="synthetic_reference" or reference.get("outcome")!="completed":
        raise ValueError("Incomplete reference")
    if any(r.get("model_sha256")!=MODEL_SHA or r.get("mnn_version")!="3.6.1" or r.get("opencv")!="4.11.0" for r in (profile,reference)):
        raise ValueError("Model/runtime version mismatch")
    if profile.get("mnn_backend")!="CPU" or profile.get("mnn_precision")!="normal_default":
        raise ValueError("Unsupported inference configuration")
    experiment=profile.get('experiment','thread_stage_v1')
    if experiment not in ('thread_stage_v1','finite_guard_v1'): raise ValueError('Unknown experiment')
    order=[4,4,2,2,2,2,4,4] if experiment=='finite_guard_v1' else [4,1,2,8,8,2,1,4]
    validations=[False,True,False,True,True,False,True,False] if experiment=='finite_guard_v1' else [False]*8
    if profile.get('range_validation_order',[False]*8)!=validations:
        raise ValueError('Changed validation condition order')
    if profile.get("thread_order")!=order or [b['threads'] for b in profile['blocks']]!=order:
        raise ValueError("Incomplete or changed condition order")
    expected={f['name']:f for f in reference['fixtures']}
    if set(expected)!=set(NAMES) or len(reference['fixtures'])!=3: raise ValueError("Incomplete reference fixtures")
    result={'experiment':experiment,'accuracy_established':False,'android_low_precision_executed':False,'blocks':[]}
    for index,block in enumerate(profile['blocks']):
        if block['index']!=index or len(block['samples'])!=60: raise ValueError("Incomplete profile samples")
        if block.get('range_validation',False)!=validations[index]: raise ValueError('Wrong validation condition')
        for n,s in enumerate(block['samples']):
            if s['iteration']!=n: raise ValueError("Changed sample order")
            values=[s[k] for k in ('validation_ms','input_copy_ms','native_run_ms','output_read_ms','total_ms')]
            if not all(math.isfinite(v) and v>=0 for v in values): raise ValueError("Invalid timing")
            if abs(sum(values[:4])-values[4])>1e-5: raise ValueError("Timing components do not sum to total")
        fixtures={f['name']:f for f in block['fixtures']}
        if set(fixtures)!=set(NAMES) or len(block['fixtures'])!=3: raise ValueError("Incomplete profile fixtures")
        checks={}
        for name in NAMES:
            a=fixtures[name]['output']; b=expected[name]['output_normal']
            if len(a)!=258 or len(b)!=258: raise ValueError("Invalid model output dimensions")
            checks[name]=difference(a,b,INFERENCE_ATOL,INFERENCE_RTOL)
        result['blocks'].append({'index':index,'threads':block['threads'],
            'range_validation':validations[index],
            'parity_pass':all(c['pass'] for c in checks.values()),'checks':checks})
    result['all_conditions_parity_pass']=all(b['parity_pass'] for b in result['blocks'])
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile',type=Path,required=True); parser.add_argument('--reference',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    result=compare(json.loads(args.profile.read_text()),json.loads(args.reference.read_text()))
    result['profile_sha256']=hashlib.sha256(args.profile.read_bytes()).hexdigest()
    result['reference_sha256']=hashlib.sha256(args.reference.read_bytes()).hexdigest()
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='blocks'}))
    raise SystemExit(0 if result['all_conditions_parity_pass'] else 1)
