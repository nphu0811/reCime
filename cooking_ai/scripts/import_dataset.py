"""Import author annotations; never download or grant rights to third-party media."""
import argparse
import json
from pathlib import Path
from cooking_ai.data.manifest import Annotation, digest, media_path, source_split, validate


def import_annotations(annotations, root, output, *, preserve_splits=False):
    """Import annotations; reviewers may retain already validated source partitions."""
    rows = []
    for line in Path(annotations).read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        value = json.loads(line)
        if not preserve_splits:
            value['split'] = source_split(value['source_id'])
        value['sha256'] = (digest(media_path(root, value['media'])) if value['kind'] != 'text'
                           else __import__('hashlib').sha256(value['text'].encode()).hexdigest())
        rows.append(Annotation.model_validate(value).model_dump_json())
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text('\n'.join(rows) + '\n', encoding='utf-8')
    _, report = validate(output, root)
    output.with_suffix('.quality.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    return report


def main():
    p = argparse.ArgumentParser()
    p.add_argument('annotations', help='JSONL, same fields as Annotation except sha256/split')
    p.add_argument('--root', required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--preserve-splits', action='store_true', help='Keep supplied source partitions; validator still checks leakage')
    a = p.parse_args()
    report = import_annotations(a.annotations, a.root, a.output, preserve_splits=a.preserve_splits)
    print(json.dumps(report))


if __name__ == '__main__':
    main()
