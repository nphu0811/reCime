"""Official API mocks test audit plumbing; no fixture proves image correctness."""
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
from urllib.error import HTTPError, URLError

from cooking_ai.data.manifest import digest
from cooking_ai.reviewer.wiki_audit import WikiAuditor


class ApiResponse(io.BytesIO):
    status = 200

    def __init__(self, value):
        super().__init__(json.dumps(value).encode())


class WikiAuditTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / 'media').mkdir()
        image = self.root / 'media/image.jpg'
        image.write_bytes(b'owned technical fixture only')
        self.license = 'https://creativecommons.org/licenses/by-sa/3.0'
        self.commons = 'https://commons.wikimedia.org/wiki/File:Canh_kh%E1%BB%95_qua.JPG'
        self.row = {'id': 'viwikibooks:11277:image', 'media': 'media/image.jpg', 'sha256': digest(image),
                    'target': {'title': 'Canh khổ qua nhồi thịt'}, 'license': 'CC BY-SA 3.0',
                    'attribution': f'Ngô Trung | {self.commons} | {self.license} | Resized to <=640px',
                    'permission_evidence': f'{self.commons} | {self.license}'}
        self.catalog = {'revision': 243117, 'source': 'https://vi.wikibooks.org/w/index.php?oldid=243117',
                        'sourcePage': 'https://vi.wikibooks.org/wiki/Nấu_ăn:Canh_khổ_qua_nhồi_thịt',
                        'imageSource': self.commons, 'imageFile': 'Canh khổ qua.JPG',
                        'image': 'media/image.jpg', 'imageLicenseUrl': self.license,
                        'imageAttribution': 'Ngô Trung', 'imageModification': 'Resized to <=640px'}
        self.wiki_data = {'parse': {'title': 'Nấu ăn:Canh khổ qua nhồi thịt', 'revid': 243117,
                                  'images': ['Canh_khổ_qua.JPG'],
                                  'text': '<figure><figcaption>Thành phẩm</figcaption></figure><p>Recipe text</p>'}}
        self.commons_data = {'query': {'pages': [{'title': 'File:Canh khổ qua.JPG',
            'categories': [{'title': 'Category:Vietnamese soups'}],
            'imageinfo': [{'sha1': 'original-sha1-not-local-image-hash', 'width': 2048, 'height': 1536,
                'extmetadata': {k: {'value': v} for k, v in {
                    'LicenseUrl': self.license, 'LicenseShortName': 'CC BY-SA 3.0',
                    'Artist': '<a href="/wiki/User:Example">Ngô Trung</a>',
                    'ImageDescription': 'Cooked soup; served in a bowl', 'Restrictions': ''}.items()}}]}]}}
        self.auditor = WikiAuditor(self.root / 'cache', retries=0)

    def tearDown(self):
        self.temporary.cleanup()

    def audit(self, auditor=None):
        return (auditor or self.auditor).audit(self.row, self.catalog, self.root, self.root)

    def mock_api(self, **changes):
        values = [ApiResponse(self.wiki_data), ApiResponse(self.commons_data)]
        return patch.object(self.auditor._opener, 'open', side_effect=values, **changes)

    def test_online_metadata_preserves_rights_and_source_but_never_visual_verifies(self):
        with self.mock_api() as open_api, patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            result = self.audit()
        self.assertTrue(all(result['checks'].values()), result)
        self.assertFalse(result['rights_uncertain'])
        self.assertEqual(result['evidence']['metadata_food_stage'], 'finished')
        self.assertEqual(result['evidence']['commons_original_sha1'], 'original-sha1-not-local-image-hash')
        self.assertFalse(result['evidence']['original_hash_comparable_to_resized'])
        self.assertNotIn('annotation_verified', result)
        self.assertNotIn('ingredients', result)
        self.assertEqual(open_api.call_count, 2)
        self.assertIn('oldid=243117', open_api.call_args_list[0].args[0].full_url)
        self.assertTrue(all('/w/api.php?' in call.args[0].full_url for call in open_api.call_args_list))

    def test_offline_reuses_exact_cached_metadata_without_network(self):
        with self.mock_api(), patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            self.audit()
        offline = WikiAuditor(self.root / 'cache', online=False)
        with patch.object(offline._opener, 'open', side_effect=AssertionError('No network')):
            result = self.audit(offline)
        self.assertTrue(result['online']['commons']['cached'])
        self.assertFalse(result['online']['commons']['online_attempted'])
        self.assertTrue(result['checks']['source_image_embedded'])

    def test_http_failure_recorded_and_offline_missing_is_uncertain_not_false(self):
        with patch.object(self.auditor._opener, 'open', side_effect=HTTPError('https://commons.wikimedia.org/w/api.php', 403, 'Forbidden', {}, None)), patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            result = self.audit()
        self.assertEqual(result['online']['commons']['http_status'], 403)
        self.assertIn('HTTP 403: Forbidden', result['errors'][-1])
        self.assertIsNone(result['checks']['source_image_embedded'])
        self.assertIsNone(result['checks']['license_consistent'])
        self.assertTrue(result['rights_uncertain'])
        offline = WikiAuditor(self.root / 'emptycache', online=False)
        missing = self.audit(offline)
        self.assertEqual(missing['online']['commons']['error'], 'offline_cache_miss')
        self.assertIsNone(missing['checks']['license_consistent'])

    def test_proven_mismatches_are_false_not_unknown(self):
        self.wiki_data['parse']['images'] = ['Other.jpg']
        self.wiki_data['parse']['title'] = 'Nấu ăn:Món khác'
        self.commons_data['query']['pages'][0]['imageinfo'][0]['extmetadata']['LicenseUrl']['value'] = 'https://creativecommons.org/licenses/by/4.0/'
        with self.mock_api(), patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            result = self.audit()
        self.assertFalse(result['checks']['source_image_embedded'])
        self.assertFalse(result['checks']['title_matches_source'])
        self.assertFalse(result['checks']['license_consistent'])
        self.assertTrue(result['rights_uncertain'])

    def test_untrusted_sources_and_host_are_not_requested(self):
        self.catalog['source'] = 'https://example.org/w/index.php?oldid=243117'
        self.catalog['imageSource'] = 'https://example.org/wiki/File:Canh_khổ_qua.JPG'
        with patch.object(self.auditor._opener, 'open', side_effect=AssertionError('No network')):
            result = self.audit()
        self.assertFalse(result['online']['commons']['online_attempted'])
        self.assertIsNone(result['checks']['source_image_embedded'])
        with self.assertRaisesRegex(ValueError, 'official'):
            self.auditor.fetch('127.0.0.1', {})

    def test_raw_and_finished_cues_are_ambiguous_never_observations(self):
        self.commons_data['query']['pages'][0]['imageinfo'][0]['extmetadata']['ImageDescription']['value'] = 'Raw ingredients near cooked soup'
        with self.mock_api(), patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            result = self.audit()
        self.assertEqual(result['evidence']['metadata_food_stage'], 'ambiguous')
        self.assertIn('ingredients', result['evidence']['metadata_raw_cues'])

    def test_local_hash_failure_is_not_masked_by_valid_online_license(self):
        (self.root / 'media/image.jpg').write_bytes(b'tampered fixture')
        with self.mock_api(), patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            result = self.audit()
        self.assertFalse(result['checks']['local_hash_matches'])
        self.assertFalse(result['checks']['catalog_hash_matches'])

    def test_invalid_payload_and_retries_are_bounded(self):
        self.auditor.retries = 1
        with patch.object(self.auditor._opener, 'open', side_effect=URLError('blocked network')) as request, patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            result = self.auditor.fetch('commons.wikimedia.org', {'action': 'query'})
        self.assertFalse(result['ok'])
        self.assertEqual(request.call_count, 2)
        self.assertEqual(len(result['attempts']), 2)
        self.assertIn('blocked network', result['error'])

    def test_older_file_versions_continuation_does_not_mean_categories_truncated(self):
        self.commons_data['continue'] = {'iistart': '2013-01-04T13:50:06Z', 'continue': '||categories'}
        with self.mock_api(), patch('cooking_ai.reviewer.wiki_audit.time.sleep'):
            result = self.audit()
        self.assertFalse(result['rights_uncertain'])
        self.assertNotIn('Commons categories truncated', ' '.join(result['rights_reasons']))


if __name__ == '__main__':
    unittest.main()
