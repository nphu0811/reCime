"""Context filtering contracts without loading torch, transformers, or a model."""
from contextlib import redirect_stderr, redirect_stdout
import hashlib
import importlib.util
from io import StringIO
import json
from pathlib import Path
import sys
import tempfile
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from cooking_ai.data.manifest import validate
from cooking_ai.scripts.smoke_fixtures import create


class TrainingSubsetTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.manifest = create(self.root)
        rows, _ = validate(self.manifest)
        # First training example requires 3000 tokens in this synthetic batcher.
        long_image = rows[0].model_dump()
        long_image['text'] = 'Untruncated long context fixture. ' * 100
        short_text = rows[0].model_dump()
        short_text.update(id='short-train-text', source_id='short-self-authored-source',
                          kind='text', media=None, text='Short, unchanged replacement source text.')
        short_text['sha256'] = hashlib.sha256(short_text['text'].encode()).hexdigest()
        self.values = [long_image, short_text, rows[1].model_dump(), rows[2].model_dump()]
        self.manifest.write_text(''.join(json.dumps(value) + '\n' for value in self.values), encoding='utf-8')
        self.source_bytes = self.manifest.read_bytes()
        self.processor = SimpleNamespace(image_processor=SimpleNamespace(do_image_splitting=True))
        self.fetch = Mock(return_value=self.processor)
        self.batch = Mock(side_effect=self.fake_batch)
        transformer_stub = ModuleType('transformers')
        transformer_stub.AutoProcessor = SimpleNamespace(from_pretrained=self.fetch)
        trainer_stub = ModuleType('cooking_ai.training.train')
        trainer_stub.batch = self.batch
        script = Path(__file__).resolve().parents[1] / 'scripts' / 'training_subset.py'
        spec = importlib.util.spec_from_file_location('_recime_subset_context_test', script)
        self.module = importlib.util.module_from_spec(spec)
        # Only this isolated script import sees the lightweight stubs.
        with patch.dict(sys.modules, {'transformers': transformer_stub,
                                     'cooking_ai.training.train': trainer_stub}):
            spec.loader.exec_module(self.module)

    @staticmethod
    def fake_batch(processor, row, root, device, max_length):
        if row.id == 'fixture-0' and max_length < 3000:
            raise ValueError('Synthetic example exceeds configured context; do not truncate')
        return {'synthetic_plumbing_only': True}

    def invoke(self, *extra):
        output = self.root / 'selected.jsonl'
        argv = ['training_subset', '--manifest', str(self.manifest), '--output', str(output),
                '--train', '1', '--validation', '1', '--test', '1', *extra]
        with patch.object(sys, 'argv', argv), redirect_stdout(StringIO()):
            self.module.main()
        rows, _ = validate(output)
        quality = json.loads(output.with_suffix('.quality.json').read_text(encoding='utf-8'))
        return rows, quality

    def test_configured_context_excludes_long_example_and_selects_intact_replacement(self):
        rows, report = self.invoke('--max-length', '2048')
        self.assertEqual([row.id for row in rows], ['short-train-text', 'fixture-1', 'fixture-2'])
        self.assertEqual(report['excluded_context_ids'], ['fixture-0'])
        self.assertEqual(report['max_length'], 2048)
        self.assertEqual(report['modalities'], {
            'train': {'image': 0, 'text': 1},
            'validation': {'image': 1, 'text': 0},
            'test': {'image': 1, 'text': 0},
        })
        self.assertTrue(report['pilot_only'])
        self.assertEqual(self.batch.call_count, 4)
        self.assertTrue(all(call.args[-1] == 2048 for call in self.batch.call_args_list))
        self.assertEqual(rows[0].text, self.values[1]['text'])
        self.assertEqual(rows[0].target.model_dump(), self.values[1]['target'])
        self.assertEqual(self.manifest.read_bytes(), self.source_bytes)
        self.assertFalse(self.processor.image_processor.do_image_splitting)
        self.assertTrue(self.fetch.call_args.kwargs['local_files_only'])

    def test_default_context_4096_keeps_the_original_long_example(self):
        rows, report = self.invoke()
        self.assertEqual([row.id for row in rows], ['fixture-0', 'fixture-1', 'fixture-2'])
        self.assertEqual(report['excluded_context_ids'], [])
        self.assertEqual(report['max_length'], 4096)
        self.assertEqual(report['modalities']['train'], {'image': 1, 'text': 0})
        self.assertEqual(rows[0].text, self.values[0]['text'])
        self.assertEqual(self.batch.call_count, 3)
        self.assertTrue(all(call.args[-1] == 4096 for call in self.batch.call_args_list))
        self.assertEqual(self.manifest.read_bytes(), self.source_bytes)

    def test_nonpositive_context_is_rejected_before_model_or_dataset_fetch(self):
        for length in ('-1', '0'):
            with self.subTest(length=length):
                argv = ['training_subset', '--manifest', str(self.manifest),
                        '--output', str(self.root / 'never-created.jsonl'), '--max-length', length]
                with patch.object(sys, 'argv', argv), redirect_stderr(StringIO()), \
                        patch.object(self.module, 'validate', side_effect=AssertionError('No dataset access')), \
                        self.assertRaises(SystemExit) as error:
                    self.module.main()
                self.assertEqual(error.exception.code, 2)
        self.fetch.assert_not_called()
        self.batch.assert_not_called()
        self.assertFalse((self.root / 'never-created.jsonl').exists())


if __name__ == '__main__':
    unittest.main()
