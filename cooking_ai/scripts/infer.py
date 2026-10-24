import argparse
import json
import time
from pathlib import Path
from PIL import Image
from cooking_ai.inference.local import CookingModelInference
from cooking_ai.schema import Recipe


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--adapter', required=True)
    p.add_argument('--image', required=True)
    p.add_argument('--smoke', action='store_true')
    p.add_argument('--max-tokens', type=int, default=600)
    a = p.parse_args()
    start = time.monotonic()
    with Image.open(a.image) as original:
        image = original.convert('RGB')
    value = CookingModelInference().generate([image], folder=a.adapter, smoke=a.smoke, raw=True, max_tokens=a.max_tokens)
    try:
        Recipe.model_validate_json(value)
        valid = True
    except ValueError:
        valid = False
    folder = Path(a.adapter)
    meta = json.loads((folder / 'run.json').read_text())
    meta['inference_verified'] = True
    meta['inference_json_valid'] = valid
    meta['inference_seconds'] = time.monotonic() - start
    (folder / 'run.json').write_text(json.dumps(meta, indent=2), encoding='utf-8')
    (folder / 'inference.json').write_text(json.dumps({'output': value, 'json_valid': valid}, ensure_ascii=False), encoding='utf-8')
    print(json.dumps({'checkpoint_loaded': True, 'json_valid': valid, 'seconds': meta['inference_seconds']}))


if __name__ == '__main__':
    main()
