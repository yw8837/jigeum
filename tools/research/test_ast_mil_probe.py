import unittest
import numpy as np
from ast_mil_probe import ast_bag, score_bag, validate_output, EVIDENCE_ROOT
from infant_mil_probe import fit_mil, select, loss_and_gradient, bag_logit_and_attention
from infant_presence_probe import fit_binary, sigmoid
from ast_infant_head_probe import spans
from test_infant_head_source_cv import training_cases


class AstMILGuards(unittest.TestCase):
    def test_actual_time_quiet_padding_chronology(self):
        frames = [f for _, f in spans(np.full(160001, .1, np.float32))]
        x = np.zeros((2,768)); x[0,0]=2; x[1,1]=3
        values, time = ast_bag(x, frames)
        np.testing.assert_allclose(time, [160000/160001, 1/160001])
        np.testing.assert_array_equal(values[:,:2], np.eye(2))
        quiet = [dict(frames[0], rms=0), frames[1]]
        values, time = ast_bag(x, quiet)
        self.assertEqual(values.shape, (1,768)); self.assertEqual(time[0],1)
        for wrong in ([dict(f,rms=0) for f in frames],
                      [frames[0],dict(frames[1],padded_samples=0)],
                      [frames[0],dict(frames[1],start_sample=1)]):
            with self.assertRaises(ValueError): ast_bag(x,wrong)
        with self.assertRaises(ValueError): ast_bag(x*np.nan,frames)

    def test_single_span_mil_exact_binary_fit_and_original_1024(self):
        x = np.eye(2,768); labels=np.array([1.,0.]); weights=np.ones(2)/2
        bags=[(row[None,:],np.ones(1)) for row in x]
        w,b=fit_mil(bags,labels,weights,.001,768)
        ref,rb=fit_binary(x,labels,weights,.001,768)
        np.testing.assert_allclose(w,ref,atol=1e-13)
        self.assertAlmostEqual(b,rb,places=13)
        with self.assertRaises(ValueError): fit_mil(bags,labels,weights,.001)
        for dimension in (767, True, 0):
            with self.assertRaises(ValueError): fit_mil(bags,labels,weights,.001,dimension)

    def test_768_gradient_numerical_and_two_span_weighting(self):
        x=np.zeros((2,768)); x[0,:2]=[.3,.4]; x[1,:2]=[-.2,.8]
        bags=[(x,np.array([.2,.8])),(x[::-1],np.array([.6,.4]))]
        labels=np.array([1.,0.]); weights=np.array([.4,.6])
        w=np.zeros(768); w[:2]=[.2,-.4]; b=.1
        _,gw,gb=loss_and_gradient(bags,labels,weights,w,b,.001)
        epsilon=1e-5
        for i in range(2):
            hi=w.copy(); lo=w.copy(); hi[i]+=epsilon; lo[i]-=epsilon
            numerical=(loss_and_gradient(bags,labels,weights,hi,b,.001)[0]-
                loss_and_gradient(bags,labels,weights,lo,b,.001)[0])/(2*epsilon)
            self.assertAlmostEqual(gw[i],numerical,places=8)
        numerical=(loss_and_gradient(bags,labels,weights,w,b+epsilon,.001)[0]-
            loss_and_gradient(bags,labels,weights,w,b-epsilon,.001)[0])/(2*epsilon)
        self.assertAlmostEqual(gb,numerical,places=8)
        logit,_=bag_logit_and_attention(np.array([1000.,-1000.]),np.array([.25,.75]))
        self.assertAlmostEqual(logit,1000+np.log(.25))

    def test_source_selection_no_validation_and_fp32_no_event_time(self):
        cases=training_cases(); bags=[]
        for i in range(len(cases)):
            x=np.zeros((1,768)); x[0,i//2]=1; bags.append((x,np.ones(1)))
        chosen,rows=select(bags,cases,768)
        self.assertIn(chosen,(.0001,.001,.01))
        for row in rows:
            for f in row['folds']:
                self.assertFalse(set(f['train_ids'])&set(f['held_ids']))
        with self.assertRaises(ValueError): select(bags,[dict(cases[0],role='validation'),*cases[1:]],768)
        w,b=fit_mil(bags,np.array([1.,1.,0.,0.,0.,0.]),np.ones(6)/6,.001,768)
        result=score_bag(bags[0],w,b)
        self.assertLessEqual(result['float32_absolute_error'],5e-5)
        self.assertNotIn('support_seconds',result)

    def test_output_root_boundary(self):
        validate_output((EVIDENCE_ROOT/'future-run').resolve())
        for path in (EVIDENCE_ROOT, EVIDENCE_ROOT.parent/'elsewhere', EVIDENCE_ROOT/'..'/'elsewhere'):
            with self.assertRaises(ValueError): validate_output(path.resolve())


if __name__ == '__main__': unittest.main()
