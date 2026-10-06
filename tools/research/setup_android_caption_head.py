"""Stage only the frozen public caption head into the exact research asset path."""
import hashlib
from pathlib import Path
import sys
from types import SimpleNamespace

from ast_caption_dual_probe import CAPTION_HEAD, CAPTION_SHA
from ast_caption_wholebag_probe import ROOT, verify_head

DESTINATION = ROOT/'outputs/MoaAndroid/app/src/infantCaption/assets/ast-caption-presence.f32'
BINARY_SHA = '979e79448a5b27293c02642eb8a72ccc6fbf322fd16cc9ee43a467e7434c2df1'


def bytes_for_head():
    import numpy as np
    verify_head(SimpleNamespace(head=CAPTION_HEAD,head_sha=CAPTION_SHA))
    with np.load(CAPTION_HEAD/'head-ast-presence-public.npz',allow_pickle=False) as data:
        w,b=data['weights'],data['bias']
        if (w.shape!=(768,) or b.shape!=() or w.dtype!=np.float32 or b.dtype!=np.float32
                or not np.isfinite(w).all() or not np.isfinite(b)):
            raise ValueError('Invalid frozen caption head tensors')
        encoded=np.concatenate((w,np.asarray([b],np.float32))).astype('<f4').tobytes()
    if len(encoded)!=3076 or hashlib.sha256(encoded).hexdigest()!=BINARY_SHA:
        raise ValueError('Frozen caption binary differs')
    return encoded


def main():
    value=bytes_for_head()
    if DESTINATION.is_symlink():raise ValueError('Symlink asset prohibited')
    if DESTINATION.exists():
        if DESTINATION.read_bytes()!=value:raise ValueError('Existing asset differs; preserve it')
        print('Frozen caption asset already present');return
    DESTINATION.parent.mkdir(parents=True,exist_ok=True)
    with DESTINATION.open('xb') as handle:handle.write(value)
    print('Frozen caption asset staged: 3076 bytes, '+BINARY_SHA)


if __name__=='__main__':main()
