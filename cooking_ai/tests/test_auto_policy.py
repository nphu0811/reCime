"""Synthetic policy counterexamples; these do not measure real label accuracy."""
import unittest
from copy import deepcopy

from cooking_ai.reviewer.auto_policy import SOURCE_CHECKS, classify


class AutoPolicyTests(unittest.TestCase):
    def setUp(self):
        self.source = {'checks': dict.fromkeys(SOURCE_CHECKS, True),
                       'rights_uncertain': False}
        self.vision = {'model_reviewed': True, 'independent_description': 'A bowl of pho.',
                       'expected_label_match': 'yes', 'independent_label_match': True,
                       'is_food': True, 'image_category': 'finished_dish'}

    def decide(self, source=None, vision=None, **kwargs):
        return classify(self.source if source is None else source,
                        self.vision if vision is None else vision, **kwargs)

    def test_full_evidence_accepts_only_weak_label(self):
        result = self.decide()
        self.assertEqual(result['status'], 'AUTO_ACCEPTED')
        self.assertFalse(result['thresholds']['human_verification_implied'])
        self.assertFalse(result['thresholds']['similarity_is_probability'])
        self.assertIsNone(result['thresholds']['score_thresholds'])
        self.assertNotIn('annotation_verified', result)
        self.assertNotIn('ingredients', result)

    def test_every_missing_source_check_is_uncertain(self):
        for key in SOURCE_CHECKS:
            with self.subTest(key=key):
                source = deepcopy(self.source)
                source['checks'][key] = None
                self.assertEqual(self.decide(source=source)['status'], 'UNCERTAIN')

    def test_conflicting_license_and_unknown_rights_excluded(self):
        source = deepcopy(self.source)
        source['checks']['license_consistent'] = False
        source['rights_uncertain'] = True
        result = self.decide(source=source)
        self.assertEqual(result['status'], 'UNCERTAIN')
        self.assertIn('source_check_failed:license_consistent', result['reasons'])
        self.assertIn('rights_uncertain', result['reasons'])

    def test_explicit_rights_denial_is_rejected(self):
        self.assertEqual(self.decide(source={**self.source, 'rights_denied': True})['status'],
                         'AUTO_REJECTED')

    def test_proven_hash_mismatch_and_duplicate_rejected(self):
        self.assertEqual(self.decide(duplicate=True)['status'], 'AUTO_REJECTED')
        for key in ('local_hash_matches', 'catalog_hash_matches'):
            source = deepcopy(self.source)
            source['checks'][key] = False
            self.assertEqual(self.decide(source=source)['status'], 'AUTO_REJECTED')

    def test_wrong_label_needs_independent_alternative_evidence(self):
        wrong = {**self.vision, 'expected_label_match': 'no', 'independent_label_match': False}
        self.assertEqual(self.decide(vision=wrong)['status'], 'UNCERTAIN')
        wrong['alternative_label_supported'] = True
        self.assertEqual(self.decide(vision=wrong)['status'], 'AUTO_REJECTED')

    def test_raw_ingredients_are_excluded(self):
        raw = {**self.vision, 'independent_description': 'Uncooked onions and carrots.',
               'image_category': 'raw_ingredients', 'expected_label_match': 'no',
               'independent_label_match': False}
        self.assertEqual(self.decide(vision=raw)['status'], 'UNCERTAIN')

    def test_source_metadata_stage_conflict_excludes_positive_vision(self):
        for stage in ('raw', 'raw_ingredients', 'illustration', 'unrelated', 'ambiguous'):
            with self.subTest(stage=stage):
                source = {**self.source, 'evidence': {'metadata_food_stage': stage}}
                result = self.decide(source=source)
                self.assertEqual(result['status'], 'UNCERTAIN')
                self.assertIn(f'source_metadata_stage_conflict:{stage}', result['reasons'])
        for stage in ('finished', 'unknown', None):
            source = {**self.source, 'evidence': {'metadata_food_stage': stage}}
            self.assertEqual(self.decide(source=source)['status'], 'AUTO_ACCEPTED')

    def test_consistent_unrelated_image_is_rejected(self):
        unrelated = {**self.vision, 'independent_description': 'A motorbike.',
                     'image_category': 'unrelated', 'is_food': False,
                     'expected_label_match': 'no', 'independent_label_match': False}
        self.assertEqual(self.decide(vision=unrelated)['status'], 'AUTO_REJECTED')
        unrelated['is_food'] = None
        self.assertEqual(self.decide(vision=unrelated)['status'], 'UNCERTAIN')

    def test_prompted_yes_generic_food_is_not_label_verification(self):
        generic = {**self.vision, 'independent_description': 'A plate of food.',
                   'independent_label_match': None, 'confidence': 1.0}
        self.assertEqual(self.decide(vision=generic)['status'], 'UNCERTAIN')

    def test_strings_are_not_boolean_proof(self):
        self.assertEqual(self.decide(vision={**self.vision, 'model_reviewed': 'true'})['status'],
                         'UNCERTAIN')
        source = deepcopy(self.source)
        source['checks']['license_consistent'] = 1
        self.assertEqual(self.decide(source=source)['status'], 'UNCERTAIN')

    def test_missing_inputs_and_models_are_uncertain(self):
        self.assertEqual(classify(None, None)['status'], 'UNCERTAIN')
        self.assertEqual(self.decide(vision={})['status'], 'UNCERTAIN')

    def test_deterministic_without_mutating_inputs(self):
        source, vision = deepcopy(self.source), deepcopy(self.vision)
        first, second = self.decide(), self.decide()
        self.assertEqual(first, second)
        first['thresholds']['required_source_checks'].clear()
        self.assertEqual(len(self.decide()['thresholds']['required_source_checks']),
                         len(SOURCE_CHECKS))
        self.assertEqual(self.source, source)
        self.assertEqual(self.vision, vision)


if __name__ == '__main__':
    unittest.main()
