$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:DEMAND_SERVICE_TOKEN)) {
    throw 'Set DEMAND_SERVICE_TOKEN to the same private value used by Spring Boot.'
}
$demandPython = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $demandPython)) {
    throw 'Create AI/demand/.venv and install AI/demand/requirements.txt first; see docs/demand-forecasting.md.'
}
& $demandPython -m uvicorn service:app --app-dir $PSScriptRoot --host 127.0.0.1 --port 8091 --workers 1
exit $LASTEXITCODE
