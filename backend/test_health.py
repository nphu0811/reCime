"""Health reports configuration only, without contacting remote providers."""
import unittest
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient
from backend.app import app


class HealthConfigurationTests(unittest.TestCase):
    def health_with(self, environment):
        local_engine = Mock(recipes=[{'title': 'test-only fixture'}])
        local_engine.vision_available.return_value = False
        with patch.dict('os.environ', environment, clear=True), \
                patch('backend.app.engine', return_value=local_engine), \
                patch('backend.app.requests.get', side_effect=AssertionError('Network forbidden')), \
                patch('backend.app.requests.post', side_effect=AssertionError('Network forbidden')):
            response = TestClient(app).get('/health')
        self.assertEqual(200, response.status_code)
        return response.json()

    def test_missing_or_blank_keys_report_unconfigured(self):
        for environment in ({}, {'OPENAI_API_KEY': '', 'FDC_API_KEY': ''},
                            {'OPENAI_API_KEY': '  ', 'FDC_API_KEY': '\t\n'}):
            with self.subTest(environment=environment):
                result = self.health_with(environment)
                self.assertIs(False, result['assistantAvailable'])
                self.assertIs(False, result['nutritionAvailable'])

    def test_provider_capabilities_are_independent_and_do_not_expose_keys(self):
        for environment, assistant, nutrition in (
                ({'OPENAI_API_KEY': 'test-openai-secret'}, True, False),
                ({'FDC_API_KEY': 'test-usda-secret'}, False, True),
                ({'OPENAI_API_KEY': 'test-openai-secret', 'FDC_API_KEY': 'test-usda-secret'},
                 True, True)):
            with self.subTest(assistant=assistant, nutrition=nutrition):
                result = self.health_with(environment)
                self.assertIs(assistant, result['assistantAvailable'])
                self.assertIs(nutrition, result['nutritionAvailable'])
                for key in environment.values():
                    self.assertNotIn(key, str(result))

    def test_cost_description_limits_free_api_claim_to_local_retrieval(self):
        result = self.health_with({'OPENAI_API_KEY': 'test-only-secret'})
        self.assertIn('Local retrieval: no API fee.', result['cost'])
        self.assertIn('remote OpenAI assistant may incur provider charges', result['cost'])


if __name__ == '__main__':
    unittest.main()
