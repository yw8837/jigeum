"""Exercise early option rejection without signing keys, builds or destinations."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class BundleOptionTest(unittest.TestCase):
    def test_invalid_infant_options_reject_before_signing_or_build(self):
        powershell = shutil.which('pwsh')
        self.assertIsNotNone(powershell, 'PowerShell is required for this Windows packaging gate')
        with tempfile.TemporaryDirectory(prefix='agattok-options-') as temp:
            destination = Path(temp) / 'never-created-beta-code33.aab'
            cases = [
                (['-Purpose', 'closed-test', '-InfantCaptionSupport'], 'Caption support requires'),
                (['-Purpose', 'closed-test', '-AstBabbleSupport', '-InfantCaptionSupport'], 'Caption support requires'),
                (['-Purpose', 'internal', '-InfantPresenceSupport'], 'requires closed-test or explicit production and AstBabbleSupport'),
                (['-Purpose', 'closed-test', '-InfantPresenceSupport'], 'requires closed-test or explicit production and AstBabbleSupport'),
                (['-Purpose', 'closed-test', '-AstBabbleSupport', '-InfantPresenceSupport'], 'Provide actual ARM proof'),
                (['-Purpose', 'closed-test', '-InfantArmProof', 'missing.json'], 'ARM proof arguments require'),
                (['-Purpose', 'internal', '-AstBabbleSupport'], 'valid only for closed-test'),
                (['-Purpose', 'production-basic'], 'Production requires the complete'),
                (['-Purpose', 'paid-candidate', '-AstBabbleSupport'], 'Production requires the complete'),
                (['-Purpose', 'production-basic', '-AstBabbleSupport', '-InfantPresenceSupport',
                  '-InfantCaptionSupport'], 'Provide actual ARM proof'),
                (['-Purpose', 'production-basic', '-AstBabbleSupport', '-InfantPresenceSupport',
                  '-InfantCaptionSupport', '-CaptionReleaseQaProof',
                  '-InfantArmProof', str(Path(temp) / 'missing.json'), '-ProofPython', sys.executable],
                 'ARM proof rejected; no signing or build started'),
                (['-Purpose', 'closed-test', '-AstBabbleSupport', '-InfantPresenceSupport',
                  '-InfantArmProof', str(Path(temp) / 'missing.json'), '-ProofPython', sys.executable],
                 'ARM proof rejected; no signing or build started'),
            ]
            for args, expected in cases:
                with self.subTest(args=args):
                    result = subprocess.run([powershell, '-NoProfile', '-File',
                        str(ROOT / 'tools/release/build-play-bundle.ps1'), '-Destination', str(destination),
                        '-SigningDirectory', str(Path(temp) / 'absent-signing'), *args],
                        capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=20)
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(expected, result.stdout + result.stderr)
                    self.assertFalse(destination.exists())


if __name__ == '__main__':
    unittest.main()
