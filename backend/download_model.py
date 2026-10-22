"""Download only the pinned quantized CLIP image encoder (no torch, paid API or token)."""
from pathlib import Path
import hashlib,json,requests

MODEL='Xenova/clip-vit-base-patch32'
ROOT=Path(__file__).resolve().parent/'models/clip'

def main():
    ROOT.mkdir(parents=True,exist_ok=True)
    metadata=requests.get(f'https://huggingface.co/api/models/{MODEL}',timeout=30);metadata.raise_for_status()
    revision=metadata.json()['sha']
    tree=requests.get(f'https://huggingface.co/api/models/{MODEL}/tree/{revision}/onnx?expand=false',timeout=30);tree.raise_for_status()
    entry=next(x for x in tree.json() if x['path']=='onnx/vision_model_quantized.onnx')
    target=ROOT/'vision_model_quantized.onnx'
    expected=entry.get('lfs',{}).get('oid')
    if not target.exists() or (expected and hashlib.sha256(target.read_bytes()).hexdigest()!=expected):
        response=requests.get(f'https://huggingface.co/{MODEL}/resolve/{revision}/onnx/vision_model_quantized.onnx',stream=True,timeout=90);response.raise_for_status()
        temp=target.with_suffix('.part')
        with temp.open('wb') as out:
            for block in response.iter_content(1024*1024):out.write(block)
        actual=hashlib.sha256(temp.read_bytes()).hexdigest()
        if expected and actual!=expected:raise RuntimeError('Model SHA256 mismatch')
        temp.replace(target)
    provenance={'model':MODEL,'revision':revision,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'bytes':target.stat().st_size,
        'upstream':'https://huggingface.co/openai/clip-vit-base-patch32','license':'MIT (upstream CLIP); ONNX conversion by Xenova','engine':'onnxruntime CPU'}
    (ROOT/'provenance.json').write_text(json.dumps(provenance,indent=2),encoding='utf-8')
    print(json.dumps(provenance),flush=True)

if __name__=='__main__':main()
