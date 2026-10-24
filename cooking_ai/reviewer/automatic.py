"""Resumable, local-model source audit. Machine labels never become human labels."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import time

from cooking_ai.data.manifest import Annotation, digest, media_path, validate
from cooking_ai.reviewer.store import ReviewerStore


VERSION = 'wiki-auto-review-v1'
SOURCE_MANIFEST = 'cooking_ai/data/private/wiki/manifest.jsonl'
CATALOG = 'app/src/main/assets/catalog/recipes.json'
OUTPUT = 'cooking_ai/data/private/auto_review'


def write_jsonl(path, values):
    path = Path(path)
    temporary = path.with_suffix('.jsonl.tmp')
    with temporary.open('w', encoding='utf-8') as stream:
        for value in values:
            stream.write(json.dumps(value, ensure_ascii=False) + '\n')
        stream.flush()
        __import__('os').fsync(stream.fileno())
    temporary.replace(path)


def export_results(rows, records, source_root, destination, *, dataset_sha, catalog_sha):
    """Export only policy-accepted weak labels; keep every exclusion and its evidence."""
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=False)
    (destination / 'media').mkdir()
    accepted, rejected, uncertain, audit = [], [], [], []
    for row in rows:
        record = records.get(row.id)
        if not record:
            continue
        status = record['machine_review_status']
        audit.append({'id': row.id, 'source_id': row.source_id, 'sha256': row.sha256,
                      'status': status, 'rights_uncertain': record['source']['rights_uncertain'],
                      'source_audit': record['source']})
        if status != 'AUTO_ACCEPTED':
            (rejected if status == 'AUTO_REJECTED' else uncertain).append(record)
            continue
        image = media_path(source_root, row.media)
        if digest(image) != row.sha256:
            raise ValueError('Source media changed during review: ' + row.id)
        value = row.model_dump()
        value['annotation_verified'] = False
        value['media'] = 'media/' + row.sha256 + image.suffix.lower()
        # Vision responses remain evidence in the report, never unverified ingredient facts.
        value['target'].update(ingredients=[], steps=[], suggested_recipe=None,
            uncertainty=list(dict.fromkeys(row.target.uncertainty + [
                'Nhãn yếu được máy đối chiếu, không phải xác minh của con người.',
                'Nguyên liệu ẩn, định lượng, thời gian, nhiệt độ và bước nấu chưa xác định.'])))
        annotation = Annotation.model_validate(value)
        target = destination / annotation.media
        if not target.exists():
            shutil.copyfile(image, target)
        if digest(target) != row.sha256:
            raise ValueError('Export media hash mismatch')
        accepted.append(annotation.model_dump())
    write_jsonl(destination / 'machine_review_report.jsonl', list(records.values()))
    write_jsonl(destination / 'rejected.jsonl', rejected)
    write_jsonl(destination / 'uncertain.jsonl', uncertain)
    write_jsonl(destination / 'provenance_license_audit.jsonl', audit)
    validation = {'status': 'NO_ELIGIBLE_RECORDS', 'passed': 0, 'failed': 0,
                  'real_accuracy_evidence': False}
    # Always provide an explicitly empty accepted output when no sample qualifies.
    write_jsonl(destination / 'accepted.jsonl', accepted)
    if accepted:
        _, quality = validate(destination / 'accepted.jsonl')
        validation.update(status='PASS', passed=quality['records'], report=quality)
    counts = Counter(record['machine_review_status'] for record in records.values())
    summary = {'review_version': VERSION, 'review_method': 'automatic',
        'annotation_verified': False, 'real_accuracy_evidence': False,
        'dataset_sha256': dataset_sha, 'catalog_sha256': catalog_sha,
        'processed': len(records), 'total': len(rows), 'complete': len(records) == len(rows),
        'counts': {k: counts[k] for k in ('AUTO_ACCEPTED', 'AUTO_REJECTED', 'UNCERTAIN')},
        'vision_checked': sum(bool(r['vision'].get('model_reviewed')) for r in records.values()),
        'online_wikibooks': sum(bool(r['source'].get('online', {}).get('wikibooks', {}).get('ok')) for r in records.values()),
        'online_commons': sum(bool(r['source'].get('online', {}).get('commons', {}).get('ok')) for r in records.values()),
        'rights_uncertain': sum(bool(r['source']['rights_uncertain']) for r in records.values()),
        'validation': validation, 'output_dir': str(destination.resolve()),
        'accepted_manifest': str((destination / 'accepted.jsonl').resolve()),
        'training_eligible': bool(accepted) and len(records) == len(rows),
        'label_quality': 'machine-reviewed weak supervision; never expert benchmark labels',
        'timestamp': datetime.now(timezone.utc).isoformat()}
    ReviewerStore.write_json(destination / 'summary.json', summary)
    return summary


def run(args, *, auditor=None, vision=None):
    from cooking_ai.reviewer.auto_policy import classify
    from cooking_ai.reviewer.wiki_audit import WikiAuditor
    from cooking_ai.reviewer.vision_review import LocalVisionReviewer
    manifest, catalog = Path(args.manifest).resolve(), Path(args.catalog).resolve()
    rows, initial = validate(manifest)
    rows = [row for row in rows if row.kind == 'image']
    if not rows:
        raise ValueError('No image records')
    metadata = {r['catalogKey']: r for r in json.loads(catalog.read_text(encoding='utf-8'))['recipes']}
    catalog_sha = digest(catalog)
    # Different source/catalog or implementation -> separate checkpoint namespace.
    code_sha = hashlib.sha256(b''.join(Path(__file__).with_name(f).read_bytes() for f in
        ('automatic.py', 'wiki_audit.py', 'vision_review.py', 'auto_policy.py'))).hexdigest()
    key = initial['manifest_sha256'][:12] + '-' + catalog_sha[:8] + '-' + code_sha[:8] + ('-offline' if args.offline else '')
    home = Path(args.output).resolve() / key
    if home.is_relative_to(manifest.parent):
        raise ValueError('Automatic review output must be separate from source dataset')
    (home / 'records').mkdir(parents=True, exist_ok=True)
    auditor = auditor or WikiAuditor(Path(args.output).resolve() / 'api_cache', online=not args.offline)
    records, seen = {}, set()
    todo = rows[:args.limit] if args.limit else rows
    started = time.monotonic()
    try:
        for row in todo:
            path = home / 'records' / (hashlib.sha256(row.id.encode()).hexdigest() + '.json')
            if path.exists():
                record = json.loads(path.read_text(encoding='utf-8'))
                if record['id'] != row.id or record['sha256'] != row.sha256:
                    raise ValueError('Checkpoint identity mismatch')
            else:
                source = auditor.audit(row, metadata.get(row.id.removesuffix(':image'), {}), manifest.parent,
                                       catalog_root=catalog.parent)
                if vision is None:
                    vision = LocalVisionReviewer()
                try:
                    evidence = vision.review(media_path(manifest.parent, row.media), row.target.title or '')
                except (OSError, ValueError, RuntimeError) as error:
                    evidence = {'model_reviewed': False, 'error': str(error)[:800]}
                decision = classify(source, evidence, duplicate=row.sha256 in seen)
                record = {'id': row.id, 'source_id': row.source_id, 'split': row.split,
                    'sha256': row.sha256, 'title': row.target.title, 'machine_reviewed': True,
                    'review_method': 'automatic', 'machine_review_status': decision['status'],
                    'review_model_version': evidence.get('model_id', '') + '@' + evidence.get('model_revision', ''),
                    'annotation_verified': False, 'real_accuracy_evidence': False,
                    'review_timestamp': datetime.now(timezone.utc).isoformat(),
                    'source': source, 'vision': evidence, 'decision': decision}
                ReviewerStore.write_json(path, record)
            records[row.id] = record
            seen.add(row.sha256)
            if len(records) % 5 == 0 or len(records) == len(todo):
                print(json.dumps({'processed': len(records), 'total': len(rows),
                    'counts': dict(Counter(r['machine_review_status'] for r in records.values())),
                    'elapsed_s': round(time.monotonic() - started, 1)}), flush=True)
        if digest(manifest) != initial['manifest_sha256'] or digest(catalog) != catalog_sha:
            raise ValueError('Source changed during review; keep checkpoints but do not publish')
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        summary = export_results(rows, records, manifest.parent, home / 'reports' / stamp,
                                 dataset_sha=initial['manifest_sha256'], catalog_sha=catalog_sha)
        ReviewerStore.write_json(home / 'latest.json', summary)
        print(json.dumps(summary, ensure_ascii=True), flush=True)
        return summary
    finally:
        if vision is not None and hasattr(vision, 'close'):
            vision.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', default=SOURCE_MANIFEST)
    parser.add_argument('--catalog', default=CATALOG)
    parser.add_argument('--output', default=OUTPUT)
    parser.add_argument('--limit', type=int, default=0, help='Pilot first N images, 0=all; rerun resumes')
    parser.add_argument('--offline', action='store_true', help='Use cached/source metadata, never imply online verification')
    args = parser.parse_args()
    if args.limit < 0:
        parser.error('--limit must be nonnegative')
    run(args)


if __name__ == '__main__':
    main()
