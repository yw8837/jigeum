"""Tiny synthetic archive fixtures. No actual model or publication approval."""
from pathlib import Path
import hashlib,tempfile,unittest,zipfile
from unittest.mock import patch
import verify_ast_payload as v

class CaptionPayloadTest(unittest.TestCase):
 def make(self,path,*,base=False,head=True,bad=None,notice=True):
  prefix='base/' if path.suffix=='.aab' else ''
  with zipfile.ZipFile(path,'w') as z:
   z.writestr(prefix+'assets/'+(v.MODEL if base else v.CANDIDATE_MODEL),b'graph')
   for name in ('ONNX_RUNTIME_LICENSE.txt','ONNX_RUNTIME_THIRD_PARTY_NOTICES.txt','AST_MODEL_NOTICE.txt','ONNX_RUNTIME_NOTICE.txt'):z.writestr(prefix+'assets/'+name,b'notice')
   if head:
    z.writestr(prefix+'assets/'+v.CAPTION_HEAD,bad if bad is not None else b'x'*3076)
    if notice:z.writestr(prefix+'assets/AST_CAPTION_NOTICE.txt',b'notice')
   for abi in v.ABIS:
    for lib in ('libonnxruntime.so','libonnxruntime4j_jni.so'):z.writestr(prefix+'lib/'+abi+'/'+lib,b'native')
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory(prefix='caption-archive-unit-');self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
  self.pins=patch.multiple(v,MODEL_SIZE=5,CANDIDATE_SIZE=5,MODEL_SHA=hashlib.sha256(b'graph').hexdigest(),CANDIDATE_SHA=hashlib.sha256(b'graph').hexdigest(),CAPTION_HEAD_SHA=hashlib.sha256(b'x'*3076).hexdigest(),NOTICES={n:hashlib.sha256(b'notice').hexdigest() for n in v.NOTICES});self.pins.start();self.addCleanup(self.pins.stop)
 def test_correct_apk_and_aab_require_both_graph_and_caption(self):
  for ext in ('.apk','.aab'):
   p=self.root/('valid'+ext);self.make(p);self.assertEqual('pass',v.verify(p,'present','infant-caption')['status'])
 def test_missing_size_changed_hash_and_missing_notice_reject(self):
  cases=[dict(head=False),dict(bad=b'x'*3075),dict(bad=b'y'*3076),dict(notice=False)]
  for i,kw in enumerate(cases):
   p=self.root/f'bad{i}.apk';self.make(p,**kw)
   with self.assertRaises((ValueError,KeyError)):v.verify(p,'present','infant-caption')
 def test_old_model_modes_and_release_exclusion_reject_extra_caption(self):
  for mode,base in [('base',True),('infant-candidate',False)]:
   p=self.root/(mode+'.apk');self.make(p,base=base)
   with self.assertRaises(ValueError):v.verify(p,'present',mode)
   with self.assertRaises(ValueError):v.verify(p,'absent')

if __name__=='__main__':unittest.main()
