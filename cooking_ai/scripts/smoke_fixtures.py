"""Self-authored text cards: plumbing only, NOT real-food accuracy evidence."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from cooking_ai.data.manifest import digest


def create(root):
    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    records = []
    for number, (split, name, text) in enumerate([
        ('train', 'Water', 'Water\nIngredient: 200 ml water\nStep: Pour water into a cup.'),
        ('validation', 'Rice', 'Rice\nIngredient: 100 g rice\nStep: Rinse rice.'),
        ('test', 'Tea', 'Tea\nIngredient: 1 g tea\nStep: Put tea in a cup.')]):
        image = Image.new('RGB', (384, 256), 'white')
        ImageDraw.Draw(image).multiline_text((16, 25), text, fill='black', spacing=10)
        path = root / f'card-{number}.png'
        image.save(path)
        ingredient = [('water', 200, 'ml'), ('rice', 100, 'g'), ('tea', 1, 'g')][number]
        evidence = {'source': 'ocr', 'text': text, 'start': None, 'end': None}
        records.append({'id': f'fixture-{number}', 'source_id': f'self-authored-card-{number}',
            'kind': 'image', 'media': path.name, 'sha256': digest(path), 'text': '',
            'attribution': 'reCime self-authored technical fixture', 'license': 'CC0-1.0',
            'permission_to_train': True, 'permission_evidence': 'Authored in this repository; dedicated CC0',
            'language': 'en', 'split': split, 'fixture': True,
            'target': {'is_cooking': True, 'title': name, 'language': 'en',
                'ingredients': [{'name': ingredient[0], 'quantity': ingredient[1], 'unit': ingredient[2], 'evidence': evidence}],
                'steps': [{'text': text.split('Step: ')[1], 'evidence': evidence}],
                'uncertainty': ['Technical text-card fixture; not food recognition evidence'], 'suggested_recipe': None}})
    manifest = root / 'manifest.jsonl'
    manifest.write_text('\n'.join(json.dumps(r) for r in records) + '\n', encoding='utf-8')
    return manifest


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument('--output', default='cooking_ai/data/private/smoke')
    print(create(p.parse_args().output))
