"""Offline export/resume plumbing fixtures, never real annotation evidence."""
from contextlib import redirect_stdout
from io import StringIO
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from cooking_ai.data.manifest import digest, validate
from cooking_ai.reviewer.auto_policy import SOURCE_CHECKS
from cooking_ai.reviewer.automatic import export_results, run
from cooking_ai.scripts.smoke_fixtures import create


def source_evidence():
    return {'checks': dict.fromkeys(SOURCE_CHECKS, True), 'rights_uncertain': False,
            'online': {'wikibooks': {'ok': True}, 'commons': {'ok': True}}}


def visual_evidence(title='Water'):
    return {'model_reviewed': True, 'model_id': 'synthetic-test-only', 'model_revision': 'fixture',
            'independent_description': 'A cooked dish of ' + title,
            'expected_label_match': 'yes', 'independent_label_match': True,
            'is_food': True, 'image_category': 'finished_dish',
            'observed_ingredients': [{'name': 'UNVERIFIED', 'quantity': 999, 'unit': 'g'}]}


def record(row, status):
    return {'id': row.id, 'source_id': row.source_id, 'sha256': row.sha256,
            'machine_review_status': status, 'annotation_verified': False,
            'source': source_evidence(), 'vision': visual_evidence(row.target.title)}


class FakeAuditor:
    def __init__(self):
        self.calls = []

    def audit(self, row, catalog_record, dataset_root, *, catalog_root):
        self.calls.append((row.id, catalog_record, Path(dataset_root), Path(catalog_root)))
        return source_evidence()


class FakeVision:
    def __init__(self):
        self.calls, self.closed = [], False

    def review(self, image, expected_title):
        self.calls.append((Path(image), expected_title))
        return visual_evidence(expected_title)

    def close(self):
        self.closed = True


class AutomaticRunTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = create(self.root / 'source')
        self.rows, self.quality = validate(self.manifest)
        self.source_snapshot = {p.name: p.read_bytes() for p in self.manifest.parent.iterdir()}
        self.catalog = self.root / 'catalog' / 'recipes.json'
        self.catalog.parent.mkdir()
        self.catalog.write_text(json.dumps({'recipes': [
            {'catalogKey': row.id, 'title': row.target.title} for row in self.rows]}), encoding='utf-8')
        self.args = SimpleNamespace(manifest=str(self.manifest), catalog=str(self.catalog),
                                    output=str(self.root / 'automatic'), offline=True, limit=0)

    def assert_source_unchanged(self):
        self.assertEqual({p.name: p.read_bytes() for p in self.manifest.parent.iterdir()},
                         self.source_snapshot)

    def export(self, records, destination='export'):
        return export_results(self.rows, records, self.manifest.parent, self.root / destination,
                              dataset_sha=digest(self.manifest), catalog_sha=digest(self.catalog))

    def test_export_preserves_provenance_but_removes_unverified_recipe_facts(self):
        original = self.rows[0]
        original.annotation_verified = True  # Even preexisting human flags cannot transfer automatically.
        summary = self.export({original.id: record(original, 'AUTO_ACCEPTED')})
        accepted, report = validate(summary['accepted_manifest'])
        row = accepted[0]
        for field in ('id', 'source_id', 'sha256', 'license', 'attribution', 'split',
                      'permission_to_train', 'permission_evidence', 'language', 'fixture'):
            self.assertEqual(getattr(row, field), getattr(original, field), field)
        self.assertFalse(row.annotation_verified)
        self.assertFalse(summary['annotation_verified'])
        self.assertFalse(report['real_accuracy_evidence'])
        self.assertEqual(row.target.title, original.target.title)
        self.assertEqual(row.target.ingredients, [])
        self.assertEqual(row.target.steps, [])
        self.assertIsNone(row.target.suggested_recipe)
        self.assertGreater(len(row.target.uncertainty), len(original.target.uncertainty))
        self.assertEqual(digest(Path(summary['output_dir']) / row.media), original.sha256)
        self.assertEqual(summary['validation']['status'], 'PASS')
        self.assertEqual(summary['validation']['passed'], 1)
        self.assertFalse(summary['training_eligible'])  # Partial run does not imply complete review.
        self.assert_source_unchanged()

    def test_rejected_uncertain_and_audit_sidecars_and_counts(self):
        statuses = ('AUTO_ACCEPTED', 'AUTO_REJECTED', 'UNCERTAIN')
        records = {row.id: record(row, status) for row, status in zip(self.rows, statuses)}
        records[self.rows[-1].id]['source']['rights_uncertain'] = True
        summary = self.export(records)
        directory = Path(summary['output_dir'])
        for filename, expected_ids in (
            ('machine_review_report.jsonl', {r.id for r in self.rows}),
            ('rejected.jsonl', {self.rows[1].id}),
            ('uncertain.jsonl', {self.rows[2].id}),
            ('provenance_license_audit.jsonl', {r.id for r in self.rows}),
        ):
            values = [json.loads(line) for line in (directory / filename).read_text(encoding='utf-8').splitlines()]
            self.assertEqual({value['id'] for value in values}, expected_ids, filename)
        self.assertEqual(summary['counts'], dict.fromkeys(statuses, 1))
        self.assertEqual(summary['processed'], 3)
        self.assertEqual(summary['total'], 3)
        self.assertTrue(summary['complete'])
        self.assertEqual(summary['vision_checked'], 3)
        self.assertEqual(summary['online_wikibooks'], 3)
        self.assertEqual(summary['online_commons'], 3)
        self.assertEqual(summary['rights_uncertain'], 1)
        self.assertFalse(summary['real_accuracy_evidence'])
        self.assert_source_unchanged()

    def test_zero_accepted_is_explicitly_empty_without_fake_validator_pass(self):
        records = {row.id: record(row, 'UNCERTAIN') for row in self.rows}
        with patch('cooking_ai.reviewer.automatic.validate', side_effect=AssertionError('Empty validation forbidden')):
            summary = self.export(records)
        self.assertEqual(Path(summary['accepted_manifest']).read_bytes(), b'')
        self.assertEqual(summary['validation'], {'status': 'NO_ELIGIBLE_RECORDS', 'passed': 0,
                                               'failed': 0, 'real_accuracy_evidence': False})
        self.assertFalse(summary['training_eligible'])
        self.assertEqual(summary['counts']['AUTO_ACCEPTED'], 0)
        self.assert_source_unchanged()

    def test_changed_source_media_is_not_exported(self):
        row = self.rows[0]
        image = self.manifest.parent / row.media
        image.write_bytes(image.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'Source media changed'):
            self.export({row.id: record(row, 'AUTO_ACCEPTED')})

    def test_existing_export_destination_never_overwritten(self):
        records = {row.id: record(row, 'AUTO_ACCEPTED') for row in self.rows}
        self.export(records)
        first = (self.root / 'export' / 'summary.json').read_bytes()
        with self.assertRaises(FileExistsError):
            self.export(records)
        self.assertEqual((self.root / 'export' / 'summary.json').read_bytes(), first)
        self.assert_source_unchanged()

    def test_run_resumes_checkpoints_without_repeating_source_or_vision_calls(self):
        auditor, vision = FakeAuditor(), FakeVision()
        self.args.limit = 1
        with redirect_stdout(StringIO()):
            pilot = run(self.args, auditor=auditor, vision=vision)
        self.assertEqual(len(auditor.calls), 1)
        self.assertEqual(len(vision.calls), 1)
        self.assertTrue(vision.closed)
        self.assertFalse(pilot['complete'])
        self.assertEqual(auditor.calls[0][1]['catalogKey'], self.rows[0].id)
        self.assertEqual(auditor.calls[0][2], self.manifest.parent)
        self.assertEqual(auditor.calls[0][3], self.catalog.parent)

        auditor, vision = FakeAuditor(), FakeVision()
        self.args.limit = 0
        with redirect_stdout(StringIO()):
            complete = run(self.args, auditor=auditor, vision=vision)
        self.assertEqual([call[0] for call in auditor.calls], [r.id for r in self.rows[1:]])
        self.assertEqual(len(vision.calls), 2)
        self.assertEqual(complete['counts']['AUTO_ACCEPTED'], 3)
        self.assertTrue(complete['complete'])
        self.assertTrue(vision.closed)
        self.assertNotEqual(pilot['output_dir'], complete['output_dir'])
        self.assertTrue(Path(pilot['output_dir']).is_dir())

        auditor, vision = FakeAuditor(), FakeVision()
        with redirect_stdout(StringIO()):
            resumed = run(self.args, auditor=auditor, vision=vision)
        self.assertEqual(auditor.calls, [])
        self.assertEqual(vision.calls, [])
        self.assertEqual(resumed['counts'], complete['counts'])
        self.assert_source_unchanged()

    def test_run_rejects_checkpoint_identity_mismatch(self):
        self.args.limit = 1
        with redirect_stdout(StringIO()):
            run(self.args, auditor=FakeAuditor(), vision=FakeVision())
        checkpoint = next((self.root / 'automatic').glob('*/records/*.json'))
        contents = json.loads(checkpoint.read_text(encoding='utf-8'))
        contents['id'] = 'different-id'
        checkpoint.write_text(json.dumps(contents), encoding='utf-8')
        with redirect_stdout(StringIO()), self.assertRaisesRegex(ValueError, 'Checkpoint identity mismatch'):
            run(self.args, auditor=FakeAuditor(), vision=FakeVision())
        self.assert_source_unchanged()

    def test_run_refuses_output_inside_source_dataset(self):
        self.args.output = str(self.manifest.parent / 'automatic')
        with self.assertRaisesRegex(ValueError, 'separate from source dataset'):
            run(self.args, auditor=FakeAuditor(), vision=FakeVision())
        self.assert_source_unchanged()


if __name__ == '__main__':
    unittest.main()
