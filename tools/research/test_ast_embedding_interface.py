import unittest
from types import SimpleNamespace
import numpy as np
from ast_embedding_interface import compare,validate_inputs


class ASTInterfaceGuards(unittest.TestCase):
    def test_exact_logits_tiny_change_shape_and_nonfinite(self):
        x=np.zeros((1,527),np.float32)
        self.assertEqual(compare(x,x.copy()),0.)
        changed=x.copy(); changed[0,-1]=1e-7
        for wrong in (changed,np.full((1,527),np.nan,np.float32),
                      np.zeros((1,526),np.float32),x.astype(np.float64)):
            with self.assertRaises(ValueError): compare(x,wrong)

    def test_wrong_source_sha_rejected_before_public_path_access(self):
        with self.assertRaisesRegex(ValueError,'source SHA'):
            validate_inputs(SimpleNamespace(sources_sha='0'*64))


if __name__=='__main__': unittest.main()
