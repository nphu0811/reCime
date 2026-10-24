"""Preparation fixtures test contracts, not real-world model/annotation quality."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from cooking_ai.data.manifest import digest, validate
from cooking_ai.reviewer.automatic import export_results
from cooking_ai.scripts.auto_training_manifest import prepare
from cooking_ai.scripts.smoke_fixtures import create


class AutoTrainingManifestTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = create(self.root / 'wiki')
        originals, _ = validate(self.source)
        values = []
        for row in originals:
            image = row.model_dump()
            image.update(fixture=False, annotation_verified=False)
            values.append(image)
            text = deepcopy(image)
            text.update(id=row.id + ':text', kind='text', media=None,
                        text='Original source recipe for ' + row.target.title,
                        annotation_verified=True)
            text['sha256'] = hashlib.sha256(text['text'].encode()).hexdigest()
            for ingredient in text['target']['ingredients']:
                ingredient['evidence']['source'] = 'user_text'
            for step in text['target']['steps']:
                step['evidence']['source'] = 'user_text'
            values.append(text)
        self.source.write_text(''.join(json.dumps(row) + '\n' for row in values), encoding='utf-8')
        rows, _ = validate(self.source)
        images = [row for row in rows if row.kind == 'image']
        statuses = ('AUTO_ACCEPTED', 'AUTO_REJECTED', 'UNCERTAIN')
        records = {row.id: {'id': row.id, 'sha256': row.sha256, 'source_id': row.source_id,
                           'machine_review_status': status,
                           'source': {'rights_uncertain': False}, 'vision': {'model_reviewed': True}}
                   for row, status in zip(images, statuses)}
        self.summary = export_results(images, records, self.source.parent, self.root / 'reviewed',
                                      dataset_sha=digest(self.source), catalog_sha='f' * 64)
        self.summary_path = self.root / 'reviewed' / 'summary.json'
        self.output = self.summary_path.with_name('mixed-weak-candidate.jsonl')
        self.original_source_bytes = self.source.read_bytes()
        self.original_accepted_bytes = Path(self.summary['accepted_manifest']).read_bytes()

    def save_summary(self):
        self.summary_path.write_text(json.dumps(self.summary), encoding='utf-8')

    def test_prepare_only_accepted_images_plus_original_text_and_preserve_source_splits(self):
        report = prepare(self.summary_path, self.source, self.output)
        rows, quality = validate(self.output)
        originals, _ = validate(self.source)
        original_by_id = {row.id: row for row in originals}
        self.assertEqual({row.id for row in rows if row.kind == 'image'}, {'fixture-0'})
        self.assertEqual({row.id for row in rows if row.kind == 'text'},
                         {row.id for row in originals if row.kind == 'text'})
        self.assertTrue(all(not row.annotation_verified for row in rows))
        for row in rows:
            original = original_by_id[row.id]
            for field in ('source_id', 'split', 'sha256', 'license', 'attribution', 'permission_evidence'):
                self.assertEqual(getattr(row, field), getattr(original, field), (row.id, field))
            if row.kind == 'text':
                self.assertEqual(row.target, original.target)
                self.assertEqual(row.text, original.text)
                self.assertTrue(all(item.evidence.source == 'user_text' for item in row.target.ingredients))
            else:
                self.assertEqual(row.target.ingredients, [])
                self.assertEqual(row.target.steps, [])
        self.assertEqual(report['accepted_image_count'], 1)
        self.assertEqual(report['original_text_count'], 3)
        self.assertEqual(report['excluded_image_count'], 2)
        self.assertEqual(report['modalities_by_split'],
                         {'train': {'image': 1, 'text': 1}, 'validation': {'text': 1}, 'test': {'text': 1}})
        self.assertEqual(report['heldout_modality_counts'], {'validation': {'text': 1}, 'test': {'text': 1}})
        self.assertTrue(report['weak_supervision'])
        self.assertFalse(report['expert_evaluation_eligible'])
        self.assertFalse(report['real_accuracy_evidence'])
        self.assertEqual(report['manifest_sha256'], quality['manifest_sha256'])
        self.assertEqual(json.loads(self.output.with_suffix('.quality.json').read_text(encoding='utf-8')), report)
        self.assertEqual(self.source.read_bytes(), self.original_source_bytes)
        self.assertEqual(Path(self.summary['accepted_manifest']).read_bytes(), self.original_accepted_bytes)

    def test_incomplete_empty_and_invalid_review_are_refused(self):
        for change in ({'complete': False}, {'counts': {'AUTO_ACCEPTED': 0}},
                       {'validation': {'status': 'NO_ELIGIBLE_RECORDS'}}, {'processed': 2}):
            with self.subTest(change=change):
                original = self.summary.copy()
                self.summary.update(change)
                self.save_summary()
                with self.assertRaises(ValueError):
                    prepare(self.summary_path, self.source, self.output)
                self.assertFalse(self.output.exists())
                self.summary = original

    def test_changed_source_sha_is_refused(self):
        self.summary['dataset_sha256'] = '0' * 64
        self.save_summary()
        with self.assertRaisesRegex(ValueError, 'SHA256'):
            prepare(self.summary_path, self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_output_must_be_beside_accepted_and_outside_original_source(self):
        for output in (self.root / 'elsewhere.jsonl', self.source.parent / 'new.jsonl'):
            with self.subTest(output=output), self.assertRaises(ValueError):
                prepare(self.summary_path, self.source, output)
            self.assertFalse(output.exists())
        self.assertEqual(self.source.read_bytes(), self.original_source_bytes)

    def test_never_overwrites_manifest_or_quality_sidecar(self):
        self.output.write_text('KEEP', encoding='utf-8')
        with self.assertRaises(FileExistsError):
            prepare(self.summary_path, self.source, self.output)
        self.assertEqual(self.output.read_text(encoding='utf-8'), 'KEEP')
        other = self.output.with_name('other.jsonl')
        other.with_suffix('.quality.json').write_text('KEEP QUALITY', encoding='utf-8')
        with self.assertRaises(FileExistsError):
            prepare(self.summary_path, self.source, other)
        self.assertFalse(other.exists())
        self.assertEqual(other.with_suffix('.quality.json').read_text(encoding='utf-8'), 'KEEP QUALITY')

    def test_accepted_identity_rights_splits_and_human_flags_cannot_drift(self):
        path = Path(self.summary['accepted_manifest'])
        original = json.loads(self.original_accepted_bytes)
        for field, value in (('source_id', 'different-source'), ('split', 'test'),
                             ('license', 'Different'), ('annotation_verified', True), ('fixture', True)):
            with self.subTest(field=field):
                changed = {**original, field: value}
                path.write_text(json.dumps(changed) + '\n', encoding='utf-8')
                with self.assertRaises(ValueError):
                    prepare(self.summary_path, self.source, self.output)
                self.assertFalse(self.output.exists())
        path.write_bytes(self.original_accepted_bytes)

    def test_accepted_recipe_facts_cannot_come_from_original_article(self):
        path = Path(self.summary['accepted_manifest'])
        changed = json.loads(self.original_accepted_bytes)
        original, _ = validate(self.source)
        changed['target']['ingredients'] = original[0].target.model_dump()['ingredients']
        path.write_text(json.dumps(changed) + '\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'must not invent'):
            prepare(self.summary_path, self.source, self.output)
        self.assertFalse(self.output.exists())


if __name__ == '__main__':
    unittest.main()
