"""Independent accounts, revocable sessions, optimistic sync and cloud backups.

No ReciMe endpoints or credentials. Database schema is versioned and additive.
"""
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import time
import uuid
from contextlib import contextmanager
from typing import Literal

from fastapi import APIRouter, Depends, Header, HTTPException, Query
from pydantic import BaseModel, Field, field_validator
from backend.config import database_path

router = APIRouter(prefix='/v1')


@contextmanager
def database(write=False):
    path = database_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path, timeout=15)
    db.row_factory = sqlite3.Row
    try:
        db.execute('PRAGMA foreign_keys=ON')
        db.execute('PRAGMA journal_mode=WAL')
        db.execute('CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY)')
        if not db.execute('SELECT 1 FROM schema_migrations WHERE version=1').fetchone():
            db.executescript('''
              CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,name TEXT NOT NULL,created_at INTEGER NOT NULL);
              CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY,user_id TEXT NOT NULL
                REFERENCES users(id) ON DELETE CASCADE,expires_at INTEGER NOT NULL);
              CREATE TABLE IF NOT EXISTS records(user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                entity TEXT NOT NULL,entity_id TEXT NOT NULL,data TEXT NOT NULL,deleted INTEGER NOT NULL,
                revision INTEGER NOT NULL,PRIMARY KEY(user_id,entity,entity_id));
              CREATE INDEX IF NOT EXISTS records_cursor ON records(user_id,revision);
              CREATE TABLE IF NOT EXISTS clocks(user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                revision INTEGER NOT NULL DEFAULT 0);
              CREATE TABLE IF NOT EXISTS operations(user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                operation_id TEXT NOT NULL,digest TEXT NOT NULL,result TEXT NOT NULL,
                PRIMARY KEY(user_id,operation_id));
              CREATE TABLE IF NOT EXISTS backups(user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                revision INTEGER NOT NULL,payload TEXT NOT NULL,updated_at INTEGER NOT NULL);
              INSERT OR IGNORE INTO schema_migrations VALUES(1);
            ''')
        if write:
            db.execute('BEGIN IMMEDIATE')
        yield db
        db.commit()
    except BaseException:
        db.rollback()
        raise
    finally:
        db.close()


def password_hash(value, salt=None):
    salt = salt or secrets.token_hex(16)
    digest = hashlib.scrypt(value.encode(), salt=bytes.fromhex(salt), n=16384, r=8, p=1).hex()
    return salt + ':' + digest


class Credentials(BaseModel):
    email: str = Field(min_length=3, max_length=254)
    password: str = Field(min_length=10, max_length=128)

    @field_validator('email')
    @classmethod
    def email_format(cls, value):
        value = value.strip().lower()
        if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', value):
            raise ValueError('Invalid email address')
        return value


class Registration(Credentials):
    name: str = Field(default='', max_length=100)


class Profile(BaseModel):
    name: str = Field(min_length=1, max_length=100)


def session(db, user):
    token = secrets.token_urlsafe(32)
    expires = int(time.time()) + 7 * 86400
    db.execute('INSERT INTO sessions VALUES(?,?,?)', (hashlib.sha256(token.encode()).hexdigest(), user['id'], expires))
    return {'accessToken': token, 'expiresAt': expires, 'user': public_user(user)}


def public_user(user):
    return {key: user[key] for key in ('id', 'email', 'name')}


def identity(authorization: str = Header(default='')):
    if not authorization.startswith('Bearer ') or len(authorization) > 200:
        raise HTTPException(401, 'Login required')
    digest = hashlib.sha256(authorization[7:].encode()).hexdigest()
    with database() as db:
        user = db.execute('SELECT users.* FROM users JOIN sessions ON sessions.user_id=users.id '
                          'WHERE sessions.token_hash=? AND expires_at>?', (digest, int(time.time()))).fetchone()
    if user is None:
        raise HTTPException(401, 'Session expired; sign in again')
    return dict(user)


@router.post('/auth/register', status_code=201)
def register(body: Registration):
    if os.getenv('ALLOW_REGISTRATION', 'true').lower() != 'true':
        raise HTTPException(403, 'Registration disabled by server')
    user = {'id': str(uuid.uuid4()), 'email': body.email, 'name': body.name.strip()}
    hashed = password_hash(body.password)
    with database(True) as db:
        try:
            db.execute('INSERT INTO users VALUES(?,?,?,?,?)', (user['id'], user['email'], hashed, user['name'], int(time.time())))
        except sqlite3.IntegrityError:
            raise HTTPException(409, 'Email already registered')
        return session(db, user)


@router.post('/auth/login')
def login(body: Credentials):
    with database(True) as db:
        user = db.execute('SELECT * FROM users WHERE email=?', (body.email,)).fetchone()
        # Same cost for unknown accounts; never store plaintext passwords.
        expected = user['password_hash'] if user else '00' * 16 + ':' + '00' * 64
        actual = password_hash(body.password, expected.split(':')[0])
        if not hmac.compare_digest(expected, actual):
            raise HTTPException(401, 'Invalid email or password')
        db.execute('DELETE FROM sessions WHERE expires_at<=?', (int(time.time()),))
        return session(db, user)


@router.get('/account')
def account(user=Depends(identity)):
    return public_user(user)


@router.patch('/account')
def profile(body: Profile, user=Depends(identity)):
    with database(True) as db:
        db.execute('UPDATE users SET name=? WHERE id=?', (body.name.strip(), user['id']))
    user['name'] = body.name.strip()
    return public_user(user)


@router.post('/auth/logout')
def logout(user=Depends(identity), authorization: str = Header()):
    with database(True) as db:
        db.execute('DELETE FROM sessions WHERE token_hash=?', (hashlib.sha256(authorization[7:].encode()).hexdigest(),))
    return {'status': 'signed-out', 'localData': 'preserved'}


class DeleteAccount(BaseModel):
    password: str = Field(min_length=10, max_length=128)


@router.delete('/account')
def delete_account(body: DeleteAccount, user=Depends(identity)):
    if not hmac.compare_digest(password_hash(body.password, user['password_hash'].split(':')[0]), user['password_hash']):
        raise HTTPException(401, 'Password confirmation failed')
    with database(True) as db:
        db.execute('DELETE FROM users WHERE id=?', (user['id'],))
    from backend.cooking import delete_user_jobs
    delete_user_jobs(user['id'])
    return {'status': 'deleted', 'localData': 'preserved'}


class Change(BaseModel):
    operationId: uuid.UUID
    entity: Literal['recipe', 'cookbook', 'membership', 'meal', 'grocery', 'preferences']
    entityId: str = Field(min_length=1, max_length=100, pattern=r'^[a-zA-Z0-9:_-]+$')
    baseRevision: int = Field(ge=0)
    deleted: bool = False
    data: dict = Field(default_factory=dict)

    @field_validator('data')
    @classmethod
    def bounded(cls, value):
        if len(json.dumps(value, ensure_ascii=False, allow_nan=False).encode()) > 128 * 1024:
            raise ValueError('Record exceeds 128KB')
        return value


@router.post('/sync/push')
def push(body: Change, user=Depends(identity)):
    digest = hashlib.sha256(body.model_dump_json().encode()).hexdigest()
    with database(True) as db:
        prior = db.execute('SELECT * FROM operations WHERE user_id=? AND operation_id=?', (user['id'], str(body.operationId))).fetchone()
        if prior:
            if prior['digest'] != digest:
                raise HTTPException(409, 'Idempotency key reused with different content')
            return json.loads(prior['result'])
        current = db.execute('SELECT * FROM records WHERE user_id=? AND entity=? AND entity_id=?', (user['id'], body.entity, body.entityId)).fetchone()
        if (current['revision'] if current else 0) != body.baseRevision:
            raise HTTPException(409, {'reason': 'revision-conflict', 'current': record(current) if current else None,
                                      'policy': 'Keep both local draft and remote; resolve explicitly'})
        db.execute('INSERT OR IGNORE INTO clocks VALUES(?,0)', (user['id'],))
        db.execute('UPDATE clocks SET revision=revision+1 WHERE user_id=?', (user['id'],))
        revision = db.execute('SELECT revision FROM clocks WHERE user_id=?', (user['id'],)).fetchone()[0]
        data = json.dumps(body.data, ensure_ascii=False, allow_nan=False)
        db.execute('INSERT INTO records VALUES(?,?,?,?,?,?) ON CONFLICT(user_id,entity,entity_id) DO UPDATE SET '
                   'data=excluded.data,deleted=excluded.deleted,revision=excluded.revision',
                   (user['id'], body.entity, body.entityId, data, int(body.deleted), revision))
        result = {'entity': body.entity, 'entityId': body.entityId, 'revision': revision, 'operationId': str(body.operationId)}
        db.execute('INSERT INTO operations VALUES(?,?,?,?)', (user['id'], str(body.operationId), digest, json.dumps(result)))
        return result


def record(row):
    return {'entity': row['entity'], 'entityId': row['entity_id'], 'data': json.loads(row['data']),
            'deleted': bool(row['deleted']), 'revision': row['revision']}


@router.get('/sync/pull')
def pull(cursor: int = Query(0, ge=0), limit: int = Query(100, ge=1, le=500), user=Depends(identity)):
    with database() as db:
        rows = db.execute('SELECT * FROM records WHERE user_id=? AND revision>? ORDER BY revision LIMIT ?',
                          (user['id'], cursor, limit + 1)).fetchall()
    items = [record(row) for row in rows[:limit]]
    return {'changes': items, 'cursor': items[-1]['revision'] if items else cursor, 'hasMore': len(rows) > limit}


class Backup(BaseModel):
    operationId: uuid.UUID
    baseRevision: int = Field(ge=0)
    payload: dict


@router.put('/backup')
def upload_backup(body: Backup, user=Depends(identity)):
    encoded = json.dumps(body.payload, ensure_ascii=False, allow_nan=False)
    if len(encoded.encode()) > 8 * 1024 * 1024:
        raise HTTPException(413, 'Cloud backup maximum 8MB; export a local backup instead')
    if body.payload.get('format') != 'recime-backup' or body.payload.get('version') != 1:
        raise HTTPException(422, 'Unsupported backup format')
    digest = hashlib.sha256(body.model_dump_json().encode()).hexdigest()
    with database(True) as db:
        prior = db.execute('SELECT * FROM operations WHERE user_id=? AND operation_id=?', (user['id'], str(body.operationId))).fetchone()
        if prior:
            if digest != prior['digest']:
                raise HTTPException(409, 'Idempotency key reused')
            return json.loads(prior['result'])
        row = db.execute('SELECT revision FROM backups WHERE user_id=?', (user['id'],)).fetchone()
        revision = row['revision'] if row else 0
        if body.baseRevision != revision:
            raise HTTPException(409, {'reason': 'backup-conflict', 'revision': revision,
                                      'policy': 'Download and merge before uploading; no overwrite'})
        revision += 1
        db.execute('INSERT INTO backups VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET '
                   'revision=excluded.revision,payload=excluded.payload,updated_at=excluded.updated_at',
                   (user['id'], revision, encoded, int(time.time())))
        result = {'revision': revision, 'status': 'saved'}
        db.execute('INSERT INTO operations VALUES(?,?,?,?)', (user['id'], str(body.operationId), digest, json.dumps(result)))
    return result


@router.get('/backup')
def download_backup(user=Depends(identity)):
    with database() as db:
        row = db.execute('SELECT * FROM backups WHERE user_id=?', (user['id'],)).fetchone()
    if not row:
        raise HTTPException(404, 'No cloud backup yet')
    return {'revision': row['revision'], 'updatedAt': row['updated_at'], 'payload': json.loads(row['payload'])}
