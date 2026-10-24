"""Re-use licensed Wiki catalog, never read or export a user's private SQLite rows.

Image supervision is source-associated dish title ONLY. Recipe text must never be
presented as visually observed ingredients/quantities/steps of the associated photo.
"""
import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path
from urllib.parse import urlsplit
from cooking_ai.data.manifest import digest, source_split, validate


def allowed_license(value):
    parsed = urlsplit(value)
    return parsed.scheme in ('http', 'https') and parsed.hostname == 'creativecommons.org' and (
        bool(re.match(r'^/licenses/by(?:-sa)?/(?:2\.0|3\.0|4\.0)(?:/|$)', parsed.path)) or
        parsed.path.startswith('/publicdomain/zero/1.0'))


def export(catalog, output):
    catalog, output = Path(catalog), Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output / 'media').mkdir(exist_ok=True)
    data = json.loads((catalog / 'recipes.json').read_text(encoding='utf-8'))['recipes']
    parents = {r['catalogKey']: r['catalogKey'] for r in data}
    def find(key):
        while parents[key] != key:
            parents[key] = parents[parents[key]]
            key = parents[key]
        return key
    def union(a, b):
        parents[find(b)] = find(a)
    seen = {}
    # Link variants sharing an image, source article or title BEFORE splitting.
    for record in data:
        keys = ['title:' + record['title'].casefold(), 'source:' + record['source']]
        image = catalog / record.get('image', '')
        if image.is_file():
            keys.append('image:' + digest(image))
        for key in keys:
            if key in seen:
                union(seen[key], record['catalogKey'])
            seen[key] = record['catalogKey']
    rows, notices, used_hashes, skipped = [], [], set(), []
    for r in data:
        if not allowed_license(r.get('licenseUrl', '')) or not r.get('revision') or not r.get('authors'):
            skipped.append([r['catalogKey'], 'text-license-or-provenance-missing'])
            continue
        group = 'wiki-source-group:' + find(r['catalogKey'])
        common = {'source_id': group, 'permission_to_train': True, 'language': 'vi',
            'split': source_split(group), 'fixture': False}
        title = r['title']
        source = r['source']
        attribution = f"{r['authors']} | {source} | {r.get('history', '')} | {r['licenseUrl']} | {r.get('modifications', '')}"
        ingredient_lines = [line.strip() for line in r['ingredients'].splitlines() if line.strip()]
        step_lines = [line.strip() for line in r['steps'].splitlines() if line.strip()]
        body = title + '\nIngredients:\n' + '\n'.join(ingredient_lines) + '\nSteps:\n' + '\n'.join(step_lines)
        ingredients = []
        for line in ingredient_lines:
            match = re.match(r'^(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l|quả|củ|tép|muỗng canh|muỗng cà phê)\s+(.+)$', line)
            quantity, unit, name = (float(match[1].replace(',', '.')), match[2], match[3]) if match else (None, None, line)
            ingredients.append({'name': name, 'quantity': quantity, 'unit': unit,
                'evidence': {'source': 'user_text', 'text': line, 'start': None, 'end': None}})
        # Bounded extraction subset; skipped rows stay in the original catalog intact.
        if (len(body) <= 1800 and 0 < len(ingredients) <= 25 and 0 < len(step_lines) <= 20
            and all(len(i['name']) <= 200 and len(i['evidence']['text']) <= 2000 for i in ingredients)
            and all(len(line) <= 1500 for line in step_lines)):
            sha = hashlib.sha256(body.encode()).hexdigest()
            if sha not in used_hashes:
                used_hashes.add(sha)
                rows.append({**common, 'id': r['catalogKey'] + ':text', 'kind': 'text', 'media': None,
                    'text': body, 'sha256': sha, 'attribution': attribution, 'license': r['license'],
                    'permission_evidence': r['licenseUrl'] + ' | retained source revision ' + source,
                    'target': {'is_cooking': True, 'title': title, 'language': 'vi', 'ingredients': ingredients,
                        'steps': [{'text': line, 'evidence': {'source': 'user_text', 'text': line, 'start': None, 'end': None}}
                                  for line in step_lines],
                        'uncertainty': ['Chỉ trích văn bản nguồn; không xác minh an toàn hoặc ảnh tương ứng'], 'suggested_recipe': None}})
        image = catalog / r.get('image', '')
        if not image.is_file() or not allowed_license(r.get('imageLicenseUrl', '')):
            continue
        if r.get('imageMatch') != 'Ảnh được bài công thức dẫn chiếu':
            continue  # Family illustrations are not exact variant labels.
        sha = digest(image)
        if sha in used_hashes:
            continue
        used_hashes.add(sha)
        target = output / 'media' / (sha + '.jpg')
        if not target.exists():
            shutil.copyfile(image, target)
        credit = f"{r['imageAttribution']} | {r['imageSource']} | {r['imageLicenseUrl']} | {r.get('imageModification', '')}"
        rows.append({**common, 'id': r['catalogKey'] + ':image', 'kind': 'image',
            'media': 'media/' + target.name, 'sha256': sha, 'text': '', 'attribution': credit,
            'license': r['imageLicense'], 'permission_evidence': r['imageSource'] + ' | ' + r['imageLicenseUrl'],
            'target': {'is_cooking': True, 'title': title, 'language': 'vi', 'ingredients': [], 'steps': [],
                'uncertainty': ['Nhãn tên món theo bài nguồn, chưa xác minh thủ công; ảnh không chứng minh định lượng hoặc cách nấu'],
                'suggested_recipe': None}})
        notices.append({'id': r['catalogKey'], 'credit': credit, 'recipe_credit': attribution,
                        'label_quality': 'source-associated-title-not-manually-verified', 'sha256': sha})
    # Start training with actual images, interleave source text afterwards.
    rows.sort(key=lambda row: (row['kind'] != 'image', row['id']))
    manifest = output / 'manifest.jsonl'
    manifest.write_text('\n'.join(json.dumps(row, ensure_ascii=False) for row in rows) + '\n', encoding='utf-8')
    _, report = validate(manifest)
    report.update({'catalog_sha256': digest(catalog / 'recipes.json'),
        'image_records': sum(r['kind'] == 'image' for r in rows), 'text_records': sum(r['kind'] == 'text' for r in rows),
        'supervision': 'Wiki text extraction + weak source-associated image title; no image ingredient or step labels',
        'skipped': skipped, 'manual_annotation_verified': False})
    (output / 'quality.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    (output / 'ATTRIBUTION.json').write_text(json.dumps(notices, ensure_ascii=False, indent=2), encoding='utf-8')
    shutil.copyfile(catalog / 'LICENSE.md', output / 'LICENSE.md')
    return report


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--catalog', default='app/src/main/assets/catalog')
    p.add_argument('--output', default='cooking_ai/data/private/wiki')
    a = p.parse_args()
    print(json.dumps(export(a.catalog, a.output), ensure_ascii=True))
