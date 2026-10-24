"""Real multimodal assistant-only LoRA loss, optimizer, resume, validation and export."""
import argparse
import hashlib
import json
import random
import time
import os
from pathlib import Path
from cooking_ai.data.manifest import validate, media_path
from cooking_ai.schema import PROMPT


def weight_hash(model):
    result = hashlib.sha256()
    for name, value in model.named_parameters():
        if value.requires_grad:
            result.update(name.encode())
            result.update(value.detach().float().cpu().contiguous().numpy().tobytes())
    return result.hexdigest()


def messages(row, prompt=PROMPT):
    content = [] if row.kind == 'text' else [{'type': 'image'}]
    content.append({'type': 'text', 'text': prompt + '\nSupplied text/timeline:\n' + row.text})
    return [{'role': 'user', 'content': content}]


def batch(processor, row, root, device, max_length):
    import torch
    from PIL import Image, ImageOps
    if row.kind == 'video':
        raise ValueError('Preprocess video into annotated timestamped images before training')
    images = None
    if row.kind == 'image':
        with Image.open(media_path(root, row.media)) as original:
            image = ImageOps.exif_transpose(original).convert('RGB')
            image.thumbnail((512, 512))
            images = [image]
    prompt = processor.apply_chat_template(messages(row), add_generation_prompt=True, tokenize=False)
    conversation = messages(row) + [{'role': 'assistant', 'content': [
        {'type': 'text', 'text': row.target.model_dump_json()}]}]
    full = processor.apply_chat_template(conversation, add_generation_prompt=False, tokenize=False)
    options = {'images': images} if images else {}
    inputs = processor(text=full, return_tensors='pt', **options)
    prefix = processor(text=prompt, return_tensors='pt', **options)['input_ids']
    length = prefix.shape[1]
    if inputs['input_ids'].shape[1] > max_length:
        raise ValueError('Example exceeds max_length; preprocess explicitly, never truncate images/labels')
    if not torch.equal(inputs['input_ids'][:, :length], prefix):
        raise ValueError('Processor assistant prefix mismatch; refusing incorrect label mask')
    labels = inputs['input_ids'].clone()
    labels[:, :length] = -100
    if not (labels != -100).any():
        raise ValueError('No assistant tokens')
    inputs['labels'] = labels
    return {key: value.to(device) for key, value in inputs.items()}


def load_base(config, revision, device):
    import torch
    from transformers import AutoModelForImageTextToText, AutoProcessor, BitsAndBytesConfig
    if device == 'cuda' and torch.cuda.mem_get_info()[0] < config.get('minimum_cuda_gb', 0) * 1024 ** 3:
        raise RuntimeError('Insufficient free VRAM for configured VLM; use a smaller model or approved GPU')
    options = {'revision': revision, 'trust_remote_code': False,
               'torch_dtype': torch.float16 if device == 'cuda' else torch.float32,
               'attn_implementation': 'eager'}
    if config.get('quantize'):
        if device != 'cuda':
            raise ValueError('QLoRA requires CUDA and separately installed bitsandbytes')
        options['quantization_config'] = BitsAndBytesConfig(load_in_4bit=True,
            bnb_4bit_quant_type='nf4', bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=torch.float16)
        options['device_map'] = {'': 0}
    model = AutoModelForImageTextToText.from_pretrained(config['base_model'], **options)
    if not config.get('quantize'):
        model.to(device)
    processor = AutoProcessor.from_pretrained(config['base_model'], revision=revision, trust_remote_code=False)
    # Bound SmolVLM image splitting/resolution; Qwen has a different processor API.
    if 'SmolVLM' in config['base_model']:
        processor.image_processor.do_image_splitting = False
    return model, processor


def run(args):
    import torch
    torch.set_num_threads(min(4, os.cpu_count() or 1))
    from huggingface_hub import model_info
    from peft import LoraConfig, get_peft_model, PeftModel, prepare_model_for_kbit_training
    config = json.loads(Path(args.config).read_text())
    rows, report = validate(args.manifest, args.root)
    if report['duplicates']:
        raise ValueError('Deduplicate manifest before training')
    train = [r for r in rows if r.split == 'train']
    val = [r for r in rows if r.split == 'validation']
    if not train or not val:
        raise ValueError('Training and source-separated validation required')
    validation_total = len(val)
    if args.validation_limit:
        val = val[:args.validation_limit]
    if any(r.fixture for r in rows) and not args.allow_fixtures:
        raise ValueError('Fixtures require --allow-fixtures and cannot establish cooking accuracy')
    root = args.root or str(Path(args.manifest).parent)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    if (output / 'run.json').exists() and not args.resume:
        raise ValueError('Output already contains a run; use --resume or another output')
    random.seed(config['seed'])
    torch.manual_seed(config['seed'])
    device = 'cuda' if torch.cuda.is_available() and not args.cpu else 'cpu'
    previous = json.loads((Path(args.resume) / 'run.json').read_text()) if args.resume else None
    if previous:
        revision = previous['revision']
    elif os.getenv('HF_HUB_OFFLINE') == '1':
        from huggingface_hub import snapshot_download
        revision = Path(snapshot_download(config['base_model'], revision=config['revision'],
                        allow_patterns=['config.json'], local_files_only=True)).name
    else:
        revision = model_info(config['base_model'], revision=config['revision']).sha
    if previous and (previous['manifest_sha256'] != report['manifest_sha256'] or previous['config'] != config):
        raise ValueError('Resume manifest/config mismatch')
    prompt_hash = hashlib.sha256(PROMPT.encode()).hexdigest()
    if previous and previous.get('prompt_sha256') != prompt_hash:
        raise ValueError('Resume prompt mismatch; use the original training code/template')
    base, processor = load_base(config, revision, device)
    if config.get('quantize'):
        base = prepare_model_for_kbit_training(base)
    if args.resume:
        model = PeftModel.from_pretrained(base, args.resume, is_trainable=True)
    else:
        model = get_peft_model(base, LoraConfig(r=config['rank'], lora_alpha=config['rank'] * 2,
                    target_modules=['q_proj', 'v_proj'], lora_dropout=0.0, task_type='CAUSAL_LM'))
    model.config.use_cache = False
    if config.get('gradient_checkpointing'):
        model.gradient_checkpointing_enable(gradient_checkpointing_kwargs={'use_reentrant': False})
    optimizer = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad), lr=config['learning_rate'])
    # FP16 frozen base + FP32 trainable adapters; GradScaler prevents FP16 underflow.
    for param in model.parameters():
        if param.requires_grad:
            param.data = param.data.float()
    scaler = torch.amp.GradScaler('cuda', enabled=device == 'cuda')
    step = 0
    if args.resume:
        state = torch.load(Path(args.resume) / 'optimizer.pt', map_location='cpu', weights_only=True)
        optimizer.load_state_dict(state['optimizer'])
        scaler.load_state_dict(state['scaler'])
        step = state['step']
        torch.set_rng_state(state['rng'])
        if device == 'cuda' and 'cuda_rng' in state:
            torch.cuda.set_rng_state_all(state['cuda_rng'])
    before = weight_hash(model)
    initial = previous['initial_weights_sha256'] if previous else before
    start = time.monotonic()
    metrics = []
    limit = args.steps or config['max_steps']
    if limit <= step:
        raise ValueError('--steps is total desired optimizer steps; must exceed resumed step')
    model.train()
    for current in range(step, limit):
        inputs = batch(processor, train[current % len(train)], root, device, config['max_length'])
        optimizer.zero_grad(set_to_none=True)
        with torch.autocast(device_type=device, dtype=torch.float16, enabled=device == 'cuda'):
            loss = model(**inputs).loss
        if not torch.isfinite(loss):
            raise ValueError('Non-finite training loss')
        scaler.scale(loss).backward()
        scaler.unscale_(optimizer)
        norm = torch.nn.utils.clip_grad_norm_([p for p in model.parameters() if p.requires_grad], 1.0)
        if not torch.isfinite(norm):
            raise ValueError('Non-finite gradients; optimizer step not claimed')
        scaler.step(optimizer)
        scaler.update()
        metrics.append({'step': current + 1, 'loss': float(loss.detach()), 'gradient_norm': float(norm)})
        with (output / 'metrics.jsonl').open('a', encoding='utf-8') as log:
            log.write(json.dumps(metrics[-1]) + '\n')
        print(json.dumps(metrics[-1]), flush=True)
    after = weight_hash(model)
    if after == before:
        raise RuntimeError('Adapter weights did not change')
    model.eval()
    losses = []
    with torch.no_grad():
        for row in val:
            with torch.autocast(device_type=device, dtype=torch.float16, enabled=device == 'cuda'):
                value = model(**batch(processor, row, root, device, config['max_length'])).loss
            if not torch.isfinite(value):
                raise ValueError('Non-finite validation loss')
            losses.append(float(value))
    model.save_pretrained(output, safe_serialization=True)
    processor.save_pretrained(output)
    saved = {'optimizer': optimizer.state_dict(), 'scaler': scaler.state_dict(), 'step': limit,
             'rng': torch.get_rng_state()}
    if device == 'cuda':
        saved['cuda_rng'] = torch.cuda.get_rng_state_all()
    torch.save(saved, output / 'optimizer.pt')
    metadata = {**report, 'base_model': config['base_model'], 'revision': revision,
        'prompt': PROMPT, 'prompt_sha256': prompt_hash,
        'config': config, 'optimizer_steps': limit, 'initial_weights_sha256': initial,
        'resume_start_weights_sha256': before, 'final_weights_sha256': after,
        'adapter_sha256': __import__('cooking_ai.data.manifest', fromlist=['digest']).digest(output / 'adapter_model.safetensors'),
        'validation_loss': sum(losses) / len(losses), 'validation_records': len(val), 'validation_total': validation_total, 'device': device,
        'peak_vram_bytes': torch.cuda.max_memory_allocated() if device == 'cuda' else 0,
        'elapsed_seconds': time.monotonic() - start, 'metrics': (previous['metrics'] if previous else []) + metrics,
        'status': 'SMOKE_TRAINED' if report['fixtures'] else 'TRAINED_PENDING_HELD_OUT_EVALUATION',
        'inference_verified': False, 'model_version': 'lora-' + after[:12]}
    (output / 'run.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(json.dumps({'output': str(output), 'steps': limit, 'status': metadata['status'],
                      'validation_loss': metadata['validation_loss'], 'weights_changed': before != after}))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', default='cooking_ai/configs/smolvlm.json')
    parser.add_argument('--manifest', required=True)
    parser.add_argument('--root')
    parser.add_argument('--output', required=True)
    parser.add_argument('--resume')
    parser.add_argument('--steps', type=int)
    parser.add_argument('--validation-limit', type=int, help='Explicit bounded validation subset; reported in run.json')
    parser.add_argument('--cpu', action='store_true')
    parser.add_argument('--allow-fixtures', action='store_true')
    run(parser.parse_args())


if __name__ == '__main__':
    main()
