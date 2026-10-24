"""Conservative weak-label policy, separate from human annotation verification.

VLM answers are observations from one imperfect model, not calibrated probabilities.
In particular a prompted ``yes`` alone cannot establish a Vietnamese dish label.
"""
from collections.abc import Mapping
from copy import deepcopy


POLICY_VERSION = 'wiki-machine-review-v1'
SOURCE_CHECKS = (
    'local_hash_matches', 'catalog_hash_matches', 'source_image_embedded',
    'title_matches_source', 'license_consistent', 'attribution_present',
    'sharealike_preserved',
)
THRESHOLDS = {
    'policy_version': POLICY_VERSION,
    'required_source_checks': list(SOURCE_CHECKS),
    'required_source_check_value': True,
    'rights_uncertain_allowed': False,
    'required_image_category': 'finished_dish',
    'expected_label_match': 'yes',
    'independent_label_match_required': True,
    'prompted_yes_alone_sufficient': False,
    'score_thresholds': None,
    'similarity_is_probability': False,
    'human_verification_implied': False,
    'metadata_stage_conflict_allowed': False,
}


def classify(source, vision, duplicate=False):
    """Return a reproducible machine status without changing any annotation.

    Missing source checks must be ``None`` (or absent); only actual checked
    failures may be ``False``. ``independent_label_match`` means the unprompted
    image description names the expected dish or a known alias, before the
    expected title is supplied to the model. Raw ingredients and generic food
    descriptions are insufficient. Online/offline provenance stays in source
    evidence; this function never infers successful online verification.
    """
    source = source if isinstance(source, Mapping) else {}
    vision = vision if isinstance(vision, Mapping) else {}
    checks = source.get('checks')
    checks = checks if isinstance(checks, Mapping) else {}
    rejected, uncertain = [], []

    if duplicate is True:
        rejected.append('duplicate_media')
    for key in ('local_hash_matches', 'catalog_hash_matches'):
        if checks.get(key) is False:
            rejected.append(f'proven_hash_mismatch:{key}')
    if source.get('rights_denied') is True or source.get('permission_to_train') is False:
        rejected.append('training_rights_explicitly_denied')

    for key in SOURCE_CHECKS:
        value = checks.get(key)
        if value is not True:
            uncertain.append(f'source_check_{"failed" if value is False else "unavailable"}:{key}')
    if source.get('rights_uncertain') is not False:
        uncertain.append('rights_uncertain')
    metadata = source.get('evidence')
    metadata = metadata if isinstance(metadata, Mapping) else {}
    metadata_stage = metadata.get('metadata_food_stage')
    if metadata_stage in ('raw', 'raw_ingredients', 'illustration', 'unrelated', 'ambiguous'):
        uncertain.append(f'source_metadata_stage_conflict:{metadata_stage}')

    reviewed = vision.get('model_reviewed') is True
    description = vision.get('independent_description')
    has_description = isinstance(description, str) and bool(description.strip())
    category = vision.get('image_category')
    label_match = vision.get('expected_label_match')
    independent_match = vision.get('independent_label_match')

    # Multiple explicit, consistent negative answers are required. Mere model
    # unavailability, a vague caption, or a source/model conflict is uncertain.
    if (reviewed and has_description and category == 'unrelated'
            and vision.get('is_food') is False and label_match == 'no'):
        rejected.append('vision_consistently_non_food')
    if (reviewed and has_description and category == 'finished_dish'
            and vision.get('is_food') is True and label_match == 'no'
            and independent_match is False
            and vision.get('alternative_label_supported') is True):
        rejected.append('vision_explicitly_supports_different_dish')

    if not reviewed:
        uncertain.append('vision_not_reviewed')
    if not has_description:
        uncertain.append('independent_visual_description_unavailable')
    if vision.get('is_food') is not True:
        uncertain.append('food_not_positively_identified')
    if category != 'finished_dish':
        uncertain.append(f'image_category_not_finished_dish:{category or "unknown"}')
    if label_match != 'yes':
        uncertain.append('expected_label_not_supported')
    if independent_match is not True:
        uncertain.append('independent_dish_label_not_supported')

    if rejected:
        status, reasons = 'AUTO_REJECTED', rejected + uncertain
    elif uncertain:
        status, reasons = 'UNCERTAIN', uncertain
    else:
        status, reasons = 'AUTO_ACCEPTED', ['source_and_independent_vision_consistent_weak_label']
    return {'status': status, 'reasons': reasons, 'thresholds': deepcopy(THRESHOLDS)}
