"""Adversarial checks for the signed update verifier's reported false-pass paths."""
import unittest
from signed_media_upgrade import finish_status, verify_install, verify_legacy_record


class SignedMediaUpgradeVerifierTest(unittest.TestCase):
    def setUp(self):
        self.seed = dict(id=101, phrase='unique run record', sound='옹알이', context='QA',
                         note='synthetic', source='VIDEO_FILE', modelCue='')

    def backup(self, *rows):
        return dict(format='jigeum-observations', version=2, records=list(rows))

    def test_same_phrase_in_an_old_record_does_not_replace_missing_id(self):
        stale = dict(self.seed, id=100, basis='LEGACY')
        with self.assertRaises(AssertionError):
            verify_legacy_record(self.backup(stale), self.seed)

    def test_neighboring_legacy_label_does_not_hide_wrong_basis(self):
        wrong = dict(self.seed, basis='AUDIO')
        neighbor = dict(self.seed, id=100, basis='LEGACY')
        with self.assertRaises(AssertionError):
            verify_legacy_record(self.backup(wrong, neighbor), self.seed)

    def test_exact_id_with_changed_content_fails(self):
        with self.assertRaises(AssertionError):
            verify_legacy_record(self.backup(dict(self.seed, phrase='changed', basis='LEGACY')), self.seed)

    def test_duplicate_id_is_ambiguous(self):
        row = dict(self.seed, basis='LEGACY')
        with self.assertRaises(AssertionError):
            verify_legacy_record(self.backup(row, row), self.seed)

    def test_exact_record_with_legacy_basis_passes(self):
        row = dict(self.seed, basis='LEGACY')
        self.assertEqual(verify_legacy_record(self.backup(row), self.seed), row)

    def test_increased_but_wrong_version_or_distribution_fails(self):
        installed = dict(verified=True, package='com.moa.babylanguage', version_code=23, version_name='0.5.9-beta')
        with self.assertRaises(AssertionError):
            verify_install(installed, 24, '0.5.10-beta')
        installed.update(version_code=24, version_name='0.5.10')
        with self.assertRaises(AssertionError):
            verify_install(installed, 24, '0.5.10-beta')
        installed['version_name'] = '0.5.10-beta'
        verify_install(installed, 24, '0.5.10-beta')

    def test_cleanup_failure_cannot_leave_pass_status(self):
        result = dict(status='pass', checks_passed=True)
        finish_status(result, RuntimeError('fixture remains'))
        self.assertEqual(result['status'], 'fail')
        self.assertIn('fixture remains', result['cleanup_error'])

    def test_cleanup_success_does_not_mask_test_failure(self):
        result = dict(status='fail', checks_passed=True)
        finish_status(result)
        self.assertEqual(result['status'], 'fail')


if __name__ == '__main__':
    unittest.main()
