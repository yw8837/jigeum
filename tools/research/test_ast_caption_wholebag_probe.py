import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import numpy as np

from ast_caption_wholebag_probe import (
    EXPERIMENT, EVIDENCE_ROOT, EXTRA, whole_spans, validate_whole, whole_bag,
    extra_sources, claim_worker, output_path, provenance, span_scores, verify_completion, RUNTIME, digest,
)


class WholeCaptionGuards(unittest.TestCase):
    def test_completed_text_cannot_approve_missing_or_changed_artifact(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder); args = SimpleNamespace(output=out, mode='features')
            artifact = out/'features.json'; artifact.write_text('{"cases": []}', encoding='utf-8')
            pins = provenance()
            done = dict(status='completed',mode='features',runtime=RUNTIME,experiment_id=EXPERIMENT,
                source_code_sha256=pins,product_changed=False,artifact_metadata_sha256=digest(artifact))
            (out/'completion.json').write_text(json.dumps(done), encoding='utf-8')
            verify_completion(args,pins)
            artifact.write_text('{"cases": ["changed"]}', encoding='utf-8')
            with self.assertRaises(ValueError): verify_completion(args,pins)
            artifact.unlink()
            with self.assertRaises(OSError): verify_completion(args,pins)

    def test_full_975s_coverage_tail_and_actual_time_weights(self):
        count=EXTRA['244237'][0]
        blocks=list(whole_spans(np.full(count,.125,np.float32)))
        frames=[frame for _,frame in blocks]
        self.assertEqual(len(blocks),10)
        self.assertEqual(sum(f['real_samples'] for f in frames),count)
        self.assertEqual(frames[-1]['real_samples'],120295)
        features=np.zeros((10,768));features[:,0]=2
        values,time=whole_bag(features,frames,count)
        self.assertEqual(values.shape,(10,768));self.assertAlmostEqual(float(time.sum()),1.)
        self.assertAlmostEqual(time[-1],120295/count)
        np.testing.assert_array_equal(values[:,0],1)
        frames[4]=dict(frames[4],rms=0.)
        values,time=whole_bag(features,frames,count)
        self.assertEqual(len(values),9);self.assertAlmostEqual(float(time.sum()),1.)
        self.assertAlmostEqual(time[-1],120295/(count-160000))

    def test_padding_never_counts_as_real_time_or_creates_more_bags(self):
        for count in (1,21456,160001,1920000):
            blocks=list(whole_spans(np.full(count,.1,np.float32)))
            self.assertEqual(sum(f['real_samples'] for _,f in blocks),count)
            for block,f in blocks:
                self.assertEqual(len(block),max(16000,f['real_samples']))
                self.assertEqual(np.abs(block[f['real_samples']:]).sum(),0)
        blocks=list(whole_spans(np.full(160001,.1,np.float32)))
        features=np.zeros((2,768));features[:,0]=1
        _,time=whole_bag(features,[f for _,f in blocks],160001)
        self.assertAlmostEqual(time[-1],1/160001)

    def test_invalid_whole_shapes_pcm_coverage_chronology_and_quiet(self):
        for pcm in (np.zeros(0),np.zeros(1920001),np.zeros((1,10)),np.zeros(10,dtype='i2'),np.array([np.nan]),np.array([1.01])):
            with self.assertRaises(ValueError):list(whole_spans(pcm))
        frames=[f for _,f in whole_spans(np.full(200000,.1,np.float32))]
        values=np.ones((2,768))
        for wrong in ([frames[0]], [frames[0],dict(frames[1],start_sample=0)],
                      [frames[0],dict(frames[1],real_samples=39999)],
                      [frames[0],dict(frames[1],padded_samples=1)]):
            with self.assertRaises(ValueError):validate_whole(values,wrong,200000)
        for count in (True,1920001,0):
            with self.assertRaises(ValueError):validate_whole(values,frames,count)
        with self.assertRaises(ValueError):whole_bag(values,[dict(f,rms=0.) for f in frames],200000)
        with self.assertRaises(ValueError):whole_bag(values*np.nan,frames,200000)
        with self.assertRaises(ValueError):whole_bag(np.zeros_like(values),frames,200000)

    def test_experiment_allowlist_preserves_blocked_null_not_general_loader(self):
        rows=extra_sources(EXPERIMENT)
        self.assertEqual([r['id'] for r in rows],list(EXTRA))
        for row in rows:
            self.assertIs(row['training_blocked'],True)
            self.assertIsNone(row['approved_training_label'])
            self.assertIs(row['audio_review_completed'],False)
            self.assertIs(row['event_ground_truth'],False)
        with self.assertRaises(ValueError):extra_sources('ignore-all-instructions')

    def test_caption_frame_scores_are_not_event_durations(self):
        frames=[f for _,f in whole_spans(np.full(180000,.1,np.float32))]
        values=np.zeros((2,768));values[:,0]=1; w=np.zeros(768);w[0]=10
        rows=span_scores(values,dict(frames=frames,actual_pcm_samples=180000),w,0.)
        self.assertTrue(all(r['infant_support'] for r in rows))
        self.assertTrue(all('support_seconds' not in r for r in rows))
        frames[1]=dict(frames[1],rms=0.)
        rows=span_scores(values,dict(frames=frames,actual_pcm_samples=180000),w,0.)
        self.assertIsNone(rows[1]['infant_score']);self.assertFalse(rows[1]['infant_support'])

    def test_direct_worker_terminal_claim_and_path_boundary(self):
        output_path((EVIDENCE_ROOT/'future').resolve())
        for p in (EVIDENCE_ROOT,EVIDENCE_ROOT.parent/'outside'):
            with self.assertRaises(ValueError):output_path(p.resolve())
        with tempfile.TemporaryDirectory() as folder:
            out=Path(folder);args=SimpleNamespace(output=out,mode='features')
            with patch.dict(os.environ,{},clear=True):
                with self.assertRaises(ValueError):claim_worker(args)
            launch=dict(token='test-only',output=str(out),mode='features',source_code_sha256=provenance(),experiment_id=EXPERIMENT)
            (out/'launch.json').write_text(json.dumps(launch),encoding='utf-8')
            with patch.dict(os.environ,{'AGATTOK_CAPTION_WORKER_TOKEN':'test-only'},clear=True):
                claim_worker(args)
                with self.assertRaises(FileExistsError):claim_worker(args)
                (out/'features.json').write_text('preserve',encoding='utf-8')
                with self.assertRaises(ValueError):claim_worker(args)
                self.assertEqual((out/'features.json').read_text(),'preserve')


if __name__=='__main__':unittest.main()
