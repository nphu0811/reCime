"""Generate held-out predictions with the same processor/template for base and adapter."""
import argparse
import json
import time
from pathlib import Path
from PIL import Image, ImageOps
from cooking_ai.data.manifest import validate, media_path
from cooking_ai.schema import Recipe, LEGACY_PROMPT


def main():
    import torch
    torch.set_num_threads(4)
    from cooking_ai.training.train import load_base, messages
    from cooking_ai.inference.local import CookingModelInference
    p = argparse.ArgumentParser()
    p.add_argument('--manifest', required=True)
    p.add_argument('--root')
    p.add_argument('--adapter', required=True, help='Used for pinned base revision even for --base')
    p.add_argument('--base', action='store_true')
    p.add_argument('--smoke', action='store_true')
    p.add_argument('--output', required=True)
    p.add_argument('--max-tokens', type=int, default=600)
    a = p.parse_args()
    rows, report = validate(a.manifest, a.root)
    if report['fixtures'] and not a.smoke:
        raise ValueError('Fixtures require --smoke')
    metadata = json.loads((Path(a.adapter) / 'run.json').read_text())
    root = a.root or Path(a.manifest).parent
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    if a.base:
        model, processor = load_base(metadata['config'], metadata['revision'], device)
        model.eval()
    else:
        inference = CookingModelInference()
        inference.load(a.adapter, a.smoke)
    with Path(a.output).open('w', encoding='utf-8') as output:
        for row in (r for r in rows if r.split == 'test'):
            if row.kind == 'video':
                raise ValueError('Preprocess video frames/timeline annotations before batch evaluation')
            images = []
            if row.media:
                with Image.open(media_path(root, row.media)) as original:
                    image = ImageOps.exif_transpose(original).convert('RGB')
                    image.thumbnail((512, 512))
                    images.append(image)
            start = time.monotonic()
            if a.base:
                prompt = processor.apply_chat_template(messages(row, metadata.get('prompt', LEGACY_PROMPT)), tokenize=False, add_generation_prompt=True)
                inputs = processor(text=prompt, images=images or None, return_tensors='pt').to(device)
                with torch.inference_mode():
                    generated = model.generate(**inputs, do_sample=False, max_new_tokens=a.max_tokens)
                raw = processor.batch_decode(generated[:, inputs['input_ids'].shape[1]:], skip_special_tokens=True)[0]
            else:
                raw = inference.generate(images, row.text, folder=a.adapter, smoke=a.smoke, raw=True, max_tokens=a.max_tokens)
            try:
                recipe = Recipe.model_validate_json(raw).model_dump()
            except ValueError:
                recipe = None
            output.write(json.dumps({'id': row.id, 'recipe': recipe, 'raw': raw,
                'model_version': 'base-' + metadata['revision'] if a.base else metadata['model_version'],
                'latency_seconds': time.monotonic() - start,
                'peak_vram_bytes': torch.cuda.max_memory_allocated() if device == 'cuda' else 0}, ensure_ascii=False) + '\n')


if __name__ == '__main__':
    main()
