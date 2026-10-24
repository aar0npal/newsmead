import unittest
from compare_profile import compare
from reference import MODEL_SHA, NAMES


class ProfileEvidenceTest(unittest.TestCase):
    def setUp(self):
        metadata={'model_sha256':MODEL_SHA,'mnn_version':'3.6.1','opencv':'4.11.0'}
        self.reference=dict(metadata,mode='synthetic_reference',outcome='completed',
            fixtures=[{'name':n,'output_normal':[.1]*258} for n in NAMES])
        order=[4,1,2,8,8,2,1,4]
        self.profile=dict(metadata,mode='synthetic_profile',outcome='completed_pending_external_parity',
            mnn_backend='CPU',mnn_precision='normal_default',thread_order=order,
            blocks=[{'index':i,'threads':t,'fixtures':[{'name':n,'output':[.1]*258} for n in NAMES],
                'samples':[{'iteration':j,'validation_ms':.1,'input_copy_ms':.2,'native_run_ms':5.,'output_read_ms':.1,'total_ms':5.4} for j in range(60)]}
                for i,t in enumerate(order)])
    def test_complete_synthetic_match_cannot_certify_accuracy(self):
        result=compare(self.profile,self.reference)
        self.assertTrue(result['all_conditions_parity_pass']); self.assertFalse(result['accuracy_established'])
    def test_bad_output_in_one_condition_fails_entire_experiment(self):
        self.profile['blocks'][5]['fixtures'][2]['output'][180]=.5
        self.assertFalse(compare(self.profile,self.reference)['all_conditions_parity_pass'])
    def test_missing_sample_rejected(self):
        self.profile['blocks'][0]['samples'].pop()
        with self.assertRaises(ValueError): compare(self.profile,self.reference)
    def test_changed_condition_order_rejected(self):
        self.profile['blocks'][1]['threads']=8
        with self.assertRaises(ValueError): compare(self.profile,self.reference)
    def test_inconsistent_timing_rejected(self):
        self.profile['blocks'][1]['samples'][0]['total_ms']=.1
        with self.assertRaises(ValueError): compare(self.profile,self.reference)
    def test_nonfinite_timing_rejected(self):
        self.profile['blocks'][2]['samples'][10]['native_run_ms']=float('nan')
        with self.assertRaises(ValueError): compare(self.profile,self.reference)
    def test_runtime_change_rejected(self):
        self.profile['mnn_precision']='low'
        with self.assertRaises(ValueError): compare(self.profile,self.reference)
    def test_finite_guard_design_requires_its_exact_conditions(self):
        order=[4,4,2,2,2,2,4,4]
        guards=[False,True,False,True,True,False,True,False]
        self.profile.update(experiment='finite_guard_v1',thread_order=order,range_validation_order=guards)
        for b,t,g in zip(self.profile['blocks'],order,guards): b.update(threads=t,range_validation=g)
        self.assertTrue(compare(self.profile,self.reference)['all_conditions_parity_pass'])
        self.profile['blocks'][3]['range_validation']=False
        with self.assertRaises(ValueError): compare(self.profile,self.reference)


if __name__=='__main__': unittest.main()
