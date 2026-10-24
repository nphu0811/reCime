import json
import os
import shutil
import sqlite3
import threading
import uuid
from contextlib import closing, contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated, Literal
from pydantic import Field
from cooking_ai.schema import Strict
from cooking_ai.data.manifest import Annotation, digest, media_path, validate
from cooking_ai.scripts.import_dataset import import_annotations


class DraftIngredient(Strict):
    name: str = Field(default='', max_length=200)
    evidence: str = Field(default='', max_length=2000)


class Draft(Strict):
    status: Literal['pending', 'correct', 'wrong', 'unsure', 'skipped'] = 'pending'
    title: str = Field(default='', max_length=300)
    ingredients: list[DraftIngredient] = Field(default_factory=list, max_length=100)
    uncertainty: list[Annotated[str, Field(max_length=1000)]] = Field(default_factory=list, max_length=30)
    rights_confirmed: bool = Field(default=False, strict=True)
    label_confirmed: bool = Field(default=False, strict=True)
    revision: int = Field(default=0, ge=0, strict=True)


class StaleDraft(ValueError):
    pass


class ReviewerStore:
    def __init__(self, manifest, state_dir, output_dir):
        self.manifest = Path(manifest).resolve()
        self.root = self.manifest.parent
        self.rows, self.dataset_report = validate(self.manifest)
        self.images = [row for row in self.rows if row.kind == 'image']
        if not self.images:
            raise ValueError('Dataset contains no images to review')
        self.dataset_sha = self.dataset_report['manifest_sha256']
        self.state_dir = Path(state_dir).resolve()
        self.output_dir = Path(output_dir).resolve()
        for path in (self.state_dir, self.output_dir):
            if path.is_relative_to(self.root):
                raise ValueError('Reviewer state/output must be outside the original dataset directory')
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self.db_path = self.state_dir / 'reviewer.sqlite3'
        if self.db_path.exists():
            with closing(sqlite3.connect(self.db_path.resolve().as_uri() + '?mode=ro', uri=True)) as existing:
                tables = {r[0] for r in existing.execute("SELECT name FROM sqlite_master WHERE type='table'")}
                if tables and 'reviewer_meta' not in tables:
                    raise ValueError('State path contains another database; choose a separate --state-dir')
        self.export_lock = threading.Lock()
        with self.database() as db:
            db.execute('CREATE TABLE IF NOT EXISTS reviewer_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)')
            prior = db.execute("SELECT value FROM reviewer_meta WHERE key='dataset_sha256'").fetchone()
            if prior and prior[0] != self.dataset_sha:
                raise ValueError('Dataset changed: keep this review database and use a different --state-dir for the new dataset')
            db.execute("INSERT OR IGNORE INTO reviewer_meta VALUES('dataset_sha256',?)", (self.dataset_sha,))
            db.execute("INSERT OR IGNORE INTO reviewer_meta VALUES('last_index','0')")
            db.execute('CREATE TABLE IF NOT EXISTS reviewer_drafts(id TEXT PRIMARY KEY,revision INTEGER NOT NULL,draft TEXT NOT NULL,updated TEXT NOT NULL)')

    @contextmanager
    def database(self):
        db = sqlite3.connect(self.db_path, timeout=15)
        db.row_factory = sqlite3.Row
        try:
            db.execute('PRAGMA journal_mode=WAL')
            db.execute('PRAGMA synchronous=FULL')
            with db:
                yield db
        finally:
            db.close()

    def original(self, index):
        if not 0 <= index < len(self.images):
            raise IndexError('Image index out of range')
        return self.images[index]

    def default(self, row):
        # Never inherit an existing annotation_verified bit as human confirmation here.
        return Draft(title=row.target.title or '', uncertainty=[
            'Không xác định được định lượng ẩn hoặc các bước nấu chỉ từ ảnh.'])

    def read(self, db, row):
        saved = db.execute('SELECT draft FROM reviewer_drafts WHERE id=?', (row.id,)).fetchone()
        return Draft.model_validate_json(saved[0]) if saved else self.default(row)

    @staticmethod
    def verified(draft):
        return (draft.status == 'correct' and draft.label_confirmed and draft.rights_confirmed
                and bool(draft.title.strip()) and all(i.name.strip() and i.evidence.strip() for i in draft.ingredients))

    def progress(self, db=None):
        if db is None:
            with self.database() as connection:
                return self.progress(connection)
        result = dict.fromkeys(('correct', 'wrong', 'unsure', 'skipped', 'pending'), 0)
        verified = 0
        for row in self.images:
            draft = self.read(db, row)
            result[draft.status] += 1
            verified += bool(self.verified(draft))
        result.update(verified=verified, reviewed=result['correct'] + result['wrong'] + result['unsure'])
        result['handled'] = result['reviewed'] + result['skipped']
        return result

    def session(self):
        with self.database() as db:
            return {'total': len(self.images), 'progress': self.progress(db),
                    'last_index': int(db.execute("SELECT value FROM reviewer_meta WHERE key='last_index'").fetchone()[0]),
                    'dataset_sha256': self.dataset_sha, 'session_id': self.dataset_sha[:12]}

    def item(self, index, *, remember=True):
        row = self.original(index)
        with self.database() as db:
            draft = self.read(db, row)
            if remember:
                db.execute("UPDATE reviewer_meta SET value=? WHERE key='last_index'", (str(index),))
        return {'index': index, 'total': len(self.images), 'id': row.id,
                'title': row.target.title, 'source_id': row.source_id, 'split': row.split,
                'attribution': row.attribution, 'license': row.license,
                'permission_evidence': row.permission_evidence,
                'original_uncertainty': row.target.uncertainty,
                'media_url': '/media/' + str(index), 'draft': draft.model_dump(),
                'verified': bool(self.verified(draft))}

    def save(self, index, requested):
        row = self.original(index)
        draft = Draft.model_validate(requested)
        with self.database() as db:
            db.execute('BEGIN IMMEDIATE')
            current = self.read(db, row)
            if current.revision != draft.revision:
                raise StaleDraft('Ảnh đã được sửa trong tab khác. Giữ nội dung hiện tại và tải lại trước khi lưu tiếp.')
            fields = ('status', 'title', 'ingredients', 'uncertainty')
            if any(getattr(current, key) != getattr(draft, key) for key in fields):
                draft.label_confirmed = False
            if draft.label_confirmed and draft.status == 'correct':
                if not draft.title.strip() or any(not i.name.strip() or not i.evidence.strip() for i in draft.ingredients):
                    raise ValueError('Cần tên món và bằng chứng cho từng nguyên liệu; bỏ hàng nhập trống trước khi xác nhận')
            draft.revision += 1
            db.execute('INSERT OR REPLACE INTO reviewer_drafts VALUES(?,?,?,?)',
                       (row.id, draft.revision, draft.model_dump_json(), datetime.now(timezone.utc).isoformat()))
            db.execute("UPDATE reviewer_meta SET value=? WHERE key='last_index'", (str(index),))
        result = self.item(index, remember=False)
        result['progress'] = self.progress()
        return result

    def annotation(self, row, draft):
        value = row.model_dump()
        value.update(media='media/' + row.sha256 + Path(row.media).suffix.lower(),
                     permission_to_train=True, annotation_verified=True, text='')
        value['target'] = {'is_cooking': True, 'title': draft.title.strip(), 'language': row.language,
            'ingredients': [{'name': ingredient.name.strip(), 'quantity': None, 'unit': None,
                'evidence': {'source': 'visual', 'text': ingredient.evidence.strip(), 'start': None, 'end': None}}
                for ingredient in draft.ingredients],
            'steps': [], 'uncertainty': draft.uncertainty or ['Định lượng ẩn và cách nấu chưa xác định từ ảnh.'],
            'suggested_recipe': None}
        return Annotation.model_validate(value)

    @staticmethod
    def write_json(path, value):
        path = Path(path)
        temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
        with temporary.open('w', encoding='utf-8') as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)

    def export(self, *, publish=True):
        with self.export_lock:
            if digest(self.manifest) != self.dataset_sha:
                raise ValueError('Manifest Wiki đã thay đổi; không export hoặc ghi đè phiên rà cũ')
            with self.database() as db:
                db.execute('BEGIN')
                progress = self.progress(db)
                selected = [(row, self.read(db, row)) for row in self.images]
            report = {'total': len(self.images), 'verified': progress['verified'],
                'reviewed': progress['reviewed'], 'all_reviewed': progress['reviewed'] == len(self.images),
                'progress': progress, 'exported': 0, 'passed': 0, 'failed': 0,
                'excluded': len(self.images) - progress['verified'], 'errors': [],
                'dataset_sha256': self.dataset_sha, 'source_splits_preserved': True,
                'label_accuracy_measured': False,
                'message': 'Validate kiểm tra schema/hash/quyền/split; không chứng minh độ chính xác nhãn.'}
            selected = [(row, draft) for row, draft in selected if self.verified(draft)]
            if not selected:
                report['message'] = 'Chưa có ảnh được bạn xác nhận cả nhãn và quyền; không tạo manifest rỗng.'
                return report
            home = self.output_dir / ('reviewer-' + self.dataset_sha[:12])
            home.mkdir(parents=True, exist_ok=True)
            marker = home / '.reviewer-owner.json'
            if marker.exists():
                if json.loads(marker.read_text()) != {'dataset_sha256': self.dataset_sha}:
                    raise ValueError('Thư mục output thuộc dataset khác; chọn output riêng')
            elif any(home.iterdir()):
                raise ValueError('Thư mục output có dữ liệu chưa xác định chủ sở hữu; không ghi đè')
            else:
                self.write_json(marker, {'dataset_sha256': self.dataset_sha})
            target = home / ('exports' if publish else 'checks') / uuid.uuid4().hex
            (target / 'media').mkdir(parents=True)
            annotations = []
            for row, draft in selected:
                try:
                    source = media_path(self.root, row.media)
                    if digest(source) != row.sha256:
                        raise ValueError('Hash ảnh gốc đã thay đổi')
                    annotation = self.annotation(row, draft)
                    destination = target / annotation.media
                    shutil.copyfile(source, destination)
                    if digest(destination) != row.sha256:
                        raise ValueError('Hash bản sao không khớp')
                    annotations.append(annotation)
                except (OSError, ValueError) as error:
                    report['errors'].append({'id': row.id, 'error': str(error)[:500]})
            report['passed'] = len(annotations)
            report['failed'] = len(report['errors'])
            annotation_path = target / 'annotations.jsonl'
            manifest_path = target / 'manifest.jsonl'
            if annotations:
                annotation_path.write_text('\n'.join(a.model_dump_json() for a in annotations) + '\n', encoding='utf-8')
                validation = import_annotations(annotation_path, target, manifest_path, preserve_splits=True)
                rows, _ = validate(manifest_path, target)
                expected = {row.id: row for row, _ in selected}
                for row in rows:
                    origin = expected[row.id]
                    if (row.source_id, row.split, row.sha256) != (origin.source_id, origin.split, origin.sha256):
                        raise ValueError('Export changed original source group/split/hash')
                report.update(annotations_path=str(annotation_path), manifest_path=str(manifest_path),
                              dataset_root=str(target), validation=validation)
            report_path = target / 'validation_report.json'
            report['validation_report_path'] = str(report_path)
            if report['failed'] == 0:
                report['exported'] = len(annotations)
                if publish:
                    self.write_json(home / 'latest.json', {**report, 'published': True})
            self.write_json(report_path, report)
            return report
