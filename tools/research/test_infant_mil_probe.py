import unittest
import numpy as np
from infant_mil_probe import bag,bag_logit_and_attention,loss_and_gradient,fit_mil,select
from test_infant_head_source_cv import training_cases


class MILGuards(unittest.TestCase):
    def test_actual_chronology_quiet_and_l2_windows(self):
        features=np.zeros((3,1024)); features[0,0]=2; features[1,1]=3; features[2,2]=4
        frames=[dict(start_sample=0,real_samples=15600,rms=.1),
                dict(start_sample=15600,real_samples=15600,rms=.002),
                dict(start_sample=31200,real_samples=7800,rms=.1)]
        x,time=bag(features,frames)
        self.assertEqual(x.shape,(2,1024))
        np.testing.assert_allclose(time,[2/3,1/3])
        self.assertEqual(x[0,0],1)
        self.assertEqual(x[1,2],1)
        wrong=[*frames[:2],dict(frames[2],start_sample=30000)]
        with self.assertRaises(ValueError): bag(features,wrong)
        with self.assertRaises(ValueError): bag(features,[dict(f,rms=0) for f in frames])
        with self.assertRaises(ValueError): bag(features*np.nan,frames)

    def test_logmeanexp_stable_extremes_time_and_invalid(self):
        value,attention=bag_logit_and_attention(np.array([1000.,-1000.]),np.array([.25,.75]))
        self.assertAlmostEqual(value,1000+np.log(.25))
        np.testing.assert_array_equal(attention,[1,0])
        value,_=bag_logit_and_attention(np.array([0.,0.]),np.array([.2,.8]))
        self.assertEqual(value,0.)
        for logits,time in ((np.array([np.nan]),np.ones(1)),(np.ones(1),np.zeros(1)),
                            (np.ones(2),np.ones(2)),(np.ones(2),np.ones(1))):
            with self.assertRaises(ValueError): bag_logit_and_attention(logits,time)

    def test_analytic_gradient_matches_central_difference(self):
        x=np.zeros((2,1024)); x[0,:3]=[.2,.1,-.5]; x[1,:3]=[-.4,.8,.3]
        bags=[(x,np.array([.2,.8])),(x[::-1],np.array([.6,.4]))]
        labels=np.array([1.,0.]); weights=np.array([.4,.6])
        w=np.zeros(1024); w[:3]=[.3,-.4,.2]; b=.15
        _,gw,gb=loss_and_gradient(bags,labels,weights,w,b,.001)
        epsilon=1e-5
        for i in range(3):
            high=w.copy(); low=w.copy(); high[i]+=epsilon; low[i]-=epsilon
            finite=(loss_and_gradient(bags,labels,weights,high,b,.001)[0]
                    -loss_and_gradient(bags,labels,weights,low,b,.001)[0])/(2*epsilon)
            self.assertAlmostEqual(gw[i],finite,places=8)
        finite=(loss_and_gradient(bags,labels,weights,w,b+epsilon,.001)[0]
                -loss_and_gradient(bags,labels,weights,w,b-epsilon,.001)[0])/(2*epsilon)
        self.assertAlmostEqual(gb,finite,places=8)

    def test_fit_rejects_bad_training_without_labeling_each_window(self):
        positive=np.zeros((2,1024)); positive[0,0]=1; positive[1,1]=1
        negative=np.zeros((2,1024)); negative[:,1]=1
        bags=[(positive,np.ones(2)/2),(negative,np.ones(2)/2)]
        labels=np.array([1.,0.]); weights=np.ones(2)/2
        w,b=fit_mil(bags,labels,weights,.001)
        pos,_=bag_logit_and_attention(positive@w+b,np.ones(2)/2)
        neg,_=bag_logit_and_attention(negative@w+b,np.ones(2)/2)
        self.assertGreater(pos,neg)
        for labs,ws,penalty in ((np.ones(2),weights,.001),(labels,weights*2,.001),(labels,weights,.1)):
            with self.assertRaises(ValueError): fit_mil(bags,labs,ws,penalty)
        with self.assertRaises(ValueError): fit_mil([(positive*np.nan,np.ones(2)/2),bags[1]],labels,weights,.001)

    def test_selection_train_only_and_source_holdout(self):
        cases=training_cases(); bags=[]
        for i in range(6):
            x=np.zeros((1,1024)); x[0,i//2]=1
            bags.append((x,np.ones(1)))
        chosen,rows=select(bags,cases)
        self.assertIn(chosen,(.0001,.001,.01))
        for row in rows:
            for fold in row['folds']:
                self.assertFalse(set(fold['train_ids'])&set(fold['held_ids']))
            self.assertEqual(len(row['folds']),6)
        with self.assertRaises(ValueError): select(bags,[dict(cases[0],role='validation'),*cases[1:]])
        with self.assertRaises(ValueError): select(bags[:-1],cases)


if __name__=='__main__': unittest.main()
