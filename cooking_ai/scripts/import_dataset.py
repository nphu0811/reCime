"""Import author annotations; never download or grant rights to third-party media."""
import argparse
import json
from pathlib import Path
from cooking_ai.data.manifest import Annotation, digest, media_path, source_split, validate


def main():
    p = argparse.ArgumentParser()
    p.add_argument('annotations', help='JSONL, same fields as Annotation except sha256/split')
    p.add_argument('--root', required=True)
    p.add_argument('--output', required=True)
    a = p.parse_args()
    rows = []
    for line in Path(a.annotations).read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        value = json.loads(line)
        value['split'] = source_split(value['source_id'])
        value['sha256'] = (digest(media_path(a.root, value['media'])) if value['kind'] != 'text'
                           else __import__('hashlib').sha256(value['text'].encode()).hexdigest())
        rows.append(Annotation.model_validate(value).model_dump_json())
    output = Path(a.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text('\n'.join(rows) + '\n', encoding='utf-8')
    _, report = validate(output, a.root)
    output.with_suffix('.quality.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
