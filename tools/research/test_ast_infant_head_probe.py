"""AST head input/leakage/real-span guards; synthetic scores are not infant evidence."""
import unittest
import numpy as np
from ast_infant_head_probe import spans,normalized,pooled,score_spans
from infant_presence_probe import fit_binary,select,sigmoid
from test_infant_head_source_cv import training_cases


class AstHeadGuards(unittest.TestCase):
    def test_short_padding_and_20s_nonoverlap_real_time(self):
        for count in (1,8996,15999,16000,160000,160001,320000):
            pcm=np.full(count,.125,np.float32)
            rows=spans(pcm)
            self.assertEqual(sum(r['real_samples'] for _,r in rows),count)
            self.assertEqual(len(rows),(count+159999)//160000)
            for i,(block,frame) in enumerate(rows):
                self.assertEqual(frame['start_sample'],i*160000)
                self.assertEqual(len(block),max(16000,frame['real_samples']))
                np.testing.assert_array_equal(block[:frame['real_samples']],.125)
                self.assertEqual(float(np.sum(np.abs(block[frame['real_samples']:]))),0.)
                self.assertAlmostEqual(frame['rms'],.125)

    def test_pcm_domain_refuses_empty_nonfinite_outofrange_and_badshape(self):
        for wrong in (np.zeros(0),np.zeros(320001),np.zeros((1,16000)),np.zeros(16000,dtype=np.int16),
                      np.array([np.nan]),np.array([np.inf]),np.array([1.01])):
            with self.assertRaises(ValueError): spans(wrong)

    def test_pool_actual_sample_weights_quiet_and_invalid_frames(self):
        frames=[r for _,r in spans(np.full(240000,.1,np.float32))]
        values=np.zeros((2,768)); values[0,0]=1; values[1,1]=1
        np.testing.assert_allclose(pooled(values,frames)[:2],[2/np.sqrt(5),1/np.sqrt(5)])
        quiet=[dict(f,rms=0.) for f in frames]
        with self.assertRaises(ValueError): pooled(values,quiet)
        for bad in ([dict(frames[0],real_samples=0),frames[1]],
                    [frames[0],dict(frames[1],start_sample=1)],
                    [frames[0],dict(frames[1],rms=np.nan)],
                    [dict(frames[0],real_samples=1000,padded_samples=15000),frames[1]]):
            with self.assertRaises(ValueError): pooled(values,bad)
        for vector in (np.zeros(768),np.ones(1024),np.ones(768)*np.nan):
            with self.assertRaises(ValueError): normalized(vector)

    def test_768_fit_and_selection_preserve_old_dimension_guard(self):
        cases=training_cases(); x=np.zeros((len(cases),768))
        for i in range(len(cases)): x[i,i//2]=1
        chosen,cv=select(x,cases,768)
        self.assertEqual(len(cv),3)
        for row in cv:
            for f in row['folds']:
                self.assertFalse(set(f['train_ids'])&set(f['held_ids']))
        labels=np.array([1.,0.]); weights=np.ones(2)/2
        matrix=np.eye(2,768)
        w,b=fit_binary(matrix,labels,weights,chosen,768)
        self.assertEqual(w.shape,(768,))
        self.assertGreater(float(sigmoid(matrix[0]@w+b)),.8)
        with self.assertRaises(ValueError): fit_binary(matrix,labels,weights,chosen)
        with self.assertRaises(ValueError): select(x,cases,767)
        with self.assertRaises(ValueError): select(x,[dict(cases[0],role='validation'),*cases[1:]],768)

    def test_quiet_eval_never_scores_and_no_fake_event_duration(self):
        frames=[r for _,r in spans(np.zeros(160001,np.float32))]
        values=np.ones((2,768)); w=np.ones(768)
        rows=score_spans(values,frames,w,0.)
        self.assertTrue(all(r['infant_score'] is None and not r['infant_support'] for r in rows))
        self.assertTrue(all('support_seconds' not in r for r in rows))
        with self.assertRaises(ValueError): score_spans(values*np.nan,frames,w,0.)
        frames[0]['rms']=.1
        row=score_spans(values,frames,w,0.)[0]
        self.assertTrue(row['infant_support'])
        self.assertLessEqual(row['float32_absolute_error'],5e-5)


if __name__=='__main__': unittest.main()
