"""Attach checked release artifacts to a GitHub release without replacing public files.

Invoked by Actions using its scoped GH_TOKEN. Existing release titles and notes are kept.
No tokens or raw API error bodies are logged.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import quote

import release


class GitHubReleases:
    def __init__(self, repo, runner=subprocess.run):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
            raise ValueError('Expected a GitHub owner/repository name')
        self.repo = repo
        self.runner = runner

    def command(self, *args, missing_ok=False):
        result = self.runner(['gh', *args], capture_output=True, text=True, check=False)
        if result.returncode:
            if missing_ok and 'HTTP 404' in result.stderr:
                return None
            raise ValueError('GitHub request failed. Check Actions permissions and retry; no token or response body logged.')
        return result.stdout

    def get(self, tag):
        raw = self.command('api', f'repos/{self.repo}/releases/tags/{quote(tag, safe="")}', missing_ok=True)
        return json.loads(raw) if raw is not None else None

    def create_draft(self, info, paths, notes):
        args = ['release', 'create', info['tag'], *map(str, paths), '--repo', self.repo,
                '--verify-tag', '--draft', '--title', f'清岚 {info["version"]}', '--notes-file', str(notes)]
        if info['prerelease']:
            args.append('--prerelease')
        self.command(*args)

    def upload(self, tag, paths, replace=False):
        args = ['release', 'upload', tag, *map(str, paths), '--repo', self.repo]
        if replace:
            args.append('--clobber')
        self.command(*args)

    def download(self, tag, name):
        with tempfile.TemporaryDirectory(prefix='qinglan-release-') as folder:
            self.command('release', 'download', tag, '--repo', self.repo, '--pattern', name, '--dir', folder)
            return (Path(folder) / name).read_bytes()


def files_for(info):
    return (f'qinglan-{info["version"]}.apk', 'SHA256SUMS')


def check_metadata(existing, info):
    if existing is None:
        return
    if existing.get('tag_name') != info['tag']:
        raise ValueError('Release tag does not match the requested tag')
    if existing.get('prerelease') is not info['prerelease']:
        raise ValueError('The Release prerelease checkbox must match VERSION_NAME')
    if not isinstance(existing.get('draft'), bool) or not isinstance(existing.get('assets'), list):
        raise ValueError('Invalid GitHub release metadata')


def ready_assets(existing):
    if existing is None:
        return set()
    return {a['name'] for a in existing['assets']
            if isinstance(a.get('name'), str) and a.get('state') == 'uploaded'
            and isinstance(a.get('size'), int) and a['size'] > 0}


def check_mutable(existing, info):
    if existing and not existing['draft'] and existing.get('immutable') is True:
        if not set(files_for(info)) <= ready_assets(existing):
            raise ValueError('This published release is immutable and cannot receive files. Build and attach files to a draft before publishing; see docs/releasing.md.')


def inspect(tag, github, version_file=release.ROOT / 'version.properties'):
    info = release.validate(tag, version_file)
    existing = github.get(tag)
    check_metadata(existing, info)
    check_mutable(existing, info)
    # Published binaries are preserved. Drafts may be rebuilt and replaced intentionally.
    return existing is not None and not existing['draft'] and set(files_for(info)) <= ready_assets(existing)


def attach(tag, folder, github, version_file=release.ROOT / 'version.properties', notes=None):
    info = release.validate(tag, version_file)
    folder = Path(folder)
    paths = [folder / name for name in files_for(info)]
    if not all(p.is_file() and p.stat().st_size > 0 for p in paths):
        raise ValueError('Missing APK or SHA256SUMS in release artifacts')
    checksum = f'{hashlib.sha256(paths[0].read_bytes()).hexdigest()}  {paths[0].name}\n'
    if paths[1].read_text(encoding='utf-8') != checksum:
        raise ValueError('SHA256SUMS does not match the exact APK')
    existing = github.get(tag)
    check_metadata(existing, info)
    check_mutable(existing, info)
    if existing is None:
        with tempfile.TemporaryDirectory(prefix='qinglan-notes-') as temporary:
            note_file = Path(notes) if notes is not None else release.ROOT / 'docs' / 'releases' / f'{info["version"]}.md'
            if not note_file.is_file():
                note_file = Path(temporary) / 'notes.md'
                note_file.write_text(f'清岚 {info["version"]}\n\n适用于 Android 8.0 及以上版本。下载 APK 安装；SHA256SUMS 用于校验文件完整性。\n', encoding='utf-8')
            github.create_draft(info, paths, note_file)
        return 'Created release draft with signed APK and checksum'
    if existing['draft']:
        github.upload(tag, paths, replace=True)
        return 'Updated draft artifacts; title and release notes kept'
    present = ready_assets(existing)
    missing = [p for p in paths if p.name not in present]
    if not missing:
        return 'Published artifacts already exist; no files replaced'
    # Recover partial uploads only when the published half belongs to this exact build.
    for path in paths:
        if path.name in present and github.download(tag, path.name) != path.read_bytes():
            raise ValueError('An existing public artifact differs from this build. It was not replaced; publish a new version or verify the partial upload manually.')
    github.upload(tag, missing)
    return 'Attached missing APK/checksum; public files, title and release notes kept'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['inspect', 'attach'])
    parser.add_argument('--tag', required=True)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--github-output', type=Path)
    parser.add_argument('--files', type=Path, default=Path('release'))
    args = parser.parse_args()
    try:
        github = GitHubReleases(args.repo)
        if args.command == 'inspect':
            complete = inspect(args.tag, github)
            if args.github_output:
                with args.github_output.open('a', encoding='utf-8', newline='\n') as stream:
                    stream.write(f'complete={str(complete).lower()}\n')
            print('Published artifacts already exist; build skipped' if complete else 'Release artifacts need building')
        else:
            print(attach(args.tag, args.files, github))
    except (ValueError, OSError) as error:
        parser.exit(1, f'{error}\n')


if __name__ == '__main__':
    main()
