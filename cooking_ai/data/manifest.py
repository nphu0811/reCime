"""Validate explicit training rights and prevent source/frame/hash leakage."""
import argparse
import hashlib
import json
from pathlib import Path
from typing import Literal
from pydantic import Field
from cooking_ai.schema import Strict, Recipe


class Annotation(Strict):
    id: str = Field(min_length=1, max_length=100)
    source_id: str = Field(min_length=1, max_length=300)
    kind: Literal['image', 'video', 'text']
    media: str | None
    sha256: str = Field(pattern=r'^[0-9a-f]{64}$')
    text: str = Field(max_length=16000)
    attribution: str = Field(min_length=1, max_length=2000)
    license: str = Field(min_length=1, max_length=300)
    permission_to_train: bool
    permission_evidence: str = Field(min_length=1, max_length=2000)
    language: str = Field(min_length=1, max_length=30)
    split: Literal['train', 'validation', 'test']
    fixture: bool = False
    annotation_verified: bool = False
    target: Recipe


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def media_path(root, value):
    root = Path(root).resolve()
    path = (root / value).resolve()
    if not path.is_relative_to(root) or not path.is_file():
        raise ValueError('Media must be an existing file within dataset root')
    return path


def source_split(source):
    bucket = int(hashlib.sha256(source.encode()).hexdigest()[:8], 16) % 100
    return 'train' if bucket < 80 else 'validation' if bucket < 90 else 'test'


def validate(path, root=None):
    path = Path(path)
    root = Path(root or path.parent)
    rows, ids, hashes, sources, duplicates = [], set(), {}, {}, []
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip():
            continue
        row = Annotation.model_validate_json(line)
        if not row.permission_to_train:
            raise ValueError(f'Line {number}: no training permission')
        if row.id in ids:
            raise ValueError('Duplicate annotation id')
        ids.add(row.id)
        if sources.setdefault(row.source_id, row.split) != row.split:
            raise ValueError('Source leakage across splits')
        if row.kind == 'text':
            if row.media is not None:
                raise ValueError('Text records must not reference media')
            actual = hashlib.sha256(row.text.encode()).hexdigest()
        else:
            if not row.media:
                raise ValueError('Missing media')
            actual = digest(media_path(root, row.media))
        if actual != row.sha256:
            raise ValueError(f'Line {number}: SHA256 mismatch')
        if actual in hashes:
            if hashes[actual] != row.split:
                raise ValueError('Duplicate media leakage across splits')
            duplicates.append(row.id)
        hashes[actual] = row.split
        rows.append(row)
    if not rows:
        raise ValueError('Empty dataset')
    return rows, {'records': len(rows), 'sources': len(sources), 'duplicates': duplicates,
                  'splits': {s: sum(r.split == s for r in rows) for s in ('train', 'validation', 'test')},
                  'fixtures': sum(r.fixture for r in rows), 'manifest_sha256': digest(path),
                  'real_accuracy_evidence': False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('manifest')
    parser.add_argument('--root')
    parser.add_argument('--report')
    args = parser.parse_args()
    _, report = validate(args.manifest, args.root)
    value = json.dumps(report, indent=2)
    if args.report:
        Path(args.report).write_text(value, encoding='utf-8')
    print(value)


if __name__ == '__main__':
    main()
