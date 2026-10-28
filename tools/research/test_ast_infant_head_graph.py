"""Graph preservation and numeric head checks; no baby or phone accuracy claims."""
import unittest
import numpy as np
import onnx
import onnxruntime as ort
from ast_infant_head_graph import graph_bytes,MODEL,PREFIX,OUTPUT,EMBEDDING
from infant_presence_probe import sigmoid


class GraphGuards(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weights=np.linspace(-2,2,768,dtype=np.float32)
        cls.bias=np.asarray(.25,np.float32)
        cls.original=onnx.load(str(MODEL),load_external_data=False)
        cls.candidate=onnx.load_from_string(graph_bytes(cls.weights,cls.bias))

    def test_all_original_nodes_weights_inputs_and_outputs_preserved(self):
        before=self.original.graph; after=self.candidate.graph
        self.assertEqual(len(after.node),len(before.node)+8)
        self.assertEqual([n.SerializeToString() for n in before.node],
                         [n.SerializeToString() for n in after.node[:len(before.node)]])
        self.assertEqual([w.SerializeToString() for w in before.initializer],
                         [w.SerializeToString() for w in after.initializer[:len(before.initializer)]])
        self.assertEqual([v.SerializeToString() for v in before.input],[v.SerializeToString() for v in after.input])
        self.assertEqual(before.output[0].SerializeToString(),after.output[0].SerializeToString())
        self.assertEqual([v.name for v in after.output],['logits',EMBEDDING,OUTPUT])

    def test_isolated_head_matches_finite_numpy_normalization_and_zero_floor(self):
        original_count=len(self.original.graph.node)
        head=onnx.helper.make_graph(list(self.candidate.graph.node[original_count:]),'synthetic-head',
            [onnx.helper.make_tensor_value_info(EMBEDDING,onnx.TensorProto.FLOAT,[1,768])],
            [onnx.helper.make_tensor_value_info(OUTPUT,onnx.TensorProto.FLOAT,[1,1])],
            initializer=[t for t in self.candidate.graph.initializer if t.name.startswith(PREFIX)])
        model=onnx.helper.make_model(head,opset_imports=list(self.candidate.opset_import))
        model.ir_version=self.candidate.ir_version
        onnx.checker.check_model(model)
        session=ort.InferenceSession(model.SerializeToString(),providers=['CPUExecutionProvider'])
        for vector in (np.zeros(768,np.float32),np.ones(768,np.float32),
                       np.linspace(-10000,10000,768,dtype=np.float32),np.eye(1,768,dtype=np.float32)[0]):
            actual=float(session.run(None,{EMBEDDING:vector[None]})[0][0,0])
            norm=max(float(np.linalg.norm(vector.astype(np.float64))),1e-12)
            reference=float(sigmoid((vector.astype(np.float64)/norm)@self.weights+self.bias))
            self.assertTrue(np.isfinite(actual))
            self.assertLessEqual(abs(actual-reference),5e-5)


if __name__=='__main__': unittest.main()
