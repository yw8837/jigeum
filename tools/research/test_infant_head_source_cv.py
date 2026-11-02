import unittest
import numpy as np
from infant_head_source_cv import folds,select_from_training,CLASSES


def training_cases():
    return [dict(id=str(c)+str(s),role='train',weak_class=label,sourcegroup=str(c)+str(s))
            for c,label in enumerate(CLASSES) for s in range(2)]


class SourceCVGuards(unittest.TestCase):
    def test_each_source_is_entirely_outside_its_training_fold(self):
        cases=training_cases()
        values=folds(cases)
        self.assertEqual(len(values),6)
        for group,train,held in values:
            self.assertEqual(set(train)&set(held),set())
            self.assertTrue(all(cases[i]['sourcegroup']!=group for i in train))
            self.assertTrue(all(cases[i]['sourcegroup']==group for i in held))
            self.assertEqual({cases[i]['weak_class'] for i in train},set(CLASSES))

    def test_validation_role_duplicate_and_class_absence_rejected(self):
        values=training_cases()
        for cases in ([dict(values[0],role='validation'),*values[1:]],values+[values[0]],values[:1]+values[2:]):
            with self.assertRaises(ValueError):
                folds(cases)

    def test_selection_uses_only_training_signal_and_finite_loss(self):
        cases=training_cases()
        matrix=np.zeros((6,1024))
        for i in range(6):
            matrix[i,i//2]=1
        chosen,scores=select_from_training(matrix,cases)
        self.assertIn(chosen,(.0001,.001,.01))
        self.assertEqual(len(scores),3)
        self.assertTrue(all(len(row['folds'])==6 for row in scores))
        self.assertTrue(all(np.isfinite(row['source_macro_nll']) for row in scores))
        self.assertTrue(all(all(fold['caption_argmax_matches']==1 for fold in row['folds']) for row in scores))
        with self.assertRaises(ValueError):
            select_from_training(matrix*np.nan,cases)


if __name__=='__main__':
    unittest.main()
