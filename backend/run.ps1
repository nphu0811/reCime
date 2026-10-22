$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    python -m venv backend/.venv
    & $python -m pip install -r backend/requirements.txt
}
& $python -m uvicorn backend.app:app --host 127.0.0.1 --port 8765
