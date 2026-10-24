import os
import unittest
from unittest.mock import Mock, patch
from fastapi.testclient import TestClient
from backend.app import app
from backend.nutrition import estimate


class ProviderContractTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.payload = {'text': 'Rice\nIngredients\n100 g rice\nInstructions\nCook rice.'}

    def reply(self, value):
        import json
        return Mock(status_code=200, json=lambda: {'output': [{'content': [{'type': 'output_text', 'text': json.dumps(value)}]}]})

    def test_structured_extraction_missing_key_and_valid_grounded_contract(self):
        with patch.dict(os.environ, {'OPENAI_API_KEY': ''}):
            self.assertEqual(503, self.client.post('/v1/import/ai-text', json=self.payload).status_code)
        value = {'title': 'Rice', 'ingredients': ['100 g rice'], 'steps': ['Cook rice.'], 'servings': None}
        with patch.dict(os.environ, {'OPENAI_API_KEY': 'test-only'}), patch('backend.app.requests.post', return_value=self.reply(value)) as call:
            result = self.client.post('/v1/import/ai-text', json=self.payload)
            self.assertEqual(200, result.status_code, result.text)
            self.assertIsNone(result.json()['recipe']['servings'])
            self.assertTrue(result.json()['requiresConfirmation'])
            request = call.call_args.kwargs['json']
            self.assertTrue(request['text']['format']['strict'])
            self.assertFalse(request['store'])
            self.assertEqual(self.payload['text'], request['input'])

    def test_invalid_ai_schema_rate_limit_and_timeout_are_errors(self):
        from requests.exceptions import Timeout
        with patch.dict(os.environ, {'OPENAI_API_KEY': 'test-only'}):
            for response in (self.reply({'title': 'Rice', 'ingredients': [], 'steps': [], 'servings': -1}),
                             self.reply({'title': 'Rice', 'ingredients': [], 'steps': [], 'servings': None, 'invented': True}),
                             Mock(status_code=429)):
                with patch('backend.app.requests.post', return_value=response):
                    self.assertIn(self.client.post('/v1/import/ai-text', json=self.payload).status_code, (429, 502))
            with patch('backend.app.requests.post', side_effect=Timeout):
                self.assertEqual(502, self.client.post('/v1/import/ai-text', json=self.payload).status_code)

    def test_discover_paginates_normalizes_accents_and_has_real_provenance(self):
        first = self.client.get('/v1/discover', params={'q': 'bun bo', 'limit': 1}).json()
        self.assertGreater(first['total'], 0)
        self.assertEqual(1, len(first['recipes']))
        self.assertTrue(first['recipes'][0]['source'].startswith('https://'))
        self.assertIn('CC BY-SA', first['recipes'][0]['license'])
        all_page = self.client.get('/v1/discover', params={'offset': 0, 'limit': 2}).json()
        next_page = self.client.get('/v1/discover', params={'offset': all_page['nextOffset'], 'limit': 2}).json()
        self.assertNotEqual(all_page['recipes'][0]['catalogKey'], next_page['recipes'][0]['catalogKey'])
        self.assertEqual([], self.client.get('/v1/discover?q=zxqv-unknown').json()['recipes'])
        self.assertEqual(422, self.client.get('/v1/discover?limit=999').status_code)

    def test_nutrition_does_not_confuse_kilojoules_or_fill_missing_macros(self):
        food = {'fdcId': 100, 'description': 'Unit fixture', 'foodNutrients': [
            {'nutrientName': 'Energy', 'unitName': 'kJ', 'value': 400},
            {'nutrientName': 'Protein', 'unitName': 'g', 'value': 2},
            {'nutrientName': 'Total lipid (fat)', 'unitName': 'g', 'value': 1},
            {'nutrientName': 'Carbohydrate, by difference', 'unitName': 'g', 'value': 20}]}
        with patch('backend.nutrition.requests.get', return_value=Mock(status_code=200, json=lambda: {'foods': [food]})) as request:
            result = estimate('100 g rice\n50 g rice', 2, key='test-only')
            self.assertFalse(result['complete'])
            self.assertIsNone(result['perServing'])
            self.assertEqual(1, request.call_count)
            food['foodNutrients'].append({'nutrientName': 'Energy', 'unitName': 'kcal', 'value': 100})
            result = estimate('100 g rice', 2, key='test-only')
            self.assertEqual(50, result['perServing']['calories'])
            self.assertEqual('candidate-requires-user-confirmation', result['matched'][0]['matchStatus'])

    def test_production_provider_auth_and_explicit_social_capabilities(self):
        with patch.dict(os.environ, {'APP_ENV': 'production', 'OPENAI_API_KEY': 'test-only'}), \
                patch('backend.app.requests.post', side_effect=AssertionError('Unauthenticated network call')):
            self.assertEqual(401, self.client.post('/v1/import/ai-text', json=self.payload).status_code)
        providers = self.client.get('/v1/import/providers').json()['providers']
        self.assertFalse(next(p for p in providers if p['provider'] == 'TikTok')['videoExtraction'])
        self.assertEqual('provider-unsupported', next(p for p in providers if p['provider'] == 'YouTube')['status'])

    def test_chunked_body_limit_is_enforced_before_endpoint(self):
        import asyncio
        from backend.hardening import RequestGuard
        messages = []
        async def endpoint(scope, receive, send):
            raise AssertionError('Oversized body reached endpoint')
        async def receive():
            return {'type': 'http.request', 'body': b'x' * (13 * 1024 * 1024), 'more_body': False}
        async def send(message):
            messages.append(message)
        asyncio.run(RequestGuard(endpoint)({'type': 'http', 'path': '/test', 'method': 'POST', 'headers': [], 'client': ('fixture', 0)}, receive, send))
        self.assertEqual(413, messages[0]['status'])

    def test_auth_validation_never_echoes_rejected_password(self):
        password = 'private!'
        result = self.client.post('/v1/auth/register', json={'email': 'qa@example.invalid', 'password': password})
        self.assertEqual(422, result.status_code)
        self.assertNotIn(password, result.text)
        for error in result.json()['detail']:
            self.assertNotIn('input', error)


if __name__ == '__main__':
    unittest.main()
