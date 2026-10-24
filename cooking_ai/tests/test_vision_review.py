"""No weights, downloads or GPU required for the offline evidence contract."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image

from cooking_ai.reviewer.vision_review import (
    BLIND_PROMPT, LocalVisionReviewer, image_category, independent_label_match,
    parse_label_answer,
)


class VisionReviewTests(unittest.TestCase):
    def test_strict_label_answers(self):
        self.assertEqual(parse_label_answer("YES, a bowl of soup."), "yes")
        self.assertEqual(parse_label_answer("No. It is a car."), "no")
        self.assertEqual(parse_label_answer("It might be yes."), "uncertain")
        self.assertEqual(parse_label_answer("Yesterday was sunny"), "uncertain")
        self.assertEqual(parse_label_answer("UNCERTAIN: cannot identify"), "uncertain")

    def test_independent_match_not_generic_food_or_partial_word(self):
        self.assertTrue(independent_label_match("A bowl of pho bo soup.", "Phở bò"))
        self.assertTrue(independent_label_match("This is pickled carrots.", "Cà rốt chua", ["pickled carrots"]))
        self.assertFalse(independent_label_match("It shows soup.", "Canh khổ qua", ["soup"]))
        self.assertFalse(independent_label_match("Probably not pho bo.", "Phở bò"))
        self.assertFalse(independent_label_match("A crab soup", "rab soup"))
        self.assertTrue(independent_label_match("A carrot cake.", "Bánh cà rốt"))
        self.assertTrue(independent_label_match("A bowl of pho.", "Phở"))
        self.assertFalse(independent_label_match("A soup containing corn.", "Chè bắp"))

    def test_category_conservative_unknowns(self):
        self.assertEqual(image_category("A bowl of soup."), ("finished_dish", True))
        self.assertEqual(image_category("Raw vegetables."), ("raw_ingredients", True))
        self.assertEqual(image_category("Carrots."), ("unclear", True))
        self.assertEqual(image_category("A cartoon of soup."), ("illustration", None))
        self.assertEqual(image_category("An airplane."), ("unrelated", False))
        self.assertEqual(image_category("Unknown."), ("unclear", None))

    def test_only_pinned_audited_model_and_bounded_resources(self):
        with self.assertRaises(ValueError):
            LocalVisionReviewer(model_id="remote/new-model")
        reviewer = LocalVisionReviewer(max_new_tokens=2000, threads=100)
        self.assertEqual(reviewer.max_new_tokens, 128)
        self.assertEqual(reviewer.threads, 4)

    def test_actual_image_used_blind_before_expected_label(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "photo.png"
            Image.new("RGB", (24, 24), "red").save(path)
            reviewer = LocalVisionReviewer()
            prompts = []

            def generate(image, prompt, max_tokens):
                self.assertEqual(image.size, (24, 24))
                self.assertEqual(image.getpixel((0, 0)), (255, 0, 0))
                prompts.append(prompt)
                return "A bowl of pho bo soup." if len(prompts) == 1 else "YES, a bowl of soup."

            with patch.object(reviewer, "load"), patch.object(reviewer, "_generate", side_effect=generate):
                evidence = reviewer.review(path, "Phở bò")
            self.assertEqual(prompts[0], BLIND_PROMPT)
            self.assertNotIn("Phở bò", prompts[0])
            self.assertIn("Phở bò", prompts[1])
            self.assertTrue(evidence["model_reviewed"])
            self.assertTrue(evidence["base_model_only"])
            self.assertTrue(evidence["independent_label_match"])
            self.assertEqual(evidence["image_category"], "finished_dish")
            self.assertEqual(evidence["expected_label_match"], "yes")
            self.assertEqual(evidence["observed_ingredients"], [])
            self.assertIsNone(evidence["confidence"])
            self.assertNotIn("annotation_verified", evidence)
            self.assertEqual(len(evidence["image_sha256"]), 64)
            json.dumps(evidence)


if __name__ == "__main__":
    unittest.main()
