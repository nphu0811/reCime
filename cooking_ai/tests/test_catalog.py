import tempfile
import unittest
from pathlib import Path
from cooking_ai.scripts.from_catalog import export
from cooking_ai.data.manifest import validate


class CatalogTests(unittest.TestCase):
    def test_existing_wiki_provenance_and_no_hidden_visual_recipe(self):
        with tempfile.TemporaryDirectory() as directory:
            catalog = Path(__file__).resolve().parents[2] / 'app/src/main/assets/catalog'
            report = export(catalog, directory)
            rows, _ = validate(Path(directory) / 'manifest.jsonl')
            self.assertGreater(report['image_records'], 50)
            self.assertGreater(report['text_records'], 100)
            self.assertFalse(report['manual_annotation_verified'])
            for row in rows:
                self.assertTrue(row.permission_to_train)
                self.assertIn('http', row.permission_evidence)
                if row.kind == 'image':
                    self.assertEqual(row.target.ingredients, [])
                    self.assertEqual(row.target.steps, [])
                    self.assertEqual(row.text, '')
            for split in ('train', 'validation', 'test'):
                self.assertTrue(any(r.kind == 'image' and r.split == split for r in rows))
