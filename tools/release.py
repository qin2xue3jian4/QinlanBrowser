"""Validate the release version and assemble the public download files."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION_PATTERN = re.compile(r'(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?')


def validate(tag, version_file=ROOT / 'version.properties'):
    properties = {}
    for line in version_file.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#'):
            key, value = line.split('=', 1)
            if key.strip() in properties:
                raise ValueError('Duplicate version property')
            properties[key.strip()] = value.strip()
    version = properties.get('VERSION_NAME', '')
    if not VERSION_PATTERN.fullmatch(version):
        raise ValueError('VERSION_NAME must be a version such as 0.4.0 or 0.5.0-beta.1')
    code = int(properties.get('VERSION_CODE', '0'))
    if not 1 <= code <= 2100000000:
        raise ValueError('VERSION_CODE must be a positive Android version code')
    if tag not in (version, f'v{version}'):
        raise ValueError(f'Tag must match version.properties: {version} or v{version}')
    return {'tag': tag, 'version': version, 'code': code, 'prerelease': '-' in version}


def package(tag, apk, output=ROOT / 'dist' / 'release', version_file=ROOT / 'version.properties'):
    info = validate(tag, version_file)
    if not zipfile.is_zipfile(apk):
        raise ValueError('APK is missing or is not a ZIP archive')
    with zipfile.ZipFile(apk) as archive:
        if 'AndroidManifest.xml' not in archive.namelist():
            raise ValueError('APK is missing AndroidManifest.xml')
    output.mkdir(parents=True, exist_ok=True)
    name = f'qinglan-{info["version"]}.apk'
    if any(p.name not in {name, 'SHA256SUMS'} for p in output.iterdir()):
        raise ValueError('Output directory contains files from another release; use an empty directory')
    dest = output / name
    shutil.copyfile(apk, dest)
    digest = hashlib.sha256(dest.read_bytes()).hexdigest()
    (output / 'SHA256SUMS').write_text(f'{digest}  {name}\n', encoding='utf-8', newline='\n')
    return dest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['validate', 'package'])
    parser.add_argument('--tag', required=True)
    parser.add_argument('--github-output', type=Path)
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    try:
        info = validate(args.tag)
        if args.command == 'package':
            if args.apk is None:
                parser.error('package requires --apk')
            print(package(args.tag, args.apk))
        else:
            if args.github_output:
                with args.github_output.open('a', encoding='utf-8', newline='\n') as stream:
                    for key in ('tag', 'version', 'prerelease'):
                        value = str(info[key]).lower() if isinstance(info[key], bool) else info[key]
                        stream.write(f'{key}={value}\n')
            print(f'Validated {info["tag"]} (version code {info["code"]})')
    except (ValueError, OSError) as error:
        parser.exit(1, f'{error}\n')


if __name__ == '__main__':
    main()
