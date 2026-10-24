"""Summarize retained synthetic profiles, recomputing statistics from raw samples."""
import argparse
import hashlib
import json
import math
from pathlib import Path
from compare_profile import compare


def stats(values):
    ordered=sorted(values)
    return {'n':len(ordered),'mean':sum(ordered)/len(ordered),
        'median':ordered[math.ceil(.5*len(ordered))-1],
        'p95':ordered[math.ceil(.95*len(ordered))-1],'max':ordered[-1]}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence',type=Path,required=True)
    parser.add_argument('--reference',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    expected=json.loads(args.reference.read_text())
    result={'scope':'Synthetic debug profiling; not end-to-end gaze performance or accuracy',
        'active_tracker_changed':False,'camera_used':False,'participant_calibration_used':False,
        'android_low_precision_executed':False,'reference_sha256':hashlib.sha256(args.reference.read_bytes()).hexdigest(),
        'profiles':[]}
    paths=sorted(args.evidence.glob('*/report.json'))
    if not paths: raise ValueError('No retained profiles')
    for path in paths:
        report=json.loads(path.read_text()); checks=compare(report,expected)
        item={'case':path.parent.name,'device':report['device'],'source':path.as_posix(),
            'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
            'experiment':checks['experiment'],'execution_context':report['execution_context'],
            'all_conditions_parity_pass':checks['all_conditions_parity_pass'],
            'maximum_model_feature_difference':max(c['max_abs'] for b in checks['blocks'] for c in b['checks'].values()),
            'blocks':[]}
        for block in report['blocks']:
            summary={k:block[k] for k in ('index','threads','before','after')}
            summary['range_validation']=block.get('range_validation',False)
            for key in ('validation_ms','input_copy_ms','native_run_ms','output_read_ms','total_ms'):
                summary[key]=stats([s[key] for s in block['samples']])
            item['blocks'].append(summary)
        result['profiles'].append(item)
    result['all_conditions_parity_pass']=all(p['all_conditions_parity_pass'] for p in result['profiles'])
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(f"{len(paths)} profiles; numerical parity: {result['all_conditions_parity_pass']}; {args.output}")


if __name__=='__main__': main()
