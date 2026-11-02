"""Pure synthetic checks; no network, media acquisition or classifier calls."""
import unittest
import numpy as np
from infant_preview_interface import dequantize_embedding, filter_actual_public_pcm


class PreviewFeatureTest(unittest.TestCase):
    def test_quantized_zero_and_extreme_decode_to_real_values(self):
        value=np.full((1,1,1,1024),-128,dtype=np.int8)
        value[0,0,0,1]=127
        output=dequantize_embedding(value,dict(quantization=(0.022328350692987442,-128)))
        self.assertEqual(output.dtype,np.float32)
        self.assertEqual(float(output[0,0,0,0]),0.)
        self.assertAlmostEqual(float(output[0,0,0,1]),255*.022328350692987442,places=6)

    def test_wrong_quantization_dtype_or_shape_is_rejected(self):
        good=np.zeros((1,1,1,1024),np.int8)
        for value,metadata in ((good.astype(np.float32),(0.022328350692987442,-128)),
                (good.reshape(1024),(0.022328350692987442,-128)),
                (good,(0.,-128)),(good,(0.022328350692987442,0))):
            with self.assertRaises(ValueError):
                dequantize_embedding(value,dict(quantization=metadata))

    def test_short_dc_removed_before_padding_without_changing_source(self):
        pcm=np.full(9328,.15,np.float32)
        old=pcm.tobytes()
        value=filter_actual_public_pcm(pcm)
        self.assertEqual(pcm.tobytes(),old)
        self.assertEqual(value.shape,(9328,))
        self.assertTrue(np.all(value==0))

    def test_short_invalid_pcm_rejected(self):
        for pcm in (np.zeros(0,np.float32),np.zeros(100,np.float64),
                np.zeros((100,1),np.float32),np.full(100,float('nan'),np.float32),np.full(100,1.01,np.float32)):
            with self.assertRaises(ValueError):
                filter_actual_public_pcm(pcm)


if __name__=='__main__':
    unittest.main()
