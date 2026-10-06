"""Public evaluation boundary guards with in-memory metadata mutations only."""
import json
import unittest
from unittest.mock import patch
import copy
from extra_babble_evaluation import sources,ROOT


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


if __name__=='__main__': unittest.main()
