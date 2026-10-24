import json
import tempfile
import unittest
from pathlib import Path
from cooking_ai.scripts.smoke_fixtures import create
from cooking_ai.data.manifest import validate
from cooking_ai.schema import Recipe


class DatasetTests(unittest.TestCase):
    def test_hash_rights_and_source_leakage(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = create(directory)
            rows, report = validate(manifest)
            self.assertEqual(report['splits'], {'train': 1, 'validation': 1, 'test': 1})
            self.assertFalse(report['real_accuracy_evidence'])
            values = [r.model_dump() for r in rows]
            for field, changed in [('permission_to_train', False), ('sha256', '0' * 64),
                                   ('source_id', values[1]['source_id'])]:
                original = values[0][field]
                values[0][field] = changed
                manifest.write_text('\n'.join(json.dumps(v) for v in values), encoding='utf-8')
                with self.assertRaises(ValueError):
                    validate(manifest)
                values[0][field] = original

    def test_quantity_and_unknown_rules(self):
        with tempfile.TemporaryDirectory() as directory:
            rows, _ = validate(create(directory))
            value = rows[0].target.model_dump()
            value['ingredients'][0]['evidence']['source'] = 'visual'
            with self.assertRaises(ValueError):
                Recipe.model_validate(value)
            value['ingredients'][0].update(quantity=None, unit=None)
            self.assertIsNone(Recipe.model_validate(value).ingredients[0].quantity)

    def test_path_escape(self):
        from cooking_ai.data.manifest import media_path
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                media_path(directory, '../outside.png')
