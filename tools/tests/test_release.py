import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = self.root / 'version.properties'
        self.write_version('0.4.0', 4)

    def write_version(self, name, code):
        self.version.write_text(f'VERSION_NAME={name}\nVERSION_CODE={code}\n', encoding='utf-8')

    def test_tag_must_match_version(self):
        self.assertEqual(4, release.validate('v0.4.0', self.version)['code'])
        self.assertEqual('0.4.0', release.validate('0.4.0', self.version)['tag'])
        for tag in ('v0.5.0', 'main', 'v0.4.0\nother=value', '../v0.4.0'):
            with self.assertRaises(ValueError):
                release.validate(tag, self.version)

    def test_prerelease_and_invalid_versions(self):
        self.write_version('0.5.0-beta.1', 5)
        self.assertTrue(release.validate('v0.5.0-beta.1', self.version)['prerelease'])
        for name, code in [('0.5.0', 0), ('0.5.0', -1), ('0.5.0', 2100000001), ('0.5.0/evil', 5), ('x', 5)]:
            self.write_version(name, code)
            with self.assertRaises(ValueError):
                release.validate('v' + name, self.version)

    def test_package_and_checksum_are_for_exact_apk(self):
        apk = self.root / 'app.apk'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('AndroidManifest.xml', 'synthetic manifest')
        dest = release.package('v0.4.0', apk, self.root / 'out', self.version)
        self.assertEqual(apk.read_bytes(), dest.read_bytes())
        self.assertEqual(f'{hashlib.sha256(apk.read_bytes()).hexdigest()}  qinglan-0.4.0.apk\n', (dest.parent / 'SHA256SUMS').read_text())
        (dest.parent / 'old.apk').write_bytes(b'old')
        with self.assertRaises(ValueError):
            release.package('v0.4.0', apk, dest.parent, self.version)

    def test_rejects_non_apk_input(self):
        apk = self.root / 'bad.apk'
        apk.write_bytes(b'not an apk')
        with self.assertRaises(ValueError):
            release.package('v0.4.0', apk, self.root / 'out', self.version)


if __name__ == '__main__':
    unittest.main()
