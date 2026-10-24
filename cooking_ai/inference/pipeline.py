import base64
import io
import json
import os
import re
import time
from pathlib import Path
import requests
from PIL import Image, ImageOps
from cooking_ai.schema import Recipe, PROMPT
from cooking_ai.preprocessing.video import VideoDecoder, KeyframeSampler, SpeechToText, OCR, TimelineAligner
from cooking_ai.inference.local import local_model, model_info


def normalized_image(path):
    with Image.open(path) as original:
        if original.format not in ('JPEG', 'PNG', 'WEBP') or original.width * original.height > 40_000_000:
            raise ValueError('Use JPEG/PNG/WebP under 40 megapixels')
        if getattr(original, 'is_animated', False):
            raise ValueError('Animated images unsupported')
        image = ImageOps.exif_transpose(original).convert('RGB')
        image.thumbnail((1024, 1024))
        return image


def strict_schema(value):
    if isinstance(value, dict):
        value = {k: strict_schema(v) for k, v in value.items() if k != 'default'}
        if value.get('type') == 'object':
            value['additionalProperties'] = False
            value['required'] = list(value.get('properties', {}))
        return value
    return [strict_schema(v) for v in value] if isinstance(value, list) else value


def openai(images, timeline):
    key = os.getenv('OPENAI_API_KEY', '').strip()
    if not key:
        raise RuntimeError('OpenAI is not configured; no fallback was attempted')
    content = [{'type': 'input_text', 'text': PROMPT + '\nTimeline: ' + timeline}]
    for image in images:
        buffer = io.BytesIO()
        image.save(buffer, format='JPEG', quality=85)
        content.append({'type': 'input_image', 'image_url': 'data:image/jpeg;base64,' +
                        base64.b64encode(buffer.getvalue()).decode('ascii'), 'detail': 'high'})
    payload = {'model': os.getenv('OPENAI_MODEL', 'gpt-4.1-mini'), 'store': False,
        'max_output_tokens': 3500, 'input': [{'role': 'user', 'content': content}],
        'text': {'format': {'type': 'json_schema', 'name': 'cooking_evidence', 'strict': True,
                          'schema': strict_schema(Recipe.model_json_schema())}}}
    response = requests.post('https://api.openai.com/v1/responses', json=payload,
        headers={'Authorization': 'Bearer ' + key}, timeout=(8, 90))
    if response.status_code != 200:
        raise RuntimeError('External provider failed HTTP ' + str(response.status_code))
    data = response.json()
    texts = [c['text'] for i in data.get('output', []) for c in i.get('content', []) if c.get('type') == 'output_text']
    return Recipe.model_validate_json(''.join(texts))


class RecipeSchemaValidator:
    def validate(self, recipe, timeline, duration=None):
        for item in [*recipe.ingredients, *recipe.steps]:
            evidence = item.evidence
            if duration is None and evidence.start is not None:
                raise ValueError('Image results must not invent timestamps')
            if duration is not None and evidence.end is not None and evidence.end > duration:
                raise ValueError('Evidence timestamp exceeds source duration')
            if evidence.source in ('audio', 'ocr', 'user_text'):
                matches = [t for t in timeline if t['source'] == evidence.source and evidence.text in t['text']]
                if not matches:
                    raise ValueError('Evidence quote not found in supplied timeline')
                if evidence.start is not None and not any(
                        t['start'] <= evidence.start <= evidence.end <= t['end'] for t in matches):
                    raise ValueError('Evidence timestamp does not match its quoted source')
            elif duration is not None and not any(t['source'] == 'visual' and t['start'] == evidence.start
                                                 and t['end'] == evidence.end for t in timeline):
                raise ValueError('Visual evidence must cite a supplied frame timestamp')
            if hasattr(item, 'quantity') and item.quantity is not None:
                numbers = [float(n.replace(',', '.')) for n in re.findall(r'\d+(?:[.,]\d+)?', evidence.text)]
                if item.quantity not in numbers:
                    raise ValueError('Quantity has no numeric evidence in its source quote')
                if item.unit and not re.search(r'(?<!\w)' + re.escape(item.unit) + r'(?!\w)', evidence.text, re.IGNORECASE):
                    raise ValueError('Unit has no literal evidence in its source quote')
            if hasattr(item, 'text'):
                for number in re.findall(r'\d+(?:[.,]\d+)?', item.text):
                    if number not in evidence.text:
                        raise ValueError('Step contains an unsupported numeric detail')
                if duration is None and evidence.source == 'visual':
                    raise ValueError('A still dish image cannot establish recipe steps')
        return recipe


def analyze(path, kind, provider, work, progress=lambda value: None, cancelled=lambda: False):
    if provider not in ('trained_local_vlm', 'openai_existing'):
        raise ValueError('Cooking AI disabled or invalid provider')
    if provider == 'trained_local_vlm' and not model_info()['available']:
        raise RuntimeError('Local model unavailable: ' + model_info()['status'] + '; select OpenAI explicitly if permitted')
    started, warnings, duration = time.monotonic(), [], None
    progress(10)
    if kind == 'video':
        decoded = VideoDecoder().decode(path, work, cancelled=cancelled)
        frames = KeyframeSampler().sample(decoded['frames'])
        progress(30)
        if cancelled():
            raise TimeoutError('Cancelled')
        speech, warning = SpeechToText().transcribe(decoded['audio'], os.getenv('COOKING_AI_ASR_MODEL'))
        warnings += warning
        progress(45)
        text, warning = OCR().read(frames)
        warnings += warning
        timeline = TimelineAligner().align(speech, text, frames)
        images = [normalized_image(f['path']) for f in frames]
        duration = decoded['duration']
    else:
        images = [normalized_image(path)]
        text, warning = OCR().read([{'path': path, 'timestamp': 0}])
        warnings += warning
        timeline = [{**t, 'start': None, 'end': None} for t in text]
    if cancelled():
        raise TimeoutError('Cancelled')
    progress(60)
    data = json.dumps(timeline, ensure_ascii=False)
    recipe = openai(images, data) if provider == 'openai_existing' else local_model.generate(images, data)
    RecipeSchemaValidator().validate(recipe, timeline, duration)
    progress(95)
    return {'recipe': recipe.model_dump(), 'requiresConfirmation': True, 'provider': provider,
        'modelVersion': os.getenv('OPENAI_MODEL', 'gpt-4.1-mini') if provider == 'openai_existing' else model_info()['modelVersion'],
        'scope': 'sampled-video-frames-and-available-timeline' if kind == 'video' else 'image-and-available-ocr',
        'duration': duration, 'warnings': warnings, 'timeline': timeline,
        'seconds': time.monotonic() - started}
