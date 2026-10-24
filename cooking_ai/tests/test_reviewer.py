"""Local reviewer integration; generated cards test plumbing, never model accuracy."""
import json
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from cooking_ai.data.manifest import Annotation, validate
from cooking_ai.reviewer.server import create_app
from cooking_ai.scripts.smoke_fixtures import create


class ReviewerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.manifest = create(self.root / 'source')
        self.rows = [json.loads(line) for line in self.manifest.read_text(encoding='utf-8').splitlines()]
        # Explicitly remove fixture marker to exercise reviewer eligibility only.
        # These self-authored cards remain technical fixtures, not food evidence.
        for row in self.rows:
            row['fixture'] = False
        self.manifest.write_text('\n'.join(json.dumps(row) for row in self.rows) + '\n', encoding='utf-8')
        self.original = self.manifest.read_bytes()
        self.state_dir, self.output_dir = self.root / 'state', self.root / 'reviewed'
        self.open_client()

    def open_client(self):
        self.app = create_app(self.manifest, self.state_dir, self.output_dir)
        self.client = TestClient(self.app, base_url='http://127.0.0.1:8770')
        self.client.__enter__()
        self.token = self.client.get('/api/session').json()['csrf']

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temporary.cleanup()

    def item(self, index=0):
        response = self.client.get(f'/api/items/{index}')
        self.assertEqual(response.status_code, 200, response.text[:300])
        return response.json()

    def save(self, index=0, **changes):
        draft = self.item(index)['draft']
        draft.update(changes)
        response = self.client.put(f'/api/items/{index}', json=draft,
                                   headers={'X-Reviewer-CSRF': self.token})
        self.assertEqual(response.status_code, 200, response.text[:300])
        return self.item(index)

    def confirm(self, index=0):
        self.save(index, status='correct', rights_confirmed=True, label_confirmed=False,
                  ingredients=[{'name': 'nước', 'evidence': 'Nhìn thấy nước trong cốc'}],
                  uncertainty=['Không biết định lượng từ ảnh'])
        return self.save(index, label_confirmed=True)

    def action(self, route):
        response = self.client.post(route, headers={'X-Reviewer-CSRF': self.token})
        self.assertEqual(response.status_code, 200, response.text[:300])
        return response.json()

    def test_initial_session_media_and_original_untouched(self):
        session = self.client.get('/api/session').json()
        self.assertEqual(session['total'], 3)
        self.assertEqual(session['progress']['verified'], 0)
        self.assertEqual(session['progress']['reviewed'], 0)
        item = self.item()
        self.assertFalse(item['verified'])
        self.assertFalse(item['draft']['label_confirmed'])
        self.assertFalse(item['draft']['rights_confirmed'])
        self.assertEqual(item['source_id'], self.rows[0]['source_id'])
        self.assertEqual(item['split'], 'train')
        self.assertEqual(item['attribution'], self.rows[0]['attribution'])
        self.assertEqual(item['license'], self.rows[0]['license'])
        self.assertEqual(item['permission_evidence'], self.rows[0]['permission_evidence'])
        response = self.client.get('/media/0')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.content, (self.manifest.parent / self.rows[0]['media']).read_bytes())
        self.assertEqual(self.manifest.read_bytes(), self.original)
        self.assertFalse(self.output_dir.exists() and any(self.output_dir.rglob('*')))

    def test_confirmation_is_explicit_and_rights_required(self):
        item = self.save(status='correct', rights_confirmed=True, label_confirmed=True)
        self.assertFalse(item['verified'])
        self.assertFalse(item['draft']['label_confirmed'])
        item = self.save(label_confirmed=True)
        self.assertTrue(item['verified'])
        item = self.save(rights_confirmed=False)
        self.assertFalse(item['verified'])

    def test_edit_revokes_label_even_when_payload_requests_confirmation(self):
        self.assertTrue(self.confirm()['verified'])
        changes = [
            {'title': 'Tên món đã sửa'},
            {'ingredients': [{'name': 'gạo', 'evidence': 'Hạt gạo nhìn thấy'}]},
            {'uncertainty': ['Không xác định thành phần bên trong']},
        ]
        for change in changes:
            item = self.save(**change, label_confirmed=True)
            self.assertFalse(item['verified'])
            self.assertFalse(item['draft']['label_confirmed'])
            self.assertTrue(self.save(label_confirmed=True)['verified'])

    def test_wrong_unsure_and_skipped_never_verified(self):
        self.confirm()
        for status in ('wrong', 'unsure', 'skipped'):
            item = self.save(status=status, rights_confirmed=True, label_confirmed=True)
            self.assertFalse(item['verified'])
            self.assertFalse(self.save(label_confirmed=True)['verified'])
            report = self.action('/api/export')
            self.assertEqual(report['exported'], 0)
        self.assertEqual(self.client.get('/api/session').json()['progress']['reviewed'], 0)

    def test_drafts_survive_reopen_and_stale_writes_conflict(self):
        stale = self.item()['draft']
        saved = self.save(title='Phở bò', status='unsure')
        self.assertGreater(saved['draft']['revision'], stale['revision'])
        response = self.client.put('/api/items/0', json=stale,
                                   headers={'X-Reviewer-CSRF': self.token})
        self.assertEqual(response.status_code, 409)
        self.assertEqual(self.item()['draft']['title'], 'Phở bò')
        self.client.__exit__(None, None, None)
        self.open_client()
        self.assertEqual(self.item()['draft']['title'], 'Phở bò')
        self.assertEqual(self.item()['draft']['status'], 'unsure')
        self.assertEqual(self.item()['draft']['revision'], saved['draft']['revision'])

    def test_resume_restores_last_viewed_image(self):
        self.item(2)
        self.client.__exit__(None, None, None)
        self.open_client()
        self.assertEqual(self.client.get('/api/session').json()['last_index'], 2)

    def test_state_bound_to_original_manifest(self):
        self.manifest.write_bytes(self.original + b'\n')
        with self.assertRaises(ValueError):
            create_app(self.manifest, self.state_dir, self.output_dir)

    def test_export_rejects_changed_original_manifest(self):
        self.confirm()
        self.manifest.write_bytes(self.original + b'\n')
        response = self.client.post('/api/export', headers={'X-Reviewer-CSRF': self.token})
        self.assertEqual(response.status_code, 422)
        self.assertFalse(self.output_dir.exists() and any(self.output_dir.rglob('manifest.jsonl')))

    def test_missing_media_reports_failure_and_does_not_publish_partial_export(self):
        self.confirm(0)
        self.confirm(1)
        (self.manifest.parent / self.rows[1]['media']).unlink()
        report = self.action('/api/export')
        self.assertEqual(report['passed'], 1)
        self.assertEqual(report['failed'], 1)
        self.assertEqual(report['exported'], 0)
        self.assertEqual(report['errors'][0]['id'], self.rows[1]['id'])
        self.assertFalse(any(self.output_dir.rglob('latest.json')))

    def test_empty_export_does_not_produce_training_manifest(self):
        for route in ('/api/export', '/api/validate'):
            report = self.action(route)
            self.assertEqual(report['exported'], 0)
            self.assertEqual(report['verified'], 0)
            self.assertFalse(report.get('manifest_path'))
        self.assertEqual(self.manifest.read_bytes(), self.original)

    def test_export_roundtrip_preserves_provenance_split_hash_and_no_hidden_facts(self):
        self.assertTrue(self.confirm()['verified'])
        self.save(1, status='wrong')
        self.save(2, status='unsure')
        report = self.action('/api/export')
        self.assertEqual(report['exported'], 1)
        self.assertEqual(report['passed'], 1)
        self.assertEqual(report['failed'], 0)
        self.assertEqual(report['excluded'], 2)
        self.assertEqual(report['reviewed'], 3)
        self.assertTrue(report['all_reviewed'])
        output_manifest = Path(report['manifest_path'])
        self.assertTrue(output_manifest.resolve().is_relative_to(self.output_dir.resolve()))
        self.assertNotEqual(output_manifest, self.manifest)
        rows, validation = validate(output_manifest)
        self.assertEqual(validation['records'], 1)
        row = Annotation.model_validate(rows[0])
        for field in ('source_id', 'split', 'sha256', 'attribution', 'license'):
            self.assertEqual(getattr(row, field), self.rows[0][field])
        self.assertTrue(row.annotation_verified)
        self.assertTrue(row.permission_to_train)
        ingredient = row.target.ingredients[0]
        self.assertIsNone(ingredient.quantity)
        self.assertIsNone(ingredient.unit)
        self.assertEqual(ingredient.evidence.source, 'visual')
        self.assertEqual(row.target.steps, [])
        annotations = Path(report['annotations_path'])
        self.assertTrue(annotations.is_file())
        self.assertEqual(self.manifest.read_bytes(), self.original)
        validated = self.action('/api/validate')
        self.assertEqual(validated['passed'], 1)
        self.assertEqual(validated['failed'], 0)
        # Exports are versioned: rerunning never overwrites a previous manifest.
        second = self.action('/api/export')
        self.assertNotEqual(second['manifest_path'], report['manifest_path'])
        self.assertTrue(output_manifest.is_file())

    def test_security_guards_no_remote_host_origin_or_missing_csrf(self):
        draft = self.item()['draft']
        self.assertEqual(self.client.put('/api/items/0', json=draft).status_code, 403)
        self.assertEqual(self.client.post('/api/export').status_code, 403)
        response = self.client.put('/api/items/0', json=draft,
            headers={'X-Reviewer-CSRF': self.token, 'Origin': 'https://untrusted.example'})
        self.assertEqual(response.status_code, 403)
        self.assertEqual(self.client.get('/api/session', headers={'Host': 'untrusted.example'}).status_code, 403)
        self.assertEqual(self.client.post('/api/validate', headers={'X-Reviewer-CSRF': 'wrong'}).status_code, 403)

    def test_confirmation_flags_are_real_booleans_and_quantities_cannot_be_added(self):
        draft = self.item()['draft']
        for change in ({'label_confirmed': 'true'}, {'rights_confirmed': 1},
                       {'ingredients': [{'name': 'gạo', 'evidence': 'Nhìn thấy gạo', 'quantity': 100}]}):
            response = self.client.put('/api/items/0', json={**draft, **change},
                                       headers={'X-Reviewer-CSRF': self.token})
            self.assertEqual(response.status_code, 422)
        self.assertFalse(self.item()['verified'])

    def test_importer_default_split_policy_remains_compatible(self):
        from cooking_ai.scripts.import_dataset import import_annotations
        from cooking_ai.data.manifest import source_split
        destination = self.root / 'default-import.jsonl'
        import_annotations(self.manifest, self.manifest.parent, destination)
        rows, _ = validate(destination, self.manifest.parent)
        self.assertEqual([row.split for row in rows], [source_split(row.source_id) for row in rows])

    def test_invalid_index_and_traversal_cannot_read_files(self):
        for path in ('/api/items/-1', '/api/items/999', '/media/-1', '/media/999',
                     '/media/%2e%2e%2fmanifest.jsonl', '/media/%2e%2e%2f%2e%2e%2fstate'):
            self.assertIn(self.client.get(path).status_code, (400, 403, 404, 422), path)


if __name__ == '__main__':
    unittest.main()
