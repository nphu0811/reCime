"""Bounded, metadata-only Wikimedia provenance checks; no media is downloaded.

Metadata supports source association, never visual ingredient/quantity evidence or
human confirmation. Commons file licensing is checked per file, not per website.
"""
import hashlib
import html
import json
import os
import re
import threading
import time
import unicodedata
import uuid
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, unquote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from cooking_ai.data.manifest import digest, media_path
from cooking_ai.scripts.from_catalog import allowed_license

API_HOSTS = {'vi.wikibooks.org', 'commons.wikimedia.org'}
USER_AGENT = 'reCimeDatasetAudit/1.0 (https://github.com/nphu0811/reCime; metadata-only local dataset audit)'


def plain(value):
    return html.unescape(re.sub('<[^>]*>', ' ', str(value or ''))).strip()


def normalized(value):
    value = unicodedata.normalize('NFC', plain(unquote(str(value or '')))).casefold()
    return re.sub(r'\s+', ' ', value.replace('_', ' ')).strip()


def license_url(value):
    parsed = urlsplit(str(value or '').strip())
    if parsed.scheme not in ('http', 'https') or parsed.hostname != 'creativecommons.org':
        return None
    return 'https://creativecommons.org' + parsed.path.rstrip('/')


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never follow metadata redirects to arbitrary hosts.


class _ArticleText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.text, self.captions, self._caption = [], [], 0

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        is_caption = tag == 'figcaption' or 'thumbcaption' in attrs.get('class', '').split()
        if is_caption:
            self._caption = 1
        elif self._caption and tag not in ('br', 'img', 'hr', 'input', 'meta', 'link'):
            self._caption += 1

    def handle_endtag(self, tag):
        if self._caption and tag not in ('br', 'img', 'hr', 'input', 'meta', 'link'):
            self._caption -= 1

    def handle_data(self, value):
        if value.strip():
            self.text.append(value.strip())
            if self._caption:
                self.captions.append(value.strip())


class WikiAuditor:
    def __init__(self, cache_dir, online=True, timeout=12, retries=1, min_interval=1.0):
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.online = bool(online)
        self.timeout = min(30.0, max(1.0, float(timeout)))
        self.retries = min(2, max(0, int(retries)))
        self.min_interval = max(1.0, float(min_interval))
        self._last_request = 0.0
        self._rate_lock = threading.Lock()
        self._opener = build_opener(_NoRedirect())

    def fetch(self, host, params):
        """Public API response plus cache/error provenance; only fixed hosts allowed."""
        if host not in API_HOSTS:
            raise ValueError('Only official Wikibooks/Commons API hosts are permitted')
        params = {**params, 'format': 'json', 'formatversion': '2', 'maxlag': '5'}
        key = hashlib.sha256(json.dumps([host, params], sort_keys=True).encode()).hexdigest()
        path = self.cache_dir / (key + '.json')
        cached = None
        if path.is_file():
            try:
                cached = json.loads(path.read_text(encoding='utf-8'))
                if cached.get('host') != host or cached.get('params') != params:
                    cached = None
            except (OSError, ValueError, AttributeError):
                pass
        if cached and cached.get('ok'):
            return {**cached, 'cached': True, 'online_attempted': False}
        if not self.online:
            if cached:
                return {**cached, 'cached': True, 'online_attempted': False}
            return {'ok': False, 'host': host, 'params': params, 'cached': False,
                    'online_attempted': False, 'http_status': None, 'error': 'offline_cache_miss',
                    'attempts': [], 'data': None, 'checked_at': None}
        url = 'https://' + host + '/w/api.php?' + urlencode(params)
        record = {'host': host, 'params': params, 'ok': False, 'cached': False,
                  'online_attempted': True, 'http_status': None, 'data': None,
                  'checked_at': datetime.now(timezone.utc).isoformat(), 'attempts': []}
        for attempt in range(self.retries + 1):
            with self._rate_lock:
                delay = self.min_interval - (time.monotonic() - self._last_request)
                if delay > 0:
                    time.sleep(delay)
                self._last_request = time.monotonic()
            try:
                request = Request(url, headers={'User-Agent': USER_AGENT, 'Accept': 'application/json'})
                with self._opener.open(request, timeout=self.timeout) as response:
                    record['http_status'] = response.status
                    payload = response.read(3 * 1024 * 1024 + 1)
                if len(payload) > 3 * 1024 * 1024:
                    raise ValueError('response_exceeds_3MiB')
                data = json.loads(payload)
                if not isinstance(data, dict):
                    raise ValueError('response_is_not_json_object')
                if 'error' in data:
                    error = data['error']
                    record['error'] = 'mediawiki_api_error:' + json.dumps(error, ensure_ascii=False)
                    record['attempts'].append({'http_status': record['http_status'], 'error': record['error']})
                    if isinstance(error, dict) and error.get('code') == 'maxlag' and attempt < self.retries:
                        continue
                    break
                record.update(ok=True, data=data, error=None)
                record['attempts'].append({'http_status': record['http_status'], 'error': None})
                break
            except HTTPError as exc:
                record.update(http_status=exc.code, error=f'HTTP {exc.code}: {exc.reason}')
                record['attempts'].append({'http_status': exc.code, 'error': record['error']})
                if exc.code not in (429, 500, 502, 503, 504):
                    break
            except (URLError, TimeoutError, OSError, ValueError) as exc:
                record['error'] = f'{type(exc).__name__}: {exc}'
                record['attempts'].append({'http_status': record['http_status'], 'error': record['error']})
        temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
        temporary.write_text(json.dumps(record, ensure_ascii=False), encoding='utf-8')
        os.replace(temporary, path)
        return record

    def audit(self, row, catalog_record, dataset_root, catalog_root='app/src/main/assets/catalog'):
        """Return tri-state checks: False=mismatch; None=unavailable, never rejection."""
        row = row.model_dump() if hasattr(row, 'model_dump') else row
        catalog = catalog_record or {}
        checks = dict.fromkeys(('local_hash_matches', 'catalog_hash_matches', 'source_image_embedded',
                                'title_matches_source', 'license_consistent', 'attribution_present',
                                'sharealike_preserved'))
        errors = []
        try:
            local = media_path(dataset_root, row.get('media') or '')
            local_sha = digest(local)
            checks['local_hash_matches'] = local_sha == row['sha256']
        except (OSError, ValueError, KeyError) as exc:
            local_sha = None
            errors.append('local_media:' + str(exc))
        try:
            catalog_image = media_path(catalog_root, catalog.get('image') or '')
            checks['catalog_hash_matches'] = digest(catalog_image) == row.get('sha256')
        except (OSError, ValueError) as exc:
            errors.append('catalog_media:' + str(exc))

        revision = catalog.get('revision')
        source = urlsplit(catalog.get('source') or '')
        article_valid = (source.scheme == 'https' and source.hostname == 'vi.wikibooks.org'
                         and isinstance(revision, int) and revision > 0
                         and parse_qs(source.query).get('oldid') == [str(revision)])
        if article_valid:
            wiki = self.fetch('vi.wikibooks.org', {'action': 'parse', 'oldid': str(revision),
                                                  'prop': 'text|images|revid|displaytitle'})
        else:
            wiki = {'ok': False, 'error': 'missing_or_untrusted_wikibooks_revision', 'cached': False,
                    'online_attempted': False, 'http_status': None, 'data': None}
        file_name = str(catalog.get('imageFile') or '')
        file_source = urlsplit(catalog.get('imageSource') or '')
        source_file_name = unquote(file_source.path.removeprefix('/wiki/File:'))
        commons_valid = file_source.scheme == 'https' and file_source.hostname == 'commons.wikimedia.org' and file_source.path.startswith('/wiki/File:') and normalized(source_file_name) == normalized(file_name) and bool(file_name)
        if commons_valid:
            commons = self.fetch('commons.wikimedia.org', {'action': 'query', 'prop': 'imageinfo|categories',
                  'titles': 'File:' + file_name, 'iiprop': 'url|sha1|size|mime|extmetadata|timestamp',
                  'cllimit': 'max', 'redirects': '1'})
        else:
            commons = {'ok': False, 'error': 'missing_or_untrusted_commons_file', 'cached': False,
                       'online_attempted': False, 'http_status': None, 'data': None}

        article, commons_info, categories, captions, article_text = {}, {}, [], [], ''
        if wiki['ok']:
            article = wiki['data'].get('parse') or {}
            if article and article.get('revid') == revision:
                checks['source_image_embedded'] = normalized(file_name) in {normalized(x) for x in article.get('images', [])}
                title = re.sub(r'^(?:nấu ăn|cookbook)\s*:\s*', '', normalized(article.get('title')))
                checks['title_matches_source'] = title == normalized(row.get('target', {}).get('title'))
                article_html = article.get('text', '')
                if isinstance(article_html, dict):
                    article_html = article_html.get('*', '')
                parser = _ArticleText()
                parser.feed(article_html)
                article_text = ' '.join(parser.text)[:40000]
                captions = parser.captions[:100]
            else:
                errors.append('wikibooks:missing_or_unmatched_revision_payload')
        if commons['ok']:
            pages = commons['data'].get('query', {}).get('pages', [])
            if isinstance(pages, dict):
                pages = list(pages.values())
            page = next((p for p in pages if p.get('imageinfo')), {})
            commons_info = (page.get('imageinfo') or [{}])[0]
            categories = [x.get('title', '') for x in page.get('categories', [])]
            if not commons_info:
                errors.append('commons:missing_imageinfo_payload')

        metadata = commons_info.get('extmetadata') or {}
        meta = {key: plain(value.get('value', '')) for key, value in metadata.items() if isinstance(value, dict)}
        current_license = license_url(meta.get('LicenseUrl'))
        stored_license = license_url(catalog.get('imageLicenseUrl'))
        attribution = plain(row.get('attribution', ''))
        permission_evidence = row.get('permission_evidence', '')
        artist = normalized(meta.get('Artist'))
        reasons = []
        if commons_info and current_license:
            current_short = normalized(meta.get('LicenseShortName'))
            expected_short = normalized(row.get('license'))
            checks['license_consistent'] = (allowed_license(current_license) and current_license == stored_license
                  and current_license in permission_evidence.replace('http://', 'https://')
                  and (not current_short or current_short == expected_short))
            stored_artist = normalized(catalog.get('imageAttribution'))
            checks['attribution_present'] = bool(artist and artist not in ('unknown', 'anonymous', 'not provided', 'không rõ')
                    and stored_artist and artist in normalized(attribution) and stored_artist in normalized(attribution)
                    and normalized(catalog.get('imageSource')) in normalized(attribution))
            sharealike = '/by-sa/' in current_license
            checks['sharealike_preserved'] = (not sharealike or (
                    'by-sa' in normalized(row.get('license'))
                    and current_license in attribution.replace('http://', 'https://')
                    and bool(catalog.get('imageModification'))
                    and normalized(catalog['imageModification']) in normalized(attribution)))
        if meta.get('Restrictions', '').strip():
            reasons.append('Commons reports additional restrictions: ' + meta['Restrictions'])
        if not meta.get('LicenseUrl'):
            reasons.append('Current per-file Commons license unavailable')
        if commons.get('data') and commons['data'].get('continue', {}).get('clcontinue'):
            reasons.append('Commons categories truncated; complete category audit unavailable')
        for key in ('license_consistent', 'attribution_present', 'sharealike_preserved'):
            if checks[key] is not True:
                reasons.append(key + (':unavailable' if checks[key] is None else ':mismatch'))
        for service, result in (('wikibooks', wiki), ('commons', commons)):
            if not result['ok']:
                errors.append(service + ':' + result.get('error', 'unknown_error'))
        # Captions/descriptions/categories are weak source metadata, not image observations.
        cue_text = normalized(' '.join([file_name, meta.get('ImageDescription', ''), *categories, *captions]))
        raw_cues = [x for x in ('raw ', 'uncooked', 'ingredients', 'nguyên liệu', 'sống', 'unripe', 'preparation') if x in cue_text]
        finished_cues = [x for x in ('cooked', 'finished dish', 'served', 'món ăn', 'thành phẩm', 'đã nấu', 'ready to eat') if x in cue_text]
        stage = 'ambiguous' if raw_cues and finished_cues else 'raw' if raw_cues else 'finished' if finished_cues else 'unknown'
        return {'checks': checks, 'online': {'wikibooks': wiki, 'commons': commons},
                'rights_uncertain': bool(reasons), 'rights_reasons': reasons, 'errors': errors,
                'evidence': {'revision': revision, 'source_url': catalog.get('source'),
                  'source_page': catalog.get('sourcePage'), 'article_title': article.get('title'),
                  'embedded_images': article.get('images', []), 'article_text': article_text,
                  'captions': captions, 'commons_file': file_name, 'commons_url': catalog.get('imageSource'),
                  'commons_metadata': meta, 'categories': categories,
                  'local_resized_sha256': local_sha, 'commons_original_sha1': commons_info.get('sha1'),
                  'original_hash_comparable_to_resized': False,
                  'original_width': commons_info.get('width'), 'original_height': commons_info.get('height'),
                  'metadata_food_stage': stage, 'metadata_raw_cues': raw_cues, 'metadata_finished_cues': finished_cues,
                  'warning': 'Source metadata association is not visual verification or ingredient evidence'}}
