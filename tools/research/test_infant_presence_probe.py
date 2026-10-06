"""Presence research guards; synthetic inputs are never baby quality evidence."""
import unittest
import numpy as np
from infant_presence_probe import (sigmoid,presence_weights,fit_binary,select,supported,
                                   windows_for,temporal_support)
from test_infant_head_source_cv import training_cases


class PresenceGuards(unittest.TestCase):
    def test_sigmoid_extremes_nonfinite_and_support_domain(self):
        values=sigmoid(np.array([-1000.,0.,1000.]))
        np.testing.assert_array_equal(values,[0.,.5,1.])
        for wrong in (np.array([np.nan]),np.array([np.inf])):
            with self.assertRaises(ValueError):
                sigmoid(wrong)
        self.assertTrue(supported(.8))
        self.assertFalse(supported(.799))
        for wrong in (np.nan,np.inf,-.01,1.01):
            with self.assertRaises(ValueError):
                supported(wrong)

    def test_binary_class_source_balancing_and_role_label_guard(self):
        cases=training_cases()
        weight=presence_weights(cases)
        self.assertAlmostEqual(weight[:2].sum(),.5)
        self.assertAlmostEqual(weight[2:].sum(),.5)
        duplicated=[cases[0],*cases]
        weighted=presence_weights(duplicated)
        self.assertAlmostEqual(weighted[0]+weighted[1],.25)
        for wrong in ([dict(cases[0],role='validation'),*cases[1:]],
                      [dict(cases[0],weak_class='private'),*cases[1:]],cases[:2]):
            with self.assertRaises(ValueError):
                presence_weights(wrong)

    def test_fit_domains_determinism_and_pure_binary_signal(self):
        x=np.zeros((2,1024))
        x[0,0]=1
        x[1,1]=1
        labels=np.array([1.,0.])
        weights=np.ones(2)/2
        w,b=fit_binary(x,labels,weights,.001)
        self.assertGreater(float(sigmoid(x[0]@w+b)),.8)
        self.assertLess(float(sigmoid(x[1]@w+b)),.2)
        w2,b2=fit_binary(x,labels,weights,.001)
        np.testing.assert_array_equal(w,w2)
        self.assertEqual(b,b2)
        for matrix,labs,ws,p in ((x[:,:3],labels,weights,.001),(x*np.nan,labels,weights,.001),
                (x,np.ones(2),weights,.001),(x,labels,weights*2,.001),
                (x,labels,np.array([1.,-1.]),.001),(x,labels,weights,.1)):
            with self.assertRaises(ValueError):
                fit_binary(matrix,labs,ws,p)

    def test_selection_source_only_complete_holdout_and_bad_shape(self):
        cases=training_cases()
        x=np.zeros((6,1024))
        for i in range(6):
            x[i,i//2]=1
        chosen,rows=select(x,cases)
        self.assertIn(chosen,(.0001,.001,.01))
        for row in rows:
            self.assertEqual(len(row['folds']),6)
            self.assertTrue(np.isfinite(row['source_macro_nll']))
            for fold in row['folds']:
                self.assertFalse(set(fold['train_ids'])&set(fold['held_ids']))
        for matrix,items in ((x[:,:3],cases),(x*np.nan,cases),
                (x,[dict(cases[0],role='validation'),*cases[1:]])):
            with self.assertRaises(ValueError):
                select(matrix,items)

    def test_quiet_exclusion_actual_support_and_adjacency(self):
        x=np.zeros((3,1024)); x[:,0]=1
        w=np.zeros(1024); w[0]=4
        frames=[dict(rms=.1,real_samples=15600),dict(rms=.002,real_samples=15600),
                dict(rms=.1,real_samples=1000)]
        rows=windows_for(x,frames,w,0.)
        self.assertIsNone(rows[1]['infant_score'])
        self.assertFalse(rows[1]['infant_support'])
        result=temporal_support(rows)
        self.assertAlmostEqual(result['support_seconds'],1.0375)
        self.assertFalse(result['consecutive_support'])
        frames[1]['rms']=.1
        self.assertTrue(temporal_support(windows_for(x,frames,w,0.))['consecutive_support'])
        with self.assertRaises(ValueError):
            windows_for(x[:1],frames,w,0.)


if __name__=='__main__':
    unittest.main()
