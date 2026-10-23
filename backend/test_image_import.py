"""Original generated image fixtures; remote calls are always mocked."""
import base64
import io
import json
import os
import unittest
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient
from PIL import Image
from backend.app import app


class ImageImportTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.environment = patch.dict(os.environ, {'APP_ENV': 'development', 'OPENAI_API_KEY': 'test-only'})
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def fixture(self, size=(80, 40)):
        output = io.BytesIO()
        exif = Image.Exif()
        exif[274] = 6  # Rotate 90 degrees.
        exif[270] = 'private fixture metadata'
        Image.new('RGB', size, 'white').save(output, format='JPEG', exif=exif)
        return output.getvalue()

    def post(self, content):
        return self.client.post('/v1/import/ai-image', files={'photo': ('fixture.jpg', content, 'image/jpeg')})

    def reply(self, value):
        return Mock(status_code=200, json=lambda: {'output': [{'content': [{'type': 'output_text', 'text': json.dumps(value)}]}]})

    def test_oriented_metadata_free_upload_and_grounded_contract(self):
        recipe = {'title': 'Canh đậu', 'ingredients': ['200 g đậu phụ'], 'steps': ['Cho đậu vào nước.'], 'servings': 2}
        with patch('backend.app.requests.post', return_value=self.reply(recipe)) as call:
            result = self.post(self.fixture())
        self.assertEqual(200, result.status_code, result.text)
        self.assertEqual(recipe, result.json()['recipe'])
        self.assertEqual('user-supplied-image', result.json()['source'])
        self.assertTrue(result.json()['requiresConfirmation'])
        payload = call.call_args.kwargs['json']
        self.assertFalse(payload['store'])
        self.assertIn('Never infer a recipe', payload['instructions'])
        content = payload['input'][0]['content']
        self.assertEqual('input_text', content[0]['type'])
        pixels = base64.b64decode(content[1]['image_url'].split(',', 1)[1])
        with Image.open(io.BytesIO(pixels)) as image:
            self.assertEqual((40, 80), image.size)
            self.assertEqual({}, dict(image.getexif()))
        self.assertNotIn(b'private fixture metadata', pixels)

    def test_missing_key_bad_image_oversize_and_large_dimensions_do_not_call_provider(self):
        with patch('backend.app.requests.post', side_effect=AssertionError('Unexpected remote call')):
            with patch.dict(os.environ, {'OPENAI_API_KEY': ''}):
                self.assertEqual(503, self.post(self.fixture()).status_code)
            self.assertEqual(422, self.post(b'not an image').status_code)
            self.assertEqual(413, self.post(b'x' * (8 * 1024 * 1024 + 1)).status_code)
            # Pillow reads dimensions before decoding this sparse PNG.
            large = io.BytesIO()
            Image.new('1', (6500, 6500)).save(large, format='PNG')
            self.assertEqual(422, self.post(large.getvalue()).status_code)

    def test_transparent_recipe_page_is_flattened_to_white(self):
        fixture = io.BytesIO()
        Image.new('RGBA', (20, 20), (0, 0, 0, 0)).save(fixture, format='PNG')
        value = {'title': 'Soup', 'ingredients': [], 'steps': [], 'servings': None}
        with patch('backend.app.requests.post', return_value=self.reply(value)) as call:
            self.assertEqual(200, self.post(fixture.getvalue()).status_code)
        url = call.call_args.kwargs['json']['input'][0]['content'][1]['image_url']
        with Image.open(io.BytesIO(base64.b64decode(url.split(',', 1)[1]))) as image:
            self.assertEqual((255, 255, 255), image.getpixel((10, 10)))

    def test_empty_recipe_refusal_malformed_provider_and_timeout_are_real_errors(self):
        import requests
        empty = {'title': '', 'ingredients': [], 'steps': [], 'servings': None}
        for response, expected in [(self.reply(empty), 422),
                                   (Mock(status_code=200, json=lambda: []), 502),
                                   (Mock(status_code=200, json=lambda: {'output': [None]}), 502),
                                   (Mock(status_code=200, json=lambda: {'output': [{'content': [{'type': 'refusal'}]}]}), 502),
                                   (Mock(status_code=429), 429)]:
            with self.subTest(expected=expected), patch('backend.app.requests.post', return_value=response):
                self.assertEqual(expected, self.post(self.fixture()).status_code)
        with patch('backend.app.requests.post', side_effect=requests.Timeout):
            self.assertEqual(502, self.post(self.fixture()).status_code)

    def test_production_requires_login_before_upload(self):
        with patch.dict(os.environ, {'APP_ENV': 'production'}), patch('backend.app.requests.post') as call:
            self.assertEqual(401, self.post(self.fixture()).status_code)
            call.assert_not_called()


if __name__ == '__main__':
    unittest.main()
