import io
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from fastapi.testclient import TestClient
from PIL import Image
from backend.app import app
from backend import cooking
from backend.cloud import identity


class CookingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.env = patch.dict(os.environ, {'COOKING_AI_JOBS_DIR': self.temp.name, 'OPENAI_API_KEY': 'test-only'})
        self.env.start()
        self.client = TestClient(app)
        self.owner = {'id': 'a'}
        app.dependency_overrides[identity] = lambda: self.owner
        self.submit = patch.object(cooking.pool, 'submit')
        self.submit.start()
        buffer = io.BytesIO()
        Image.new('RGB', (24, 24), 'white').save(buffer, 'JPEG')
        self.image = buffer.getvalue()

    def tearDown(self):
        app.dependency_overrides.clear()
        self.submit.stop()
        self.env.stop()
        self.temp.cleanup()

    def create(self, **fields):
        data = {'kind': 'image', 'provider': 'openai_existing', 'external_consent': 'true', **fields}
        return self.client.post('/v1/cooking/jobs', data=data, files={'media': ('image.jpg', self.image, 'image/jpeg')})

    def test_consent_auth_and_no_automatic_fallback(self):
        self.assertEqual(self.create(external_consent='false').status_code, 403)
        self.assertEqual(self.create(provider='trained_local_vlm').status_code, 503)
        app.dependency_overrides.clear()
        self.assertEqual(self.client.get('/v1/cooking/models').status_code, 401)

    def test_owner_cancel_retry_and_persistence(self):
        response = self.create()
        self.assertEqual(response.status_code, 202, response.text)
        job = response.json()['id']
        self.owner = {'id': 'other'}
        self.assertEqual(self.client.get('/v1/cooking/jobs/' + job).status_code, 404)
        self.assertEqual(self.client.post('/v1/cooking/jobs/' + job + '/cancel').status_code, 404)
        self.owner = {'id': 'a'}
        cancelled = self.client.post('/v1/cooking/jobs/' + job + '/cancel')
        self.assertEqual(cancelled.json()['state'], 'cancelled')
        retry = self.client.post('/v1/cooking/jobs/' + job + '/retry', data={'external_consent': 'true'},
                                 files={'media': ('again.jpg', self.image, 'image/jpeg')})
        self.assertEqual(retry.status_code, 202, retry.text)
        cooking.initialized.discard(str(Path(self.temp.name).resolve()))
        self.assertEqual(self.client.get('/v1/cooking/jobs/' + retry.json()['id']).json()['state'], 'interrupted')

    def test_idempotency_limits_and_cleanup(self):
        def submit(key):
            return self.client.post('/v1/cooking/jobs', data={'kind': 'image', 'provider': 'openai_existing',
                'external_consent': 'true'}, headers={'Idempotency-Key': key},
                files={'media': ('image.jpg', self.image, 'image/jpeg')})
        first = submit('unique-request-1')
        second = submit('unique-request-1')
        self.assertEqual(first.json()['id'], second.json()['id'])
        different = self.client.post('/v1/cooking/jobs', data={'kind': 'image', 'provider': 'openai_existing',
            'external_consent': 'true'}, headers={'Idempotency-Key': 'unique-request-1'},
            files={'media': ('different.jpg', b'different-bytes', 'image/jpeg')})
        self.assertEqual(different.status_code, 409)
        self.assertEqual(submit('unique-request-2').status_code, 202)
        self.assertEqual(submit('unique-request-3').status_code, 429)
        self.assertEqual(len([p for p in Path(self.temp.name).iterdir() if p.is_dir()]), 2)

    def test_oversize(self):
        response = self.client.post('/v1/cooking/jobs', data={'kind': 'video', 'provider': 'openai_existing',
            'external_consent': 'true'}, files={'media': ('v.mp4', b'0' * (10 * 1024 * 1024 + 1), 'video/mp4')})
        self.assertEqual(response.status_code, 413)

    def test_real_isolated_worker_failure_cleans_media(self):
        response = self.client.post('/v1/cooking/jobs', data={'kind': 'image', 'provider': 'openai_existing',
            'external_consent': 'true'}, files={'media': ('invalid.jpg', b'invalid-image', 'image/jpeg')})
        self.assertEqual(response.status_code, 202)
        job = response.json()['id']
        folder = Path(self.temp.name) / job
        cooking.execute(job, folder)
        result = self.client.get('/v1/cooking/jobs/' + job).json()
        self.assertEqual(result['state'], 'failed')
        self.assertNotIn('test-only', str(result))
        self.assertFalse(folder.exists())

    def test_cancel_before_worker_never_starts_provider(self):
        response = self.create()
        job = response.json()['id']
        folder = Path(self.temp.name) / job
        self.client.post('/v1/cooking/jobs/' + job + '/cancel')
        with patch('backend.cooking.subprocess.Popen', side_effect=AssertionError('Provider must not start')):
            cooking.execute(job, folder)
        self.assertFalse(folder.exists())
        self.assertEqual(self.client.get('/v1/cooking/jobs/' + job).json()['state'], 'cancelled')

    def test_cleanup_preserves_unrelated_directories(self):
        import time
        unrelated = Path(self.temp.name) / ('a' * 32)
        unrelated.mkdir()
        (unrelated / 'user-file.txt').write_text('must keep')
        os.utime(unrelated, (time.time() - 1000, time.time() - 1000))
        self.assertEqual(self.create().status_code, 202)
        self.assertEqual((unrelated / 'user-file.txt').read_text(), 'must keep')
