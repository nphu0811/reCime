"""Prepare a source-preserving weak pilot candidate, never an expert benchmark."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import tempfile

from cooking_ai.data.manifest import digest, validate


def prepare(summary_path, source_manifest, output):
    """Combine accepted weak images with original text; do not train anything."""
    summary_path, source_manifest, output = (
        Path(value).resolve() for value in (summary_path, source_manifest, output))
    summary = json.loads(summary_path.read_text(encoding='utf-8'))
    if (summary.get('complete') is not True
            or summary.get('validation', {}).get('status') != 'PASS'
            or summary.get('counts', {}).get('AUTO_ACCEPTED', 0) <= 0):
        raise ValueError('A complete review with nonempty, validated accepted images is required')
    if summary.get('dataset_sha256') != digest(source_manifest):
        raise ValueError('Source manifest SHA256 does not match the reviewed dataset')
    source, _ = validate(source_manifest)
    image_count = sum(row.kind == 'image' for row in source)
    if (summary.get('processed') != image_count or summary.get('total') != image_count
            or sum(summary['counts'].get(status, 0) for status in
                   ('AUTO_ACCEPTED', 'AUTO_REJECTED', 'UNCERTAIN')) != image_count):
        raise ValueError('Review counts do not cover all source images')
    accepted_path = Path(summary['accepted_manifest']).resolve()
    if (output.parent != accepted_path.parent or output.suffix != '.jsonl'
            or output.is_relative_to(source_manifest.parent)
            or accepted_path.is_relative_to(source_manifest.parent)):
        raise ValueError('Output must be a new JSONL beside accepted images and outside the source dataset')
    quality_path = output.with_suffix('.quality.json')
    if output.exists() or quality_path.exists():
        raise FileExistsError('Never overwrite an existing manifest or quality report')
    accepted, _ = validate(accepted_path)
    if (len(accepted) != summary['counts']['AUTO_ACCEPTED']
            or summary['validation'].get('passed') != len(accepted)
            or summary['validation'].get('failed', 0) != 0):
        raise ValueError('Accepted manifest count does not match the final review summary')
    originals = {row.id: row for row in source}
    provenance_fields = ('id', 'source_id', 'kind', 'sha256', 'split', 'attribution',
                         'license', 'permission_to_train', 'permission_evidence', 'language', 'fixture')
    for row in accepted:
        original = originals.get(row.id)
        if (row.kind != 'image' or row.annotation_verified or row.fixture
                or original is None or any(getattr(row, field) != getattr(original, field)
                                           for field in provenance_fields)
                or row.target.title != original.target.title):
            raise ValueError('Accepted image identity/provenance/split must match the original weak source')
        if row.target.ingredients or row.target.steps or row.target.suggested_recipe is not None:
            raise ValueError('Accepted dish photos must not invent ingredient, quantity or recipe-step labels')
    # Original text evidence remains text evidence; it is never converted to visual facts.
    texts = [row for row in source if row.kind == 'text']
    rows = [row.model_copy(update={'annotation_verified': False}) for row in [*accepted, *texts]]
    content = ''.join(row.model_dump_json() + '\n' for row in rows)
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', newline='\n', suffix='.jsonl',
                                     prefix='.weak-pilot-', dir=output.parent, delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(content)
    try:
        _, report = validate(temporary, root=output.parent)
    finally:
        temporary.unlink()
    modalities = {split: dict(Counter(row.kind for row in rows if row.split == split))
                  for split in ('train', 'validation', 'test')}
    report.update({
        'manifest_sha256': hashlib.sha256(content.encode('utf-8')).hexdigest(),
        'candidate_type': 'mixed_machine_weak_images_and_source_text', 'weak_supervision': True,
        'annotation_verified': False, 'real_accuracy_evidence': False,
        'expert_evaluation_eligible': False, 'source_splits_preserved': True,
        'accepted_image_count': len(accepted), 'original_text_count': len(texts),
        'excluded_image_count': image_count - len(accepted),
        'modalities_by_split': modalities,
        'heldout_modality_counts': {split: modalities[split] for split in ('validation', 'test')},
        'benchmark_scope': 'Held-out text supports source extraction/JSON checks, not image recognition accuracy; weak images are not expert labels.',
        'source_manifest': str(source_manifest), 'source_manifest_sha256': digest(source_manifest),
        'accepted_manifest': str(accepted_path), 'accepted_manifest_sha256': digest(accepted_path),
        'automatic_summary': str(summary_path), 'automatic_summary_sha256': digest(summary_path),
        'output_manifest': str(output),
    })
    # Exclusive creation protects existing files even if another process races the precheck.
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(content)
    with quality_path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--summary', required=True)
    parser.add_argument('--source-manifest', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.summary, args.source_manifest, args.output), ensure_ascii=True))


if __name__ == '__main__':
    main()
