import tempfile
import unittest
from cooking_ai.scripts.smoke_fixtures import create
from cooking_ai.data.manifest import validate
from cooking_ai.evaluation.evaluate import evaluate


class EvaluationTests(unittest.TestCase):
    def test_missing_and_invalid_predictions_reduce_scores(self):
        with tempfile.TemporaryDirectory() as directory:
            rows, _ = validate(create(directory))
            row = next(r for r in rows if r.split == 'test')
            valid = evaluate(rows, [{'id': row.id, 'recipe': row.target.model_dump()}])
            self.assertEqual(valid['ingredient_f1'], 1)
            self.assertFalse(valid['real_accuracy_evidence'])
            self.assertEqual(evaluate(rows, [])['json_validity'], 0)
            self.assertEqual(evaluate(rows, [{'id': row.id, 'recipe': {}}])['ingredient_f1'], 0)
            invalid = evaluate(rows, [{'id': row.id, 'recipe': {}, 'latency_seconds': 9, 'peak_vram_bytes': 10}])
            self.assertEqual(invalid['mean_latency_seconds'], 9)
            self.assertEqual(invalid['peak_vram_bytes'], 10)
            with self.assertRaises(ValueError):
                evaluate(rows, [{'id': 'train-id', 'recipe': None}])
