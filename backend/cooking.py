"""Authorized, durable job metadata; isolated workers enforce cancellation/timeouts."""
import json
import hashlib
import os
import re
import shutil
import signal
import sqlite3
import subprocess
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from pathlib import Path
from fastapi import APIRouter, Depends, File, Form, Header, HTTPException, UploadFile
from backend.cloud import identity
from cooking_ai.inference.local import model_info

router = APIRouter(prefix='/v1/cooking', tags=['Cooking AI'])
pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix='cooking-job')
lock = threading.Lock()
initialized = set()


def root():
    from backend.config import database_path
    value = Path(os.getenv('COOKING_AI_JOBS_DIR', str(database_path().parent / 'cooking_jobs'))).resolve()
    value.mkdir(parents=True, exist_ok=True)
    return value


def delete_user_jobs(owner):
    with lock, connect() as db:
        db.execute("UPDATE jobs SET state='cancelled' WHERE owner=? AND state IN ('queued','running')", (str(owner),))
        db.execute('UPDATE jobs SET result=NULL,error=NULL WHERE owner=?', (str(owner),))


@contextmanager
def connect():
    folder = root()
    db = sqlite3.connect(folder / 'jobs.db', timeout=15)
    db.row_factory = sqlite3.Row
    db.execute('PRAGMA journal_mode=WAL')
    db.execute('''CREATE TABLE IF NOT EXISTS jobs (
        id TEXT PRIMARY KEY, owner TEXT NOT NULL, kind TEXT NOT NULL, provider TEXT NOT NULL,
        consent INTEGER NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL,
        created REAL NOT NULL, updated REAL NOT NULL, result TEXT, error TEXT,
        request_key TEXT, media_sha TEXT, UNIQUE(owner, request_key))''')
    if 'media_sha' not in {r[1] for r in db.execute('PRAGMA table_info(jobs)')}:
        db.execute('ALTER TABLE jobs ADD COLUMN media_sha TEXT')
    # Exactly one Uvicorn process is supported. Interrupted media is never silently resent.
    if str(folder) not in initialized:
        interrupted = [r[0] for r in db.execute("SELECT id FROM jobs WHERE state IN ('queued','running')")]
        db.execute("UPDATE jobs SET state='interrupted', error='Server restarted; upload again', updated=? WHERE state IN ('queued','running')", (time.time(),))
        for identifier in interrupted:
            if re.fullmatch(r'[a-f0-9]{32}', identifier) and (folder / identifier / '.recime-cooking-job').is_file():
                shutil.rmtree(folder / identifier, ignore_errors=True)
        initialized.add(str(folder))
    try:
        db.commit()
        with db:
            yield db
    finally:
        db.close()


def update(job, **values):
    values['updated'] = time.time()
    with lock, connect() as db:
        db.execute('UPDATE jobs SET ' + ','.join(k + '=?' for k in values) + ' WHERE id=?',
                   [*values.values(), job])


def row(job, user):
    with lock, connect() as db:
        found = db.execute('SELECT * FROM jobs WHERE id=? AND owner=?', (job, str(user['id']))).fetchone()
    if not found:
        raise HTTPException(404, 'Job not found')
    return found


def public(value):
    return {'id': value['id'], 'state': value['state'], 'progress': value['progress'],
            'kind': value['kind'], 'provider': value['provider'],
            'result': json.loads(value['result']) if value['result'] else None,
            'error': value['error'], 'retryRequiresUpload': value['state'] in ('failed', 'cancelled', 'interrupted')}


def execute(job, folder):
    process = None
    try:
        with lock, connect() as db:
            record = db.execute('SELECT state FROM jobs WHERE id=?', (job,)).fetchone()
            if record['state'] != 'queued':
                return
            db.execute("UPDATE jobs SET state='running',updated=? WHERE id=?", (time.time(), job))
        process = subprocess.Popen([sys.executable, '-m', 'cooking_ai.scripts.worker', str(folder)],
            cwd=Path(__file__).resolve().parent.parent, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            start_new_session=os.name != 'nt',
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        deadline = time.monotonic() + min(600, max(30, int(os.getenv('COOKING_AI_JOB_TIMEOUT', '300'))))
        while process.poll() is None:
            with lock, connect() as db:
                state = db.execute('SELECT state FROM jobs WHERE id=?', (job,)).fetchone()['state']
            if state == 'cancelled' or time.monotonic() > deadline:
                terminate(process)
                process.wait(timeout=15)
                if state != 'cancelled':
                    update(job, state='failed', error='Analysis timed out; shorten media and upload again')
                return
            try:
                value = int((folder / 'progress').read_text())
                update(job, progress=max(0, min(99, value)))
            except (OSError, ValueError):
                pass
            time.sleep(0.25)
        result = json.loads((folder / 'result.json').read_text(encoding='utf-8'))
        with lock, connect() as db:
            if db.execute('SELECT state FROM jobs WHERE id=?', (job,)).fetchone()['state'] != 'cancelled':
                db.execute('UPDATE jobs SET state=?,progress=?,result=?,error=?,updated=? WHERE id=?',
                    ('succeeded' if result['ok'] else 'failed', 100 if result['ok'] else 0,
                     json.dumps(result['result'], ensure_ascii=False) if result['ok'] else None,
                     result.get('error'), time.time(), job))
    except Exception:
        with lock, connect() as db:
            db.execute("UPDATE jobs SET state='failed', error='Worker unavailable; verify model and media tools',updated=? WHERE id=? AND state!='cancelled'", (time.time(), job))
    finally:
        if process is not None and process.poll() is None:
            terminate(process)
            process.wait(timeout=15)
        shutil.rmtree(folder, ignore_errors=True)


def terminate(process):
    if os.name == 'nt':
        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
    else:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


@router.get('/models')
def models(user=Depends(identity)):
    return {'models': [model_info(), {'provider': 'openai_existing',
        'available': bool(os.getenv('OPENAI_API_KEY', '').strip()), 'requiresExternalConsent': True},
        {'provider': 'disabled', 'available': True}], 'autoFallback': False,
        'maxUploadBytes': 10 * 1024 * 1024, 'maxDurationSeconds': 180,
        'singleWorkerRequired': True}


@router.post('/jobs', status_code=202)
def create_job(media: UploadFile = File(...), kind: str = Form(...),
               provider: str = Form('trained_local_vlm'), external_consent: bool = Form(False),
               idempotency_key: str | None = Header(default=None), user=Depends(identity)):
    if provider == 'disabled':
        raise HTTPException(503, 'Cooking AI disabled; OCR and manual import remain available')
    if kind not in ('image', 'video') or provider not in ('trained_local_vlm', 'openai_existing'):
        raise HTTPException(422, 'Choose image/video and an available provider')
    if provider == 'openai_existing' and not external_consent:
        raise HTTPException(403, 'Explicit external media consent required')
    if provider == 'trained_local_vlm' and not model_info()['available']:
        raise HTTPException(503, 'Local model unavailable: ' + model_info()['status'] + '; validated/approved checkpoint required. OpenAI requires separate consent')
    if provider == 'openai_existing' and not os.getenv('OPENAI_API_KEY', '').strip():
        raise HTTPException(503, 'OpenAI not configured')
    if idempotency_key is not None and (not 8 <= len(idempotency_key) <= 100):
        raise HTTPException(422, 'Idempotency-Key length must be 8..100')
    suffix = Path(media.filename or '').suffix.lower()
    allowed = ('.mp4', '.mov') if kind == 'video' else ('.jpg', '.jpeg', '.png', '.webp')
    if suffix not in allowed:
        raise HTTPException(422, 'Unsupported media extension')
    job = uuid.uuid4().hex
    folder = root() / job
    folder.mkdir()
    (folder / '.recime-cooking-job').write_text(job, encoding='ascii')
    try:
        size = 0
        checksum = hashlib.sha256()
        with (folder / ('media' + suffix)).open('wb') as output:
            for block in iter(lambda: media.file.read(65536), b''):
                size += len(block)
                if size > 10 * 1024 * 1024:
                    raise HTTPException(413, 'Media maximum 10MB')
                output.write(block)
                checksum.update(block)
        if not size:
            raise HTTPException(422, 'Empty media')
        with lock, connect() as db:
            if idempotency_key:
                existing = db.execute('SELECT * FROM jobs WHERE owner=? AND request_key=?',
                                      (str(user['id']), idempotency_key)).fetchone()
                if existing:
                    if (existing['kind'] != kind or existing['provider'] != provider
                        or existing['media_sha'] != checksum.hexdigest() or existing['consent'] != int(external_consent)):
                        raise HTTPException(409, 'Idempotency key belongs to another request')
                    return public(existing)
            active = db.execute("SELECT owner FROM jobs WHERE state IN ('queued','running')").fetchall()
            if len(active) >= 4 or sum(r['owner'] == str(user['id']) for r in active) >= 2:
                raise HTTPException(429, 'Cooking queue full; wait or cancel a job')
            now = time.time()
            # Terminal metadata expires after seven days; remove restart orphans too.
            db.execute("DELETE FROM jobs WHERE updated<? AND state NOT IN ('queued','running')", (now - 7 * 86400,))
            active_ids = {r['id'] for r in db.execute("SELECT id FROM jobs WHERE state IN ('queued','running')")}
            for child in root().iterdir():
                if (child.is_dir() and re.fullmatch(r'[a-f0-9]{32}', child.name)
                    and (child / '.recime-cooking-job').is_file() and child.name != job
                    and child.name not in active_ids and child.stat().st_mtime < now - 600):
                    shutil.rmtree(child, ignore_errors=True)
            db.execute('INSERT INTO jobs VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)',
                       (job, str(user['id']), kind, provider, int(external_consent), 'queued', 0, now, now, None, None, idempotency_key, checksum.hexdigest()))
        (folder / 'request.json').write_text(json.dumps({'kind': kind, 'provider': provider,
            'path': str(folder / ('media' + suffix))}), encoding='utf-8')
        try:
            pool.submit(execute, job, folder)
        except RuntimeError:
            update(job, state='failed', error='Worker unavailable; upload again')
            raise HTTPException(503, 'Worker unavailable; upload again')
        folder = None  # Worker owns cleanup after successful scheduling.
        return public(row(job, user))
    finally:
        if folder is not None:
            shutil.rmtree(folder, ignore_errors=True)


@router.get('/jobs/{job}')
def status(job: str, user=Depends(identity)):
    return public(row(job, user))


@router.post('/jobs/{job}/cancel')
def cancel(job: str, user=Depends(identity)):
    row(job, user)
    with lock, connect() as db:
        db.execute("UPDATE jobs SET state='cancelled',updated=? WHERE id=? AND state IN ('queued','running')", (time.time(), job))
    return public(row(job, user))


@router.post('/jobs/{job}/retry', status_code=202)
def retry(job: str, media: UploadFile = File(...), external_consent: bool = Form(False),
          idempotency_key: str | None = Header(default=None), user=Depends(identity)):
    previous = row(job, user)
    if previous['state'] not in ('failed', 'cancelled', 'interrupted'):
        raise HTTPException(409, 'Only terminal unsuccessful jobs can be retried')
    return create_job(media, previous['kind'], previous['provider'], external_consent, idempotency_key, user)
