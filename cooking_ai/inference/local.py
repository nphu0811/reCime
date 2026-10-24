import json
import os
import threading
from pathlib import Path
from cooking_ai.data.manifest import digest
from cooking_ai.schema import Recipe, PROMPT, LEGACY_PROMPT


def model_info():
    folder = Path(os.getenv('COOKING_AI_ADAPTER', 'cooking_ai/runs/production'))
    try:
        info = json.loads((folder / 'run.json').read_text())
        integrity = (info['optimizer_steps'] > 0 and info['initial_weights_sha256'] != info['final_weights_sha256']
                     and digest(folder / 'adapter_model.safetensors') == info['adapter_sha256'])
        available = (integrity and not info['fixtures'] and info['inference_verified']
                     and info.get('inference_json_valid', False) and info.get('production_approved', False))
        return {'provider': 'trained_local_vlm', 'available': available, 'status': info['status'],
                'modelVersion': info['model_version'], 'baseModel': info['base_model'],
                'optimizerSteps': info['optimizer_steps'], 'fixtureOnly': bool(info['fixtures']),
                'integrity': integrity, 'inferenceVerified': info.get('inference_verified', False),
                'jsonValid': info.get('inference_json_valid', False),
                'productionApproved': info.get('production_approved', False)}
    except (OSError, ValueError, KeyError):
        return {'provider': 'trained_local_vlm', 'available': False, 'status': 'TRAINING_READY / WAITING_FOR_DATA'}


class CookingModelInference:
    def __init__(self):
        self.lock = threading.Lock()
        self.model = self.processor = None
        self.folder = None

    def load(self, folder, smoke=False):
        import torch
        from peft import PeftModel
        from cooking_ai.training.train import load_base
        folder = Path(folder).resolve()
        meta = json.loads((folder / 'run.json').read_text())
        if digest(folder / 'adapter_model.safetensors') != meta['adapter_sha256']:
            raise ValueError('Adapter checksum mismatch')
        if meta['fixtures'] and not smoke:
            raise ValueError('Fixture smoke adapter is not a cooking model')
        self.device = 'cuda' if torch.cuda.is_available() else 'cpu'
        base, self.processor = load_base(meta['config'], meta['revision'], self.device)
        self.model = PeftModel.from_pretrained(base, folder).eval()
        self.folder = folder
        self.meta = meta

    def generate(self, images, timeline='', folder=None, smoke=False, raw=False, max_tokens=1200):
        import torch
        torch.set_num_threads(min(4, os.cpu_count() or 1))
        with self.lock:
            folder = Path(folder or os.getenv('COOKING_AI_ADAPTER', 'cooking_ai/runs/production')).resolve()
            if self.model is None or self.folder != folder:
                self.load(folder, smoke)
            prompt_text = self.meta.get('prompt', LEGACY_PROMPT)
            content = [{'type': 'image'} for _ in images] + [{'type': 'text', 'text': prompt_text + '\nSupplied text/timeline:\n' + timeline}]
            prompt = self.processor.apply_chat_template([{'role': 'user', 'content': content}],
                         tokenize=False, add_generation_prompt=True)
            inputs = self.processor(text=prompt, images=images or None, return_tensors='pt').to(self.device)
            if inputs['input_ids'].shape[1] > 10000:
                raise ValueError('Input exceeds inference context budget')
            with torch.inference_mode():
                generated = self.model.generate(**inputs, max_new_tokens=max_tokens, max_time=180, do_sample=False)
            value = self.processor.batch_decode(generated[:, inputs['input_ids'].shape[1]:], skip_special_tokens=True)[0]
            if raw:
                return value
            return Recipe.model_validate_json(value)


local_model = CookingModelInference()
