"""Bounded deterministic real-data pilot; explicitly records context-filtered IDs."""
import argparse
import itertools
import json
from pathlib import Path
from transformers import AutoProcessor
from cooking_ai.data.manifest import validate
from cooking_ai.training.train import batch


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--manifest', required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--train', type=int, default=24)
    p.add_argument('--validation', type=int, default=4)
    p.add_argument('--test', type=int, default=6)
    p.add_argument('--image-only', action='store_true')
    a = p.parse_args()
    rows, _ = validate(a.manifest)
    processor = AutoProcessor.from_pretrained('HuggingFaceTB/SmolVLM-256M-Instruct',
        revision='7e3e67edbbed1bf9888184d9df282b700a323964', local_files_only=True)
    processor.image_processor.do_image_splitting = False
    selected, excluded = [], []
    root = Path(a.manifest).parent
    for split, count in [('train', a.train), ('validation', a.validation), ('test', a.test)]:
        image = [r for r in rows if r.split == split and r.kind == 'image']
        text = [] if a.image_only else [r for r in rows if r.split == split and r.kind == 'text']
        ordered = [r for pair in itertools.zip_longest(image, text) for r in pair if r is not None]
        taken = 0
        for row in ordered:
            if taken >= count:
                break
            try:
                batch(processor, row, root, 'cpu', 4096)
            except ValueError:
                excluded.append(row.id)
                continue
            selected.append(row)
            taken += 1
    output = Path(a.output)
    if output.parent.resolve() != root.resolve():
        raise ValueError('Keep pilot manifest beside original to preserve media paths')
    output.write_text('\n'.join(r.model_dump_json() for r in selected) + '\n', encoding='utf-8')
    _, report = validate(output)
    report['excluded_context_ids'] = excluded
    report['pilot_only'] = True
    output.with_suffix('.quality.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
