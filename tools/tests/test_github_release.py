import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import github_release


class FakeGitHub:
    def __init__(self, release=None, files=None):
        self.release = release
        self.files = files or {}
        self.actions = []

    def get(self, tag):
        return self.release

    def create_draft(self, info, paths, notes):
        self.actions.append(('create', info, [p.name for p in paths], notes.read_text(encoding='utf-8')))

    def upload(self, tag, paths, replace=False):
        self.actions.append(('upload', tag, [p.name for p in paths], replace))

    def download(self, tag, name):
        return self.files[name]


class GitHubReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = self.root / 'version.properties'
        self.version.write_text('VERSION_NAME=1.0.0\nVERSION_CODE=17\n', encoding='utf-8')
        self.apk = self.root / 'qinglan-1.0.0.apk'
        self.apk.write_bytes(b'synthetic-verified-apk')
        self.sums = self.root / 'SHA256SUMS'
        self.sums.write_text(f'{hashlib.sha256(self.apk.read_bytes()).hexdigest()}  {self.apk.name}\n', encoding='utf-8')

    def metadata(self, names=(), draft=False, tag='1.0.0', prerelease=False):
        return {'tag_name': tag, 'draft': draft, 'prerelease': prerelease,
                'name': 'User title', 'body': 'User notes',
                'assets': [{'name': name, 'state': 'uploaded', 'size': 100} for name in names]}

    def attach(self, github, tag='1.0.0', notes=None):
        return github_release.attach(tag, self.root, github, self.version, notes)

    def test_release_ui_publish_receives_both_assets_without_rewriting_text(self):
        original = self.metadata()
        github = FakeGitHub(original)
        self.attach(github)
        self.assertEqual([('upload', '1.0.0', ['qinglan-1.0.0.apk', 'SHA256SUMS'], False)], github.actions)
        self.assertEqual('User title', original['name'])
        self.assertEqual('User notes', original['body'])

    def test_v1_release_ui_publish_uses_the_selected_tag(self):
        github = FakeGitHub(self.metadata(tag='v1.0.0'))
        self.attach(github, tag='v1.0.0')
        self.assertEqual([('upload', 'v1.0.0', ['qinglan-1.0.0.apk', 'SHA256SUMS'], False)], github.actions)

    def test_published_release_is_idempotent_and_skips_rebuild(self):
        github = FakeGitHub(self.metadata([self.apk.name, self.sums.name]))
        self.assertTrue(github_release.inspect('1.0.0', github, self.version))
        self.attach(github)
        self.assertEqual([], github.actions)

    def test_draft_can_replace_files_and_preserves_user_notes(self):
        github = FakeGitHub(self.metadata([self.apk.name, self.sums.name], draft=True))
        self.assertFalse(github_release.inspect('1.0.0', github, self.version))
        self.attach(github)
        self.assertTrue(github.actions[0][-1])
        self.assertEqual('User notes', github.release['body'])

    def test_pushed_tag_creates_draft_using_checked_in_notes(self):
        notes = self.root / 'notes.md'
        notes.write_text('清岚 1.0.0\n\nRelease notes.', encoding='utf-8')
        github = FakeGitHub()
        self.attach(github, tag='v1.0.0', notes=notes)
        self.assertEqual('create', github.actions[0][0])
        self.assertEqual('v1.0.0', github.actions[0][1]['tag'])
        self.assertEqual(notes.read_text(encoding='utf-8'), github.actions[0][3])

    def test_partial_upload_can_resume_only_for_identical_public_files(self):
        for path in (self.apk, self.sums):
            github = FakeGitHub(self.metadata([path.name]), {path.name: path.read_bytes()})
            self.attach(github)
            expected = self.sums.name if path == self.apk else self.apk.name
            self.assertEqual([('upload', '1.0.0', [expected], False)], github.actions)
            differing = FakeGitHub(self.metadata([path.name]), {path.name: b'different public build'})
            with self.assertRaisesRegex(ValueError, 'not replaced'):
                self.attach(differing)
            self.assertEqual([], differing.actions)

    def test_bad_checksum_missing_artifacts_and_wrong_tag_never_upload(self):
        github = FakeGitHub(self.metadata())
        self.sums.write_text('incorrect checksum', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'does not match'):
            self.attach(github)
        self.sums.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing'):
            self.attach(github)
        with self.assertRaisesRegex(ValueError, 'Tag must match'):
            self.attach(github, 'v2.0.0')
        self.assertEqual([], github.actions)

    def test_prerelease_flag_mismatch_and_zero_byte_asset_do_not_pass_preflight(self):
        wrong = FakeGitHub(self.metadata(prerelease=True))
        with self.assertRaisesRegex(ValueError, 'checkbox'):
            github_release.inspect('1.0.0', wrong, self.version)
        partial = self.metadata([self.apk.name, self.sums.name])
        partial['assets'][0]['size'] = 0
        self.assertFalse(github_release.inspect('1.0.0', FakeGitHub(partial), self.version))

    def test_immutable_public_release_requires_assets_before_publication(self):
        empty = self.metadata()
        empty['immutable'] = True
        github = FakeGitHub(empty)
        with self.assertRaisesRegex(ValueError, 'immutable'):
            github_release.inspect('1.0.0', github, self.version)
        with self.assertRaisesRegex(ValueError, 'immutable'):
            self.attach(github)
        self.assertEqual([], github.actions)
        complete = self.metadata([self.apk.name, self.sums.name])
        complete['immutable'] = True
        self.assertTrue(github_release.inspect('1.0.0', FakeGitHub(complete), self.version))

    def test_cli_uses_structured_arguments_and_only_404_means_missing(self):
        commands = []
        def runner(args, **kwargs):
            commands.append(args)
            return subprocess.CompletedProcess(args, 1, '', 'gh: Not Found (HTTP 404)')
        github = github_release.GitHubReleases('owner/browser', runner)
        self.assertIsNone(github.get('v1.0.0'))
        self.assertEqual(['gh', 'api', 'repos/owner/browser/releases/tags/v1.0.0'], commands[0])
        def forbidden(args, **kwargs):
            return subprocess.CompletedProcess(args, 1, '', 'HTTP 403; synthetic-secret')
        with self.assertRaises(ValueError) as error:
            github_release.GitHubReleases('owner/browser', forbidden).get('v1.0.0')
        self.assertNotIn('synthetic-secret', str(error.exception))
        with self.assertRaises(ValueError):
            github_release.GitHubReleases('owner/browser; echo token')

    def test_cli_never_clobbers_public_assets(self):
        commands = []
        def runner(args, **kwargs):
            commands.append(args)
            return subprocess.CompletedProcess(args, 0, '', '')
        github = github_release.GitHubReleases('owner/browser', runner)
        github.upload('1.0.0', [self.apk])
        self.assertNotIn('--clobber', commands[0])
        github.upload('1.0.0', [self.apk], replace=True)
        self.assertIn('--clobber', commands[1])


if __name__ == '__main__':
    unittest.main()
