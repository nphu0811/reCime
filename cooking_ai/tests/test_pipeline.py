import unittest
from unittest.mock import patch
from cooking_ai.inference.pipeline import openai, strict_schema, RecipeSchemaValidator
from cooking_ai.schema import Recipe


class ProviderTests(unittest.TestCase):
    def test_structured_response_and_untrusted_media_prompt(self):
        value = {'is_cooking': False, 'title': None, 'language': 'vi', 'ingredients': [],
                 'steps': [], 'uncertainty': ['Không có nội dung nấu ăn'], 'suggested_recipe': None}
        with patch.dict('os.environ', {'OPENAI_API_KEY': 'fixture-secret'}), patch('requests.post') as post:
            post.return_value.status_code = 200
            post.return_value.json.return_value = {'output': [{'content': [{'type': 'output_text', 'text': __import__('json').dumps(value)}]}]}
            self.assertFalse(openai([], '').is_cooking)
            payload = post.call_args.kwargs['json']
            self.assertFalse(payload['store'])
            self.assertNotIn('fixture-secret', str(payload))
            schema = payload['text']['format']['schema']
            self.assertEqual(set(schema['required']), set(schema['properties']))
            self.assertFalse(schema['additionalProperties'])
            for definition in schema['$defs'].values():
                self.assertEqual(set(definition['required']), set(definition['properties']))

    def test_wrong_quantity_and_unit_rejected_despite_real_quote(self):
        evidence = {'source': 'audio', 'text': '100 g gạo', 'start': 1, 'end': 2}
        value = {'is_cooking': True, 'title': 'Cơm', 'language': 'vi',
                 'ingredients': [{'name': 'gạo', 'quantity': 100, 'unit': 'g', 'evidence': evidence}],
                 'steps': [], 'uncertainty': [], 'suggested_recipe': None}
        timeline = [evidence]
        validator = RecipeSchemaValidator()
        validator.validate(Recipe.model_validate(value), timeline, 3)
        value['ingredients'][0]['quantity'] = 200
        with self.assertRaises(ValueError):
            validator.validate(Recipe.model_validate(value), timeline, 3)
        value['ingredients'][0].update(quantity=100, unit='kg')
        with self.assertRaises(ValueError):
            validator.validate(Recipe.model_validate(value), timeline, 3)
