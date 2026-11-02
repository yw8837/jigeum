"""Public evaluation boundary guards with in-memory metadata mutations only."""
import json
import unittest
from unittest.mock import patch
import copy
from extra_babble_evaluation import sources,ROOT
from extra_babble_evaluation import verify_final_pins,SOURCES_SHA,CANDIDATE_SHA,HEAD_SHA


class ExtraSourceGuards(unittest.TestCase):
    path=ROOT/'work/infant-head-20261007/extra-babble-sources.json'

    def test_exact_sources_rebound_for_evaluation_without_training_approval(self):
        rows=sources(self.path)
        self.assertEqual(len(rows),10)
        self.assertEqual(len({r['sourcegroup'] for r in rows}),4)
        self.assertTrue(all(r['role']=='evaluation_only' and r['training_blocked']
                            and r['approved_training_label'] is None and not r['age_verified'] for r in rows))
        self.assertEqual(rows[-1]['caption_category'],'cartoon_grandpa_gibberish')
        self.assertTrue(all(r['speaker_claim_strength']=='tags_only' for r in rows if 'wjoojoo' in r['sourcegroup']))

    def test_wrong_sha_role_id_url_and_license_do_not_become_training_data(self):
        with patch('extra_babble_evaluation.digest',return_value='0'*64):
            with self.assertRaises(ValueError): sources(self.path)
        raw=json.loads(self.path.read_text('utf-8'))
        edits=(('training_blocked',False),('approved_training_label','infant'),('id','../private'),
               ('preview_url','https://example.com/private.mp3'),
               ('current_license_url','https://creativecommons.org/licenses/by-nc/4.0/'))
        for field,value in edits:
            changed=copy.deepcopy(raw); changed['files'][0][field]=value
            with patch('extra_babble_evaluation.read_report',return_value=changed):
                with self.assertRaises(ValueError): sources(self.path)

    def test_metadata_drift_after_acquisition_prevents_completion(self):
        from pathlib import Path
        model=Path('model.onnx');head=Path('head')
        pins={self.path:SOURCES_SHA,model:CANDIDATE_SHA,head/'head-ast-presence-public.npz':HEAD_SHA}
        with patch('extra_babble_evaluation.digest',side_effect=pins.__getitem__):
            self.assertEqual(verify_final_pins(self.path,model,head)['source'],SOURCES_SHA)
        for changed in pins:
            drift=dict(pins);drift[changed]='0'*64
            with patch('extra_babble_evaluation.digest',side_effect=drift.__getitem__):
                with self.assertRaises(ValueError): verify_final_pins(self.path,model,head)


if __name__=='__main__': unittest.main()
