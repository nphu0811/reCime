"""Explicitly approved fallback for the 109 public Wiki images; never an implicit fallback."""
import argparse
import json
from pathlib import Path
from types import SimpleNamespace

from cooking_ai.data.manifest import digest
from cooking_ai.reviewer.automatic import CATALOG, OUTPUT, SOURCE_MANIFEST, run
from cooking_ai.reviewer.openai_review import DEFAULT_LEDGER, MODEL, OpenAIVisionReviewer
from cooking_ai.reviewer.store import ReviewerStore
from cooking_ai.reviewer.wiki_audit import WikiAuditor

APPROVAL = Path(OUTPUT) / 'openai-approval.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--consent', action='store_true', help='Explicitly authorize public-image upload and paid requests')
    parser.add_argument('--limit', type=int, default=0)
    args = parser.parse_args()
    if not args.consent or not APPROVAL.is_file():
        parser.error('Explicit consent and local approval record required before reading credentials or sending media')
    try:
        approval = json.loads(APPROVAL.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        parser.error('Unreadable local approval record; credentials were not read')
    if (not isinstance(approval, dict) or approval.get('approved') is not True or approval.get('model') != MODEL
            or approval.get('budget_usd') != 1 or approval.get('max_requests') != 218
            or not isinstance(approval.get('receipt'), str) or len(approval['receipt'].strip()) < 8
            or approval.get('manifest_sha256') != digest(SOURCE_MANIFEST)):
        parser.error('Approval scope does not match this public Wiki dataset/model/budget')
    if not 0 <= args.limit <= 109:
        parser.error('--limit must be 0 (all) or at most 109')
    from dotenv import dotenv_values
    import os
    credential = os.environ.get('OPENAI_API_KEY') or dotenv_values('backend/.env').get('OPENAI_API_KEY')
    reviewer = OpenAIVisionReviewer(credential, consent=True, approval_receipt=approval['receipt'],
                                    ledger_path=DEFAULT_LEDGER, budget_usd=1, model=MODEL)
    # Separate model namespace while keeping one ledger/cache and the same approval.
    implementation = digest(Path(__file__).with_name('openai_review.py'))[:12]
    output = Path(OUTPUT) / ('openai-' + implementation)
    arguments = SimpleNamespace(manifest=SOURCE_MANIFEST, catalog=CATALOG, output=str(output),
                                offline=False, limit=args.limit)
    try:
        summary = run(arguments, auditor=WikiAuditor(Path(OUTPUT) / 'api_cache'), vision=reviewer)
        budget = reviewer.budget_status()
        ReviewerStore.write_json(output / 'budget_status.json', budget)
        ReviewerStore.write_json(Path(OUTPUT) / 'openai-latest.json', {**summary, 'budget': budget})
        print(json.dumps({'budget': budget}), flush=True)
    finally:
        # Also persist billing uncertainty on interruption, without logging credentials.
        ReviewerStore.write_json(Path(OUTPUT) / 'openai-budget-status.json', reviewer.budget_status())


if __name__ == '__main__':
    main()
