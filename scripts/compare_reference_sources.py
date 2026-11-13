#!/usr/bin/env python3
"""Screen candidate source for exact, normalized four-line reference windows.

This is a limited similarity screen, not a plagiarism or license determination.
Only local reference paths supplied on the command line are inspected.
"""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path

from audit_source_tree import files

EXTENSIONS = {'.c', '.h', '.cpp', '.hpp', '.java', '.py', '.sh', '.m',
              '.mm', '.swift', '.S', '.gradle', '.xml', '.md', '.txt', '.cmake'}
SKIP = {'.git', 'build', 'tests', 'test', 'testdata', 'third_party', 'docs',
        'examples', 'doc', '__pycache__'}


def windows(lines):
    for i in range(len(lines) - 3):
        text = '\n'.join(line.strip() for line in lines[i:i + 4])
        if len(text) >= 140 and sum(c.isalnum() for c in text) >= 90:
            yield i + 1, hashlib.sha256(text.encode('utf-8')).digest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, action='append', required=True,
                        help='Local reference directory; repeat for each source tree')
    parser.add_argument('--output', type=Path, required=True, help='New JSON report')
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Output already exists; choose a new path')
    for root in args.reference:
        if not root.is_dir():
            parser.error(f'Reference directory does not exist: {root}')
    index = defaultdict(list)
    for rel, kind, data in files():
        if (kind != 'file' or rel.parts[0] not in {'app', 'native', 'experiments', 'scripts', 'tests'}
                or rel.suffix not in EXTENSIONS or 'licenses' in rel.parts):
            continue
        try:
            lines = data.decode('utf-8').splitlines()
        except UnicodeError:
            continue
        for line, digest in windows(lines):
            index[digest].append((rel.as_posix(), line))
    matches = defaultdict(list)
    scanned = 0
    for root in args.reference:
        for path in root.rglob('*'):
            rel = path.relative_to(root)
            if (path.is_symlink() or not path.is_file() or any(x in SKIP for x in rel.parts)
                    or path.suffix not in EXTENSIONS or path.stat().st_size > 2_000_000):
                continue
            try:
                lines = path.read_text(encoding='utf-8').splitlines()
            except (UnicodeError, OSError):
                continue
            scanned += 1
            for line, digest in windows(lines):
                for candidate in index.get(digest, []):
                    if len(matches[candidate]) < 5:
                        matches[candidate].append({'reference_root': root.name,
                                                   'file': rel.as_posix(), 'line': line})
    report = {'limitations': 'Exact four-line windows only; not proof of origin or rights.',
              'reference_files': scanned, 'fingerprints': len(index),
              'matches': [{'file': name, 'line': line, 'references': refs}
                          for (name, line), refs in sorted(matches.items())]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2)
        stream.write('\n')
    print(f'Screened {scanned} reference files; {len(matches)} matching candidate windows.')
    print('Report:', args.output)
    print('Review matches and omissions manually; a clean result does not prove provenance.')


if __name__ == '__main__':
    main()
