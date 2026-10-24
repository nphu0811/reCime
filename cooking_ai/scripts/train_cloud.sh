#!/usr/bin/env bash
# Run manually INSIDE an already approved Linux GPU host. Never rents infrastructure.
set -euo pipefail
test "$#" -ge 2 || { echo 'Usage: bash cooking_ai/scripts/train_cloud.sh /data/manifest.jsonl /data [output]'; exit 2; }
nvidia-smi --query-gpu=name,memory.total --format=csv
python3 -m venv cooking_ai/.venv
cooking_ai/.venv/bin/python -m pip install -r cooking_ai/requirements-training.txt bitsandbytes==0.48.2
cooking_ai/.venv/bin/python -m cooking_ai.data.manifest "$1" --root "$2"
cooking_ai/.venv/bin/python -m cooking_ai.training.train --config cooking_ai/configs/qwen3vl.json --manifest "$1" --root "$2" --output "${3:-cooking_ai/runs/qwen3vl}"
