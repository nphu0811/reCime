$ErrorActionPreference = 'Stop'
# Optional local env file; it is gitignored and not bundled into Android APK.
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envFile) {
  Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
      $separator = $line.IndexOf('=')
      if ($separator -gt 0) {
        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim().Trim('"')
        if ($name -match '^[A-Z][A-Z0-9_]*
Set-Location -LiteralPath $projectRoot
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    python -m venv backend/.venv
    & $python -m pip install -r backend/requirements.txt
}
& $python -m uvicorn backend.app:app --host 127.0.0.1 --port 8765
) {
          [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
      }
    }
  }
}
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    python -m venv backend/.venv
    & $python -m pip install -r backend/requirements.txt
}
& $python -m uvicorn backend.app:app --host 127.0.0.1 --port 8765
