"""Paid CLI approval guard tests: no credentials, network, or model calls."""
from contextlib import ExitStack, redirect_stderr, redirect_stdout
from io import StringIO
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from cooking_ai.reviewer import paid_automatic


class PaidAutomaticTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.approval_path = self.root / 'approval.json'
        self.output_path = self.root / 'output'
        self.manifest = 'synthetic/manifest.jsonl'
        self.catalog = 'synthetic/catalog.json'
        self.manifest_sha = 'a' * 64
        self.approval = {'approved': True, 'model': paid_automatic.MODEL,
                         'budget_usd': 1, 'max_requests': 218,
                         'manifest_sha256': self.manifest_sha,
                         'receipt': 'synthetic-approval-receipt'}
        self.key = 'synthetic-secret-never-a-real-key'
        self.out, self.err = StringIO(), StringIO()
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        for name, value in (('APPROVAL', self.approval_path), ('OUTPUT', str(self.output_path)),
                            ('SOURCE_MANIFEST', self.manifest), ('CATALOG', self.catalog)):
            self.stack.enter_context(patch.object(paid_automatic, name, value))
        self.stack.enter_context(patch.dict('os.environ', {}, clear=True))
        self.dotenv = Mock(return_value={'OPENAI_API_KEY': self.key})
        self.stack.enter_context(patch.dict(sys.modules, {'dotenv': SimpleNamespace(dotenv_values=self.dotenv)}))
        self.digest = self.stack.enter_context(patch.object(paid_automatic, 'digest',
            side_effect=lambda path: self.manifest_sha if str(path) == self.manifest else 'b' * 64))
        self.vision = self.stack.enter_context(patch.object(paid_automatic, 'OpenAIVisionReviewer'))
        self.vision.return_value.budget_status.return_value = {'reserved_usd': 0, 'requests': 0}
        self.auditor = self.stack.enter_context(patch.object(paid_automatic, 'WikiAuditor'))
        self.run = self.stack.enter_context(patch.object(paid_automatic, 'run',
            return_value={'processed': 3, 'total': 109, 'counts': {'AUTO_ACCEPTED': 1}}))
        self.write = self.stack.enter_context(patch.object(paid_automatic.ReviewerStore, 'write_json'))
        self.save_approval()

    def save_approval(self):
        self.approval_path.write_text(json.dumps(self.approval), encoding='utf-8')

    def invoke(self, *arguments):
        with patch.object(sys, 'argv', ['paid_automatic', *arguments]), \
                redirect_stdout(self.out), redirect_stderr(self.err):
            paid_automatic.main()

    def assert_refused_without_credentials(self, *arguments):
        with self.assertRaises(SystemExit) as error:
            self.invoke(*arguments)
        self.assertEqual(error.exception.code, 2)
        self.dotenv.assert_not_called()
        self.vision.assert_not_called()
        self.run.assert_not_called()
        self.assertNotIn(self.key, self.out.getvalue() + self.err.getvalue())

    def test_missing_consent_refuses_before_loading_dotenv_or_hashing_source(self):
        self.assert_refused_without_credentials()
        self.digest.assert_not_called()

    def test_missing_approval_refuses_even_with_consent(self):
        self.approval_path.unlink()
        self.assert_refused_without_credentials('--consent')
        self.digest.assert_not_called()

    def test_wrong_model_approval_is_refused_before_credentials(self):
        self.approval['model'] = 'unapproved-model'
        self.save_approval()
        self.assert_refused_without_credentials('--consent')

    def test_wrong_manifest_sha_approval_is_refused_before_credentials(self):
        self.approval['manifest_sha256'] = 'c' * 64
        self.save_approval()
        self.assert_refused_without_credentials('--consent')
        self.digest.assert_called_once_with(self.manifest)

    def test_wrong_budget_and_request_caps_are_refused_before_credentials(self):
        for field, value in (('budget_usd', 2), ('budget_usd', 0.5),
                             ('max_requests', 219), ('max_requests', 109)):
            with self.subTest(field=field, value=value):
                approval = self.approval.copy()
                approval[field] = value
                self.approval_path.write_text(json.dumps(approval), encoding='utf-8')
                self.assert_refused_without_credentials('--consent')

    def test_unapproved_and_invalid_limits_are_refused(self):
        self.approval['approved'] = False
        self.save_approval()
        self.assert_refused_without_credentials('--consent')
        self.approval['approved'] = True
        self.save_approval()
        for limit in ('-1', '110'):
            with self.subTest(limit=limit):
                self.assert_refused_without_credentials('--consent', '--limit', limit)

    def test_matching_approval_pilot_uses_existing_dotenv_key_after_consent(self):
        self.invoke('--consent', '--limit', '3')
        self.dotenv.assert_called_once_with('backend/.env')
        self.vision.assert_called_once_with(self.key, consent=True,
            approval_receipt=self.approval['receipt'], ledger_path=paid_automatic.DEFAULT_LEDGER,
            budget_usd=1, model=paid_automatic.MODEL)
        self.run.assert_called_once()
        arguments = self.run.call_args.args[0]
        self.assertEqual(arguments.manifest, self.manifest)
        self.assertEqual(arguments.catalog, self.catalog)
        self.assertEqual(arguments.limit, 3)
        self.assertFalse(arguments.offline)
        self.assertIs(self.run.call_args.kwargs['vision'], self.vision.return_value)
        self.assertIs(self.run.call_args.kwargs['auditor'], self.auditor.return_value)
        self.assertNotIn(self.key, self.out.getvalue() + self.err.getvalue())
        self.assertNotIn(self.key, repr(self.write.call_args_list))

    def test_shared_default_ledger_is_independent_of_output_directory(self):
        self.invoke('--consent', '--limit', '3')
        first = self.vision.call_args.kwargs['ledger_path']
        with patch.object(paid_automatic, 'OUTPUT', str(self.root / 'different-output')):
            self.invoke('--consent', '--limit', '3')
        second = self.vision.call_args.kwargs['ledger_path']
        self.assertEqual(first, paid_automatic.DEFAULT_LEDGER)
        self.assertEqual(second, paid_automatic.DEFAULT_LEDGER)
        self.assertNotEqual(self.run.call_args_list[0].args[0].output,
                            self.run.call_args_list[1].args[0].output)
        self.assertNotIn(str(self.output_path), str(second))

    def test_existing_environment_key_takes_precedence_without_dotenv_read(self):
        environment_key = 'synthetic-env-secret-never-real'
        with patch.dict('os.environ', {'OPENAI_API_KEY': environment_key}):
            self.invoke('--consent', '--limit', '3')
        self.dotenv.assert_not_called()
        self.assertEqual(self.vision.call_args.args[0], environment_key)
        self.assertNotIn(environment_key, self.out.getvalue() + self.err.getvalue())
        self.assertNotIn(environment_key, repr(self.write.call_args_list))

    def test_provider_failure_persists_budget_without_printing_key(self):
        self.run.side_effect = RuntimeError('synthetic interrupted run')
        with self.assertRaisesRegex(RuntimeError, 'synthetic interrupted run'):
            self.invoke('--consent', '--limit', '3')
        self.write.assert_called_once_with(self.output_path / 'openai-budget-status.json',
                                          {'reserved_usd': 0, 'requests': 0})
        self.assertNotIn(self.key, self.out.getvalue() + self.err.getvalue())


if __name__ == '__main__':
    unittest.main()
