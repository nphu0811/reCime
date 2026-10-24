"""Strict held-out field metrics; exact step match is not semantic recipe validity."""
import argparse
import json
import unicodedata
from pathlib import Path
from cooking_ai.data.manifest import validate
from cooking_ai.schema import Recipe


def norm(value):
    return ' '.join(unicodedata.normalize('NFC', value or '').lower().split())


def evaluate(rows, predictions):
    targets = {r.id: r for r in rows if r.split == 'test'}
    if not targets:
        raise ValueError('No held-out test sources')
    if len({p['id'] for p in predictions}) != len(predictions):
        raise ValueError('Duplicate prediction IDs')
    supplied = {p['id']: p for p in predictions}
    if set(supplied) - set(targets):
        raise ValueError('Predictions must refer only to test records')
    tp = fp = fn = dish = valid = quantities = quantity_total = step_hits = step_total = 0
    invented = observed = unknown_ok = unknown_total = 0
    timestamp_errors, latency, vram = [], [], []
    vi_total = vi_dish = 0
    for key, row in targets.items():
        pred = supplied.get(key, {})
        if isinstance(pred.get('latency_seconds'), (int, float)):
            latency.append(pred['latency_seconds'])
        if isinstance(pred.get('peak_vram_bytes'), (int, float)):
            vram.append(pred['peak_vram_bytes'])
        try:
            recipe = Recipe.model_validate(pred.get('recipe'))
        except ValueError:
            fn += len(row.target.ingredients)
            step_total += len(row.target.steps)
            quantity_total += sum(i.quantity is not None for i in row.target.ingredients)
            unknown_total += sum(i.quantity is None for i in row.target.ingredients)
            if row.language.startswith('vi'):
                vi_total += 1
            continue
        valid += 1
        same_dish = norm(recipe.title) == norm(row.target.title)
        dish += same_dish
        actual = {norm(i.name): i for i in row.target.ingredients}
        result = {norm(i.name): i for i in recipe.ingredients}
        tp += len(actual.keys() & result.keys())
        fp += len(result.keys() - actual.keys())
        fn += len(actual.keys() - result.keys())
        for name, target in actual.items():
            item = result.get(name)
            if target.quantity is not None:
                quantity_total += 1
                quantities += item is not None and item.quantity == target.quantity and norm(item.unit) == norm(target.unit)
            else:
                unknown_total += 1
                unknown_ok += item is not None and item.quantity is None
        for name, item in result.items():
            observed += 1
            invented += name not in actual or (item.quantity is not None and
                (actual[name].quantity is None or item.quantity != actual[name].quantity))
        step_map = {norm(s.text): s for s in recipe.steps}
        for target in row.target.steps:
            step_total += 1
            item = step_map.get(norm(target.text))
            step_hits += item is not None
            if item is not None and target.evidence.start is not None and item.evidence.start is not None:
                timestamp_errors.append(abs(item.evidence.start - target.evidence.start))
        if row.language.startswith('vi'):
            vi_total += 1
            vi_dish += same_dish
    def ratio(a, b):
        return a / b if b else None
    precision, recall = ratio(tp, tp + fp), ratio(tp, tp + fn)
    return {'test_records': len(targets), 'predictions_received': len(supplied),
        'fixture_only': all(r.fixture for r in targets.values()),
        'real_source_records': sum(not r.fixture for r in targets.values()),
        'real_accuracy_evidence': all(not r.fixture and r.annotation_verified for r in targets.values()),
        'json_validity': valid / len(targets), 'dish_exact_accuracy': dish / len(targets),
        'ingredient_precision': precision, 'ingredient_recall': recall,
        'ingredient_f1': 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else None,
        'quantity_exact_accuracy': ratio(quantities, quantity_total),
        'step_exact_recall': ratio(step_hits, step_total),
        'timestamp_start_mae_seconds': ratio(sum(timestamp_errors), len(timestamp_errors)),
        'timestamp_pairs': len(timestamp_errors), 'hallucinated_ingredient_or_quantity_rate': ratio(invented, observed),
        'unknown_quantity_recall': ratio(unknown_ok, unknown_total),
        'vietnamese_dish_exact_accuracy': ratio(vi_dish, vi_total), 'vietnamese_test_records': vi_total,
        'mean_latency_seconds': ratio(sum(latency), len(latency)), 'peak_vram_bytes': max(vram) if vram else None,
        'limitations': 'Exact lexical metrics only; manual evidence/safety review required. Null means unmeasured.'}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--manifest', required=True)
    p.add_argument('--root')
    p.add_argument('--predictions', required=True)
    p.add_argument('--output', required=True)
    a = p.parse_args()
    rows, report = validate(a.manifest, a.root)
    predictions = [json.loads(line) for line in Path(a.predictions).read_text(encoding='utf-8').splitlines() if line.strip()]
    output = {**evaluate(rows, predictions), 'manifest_sha256': report['manifest_sha256']}
    Path(a.output).write_text(json.dumps(output, indent=2), encoding='utf-8')
    print(json.dumps(output))


if __name__ == '__main__':
    main()
