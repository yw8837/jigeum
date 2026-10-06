import unittest
import json
from pathlib import Path
import tempfile
import numpy as np
from ast_caption_dual_probe import head_pair,output_path,EVIDENCE_ROOT,validate_artifact


class DualHeadGuards(unittest.TestCase):
    def test_completed_report_content_rejects_partial_scalar_flag_and_summary(self):
        report=json.loads((EVIDENCE_ROOT/'extra01/evaluation.json').read_text())
        with tempfile.TemporaryDirectory() as folder:
            out=Path(folder)
            def check(value):
                (out/'evaluation.json').write_text(json.dumps(value),encoding='utf-8')
                validate_artifact(out)
            check(report)
            for condition in ('empty','missing-scalar','missing-flag','partial-coverage','wrong-summary','over20s'):
                bad=json.loads(json.dumps(report))
                if condition=='empty':bad['cases']=[]
                elif condition=='missing-scalar':del bad['cases'][0]['spans'][0]['caption_score']
                elif condition=='missing-flag':del bad['product_changed']
                elif condition=='partial-coverage':bad['cases'][0]['spans']=[]
                elif condition=='wrong-summary':bad['cases'][0]['paired_any']=False
                else:
                    case=bad['cases'][0];case['actual_pcm_samples']=480000
                    frame=case['spans'][0];case['spans']=[dict(frame,start_sample=i*160000,real_samples=160000,padded_samples=0) for i in range(3)]
                with self.assertRaises((ValueError,KeyError)):check(bad)

    def test_additive_support_preserves_old_and_adds_only_same_window_new(self):
        vector=np.zeros(768);vector[0]=1
        old=np.zeros(768);old[0]=2;new=np.zeros(768);new[0]=-2
        a=head_pair(vector,old,0.,new,0.,.1)
        self.assertTrue(a['base_support']);self.assertFalse(a['caption_support']);self.assertTrue(a['paired_support'])
        b=head_pair(vector,new,0.,old,0.,.1)
        self.assertFalse(b['base_support']);self.assertTrue(b['caption_support']);self.assertTrue(b['paired_support'])
        c=head_pair(vector,new,0.,new,0.,.1)
        self.assertFalse(c['paired_support'])
        self.assertNotIn('combined_probability',a)

    def test_quiet_invalid_and_frame_geometry_do_not_authorize(self):
        old=np.zeros(768);old[0]=10;vector=np.ones(768)
        quiet=head_pair(None,old,10.,old,10.,.002)
        self.assertFalse(quiet['paired_support']);self.assertIsNone(quiet['base_score'])
        for rms in (np.nan,-1.,1.01):
            with self.assertRaises(ValueError):head_pair(vector,old,0.,old,0.,rms)
        for bad in (np.zeros(768),np.ones(769),np.ones(768)*np.nan):
            with self.assertRaises(ValueError):head_pair(bad,old,0.,old,0.,.1)
        self.assertNotIn('onset',quiet)

    def test_output_dedicated_boundary(self):
        output_path((EVIDENCE_ROOT/'future').resolve())
        for path in (EVIDENCE_ROOT,EVIDENCE_ROOT.parent/'outside'):
            with self.assertRaises(ValueError):output_path(path.resolve())


if __name__=='__main__':unittest.main()
