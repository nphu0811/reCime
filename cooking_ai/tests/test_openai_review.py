import json
from contextlib import closing
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import Mock

from PIL import Image

from cooking_ai.reviewer.openai_review import (
    ENDPOINT, OpenAIVisionReviewer, ReviewBudgetExceeded, ReviewConsentRequired,
    ReviewProviderError, _independent_title_match,
)


def provider_response(value, *, status="completed", input_tokens=100, output_tokens=60):
    response = Mock(status_code=200)
    response.json.return_value = {
        "id": "resp_test", "model": "gpt-4.1-mini-2025-04-14", "status": status,
        "usage": {"input_tokens": input_tokens, "output_tokens": output_tokens},
        "output": [{"type": "message", "content": [{"type": "output_text", "text": json.dumps(value)}]}],
    }
    return response


BLIND = {"description": "A bowl of beef pho with visible noodles.", "predicted_dish": "Phở bò",
         "is_food": True, "image_category": "finished_dish", "visible_ingredients": ["noodles"],
         "uncertainty": ["Hidden amounts unknown"]}
LABEL = {"expected_label_match": "yes", "evidence": "A bowl of pho is visible.", "uncertainty": []}


class OpenAIReviewTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.image = self.root / "photo.png"
        Image.new("RGB", (64, 64), "red").save(self.image)
        self.transport = Mock()
        self.transport.post.side_effect = [provider_response(BLIND), provider_response(LABEL)]

    def reviewer(self, **extra):
        options = dict(consent=True, approval_receipt="user-approved-fixture-only",
                       ledger_path=self.root / "ledger.sqlite3", transport=self.transport)
        options.update(extra)
        return OpenAIVisionReviewer("fixture-never-real-secret", **options)

    def test_no_consent_no_requests_or_ledger(self):
        for options in [{"consent": False}, {"consent": True, "approval_receipt": ""}, {"consent": "yes"}]:
            with self.assertRaises(ReviewConsentRequired):
                self.reviewer(**options)
        self.transport.post.assert_not_called()
        self.assertFalse((self.root / "ledger.sqlite3").exists())

    def test_blind_first_fixed_endpoint_no_storage_and_weak_evidence(self):
        reviewer = self.reviewer()
        evidence = reviewer.review(self.image, "Phở bò")
        self.assertEqual(self.transport.post.call_count, 2)
        first, second = self.transport.post.call_args_list
        self.assertEqual(first.args, (ENDPOINT,))
        self.assertEqual(first.kwargs["timeout"], 55)
        self.assertFalse(first.kwargs["allow_redirects"])
        payload = first.kwargs["json"]
        self.assertFalse(payload["store"])
        self.assertEqual(payload["max_output_tokens"], 512)
        self.assertTrue(payload["text"]["format"]["strict"])
        self.assertNotIn("Phở bò", json.dumps(payload, ensure_ascii=False))
        self.assertIn("Phở bò", json.dumps(second.kwargs["json"], ensure_ascii=False))
        self.assertTrue(evidence["independent_label_match"])
        self.assertEqual(evidence["observed_ingredients"], [])
        self.assertEqual(evidence["model_suggested_visible_ingredients"], ["noodles"])
        self.assertIsNone(evidence["confidence"])
        self.assertNotIn("annotation_verified", evidence)
        self.assertNotIn("fixture-never-real-secret", (self.root / "ledger.sqlite3").read_bytes().decode("latin1"))
        cost = (100 * 400 + 60 * 1600) * 2 / 1e9
        self.assertAlmostEqual(reviewer.budget_status()["known_usage_usd"], cost)

    def test_resume_reuses_completed_requests_shared_budget(self):
        original = self.reviewer()
        first = original.review(self.image, "Phở bò")
        reopened = self.reviewer()
        second = reopened.review(self.image, "Phở bò")
        self.assertEqual(self.transport.post.call_count, 2)
        self.assertEqual(first["predicted_dish"], second["predicted_dish"])
        self.assertEqual(reopened.budget_status()["request_count"], 2)

    def test_budget_prevents_connection(self):
        reviewer = self.reviewer(budget_usd=0.000001)
        with self.assertRaises(ReviewBudgetExceeded):
            reviewer.review(self.image, "Phở bò")
        self.transport.post.assert_not_called()

    def test_timeout_keeps_reservation_no_retry_even_after_restart(self):
        self.transport.post.side_effect = TimeoutError("Bearer fixture-never-real-secret must not leak")
        reviewer = self.reviewer()
        with self.assertRaisesRegex(ReviewProviderError, "reservation retained") as failure:
            reviewer.review(self.image, "Phở bò")
        self.assertNotIn("fixture-never-real-secret", str(failure.exception))
        before = reviewer.budget_status()
        self.assertGreater(before["spent_or_reserved_usd"], 0)
        self.assertEqual(before["unresolved_requests"], 1)
        with self.assertRaisesRegex(ReviewProviderError, "automatic paid retry refused"):
            self.reviewer().review(self.image, "Phở bò")
        self.assertEqual(self.transport.post.call_count, 1)
        self.assertEqual(before["spent_or_reserved_usd"], reviewer.budget_status()["spent_or_reserved_usd"])

    def test_budget_cannot_be_raised_same_approval(self):
        self.reviewer(budget_usd=0.01)
        with self.assertRaises(ReviewBudgetExceeded):
            self.reviewer(budget_usd=1.0)
        self.transport.post.assert_not_called()

    def test_strict_json_invalid_boolean_rejected_without_second_call(self):
        bad = dict(BLIND, is_food="true")
        self.transport.post.side_effect = [provider_response(bad)]
        reviewer = self.reviewer()
        with self.assertRaises(ReviewProviderError):
            reviewer.review(self.image, "Phở bò")
        self.assertEqual(self.transport.post.call_count, 1)
        self.assertEqual(reviewer.budget_status()["unresolved_requests"], 1)

    def test_yes_cannot_replace_exact_independent_title(self):
        self.transport.post.side_effect = [provider_response(dict(BLIND, predicted_dish="Some Phở bò or chicken soup")),
                                           provider_response(LABEL)]
        evidence = self.reviewer().review(self.image, "Phở bò")
        self.assertEqual(evidence["expected_label_match"], "yes")
        self.assertFalse(evidence["independent_label_match"])

    def test_exact_leading_dish_with_explanatory_parenthetical(self):
        self.assertTrue(_independent_title_match("chè bắp (Vietnamese sweet corn dessert)", "Chè bắp"))
        self.assertTrue(_independent_title_match("bánh su kem (cream puffs)", "Bánh su kem"))
        self.assertTrue(_independent_title_match("cream puffs (French choux pastry)", "Bánh su kem"))

    def test_tentative_or_alternative_parenthetical_stays_uncertain(self):
        for predicted in ["chè bắp (possibly sweet corn dessert)", "chè bắp (corn or rice dessert)",
                          "chè bắp (unknown variety)", "chè bắp (chè bắp hoặc chè đậu)",
                          "chè bắp (chè bắp hay chè đậu)"]:
            self.assertFalse(_independent_title_match(predicted, "Chè bắp"), predicted)

    def test_parenthetical_does_not_allow_partial_or_reordered_names(self):
        self.assertFalse(_independent_title_match("bánh (cream puffs)", "Bánh su kem"))
        self.assertFalse(_independent_title_match("chè bắp with rice (dessert)", "Chè bắp"))
        self.assertFalse(_independent_title_match("bắp chè (corn dessert)", "Chè bắp"))
        self.assertFalse(_independent_title_match("chè bắp (corn dessert) or chè đậu", "Chè bắp"))

    def test_input_bound_rejects_without_upload(self):
        Image.new("RGB", (641, 640)).save(self.image)
        with self.assertRaisesRegex(ValueError, "640 pixels"):
            self.reviewer().review(self.image, "Phở bò")
        self.transport.post.assert_not_called()

    def test_incomplete_response_keeps_reservation(self):
        self.transport.post.side_effect = [provider_response(BLIND, status="incomplete")]
        reviewer = self.reviewer()
        with self.assertRaises(ReviewProviderError):
            reviewer.review(self.image, "Phở bò")
        self.assertGreater(reviewer.budget_status()["spent_or_reserved_usd"], reviewer.budget_status()["known_usage_usd"])

    def test_218_request_limit_counts_all_prompts_but_allows_cache(self):
        reviewer = self.reviewer()
        reviewer.review(self.image, "Phở bò")
        with closing(sqlite3.connect(reviewer.ledger_path)) as connection:
            connection.executemany(
                "INSERT INTO requests VALUES (?,?,?,?,0,'unresolved',NULL)",
                [(f"reserved-{n}", reviewer.approval, f"other-prompt-{n}", 0) for n in range(216)])
            connection.commit()
        self.assertEqual(reviewer.budget_status()["request_count"], 218)
        reviewer.review(self.image, "Phở bò")  # Both completed stages reuse their cache.
        self.assertEqual(self.transport.post.call_count, 2)
        with self.assertRaisesRegex(ReviewBudgetExceeded, "218-request limit"):
            reviewer.review(self.image, "Another dish")
        self.assertEqual(self.transport.post.call_count, 2)


if __name__ == "__main__":
    unittest.main()
