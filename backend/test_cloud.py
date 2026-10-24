import json
import os
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from fastapi import FastAPI
from fastapi.testclient import TestClient
from backend.cloud import router, database
from backend.hardening import RequestGuard


class CloudContractTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.env = patch.dict(os.environ, {'DATABASE_URL': 'sqlite:///' + self.directory.name + '/cloud.db',
                                          'ALLOW_REGISTRATION': 'true', 'APP_ENV': 'development'})
        self.env.start()
        app = FastAPI()
        app.include_router(router)
        app.add_middleware(RequestGuard)
        self.client = TestClient(app)
        self.credentials = {'email': 'test@example.org', 'password': 'test-only-long-password'}
        response = self.client.post('/v1/auth/register', json={**self.credentials, 'name': 'QA'})
        self.assertEqual(201, response.status_code, response.text)
        self.user = response.json()
        self.headers = {'Authorization': 'Bearer ' + self.user['accessToken']}

    def tearDown(self):
        self.client.close()
        self.env.stop()
        self.directory.cleanup()

    def change(self, **kwargs):
        return {'operationId': str(uuid.uuid4()), 'entity': 'recipe', 'entityId': 'test-recipe',
                'baseRevision': 0, 'data': {'title': 'Fixture'}, **kwargs}

    def test_credentials_are_hashed_and_sessions_revoked_on_logout(self):
        with database() as db:
            user = dict(db.execute('SELECT * FROM users').fetchone())
            stored_session = dict(db.execute('SELECT * FROM sessions').fetchone())
            self.assertNotIn(self.credentials['password'], user['password_hash'])
            self.assertNotEqual(stored_session['token_hash'], self.user['accessToken'])
            self.assertEqual(1, db.execute('PRAGMA user_version').fetchone()[0] or db.execute('SELECT version FROM schema_migrations').fetchone()[0])
        self.assertEqual(200, self.client.get('/v1/account', headers=self.headers).status_code)
        self.assertEqual(200, self.client.post('/v1/auth/logout', headers=self.headers).status_code)
        self.assertEqual(401, self.client.get('/v1/account', headers=self.headers).status_code)
        self.assertEqual(200, self.client.post('/v1/auth/login', json=self.credentials).status_code)

    def test_invalid_auth_registration_and_password(self):
        self.assertEqual(401, self.client.get('/v1/account').status_code)
        self.assertEqual(409, self.client.post('/v1/auth/register', json=self.credentials).status_code)
        self.assertEqual(422, self.client.post('/v1/auth/register', json={'email': 'bad', 'password': 'short'}).status_code)
        self.assertEqual(401, self.client.post('/v1/auth/login', json={**self.credentials, 'password': 'wrong-password'}).status_code)
        with patch.dict(os.environ, {'ALLOW_REGISTRATION': 'false'}):
            self.assertEqual(403, self.client.post('/v1/auth/register', json={**self.credentials, 'email': 'other@example.org'}).status_code)

    def test_idempotency_conflicts_tombstones_and_pagination(self):
        body = self.change()
        first = self.client.post('/v1/sync/push', headers=self.headers, json=body)
        self.assertEqual(200, first.status_code)
        revision = first.json()['revision']
        self.assertEqual(first.json(), self.client.post('/v1/sync/push', headers=self.headers, json=body).json())
        self.assertEqual(409, self.client.post('/v1/sync/push', headers=self.headers, json={**body, 'data': {'title': 'changed'}}).status_code)
        conflict = self.client.post('/v1/sync/push', headers=self.headers, json=self.change(data={'title': 'stale'}))
        self.assertEqual(409, conflict.status_code)
        self.assertEqual('Fixture', conflict.json()['detail']['current']['data']['title'])
        for entity in ['cookbook', 'membership', 'meal', 'grocery', 'preferences']:
            self.assertEqual(200, self.client.post('/v1/sync/push', headers=self.headers, json=self.change(entity=entity)).status_code)
        deleted = self.client.post('/v1/sync/push', headers=self.headers, json=self.change(baseRevision=revision, deleted=True))
        self.assertEqual(200, deleted.status_code)
        changes, cursor = [], 0
        while True:
            page = self.client.get('/v1/sync/pull', params={'cursor': cursor, 'limit': 2}, headers=self.headers).json()
            changes.extend(page['changes'])
            cursor = page['cursor']
            if not page['hasMore']:
                break
        self.assertEqual(6, len(changes))
        self.assertTrue(next(r for r in changes if r['entity'] == 'recipe')['deleted'])
        self.assertEqual([], self.client.get('/v1/sync/pull', params={'cursor': cursor}, headers=self.headers).json()['changes'])

    def test_other_users_cannot_read_or_modify_records_or_backups(self):
        self.client.post('/v1/sync/push', headers=self.headers, json=self.change())
        second = self.client.post('/v1/auth/register', json={**self.credentials, 'email': 'second@example.org'}).json()
        headers = {'Authorization': 'Bearer ' + second['accessToken']}
        self.assertEqual([], self.client.get('/v1/sync/pull', headers=headers).json()['changes'])
        self.assertEqual(404, self.client.get('/v1/backup', headers=headers).status_code)
        self.assertEqual(200, self.client.post('/v1/sync/push', headers=headers, json=self.change()).status_code)

    def test_backup_conflict_retry_and_delete_account_cascade(self):
        body = {'operationId': str(uuid.uuid4()), 'baseRevision': 0,
                'payload': {'format': 'recime-backup', 'version': 1, 'tables': {}}}
        first = self.client.put('/v1/backup', headers=self.headers, json=body)
        self.assertEqual(200, first.status_code, first.text)
        self.assertEqual(first.json(), self.client.put('/v1/backup', headers=self.headers, json=body).json())
        self.assertEqual(409, self.client.put('/v1/backup', headers=self.headers, json={**body, 'operationId': str(uuid.uuid4())}).status_code)
        backup = self.client.get('/v1/backup', headers=self.headers).json()
        self.assertEqual(1, backup['revision'])
        self.assertEqual(body['payload'], backup['payload'])
        self.client.post('/v1/sync/push', headers=self.headers, json=self.change())
        self.assertEqual(401, self.client.request('DELETE', '/v1/account', headers=self.headers, json={'password': 'wrong-password'}).status_code)
        self.assertEqual(200, self.client.request('DELETE', '/v1/account', headers=self.headers, json={'password': self.credentials['password']}).status_code)
        self.assertEqual(401, self.client.get('/v1/account', headers=self.headers).status_code)
        with database() as db:
            for table in ('users', 'records', 'clocks', 'sessions', 'backups', 'operations'):
                self.assertEqual(0, db.execute('SELECT COUNT(*) FROM ' + table).fetchone()[0])
            self.assertEqual([], db.execute('PRAGMA foreign_key_check').fetchall())

    def test_limits_profile_and_invalid_payload(self):
        result = self.client.patch('/v1/account', headers=self.headers, json={'name': 'Renamed'})
        self.assertEqual('Renamed', result.json()['name'])
        self.assertEqual(422, self.client.post('/v1/sync/push', headers=self.headers, json=self.change(entity='invalid')).status_code)
        self.assertEqual(422, self.client.post('/v1/sync/push', headers=self.headers, json=self.change(data={'huge': 'a' * 131073})).status_code)
        self.assertEqual(413, self.client.post('/v1/auth/login', content=b'', headers={'Content-Length': str(13 * 1024 * 1024)}).status_code)
        for _ in range(13):
            self.assertEqual(401, self.client.post('/v1/auth/login', json={**self.credentials, 'password': 'wrong-password'}).status_code)
        self.assertEqual(429, self.client.post('/v1/auth/login', json=self.credentials).status_code)


if __name__ == '__main__':
    unittest.main()
